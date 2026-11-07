package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The easter eggs' figures up close, to judge how they look: easter egg 9's robes, standing and
 * crouched, by day and by night; easter egg 6's walker from the front, three quarters in a
 * stride, and the side. Screenshots only.
 */
public final class EggFiguresScenario implements Scenario {
    @Override
    public String name() {
        return "eggfigures";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.easterEggs = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "time set noon");
            Scene.command(world, "weather clear");
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                minecraft.player.setYRot(0f);
                minecraft.player.setXRot(10f);
                FarPlayerTracker.get().ambience().sect().showcase(minecraft.level, minecraft.player.getX(),
                        minecraft.player.getY(), minecraft.player.getZ() + 5, new String[]{"sect/veil", "sect/mask", "sect/grin"});
            });
            context.waitTicks(10);
            Scene.screenshot(context, this, "sect-robes");
            Scene.command(world, "time set midnight");
            context.waitTicks(10);
            Scene.screenshot(context, this, "sect-robes-night");
            Scene.command(world, "time set noon");
            // The switch off and on again: the robes go.
            Scene.configure(context, config -> config.easterEggs = false);
            context.waitTicks(2);
            Scene.configure(context, config -> config.easterEggs = true);
            context.waitTicks(5);
            float[][] views = {{180f, 0f}, {135f, 22f}, {90f, 0f}};
            String[] names = {"front", "quarter", "side"};
            for (int v = 0; v < views.length; v++) {
                float yaw = views[v][0], swing = views[v][1];
                context.runOnClient(minecraft -> {
                    minecraft.player.setYRot(0f);
                    minecraft.player.setXRot(8f);
                    FarPlayerTracker.get().ambience().bigfoot().showcase(minecraft.level, minecraft.player.getX(),
                            minecraft.player.getY(), minecraft.player.getZ() + 5.5, yaw, swing);
                });
                context.waitTicks(5);
                Scene.screenshot(context, this, "bigfoot-" + names[v]);
            }
        }
    }
}
