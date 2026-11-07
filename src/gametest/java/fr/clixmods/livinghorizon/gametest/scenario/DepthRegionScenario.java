package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.Occlusion;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.Arrays;

/**
 * The far terrain's depth is worked on in a rectangle round the distant figures only
 * ({@code optDepthRegion}). Remembered mobs 200 blocks away, half of them behind a wall:
 *
 * <ul>
 *   <li>the rectangle is a band, far smaller than the screen, and every figure and every box the
 *       occlusion queries test lies inside it (one outside would show through the far terrain);</li>
 *   <li>the queries, tested against the copy of the depth that is only up to date inside it,
 *       hide the same mobs as with the whole screen;</li>
 *   <li>with the setting off, or the depth view on, the whole texture is worked on.</li>
 * </ul>
 *
 * <p>Voxy and Distant Horizons cannot run here: the merge into the game's depth and its undoing
 * are not drawn, only the copies and the queries they share with it.
 */
public final class DepthRegionScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "horse", "villager", "wolf"};
    /** Where the player watches from, 200 blocks north of the row. */
    private static final int WATCH = -160;

    @Override
    public String name() {
        return "depthregion";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 2, 40, 5.0);
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ " + WATCH + " 0 0");
            context.waitTicks(40);
            // Ten blocks ahead, a wall hiding the east half of the row: built once its chunks are loaded.
            Scene.command(world, "fill 0 -60 " + (WATCH + 10) + " 60 -50 " + (WATCH + 10) + " stone");
            context.waitTicks(260);

            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            int[] on = report(context, "on");
            int hiddenOn = context.computeOnClient(minecraft -> Occlusion.hiddenCount());
            Scene.screenshot(context, this, "region");
            if (on[6] == 0) throw new AssertionError("depthregion: no figure handed over, the scene is wrong");
            if (on[7] > 0) throw new AssertionError("depthregion: " + on[7] + " figures reach out of the rectangle");
            if (on[2] <= 0 || on[3] <= 0 || (long) on[2] * on[3] * 4 > (long) on[4] * on[5]) {
                throw new AssertionError("depthregion: the rectangle " + Arrays.toString(on)
                        + " is not a small part of the screen");
            }
            if (hiddenOn <= 0 || hiddenOn >= shown) {
                throw new AssertionError("depthregion: " + hiddenOn + " of " + shown
                        + " mobs hidden, the wall should hide about half");
            }

            Scene.configure(context, config -> config.optDepthRegion = false);
            int[] off = report(context, "off");
            int hiddenOff = context.computeOnClient(minecraft -> Occlusion.hiddenCount());
            whole(off, "with the setting off");
            Scene.log("hidden behind the wall: " + hiddenOn + " in the rectangle, " + hiddenOff + " over the whole screen");
            if (hiddenOn != hiddenOff) {
                throw new AssertionError("depthregion: " + hiddenOn + " mobs hidden in the rectangle, "
                        + hiddenOff + " over the whole screen");
            }

            Scene.configure(context, config -> {
                config.optDepthRegion = true;
                config.debugDepthView = 1;
            });
            whole(report(context, "depth view"), "with the depth view on");
        }
    }

    /** The last frame's rectangle, once a few frames have settled. */
    private static int[] report(ClientGameTestContext context, String what) {
        context.waitTicks(40);
        int[] report = context.computeOnClient(minecraft -> FarDepth.regionReport());
        Scene.log("region " + what + ": " + report[2] + "x" + report[3] + " at " + report[0] + "," + report[1]
                + " of " + report[4] + "x" + report[5] + ", " + report[6] + " spheres, " + report[7] + " outside");
        return report;
    }

    private static void whole(int[] report, String when) {
        if (report[0] != 0 || report[1] != 0 || report[2] != report[4] || report[3] != report[5]) {
            throw new AssertionError("depthregion: not the whole texture " + when + ": " + Arrays.toString(report));
        }
    }
}
