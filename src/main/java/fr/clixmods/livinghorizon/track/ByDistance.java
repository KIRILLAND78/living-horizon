package fr.clixmods.livinghorizon.track;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Sorts thousands of mobs nearest first, each distance worked out once: a positive double sorts
 * as its bits do, so each one becomes a number, its distance in the high bits and its place in
 * the list in the low ones, and numbers are sorted many times faster than objects through a
 * comparator. Asks the game nothing.
 */
final class ByDistance {
    /** The low bits that hold the place: a list longer than this is sorted through a comparator. */
    private static final int PLACE = 0xFFFFF;

    private ByDistance() {
    }

    /**
     * Sorts {@code list} by {@code distance}, smallest first; a negative distance counts as 0. Two
     * distances closer than a few billionths of either keep the order they had.
     */
    @SuppressWarnings("unchecked")
    static <T> void sort(List<T> list, ToDoubleFunction<? super T> distance) {
        int n = list.size();
        if (n < 2) return;
        if (n > PLACE) {
            list.sort(Comparator.comparingDouble(distance));
            return;
        }
        long[] order = new long[n];
        for (int i = 0; i < n; i++) {
            double d = Math.max(0.0, distance.applyAsDouble(list.get(i)));
            order[i] = (Double.doubleToRawLongBits(d) & ~(long) PLACE) | i;
        }
        Arrays.sort(order);
        Object[] sorted = new Object[n];
        for (int i = 0; i < n; i++) sorted[i] = list.get((int) (order[i] & PLACE));
        for (int i = 0; i < n; i++) list.set(i, (T) sorted[i]);
    }
}
