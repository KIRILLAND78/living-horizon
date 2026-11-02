package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Impostors chosen by their size on screen, the impostor distance out of reach: none with the
 * size off, the small far row as pictures and the big near mobs as models at 10 pixels, every
 * mob a picture at 64. At 70 degrees the 480-pixel window gives a cow 10 pixels at 50 blocks.
 */
public final class ImpostorSizeScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "villager", "chicken", "horse", "wolf", "iron_golem"};

    @Override
    public String name() {
        return "impostorsize";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 2000;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 3, 50, 2.6);
            Scene.row(world, MOBS, 3, 95, 4.5);
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                }
            });
            context.waitTicks(80);

            int[] off = drawn(context, "off");
            Scene.screenshot(context, this, "off");
            Scene.configure(context, config -> config.impostorPixels = 10);
            int[] some = drawn(context, "10px");
            Scene.screenshot(context, this, "10px");
            Scene.configure(context, config -> config.impostorPixels = 64);
            int[] all = drawn(context, "64px");
            Scene.screenshot(context, this, "64px");

            if (off[0] != 0) throw new AssertionError("Impostors with the size off and the distance out of reach: " + off[0]);
            if (some[0] == 0 || some[1] == 0) {
                throw new AssertionError("At 10 pixels, the far mobs should be pictures and the near big ones models");
            }
            if (all[1] != 0 || all[0] <= some[0]) throw new AssertionError("At 64 pixels, every mob should be a picture");
        }
    }

    /** Impostors and models drawn, once the setting has had a few frames. */
    private static int[] drawn(ClientGameTestContext context, String label) {
        context.waitTicks(10);
        return context.computeOnClient(minecraft -> {
            int impostors = PolygonStats.impostors(), models = PolygonStats.models();
            Scene.log("impostorsize " + label + " impostors=" + impostors + " models=" + models);
            return new int[]{impostors, models};
        });
    }
}
