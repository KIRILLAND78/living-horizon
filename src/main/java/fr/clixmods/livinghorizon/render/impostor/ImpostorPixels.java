package fr.clixmods.livinghorizon.render.impostor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.jspecify.annotations.Nullable;

/**
 * Turns one rendered picture of a figure into the mip levels of its tile, on the CPU.
 *
 * <p>The graphics card could build mip levels for a whole page, but a figure's tile has to
 * be filtered on its own, without its neighbours bleeding in, and its see-through pixels
 * need a colour: a smaller level averages them in, and so does the texture filter at the
 * edge of a figure. Left black, every figure would get a dark outline that thickens with
 * the distance.
 *
 * <p>The pictures are drawn cut out, as models are: the game's shader keeps a pixel whose
 * alpha, once filtered, is at least 0.1 ({@link #CUT}), and draws it opaque. Drawn smoothly
 * instead, a figure a few pixels wide would show the sky through it, a ghost of itself.
 * So each level's alpha is stored as a steep ramp that crosses 0.1 where half of what a
 * pixel covers is the figure: the filter then cuts the outline where it really is, and a
 * figure is neither fatter nor thinner than its model.
 *
 * <p>Pixels are packed as the game packs them in memory: {@code 0xAABBGGRR}.
 */
public final class ImpostorPixels {
    /** The alpha below which the game's cut-out shader drops a pixel, out of 255. */
    static final int CUT = 26;
    /** How steep the stored alpha ramp is round half coverage. */
    private static final int STEEP = 4;

    /** Passes of {@link #bleed} on each level: enough for the filter's reach at the next one. */
    private static final int BLEED_PASSES = 3;

    private ImpostorPixels() {
    }

    /**
     * The mip levels of one square picture.
     *
     * @param picture the picture, {@code size * size} pixels, row by row
     * @param size    its side, a power of two times {@code tile}
     * @param tile    the side of level 0
     * @param levels  how many levels, each half the side of the one before
     */
    public static int[][] mips(int[] picture, int size, int tile, int levels) {
        int[][] out = new int[levels][];
        int[] level = picture;
        int side = size;
        while (side > tile) {
            level = halve(level, side);
            side /= 2;
        }
        out[0] = cut(level, side);
        for (int i = 1; i < levels; i++) {
            level = halve(level, side);
            side /= 2;
            out[i] = cut(level, side);
        }
        return out;
    }

    /**
     * The mip levels of a whole sheet, its views side by side in each, top row first.
     *
     * @param pictures the views as read back, bottom row first, {@code render * render} pixels each
     * @param render   the side of a picture, a power of two times {@code tile}
     * @param tile     the side of a tile at level 0
     * @param levels   how many levels
     */
    public static int[][] sheet(int[][] pictures, int render, int tile, int levels) {
        int views = pictures.length;
        int[][][] mips = new int[views][][];
        for (int view = 0; view < views; view++) mips[view] = mips(pictures[view], render, tile, levels);
        int[][] out = new int[levels][];
        for (int level = 0; level < levels; level++) {
            int side = tile >> level;
            int width = side * views;
            int[] strip = new int[width * side];
            for (int view = 0; view < views; view++) {
                int[] pixels = mips[view][level];
                // Read back bottom row first: turned so that the top of the figure is at the top.
                for (int y = 0; y < side; y++) {
                    System.arraycopy(pixels, y * side, strip, (side - 1 - y) * width + view * side, side);
                }
            }
            out[level] = strip;
        }
        return out;
    }

    /**
     * Whether levels read from disk have the size of a sheet's: {@code count} levels of
     * {@link ImpostorViews#VIEWS} tiles of {@link ImpostorViews#TILE} pixels at level 0.
     * Written by another format otherwise.
     */
    public static boolean fits(int[][] levels, int count) {
        if (levels.length != count) return false;
        for (int level = 0; level < count; level++) {
            // Past the colours, the normal maps' levels, of the same sizes.
            int tile = ImpostorViews.TILE >> level % ImpostorAtlas.MIPS;
            if (levels[level] == null || levels[level].length != tile * ImpostorViews.VIEWS * tile) return false;
        }
        return true;
    }

    /** A hash of a picture's pixels: two looks drawn the same have the same. */
    public static long hash(int[] level) {
        long hash = 1125899906842597L;
        for (int pixel : level) hash = hash * 31 + pixel;
        return hash;
    }

    /**
     * The views read back from the graphics card, one after the other in {@code data}, each
     * {@code render * render} pixels packed as {@code 0xAABBGGRR}.
     */
    public static int[][] views(ByteBuffer data, int views, int render) {
        int[][] read = new int[views][];
        IntBuffer pixels = data.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        for (int view = 0; view < views; view++) {
            read[view] = new int[render * render];
            pixels.get(view * read[view].length, read[view]);
        }
        return read;
    }

    /**
     * The depth of each view as read back, bottom row first, turned into how far each pixel
     * stands out towards the camera, in pixels of the picture; {@code NaN} where nothing was drawn.
     * The views are drawn with an orthographic projection 2000 pixels deep.
     *
     * @param reversed whether the depth runs from 1 near to 0 far (from 26.2)
     */
    public static float[][] depths(ByteBuffer data, int views, int render, boolean reversed) {
        float[][] read = new float[views][];
        java.nio.FloatBuffer floats = data.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        for (int view = 0; view < views; view++) {
            float[] depth = new float[render * render];
            floats.get(view * depth.length, depth);
            read[view] = heights(depth, reversed);
        }
        return read;
    }

    /** One view's depth as drawn turned, in place, into how far each pixel stands out: see {@link #depths}. */
    public static float[] heights(float[] depth, boolean reversed) {
        for (int i = 0; i < depth.length; i++) {
            float d = depth[i];
            boolean empty = reversed ? d <= 0f : d >= 1f;
            depth[i] = empty ? Float.NaN : (reversed ? d : -d) * DEPTH_RANGE;
        }
        return depth;
    }

    /** A view with no depth known: nothing stands out, its normal map is flat. */
    public static float[] noDepth(int render) {
        float[] depth = new float[render * render];
        java.util.Arrays.fill(depth, Float.NaN);
        return depth;
    }

    /** Pixels of the picture between the near and the far plane of the views. */
    static final float DEPTH_RANGE = 2000f;

    /**
     * Past this step between two pixels, in pixels of the picture, they are taken for two parts
     * of the figure, one in front of the other, not one surface: no slope is read across it.
     */
    private static final float STEP = 3f;

    /**
     * How far the quad an impostor is drawn on leans back, from facing the camera, when seen
     * level: {@link ImpostorLean} turns it half way to facing up. The normal maps are made for
     * that plane.
     */
    static final double LEAN = Math.toRadians(45);

    /**
     * The normal map of one view, in the packing of the pictures: red the normal's part along
     * the picture's right, green its part along the picture's down (the texture's v, which a
     * shader pack takes for its bitangent), both from the quad leant back by {@link #LEAN},
     * blue full (no ambient occlusion), alpha the picture's. Worked out from the slope of the
     * depth between neighbouring pixels: where a pixel has no neighbour on the same surface on
     * one side, the other side's slope is taken.
     *
     * @param picture the view's colours, bottom row first
     * @param height  how far each pixel stands out, from {@link #depths}
     */
    public static int[] normals(int[] picture, float[] height, int side) {
        int[] out = new int[picture.length];
        double cos = Math.cos(LEAN), sin = Math.sin(LEAN);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int i = y * side + x;
                int alpha = picture[i] >>> 24;
                float h = height[i];
                if (alpha == 0 || Float.isNaN(h)) {
                    out[i] = alpha << 24 | FLAT;
                    continue;
                }
                double dx = slope(height, h, x > 0 ? i - 1 : -1, x < side - 1 ? i + 1 : -1);
                double dy = slope(height, h, y > 0 ? i - side : -1, y < side - 1 ? i + side : -1);
                // Right, up and towards the camera, as the figure was drawn.
                double nx = -dx, ny = -dy, nz = 1;
                double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= length;
                ny /= length;
                nz /= length;
                // On the leant quad: its up turned back, its normal turned up.
                double up = ny * cos - nz * sin, outward = nz * cos + ny * sin;
                double across = nx;
                if (outward < 0) {
                    // Facing away from the quad: as far round as its normal map can say.
                    double edge = Math.sqrt(across * across + up * up);
                    if (edge > 1e-6) {
                        across /= edge;
                        up /= edge;
                    }
                }
                out[i] = alpha << 24 | 0xFF << 16 | channel(-up) << 8 | channel(across);
            }
        }
        return out;
    }

    /** A normal map with every pixel straight out of the quad, for a view read back without its depth. */
    public static int[] flat(int[] picture) {
        int[] out = new int[picture.length];
        for (int i = 0; i < picture.length; i++) out[i] = picture[i] & 0xFF000000 | FLAT;
        return out;
    }

    /** A normal straight out of the quad, without alpha. */
    private static final int FLAT = 0xFF << 16 | 128 << 8 | 128;

    /** The slope of the depth through a pixel, from its neighbours {@code before} and {@code after} (-1 for none). */
    private static double slope(float[] height, float h, int before, int after) {
        float back = before < 0 ? Float.NaN : h - height[before];
        float on = after < 0 ? Float.NaN : height[after] - h;
        boolean b = !Float.isNaN(back) && Math.abs(back) < STEP, a = !Float.isNaN(on) && Math.abs(on) < STEP;
        if (b && a) return Math.abs(back) < Math.abs(on) ? back : on;
        if (b) return back;
        if (a) return on;
        return 0;
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, -1.0, 1.0) * 127.5 + 127.5) & 0xFF;
    }

    /**
     * How many times larger a picture must be for its figure to stay out of the strip of
     * {@code margin} pixels round it, which its tile does not show: 1 when it already does.
     * The picture grows round the feet, {@code feet} pixels up from its bottom row (row 0).
     * A figure that reaches the very edge goes on past it, by how much is not known: then
     * at least {@code beyond}.
     */
    public static double growth(int[] picture, int size, int margin, double feet, double beyond) {
        int[] box = bounds(picture, size);
        return box == null ? 1 : growth(box, 0, size, margin, feet, beyond);
    }

    /**
     * {@link #growth} of a figure whose {@code box} ({@link #bounds}) is {@code shift} rows
     * higher in its picture, its feet too: whether it reaches the very edge is the box's own.
     */
    private static double growth(int[] box, int shift, int size, int margin, double feet, double beyond) {
        int left = box[0], right = box[1], bottom = box[2] + shift, top = box[3] + shift;
        feet += shift;
        // One pixel to spare: drawn larger, the figure's edge may round outwards.
        double centre = size / 2.0, across = centre - margin - 1;
        double growth = Math.max(1, Math.max(centre - left, right + 1 - centre) / across);
        growth = Math.max(growth, (top + 1 - feet) / (size - margin - 1 - feet));
        if (bottom < feet) growth = Math.max(growth, (feet - bottom) / Math.max(1, feet - margin - 1));
        if (box[0] == 0 || box[2] == 0 || box[1] == size - 1 || box[3] == size - 1) growth = Math.max(growth, beyond);
        return growth;
    }

    /** Left, right, bottom and top of the figure in a picture, in pixels; null when it shows nowhere. */
    private static int @Nullable [] bounds(int[] picture, int size) {
        int left = size, right = -1, bottom = size, top = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (picture[y * size + x] >>> 24 < 128) continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
                bottom = Math.min(bottom, y);
                top = Math.max(top, y);
            }
        }
        return right < 0 ? null : new int[]{left, right, bottom, top};
    }

    /**
     * Where the point a figure turns round stands in its pictures, and how much larger they
     * must be: {@code feet} pixels up from the bottom row, and {@link #growth} at that height.
     */
    public record Fit(int feet, double growth) {
    }

    /**
     * A figure pictured round a point that is not its middle, a ship round the middle of its
     * hull with its masts reaching far above: the point moved so that the figure, all its views
     * together, stands in the middle of its pictures, between {@code lowest} and {@code highest}
     * pixels up from the bottom row, and how much larger the pictures must then be. The point is
     * at {@code feet} now; it stays there when the figure shows nowhere.
     */
    public static Fit centred(int[][] pictures, int size, int margin, int feet, int lowest, int highest, double beyond) {
        int bottom = size, top = -1;
        int[][] boxes = new int[pictures.length][];
        for (int view = 0; view < pictures.length; view++) {
            boxes[view] = bounds(pictures[view], size);
            if (boxes[view] == null) continue;
            bottom = Math.min(bottom, boxes[view][2]);
            top = Math.max(top, boxes[view][3]);
        }
        if (top < 0) return new Fit(feet, 1);
        int moved = Math.clamp(feet + Math.round((size - 1 - top - bottom) / 2f), lowest, highest);
        double growth = 1;
        for (int[] box : boxes) {
            if (box != null) growth = Math.max(growth, growth(box, moved - feet, size, margin, feet, beyond));
        }
        return new Fit(moved, growth);
    }

    /** A level as it is stored: coloured round the figure, its alpha a ramp through the cut. */
    static int[] cut(int[] level, int side) {
        int[] out = level.clone();
        bleed(out, side);
        for (int i = 0; i < out.length; i++) {
            int alpha = (int) Math.clamp((long) ((out[i] >>> 24) - 128) * STEEP + CUT, 0, 255);
            out[i] = alpha << 24 | (out[i] & 0x00FFFFFF);
        }
        return out;
    }

    /**
     * Half the side: each pixel the average of four, the colours weighted by how opaque they
     * are, so that a see-through pixel does not darken an opaque one next to it.
     */
    static int[] halve(int[] pixels, int side) {
        int half = side / 2;
        int[] out = new int[half * half];
        for (int y = 0; y < half; y++) {
            for (int x = 0; x < half; x++) {
                int a = pixels[2 * y * side + 2 * x], b = pixels[2 * y * side + 2 * x + 1];
                int c = pixels[(2 * y + 1) * side + 2 * x], d = pixels[(2 * y + 1) * side + 2 * x + 1];
                out[y * half + x] = average(a, b, c, d);
            }
        }
        return out;
    }

    private static int average(int... pixels) {
        long alpha = 0, red = 0, green = 0, blue = 0, plainRed = 0, plainGreen = 0, plainBlue = 0;
        for (int pixel : pixels) {
            int a = pixel >>> 24;
            alpha += a;
            red += (long) (pixel & 0xFF) * a;
            green += (long) (pixel >> 8 & 0xFF) * a;
            blue += (long) (pixel >> 16 & 0xFF) * a;
            plainRed += pixel & 0xFF;
            plainGreen += pixel >> 8 & 0xFF;
            plainBlue += pixel >> 16 & 0xFF;
        }
        int n = pixels.length;
        int r, g, b;
        if (alpha > 0) {
            r = (int) ((red + alpha / 2) / alpha);
            g = (int) ((green + alpha / 2) / alpha);
            b = (int) ((blue + alpha / 2) / alpha);
        } else {
            r = (int) (plainRed / n);
            g = (int) (plainGreen / n);
            b = (int) (plainBlue / n);
        }
        int a = (int) ((alpha + n / 2) / n);
        return a << 24 | b << 16 | g << 8 | r;
    }

    /**
     * Gives the see-through pixels around a figure the colour of the figure next to them,
     * a few pixels out, leaving them see-through.
     */
    static void bleed(int[] pixels, int side) {
        boolean[] known = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) known[i] = pixels[i] >>> 24 != 0;
        for (int pass = 0; pass < BLEED_PASSES; pass++) {
            boolean[] next = known.clone();
            boolean changed = false;
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int i = y * side + x;
                    if (known[i]) continue;
                    int count = 0, r = 0, g = 0, b = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= side || ny >= side || !known[ny * side + nx]) continue;
                            int pixel = pixels[ny * side + nx];
                            r += pixel & 0xFF;
                            g += pixel >> 8 & 0xFF;
                            b += pixel >> 16 & 0xFF;
                            count++;
                        }
                    }
                    if (count == 0) continue;
                    pixels[i] = (pixels[i] & 0xFF000000) | (b / count) << 16 | (g / count) << 8 | r / count;
                    next[i] = true;
                    changed = true;
                }
            }
            known = next;
            if (!changed) return;
        }
    }
}
