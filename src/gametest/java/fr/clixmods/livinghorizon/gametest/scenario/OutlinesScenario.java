package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.ui.OutlinesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The outlines: remembered copies left behind, the near row drawn as models (green), the far
 * row as impostors (magenta), the copies past the most shown at once left out of the frame
 * (grey crosses), and a cow close by that the game draws itself (white). Screenshots with the
 * outlines off, then on, and the Outlines screen.
 */
public final class OutlinesScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "villager", "horse", "wolf", "iron_golem"};

    @Override
    public String name() {
        return "outlines";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 215;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
            config.maxDistantMobs = 18;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            Scene.row(world, MOBS, 2, 40, 5.0);
            Scene.row(world, MOBS, 2, 70, 5.0);
            context.waitTicks(100);
            // Walk away: the copies take over once the real mobs are no longer sent.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "plain-zoom");
            Scene.summon(world, "cow", 3, 12, 90);
            Scene.configure(context, config -> {
                config.outlineGameMobs = config.outlineLiveMobs = config.outlineCopies = true;
                config.outlinePlayers = config.outlineImpostors = config.outlineLeftOut = true;
            });
            Scene.screenshotFrame(context, this, "outlines-zoom");
            context.runOnClient(minecraft -> Scene.log("outlines models=" + PolygonStats.models()
                    + " impostors=" + PolygonStats.impostors() + " fake=" + DebugMarks.count(Mark.FAKE)
                    + " spare=" + DebugMarks.count(Mark.SPARE) + " waiting=" + DebugMarks.count(Mark.WAITING)));
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshotFrame(context, this, "outlines");
            context.setScreen(() -> new OutlinesScreen(null));
            Scene.screenshot(context, this, "screen");
            context.setScreen(() -> null);
        }
    }
}
