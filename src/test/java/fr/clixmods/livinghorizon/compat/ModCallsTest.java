package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A read that Distant Horizons will never answer is called off once its world is left, and only then. */
class ModCallsTest {
    @Test
    void aReadNeverAnsweredIsCalledOffOnceItsWorldIsLeft() throws Exception {
        ModCalls<Object> calls = new ModCalls<>(new Object());
        Object opened = calls.world();
        CountDownLatch waiting = new CountDownLatch(1);
        CompletableFuture<String> outcome = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            if (!calls.enter(opened)) {
                outcome.complete("not entered");
                return;
            }
            try {
                waiting.countDown();
                // A read queued as Distant Horizons shut its threads down: nothing completes it.
                new CompletableFuture<Void>().get();
                outcome.complete("answered");
            } catch (InterruptedException e) {
                outcome.complete("called off");
            } catch (Exception e) {
                outcome.complete(e.toString());
            } finally {
                calls.leave();
            }
        }, "reader");
        reader.setDaemon(true);
        reader.start();
        assertTrue(waiting.await(5, TimeUnit.SECONDS));

        calls.moveOn(new Object());
        calls.callOff();

        assertEquals("called off", outcome.get(5, TimeUnit.SECONDS));
        reader.join(5_000);
        assertFalse(reader.isAlive());
    }

    @Test
    void aReadForTheWorldStillOpenGoesOn() {
        ModCalls<Object> calls = new ModCalls<>(new Object());
        assertTrue(calls.enter(calls.world()));
        calls.callOff();
        assertFalse(Thread.currentThread().isInterrupted());
        calls.leave();
    }

    @Test
    void noReadBeginsForAWorldLeft() {
        ModCalls<Object> calls = new ModCalls<>(new Object());
        Object opened = calls.world();
        calls.moveOn(new Object());
        assertFalse(calls.enter(opened));
        assertTrue(calls.enter(calls.world()));
        calls.leave();
    }

    @Test
    void anInterruptGoesNoFurtherThanTheRead() {
        ModCalls<Object> calls = new ModCalls<>(new Object());
        assertTrue(calls.enter(calls.world()));
        calls.moveOn(new Object());
        // Called off just as the read was answered: the flag is set on this very thread.
        calls.callOff();
        assertTrue(Thread.currentThread().isInterrupted());
        calls.leave();
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
