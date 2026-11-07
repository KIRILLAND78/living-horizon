package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jspecify.annotations.Nullable;

/**
 * A remembered ender dragon flies as the real one does: it soars in wide rounds about where it
 * was last seen, beating its wings, its head first (its yaw points behind it), its neck and
 * tail bending along its flight, and it is never a picture. It is summoned high up, then left
 * 210 blocks behind, past the 160 blocks the server sends it at, and its copy is watched for
 * a few hundred ticks.
 */
public final class DragonScenario implements Scenario {
    @Override
    public String name() {
        return "dragon";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.mobTypes.add("minecraft:ender_dragon");
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.animationMinPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "execute at @p run summon ender_dragon ~ ~40 ~40 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -170 0 0");
            context.waitTicks(250);

            double[] travelled = new double[1], flapped = new double[1], bent = new double[1];
            int[] ticks = new int[2];
            double[][] last = new double[1][];
            for (int tick = 0; tick < 400; tick++) {
                context.runOnClient(minecraft -> {
                    EnderDragon dragon = copy();
                    if (dragon == null) return;
                    double[] now = {dragon.getX(), dragon.getY(), dragon.getZ(), dragon.flapTime};
                    double[] before = last[0];
                    last[0] = now;
                    // How far its neck and tail are turned from its body: the game reads them from its flight.
                    bent[0] = Math.max(bent[0], Math.abs(Mth.wrapDegrees(
                            dragon.flightHistory.get(5).yRot() - dragon.flightHistory.get(10).yRot())));
                    if (before == null) return;
                    double dx = now[0] - before[0], dz = now[2] - before[2], across = Math.hypot(dx, dz);
                    travelled[0] += Math.sqrt(dx * dx + Math.pow(now[1] - before[1], 2) + dz * dz);
                    flapped[0] += now[3] - before[3];
                    if (across < 0.05) return;
                    // Its head the way it flies: the dragon's yaw looks along (sin, -cos).
                    float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
                    double ahead = (Mth.sin(yaw) * dx - Mth.cos(yaw) * dz) / across;
                    ticks[1]++;
                    if (ahead > 0.7) ticks[0]++;
                });
                context.waitTicks(1);
            }
            Scene.log("dragon travelled " + String.format("%.1f", travelled[0]) + " blocks, wings beat "
                    + String.format("%.1f", flapped[0]) + " times, head first " + ticks[0] + " of " + ticks[1]
                    + " ticks, neck and tail bent up to " + String.format("%.1f", bent[0]) + " degrees");
            if (last[0] == null) throw new AssertionError("No copy of the ender dragon was drawn");
            if (travelled[0] < 100) throw new AssertionError("The dragon's copy flew " + travelled[0] + " blocks only");
            if (flapped[0] < 5) throw new AssertionError("The dragon's copy beat its wings " + flapped[0] + " times only");
            if (ticks[0] < ticks[1] * 0.9) throw new AssertionError("The dragon's copy flies tail first");
            if (bent[0] < 1) throw new AssertionError("The dragon's copy never bends along its flight");

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            for (int shot = 0; shot < 3; shot++) {
                Scene.screenshot(context, this, "model-" + shot);
                context.waitTicks(12);
            }
            Scene.configure(context, config -> {
                config.impostors = true;
                config.impostorDistance = 32;
                config.impostorPixels = 0;
            });
            context.waitTicks(80);
            int[] baked = new int[1];
            context.runOnClient(minecraft -> {
                EnderDragon dragon = copy();
                if (dragon == null) throw new AssertionError("The dragon's copy is gone");
                ImpostorKey key = ImpostorKey.of(dragon);
                if (ImpostorAtlas.sheet(key) != null) baked[0]++;
                for (int height = 0; height < ImpostorViews.HEIGHTS.length; height++) {
                    if (ImpostorAtlas.sheet(ImpostorKey.seenFrom(key, height)) != null) baked[0]++;
                }
            });
            Scene.log("dragon pictures baked " + baked[0]);
            if (baked[0] > 0) throw new AssertionError("The dragon was baked into " + baked[0] + " pictures");
            Scene.screenshot(context, this, "impostors-on");
        }
    }

    /** The remembered dragon's copy, null until it is made. */
    private static @Nullable EnderDragon copy() {
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            if (mob.puppet() instanceof EnderDragon dragon) return dragon;
        }
        return null;
    }
}
