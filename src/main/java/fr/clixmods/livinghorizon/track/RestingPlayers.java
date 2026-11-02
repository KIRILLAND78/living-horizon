package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
     * @param lastSeen when they logged off, in milliseconds since 1970; 0 when unknown (seen
     *                 by the server alone, or saved by a version that did not keep it)
     * @param mount    what they rode when they logged off, or null
     * @param gear     what they wore and held ({@link Gear}), or null when not known
     */
    public record Spot(String name, @Nullable UUID id, @Nullable String textures, @Nullable String signature,
                       int dimension, double x, double y, double z, float yaw, long lastSeen,
                       @Nullable Mount mount, @Nullable List<String> gear) {

        Spot moved(int dimension, double x, double y, double z, float yaw, @Nullable Mount mount) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw, lastSeen, mount, gear);
        }

        /** The same player, wearing this skin. */
        Spot skinned(Property skin) {
            return new Spot(name, id, Profiles.value(skin), Profiles.signature(skin), dimension, x, y, z, yaw, lastSeen,
                    mount, gear);
        }

        /** The same player, in this skin and this gear, logged off at {@code lastSeen} when it is known. */
        Spot looking(@Nullable String textures, @Nullable String signature, @Nullable List<String> gear, long lastSeen) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw,
                    lastSeen != 0 ? lastSeen : this.lastSeen, mount, gear);
        }

        Spot dressed(GameProfile profile) {
            Property skin = skinOf(profile);
            return new Spot(name, Profiles.id(profile), skin == null ? textures : Profiles.value(skin),
                    skin == null ? signature : Profiles.signature(skin), dimension, x, y, z, yaw, lastSeen, mount, gear);
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
            try {
                return new Mount(EntityType.getKey(vehicle.getType()).toString(),
                        EntityNbt.save(vehicle, vehicle.level()).toString(), RestingMount.dataOf(vehicle),
                        seat.x, seat.y, seat.z, yaw, riderYaw);
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
            return RestingMount.kindOf(vehicle) == RestingMount.Kind.AIR;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Spot> spots = new LinkedHashMap<>();
    private final Map<String, RestingPuppet> puppets = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();
    /** Test players placed with {@code /livinghorizon debug offline test}: never written to the file. */
    private final Set<String> fakes = new HashSet<>();
    /** Test players the host of this world placed, which its server shares: among {@link #fakes}. */
    private final Set<String> fromHost = new HashSet<>();
    private @Nullable Path file;
    private @Nullable Spot lookedAt;
    private boolean wasAttacking;

    /** Blocks from the eye to the point aimed at on a sleeper: about the reach of a hand. */
    private static final double MAX_AIM = 3;
    /** Spots of the world the puppets may read per tick, all of them together. */
    private static final int WORK = 600;

    public Collection<Spot> spots() {
        return Collections.unmodifiableCollection(spots.values());
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir()
                .resolve("livinghorizon").resolve("resting").resolve(safe(server) + ".json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            for (Spot spot : read(reader)) spots.put(key(spot.name()), spot);
        } catch (IOException | RuntimeException e) {
            LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
        }
    }

    void close() {
        save();
        spots.clear();
        fakes.clear();
        fromHost.clear();
        puppets.clear();
        lookedAt = null;
        file = null;
    }

    /** Puppets belong to one level; a new dimension needs new ones. */
    void forgetPuppets() {
        puppets.clear();
        lookedAt = null;
    }

    // --- Who rests where ---------------------------------------------------------------

    /** A player who just logged off, at the last place this client knew, on what they rode, in what they wore. */
    void rest(GameProfile profile, int dimension, double x, double y, double z, float yaw, @Nullable Mount mount,
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
        if (known != null && known.dimension() == report.dimension()
                && Math.abs(known.x() - report.x()) < 0.05 && Math.abs(known.y() - report.y()) < 0.05
                && Math.abs(known.z() - report.z()) < 0.05 && (known.mount() == null) == (mount == null)
                && (look == null || looksLike(known, look))) {
            return;
        }
        Spot spot = known != null
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
            spots.remove(name);
            puppets.remove(name);
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
        if (spots.remove(key(name)) != null) {
            puppets.remove(key(name));
            if (lookedAt != null && key(lookedAt.name()).equals(key(name))) lookedAt = null;
            save();
        }
    }

    private void put(Spot spot) {
        spots.put(key(spot.name()), spot);
        puppets.remove(key(spot.name()));
        save();
    }

    /** Fetches the skin of a player this client never saw, from Mojang, off the game thread. */
    private void lookUp(String name) {
        if (!lookups.add(key(name))) return;
        Minecraft minecraft = Minecraft.getInstance();
        // The game asks Mojang for a profile by name from 1.21.9; before, skins of players
        // never seen stay the default ones.
        //? if >=1.21.9 {
        CompletableFuture
                .supplyAsync(() -> minecraft.services().profileResolver().fetchByName(name), Util.ioPool())
                .exceptionally(e -> Optional.empty())
                .thenAcceptAsync(found -> found.ifPresent(profile -> {
                    Spot spot = spots.get(key(name));
                    if (spot != null && spot.textures() == null) put(spot.dressed(profile));
                }), minecraft);
        //?}
    }

    // --- Test players --------------------------------------------------------------------

    /**
     * A test player who logged off {@code minutesAgo} minutes ago, here, wearing the skin of
     * {@code skin} and riding {@code mount} if not null: shown like any other until
     * {@link #clearFakes}, but never written to the file, so gone at the next start.
     */
    public void fake(String name, GameProfile skin, int dimension, double x, double y, double z, float yaw,
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
        int count = 0;
        for (String name : List.copyOf(fakes)) {
            if (fromHost.contains(name)) continue;
            if (spots.remove(name) != null) count++;
            puppets.remove(name);
            if (lookedAt != null && key(lookedAt.name()).equals(name)) lookedAt = null;
            fakes.remove(name);
        }
        return count;
    }

    /** Wakes everyone up: nobody rests anywhere any more, here and in the file. */
    public void forgetAll() {
        spots.clear();
        fakes.clear();
        fromHost.clear();
        puppets.clear();
        lookedAt = null;
        save();
    }

    // --- Puppets -----------------------------------------------------------------------

    public RestingPuppet puppet(ClientLevel level, Spot spot) {
        return puppets.computeIfAbsent(key(spot.name()),
                k -> Puppets.numbered(new RestingPuppet(level, spot, spot.profile())));
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
        int dimension = SharedPositions.dimensionCode(level.dimension());
        double nearest = Double.MAX_VALUE;
        Spot aimed = null;
        for (Spot spot : spots.values()) {
            RestingPuppet puppet = puppets.get(key(spot.name()));
            if (spot.dimension() != dimension || puppet == null) continue;
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
        if (struck && config.offlineLife) puppets.get(key(aimed.name())).strike(eye);
    }

    /** Every one of them lives a tick: idle animations, their life around their spot, the ground under them. */
    void tick(ClientLevel level, FarConfig config) {
        Set<BlockPos> beds = new HashSet<>();
        // Where they fish, or walk to fish: one on a stretch of shore.
        List<Vec3> fishers = new ArrayList<>();
        for (RestingPuppet puppet : puppets.values()) {
            if (puppet.bed() != null) beds.add(puppet.bed());
            Vec3 fishing = puppet.fishing();
            if (fishing != null) fishers.add(fishing);
        }
        boolean night = !level.isBrightOutside();
        // Spots read per tick by all of them together, looking around and for their way.
        int[] work = {WORK};
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        for (RestingPuppet puppet : puppets.values()) {
            // Near the camera they live their life; away from it, they play a loop.
            if (camera != null) puppet.near(camera.getEyePosition());
            // You may shove them out of your way; they never push you.
            puppet.tick(level, config, beds, fishers, night, work, minecraft.player);
        }
    }

    // --- File --------------------------------------------------------------------------

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                write(spots.values().stream().filter(spot -> !fakes.contains(key(spot.name()))).toList(), writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }

    static List<Spot> read(Reader reader) {
        List<Spot> read = GSON.fromJson(reader, new TypeToken<List<Spot>>() { }.getType());
        if (read == null) return List.of();
        return read.stream().filter(spot -> spot != null && spot.name() != null).toList();
    }

    static void write(Collection<Spot> spots, Writer writer) {
        GSON.toJson(new ArrayList<>(spots), writer);
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
