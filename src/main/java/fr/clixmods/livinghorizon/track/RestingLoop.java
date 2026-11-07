package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A player who logged off, or what they ride, away from the camera: no life of its own any
 * more, one of the {@link MobAnimations} played in a loop around where it stands, as
 * remembered mobs play theirs. It starts where its life near the camera left it, never
 * walking back to where its player logged off. The ground there is read once
 * ({@link MobPaths}), again later if some of it was missing; then nothing is decided, where
 * it is and which way it faces come from the animation and the clock. Each starts its loop
 * at its own moment, drawn from its seed.
 *
 * <p>A player with no room for any animation, or whose ground is not read yet, is never a
 * statue ({@code onTheSpot}): it turns on its spot now and then, as a mob does, and sits a
 * while in some of those stances.
 */
final class RestingLoop {
    /** Ticks before a ground read with columns missing is read again. */
    private static final int READ_AGAIN = 600;
    /**
     * Grounds all the loops together start reading per tick, at most: each copies the blocks
     * around it on the game's thread first. Joining a server with many players who logged off
     * would read them all at once.
     */
    static final int READS_PER_TICK = 2;
    /** Grounds still to be read this tick, by every loop: set again by {@link #nextTick()}. Game's thread. */
    private static int readsLeft = READS_PER_TICK;
    /** On the spot: ticks one stance lasts, and how far it turns from the way it faced, at most. */
    private static final int STANCE = 160;
    private static final long TURN = 120;

    private final long seed;
    /** Where it loops around, and the way it faced there. */
    private final double homeX, homeY, homeZ;
    private final float homeYaw;
    /** Reads the ground around and chooses the animation. */
    private final Supplier<CompletableFuture<MobPaths.Choice>> planner;
    /** Blocks a tick it walks at, at most. */
    private final double pace;
    /** With no animation to play, it turns and sits on its spot rather than stand still. */
    private final boolean onTheSpot;

    private MobPaths.@Nullable Choice choice;
    private @Nullable CompletableFuture<MobPaths.Choice> pending;
    private int started, readAgain;

    double x, y, z;
    float body;
    /** Walking this tick, or stopped in a pause the animation grazes in (a seat, for a player). */
    boolean walking, pausing;

    RestingLoop(long seed, double homeX, double homeY, double homeZ, float homeYaw, double pace,
                Supplier<CompletableFuture<MobPaths.Choice>> planner) {
        this(seed, homeX, homeY, homeZ, homeYaw, pace, false, planner);
    }

    RestingLoop(long seed, double homeX, double homeY, double homeZ, float homeYaw, double pace, boolean onTheSpot,
                Supplier<CompletableFuture<MobPaths.Choice>> planner) {
        this.seed = seed;
        this.onTheSpot = onTheSpot;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.pace = pace;
        this.planner = planner;
        x = homeX;
        y = homeY;
        z = homeZ;
        body = homeYaw;
    }

    /** A new tick: the loops may read {@link #READS_PER_TICK} grounds again, the first to ask first. */
    static void nextTick() {
        readsLeft = READS_PER_TICK;
    }

    /**
     * One tick of the loop; how far it moved. {@code free} is for a figure that flies or
     * floats: it keeps the spot's height instead of following the ground.
     */
    double tick(int clock, boolean free) {
        if (pending == null && (choice == null || !choice.complete() && clock >= readAgain) && readsLeft > 0) {
            readsLeft--;
            pending = planner.get();
        }
        if (pending != null && pending.isDone()) {
            MobPaths.Choice read = pending.getNow(null);
            pending = null;
            readAgain = clock + READ_AGAIN;
            if (read != null) {
                if (choice == null || !read.same(choice)) started = clock;
                choice = read;
            }
        }

        // Where the animation has it now.
        double tx = homeX, tz = homeZ;
        float facing = homeYaw;
        boolean moving = false, grazing = false;
        MobAnimations.Animation animation = choice == null ? null : choice.animation();
        int t = clock - started - (int) Math.floorMod(seed, 200L);
        if (animation != null && t >= 0) {
            MobAnimations.Pose pose = animation.sample(t);
            tx += MobAnimations.worldX(pose.x(), pose.z(), choice.angle(), choice.mirror());
            tz += MobAnimations.worldZ(pose.x(), pose.z(), choice.angle(), choice.mirror());
            facing = MobAnimations.yaw(pose.heading(), choice.angle(), choice.mirror());
            moving = pose.walking();
            grazing = pose.grazing();
        } else if (onTheSpot) {
            // Nowhere to walk: a new stance every so often, turned its own way, seated in one of three.
            long stance = Math.floorDiv(clock + Math.floorMod(seed, (long) STANCE), (long) STANCE);
            facing = homeYaw + (Math.floorMod(MobMotion.mix(seed, stance), 2 * TURN + 1) - TURN);
            grazing = Math.floorMod(MobMotion.mix(seed, stance + 7919), 3L) == 0;
        }

        // On it, or catching up with it at a walk when a new animation starts elsewhere.
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double reach = Math.max(pace, MobAnimations.WALK);
        double step = Math.min(d, reach);
        double fromX = x, fromZ = z;
        if (d > 1e-6) {
            x += dx / d * step;
            z += dz / d * step;
        }
        double ground = free || choice == null ? Double.NaN : choice.ground().height(x, z);
        if (Double.isNaN(ground)) ground = homeY;
        y += (ground - y) * 0.4;
        if (Math.abs(ground - y) < 0.01) y = ground;
        double moved = Math.hypot(x - fromX, z - fromZ);
        boolean catching = d > reach;
        float turnTo = catching ? (float) (Mth.atan2(z - fromZ, x - fromX) * Mth.RAD_TO_DEG) - 90f : facing;
        body = Mth.approachDegrees(body, turnTo, moving || catching ? 18f : 4f);
        walking = moving || moved > 0.02;
        pausing = grazing && !walking;
        return moved;
    }

    /** What it plays, for the debug tools. */
    String state() {
        MobAnimations.Animation animation = choice == null ? null : choice.animation();
        if (pending != null) return "reading the ground";
        if (animation != null) return animation.toString();
        return onTheSpot ? "turning on its spot" : "standing";
    }
}
