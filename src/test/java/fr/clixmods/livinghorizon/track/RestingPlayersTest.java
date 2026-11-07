package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file of who rests where survives a game restart unchanged. */
class RestingPlayersTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path folder;

    @Test
    void roundTrips() throws IOException {
        List<RestingPlayers.Spot> spots = List.of(
                new RestingPlayers.Spot("Steve", UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"),
                        "ewogICJ0aW1lc3RhbXAiIDogMQp9", "c2lnbmF0dXJl", NETHER, -1234.5, 64.0, 8001.9, 270f, 1760100000000L, null,
                        List.of("", "", "", "", "{count:1,id:\"minecraft:iron_chestplate\"}", "")),
                new RestingPlayers.Spot("Alex", null, null, null, "aether:the_aether", 10, 70, -20, 0f, 0,
                        new RestingPlayers.Mount("minecraft:horse", "{Variant:3,Tame:1b}", "17:b:4", 0, 0.85, -0.1,
                                90f, -15f), null),
                new RestingPlayers.Spot("Ghast", null, null, null, OVERWORLD, 0, 120, 0, 0f, 0,
                        new RestingPlayers.Mount("minecraft:happy_ghast", "{}", null, 0, 4, 0, 0f, 0f), null));
        StringWriter out = new StringWriter();
        RestingPlayers.write(spots, out);
        assertTrue(out.toString().startsWith("{\n  \"version\": " + RestingPlayers.VERSION + ","));
        assertEquals(spots, RestingPlayers.read(new StringReader(out.toString())));
    }

    /** Where they were found to rest - the ground under them, a shore, a boat - comes back with them. */
    @Test
    void keepsWhereTheyRest() throws IOException {
        List<RestingPlayers.Spot> spots = List.of(
                new RestingPlayers.Spot("Glider", null, null, null, OVERWORLD, 5.5, 140, -3.5, 0f, 0, null, null,
                        new RestingPlayers.Rest(5.5, 64, -3.5, false)),
                new RestingPlayers.Spot("Rower", null, null, null, OVERWORLD, 30.5, 62.9, 12.5, 90f, 0, null, null,
                        new RestingPlayers.Rest(30.5, 62.888, 12.5, true)),
                new RestingPlayers.Spot("Walker", null, null, null, OVERWORLD, 0, 64, 0, 0f, 0, null, null));
        StringWriter out = new StringWriter();
        RestingPlayers.write(spots, out);
        assertEquals(spots, RestingPlayers.read(new StringReader(out.toString())));
        // A file written before the mod kept it: found again from the blocks.
        assertEquals(null, RestingPlayers.read(new StringReader(
                "{\"version\": 2, \"players\": [{\"name\": \"Alex\", \"dimension\": \"minecraft:overworld\", "
                        + "\"x\": 1, \"y\": 2, \"z\": 3, \"yaw\": 0}]}")).get(0).rest());
    }

    @Test
    void keepsADamagedFileAside() throws IOException {
        Path file = folder.resolve("server.json");
        Files.writeString(file, "[{\"name\": \"Alex\", \"x\": ");
        assertEquals(List.of(), RestingPlayers.read(file));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(folder.resolve("server.json.bad")));
    }

    @Test
    void readsFilesWithoutTheLastSeenTimeOrMount() {
        String older = "[{\"name\": \"Alex\", \"dimension\": 0, \"x\": 10, \"y\": 70, \"z\": -20, \"yaw\": 0}]";
        assertEquals(List.of(new RestingPlayers.Spot("Alex", null, null, null, OVERWORLD, 10, 70, -20, 0f, 0, null, null)),
                RestingPlayers.read(new StringReader(older)));
    }

    /** Layout 1 numbered the dimensions; 3 was any dimension of a mod, which cannot be told apart. */
    @Test
    void namesTheDimensionsOfLayout1() {
        String layout1 = "{\"version\": 1, \"players\": ["
                + "{\"name\": \"Steve\", \"dimension\": 1, \"x\": 1, \"y\": 2, \"z\": 3, \"yaw\": 0},"
                + "{\"name\": \"Alex\", \"dimension\": 2, \"x\": 4, \"y\": 5, \"z\": 6, \"yaw\": 0},"
                + "{\"name\": \"Modded\", \"dimension\": 3, \"x\": 7, \"y\": 8, \"z\": 9, \"yaw\": 0},"
                + "{\"name\": \"Sam\", \"x\": 0, \"y\": 64, \"z\": 0, \"yaw\": 0}]}";
        assertEquals(List.of(
                        new RestingPlayers.Spot("Steve", null, null, null, NETHER, 1, 2, 3, 0f, 0, null, null),
                        new RestingPlayers.Spot("Alex", null, null, null, "minecraft:the_end", 4, 5, 6, 0f, 0, null, null),
                        new RestingPlayers.Spot("Sam", null, null, null, OVERWORLD, 0, 64, 0, 0f, 0, null, null)),
                RestingPlayers.read(new StringReader(layout1)));
    }

    /** Only the nearest are drawn, as many as the settings say, in the camera's dimension. */
    @Test
    void drawsTheNearestOnly() {
        List<RestingPlayers.Spot> spots = List.of(spot("Far", OVERWORLD, 300), spot("Nether", NETHER, 1),
                spot("Near", OVERWORLD, 5), spot("Middle", OVERWORLD, 50));
        assertEquals(List.of("Near", "Middle"), names(RestingPlayers.nearest(spots, OVERWORLD, 0, 0, 2)));
        assertEquals(List.of("Near", "Middle", "Far"),
                names(RestingPlayers.nearest(spots, OVERWORLD, 0, 0, fr.clixmods.livinghorizon.FarConfig.UNLIMITED_OFFLINE)));
        assertEquals(List.of(), RestingPlayers.nearest(spots, OVERWORLD, 0, 0, 0));
        assertEquals(List.of("Nether"), names(RestingPlayers.nearest(spots, NETHER, 0, 0, 256)));
    }

    private static RestingPlayers.Spot spot(String name, String dimension, double x) {
        return new RestingPlayers.Spot(name, null, null, null, dimension, x, 64, 0, 0f, 0, null, null);
    }

    private static List<String> names(List<RestingPlayers.Spot> spots) {
        return spots.stream().map(RestingPlayers.Spot::name).toList();
    }

    @Test
    void skipsBrokenEntries() {
        assertEquals(List.of(), RestingPlayers.read(new StringReader("[null, {\"x\": 1}]")));
        assertEquals(List.of(), RestingPlayers.read(new StringReader("")));
    }
}
