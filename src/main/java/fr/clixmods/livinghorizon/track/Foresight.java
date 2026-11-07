package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The first animals of the land nobody has explored, foreseen by this client ({@link SeedDraws}),
 * on top of those the server foresees around its players and shares ({@link SeedMobs}).
 *
 * <p>Asked for three ways: by itself, everywhere within <i>Mob Render Distance</i>, so that they
 * stand on the far terrain as Voxy or Distant Horizons draws it; through a spyglass, where the
 * player looks, however far, the middle of the view first and ahead of anything else; and by
 * hand, in a circle of up to {@link #MAX_RADIUS} chunks ({@link #start}, <i>Guess Nearby Mobs</i>).
 *
 * <p>Who works them out: in a world played alone or hosted on LAN, the server in this game
 * ({@link SeedMobs#foresee}), which knows which chunks are generated; on another server, this
 * client, from the seed that server shares when its owner allows it ({@code shareSeed}) and the
 * game's own generator built again ({@link SeedDraws#vanilla}). This client then only knows the
 * chunks it has held itself: those are left out, their real mobs being there.
 *
 * <p>What is found is kept per chunk in a few numbers a mob ({@link #index}): a scan of a thousand
 * chunks around finds about a million animals. Only the nearest become remembered mobs, as many as
 * <i>Max Mobs</i> lets be drawn ({@link #wanted}). Saved per world and dimension in a file of its
 * own, about ten bytes a mob ({@link #save}); read again in the next game, and in a world played
 * here, those of its chunks generated since are left out.
 *
 * <p>On the game's thread but the work, done on threads of its own.
 */
public final class Foresight {
    public static final int MIN_RADIUS = 4;
    /** The top of the settings slider and the command. */
    public static final int MAX_RADIUS = 1024;
    /** Ticks between two automatic requests, two through the spyglass, two saves. */
    private static final int AUTO_EVERY = 5, SCOPE_EVERY = 4, SAVE_EVERY = 6000;
    /** Chunks in an automatic request; through the spyglass; in one part of a scan. */
    private static final int BATCH = 1024, SCOPE_BATCH = 256, SCAN_BATCH = 65536;
    /** What the player sees by itself, asked for first: this far either side of where they look. */
    private static final double VIEW_COS = Math.cos(Math.toRadians(75));
    /** How far the spyglass looks, in chunks, and how wide: about what it shows. */
    private static final int SCOPE_REACH = 256;
    private static final double SCOPE_COS = Math.cos(Math.toRadians(8));
    /** Blocks: how far it foresees by itself when <i>Mob Render Distance</i> has no limit. */
    private static final int UNLIMITED_REACH = 4096;
    /** Longs a mob takes in the index: its id, x and z, then y, kind, variant and age. */
    private static final int LONGS = 4;
    /** The file's first bytes and its layout. */
    private static final int MAGIC = 0x4C484653, FILE_VERSION = 1;
    private static final ExecutorService THREAD = thread("Living Horizon foresight (client)", Thread.MIN_PRIORITY);
    /** Where the spyglass looks, never behind a scan. */
    private static final ExecutorService URGENT = thread("Living Horizon foresight (client, urgent)", Thread.NORM_PRIORITY);
    /** Files read and written, one at a time. */
    private static final ExecutorService FILES = thread("Living Horizon foresight (files)", Thread.MIN_PRIORITY);

    private static ExecutorService thread(String name, int priority) {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        });
    }

    /** Works out the chunks given; the map holds those with animals only. */
    private interface Seer {
        CompletableFuture<Long2ObjectMap<List<PositionsMessage.Mob>>> foresee(long[] chunks, @Nullable IntConsumer progress,
                                                                            boolean urgent);
    }

    /** Where the spyglass looks: from the eye, along a line, a few degrees round it. */
    private record Scope(double x, double y, double z, double dx, double dy, double dz) {
        boolean contains(double px, double py, double pz) {
            double vx = px - x, vy = py - y, vz = pz - z;
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            return length > 1 && (vx * dx + vy * dy + vz * dz) / length >= SCOPE_COS;
        }

        /** How close to the line of sight a chunk's middle is across the ground: 1 on it, below {@link #SCOPE_COS} out of view. */
        double across(double px, double pz) {
            double vx = px - x, vz = pz - z;
            double flat = Math.sqrt(dx * dx + dz * dz), length = Math.sqrt(vx * vx + vz * vz);
            if (flat < 1e-3) return length < 64 ? 1 : -1;
            if (length < 24) return 1;
            return (vx * dx + vz * dz) / (length * flat);
        }

        /** The chunk it looks from and the way it looks, to a degree: what {@link #coneChunks} depends on. */
        long key() {
            int yaw = (int) Math.floor(Math.toDegrees(Math.atan2(dz, dx)));
            boolean down = Math.sqrt(dx * dx + dz * dz) < 1e-3;
            return SeedDraws.chunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4) * 31 + (down ? 1000 : yaw);
        }
    }

    /** A scan by hand under way. */
    private static final class Scan {
        final int radius;
        int chunks, done, mobs, told;

        Scan(int radius) {
            this.radius = radius;
        }
    }

    /** What a file held: the chunks asked for, those with mobs, and the seed their ids come from. */
    record Saved(long seed, Worked worked, Long2ObjectOpenHashMap<long[]> index) {
    }

    private long world = Long.MIN_VALUE;
    private @Nullable String dimension;
    /** Bumped whenever the index changes: what was wanted from it is worked out again then only. */
    private int version;
    /** What {@link #wantedIfChanged} last worked out from: the index, where the player was, the spyglass, the settings. */
    private long wantedFrom = Long.MIN_VALUE;
    /** Chunk to its foreseen mobs, {@link #LONGS} longs each; only chunks with some. Never changed once put. */
    private Long2ObjectOpenHashMap<long[]> index = new Long2ObjectOpenHashMap<>();
    /** Chunks asked for already, whatever came of it. */
    private Worked worked = new Worked();
    /** Bumped when the world or dimension changes: what was asked before is dropped as it comes. */
    private int generation;
    private int clock;
    private @Nullable CompletableFuture<?> asking, scoping;
    private @Nullable Scan scan;
    private @Nullable Scope scope;
    private @Nullable String sharedKey;
    private SeedDraws.@Nullable Source shared;
    private @Nullable CompletableFuture<SeedDraws.@Nullable Source> building;
    /** The seed the ids are drawn from: needed to write them in a few bytes. Null before any. */
    private @Nullable Long seed;
    /** The folder of this server's files; null before {@link #open}. */
    private @Nullable Path folder;
    private boolean dirty;
    private @Nullable CompletableFuture<@Nullable Saved> loading;
    /** The chunks along the spyglass, middle of the view first, for the way it looked last. */
    private long coneKey = Long.MIN_VALUE;
    private long[] cone = new long[0];

    // --- Every tick ------------------------------------------------------------------------

    void tick(Minecraft minecraft, ClientLevel level, LocalPlayer self, long world, FarConfig config,
              @Nullable SharedPositions pack) {
        String here = SharedPositions.dimensionId(level.dimension());
        if (world != this.world || !here.equals(dimension)) reset(world, here);
        clock++;
        if (loading != null && loading.isDone()) restore(minecraft, level, loading.getNow(null));
        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getViewVector(1f);
        scope = config.seedMobs && self.isScoping() ? new Scope(eye.x, eye.y, eye.z, look.x, look.y, look.z) : null;
        if (clock % 20 == 0) forgetHeld(level, self, minecraft.options.getEffectiveRenderDistance() + 2);
        if (dirty && clock % SAVE_EVERY == 0) save();
        if (!config.seedMobs) return;
        Seer seer = null;
        // Where the spyglass looks: on its own, never waiting for the rest.
        if (scope != null && clock % SCOPE_EVERY == 0 && (scoping == null || scoping.isDone())) {
            long[] chunks = coneRequest(scope);
            if (chunks.length > 0 && (seer = seer(minecraft, level, pack)) != null) scoping = ask(minecraft, seer, chunks, true);
        }
        if (clock % AUTO_EVERY == 0 && (asking == null || asking.isDone())) {
            long[] chunks = around(self.getX(), self.getZ(), look.x, look.z, reach(config) / 16 + 1, BATCH);
            if (chunks.length > 0 && (seer != null || (seer = seer(minecraft, level, pack)) != null)) {
                asking = ask(minecraft, seer, chunks, false);
            }
        }
    }

    private CompletableFuture<?> ask(Minecraft minecraft, Seer seer, long[] chunks, boolean urgent) {
        for (long chunk : chunks) worked.mark(chunk);
        int asked = generation;
        return seer.foresee(chunks, null, urgent).whenComplete((found, error) -> minecraft.execute(() -> {
            if (error != null) LivingHorizonClient.LOGGER.debug("Could not foresee the mobs of the chunks around", error);
            if (found != null && asked == generation) take(found);
        }));
    }

    /** Another world or dimension: nothing found applies there. What was found is saved, and the new one's read. */
    void reset(long world, @Nullable String dimension) {
        save();
        this.world = world;
        this.dimension = dimension;
        index = new Long2ObjectOpenHashMap<>();
        worked = new Worked();
        generation++;
        version++;
        asking = scoping = null;
        scan = null;
        scope = null;
        seed = null;
        dirty = false;
        coneKey = Long.MIN_VALUE;
        Path file = file();
        loading = file == null ? null : CompletableFuture.supplyAsync(() -> read(file, dimension), FILES);
    }

    /** The folder this server's foreseen mobs are saved in: read from the next world or dimension on. */
    void open(Path folder) {
        this.folder = folder;
    }

    /** Leaving the server: what was found is saved. */
    void close() {
        reset(Long.MIN_VALUE, null);
        folder = null;
    }

    /** The chunks this client holds are generated: their real mobs are there, never those foreseen. */
    private void forgetHeld(ClientLevel level, LocalPlayer self, int reach) {
        int cx = self.getBlockX() >> 4, cz = self.getBlockZ() >> 4;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) continue;
                long chunk = SeedDraws.chunk(cx + dx, cz + dz);
                if (index.remove(chunk) != null) {
                    dirty = true;
                    version++;
                }
                worked.mark(chunk);
            }
        }
    }

    private void take(Long2ObjectMap<List<PositionsMessage.Mob>> found) {
        for (Long2ObjectMap.Entry<List<PositionsMessage.Mob>> entry : found.long2ObjectEntrySet()) {
            index.put(entry.getLongKey(), pack(entry.getValue()));
        }
        dirty = true;
        if (!found.isEmpty()) version++;
    }

    /** Blocks around the player it foresees by itself: as far as mobs are drawn, and a little more. */
    private static int reach(FarConfig config) {
        return config.mobMaxDistance > 0 ? config.mobMaxDistance + 64 : UNLIMITED_REACH;
    }

    // --- Who works them out ----------------------------------------------------------------

    /** Whether anything can be foreseen here: a world played here, or a server sharing its seed for this dimension. */
    public boolean available(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) return false;
        if (minecraft.getSingleplayerServer() != null) return true;
        return ground(FarPlayerTracker.get().shared().seed(), SharedPositions.dimensionId(level.dimension())) != null;
    }

    private @Nullable Seer seer(Minecraft minecraft, ClientLevel level, @Nullable SharedPositions pack) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ResourceKey<Level> key = level.dimension();
            seed = server.overworld().getSeed();
            return (chunks, progress, urgent) -> SeedMobs.foresee(server, key, chunks, progress, urgent);
        }
        PositionsMessage.Seed shares = pack == null ? null : pack.seed();
        PositionsMessage.SeedGround ground = ground(shares, SharedPositions.dimensionId(level.dimension()));
        if (shares == null || ground == null) return null;
        String key = shares.seed() + "|" + ground;
        if (!key.equals(sharedKey)) {
            sharedKey = key;
            shared = null;
            long number = shares.seed();
            building = CompletableFuture.supplyAsync(() -> {
                try {
                    return SeedDraws.vanilla(ground.dimension(), number, ground.settings(), ground.biomes(), level,
                            level.dimensionType(), level.registryAccess());
                } catch (RuntimeException e) {
                    LivingHorizonClient.LOGGER.warn("Living Horizon: could not build the generator of {} from the shared seed",
                            ground.dimension(), e);
                    return null;
                }
            }, THREAD);
        }
        if (shared == null && building != null && building.isDone()) shared = building.getNow(null);
        SeedDraws.Source source = shared;
        if (source == null) return null;
        seed = source.seed();
        return (chunks, progress, urgent) -> CompletableFuture.supplyAsync(
                () -> SeedMobs.draw(source, chunks, progress, urgent), urgent ? URGENT : THREAD);
    }

    private static PositionsMessage.@Nullable SeedGround ground(PositionsMessage.@Nullable Seed seed, String dimension) {
        if (seed == null) return null;
        for (PositionsMessage.SeedGround ground : seed.dimensions()) {
            if (ground.dimension().equals(dimension)) return ground;
        }
        return null;
    }

    // --- Where to ask ------------------------------------------------------------------------

    /** What to keep of a chunk while going round the rings. */
    private interface Filter {
        boolean keep(int cx, int cz);
    }

    /**
     * The chunks within {@code reach} chunks of a chunk that {@code filter} keeps, ring after ring:
     * the nearest first, with no sorting, and only each ring's edge gone round. {@code most} at most.
     */
    private static long[] rings(int cx, int cz, int reach, int most, Filter filter) {
        LongArrayList chunks = new LongArrayList();
        long reachSquared = (long) reach * reach;
        for (int ring = 0; ring <= reach && chunks.size() < most; ring++) {
            int side = ring == 0 ? 1 : 8 * ring;
            for (int step = 0; step < side && chunks.size() < most; step++) {
                int dx, dz;
                if (ring == 0) {
                    dx = 0;
                    dz = 0;
                } else if (step < 2 * ring) {
                    dx = -ring + step;
                    dz = -ring;
                } else if (step < 4 * ring) {
                    dx = ring;
                    dz = -ring + (step - 2 * ring);
                } else if (step < 6 * ring) {
                    dx = ring - (step - 4 * ring);
                    dz = ring;
                } else {
                    dx = -ring;
                    dz = ring - (step - 6 * ring);
                }
                if ((long) dx * dx + (long) dz * dz > reachSquared) continue;
                if (filter.keep(cx + dx, cz + dz)) chunks.add(SeedDraws.chunk(cx + dx, cz + dz));
            }
        }
        return chunks.toLongArray();
    }

    /**
     * Chunks within {@code reach} chunks of a point not asked for yet, {@code most} at most: those
     * the player sees first (where they look, and right round them), then the others, each the
     * nearest first. A wider <i>Mob Render Distance</i> fills in where they look before behind them.
     */
    private long[] around(double x, double z, double lookX, double lookZ, int reach, int most) {
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
        double flat = Math.sqrt(lookX * lookX + lookZ * lookZ);
        Filter seen = (a, b) -> {
            double dx = (a << 4) + 8 - x, dz = (b << 4) + 8 - z, length = Math.sqrt(dx * dx + dz * dz);
            return length < 64 || flat < 1e-3 || (dx * lookX + dz * lookZ) / (length * flat) >= VIEW_COS;
        };
        long[] first = rings(cx, cz, reach, most, (a, b) -> !worked.contains(SeedDraws.chunk(a, b)) && seen.keep(a, b));
        if (first.length >= most) return first;
        long[] then = rings(cx, cz, reach, most - first.length,
                (a, b) -> !worked.contains(SeedDraws.chunk(a, b)) && !seen.keep(a, b));
        long[] both = java.util.Arrays.copyOf(first, first.length + then.length);
        System.arraycopy(then, 0, both, first.length, then.length);
        return both;
    }

    /**
     * Every chunk the spyglass shows, out to {@link #SCOPE_REACH}: the line of sight first, then
     * wider and wider, each step the nearest first. Worked out again when it looks elsewhere.
     */
    private long[] coneChunks(Scope scope) {
        long key = scope.key();
        if (key == coneKey) return cone;
        int cx = Mth.floor(scope.x()) >> 4, cz = Mth.floor(scope.z()) >> 4;
        double flat = Math.sqrt(scope.dx() * scope.dx() + scope.dz() * scope.dz());
        LongArrayList found = new LongArrayList();
        if (flat < 1e-3) {
            // Straight down: the ground under the player.
            found.addElements(0, rings(cx, cz, 4, Integer.MAX_VALUE, (x, z) -> true));
        } else {
            // Along the line of sight and across it, half a chunk at a time: only the chunks in view.
            double ux = scope.dx() / flat, uz = scope.dz() / flat, ox = scope.x() / 16, oz = scope.z() / 16;
            double tan = Math.tan(Math.acos(SCOPE_COS - 0.01));
            it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            for (double along = 0; along <= SCOPE_REACH; along += 0.5) {
                double width = along * tan + 1;
                for (double side = -width; side <= width; side += 0.5) {
                    int x = Mth.floor(ox + ux * along - uz * side), z = Mth.floor(oz + uz * along + ux * side);
                    long chunk = SeedDraws.chunk(x, z);
                    if (seen.add(chunk) && scope.across((x << 4) + 8, (z << 4) + 8) >= SCOPE_COS - 0.01) found.add(chunk);
                }
            }
        }
        // In steps of half a degree off the line of sight, each step the nearest first.
        long[] all = found.toLongArray();
        long[] order = new long[all.length];
        for (int i = 0; i < all.length; i++) {
            int x = SeedDraws.chunkX(all[i]), z = SeedDraws.chunkZ(all[i]);
            double cos = Mth.clamp(scope.across((x << 4) + 8, (z << 4) + 8), -1, 1);
            long step = (long) (Math.toDegrees(Math.acos(cos)) * 2);
            long distance = (long) (x - cx) * (x - cx) + (long) (z - cz) * (z - cz);
            order[i] = step << 40 | distance << 20 | i;
        }
        java.util.Arrays.sort(order);
        long[] sorted = new long[all.length];
        for (int i = 0; i < all.length; i++) sorted[i] = all[(int) (order[i] & 0xFFFFF)];
        coneKey = key;
        cone = sorted;
        return sorted;
    }

    /** The next chunks to ask for along the spyglass: those not asked for yet, middle of the view first. */
    private long[] coneRequest(Scope scope) {
        LongArrayList chunks = new LongArrayList();
        for (long chunk : coneChunks(scope)) {
            if (worked.contains(chunk)) continue;
            chunks.add(chunk);
            if (chunks.size() >= SCOPE_BATCH) break;
        }
        return chunks.toLongArray();
    }

    // --- By hand ---------------------------------------------------------------------------

    /**
     * Foresees every chunk within {@code radius} chunks, saying so in the chat: the settings
     * button and {@code /livinghorizon foresee}. One at a time.
     */
    public void start(Minecraft minecraft, int radius) {
        ClientLevel level = minecraft.level;
        LocalPlayer self = minecraft.player;
        if (scan != null) {
            tell(minecraft, Component.translatable("livinghorizon.foresee.running"));
            return;
        }
        Seer seer = level == null || self == null ? null : seer(minecraft, level, FarPlayerTracker.get().shared());
        if (seer == null || !FarConfig.get().seedMobs) {
            tell(minecraft, Component.translatable(level != null && available(minecraft) && !FarConfig.get().seedMobs
                    ? "livinghorizon.foresee.off" : "livinghorizon.foresee.unavailable"));
            return;
        }
        int r = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        Scan running = new Scan(r);
        scan = running;
        int asked = generation;
        double x = self.getX(), z = self.getZ();
        tell(minecraft, Component.translatable("livinghorizon.foresee.started", r));
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
        CompletableFuture.runAsync(() -> {
            // Listed here, all of them: those asked for already are asked again, in case.
            long[] all = rings(cx, cz, r, Integer.MAX_VALUE, (any, other) -> true);
            running.chunks = all.length;
            for (int from = 0; from < all.length; from += SCAN_BATCH) {
                if (asked != generation) return;
                long[] part = java.util.Arrays.copyOfRange(all, from, Math.min(all.length, from + SCAN_BATCH));
                int before = from;
                Long2ObjectMap<List<PositionsMessage.Mob>> found = seer.foresee(part,
                        done -> minecraft.execute(() -> progress(minecraft, running, before + done)), false).join();
                int mobs = 0;
                for (List<PositionsMessage.Mob> list : found.values()) mobs += list.size();
                int counted = mobs, reached = before + part.length;
                minecraft.execute(() -> {
                    if (asked != generation) return;
                    for (long chunk : part) worked.mark(chunk);
                    take(found);
                    running.mobs += counted;
                    progress(minecraft, running, reached);
                });
            }
        }, THREAD).whenComplete((done, error) -> minecraft.execute(() -> {
            if (scan == running) scan = null;
            if (asked != generation) return;
            if (error != null) {
                LivingHorizonClient.LOGGER.warn("Living Horizon: could not foresee the mobs of the chunks around", error);
                tell(minecraft, Component.translatable("livinghorizon.foresee.failed"));
                return;
            }
            save();
            tell(minecraft, Component.translatable("livinghorizon.foresee.done", running.mobs, running.radius));
        }));
    }

    /** Every quarter of the way, in the chat. */
    private void progress(Minecraft minecraft, Scan running, int done) {
        running.done = Math.max(running.done, done);
        if (running.chunks < SCAN_BATCH) return;
        int quarter = running.done * 4 / Math.max(1, running.chunks);
        if (quarter <= running.told || quarter >= 4) return;
        running.told = quarter;
        tell(minecraft, Component.translatable("livinghorizon.foresee.progress", quarter * 25));
    }

    private static void tell(Minecraft minecraft, Component message) {
        //? if >=26.1 {
        /*if (minecraft.player != null) minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        if (minecraft.player != null) minecraft.player.displayClientMessage(message, false);
        //?}
    }

    // --- What becomes a remembered mob -------------------------------------------------------

    /**
     * The foreseen mobs worth remembering now: those where the spyglass looks, however far, then
     * those within <i>Mob Render Distance</i>, the nearest first; as many as <i>Max Mobs</i> lets be
     * drawn, since no other could be.
     */
    List<PositionsMessage.Mob> wanted(double x, double y, double z, FarConfig config) {
        if (!config.seedMobs || dimension == null || index.isEmpty()) return List.of();
        List<PositionsMessage.Mob> near = new ArrayList<>();
        List<PositionsMessage.Mob> looked = new ArrayList<>();
        int reach = reach(config);
        double reachSquared = (double) reach * reach;
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4, r = reach / 16 + 1;
        Scope seen = scope;
        if (index.size() < (2 * r + 1) * (2 * r + 1)) {
            for (Long2ObjectMap.Entry<long[]> entry : index.long2ObjectEntrySet()) {
                pick(entry.getValue(), x, y, z, reachSquared, seen, near, looked);
            }
        } else {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    long[] mobs = index.get(SeedDraws.chunk(cx + dx, cz + dz));
                    if (mobs != null) pick(mobs, x, y, z, reachSquared, null, near, looked);
                }
            }
            if (seen != null) {
                for (long chunk : coneChunks(seen)) {
                    int ax = SeedDraws.chunkX(chunk) - cx, az = SeedDraws.chunkZ(chunk) - cz;
                    if (Math.abs(ax) <= r && Math.abs(az) <= r) continue; // gone through above
                    long[] mobs = index.get(chunk);
                    if (mobs != null) pick(mobs, x, y, z, 0, seen, near, looked);
                }
            }
        }
        int most = config.most(true, looked.size() + near.size());
        // All of them wanted: in any order. Otherwise the nearest, those where the spyglass looks first.
        if (most < looked.size() + near.size()) {
            nearest(looked, x, z);
            nearest(near, x, z);
        }
        List<PositionsMessage.Mob> wanted = new ArrayList<>(most);
        for (PositionsMessage.Mob mob : looked) if (wanted.size() < most) wanted.add(mob);
        for (PositionsMessage.Mob mob : near) if (wanted.size() < most) wanted.add(mob);
        return wanted;
    }

    /**
     * {@link #wanted}, or null when nothing it depends on changed since the last call: the index,
     * the chunk the player is in, where the spyglass looks, the settings. Thousands of mobs are
     * then not gone through again for nothing.
     */
    @Nullable List<PositionsMessage.Mob> wantedIfChanged(double x, double y, double z, FarConfig config) {
        Scope seen = scope;
        long from = version;
        from = from * 31 + SeedDraws.chunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        from = from * 31 + (seen == null ? 0 : seen.key());
        from = from * 31 + config.mobMaxDistance;
        from = from * 31 + config.maxDistantMobs;
        from = from * 31 + (config.seedMobs ? 1 : 0);
        if (from == wantedFrom) return null;
        wantedFrom = from;
        return wanted(x, y, z, config);
    }

    /** Sorts mobs nearest first across the ground, each distance worked out once. */
    private static void nearest(List<PositionsMessage.Mob> mobs, double x, double z) {
        int n = mobs.size();
        if (n > 0xFFFFF) {
            mobs.sort(java.util.Comparator.comparingDouble(mob -> (mob.x() - x) * (mob.x() - x) + (mob.z() - z) * (mob.z() - z)));
            return;
        }
        long[] order = new long[n];
        for (int i = 0; i < n; i++) {
            PositionsMessage.Mob mob = mobs.get(i);
            double dx = mob.x() - x, dz = mob.z() - z;
            // A positive double sorts as its bits do: the squared distance above, the place below.
            order[i] = (Double.doubleToRawLongBits(dx * dx + dz * dz) & ~0xFFFFFL) | i;
        }
        java.util.Arrays.sort(order);
        List<PositionsMessage.Mob> sorted = new ArrayList<>(n);
        for (long entry : order) sorted.add(mobs.get((int) (entry & 0xFFFFF)));
        mobs.clear();
        mobs.addAll(sorted);
    }

    private void pick(long[] mobs, double x, double y, double z, double reachSquared, @Nullable Scope seen,
                      List<PositionsMessage.Mob> near, List<PositionsMessage.Mob> looked) {
        for (int i = 0; i < mobs.length; i += LONGS) {
            PositionsMessage.Mob mob = unpack(mobs, i);
            double dx = mob.x() - x, dz = mob.z() - z;
            if (seen != null && seen.contains(mob.x(), mob.y() + 0.5, mob.z())) looked.add(mob);
            else if (dx * dx + dz * dz <= reachSquared) near.add(mob);
        }
    }

    /** Whether a point is where the spyglass looks now: drawn there whatever <i>Mob Render Distance</i> says. */
    public boolean inScope(double x, double y, double z) {
        Scope seen = scope;
        return seen != null && seen.contains(x, y, z);
    }

    /** Foreseen mobs kept, for the debug panel and the game tests. */
    public int size() {
        int mobs = 0;
        for (long[] list : index.values()) mobs += list.length / LONGS;
        return mobs;
    }

    /** What is kept, for the Statistics screen: mobs, chunks with some, chunks asked for, the memory it takes, the folder. */
    public record Stats(int mobs, int chunks, long asked, long bytes, @Nullable Path folder) {
    }

    public Stats stats() {
        int mobs = size();
        long asked = 0;
        for (long[] bits : worked.regions.values()) {
            for (long word : bits) asked += Long.bitCount(word);
        }
        // The longs of each mob, then each chunk's array and its place in the map, then the regions asked for.
        long bytes = (long) mobs * LONGS * 8 + index.size() * 48L + worked.regions.size() * 200L;
        return new Stats(mobs, index.size(), asked, bytes, folder);
    }

    /** Whether a scan by hand is under way. */
    public boolean scanning() {
        return scan != null;
    }

    // --- The index's numbers -----------------------------------------------------------------

    static long[] pack(List<PositionsMessage.Mob> mobs) {
        long[] packed = new long[mobs.size() * LONGS];
        for (int i = 0; i < mobs.size(); i++) {
            PositionsMessage.Mob mob = mobs.get(i);
            pack(packed, i * LONGS, mob.id(), mob.x(), mob.y(), mob.z(), mob.kind(), mob.variant(), mob.baby());
        }
        return packed;
    }

    private static void pack(long[] packed, int at, UUID id, double x, double y, double z, int kind, int variant, boolean baby) {
        packed[at] = id.getMostSignificantBits();
        packed[at + 1] = id.getLeastSignificantBits();
        packed[at + 2] = (long) Float.floatToIntBits((float) x) << 32 | Float.floatToIntBits((float) z) & 0xFFFFFFFFL;
        packed[at + 3] = ((long) Mth.floor(y) & 0xFFFF) << 48 | ((long) kind & 0xFFFF) << 32
                | ((long) variant & 0x7FFFFFFFL) << 1 | (baby ? 1 : 0);
    }

    private PositionsMessage.Mob unpack(long[] packed, int at) {
        long place = packed[at + 2], rest = packed[at + 3];
        double x = Float.intBitsToFloat((int) (place >>> 32)), z = Float.intBitsToFloat((int) place);
        int y = (short) (rest >>> 48);
        int kind = (int) (rest >>> 32) & 0xFFFF;
        int variant = (int) ((rest >>> 1) & 0x7FFFFFFFL);
        return new PositionsMessage.Mob(new UUID(packed[at], packed[at + 1]), x, y, z, kind, dimension,
                (rest & 1) != 0, variant, null);
    }

    /** Chunks asked for, a bit each, in blocks of 32 by 32: a scan of a thousand chunks around takes half a megabyte. */
    static final class Worked {
        private final Long2ObjectOpenHashMap<long[]> regions = new Long2ObjectOpenHashMap<>();

        void mark(long chunk) {
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            long[] bits = regions.computeIfAbsent(SeedDraws.chunk(cx >> 5, cz >> 5), key -> new long[16]);
            int i = (cx & 31) + (cz & 31) * 32;
            bits[i >> 6] |= 1L << (i & 63);
        }

        boolean contains(long chunk) {
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            long[] bits = regions.get(SeedDraws.chunk(cx >> 5, cz >> 5));
            if (bits == null) return false;
            int i = (cx & 31) + (cz & 31) * 32;
            return (bits[i >> 6] & 1L << (i & 63)) != 0;
        }

        /** Every chunk of the other marked here too. */
        void add(Worked other) {
            for (Long2ObjectMap.Entry<long[]> entry : other.regions.long2ObjectEntrySet()) {
                long[] bits = regions.computeIfAbsent(entry.getLongKey(), key -> new long[16]);
                for (int i = 0; i < 16; i++) bits[i] |= entry.getValue()[i];
            }
        }

        Worked copy() {
            Worked copy = new Worked();
            copy.add(this);
            return copy;
        }
    }

    // --- The file ----------------------------------------------------------------------------

    /** This world and dimension's file; null before {@link #open} or outside a world. */
    private @Nullable Path file() {
        if (folder == null || dimension == null || world == Long.MIN_VALUE) return null;
        return folder.resolve(Long.toHexString(world) + "-" + dimension.replaceAll("[^a-z0-9_.-]", "_") + ".bin");
    }

    /** Written on threads of its own, from a copy: the index's arrays are never changed once put. */
    void save() {
        Path file = file();
        Long known = seed;
        if (!dirty || file == null || known == null) return;
        dirty = false;
        Long2ObjectOpenHashMap<long[]> mobs = new Long2ObjectOpenHashMap<>(index);
        Worked asked = worked.copy();
        String here = dimension;
        long at = world;
        FILES.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                try (OutputStream out = Files.newOutputStream(temporary)) {
                    write(out, new Saved(known, asked, mobs), at, here);
                }
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Living Horizon: could not save the mobs foreseen in {}", file, e);
            }
        });
    }

    /**
     * The file: its header, the chunks asked for, then per chunk with mobs a few bytes each: its
     * place among the chunk's draws (its id comes again from the seed), where it stands in the
     * chunk to a 256th of a block, its height, kind and age, and its variant. Gzipped.
     */
    static void write(OutputStream to, Saved saved, long world, String dimension) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(to)))) {
            out.writeInt(MAGIC);
            out.writeInt(FILE_VERSION);
            out.writeLong(world);
            out.writeUTF(dimension);
            out.writeLong(saved.seed());
            out.writeInt(saved.worked().regions.size());
            for (Long2ObjectMap.Entry<long[]> entry : saved.worked().regions.long2ObjectEntrySet()) {
                out.writeLong(entry.getLongKey());
                for (long bits : entry.getValue()) out.writeLong(bits);
            }
            out.writeInt(saved.index().size());
            for (Long2ObjectMap.Entry<long[]> entry : saved.index().long2ObjectEntrySet()) {
                long chunk = entry.getLongKey();
                long[] mobs = entry.getValue();
                int minX = SeedDraws.chunkX(chunk) << 4, minZ = SeedDraws.chunkZ(chunk) << 4;
                out.writeLong(chunk);
                varint(out, mobs.length / LONGS);
                for (int at = 0; at < mobs.length; at += LONGS) {
                    long place = mobs[at + 2], rest = mobs[at + 3];
                    double x = Float.intBitsToFloat((int) (place >>> 32)), z = Float.intBitsToFloat((int) place);
                    varint(out, SeedDraws.index(new UUID(mobs[at], mobs[at + 1])));
                    out.writeShort((int) Math.round((x - minX) * 256));
                    out.writeShort((int) Math.round((z - minZ) * 256));
                    out.writeShort((short) (rest >>> 48));
                    varint(out, ((int) (rest >>> 32) & 0xFFFF) << 1 | (int) (rest & 1));
                    varint(out, (int) ((rest >>> 1) & 0x7FFFFFFFL));
                }
            }
        }
    }

    /** A file written by {@link #write} for this world and dimension; null when there is none, or another's. */
    static @Nullable Saved read(InputStream from, long world, String dimension) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(from)))) {
            if (in.readInt() != MAGIC || in.readInt() != FILE_VERSION) return null;
            if (in.readLong() != world || !in.readUTF().equals(dimension)) return null;
            long seed = in.readLong();
            Worked worked = new Worked();
            for (int regions = in.readInt(); regions > 0; regions--) {
                long[] bits = new long[16];
                long key = in.readLong();
                for (int i = 0; i < 16; i++) bits[i] = in.readLong();
                worked.regions.put(key, bits);
            }
            int chunks = in.readInt();
            Long2ObjectOpenHashMap<long[]> index = new Long2ObjectOpenHashMap<>(chunks);
            for (int c = 0; c < chunks; c++) {
                long chunk = in.readLong();
                int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
                long[] mobs = new long[varint(in) * LONGS];
                for (int at = 0; at < mobs.length; at += LONGS) {
                    UUID id = SeedDraws.id(seed, dimension, cx, cz, varint(in));
                    double x = (cx << 4) + in.readUnsignedShort() / 256.0, z = (cz << 4) + in.readUnsignedShort() / 256.0;
                    int y = in.readShort();
                    int kindBaby = varint(in);
                    pack(mobs, at, id, x, y, z, kindBaby >>> 1, varint(in), (kindBaby & 1) != 0);
                }
                index.put(chunk, mobs);
            }
            return new Saved(seed, worked, index);
        }
    }

    private static @Nullable Saved read(Path file, @Nullable String dimension) {
        if (dimension == null || !Files.exists(file)) return null;
        String name = file.getFileName().toString();
        long world;
        try {
            world = Long.parseUnsignedLong(name.substring(0, name.indexOf('-')), 16);
        } catch (RuntimeException e) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return read(in, world, dimension);
        } catch (IOException | RuntimeException e) {
            LivingHorizonClient.LOGGER.warn("Living Horizon: could not read the mobs foreseen in {}", file, e);
            return null;
        }
    }

    /**
     * What a file held, taken in: in a world played here, only the chunks still not generated,
     * which the server says first. Those found since stay as found.
     */
    private void restore(Minecraft minecraft, ClientLevel level, @Nullable Saved saved) {
        loading = null;
        if (saved == null) return;
        if (seed == null) seed = saved.seed();
        else if (seed != saved.seed()) return;
        IntegratedServer server = minecraft.getSingleplayerServer();
        int asked = generation;
        if (server == null) {
            merge(saved, saved.index().keySet().toLongArray());
            return;
        }
        SeedMobs.unexplored(server, level.dimension(), saved.index().keySet().toLongArray())
                .whenComplete((free, error) -> minecraft.execute(() -> {
                    if (asked != generation) return;
                    if (error != null) {
                        LivingHorizonClient.LOGGER.debug("Could not check the chunks of the mobs foreseen before", error);
                        return;
                    }
                    merge(saved, free);
                }));
    }

    private void merge(Saved saved, long[] keep) {
        worked.add(saved.worked());
        for (long chunk : keep) {
            if (!index.containsKey(chunk)) index.put(chunk, saved.index().get(chunk));
        }
        // Chunks generated since: asked for, none to show.
        if (keep.length != saved.index().size()) dirty = true;
        if (keep.length > 0) version++;
    }

    /** <i>Forget All</i>: what was foreseen goes, here and in every file of this server. */
    void forgetAll() {
        version++;
        index = new Long2ObjectOpenHashMap<>();
        worked = new Worked();
        generation++;
        asking = scoping = null;
        loading = null;
        dirty = false;
        coneKey = Long.MIN_VALUE;
        Path files = folder;
        if (files == null) return;
        FILES.execute(() -> {
            if (!Files.isDirectory(files)) return;
            try (Stream<Path> list = Files.list(files)) {
                for (Path file : list.toList()) Files.deleteIfExists(file);
            } catch (IOException e) {
                LivingHorizonClient.LOGGER.warn("Living Horizon: could not delete the mobs foreseen in {}", files, e);
            }
        });
    }

    private static void varint(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int varint(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("varint too long");
    }
}
