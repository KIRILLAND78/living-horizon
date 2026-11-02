package fr.clixmods.livinghorizon.track;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The threads that read the ground off the game's own: the animation a distant mob plays
 * ({@link MobPaths}), where an offline player can walk and the way to a spot
 * ({@link Walkway}). They read a {@link BlockCopy}, never the level, and their answers are
 * taken on the game's thread.
 *
 * <p>Threads of their own rather than the game's workers, which build the chunks' meshes:
 * a large survey must never hold those up.
 */
final class GroundWork {
    private static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 2);
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon ground " + NAMES.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private GroundWork() {
    }

    /** These threads, to go on with work once something else is done. */
    static Executor executor() {
        return WORKERS;
    }

    /** Runs the work on one of these threads. */
    static <T> CompletableFuture<T> run(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, WORKERS);
    }
}
