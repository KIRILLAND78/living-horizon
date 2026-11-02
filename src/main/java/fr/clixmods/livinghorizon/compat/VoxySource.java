package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voxy's world, one block per voxel, in sections of 32 blocks a side at level 0. Voxy has
 * no API for this: its classes are reached by reflection so that the mod still loads
 * without it, and any failure turns this source off for the session.
 */
final class VoxySource implements LodSource {
    private static final double STEP = 0.75;
    /** Sections each reader keeps acquired between reads: 256 KiB each in Voxy's memory. */
    private static final int CACHED_SECTIONS = 96;
    /** A section Voxy did not have is asked for again after this long: it may have it by then. */
    private static final long MISS_NANOS = 2_000_000_000L;

    /** Where Voxy had nothing, and since when. */
    private record Miss(long at) {
    }

    private volatile boolean ready;
    private volatile boolean broken;
    /** After a failed read, nothing is read until then: Voxy's world may only have been closing or opening. */
    private volatile long pausedUntil;
    private volatile @Nullable String failure;
    private MethodHandle renderSystem, engine, acquire, data, release, mapper, opacity, isAir, isLive;
    private MethodHandle biomeId, biomeEntries, biomeName, blockState, blockId;

    @Override
    public String name() {
        return "Voxy";
    }

    @Override
    public boolean loaded() {
        return Platform.isModLoaded("voxy");
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    /**
     * Every section held is let go now, whatever thread holds it: Voxy waits for them before
     * it closes its world, and a reader only lets go at its next read - which never comes
     * once the world is left. Called with no read running.
     */
    @Override
    public void clear() {
        generation++;
        if (!ready || broken) return;
        for (Cache cache : ALL_CACHES) releaseAll(cache);
    }

    /** Each reader keeps its sections between reads: {@link #clear()} waits for the reads to end. */
    @Override
    public boolean keepsBetweenReads() {
        return true;
    }

    /** One reader's sections, for one world; a {@link Miss} where Voxy had none. */
    private static final class Cache {
        final LinkedHashMap<Long, Object> sections = new LinkedHashMap<>(128, 0.75f, true);
        @Nullable Object world;
        int generation;
    }

    /** Every reader's cache, so that they can all be emptied from the game's thread. */
    private final Set<Cache> ALL_CACHES = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Cache> CACHES = ThreadLocal.withInitial(() -> {
        Cache cache = new Cache();
        ALL_CACHES.add(cache);
        return cache;
    });
    /** Bumped when the world changes: every reader lets go of what it holds at its next read. */
    private volatile int generation;

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable {
        Object types = (Object) mapper.invokeExact(world);
        for (int y = 319; y >= -64; y--) {
            long[] voxels = section(world, x >> 5, y >> 5, z >> 5,
                    ((long) (x >> 5) & 0xFFFFFFL) | ((long) (z >> 5) & 0xFFFFFFL) << 24 | ((long) (y >> 5) & 0xFFL) << 48);
            if (voxels == null) {
                y -= y & 31; // a whole empty section: on to the one below
                continue;
            }
            long id = voxels[(y & 31) << 10 | (z & 31) << 5 | (x & 31)];
            if ((boolean) isAir.invokeExact(id)) continue;
            Object[] biomes = (Object[]) biomeEntries.invokeExact(types);
            int b = (int) biomeId.invokeExact(id);
            String biome = b >= 0 && b < biomes.length ? (String) biomeName.invokeExact(biomes[b]) : "";
            BlockState state = (BlockState) blockState.invokeExact(types, (int) blockId.invokeExact(id));
            return new LodWorld.Surface(y, biome, state != null && state.getFluidState().is(FluidTags.WATER), state);
        }
        return null;
    }

    @Override
    public synchronized boolean available() {
        if (broken || System.nanoTime() < pausedUntil) return false;
        if (!ready) {
            ready = true;
            if (!Platform.isModLoaded("voxy")) {
                broken = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                broken = true;
                failure = "Voxy found, but not the version this mod knows: " + e;
                LivingHorizonClient.LOGGER.warn("Voxy found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    /**
     * Voxy's render distance, from its settings: {@code sectionRenderDistance} times 512
     * blocks (its menu shows it times 32, in chunks); half of that while its fog or fade is
     * on, which hides the far part. 0 with its rendering off, or a version this does not know.
     */
    @Override
    public double clearDistance() {
        return distance(true);
    }

    /** Voxy's render distance, {@link #clearDistance} without its fog. */
    @Override
    public double drawnDistance() {
        return distance(false);
    }

    private static double distance(boolean clear) {
        try {
            Class<?> type = Class.forName("me.cortex.voxy.client.config.VoxyConfig");
            Object config = type.getField("CONFIG").get(null);
            if (config == null) return 0;
            try {
                if (!(boolean) type.getMethod("isRenderingEnabled").invoke(config)) return 0;
            } catch (NoSuchMethodException e) {
                // A version without it: drawing, as far as this can tell.
            }
            double drawn = type.getField("sectionRenderDistance").getFloat(config) * 512.0;
            if (!clear) return drawn;
            return drawn * (fogged(type, config) ? 0.5 : 0.9);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** Voxy's fog or fade on: its {@code getFogMode()} from 0.2.19, {@code useEnvironmentalFog} before. */
    private static boolean fogged(Class<?> type, Object config) throws ReflectiveOperationException {
        try {
            Object mode = type.getMethod("getFogMode").invoke(config);
            return mode.getClass().getField("hasFog").getBoolean(mode) || mode.getClass().getField("hasFade").getBoolean(mode);
        } catch (NoSuchMethodException e) {
            return type.getField("useEnvironmentalFog").getBoolean(config);
        }
    }

    @Override
    public void fail(Throwable e) {
        // Not for good: a world closing or opening as the player joins fails one read ("World
        // is not live"), and the mobs would then find no ground until the game restarted.
        pausedUntil = System.nanoTime() + 20_000_000_000L;
        failure = "Voxy did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Reading Voxy's world paused for 20 seconds: it did not answer as expected", e);
    }

    private void link() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        // Found the way VoxyLink finds it for the depth: one place to follow Voxy's renames.
        Class<?> getter = VoxyLink.renderSystemGetter();
        Class<?> system = Class.forName(VoxyLink.RENDER_SYSTEM);
        Class<?> worldEngine = Class.forName("me.cortex.voxy.common.world.WorldEngine");
        Class<?> section = Class.forName("me.cortex.voxy.common.world.WorldSection");
        Class<?> mapperClass = Class.forName("me.cortex.voxy.common.world.other.Mapper");
        // Erased to Object so that invokeExact works without Voxy's classes in sight:
        // an exact call costs what a plain call does, a generic one boxes every argument.
        renderSystem = lookup.findStatic(getter, "getNullable", MethodType.methodType(system))
                .asType(MethodType.methodType(Object.class));
        engine = lookup.findVirtual(system, "getEngine", MethodType.methodType(worldEngine))
                .asType(MethodType.methodType(Object.class, Object.class));
        isLive = lookup.findVirtual(worldEngine, "isLive", MethodType.methodType(boolean.class))
                .asType(MethodType.methodType(boolean.class, Object.class));
        acquire = lookup.findVirtual(worldEngine, "acquireIfExists",
                        MethodType.methodType(section, int.class, int.class, int.class, int.class))
                .asType(MethodType.methodType(Object.class, Object.class, int.class, int.class, int.class, int.class));
        data = lookup.findVirtual(section, "_unsafeGetRawDataArray", MethodType.methodType(long[].class))
                .asType(MethodType.methodType(long[].class, Object.class));
        release = lookup.findVirtual(section, "release", MethodType.methodType(int.class))
                .asType(MethodType.methodType(int.class, Object.class));
        mapper = lookup.findVirtual(worldEngine, "getMapper", MethodType.methodType(mapperClass))
                .asType(MethodType.methodType(Object.class, Object.class));
        opacity = lookup.findVirtual(mapperClass, "getBlockStateOpacity", MethodType.methodType(int.class, long.class))
                .asType(MethodType.methodType(int.class, Object.class, long.class));
        isAir = lookup.findStatic(mapperClass, "isAir", MethodType.methodType(boolean.class, long.class));
        biomeId = lookup.findStatic(mapperClass, "getBiomeId", MethodType.methodType(int.class, long.class));
        blockId = lookup.findStatic(mapperClass, "getBlockId", MethodType.methodType(int.class, long.class));
        Class<?> biomeEntry = Class.forName("me.cortex.voxy.common.world.other.Mapper$BiomeEntry");
        biomeEntries = lookup.findVirtual(mapperClass, "getBiomeEntries", MethodType.methodType(biomeEntry.arrayType()))
                .asType(MethodType.methodType(Object[].class, Object.class));
        biomeName = lookup.findGetter(biomeEntry, "biome", String.class)
                .asType(MethodType.methodType(String.class, Object.class));
        blockState = lookup.findVirtual(mapperClass, "getBlockStateFromBlockId",
                        MethodType.methodType(BlockState.class, int.class))
                .asType(MethodType.methodType(BlockState.class, Object.class, int.class));
    }

    /** Voxy's world for this reader, after letting go of what it held for another one. */
    @Override
    public @Nullable Object open() throws Throwable {
        Object system = (Object) renderSystem.invokeExact();
        if (system == null) return null;
        Object world = (Object) engine.invokeExact(system);
        if (world == null || !(boolean) isLive.invokeExact(world)) return null;
        Cache cache = CACHES.get();
        if (world != cache.world || cache.generation != generation) {
            releaseAll(cache);
            cache.world = world;
            cache.generation = generation;
        }
        return world;
    }

    /** Distance to the first opaque voxel along a unit direction, between two distances; NaN if none. */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                                   double from, double to) throws Throwable {
        Object types = (Object) mapper.invokeExact(world);
        long key = Long.MIN_VALUE;
        long[] voxels = null;
        for (double t = from; t < to; t += STEP) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long k = ((long) (x >> 5) & 0xFFFFFFL) | ((long) (z >> 5) & 0xFFFFFFL) << 24 | ((long) (y >> 5) & 0xFFL) << 48;
            if (k != key) {
                key = k;
                voxels = section(world, x >> 5, y >> 5, z >> 5, k);
            }
            if (voxels == null) continue;
            long id = voxels[(y & 31) << 10 | (z & 31) << 5 | (x & 31)];
            if (!(boolean) isAir.invokeExact(id) && (int) opacity.invokeExact(types, id) > 0) return t;
        }
        return Double.NaN;
    }

    /** Every block of the slice, as Voxy keeps them: plants, paths and fences included. */
    @Override
    public BlockState @Nullable [] slice(Object world, int x, int z, int bottom, int height) throws Throwable {
        Object types = (Object) mapper.invokeExact(world);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] slice = new BlockState[height];
        boolean any = false;
        for (int k = 0; k < height; k++) {
            int y = bottom + k;
            long[] voxels = section(world, x >> 5, y >> 5, z >> 5,
                    ((long) (x >> 5) & 0xFFFFFFL) | ((long) (z >> 5) & 0xFFFFFFL) << 24 | ((long) (y >> 5) & 0xFFL) << 48);
            slice[k] = air;
            if (voxels == null) continue;
            any = true;
            long id = voxels[(y & 31) << 10 | (z & 31) << 5 | (x & 31)];
            if ((boolean) isAir.invokeExact(id)) continue;
            BlockState state = (BlockState) blockState.invokeExact(types, (int) blockId.invokeExact(id));
            if (state != null) slice[k] = state;
        }
        return any ? slice : null;
    }

    /** A level 0 section's voxels, from this reader's cache or from Voxy; null where Voxy has nothing. */
    private long @Nullable [] section(Object world, int sx, int sy, int sz, long key) throws Throwable {
        LinkedHashMap<Long, Object> sections = CACHES.get().sections;
        Object section = sections.get(key);
        if (section == null || section instanceof Miss miss && System.nanoTime() - miss.at() > MISS_NANOS) {
            section = (Object) acquire.invokeExact(world, 0, sx, sy, sz);
            if (section == null) section = new Miss(System.nanoTime());
            sections.put(key, section);
            if (sections.size() > CACHED_SECTIONS) {
                Iterator<Map.Entry<Long, Object>> oldest = sections.entrySet().iterator();
                Object evicted = oldest.next().getValue();
                oldest.remove();
                if (!(evicted instanceof Miss)) {
                    int ignored = (int) release.invokeExact(evicted);
                }
            }
        }
        return section instanceof Miss ? null : (long[]) data.invokeExact(section);
    }

    private void releaseAll(Cache cache) {
        try {
            for (Object section : cache.sections.values()) {
                if (!(section instanceof Miss)) {
                    int ignored = (int) release.invokeExact(section);
                }
            }
        } catch (Throwable e) {
            fail(e);
        }
        cache.sections.clear();
        cache.world = null;
    }
}
