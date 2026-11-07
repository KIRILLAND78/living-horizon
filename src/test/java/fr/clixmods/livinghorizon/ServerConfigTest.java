package fr.clixmods.livinghorizon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's settings: written the first time, filled in, read again once changed, never lost to a typo. */
class ServerConfigTest {
    @TempDir
    Path folder;

    @Test
    void writesTheDefaultsTheFirstTime() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        assertTrue(Files.isRegularFile(file));
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(written.get("sharePlayers").getAsBoolean());
        assertEquals(0, written.get("playerRange").getAsInt());
        assertTrue(written.get("hideSpectators").getAsBoolean());
        assertTrue(written.get("hideInvisible").getAsBoolean());
        assertTrue(written.get("shareOfflinePlayers").getAsBoolean());
        assertTrue(written.get("shareMobs").getAsBoolean());
        assertTrue(written.get("seedMobs").getAsBoolean());
        assertEquals(512, written.get("seedMobRange").getAsInt());
        assertFalse(written.get("shareSeed").getAsBoolean());
        assertFalse(watched.refresh());
    }

    @Test
    void fillsInWhatTheFileLeavesOutAndTidiesIt() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        Files.writeString(file, "{\"sharePlayers\": false, \"playerRange\": -5, \"hiddenPlayers\": [\" Notch \", \"\"]}");
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        ServerConfig config = watched.get();
        assertFalse(config.sharePlayers);
        assertEquals(0, config.playerRange);
        assertEquals(List.of("Notch"), config.hiddenPlayers);
        assertEquals(Set.of("notch"), config.hiddenNames());
        assertTrue(config.shareMobs);
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(written.has("shareMobs"));
        assertFalse(written.get("sharePlayers").getAsBoolean());
    }

    @Test
    void keepsTheRangeOfTheSeedsMobsInBounds() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        Files.writeString(file, "{\"seedMobRange\": 99999}");
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        assertEquals(ServerConfig.MAX_SEED_MOB_RANGE, watched.get().seedMobRange);
        Files.writeString(file, "{\"seedMobRange\": -3}");
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        watched.refresh();
        assertEquals(0, watched.get().seedMobRange);
    }

    @Test
    void readsTheFileAgainOnceChanged() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        assertTrue(watched.get().shareMobs);
        String text = Files.readString(file).replace("\"shareMobs\": true", "\"shareMobs\": false");
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        assertTrue(watched.refresh());
        assertFalse(watched.get().shareMobs);
    }

    @Test
    void keepsTheSettingsWhenAnEditDoesNotRead() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        Files.writeString(file, "{\"sharePlayers\": false}");
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        Files.writeString(file, "{\"sharePlayers\": tru");
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        assertFalse(watched.refresh());
        assertFalse(watched.get().sharePlayers);
        // The half-made edit is left alone, for its owner to finish.
        assertEquals("{\"sharePlayers\": tru", Files.readString(file));
    }

    @Test
    void keepsAsideAFileThatDoesNotReadAtStart() throws Exception {
        Path file = folder.resolve(ServerConfig.FILE);
        Files.writeString(file, "{\"playerRange\": \"far\"}");
        ServerConfig.Watched watched = new ServerConfig.Watched(file);
        watched.refresh();
        assertTrue(Files.isRegularFile(folder.resolve(ServerConfig.FILE + ".bad")));
        assertEquals(0, watched.get().playerRange);
        assertTrue(Files.isRegularFile(file));
    }
}
