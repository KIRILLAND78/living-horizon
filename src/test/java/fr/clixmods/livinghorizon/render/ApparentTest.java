package fr.clixmods.livinghorizon.render;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How big and how near a distant figure is drawn, for its model, its picture and its occlusion box alike. */
class ApparentTest {
    /** Pixels a radian is across a 1080 pixel tall screen at a field of view of 70 degrees. */
    private static final double PIXELS_PER_RADIAN = 1080 / (2 * Math.tan(Math.toRadians(35)));

    @Test
    void aSmallFigureCountsAsTallAsAPlayer() {
        assertEquals(Apparent.MIN_EXTENT, Apparent.extent(0.7, 0.4));
        // A sleeper's box: 0.2 high, 0.6 wide, though it lies 1.8 long.
        assertEquals(Apparent.MIN_EXTENT, Apparent.extent(0.2, 0.6));
        assertEquals(3.5, Apparent.extent(3.5, 1.2));
        assertEquals(4.0, Apparent.extent(1.0, 4.0));
        assertEquals(Apparent.extent(0.9, 0.9), Apparent.extent(0.9));
    }

    @Test
    void noBoostWithoutAMinimumOrUpClose() {
        assertEquals(1.0, Apparent.boost(1.8, 500, PIXELS_PER_RADIAN, 0));
        assertEquals(1.0, Apparent.boost(1.8, 20, PIXELS_PER_RADIAN, 8));
        assertEquals(1.0, Apparent.boost(1.8, 0, PIXELS_PER_RADIAN, 8));
    }

    @Test
    void aBoostedFigureIsExactlyTheMinimumOnScreen() {
        double extent = 1.8, distance = 2000, min = 12;
        double boost = Apparent.boost(extent, distance, PIXELS_PER_RADIAN, min);
        assertTrue(boost > 1.0);
        assertEquals(min, extent * boost / distance * PIXELS_PER_RADIAN, 1e-9);
    }

    @Test
    void theBoostHasACeiling() {
        assertEquals(Apparent.MAX_BOOST, Apparent.boost(0.01, 1e9, PIXELS_PER_RADIAN, 32));
    }

    @Test
    void aFigureInsideTheFarPlaneStaysWhereItIs() {
        assertEquals(1.0, Apparent.pull(100, 512));
        assertEquals(1.0, Apparent.pull(512 * Apparent.REACH, 512));
    }

    @Test
    void outsideTheViewAsTheAnglesSay() {
        Random random = new Random(42);
        int compared = 0;
        for (int i = 0; i < 200_000; i++) {
            // Views from a narrow spyglass to wider than a half turn, figures from specks to wider than their distance.
            double view = random.nextDouble() * 2.0;
            double angle = random.nextDouble() * Math.PI;
            double across = random.nextDouble() < 0.1 ? random.nextDouble() * 3 : random.nextDouble() * 0.2;
            double edge = view + Math.asin(Math.min(1, across));
            // Too close to the edge to tell the two apart: left out.
            if (Math.abs(angle - edge) < 1e-9) continue;
            assertEquals(angle > edge, Apparent.outsideView(Math.cos(angle), across, Math.cos(view), Math.sin(view)),
                    "angle " + angle + ", view " + view + ", across " + across);
            compared++;
        }
        assertTrue(compared > 199_000);
    }

    @Test
    void nothingIsOutsideAViewOfHalfATurn() {
        assertFalse(Apparent.outsideView(-1, 0.01, Math.cos(Math.PI - 0.005), Math.sin(Math.PI - 0.005)));
        assertTrue(Apparent.outsideView(-1, 0.01, Math.cos(1.2), Math.sin(1.2)));
        assertFalse(Apparent.outsideView(1, 0, Math.cos(1.2), Math.sin(1.2)));
    }

    @Test
    void aFigurePastTheFarPlaneIsDrawnJustInsideIt() {
        double far = 512, distance = 3000;
        double pull = Apparent.pull(distance, far);
        assertEquals(far * Apparent.REACH, distance * pull, 1e-9);
    }
}
