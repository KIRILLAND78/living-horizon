package fr.clixmods.livinghorizon.debug;

import java.lang.management.ManagementFactory;

/** Bytes the game's thread allocates in the mod's part of a tick, smoothed, while the numbers are watched. */
public final class Allocation {
    private static com.sun.management.ThreadMXBean bean;
    private static boolean checked;
    private static double perTick = -1;

    private Allocation() {
    }

    /** What the thread has allocated so far, or -1 where the game's Java cannot say or nobody is looking. */
    public static long begin() {
        if (!ProfileHook.watched()) return -1;
        try {
            if (!checked) {
                checked = true;
                if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean sun
                        && sun.isThreadAllocatedMemorySupported()) {
                    bean = sun;
                }
            }
            return bean == null ? -1 : bean.getCurrentThreadAllocatedBytes();
        } catch (Throwable e) {
            bean = null;
            return -1;
        }
    }

    public static void end(long before) {
        if (before < 0 || bean == null) return;
        try {
            long used = bean.getCurrentThreadAllocatedBytes() - before;
            perTick = perTick < 0 ? used : perTick * 0.95 + used * 0.05;
        } catch (Throwable e) {
            bean = null;
        }
    }

    /** Kilobytes per tick, -1 where unknown. */
    public static double kilobytesPerTick() {
        return perTick < 0 ? -1 : perTick / 1024.0;
    }
}
