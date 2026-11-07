package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobFlightTest {
    private static final long[] SEEDS = {0L, 1L, -7L, 0x1234_5678_9ABC_DEF0L, Long.MIN_VALUE, Long.MAX_VALUE, 42L};

    @Test
    void phantomsCircleAndGhastsDriftWhileWalkersHaveNoFlight() {
        assertEquals(MobFlight.CIRCLE, MobFlight.of("minecraft:phantom"));
        assertEquals(MobFlight.DRIFT, MobFlight.of("minecraft:ghast"));
        assertEquals(MobFlight.DRIFT, MobFlight.of("minecraft:happy_ghast"));
        assertNull(MobFlight.of("minecraft:cow"));
        assertNull(MobFlight.of("minecraft:parrot"));
    }

    @Test
    void otherModsBirdsWheelAndTheirOtherMobsWalk() {
        for (String bird : new String[]{"tit", "crow", "jay", "bullfinch", "sparrow", "lark", "seagull", "owl", "woodpecker"}) {
            assertEquals(MobFlight.WHEEL, MobFlight.of("ambientbirds:" + bird), bird);
        }
        assertNull(MobFlight.of("ambientbirds:dodo"));
        assertNull(MobFlight.of("othermod:crow"));
    }

    @Test
    void neverFasterThanTheCopyCanFollow() {
        double[] a = new double[3], b = new double[3];
        for (MobFlight flight : MobFlight.values()) {
            for (long seed : SEEDS) {
                flight.at(seed, 0, a);
                for (int t = 1; t < 20_000; t++) {
                    flight.at(seed, t, b);
                    double step = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
                    // MobMotion keeps up with anything slower than its slowest step, 0.7 a tick.
                    assertTrue(step < 0.65, flight + " jumps " + step + " at tick " + t);
                    double[] swap = a;
                    a = b;
                    b = swap;
                }
            }
        }
    }

    @Test
    void phantomsCircleAboveTheirSpotAndGhastsStayNearIt() {
        double[] at = new double[3];
        for (long seed : SEEDS) {
            for (int t = 0; t < 20_000; t += 7) {
                MobFlight.CIRCLE.at(seed, t, at);
                double across = Math.hypot(at[0], at[2]);
                assertTrue(across >= 4 && across <= 16, "circle of " + across);
                assertTrue(at[1] >= MobFlight.CIRCLE_RISE - 2.001 && at[1] <= MobFlight.CIRCLE_RISE + 2.001);
                MobFlight.DRIFT.at(seed, t, at);
                assertTrue(Math.abs(at[0]) <= MobFlight.DRIFT_REACH + 1e-9 && Math.abs(at[2]) <= MobFlight.DRIFT_REACH + 1e-9);
                assertTrue(Math.abs(at[1]) <= MobFlight.DRIFT_RISE + 1e-9);
            }
        }
    }

    @Test
    void birdsWheelOverTheirSpotAtABirdsPace() {
        double[] a = new double[3], b = new double[3];
        for (long seed : SEEDS) {
            double farthest = 0, flown = 0;
            MobFlight.WHEEL.at(seed, 0, a);
            for (int t = 1; t < 20_000; t++) {
                MobFlight.WHEEL.at(seed, t, b);
                double out = Math.hypot(b[0], b[2]);
                farthest = Math.max(farthest, out);
                assertTrue(out <= MobFlight.WHEEL_REACH_MAX + 1e-9, "out " + out);
                // Above the spot, clear of the trees it perched in.
                assertTrue(b[1] >= MobFlight.WHEEL_RISE - 2.001 && b[1] <= MobFlight.WHEEL_RISE + 2.001);
                flown += Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
                double[] swap = a;
                a = b;
                b = swap;
            }
            // It goes out to its whole reach, at about the pace of a bird.
            assertTrue(farthest >= MobFlight.WHEEL_REACH_MIN - 0.1, "reaches " + farthest);
            double pace = flown / 20_000;
            assertTrue(pace > 0.12 && pace < 0.3, "pace " + pace);
        }
    }

    @Test
    void eachFliesItsOwnWayAndTheSameEveryTime() {
        double[] a = new double[3], b = new double[3];
        MobFlight.CIRCLE.at(5L, 1000, a);
        MobFlight.CIRCLE.at(5L, 1000, b);
        assertEquals(a[0], b[0]);
        assertEquals(a[2], b[2]);
        MobFlight.CIRCLE.at(5L << 13 | 77L << 3, 1000, b);
        assertNotEquals(a[0], b[0]);
    }

    private static double sq(double v) {
        return v * v;
    }
}
