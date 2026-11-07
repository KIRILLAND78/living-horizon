package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.ServerConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Who the server shares with whom, from the owner's settings ({@link ServerConfig}): the players
 * left out of the lists {@link PositionPublisher} sends one player, and whether that player is
 * sent the mobs. Plain lists, no type of the game's but a team's name, so that a unit test reads it.
 *
 * <p>Players in spectator mode or invisible are left out before, by the publisher, which keeps
 * them where they were last seen.
 *
 * <p>Loaded on a dedicated server: nothing here may reach a class of the client's.
 */
final class ShareRules {
    /** Who is sent the lists: their name, where they stand, and their team (null when none). */
    record Viewer(String name, String dimension, double x, double z, @Nullable String team) {
    }

    private final ServerConfig config;
    private final Function<String, @Nullable String> teams;
    private final Set<String> hiddenNames;
    private final Set<String> hiddenDimensions;

    /** {@code teams} names the team of a player, by name; null when they have none. */
    ShareRules(ServerConfig config, Function<String, @Nullable String> teams) {
        this.config = config;
        this.teams = teams;
        this.hiddenNames = config.hiddenNames();
        this.hiddenDimensions = config.hiddenDimensionIds();
    }

    /** Whether the players online are the same for everyone: one message for all. */
    boolean sameForAll() {
        return config.playerRange <= 0 && !config.teamsOnly;
    }

    /**
     * The players online {@code viewer} is sent, themselves included. A null viewer is everyone,
     * for {@link #sameForAll()} only.
     */
    List<PositionsMessage.Player> online(List<PositionsMessage.Player> players, @Nullable Viewer viewer) {
        return config.sharePlayers ? shared(players, viewer) : self(players, viewer);
    }

    /** The players who logged off {@code viewer} is sent. */
    List<PositionsMessage.Player> offline(List<PositionsMessage.Player> players, Viewer viewer) {
        return config.shareOfflinePlayers ? shared(players, viewer) : List.of();
    }

    /** Whether a player standing in {@code dimension} is sent the remembered mobs around them. */
    boolean mobs(String dimension) {
        return config.shareMobs && !hiddenDimensions.contains(dimension.toLowerCase(Locale.ROOT));
    }

    private List<PositionsMessage.Player> shared(List<PositionsMessage.Player> players, @Nullable Viewer viewer) {
        String team = viewer == null ? null : viewer.team();
        List<PositionsMessage.Player> kept = new ArrayList<>(players.size());
        for (PositionsMessage.Player player : players) {
            if (viewer != null && player.name().equalsIgnoreCase(viewer.name())) {
                kept.add(player);
                continue;
            }
            if (hiddenNames.contains(player.name().toLowerCase(Locale.ROOT))) continue;
            if (hiddenDimensions.contains(player.dimension().toLowerCase(Locale.ROOT))) continue;
            if (viewer != null && config.playerRange > 0 && !within(player, viewer)) continue;
            if (viewer != null && config.teamsOnly && (team == null || !team.equals(teams.apply(player.name())))) continue;
            kept.add(player);
        }
        return kept;
    }

    /** The viewer alone, when the players are not shared. */
    private static List<PositionsMessage.Player> self(List<PositionsMessage.Player> players, @Nullable Viewer viewer) {
        if (viewer == null) return List.of();
        for (PositionsMessage.Player player : players) {
            if (player.name().equalsIgnoreCase(viewer.name())) return List.of(player);
        }
        return List.of();
    }

    /** Within {@link ServerConfig#playerRange} of the viewer, across the ground, in their dimension. */
    private boolean within(PositionsMessage.Player player, Viewer viewer) {
        return player.dimension().equals(viewer.dimension())
                && Math.hypot(player.x() - viewer.x(), player.z() - viewer.z()) <= config.playerRange;
    }
}
