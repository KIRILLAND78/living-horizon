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
 * The same mobs as full models, then as impostors: two rows of real mobs past the game's
 * own entity distance (the mod draws them), at 50 and 95 blocks, in every turn, and the
 * baked atlas. The model and impostor screenshots should show the same figures, turned
 * the same way, the same size and colour.
 */
public final class ImpostorsScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "villager", "chicken", "horse", "wolf", "iron_golem"};

    @Override
    public String name() {
        return "impostors";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = false;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 3, 50, 2.6);
            Scene.row(world, MOBS, 3, 95, 4.5);
            context.waitTicks(60);

            // Asked for at once rather than waiting for them to be needed.
            Scene.configure(context, config -> config.impostors = true);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                }
            });
            context.waitTicks(80);
            boolean failed = context.computeOnClient(minecraft -> {
                Scene.log("impostors baked=" + ImpostorAtlas.sheets() + " waiting=" + ImpostorAtlas.waiting()
                        + " from disk=" + ImpostorAtlas.loads() + " failed=" + ImpostorAtlas.failed());
                return ImpostorAtlas.failed();
            });
            if (failed) throw new AssertionError("The impostor bake failed, see the log");

            Scene.configure(context, config -> config.impostors = false);
            Scene.screenshot(context, this, "models");
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "models-zoom");
            Scene.configure(context, config -> config.impostors = true);
            Scene.screenshot(context, this, "impostors-zoom");
            context.runOnClient(minecraft ->
                    Scene.log("impostors drawn=" + PolygonStats.impostors() + " models=" + PolygonStats.models()));
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshot(context, this, "impostors");
            Scene.screenshotAtlas(context, this);
        }
    }
}
