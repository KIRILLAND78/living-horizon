package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A player who logged off, or what they ride, away from the camera: no life of its own any
 * more, one of the {@link MobAnimations} played in a loop around the spot where they logged
 * off, as remembered mobs play theirs. The ground around the spot is read once
 * ({@link MobPaths}), again later if some of it was missing; then nothing is decided, where
 * it is and which way it faces come from the animation and the clock. Each starts its loop
 * at its own moment, drawn from its seed.
 *
 * <p>Coming from wherever its life near the camera left it, it walks back to its spot in a
 * straight line, at a stroll, stopping now and then to look around, and starts its loop
 * once there: a player has all the time in the world, unlike a mob that must be on its
 * mark before the real one is sent.
 *
 * <p>A player with no room for any animation, or whose ground is not read yet, is never a
 * statue ({@code onTheSpot}): it turns on its spot now and then, as a mob does, and sits a
 * while in some of those stances.
 */
final class RestingLoop {
    /** Ticks before a ground read with columns missing is read again. */
    private static final int READ_AGAIN = 600;
    /** On its way back: blocks walked between two stops, and ticks a stop lasts at least. */
    private static final double STOP_EVERY = 9;
    private static final int STOP = 40;
    /** On the spot: ticks one stance lasts, and how far it turns from the way it faced, at most. */
    private static final int STANCE = 160;
    private static final long TURN = 120;

    private final long seed;
    /** The spot it loops around, and the way it faced there. */
    private final double homeX, homeY, homeZ;
    private final float homeYaw;
    /** Reads the ground around the spot and chooses the animation. */
    private final Supplier<CompletableFuture<MobPaths.Choice>> planner;
    /** Blocks a tick it walks at, at most. */
    private final double pace;
    /** With no animation to play, it turns and sits on its spot rather than stand still. */
    private final boolean onTheSpot;

    private MobPaths.@Nullable Choice choice;
    private @Nullable CompletableFuture<MobPaths.Choice> pending;
    private int started, readAgain;
    /** Walking back to its spot, the loop not started yet; blocks since the last stop, and ticks still to stand. */
    private boolean returning;
    private double since;
    private int stopped, stops;

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

    /** Takes over from wherever the figure is now. */
    void from(double x, double y, double z, float body) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.body = body;
        returning = Math.hypot(x - homeX, z - homeZ) > 0.5;
        since = 0;
        stopped = 0;
    }

    /**
     * One tick of the loop; how far it moved. {@code free} is for a figure that flies or
     * floats: it keeps the spot's height instead of following the ground.
     */
    double tick(int clock, boolean free) {
        if (pending == null && (choice == null || !choice.complete() && clock >= readAgain)) pending = planner.get();
        if (pending != null && pending.isDone()) {
            MobPaths.Choice read = pending.getNow(null);
            pending = null;
            readAgain = clock + READ_AGAIN;
            if (read != null) {
                if (choice == null || !read.same(choice)) started = clock;
                choice = read;
            }
        }

        if (returning) return walkBack(clock, free);

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

    /**
     * One tick of the walk back to its spot: at its own pace, a stop to look around every
     * {@link #STOP_EVERY} blocks or so. Once there, its loop starts from the beginning.
     */
    private double walkBack(int clock, boolean free) {
        if (stopped > 0) {
            stopped--;
            walking = false;
            pausing = false;
            return 0;
        }
        double dx = homeX - x, dz = homeZ - z, d = Math.hypot(dx, dz);
        double step = Math.min(d, pace);
        if (d > 1e-6) {
            x += dx / d * step;
            z += dz / d * step;
        }
        double ground = free || choice == null ? Double.NaN : choice.ground().height(x, z);
        if (Double.isNaN(ground)) ground = homeY;
        y += (ground - y) * 0.2;
        if (d > 1e-3) body = Mth.approachDegrees(body, (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f, 10f);
        walking = step > 0.01;
        pausing = false;
        since += step;
        if (d - step < 0.05) {
            // Home: the loop starts here, where every animation begins.
            returning = false;
            started = clock;
        } else if (since >= STOP_EVERY + Math.floorMod(MobMotion.mix(seed, stops), 5L)) {
            since = 0;
            stopped = STOP + (int) Math.floorMod(MobMotion.mix(seed, stops + 1000L), 60L);
            stops++;
        }
        return step;
    }

    /** What it plays, for the debug tools. */
    String state() {
        MobAnimations.Animation animation = choice == null ? null : choice.animation();
        if (returning) return stopped > 0 ? "on its way back, looking around" : "on its way back";
        if (pending != null) return "reading the ground";
        if (animation != null) return animation.toString();
        return onTheSpot ? "turning on its spot" : "standing";
    }
}
