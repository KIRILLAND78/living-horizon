package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Impostors a few pixels tall, against the sky, where the smallest levels of the atlas are
 * read: nothing but the figure may show along the top or bottom of its quad (no specks of
 * the tile above or below it in the page). The atlas shows long mobs (horse, sniffer)
 * whole, not cut off at the edge of their tiles.
 */
public final class ImpostorEdgesScenario implements Scenario {
    private static final String[] MOBS = {"husk", "cow", "sheep", "pig", "villager", "chicken", "horse", "wolf",
            "iron_golem", "sniffer"};

    @Override
    public String name() {
        return "impostoredges";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostorDistance = 32;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Within the server's tracking range, which the entity distance halves too.
            Scene.row(world, MOBS, 1, 45, 4);
            Scene.row(world, MOBS, 1, 70, 10);
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                }
            });
            context.waitTicks(80);
            if (context.computeOnClient(minecraft -> ImpostorAtlas.failed())) {
                throw new AssertionError("The impostor bake failed, see the log");
            }
            // Looking up a little, so that the figures stand against the sky, and wide, so
            // that they are a few pixels tall.
            Scene.command(world, "tp @p ~ ~ ~ 0 -8");
            context.runOnClient(minecraft -> minecraft.options.fov().set(110));
            Scene.screenshot(context, this, "impostors");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshotAtlas(context, this);
        }
    }
}
