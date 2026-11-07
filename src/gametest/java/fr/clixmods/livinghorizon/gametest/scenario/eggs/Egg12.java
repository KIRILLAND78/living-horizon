package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Glider;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Easter egg 12, by day: a player gliding with an elytra comes far off, across the view, and
 * moves along; seen again from 44 blocks, then left to finish its way, which ends in a dive
 * under the ground, gone within the time its flight takes.
 */
public final class Egg12 {
    /** Ticks its whole flight takes at most (crossing and dive), and a margin. */
    private static final int LONGEST = 760 + 140;
    /** Ticks its whole flight takes at least. */
    private static final int SHORTEST = 440;

    private Egg12() {
    }

    private static Glider egg() {
        return FarPlayerTracker.get().ambience().glider();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 12 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set noon");
        Scene.command(world, "weather clear");
        // It crosses out to where the loaded chunks end: wait for them, after a teleport this far.
        for (int i = 0; i < 1200 && !context.computeOnClient(Egg12::loadedAhead); i++) context.waitTick();
        context.waitTicks(20);
        Vec3 at = null;
        for (int tries = 0; tries < 4 && at == null; tries++) {
            boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
            if (!on) throw new AssertionError("ee12 did not start");
            at = await(context);
        }
        if (at == null) throw new AssertionError("ee12 found no place");
        Vec3 first = at;
        int started = 0;
        double across = context.computeOnClient(minecraft -> Math.hypot(first.x - minecraft.player.getX(), first.z - minecraft.player.getZ()));
        Scene.log("eggs2 ee12 at " + first + ", " + (int) across + " blocks");
        if (across < 100) throw new AssertionError("ee12 at " + (int) across + " blocks, too near");
        if (first.y < -50) throw new AssertionError("ee12 starts at height " + first.y + ", not over the ground");
        context.runOnClient(minecraft -> {
            List<? extends Entity> figures = egg().figures();
            if (figures.size() != 1 || !(figures.get(0) instanceof RemotePlayer him)) {
                throw new AssertionError("ee12 shows " + figures + ", not one player");
            }
            if (!him.isFallFlying()) throw new AssertionError("ee12's player does not glide");
            if (!him.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                throw new AssertionError("ee12's player wears no elytra: " + him.getItemBySlot(EquipmentSlot.CHEST));
            }
            lookAt(minecraft.player, first.x, first.y, first.z);
            minecraft.options.fov().set(30);
        });

        // Along its crossing.
        context.waitTicks(100);
        started += 100;
        Vec3 later = context.computeOnClient(minecraft -> egg().position());
        if (later == null) throw new AssertionError("ee12 gone after 5 seconds");
        double moved = later.distanceTo(first);
        Scene.log("eggs2 ee12 moved " + (int) moved + " blocks in 100 ticks, now at " + later);
        if (moved < 15) throw new AssertionError("ee12 moved only " + moved + " blocks in 100 ticks");
        context.runOnClient(minecraft -> lookAt(minecraft.player, later.x, later.y, later.z));
        Scene.screenshot(context, scenario, "ee12-far");
        started += 5;

        // From 44 blocks, on the player's side of it, still farther than where it goes.
        Vec3 now = context.computeOnClient(minecraft -> egg().position());
        if (now == null) throw new AssertionError("ee12 gone mid-flight");
        Vec3 near = context.computeOnClient(minecraft -> {
            double dx = minecraft.player.getX() - now.x, dz = minecraft.player.getZ() - now.z, length = Math.hypot(dx, dz);
            return new Vec3(now.x + dx / length * 44, -60, now.z + dz / length * 44);
        });
        Scene.command(world, "tp @a " + near.x + " -60 " + near.z);
        context.waitTicks(3);
        started += 3;
        context.runOnClient(minecraft -> {
            Vec3 here = egg().position();
            if (here == null) throw new AssertionError("ee12 gone with the player 44 blocks away");
            lookAt(minecraft.player, here.x, here.y + 0.3, here.z);
        });
        Scene.screenshot(context, scenario, "ee12-close");
        started += 5;

        // The rest of its way: the dive, under the ground, then gone.
        double lowest = Double.MAX_VALUE;
        Vec3 last = now;
        while (context.computeOnClient(minecraft -> egg().busy())) {
            if (started > LONGEST) throw new AssertionError("ee12 still there after " + started + " ticks");
            Vec3 where = context.computeOnClient(minecraft -> egg().position());
            if (where != null) {
                last = where;
                lowest = Math.min(lowest, where.y);
            }
            context.waitTick();
            started++;
        }
        Scene.log("eggs2 ee12 gone after " + started + " ticks, last at " + last + ", lowest " + lowest);
        if (started < SHORTEST) throw new AssertionError("ee12 gone after " + started + " ticks, too soon");
        if (lowest > -60) throw new AssertionError("ee12 never went under the ground, lowest at " + lowest);
    }

    /** Where it is once its search is done, or null if it found no place. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) return at;
            if (i > 5 && !context.computeOnClient(minecraft -> egg().busy())) return null;
            context.waitTick();
        }
        return null;
    }

    /** The chunks ahead loaded out to 225 blocks, across the player's view. */
    private static boolean loadedAhead(Minecraft minecraft) {
        double x = minecraft.player.getX(), z = minecraft.player.getZ();
        for (int side = -80; side <= 80; side += 40) {
            int bx = Mth.floor(x + side), bz = Mth.floor(z + 225);
            // A chunk not received yet is there, empty to the bottom of the world.
            if (!minecraft.level.hasChunk(bx >> 4, bz >> 4)
                    || minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) <= minecraft.level.getMinY()) return false;
        }
        return true;
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
