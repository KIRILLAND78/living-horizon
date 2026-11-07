package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layout of the messages, byte for byte: a change to it without a new {@link PositionsMessage#VERSION}
 * fails here, and so does one that an older client of the same version could not read.
 */
class PositionsMessageTest {
    private static final UUID HORSE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    /** Steve, online in the nether, at (1.5, 64, -2), facing 90 degrees. */
    private static final String STEVE = "00000004" + "00000003" + "00"
            // The dimensions: one, by its id.
            + "01" + "0014" + hex(NETHER)
            // One player, 37 bytes: name, x, y, z, yaw, dimension 0, online.
            + "01" + "25" + "0005" + hex("Steve") + "3ff8000000000000" + "4050000000000000" + "c000000000000000"
            + "42b40000" + "00" + "01";

    /** A baby horse (kind 1, variant 513) in the overworld, sent around (0, 0), 2048 blocks. */
    private static final String HORSE_AROUND = "00000004" + "00000003" + "01"
            + "01" + "0013" + hex(OVERWORLD)
            // The circle, 25 bytes: dimension 0, x, z, radius.
            + "19" + "00" + "0000000000000000" + "0000000000000000" + "40a0000000000000"
            // One mob, 48 bytes: id, x, y, z, kind, dimension 0, baby, variant.
            + "01" + "30" + "0f8fad5bd9cb469fa16570867728950e" + "3ff8000000000000" + "4051800000000000"
            + "c004000000000000" + "0001" + "00" + "01" + "00000201";

    /**
     * Alex gliding with an elytra in the overworld, at (0, 120, 0), facing 0 and looking 15 degrees
     * down: the flag version 3 leaves unread, and the pitch at the end of the record, which it skips.
     */
    private static final String ALEX_GLIDING = "00000004" + "00000003" + "00"
            + "01" + "0013" + hex(OVERWORLD)
            // One player, 40 bytes: name, x, y, z, yaw, dimension 0, online and gliding, pitch.
            + "01" + "28" + "0004" + hex("Alex") + "0000000000000000" + "405e000000000000" + "0000000000000000"
            + "00000000" + "00" + "81" + "41700000";

    private static final PositionsMessage.Player STEVE_PLAYER =
            new PositionsMessage.Player("Steve", 1.5, 64, -2, 90f, NETHER, true, false, null);
    private static final PositionsMessage.Mobs HORSE_MOBS = new PositionsMessage.Mobs(
            List.of(new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, OVERWORLD, true, 513)), OVERWORLD, 0, 0, 2048);

    @Test
    void writesThePlayersByteForByte() {
        assertEquals(STEVE, HexFormat.of().formatHex(PositionsMessage.players(List.of(STEVE_PLAYER))));
        PositionsMessage.Decoded read = PositionsMessage.read(bytes(STEVE));
        assertNotNull(read);
        assertEquals(PositionsMessage.PLAYERS, read.kind());
        assertEquals(List.of(STEVE_PLAYER), read.players());
    }

    @Test
    void writesAGliderByteForByte() {
        PositionsMessage.Player alex = new PositionsMessage.Player("Alex", 0, 120, 0, 0f, OVERWORLD, true, false, null,
                null, null, 0, false, true, 15f);
        assertEquals(ALEX_GLIDING, HexFormat.of().formatHex(PositionsMessage.players(List.of(alex))));
        PositionsMessage.Decoded read = PositionsMessage.read(bytes(ALEX_GLIDING));
        assertNotNull(read);
        assertEquals(List.of(alex), read.players());
        // Sent by position alone, still gliding: it comes with every message, not every few seconds.
        assertTrue(alex.plain().gliding());
    }

    /** The same player from a server of version 3, which never says who glides: standing, looking ahead. */
    @Test
    void readsAServerOfVersion3AsStanding() {
        String record = ALEX_GLIDING.substring(ALEX_GLIDING.indexOf("0004" + hex("Alex")));
        String standing = "00000003" + "00000003" + "00" + "01" + "0013" + hex(OVERWORLD)
                + "01" + "24" + record.substring(0, record.length() - 10) + "01";
        PositionsMessage.Decoded read = PositionsMessage.read(bytes(standing));
        assertNotNull(read);
        assertEquals(List.of(new PositionsMessage.Player("Alex", 0, 120, 0, 0f, OVERWORLD, true, false, null)),
                read.players());
    }

    @Test
    void writesTheMobsByteForByte() {
        assertEquals(HORSE_AROUND, HexFormat.of().formatHex(PositionsMessage.mobs(HORSE_MOBS)));
        PositionsMessage.Decoded read = PositionsMessage.read(bytes(HORSE_AROUND));
        assertNotNull(read);
        assertEquals(HORSE_MOBS, read.mobs());
    }

    /** Two players in two dimensions of mods: each keeps its own, never mistaken for the other's. */
    @Test
    void namesTheDimensionsOfMods() {
        List<PositionsMessage.Player> players = List.of(
                new PositionsMessage.Player("Steve", 1, 2, 3, 0f, "aether:the_aether", true, false, null),
                new PositionsMessage.Player("Alex", 1, 2, 3, 0f, "twilightforest:twilight_forest", true, false, null),
                new PositionsMessage.Player("Sam", 1, 2, 3, 0f, "aether:the_aether", true, false, null));
        PositionsMessage.Decoded read = PositionsMessage.read(PositionsMessage.players(players));
        assertNotNull(read);
        assertEquals(players, read.players());
    }

    /**
     * A later version that adds a field at the end of a record, and something after the
     * records, and says version 3 reads it: read, what is new skipped.
     */
    @Test
    void readsALaterVersionThatOnlyAddsFields() {
        String record = STEVE.substring(STEVE.indexOf("0005" + hex("Steve")));
        String later = "00000005" + "00000003" + "00" + "01" + "0014" + hex(NETHER)
                + "01" + "28" + record + "abcdef" + "0102030405";
        PositionsMessage.Decoded read = PositionsMessage.read(bytes(later));
        assertNotNull(read);
        assertEquals(List.of(STEVE_PLAYER), read.players());
        assertTrue(new PositionsMessage.Format(5, 3).readableBy(PositionsMessage.VERSION));
    }

    @Test
    void refusesAVersionItCannotRead() {
        byte[] message = bytes(STEVE);
        message[7] = 5;
        assertNull(PositionsMessage.read(message));
        PositionsMessage.Format format = PositionsMessage.format(message);
        assertEquals(new PositionsMessage.Format(4, 5), format);
        assertFalse(format.readableBy(PositionsMessage.VERSION));
        assertFalse(new PositionsMessage.Format(1, 1).readableBy(PositionsMessage.VERSION));
    }

    @Test
    void refusesADamagedMessage() {
        byte[] message = bytes(STEVE);
        assertNull(PositionsMessage.read(java.util.Arrays.copyOf(message, message.length - 1)));
        // A record longer than the message: refused, nothing allocated for it.
        message[message.length - 38] = 0x7f;
        assertNull(PositionsMessage.read(message));
        // A dimension past the table.
        byte[] lost = bytes(STEVE);
        lost[lost.length - 2] = 5;
        assertNull(PositionsMessage.read(lost));
    }

    /** A server with version 2: dimensions by number, those of mods all 3, left out. */
    @Test
    void readsTheMessagesOfVersion2() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2);
            out.writeByte(PositionsMessage.PLAYERS);
            out.writeInt(2);
            for (String name : List.of("Steve", "Alex")) {
                out.writeUTF(name);
                out.writeDouble(1.5);
                out.writeDouble(64);
                out.writeDouble(-2);
                out.writeFloat(90f);
                out.writeByte(name.equals("Steve") ? 1 : 3);
                out.writeByte(1);
            }
        }
        PositionsMessage.Decoded read = PositionsMessage.read(bytes.toByteArray());
        assertNotNull(read);
        assertEquals(List.of(STEVE_PLAYER), read.players());

        bytes.reset();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2);
            out.writeByte(PositionsMessage.MOBS);
            out.writeByte(3);
            out.writeDouble(0);
            out.writeDouble(0);
            out.writeDouble(2048);
            out.writeInt(0);
        }
        PositionsMessage.Decoded mobs = PositionsMessage.read(bytes.toByteArray());
        assertNotNull(mobs);
        assertEquals(List.of(), mobs.mobs().mobs());
        assertFalse(mobs.mobs().covers(OVERWORLD, 0, 0));
    }

    /** Too many players online with their mounts for one message: the first ones whole, then by position alone. */
    @Test
    void cutsPlayersToTheSize() {
        List<PositionsMessage.Player> players = mounted(200, true);
        int limit = 12_000;
        byte[] message = PositionsMessage.players(PositionsMessage.PLAYERS, players, limit);
        assertTrue(message.length <= limit, message.length + " bytes");
        List<PositionsMessage.Player> read = PositionsMessage.read(message).players();
        assertEquals(players.get(0), read.get(0));
        assertNull(read.get(read.size() - 1).mount());
        assertTrue(read.size() > 5 && read.size() < 200, read.size() + " players");
    }

    /** Too many players who logged off for one message: all of them, whole and in order, in several. */
    @Test
    void sendsEveryPlayerWhoLoggedOffInParts() {
        List<PositionsMessage.Player> players = mounted(200, false);
        int limit = 12_000;
        List<byte[]> parts = PositionsMessage.offline(players, limit);
        assertTrue(parts.size() > 1, parts.size() + " parts");
        List<PositionsMessage.Player> read = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(parts.get(i).length <= limit, parts.get(i).length + " bytes");
            PositionsMessage.Decoded part = PositionsMessage.read(parts.get(i));
            assertEquals(new PositionsMessage.Part(i, parts.size()), part.part());
            read.addAll(part.players());
        }
        assertEquals(players, read);
        // A short list: one message, the whole of it.
        List<byte[]> one = PositionsMessage.offline(List.of(STEVE_PLAYER));
        assertEquals(1, one.size());
        assertEquals(PositionsMessage.Part.WHOLE, PositionsMessage.read(one.get(0)).part());
    }

    /** Too many mobs: the nearest, and a circle that stops short of the first left out, which is not gone. */
    @Test
    void cutsMobsToTheSize() {
        List<PositionsMessage.Mob> mobs = new ArrayList<>();
        // Listed the farthest first.
        for (int i = 999; i >= 0; i--) {
            mobs.add(new PositionsMessage.Mob(new UUID(0, i), i, 64, 0, 1, OVERWORLD, false, 0));
        }
        int limit = 5_000;
        byte[] message = PositionsMessage.mobs(new PositionsMessage.Mobs(mobs, OVERWORLD, 0, 0, 2048), limit);
        assertTrue(message.length <= limit, message.length + " bytes");
        PositionsMessage.Mobs read = PositionsMessage.read(message).mobs();
        int sent = read.mobs().size();
        assertTrue(sent > 10 && sent < 1000, sent + " mobs");
        for (int i = 0; i < sent; i++) assertEquals(i, read.mobs().get(i).x(), 1e-9);
        assertTrue(read.covers(OVERWORLD, sent - 1, 0));
        assertFalse(read.covers(OVERWORLD, sent, 0));
    }

    @Test
    void choosesTheNearestPlayers() {
        List<PositionsMessage.Player> players = List.of(
                new PositionsMessage.Player("Far", 500, 64, 0, 0f, OVERWORLD, false, false, null),
                new PositionsMessage.Player("Elsewhere", 1, 64, 0, 0f, NETHER, false, false, null),
                new PositionsMessage.Player("Near", 10, 64, 0, 0f, OVERWORLD, false, false, null),
                new PositionsMessage.Player("Middle", 0, 64, 100, 0f, OVERWORLD, false, false, null));
        assertEquals(List.of("Near", "Middle"), names(PlayerLists.nearest(players, OVERWORLD, 0, 0, 2)));
        assertEquals(List.of("Near", "Middle", "Far", "Elsewhere"), names(PlayerLists.nearest(players, OVERWORLD, 0, 0, 9)));
        assertEquals(List.of("Elsewhere"), names(PlayerLists.nearest(players, NETHER, 0, 0, 1)));
        assertEquals(List.of(), PlayerLists.nearest(players, OVERWORLD, 0, 0, 0));
    }

    /** {@code count} players on 2000 bytes of mount each, a block apart. */
    private static List<PositionsMessage.Player> mounted(int count, boolean online) {
        PositionsMessage.Mount mount = new PositionsMessage.Mount("minecraft:horse", "x".repeat(2000), 0, 0, 0, 0f, 0f);
        List<PositionsMessage.Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new PositionsMessage.Player("P" + i, i, 64, 0, 0f, OVERWORLD, online, true, null, mount));
        }
        return players;
    }

    private static List<String> names(List<PositionsMessage.Player> players) {
        return players.stream().map(PositionsMessage.Player::name).toList();
    }

    private static String hex(String text) {
        return HexFormat.of().formatHex(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    void sharesTheSeedWithTheDimensionsAClientCanBuild() {
        PositionsMessage.Seed seed = new PositionsMessage.Seed(-123456789L, List.of(
                new PositionsMessage.SeedGround("minecraft:overworld", "minecraft:overworld", "minecraft:overworld")));
        PositionsMessage.Decoded read = PositionsMessage.read(PositionsMessage.seed(seed));
        assertNotNull(read);
        assertEquals(PositionsMessage.SEED, read.kind());
        assertEquals(seed, read.seed());
        PositionsMessage.Decoded none = PositionsMessage.read(PositionsMessage.seed(PositionsMessage.Seed.NONE));
        assertNotNull(none);
        assertEquals(PositionsMessage.Seed.NONE, none.seed());
    }
}
