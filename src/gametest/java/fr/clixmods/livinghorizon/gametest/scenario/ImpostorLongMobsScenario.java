package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Long mobs, larger than their box, side on, past the impostor distance: model and impostor
 * through a narrow field of view, then their pictures in the impostor screen. They must be
 * whole, muzzle and tail, and as sharp as the model at that distance.
 */
public final class ImpostorLongMobsScenario implements Scenario {
    private static final String[] MOBS = {"horse", "sniffer", "cow", "camel"};

    @Override
    public String name() {
        return "longmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < MOBS.length; i++) Scene.summon(world, MOBS[i], (i - 1.5) * 4, 40, 90);
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity && entity != minecraft.player) {
                        ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                    }
                }
            });
            context.waitTicks(120);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.configure(context, config -> config.impostors = false);
            Scene.screenshot(context, this, "models-zoom");
            Scene.configure(context, config -> config.impostors = true);
            Scene.screenshot(context, this, "impostors-zoom");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            context.setScreen(() -> new ImpostorScreen(null));
            Scene.screenshot(context, this, "screen");
            context.setScreen(() -> null);
        }
    }
}
