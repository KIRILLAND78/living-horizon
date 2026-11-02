package fr.clixmods.livinghorizon.track;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Where a player who logged off can walk: every spot a player could stand on and reach on
 * foot, as a player would - a step up of one block, a drop of two at most, no corner cut,
 * nothing walked through. Worked out on the client from the blocks it has, for the figure
 * alone: nothing is sent anywhere.
 *
 * <p>Two searches share what they read of the blocks: a {@link Survey} of everything
 * reachable around a spot (a village's worth: where to stroll, which beds and chests are in
 * reach), and a {@link Route} to one spot, guided towards it. Both can grow a little at a
 * time; the offline players run them whole, off the game's thread, on a {@link BlockCopy}.
 * The blocks are read through {@link Ground}, so that this is tested without a world.
 *
 * <p>A survey reads tens of thousands of spots, so nothing here boxes a number: the maps are
 * fastutil's, keyed by {@link #key}, and each spot read is kept as its {@link Step}.
 */
public final class Walkway {
    /** What a spot is like: the height a player stands at with its feet in that block, or NaN. */
    public interface Ground {
        double stand(int x, int y, int z);
    }

    /** A spot to stand on: its block, and the height of the feet in it. */
    public record Step(int x, int y, int z, double height) {
        double distanceSqr(int ox, int oz) {
            double dx = x - ox, dz = z - oz;
            return dx * dx + dz * dz;
        }
    }

    /** Highest step up, in blocks: a jump. */
    static final double CLIMB = 1.25;
    /** Lowest drop, in blocks. */
    static final double DROP = 2.2;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** Blocks up or down to the next spot, in the order they are tried: level, up, down. */
    private static final int[] RISES = {0, 1, -1, -2};
    /** A spot read where no one stands, kept so that it is not read again. */
    private static final Step NOWHERE = new Step(0, 0, 0, Double.NaN);

    private final Ground ground;
    /** Every spot read: its step, or {@link #NOWHERE}; null when not read yet. */
    private final Long2ObjectOpenHashMap<Step> read = new Long2ObjectOpenHashMap<>();

    public Walkway(Ground ground) {
        this.ground = ground;
    }

    /** Forgets the blocks read so far: they may have changed. */
    public void forget() {
        read.clear();
    }

    /**
     * A survey grown to its end at once: every spot reachable from the one with its feet in
     * block ({@code x}, {@code y}, {@code z}), at most {@code radius} blocks across from
     * ({@code cx}, {@code cz}) and {@code budget} spots in all. Null when no one can stand there.
     */
    public static @Nullable Survey around(Ground ground, int x, int y, int z, int cx, int cz, int radius, int budget) {
        Survey survey = new Walkway(ground).survey(x, y, z, cx, cz, radius, budget);
        if (survey != null) survey.grow(Integer.MAX_VALUE);
        return survey;
    }

    /**
     * A route searched to its end at once: the way from the spot with its feet in block
     * ({@code x}, {@code y}, {@code z}) to {@code target}, the start left out; null when there
     * is none, or no one can stand at the start.
     */
    public static @Nullable List<Step> way(Ground ground, int x, int y, int z, Step target, int cx, int cz, int radius, int budget) {
        Route route = new Walkway(ground).route(x, y, z, target, cx, cz, radius, budget);
        if (route == null) return null;
        route.grow(Integer.MAX_VALUE);
        return route.path();
    }

    /** A survey from this spot, to {@link Survey#grow grow}; null when no one can stand there. */
    public @Nullable Survey survey(int x, int y, int z, int cx, int cz, int radius, int budget) {
        Step start = start(x, y, z);
        return start == null ? null : new Survey(start, cx, cz, radius, budget);
    }

    /**
     * A way from the spot with its feet in block ({@code x}, {@code y}, {@code z}) to
     * {@code target}, staying within {@code radius} of ({@code cx}, {@code cz}); null when
     * no one can stand at the start.
     */
    public @Nullable Route route(int x, int y, int z, Step target, int cx, int cz, int radius, int budget) {
        Step start = start(x, y, z);
        return start == null ? null : new Route(start, target, cx, cz, radius, budget);
    }

    /** The spot at the feet: in their block, or the one above or below when a little inside or over it. */
    private @Nullable Step start(int x, int y, int z) {
        Step start = step(x, y, z);
        if (start == null) start = step(x, y + 1, z);
        if (start == null) start = step(x, y - 1, z);
        return start;
    }

    /** Everything reachable from a spot, nearest first. */
    public final class Survey {
        private final Long2ObjectOpenHashMap<Step> steps = new Long2ObjectOpenHashMap<>();
        /** The spots reached, in the order they were: nearest first. */
        private final List<Step> order = new ArrayList<>();
        private final Long2LongOpenHashMap previous = new Long2LongOpenHashMap();
        private final Long2DoubleOpenHashMap costs = costs();
        private final PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
        private final int cx, cz, budget;
        private final double limit;

        private Survey(Step start, int cx, int cz, int radius, int budget) {
            this.cx = cx;
            this.cz = cz;
            this.budget = budget;
            this.limit = (radius + 0.5) * (radius + 0.5);
            costs.put(key(start), 0.0);
            open.add(new Open(start, 0, 0));
        }

        /** Reads up to {@code spots} more spots; true once there is nothing more to read. */
        public boolean grow(int spots) {
            int read = 0;
            while (!open.isEmpty() && steps.size() < budget && read < spots) {
                Open next = open.poll();
                Step at = next.step();
                long atKey = key(at);
                if (steps.putIfAbsent(atKey, at) != null) continue;
                order.add(at);
                read++;
                for (int[] d : DIRECTIONS) {
                    Step to = neighbour(at, d[0], d[1]);
                    if (to == null || to.distanceSqr(cx, cz) > limit) continue;
                    long toKey = key(to);
                    if (steps.containsKey(toKey)) continue;
                    double cost = next.cost() + cost(at, to, d);
                    if (costs.get(toKey) <= cost) continue;
                    costs.put(toKey, cost);
                    previous.put(toKey, atKey);
                    open.add(new Open(to, cost, cost));
                }
            }
            return done();
        }

        public boolean done() {
            return open.isEmpty() || steps.size() >= budget;
        }

        /** The spots reached so far, nearest first; not to be changed. */
        public List<Step> spots() {
            return Collections.unmodifiableList(order);
        }

        /** Whether a player can stand with its feet in this block, and reach it from the start. */
        public @Nullable Step reached(int x, int y, int z) {
            return steps.get(key(x, y, z));
        }

        /** The way from the start to a spot reached, the start left out; empty if it is the start. */
        public List<Step> pathTo(Step target) {
            return walk(target, steps, previous);
        }
    }

    /** The way to one spot, searched towards it first. */
    public final class Route {
        private final Long2ObjectOpenHashMap<Step> closed = new Long2ObjectOpenHashMap<>();
        private final Long2LongOpenHashMap previous = new Long2LongOpenHashMap();
        private final Long2DoubleOpenHashMap costs = costs();
        private final PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.rank(), b.rank()));
        private final Step target;
        private final int cx, cz, budget;
        private final double limit;
        private boolean finished;
        private @Nullable List<Step> path;

        private Route(Step start, Step target, int cx, int cz, int radius, int budget) {
            this.target = target;
            this.cx = cx;
            this.cz = cz;
            this.budget = budget;
            this.limit = (radius + 0.5) * (radius + 0.5);
            costs.put(key(start), 0.0);
            open.add(new Open(start, 0, guess(start)));
        }

        /** Reads up to {@code spots} more spots; true once the way is found, or known not to be. */
        public boolean grow(int spots) {
            int read = 0;
            while (!finished && read < spots) {
                if (open.isEmpty() || closed.size() >= budget) {
                    finished = true;
                    break;
                }
                Open next = open.poll();
                Step at = next.step();
                long atKey = key(at);
                if (closed.putIfAbsent(atKey, at) != null) continue;
                read++;
                if (at.x() == target.x() && at.y() == target.y() && at.z() == target.z()) {
                    path = walk(at, closed, previous);
                    finished = true;
                    break;
                }
                for (int[] d : DIRECTIONS) {
                    Step to = neighbour(at, d[0], d[1]);
                    if (to == null || to.distanceSqr(cx, cz) > limit) continue;
                    long toKey = key(to);
                    if (closed.containsKey(toKey)) continue;
                    double cost = next.cost() + cost(at, to, d);
                    if (costs.get(toKey) <= cost) continue;
                    costs.put(toKey, cost);
                    previous.put(toKey, atKey);
                    open.add(new Open(to, cost, cost + guess(to)));
                }
            }
            return finished;
        }

        public boolean done() {
            return finished;
        }

        /** The way, the start left out; null while searching, or when there is none. */
        public @Nullable List<Step> path() {
            return path;
        }

        private double guess(Step step) {
            double dx = step.x() - target.x(), dy = step.height() - target.height(), dz = step.z() - target.z();
            return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 0.5;
        }
    }

    private record Open(Step step, double cost, double rank) {
    }

    private static double cost(Step at, Step to, int[] d) {
        return (d[0] != 0 && d[1] != 0 ? 1.414 : 1.0) + Math.max(0, to.height() - at.height()) * 0.5;
    }

    /** The cheapest cost known to each spot: infinite where none is. */
    private static Long2DoubleOpenHashMap costs() {
        Long2DoubleOpenHashMap costs = new Long2DoubleOpenHashMap();
        costs.defaultReturnValue(Double.POSITIVE_INFINITY);
        return costs;
    }

    private static List<Step> walk(Step target, Long2ObjectOpenHashMap<Step> steps, Long2LongOpenHashMap previous) {
        Deque<Step> path = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        long at = key(target);
        while (previous.containsKey(at) && seen.add(at)) {
            Step step = steps.get(at);
            if (step == null) break;
            path.addFirst(step);
            at = previous.get(at);
        }
        return new ArrayList<>(path);
    }

    /** The spot one block over, a step up, level or down; for a diagonal, both sides must be open. */
    private @Nullable Step neighbour(Step from, int dx, int dz) {
        Step to = over(from, from.x() + dx, from.z() + dz);
        if (to == null || dx == 0 || dz == 0) return to;
        Step side = over(from, from.x() + dx, from.z());
        Step other = over(from, from.x(), from.z() + dz);
        if (side == null || other == null || side.y() != to.y() || other.y() != to.y()) return null;
        return to;
    }

    private @Nullable Step over(Step from, int x, int z) {
        for (int dy : RISES) {
            Step to = step(x, from.y() + dy, z);
            if (to == null) continue;
            double rise = to.height() - from.height();
            if (rise > CLIMB || -rise > DROP) continue;
            return to;
        }
        return null;
    }

    /** The spot with its feet in that block, read once; null where no one stands. */
    private @Nullable Step step(int x, int y, int z) {
        long key = key(x, y, z);
        Step known = read.get(key);
        if (known == null) {
            double h = ground.stand(x, y, z);
            known = Double.isNaN(h) ? NOWHERE : new Step(x, y, z, h);
            read.put(key, known);
        }
        return known == NOWHERE ? null : known;
    }

    private static long key(Step step) {
        return key(step.x(), step.y(), step.z());
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The spots in a random order, for picking one. */
    static List<Step> shuffled(List<Step> spots, Random random) {
        List<Step> copy = new ArrayList<>(spots);
        Collections.shuffle(copy, random);
        return copy;
    }
}
