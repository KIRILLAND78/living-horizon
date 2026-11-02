package fr.clixmods.livinghorizon.track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/**
 * How the nearest are chosen from a long list: the players the server sends with how they look
 * and what they ride ({@link PositionPublisher}), and the players who logged off a client draws
 * ({@link RestingPlayers}). Plain lists, no type of the game's, so that a unit test reads them.
 *
 * <p>Loaded on a dedicated server: nothing here may reach a class of the client's.
 */
final class PlayerLists {
    private PlayerLists() {
    }

    /**
     * The {@code count} players nearest a spot, the nearest first: those in its dimension by
     * distance, then those elsewhere.
     */
    static List<PositionsMessage.Player> nearest(Collection<PositionsMessage.Player> players, String dimension,
                                                 double x, double z, int count) {
        return nearest(players, count, player -> distance(player, dimension, x, z));
    }

    /**
     * The {@code count} things of a list with the smallest {@code distance}, the nearest first.
     * It keeps {@code count} at a time, rather than sorting them all, so that a long list costs
     * little each time it is chosen from.
     */
    static <T> List<T> nearest(Collection<T> things, int count, ToDoubleFunction<T> distance) {
        if (count <= 0) return List.of();
        Comparator<T> nearer = Comparator.comparingDouble(distance);
        // The farthest of those kept on top, to make way for a nearer one.
        PriorityQueue<T> kept = new PriorityQueue<>(Math.min(count, Math.max(things.size(), 1)), nearer.reversed());
        for (T thing : things) {
            if (kept.size() < count) {
                kept.add(thing);
            } else if (nearer.compare(thing, kept.peek()) < 0) {
                kept.poll();
                kept.add(thing);
            }
        }
        List<T> chosen = new ArrayList<>(kept);
        chosen.sort(nearer);
        return chosen;
    }

    /** Across the ground; any other dimension is farther than anywhere in this one. */
    private static double distance(PositionsMessage.Player player, String dimension, double x, double z) {
        return player.dimension().equals(dimension) ? Math.hypot(player.x() - x, player.z() - z) : Double.MAX_VALUE;
    }
}
