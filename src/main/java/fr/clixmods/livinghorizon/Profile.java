package fr.clixmods.livinghorizon;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What each part of the mod costs, section by section, for the debug panel and {@code /livinghorizon}:
 * the last time, a smoothed average and the worst over the last five seconds, which an average
 * alone would hide (a save of 100 ms once a minute). Sections are timed with {@code System.nanoTime()}
 * from any thread. Nothing here touches a client class: the server's publisher is timed too.
 */
public final class Profile {
    /** Where a section is shown with its name in {@code lang/}: {@code livinghorizon.profile.<id>}. */
    public enum Section {
        TICK_MOBS_FOLLOW("tickMobsFollow"),
        TICK_MOBS_CHOOSE("tickMobsChoose"),
        TICK_MOBS_FORESEE("tickMobsForesee"),
        TICK_MOBS_BUILD("tickMobsBuild"),
        TICK_MOBS_GROUND("tickMobsGround"),
        TICK_MOBS_MOTION("tickMobsMotion"),
        TICK_RESTING("tickResting"),
        TICK_AMBIENCE("tickAmbience"),
        TICK_OCCLUSION("tickOcclusion"),
        EXTRACT("extract"),
        DEPTH_BEFORE("depthBefore", true),
        DEPTH_AFTER("depthAfter", true),
        DEPTH_BEFORE_TRANSLUCENT("depthBeforeTranslucent", true),
        DEPTH_AFTER_TRANSLUCENT("depthAfterTranslucent", true),
        DEPTH_BEFORE_FADE("depthBeforeFade", true),
        DEPTH_AFTER_FADE("depthAfterFade", true),
        OCCLUSION_RUN("occlusionRun", true),
        OCCLUSION_COLLECT("occlusionCollect"),
        BAKE("bake", true),
        BAKE_READ_BACK("bakeReadBack"),
        BAKE_STORE("bakeStore"),
        SUBMIT_BIRDS("submitBirds"),
        SUBMIT_IMPOSTORS("submitImpostors"),
        SAVE_MOBS("saveMobs"),
        SAVE_RESTING("saveResting"),
        SAVE_POSITIONS("savePositions"),
        PUBLISH("publish");

        private final String id;
        private final String profilerName;
        private final boolean gpu;
        private final Stat cpuStat = new Stat();
        private final Stat gpuStat = new Stat();

        Section(String id) {
            this(id, false);
        }

        Section(String id, boolean gpu) {
            this.id = id;
            this.profilerName = "livinghorizon:" + id;
            this.gpu = gpu;
        }

        public String id() {
            return id;
        }

        /** Whether the GPU's time is measured too, where the game draws with OpenGL. */
        public boolean gpu() {
            return gpu;
        }

        /** The name that the game's profiler shows (F3 pie chart, spark). */
        public String profilerName() {
            return profilerName;
        }

        public Stat cpu() {
            return cpuStat;
        }

        /** The time the GPU took, read some frames later. */
        public Stat gpuTime() {
            return gpuStat;
        }

        /** The key of its name in {@code lang/}. */
        public String langKey() {
            return "livinghorizon.profile." + id;
        }
    }

    /** What happens at the start and the end of a section on the game's own thread: its profiler, the GPU timer. */
    public interface Hook {
        void begin(Section section);

        void end(Section section);

        /** A frame is over: the GPU's answers are taken in. */
        void frame();
    }

    private static final long WINDOW_SECONDS = 5;
    private static volatile @Nullable Hook hook;

    private Profile() {
    }

    /** The client sets what runs at every section: the game's profiler and the GPU's timer. */
    public static void hook(@Nullable Hook set) {
        hook = set;
    }

    /** Opens a section: give the result to {@link #end}. */
    public static long begin(Section section) {
        Hook current = hook;
        if (current != null) current.begin(section);
        return System.nanoTime();
    }

    public static void end(Section section, long started) {
        end(section, started, -1);
    }

    /** Closes a section. {@code bytes} is what it wrote or sent, -1 for nothing to say. */
    public static void end(Section section, long started, long bytes) {
        long nanos = System.nanoTime() - started;
        Hook current = hook;
        if (current != null) current.end(section);
        section.cpuStat.record(nanos, bytes, System.nanoTime());
    }

    /** A time measured by hand, added up over a tick or a frame: counts once. */
    public static void record(Section section, long nanos) {
        section.cpuStat.record(nanos, -1, System.nanoTime());
    }

    /** What the GPU took for a section, once its query has an answer. */
    public static void recordGpu(Section section, long nanos) {
        section.gpuStat.record(nanos, -1, System.nanoTime());
    }

    /** A frame is over: the calls of each section since the last are what it did in that frame. */
    public static void frame() {
        for (Section section : Section.values()) section.cpuStat.frame();
        Hook current = hook;
        if (current != null) current.frame();
    }

    /** One line of milliseconds with two decimals. */
    public static String millis(double nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    /** The numbers of one section: the last time, the average, the worst of the last five seconds. */
    public static final class Stat {
        private static final double SMOOTHING = 0.95;
        private final long[] worst = new long[(int) WINDOW_SECONDS];
        private final long[] worstSecond = new long[(int) WINDOW_SECONDS];
        private long last, bytes = -1, calls, perFrame, thisFrame, at;
        private double average;
        private boolean seen;

        /** {@code now} is {@code System.nanoTime()}, passed in so that a test can move it. */
        public synchronized void record(long nanos, long bytesNow, long now) {
            last = nanos;
            if (bytesNow >= 0) bytes = bytesNow;
            average = seen ? average * SMOOTHING + nanos * (1 - SMOOTHING) : nanos;
            seen = true;
            calls++;
            thisFrame++;
            at = now;
            long second = Math.floorDiv(now, 1_000_000_000L);
            int slot = (int) Math.floorMod(second, WINDOW_SECONDS);
            if (worstSecond[slot] != second) {
                worstSecond[slot] = second;
                worst[slot] = 0;
            }
            worst[slot] = Math.max(worst[slot], nanos);
        }

        synchronized void frame() {
            perFrame = thisFrame;
            thisFrame = 0;
        }

        /** Whether the section ever ran. */
        public synchronized boolean seen() {
            return seen;
        }

        public synchronized long last() {
            return last;
        }

        public synchronized double average() {
            return average;
        }

        /** The worst time of the last five seconds, 0 when nothing ran in them. */
        public synchronized long worst(long now) {
            long second = Math.floorDiv(now, 1_000_000_000L);
            long max = 0;
            for (int i = 0; i < worst.length; i++) {
                if (second - worstSecond[i] < WINDOW_SECONDS) max = Math.max(max, worst[i]);
            }
            return max;
        }

        public synchronized long calls() {
            return calls;
        }

        /** Whole seconds since the last call, 0 where it never ran. */
        public synchronized long idleSeconds(long now) {
            return seen ? Math.max(0, now - at) / 1_000_000_000L : 0;
        }

        /** How many times it ran in the last whole frame. */
        public synchronized long perFrame() {
            return perFrame;
        }

        /** What the last call wrote or sent, in bytes; -1 where it says nothing. */
        public synchronized long bytes() {
            return bytes;
        }

        /** Forgets everything, for a test. */
        public synchronized void reset() {
            java.util.Arrays.fill(worst, 0);
            java.util.Arrays.fill(worstSecond, 0);
            last = 0;
            bytes = -1;
            calls = perFrame = thisFrame = at = 0;
            average = 0;
            seen = false;
        }
    }
}
