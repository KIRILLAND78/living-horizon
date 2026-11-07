package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Distant mobs are drawn only over drawn terrain ({@code onlyOverTerrain}). Copies 200 blocks
 * away are drawn with a render distance of 16 chunks, where the game draws their chunks; left
 * out once it is lowered to 8, where nothing is drawn under them (no Voxy or Distant Horizons
 * here); and drawn again there with the setting off.
 */
public final class OverVoidScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "horse"};

    @Override
    public String name() {
        return "void";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.debugBoxes = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            Scene.row(world, MOBS, 2, 40, 5.0);
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            Scene.screenshot(context, this, "within");
            expect(context, "within the render distance", true);

            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(8));
            context.waitTicks(60);
            Scene.screenshot(context, this, "past");
            expect(context, "past the render distance", false);

            Scene.configure(context, config -> config.onlyOverTerrain = false);
            context.waitTicks(20);
            Scene.screenshot(context, this, "off");
            expect(context, "with the setting off", true);
        }
    }

    /** The copies are drawn, or all left out over the void. */
    private static void expect(ClientGameTestContext context, String when, boolean drawn) {
        int[] counts = context.computeOnClient(minecraft ->
                new int[]{DebugMarks.count(Mark.FAKE), DebugMarks.count(Mark.VOID)});
        Scene.log("void " + when + ": copies drawn " + counts[0] + ", over the void " + counts[1]);
        if (drawn && (counts[0] == 0 || counts[1] > 0)) {
            throw new AssertionError("Copies not drawn " + when + ": " + counts[0] + " drawn, " + counts[1] + " over the void");
        }
        if (!drawn && (counts[0] > 0 || counts[1] == 0)) {
            throw new AssertionError("Copies drawn over the void " + when + ": " + counts[0] + " drawn, " + counts[1] + " left out");
        }
    }
}
