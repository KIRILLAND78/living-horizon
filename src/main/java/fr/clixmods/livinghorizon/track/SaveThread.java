package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The thread the client's files are read and written on, off the game's thread: the remembered
 * mobs ({@link MobMemory}) and the offline players ({@link RestingPlayers}). One thread, so that
 * a file is read only once what was written to it before is there.
 */
final class SaveThread {
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon files");
        thread.setDaemon(true);
        return thread;
    });

    private SaveThread() {
    }

    /** Runs the work on this thread, after what was given to it before. */
    static CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(work, THREAD);
    }

    /** Works something out on this thread, after what was given to it before. */
    static <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, THREAD);
    }

    /**
     * Waits for work given to this thread, at most {@code seconds}, as a world closes or the game
     * quits; says in the log when it did not end in time, or failed.
     */
    static void await(@Nullable CompletableFuture<?> work, long seconds, String what) {
        if (work == null) return;
        try {
            work.get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LivingHorizonClient.LOGGER.warn("Living Horizon: {} not done in time", what, e);
        }
    }
}
