package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.properties.Property;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.ServerConfig;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What a server with the mod tells the clients with the mod, so that they draw every player
 * at any distance, as they look and on what they ride, and the remembered mobs past what the
 * server sends them ({@link PositionsMessage}, received by {@link SharedPositions}).
 *
 * <p>Run by {@code MinecraftServerMixin} on every server: a dedicated one, and the one inside the
 * game of a player alone in their world or hosting it on LAN, whose own game reads it back like
 * any other. It touches nothing of the client, so a dedicated server can load it. A client
 * without the mod is sent nothing.
 *
 * <p>It keeps the last position of every player who logged off, with how they looked and the
 * mount they took away with them, and of the mobs in chunks no longer loaded, in a file of the
 * world ({@link #FILE}), so that a player who joins later sees them too. A mob is forgotten when
 * its chunk is loaded and it is not there any more.
 *
 * <p>What it shares, and with whom, follows the owner's settings ({@link ServerConfig},
 * {@link ShareRules}), read again a few seconds after the file changes.
 *
 * <p>Everything here runs on the server's thread.
 */
public final class PositionPublisher {
    /** Ticks between two publications of the players. */
    public static final int PERIOD = 4;
    /** Publications between two of everything else (looks, mounts, offline players, mobs): five seconds. */
    private static final int FULL_EVERY = 25;
    /** Publications between two reads of what an online player wears: a second. */
    private static final int LOOK_EVERY = 5;
    /** How far around a player the mobs sent to them reach, in blocks. */
    private static final double MOB_RANGE = 2048;
    /** Bounds the file and the messages; the mobs seen longest ago go first. */
    private static final int MAX_MOBS = 4000;
    /** Players sent with how they look and what they ride, the nearest first; the others are known by their position. */
    private static final int MAX_LOOKS = 64;
    /** Publications between two reads of a mob's saved data: thirty seconds. */
    private static final int MOB_READ_EVERY = 150;
    /** Past this, what a mob wears is left out. */
    private static final int MAX_EXTRA = 4000;
    /** What a mob's look takes from its saved data besides its kind and variant: what it wears. */
    private static final List<String> EXTRA_KEYS = List.of("equipment", "ArmorItems", "HandItems", "body_armor_item",
            "SaddleItem", "Saddle", "ArmorItem", "DecorItem", "ChestedHorse", "Sheared");
    /** Publications between two saves of the file, when something changed: a minute. */
    private static final int SAVE_EVERY = 300;
    /** The file in the world's folder. */
    static final String FILE = "livinghorizon-positions.dat";
    private static final Map<String, Integer> KINDS = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    private static final Map<MinecraftServer, PositionPublisher> PUBLISHERS = new WeakHashMap<>();

    static {
        for (int i = 0; i < MobKinds.TYPES.size(); i++) KINDS.put(MobKinds.TYPES.get(i), i + 1);
    }

    /** The publisher of a server, made the first time it is asked for. */
    public static synchronized PositionPublisher of(MinecraftServer server) {
        return PUBLISHERS.computeIfAbsent(server, PositionPublisher::new);
    }

    /** The publisher of a server, if it has one. */
    public static synchronized @Nullable PositionPublisher existing(MinecraftServer server) {
        return PUBLISHERS.get(server);
    }

    private final MinecraftServer server;
    /** Every player who ever played here, online or not, by name; those offline with their look and mount. */
    private final Map<String, PositionsMessage.Player> players = new LinkedHashMap<>();
    /** How each player online looks, by name, read every second. */
    private final Map<String, PositionsMessage.Look> looks = new HashMap<>();
    /** What each player online rides alone, by name: the server takes it away with them when they log off. */
    private final Map<String, PositionsMessage.Mount> riding = new HashMap<>();
    /** Which mount {@link #riding} saved, by name: it is saved again when it changes, and every few seconds. */
    private final Map<String, UUID> ridden = new HashMap<>();
    /** Every remembered mob, the one seen longest ago first. */
    private final LinkedHashMap<UUID, PositionsMessage.Mob> mobs = new LinkedHashMap<>();
    /** What was last read of each mob's saved data. */
    private final Map<UUID, Read> reads = new HashMap<>();
    /** The animals of the chunks not generated yet, from the seed: shared with the mobs, never saved. */
    private final SeedMobs seeded = new SeedMobs();
    /** Test players the host placed with the debug command: shared, never saved. */
    private Map<String, PositionsMessage.Player> tests = Map.of();
    /** Test players handed over by the host's game, not taken yet. */
    private volatile @Nullable List<PositionsMessage.Player> offered;
    /** The owner's settings, read again when their file changes. */
    private final ServerConfig.Watched settings;
    /** The players online left out right now, by name: in spectator mode or invisible. */
    private final Set<String> hidden = new HashSet<>();
    /** The players already sent everything: one who is not gets it all at once. */
    private Set<UUID> greeted = new HashSet<>();
    private boolean loaded;
    private boolean dirty;
    private boolean told;
    private int runs;

    private record Read(int variant, @Nullable String extra, int at) {
    }

    private PositionPublisher(MinecraftServer server) {
        this.server = server;
        this.settings = new ServerConfig.Watched(Platform.configDir().resolve(ServerConfig.FILE));
    }

    /** One publication; call it every {@link #PERIOD} ticks. */
    public void run() {
        long started = Profile.begin(Profile.Section.PUBLISH);
        long[] sentBytes = {0};
        try {
            publish(sentBytes);
        } finally {
            Profile.end(Profile.Section.PUBLISH, started, sentBytes[0]);
        }
    }

    private void publish(long[] sentBytes) {
        if (!server.isRunning()) return;
        boolean full = runs % FULL_EVERY == 0;
        // Settings changed: everyone is sent everything again, as they now say.
        if (full && settings.refresh()) greeted = new HashSet<>();
        ServerConfig config = settings.get();
        if (!loaded) load(config);
        tell();
        List<PositionsMessage.Player> placed = offered;
        if (placed != null) {
            offered = null;
            testPlayers(placed);
        }
        ShareRules rules = new ShareRules(config, this::teamOf);
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        update(online, config);
        // A player who just joined is sent the mobs as they are now, not as they were a few seconds ago.
        boolean joined = false;
        for (ServerPlayer player : online) joined |= !greeted.contains(player.getUUID()) && Network.canSend(player);
        if (full || joined) {
            refreshMobs();
            foresee(online, config, rules);
        }

        Set<UUID> sent = new HashSet<>();
        List<PositionsMessage.Player> shown = onlinePlayers();
        byte[] plain = null;
        byte[] seed = null;
        for (ServerPlayer player : online) {
            if (!Network.canSend(player)) continue;
            sent.add(player.getUUID());
            ShareRules.Viewer viewer = viewer(player);
            if (full || !greeted.contains(player.getUUID())) {
                // Every few seconds, and at once to a player who just joined: everything.
                byte[] everyone = PositionsMessage.players(withLooks(rules.online(shown, viewer), player));
                List<byte[]> offline = PositionsMessage.offline(
                        withLooks(rules.offline(offlinePlayers(), viewer), player));
                byte[] around = PositionsMessage.mobs(
                        rules.mobs(viewer.dimension()) ? mobsAround(player, config) : noMobs(player));
                sentBytes[0] += everyone.length + around.length;
                Network.send(player, everyone);
                for (byte[] part : offline) {
                    sentBytes[0] += part.length;
                    Network.send(player, part);
                }
                Network.send(player, around);
                if (seed == null) seed = PositionsMessage.seed(seed(config, rules));
                sentBytes[0] += seed.length;
                Network.send(player, seed);
            } else if (rules.sameForAll()) {
                if (plain == null) plain = PositionsMessage.players(rules.online(shown, null));
                sentBytes[0] += plain.length;
                Network.send(player, plain);
            } else {
                byte[] own = PositionsMessage.players(rules.online(shown, viewer));
                sentBytes[0] += own.length;
                Network.send(player, own);
            }
        }
        greeted = sent;
        if (++runs % SAVE_EVERY == 0 && dirty) save();
    }

    /**
     * The test players the host has placed now, from the host's game: taken at the next
     * publication, on the server's thread. Nothing is handed to the server's queue for it.
     */
    public void offerTestPlayers(List<PositionsMessage.Player> placed) {
        offered = List.copyOf(placed);
    }

    private void testPlayers(List<PositionsMessage.Player> placed) {
        Map<String, PositionsMessage.Player> now = new LinkedHashMap<>();
        for (PositionsMessage.Player player : placed) {
            if (!players.containsKey(player.name()) || !players.get(player.name()).online()) now.put(player.name(), player);
        }
        if (!now.equals(tests)) greeted = new HashSet<>();
        tests = now;
    }

    private void tell() {
        if (told) return;
        if (server.isDedicatedServer()) {
            LOGGER.info("Living Horizon: sharing the positions of this server with the players who have the mod");
            told = true;
        } else if (server.isPublished()) {
            LOGGER.info("Living Horizon: sharing the positions of this LAN world with the players who join with the mod");
            told = true;
        }
    }

    private void update(List<ServerPlayer> online, ServerConfig config) {
        Set<String> names = new HashSet<>();
        hidden.clear();
        for (ServerPlayer player : online) {
            String name = player.getScoreboardName();
            names.add(name);
            // Left as they were last seen, online or not: they log off from there.
            if (config.hideSpectators && player.isSpectator() || config.hideInvisible && player.isInvisible()) {
                hidden.add(name);
                continue;
            }
            Entity vehicle = player.getVehicle();
            PositionsMessage.Mount mount = vehicle != null ? mountOf(player, name) : null;
            if (mount != null) {
                riding.put(name, mount);
            } else {
                riding.remove(name);
                ridden.remove(name);
            }
            if (runs % LOOK_EVERY == 0 || !looks.containsKey(name)) looks.put(name, lookOf(player));
            // What they ride, whatever it is: a client that remembers it (a mob shared, a plane it
            // met) draws it under them, and never on its own again.
            Entity root = player.getRootVehicle();
            UUID published = root != player ? root.getUUID() : null;
            // Gliding with an elytra: their pitch too, which tilts the body along the flight.
            boolean gliding = player.isFallFlying();
            players.put(name, new PositionsMessage.Player(name, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), SharedPositions.dimensionId(player.level().dimension()), true,
                    vehicle != null, published, null, null, 0, false, gliding, gliding ? player.getXRot() : 0f));
            dirty = true;
        }
        // Those who logged off stay where they were last, as they looked, on what they rode.
        for (Map.Entry<String, PositionsMessage.Player> entry : players.entrySet()) {
            PositionsMessage.Player player = entry.getValue();
            if (player.online() && !names.contains(entry.getKey())) entry.setValue(loggedOff(player));
        }
    }

    /** An online player as they are now, kept once they log off. */
    private PositionsMessage.Player loggedOff(PositionsMessage.Player player) {
        PositionsMessage.Mount mount = riding.remove(player.name());
        UUID taken = ridden.remove(player.name());
        // The server took the mount away with them: it rests under them now, no longer a mob of its own.
        if (mount != null && taken != null && mobs.remove(taken) != null) reads.remove(taken);
        PositionsMessage.Look look = looks.remove(player.name());
        return new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(), player.yaw(),
                player.dimension(), false, mount != null, null, mount, look, System.currentTimeMillis(), false);
    }

    private static PositionsMessage.Look lookOf(ServerPlayer player) {
        Property skin = null;
        for (Property property : Profiles.properties(player.getGameProfile()).get("textures")) {
            skin = property;
            break;
        }
        return new PositionsMessage.Look(skin == null ? null : Profiles.value(skin), skin == null ? null : Profiles.signature(skin),
                Gear.of(player, player.level().registryAccess()));
    }

    /**
     * What {@code player} rides, as it would rest under them once they log off; null when they
     * share it with another player (the server leaves it to them), or it cannot be saved.
     */
    private PositionsMessage.@Nullable Mount mountOf(ServerPlayer player, String name) {
        Entity root = player.getRootVehicle();
        for (Entity passenger : root.getIndirectPassengers()) {
            if (passenger instanceof Player other && other != player) return null;
        }
        PositionsMessage.Mount last = riding.get(name);
        String nbt;
        if (last != null && root.getUUID().equals(ridden.get(name)) && runs % FULL_EVERY != 0) {
            nbt = last.nbt();
        } else {
            try {
                nbt = EntityNbt.save(root, root.level()).toString();
            } catch (RuntimeException e) {
                return null;
            }
            if (nbt.getBytes(StandardCharsets.UTF_8).length > PositionsMessage.Mount.MAX_NBT) return null;
            ridden.put(name, root.getUUID());
        }
        Vec3 seat = player.position().subtract(root.position()).yRot((float) Math.toRadians(root.getYRot()));
        // The body, not the head: a rider looking aside still sits facing the way of their mount.
        return new PositionsMessage.Mount(EntityType.getKey(root.getType()).toString(), nbt, seat.x, seat.y, seat.z,
                root.getYRot(), player.yBodyRot - root.getYRot());
    }

    /** The players online, but those left out right now. */
    private List<PositionsMessage.Player> onlinePlayers() {
        List<PositionsMessage.Player> now = new ArrayList<>();
        for (PositionsMessage.Player player : players.values()) {
            if (player.online() && !hidden.contains(player.name())) now.add(player);
        }
        return now;
    }

    private ShareRules.Viewer viewer(ServerPlayer player) {
        String name = player.getScoreboardName();
        return new ShareRules.Viewer(name, SharedPositions.dimensionId(player.level().dimension()), player.getX(),
                player.getZ(), teamOf(name));
    }

    /** The name of a player's team, online or not; null when they have none. */
    private @Nullable String teamOf(String name) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(name);
        return team == null ? null : team.getName();
    }

    /**
     * Every player who logged off, but those online again and left out, and the host's test
     * players. A client chooses how many it shows.
     */
    private List<PositionsMessage.Player> offlinePlayers() {
        List<PositionsMessage.Player> gone = new ArrayList<>();
        for (PositionsMessage.Player player : players.values()) {
            if (!player.online() && !tests.containsKey(player.name()) && !hidden.contains(player.name())) gone.add(player);
        }
        gone.addAll(tests.values());
        return gone;
    }

    /**
     * The players, the {@link #MAX_LOOKS} nearest {@code to} first, with how they look and what
     * they ride, then the others by their position alone, which keeps the message small on a big
     * server. Should the message still be too large, those at the end are by their position
     * alone (players online) or sent in a message after it (players who logged off).
     */
    private List<PositionsMessage.Player> withLooks(List<PositionsMessage.Player> list, ServerPlayer to) {
        List<PositionsMessage.Player> nearest = PlayerLists.nearest(list,
                SharedPositions.dimensionId(to.level().dimension()), to.getX(), to.getZ(), MAX_LOOKS);
        Set<String> dressed = new HashSet<>();
        List<PositionsMessage.Player> sent = new ArrayList<>(list.size());
        for (PositionsMessage.Player player : nearest) {
            dressed.add(player.name());
            sent.add(player.online() ? player.full(riding.get(player.name()), looks.get(player.name())) : player);
        }
        for (PositionsMessage.Player player : list) {
            if (!dressed.contains(player.name())) sent.add(player.plain());
        }
        return sent;
    }

    private void refreshMobs() {
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = SharedPositions.dimensionId(level.dimension());
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved()) continue;
                String type = EntityType.getKey(entity.getType()).toString();
                Integer kind = KINDS.get(type);
                if (kind == null) continue;
                UUID id = entity.getUUID();
                Read read = reads.get(id);
                // Its saved data now and then only, each mob at its own time: what it wears seldom changes.
                if (read == null || runs - read.at() >= MOB_READ_EVERY + (id.hashCode() & 63)) {
                    read = read(entity, level, type);
                    reads.put(id, read);
                }
                boolean baby = entity instanceof LivingEntity living && living.isBaby();
                seen.add(id);
                // Moved to the end: the mob seen longest ago comes first, and goes first.
                mobs.remove(id);
                mobs.put(id, new PositionsMessage.Mob(id, entity.getX(), entity.getY(), entity.getZ(), kind, dimension,
                        baby, read.variant(), read.extra()));
                dirty = true;
            }
            // Its chunk is loaded and it is not there: dead, despawned, or gone through a portal.
            mobs.values().removeIf(mob -> mob.dimension().equals(dimension) && !seen.contains(mob.id())
                    && level.areEntitiesLoaded(chunk(mob.x(), mob.z())));
        }
        while (mobs.size() > MAX_MOBS) mobs.remove(mobs.keySet().iterator().next());
        reads.keySet().retainAll(mobs.keySet());
    }

    /** A mob's variant, and what it wears, from its saved data. */
    private Read read(Entity entity, ServerLevel level, String type) {
        CompoundTag tag;
        try {
            tag = EntityNbt.save(entity, level);
        } catch (RuntimeException e) {
            return new Read(0, null, runs);
        }
        String field = MobKinds.variantField(type);
        int variant = field != null ? number(tag, field) & 2047 : 0;
        CompoundTag kept = new CompoundTag();
        for (String key : EXTRA_KEYS) {
            Tag value = tag.get(key);
            if (value != null) kept.put(key, value.copy());
        }
        String extra = kept.isEmpty() ? null : kept.toString();
        return new Read(variant, extra != null && extra.length() <= MAX_EXTRA ? extra : null, runs);
    }

    private PositionsMessage.Mobs mobsAround(ServerPlayer player, ServerConfig config) {
        String dimension = SharedPositions.dimensionId(player.level().dimension());
        PositionsMessage.Mobs around = new PositionsMessage.Mobs(new ArrayList<>(), dimension,
                player.getX(), player.getZ(), MOB_RANGE);
        for (PositionsMessage.Mob mob : mobs.values()) {
            if (around.covers(mob.dimension(), mob.x(), mob.z())) around.mobs().add(mob);
        }
        if (config.seedMobs) {
            seeded.addAround((ServerLevel) player.level(), player.getX(), player.getZ(), config.seedMobRange, around.mobs());
        }
        return around;
    }

    /**
     * The world's seed, when the owner shares it ({@code shareSeed}), with the dimensions whose
     * generator a client can build again: the game's own, where mobs are shared.
     */
    private PositionsMessage.Seed seed(ServerConfig config, ShareRules rules) {
        if (!config.shareSeed || !config.seedMobs) return PositionsMessage.Seed.NONE;
        List<PositionsMessage.SeedGround> grounds = new ArrayList<>();
        long seed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = SharedPositions.dimensionId(level.dimension());
            if (!rules.mobs(dimension)) continue;
            String[] keys;
            try {
                keys = SeedDraws.vanillaKeys(level);
            } catch (RuntimeException e) {
                keys = null;
            }
            if (keys == null) continue;
            grounds.add(new PositionsMessage.SeedGround(dimension, keys[0], keys[1]));
            seed = level.getSeed();
        }
        return grounds.isEmpty() ? PositionsMessage.Seed.NONE : new PositionsMessage.Seed(seed, grounds);
    }

    /** The animals of the chunks not generated yet, worked out around the players they are shared with. */
    private void foresee(List<ServerPlayer> online, ServerConfig config, ShareRules rules) {
        List<ServerPlayer> viewers = new ArrayList<>();
        if (config.seedMobs) {
            for (ServerPlayer player : online) {
                if (Network.canSend(player) && rules.mobs(SharedPositions.dimensionId(player.level().dimension()))) {
                    viewers.add(player);
                }
            }
        }
        seeded.update(server, viewers, config.seedMobRange);
    }

    /** No mobs, in a circle that covers nothing: the client keeps the mobs it remembers. */
    private static PositionsMessage.Mobs noMobs(ServerPlayer player) {
        return new PositionsMessage.Mobs(List.of(), SharedPositions.dimensionId(player.level().dimension()),
                player.getX(), player.getZ(), -1);
    }

    // --- The file ------------------------------------------------------------------------

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    private void load(ServerConfig config) {
        loaded = true;
        readFile();
        // Those the world saved before the mod saw them log off: logged off where they were.
        int found = 0;
        for (PositionsMessage.Player player : WorldPlayers.read(server, config.hideSpectators)) {
            if (players.putIfAbsent(player.name(), player) == null) found++;
        }
        if (found > 0) {
            dirty = true;
            LOGGER.info("Living Horizon: {} players of this world shown where they logged off", found);
        }
    }

    private void readFile() {
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try {
            byte[] data = Files.readAllBytes(file);
            PositionsMessage.Saved saved = PositionsMessage.readSaved(data);
            if (saved == null) {
                // Kept aside, never written over: the next save starts a new file beside it.
                Path bad = file.resolveSibling(FILE + ".bad");
                Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
                PositionsMessage.Format format = PositionsMessage.format(data);
                LOGGER.warn("Living Horizon: could not read the shared positions of this world ({}), kept the file as {}",
                        format == null || format.readableBy(PositionsMessage.FILE_VERSION) ? "damaged"
                                : "version " + format.version() + ", this one reads " + PositionsMessage.FILE_VERSION,
                        bad.getFileName());
                return;
            }
            PositionsMessage.Format format = PositionsMessage.format(data);
            if (format != null && format.version() > PositionsMessage.FILE_VERSION) {
                // Saving it again leaves out what this version does not know: a copy first.
                Path copy = file.resolveSibling(FILE + ".v" + format.version());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("Living Horizon: the shared positions of this world were saved by a newer version of the mod, "
                        + "kept a copy as {}", copy.getFileName());
            }
            // Whoever was online when it was saved logged off then.
            for (PositionsMessage.Player player : saved.players()) {
                players.put(player.name(), new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(),
                        player.yaw(), player.dimension(), false, player.mount() != null, null, player.mount(),
                        player.look(), player.lastSeen(), false));
            }
            for (PositionsMessage.Mob mob : saved.mobs()) mobs.put(mob.id(), mob);
        } catch (IOException e) {
            LOGGER.warn("Could not read the shared positions of this world", e);
        }
    }

    /** Writes the file, when something changed; also when the server stops. */
    public void save() {
        if (!loaded || !dirty) return;
        dirty = false;
        Path file = file();
        long started = Profile.begin(Profile.Section.SAVE_POSITIONS);
        long bytes = -1;
        try {
            Path written = file.resolveSibling(FILE + ".tmp");
            // Those still online when the server stops log off as they are, on what they ride.
            List<PositionsMessage.Player> kept = new ArrayList<>();
            for (PositionsMessage.Player player : players.values()) {
                kept.add(player.online()
                        ? new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(), player.yaw(),
                                player.dimension(), true, riding.containsKey(player.name()), null,
                                riding.get(player.name()), looks.get(player.name()), System.currentTimeMillis(), false)
                        : player);
            }
            byte[] saved = PositionsMessage.saved(kept, new ArrayList<>(mobs.values()));
            bytes = saved.length;
            Files.write(written, saved);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not save the shared positions of this world", e);
        } finally {
            Profile.end(Profile.Section.SAVE_POSITIONS, started, bytes);
        }
    }

    // ChunkPos became a record in 26.1, its packed form a method of its own.
    private static long chunk(double x, double z) {
        ChunkPos pos = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }

    private static int number(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, 0);
        //?} else {
        /*return tag.getInt(key);
        *///?}
    }
}
