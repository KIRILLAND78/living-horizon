package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Watcher;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 11, called: the figure stands far ahead with its spyglass raised, seen from where
 * it came and from 60 blocks. Then the player scopes at it: with a real spyglass held to the
 * eye (the use key held down) where the game lets the test do it, otherwise faked through
 * {@link Watcher#scoping(Boolean)}; looked at, it stands where it is, lowers its spyglass a
 * while and raises it again, and is gone once the spyglass turns away from it. Called again:
 * gone once the player stops scoping, and gone as they come near.
 */
public final class Egg11 {
    private Egg11() {
    }

    private static Watcher watcher() {
        return FarPlayerTracker.get().ambience().watcher();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        try {
            parts(context, world, scenario);
        } finally {
            context.runOnClient(minecraft -> {
                minecraft.options.keyUse.setDown(false);
                watcher().scoping(null);
            });
        }
    }

    private static void parts(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a 11000 -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "item replace entity @p weapon.mainhand with minecraft:spyglass");
        context.waitTicks(5);
        Vec3 at = summon(context);
        double first = context.computeOnClient(minecraft -> across(minecraft.player, at));
        Scene.log("eggs2 ee11 at " + at + ", " + (int) first + " blocks");
        if (first < 90) throw new AssertionError("ee11 at " + (int) first + " blocks, too near");
        boolean raised = context.computeOnClient(minecraft -> {
            LivingEntity him = (LivingEntity) watcher().figures().get(0);
            return him.isUsingItem() && him.getUseItemRemainingTicks() > 0;
        });
        Scene.log("eggs2 ee11 spyglass raised: " + raised);
        if (!raised) throw new AssertionError("ee11 does not hold its spyglass up");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y + 1.5, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(10);
        Scene.screenshot(context, scenario, "ee11-far");

        // Closer, still farther than where it goes.
        Vec3 here = context.computeOnClient(minecraft -> {
            Vec3 from = minecraft.player.position().subtract(at);
            Vec3 flat = new Vec3(from.x, 0, from.z).normalize();
            return at.add(flat.scale(60));
        });
        Scene.command(world, "tp @a " + here.x + " ~ " + here.z);
        context.waitTicks(10);
        if (!context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 gone at 60 blocks");
        context.runOnClient(minecraft -> lookAt(minecraft.player, at.x, at.y + 1.5, at.z));
        context.waitTicks(3);
        Scene.screenshot(context, scenario, "ee11-close");

        // Through the spyglass: it never moves, lowers its own a while and raises it again.
        context.runOnClient(minecraft -> minecraft.options.keyUse.setDown(true));
        context.waitTicks(10);
        boolean real = context.computeOnClient(minecraft -> minecraft.player.isScoping());
        if (!real) {
            context.runOnClient(minecraft -> {
                minecraft.options.keyUse.setDown(false);
                watcher().scoping(true);
            });
        }
        Scene.log("eggs2 ee11 scoping " + (real ? "with the spyglass" : "faked"));
        context.runOnClient(minecraft -> lookAt(minecraft.player, at.x, at.y + 1.5, at.z));
        Scene.screenshot(context, scenario, "ee11-scope");
        Vec3 from = context.computeOnClient(minecraft -> watcher().position());
        boolean lowered = false, raisedAgain = false;
        double moved = 0;
        for (int i = 0; i < 400 && !raisedAgain; i++) {
            if (!context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 gone while looked at, after " + i + " ticks");
            Vec3 now = context.computeOnClient(minecraft -> watcher().position());
            moved = Math.max(moved, now.distanceTo(from));
            boolean up = context.computeOnClient(minecraft -> watcher().raised());
            if (!up && !lowered) {
                lowered = true;
                Scene.screenshot(context, scenario, "ee11-lowered");
            } else if (up && lowered) {
                raisedAgain = true;
            }
            context.waitTick();
        }
        Scene.log("eggs2 ee11 looked at: moved " + String.format("%.2f", moved) + " blocks, lowered " + lowered + ", raised again " + raisedAgain);
        if (moved > 0.01) throw new AssertionError("ee11 moved " + moved + " blocks while looked at");
        if (!lowered) throw new AssertionError("ee11 never lowered its spyglass");
        if (!raisedAgain) throw new AssertionError("ee11 never raised its spyglass again");

        // The spyglass turned away from it: gone at once.
        context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 90f));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 still there once looked away from");
        context.runOnClient(minecraft -> {
            minecraft.options.keyUse.setDown(false);
            watcher().scoping(null);
        });
        context.waitTicks(10);

        // Called while the player scopes, then the spyglass lowered: gone.
        context.runOnClient(minecraft -> watcher().scoping(true));
        summon(context);
        context.waitTicks(5);
        if (!context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 gone while scoped at");
        context.runOnClient(minecraft -> watcher().scoping(false));
        context.waitTicks(3);
        if (context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 still there once the spyglass is lowered");
        context.runOnClient(minecraft -> watcher().scoping(null));

        // Called again, then walked up to.
        Vec3 again = summon(context);
        Scene.command(world, "tp @a " + (again.x + 20) + " ~ " + (again.z - 20));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> watcher().busy())) throw new AssertionError("ee11 still there with the player near");
    }

    /** Calls it and waits for it to stand there: a few seconds at most. */
    private static Vec3 summon(ClientGameTestContext context) {
        if (!context.computeOnClient(minecraft -> watcher().summon(minecraft.level, minecraft.player, true))) {
            throw new AssertionError("ee11 did not start");
        }
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> watcher().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee11 found no place");
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
