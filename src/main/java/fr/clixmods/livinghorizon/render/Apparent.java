package fr.clixmods.livinghorizon.render;

/**
 * How big and how near a distant figure is drawn: the arithmetic {@link GhostRenderer} shares
 * between a model, its picture and the box tested against the terrain, so that all three agree.
 * Asks the game nothing.
 */
final class Apparent {
    /**
     * Blocks a figure counts as at least, along its longest side: a player's height. A sleeper's
     * box is 0.2 blocks high and 0.6 wide, though it lies 1.8 blocks long.
     */
    static final double MIN_EXTENT = 1.8;

    /** A figure is drawn at most this many times its size, however far. */
    static final double MAX_BOOST = 400.0;

    /** How far in the far plane a figure past it is drawn, as a part of the plane's distance. */
    static final double REACH = 0.95;

    private Apparent() {
    }

    /** Blocks along a figure's longest side, as {@link #boost} counts it. */
    static double extent(double height, double width) {
        return extent(Math.max(height, width));
    }

    /** A figure's longest side, as {@link #boost} counts it: never less than {@link #MIN_EXTENT}. */
    static double extent(double longest) {
        return Math.max(MIN_EXTENT, longest);
    }

    /**
     * How many times larger a figure {@code extent} blocks long, this far away, is drawn so that it
     * is never smaller on screen than {@code minPixels}: 1 when it is already, or the minimum is off.
     */
    static double boost(double extent, double distance, double pixelsPerRadian, double minPixels) {
        if (minPixels <= 0 || distance <= 1e-3 || extent <= 0 || pixelsPerRadian <= 0) return 1.0;
        double apparent = extent / distance * pixelsPerRadian;
        return Math.clamp(minPixels / apparent, 1.0, MAX_BOOST);
    }

    /**
     * How much nearer a figure this far away is drawn, along its line of sight and smaller in
     * proportion, to stay inside the far plane: 1 within {@link #REACH} of it.
     */
    static double pull(double distance, double far) {
        double reach = far * REACH;
        return distance > reach ? reach / distance : 1.0;
    }
}
