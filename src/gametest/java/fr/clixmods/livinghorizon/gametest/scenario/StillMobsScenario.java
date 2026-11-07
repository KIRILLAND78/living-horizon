package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.LivingEntity;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Remembered mobs past *Animation Distance* stand where they are. They used to go on along
 * their animation with their legs at rest, gliding to and fro, and tremble: their tick count
 * stopped, so the game replayed a tick of their idle movements twenty times a second. Cows are left 140 blocks behind,
 * out of the chunks the server moves, so that their copies play their own animation; how far
 * they go is measured animated, still, and animated again, where they must go on without a jump.
 */
public final class StillMobsScenario implements Scenario {
    @Override
    public String name() {
        return "stillmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.animationMinPixels = 0;
            config.animationMaxDistance = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < 6; i++) {
                Scene.command(world, "execute at @p run summon cow ~" + (i - 3) * 6 + " ~ ~40 {NoAI:1b,PersistenceRequired:1b}");
            }
            context.waitTicks(100);
            // Past the 80 blocks cows are sent at, and outside the five chunks the server moves.
            Scene.command(world, "tp @a 0 ~ -100 0 0");
            context.waitTicks(200);
            double[] animated = measure(context, "animated");
            Scene.configure(context, config -> config.animationMaxDistance = 32);
            context.waitTicks(2);
            double[] still = measure(context, "still");
            Scene.screenshot(context, this, "still");
            Scene.configure(context, config -> config.animationMaxDistance = 0);
            double[] again = measure(context, "animated again");
            if (animated[0] < 20) throw new AssertionError("The animated cows hardly moved: " + animated[0] + " blocks");
            if (still[0] > 0.5) throw new AssertionError("Cows past Animation Distance moved " + still[0] + " blocks");
            if (again[0] < 20) throw new AssertionError("The cows did not walk again: " + again[0] + " blocks");
            if (again[1] > 0.3) throw new AssertionError("A cow jumped " + again[1] + " blocks in a tick as it walked again");
            if (still[2] > 0) throw new AssertionError("A still cow's tick count stood still " + still[2] + " times: it trembles");
        }
    }

    /**
     * Over 600 ticks: the blocks the cows covered together, the longest step one made in a tick,
     * and how many times a cow's tick count did not go on.
     */
    private static double[] measure(ClientGameTestContext context, String label) {
        Map<MobMemory.Remembered, double[]> last = new IdentityHashMap<>();
        Map<MobMemory.Remembered, Integer> ticks = new IdentityHashMap<>();
        double[] path = new double[1], longest = new double[1], legs = new double[1], stopped = new double[1];
        for (int tick = 0; tick < 600; tick++) {
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (!(mob.puppet() instanceof LivingEntity puppet)) continue;
                    double[] at = {puppet.getX(), puppet.getZ()};
                    double[] was = last.put(mob, at);
                    legs[0] = Math.max(legs[0], puppet.walkAnimation.speed());
                    Integer count = ticks.put(mob, puppet.tickCount);
                    if (count != null && puppet.tickCount == count) stopped[0]++;
                    if (was == null) continue;
                    double d = Math.hypot(at[0] - was[0], at[1] - was[1]);
                    path[0] += d;
                    longest[0] = Math.max(longest[0], d);
                }
            });
            context.waitTicks(1);
        }
        Scene.log("stillmobs " + label + ": " + last.size() + " cows covered " + String.format("%.1f", path[0])
                + " blocks, at most " + String.format("%.2f", longest[0]) + " a tick, legs " + String.format("%.2f", legs[0])
                + ", tick count stopped " + (int) stopped[0] + " times");
        return new double[]{path[0], longest[0], stopped[0]};
    }
}
