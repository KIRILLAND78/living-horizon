package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.JsonStore;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.ambient.Herd;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.render.DepthFar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
//? if >=26.2 {
/*import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.parrot.Parrot;
*///?} else {
import net.minecraft.world.entity.animal.FlyingAnimal;
//?}
//? if >=1.21.9
import net.minecraft.world.entity.decoration.Mannequin;
//? if >=1.21.2
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The mobs this client has met, left where it last saw them once the server stops
 * sending them, doing a little loop of their own.
 *
 * <p>Nothing runs on the server and no AI runs here: a remembered mob is a copy that was
 * never added to the world, saved as the game saves it (so the horse keeps its coat,
 * the sheep its colour, the wolf its collar), that plays where it was an animation
 * chosen from the blocks around it ({@link MobPaths}, {@link MobMotion}): a stroll, a pause
 * to graze or look around, and back; a phantom or a ghast flies its own loop ({@link MobFlight}).
 * It is a picture of what lives there, not a simulation.
 * When the server sends the real mob again, the copy walks up to it first: the real one
 * is only drawn once the copy stands where it is.
 *
 * <p>In a single player world, where the server is right here, a remembered mob the server
 * still moves is followed instead ({@link ServerMobs}): the copy walks where the real one is.
 *
 * <p>Only mobs that stay put are worth remembering: animals do, hostile mobs despawn as
 * soon as nobody is around. Which ones is {@link FarConfig#mobTypes}, plus every mob with
 * a name tag ({@link FarConfig#rememberNamedMobs}). A memory is dropped
 * when the mob is seen dying, or when this client comes back within range of where it
 * was and it is not there any more.
 *
 * <p>Remembered per server across sessions, in {@code config/livinghorizon/mobs/<server>.json}.
 */
public final class MobMemory {
    /** One remembered mob. Gson writes every field that is not transient. */
    public static final class Remembered {
        UUID id;
        String type;
        String dimension;
        /** The world it lives in, told apart by its seed: null in a file from before worlds were told apart. */
        @Nullable Long world;
        double x, y, z;
        /** Where its body faces, as drawn: not where it last walked to, which a mob standing still keeps. */
        float yaw;
        /** Where its head turned, when it was just seen; NaN when not known, then the body's. */
        transient float head = Float.NaN;
        String nbt;
        long seenAt;
        /** Published by the server (the field kept its name in saved files): dropped when the server drops it, which means it died. */
        boolean fromPack;
        /** Wore a name tag when last seen. */
        boolean named;
        /** Foreseen by this client ({@link Foresight}), not shared by the server: it lets it go. */
        transient boolean foreseenHere;
        /**
         * Built from what the server said of it alone (kind, variant, age, what it wears), as that
         * text: rebuilt when the server says something else. Null for a mob this client saw.
         */
        @Nullable String packLook;
        /**
         * The ground around it, read whole where it was last remembered ({@link MobPaths.Ground#save()}):
         * it walks on it from the start next time, before the far world is read, or where it knows nothing.
         */
        @Nullable String ground;

        transient @Nullable Entity puppet;
        /** The animation it plays, chosen from the blocks around where it is remembered; null until read. */
        transient volatile MobPaths.@Nullable Choice choice;
        transient volatile boolean planning;
        /** Its ground was read with columns missing: when to read it again, and how many times it was. */
        transient int replanAt, replans;
        /** The choice whose ground is saved in {@link #ground}. */
        transient MobPaths.@Nullable Choice saved;
        /** Where its animation took it, where it looks. */
        transient @Nullable MobMotion motion;
        /** The real mob, sent again by the server, that the copy walks up to before it takes over. */
        transient @Nullable Entity handover;
        transient boolean failed;
        /** Its saved data, being read off the game's thread. */
        transient @Nullable CompletableFuture<CompoundTag> parsed;
        transient @Nullable EntityType<?> entityType;
        transient int missingChecks;
        transient double distance;
        /** Forgotten while still on the list being drawn: not drawn again, nor rebuilt. */
        transient boolean gone;
        /** The single player server moves it now: the copy follows it instead of playing its animation. */
        transient boolean simulated;
        /** How the server last had it turned, body and head; NaN before its first answer. */
        transient float serverBody = Float.NaN, serverHead = Float.NaN;
        /** Too small or too far for its animation to show; kept between ticks for the hysteresis. */
        transient boolean still;
        /** The last choice that drew it ({@code MobMemory.choose}). */
        transient int chosenIn;
        /** When it was last drawn, in nanoseconds; 0 if never. */
        transient long drawnAt;

        /** Said by the renderer each frame it draws this copy. */
        public void drawn() { drawnAt = System.nanoTime(); }

        public UUID id() { return id; }
        public @Nullable Entity puppet() { return gone ? null : puppet; }
        public boolean waiting() { return !gone && puppet == null && !failed; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public String type() { return type; }
        public boolean fromPack() { return fromPack; }
        /** Its saved data, as text. */
        public String nbt() { return nbt; }
        /** When this client last had it loaded (or the server last said where it is), in milliseconds since the epoch. */
        public long seenAt() { return seenAt; }
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** Where the game saves a pet's order to sit. */
    private static final String SITTING = "Sitting";
    /** Layout of the file: 1 is the list under {@code "mobs"}, with its version; 0 the bare list before it. */
    static final int VERSION = 1;
    /** Bounds the file; the oldest sightings go first. */
    private static final int MAX_REMEMBERED = 2000;
    /** Nanoseconds per tick for building puppets, so that a crowd does not cost one long frame. */
    private static final long BUILD_BUDGET = 2_000_000L;
    /** A copy not drawn for this long (nanoseconds) is frozen, with {@link FarConfig#optFreezeHidden}. */
    private static final long FREEZE_AFTER = 500_000_000L;
    /** Grounds asked for per tick, at most: a copy of a few sections each here, the rest off the game's thread. */
    private static final int READS_PER_TICK = 6;
    /** Saved data this short (a mob foreseen or built from what the server said) is read at once, never queued. */
    private static final int READ_AT_ONCE = 512;
    /** Copies the player sees are built first: this far either side of where they look. */
    private static final double SEEN_COS = Math.cos(Math.toRadians(70));
    /**
     * Ticks before a ground read with columns missing is read again - the far world may not
     * be open yet, as when the world was just entered - doubled each time, up to a minute.
     */
    private static final int REPLAN_FIRST = 40, REPLAN_MAX = 1200;

    private final Map<UUID, Remembered> mobs = new LinkedHashMap<>();
    /** How far the server sends entities, in blocks, as of the last tick ({@link ServerView}). */
    private int renderBlocks;
    /** Mobs the server sends right now: never drawn twice. */
    private final Map<UUID, Entity> live = new HashMap<>();
    /** Real mobs kept out of the frame while their copy walks up to them. */
    private final Map<Entity, Remembered> joining = new IdentityHashMap<>();
    /**
     * Per kind of mob, the farthest this server was seen to send one, in blocks across:
     * how near a remembered mob must be before its absence means something. Servers differ
     * a lot - vanilla sends animals as far as the view distance, Paper only to 48 blocks.
     */
    private final Map<MobCategory, Double> reach = new EnumMap<>(MobCategory.class);
    /** A mob just left or came back: choose what is drawn on the next tick, not in a second. */
    private boolean chooseSoon;
    /** The mounts far players ride: their copy is drawn under the player, never on its own. */
    private Set<UUID> carried = Set.of();
    /**
     * Published mobs this client went to look at and did not find, with where the server had
     * them: not taken back from the server until it says they moved.
     */
    private final Map<UUID, Double> dismissed = new HashMap<>();
    /** Mounts players took away as they logged off: never remembered when the server takes them out of the world. */
    private final Set<UUID> takenAway = new HashSet<>();
    /** What the server shared, as last synced. */
    private @Nullable SharedPositions lastPack;
    private List<Remembered> shown = List.of();
    private @Nullable Path file;
    private boolean firstVisit;
    /** The level the memory is read for: what unloads from any other one is not remembered. */
    private @Nullable ClientLevel bound;
    /** The seed of the world of {@link #bound}: what a memory must carry to be drawn. */
    private long world;
    private boolean dirty;
    private int clock;
    private double selfX, selfY, selfZ;
    private boolean selfKnown;
    /** Nanoseconds spent since the last tick building copies of mobs as they unloaded. */
    private long builtNow;
    /** In a single player world, where the server has the remembered mobs. */
    private final ServerMobs serverMobs = new ServerMobs();
    /** The first animals of the land nobody explored, foreseen by this client. */
    private final Foresight foresight = new Foresight();
    private boolean scoping;

    public List<Remembered> shown() {
        return shown;
    }

    /** The first animals of the land nobody explored, foreseen by this client: the scan, the spyglass. */
    public Foresight foresight() {
        return foresight;
    }

    public int size() {
        return mobs.size();
    }

    /** Choices made so far, each copy marked with the last that drew it. */
    private int choices;

    /** Copies built last tick, and how many waited to be when it began. */
    private int lastBuilds, lastCandidates;

    /** How many mobs are remembered, and of which kind, for the Statistics screen. */
    public record Stats(int remembered, int met, int shared, int guessed, int chosen, int built, int waiting,
                        int reading, int builtLastTick, int candidatesLastTick, @Nullable Path file) {
    }

    public Stats stats() {
        int met = 0, shared = 0, guessed = 0, built = 0, waiting = 0, reading = 0;
        for (Remembered mob : mobs.values()) {
            if (mob.fromPack) shared++;
            else if (mob.foreseenHere) guessed++;
            else met++;
        }
        for (Remembered mob : shown) {
            if (mob.puppet() != null) built++;
            else if (mob.waiting()) {
                waiting++;
                if (mob.parsed != null && !mob.parsed.isDone()) reading++;
            }
        }
        return new Stats(mobs.size(), met, shared, guessed, shown.size(), built, waiting, reading, lastBuilds, lastCandidates, file);
    }

    /** The mobs and boats the server sends right now. */
    public Collection<Entity> live() {
        return live.values();
    }

    /** A real mob its copy is still walking up to: not drawn yet, the copy stands for it. */
    public boolean hides(Entity entity) {
        return !joining.isEmpty() && joining.containsKey(entity);
    }

    /** A copy walking up to its real mob: drawn however close it is. */
    public boolean handingOver(Remembered mob) {
        return mob.handover != null;
    }

    /** How far this server was seen sending each kind of mob, for the debug panel. */
    public Map<MobCategory, Double> reach() {
        return reach;
    }

    /** Every remembered mob of a dimension, drawn or not, for the debug view. */
    public List<Remembered> remembered(String dimension) {
        List<Remembered> here = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(dimension) && isHere(mob) && !live.containsKey(mob.id)) here.add(mob);
        }
        return here;
    }

    /**
     * The mobs far players ride, told each tick: a mount is drawn under its rider, so the copy
     * remembered on its own (met, or published by the server) is left out at once.
     */
    void carried(Set<UUID> ids) {
        if (ids.equals(carried)) return;
        carried = Set.copyOf(ids);
        if (shown.stream().anyMatch(mob -> carried.contains(mob.id))) {
            shown = shown.stream().filter(mob -> !carried.contains(mob.id)).toList();
        }
        chooseSoon = true;
    }

    /** The remembered mob with this id, in this world: null when none is. */
    @Nullable Remembered known(UUID id) {
        Remembered mob = mobs.get(id);
        return mob != null && isHere(mob) ? mob : null;
    }

    /** A new copy of a remembered mob, for a far player to ride: null when it cannot be made. */
    @Nullable Entity copy(Remembered mob, ClientLevel level) {
        EntityType<?> type = type(mob);
        CompoundTag tag = parse(mob.nbt);
        Entity entity = type == null || tag == null ? null : Puppets.create(type, level);
        if (entity == null) return null;
        try {
            EntityNbt.load(entity, level, tag);
        } catch (RuntimeException e) {
            return null;
        }
        entity.setPos(mob.x, mob.y, mob.z);
        entity.setYRot(mob.yaw);
        entity.setOldPosAndRot();
        return entity;
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir()
                .resolve("livinghorizon").resolve("mobs").resolve(RestingPlayers.safe(server) + ".json");
        foresight.open(Platform.configDir().resolve("livinghorizon").resolve("foreseen").resolve(RestingPlayers.safe(server)));
        // No file yet: the mod has never been in this world.
        firstVisit = !Files.exists(file);
        if (firstVisit) return;
        for (Remembered mob : read(file)) {
            // A memory with no world cannot be told from another world's: dropped.
            if (mob.world != null) mobs.put(mob.id, mob);
        }
    }

    /**
     * The mobs saved in a file: none when it does not read, then kept aside ({@link JsonStore}),
     * so that saving the memory again does not write over what it held.
     */
    static List<Remembered> read(Path file) {
        JsonElement root = JsonStore.read(file);
        if (root == null) return List.of();
        try {
            JsonStore.Versioned saved = JsonStore.unwrap(root, "mobs");
            if (saved == null) throw new IllegalStateException("not a list of mobs");
            if (saved.version() > VERSION) JsonStore.keepNewer(file, saved.version());
            return read(saved.data());
        } catch (RuntimeException e) {
            JsonStore.keepBad(file, e);
            return List.of();
        }
    }

    /** The mobs of a list as saved; those missing what they need are left out. */
    static List<Remembered> read(JsonElement list) {
        List<Remembered> read = GSON.fromJson(list, new TypeToken<List<Remembered>>() { }.getType());
        if (read == null) return List.of();
        List<Remembered> kept = new ArrayList<>(read.size());
        for (Remembered mob : read) {
            if (mob != null && mob.id != null && mob.type != null && mob.dimension != null) kept.add(mob);
        }
        return kept;
    }

    /** Writes the mobs as {@link #read(Path)} reads them back. */
    static void write(Collection<Remembered> mobs, Writer writer) throws IOException {
        JsonStore.writeVersioned(writer, GSON, VERSION, "mobs", new ArrayList<>(mobs));
    }

    /** True once per world the mod has never been in before: the cue for a first scan. */
    boolean takeFirstVisit() {
        boolean first = firstVisit;
        firstVisit = false;
        return first;
    }

    void close() {
        save();
        foresight.close();
        firstVisit = false;
        mobs.clear();
        live.clear();
        joining.clear();
        reach.clear();
        dismissed.clear();
        takenAway.clear();
        shown = List.of();
        chooseSoon = false;
        file = null;
        bound = null;
        selfKnown = false;
        serverMobs.reset();
    }

    /** Puppets belong to one level. */
    void forgetPuppets() {
        for (Remembered mob : mobs.values()) {
            mob.puppet = null;
            mob.motion = null;
            mob.handover = null;
            mob.failed = false;
            mob.simulated = false;
        }
        joining.clear();
        shown = List.of();
        serverMobs.reset();
    }

    // --- What the server sends and stops sending ----------------------------------------

    /**
     * Seen live: the memory is out of date, the real one is here. A copy on screen walks
     * up to it first, so that the mob does not jump from where the copy was.
     */
    public void onLoad(Entity entity) {
        if (rememberable(entity)) {
            live.put(entity.getUUID(), entity);
            discover(entity);
        }
        // Back in the world with its rider: remembered again once it leaves.
        takenAway.remove(entity.getUUID());
        Remembered mob = mobs.get(entity.getUUID());
        if (mob != null && joins(mob, entity)) {
            mob.handover = entity;
            joining.put(entity, mob);
            return;
        }
        forget(entity.getUUID());
    }

    /** A copy drawn now, on its own feet, not so far from the real one that walking up to it would take long. */
    private boolean joins(Remembered mob, Entity real) {
        return !mob.gone && mob.handover == null && mob.puppet instanceof LivingEntity puppet
                && real instanceof LivingEntity && !real.isPassenger() && !real.isVehicle()
                && shown.contains(mob) && puppet.distanceToSqr(real) < 48 * 48;
    }

    /** Its copy reached the real mob, or cannot any more: the real one takes over. */
    private void handOver(Remembered mob) {
        if (mob.handover != null) joining.remove(mob.handover);
        mob.handover = null;
        if (mobs.get(mob.id) == mob) forget(mob.id);
    }

    /**
     * Which world a level is: its seed, as the server sends it (hashed). Dimensions of one
     * world share it, and so do two names or two addresses that lead to different worlds
     * only by it - a proxy, a multiworld plugin, two saves called the same.
     */
    private static long worldOf(ClientLevel level) {
        return level.getBiomeManager().biomeZoomSeed;
    }

    private boolean isHere(Remembered mob) {
        return mob.world != null && mob.world == world;
    }

    /** Dropped from memory, and from the frame at once rather than at the next choice. */
    private void forget(UUID id) {
        Remembered mob = mobs.remove(id);
        if (mob == null) return;
        if (mob.handover != null) joining.remove(mob.handover);
        mob.handover = null;
        mob.gone = true;
        mob.puppet = null;
        dirty = true;
        chooseSoon = true;
    }

    /**
     * A mount a far player got off, where its copy under them was: it stays there, rather than
     * walk from where it was remembered before it was ridden. What the server last said of it is
     * older than that, and is not taken until it says something new.
     */
    void released(UUID id, Vec3 at, float yaw) {
        Remembered mob = mobs.get(id);
        if (mob == null || live.containsKey(id) || !isHere(mob)) return;
        mob.x = at.x;
        mob.y = at.y;
        mob.z = at.z;
        mob.yaw = yaw;
        mob.head = Float.NaN;
        mob.puppet = null;
        mob.motion = null;
        mob.choice = null;
        mob.saved = null;
        mob.ground = null;
        mob.failed = false;
        SharedPositions.MobReport report = lastPack == null ? null : lastPack.readMob(id);
        if (report != null) dismissed.put(id, report.x() + report.y() * 31 + report.z() * 961);
        dirty = true;
        chooseSoon = true;
    }

    /** A mount a player took with them as they logged off: it rests under them, not here. */
    void forgetRidden(UUID id) {
        forget(id);
        takenAway.add(id);
        // What the server last said of it is older than that: not taken back until it says something new.
        SharedPositions.MobReport report = lastPack == null ? null : lastPack.readMob(id);
        if (report != null) dismissed.put(id, report.x() + report.y() * 31 + report.z() * 961);
    }

    /** No longer sent: remembered if it walked out of range, forgotten if it died. */
    public void onUnload(Entity entity, ClientLevel level) {
        live.remove(entity.getUUID());
        // Gone again before its copy reached it: that copy carries on from where it got to.
        Remembered joined = joining.remove(entity);
        if (joined != null) joined.handover = null;
        Entity joinedPuppet = joined != null && mobs.get(joined.id) == joined ? joined.puppet : null;
        // The old level's mobs unload while the new one is already being read: they would
        // be filed under the new world.
        if (file == null || (bound != null && level != bound) || !rememberable(entity)) return;
        Entity mob = entity;
        FarConfig config = FarConfig.get();
        if (!config.shows(MobKinds.creature(mob.getType())) || !worthRemembering(mob, config)) return;
        if ((mob instanceof LivingEntity living && living.isDeadOrDying()) || carriesPlayer(mob)
                || takenAway.contains(mob.getUUID())) {
            forget(mob.getUUID());
            return;
        }
        // Gone right next to us: killed, despawned, picked up - not out of range.
        if (selfKnown && distance(mob.getX(), mob.getY(), mob.getZ()) < 32) {
            forget(mob.getUUID());
            return;
        }
        CompoundTag tag = snapshot(mob, level);
        if (tag == null) return;
        // The game saves a pet's order to sit, which only the server knows: on this client it
        // always says no, and the copy would stand up. What the client sees goes in its place.
        if (sitting(mob)) tag.putBoolean(SITTING, true);
        // The body as the game drew it: a mob standing still turns it, not its yaw.
        float body = mob instanceof LivingEntity living ? living.yBodyRot : mob.getYRot();
        remember(new MobScan.Found(mob.getUUID(), EntityType.getKey(mob.getType()).toString(),
                mob.getX(), mob.getY(), mob.getZ(), body, tag, mob.hasCustomName()),
                level.dimension().identifier().toString(), worldOf(level));
        Remembered fresh = mobs.get(entity.getUUID());
        if (fresh != null) {
            if (mob instanceof LivingEntity living) fresh.head = living.yHeadRot;
            if (joinedPuppet != null) {
                fresh.puppet = joinedPuppet;
                fresh.motion = joined.motion;
            }
            showNow(fresh, level, config);
        }
        trim();
    }

    /**
     * Drawn from this frame on. The server's packets are read every frame, but the game ticks
     * every 50 ms: waiting for the next tick to choose and build the copy left the frames in
     * between with neither the real mob nor its copy. The next choice puts the list in order.
     */
    private void showNow(Remembered mob, ClientLevel level, FarConfig config) {
        if (config.most(creature(mob), 1) <= 0 || carried.contains(mob.id)) return;
        if (mob.puppet == null) {
            // A crowd unloaded at once (a teleport) is left to the ticks and their budget.
            if (config.optBackgroundBuild && builtNow >= BUILD_BUDGET) return;
            long started = System.nanoTime();
            build(mob, level);
            builtNow += System.nanoTime() - started;
            if (mob.puppet == null) return;
        }
        mob.distance = distance(mob.x, mob.y, mob.z);
        List<Remembered> next = new ArrayList<>(shown);
        next.add(mob);
        shown = Collections.unmodifiableList(next);
    }

    /**
     * What a scan of the world found ({@link MobScan}): every mob of the chunks it read,
     * remembered as if met there. A mob remembered in those chunks and not found any more
     * is gone. Returns how many were found.
     */
    public int adopt(MobScan.Result scan) {
        if (file == null || bound == null) return 0;
        Set<UUID> found = new HashSet<>();
        for (MobScan.Found mob : scan.mobs()) found.add(mob.id());
        List<UUID> gone = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(scan.dimension()) && isHere(mob) && !found.contains(mob.id) && !live.containsKey(mob.id)
                    && scan.covers(mob.x, mob.z)) {
                gone.add(mob.id);
            }
        }
        gone.forEach(this::forget);
        // Farthest first: past the limit, the oldest go first, so the nearest stay.
        List<MobScan.Found> sorted = new ArrayList<>(scan.mobs());
        sorted.sort(Comparator.comparingDouble((MobScan.Found mob) -> distance(mob.x(), mob.y(), mob.z())).reversed());
        int count = 0;
        for (MobScan.Found mob : sorted) {
            if (live.containsKey(mob.id())) continue;
            dismissed.remove(mob.id());
            remember(mob, scan.dimension(), world);
            count++;
        }
        trim();
        return count;
    }

    private void remember(MobScan.Found mob, String dimension, long world) {
        Remembered memory = new Remembered();
        memory.id = mob.id();
        memory.type = mob.type();
        memory.dimension = dimension;
        memory.world = world;
        memory.x = mob.x();
        memory.y = mob.y();
        memory.z = mob.z();
        memory.yaw = mob.yaw();
        memory.nbt = mob.nbt().toString();
        // Already read: the copy takes over on the next tick, where the real one was.
        memory.parsed = CompletableFuture.completedFuture(mob.nbt());
        memory.seenAt = System.currentTimeMillis();
        memory.named = mob.named();
        forget(memory.id);
        mobs.put(memory.id, memory);
        chooseSoon = true;
        dirty = true;
    }

    /**
     * The mobs met, the oldest first, past {@link #MAX_REMEMBERED}. The animals foreseen from the
     * seed are not counted: there are as many as the server shares and this client foresees,
     * <i>Max Mobs</i> at most ({@link Foresight#wanted}), and this file never holds them
     * ({@link Foresight} keeps its own).
     */
    private void trim() {
        int met = 0;
        for (Remembered mob : mobs.values()) {
            if (!SeedDraws.foreseen(mob.id)) met++;
        }
        for (Iterator<Remembered> it = mobs.values().iterator(); met > MAX_REMEMBERED && it.hasNext(); ) {
            if (SeedDraws.foreseen(it.next().id)) continue;
            it.remove();
            met--;
        }
    }

    /**
     * Mobs, boats - a parked boat vanishes from far away just like an animal - other vehicles,
     * other mods' aircraft and cars among them ({@link Vehicles}), and mannequins, the skinned
     * figures that Distant Friends stands far away as fake players.
     */
    static boolean rememberable(Entity entity) {
        // Mannequins came in 1.21.9.
        //? if >=1.21.9 {
        return entity instanceof Mob || boat(entity) || entity instanceof Mannequin || Vehicles.is(entity);
        //?} else
        /*return entity instanceof Mob || boat(entity) || Vehicles.is(entity);*/
    }

    /** A boat or a raft, chest or not, other mods' boats built on the game's included. */
    static boolean boat(Entity entity) {
        // Every boat was one class before 1.21.2.
        //? if >=1.21.2 {
        return entity instanceof AbstractBoat;
        //?} else
        /*return entity instanceof net.minecraft.world.entity.vehicle.Boat;*/
    }

    /**
     * A vehicle of a kind never met before, whose id the list of mobs cannot know (an aircraft,
     * another mod's boat): from now on it is in that list, where the player chooses whether it
     * is shown ({@link FarConfig#adoptVehicle}). The game's boats are listed already.
     */
    private static void discover(Entity entity) {
        Vehicles.Kind kind = Vehicles.of(entity);
        if (kind == Vehicles.Kind.NONE) return;
        String id = EntityType.getKey(entity.getType()).toString();
        if (!MobKinds.isBoat(id)) FarConfig.adoptVehicle(id, kind == Vehicles.Kind.STEERED);
    }

    private static boolean worthRemembering(Entity mob, FarConfig config) {
        if (config.rememberNamedMobs && mob.hasCustomName()) return true;
        return config.mobTypes.contains(EntityType.getKey(mob.getType()).toString());
    }

    /** A mount under another player belongs to that player's copy. */
    private static boolean carriesPlayer(Entity entity) {
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof AbstractClientPlayer) return true;
        }
        return false;
    }

    /** A cat, wolf or parrot sitting where it was told to, as drawn. */
    private static boolean sitting(Entity entity) {
        return entity instanceof TamableAnimal pet && pet.isInSittingPose();
    }

    /**
     * The pose the single player server has a pet in, given to its copy and to its saved data,
     * which the copy is built from again next time. Nothing for any other mob, or no change.
     */
    private void sit(Remembered mob, @Nullable Boolean sitting) {
        if (sitting == null || !(mob.puppet instanceof TamableAnimal pet) || pet.isInSittingPose() == sitting) return;
        pet.setInSittingPose(sitting);
        CompoundTag tag = parse(mob.nbt);
        if (tag == null) return;
        tag.putBoolean(SITTING, sitting);
        mob.nbt = tag.toString();
        mob.parsed = CompletableFuture.completedFuture(tag);
        dirty = true;
    }

    /** The mob as the game saves it: variants, colours, equipment, name, age. */
    private static @Nullable CompoundTag snapshot(Entity mob, ClientLevel level) {
        try {
            return EntityNbt.save(mob, level);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- Every tick --------------------------------------------------------------------

    void tick(ClientLevel level, LocalPlayer self, int renderDistanceBlocks, FarConfig config,
              @Nullable SharedPositions pack) {
        bound = level;
        world = worldOf(level);
        selfX = self.getX();
        selfY = self.getY();
        selfZ = self.getZ();
        selfKnown = true;
        renderBlocks = renderDistanceBlocks;
        builtNow = 0;
        clock++;
        if (!config.distantMobs && !config.distantEntities) {
            shown = List.of();
            return;
        }
        String dimension = level.dimension().identifier().toString();
        foresight.tick(Minecraft.getInstance(), level, self, world, config, pack);
        // Through a spyglass and back: the mobs where it looks are chosen first, and then no longer.
        if (self.isScoping() != scoping) {
            scoping = self.isScoping();
            chooseSoon = true;
        }
        // Through the spyglass, those where it looks are taken at once; otherwise once a second,
        // not at every batch found, which would go through thousands of mobs each time.
        boolean foreseeNow = scoping && clock % 5 == 0;
        long following = Profile.begin(Profile.Section.TICK_MOBS_FOLLOW);
        try {
            followServer(level, dimension);
        } finally {
            Profile.end(Profile.Section.TICK_MOBS_FOLLOW, following);
        }

        if (clock % 20 == 0 || chooseSoon || foreseeNow) {
            long choosing = Profile.begin(Profile.Section.TICK_MOBS_CHOOSE);
            try {
                if (clock % 20 == 0) {
                    lastPack = pack;
                    if (pack != null) sync(pack);
                    foresee(config, dimension);
                    learnReach();
                    reconcile(level, dimension, renderDistanceBlocks);
                } else if (foreseeNow) {
                    foresee(config, dimension);
                }
                choose(dimension, config);
            } finally {
                Profile.end(Profile.Section.TICK_MOBS_CHOOSE, choosing);
            }
        }
        if (clock % 1200 == 0 && dirty) save();

        Set<String> still = new HashSet<>(config.stillMobTypes);
        double pixelsPerRadian = config.optStillTiny ? pixelsPerRadian() : 0;

        long buildNanos = 0, groundNanos = 0, joiningStarted = System.nanoTime();
        // Copies walking up to their real mob, before anything else.
        if (!joining.isEmpty()) {
            List<Remembered> arrived = new ArrayList<>();
            for (Map.Entry<Entity, Remembered> joined : joining.entrySet()) {
                Remembered mob = joined.getValue();
                // Sitting or standing as the real one is, which may have got up meanwhile.
                if (mob.puppet() instanceof TamableAnimal pet && joined.getKey() instanceof TamableAnimal real) {
                    pet.setInSittingPose(real.isInSittingPose());
                }
                if (joined.getKey().isRemoved() || !(mob.puppet() instanceof LivingEntity puppet)
                        || motion(mob).follow(puppet, mob, joined.getKey())) {
                    arrived.add(mob);
                }
            }
            arrived.forEach(this::handOver);
        }
        long motionNanos = System.nanoTime() - joiningStarted;

        boolean background = config.optBackgroundBuild;
        long budget = System.nanoTime() + BUILD_BUDGET;
        int reads = 0;
        // Those the player sees first, the nearest first, then the others: sorted out before the
        // budget starts, which counts the building alone, however many wait.
        int builds = 0, unbuilt = 0;
        if (background) {
            Vec3 sight = self.getViewVector(1f);
            List<Remembered> seen = new ArrayList<>(), unseen = new ArrayList<>();
            for (Remembered mob : shown) {
                if (mob.gone || mob.handover != null || mob.puppet != null || mob.failed) continue;
                (seen(mob, sight) ? seen : unseen).add(mob);
            }
            unbuilt = seen.size() + unseen.size();
            long started = System.nanoTime();
            budget = started + BUILD_BUDGET;
            building:
            for (List<Remembered> group : List.of(seen, unseen)) {
                for (Remembered mob : group) {
                    if (System.nanoTime() >= budget) break building;
                    if (mob.parsed == null) mob.parsed = read(mob.nbt, true);
                    if (!mob.parsed.isDone()) continue;
                    build(mob, level);
                    builds++;
                }
            }
            buildNanos += System.nanoTime() - started;
        }
        lastBuilds = builds;
        lastCandidates = unbuilt;
        for (Remembered mob : shown) {
            if (mob.gone || mob.handover != null) continue;
            if (mob.puppet == null && !mob.failed) {
                // The saved data is read on a background thread; the puppet is made here,
                // where the world is, as long as this tick's budget lasts.
                if (mob.parsed == null) mob.parsed = read(mob.nbt, background);
                if (mob.parsed.isDone() && !background) {
                    long building = System.nanoTime();
                    build(mob, level);
                    buildNanos += System.nanoTime() - building;
                }
            }
            if (mob.puppet == null) continue;
            // Too small or far to show its animation, standing with nothing left to walk or turn:
            // looked at again once in sixteen ticks, each on a tick of its own, and only kept going
            // meanwhile. Thousands far off cost little then. One that moves (the server's, one
            // called back to its spot) is moved every tick: once in sixteen, it went in jerks.
            if (mob.still && mob.motion != null && mob.motion.settled() && !mob.simulated && mob.handover == null
                    && mob.puppet instanceof LivingEntity resting && ((clock + mob.id.hashCode()) & 15) != 0) {
                mob.motion.rest(resting);
                continue;
            }
            if (mob.puppet instanceof LivingEntity living) {
                // Not drawn for a moment: it keeps its place, as a position and nothing more.
                // Held by easter egg 15 it turns all the same, out of view too.
                if (config.optFreezeHidden && mob.motion != null && mob.motion.placed()
                        && System.nanoTime() - mob.drawnAt > FREEZE_AFTER && !Herd.holds(living)) {
                    // Moved by the server meanwhile: there when it shows again, not running there.
                    if (mob.simulated) mob.motion.jump(living, mob);
                    continue;
                }
                // A pet told to sit stays on its spot, as the real one does.
                boolean roam = !still.contains(mob.type) && !sitting(living);
                // A phantom or a ghast flies its own way, on no ground: nothing to read for it.
                boolean grounded = roam && MobFlight.of(mob.type) == null;
                long grounding = System.nanoTime();
                // Moved by the server's word: the blocks are read again around where it is now.
                MobPaths.Choice choice = mob.choice;
                if (choice != null && !choice.ground().around(mob.x, mob.z)) mob.choice = choice = null;
                // Read with part of the far world missing: read again, now that it may be there.
                boolean again = choice != null && !choice.complete() && clock >= mob.replanAt;
                if (choice == null && grounded && !mob.simulated && !mob.planning && mob.ground != null) {
                    // Saved with it: the ground it was last seen on, if it is still where it was.
                    MobPaths.Ground saved = MobPaths.Ground.load(mob.ground);
                    if (saved != null && saved.around(mob.x, mob.z) && walker(living)) {
                        mob.choice = choice = mob.saved = MobPaths.choose(saved, living.getBbWidth(), seed(mob));
                    } else {
                        mob.ground = null;
                    }
                }
                if ((choice == null || again) && grounded && !mob.simulated && !mob.planning && reads < READS_PER_TICK) {
                    reads++;
                    mob.planning = true;
                    if (choice == null) mob.replans = 0;
                    mob.replanAt = clock + Math.min(REPLAN_MAX, REPLAN_FIRST << Math.min(mob.replans++, 5));
                    double x = mob.x, y = mob.y, z = mob.z;
                    long seed = seed(mob);
                    CompletableFuture<MobPaths.Choice> read = walker(living)
                            ? MobPaths.plan(level, x, y, z, living.getBbWidth(), seed)
                            : CompletableFuture.completedFuture(MobPaths.free(x, y, z, seed));
                    read.whenComplete((found, error) -> {
                        mob.choice = found != null ? found : MobPaths.stand(x, y, z);
                        mob.planning = false;
                    });
                }
                // Read whole: kept with it, for the next time the world is entered.
                if (choice != null && choice != mob.saved && choice.complete() && walker(living)) {
                    mob.saved = choice;
                    mob.ground = choice.ground().save();
                    dirty = true;
                }
                long moving = System.nanoTime();
                groundNanos += moving - grounding;
                mob.still = isStill(living, mob, config, pixelsPerRadian);
                boolean tiny = mob.still;
                double handover = Math.max(24, Math.min(sent(type(mob)), renderDistanceBlocks));
                float home = MobMotion.home(Math.hypot(mob.x - selfX, mob.z - selfZ), handover);
                // Easter egg 15 holds a few copies still, turned to the player: their loop waits.
                if (Herd.turn(living)) {
                    motion(mob).held(living, mob);
                } else {
                    motion(mob).tick(living, mob, choice, clock, roam, tiny, home);
                }
                motionNanos += System.nanoTime() - moving;
            } else {
                long holding = System.nanoTime();
                hold(mob.puppet, mob);
                motionNanos += System.nanoTime() - holding;
            }
        }
        Profile.record(Profile.Section.TICK_MOBS_BUILD, buildNanos);
        Profile.record(Profile.Section.TICK_MOBS_GROUND, groundNanos);
        Profile.record(Profile.Section.TICK_MOBS_MOTION, motionNanos);
    }

    private static long seed(Remembered mob) {
        return mob.id.getMostSignificantBits() ^ mob.id.getLeastSignificantBits();
    }

    private static MobMotion motion(Remembered mob) {
        if (mob.motion == null) mob.motion = new MobMotion(seed(mob), MobFlight.of(mob.type));
        return mob.motion;
    }

    /** Where it was, facing where it faced, not moving: a boat or another vehicle, which has no animation. */
    private static void hold(Entity puppet, Remembered mob) {
        puppet.setOldPosAndRot();
        puppet.setPos(mob.x, mob.y, mob.z);
        puppet.setYRot(mob.yaw);
        puppet.tickCount++;
    }

    /** How many pixels a radian is across the screen, as the renderer counts them. */
    private static double pixelsPerRadian() {
        return DepthFar.pixelsPerRadian(Minecraft.getInstance());
    }

    /**
     * Whether a mob's animation is switched off: past the distance cap, or smaller on screen than
     * the minimum. Once still it resumes only above the minimum plus a quarter and a pixel.
     */
    private static boolean isStill(LivingEntity puppet, Remembered mob, FarConfig config, double pixelsPerRadian) {
        if (config.animationMaxDistance > 0 && mob.distance > config.animationMaxDistance) return true;
        if (pixelsPerRadian <= 0 || config.animationMinPixels <= 0) return false;
        double min = config.animationMinPixels;
        return apparentPixels(puppet, mob, config, pixelsPerRadian) < (mob.still ? min * 1.25 + 1 : min);
    }

    /** Roughly how tall a mob looks on screen, at the distance it was last sorted at. */
    private static double apparentPixels(LivingEntity puppet, Remembered mob, FarConfig config, double pixelsPerRadian) {
        double size = Math.max(0.5, Math.max(puppet.getBbHeight(), puppet.getBbWidth()));
        double apparent = size / Math.max(1, mob.distance) * pixelsPerRadian;
        return Math.max(apparent, config.minApparentPixels);
    }

    /**
     * Takes the server's word: where every mob it publishes around this client is now, as last
     * seen by whoever was near it. A mob this client has seen keeps the look it saved; one it
     * never met is built from its kind, variant and age. One the server stopped publishing where
     * it sent the mobs died: the server forgets a mob whose chunk is loaded without it.
     */
    private void sync(SharedPositions pack) {
        FarConfig config = FarConfig.get();
        Set<UUID> published = new HashSet<>();
        for (UUID id : pack.mobs()) {
            SharedPositions.MobReport report = pack.readMob(id);
            String type = report == null ? null : MobKinds.type(report.kind());
            String dimension = report == null ? null : report.dimension();
            if (type == null || dimension == null) continue;
            published.add(id);
            if (live.containsKey(id)) continue;
            Double gone = dismissed.get(id);
            if (gone != null) {
                if (gone == report.x() + report.y() * 31 + report.z() * 961) continue;
                dismissed.remove(id);
            }

            Remembered mob = mobs.get(id);
            // Followed on the single player server, which knows better than the last report.
            if (mob != null && mob.simulated) continue;
            // An animal foreseen from the seed, which would not be drawn: not taken at all.
            if (mob == null && SeedDraws.foreseen(id) && !wantsForeseen(report, config)) continue;
            String look = packLook(report);
            if (mob == null) {
                mob = new Remembered();
                mob.id = id;
                mob.type = type;
                mob.nbt = packNbt(type, report);
                mob.packLook = look;
                mob.seenAt = System.currentTimeMillis();
                mobs.put(id, mob);
            } else if (builtFromPack(mob, report) && !look.equals(mob.packLook)) {
                // Saddled, harnessed, given a chest since: built again.
                mob.nbt = packNbt(type, report);
                mob.packLook = look;
                mob.parsed = null;
                mob.puppet = null;
                mob.failed = false;
                dirty = true;
            } else if (mob.x == report.x() && mob.y == report.y() && mob.z == report.z()
                    && dimension.equals(mob.dimension) && isHere(mob) && mob.fromPack) {
                continue;
            }
            // A mob already drawn is not snapped to its new spot: it walks there (MobMotion).
            mob.fromPack = true;
            mob.world = world;
            mob.dimension = dimension;
            mob.x = report.x();
            mob.y = report.y();
            mob.z = report.z();
            dirty = true;
        }
        List<UUID> dead = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            // Only where the server just sent the mobs around: one farther off is out of reach, not gone.
            if (mob.fromPack && isHere(mob) && !published.contains(mob.id) && !live.containsKey(mob.id)
                    && pack.covers(mob.dimension, mob.x, mob.z)) dead.add(mob.id);
        }
        dead.forEach(this::forget);
    }

    /**
     * The animals this client foresees that are worth remembering now ({@link Foresight#wanted}):
     * those not remembered yet become remembered mobs, built from their kind, variant and age as
     * the server's are; those it foresaw and no longer wants are forgotten. The server's own stay
     * as it says.
     */
    private void foreseen(List<PositionsMessage.Mob> wanted, String dimension) {
        Set<UUID> kept = new HashSet<>();
        for (PositionsMessage.Mob found : wanted) {
            UUID id = found.id();
            kept.add(id);
            if (mobs.containsKey(id) || live.containsKey(id) || dismissed.containsKey(id)) continue;
            String type = MobKinds.type(found.kind());
            if (type == null) continue;
            SharedPositions.MobReport report = new SharedPositions.MobReport(id, found.x(), found.y(), found.z(),
                    found.kind(), found.dimension(), found.baby(), found.variant(), null);
            Remembered mob = new Remembered();
            mob.id = id;
            mob.type = type;
            mob.nbt = packNbt(type, report);
            mob.packLook = packLook(report);
            mob.seenAt = System.currentTimeMillis();
            mob.world = world;
            mob.dimension = dimension;
            mob.x = found.x();
            mob.y = found.y();
            mob.z = found.z();
            mob.foreseenHere = true;
            mobs.put(id, mob);
            // Where the spyglass looks, drawn at once; otherwise at the next choice, within a second,
            // not a choice through thousands of mobs at every batch found.
            if (scoping) chooseSoon = true;
        }
        List<UUID> dropped = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.foreseenHere && !mob.fromPack && !kept.contains(mob.id)) dropped.add(mob.id);
        }
        dropped.forEach(this::forget);
    }

    /** The foreseen mobs worth remembering, worked out again only when what they depend on changed. */
    private void foresee(FarConfig config, String dimension) {
        long started = Profile.begin(Profile.Section.TICK_MOBS_FORESEE);
        try {
            List<PositionsMessage.Mob> wanted = foresight.wantedIfChanged(selfX, selfY, selfZ, config);
            if (wanted != null) foreseen(wanted, dimension);
        } finally {
            Profile.end(Profile.Section.TICK_MOBS_FORESEE, started);
        }
    }

    /**
     * Whether the chunk of an animal foreseen from the seed is here: generated, with mobs of its
     * own, which the server stops foreseeing at its next share.
     */
    private boolean generated(Remembered mob) {
        return bound != null && bound.getChunkSource().hasChunk((int) Math.floor(mob.x) >> 4, (int) Math.floor(mob.z) >> 4);
    }

    /** Whether an animal foreseen from the seed is worth remembering: shown, and not past the distance mobs are drawn at. */
    private boolean wantsForeseen(SharedPositions.MobReport report, FarConfig config) {
        if (!config.seedMobs) return false;
        return config.mobMaxDistance <= 0 || Math.hypot(report.x() - selfX, report.z() - selfZ) <= config.mobMaxDistance + 64;
    }

    /** How the server says a mob looks, as one text. */
    private static String packLook(SharedPositions.MobReport report) {
        return report.variant() + "|" + report.baby() + "|" + report.extra();
    }

    /** Whether a remembered mob was built from what the server said, not seen by this client. */
    private static boolean builtFromPack(Remembered mob, SharedPositions.MobReport report) {
        if (mob.packLook != null) return true;
        // Remembered before the server said what mobs wear.
        return mob.fromPack && mob.nbt != null && mob.nbt.equals(MobKinds.nbt(mob.type, report.variant(), report.baby()));
    }

    /** A mob known only from the server: its kind, variant and age, and what it wears. */
    private static String packNbt(String type, SharedPositions.MobReport report) {
        String base = MobKinds.nbt(type, report.variant(), report.baby());
        if (report.extra() == null) return base;
        try {
            CompoundTag tag = TagParser.parseCompoundFully(base);
            tag.merge(TagParser.parseCompoundFully(report.extra()));
            return tag.toString();
        } catch (Exception e) {
            return base;
        }
    }

    /**
     * In a single player world, where the server has the remembered mobs it no longer sends:
     * those it still moves are followed by their copies, those that died there are forgotten.
     * Asked every {@link ServerMobs#EVERY} ticks; each answer is applied as it comes.
     */
    private void followServer(ClientLevel level, String dimension) {
        ServerMobs.Answer answer = serverMobs.answer();
        if (answer != null) {
            for (Remembered mob : mobs.values()) {
                ServerMobs.Where where = answer.found().get(mob.id);
                if (where == null || !mob.dimension.equals(dimension) || !isHere(mob)
                        || live.containsKey(mob.id) || mob.handover != null) {
                    mob.simulated = false;
                    continue;
                }
                mob.simulated = where.simulated();
                double moved = Math.abs(where.x() - mob.x) + Math.abs(where.y() - mob.y) + Math.abs(where.z() - mob.z);
                if (moved > 0) {
                    mob.x = where.x();
                    mob.y = where.y();
                    mob.z = where.z();
                    dirty = true;
                }
                // Turned as the game drew it until the real one walks or turns: the server
                // works out a body of its own for a mob standing still.
                boolean turned = !Float.isNaN(mob.serverBody)
                        && (where.body() != mob.serverBody || where.head() != mob.serverHead);
                if (moved > 0.05 || turned) {
                    mob.yaw = where.body();
                    mob.head = where.head();
                }
                mob.serverBody = where.body();
                mob.serverHead = where.head();
                sit(mob, where.sitting());
            }
            for (UUID id : answer.gone()) {
                if (!live.containsKey(id)) forget(id);
            }
        }
        if (clock % ServerMobs.EVERY != 0 || !serverMobs.idle()) return;
        IntegratedServer local = Minecraft.getInstance().getSingleplayerServer();
        if (local == null) return;
        List<ServerMobs.Asked> asked = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(dimension) && isHere(mob) && !live.containsKey(mob.id) && mob.handover == null) {
                asked.add(new ServerMobs.Asked(mob.id, mob.x, mob.y, mob.z, mob.simulated));
            }
        }
        if (!asked.isEmpty()) serverMobs.ask(local, level.dimension(), asked);
    }

    /**
     * How far this server sends each kind of mob: the farthest one of them it sends now,
     * if that is farther than any before. A mob carried or carrying is left out: the
     * server sends a whole stack as far as the farthest-seen of its riders.
     */
    private void learnReach() {
        for (Entity entity : live.values()) {
            if (entity.isPassenger() || entity.isVehicle()) continue;
            double across = Math.hypot(entity.getX() - selfX, entity.getZ() - selfZ);
            reach.merge(entity.getType().getCategory(), across, Math::max);
        }
    }

    /**
     * Back within range of a remembered mob and it is not there: it moved on, or died.
     * Within range means well inside where this server was seen sending that kind of mob,
     * not merely inside the loaded chunks: a server that sends animals to 48 blocks only
     * would otherwise wipe out, a few seconds after they appear, every copy between 48
     * blocks and the render distance - to bring them back when the server next moves them.
     */
    /**
     * How close to the player a copy of this kind of mob cannot be: where the real mob would be
     * sent, and so drawn by the game or by the mod, were it still there. That is how far the
     * server sends this kind (which follows the "Entity Distance" slider in a single player
     * world), within the server's view distance ({@link ServerView}: never Bobby's render
     * distance, whose far chunks hold no mobs), less a margin. The simulation
     * distance does not shrink it: mobs past it still stand in the loaded chunks and are sent.
     */
    public double nearRange(Remembered mob) {
        return nearRange(type(mob));
    }

    private double nearRange(@Nullable EntityType<?> type) {
        return Math.max(24, Math.min(sent(type), renderBlocks) - 16);
    }

    private void reconcile(ClientLevel level, String dimension, int renderDistanceBlocks) {
        List<Remembered> missing = new ArrayList<>();
        double bound = Math.max(24, renderBlocks);
        for (Remembered mob : mobs.values()) {
            // Past the render distance it is never in range: nothing more looked up for it.
            double dx = mob.x - selfX, dz = mob.z - selfZ, across = dx * dx + dz * dz;
            if (across >= bound * bound) {
                mob.missingChecks = 0;
                continue;
            }
            if (!mob.dimension.equals(dimension) || !isHere(mob) || live.containsKey(mob.id)) continue;
            boolean inRange = false;
            {
                double range = nearRange(type(mob));
                inRange = across < range * range
                        && level.hasChunk((int) Math.floor(mob.x) >> 4, (int) Math.floor(mob.z) >> 4);
            }
            mob.missingChecks = inRange ? mob.missingChecks + 1 : 0;
            if (mob.missingChecks >= 5) missing.add(mob);
        }
        for (Remembered mob : missing) {
            if (mob.fromPack) dismissed.put(mob.id, mob.x + mob.y * 31 + mob.z * 961);
            forget(mob.id);
        }
    }

    /**
     * How far, in blocks across, the server sends mobs of a type. In a single player world
     * the answer is known: the type's tracking range scaled by the "Entity Distance" slider,
     * which the integrated server applies to what it sends, not only to what is drawn. On
     * another server, the farthest one of that kind it was seen sending.
     */
    private static double sent(@Nullable EntityType<?> type, Map<MobCategory, Double> reach) {
        if (type == null) return 0;
        int range = type.clientTrackingRange() * 16;
        IntegratedServer local = Minecraft.getInstance().getSingleplayerServer();
        if (local != null) return local.getScaledTrackingDistance(range);
        return Math.min(reach.getOrDefault(type.getCategory(), 0.0), range);
    }

    private double sent(@Nullable EntityType<?> type) {
        return sent(type, reach);
    }

    /** Milliseconds a change in the list of mobs may spend building the copies it shows. */
    private static final long CHANGE_BUDGET = 100;

    /**
     * The kinds shown far away were just changed in the list of mobs: drawn or left out from
     * this frame, not from the next tick, which never comes while the list pauses the game.
     */
    public void kindsChanged() {
        FarConfig config = FarConfig.get();
        ClientLevel level = bound;
        chooseSoon = true;
        if (level == null || file == null || (!config.distantMobs && !config.distantEntities)) return;
        choose(level.dimension().identifier().toString(), config);
        long until = System.nanoTime() + CHANGE_BUDGET * 1_000_000L;
        for (Remembered mob : shown) {
            if (System.nanoTime() > until) break;
            if (mob.puppet != null || mob.failed || mob.gone || mob.handover != null) continue;
            if (mob.parsed == null) mob.parsed = CompletableFuture.completedFuture(parse(mob.nbt));
            if (mob.parsed.isDone()) build(mob, level);
        }
    }

    /**
     * A mob's saved data, read: at once when short, otherwise on a background thread when
     * {@code background} (the game's own, busy with its world as it generates).
     */
    private static CompletableFuture<CompoundTag> read(String nbt, boolean background) {
        return background && nbt.length() > READ_AT_ONCE
                ? CompletableFuture.supplyAsync(() -> parse(nbt), Util.backgroundExecutor())
                : CompletableFuture.completedFuture(parse(nbt));
    }

    /** Whether a copy is in front of the player, across the ground, or right by them. */
    private boolean seen(Remembered mob, Vec3 sight) {
        double dx = mob.x - selfX, dz = mob.z - selfZ, length = Math.sqrt(dx * dx + dz * dz);
        double flat = Math.sqrt(sight.x * sight.x + sight.z * sight.z);
        return length < 16 || flat < 1e-3 || (dx * sight.x + dz * sight.z) / (length * flat) >= SEEN_COS;
    }

    /** The nearest few, in this dimension: the ones drawn until the next choice. */
    private void choose(String dimension, FarConfig config) {
        chooseSoon = false;
        Set<String> wanted = new HashSet<>(config.mobTypes);
        // The mobs and the other entities, each shown or not and each limited on its own: a
        // crowd of mobs never pushes an aircraft out.
        List<Remembered> creatures = new ArrayList<>(), others = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (!mob.dimension.equals(dimension) || !isHere(mob)
                    || (live.containsKey(mob.id) && mob.handover == null) || carried.contains(mob.id)) continue;
            if (!wanted.contains(mob.type) && !(config.rememberNamedMobs && mob.named)) continue;
            boolean creature = creature(mob);
            if (!config.shows(creature)) continue;
            mob.distance = distance(mob.x, mob.y, mob.z);
            // Only a chunk within the render distance can be held: no lookup for those past it.
            if (SeedDraws.foreseen(mob.id) && (!config.seedMobs
                    || mob.distance < renderBlocks + 64 && generated(mob))) continue;
            (creature ? creatures : others).add(mob);
        }
        List<Remembered> chosen = new ArrayList<>(creatures.size() + others.size());
        for (List<Remembered> here : List.of(creatures, others)) {
            int most = config.most(here == creatures, here.size());
            // All of them drawn and no spyglass: no order to put them in. Otherwise the nearest,
            // where the spyglass looks first, however far: the player is looking for them there.
            if (most < here.size() || scoping) {
                for (Remembered mob : here) {
                    if (foresight.inScope(mob.x, mob.y + 0.5, mob.z)) mob.distance -= 1e9;
                }
                here.sort(Comparator.comparingDouble(mob -> mob.distance));
                for (Remembered mob : here) {
                    if (mob.distance < 0) mob.distance += 1e9;
                }
            }
            chosen.addAll(most < here.size() ? here.subList(0, most) : here);
        }
        // Those no longer drawn let their copy go: marked by this choice's number, no set of thousands.
        int choice = ++choices;
        for (Remembered mob : chosen) mob.chosenIn = choice;
        for (Remembered mob : shown) {
            if (mob.chosenIn != choice) mob.puppet = null;
        }
        shown = Collections.unmodifiableList(chosen);
    }

    /** A mob rather than another entity (a boat, a vehicle, a mannequin); one of a kind not known here is taken for a mob. */
    private static boolean creature(Remembered mob) {
        EntityType<?> type = type(mob);
        return type == null || MobKinds.creature(type);
    }

    /** A mob that walks on the ground, and so can fall: not one that flies or swims. */
    private static boolean walker(LivingEntity entity) {
        // 26.2 dropped the interface bees and parrots shared, the only two that had it.
        //? if >=26.2 {
        /*if (entity instanceof Bee || entity instanceof Parrot || entity.isNoGravity()) return false;
        *///?} else {
        if (entity instanceof FlyingAnimal || entity.isNoGravity()) return false;
        //?}
        MobCategory category = entity.getType().getCategory();
        return category != MobCategory.WATER_CREATURE && category != MobCategory.WATER_AMBIENT
                && category != MobCategory.UNDERGROUND_WATER_CREATURE && category != MobCategory.AXOLOTLS;
    }

    private static @Nullable EntityType<?> type(Remembered mob) {
        if (mob.entityType == null) mob.entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(mob.type)).orElse(null);
        return mob.entityType;
    }

    private static @Nullable CompoundTag parse(String nbt) {
        try {
            return TagParser.parseCompoundFully(nbt);
        } catch (Exception e) {
            return null;
        }
    }

    private void build(Remembered mob, ClientLevel level) {
        try {
            EntityType<?> type = type(mob);
            Entity entity = type == null ? null : Puppets.create(type, level);
            CompoundTag tag = mob.parsed == null ? null : mob.parsed.join();
            if (entity == null || tag == null) {
                mob.failed = true;
                return;
            }
            EntityNbt.load(entity, level, tag);
            if (entity instanceof LivingEntity living) {
                // A puppet never ticks, so a hit taken just before it was remembered would
                // leave it flashing red for good.
                living.hurtTime = 0;
                living.hurtDuration = 0;
                living.deathTime = 0;
                // Its saved data turns body and head to its yaw: they face as they were seen.
                living.setYRot(mob.yaw);
                living.yBodyRot = mob.yaw;
                living.yHeadRot = Float.isNaN(mob.head) ? mob.yaw : mob.head;
                living.yBodyRotO = living.yBodyRot;
                living.yHeadRotO = living.yHeadRot;
            }
            MobMotion motion = mob.motion;
            if (motion != null && motion.placed()) {
                entity.setPos(motion.x(), motion.y(), motion.z());
            } else {
                entity.setPos(mob.x, mob.y, mob.z);
            }
            entity.setOldPosAndRot();
            mob.puppet = entity;
        } catch (Exception e) {
            mob.failed = true;
        }
    }

    /**
     * A second copy of a remembered mob, the same to look at (coat, variant) but with no
     * name and an id of its own, never remembered nor added to the level: something the mod
     * draws beside it. Null if its saved data was not read yet. Render thread only.
     */
    public @Nullable LivingEntity twin(Remembered mob, ClientLevel level) {
        CompletableFuture<CompoundTag> parsed = mob.parsed;
        EntityType<?> type = type(mob);
        if (mob.gone || parsed == null || !parsed.isDone() || type == null) return null;
        try {
            CompoundTag tag = parsed.join();
            if (tag == null || !(Puppets.create(type, level) instanceof LivingEntity twin)) return null;
            EntityNbt.load(twin, level, tag);
            twin.setUUID(UUID.randomUUID());
            twin.setCustomName(null);
            twin.hurtTime = 0;
            twin.hurtDuration = 0;
            twin.deathTime = 0;
            return twin;
        } catch (Exception e) {
            return null;
        }
    }

    private double distance(double x, double y, double z) {
        double dx = x - selfX, dy = y - selfY, dz = z - selfZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Forgets every mob, here and in the file. */
    public void forgetAll() {
        foresight.forgetAll();
        for (Remembered mob : mobs.values()) mob.gone = true;
        mobs.clear();
        joining.clear();
        dismissed.clear();
        takenAway.clear();
        shown = List.of();
        dirty = true;
        save();
    }

    // --- File --------------------------------------------------------------------------

    private void save() {
        if (file == null) return;
        dirty = false;
        long started = Profile.begin(Profile.Section.SAVE_MOBS);
        long bytes = -1;
        try {
            // Those foreseen from the seed are the server's to share again, not this client's to keep.
            Collection<Remembered> kept = new ArrayList<>();
            for (Remembered mob : mobs.values()) {
                if (!SeedDraws.foreseen(mob.id)) kept.add(mob);
            }
            JsonStore.write(file, writer -> write(kept, writer));
            bytes = Files.size(file);
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        } finally {
            Profile.end(Profile.Section.SAVE_MOBS, started, bytes);
        }
    }
}
