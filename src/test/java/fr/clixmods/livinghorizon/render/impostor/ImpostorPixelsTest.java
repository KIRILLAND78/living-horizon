package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpostorPixelsTest {
    private static final int RED = 0xFF0000FF, CLEAR = 0;

    private static int alpha(int pixel) {
        return pixel >>> 24;
    }

    private static int red(int pixel) {
        return pixel & 0xFF;
    }

    @Test
    void halvingWeighsColoursByHowOpaqueTheyAre() {
        // One red pixel and three see-through black ones: a quarter opaque, and red.
        int[] half = ImpostorPixels.halve(new int[]{RED, CLEAR, CLEAR, CLEAR}, 2);
        assertEquals(1, half.length);
        assertEquals(255, red(half[0]));
        assertEquals(64, alpha(half[0]));
    }

    @Test
    void seeThroughPixelsTakeTheColourOfTheFigureNextToThem() {
        int[] pixels = {RED, CLEAR, CLEAR, CLEAR};
        ImpostorPixels.bleed(pixels, 2);
        for (int pixel : pixels) assertEquals(255, red(pixel));
        assertEquals(0, alpha(pixels[1]));
    }

    @Test
    void everyLevelIsHalfTheOneBefore() {
        int[] picture = new int[128 * 128];
        int[][] levels = ImpostorPixels.mips(picture, 128, 64, 4);
        assertEquals(4, levels.length);
        for (int level = 0; level < 4; level++) assertEquals((64 >> level) * (64 >> level), levels[level].length);
    }

    @Test
    void theCutFallsAtHalfCoverage() {
        // The shader keeps what is at least CUT: exactly half covered is kept, a little less is not.
        assertEquals(ImpostorPixels.CUT, alpha(ImpostorPixels.cut(new int[]{128 << 24}, 1)[0]));
        assertTrue(alpha(ImpostorPixels.cut(new int[]{120 << 24}, 1)[0]) < ImpostorPixels.CUT);
        assertEquals(255, alpha(ImpostorPixels.cut(new int[]{0xFF000000}, 1)[0]));
        assertEquals(0, alpha(ImpostorPixels.cut(new int[]{0}, 1)[0]));
    }

    @Test
    void aSolidSquareStaysSolidAllTheWayDown() {
        int[] picture = new int[128 * 128];
        // A 64-pixel opaque square in the middle.
        for (int y = 32; y < 96; y++) for (int x = 32; x < 96; x++) picture[y * 128 + x] = RED;
        int[][] levels = ImpostorPixels.mips(picture, 128, 64, 4);
        for (int[] level : levels) {
            int side = (int) Math.sqrt(level.length);
            assertEquals(255, alpha(level[side / 2 * side + side / 2]));
            assertEquals(0, alpha(level[0]));
        }
    }

    @Test
    void aFigureWithinWhatItsTileShowsNeedsNoLargerPicture() {
        int[] picture = new int[16 * 16];
        picture[4 * 16 + 8] = RED;
        picture[12 * 16 + 3] = RED;
        assertEquals(1.0, ImpostorPixels.growth(picture, 16, 2, 4, 1.3));
        assertEquals(1.0, ImpostorPixels.growth(new int[16 * 16], 16, 2, 4, 1.3));
    }

    @Test
    void aFigureInTheHiddenStripGrowsJustEnough() {
        // Reaching column 1: 7 pixels left of the centre, where 5 are shown.
        int[] picture = new int[16 * 16];
        picture[4 * 16 + 1] = RED;
        assertEquals(7 / 5.0, ImpostorPixels.growth(picture, 16, 2, 4, 1.3), 1e-9);
        // Reaching row 14: 11 pixels above the feet, where 9 are shown.
        picture = new int[16 * 16];
        picture[14 * 16 + 8] = RED;
        assertEquals(11 / 9.0, ImpostorPixels.growth(picture, 16, 2, 4, 1.3), 1e-9);
    }

    @Test
    void aFigureCutByTheVeryEdgeGrowsAtLeastByTheGivenStep() {
        int[] picture = new int[16 * 16];
        picture[4 * 16 + 15] = RED;
        assertEquals(1.6, ImpostorPixels.growth(picture, 16, 2, 4, 1.6), 1e-9);
    }

    @Test
    void aSheetHasItsViewsSideBySideTheRightWayUp() {
        // Two views of 4 pixels, read back bottom row first: a red bottom row in the first,
        // a red top row in the second.
        int[] first = new int[4 * 4], second = new int[4 * 4];
        for (int x = 0; x < 4; x++) {
            first[x] = RED;
            second[3 * 4 + x] = RED;
        }
        int[][] levels = ImpostorPixels.sheet(new int[][]{first, second}, 4, 4, 2);
        assertEquals(2, levels.length);
        assertEquals(8 * 4, levels[0].length);
        assertEquals(4 * 2, levels[1].length);
        // Top row first: the first view's red row is now at the bottom, the second's at the top.
        assertEquals(255, alpha(levels[0][3 * 8]));
        assertEquals(0, alpha(levels[0][0]));
        assertEquals(255, alpha(levels[0][4]));
        assertEquals(0, alpha(levels[0][3 * 8 + 4]));
    }

    @Test
    void onlyLevelsOfASheetsSizeFit() {
        int[][] levels = new int[4][];
        for (int level = 0; level < 4; level++) {
            int tile = ImpostorViews.TILE >> level;
            levels[level] = new int[tile * ImpostorViews.VIEWS * tile];
        }
        assertTrue(ImpostorPixels.fits(levels, 4));
        assertFalse(ImpostorPixels.fits(levels, 3));
        levels[2] = new int[1];
        assertFalse(ImpostorPixels.fits(levels, 4));
        levels[2] = null;
        assertFalse(ImpostorPixels.fits(levels, 4));
    }

    @Test
    void picturesDrawnTheSameHashTheSame() {
        int[] picture = new int[64];
        Arrays.fill(picture, RED);
        assertEquals(ImpostorPixels.hash(picture), ImpostorPixels.hash(picture.clone()));
        int[] other = picture.clone();
        other[10] = CLEAR;
        assertNotEquals(ImpostorPixels.hash(picture), ImpostorPixels.hash(other));
    }

    @Test
    void viewsReadBackOneAfterTheOther() {
        ByteBuffer data = ByteBuffer.allocate(2 * 2 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8; i++) data.putInt(i * 4, i == 5 ? RED : i);
        // Read in the order the game packs pixels, whatever order the buffer came in.
        data.order(ByteOrder.BIG_ENDIAN);
        int[][] views = ImpostorPixels.views(data, 2, 2);
        assertArrayEquals(new int[]{0, 1, 2, 3}, views[0]);
        assertArrayEquals(new int[]{4, RED, 6, 7}, views[1]);
    }
}
