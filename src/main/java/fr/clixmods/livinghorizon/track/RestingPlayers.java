package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.JsonStore;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Players who logged off, left where they were last.
 *
 * <p>Remembered per server, across sessions, in
 * {@code config/livinghorizon/resting/<server>.json}: a friend who logged off yesterday
 * is still napping where they stopped when you come back. When the server runs the mod,
 * it keeps the position of every player who ever played there and shares it, so even
 * someone who left before you joined gets a spot; their skin is then fetched from Mojang
 * by name, once.
 */
public final class RestingPlayers {
    /**
     * Where one player rests. Keyed by name: the server shares names only.
     *
     * @param dimension the id of the dimension they rest in, {@code minecraft:overworld}
     * @param lastSeen when they logged off, in milliseconds since 1970; 0 when unknown (seen
     *                 by the server alone, or saved by a version that did not keep it)
     * @param mount    what they rode when they logged off, or null
     * @param gear     what they wore and held ({@link Gear}), or null when not known
     * @param rest     where they really rest when it is not their spot (in the air, in water), or
     *                 null when it is, or not known yet
     */
    public record Spot(String name, @Nullable UUID id, @Nullable String textures, @Nullable String signature,
                       String dimension, double x, double y, double z, float yaw, long lastSeen,
                       @Nullable Mount mount, @Nullable List<String> gear, @Nullable Rest rest) {

        public Spot(String name, @Nullable UUID id, @Nullable String textures, @Nullable String signature,
                    String dimension, double x, double y, double z, float yaw, long lastSeen,
                    @Nullable Mount mount, @Nullable List<String> gear) {
            this(name, id, textures, signature, dimension, x, y, z, yaw, lastSeen, mount, gear, null);
        }

        /** Somewhere else: where they rest there is found again. */
        Spot moved(String dimension, double x, double y, double z, float yaw, @Nullable Mount mount) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw, lastSeen, mount, gear, null);
        }

        /** The same player, wearing this skin. */
        Spot skinned(Property skin) {
            return new Spot(name, id, Profiles.value(skin), Profiles.signature(skin), dimension, x, y, z, yaw, lastSeen,
                    mount, gear, rest);
        }

        /** The same player, in this skin and this gear, logged off at {@code lastSeen} when it is known. */
        Spot looking(@Nullable String textures, @Nullable String signature, @Nullable List<String> gear, long lastSeen) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw,
                    lastSeen != 0 ? lastSeen : this.lastSeen, mount, gear, rest);
        }

        Spot dressed(GameProfile profile) {
            Property skin = skinOf(profile);
            return new Spot(name, Profiles.id(profile), skin == null ? textures : Profiles.value(skin),
                    skin == null ? signature : Profiles.signature(skin), dimension, x, y, z, yaw, lastSeen, mount, gear,
                    rest);
        }

        /** The same player, resting there. */
        Spot resting(@Nullable Rest rest) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw, lastSeen, mount, gear, rest);
        }

        GameProfile profile() {
            UUID uuid = id != null ? id : UUIDUtil.createOfflinePlayerUUID(name);
            if (textures == null) return new GameProfile(uuid, name);
            return Profiles.withSkin(uuid, name, new Property("textures", textures, signature));
        }
    }

    /**
     * What a player rode when they logged off: a horse, a boat, a happy ghast. The server
     * takes it away with them; it is drawn under them all the same, and carries them.
     *
     * @param type     the entity type, {@code minecraft:horse}
     * @param nbt      the mount as the game saves it (coat, saddle, armour, name)
     * @param data     what the server sent of it that the save leaves out on the client (a
     *                 saddle before 1.21.5), as {@link RestingMount#dataOf} writes it; may be null
     * @param seatX    where the rider sits on it, with {@code seatY} and {@code seatZ}: the rider's
     *                 feet from the mount's, in the mount's own frame, as {@link FarPlayer} keeps them
     * @param yaw      the way it faced
     * @param riderYaw the way the rider faced, from the mount's
     */
    public record Mount(String type, String nbt, @Nullable String data, double seatX, double seatY, double seatZ,
                        float yaw, float riderYaw) {

        /**
         * The mount {@code vehicle} as it is now, its rider's feet {@code seat} from its own
         * (in its frame); null when the game cannot save it on the client.
         */
        static @Nullable Mount of(Entity vehicle, Vec3 seat, float yaw, float riderYaw) {
            return of(vehicle, seat, yaw, riderYaw, Vec3.ZERO, null);
        }

        /**
         * The same, knowing how fast its rider last went ({@code going}, blocks a tick) and
         * whether it was off the ground ({@code aloft}; null to take the entity's word): what
         * the client sees of a vehicle the server moves says little of either.
         */
        static @Nullable Mount of(Entity vehicle, Vec3 seat, float yaw, float riderYaw, Vec3 going,
                                  @Nullable Boolean aloft) {
            try {
                CompoundTag saved = EntityNbt.save(vehicle, vehicle.level());
                RestingMount.keepMotion(vehicle, saved, going);
                if (aloft != null) saved.putBoolean("OnGround", !aloft);
                return new Mount(EntityType.getKey(vehicle.getType()).toString(), saved.toString(),
                        RestingMount.dataOf(vehicle), seat.x, seat.y, seat.z, yaw, riderYaw);
            } catch (RuntimeException e) {
                // A modded mount the game cannot save from the client: they rest on foot.
                return null;
            }
        }

        /** The same mount, as a server shares it. */
        PositionsMessage.Mount shared() {
            return new PositionsMessage.Mount(type, nbt, seatX, seatY, seatZ, yaw, riderYaw);
        }

        /** The server's copy of a mount; null for none. */
        static @Nullable Mount of(PositionsMessage.@Nullable Mount shared) {
            return shared == null ? null : new Mount(shared.type(), shared.nbt(), null, shared.seatX(), shared.seatY(),
                    shared.seatZ(), shared.yaw(), shared.riderYaw());
        }

        /** Whether a mount flies, as a happy ghast does: it stays in the air, at its height. */
        public static boolean flies(Entity vehicle) {
            RestingMount.Kind kind = RestingMount.kindOf(vehicle);
            return kind == RestingMount.Kind.AIR || kind == RestingMount.Kind.HOVER || kind == RestingMount.Kind.CIRCLE;
        }

        /** The same mount, turned to {@code facing}, its rider turned with it. */
        public Mount facing(float facing) {
            return new Mount(type, nbt, data, seatX, seatY, seatZ, facing, riderYaw);
        }

        /** What {@code rider} rides now; null when nothing. For the debug command's test players. */
        public static @Nullable Mount ridden(Entity rider) {
            Entity vehicle = rider.getRootVehicle();
            if (vehicle == rider) return null;
            Vec3 seat = rider.position().subtract(vehicle.position()).yRot((float) Math.toRadians(vehicle.getYRot()));
            float body = rider instanceof LivingEntity living ? living.yBodyRot : rider.getYRot();
            return of(vehicle, seat, vehicle.getYRot(), body - vehicle.getYRot());
        }
    }

    /**
     * Where a player who logged off in the air or in water really rests: the ground under them
     * (they logged off flying, with an elytra or not), the nearest shore, or a boat on the water
     * when there is none. Found once the blocks around are loaded, or guessed from the far world
     * before; kept with the spot, so that it holds far away, when the figure is made again,
     * and in the next game.
     *
     * @param boat in a boat, {@code y} being the water's surface
     */
    public record Rest(double x, double y, double z, boolean boat) {
        Vec3 at() {
            return new Vec3(x, y, z);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * Layout of the file: 2 names each spot's dimension by its id; 1 numbered it (0 overworld, 1
     * nether, 2 end, 3 any other); 0 is the bare list, before the list came with its version.
     */
    static final int VERSION = 2;
    /** Ticks between two saves of the file, when something changed: five seconds. */
    private static final int SAVE_EVERY = 100;
    /** Milliseconds between two skins asked of Mojang, which turns away a client asking too often. */
    private static final long LOOKUP_GAP = 1500;

    private final Map<String, Spot> spots = new LinkedHashMap<>();
    /**
     * Each spot's puppet, found by the spot itself, so that drawing a frame works out no key.
     * A spot replaced or removed takes its puppet with it.
     */
    private final Map<Spot, RestingPuppet> puppets = new IdentityHashMap<>();
    /** Names whose skin was asked for, or will be: each once. */
    private final Set<String> lookups = new HashSet<>();
    /** Names waiting for their skin to be asked, one at a time ({@link #LOOKUP_GAP}). */
    private final ArrayDeque<String> queued = new ArrayDeque<>();
    private boolean lookingUp;
    private long nextLookup;
    /** Something changed since the file was written: written at the next {@link #SAVE_EVERY}. */
    private boolean dirty;
    private int clock;
    /** Test players placed with {@code /livinghorizon debug offline test}: never written to the file. */
    private final Set<String> fakes = new HashSet<>();
    /** Test players the host of this world placed, which its server shares: among {@link #fakes}. */
    private final Set<String> fromHost = new HashSet<>();
    private @Nullable Path file;
    private @Nullable Spot lookedAt;
    /** Who is drawn now ({@link #shown}). */
    private List<Spot> shown = List.of();
    /** A spot changed or went since {@link #shown} was chosen: brought up to date when next asked. */
    private boolean shownStale;
    private boolean wasAttacking;

    /** Blocks from the eye to the point aimed at on a sleeper: about the reach of a hand. */
    private static final double MAX_AIM = 3;
    /**
     * Sections of blocks the puppets may copy per tick, all of them together, to look around
     * off the game's thread: a village of 50 blocks around in a few ticks.
     */
    private static final int WORK = 96;
    /** Which puppet is first served the shared {@link #WORK} this tick. */
    private int turn;

    /** Every player resting on this server, drawn or not. */
    public Collection<Spot> spots() {
        return Collections.unmodifiableCollection(spots.values());
    }

    /**
     * Those drawn: in the camera's dimension, the {@link FarConfig#maxOfflinePlayers} nearest
     * it, chosen again every tick ({@link #choose}). One woken or cleared since is left out at
     * once, one moved is there as it is now; drawing a frame otherwise works out nothing.
     */
    public List<Spot> shown() {
        if (shownStale) {
            shownStale = false;
            List<Spot> now = new ArrayList<>(shown.size());
            for (Spot spot : shown) {
                Spot current = spots.get(key(spot.name()));
                if (current != null && current.dimension().equals(spot.dimension())) now.add(current);
            }
            shown = now;
        }
        return shown;
    }

    /**
     * The spots of {@code dimension} nearest {@code x, z}, at most {@code most}
     * ({@link FarConfig#UNLIMITED_OFFLINE} and above: all of them), the nearest first.
     */
    static List<Spot> nearest(Collection<Spot> spots, String dimension, double x, double z, int most) {
        List<Spot> here = new ArrayList<>();
        for (Spot spot : spots) {
            if (spot.dimension().equals(dimension)) here.add(spot);
        }
        int count = most >= FarConfig.UNLIMITED_OFFLINE ? here.size() : Math.max(0, most);
        return PlayerLists.nearest(here, count, spot -> Math.hypot(spot.x() - x, spot.z() - z));
    }

    /** Chooses who is drawn ({@link #shown}); the puppets of the others are let go. */
    private void choose(ClientLevel level, FarConfig config) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        shown = camera == null ? List.of() : nearest(spots.values(), SharedPositions.dimensionId(level.dimension()),
                camera.getX(), camera.getZ(), config.maxOfflinePlayers);
        shownStale = false;
        Set<Spot> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(shown);
        puppets.keySet().retainAll(kept);
        if (lookedAt != null && !kept.contains(lookedAt)) lookedAt = null;
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir()
                .resolve("livinghorizon").resolve("resting").resolve(safe(server) + ".json");
        for (Spot spot : read(file)) spots.put(key(spot.name()), spot);
    }

    void close() {
        if (dirty) save();
        // Asked again on the next server, if they rest there too.
        for (String name : queued) lookups.remove(key(name));
        queued.clear();
        spots.clear();
        fakes.clear();
        fromHost.clear();
        puppets.clear();
        shown = List.of();
        lookedAt = null;
        file = null;
    }

    /** Puppets belong to one level; a new dimension needs new ones. */
    void forgetPuppets() {
        puppets.clear();
        shown = List.of();
        lookedAt = null;
    }

    // --- Who rests where ---------------------------------------------------------------

    /** A player who just logged off, at the last place this client knew, on what they rode, in what they wore. */
    void rest(GameProfile profile, String dimension, double x, double y, double z, float yaw, @Nullable Mount mount,
              @Nullable List<String> gear) {
        Spot spot = new Spot(Profiles.name(profile), Profiles.id(profile), null, null, dimension, x, y, z, yaw,
                System.currentTimeMillis(), mount, gear).dressed(profile);
        put(spot);
    }

    /**
     * The server's last word on an offline player, and the mount they took away with them: the
     * copy this client saw them ride when it is the same kind, the server's own otherwise.
     */
    void restFromServer(String name, SharedPositions.Report report, PositionsMessage.@Nullable Mount shared,
                        PositionsMessage.@Nullable Look look) {
        Spot known = spots.get(key(name));
        // A test player of the host's: shown here too, never saved, gone once the host clears it.
        if (report.test() && (known == null || fromHost.contains(key(name)))) {
            fakes.add(key(name));
            fromHost.add(key(name));
        }
        Mount mount = null;
        if (report.riding()) {
            Mount seen = known != null ? known.mount() : null;
            mount = seen != null && (shared == null || seen.type().equals(shared.type())) ? seen : Mount.of(shared);
        }
        boolean there = known != null && known.dimension().equals(report.dimension())
                && Math.abs(known.x() - report.x()) < 0.05 && Math.abs(known.y() - report.y()) < 0.05
                && Math.abs(known.z() - report.z()) < 0.05 && (known.mount() == null) == (mount == null);
        if (there && (look == null || looksLike(known, look))) return;
        // Where they rest is kept while they stay put: only how they look changed.
        Spot spot = there ? known
                : known != null
                ? known.moved(report.dimension(), report.x(), report.y(), report.z(), report.yaw(), mount)
                : new Spot(name, null, null, null, report.dimension(), report.x(), report.y(), report.z(), report.yaw(),
                        report.lastSeen(), mount, null);
        // The server knows their skin as it is now, and what they wore.
        if (look != null && !looksLike(spot, look)) {
            spot = spot.looking(look.textures() != null ? look.textures() : spot.textures(),
                    look.textures() != null ? look.signature() : spot.signature(), look.gear(), report.lastSeen());
        }
        put(spot);
        if (spot.textures() == null) lookUp(name);
    }

    /** Whether a spot shows what the server says of how its player looks. */
    private static boolean looksLike(Spot spot, PositionsMessage.Look look) {
        return (look.textures() == null || look.textures().equals(spot.textures())) && look.gear().equals(spot.gear());
    }

    /** The host's test players the server no longer lists: the host cleared them. */
    void dropHostTests(Set<String> listed) {
        Set<String> kept = new HashSet<>();
        for (String name : listed) kept.add(key(name));
        for (String name : List.copyOf(fromHost)) {
            if (kept.contains(name)) continue;
            fromHost.remove(name);
            fakes.remove(name);
            Spot gone = spots.remove(name);
            shownStale = true;
            if (gone != null) puppets.remove(gone);
            if (lookedAt != null && key(lookedAt.name()).equals(name)) lookedAt = null;
        }
    }

    /** The test players placed here, as the server of this game shares them with the others. */
    List<PositionsMessage.Player> placedTests(RegistryAccess registries) {
        List<PositionsMessage.Player> placed = new ArrayList<>();
        for (String name : fakes) {
            Spot spot = spots.get(name);
            if (spot == null || fromHost.contains(name)) continue;
            Mount mount = spot.mount();
            placed.add(new PositionsMessage.Player(spot.name(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.dimension(),
                    false, mount != null, null, mount == null ? null : mount.shared(),
                    new PositionsMessage.Look(spot.textures(), spot.signature(),
                            spot.gear() != null ? spot.gear() : List.of()),
                    spot.lastSeen(), true));
        }
        return placed;
    }

    /** Back online. */
    void wake(String name) {
        fromHost.remove(key(name));
        fakes.remove(key(name));
        Spot gone = spots.remove(key(name));
        shownStale = true;
        if (gone != null) {
            puppets.remove(gone);
            if (lookedAt != null && key(lookedAt.name()).equals(key(name))) lookedAt = null;
            dirty = true;
        }
    }

    /**
     * Writes the file now and then, when something changed, rather than at each change: a
     * server full of players who logged off sends them all as one joins. Asks for the next skin.
     * Every tick, whatever the settings.
     */
    void maintain() {
        if (++clock % SAVE_EVERY == 0 && dirty) save();
        nextLookUp();
    }

    private void put(Spot spot) {
        Spot old = spots.put(key(spot.name()), spot);
        shownStale = true;
        if (old != null) puppets.remove(old);
        if (!fakes.contains(key(spot.name()))) dirty = true;
    }

    /** Asks for the skin of a player this client never saw, in its turn ({@link #nextLookUp}). */
    private void lookUp(String name) {
        if (lookups.add(key(name))) queued.add(name);
    }

    /**
     * Fetches the next skin waiting, from Mojang, off the game thread: one at a time, at most
     * one every {@link #LOOKUP_GAP}, so that a server with hundreds of players who logged off
     * is not asked for all at once.
     */
    private void nextLookUp() {
        if (lookingUp || queued.isEmpty() || System.currentTimeMillis() < nextLookup) return;
        String name = queued.poll();
        // The game asks Mojang for a profile by name from 1.21.9; before, skins of players
        // never seen stay the default ones.
        //? if >=1.21.9 {
        Spot waiting = spots.get(key(name));
        if (waiting == null || waiting.textures() != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        lookingUp = true;
        nextLookup = System.currentTimeMillis() + LOOKUP_GAP;
        CompletableFuture
                .supplyAsync(() -> minecraft.services().profileResolver().fetchByName(name), Util.ioPool())
                .exceptionally(e -> Optional.empty())
                .thenAcceptAsync(found -> {
                    lookingUp = false;
                    found.ifPresent(profile -> {
                        Spot spot = spots.get(key(name));
                        if (spot != null && spot.textures() == null) put(spot.dressed(profile));
                    });
                }, minecraft);
        //?} else {
        /*queued.clear();
        *///?}
    }

    // --- Test players --------------------------------------------------------------------

    /**
     * A test player who logged off {@code minutesAgo} minutes ago, here, wearing the skin of
     * {@code skin} and riding {@code mount} if not null: shown like any other until
     * {@link #clearFakes}, but never written to the file, so gone at the next start.
     */
    public void fake(String name, GameProfile skin, String dimension, double x, double y, double z, float yaw,
                     long minutesAgo, @Nullable Mount mount, @Nullable List<String> gear) {
        Property textures = skinOf(skin);
        fakes.add(key(name));
        put(new Spot(name, null, textures == null ? null : Profiles.value(textures),
                textures == null ? null : Profiles.signature(textures), dimension, x, y, z, yaw,
                System.currentTimeMillis() - minutesAgo * 60_000, mount, gear));
    }

    /** Whether this resting player is a test one. */
    public boolean isFake(Spot spot) {
        return fakes.contains(key(spot.name()));
    }

    /**
     * Real players with a skin of their own, most of them well known: a crowd of test players
     * wears theirs, so that no two look alike. A name no longer in use keeps the default skin.
     * None of the names the game draws differently (upside down, in rainbow colours).
     */
    private static final List<String> SKINS = List.of(
            "Notch", "Technoblade", "Dream", "Grian", "MumboJumbo", "xisumavoid", "GeminiTay", "ImpulseSV",
            "TangoTek", "EthosLab", "BdoubleO100", "docm77", "Keralis1", "iJevin", "Smallishbeans", "Rendog",
            "Welsknight", "Cubfan135", "ZombieCleo", "falsesymmetry", "CaptainSparklez", "DanTDM", "Ph1LzA",
            "TommyInnit", "Tubbo", "Ranboo", "Sapnap", "GeorgeNotFound", "Quackity", "Skeppy", "BadBoyHalo",
            "Fundy", "Purpled", "AntVenom", "SSundee", "PrestonPlayz", "Aphmau", "LDShadowLady", "Smajor1995",
            "Pearlescentmoon", "Hypnotizd", "Stressmonster101", "joehillssays", "Thinknoodles", "Fruitberries",
            "Illumina", "Tapl", "Stimpy", "Feinberg", "Sykkuno");

    /** Profiles of the real players whose skins test players wore, fetched once each. */
    private final Map<String, GameProfile> skins = new HashMap<>();

    /** {@code count} real players to borrow skins from, in a new order every time. */
    public static List<String> skinsFor(int count, Random random) {
        List<String> order = new ArrayList<>(SKINS);
        Collections.shuffle(order, random);
        List<String> chosen = new ArrayList<>();
        for (int i = 0; i < count; i++) chosen.add(order.get(i % order.size()));
        return chosen;
    }

    /**
     * Dresses test player {@code name} in the skin of the real player {@code skin}, fetched
     * from Mojang off the game thread. The game asks Mojang for a profile by name from 1.21.9;
     * before, test players keep the default skin their name gives them.
     */
    public void dressFake(String name, String skin) {
        GameProfile known = skins.get(key(skin));
        if (known != null) {
            wear(name, known);
            return;
        }
        //? if >=1.21.9 {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture
                .supplyAsync(() -> minecraft.services().profileResolver().fetchByName(skin), Util.ioPool())
                .exceptionally(e -> Optional.empty())
                .thenAcceptAsync(found -> found.ifPresent(profile -> {
                    skins.put(key(skin), profile);
                    wear(name, profile);
                }), minecraft);
        //?}
    }

    private void wear(String name, GameProfile profile) {
        Spot spot = spots.get(key(name));
        Property skin = skinOf(profile);
        if (spot != null && skin != null && fakes.contains(key(name))) put(spot.skinned(skin));
    }

    /** Removes every test player placed here; how many there were. Those the host placed stay theirs. */
    public int clearFakes() {
        return clearFakes(name -> true);
    }

    /** Removes the test players placed here whose lower-case name {@code which} accepts; how many there were. */
    public int clearFakes(Predicate<String> which) {
        int count = 0;
        for (String name : List.copyOf(fakes)) {
            if (fromHost.contains(name) || !which.test(name)) continue;
            Spot gone = spots.remove(name);
            shownStale = true;
            if (gone != null) {
                count++;
                puppets.remove(gone);
            }
            if (lookedAt != null && key(lookedAt.name()).equals(name)) lookedAt = null;
            fakes.remove(name);
        }
        return count;
    }

    /** Wakes everyone up: nobody rests anywhere any more, here and in the file. */
    public void forgetAll() {
        spots.clear();
        shown = List.of();
        fakes.clear();
        fromHost.clear();
        puppets.clear();
        lookedAt = null;
        dirty = true;
        save();
    }

    // --- Puppets -----------------------------------------------------------------------

    /** The puppet of one of {@link #spots()}, or null when it was not made yet. */
    public @Nullable RestingPuppet made(Spot spot) {
        return puppets.get(spot);
    }

    /** The puppet of one of {@link #spots()}, made the first time it is asked for. */
    public RestingPuppet puppet(ClientLevel level, Spot spot) {
        RestingPuppet puppet = puppets.get(spot);
        if (puppet != null) return puppet;
        puppet = Puppets.numbered(new RestingPuppet(level, spot, spot.profile()));
        // A spot no longer listed (moved, back online) gets one that is not kept: nothing would take it away.
        if (spots.get(key(spot.name())) == spot) puppets.put(spot, puppet);
        return puppet;
    }

    /** The sleeper under the crosshair, or null. */
    public @Nullable Spot lookedAt() {
        return lookedAt;
    }

    /**
     * Finds the sleeper under the crosshair, right next to the player ({@link #MAX_AIM}),
     * unless a block is in the way, where it lies, sits or stands now; and tells it when the
     * player hits it.
     */
    void aim(Minecraft minecraft, FarConfig config) {
        lookedAt = null;
        // A click of the attack button, not a held one breaking a block.
        boolean attacking = minecraft.screen == null && minecraft.options.keyAttack.isDown();
        boolean struck = attacking && !wasAttacking;
        wasAttacking = attacking;
        Entity camera = minecraft.getCameraEntity();
        ClientLevel level = minecraft.level;
        if (!config.offlinePlayers || camera == null || level == null) return;
        if (!config.offlineNames && !(struck && config.offlineLife)) return;
        Vec3 eye = camera.getEyePosition();
        Vec3 look = camera.getViewVector(1.0f);
        double nearest = Double.MAX_VALUE;
        Spot aimed = null;
        for (Spot spot : shown()) {
            RestingPuppet puppet = puppets.get(spot);
            if (puppet == null) continue;
            double distance = eye.distanceTo(puppet.position());
            // The box reaches 1 block round its feet, its top 2 blocks over them.
            if (distance > MAX_AIM + 2.5) continue;
            double height = switch (puppet.look()) {
                case STAND, RIDE -> 1.9;
                case SIT -> 1.3;
                case BED -> 0.5;
            };
            // A sleeper is 1.8 blocks long, whichever way it lies.
            double half = height < 1 ? 1.0 : 0.4;
            AABB box = new AABB(puppet.getX() - half, puppet.getY() + 0.05, puppet.getZ() - half,
                    puppet.getX() + half, puppet.getY() + height, puppet.getZ() + half);
            Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(MAX_AIM)));
            if (hit.isEmpty()) continue;
            double reach = eye.distanceTo(hit.get());
            if (reach >= nearest) continue;
            // Terrain the client has, between the eye and the sleeper, hides it.
            BlockHitResult wall = level.clip(new ClipContext(eye, hit.get(), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, camera));
            if (wall.getType() != HitResult.Type.MISS) continue;
            nearest = reach;
            aimed = spot;
        }
        if (aimed == null) return;
        if (config.offlineNames) lookedAt = aimed;
        // Hit: it raises its shield. Nothing reaches the server: the hit lands on whatever is behind.
        if (struck && config.offlineLife) puppets.get(aimed).strike(eye);
    }

    /** Every one of them lives a tick: idle animations, their life around their spot, the ground under them. */
    void tick(ClientLevel level, FarConfig config) {
        choose(level, config);
        Set<BlockPos> beds = new HashSet<>();
        // Where they fish, or walk to fish: one on a stretch of shore.
        List<Vec3> fishers = new ArrayList<>();
        for (RestingPuppet puppet : puppets.values()) {
            if (puppet.bed() != null) beds.add(puppet.bed());
            Vec3 fishing = puppet.fishing();
            if (fishing != null) fishers.add(fishing);
        }
        boolean night = !level.isBrightOutside();
        // The evening: from late afternoon until dark, when they gather by the fire.
        boolean evening = !night && Math.floorMod(dayTime(level), 24000L) >= EVENING;
        RestingPuppet.Around around = new RestingPuppet.Around(beds, fishers, puppets.values(), night, evening);
        // Sections copied per tick by all of them together, to look around. Each tick another
        // goes first, so that the same few never take it all.
        int[] work = {WORK};
        // The grounds their loops read, a few a tick: the one going first this tick reads first.
        RestingLoop.nextTick();
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        // The one you look at looks back.
        Player you = minecraft.player;
        RestingPuppet gazed = gazed(level, you);
        if (gazed != null) gazed.lookedAt(you.getEyePosition(), you.isShiftKeyDown() || you.isCrouching());
        List<Spot> order = new ArrayList<>(puppets.keySet());
        List<Spot> rested = new ArrayList<>();
        int count = order.size();
        int first = count == 0 ? 0 : Math.floorMod(turn++, count);
        for (int i = 0; i < count; i++) {
            Spot spot = order.get((first + i) % count);
            RestingPuppet puppet = puppets.get(spot);
            // Near the camera they live their life; away from it, they play a loop.
            if (camera != null) puppet.near(camera.getEyePosition());
            // You may shove them out of your way; they never push you.
            puppet.tick(level, config, around, work, minecraft.player);
            if (puppet.restFound()) rested.add(spot);
        }
        for (Spot spot : rested) keepRest(spot, puppets.get(spot).rest());
    }

    /** Ticks into the day when the evening starts: an hour of the game's before sunset. */
    private static final long EVENING = 11000;

    /** The time of day in this level, in ticks from dawn, counted from the first day. */
    private static long dayTime(ClientLevel level) {
        //? if >=26.1 {
        /*return level.getDefaultClockTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }

    /**
     * Keeps where a player really rests with their spot, found by its puppet: the puppet made
     * again, far away or in the next game, starts there. The puppet stays the same.
     */
    private void keepRest(Spot spot, @Nullable Rest rest) {
        String key = key(spot.name());
        if (spots.get(key) != spot || Objects.equals(spot.rest(), rest)) return;
        Spot kept = spot.resting(rest);
        spots.put(key, kept);
        RestingPuppet puppet = puppets.remove(spot);
        if (puppet != null) puppets.put(kept, puppet);
        if (lookedAt == spot) lookedAt = kept;
        shownStale = true;
        if (!fakes.contains(key)) dirty = true;
    }

    /** Blocks within which one notices you looking at it: close by, as someone would. */
    private static final double GAZE = 6;

    /**
     * The nearest of them under your crosshair, within {@link #GAZE} blocks, living its life
     * near you, unless a block is in the way; null when none.
     */
    private @Nullable RestingPuppet gazed(ClientLevel level, @Nullable Player you) {
        if (you == null || you.isSpectator()) return null;
        Vec3 eye = you.getEyePosition();
        Vec3 end = eye.add(you.getViewVector(1.0f).scale(GAZE));
        RestingPuppet nearest = null;
        double best = Double.MAX_VALUE;
        for (RestingPuppet puppet : puppets.values()) {
            if (puppet.away() || puppet.level() != level) continue;
            double height = switch (puppet.look()) {
                case STAND, RIDE -> 1.9;
                case SIT -> 1.3;
                case BED -> 0.6;
            };
            // A little wider than it is: a glance its way is enough.
            AABB box = new AABB(puppet.getX() - 0.5, puppet.getY(), puppet.getZ() - 0.5,
                    puppet.getX() + 0.5, puppet.getY() + height, puppet.getZ() + 0.5);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance >= best) continue;
            BlockHitResult wall = level.clip(new ClipContext(eye, hit.get(), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, you));
            if (wall.getType() != HitResult.Type.MISS) continue;
            best = distance;
            nearest = puppet;
        }
        return nearest;
    }

    // --- File --------------------------------------------------------------------------

    private void save() {
        if (file == null) return;
        dirty = false;
        long started = Profile.begin(Profile.Section.SAVE_RESTING);
        long bytes = -1;
        try {
            List<Spot> kept = spots.values().stream().filter(spot -> !fakes.contains(key(spot.name()))).toList();
            JsonStore.write(file, writer -> write(kept, writer));
            bytes = Files.size(file);
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        } finally {
            Profile.end(Profile.Section.SAVE_RESTING, started, bytes);
        }
    }

    /**
     * The spots saved in a file: none when it does not read, then kept aside ({@link JsonStore}),
     * so that saving again does not write over what it held.
     */
    static List<Spot> read(Path file) {
        JsonElement root = JsonStore.read(file);
        if (root == null) return List.of();
        try {
            return read(file, root);
        } catch (RuntimeException e) {
            JsonStore.keepBad(file, e);
            return List.of();
        }
    }

    /** The spots of a file's text, in either layout; throws when it is not one. */
    static List<Spot> read(Reader reader) {
        return read(null, JsonStore.parse(reader));
    }

    private static List<Spot> read(@Nullable Path file, JsonElement root) {
        JsonStore.Versioned saved = JsonStore.unwrap(root, "players");
        if (saved == null) throw new IllegalStateException("not a list of players");
        if (file != null && saved.version() > VERSION) JsonStore.keepNewer(file, saved.version());
        JsonElement data = saved.version() < 2 ? namedDimensions(saved.data()) : saved.data();
        List<Spot> read = GSON.fromJson(data, new TypeToken<List<Spot>>() { }.getType());
        if (read == null) return List.of();
        return read.stream().filter(spot -> spot != null && spot.name() != null && spot.dimension() != null).toList();
    }

    /**
     * The spots of a file of layout 0 or 1, their dimensions numbered, with the ids in place of
     * the numbers. A spot in 3, a dimension of a mod, is left out: which one cannot be told.
     */
    private static JsonElement namedDimensions(JsonElement data) {
        if (!data.isJsonArray()) return data;
        JsonArray named = new JsonArray();
        for (JsonElement element : data.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject spot = element.getAsJsonObject().deepCopy();
            JsonElement code = spot.get("dimension");
            // Missing, it read as 0 then.
            String id = code == null ? PositionsMessage.legacyDimension(0)
                    : code.isJsonPrimitive() && code.getAsJsonPrimitive().isNumber()
                    ? PositionsMessage.legacyDimension(code.getAsInt()) : null;
            if (id == null) continue;
            spot.addProperty("dimension", id);
            named.add(spot);
        }
        return named;
    }

    static void write(Collection<Spot> spots, Writer writer) throws IOException {
        JsonStore.writeVersioned(writer, GSON, VERSION, "players", new ArrayList<>(spots));
    }

    private static @Nullable Property skinOf(GameProfile profile) {
        for (Property property : Profiles.properties(profile).get("textures")) return property;
        return null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** A server address as a file name. */
    public static String safe(String server) {
        String cleaned = server.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
