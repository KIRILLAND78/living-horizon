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
import net.minecraft.world.entity.player.Player;

/**
 * Impostors lit like their models, at noon, the sun straight above: a few mobs past the
 * game's entity distance, as models then as impostors, zoomed in. Meant to be run under a
 * shader pack ({@code run.sh lighting --shaders <pack>}), where the impostors were dark;
 * every wait is on a condition, the game being very slow there.
 */
public final class LightingScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "villager", "horse", "pig", "wolf"};

    @Override
    public String name() {
        return "lighting";
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
            Scene.row(world, MOBS, 1, 50, 3.5);
            for (int tries = 0; tries < 60 && context.computeOnClient(minecraft -> mobs(minecraft.level)) < MOBS.length; tries++) {
                context.waitTicks(20);
                context.runOnClient(minecraft -> Scene.log("lighting waiting: client mobs=" + mobs(minecraft.level)
                        + " entities=" + minecraft.level.getEntityCount() + " player at " + minecraft.player.blockPosition()));
            }
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));

            Scene.configure(context, config -> config.impostors = true);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                        ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                    }
                }
            });
            context.waitFor(minecraft -> ImpostorAtlas.sheets() >= MOBS.length || ImpostorAtlas.failed(), 6000);
            if (context.computeOnClient(minecraft -> ImpostorAtlas.failed())) {
                throw new AssertionError("The impostor bake failed, see the log");
            }

            Scene.configure(context, config -> config.impostors = false);
            // Shader packs blend a few frames together: some time to settle.
            context.waitTicks(20);
            Scene.screenshot(context, this, "models");
            Scene.configure(context, config -> config.impostors = true);
            context.waitTicks(20);
            Scene.screenshot(context, this, "impostors");
            context.runOnClient(minecraft ->
                    Scene.log("lighting impostors drawn=" + PolygonStats.impostors() + " models=" + PolygonStats.models()));
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        }
    }

    private static int mobs(net.minecraft.client.multiplayer.ClientLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) count++;
        }
        return count;
    }
}
