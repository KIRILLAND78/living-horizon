package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reads the world past the render distance, from whichever mod keeps it - Voxy, Distant
 * Horizons, Lodistance, Far View or Ecstatic, none required: the ground and biome of a column - for the birds to find
 * coasts, fields and lakes, and for the mobs to find where they may walk - and the first
 * block along a line, for the line of sight and {@code /livinghorizon lod}.
 *
 * <p>Reads run on a few threads of their own, never on the game's. Columns already read
 * are remembered for a while, since herds and flocks keep asking about the same ground;
 * and many columns go in one task. The mod to read is picked at each read: the first of
 * the {@link LodSource}s installed that this mod knows how to talk to.
 */
public final class LodWorld {
    /** A column read is trusted this long: the terrain far away rarely changes. */
    private static final long COLUMN_NANOS = 120_000_000_000L;
    private static final int COLUMNS_KEPT = 60_000;
    private static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);

    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ExecutorService READERS = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon LOD reader " + NAMES.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** The same work on a single thread, when {@link FarConfig#optParallelRead} is off, to compare. */
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon LOD reader");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /**
     * The short questions that decide what is drawn now (is there terrain under these mobs), on a
     * thread of their own: behind the ground reads of thousands of guessed mobs in the readers'
     * queue, a mob waited seconds for its answer, hidden meanwhile. Not the debug command's probe,
     * which reads hundreds of columns along its ray. A column a task of its own, on four threads:
     * one that Distant Horizons is slow to answer holds one thread, not every answer behind it.
     */
    private static final int URGENT_THREADS = 4;
    private static final ExecutorService URGENT = Executors.newFixedThreadPool(URGENT_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon LOD reader (urgent) " + NAMES.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    /** The urgent readers reading a column now, and since when: what a stuck one waits on, for the debug command. */
    private static final ConcurrentHashMap<Thread, Long> URGENT_BUSY = new ConcurrentHashMap<>();
    /** Tasks given to the readers and not finished yet, for the debug panel. */
    private static final AtomicInteger PENDING = new AtomicInteger();

    /**
     * Reads of a source that keeps what it reads between reads (Voxy) hold the read side;
     * letting go of what they hold waits for them on the write side. Distant Horizons' reads
     * hold nothing: one can wait for Distant Horizons' threads while those wait for the game's
     * (to upload its terrain), or have been shut down with the world - and the game, waiting
     * here as the player changed dimension or left, froze for good.
     */
    private static final ReadWriteLock READS = new ReentrantReadWriteLock();
    /** Set while there is no world: reads find nothing and hold nothing. */
    private static volatile boolean suspended;

    private static void run(LodSource source, Runnable task) {
        run(source, task, false);
    }

    private static void run(LodSource source, Runnable task, boolean urgent) {
        PENDING.incrementAndGet();
        (urgent ? URGENT : FarConfig.get().optParallelRead ? READERS : READER).execute(() -> {
            Lock held = source.keepsBetweenReads() ? READS.readLock() : null;
            if (held != null) held.lock();
            try {
                task.run();
            } finally {
                if (held != null) held.unlock();
                PENDING.decrementAndGet();
            }
        });
    }

    /** The source's world for this read; null while suspended. */
    private static @Nullable Object open(LodSource source) throws Throwable {
        return suspended ? null : source.open();
    }

    /** For the debug panel: reads waiting or running, columns remembered, reader threads. */
    public static int pending() {
        return PENDING.get();
    }

    public static int columnsKnown() {
        return memory.size();
    }

    public static int threads() {
        return FarConfig.get().optParallelRead ? THREADS : 1;
    }

    /** Columns already read: the surface, or {@link #NOTHING}, and when. */
    private record Known(@Nullable Surface surface, long at) {
    }

    private static final Surface NOTHING = new Surface(Integer.MIN_VALUE, "", false, null);
    /**
     * Replaced, never emptied, by {@link #release()}: a read still running for the world left,
     * which nothing waits for, fills the old one.
     */
    private static volatile ConcurrentHashMap<Long, Known> memory = new ConcurrentHashMap<>();

    private static final LodSource[] SOURCES = {new VoxySource(), new DhSource(), new LodistanceSource(),
            new FarViewSource(), new EcstaticSource()};

    private LodWorld() {
    }

    /** Every client tick with a level, on the game's thread: each source installed does its part. */
    public static void tick() {
        for (LodSource source : SOURCES) {
            if (source.loaded()) source.tick();
        }
    }

    /** Another world: every section held is let go, every column read forgotten. */
    public static void clear() {
        OcclusionQueries.clear();
        DrawnGround.clear();
        release();
        suspended = false;
    }

    /**
     * No world any more, or the game is closing: everything held is let go at once, so that
     * the mods can close their worlds, and reads still waiting on a mod are called off, since
     * it may never answer them. Reads find nothing until {@link #clear()}.
     */
    public static void suspend() {
        suspended = true;
        DrawnGround.clear();
        release();
        for (LodSource source : SOURCES) source.cancel();
    }

    private static void release() {
        READS.writeLock().lock();
        try {
            for (LodSource source : SOURCES) source.clear();
            memory = new ConcurrentHashMap<>();
        } finally {
            READS.writeLock().unlock();
        }
    }

    /** The mod this read goes to: the first installed one that is not turned off; null if none. */
    private static @Nullable LodSource source() {
        for (LodSource source : SOURCES) {
            if (source.loaded() && source.available()) return source;
        }
        return null;
    }

    /**
     * Blocks across to which the far world's terrain is drawn and still clear of its fog, on
     * the game's thread; 0 without Voxy, Distant Horizons or Lodistance, or when it cannot say.
     */
    public static double clearDistance() {
        LodSource source = source();
        return source == null ? 0 : source.clearDistance();
    }

    /** Whether a far-terrain mod is installed (Voxy, Distant Horizons, Lodistance or Far View), answering or not. */
    public static boolean installed() {
        for (LodSource source : SOURCES) {
            if (source.loaded()) return true;
        }
        return false;
    }

    /** Whether a far-terrain mod is installed (Voxy, Distant Horizons, Lodistance or Far View) and answering: there is a far world to read. */
    public static boolean present() {
        return source() != null;
    }

    /**
     * Blocks across to which the far world's terrain is drawn at all, fog or not, on the game's
     * thread; 0 without Voxy, Distant Horizons or Lodistance, or when it cannot say.
     */
    public static double drawnDistance() {
        LodSource source = source();
        return source == null ? 0 : source.drawnDistance();
    }

    /** For {@code /livinghorizon lod}. */
    public record Status(boolean loaded, String name, @Nullable String failure) {
    }

    /** What the reads of the mod in use found so far, for {@code /livinghorizon lod}. */
    public static String details() {
        LodSource source = source();
        return source == null ? "" : source.details();
    }

    /** The mod being read, or else why none is: nothing installed, or the one installed turned off. */
    public static Status status() {
        LodSource source = source();
        if (source != null) return new Status(true, source.name(), null);
        for (LodSource candidate : SOURCES) {
            if (candidate.loaded()) return new Status(true, candidate.name(), candidate.failure());
        }
        return new Status(false, "", null);
    }

    /**
     * Walks the far world along a direction and says how far the first opaque block is:
     * NaN when there is none within {@code max}, or when no mod has a world loaded.
     */
    public static CompletableFuture<Double> probe(Vec3 eye, Vec3 direction, double max) {
        CompletableFuture<Double> result = new CompletableFuture<>();
        LodSource source = source();
        if (source == null) {
            String failure = status().failure();
            result.completeExceptionally(new IllegalStateException(failure != null ? failure
                    : "no far-terrain mod is loaded (Voxy, Distant Horizons, Lodistance, Far View or Ecstatic)"));
            return result;
        }
        run(source, () -> {
            try {
                Object world = open(source);
                if (world == null) {
                    result.completeExceptionally(new IllegalStateException(source.name() + " has no world loaded"));
                    return;
                }
                Vec3 d = direction.normalize();
                result.complete(source.firstHit(world, eye.x, eye.y, eye.z, d.x, d.y, d.z, 0.5, max));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** The top of one column of the far world: its height, its biome, whether it is water, its block. */
    public record Surface(int y, String biome, boolean water, @Nullable BlockState block) {
    }

    /**
     * The highest block the far world holds at a column, and its biome, read off the thread;
     * empty where it has nothing, or without Voxy, Distant Horizons or Lodistance.
     */
    public static CompletableFuture<Optional<Surface>> surface(int x, int z) {
        return surfaces(new int[]{x}, new int[]{z}).thenApply(found -> Optional.ofNullable(found[0]));
    }

    /**
     * Many columns at once, in one task: entry {@code i} is the surface of
     * {@code (xs[i], zs[i])}, or null where it has nothing. Columns read recently come
     * from memory without waiting.
     */
    public static CompletableFuture<@Nullable Surface[]> surfaces(int[] xs, int[] zs) {
        return surfaces(xs, zs, true);
    }

    /**
     * {@link #surfaces(int[], int[])}, but a column remembered empty is read again: for whoever
     * waits for the far world to fill in, as it does after a world is opened.
     */
    public static CompletableFuture<@Nullable Surface[]> fresh(int[] xs, int[] zs) {
        return surfaces(xs, zs, false);
    }

    /**
     * A column the mod was asked about and did not answer for, which is not the same as one it
     * holds nothing in: {@link LodSource#column} throws it, and {@link #ground} reads it as unknown.
     */
    static final class Unanswered extends Exception {
        static final Unanswered INSTANCE = new Unanswered();

        private Unanswered() {
            super(null, null, false, false);
        }
    }

    /**
     * Whether the far world holds terrain at each column: true, false where it holds nothing,
     * null where it could not say (no world open yet, a read that failed or was not answered).
     * Only a column read as empty is false: a failed read never makes ground into void. A
     * column remembered with terrain answers at once; one remembered empty is read again.
     */
    /** For {@code /livinghorizon lod}: the columns {@link #ground} read, the time it took, its last batch. */
    private static final java.util.concurrent.atomic.AtomicLong GROUND_COLUMNS = new java.util.concurrent.atomic.AtomicLong(),
            GROUND_NANOS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile int groundLastBatch;
    private static volatile long groundLastNanos;

    /** Whether a column's answer is known: true, false, or null for no answer. */
    @FunctionalInterface
    public interface ColumnAnswer {
        void accept(int index, @Nullable Boolean terrain);
    }

    /** For {@code /livinghorizon lod}: how fast the ground under mobs is read. */
    public static String groundTimes() {
        long columns = GROUND_COLUMNS.get(), nanos = GROUND_NANOS.get();
        String text = columns + " columns read in " + nanos / 1_000_000 + " ms ("
                + (columns == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.2f", nanos / 1e6 / columns)) + " ms each), last batch "
                + groundLastBatch + " in " + groundLastNanos / 1_000_000 + " ms, " + pending() + " reads waiting";
        long now = System.nanoTime();
        for (var busy : URGENT_BUSY.entrySet()) {
            long took = now - busy.getValue();
            if (took < 1_000_000_000L) continue;
            // A column read for a second or more: where it waits.
            StringBuilder at = new StringBuilder();
            int shown = 0;
            for (StackTraceElement frame : busy.getKey().getStackTrace()) {
                if (shown == 6) break;
                String name = frame.getClassName();
                at.append(shown++ == 0 ? "" : " < ").append(name.substring(name.lastIndexOf('.') + 1))
                        .append('.').append(frame.getMethodName());
            }
            text += "; a column read for " + took / 1_000_000 + " ms, at " + at;
        }
        return text;
    }

    public static CompletableFuture<@Nullable Boolean[]> ground(int[] xs, int[] zs, ColumnAnswer each) {
        Boolean[] found = new Boolean[xs.length];
        LodSource source = source();
        if (source == null || xs.length == 0) return CompletableFuture.completedFuture(found);
        CompletableFuture<@Nullable Boolean[]> result = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(xs.length);
        long batch = System.nanoTime();
        groundLastBatch = xs.length;
        for (int n = 0; n < xs.length; n++) {
            int i = n;
            run(source, () -> {
                try {
                    found[i] = column(source, xs[i], zs[i]);
                } catch (Throwable e) {
                    source.fail(e);
                }
                // Answered as it comes: one slow column no longer keeps the others waiting.
                each.accept(i, found[i]);
                if (left.decrementAndGet() == 0) {
                    groundLastNanos = System.nanoTime() - batch;
                    result.complete(found);
                }
            }, true);
        }
        return result;
    }

    /** One column for {@link #ground}: true with terrain, false empty, null unanswered or no world. */
    private static @Nullable Boolean column(LodSource source, int x, int z) throws Throwable {
        Object world = open(source);
        if (world == null) return null;
        long key = key(x, z);
        long at = System.nanoTime();
        boolean cache = FarConfig.get().optColumnCache;
        ConcurrentHashMap<Long, Known> columns = memory;
        Known known = cache ? columns.get(key) : null;
        if (known != null && at - known.at < COLUMN_NANOS && known.surface != NOTHING) return true;
        if (columns.size() > COLUMNS_KEPT) columns.clear();
        Thread self = Thread.currentThread();
        URGENT_BUSY.put(self, at);
        try {
            // Asked of the mod's own records first: a column read makes it load an area of terrain.
            Boolean held = source.holds(world, x, z);
            if (held != null) return held;
            Surface surface = source.column(world, x, z);
            if (cache) columns.put(key, new Known(surface == null ? NOTHING : surface, at));
            return surface != null;
        } catch (Unanswered unanswered) {
            return null;
        } finally {
            URGENT_BUSY.remove(self);
            GROUND_COLUMNS.incrementAndGet();
            GROUND_NANOS.addAndGet(System.nanoTime() - at);
        }
    }

    private static CompletableFuture<@Nullable Surface[]> surfaces(int[] xs, int[] zs, boolean trustNothing) {
        Surface[] found = new Surface[xs.length];
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(found);
        long now = System.nanoTime();
        boolean cache = FarConfig.get().optColumnCache;
        boolean missing = false;
        ConcurrentHashMap<Long, Known> remembered = memory;
        for (int i = 0; i < xs.length; i++) {
            Known known = cache ? remembered.get(key(xs[i], zs[i])) : null;
            if (known != null && now - known.at < COLUMN_NANOS && (trustNothing || known.surface != NOTHING)) {
                found[i] = known.surface == NOTHING ? null : known.surface;
            } else {
                missing = true;
            }
        }
        if (!missing) return CompletableFuture.completedFuture(found);
        CompletableFuture<@Nullable Surface[]> result = new CompletableFuture<>();
        run(source, () -> {
            // Taken before the world is: a release from here on leaves this read the old memory.
            ConcurrentHashMap<Long, Known> columns = memory;
            try {
                Object world = open(source);
                if (world != null) {
                    long at = System.nanoTime();
                    if (columns.size() > COLUMNS_KEPT) columns.clear();
                    for (int i = 0; i < xs.length; i++) {
                        long key = key(xs[i], zs[i]);
                        Known known = cache ? columns.get(key) : null;
                        if (known != null && at - known.at < COLUMN_NANOS && (trustNothing || known.surface != NOTHING)) {
                            found[i] = known.surface == NOTHING ? null : known.surface;
                            continue;
                        }
                        Surface surface;
                        try {
                            surface = source.column(world, xs[i], zs[i]);
                        } catch (Unanswered unanswered) {
                            // Not remembered: asked again next time.
                            continue;
                        }
                        if (cache) columns.put(key, new Known(surface == null ? NOTHING : surface, at));
                        if (surface != null) DrawnGround.noted(xs[i], zs[i]);
                        found[i] = surface;
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
            }
            result.complete(found);
        });
        return result;
    }

    /**
     * A slice of many columns at once, in one task: entry {@code i} holds the blocks of
     * {@code (xs[i], zs[i])} from {@code bottom} up, {@code height} of them, air where the
     * far world has nothing in that part of the column; null where it has nothing in the
     * column at all. Voxy gives every block; Distant Horizons only the top one.
     */
    public static CompletableFuture<BlockState @Nullable [][]> columns(int[] xs, int[] zs, int bottom, int height) {
        BlockState[][] found = new BlockState[xs.length][];
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(found);
        CompletableFuture<BlockState @Nullable [][]> result = new CompletableFuture<>();
        run(source, () -> {
            try {
                Object world = open(source);
                if (world != null) {
                    for (int i = 0; i < xs.length; i++) {
                        found[i] = source.slice(world, xs[i], zs[i], bottom, height);
                        if (found[i] != null) DrawnGround.noted(xs[i], zs[i]);
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
            }
            result.complete(found);
        });
        return result;
    }

    /**
     * For each point, whether the far terrain stands between the eye and it: a block on the
     * line, short of the point by {@code margin}. Null without Voxy, Distant Horizons or their world.
     */
    public static CompletableFuture<boolean @Nullable []> blocked(Vec3 eye, double[] xs, double[] ys, double[] zs,
                                                                  double margin) {
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<boolean @Nullable []> result = new CompletableFuture<>();
        run(source, () -> {
            boolean[] blocked = null;
            try {
                Object world = open(source);
                if (world != null) {
                    blocked = new boolean[xs.length];
                    for (int i = 0; i < xs.length; i++) {
                        double dx = xs[i] - eye.x, dy = ys[i] - eye.y, dz = zs[i] - eye.z;
                        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (length <= margin + 2) continue;
                        blocked[i] = !Double.isNaN(source.firstHit(world, eye.x, eye.y, eye.z,
                                dx / length, dy / length, dz / length, 2, length - margin));
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
                blocked = null;
            }
            result.complete(blocked);
        });
        return result;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

}
