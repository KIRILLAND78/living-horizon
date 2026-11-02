package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.ServerView;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/**
 * Bobby keeps the chunks the server stops sending, and makes the render distance the client's
 * own (issue #112). Its chunks hold no mobs: the server sends those only within its view
 * distance, and the mod's copies must take over from there, not from the render distance.
 *
 * <p>In a single player world Bobby is on only with its "view distance overwrite", the
 * distance the world's server sends: 4 chunks here, under a render distance of 12. The mobs
 * are met, then left 90 blocks behind, past the 4 chunks but inside Bobby's chunks and the
 * 160 blocks a cow is tracked to. Run with {@code run.sh bobby --with bobby}; without Bobby the
 * two distances are the same, and only that is checked.
 */
public final class BobbyScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "horse"};
    private static final int OVERWRITE = 4;

    @Override
    public String name() {
        return "bobby";
    }

    @Override
    public void run(ClientGameTestContext context) {
        boolean bobby = Platform.isModLoaded("bobby");
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(12));
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
        });
        if (bobby) context.runOnClient(minecraft -> overwrite(OVERWRITE));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.waitTicks(40);
            int[] distances = context.computeOnClient(minecraft -> new int[]{
                    minecraft.options.getEffectiveRenderDistance(), ServerView.chunks(minecraft.options)});
            Scene.log("bobby installed=" + bobby + " render distance=" + distances[0]
                    + " server view distance=" + distances[1]);
            if (!bobby) {
                if (distances[0] != distances[1]) {
                    throw new AssertionError("without Bobby the two distances differ: " + distances[0] + " and " + distances[1]);
                }
                Scene.log("bobby skipped the rest: Bobby is not installed (run.sh bobby --with bobby)");
                return;
            }
            if (distances[1] != OVERWRITE || distances[0] <= distances[1]) {
                throw new AssertionError("Bobby should draw past the server's " + OVERWRITE + " chunks: render distance "
                        + distances[0] + ", server view distance " + distances[1]);
            }

            Scene.row(world, MOBS, 2, 40, 4.0);
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -50 0 0");
            context.waitTicks(300);
            int[] counts = context.computeOnClient(minecraft -> {
                MobMemory mobs = FarPlayerTracker.get().mobs();
                int drawn = 0, tooClose = 0;
                for (MobMemory.Remembered mob : mobs.shown()) {
                    Entity puppet = mob.puppet();
                    if (puppet == null || minecraft.player == null) continue;
                    double across = Math.hypot(puppet.getX() - minecraft.player.getX(), puppet.getZ() - minecraft.player.getZ());
                    boolean kept = minecraft.level != null && minecraft.level.hasChunk(puppet.getBlockX() >> 4, puppet.getBlockZ() >> 4);
                    Scene.log("bobby " + mob.type() + " at " + Math.round(across) + " blocks, near range "
                            + Math.round(mobs.nearRange(mob)) + ", over Bobby's chunk " + kept);
                    if (across < mobs.nearRange(mob)) tooClose++;
                    else drawn++;
                }
                return new int[]{mobs.live().size(), drawn, tooClose};
            });
            Scene.log("bobby still sent=" + counts[0] + " copies drawn=" + counts[1] + " copies held back=" + counts[2]);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "copies");
            if (counts[1] == 0 || counts[2] > 0) {
                throw new AssertionError("the mobs past the server's view distance should be drawn by the mod: "
                        + counts[1] + " drawn, " + counts[2] + " held back as if the server still sent them");
            }
        } finally {
            if (bobby) context.runOnClient(minecraft -> overwrite(0));
        }
    }

    /** Bobby's "view distance overwrite", set in the settings it reads every tick. */
    private static void overwrite(int chunks) {
        try {
            Class<?> type = Class.forName("de.johni0702.minecraft.bobby.Bobby");
            Object instance = type.getMethod("getInstance").invoke(null);
            Object config = type.getMethod("getConfig").invoke(instance);
            Field field = config.getClass().getDeclaredField("viewDistanceOverwrite");
            field.setAccessible(true);
            field.setInt(config, chunks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Bobby's settings are not where this test looks", e);
        }
    }
}
