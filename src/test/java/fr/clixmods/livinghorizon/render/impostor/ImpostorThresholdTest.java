package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpostorThresholdTest {
    @Test
    void distanceAloneWhenNoSize() {
        assertFalse(ImpostorThreshold.pictured(100, 2, false, 128, 0));
        assertTrue(ImpostorThreshold.pictured(130, 200, false, 128, 0));
    }

    @Test
    void smallOnScreenIsPicturedBeforeTheDistance() {
        assertTrue(ImpostorThreshold.pictured(60, 15, false, 128, 20));
        assertFalse(ImpostorThreshold.pictured(60, 25, false, 128, 20));
        // Past the distance, a big figure is a picture all the same.
        assertTrue(ImpostorThreshold.pictured(200, 500, false, 128, 20));
    }

    @Test
    void aPictureComesBackOnlyALittleInsideBoth() {
        // Just over the size: still a picture once it is one, a model otherwise.
        assertTrue(ImpostorThreshold.pictured(60, 20.5, true, 128, 20));
        assertFalse(ImpostorThreshold.pictured(60, 20.5, false, 128, 20));
        assertFalse(ImpostorThreshold.pictured(60, 22, true, 128, 20));
        // Just inside the distance, same.
        assertTrue(ImpostorThreshold.pictured(125, 500, true, 128, 20));
        assertFalse(ImpostorThreshold.pictured(120, 500, true, 128, 20));
    }

    @Test
    void sizeAloneWhenNoDistance() {
        assertFalse(ImpostorThreshold.pictured(5000, 200, false, 0, 20));
        assertFalse(ImpostorThreshold.pictured(5000, 200, true, 0, 20));
        assertTrue(ImpostorThreshold.pictured(60, 15, false, 0, 20));
        // Both off: never a picture.
        assertFalse(ImpostorThreshold.pictured(5000, 1, false, 0, 0));
    }

    @Test
    void aPictureIsNeverBlownUpPastTwiceItsPixels() {
        // A 64-pixel tile: a cow's at 130 blocks is a dozen pixels, a grown dragon's hundreds.
        assertFalse(ImpostorThreshold.enlarged(13, 64, false));
        assertFalse(ImpostorThreshold.enlarged(128, 64, false));
        assertTrue(ImpostorThreshold.enlarged(300, 64, false));
        // Its picture kept until a little past the limit, its model until a little inside it.
        assertFalse(ImpostorThreshold.enlarged(132, 64, true));
        assertTrue(ImpostorThreshold.enlarged(132, 64, false));
    }

    @Test
    void neverAtTheEye() {
        assertFalse(ImpostorThreshold.pictured(0, 0, true, 128, 20));
    }
}
