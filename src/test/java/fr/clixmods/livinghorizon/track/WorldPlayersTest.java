package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The players a world saved before the mod: read from its playerdata folder, named from usercache.json. */
class WorldPlayersTest {
    private static final String STEVE = "8667ba71-b85a-4004-af54-457a9734eed7";
    private static final String NOBODY = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void readsWherePlayersLoggedOffAndWhatTheyWore(@TempDir Path server) throws Exception {
        Path world = server.resolve("world");
        Path folder = world.resolve("playerdata");
        Files.createDirectories(folder);
        Files.writeString(server.resolve("usercache.json"),
                "[{\"name\":\"Steve\",\"uuid\":\"" + STEVE + "\",\"expiresOn\":\"2030-01-01 00:00:00 +0000\"}]");
        // The helmet both as the game saves it from 1.21.5 (equipment) and before (inventory slot 103).
        write(folder.resolve(STEVE + ".dat"), "{Pos:[10.5d,64.0d,-20.25d],Rotation:[90.0f,0.0f],"
                + "Dimension:\"minecraft:the_nether\",SelectedItemSlot:0,"
                + "Inventory:[{Slot:0b,id:\"minecraft:iron_sword\",count:1,Count:1b},"
                + "{Slot:103b,id:\"minecraft:diamond_helmet\",count:1,Count:1b}],"
                + "equipment:{head:{id:\"minecraft:diamond_helmet\",count:1}}}");
        // A player the cache does not name: left out.
        write(folder.resolve(NOBODY + ".dat"), "{Pos:[0.0d,64.0d,0.0d]}");

        List<PositionsMessage.Player> players = WorldPlayers.read(folder, world);
        assertEquals(1, players.size());
        PositionsMessage.Player steve = players.get(0);
        assertEquals("Steve", steve.name());
        assertEquals(10.5, steve.x(), 1e-9);
        assertEquals(64.0, steve.y(), 1e-9);
        assertEquals(-20.25, steve.z(), 1e-9);
        assertEquals(90f, steve.yaw());
        assertEquals("minecraft:the_nether", steve.dimension());
        assertFalse(steve.online());
        assertTrue(steve.lastSeen() > 0);
        assertNotNull(steve.look());
        List<String> gear = steve.look().gear();
        assertTrue(gear.get(0).contains("iron_sword"), gear.toString());
        assertTrue(gear.get(5).contains("diamond_helmet"), gear.toString());
        assertFalse(gear.get(5).contains("Slot"), gear.toString());
    }

    @Test
    void leavesOutSpectatorsWhenTheServerHidesThem(@TempDir Path server) throws Exception {
        Path world = server.resolve("world");
        Path folder = world.resolve("playerdata");
        Files.createDirectories(folder);
        Files.writeString(server.resolve("usercache.json"),
                "[{\"name\":\"Steve\",\"uuid\":\"" + STEVE + "\",\"expiresOn\":\"2030-01-01 00:00:00 +0000\"}]");
        write(folder.resolve(STEVE + ".dat"), "{Pos:[10.5d,64.0d,-20.25d],playerGameType:3}");
        assertEquals(List.of(), WorldPlayers.read(folder, world, true));
        assertEquals(1, WorldPlayers.read(folder, world, false).size());
    }

    @Test
    void nothingWithoutPlayers(@TempDir Path server) {
        assertEquals(List.of(), WorldPlayers.read(server.resolve("world/playerdata"), server.resolve("world")));
    }

    private static void write(Path file, String snbt) throws Exception {
        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(TagParser.parseCompoundFully(snbt), out);
        }
    }
}
