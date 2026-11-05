package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/**
 * Plays a remembered mob's animation in a loop: the one {@link MobPaths} chose from the
 * blocks around it, out of {@link MobAnimations}. Nothing is decided here; where the mob
 * is, which way it faces, whether it walks or grazes all come from the animation and the
 * clock. Every mob starts its loop at its own moment, drawn from its UUID, so a herd
 * does not move as one.
 *
 * <p>The one thing added to the animation: the mob never jumps. When where it should be
 * changes at once - the server's shared position moved it, or the real mob is sent again and the copy
 * hands over to it - it walks there in a straight line instead. And as the player comes
 * near enough for the real mob to be sent, the copy is called back to the spot where the
 * real one was last seen, so that the hand-over has little left to walk.
 *
 * <p>A mob the single player server still moves ({@link ServerMobs}) plays no animation: it
 * walks where the server has it, at the server's pace, and faces the way the real one faces.
 *
 * <p>Walks, or hops: a slime, a magma cube or a rabbit covers the same way in hops, as its
 * kind does ({@link MobGait}), and waits on the ground between two. A phantom or a ghast
 * flies instead, along its {@link MobFlight}, facing and tilted the way it goes.
 */
final class MobMotion {
    /** Degrees a body turns in a tick, at most. */
    private static final float TURN = 18f;
    /** No room to walk: a slow turn on the spot every so many ticks. */
    private static final int TURN_ROUND = 300;
    /** Blocks a tick a flyer covers at most catching up with its flight, and at least. */
    private static final double FLY_FAST = 1.2, FLY_SLOW = 0.7;
    /** Degrees a flyer tilts at most, nose up or down. */
    private static final float TILT = 35f;

    private final long seed;
    private final @Nullable MobFlight flight;
    /** Where its flight has it at this tick and the next, from its spot: scratch, on the game's thread. */
    private final double[] flyNow = new double[3], flyNext = new double[3];
    private double x, y, z;
    private float body, head, pitch;
    private boolean placed;
    private MobPaths.@Nullable Choice playing;
    private int started;
    /** Ticks spent still ({@code tiny}): its animation's clock stops meanwhile, and goes on from there after. */
    private int held;
    /** Still and nothing of it changed this tick: until something moves it, {@link #rest} may stand for a tick. */
    private boolean settled;
    private int following;
    /** Where the server last had a mob it moves, and how fast to walk to reach it by the next answer. */
    private double aimX = Double.NaN, aimZ = Double.NaN, pace;
    private @Nullable MobGait gait;
    /** Ticks since it left the ground in a hop, -1 on the ground. */
    private int air = -1;
    /** Ticks still to wait before the next hop, and hops made so far. */
    private int rest, hops;
    /** Blocks a tick in this hop, and above the ground at this tick. */
    private double hopPace, lift;
    private boolean tookOff, landed, climbing;

    /** {@code flight}: how it flies, null for a mob that walks, hops or swims. */
    MobMotion(long seed, @Nullable MobFlight flight) {
        this.seed = seed;
        this.flight = flight;
    }

    /** Where the puppet is drawn: where the animation took it, not where it was remembered. */
    boolean placed() {
        return placed;
    }

    /** Still, where its animation had it, with nothing left to walk or turn: see {@link #rest}. */
    boolean settled() {
        return settled;
    }

    /**
     * A tick of a {@link #settled} mob, in place of {@link #tick}: it stays as it is, and its
     * clocks go on. Its idle movements are played at tickCount plus the frame's part of a tick,
     * and its legs at their speed: left alone, both would run a tick's worth and snap back.
     */
    void rest(LivingEntity puppet) {
        held++;
        puppet.setOldPosAndRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        Puppets.walk(puppet, 0f, 0.4f, puppet.isBaby() ? 3f : 1f);
        puppet.tickCount++;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    /**
     * Straight to where the server has a mob it moves, unseen: a copy not drawn for a while
     * (frozen, with {@link fr.clixmods.livinghorizon.FarConfig#optFreezeHidden}) is where it
     * should be when it shows again, instead of running there.
     */
    void jump(LivingEntity puppet, MobMemory.Remembered mob) {
        settled = false;
        place(mob);
        air = -1;
        lift = 0;
        apply(puppet, 0);
        puppet.setOldPosAndRot();
        puppet.yBodyRotO = body;
        puppet.yHeadRotO = head;
    }

    private void place(MobMemory.Remembered mob) {
        x = mob.x;
        y = mob.y;
        z = mob.z;
        body = mob.yaw;
        head = Float.isNaN(mob.head) ? mob.yaw : mob.head;
        placed = true;
    }

    /**
     * Held where it stands and turned by something else this tick (easter egg 15, {@link
     * fr.clixmods.livinghorizon.ambient.Herd}): its animation goes on from there once let go,
     * walking and turning to where its loop has got to rather than jumping.
     */
    void held(LivingEntity puppet, MobMemory.Remembered mob) {
        if (!placed) place(mob);
        x = puppet.getX();
        y = puppet.getY();
        z = puppet.getZ();
        body = puppet.yBodyRot;
        head = puppet.yHeadRot;
        pitch = puppet.getXRot();
        air = -1;
        lift = 0;
        climbing = false;
    }

    /**
     * How much a mob is called back to its spot, from 0 (far: it plays its animation) to 1
     * (about to be handed over to the real one: it stands where the real one was last seen).
     * Full from a few blocks inside the distance the server starts sending it, nil forty beyond.
     */
    static float home(double distance, double handover) {
        double t = Mth.clamp((handover + 48 - distance) / 40, 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }

    /** How far the head is turned from the body at a tick: a glance, a pause, another glance. */
    static float glance(long seed, int tick) {
        int k = Math.floorDiv(tick, 70);
        float u = Mth.clamp((tick - k * 70) / 20f, 0f, 1f);
        u = u * u * (3 - 2 * u);
        return Mth.lerp(u, look(seed, k - 1), look(seed, k));
    }

    private static float look(long seed, int k) {
        return (Math.floorMod(mix(seed, k), 101L) - 50) * 0.9f;
    }

    /** A well-mixed number from a mob's seed and a counter: the same inputs, the same number. */
    static long mix(long seed, long counter) {
        long z = seed + counter * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * One tick of its animation. {@code choice} is null until the ground around it is
     * read; {@code roam} is off for the mobs shown standing still; {@code tiny} means too
     * small or too far for its animation to be played: it stands where its animation had it
     * and goes on from there once played again (a flyer flies on); {@code home} is how much it
     * is called back to its spot ({@link #home}).
     */
    void tick(LivingEntity puppet, MobMemory.Remembered mob, MobPaths.@Nullable Choice choice, int clock, boolean roam,
              boolean tiny, float home) {
        if (!placed) place(mob);
        if (gait == null) gait = MobGait.of(puppet);
        following = 0;
        settled = false;
        if (flight != null && roam && !mob.simulated) {
            fly(puppet, mob, flight, clock, home);
            return;
        }
        double wasX = x, wasY = y + lift, wasZ = z;
        float wasBody = body, wasHead = head, wasPitch = pitch;
        // Still: it stands where its animation had it, rather than sliding along it with its legs at rest.
        if (tiny) held++;
        int own = clock - held;
        if (choice != playing) {
            // Read again and the same as it plays already: it goes on rather than starting over.
            if (choice == null || playing == null || !choice.same(playing)) started = own;
            playing = choice;
        }
        int offset = (int) Math.floorMod(seed >> 17, 400L);

        // Where the animation has it now.
        double tx = mob.x, tz = mob.z;
        float facing = mob.yaw;
        boolean walking = false, grazing = false;
        boolean followed = mob.simulated;
        MobAnimations.Animation animation = roam && choice != null && !followed ? choice.animation() : null;
        int t = own - started - (int) Math.floorMod(seed, 200L);
        if (animation != null && t >= 0) {
            MobAnimations.Pose pose = animation.sample(t);
            tx += MobAnimations.worldX(pose.x(), pose.z(), choice.angle(), choice.mirror());
            tz += MobAnimations.worldZ(pose.x(), pose.z(), choice.angle(), choice.mirror());
            facing = MobAnimations.yaw(pose.heading(), choice.angle(), choice.mirror());
            walking = pose.walking();
            grazing = pose.grazing() && puppet instanceof Animal;
        } else if (roam && choice != null && !followed) {
            // Nowhere to walk: a slow turn on the spot now and then, a different one each time.
            int round = Math.floorDiv(own + offset, TURN_ROUND);
            facing = mob.yaw + (Math.floorMod(mix(seed, round), 5L) - 2) * 35f;
        }
        if (home > 0) {
            // Close to the hand-over: back towards the spot where the real one was last seen.
            tx = Mth.lerp(home, tx, mob.x);
            tz = Mth.lerp(home, tz, mob.z);
            if (home >= 1) facing = mob.yaw;
            grazing &= home < 0.01f;
        }

        // On it, or on the way to it in a straight line when it is not one tick away.
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double reach;
        if (followed) {
            // At the pace that brings it where the server has it by the next answer.
            if (tx != aimX || tz != aimZ) {
                aimX = tx;
                aimZ = tz;
                pace = d / ServerMobs.EVERY;
            }
            reach = Mth.clamp(pace, 0.02, 0.5);
        } else {
            reach = Math.max(0.2, Math.min(0.5, d / 40));
        }
        double step = gait.hops() ? hop(d, reach) : Math.min(d, reach);
        double fromX = x, fromZ = z;
        // A hopping mob is always a hop or two behind where it should be: only farther is it catching up.
        boolean catching = d > (gait.hops() ? reach + 2 * gait.length : reach);
        if (d > 1e-6) {
            x += dx / d * step;
            z += dz / d * step;
        }
        double ground = choice == null || followed ? Double.NaN : choice.ground().height(x, z);
        if (Double.isNaN(ground) || catching) ground = d > 1e-6 ? y + (mob.y - y) * Math.min(1, step / d) : mob.y;
        climbing = Math.abs(ground - y) > 0.05;
        // Up a step in a few ticks, like a hop; down the same.
        y += (ground - y) * 0.4;
        if (Math.abs(ground - y) < 0.01) y = ground;
        land(d - step);
        walking |= air >= 0;

        // Walking back to its spot, or on its way somewhere: facing the way it goes. Followed
        // on the server: facing the way the real one faces, which turns where it walks.
        boolean going = !followed && (catching || air >= 0 || (home > 0 && Math.hypot(x - fromX, z - fromZ) > 0.02));
        float turnTo = going ? (float) (Mth.atan2(z - fromZ, x - fromX) * Mth.RAD_TO_DEG) - 90f : facing;
        body = Mth.approachDegrees(body, turnTo, walking || going || followed ? TURN : 4f);
        if (followed) {
            head = Mth.approachDegrees(head, Float.isNaN(mob.head) ? body : mob.head, TURN);
        } else {
            float look = grazing ? 0 : glance(seed, own + offset) * (walking || catching ? 0.3f : 1f) * (1 - home);
            head = Mth.approachDegrees(head, body + look, 8f);
        }
        pitch = Mth.lerp(0.15f, pitch, grazing ? 40f : 0f);
        apply(puppet, Math.hypot(x - fromX, z - fromZ));
        settled = tiny && !followed && air < 0 && x == wasX && y + lift == wasY && z == wasZ
                && body == wasBody && head == wasHead && Math.abs(pitch - wasPitch) < 0.01f;
    }

    /**
     * One tick of a flyer's flight, {@link MobFlight}: on its path, or on the way to it
     * through the air when it is not there yet (just remembered, or called back to its spot).
     * It faces where it flies and tilts as it climbs or dives.
     */
    private void fly(LivingEntity puppet, MobMemory.Remembered mob, MobFlight flight, int clock, float home) {
        playing = null;
        // Each one at its own point of its flight.
        double t = clock + Math.floorMod(seed >> 17, 4000L);
        flight.at(seed, t, flyNow);
        flight.at(seed, t + 1, flyNext);
        double tx = mob.x + flyNow[0], ty = mob.y + flyNow[1], tz = mob.z + flyNow[2];
        if (home > 0) {
            // Close to the hand-over: back towards the spot where the real one was last seen.
            tx = Mth.lerp(home, tx, mob.x);
            ty = Mth.lerp(home, ty, mob.y);
            tz = Mth.lerp(home, tz, mob.z);
        }
        double dx = tx - x, dy = ty - y, dz = tz - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Its flight never goes faster than the slowest of this: on it, it is where it should be every tick.
        double step = Math.min(d, Mth.clamp(d / 10, FLY_SLOW, FLY_FAST));
        double fromX = x, fromY = y, fromZ = z;
        if (d > 1e-6) {
            x += dx / d * step;
            y += dy / d * step;
            z += dz / d * step;
        }
        double mx = x - fromX, my = y - fromY, mz = z - fromZ, across = Math.hypot(mx, mz);
        float turnTo, tiltTo;
        if (across > 1e-3) {
            turnTo = (float) (Mth.atan2(mz, mx) * Mth.RAD_TO_DEG) - 90f;
            tiltTo = (float) (Mth.atan2(my, across) * Mth.RAD_TO_DEG);
        } else if (home >= 1) {
            // Waiting at its spot for the real one: turned as it was seen.
            turnTo = mob.yaw;
            tiltTo = 0f;
        } else {
            // Hanging where its flight has it: the way its flight goes on.
            double nx = flyNext[0] - flyNow[0], nz = flyNext[2] - flyNow[2];
            turnTo = Math.hypot(nx, nz) > 1e-6 ? (float) (Mth.atan2(nz, nx) * Mth.RAD_TO_DEG) - 90f : body;
            tiltTo = 0f;
        }
        body = Mth.approachDegrees(body, turnTo, TURN);
        head = Mth.approachDegrees(head, body, TURN);
        pitch = Mth.lerp(0.2f, pitch, Mth.clamp(tiltTo, -TILT, TILT));
        air = -1;
        lift = 0;
        climbing = false;
        apply(puppet, Math.sqrt(mx * mx + my * my + mz * mz));
    }

    /**
     * One tick of walking up to the real mob, now that the server sends it again: faster
     * the farther it is, never slower than the real one walks. True once there, and then
     * the real one takes its place without a jump.
     */
    boolean follow(LivingEntity puppet, MobMemory.Remembered mob, Entity real) {
        settled = false;
        if (!placed) place(mob);
        if (gait == null) gait = MobGait.of(puppet);
        double tx = real.getX(), ty = real.getY(), tz = real.getZ();
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double pace = Math.hypot(real.getX() - real.xo, real.getZ() - real.zo);
        double reach = Math.min(0.8, pace + 0.06 + 0.05 * d);
        double step = gait.hops() ? hop(d, reach) : Math.min(d, reach);
        double fromX = x, fromZ = z;
        climbing = Math.abs(ty - y) > 0.05;
        if (d > 1e-4) {
            x += dx / d * step;
            z += dz / d * step;
            y += (ty - y) * Math.min(1, step / d);
        } else {
            y += (ty - y) * 0.5;
        }
        land(d - step);
        float yaw = real instanceof LivingEntity living ? living.yBodyRot : real.getYRot();
        // Facing the way it walks, and the way the real one faces on the last stretch.
        float turnTo = d > 1.5 ? (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f : yaw;
        body = Mth.approachDegrees(body, turnTo, TURN);
        float headTo = real instanceof LivingEntity living ? living.yHeadRot : yaw;
        head = Mth.approachDegrees(head, d > 1.5 ? body : headTo, 10f);
        pitch = Mth.lerp(0.3f, pitch, real.getXRot());
        apply(puppet, Math.hypot(x - fromX, z - fromZ));
        boolean there = air < 0 && Math.hypot(tx - x, tz - z) < 0.1 && Math.abs(ty - y) < 0.15
                && Math.abs(Mth.wrapDegrees(body - yaw)) < 15;
        return there || ++following > 120;
    }

    private void apply(LivingEntity puppet, double moved) {
        puppet.setOldPosAndRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        // Where it already stands, the game would only make it the same box again.
        if (puppet.getX() != x || puppet.getY() != y + lift || puppet.getZ() != z) puppet.setPos(x, y + lift, z);
        puppet.setYRot(body);
        puppet.setXRot(pitch);
        puppet.yBodyRot = body;
        puppet.yHeadRot = head;
        // Legs as it moves, even still: moved by the server or walking back to its spot, a mob
        // with its legs at rest slides. Standing, they are at rest anyway.
        Puppets.walk(puppet, (float) Math.min(moved * 4.0, 1.0), 0.4f, puppet.isBaby() ? 3f : 1f);
        // Every tick, still or not: the game plays idle movements at tickCount plus the frame's
        // part of a tick, and with tickCount stopped they ran a tick's worth and snapped back,
        // twenty times a second.
        puppet.tickCount++;
        if (gait != null) gait.pose(puppet, tookOff, landed, lift > 0 || climbing);
        tookOff = false;
    }

    /**
     * Blocks a hopping mob covers this tick, {@code d} blocks from where it should be, at
     * most {@code reach} a tick were it walking: nothing while it waits on the ground, then a
     * whole hop's worth over the hop's ticks in the air.
     */
    private double hop(double d, double reach) {
        if (air < 0) {
            if (rest > 0) {
                rest--;
                return 0;
            }
            if (d < 0.3) return 0;
            // A hop's length, or more when it has fallen behind and walking would be faster.
            hopPace = Math.min(d, Math.max(gait.length, reach * gait.air)) / gait.air;
            air = 0;
            tookOff = true;
        }
        return Math.min(d, hopPace);
    }

    /** One tick further through a hop, if in one: lands at its end, and waits for the next. */
    private void land(double ahead) {
        landed = false;
        if (air >= 0 && ++air >= gait.air) {
            air = -1;
            landed = true;
            rest = gait.rest(seed, hops++, ahead);
        }
        lift = air >= 0 ? gait.lift(air) : 0;
    }
}
