package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * What the client does at every {@link Profile} section: names it in the game's own profiler
 * (the F3 pie chart, spark), and times the GPU round it where the section asks for it. Only on
 * the game's thread: the sections timed on a worker or on the server's thread are left to
 * {@link Profile} alone.
 */
public final class ProfileHook implements Profile.Hook {
    /** The panel and the command ask for the GPU's time this long at a time: its queries cost a little. */
    private static final long WATCH_NANOS = 10_000_000_000L;
    private static long watchedUntil = System.nanoTime() - WATCH_NANOS;

    private ProfileHook() {
    }

    public static void register() {
        Profile.hook(new ProfileHook());
    }

    /** Someone is reading the numbers: the GPU's time and the allocations are measured for a while. */
    public static void watch() {
        watchedUntil = System.nanoTime() + WATCH_NANOS;
    }

    public static boolean watched() {
        return System.nanoTime() - watchedUntil < 0;
    }

    private static ProfilerFiller profiler() {
        //? if >=1.21.2 {
        return net.minecraft.util.profiling.Profiler.get();
        //?} else {
        /*return Minecraft.getInstance().getProfiler();
        *///?}
    }

    private static boolean onGameThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    @Override
    public void begin(Profile.Section section) {
        if (!onGameThread()) return;
        profiler().push(section.profilerName());
        if (section.gpu()) GpuTimer.begin(section);
    }

    @Override
    public void end(Profile.Section section) {
        if (!onGameThread()) return;
        if (section.gpu()) GpuTimer.end(section);
        profiler().pop();
    }

    @Override
    public void frame() {
        GpuTimer.collect();
    }
}
