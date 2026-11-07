package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The settings file of every released version, as it wrote it the first time the game started
 * ({@code src/test/oldconfig/<version>.json}, its defaults as Gson saved them): each still reads, is
 * brought up to date, and keeps what its player chose. A new release adds its own file.
 */
class OldConfigTest {
    /** The tests run from {@code versions/<node>}. */
    private static final Path OLD = Path.of("..", "..", "src", "test", "oldconfig");
    private static final Gson GSON = new Gson();

    private record Old(String release, JsonObject json) {
    }

    private static List<Old> files() throws IOException {
        List<Old> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(OLD)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                files.add(new Old(name.substring(0, name.length() - ".json".length()),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
            }
        }
        return files;
    }

    private static FarConfig migrated(JsonElement json) {
        FarConfig config = GSON.fromJson(json, FarConfig.class);
        FarConfig.migrate(config);
        return config;
    }

    private static int current() {
        FarConfig fresh = new FarConfig();
        FarConfig.migrate(fresh);
        return fresh.version;
    }

    @Test
    void hasAFileForEveryRelease() throws IOException {
        List<String> releases = files().stream().map(Old::release).toList();
        for (String release : List.of("0.2.0", "0.6.0", "0.7.0", "0.12.1", "0.13.0", "0.14.0")) {
            assertTrue(releases.contains(release), release);
        }
        for (Old old : files()) {
            assertTrue(old.json().get("version").getAsInt() < current(), old.release());
        }
    }

    @Test
    void everyOldFileReadsAndComesUpToDate() throws IOException {
        for (Old old : files()) {
            FarConfig config;
            try {
                config = migrated(old.json());
            } catch (RuntimeException e) {
                fail(old.release() + " no longer reads: " + e);
                return;
            }
            String release = old.release();
            assertEquals(current(), config.version, release);
            assertNotNull(config.mobTypes, release);
            assertNotNull(config.stillMobTypes, release);
            assertNotNull(config.hiddenMobs, release);
            assertNotNull(config.hiddenBirds, release);
            assertNotNull(config.knownMobTypes, release);
            assertNotNull(config.knownVehicles, release);
            assertNotNull(config.seatedVehicles, release);
            // Its defaults: nothing was switched off, so nothing is hidden, and nothing waits to be sorted.
            assertEquals(List.of(), config.hiddenMobs, release);
            assertFalse(config.sortUnsure(Map.of()), release);
            for (JsonElement type : old.json().getAsJsonArray("mobTypes")) {
                assertTrue(config.showsMob(type.getAsString(), false), release + ": " + type);
            }
            // Saved and read again: the same settings, nothing migrated twice.
            String saved = GSON.toJson(config);
            assertEquals(saved, GSON.toJson(migrated(JsonParser.parseString(saved))), release);
        }
    }

    @Test
    void everyOldFileKeepsWhatItsPlayerChose() throws IOException {
        for (Old old : files()) {
            String release = old.release();
            JsonObject json = old.json().deepCopy();
            // The player switched the pig and the horse off, and met three mobs of other mods: a
            // deer left on, a Pokémon switched off, and a monster that may have been off by default.
            JsonArray shown = new JsonArray();
            for (JsonElement type : json.getAsJsonArray("mobTypes")) {
                String id = type.getAsString();
                if (!id.equals("minecraft:pig") && !id.equals("minecraft:horse")) shown.add(id);
            }
            shown.add("othermod:deer");
            json.add("mobTypes", shown);
            JsonArray met = new JsonArray();
            met.add("othermod:deer");
            met.add("cobblemon:pokemon");
            met.add("othermod:zombie");
            // Named knownModdedMobs up to 0.12, knownMobTypes from 0.13, where it also lists the game's kinds.
            if (json.has("knownMobTypes")) {
                json.getAsJsonArray("knownMobTypes").addAll(met);
            } else {
                json.add("knownModdedMobs", met);
            }

            FarConfig config = migrated(json);
            // Saved before the game has started, then sorted once it knows which kind is a monster.
            config = migrated(JsonParser.parseString(GSON.toJson(config)));
            assertTrue(config.sortUnsure(Map.of("othermod:deer", false, "cobblemon:pokemon", false,
                    "othermod:zombie", true)), release);

            assertFalse(config.showsMob("minecraft:pig", false), release);
            assertFalse(config.showsMob("minecraft:horse", false), release);
            assertTrue(config.hiddenMobs.contains("minecraft:pig"), release);
            assertTrue(config.hiddenMobs.contains("minecraft:horse"), release);
            assertFalse(config.showsMob("cobblemon:pokemon", true), release);
            assertTrue(config.hiddenMobs.contains("cobblemon:pokemon"), release);
            assertTrue(config.showsMob("othermod:deer", false), release);
            assertTrue(config.showsMob("othermod:zombie", false), release);
            assertTrue(config.showsMob("minecraft:cow", false), release);
            assertEquals(3, config.hiddenMobs.size(), release + ": " + config.hiddenMobs);
        }
    }
}
