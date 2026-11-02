package fr.clixmods.livinghorizon.compat;

import java.util.HashMap;
import java.util.Map;

/**
 * The world the reads of another mod are for, and the threads inside a call to it with the
 * world each call was made for: for a mod that may never answer a call once its world closes.
 * Distant Horizons queues a read on threads of its own, waits for it with no time limit, and
 * shuts those threads down as the player leaves: a read queued then never ends. The thread
 * waiting for it is interrupted ({@link #callOff}), which Distant Horizons takes for
 * "cancelled"; only a thread inside a call is, never one running this mod's own code.
 *
 * @param <W> what stands for one world, compared by identity
 */
final class ModCalls<W> {
    private volatile W world;
    /** Guarded by this. */
    private final Map<Thread, W> inside = new HashMap<>();

    ModCalls(W world) {
        this.world = world;
    }

    /** The world reads are for now. */
    W world() {
        return world;
    }

    /** Another world: calls made for the last one may still be running. */
    void moveOn(W next) {
        world = next;
    }

    /**
     * A call for {@code opened} begins on this thread if that is still the world; false, and
     * nothing is to be asked, once it was left. Checked under the lock {@link #callOff} takes,
     * so that no call begins for a world left without being called off.
     */
    synchronized boolean enter(W opened) {
        if (opened != world) return false;
        inside.put(Thread.currentThread(), opened);
        return true;
    }

    /** The call on this thread ended; an interrupt that came just as it ended goes no further. */
    synchronized void leave() {
        inside.remove(Thread.currentThread());
        Thread.interrupted();
    }

    /** Every call still running for a world left is interrupted. */
    synchronized void callOff() {
        W now = world;
        inside.forEach((thread, opened) -> {
            if (opened != now) thread.interrupt();
        });
    }
}
