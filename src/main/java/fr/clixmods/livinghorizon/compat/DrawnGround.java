package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether terrain is drawn under a distant mob, for {@link FarConfig#onlyOverTerrain}: the
 * game's own chunk, or the far world's terrain (Voxy, Distant Horizons or Lodistance). A mob remembered
 * where neither draws anything yet would stand in the void.
 *
 * <p>Chunks the game holds within its render distance are answered at once. The others are
 * asked of the far world a chunk at a time, in one read per frame, off the game's thread. A
 * chunk found empty is asked again, later and later, since the far world fills in as it loads;
 * one found with terrain stays so until the world changes.
 *
 * <p>Never a blink: a mob is hidden only on a "no terrain" answer. While its chunk has no
 * answer yet, it stays as it was the frame before ({@link Decisions}), so a mob walking out of
 * the game's chunks never drops out while the far world is asked. And the far world is asked
 * ahead, about the game's chunks along the edge of its render distance, before mobs or the
 * edge cross them.
 */
public final class DrawnGround {
    /** The first wait before a chunk found empty is asked again, and the longest. */
    private static final long RETRY_NANOS = 1_000_000_000L, RETRY_MAX_NANOS = 10_000_000_000L;
    /**
     * Chunks asked in one read, and waiting to be: a read answers all its chunks at its end, and
     * one of thousands of columns (as many guessed mobs ask) kept every mob waiting, hidden, for
     * seconds. The mobs drawn first, the nearest, ask first.
     */
    private static final int BATCH = 256;
    /** A read is on its way since then: the next waits for it, 30 s at most (a read that never came back). */
    private static volatile boolean reading;
    private static volatile long readingSince;
    private static final long READ_STUCK_NANOS = 30_000_000_000L;
    /** The least time between two calls asking every chunk found empty again. */
    private static final long REVISIT_NANOS = 1_000_000_000L;
    private static final int CHUNKS_KEPT = 20_000;
    /** Chunks from the edge of the game's render distance asked of the far world ahead. */
    private static final int AHEAD = 3;

    /** What is known of a chunk. */
    enum Answer { TERRAIN, VOID, UNKNOWN }

    /** What is known of one chunk; written by the reader threads, read by the game's. */
    private static final class Chunk {
        volatile boolean ground;
        volatile boolean asking;
        volatile long next;
        volatile int misses;

        Answer answer() {
            return ground ? Answer.TERRAIN : misses > 0 ? Answer.VOID : Answer.UNKNOWN;
        }
    }

    private static final ConcurrentHashMap<Long, Chunk> CHUNKS = new ConcurrentHashMap<>();
    /** Asked this frame: the chunk and the column of the mob that asked. */
    private static final List<Chunk> ASKED = new ArrayList<>();
    private static int[] askedX = new int[64], askedZ = new int[64];
    private static final Decisions DECISIONS = new Decisions();

    /**
     * A chunk found empty turned out to hold terrain: the far world is filling in (Distant
     * Horizons answers "nothing" for a while as a world opens), and every chunk found empty is
     * asked again at once rather than after its wait.
     */
    private static volatile boolean revisit;
    private static long revisitedAt = Long.MIN_VALUE;

    private static double farDrawn;
    private static long farDrawnAt = Long.MIN_VALUE;

    private DrawnGround() {
    }

    /** Another world, or none: every chunk is asked again, and every mob starts hidden. */
    static void clear() {
        // Chunks waiting in the read on its way are another world's: let them go.
        ASKED.clear();
        reading = false;
        CHUNKS.clear();
        DECISIONS.clear();
        revisit = false;
    }

    /**
     * Whether the mob {@code key}, standing at {@code (x, z)}, is drawn: terrain is drawn under
     * it seen from {@code eye}, or its chunk has no answer yet and it was drawn the last time.
     * Asks the far world when needed; {@link #ask()} sends the questions.
     */
    public static boolean shown(ClientLevel level, Vec3 eye, Object key, double x, double z) {
        long now = System.nanoTime();
        if (revisit && (revisitedAt == Long.MIN_VALUE || now - revisitedAt >= REVISIT_NANOS)) {
            revisit = false;
            revisitedAt = now;
            for (Chunk chunk : CHUNKS.values()) {
                if (!chunk.ground) chunk.next = now;
            }
        }
        return DECISIONS.decide(key, answer(level, eye, x, z), now);
    }

    private static Answer answer(ClientLevel level, Vec3 eye, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z), cx = bx >> 4, cz = bz >> 4;
        int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        long reach = reach(eye, cx, cz);
        if (reach < (long) distance * distance && level.hasChunk(cx, cz)) {
            // Near the edge, the far world is asked ahead: the answer is there when the mob or the edge moves.
            int inner = Math.max(0, distance - AHEAD);
            if (reach >= (long) inner * inner && LodWorld.present() && withinFar(eye, x, z)) chunk(cx, cz, bx, bz);
            return Answer.TERRAIN;
        }
        if (!LodWorld.installed()) return Answer.VOID;
        // Installed but not read now (paused after a failed read): no answer either way.
        if (!LodWorld.present()) return Answer.UNKNOWN;
        if (!withinFar(eye, x, z)) return Answer.VOID;
        return chunk(cx, cz, bx, bz).answer();
    }

    /**
     * The chunk's square distance from the camera's, in chunks, the way the game picks the
     * sections it draws ({@code ChunkTrackingView.isInViewDistance}): within the render
     * distance when below its square.
     */
    private static long reach(Vec3 eye, int cx, int cz) {
        long dx = Math.max(0, Math.abs(cx - (Mth.floor(eye.x) >> 4)) - 1);
        long dz = Math.max(0, Math.abs(cz - (Mth.floor(eye.z) >> 4)) - 1);
        return dx * dx + dz * dz;
    }

    /** Within the far world's own render distance, when it says. */
    private static boolean withinFar(Vec3 eye, double x, double z) {
        double far = farDrawn();
        double dx = x - eye.x, dz = z - eye.z;
        return far <= 0 || dx * dx + dz * dz <= far * far;
    }

    /** What is known of a chunk, asking the far world about it when it is time. */
    private static Chunk chunk(int cx, int cz, int bx, int bz) {
        if (CHUNKS.size() > CHUNKS_KEPT) CHUNKS.clear();
        Chunk chunk = CHUNKS.computeIfAbsent((long) cx << 32 | (cz & 0xFFFFFFFFL), key -> new Chunk());
        if (!chunk.ground && !chunk.asking && ASKED.size() < BATCH && System.nanoTime() >= chunk.next) {
            chunk.asking = true;
            int i = ASKED.size();
            if (i == askedX.length) {
                askedX = Arrays.copyOf(askedX, i * 2);
                askedZ = Arrays.copyOf(askedZ, i * 2);
            }
            askedX[i] = bx;
            askedZ[i] = bz;
            ASKED.add(chunk);
        }
        return chunk;
    }

    /**
     * For {@code /livinghorizon lod}: the chunks known with terrain, found empty, waiting for an
     * answer and never answered, and how fast the far world answers.
     */
    public static String status() {
        int terrain = 0, empty = 0, asking = 0, none = 0;
        for (Chunk chunk : CHUNKS.values()) {
            if (chunk.ground) terrain++;
            else if (chunk.asking) asking++;
            else if (chunk.misses > 0) empty++;
            else none++;
        }
        return "ground under mobs: " + terrain + " chunks with terrain, " + empty + " empty, " + asking
                + " waiting for an answer, " + none + " not asked yet; " + LodWorld.groundTimes();
    }

    /**
     * A read of the far world found blocks at this column, for whatever it was read (the ground a
     * mob walks on, most often): its chunk has terrain, without a question of its own. Most mobs
     * have their ground read anyway; asked again here, their answer waited behind those reads.
     */
    static void noted(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        Chunk chunk = CHUNKS.computeIfAbsent((long) cx << 32 | (cz & 0xFFFFFFFFL), key -> new Chunk());
        if (chunk.ground) return;
        if (chunk.misses > 0) revisit = true;
        chunk.ground = true;
    }

    /** Sends the far world this frame's questions, in one read. */
    public static void ask() {
        long started = System.nanoTime();
        if (ASKED.isEmpty() || reading && started - readingSince < READ_STUCK_NANOS) return;
        reading = true;
        readingSince = started;
        Chunk[] chunks = ASKED.toArray(new Chunk[0]);
        int[] xs = Arrays.copyOf(askedX, chunks.length), zs = Arrays.copyOf(askedZ, chunks.length);
        ASKED.clear();
        LodWorld.ground(xs, zs, (i, terrain) -> answered(chunks[i], terrain, System.nanoTime()))
                .whenComplete((found, error) -> {
                    // Any chunk still waiting got no answer (no world, a failed read): asked again soon.
                    long now = System.nanoTime();
                    for (Chunk chunk : chunks) {
                        if (chunk.asking) answered(chunk, null, now);
                    }
                    reading = false;
                });
    }

    /** One chunk's answer: terrain, nothing, or none (not void, asked again soon). */
    private static void answered(Chunk chunk, @Nullable Boolean terrain, long now) {
        if (terrain == null) {
            chunk.next = now + RETRY_NANOS;
        } else if (terrain) {
            if (chunk.misses > 0) revisit = true;
            chunk.ground = true;
        } else {
            int misses = Math.min(chunk.misses, 5);
            chunk.next = now + Math.min(RETRY_MAX_NANOS, RETRY_NANOS << misses);
            chunk.misses = misses + 1;
        }
        chunk.asking = false;
    }

    /** {@link LodWorld#drawnDistance}, read again once a second: the settings it reads seldom change. */
    private static double farDrawn() {
        long now = System.nanoTime();
        if (farDrawnAt == Long.MIN_VALUE || now - farDrawnAt > 1_000_000_000L) {
            farDrawn = LodWorld.drawnDistance();
            farDrawnAt = now;
        }
        return farDrawn;
    }

    /**
     * Whether each mob was drawn the last time it was asked about, so that one without an
     * answer stays as it was. Mobs not asked about for a while are forgotten: two maps, the
     * older dropped every {@link #FORGET_NANOS}. On the game's thread only.
     */
    static final class Decisions {
        static final long FORGET_NANOS = 20_000_000_000L;

        private Map<Object, Boolean> recent = new HashMap<>(), older = new HashMap<>();
        private long turnedAt = Long.MIN_VALUE;

        boolean decide(Object key, Answer answer, long now) {
            if (turnedAt == Long.MIN_VALUE) turnedAt = now;
            if (now - turnedAt > FORGET_NANOS) {
                older = recent;
                recent = new HashMap<>();
                turnedAt = now;
            }
            boolean shown = switch (answer) {
                case TERRAIN -> true;
                case VOID -> false;
                case UNKNOWN -> {
                    @Nullable Boolean last = recent.get(key);
                    if (last == null) last = older.get(key);
                    yield last != null && last;
                }
            };
            recent.put(key, shown);
            return shown;
        }

        void clear() {
            recent.clear();
            older.clear();
            turnedAt = Long.MIN_VALUE;
        }
    }
}
