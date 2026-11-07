package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.ServerConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * The animals the world puts in the chunks the server has not generated yet, foreseen from the
 * seed ({@link SeedDraws}): land nobody has explored is not empty from afar.
 *
 * <p>Two ways in. Every few seconds the server foresees them around each player with the mod, up
 * to {@code seedMobRange}, and shares them with the remembered mobs ({@link #update},
 * {@link #addAround}). And in a world played here or opened to LAN, the host's game asks for any
 * chunks it likes (where it looks through a spyglass, where the far terrain reaches, a scan):
 * {@link #foresee}.
 *
 * <p>A chunk is left out as soon as it has its animals: loaded, or saved past the step that puts
 * them there (a chunk the game only began, around one it generated, still gets them). Its own
 * mobs are there, the server remembers them ({@link PositionPublisher}) and they take the place of
 * those foreseen. Only a dimension of the game's usual terrain has any ({@link SeedDraws.Source#of}).
 *
 * <p>The work is done on threads of their own; the server's thread only reads what the last pass
 * found. Nothing here reaches a class of the client's.
 */
public final class SeedMobs {
    /** Chunks worked out in a pass at most, the nearest a player first: the others in the next passes. */
    private static final int CHUNKS_PER_PASS = 2048;
    /** Passes a region file is kept unread before it is forgotten. */
    private static final int REGION_PASSES = 12;
    /** Saved chunks asked for their status in a pass, or at once in a request, at most. */
    private static final int SCANS_AT_ONCE = 512;
    /**
     * What a chunk saved before it got its first animals says it is at: those around a generated
     * one, begun by the game and left there. Any other status, a mod's or a newer one, is taken
     * as generated.
     */
    private static final Set<String> BEFORE_ANIMALS = Set.of("empty", "structure_starts", "structure_references",
            "biomes", "noise", "surface", "carvers", "liquid_carvers", "features", "initialize_light", "light",
            "heightmaps");
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    private static final ExecutorService WORKER = thread("Living Horizon seed mobs");
    /** One request at a time, in the order asked. */
    private static final ExecutorService REQUESTS = thread("Living Horizon foresight");
    /** The urgent ones (where the spyglass looks), never behind a scan: on threads of their own. */
    private static final ExecutorService URGENT = thread("Living Horizon foresight (urgent)");
    /** The draws of a request, on half the cores: the game and its server keep the others. */
    private static final ForkJoinPool DRAWS = pool("Living Horizon foresight draws",
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2), Thread.MIN_PRIORITY);
    /** Those of an urgent one, on half the cores, ahead of the others. */
    private static final ForkJoinPool URGENT_DRAWS = pool("Living Horizon foresight draws (urgent)",
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2), Thread.NORM_PRIORITY);
    private static volatile boolean failed;

    /** What the last pass found, per dimension: the chunks not generated yet that have animals. */
    private volatile Map<String, Long2ObjectMap<List<PositionsMessage.Mob>>> found = Map.of();
    /** Chunks the server's thread saw loaded, per dimension: generated, whatever their region file says yet. */
    private final Map<String, LongOpenHashSet> loaded = new HashMap<>();
    private @Nullable Future<?> pass;

    // The worker's own.
    private final Map<String, Dimension> dimensions = new HashMap<>();

    private static ForkJoinPool pool(String name, int threads, int priority) {
        return new ForkJoinPool(threads, pool -> {
            var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        }, null, false);
    }

    private static ExecutorService thread(String name) {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /** One pass's work in a dimension: where its players are, and how to make its terrain. */
    private record Area(String dimension, Source source, List<double[]> centers, int range, long[] loaded) {
    }

    /** A dimension's generator, and where its region files are, taken on the server's thread. */
    private record Source(ServerLevel level, SeedDraws.Source draws, Path regions) {
        static @Nullable Source of(MinecraftServer server, ServerLevel level) {
            SeedDraws.Source draws = SeedDraws.Source.of(level);
            if (draws == null) return null;
            Path regions = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                    .resolve("region");
            return new Source(level, draws, regions);
        }
    }

    /** The worker's memory of a dimension. */
    private static final class Dimension {
        /** Chunks worked out, an empty list for none, while a player is near. */
        final Long2ObjectOpenHashMap<List<PositionsMessage.Mob>> chunks = new Long2ObjectOpenHashMap<>();
        final Generated generated = new Generated();
    }

    // --- Which chunks have their animals already -----------------------------------------

    /** What a chunk is known to be. */
    private enum State { FREE, GENERATED, UNKNOWN }

    /**
     * Which chunks have their animals already: those seen loaded, and those their region file has,
     * saved past the step that puts them there. A saved chunk's status is asked of the game's
     * storage thread, that field only; until the answer comes it is taken as generated.
     */
    private static final class Generated {
        private static final byte BEGUN = 1;
        final LongOpenHashSet known = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();
        /** Saved chunks found begun only: they get their animals later, foreseen meanwhile. */
        final Long2ByteOpenHashMap status = new Long2ByteOpenHashMap();
        /** Saved chunks asked for their status, not answered yet. */
        final LongOpenHashSet asking = new LongOpenHashSet();
        /** The answers, from the game's storage thread: whether the chunk got its animals. */
        final Map<Long, Boolean> answers = new ConcurrentHashMap<>();
        int round;

        /** Takes the answers that came; a new round reads each region file again if it changed. */
        void nextRound() {
            round++;
            for (Map.Entry<Long, Boolean> answer : answers.entrySet()) {
                long chunk = answer.getKey();
                answers.remove(chunk);
                asking.remove(chunk);
                if (answer.getValue()) known.add(chunk);
                else status.put(chunk, BEGUN);
            }
        }

        State state(Path folder, long chunk) {
            if (known.contains(chunk)) return State.GENERATED;
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            long key = SeedDraws.chunk(cx >> 5, cz >> 5);
            Region region = regions.get(key);
            if (region == null) {
                region = new Region();
                regions.put(key, region);
            }
            // Each file is looked at once a round at most, and read again only once it changed.
            if (region.round != round) {
                region.round = round;
                if (region.refresh(folder.resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca"))) {
                    // Saved again: a chunk begun then may be finished now.
                    status.keySet().removeIf((long begun) -> SeedDraws.chunkX(begun) >> 5 == cx >> 5
                            && SeedDraws.chunkZ(begun) >> 5 == cz >> 5);
                }
            }
            if (!region.chunks.get((cx & 31) + (cz & 31) * 32)) return State.FREE;
            if (status.get(chunk) == BEGUN) return State.FREE;
            return State.UNKNOWN;
        }

        /** Asks the game's storage thread the status a chunk was saved at, that field only. */
        CompletableFuture<?> ask(ServerLevel level, long chunk) {
            asking.add(chunk);
            CollectFields fields = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
            try {
                return level.getChunkSource().chunkScanner()
                        .scanChunk(new ChunkPos(SeedDraws.chunkX(chunk), SeedDraws.chunkZ(chunk)), fields)
                        .whenComplete((done, error) -> answers.put(chunk, error != null || animals(fields.getResult())));
            } catch (RuntimeException e) {
                answers.put(chunk, true);
                return CompletableFuture.completedFuture(null);
            }
        }

        /** What is no longer wanted is forgotten, and read again if wanted again. */
        void keepOnly(Long2DoubleOpenHashMap wanted) {
            known.removeIf((long chunk) -> !wanted.containsKey(chunk));
            status.keySet().removeIf((long chunk) -> !wanted.containsKey(chunk));
            regions.values().removeIf(region -> round - region.round > REGION_PASSES);
        }
    }

    /** Whether a chunk saved with this status got its animals; one that cannot be told is taken as so. */
    private static boolean animals(@Nullable Tag saved) {
        if (!(saved instanceof CompoundTag tag) || !(tag.get("Status") instanceof StringTag status)) return true;
        //? if >=1.21.5 {
        String name = status.value();
        //?} else
        /*String name = status.getAsString();*/
        return !BEFORE_ANIMALS.contains(name.startsWith("minecraft:") ? name.substring(10) : name);
    }

    /** Which chunks a region file holds, from its header, read again when the file changes. */
    private static final class Region {
        final BitSet chunks = new BitSet(1024);
        long modified = Long.MIN_VALUE;
        int round = -1;

        /** True when the file changed since it was last read. */
        boolean refresh(Path file) {
            long now;
            try {
                now = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE;
            } catch (IOException e) {
                return false;
            }
            if (now == modified) return false;
            modified = now;
            chunks.clear();
            if (now == Long.MIN_VALUE) return true;
            // The first 4 KiB: where each of the 1024 chunks is in the file, 0 for one never saved.
            ByteBuffer header = ByteBuffer.allocate(4096);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                while (header.hasRemaining() && channel.read(header) >= 0) {
                    if (channel.position() >= channel.size()) break;
                }
            } catch (IOException e) {
                // Read again at the next round.
                modified = Long.MIN_VALUE;
                return false;
            }
            for (int i = 0; i < 1024; i++) {
                if (header.getInt(i * 4) != 0) chunks.set(i);
            }
            return true;
        }
    }

    // --- Around the players, shared --------------------------------------------------------

    /**
     * Starts a pass around the players given, unless one is still running. Called on the
     * server's thread every few seconds; the mobs found are there for {@link #addAround} once
     * the pass is done.
     */
    public void update(MinecraftServer server, List<ServerPlayer> viewers, int range) {
        if (pass != null && !pass.isDone()) return;
        pass = null;
        range = Math.min(Math.max(range, 0), ServerConfig.MAX_SEED_MOB_RANGE);
        Map<ServerLevel, List<double[]>> centers = new HashMap<>();
        for (ServerPlayer player : viewers) {
            centers.computeIfAbsent((ServerLevel) player.level(), level -> new ArrayList<>())
                    .add(new double[]{player.getX(), player.getZ()});
        }
        List<Area> areas = new ArrayList<>();
        Map<String, LongOpenHashSet> kept = new HashMap<>();
        for (Map.Entry<ServerLevel, List<double[]>> entry : centers.entrySet()) {
            if (range == 0) break;
            ServerLevel level = entry.getKey();
            Source source;
            try {
                source = Source.of(server, level);
            } catch (RuntimeException e) {
                warn("could not read the generator of " + level.dimension(), e);
                continue;
            }
            if (source == null) continue;
            String dimension = SharedPositions.dimensionId(level.dimension());
            // Only those near a player are worth keeping: the region file says the same of the others.
            LongOpenHashSet seen = loaded.getOrDefault(dimension, new LongOpenHashSet());
            LongOpenHashSet near = new LongOpenHashSet();
            for (LongIterator it = seen.iterator(); it.hasNext(); ) {
                long chunk = it.nextLong();
                if (nearest(entry.getValue(), SeedDraws.chunkX(chunk), SeedDraws.chunkZ(chunk)) <= range + 256) near.add(chunk);
            }
            kept.put(dimension, near);
            areas.add(new Area(dimension, source, entry.getValue(), range, near.toLongArray()));
        }
        loaded.clear();
        loaded.putAll(kept);
        if (areas.isEmpty()) {
            found = Map.of();
            return;
        }
        pass = WORKER.submit(() -> {
            try {
                run(areas);
            } catch (RuntimeException e) {
                warn("could not work out the mobs of the chunks around", e);
            }
        });
    }

    /**
     * Adds the mobs foreseen within {@code range} blocks of a point of {@code level}, but those of
     * a chunk loaded since: generated, with mobs of its own.
     */
    public void addAround(ServerLevel level, double x, double z, int range, List<PositionsMessage.Mob> into) {
        String dimension = SharedPositions.dimensionId(level.dimension());
        Long2ObjectMap<List<PositionsMessage.Mob>> chunks = found.get(dimension);
        if (chunks == null || range <= 0) return;
        LongOpenHashSet seen = loaded.computeIfAbsent(dimension, d -> new LongOpenHashSet());
        double reach = (double) range * range;
        for (Long2ObjectMap.Entry<List<PositionsMessage.Mob>> entry : chunks.long2ObjectEntrySet()) {
            long chunk = entry.getLongKey();
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            double dx = (cx << 4) + 8 - x, dz = (cz << 4) + 8 - z;
            if (Math.sqrt(dx * dx + dz * dz) > range + 12) continue;
            if (seen.contains(chunk)) continue;
            if (level.getChunkSource().hasChunk(cx, cz) || level.areEntitiesLoaded(chunk)) {
                seen.add(chunk);
                continue;
            }
            for (PositionsMessage.Mob mob : entry.getValue()) {
                double mx = mob.x() - x, mz = mob.z() - z;
                if (mx * mx + mz * mz <= reach) into.add(mob);
            }
        }
    }

    private void run(List<Area> areas) {
        Map<String, Long2ObjectMap<List<PositionsMessage.Mob>>> next = new HashMap<>();
        for (Area area : areas) {
            Dimension dimension = dimensions.computeIfAbsent(area.dimension(), d -> new Dimension());
            Generated generated = dimension.generated;
            generated.nextRound();
            for (long chunk : area.loaded()) generated.known.add(chunk);
            Long2DoubleOpenHashMap wanted = wanted(area);
            Long2ObjectOpenHashMap<List<PositionsMessage.Mob>> shown = new Long2ObjectOpenHashMap<>();
            List<long[]> todo = new ArrayList<>();
            int scans = 0;
            for (Long2DoubleOpenHashMap.Entry entry : wanted.long2DoubleEntrySet()) {
                long chunk = entry.getLongKey();
                State state = generated.state(area.source().regions(), chunk);
                if (state != State.FREE) {
                    // Asked once, in the background: taken as generated until it answers.
                    if (state == State.UNKNOWN && !generated.asking.contains(chunk) && scans++ < SCANS_AT_ONCE) {
                        generated.ask(area.source().level(), chunk);
                    }
                    dimension.chunks.remove(chunk);
                    continue;
                }
                List<PositionsMessage.Mob> mobs = dimension.chunks.get(chunk);
                if (mobs == null) todo.add(new long[]{chunk, Double.doubleToLongBits(entry.getDoubleValue())});
                else if (!mobs.isEmpty()) shown.put(chunk, mobs);
            }
            // The nearest first: those far off come in the next passes.
            todo.sort((a, b) -> Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));
            BiomeManager biomes = todo.isEmpty() ? null : area.source().draws().biomes();
            for (int i = 0; i < Math.min(todo.size(), CHUNKS_PER_PASS); i++) {
                long chunk = todo.get(i)[0];
                List<PositionsMessage.Mob> mobs = draw(area.source().draws(), biomes, chunk);
                dimension.chunks.put(chunk, mobs);
                if (!mobs.isEmpty()) shown.put(chunk, mobs);
            }
            // What no player is near any more is forgotten, and worked out again if one comes back.
            dimension.chunks.keySet().removeIf((long chunk) -> !wanted.containsKey(chunk));
            generated.keepOnly(wanted);
            next.put(area.dimension(), shown);
        }
        dimensions.keySet().retainAll(next.keySet());
        found = next;
    }

    /** The chunks within range of a player, with how far the nearest player is. */
    private static Long2DoubleOpenHashMap wanted(Area area) {
        Long2DoubleOpenHashMap wanted = new Long2DoubleOpenHashMap();
        int reach = (area.range() >> 4) + 1;
        for (double[] center : area.centers()) {
            int cx = Mth.floor(center[0]) >> 4, cz = Mth.floor(center[1]) >> 4;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double across = distance(center, cx + dx, cz + dz);
                    if (across > area.range() + 12) continue;
                    long chunk = SeedDraws.chunk(cx + dx, cz + dz);
                    if (!wanted.containsKey(chunk) || wanted.get(chunk) > across) wanted.put(chunk, across);
                }
            }
        }
        return wanted;
    }

    // --- Asked for by the host's game ------------------------------------------------------

    /**
     * The animals foreseen in the chunks given (the nearest first is best), those generated left
     * out: for the game of a player alone in their world or hosting it on LAN, which asks where it
     * looks, where the far terrain reaches, or for a scan. Called on any thread; the work is done on
     * threads of their own, one request at a time, an {@code urgent} one ahead of the others, and
     * {@code progress} hears how many chunks are done now and then. The map holds the chunks with
     * animals only.
     */
    public static CompletableFuture<Long2ObjectMap<List<PositionsMessage.Mob>>> foresee(MinecraftServer server,
            ResourceKey<Level> dimension, long[] chunks, @Nullable IntConsumer progress, boolean urgent) {
        return free(server, dimension, chunks, urgent, (source, free) -> source == null
                ? new Long2ObjectOpenHashMap<>() : draw(source.draws(), free, progress, urgent));
    }

    /**
     * Those of the chunks given that are still not generated, no animal drawn: for foreseen mobs
     * kept from an earlier game, whose land may have been explored since. In the order given.
     */
    public static CompletableFuture<long[]> unexplored(MinecraftServer server, ResourceKey<Level> dimension, long[] chunks) {
        return free(server, dimension, chunks, false, (source, free) -> free);
    }

    /** What to do with the chunks still free, on the thread of the request; no source where nothing is foreseen. */
    private interface Then<T> {
        T apply(@Nullable Source source, long[] free);
    }

    private static <T> CompletableFuture<T> free(MinecraftServer server, ResourceKey<Level> dimension, long[] chunks,
                                                 boolean urgent, Then<T> then) {
        // What is loaded is the server's thread's to tell: only near the players and the chunks kept loaded.
        return server.submit(() -> {
            ServerLevel level = server.getLevel(dimension);
            Source source = level == null ? null : Source.of(server, level);
            if (source == null) return CompletableFuture.completedFuture(then.apply(null, new long[0]));
            LongOpenHashSet loadedNow = new LongOpenHashSet();
            int reach = server.getPlayerList().getViewDistance() + 3;
            List<int[]> players = new ArrayList<>();
            for (ServerPlayer player : level.players()) players.add(new int[]{player.getBlockX() >> 4, player.getBlockZ() >> 4});
            for (long chunk : chunks) {
                int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
                boolean near = false;
                for (int[] at : players) near |= Math.max(Math.abs(at[0] - cx), Math.abs(at[1] - cz)) <= reach;
                if (near && (level.getChunkSource().hasChunk(cx, cz) || level.areEntitiesLoaded(chunk))) loadedNow.add(chunk);
            }
            return CompletableFuture.supplyAsync(() -> then.apply(source, free(source, chunks, loadedNow)),
                    urgent ? URGENT : REQUESTS);
        }).thenCompose(future -> future);
    }

    /** The chunks given that are neither loaded now nor saved past the step that puts their animals. */
    private static long[] free(Source source, long[] chunks, LongOpenHashSet loadedNow) {
        Generated generated = new Generated();
        generated.known.addAll(loadedNow);
        generated.nextRound();
        LongArrayList free = new LongArrayList();
        LongArrayList unknown = new LongArrayList();
        for (long chunk : chunks) {
            State state = generated.state(source.regions(), chunk);
            if (state == State.FREE) free.add(chunk);
            else if (state == State.UNKNOWN) unknown.add(chunk);
        }
        // Saved chunks: their status read first, a batch at a time, to leave those begun only.
        for (int from = 0; from < unknown.size(); from += SCANS_AT_ONCE) {
            List<CompletableFuture<?>> batch = new ArrayList<>();
            for (int i = from; i < Math.min(unknown.size(), from + SCANS_AT_ONCE); i++) {
                batch.add(generated.ask(source.level(), unknown.getLong(i)));
            }
            try {
                CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Those not answered stay out: taken as generated.
            }
            generated.nextRound();
            for (int i = from; i < Math.min(unknown.size(), from + SCANS_AT_ONCE); i++) {
                long chunk = unknown.getLong(i);
                if (generated.status.get(chunk) == Generated.BEGUN) free.add(chunk);
            }
        }
        return free.toLongArray();
    }

    /**
     * The first animals of many chunks, on half the cores, each thread with its own biomes;
     * the map holds the chunks with animals only. Also for a client's own generator, built from the
     * seed a server shares.
     */
    public static Long2ObjectMap<List<PositionsMessage.Mob>> draw(SeedDraws.Source source, long[] chunks,
                                                                   @Nullable IntConsumer progress) {
        return draw(source, chunks, progress, false);
    }

    /** The same, an {@code urgent} request on threads of its own, ahead of any scan. */
    public static Long2ObjectMap<List<PositionsMessage.Mob>> draw(SeedDraws.Source source, long[] chunks,
                                                                   @Nullable IntConsumer progress, boolean urgent) {
        ForkJoinPool pool = urgent ? URGENT_DRAWS : DRAWS;
        Map<Long, List<PositionsMessage.Mob>> found = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger();
        ThreadLocal<BiomeManager> biomes = ThreadLocal.withInitial(source::biomes);
        int slice = 64;
        List<java.util.concurrent.ForkJoinTask<?>> tasks = new ArrayList<>();
        for (int from = 0; from < chunks.length; from += slice) {
            int start = from, end = Math.min(chunks.length, from + slice);
            tasks.add(pool.submit(() -> {
                for (int i = start; i < end; i++) {
                    List<PositionsMessage.Mob> mobs = draw(source, biomes.get(), chunks[i]);
                    if (!mobs.isEmpty()) found.put(chunks[i], mobs);
                }
                int now = done.addAndGet(end - start);
                if (progress != null && (now / 4096 != (now - (end - start)) / 4096 || now == chunks.length)) {
                    progress.accept(now);
                }
            }));
        }
        for (var task : tasks) task.join();
        Long2ObjectOpenHashMap<List<PositionsMessage.Mob>> result = new Long2ObjectOpenHashMap<>(found.size());
        found.forEach(result::put);
        return result;
    }

    /** One chunk's, or none where the generator fails: said once in the log. */
    private static List<PositionsMessage.Mob> draw(SeedDraws.Source source, BiomeManager biomes, long chunk) {
        try {
            return SeedDraws.spawns(source, biomes, SeedDraws.chunkX(chunk), SeedDraws.chunkZ(chunk));
        } catch (RuntimeException e) {
            warn("could not work out the first animals of a chunk", e);
            return List.of();
        }
    }

    private static double nearest(List<double[]> centers, int cx, int cz) {
        double best = Double.MAX_VALUE;
        for (double[] center : centers) best = Math.min(best, distance(center, cx, cz));
        return best;
    }

    /** From a point to the middle of a chunk, across the ground. */
    private static double distance(double[] center, int cx, int cz) {
        double dx = (cx << 4) + 8 - center[0], dz = (cz << 4) + 8 - center[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void warn(String what, Throwable error) {
        if (failed) return;
        failed = true;
        LOGGER.warn("Living Horizon: {}, the mobs of unexplored land are left out where it fails", what, error);
    }
}
