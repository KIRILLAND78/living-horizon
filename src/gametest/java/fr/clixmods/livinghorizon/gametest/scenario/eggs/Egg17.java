package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.SeaCreature;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 17, on a sea the test makes ahead (an ocean biome over one layer of water): its
 * back up, then its tail, then the end of it; called again, gone once the player is near.
 */
public final class Egg17 {
    /** Ticks after it came when its back is well up, its tail hangs, the splash flies; it is over. */
    private static final int BACK = 100, TAIL = 325, SPLASH = 368, OVER = 460;

    private Egg17() {
    }

    private static SeaCreature egg() {
        return FarPlayerTracker.get().ambience().seaCreature();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 17 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        // In strips, each under the game's limit on how many blocks one command changes.
        for (int z = 85; z < 235; z += 20) {
            Scene.command(world, "execute at @p run fillbiome ~-85 -64 ~" + z + " ~85 -57 ~" + (z + 19) + " minecraft:ocean");
        }
        Scene.command(world, "execute at @p run fill ~-80 ~-1 ~90 ~80 ~-1 ~230 minecraft:water");
        context.waitTicks(40);
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee17 did not start");
        Vec3 at = await(context, "ee17");
        context.runOnClient(minecraft -> {
            double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
            Scene.log("eggs2 ee17 at " + at + ", " + (int) across + " blocks");
            if (across < 95) throw new AssertionError("ee17 at " + (int) across + " blocks, too near");
            minecraft.options.fov().set(30);
        });

        until(context, BACK);
        context.runOnClient(minecraft -> {
            Vec3 now = egg().position();
            lookAt(minecraft.player, now.x, now.y + 1, now.z);
        });
        Scene.screenshot(context, scenario, "ee17-back");

        until(context, TAIL);
        context.runOnClient(minecraft -> {
            Vec3 now = egg().position();
            lookAt(minecraft.player, now.x, now.y + 4, now.z);
        });
        Scene.screenshot(context, scenario, "ee17-tail");

        until(context, SPLASH);
        Scene.screenshot(context, scenario, "ee17-splash");

        // Then nothing.
        for (int i = 0; i < OVER - SPLASH && context.computeOnClient(minecraft -> egg().busy()); i++) context.waitTick();
        if (context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee17 still there at its end");
        Scene.log("eggs2 ee17 over");

        // Called again, and the player brought next to it: gone.
        on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee17 did not start again");
        Vec3 again = await(context, "ee17");
        Scene.command(world, "tp @a " + (again.x + 20) + " ~ " + (again.z - 20));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee17 still there with the player near");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** Waits until it has been there {@code age} ticks; it must still be there. */
    private static void until(ClientGameTestContext context, int age) {
        for (int i = 0; i < OVER; i++) {
            int now = context.computeOnClient(minecraft -> egg().age());
            if (now < 0) throw new AssertionError("ee17 gone before " + age + " ticks");
            if (now >= age) return;
            context.waitTick();
        }
        throw new AssertionError("ee17 not at " + age + " ticks in time");
    }

    /** Where it came, once its search is done: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context, String egg) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) {
                drawn(context, at, egg);
                return at;
            }
            context.waitTick();
        }
        throw new AssertionError(egg + " found no place");
    }

    /** On the water where the game draws it: no far world here to draw farther. */
    private static void drawn(ClientGameTestContext context, Vec3 at, String egg) {
        context.runOnClient(minecraft -> {
            double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
            double edge = minecraft.options.getEffectiveRenderDistance() * 16;
            if (across > edge - 16) throw new AssertionError(egg + " at " + (int) across + " blocks, past the "
                    + (int) edge + " drawn");
            int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            if (Math.abs(at.y - ground) > 3) throw new AssertionError(egg + " at height " + at.y + ", the water there at " + ground);
            if (minecraft.level.getFluidState(new net.minecraft.core.BlockPos(Mth.floor(at.x), ground - 1, Mth.floor(at.z))).isEmpty()) {
                throw new AssertionError(egg + " not over water at " + at);
            }
        });
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
