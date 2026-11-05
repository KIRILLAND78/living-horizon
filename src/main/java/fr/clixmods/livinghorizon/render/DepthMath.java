package fr.clixmods.livinghorizon.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * The arithmetic between a distance in front of the camera and the depth a projection gives it,
 * in every layout the game, Distant Horizons and Iris use: OpenGL's -1..1 or 0..1, nearer
 * points smaller or greater (reversed). Nothing here asks the game anything, so all of it is
 * unit-tested ({@code DepthMathTest}); a fix to the depth code adds a test there.
 *
 * <p>A projection's depth comes from two of its terms, {@code a} and {@code b}: for a view-space
 * {@code z} (negative in front of the camera), {@code ndc = (a * z + b) / -z}, and back,
 * {@code distance = -z = b / (ndc + a)}. In a 0..1 layout the stored depth is the {@code ndc};
 * in OpenGL's -1..1 it is {@code ndc * 0.5 + 0.5}.
 *
 * <p>The shaders do the same in GLSL, in {@code shaders/include/far_depth.glsl}: {@link #distance}
 * is its {@code lhDistance}, {@link #depth} its {@code lhGameDepth}, {@link #dhViewZ} its
 * {@code lhDhViewZ}. A change to one is made to the other, and tested here.
 */
public final class DepthMath {
    private DepthMath() {
    }

    /**
     * {@code {a, b}} of a projection with these near and far planes, in this layout: the near and
     * far planes swapped when the depth is reversed, OpenGL's -1..1 unless {@code zeroToOne}.
     */
    public static float[] terms(float near, float far, boolean reversed, boolean zeroToOne) {
        float n = reversed ? far : near, f = reversed ? near : far;
        return zeroToOne
                ? new float[]{f / (n - f), n * f / (n - f)}
                : new float[]{(f + n) / (n - f), 2.0f * f * n / (n - f)};
    }

    /**
     * Whether a matrix whose entries are named {@code m<row><column>} is stored row by row: the
     * entry that divides by the depth is -1, and the offset {@code b} is the other one of
     * {@code m23} and {@code m32}. Distant Horizons' matrices come either way.
     */
    public static boolean rowByRow(float m23) {
        return !(Math.abs(m23 + 1.0f) < 1.0e-3f);
    }

    /** {@code {a, b}} of a projection from three of its entries, stored either way: see {@link #rowByRow}. */
    public static float[] fromMatrix(float m22, float m23, float m32) {
        return new float[]{m22, rowByRow(m23) ? m23 : m32};
    }

    /**
     * {@code {a, b}} of a JOML perspective projection, view bobbing, the hurt tilt and nausea
     * multiplied in or not: for any projection {@code P * M} with {@code M} affine, its depth row
     * is {@code -a} times its {@code w} row plus {@code b} in the last column, and the {@code z}
     * the two terms work with is the one its {@code w} row gives. Null when it is not a
     * perspective (no {@code w} row) or the terms come out wrong.
     */
    public static float @Nullable [] fromProjection(Matrix4fc projection) {
        // JOML names its entries m<column><row>.
        float wx = projection.m03(), wy = projection.m13(), wz = projection.m23(), ww = projection.m33();
        float length = wx * wx + wy * wy + wz * wz;
        if (!(length > 1.0e-6f)) return null;
        float a = -(projection.m02() * wx + projection.m12() * wy + projection.m22() * wz) / length;
        float b = projection.m32() + a * ww;
        if (!Float.isFinite(a) || !Float.isFinite(b) || Math.abs(b) < 1.0e-9f) return null;
        return new float[]{a, b};
    }

    /**
     * {@code {a, b}} of the depth Lodistance writes for its terrain with Sodium, from the
     * {@code m32} of the projection it was given: its vertex shader replaces the depth by
     * {@code A + 0.5 * m32 / w}, A 0 when the projection is reversed ({@code m32 > 0}) and 1
     * otherwise. Half the game's {@code b}: each point lies at twice its distance.
     */
    public static float[] lodistance(float m32) {
        return new float[]{m32 > 0f ? 0f : -1f, 0.5f * m32};
    }

    /** The depth stored for a point {@code distance} in front of the camera, by a projection with these terms. */
    public static float depth(float a, float b, boolean zeroToOne, float distance) {
        float ndc = b / distance - a;
        return zeroToOne ? ndc : ndc * 0.5f + 0.5f;
    }

    /** How far in front of the camera a stored depth is, by a projection with these terms: {@link #depth} undone. */
    public static float distance(float a, float b, boolean zeroToOne, float depth) {
        float ndc = zeroToOne ? depth : depth * 2.0f - 1.0f;
        return b / (ndc + a);
    }

    /** {@code {near, far}} of a projection with these terms, in this layout: {@link #terms} undone. */
    public static float[] planes(float a, float b, boolean reversed, boolean zeroToOne) {
        float nearDepth = reversed ? 1.0f : 0.0f;
        return new float[]{distance(a, b, zeroToOne, nearDepth), distance(a, b, zeroToOne, 1.0f - nearDepth)};
    }

    /**
     * A whole projection, its sixteen numbers row by row or column by column, inverted into
     * {@code out}; whether it could be. Through it a point {@code (x, y, ndc)} of its clip space
     * gives back the view: see {@link #viewZ}.
     */
    public static boolean invert(float[] values, boolean rowByRow, Matrix4f out) {
        if (values.length != 16) return false;
        // JOML reads its arrays column by column.
        out.set(values);
        if (rowByRow) out.transpose();
        float determinant = out.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1.0e-12f) return false;
        out.invert();
        return out.isFinite();
    }

    /**
     * The view-space {@code z} of a point at {@code (x, y, ndc)} of a projection's clip space,
     * through the projection's {@code inverse}: the {@code z} its {@code w} row divides by, view
     * bobbing included, which the game's own depth terms then turn into the game's depth.
     */
    public static float viewZ(Matrix4fc inverse, float x, float y, float ndc) {
        Vector4f p = inverse.transform(new Vector4f(x, y, ndc, 1.0f));
        return -1.0f / p.w;
    }

    /**
     * Distant Horizons' stored {@code depth} back to view-space {@code z} (negative in front of
     * the camera), at {@code (x, y)} on the screen, 0..1 from its bottom left corner: through
     * the inverse of its whole projection when known, else through its two terms. The shaders'
     * {@code lhDhViewZ}; the game's depth is then {@link #depth} at {@code -z}.
     */
    public static float dhViewZ(float depth, float a, float b, boolean signed, @Nullable Matrix4fc inverse,
                                float x, float y) {
        float ndc = signed ? depth * 2.0f - 1.0f : depth;
        if (inverse != null) return viewZ(inverse, x * 2.0f - 1.0f, y * 2.0f - 1.0f, ndc);
        return -b / (ndc + a);
    }

    /**
     * The rectangles of a {@code width} by {@code height} picture that spheres cover through
     * {@code viewProjection}, {@code x, y, w, h} each, two pixels wider all round, into
     * {@code out}; how many. {@code spheres} holds {@code x, y, z, radius} each, from the camera.
     * A sphere reaching behind the camera is left out: it is not past the render distance.
     */
    public static int rectangles(Matrix4fc viewProjection, float[] spheres, int count, int[] out, int width, int height) {
        int found = 0;
        Vector4f corner = new Vector4f();
        for (int f = 0; f < count && (found + 1) * 4 <= out.length; f++) {
            float x = spheres[f * 4], y = spheres[f * 4 + 1], z = spheres[f * 4 + 2], r = spheres[f * 4 + 3];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean behind = false;
            for (int c = 0; c < 8 && !behind; c++) {
                viewProjection.transform(corner.set(x + ((c & 1) == 0 ? -r : r), y + ((c & 2) == 0 ? -r : r),
                        z + ((c & 4) == 0 ? -r : r), 1f));
                if (corner.w <= 0.05f) behind = true;
                float sx = corner.x / corner.w, sy = corner.y / corner.w;
                minX = Math.min(minX, sx);
                minY = Math.min(minY, sy);
                maxX = Math.max(maxX, sx);
                maxY = Math.max(maxY, sy);
            }
            if (behind) continue;
            int x0 = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * width) - 2);
            int y0 = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * height) - 2);
            int x1 = Math.min(width, (int) Math.ceil((maxX * 0.5f + 0.5f) * width) + 2);
            int y1 = Math.min(height, (int) Math.ceil((maxY * 0.5f + 0.5f) * height) + 2);
            if (x1 <= x0 || y1 <= y0) continue;
            out[found * 4] = x0;
            out[found * 4 + 1] = y0;
            out[found * 4 + 2] = x1 - x0;
            out[found * 4 + 3] = y1 - y0;
            found++;
        }
        return found;
    }
}
