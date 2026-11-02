package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player who logged off, away from the camera, walks back to its spot at its own pace before its loop. */
class RestingLoopTest {
    private static final double X = 0.5, Y = 64, Z = 0.5;

    private static RestingLoop loop() {
        return new RestingLoop(42, X, Y, Z, 0f, MobAnimations.WALK,
                () -> CompletableFuture.completedFuture(MobPaths.free(X, Y, Z, 42)));
    }

    @Test
    void walksBackAtAStrollWithStops() {
        RestingLoop loop = loop();
        loop.from(X + 30, Y, Z, 0f);
        int ticks = 0, stopped = 0;
        double last = loop.x;
        while (loop.state().startsWith("on its way back") && ticks < 2000) {
            double moved = loop.tick(ticks++, false);
            assertTrue(moved <= MobAnimations.WALK + 1e-9, "walked " + moved + " blocks in a tick");
            if (moved == 0) stopped++;
            assertTrue(loop.x <= last + 1e-9, "walked away from its spot");
            last = loop.x;
        }
        assertTrue(ticks < 2000, "never got back");
        // 30 blocks at a stroll take some 270 ticks, and it stopped on the way.
        assertTrue(ticks > 270, "back in " + ticks + " ticks");
        assertTrue(stopped >= 80, "stopped " + stopped + " ticks on the way");
        assertEquals(X, loop.x, 0.06);
        assertEquals(Z, loop.z, 0.06);
    }

    @Test
    void startsItsLoopFromItsSpot() {
        RestingLoop loop = loop();
        loop.from(X, Y, Z, 0f);
        double farthest = 0;
        for (int tick = 0; tick < 2000; tick++) {
            assertTrue(loop.tick(tick, true) <= MobAnimations.WALK + 1e-9);
            farthest = Math.max(farthest, Math.hypot(loop.x - X, loop.z - Z));
        }
        // Around its spot, as far as the animation that fits goes, and no farther.
        assertTrue(farthest > 0.5 && farthest < 3.5, "went " + farthest + " blocks from its spot");
    }

    @Test
    void noRoomToWalkTurnsAndSitsOnItsSpot() {
        // A player with no animation that fits, or no ground read: never a statue.
        RestingLoop loop = new RestingLoop(42, X, Y, Z, 0f, MobAnimations.WALK, true,
                () -> CompletableFuture.completedFuture(MobPaths.stand(X, Y, Z)));
        loop.from(X, Y, Z, 0f);
        float least = 0, most = 0;
        int seated = 0;
        for (int tick = 0; tick < 3000; tick++) {
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
}
