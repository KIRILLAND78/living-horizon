package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player logging off in a plane: their last positions come to a stop before they leave, and
 * the plane is kept flying at the pace they went just before.
 */
class PaceTest {
    @Test
    void keepsThePaceOfTheLastSecondsThoughTheLastPositionsStop() {
        FarPlayer.Pace pace = new FarPlayer.Pace();
        for (int tick = 0; tick < 100; tick++) pace.note(0, 0.8);
        // The positions stop coming: the estimate falls off over a few ticks.
        double v = 0.8;
        for (int tick = 0; tick < 5; tick++) pace.note(0, v *= 0.7);
        assertEquals(0.8, pace.fastest().z, 1e-9);
        assertTrue(pace.fastest().horizontalDistance() >= RestingMount.CRUISE, "fast enough to circle");
    }

    @Test
    void forgetsAPaceOlderThanTwoSeconds() {
        FarPlayer.Pace pace = new FarPlayer.Pace();
        for (int tick = 0; tick < 20; tick++) pace.note(0.5, 0);
        for (int tick = 0; tick <= FarPlayer.Pace.KEPT + 1; tick++) pace.note(0, 0);
        assertEquals(0, pace.fastest().horizontalDistance(), 1e-9);
    }
}
