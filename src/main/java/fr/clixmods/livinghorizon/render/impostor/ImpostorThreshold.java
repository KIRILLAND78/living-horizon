package fr.clixmods.livinghorizon.render.impostor;

/**
 * When a figure is drawn as a picture of itself instead of its model: past the impostor
 * distance, or smaller on screen than the impostor size, each of which may be off (0).
 * Asks the game nothing.
 *
 * <p>A figure already pictured keeps its picture until it is a little inside both: 5% closer
 * than the distance and 5% taller than the size. None of it blinks between a model and a picture.
 *
 * <p>A picture is never blown up far past the pixels it holds: a huge mob just past the
 * distance (an Ice and Fire dragon twenty blocks long) keeps its model until it is small enough.
 */
public final class ImpostorThreshold {
    /** How far inside a threshold a pictured figure must come to get its model back. */
    public static final double RETURN = 0.95;

    /** How many times larger than its pixels a picture may be shown. */
    public static final double MAX_ENLARGED = 2;

    private ImpostorThreshold() {
    }

    /**
     * Whether a picture would be shown too large for what it holds, and its model drawn instead.
     * A pictured figure keeps its picture until it is 5% larger than that.
     *
     * @param tilePixels a tile's height on screen, in pixels
     * @param heldPixels the pixels along a side of a tile
     * @param pictured   the figure was a picture last frame
     */
    public static boolean enlarged(double tilePixels, int heldPixels, boolean pictured) {
        return tilePixels > heldPixels * MAX_ENLARGED / (pictured ? RETURN : 1);
    }

    /**
     * @param distance     blocks from the eye to the figure
     * @param pixels       the figure's height (or width, the larger) on screen, in pixels
     * @param pictured     the figure was a picture last frame
     * @param fromDistance blocks past which a figure is a picture; 0 for none
     * @param belowPixels  pixels under which a figure is a picture; 0 for none
     */
    public static boolean pictured(double distance, double pixels, boolean pictured, int fromDistance, int belowPixels) {
        if (distance < 1e-3) return false;
        if (fromDistance > 0 && distance > (pictured ? fromDistance * RETURN : fromDistance)) return true;
        return belowPixels > 0 && pixels < (pictured ? belowPixels / RETURN : belowPixels);
    }
}
