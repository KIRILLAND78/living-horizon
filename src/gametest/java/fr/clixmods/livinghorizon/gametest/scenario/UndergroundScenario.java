package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Cows in a sealed stone room, which has no sky light, and cows on the open ground beside
 * it; then left behind, so that the mod draws their copies. With "Hide underground mobs"
 * off every copy is drawn, on only those in the open. The copies' outlines show through
 * the room's walls, and a cross marks each one left out.
 */
public final class UndergroundScenario implements Scenario {
    private static final int EACH = 4;

    @Override
    public String name() {
        return "underground";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.impostors = false;
            config.outlineCopies = true;
            config.outlineLeftOut = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Set once the world is made: the spawn area at 16 chunks takes too long to prepare here.
            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
            // The room: stone walls, floor and roof, hollow inside, to the left of the view.
            Scene.command(world, "execute at @p run fill ~-14 ~-1 ~38 ~-2 ~3 ~46 stone");
            Scene.command(world, "execute at @p run fill ~-13 ~ ~39 ~-3 ~2 ~45 air");
            for (int i = 0; i < EACH; i++) {
                Scene.summon(world, "cow", -11 + i * 2.5, 42, 90);
                Scene.summon(world, "cow", 4 + i * 2.5, 42, 90);
            }
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);

            int off = shown(context, "off");
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "off");

            Scene.configure(context, config -> config.hideUndergroundMobs = true);
            context.waitTicks(40);
            int on = shown(context, "on");
            Scene.screenshot(context, this, "on");

            if (off != 2 * EACH) throw new AssertionError("Option off: " + off + " copies drawn, not " + 2 * EACH);
            if (on != EACH) throw new AssertionError("Option on: " + on + " copies drawn, not the " + EACH + " in the open");
        }
    }

    /** The copies drawn, logged with whether each one is underground. */
    private static int shown(ClientGameTestContext context, String label) {
        return context.computeOnClient(minecraft -> {
            int underground = 0;
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                if (mob.underground()) underground++;
                Scene.log("underground " + label + ": " + mob.type() + " at " + mob.x() + " " + mob.y() + " " + mob.z()
                        + (mob.underground() ? " underground" : " in the open"));
            }
            int shown = FarPlayerTracker.get().mobs().shown().size();
            Scene.log("underground " + label + ": shown=" + shown + " of them underground=" + underground);
            return shown;
        });
    }
}
