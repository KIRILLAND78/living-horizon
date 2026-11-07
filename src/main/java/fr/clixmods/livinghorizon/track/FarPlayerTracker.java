package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every other player on the server, followed once per client tick from what a client
 * receives: the entities the server sends, and the positions a server with the mod shares
 * ({@link SharedPositions}).
 *
 * <p>Nothing is sent to the server. Past the server's view distance, a player is placed
 * only where the shared positions say; without them, they are not shown.
 */
public final class FarPlayerTracker {
    private static final FarPlayerTracker INSTANCE = new FarPlayerTracker();

    private final Map<UUID, FarPlayer> players = new HashMap<>();
    private final SharedPositions shared = new SharedPositions();
    private final RestingPlayers resting = new RestingPlayers();
    private final MobMemory mobs = new MobMemory();
    private final Ambience ambience = new Ambience();
    private @Nullable ClientLevel level;
    private @Nullable String server;
    private int packScan;
    private int testShare;
    /** The mounts far players rode last tick, where their copies were: one got off is left there. */
    private Map<UUID, Vec3> lastCarried = Map.of();
    private Map<UUID, Float> lastCarriedYaw = Map.of();
    private int sinceJoin;
    private boolean warnedNoSharing;
    private @Nullable String warning;

    public static FarPlayerTracker get() {
        return INSTANCE;
    }

    /** Whether the server shares the positions (it runs the mod). */
    public boolean sharing() {
        return shared.active();
    }

    /** What the server shares, as last received: for the game tests and the debug screens. */
    public SharedPositions shared() {
        return shared;
    }

    /** A message from the server ({@code PositionsMessage}), on the game's thread. */
    public void receive(byte[] data) {
        shared.receive(data);
    }

    public Collection<FarPlayer> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** Birds and bats in the sky. */
    public Ambience ambience() {
        return ambience;
    }

    /** Mobs met on the way, kept where they were. */
    public MobMemory mobs() {
        return mobs;
    }

    /** Players who logged off and rest where they were. */
    public RestingPlayers resting() {
        return resting;
    }

    public void tick(Minecraft minecraft) {
        ClientLevel current = minecraft.level;
        LocalPlayer self = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (current == null || self == null || connection == null) {
            // Left the world, or the world closed under everyone: they all logged off where they were.
            if (server != null) restEveryone();
            players.clear();
            lastCarried = Map.of();
            shared.clear();
            level = null;
            if (server != null) {
                LodWorld.suspend();
                resting.close();
                mobs.close();
                ambience.calendar().close();
                server = null;
            }
            return;
        }
        if (current != level) {
            // Another dimension or another server: every position known is meaningless.
            players.clear();
            lastCarried = Map.of();
            resting.forgetPuppets();
            mobs.forgetPuppets();
            LodWorld.clear();
            level = current;
            String joined = serverKey(minecraft);
            if (!joined.equals(server)) {
                resting.open(joined);
                mobs.open(joined);
                ambience.calendar().open(joined);
                server = joined;
                // What the server shared is kept: it sends everything as soon as the player joins,
                // maybe before this first tick. It was cleared when the last world was left.
                sinceJoin = 0;
                warnedNoSharing = false;
                warning = null;
                // Opening the memory forgets the mobs sent so far: the level may have loaded
                // those round the player before this first tick, and loads them only once.
                for (Entity entity : current.entitiesForRendering()) mobs.onLoad(entity);
            }
        }
        resting.maintain();
        // Paused in single player: the world is frozen, and so is everything drawn on its horizon.
        if (minecraft.isPaused()) return;
        FarConfig config = FarConfig.get();
        // A world the mod has never been in: read its mobs at once, the way a scan would later.
        if (mobs.takeFirstVisit() && (config.distantMobs || config.distantEntities) && MobScan.available(minecraft)) {
            MobScan.start(minecraft, config.scanRadius);
        }
        String dimension = SharedPositions.dimensionId(current.dimension());

        Set<UUID> live = new HashSet<>();
        for (AbstractClientPlayer player : current.players()) {
            if (player == self || player.isRemoved()) continue;
            players.computeIfAbsent(player.getUUID(), FarPlayer::new).observeLive(player, dimension);
            live.add(player.getUUID());
        }

        // The server stops sending a player at the edge of its view distance. Lost well
        // inside it, the player was removed rather than left behind. The server's, not the
        // render distance: Bobby draws terrain past it, with nobody on it.
        double vanishRadius = Math.max(16, ServerView.chunks(minecraft.options) * 16 - 40);

        shared.update();
        // Ten seconds after joining a server, no sharing at all: say so once, in the log, and in
        // the chat while distant players are on (turning them off silences it).
        // A world played alone is shared by its own server; never warned about.
        if (!warnedNoSharing && ++sinceJoin >= 200) {
            warnedNoSharing = true;
            PositionsMessage.Format unreadable = shared.unreadable();
            if (!shared.active() && unreadable != null) {
                // The server has the mod, in a version too far from this one.
                boolean newer = unreadable.version() > PositionsMessage.VERSION;
                LivingHorizonClient.LOGGER.warn("Living Horizon: the server shares format v{}, this mod reads v{}, so distant "
                        + "players are not shown. Update the mod on {}.", unreadable.version(), PositionsMessage.VERSION,
                        newer ? "this game" : "the server");
                if (config.enabled) warn(minecraft, newer ? "livinghorizon.sharing.updateGame" : "livinghorizon.sharing.updateServer");
            } else if (!shared.active() && minecraft.getSingleplayerServer() == null) {
                LivingHorizonClient.LOGGER.warn("Living Horizon: this server does not have the mod, so distant players are not shown. "
                        + "Put the mod in the server's mods folder, or ask the host of a LAN world to use the mod.");
                if (config.enabled) warn(minecraft, "livinghorizon.sharing.none");
            }
        }

        Set<String> online = new HashSet<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            UUID id = Profiles.id(info.getProfile());
            String name = Profiles.name(info.getProfile());
            online.add(name.toLowerCase(Locale.ROOT));
            resting.wake(name);
            if (id.equals(self.getUUID())) continue;

            FarPlayer player = players.get(id);
            if (player != null) player.profile = info.getProfile();
            if (live.contains(id)) continue;

            SharedPositions.Report report = shared.read(name);
            if (player == null) {
                if (report == null) continue;
                player = new FarPlayer(id);
                player.profile = info.getProfile();
                players.put(id, player);
            }
            UUID vehicle = report != null && report.riding() ? shared.vehicle(name) : null;
            player.observeRemote(current, self, info, report, vehicle, shared.mount(name), shared.look(name), dimension,
                    vanishRadius, config, mobs);
        }

        // Gone from the tab list: logged off. They rest where they were last known.
        players.values().removeIf(player -> {
            if (connection.getPlayerInfo(player.id()) != null) return false;
            if (config.offlinePlayers && player.profile != null && player.knownDimension() != null) {
                RestingPlayers.Mount mount = player.mountToRest();
                resting.rest(player.profile, player.knownDimension(), player.x(), player.y(), player.z(), player.yaw(),
                        mount, player.gearToRest());
                // The server took their mount away with them: the copy under them stands for it,
                // not a remembered mob where it was last sent.
                Entity ridden = player.mount();
                UUID riddenId = ridden != null ? ridden.getUUID() : player.carriedMount();
                if (mount != null && riddenId != null) mobs.forgetRidden(riddenId);
            }
            return true;
        });

        // The server keeps the last position of everyone who ever played there.
        if (config.offlinePlayers && shared.active() && ++packScan >= 20) {
            packScan = 0;
            for (String name : shared.holders()) {
                if (online.contains(name.toLowerCase(Locale.ROOT))) continue;
                SharedPositions.Report report = shared.read(name);
                if (report != null) resting.restFromServer(name, report, shared.mount(name), shared.look(name));
            }
            resting.dropHostTests(new HashSet<>(shared.holders()));
        }
        // The host's test players, handed to the server of this game, which shares them with the others.
        IntegratedServer local = minecraft.getSingleplayerServer();
        if (local != null && ++testShare >= 20) {
            testShare = 0;
            List<PositionsMessage.Player> placed = resting.placedTests(current.registryAccess());
            PositionPublisher.of(local).offerTestPlayers(placed);
        }
        long restingStarted = Profile.begin(Profile.Section.TICK_RESTING);
        try {
            if (config.offlinePlayers) resting.tick(current, config);
            resting.aim(minecraft, config);
        } finally {
            Profile.end(Profile.Section.TICK_RESTING, restingStarted);
        }
        long ambient = Profile.begin(Profile.Section.TICK_AMBIENCE);
        try {
            ambience.tick(current, self, config);
        } finally {
            Profile.end(Profile.Section.TICK_AMBIENCE, ambient);
        }
        // A mount drawn under its far rider is not drawn again on its own.
        Map<UUID, Vec3> carried = new HashMap<>();
        Map<UUID, Float> carriedYaw = new HashMap<>();
        for (FarPlayer player : players.values()) {
            UUID mount = player.carriedMount();
            if (mount == null) continue;
            Entity copy = player.mountCopy();
            carried.put(mount, copy != null ? copy.position() : new Vec3(player.x(), player.y(), player.z()));
            carriedYaw.put(mount, copy != null ? copy.getYRot() : player.yaw());
        }
        // Got off: the mount stays where it was left, rather than walking back from where it was last remembered.
        for (Map.Entry<UUID, Vec3> was : lastCarried.entrySet()) {
            if (!carried.containsKey(was.getKey())) {
                mobs.released(was.getKey(), was.getValue(), lastCarriedYaw.getOrDefault(was.getKey(), 0f));
            }
        }
        lastCarried = carried;
        lastCarriedYaw = carriedYaw;
        mobs.carried(carried.keySet());
        mobs.tick(current, self, ServerView.chunks(minecraft.options) * 16, config,
                shared.active() ? shared : null);
    }

    /** Everyone followed rests where they were last known, as they looked, on what they rode. */
    private void restEveryone() {
        if (!FarConfig.get().offlinePlayers) return;
        for (FarPlayer player : players.values()) {
            if (player.profile == null || player.knownDimension() == null || !player.placed() || player.vanished()
                    || player.source() == FarPlayer.Source.LOST) {
                continue;
            }
            resting.rest(player.profile, player.knownDimension(), player.x(), player.y(), player.z(), player.yaw(),
                    player.mountToRest(), player.gearToRest());
        }
    }

    /** Says in the chat why distant players are not shown on this server: {@code key} names why. */
    private void warn(Minecraft minecraft, String key) {
        warning = key;
        Component message = Component.translatable(key).withStyle(ChatFormatting.YELLOW);
        //? if >=26.1 {
        /*minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        minecraft.gui.getChat().addMessage(message);
        //?}
    }

    /** What the chat was last told about this server's sharing, as a translation key: null when nothing. For the game tests. */
    public @Nullable String warning() {
        return warning;
    }

    /** Which server this is, as a name for the file of who rests where. */
    private static String serverKey(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) return data.ip;
        IntegratedServer local = minecraft.getSingleplayerServer();
        // The folder, not the name: two worlds may be called the same.
        if (local != null) {
            Path folder = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return "local-" + (folder != null ? folder : local.getWorldData().getLevelName());
        }
        return "unknown";
    }
}
