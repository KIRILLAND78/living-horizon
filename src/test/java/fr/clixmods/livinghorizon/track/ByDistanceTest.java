package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mobs sorted nearest first, as a comparator would, thousands at a time. */
class ByDistanceTest {
    @Test
    void sortsNearestFirst() {
        Random random = new Random(7);
        List<double[]> mobs = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) mobs.add(new double[]{random.nextDouble() * 4000, i});
        List<double[]> expected = new ArrayList<>(mobs);
        expected.sort((a, b) -> Double.compare(a[0], b[0]));
        ByDistance.sort(mobs, mob -> mob[0]);
        assertEquals(expected.size(), mobs.size());
        for (int i = 0; i < mobs.size(); i++) assertEquals(expected.get(i)[0], mobs.get(i)[0], 1e-6);
    }

    @Test
    void tiesKeepTheirOrder() {
        List<double[]> mobs = new ArrayList<>();
        for (int i = 0; i < 100; i++) mobs.add(new double[]{i % 2 == 0 ? 5 : 3, i});
        ByDistance.sort(mobs, mob -> mob[0]);
        for (int i = 1; i < mobs.size(); i++) {
            double[] before = mobs.get(i - 1), after = mobs.get(i);
            assertTrue(before[0] < after[0] || before[0] == after[0] && before[1] < after[1]);
        }
    }

    @Test
    void theSpyglassFirst() {
        // Where the spyglass looks: its distance as it is; elsewhere a billion blocks more, as choosing does.
        List<double[]> mobs = new ArrayList<>(List.of(new double[]{10, 0}, new double[]{3000, 1}, new double[]{20, 0},
                new double[]{2000, 1}));
        ByDistance.sort(mobs, mob -> mob[1] == 1 ? mob[0] : mob[0] + 1e9);
        assertEquals(List.of(2000.0, 3000.0, 10.0, 20.0), mobs.stream().map(mob -> mob[0]).toList());
    }

    @Test
    void negativeCountsAsNothing() {
        List<double[]> mobs = new ArrayList<>(List.of(new double[]{4}, new double[]{-2}, new double[]{0}));
        ByDistance.sort(mobs, mob -> mob[0]);
        assertEquals(4.0, mobs.get(2)[0]);
    }
}
