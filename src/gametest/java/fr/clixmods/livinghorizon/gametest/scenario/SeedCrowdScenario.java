package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Thousands of animals of unexplored land at once, as a player sets it up: Mob Render Distance
 * at 4000 blocks, no limit to how many are drawn. Every ten seconds, what choosing the mobs to
 * draw costs a tick and how many copies wait to be built, which must keep coming down.
 */
public final class SeedCrowdScenario implements Scenario {
    @Override
    public String name() {
        return "seedcrowd";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(4));
        Scene.configure(context, config -> {
            config.seedMobs = true;
            config.onlyOverTerrain = false;
            config.mobMaxDistance = FarConfig.MAX_DISTANCE;
            config.maxDistantMobs = FarConfig.UNLIMITED_MOBS;
        });
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("living horizon"))
                .create()) {
            for (int second = 10; second <= 90; second += 10) {
                context.waitTicks(200);
                int at = second;
                Scene.log("seedcrowd " + context.computeOnClient(minecraft -> {
                    MobMemory memory = FarPlayerTracker.get().mobs();
                    MobMemory.Stats stats = memory.stats();
                    int built = stats.built(), waiting = stats.waiting();
                    Profile.Stat choose = Profile.Section.TICK_MOBS_CHOOSE.cpu();
                    Profile.Stat tick = Profile.Section.TICK_MOBS_MOTION.cpu();
                    Profile.Stat foresee = Profile.Section.TICK_MOBS_FORESEE.cpu();
                    Profile.Stat build = Profile.Section.TICK_MOBS_BUILD.cpu();
                    return at + " s: index " + memory.foresight().size() + ", remembered " + memory.size() + ", chosen "
                            + memory.shown().size() + ", built " + built + ", waiting " + waiting + " (reading " + stats.reading() + ", last tick built " + stats.builtLastTick() + " of " + stats.candidatesLastTick() + "); choosing "
                            + Profile.millis(choose.average()) + " ms (worst " + Profile.millis(choose.worst(System.nanoTime()))
                            + "), of which guessed " + Profile.millis(foresee.average()) + " ms (worst "
                            + Profile.millis(foresee.worst(System.nanoTime())) + "), building " + Profile.millis(build.average())
                            + " ms, moving " + Profile.millis(tick.average()) + " ms";
                }));
            }
            // The copies are built as fast as they come: a few hundred waiting at most.
            MobMemory.Stats last = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().stats());
            if (last.waiting() > last.chosen() / 10) {
                throw new AssertionError(last.waiting() + " of " + last.chosen() + " copies still waiting to be built");
            }
        } finally {
            Scene.configure(context, config -> {
                FarConfig defaults = new FarConfig();
                config.mobMaxDistance = defaults.mobMaxDistance;
                config.maxDistantMobs = defaults.maxDistantMobs;
            });
        }
    }
}
