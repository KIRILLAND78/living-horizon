package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the messages a server with the mod sends ({@link PositionsMessage}), as the client keeps them. */
class SharedPositionsTest {
    private static final UUID HORSE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID GHAST = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    private static SharedPositions received() {
        SharedPositions shared = new SharedPositions();
        // Steve, in the nether, looking at 270 degrees, on the ghast; Alex logged off in the end.
        shared.receive(PositionsMessage.players(List.of(
                new PositionsMessage.Player("Steve", -1234.5, 64.25, 8001.9, 270f, NETHER, true, true, GHAST))));
        PositionsMessage.offline(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, END, false, false, null))).forEach(shared::receive);
        // A baby horse (kind 1), variant 513, in the end, sent around a player at (0, 0).
        shared.receive(PositionsMessage.mobs(new PositionsMessage.Mobs(List.of(
                new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, END, true, 513)), END, 0, 0, 2048)));
        return shared;
    }

    @Test
    void decodesThePlayers() {
        SharedPositions shared = received();
        assertTrue(shared.update());
        SharedPositions.Report steve = shared.read("Steve");
        assertNotNull(steve);
        assertEquals(-1234.5, steve.x(), 1e-9);
        assertEquals(64.25, steve.y(), 1e-9);
        assertEquals(8001.9, steve.z(), 1e-9);
        assertEquals(270f, steve.yaw());
        assertEquals(NETHER, steve.dimension());
        assertTrue(steve.riding());
        assertEquals(GHAST, shared.vehicle("Steve"));

        SharedPositions.Report alex = shared.read("Alex");
        assertNotNull(alex);
        assertEquals(END, alex.dimension());
        assertNull(shared.vehicle("Alex"));
        assertNull(shared.read("Nobody"));
        assertEquals(List.of("Steve", "Alex"), shared.holders());
    }

    @Test
    void decodesAMob() {
        SharedPositions shared = received();
        shared.update();
        assertEquals(List.of(HORSE), shared.mobs());
        SharedPositions.MobReport horse = shared.readMob(HORSE);
        assertNotNull(horse);
        assertEquals(1.5, horse.x(), 1e-9);
        assertEquals(70.0, horse.y(), 1e-9);
        assertEquals(-2.5, horse.z(), 1e-9);
        assertEquals("minecraft:horse", MobKinds.type(horse.kind()));
        assertEquals(END, horse.dimension());
        assertTrue(horse.baby());
        assertEquals(513, horse.variant());
        // Gone from where they were sent around, only out of reach farther off.
        assertTrue(shared.covers(END, 100, 100));
        assertFalse(shared.covers(END, 3000, 0));
        assertFalse(shared.covers(OVERWORLD, 100, 100));
    }

    @Test
    void stopsTrustingASilentServer() {
        SharedPositions shared = received();
        for (int tick = 0; tick < 59; tick++) assertTrue(shared.update());
        assertFalse(shared.update());
        assertNull(shared.read("Steve"));
        // News again.
        shared.receive(PositionsMessage.players(List.of()));
        assertTrue(shared.update());
    }

    @Test
    void keepsTheMountOfAPlayerWhoLoggedOff() {
        SharedPositions shared = new SharedPositions();
        PositionsMessage.Mount horse = new PositionsMessage.Mount("minecraft:horse", "{Variant:513}", 0, 0.85, -0.2, 45f, -10f);
        shared.receive(PositionsMessage.players(List.of()));
        PositionsMessage.offline(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, OVERWORLD, false, true, null, horse))).forEach(shared::receive);
        shared.update();
        assertTrue(shared.read("Alex").riding());
        assertEquals(horse, shared.mount("Alex"));
        // Back online: what they ride is no longer kept for them.
        shared.receive(PositionsMessage.players(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, OVERWORLD, true, false, null))));
        assertNull(shared.mount("Alex"));
        PositionsMessage.Saved saved = PositionsMessage.readSaved(PositionsMessage.saved(
                List.of(new PositionsMessage.Player("Alex", 10, 70, -20, 90f, OVERWORLD, false, true, null, horse)), List.of()));
        assertNotNull(saved);
        assertEquals(horse, saved.players().get(0).mount());
    }

    @Test
    void keepsLooksAndMountsBetweenTheirMessages() {
        SharedPositions shared = new SharedPositions();
        PositionsMessage.Look look = new PositionsMessage.Look("dGV4dHVyZXM=", "c2lnbg==",
                List.of("{count:1,id:\"minecraft:diamond_sword\"}", "", "", "", "", ""));
        PositionsMessage.Mount boat = new PositionsMessage.Mount("minecraft:oak_boat", "{}", 0, -0.2, 0, 90f, 0f);
        // Every few seconds: how they look and what they ride.
        shared.receive(PositionsMessage.players(List.of(new PositionsMessage.Player("Steve", 1, 2, 3, 0f, OVERWORLD, true, true,
                null, null, null, 0, false).full(boat, look))));
        // In between: positions alone, which keep what came before.
        shared.receive(PositionsMessage.players(List.of(new PositionsMessage.Player("Steve", 4, 2, 3, 0f, OVERWORLD, true, true, null))));
        shared.update();
        assertEquals(look, shared.look("Steve"));
        assertEquals(boat, shared.mount("Steve"));
        assertEquals(4, shared.read("Steve").x(), 1e-9);
        // Off the boat: no mount any more.
        shared.receive(PositionsMessage.players(List.of(new PositionsMessage.Player("Steve", 4, 2, 3, 0f, OVERWORLD, true, false, null))));
        assertNull(shared.mount("Steve"));
        // A test player of the host's, logged off a while ago.
        PositionsMessage.offline(List.of(new PositionsMessage.Player("Sleeper1", 0, 64, 0, 0f, OVERWORLD, false, false,
                null, null, look, 1760100000000L, true))).forEach(shared::receive);
        assertTrue(shared.read("Sleeper1").test());
        assertEquals(1760100000000L, shared.read("Sleeper1").lastSeen());
        assertEquals(look, shared.look("Sleeper1"));
    }

    @Test
    void sharesWhatAMobWears() {
        SharedPositions shared = new SharedPositions();
        shared.receive(PositionsMessage.players(List.of()));
        shared.receive(PositionsMessage.mobs(new PositionsMessage.Mobs(List.of(new PositionsMessage.Mob(GHAST, 0, 90, 0,
                1, OVERWORLD, false, 0, "{equipment:{body:{count:1,id:\"minecraft:white_harness\"}}}")), OVERWORLD, 0, 0, 2048)));
        shared.update();
        assertEquals("{equipment:{body:{count:1,id:\"minecraft:white_harness\"}}}", shared.readMob(GHAST).extra());
    }

    /** Too many players who logged off for one message: the list is taken once its last part has come. */
    @Test
    void takesTheOfflineListOnceWhole() {
        SharedPositions shared = new SharedPositions();
        shared.receive(PositionsMessage.players(List.of()));
        PositionsMessage.offline(List.of(new PositionsMessage.Player("Before", 0, 64, 0, 0f, OVERWORLD, false, false,
                null))).forEach(shared::receive);
        List<PositionsMessage.Player> crowd = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            crowd.add(new PositionsMessage.Player("Sleeper" + i, i, 64, 0, 0f, OVERWORLD, false, false, null));
        }
        List<byte[]> parts = PositionsMessage.offline(crowd, 1000);
        assertTrue(parts.size() >= 3, parts.size() + " parts");
        shared.update();
        // Halfway: still the list before.
        for (int i = 0; i < parts.size() - 1; i++) shared.receive(parts.get(i));
        assertEquals(List.of("Before"), shared.holders());
        shared.receive(parts.get(parts.size() - 1));
        assertEquals(60, shared.holders().size());
        assertNotNull(shared.read("Sleeper59"));
        // A part missing: the list it belonged to is dropped, the one before kept.
        PositionsMessage.offline(List.of(new PositionsMessage.Player("Before", 0, 64, 0, 0f, OVERWORLD, false, false,
                null))).forEach(shared::receive);
        shared.receive(parts.get(0));
        shared.receive(parts.get(2));
        shared.receive(parts.get(parts.size() - 1));
        assertEquals(List.of("Before"), shared.holders());
    }

    @Test
    void ignoresAnotherVersionOrGarbage() {
        SharedPositions shared = new SharedPositions();
        byte[] message = PositionsMessage.players(List.of(new PositionsMessage.Player("Steve", 0, 0, 0, 0, OVERWORLD, true, false, null)));
        // Written by a version that this one cannot read: remembered, to say so in the log.
        message[3] = 99;
        message[7] = 99;
        shared.receive(message);
        shared.receive(new byte[] {1, 2, 3});
        assertFalse(shared.update());
        assertEquals(new PositionsMessage.Format(99, 99), shared.unreadable());
        assertNull(PositionsMessage.read(new byte[0]));
    }

    @Test
    void theServerFileReadsBack() {
        List<PositionsMessage.Player> players = List.of(new PositionsMessage.Player("Alex", 10, 70, -20, 90f, END, false, false, null));
        List<PositionsMessage.Mob> mobs = List.of(new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, END, true, 513));
        PositionsMessage.Saved saved = PositionsMessage.readSaved(PositionsMessage.saved(players, mobs));
        assertNotNull(saved);
        assertEquals(players, saved.players());
        assertEquals(mobs, saved.mobs());
    }

    @Test
    void mobsKnownOnlyFromTheServerParse() throws Exception {
        for (String type : MobKinds.TYPES) {
            TagParser.parseCompoundFully(MobKinds.nbt(type, 3, true));
        }
    }
}
