package fr.clixmods.livinghorizon.track;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
//? if >=1.21.2
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
//? if >=26.2 {
/*import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
*///?} else
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * How a remembered mob gets from one point of its animation to the next, as its real one
 * does: most walk, slimes, magma cubes and rabbits hop. A hopping mob stays where it is
 * between hops and covers the way in the air, along an arc; {@link MobMotion} asks where
 * it is at each tick, and the figure follows - a slime squashes as it lands and stretches
 * as it leaves the ground, a rabbit's legs kick, a chicken flaps on its way down a step,
 * the ender dragon beats its wings and bends its neck and tail along its flight.
 */
enum MobGait {
    WALK(0, 0, 0, 0, 0),
    /** Short, quick hops, one after the other while it has somewhere to go. */
    RABBIT(10, 0.55, 1.4, 3, 8),
    /** A hop, then a wait, as long as the hop or longer. */
    SLIME(10, 0.9, 1.8, 10, 30),
    /** Higher hops and longer waits than a slime, as the game makes them. */
    MAGMA_CUBE(14, 1.6, 2.2, 30, 90);

    /** Ticks in the air. */
    final int air;
    /** Blocks above the ground at the top of the hop. */
    final double height;
    /** Blocks covered by one hop, at most. */
    final double length;
    /** Ticks on the ground between two hops, from the fewest to the most. */
    final int restMin, restMax;

    MobGait(int air, double height, double length, int restMin, int restMax) {
        this.air = air;
        this.height = height;
        this.length = length;
        this.restMin = restMin;
        this.restMax = restMax;
    }

    boolean hops() {
        return air > 0;
    }

    static MobGait of(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.RABBIT) return RABBIT;
        if (type == EntityType.MAGMA_CUBE) return MAGMA_CUBE;
        if (type == EntityType.SLIME) return SLIME;
        return WALK;
    }

    /**
     * Degrees from the way an entity faces to its yaw: the ender dragon's points behind it, 180
     * degrees round from every other mob's.
     */
    static float backwards(LivingEntity entity) {
        return entity instanceof EnderDragon ? 180f : 0f;
    }

    /** Ticks to wait before the next hop: a few less the farther it has to go. */
    int rest(long seed, int hop, double ahead) {
        int rest = restMin + (int) Math.floorMod(MobMotion.mix(seed, hop), (long) (restMax - restMin + 1));
        return ahead > 4 ? rest / 3 : rest;
    }

    /** Blocks above the ground {@code tick} ticks into a hop: a parabola, nil at both ends. */
    double lift(int tick) {
        double u = Mth.clamp(tick / (double) air, 0, 1);
        return height * 4 * u * (1 - u);
    }

    /**
     * One tick of what the figure does with its body, as the game does it while the real one
     * ticks: {@code tookOff} and {@code landed} on the first and last ticks of a hop,
     * {@code airborne} while it is off the ground for any reason (a hop, a step up or down).
     */
    void pose(LivingEntity entity, boolean tookOff, boolean landed, boolean airborne) {
        var cube = cube(entity);
        if (cube != null) {
            // Stretched as it leaves the ground, squashed as it lands, and back to round.
            cube.oSquish = cube.squish;
            if (tookOff) cube.targetSquish = 1.0f;
            else if (landed) cube.targetSquish = -0.5f;
            cube.squish += (cube.targetSquish - cube.squish) * 0.5f;
            cube.targetSquish *= this == MAGMA_CUBE ? 0.9f : 0.6f;
        }
        if (entity instanceof Rabbit rabbit) {
            // Its legs kick through the hop, as the game times them.
            if (tookOff) {
                rabbit.jumpDuration = air;
                rabbit.jumpTicks = 0;
            } else if (rabbit.jumpTicks < rabbit.jumpDuration) {
                rabbit.jumpTicks++;
            } else {
                rabbit.jumpTicks = 0;
                rabbit.jumpDuration = 0;
            }
        }
        if (entity instanceof Chicken chicken) {
            // Wings beating while it is off the ground, folded once it stands.
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            chicken.flapSpeed = Mth.clamp(chicken.flapSpeed + (airborne ? 4f : -1f) * 0.3f, 0f, 1f);
            if (airborne && chicken.flapping < 1f) chicken.flapping = 1f;
            chicken.flapping *= 0.9f;
            chicken.flap += chicken.flapping * 2f;
        }
        if (entity instanceof EnderDragon dragon) fly(dragon);
    }

    /**
     * One tick of the dragon's flight, as its own tick does it: its wings beat slower the faster
     * it flies and quicker as it climbs, and its neck, body and tail follow the heights and
     * headings it went through, which the game reads back from what it records each tick.
     * Without it, a far dragon glides with its wings still and faces one way whatever it does.
     */
    private static void fly(EnderDragon dragon) {
        double across = Math.hypot(dragon.getX() - dragon.xo, dragon.getZ() - dragon.zo);
        double up = Mth.clamp(dragon.getY() - dragon.yo, -1, 1);
        dragon.oFlapTime = dragon.flapTime;
        dragon.flapTime += 0.2f / ((float) across * 10f + 1f) * (float) Math.pow(2.0, up);
        //? if >=1.21.2 {
        dragon.flightHistory.record(dragon.getY(), dragon.getYRot());
        //?} else {
        /*double[][] positions = dragon.positions;
        if (dragon.posPointer < 0) {
            for (double[] sample : positions) {
                sample[0] = dragon.getYRot();
                sample[1] = dragon.getY();
            }
        }
        if (++dragon.posPointer == positions.length) dragon.posPointer = 0;
        positions[dragon.posPointer][0] = dragon.getYRot();
        positions[dragon.posPointer][1] = dragon.getY();
        *///?}
    }

    /**
     * Forgets the way the dragon flew, now that it was moved at once (shown again where the
     * server has it): its neck and tail would swing through the whole gap.
     */
    static void moved(LivingEntity entity) {
        if (!(entity instanceof EnderDragon dragon)) return;
        //? if >=1.21.2 {
        for (int i = 0; i < DragonFlightHistory.LENGTH; i++) dragon.flightHistory.record(dragon.getY(), dragon.getYRot());
        //?} else {
        /*for (double[] sample : dragon.positions) {
            sample[0] = dragon.getYRot();
            sample[1] = dragon.getY();
        }
        *///?}
    }

    /**
     * Puts what {@link #pose} moves at rest, for an impostor's pictures: a slime baked
     * squashed would stay squashed. Gives back what puts it as it was.
     */
    static Runnable atRest(Object entity) {
        var cube = entity instanceof LivingEntity living ? cube(living) : null;
        if (cube != null) {
            float squish = cube.squish, old = cube.oSquish, target = cube.targetSquish;
            cube.squish = cube.oSquish = cube.targetSquish = 0;
            return () -> {
                cube.squish = squish;
                cube.oSquish = old;
                cube.targetSquish = target;
            };
        }
        if (entity instanceof Rabbit rabbit) {
            int ticks = rabbit.jumpTicks, duration = rabbit.jumpDuration;
            rabbit.jumpTicks = rabbit.jumpDuration = 0;
            return () -> {
                rabbit.jumpTicks = ticks;
                rabbit.jumpDuration = duration;
            };
        }
        if (entity instanceof Chicken chicken) {
            float speed = chicken.flapSpeed, oldSpeed = chicken.oFlapSpeed;
            chicken.flapSpeed = chicken.oFlapSpeed = 0;
            return () -> {
                chicken.flapSpeed = speed;
                chicken.oFlapSpeed = oldSpeed;
            };
        }
        return () -> { };
    }

    /** A slime or a magma cube: one class with the other under it, then a common parent from 26.2. */
    //? if >=26.2 {
    /*private static @Nullable AbstractCubeMob cube(LivingEntity entity) {
        return entity instanceof AbstractCubeMob cube ? cube : null;
    }
    *///?} else {
    private static @Nullable Slime cube(LivingEntity entity) {
        return entity instanceof Slime cube ? cube : null;
    }
    //?}
}
