package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Fireworks;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 21, called, whatever today's date, at midnight: over a village made 90 blocks
 * ahead (a bell by a street), rockets go up and burst; it is still going a little over half a
 * minute on, and over by itself within a minute and a half.
 */
public final class Egg21 {
    /** Ticks it goes on at least: rockets go up for fifty seconds. */
    private static final int LASTS = 700;

    private Egg21() {
    }

    private static Fireworks fireworks() {
        return FarPlayerTracker.get().ambience().fireworks();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 21 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "execute at @p run fill ~-20 ~-1 ~75 ~20 ~-1 ~105 minecraft:dirt_path");
        Scene.command(world, "execute at @p run setblock ~ ~ ~90 minecraft:bell");
        // A second village, off to the side: fireworks go up over both.
        Scene.command(world, "execute at @p run setblock ~-125 ~ ~70 minecraft:bell");
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> fireworks().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee21 did not start");
        Vec3 village = await(context);
        int villages = context.computeOnClient(minecraft -> fireworks().villages());
        Scene.log("eggs2 ee21 over " + villages + " villages");
        if (villages != 2) throw new AssertionError("ee21 over " + villages + " villages, not both");
        context.runOnClient(minecraft -> {
            double across = Math.hypot(village.x - minecraft.player.getX(), village.z - minecraft.player.getZ());
            Scene.log("eggs2 ee21 at " + village + ", " + (int) across + " blocks, on " + java.time.LocalDate.now());
            // Over the village, not the spot in front of the player it takes without one.
            if (Math.abs(across - 90) > 15) throw new AssertionError("ee21 at " + (int) across + " blocks, not over the village");
            lookAt(minecraft.player, village.x, village.y + 45, village.z);
            minecraft.options.fov().set(30);
        });
        // The first bursts.
        int sparks = 0;
        int waited = 0;
        for (; waited < 300 && sparks < 3; waited += 5) {
            context.waitTicks(5);
            sparks = context.computeOnClient(minecraft -> fireworks().bursts());
        }
        Scene.log("eggs2 ee21 " + sparks + " bursts after " + waited + " ticks");
        if (sparks < 3) throw new AssertionError("ee21 not three bursts after 15 seconds");
        Scene.screenshot(context, scenario, "ee21-burst");
        context.waitTicks(60);
        Scene.screenshot(context, scenario, "ee21-bursts");
        // Still going a while on.
        context.waitTicks(LASTS - waited - 70);
        if (!context.computeOnClient(minecraft -> fireworks().busy())) throw new AssertionError("ee21 over too soon");
        Scene.screenshot(context, scenario, "ee21-later");
        // Over by itself: the last rockets up, the last sparks fallen.
        boolean busy = true;
        for (int i = 0; i < 40 && busy; i++) {
            context.waitTicks(25);
            busy = context.computeOnClient(minecraft -> fireworks().busy());
        }
        if (busy) throw new AssertionError("ee21 still going after a minute and a half");
        Scene.log("eggs2 ee21 over");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** Where it goes up, once it is found: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> fireworks().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee21 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
