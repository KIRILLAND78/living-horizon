package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player who logged off, away from the camera, plays its loop where it stands, never walking back. */
class RestingLoopTest {
    private static final double X = 0.5, Y = 64, Z = 0.5;

    private static RestingLoop loop() {
        return new RestingLoop(42, X, Y, Z, 0f, MobAnimations.WALK,
                () -> CompletableFuture.completedFuture(MobPaths.free(X, Y, Z, 42)));
    }

    @Test
    void playsItsLoopWhereItStands() {
        RestingLoop loop = loop();
        double farthest = 0;
        for (int tick = 0; tick < 2000; tick++) {
            RestingLoop.nextTick();
            assertTrue(loop.tick(tick, true) <= MobAnimations.WALK + 1e-9);
            farthest = Math.max(farthest, Math.hypot(loop.x - X, loop.z - Z));
        }
        // Around where it stood, as far as the animation that fits goes, and no farther.
        assertTrue(farthest > 0.5 && farthest < 3.5, "went " + farthest + " blocks from its spot");
    }

    @Test
    void noRoomToWalkTurnsAndSitsOnItsSpot() {
        // A player with no animation that fits, or no ground read: never a statue.
        RestingLoop loop = new RestingLoop(42, X, Y, Z, 0f, MobAnimations.WALK, true,
                () -> CompletableFuture.completedFuture(MobPaths.stand(X, Y, Z)));
        float least = 0, most = 0;
        int seated = 0;
        for (int tick = 0; tick < 3000; tick++) {
            RestingLoop.nextTick();
            loop.tick(tick, false);
            assertEquals(X, loop.x, 1e-9);
            assertEquals(Z, loop.z, 1e-9);
            least = Math.min(least, loop.body);
            most = Math.max(most, loop.body);
            if (loop.pausing) seated++;
        }
        assertTrue(most - least > 60, "turned only " + (most - least) + " degrees");
        assertTrue(seated > 0 && seated < 3000, "seated " + seated + " ticks out of 3000");
        assertEquals("turning on its spot", loop.state());
    }

    @Test
    void readsAFewGroundsATick() {
        AtomicInteger reads = new AtomicInteger();
        RestingLoop[] loops = new RestingLoop[10];
        for (int i = 0; i < loops.length; i++) {
            loops[i] = new RestingLoop(i, X, Y, Z, 0f, MobAnimations.WALK, () -> {
                reads.incrementAndGet();
                return CompletableFuture.completedFuture(MobPaths.free(X, Y, Z, 42));
            });
        }
        RestingLoop.nextTick();
        for (RestingLoop loop : loops) loop.tick(0, true);
        assertEquals(RestingLoop.READS_PER_TICK, reads.get(), "grounds read in the first tick");
        // The others on the next ticks, each read once.
        for (int tick = 1; tick < 20; tick++) {
            RestingLoop.nextTick();
            for (RestingLoop loop : loops) loop.tick(tick, true);
        }
        assertEquals(loops.length, reads.get());
    }
}
