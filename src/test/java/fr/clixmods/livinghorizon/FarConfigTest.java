package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import fr.clixmods.livinghorizon.track.MobKinds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each step of {@link FarConfig#migrate}: what an older file had becomes what the settings mean now. */
class FarConfigTest {
    private static final Gson GSON = new Gson();
    private static final String MANNEQUIN = "minecraft:mannequin";

    /** Settings read from a file with this text, brought up to date. */
    private static FarConfig migrated(String json) {
        FarConfig config = GSON.fromJson(json, FarConfig.class);
        FarConfig.migrate(config);
        return config;
    }

    @Test
    void leavesTheDefaultsAsTheyAre() {
        FarConfig migrated = new FarConfig();
        FarConfig.migrate(migrated);
        FarConfig fresh = new FarConfig();
        fresh.version = migrated.version;
        assertEquals(GSON.toJson(fresh), GSON.toJson(migrated));
        assertEquals(13, migrated.version);
    }

    @Test
    void leavesAnUpToDateFileAsItIs() {
        String json = "{\"version\": 11, \"minApparentPixels\": 14, \"birdSize\": 2, \"depthOcclusion\": false,"
                + " \"maxDistantMobs\": 200, \"mobMaxDistance\": 0, \"impostors\": false, \"easterEggs\": false,"
                + " \"mobTypes\": [\"minecraft:cow\"], \"stillMobTypes\": []}";
        FarConfig config = migrated(json);
        assertEquals(14, config.minApparentPixels);
        assertEquals(2, config.birdSize);
        assertFalse(config.depthOcclusion);
        assertEquals(200, config.maxDistantMobs);
        assertEquals(0, config.mobMaxDistance);
        assertFalse(config.impostors);
        assertFalse(config.easterEggs);
        assertEquals(java.util.List.of("minecraft:cow"), config.mobTypes);
        assertEquals(java.util.List.of(), config.stillMobTypes);
    }

    @Test
    void version2ShrinksGiantPlayers() {
        assertEquals(0, migrated("{\"version\": 1, \"minApparentPixels\": 14}").minApparentPixels);
        assertEquals(14, migrated("{\"version\": 2, \"minApparentPixels\": 14}").minApparentPixels);
    }

    @Test
    void version3AddsTheBoats() {
        FarConfig config = migrated("{\"version\": 2, \"mobTypes\": [\"minecraft:cow\"]}");
        for (String type : MobKinds.TYPES) {
            if (MobKinds.isBoat(type)) assertTrue(config.mobTypes.contains(type), type);
        }
        assertFalse(migrated("{\"version\": 3, \"mobTypes\": [\"minecraft:cow\"]}").mobTypes.contains("minecraft:oak_boat"));
    }

    @Test
    void version4ShrinksTheBirds() {
        assertEquals(1, migrated("{\"version\": 3, \"birdSize\": 2}").birdSize);
        assertEquals(3, migrated("{\"version\": 3, \"birdSize\": 3}").birdSize);
        assertEquals(2, migrated("{\"version\": 4, \"birdSize\": 2}").birdSize);
    }

    @Test
    void version5AddsTheMannequins() {
        FarConfig config = migrated("{\"version\": 4, \"mobTypes\": [], \"stillMobTypes\": []}");
        assertTrue(config.mobTypes.contains(MANNEQUIN));
        assertTrue(config.stillMobTypes.contains(MANNEQUIN));
        assertFalse(migrated("{\"version\": 5, \"mobTypes\": []}").mobTypes.contains(MANNEQUIN));
    }

    @Test
    void version6TurnsTheDepthBackOn() {
        String off = "\"depthOcclusion\": false, \"hideOccludedMobs\": false, \"optOcclusionQueries\": false}";
        FarConfig old = migrated("{\"version\": 5, " + off);
        assertTrue(old.depthOcclusion && old.hideOccludedMobs && old.optOcclusionQueries);
        FarConfig kept = migrated("{\"version\": 6, " + off);
        assertFalse(kept.depthOcclusion || kept.hideOccludedMobs || kept.optOcclusionQueries);
    }

    @Test
    void version7TakesTheNewDefaults() {
        String old = "\"maxDistantMobs\": 200, \"mobMaxDistance\": 0, \"impostorDistance\": 200, \"impostors\": false}";
        FarConfig config = migrated("{\"version\": 6, " + old);
        assertEquals(512, config.maxDistantMobs);
        assertEquals(512, config.mobMaxDistance);
        assertEquals(128, config.impostorDistance);
        assertTrue(config.impostors);
        FarConfig chosen = migrated("{\"version\": 6, \"maxDistantMobs\": 300, \"mobMaxDistance\": 100}");
        assertEquals(300, chosen.maxDistantMobs);
        assertEquals(100, chosen.mobMaxDistance);
        FarConfig kept = migrated("{\"version\": 7, " + old);
        assertEquals(200, kept.maxDistantMobs);
        assertFalse(kept.impostors);
    }

    @Test
    void version13KeepsNoLimitPastTheOldTop() {
        FarConfig unlimited = migrated("{\"version\": 12, \"maxDistantMobs\": 1000, \"maxDistantEntities\": 1000}");
        assertEquals(FarConfig.UNLIMITED_MOBS, unlimited.maxDistantMobs);
        assertEquals(FarConfig.UNLIMITED_MOBS, unlimited.maxDistantEntities);
        assertEquals(9000, unlimited.most(true, 9000));
        FarConfig chosen = migrated("{\"version\": 12, \"maxDistantMobs\": 999}");
        assertEquals(999, chosen.maxDistantMobs);
        assertEquals(1000, migrated("{\"version\": 13, \"maxDistantMobs\": 1000}").maxDistantMobs);
        assertEquals(FarConfig.UNLIMITED_OFFLINE, migrated("{\"version\": 12, \"maxOfflinePlayers\": 1000}").maxOfflinePlayers);
        // A number typed past the slider is a limit, not no limit.
        FarConfig typed = migrated("{\"version\": 13, \"maxDistantMobs\": 6000}");
        assertEquals(6000, typed.most(true, 9000));
    }

    @Test
    void version13GivesLimitsToWhatWasNotChosenByHand() {
        // The old High and Ultra presets go to the new ones, with limits.
        FarConfig high = migrated("{\"version\": 12, \"impostorDistance\": 256, \"maxDistantMobs\": 1000,"
                + " \"mobMaxDistance\": 1024, \"birdDensity\": 200, \"birdMaxDistance\": 1500}");
        assertEquals(QualityPreset.HIGH, QualityPreset.of(high));
        FarConfig ultra = migrated("{\"version\": 12, \"impostorDistance\": 512, \"maxDistantMobs\": 1000,"
                + " \"mobMaxDistance\": 0, \"birdDensity\": 400, \"birdMaxDistance\": 3000}");
        assertEquals(QualityPreset.ULTRA, QualityPreset.of(ultra));
        // The distances whose default was no limit.
        FarConfig distances = migrated("{\"version\": 12, \"playerMaxDistance\": 0, \"entityMaxDistance\": 0,"
                + " \"animationMaxDistance\": 0}");
        assertEquals(FarConfig.MAX_DISTANCE, distances.playerMaxDistance);
        assertEquals(FarConfig.MAX_DISTANCE, distances.entityMaxDistance);
        assertEquals(FarConfig.MAX_DISTANCE, distances.animationMaxDistance);
        // No limit set by hand, outside a preset, is kept.
        FarConfig chosen = migrated("{\"version\": 12, \"maxDistantMobs\": 1000, \"mobMaxDistance\": 0}");
        assertEquals(FarConfig.UNLIMITED_MOBS, chosen.maxDistantMobs);
        assertEquals(0, chosen.mobMaxDistance);
        assertEquals(1200, migrated("{\"version\": 12, \"playerMaxDistance\": 1200}").playerMaxDistance);
    }

    @Test
    void version8SplitsTheOutlines() {
        FarConfig glow = migrated("{\"version\": 7, \"glowOutline\": true}");
        assertTrue(glow.outlineLiveMobs && glow.outlineCopies && glow.outlinePlayers && glow.outlineImpostors);
        assertFalse(glow.outlineLeftOut);
        FarConfig debug = migrated("{\"version\": 7, \"debugOutlines\": true}");
        assertTrue(debug.outlineCopies && debug.outlineImpostors && debug.outlineLeftOut);
        assertFalse(debug.outlineLiveMobs || debug.outlinePlayers);
        FarConfig none = migrated("{\"version\": 8, \"glowOutline\": true}");
        assertFalse(none.outlineLiveMobs);
    }

    @Test
    void version9HidesThoseWhoWereHidden() {
        assertFalse(migrated("{\"version\": 8, \"offlinePose\": \"hidden\"}").offlinePlayers);
        assertTrue(migrated("{\"version\": 6, \"offlinePose\": \"sleep\"}").offlinePlayers);
        assertTrue(migrated("{\"version\": 9, \"offlinePose\": \"hidden\"}").offlinePlayers);
    }

    @Test
    void version10KeepsTheOldEasterEggSwitch() {
        assertFalse(migrated("{\"version\": 9, \"ufo\": false}").easterEggs);
        assertTrue(migrated("{\"version\": 9, \"ufo\": true}").easterEggs);
        assertTrue(migrated("{\"version\": 10, \"ufo\": false}").easterEggs);
    }

    @Test
    void version11OffersEveryOtherMob() {
        FarConfig config = migrated("{\"version\": 10, \"mobTypes\": [\"minecraft:cow\"],"
                + " \"knownModdedMobs\": [\"othermod:zombie\"]}");
        // The old defaults keep the player's choice; the game's monsters are left to be offered.
        assertTrue(config.knownMobTypes.contains("minecraft:horse"));
        assertTrue(config.knownMobTypes.contains("othermod:zombie"));
        assertFalse(config.knownMobTypes.contains("minecraft:enderman"));
        assertEquals(java.util.List.of("minecraft:cow"), config.mobTypes);
        assertFalse(migrated("{\"version\": 11, \"knownMobTypes\": []}").knownMobTypes.contains("minecraft:horse"));
    }

    @Test
    void version12SplitsTheSimplifiedFigures() {
        // The one switch off stays off for mobs and for the other entities alike.
        assertFalse(migrated("{\"version\": 11, \"impostors\": false}").impostorEntities);
        assertTrue(migrated("{\"version\": 11, \"impostors\": true}").impostorEntities);
        assertTrue(migrated("{\"version\": 12, \"impostors\": false}").impostorEntities);
    }

    @Test
    void fillsInWhatTheFileLeftOut() {
        FarConfig config = migrated("{\"version\": 11, \"mobTypes\": null, \"hiddenBirds\": null, \"birdStyle\": null,"
                + " \"knownMobTypes\": null, \"knownVehicles\": null, \"seatedVehicles\": null, \"stillMobTypes\": null}");
        assertTrue(config.mobTypes.contains("minecraft:horse"));
        assertTrue(config.stillMobTypes.contains(MANNEQUIN));
        assertEquals(java.util.List.of(), config.hiddenBirds);
        assertEquals("3d", config.birdStyle);
        assertTrue(config.knownMobTypes.contains("minecraft:horse"));
        assertEquals(java.util.List.of(), config.knownVehicles);
        assertEquals(java.util.List.of(), config.seatedVehicles);
    }

    @Test
    void clampsTheMemoryWrittenByHand() {
        FarConfig config = migrated("{\"version\": 10, \"impostorMobMemory\": 1, \"impostorPlayerMemory\": 999999}");
        assertEquals(FarConfig.MIN_MOB_MEMORY, config.impostorMobMemory);
        assertEquals(FarConfig.MAX_PLAYER_MEMORY, config.impostorPlayerMemory);
    }

    @Test
    void readsTheOldNames() {
        FarConfig config = migrated("{\"version\": 10, \"voxyOcclusion\": false, \"optHiZ\": false}");
        assertFalse(config.depthOcclusion);
        assertFalse(config.optOcclusionQueries);
    }
}
