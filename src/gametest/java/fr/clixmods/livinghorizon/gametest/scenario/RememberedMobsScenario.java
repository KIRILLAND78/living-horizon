package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

/**
 * Mobs the server no longer sends: seen up close, then left behind 150 blocks away, so the
 * mod draws its remembered copies of them - as models, then as impostors.
 *
 * <p>The server still sends mobs 150 blocks away, and without Voxy the game's fog hides
 * everything past its render distance: the scene is 200 blocks deep, with a render
 * distance of 16 chunks.
 */
public final class RememberedMobsScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "villager", "horse", "wolf", "iron_golem"};

    @Override
    public String name() {
        return "remembered";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 100;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            Scene.row(world, MOBS, 2, 40, 5.0);
            context.waitTicks(100);
            // Walk away: the copies take over once the real mobs are no longer sent.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> {
                Scene.log("remembered still sent by the server: " + FarPlayerTracker.get().mobs().live().size());
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Entity puppet = mob.puppet();
                    Scene.log("remembered " + mob.type() + " at " + (puppet == null ? "nowhere" : puppet.position()));
                }
                Scene.log("remembered shown=" + FarPlayerTracker.get().mobs().shown().size()
                        + " impostors drawn=" + PolygonStats.impostors() + " sheets=" + ImpostorAtlas.sheets());
            });

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "impostors-zoom");
            Scene.configure(context, config -> config.impostors = false);
            Scene.screenshot(context, this, "models-zoom");
        }
    }
}
