package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.BirdShape;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 18, called: a flock in the shape of a Creeper's face crosses the sky in front of
 * the player, a bird on each of the face's twenty dark pixels, and is gone at the other side.
 */
public final class Egg18 {
    /** Ticks its crossing takes at most: twice 0.85 of 240 blocks at 0.6 a tick, and a margin. */
    private static final int CROSSING = 700 + 100;

    private Egg18() {
    }

    private static BirdShape egg() {
        return FarPlayerTracker.get().ambience().birdShape();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 18 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set noon");
        Scene.command(world, "weather clear");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee18 did not start");
        Vec3 start = null;
        for (int i = 0; i < 100 && start == null; i++) {
            start = context.computeOnClient(minecraft -> egg().position());
            if (start == null) context.waitTick();
        }
        if (start == null) throw new AssertionError("ee18 found no way");
        Vec3 from = start;
        int birds = context.computeOnClient(minecraft -> egg().birds());
        Scene.log("eggs2 ee18 from " + from + ", " + birds + " birds");
        if (birds != 20) throw new AssertionError("ee18 has " + birds + " birds, not the face's twenty");

        // Halfway across, in front of the player: the face, flying.
        context.waitTicks(300);
        Vec3 now = context.computeOnClient(minecraft -> {
            Vec3 at = egg().position();
            if (at == null) throw new AssertionError("ee18 gone halfway");
            lookAt(minecraft.player, at.x, at.y, at.z);
            minecraft.options.fov().set(30);
            return at;
        });
        double moved = Math.hypot(now.x - from.x, now.z - from.z);
        Scene.log("eggs2 ee18 flew " + (int) moved + " blocks in 15 seconds, now at " + now);
        if (moved < 150) throw new AssertionError("ee18 flew only " + moved + " blocks in 15 seconds");
        Scene.screenshot(context, scenario, "ee18-face");
        context.waitTicks(20);
        context.runOnClient(minecraft -> {
            Vec3 at = egg().position();
            if (at != null) lookAt(minecraft.player, at.x, at.y, at.z);
        });
        Scene.screenshot(context, scenario, "ee18-flying");

        // Gone at the far side.
        boolean busy = true;
        int waited = 0;
        for (; waited < CROSSING && busy; waited += 20) {
            context.waitTicks(20);
            busy = context.computeOnClient(minecraft -> egg().busy());
        }
        if (busy) throw new AssertionError("ee18 still crossing after " + (320 + CROSSING) + " ticks");
        Scene.log("eggs2 ee18 gone " + (320 + waited) + " ticks after it came");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
