package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distant Horizons' world, through its public API ({@code com.seibel.distanthorizons.api}):
 * the column of blocks at a position, and a ray cast through its level-of-detail data.
 * The API is reached by reflection, like Voxy, so that the mod loads without it and any
 * change on its side turns this source off for the session instead of crashing.
 *
 * <p>Distant Horizons stores coarser data the farther it is, so a column is as precise as
 * the detail level it holds there: good enough for the ground a herd walks on and the
 * hills that hide it.
 */
final class DhSource implements LodSource {
    private boolean ready;
    private volatile boolean broken;
    private volatile @Nullable String failure;
    /** After a failed read, nothing is read until then: Distant Horizons may only have been busy. */
    private volatile long pausedUntil;

    private MethodHandle terrainRepo, worldProxy;
    private MethodHandle worldLoaded, levels, wrapped, columnAt;
    private MethodHandle top, blockOf, biomeOf, clearCache;
    private MethodHandle isAir, isLiquid, isSolid, biomeName;
    /** Before Distant Horizons 3.0 there is no opacity, and before 2.4 no cache: null then. */
    private @Nullable MethodHandle opacity, softCache;
    /**
     * Its database, read directly ({@link #holds}): null on a version whose classes differ, or
     * once a query failed, and its columns are read one by one instead.
     */
    private volatile @Nullable MethodHandle generationSteps;
    private MethodHandle dhWorld, loadedLevels, levelWrapper, dataProvider, providerRepo, sectionAt, newBytes, bytesOf;
    private @Nullable MethodHandle timestampAt;
    /** Sections asked of its database, and those holding at least one chunk. */
    private final AtomicInteger sections = new AtomicInteger(), sectionsHeld = new AtomicInteger();

    /**
     * One reader's cache of Distant Horizons' data, for the level it was made for. Distant
     * Horizons' cache keeps an area as it was first read for as long as memory allows: read
     * before Distant Horizons had filled it in (it builds its terrain over minutes in a new
     * world), the area stayed empty, and a mob standing there stayed hidden over terrain in
     * plain view. So the cache is emptied every {@link #RENEW_NANOS}, and soon after a column
     * comes back empty ({@link #MISSED_NANOS}).
     */
    private static final class Cached {
        final Object level;
        final @Nullable Object cache;
        final Era era;
        long emptied = System.nanoTime();
        boolean missed;

        Cached(Object level, @Nullable Object cache, Era era) {
            this.level = level;
            this.cache = cache;
            this.era = era;
        }
    }

    private static final long RENEW_NANOS = 30_000_000_000L, MISSED_NANOS = 2_000_000_000L;

    /**
     * What reads share until the next {@link #clear()}, which starts another: every reader's
     * cache is made again, and the heights read before are forgotten.
     */
    private static final class Era {
        /** The height of the highest block of each column read, and until when it is trusted. */
        final ConcurrentHashMap<Long, Long> tops = new ConcurrentHashMap<>();
        /** Which chunks of each section its database holds ({@link #holds}), and when that was read. */
        final ConcurrentHashMap<Long, long[]> held = new ConcurrentHashMap<>();
        /** The database of the level last asked about, and that level. */
        volatile @Nullable Database database;
    }

    private record Database(Object level, Object repo) {
    }

    /** A level opened for a read, in the era it was opened in. */
    private record Opened(Object level, Era era) {
    }

    /**
     * The era, and the reads waiting on Distant Horizons. Its reads are not waited for when a
     * world is left, since one can be waiting for the game's thread: a read still running then
     * asks nothing more and keeps nothing ({@link #left}), and {@link #cancel()} calls it off.
     */
    private final ModCalls<Era> calls = new ModCalls<>(new Era());
    /** The last read on this thread was answered "failed" by Distant Horizons, rather than empty. */
    private final ThreadLocal<Boolean> unanswered = ThreadLocal.withInitial(() -> false);

    private final ThreadLocal<Cached> caches = new ThreadLocal<>();

    /** Columns asked for, columns that came back with blocks, and the last thing Distant Horizons said. */
    private final AtomicInteger reads = new AtomicInteger(), found = new AtomicInteger();
    private volatile String lastAnswer = "nothing asked yet";

    @Override
    public String details() {
        String database = generationSteps == null ? "; its database not read"
                : "; its database asked about " + sections.get() + " sections, " + sectionsHeld.get() + " with terrain";
        return reads.get() + " columns read, " + found.get() + " with blocks; last answer: " + lastAnswer + database;
    }

    /** The data points of a column, or null; counts the answers and keeps the last message. */
    private Object @Nullable [] read(Object repo, Opened opened, int x, int z, Object cache) throws Throwable {
        unanswered.set(false);
        if (!calls.enter(opened.era)) return null;
        Object result;
        try {
            result = columnAt.invoke(repo, opened.level, x, z, cache);
        } catch (Throwable e) {
            // Called off as the world was left (a version that lets the interrupt through).
            if (left(opened)) return null;
            throw e;
        } finally {
            calls.leave();
        }
        // Called off, or answered for a world left since: not counted.
        if (left(opened)) return null;
        reads.incrementAndGet();
        if (result == null) {
            lastAnswer = "null";
            return missed();
        }
        Object message = DhLink.message(result);
        if (!DhLink.success(result)) {
            lastAnswer = "failed: " + message;
            unanswered.set(true);
            return missed();
        }
        Object[] points = (Object[]) DhLink.payload(result);
        if (points == null || points.length == 0) {
            lastAnswer = "empty: " + message;
            return missed();
        }
        found.incrementAndGet();
        lastAnswer = "ok: " + message;
        return points;
    }

    @Override
    public String name() {
        return "Distant Horizons";
    }

    @Override
    public boolean loaded() {
        return Platform.isModLoaded("distanthorizons");
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    /** A column without data: this reader's cache may hold its area as it was before Distant Horizons filled it in. */
    private Object @Nullable [] missed() {
        Cached cached = caches.get();
        if (cached != null) cached.missed = true;
        return null;
    }

    @Override
    public void clear() {
        calls.moveOn(new Era());
    }

    /**
     * Distant Horizons shuts its threads down as the player leaves, and a read it had queued
     * then never ends: its reader waited for good, a reader fewer every time a world was left.
     * Interrupted, the wait ends, and Distant Horizons answers that the read was cancelled.
     */
    @Override
    public void cancel() {
        calls.callOff();
    }

    /** A read whose world was left since it was opened: nothing more is asked for it. */
    private boolean left(Opened opened) {
        return opened.era != calls.world();
    }

    private static final long TOP_SECONDS = 90;
    private static final int TOPS_KEPT = 300_000;

    /**
     * The top (exclusive) of the highest solid block of a column, or {@link Integer#MIN_VALUE}
     * where there is none. A line of sight asks about thousands of columns, the same ones again
     * and again, and every read makes Distant Horizons build a block of objects: a column is
     * read once, then remembered for a while.
     */
    private int topOf(Object repo, Opened opened, Object cache, int x, int z) throws Throwable {
        ConcurrentHashMap<Long, Long> tops = opened.era.tops;
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        long now = System.currentTimeMillis() / 1000;
        Long known = tops.get(key);
        if (known != null && (known >>> 32) > now) return (int) known.longValue();
        Object[] points = read(repo, opened, x, z, cache);
        int best = Integer.MIN_VALUE;
        if (points != null) {
            for (Object point : points) {
                Object block = blockOf.invoke(point);
                if (block == null || (boolean) isAir.invoke(block) || !opaque(block)) continue;
                best = Math.max(best, (int) top.invoke(point));
            }
        }
        if (tops.size() > TOPS_KEPT) tops.clear();
        tops.put(key, (now + TOP_SECONDS) << 32 | (best & 0xFFFFFFFFL));
        return best;
    }

    @Override
    public synchronized boolean available() {
        if (broken || System.nanoTime() < pausedUntil) return false;
        if (!ready) {
            ready = true;
            if (!loaded()) {
                broken = true;
                return false;
            }
            try {
                if (!DhLink.linked()) throw new IllegalStateException("its API is not the one this mod knows");
                link();
                linkDatabase();
            } catch (Throwable e) {
                broken = true;
                failure = "Distant Horizons found, but too old or unknown (its world needs 2.3 or newer): " + e;
                LivingHorizonClient.LOGGER.warn("Distant Horizons found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    /**
     * Distant Horizons' render distance, from its settings through its API, as far as its far
     * fog is still thin: its start and end are fractions of the render distance. 0 with its
     * rendering off or no settings to read.
     */
    @Override
    public double clearDistance() {
        return distance(true);
    }

    /** Distant Horizons' render distance, {@link #clearDistance} without its fog. */
    @Override
    public double drawnDistance() {
        return distance(false);
    }

    private static double distance(boolean clear) {
        String api = DhLink.API, config = api + "interfaces.config.";
        try {
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            if (configs == null) return 0;
            Object graphics = call(config + "IDhApiConfig", configs, "graphics");
            String graphicsType = config + "client.IDhApiGraphicsConfig";
            if (!(Boolean) value(call(graphicsType, graphics, "renderingEnabled"))) return 0;
            double drawn = ((Number) value(call(graphicsType, graphics, "chunkRenderDistance"))).doubleValue() * 16;
            if (!clear) return drawn;
            try {
                Object fog = call(graphicsType, graphics, "fog");
                if (!(Boolean) value(call(config + "client.IDhApiFogConfig", fog, "enableDhFog"))) return drawn * 0.9;
                Object far = call(config + "client.IDhApiFogConfig", fog, "farFog");
                double start = ((Number) value(call(config + "client.IDhApiFarFogConfig", far, "farFogStartDistance"))).doubleValue();
                double end = ((Number) value(call(config + "client.IDhApiFarFogConfig", far, "farFogEndDistance"))).doubleValue();
                // A little way into the fog, while it is still thin.
                return drawn * Math.max(0.1, Math.min(0.9, start + (end - start) * 0.2));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A version whose fog settings this does not know: where its fog starts by default.
                return drawn * 0.4;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** A method of no arguments of one of Distant Horizons' API interfaces. */
    private static Object call(String type, Object on, String method) throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(on);
    }

    /** What one of its settings is set to. */
    private static Object value(Object setting) throws ReflectiveOperationException {
        return call(DhLink.API + "interfaces.config.IDhApiConfigValue", setting, "getValue");
    }

    @Override
    public void fail(Throwable e) {
        // Not for good: a level change or a section being rewritten can fail one read, and
        // nothing would then hide a mob behind a hill until the game restarted.
        pausedUntil = System.nanoTime() + 20_000_000_000L;
        failure = "Distant Horizons did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Reading Distant Horizons' world paused for 20 seconds: it did not answer as expected", e);
    }

    private void link() throws ReflectiveOperationException {
        String api = DhLink.API;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> repo = Class.forName(api + "interfaces.data.IDhApiTerrainDataRepo");
        Class<?> proxy = Class.forName(api + "interfaces.world.IDhApiWorldProxy");
        Class<?> level = Class.forName(api + "interfaces.world.IDhApiLevelWrapper");
        Class<?> cache = Class.forName(api + "interfaces.data.IDhApiTerrainDataCache");
        Class<?> unsafe = Class.forName(api + "interfaces.IDhApiUnsafeWrapper");
        Class<?> result = DhLink.resultType();
        Class<?> point = Class.forName(api + "objects.data.DhApiTerrainDataPoint");
        Class<?> block = Class.forName(api + "interfaces.block.IDhApiBlockStateWrapper");
        Class<?> biome = Class.forName(api + "interfaces.block.IDhApiBiomeWrapper");
        // Filled in by Distant Horizons once it has started: read at every use.
        terrainRepo = DhLink.delayed("terrainRepo", repo);
        worldProxy = DhLink.delayed("worldProxy", proxy);
        worldLoaded = lookup.findVirtual(proxy, "worldLoaded", MethodType.methodType(boolean.class));
        levels = lookup.findVirtual(proxy, "getAllLoadedLevelWrappers", MethodType.methodType(Iterable.class));
        wrapped = lookup.findVirtual(unsafe, "getWrappedMcObject", MethodType.methodType(Object.class));
        columnAt = lookup.findVirtual(repo, "getColumnDataAtBlockPos",
                MethodType.methodType(result, level, int.class, int.class, cache));
        softCache = Links.optional(lookup, repo, "createSoftCache", MethodType.methodType(cache));
        clearCache = lookup.findVirtual(cache, "clear", MethodType.methodType(void.class));
        if (softCache == null) DhLink.fallback("no cache for its world (before 2.4)");
        top = lookup.findGetter(point, "topYBlockPos", int.class);
        blockOf = lookup.findGetter(point, "blockStateWrapper", block);
        biomeOf = lookup.findGetter(point, "biomeWrapper", biome);
        isAir = lookup.findVirtual(block, "isAir", MethodType.methodType(boolean.class));
        isLiquid = lookup.findVirtual(block, "isLiquid", MethodType.methodType(boolean.class));
        isSolid = lookup.findVirtual(block, "isSolid", MethodType.methodType(boolean.class));
        opacity = Links.optional(lookup, block, "getOpacity", MethodType.methodType(int.class));
        if (opacity == null) DhLink.fallback("no opacity, solid blocks and liquids stop the eye (before 3.0)");
        biomeName = lookup.findVirtual(biome, "getName", MethodType.methodType(String.class));
    }

    /** Whether a block stops the eye; without opacity (before 3.0), solid blocks and liquids do. */
    private boolean opaque(Object block) throws Throwable {
        if (opacity != null) return (int) opacity.invoke(block) > 0;
        return (boolean) isSolid.invoke(block) || (boolean) isLiquid.invoke(block);
    }

    /** The level Distant Horizons holds for the one the player is in; null while it has none. */
    @Override
    public @Nullable Object open() throws Throwable {
        // First of all: a clear() from here on leaves this read behind, whichever level it finds.
        Era era = calls.world();
        // Both are filled in by Distant Horizons once it has started: until then, nothing to read.
        Object proxy = worldProxy.invoke();
        if (proxy == null || terrainRepo.invoke() == null || !(boolean) worldLoaded.invoke(proxy)) return null;
        Level current = Minecraft.getInstance().level;
        if (current == null) return null;
        // In single player Distant Horizons' levels wrap the server's, one per dimension, in no
        // order: never the client's own level. The one of the player's dimension, then; the first
        // one, as before, read another dimension's terrain (the End's void, the Nether's roof).
        Object sameDimension = null;
        for (Object level : (Iterable<?>) levels.invoke(proxy)) {
            Object game = wrapped.invoke(level);
            if (game == current) return new Opened(level, era);
            if (sameDimension == null && game instanceof Level other && other.dimension().equals(current.dimension())) {
                sameDimension = level;
            }
        }
        return sameDimension == null ? null : new Opened(sameDimension, era);
    }

    /**
     * Distant Horizons keeps its terrain in a database, by sections of 64 by 64 blocks (4 by 4
     * chunks), with how far each column was generated. Which sections exist, and which of their
     * columns hold terrain, is one small query a section, where reading a column through its API
     * loads or builds the whole section's terrain (a quarter of a second each, measured). Not
     * called off as a world is left: it never waits for Distant Horizons' threads, and an
     * interrupt could close its database.
     */
    private void linkDatabase() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            String core = "com.seibel.distanthorizons.core.";
            Class<?> shared = Class.forName(core + "api.internal.SharedApi");
            Class<?> world = Class.forName(core + "world.IDhWorld");
            Class<?> level = Class.forName(core + "level.IDhLevel");
            Class<?> wrapper = Class.forName(core + "wrapperInterfaces.world.ILevelWrapper");
            Class<?> provider = Class.forName(core + "file.fullDatafile.V2.FullDataSourceProviderV2");
            Class<?> repo = Class.forName(core + "sql.repo.FullDataSourceV2Repo");
            Class<?> section = Class.forName(core + "pos.DhSectionPos");
            Class<?> bytes = Class.forName("it.unimi.dsi.fastutil.bytes.ByteArrayList", true, repo.getClassLoader());
            dhWorld = lookup.findStatic(shared, "getAbstractDhWorld",
                    MethodType.methodType(Class.forName(core + "world.AbstractDhWorld")));
            loadedLevels = lookup.findVirtual(world, "getAllLoadedLevels", MethodType.methodType(Iterable.class));
            levelWrapper = lookup.findVirtual(level, "getLevelWrapper", MethodType.methodType(wrapper));
            dataProvider = lookup.findVirtual(level, "getFullDataProvider", MethodType.methodType(provider));
            providerRepo = lookup.findGetter(provider, "repo", repo);
            sectionAt = lookup.findStatic(section, "encode", MethodType.methodType(long.class, byte.class, int.class, int.class));
            newBytes = lookup.findConstructor(bytes, MethodType.methodType(void.class));
            bytesOf = lookup.findVirtual(bytes, "toByteArray", MethodType.methodType(byte[].class));
            timestampAt = Links.optional(lookup, repo, "getTimestampForPos", MethodType.methodType(Long.class, long.class));
            generationSteps = lookup.findVirtual(repo, "getColumnGenerationStepForPos", MethodType.methodType(void.class, long.class, bytes));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            generationSteps = null;
            DhLink.fallback("no look at its database: the ground under distant mobs is read column by column, slowly");
        }
    }

    private static final long HELD_NANOS = 2_000_000_000L;
    private static final int SECTIONS_KEPT = 50_000;

    @Override
    public @Nullable Boolean holds(Object world, int x, int z) throws Throwable {
        MethodHandle steps = generationSteps;
        if (steps == null) return null;
        Opened opened = (Opened) world;
        if (left(opened)) return null;
        int cx = x >> 4, cz = z >> 4;
        long key = (long) (cx >> 2) << 32 | ((cz >> 2) & 0xFFFFFFFFL);
        long now = System.nanoTime();
        ConcurrentHashMap<Long, long[]> held = opened.era.held;
        long[] known = held.get(key);
        if (known == null || now - known[1] > HELD_NANOS) {
            Long mask;
            try {
                mask = chunksHeld(steps, opened, cx >> 2, cz >> 2);
            } catch (Throwable e) {
                // Its database is not what this mod knows: its columns are read instead.
                generationSteps = null;
                DhLink.fallback("its database did not answer as expected, read column by column: " + e);
                LivingHorizonClient.LOGGER.warn("Distant Horizons' database did not answer as expected: its columns are read one by one", e);
                return null;
            }
            if (mask == null || left(opened)) return null;
            if (held.size() > SECTIONS_KEPT) held.clear();
            held.put(key, known = new long[]{mask, now});
        }
        return (known[0] >> ((cx & 3) * 4 + (cz & 3)) & 1) != 0;
    }

    /**
     * The chunks of a section that hold terrain, a bit each ({@code x * 4 + z} within the
     * section); 0 where the database has no such section, null where it cannot tell.
     */
    private @Nullable Long chunksHeld(MethodHandle steps, Opened opened, int sx, int sz) throws Throwable {
        Object repo = repoOf(opened);
        if (repo == null) return null;
        long section = (long) sectionAt.invoke((byte) 6, sx, sz);
        Object list = newBytes.invoke();
        steps.invoke(repo, section, list);
        byte[] generated = (byte[]) bytesOf.invoke(list);
        sections.incrementAndGet();
        if (generated.length == 0) {
            // No steps: no such section, unless a row without them (cannot tell, then).
            return timestampAt != null && timestampAt.invoke(repo, section) != null ? null : 0L;
        }
        if (generated.length != 64 * 64) return null;
        long mask = 0;
        // A column at (x, z) within the section is at x * 64 + z; 0 is a column never generated.
        for (int chunk = 0; chunk < 16; chunk++) {
            int fromX = (chunk >> 2) * 16, fromZ = (chunk & 3) * 16;
            search:
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    if (generated[(fromX + i) * 64 + fromZ + j] != 0) {
                        mask |= 1L << chunk;
                        break search;
                    }
                }
            }
        }
        if (mask != 0) sectionsHeld.incrementAndGet();
        return mask;
    }

    /** The database of the level read, found among Distant Horizons' levels by its wrapper. */
    private @Nullable Object repoOf(Opened opened) throws Throwable {
        Database database = opened.era.database;
        if (database != null && database.level == opened.level) return database.repo;
        Object world = dhWorld.invoke();
        if (world == null) return null;
        Object game = wrapped.invoke(opened.level);
        for (Object level : (Iterable<?>) loadedLevels.invoke(world)) {
            Object wrapper = levelWrapper.invoke(level);
            if (wrapper == null) continue;
            if (wrapper == opened.level || game != null && wrapped.invoke(wrapper) == game) {
                Object repo = providerRepo.invoke(dataProvider.invoke(level));
                if (repo == null) return null;
                opened.era.database = new Database(opened.level, repo);
                return repo;
            }
        }
        return null;
    }

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable {
        Opened opened = (Opened) world;
        if (left(opened)) return null;
        Object[] points = read(terrainRepo.invoke(), opened, x, z, cache(opened));
        // Distant Horizons said it could not answer: not the same as a column it holds nothing in.
        if (points == null && unanswered.get()) throw LodWorld.Unanswered.INSTANCE;
        if (points == null) return null;
        Object best = null;
        int bestTop = Integer.MIN_VALUE;
        for (Object point : points) {
            Object block = blockOf.invoke(point);
            if (block == null || (boolean) isAir.invoke(block)) continue;
            int y = (int) top.invoke(point);
            if (y > bestTop) {
                bestTop = y;
                best = point;
            }
        }
        if (best == null) return null;
        Object block = blockOf.invoke(best);
        Object biome = biomeOf.invoke(best);
        String name = biome == null ? "" : String.valueOf(biomeName.invoke(biome));
        BlockState state = wrapped.invoke(block) instanceof BlockState found ? found : null;
        boolean water = state != null ? state.getFluidState().is(FluidTags.WATER) : (boolean) isLiquid.invoke(block);
        // The top of a data point is exclusive: the block itself is the one below.
        return new LodWorld.Surface(bestTop - 1, name, water, state);
    }

    /**
     * A cache of Distant Horizons' data for this thread and level: without one its reads are
     * far slower. A new one when the level is another.
     */
    private Object cache(Opened opened) throws Throwable {
        Cached cached = caches.get();
        if (cached == null || cached.level != opened.level || cached.era != opened.era) {
            if (cached != null && cached.cache != null) clearCache.invoke(cached.cache);
            // Without a cache (before 2.4), Distant Horizons reads without one.
            cached = new Cached(opened.level, softCache == null ? null : softCache.invoke(terrainRepo.invoke()), opened.era);
            caches.set(cached);
        } else if (cached.cache != null) {
            long now = System.nanoTime();
            if (now - cached.emptied > RENEW_NANOS || cached.missed && now - cached.emptied > MISSED_NANOS) {
                clearCache.invoke(cached.cache);
                cached.emptied = now;
                cached.missed = false;
            }
        }
        return cached.cache;
    }

    /**
     * Walks the ray a block at a time near the eye and in longer steps farther away, where
     * Distant Horizons' data is coarser, and stops where it goes under the highest block of a
     * column. Caves under a hill do not count: from far away that is all the data tells.
     * Its own {@code raycast} looks at nine columns per block and gives up at the first column
     * without data, which is far too slow and too fragile for rays of hundreds of blocks.
     * A column without data is walked through.
     */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                           double from, double to) throws Throwable {
        Opened opened = (Opened) world;
        if (left(opened)) return Double.NaN;
        Object repo = terrainRepo.invoke();
        Object cache = cache(opened);
        long column = Long.MIN_VALUE;
        int height = Integer.MIN_VALUE;
        for (double t = from; t < to; t += Math.clamp(t / 48.0, 1.0, 8.0)) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (key != column) {
                column = key;
                height = topOf(repo, opened, cache, x, z);
            }
            // Under the highest block of a column: inside the ground, or a tree on it.
            if (y < height) return t;
        }
        return Double.NaN;
    }
}
