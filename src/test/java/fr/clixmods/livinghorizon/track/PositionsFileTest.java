package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The server's own file ({@link PositionPublisher#FILE}) keeps a version apart from the messages'. */
class PositionsFileTest {
    private static final UUID HORSE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID PIG = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    void roundTrips() {
        List<PositionsMessage.Player> players = List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, "minecraft:the_end", false, true, null,
                        new PositionsMessage.Mount("minecraft:horse", "{Tame:1b}", 0, 0.85, -0.1, 90f, -15f),
                        new PositionsMessage.Look("dGV4dHVyZXM=", null, List.of("", "minecraft:iron_helmet")),
                        1760100000000L, false),
                new PositionsMessage.Player("Steve", 1, 2, 3, 0f, "aether:the_aether", false, false, null));
        List<PositionsMessage.Mob> mobs = List.of(
                new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, "minecraft:overworld", true, 513,
                        "{SaddleItem:{id:\"minecraft:saddle\"}}"),
                new PositionsMessage.Mob(PIG, 0, 64, 0, 2, "twilightforest:twilight_forest", false, 0));
        byte[] saved = PositionsMessage.saved(players, mobs);
        assertEquals(new PositionsMessage.Format(PositionsMessage.FILE_VERSION, PositionsMessage.FILE_READABLE_FROM),
                PositionsMessage.format(saved));
        PositionsMessage.Saved read = PositionsMessage.readSaved(saved);
        assertNotNull(read);
        assertEquals(players, read.players());
        assertEquals(mobs, read.mobs());
    }

    /**
     * Files saved before the dimensions had their names: version 2, the messages' version then.
     * A player or a mob it put in 3, any dimension of a mod, is left out: the server reads the
     * players again from the world's own save, which names it.
     */
    @Test
    void readsTheFilesOfVersion2() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2);
            // Two players: Steve, online when it was saved, nothing else known; Alex, in a modded dimension.
            out.writeInt(2);
            legacyPlayer(out, "Steve", 1, 1);
            legacyPlayer(out, "Alex", 3, 0);
            // Two mobs: a baby horse in the nether, a pig in a modded dimension.
            out.writeInt(2);
            legacyMob(out, HORSE, 1, 0x10 | 1);
            legacyMob(out, PIG, 2, 3);
        }
        PositionsMessage.Saved read = PositionsMessage.readSaved(bytes.toByteArray());
        assertNotNull(read);
        assertEquals(List.of(new PositionsMessage.Player("Steve", 1, 64, -3, 180f, "minecraft:the_nether", true, false,
                null)), read.players());
        assertEquals(List.of(new PositionsMessage.Mob(HORSE, 1, 64, -3, 1, "minecraft:the_nether", true, 7)),
                read.mobs());
    }

    @Test
    void readsANewerFileAndRefusesOneItCannot() {
        byte[] saved = PositionsMessage.saved(List.of(new PositionsMessage.Player("Steve", 1, 2, 3, 0f,
                "minecraft:overworld", true, false, null)), List.of());
        // A newer version that only added fields: read.
        saved[3] = 9;
        assertNotNull(PositionsMessage.readSaved(saved));
        // One that changed the layout: refused, and said so.
        saved[7] = 9;
        assertNull(PositionsMessage.readSaved(saved));
        assertEquals(new PositionsMessage.Format(9, 9), PositionsMessage.format(saved));
    }

    @Test
    void refusesADamagedFile() {
        byte[] saved = PositionsMessage.saved(List.of(new PositionsMessage.Player("Steve", 1, 2, 3, 0f,
                "minecraft:overworld", true, false, null)), List.of());
        byte[] cut = java.util.Arrays.copyOf(saved, saved.length - 3);
        assertNull(PositionsMessage.readSaved(cut));
        assertNull(PositionsMessage.readSaved(new byte[] {0, 0, 0, 2, 0}));
        assertNull(PositionsMessage.format(new byte[] {0, 0}));
        assertNull(PositionsMessage.readSaved(new byte[] {0, 0, 0, 1}));
    }

    private static void legacyPlayer(DataOutputStream out, String name, int dimension, int flags) throws IOException {
        out.writeUTF(name);
        out.writeDouble(1);
        out.writeDouble(64);
        out.writeDouble(-3);
        out.writeFloat(180f);
        out.writeByte(dimension);
        out.writeByte(flags);
    }

    private static void legacyMob(DataOutputStream out, UUID id, int kind, int flags) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
        out.writeDouble(1);
        out.writeDouble(64);
        out.writeDouble(-3);
        out.writeShort(kind);
        out.writeByte(flags);
        out.writeInt(7);
    }
}
