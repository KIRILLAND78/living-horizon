package fr.clixmods.livinghorizon.track;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The positions a server with the mod shares ({@link PositionPublisher}), as this client last
 * received them ({@link PositionsMessage}): every player, online or logged off, and the
 * remembered mobs around this client. Nothing here is read from the world; a server without
 * the mod sends nothing, and {@link #active()} stays false.
 */
public final class SharedPositions {
    /** Ticks without news of the players before what was received is no longer trusted. */
    private static final int STALE_TICKS = 60;

    /**
     * Where a player is, as the server last said. {@code lastSeen} is when they logged off (0
     * when online or not known); {@code test} marks a test player the host placed;
     * {@code gliding} a player flying with an elytra, {@code pitch} the way they look up or down
     * then (0 otherwise, and from a server that does not say).
     */
    public record Report(double x, double y, double z, float yaw, String dimension, boolean riding, long lastSeen,
                         boolean test, boolean gliding, float pitch) {
        public Report(double x, double y, double z, float yaw, String dimension, boolean riding) {
            this(x, y, z, yaw, dimension, riding, 0, false, false, 0f);
        }
    }

    /** A mob the server publishes, under its UUID; {@code extra} is what it wears, as the game saves it. */
    public record MobReport(UUID id, double x, double y, double z, int kind, String dimension, boolean baby, int variant,
                            @Nullable String extra) {
    }

    private final Map<String, PositionsMessage.Player> players = new HashMap<>();
    private final Map<String, PositionsMessage.Player> offline = new HashMap<>();
    private final Map<UUID, MobReport> mobs = new HashMap<>();
    /** The last word on each player that came with their look and mount, which come every few seconds only. */
    private final Map<String, PositionsMessage.Player> dressed = new HashMap<>();
    private PositionsMessage.@Nullable Mobs lastMobs;
    /** The world's seed, when the server shares it, for this client to foresee the first animals itself. */
    private PositionsMessage.@Nullable Seed seed;
    private int ticksSinceNews = STALE_TICKS;
    private boolean active;
    /** The format of the last message from a version of the mod this one cannot read; null when none came. */
    private PositionsMessage.@Nullable Format unreadable;
    /** The players who logged off, from the parts of the list received so far; null between two lists. */
    private @Nullable List<PositionsMessage.Player> comingOffline;
    private int expectedPart;

    /** A message from the server, on the game's thread. */
    void receive(byte[] data) {
        PositionsMessage.Decoded message = PositionsMessage.read(data);
        if (message == null) {
            PositionsMessage.Format format = PositionsMessage.format(data);
            if (format != null && !format.readableBy(PositionsMessage.VERSION)) unreadable = format;
            return;
        }
        if (message.kind() == PositionsMessage.PLAYERS && message.players() != null) {
            players.clear();
            for (PositionsMessage.Player player : message.players()) {
                players.put(player.name(), player);
                if (player.look() != null || player.mount() != null) dressed.put(player.name(), player);
            }
            ticksSinceNews = 0;
            active = true;
        }
        if (message.kind() == PositionsMessage.OFFLINE && message.players() != null && message.part() != null) {
            offlinePart(message.players(), message.part());
        }
        if (message.seed() != null) seed = message.seed().dimensions().isEmpty() ? null : message.seed();
        if (message.mobs() != null) {
            mobs.clear();
            for (PositionsMessage.Mob mob : message.mobs().mobs()) {
                mobs.put(mob.id(), new MobReport(mob.id(), mob.x(), mob.y(), mob.z(), mob.kind(), mob.dimension(),
                        mob.baby(), mob.variant(), mob.extra()));
            }
            lastMobs = message.mobs();
        }
    }

    /**
     * One part of the list of players who logged off: the list is taken once whole, so that
     * nobody seems to have left between two of its parts. A part out of order drops the list.
     */
    private void offlinePart(List<PositionsMessage.Player> part, PositionsMessage.Part which) {
        if (which.index() == 0) {
            comingOffline = new ArrayList<>();
        } else if (comingOffline == null || which.index() != expectedPart) {
            comingOffline = null;
            return;
        }
        comingOffline.addAll(part);
        expectedPart = which.index() + 1;
        if (!which.last()) return;
        offline.clear();
        for (PositionsMessage.Player player : comingOffline) {
            offline.put(player.name(), player);
            if (player.look() != null || player.mount() != null) dressed.put(player.name(), player);
        }
        comingOffline = null;
    }

    /** Once per tick: whether the server shares positions right now. */
    boolean update() {
        if (ticksSinceNews < STALE_TICKS) ticksSinceNews++;
        active = ticksSinceNews < STALE_TICKS;
        return active;
    }

    /** Another server or world: what the last one said means nothing here. */
    void clear() {
        players.clear();
        offline.clear();
        mobs.clear();
        dressed.clear();
        lastMobs = null;
        seed = null;
        comingOffline = null;
        ticksSinceNews = STALE_TICKS;
        active = false;
        unreadable = null;
    }

    /**
     * The format of the messages the server sends, when this version of the mod cannot read
     * them: the server and this client run versions of the mod too far apart. Null otherwise.
     */
    public PositionsMessage.@Nullable Format unreadable() {
        return unreadable;
    }

    public boolean active() {
        return active;
    }

    /** What the server last said about one player, by name. Null when nothing. */
    public @Nullable Report read(String name) {
        PositionsMessage.Player player = active ? players.getOrDefault(name, offline.get(name)) : null;
        return player == null ? null
                : new Report(player.x(), player.y(), player.z(), player.yaw(), player.dimension(), player.riding(),
                        player.lastSeen(), player.test(), player.gliding(), player.pitch());
    }

    /** Every player the server has a position for, online or not. */
    public List<String> holders() {
        List<String> names = new ArrayList<>(players.keySet());
        for (String name : offline.keySet()) if (!players.containsKey(name)) names.add(name);
        return names;
    }

    /** The published mob a player rides: null when they ride nothing the server publishes. */
    public @Nullable UUID vehicle(String name) {
        PositionsMessage.Player player = active ? players.get(name) : null;
        return player == null ? null : player.vehicle();
    }

    /**
     * What a player rides, or rode when they logged off, as the server saved it: null when they
     * ride nothing, or the server has not said what yet.
     */
    public PositionsMessage.@Nullable Mount mount(String name) {
        PositionsMessage.Player now = active ? players.getOrDefault(name, offline.get(name)) : null;
        if (now == null || !now.riding()) return null;
        PositionsMessage.Player last = dressed.get(name);
        return last == null || last.online() != now.online() ? null : last.mount();
    }

    /** How a player looks, as the server last said: null when it has not said yet. */
    public PositionsMessage.@Nullable Look look(String name) {
        PositionsMessage.Player last = active ? dressed.get(name) : null;
        return last == null ? null : last.look();
    }

    /** Every mob the server last published around this client. */
    /** The seed the server shares, and where its generator is the game's own; null when it shares none. */
    public PositionsMessage.@Nullable Seed seed() {
        return active ? seed : null;
    }

    public List<UUID> mobs() {
        return new ArrayList<>(mobs.keySet());
    }

    public @Nullable MobReport readMob(UUID id) {
        return active ? mobs.get(id) : null;
    }

    /**
     * Whether the last mobs sent were chosen where a mob stands: a remembered mob there that
     * was not sent is gone; one farther off was only out of reach.
     */
    boolean covers(String dimension, double x, double z) {
        return lastMobs != null && lastMobs.covers(dimension, x, z);
    }

    /** A dimension as the messages name it: its id, {@code minecraft:overworld}, {@code aether:the_aether}. */
    public static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }
}
