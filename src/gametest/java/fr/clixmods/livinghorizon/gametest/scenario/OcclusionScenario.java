package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Skip Hidden Mobs through the occlusion queries: a row of remembered cows 200 blocks away, a
 * wall in front of half of them, then no wall. The queries' answers are read back
 * a few frames later: half the row hidden behind the wall, none once it is gone.
 */
public final class OcclusionScenario implements Scenario {
    private static final String[] MOBS = {"cow"};
    private static final int COUNT = 12;

    @Override
    public String name() {
        return "occlusion";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = true;
            config.optOcclusionQueries = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            Scene.row(world, MOBS, COUNT, 40, 5.0);
            context.waitTicks(100);
            // Walk away: the copies take over once the real mobs are no longer sent.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            Scene.log("occlusion remembered shown=" + shown);
            if (shown < COUNT) throw new AssertionError("Only " + shown + " of " + COUNT + " cows remembered");

            // Halfway to the cows, its edge half a block from the eye: 1 block from the middle at the
            // cows, between the two middle ones (2.5 blocks out, their boxes 0.75 wide each side).
            Scene.command(world, "execute at @p run fill ~0 ~-1 ~100 ~40 ~12 ~100 stone");
            context.waitTicks(40);
            int hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            Scene.log("occlusion with the wall: " + hidden + " hidden, " + context.computeOnClient(minecraft -> OcclusionQueries.state()));
            Scene.screenshot(context, this, "wall");
            if (hidden != COUNT / 2) throw new AssertionError("Behind the wall " + hidden + " cows hidden, not " + COUNT / 2);

            Scene.command(world, "execute at @p run fill ~0 ~-1 ~100 ~40 ~12 ~100 air");
            context.waitTicks(40);
            hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            Scene.log("occlusion without the wall: " + hidden + " hidden, " + context.computeOnClient(minecraft -> OcclusionQueries.state()));
            Scene.screenshot(context, this, "open");
            if (hidden != 0) throw new AssertionError("With nothing in front " + hidden + " cows hidden");
        }
    }
}
