package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.Links;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Distant mobs in front of Voxy's terrain under a shader pack that shades past the render
 * distance from Voxy's own depth (Photon): a row of mobs 150 blocks away, a wall behind them,
 * the render distance cut to 5 chunks once Voxy has the wall. Models, then impostors, with
 * our figures written into Voxy's depth and without, at three times of the day.
 *
 * <p>Needs a graphics card (Voxy does not run on llvmpipe, nor Photon): run it on a real
 * machine with Sodium, Iris, Voxy in {@code -Plh.mods} and Photon in {@code -Plh.shaderpack}.
 */
public final class PhotonVoxyScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "villager", "horse", "pig", "iron_golem"};

    @Override
    public String name() {
        return "photonvoxy";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(12);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.impostorDistance = 100;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A wall behind the mobs, with a ledge whose shadow falls on it behind them. Voxy takes
            // a chunk in as the client receives it: built while the player is away, then met.
            Scene.command(world, "tp @a 0 -60 -1200 0 0");
            context.waitTicks(100);
            Scene.command(world, "forceload add -64 150 64 180");
            context.waitTicks(40);
            Scene.command(world, "fill -60 -60 170 60 -25 172 stone");
            Scene.command(world, "fill -60 -38 160 60 -35 169 stone");
            Scene.command(world, "forceload remove all");
            Scene.command(world, "tp @a 0 -60 0 0 0");
            context.waitTicks(200);
            Scene.row(world, MOBS, 2, 150, 4.0);
            context.waitTicks(200);
            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(5));
            context.waitTicks(200);
            context.runOnClient(minecraft -> {
                minecraft.options.fov().set(30);
                Scene.log("photonvoxy depth: " + FarDepth.state());
                for (Links.Report link : Links.report()) Scene.log("photonvoxy link " + link.mod() + ": " + link.status());
                Scene.log("photonvoxy shown=" + FarPlayerTracker.get().mobs().shown().size()
                        + " live=" + FarPlayerTracker.get().mobs().live().size());
            });
            for (String time : new String[]{"noon", "2000", "10000"}) {
                Scene.command(world, "time set " + time);
                for (boolean impostors : new boolean[]{false, true}) {
                    Scene.configure(context, config -> config.impostors = impostors);
                    if (impostors) {
                        context.waitFor(minecraft -> ImpostorAtlas.sheets() >= MOBS.length || ImpostorAtlas.failed(), 2000);
                    }
                    for (boolean written : new boolean[]{true, false}) {
                        Scene.configure(context, config -> config.optVoxyShadows = written);
                        // Shader packs blend a few frames together: some time to settle.
                        context.waitTicks(30);
                        String name = time + (impostors ? "-impostors" : "-models") + (written ? "" : "-unwritten");
                        Scene.screenshot(context, this, name);
                        context.runOnClient(minecraft -> Scene.log("photonvoxy " + name + " impostors="
                                + PolygonStats.impostors() + " models=" + PolygonStats.models()
                                + " depth: " + FarDepth.state()));
                    }
                }
            }
            Scene.configure(context, config -> config.optVoxyShadows = true);
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        }
    }
}
