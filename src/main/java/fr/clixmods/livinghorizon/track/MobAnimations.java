package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;

import java.util.List;

/**
 * The animations a remembered mob plays, written once and for all: walks out from where
 * it stands and back, with pauses to graze or look around on the way. Nothing is decided
 * while one plays - {@link MobPaths} picks, when the mob is remembered, the one that fits
 * the blocks around it, turned and mirrored as the ground asks, and it plays in a loop.
 *
 * <p>Points are in blocks from where the mob stands, {@code z} straight ahead and
 * {@code x} to its right; every animation ends where it began, so the loop never jumps.
 */
public final class MobAnimations {
    /** Blocks a tick while walking: a stroll, brisk enough to be seen from far away. */
    static final double WALK = 0.11;

    /** Walk to a point, then stand there {@code pause} ticks, grazing or looking around. */
    record Step(double x, double z, int pause, boolean graze) {
    }

    /** One animation, its timeline worked out once. */
    public static final class Animation {
        final String name;
        final Step[] steps;
        /** The farthest it goes from where it stands, in blocks. */
        final double extent;
        /** Ticks one round lasts. */
        final int duration;
        /** When each step starts walking, and when it arrives. */
        private final int[] start, arrive;

        Animation(String name, Step... steps) {
            this.name = name;
            this.steps = steps;
            start = new int[steps.length];
            arrive = new int[steps.length];
            double extent = 0, px = 0, pz = 0;
            int t = 0;
            for (int k = 0; k < steps.length; k++) {
                Step step = steps[k];
                start[k] = t;
                t += Math.max(1, (int) Math.ceil(Math.hypot(step.x - px, step.z - pz) / WALK));
                arrive[k] = t;
                t += step.pause;
                px = step.x;
                pz = step.z;
                extent = Math.max(extent, Math.hypot(px, pz));
            }
            this.extent = extent;
            duration = t;
        }

        /** Where it is and what it does {@code t} ticks into a round, in its own frame. */
        Pose sample(int t) {
            t = Math.floorMod(t, duration);
            double px = 0, pz = 0, heading = 0;
            for (int k = 0; k < steps.length; k++) {
                Step step = steps[k];
                double dx = step.x - px, dz = step.z - pz;
                if (dx != 0 || dz != 0) heading = Math.atan2(dx, dz);
                if (t < arrive[k]) {
                    double u = (t - start[k]) / (double) (arrive[k] - start[k]);
                    return new Pose(px + dx * u, pz + dz * u, heading, true, false);
                }
                if (t < arrive[k] + step.pause) return new Pose(step.x, step.z, heading, false, step.graze);
                px = step.x;
                pz = step.z;
            }
            return new Pose(0, 0, heading, false, false);
        }

        /** The corners it walks through, from where it stands and back: {@code x, z} pairs. */
        double[] corners() {
            double[] corners = new double[steps.length * 2 + 2];
            for (int k = 0; k < steps.length; k++) {
                corners[k * 2 + 2] = steps[k].x;
                corners[k * 2 + 3] = steps[k].z;
            }
            return corners;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A moment of an animation, in its own frame: where, facing which way ({@code heading}
     * in radians, 0 straight ahead, towards {@code +x} as it grows), walking or not.
     */
    record Pose(double x, double z, double heading, boolean walking, boolean grazing) {
    }

    /** From the widest to the smallest: the widest that fits the ground is played. */
    static final List<Animation> ALL = List.of(
            new Animation("meadow round",
                    new Step(0, 3, 60, true), new Step(2.5, 5, 50, false), new Step(4.5, 3.5, 70, true),
                    new Step(3.5, 0.5, 40, false), new Step(0, 0, 90, false)),
            new Animation("long stroll",
                    new Step(1, 3, 30, false), new Step(0, 6, 110, true), new Step(-1, 3, 40, false),
                    new Step(0, 0, 80, false)),
            new Animation("zigzag",
                    new Step(1.5, 1.5, 30, false), new Step(-1, 3, 60, true), new Step(1, 5, 70, false),
                    new Step(0, 2.5, 30, true), new Step(0, 0, 80, false)),
            new Animation("wide arc",
                    new Step(-2, 2, 40, false), new Step(-1, 4, 70, true), new Step(2, 4, 40, false),
                    new Step(2, 1, 60, true), new Step(0, 0, 70, false)),
            new Animation("small round",
                    new Step(0, 2, 60, true), new Step(1.8, 2.5, 40, false), new Step(2, 0.5, 60, true),
                    new Step(0, 0, 80, false)),
            new Animation("there and back",
                    new Step(0, 3, 100, true), new Step(0, 0, 80, false)),
            new Animation("a few steps",
                    new Step(0, 1.5, 80, true), new Step(0, 0, 100, false)));

    /**
     * For a player who logged off where none of {@link #ALL} fits - indoors, on a roof, a
     * balcony: a step or two and back, still something to see from afar.
     */
    static final List<Animation> CRAMPED = List.of(
            new Animation("pacing",
                    new Step(0, 1, 90, true), new Step(0, 0, 120, false)),
            new Animation("a step aside",
                    new Step(0.7, 0.7, 110, false), new Step(0, 0, 130, true)));

    private MobAnimations() {
    }

    /** Turns a point of an animation's frame into blocks from the mob, along {@code angle}, mirrored or not. */
    static double worldX(double x, double z, double angle, boolean mirror) {
        double mx = mirror ? -x : x;
        return mx * Math.cos(angle) + z * Math.sin(angle);
    }

    static double worldZ(double x, double z, double angle, boolean mirror) {
        double mx = mirror ? -x : x;
        return -mx * Math.sin(angle) + z * Math.cos(angle);
    }

    /** The yaw the game uses for a heading of an animation's frame. */
    static float yaw(double heading, double angle, boolean mirror) {
        double dx = worldX(Math.sin(heading), Math.cos(heading), angle, mirror);
        double dz = worldZ(Math.sin(heading), Math.cos(heading), angle, mirror);
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
    }
}
