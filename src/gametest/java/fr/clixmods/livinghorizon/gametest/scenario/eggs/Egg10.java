package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Statue;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 10, called: the figure stands far ahead; each time the player turns away and back
 * it has come closer, never while looked at; near, the next look away takes it for good. Then
 * called again, and gone as the player walks up to it. The test turns the player itself.
 */
public final class Egg10 {
    private Egg10() {
    }

    private static Statue statue() {
        return FarPlayerTracker.get().ambience().statue();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a 10000 -60 0 0 0");
        context.waitTicks(100);
        if (!context.computeOnClient(minecraft -> statue().summon(minecraft.level, minecraft.player, true))) {
            throw new AssertionError("ee10 did not start");
        }
        Vec3 at = await(context);
        double first = context.computeOnClient(minecraft -> across(minecraft.player, at));
        Scene.log("eggs2 ee10 at " + at + ", " + (int) first + " blocks");
        if (first < 50) throw new AssertionError("ee10 at " + (int) first + " blocks, too near");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y + 1.7, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(20);
        Scene.screenshot(context, scenario, "ee10-far");

        double last = first;
        boolean gone = false, nearShot = false;
        for (int round = 0; round < 60 && !gone; round++) {
            // Away: behind the player, off the screen.
            context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 180f));
            context.waitTicks(10);
            if (!context.computeOnClient(minecraft -> statue().busy())) {
                gone = true;
                break;
            }
            Vec3 now = context.computeOnClient(minecraft -> statue().position());
            context.runOnClient(minecraft -> lookAt(minecraft.player, now.x, now.y + 1.7, now.z));
            context.waitTicks(15);
            Vec3 still = context.computeOnClient(minecraft -> statue().position());
            if (still == null || still.distanceTo(now) > 1e-6) {
                throw new AssertionError("ee10 moved while looked at, round " + round);
            }
            double across = context.computeOnClient(minecraft -> across(minecraft.player, now));
            int moves = context.computeOnClient(minecraft -> statue().moves());
            Scene.log("eggs2 ee10 round " + round + ": " + String.format("%.1f", across) + " blocks, " + moves + " moves");
            if (round == 0) {
                if (across > first - 3) throw new AssertionError("ee10 did not come closer: " + first + " then " + across);
                Scene.screenshot(context, scenario, "ee10-closer");
            }
            if (!nearShot && across < 40) {
                nearShot = true;
                Scene.screenshot(context, scenario, "ee10-near");
            }
            if (across > last + 0.5) throw new AssertionError("ee10 went farther: " + last + " then " + across);
            last = across;
        }
        if (!gone) throw new AssertionError("ee10 still there after 60 looks away, " + (int) last + " blocks off");
        if (last > 25) throw new AssertionError("ee10 gone while still " + (int) last + " blocks off");

        // Called again, then walked up to.
        if (!context.computeOnClient(minecraft -> statue().summon(minecraft.level, minecraft.player, true))) {
            throw new AssertionError("ee10 did not start again");
        }
        Vec3 again = await(context);
        Scene.command(world, "tp @a " + (again.x + 5) + " ~ " + (again.z - 5));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> statue().busy())) {
            throw new AssertionError("ee10 still there with the player next to it");
        }
    }

    /** Where it stands, once its spot is read: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> statue().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee10 found no place");
    }

    private static double across(LocalPlayer player, Vec3 at) {
        return Math.hypot(at.x - player.getX(), at.z - player.getZ());
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
