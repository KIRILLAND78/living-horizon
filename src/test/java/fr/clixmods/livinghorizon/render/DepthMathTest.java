package fr.clixmods.livinghorizon.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthMathTest {
    private static final boolean[] BOTH = {false, true};
    /** The game's near plane, and a far plane pushed out for the far terrain. */
    private static final float NEAR = 0.05f, FAR = 1024f;

    /** The depth a projection stores for a view-space point, in the layout the matrix has. */
    private static float stored(Matrix4f projection, float x, float y, float z, boolean zeroToOne) {
        Vector4f clip = projection.transform(new Vector4f(x, y, z, 1f));
        float ndc = clip.z / clip.w;
        return zeroToOne ? ndc : ndc * 0.5f + 0.5f;
    }

    /** A JOML perspective in one of the four layouts: reversed is near and far swapped. */
    private static Matrix4f perspective(float near, float far, boolean reversed, boolean zeroToOne) {
        return new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9f,
                reversed ? far : near, reversed ? near : far, zeroToOne);
    }

    /** View bobbing, the hurt tilt and a little sway: turns and a small step, as the game multiplies in. */
    private static Matrix4f bobbing() {
        return new Matrix4f().translate(0.04f, -0.06f, 0.01f)
                .rotateZ((float) Math.toRadians(2.5))
                .rotateX((float) Math.toRadians(1.8))
                .rotateY((float) Math.toRadians(-0.7));
    }

    /**
     * Lodistance's depth with Sodium, written as its vertex shader does ({@code z = A * w + 0.5 *
     * m32}): read back with its terms it is the right distance, it lies behind the game's depth
     * for the same distance in 26.3's layout, and the game's depth for that distance is what a point
     * there stores.
     */
    @Test
    void lodistanceDepthToTheGamesInEveryLayout() {
        float[] distances = {150f, 260f, 700f, 1000f};
        for (boolean reversed : BOTH) {
            for (boolean zeroToOne : BOTH) {
                Matrix4f projection = perspective(NEAR, FAR, reversed, zeroToOne);
                float[] game = DepthMath.fromProjection(projection);
                float[] lod = DepthMath.lodistance(projection.m32());
                String layout = (reversed ? "reversed " : "") + (zeroToOne ? "0..1" : "-1..1");
                assertNotNull(game, layout);
                for (float w : distances) {
                    float ndc = ((reversed ? 0f : 1f) * w + 0.5f * projection.m32()) / w;
                    float written = zeroToOne ? ndc : ndc * 0.5f + 0.5f;
                    float distance = DepthMath.distance(lod[0], lod[1], zeroToOne, written);
                    // A float holds little near 1, or near 0.5 for a reversed -1..1: 26.3's layout loses least.
                    assertEquals(w, distance, w * (reversed && zeroToOne ? 1e-3f : 1e-2f), layout + " at " + w);
                    float real = DepthMath.depth(game[0], game[1], zeroToOne, distance);
                    // Reversed and 0..1, as on 26.3 where it runs, its depth lies behind the game's
                    // for the same point up to half the far plane, in front past it (FarDepth's
                    // LOD_MERGE moves it both ways). Its formula does not expect the other layouts.
                    if (reversed && zeroToOne) {
                        assertTrue(w < FAR / 2 ? written < real : written > real, layout + " at " + w);
                    }
                    assertEquals(stored(projection, 0f, 0f, -w, zeroToOne), real, 1e-5f, layout + " at " + w);
                }
            }
        }
    }

    @Test
    void distanceToDepthAndBackInEveryLayout() {
        float[] distances = {0.05f, 0.3f, 2f, 17f, 64f, 250f, 700f, 1024f};
        for (boolean reversed : BOTH) {
            for (boolean zeroToOne : BOTH) {
                float[] t = DepthMath.terms(NEAR, FAR, reversed, zeroToOne);
                String layout = (reversed ? "reversed " : "") + (zeroToOne ? "0..1" : "-1..1");
                float previous = Float.NaN;
                for (float d : distances) {
                    float depth = DepthMath.depth(t[0], t[1], zeroToOne, d);
                    assertTrue(depth >= -1e-6f && depth <= 1f + 1e-6f, layout + ": depth " + depth + " at " + d);
                    assertEquals(d, DepthMath.distance(t[0], t[1], zeroToOne, depth), d * 2e-3f, layout + " at " + d);
                    // Farther is greater, or smaller once reversed.
                    if (!Float.isNaN(previous)) assertTrue(reversed ? depth < previous : depth > previous, layout + " at " + d);
                    previous = depth;
                }
                // The near plane at one end of the range, the far one at the other.
                assertEquals(reversed ? 1f : 0f, DepthMath.depth(t[0], t[1], zeroToOne, NEAR), 1e-5f, layout);
                assertEquals(reversed ? 0f : 1f, DepthMath.depth(t[0], t[1], zeroToOne, FAR), 1e-5f, layout);
                float[] planes = DepthMath.planes(t[0], t[1], reversed, zeroToOne);
                assertEquals(NEAR, planes[0], NEAR * 1e-3f, layout + ": near");
                assertEquals(FAR, planes[1], FAR * 1e-3f, layout + ": far");
            }
        }
    }

    /**
     * Ecstatic draws its terrain after the entities, tested against the game's depth, with the
     * game's near plane and a far plane of its own. With the game's plane nearer, a hill well
     * behind a distant mob passes the test and is painted over it; with the same plane, only a
     * hill in front of it does.
     */
    @Test
    void ecstaticsTerrainStaysBehindAMobOnlyWithTheSameFarPlane() {
        float mob = 300f, behind = 400f, inFront = 295f;
        float[] game = DepthMath.terms(NEAR, 1100f, false, false);
        float[] ecstatic = DepthMath.terms(NEAR, 16800f * 1.05f, false, false);
        float mobDepth = DepthMath.depth(game[0], game[1], false, mob);
        assertTrue(DepthMath.depth(ecstatic[0], ecstatic[1], false, behind) <= mobDepth, "the hill behind wins");

        float matched = DepthMath.depth(ecstatic[0], ecstatic[1], false, mob);
        assertTrue(DepthMath.depth(ecstatic[0], ecstatic[1], false, behind) > matched, "the hill behind loses");
        assertTrue(DepthMath.depth(ecstatic[0], ecstatic[1], false, inFront) <= matched, "the hill in front wins");
    }

    @Test
    void termsAreTheProjectionsOwn() {
        for (boolean reversed : BOTH) {
            for (boolean zeroToOne : BOTH) {
                Matrix4f projection = perspective(NEAR, FAR, reversed, zeroToOne);
                float[] t = DepthMath.terms(NEAR, FAR, reversed, zeroToOne);
                float[] read = DepthMath.fromProjection(projection);
                assertNotNull(read);
                assertArrayEquals(t, read, 1e-4f);
                // What the matrix stores is what the terms say.
                for (float d : new float[]{0.5f, 30f, 600f}) {
                    assertEquals(stored(projection, 3f, -1f, -d, zeroToOne), DepthMath.depth(t[0], t[1], zeroToOne, d), 1e-5f);
                }
            }
        }
    }

    @Test
    void aMatrixIsReadWhicheverWayItIsStored() {
        float[] t = DepthMath.terms(7.5f, 8000f, false, false);
        // Named m<row><column>: the offset at row 2, column 3, and -1 below it.
        assertArrayEquals(t, DepthMath.fromMatrix(t[0], t[1], -1f), 0f);
        assertTrue(DepthMath.rowByRow(t[1]));
        // Stored column by column: the two swapped.
        assertArrayEquals(t, DepthMath.fromMatrix(t[0], -1f, t[1]), 0f);
        assertTrue(!DepthMath.rowByRow(-1f));
        // A slightly bobbed one still reads as column by column.
        assertTrue(!DepthMath.rowByRow(-0.9995f));
    }

    @Test
    void anOrthographicProjectionHasNoTerms() {
        assertNull(DepthMath.fromProjection(new Matrix4f().ortho(-1, 1, -1, 1, 0.05f, 100f)));
    }

    @Test
    void bobbingLeavesTheTermsAlone() {
        Matrix4f bob = bobbing();
        for (boolean reversed : BOTH) {
            for (boolean zeroToOne : BOTH) {
                Matrix4f projection = perspective(NEAR, FAR, reversed, zeroToOne);
                Matrix4f bobbed = new Matrix4f(projection).mul(bob);
                float[] t = DepthMath.fromProjection(bobbed);
                assertNotNull(t);
                assertArrayEquals(DepthMath.terms(NEAR, FAR, reversed, zeroToOne), t, 1e-4f);
                // Its two entries alone are off: the bobbing turned them.
                assertTrue(Math.abs(bobbed.m22() / t[0] - 1f) > 1e-4f, "m22 " + bobbed.m22() + " against " + t[0]);
                // The depth the world is drawn with is the terms' at the bobbed z.
                for (float[] v : new float[][]{{0, 0, -40}, {12, 5, -90}, {-30, -8, -300}, {1, 1, -2}}) {
                    float z = new Matrix4f(bob).transform(new Vector4f(v[0], v[1], v[2], 1f)).z;
                    assertEquals(stored(bobbed, v[0], v[1], v[2], zeroToOne), DepthMath.depth(t[0], t[1], zeroToOne, -z), 1e-5f);
                }
            }
        }
    }

    /**
     * Distant Horizons takes the game's bobbed projection and replaces its two depth entries
     * with its own: only the whole matrix inverted gives back the z the game's depth is made at.
     */
    @Test
    void distantHorizonsDepthThroughABobbedProjection() {
        Matrix4f bob = bobbing();
        Matrix4f game = perspective(NEAR, FAR, false, false).mul(bob);
        float[] dhTerms = DepthMath.terms(7.5f, 8000f, false, false);
        Matrix4f dh = new Matrix4f(game).m22(dhTerms[0]).m32(dhTerms[1]);
        float[] gameTerms = DepthMath.fromProjection(game);
        assertNotNull(gameTerms);

        float[] columns = dh.get(new float[16]);
        float[] rows = new Matrix4f(dh).transpose().get(new float[16]);
        Matrix4f inverse = new Matrix4f(), fromRows = new Matrix4f();
        assertTrue(DepthMath.invert(columns, false, inverse));
        assertTrue(DepthMath.invert(rows, true, fromRows));
        assertTrue(inverse.equals(fromRows, 1e-6f));

        boolean offByTerms = false;
        for (float[] v : new float[][]{{0, 0, -40}, {25, 9, -120}, {-80, -20, -400}, {150, 40, -900}}) {
            Vector4f clip = dh.transform(new Vector4f(v[0], v[1], v[2], 1f));
            float x = clip.x / clip.w, y = clip.y / clip.w, ndc = clip.z / clip.w;
            float z = new Matrix4f(bob).transform(new Vector4f(v[0], v[1], v[2], 1f)).z;
            float viewZ = DepthMath.viewZ(inverse, x, y, ndc);
            assertEquals(z, viewZ, Math.abs(z) * 1e-3f, "z at " + v[2]);
            // Forward through the game's terms: the depth the game drew that point with.
            assertEquals(stored(game, v[0], v[1], v[2], false),
                    DepthMath.depth(gameTerms[0], gameTerms[1], false, -viewZ), 1e-4f);
            // Its two terms alone miss the bobbing.
            float byTerms = DepthMath.distance(dhTerms[0], dhTerms[1], true, ndc);
            if (Math.abs(byTerms + z) > Math.abs(z) * 1e-2f) offByTerms = true;
        }
        assertTrue(offByTerms, "the bobbing moves Distant Horizons' depth");
    }

    /**
     * What the merge shaders do, one pixel at a time (far_depth.glsl, mirrored by dhViewZ and
     * depth): Distant Horizons' stored depth, in either of its layouts, through its terms or its
     * whole matrix, into the game's in each of its layouts lands where the game draws the point.
     */
    @Test
    void distantHorizonsDepthIntoTheGamesAsTheShadersDo() {
        Matrix4f bob = bobbing();
        for (boolean signed : BOTH) {
            float[] dhTerms = DepthMath.terms(7.5f, 8000f, false, !signed);
            for (boolean exact : BOTH) {
                Matrix4f dh = new Matrix4f(perspective(NEAR, FAR, false, !signed)).mul(bob).m22(dhTerms[0]).m32(dhTerms[1]);
                if (!exact) dh = perspective(7.5f, 8000f, false, !signed);
                Matrix4f inverse = new Matrix4f();
                assertTrue(DepthMath.invert(dh.get(new float[16]), false, inverse));
                for (boolean reversed : BOTH) {
                    for (boolean zeroToOne : BOTH) {
                        Matrix4f game = perspective(NEAR, FAR, reversed, zeroToOne);
                        if (exact) game.mul(bob);
                        float[] gameTerms = DepthMath.fromProjection(game);
                        assertNotNull(gameTerms);
                        for (float[] v : new float[][]{{0, 0, -40}, {25, 9, -120}, {-80, -20, -400}, {150, 40, -900}}) {
                            Vector4f clip = dh.transform(new Vector4f(v[0], v[1], v[2], 1f));
                            float x = (clip.x / clip.w) * 0.5f + 0.5f, y = (clip.y / clip.w) * 0.5f + 0.5f;
                            float stored = stored(dh, v[0], v[1], v[2], !signed);
                            float z = DepthMath.dhViewZ(stored, dhTerms[0], dhTerms[1], signed, exact ? inverse : null, x, y);
                            String at = "signed " + signed + ", exact " + exact + ", reversed " + reversed
                                    + ", 0..1 " + zeroToOne + ", z " + v[2];
                            assertTrue(z < 0f, at);
                            assertEquals(stored(game, v[0], v[1], v[2], zeroToOne),
                                    DepthMath.depth(gameTerms[0], gameTerms[1], zeroToOne, -z), 2e-4f, at);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aFlatMatrixDoesNotInvert() {
        Matrix4f out = new Matrix4f();
        assertTrue(!DepthMath.invert(new float[16], false, out));
        assertTrue(!DepthMath.invert(new float[9], false, out));
    }

    /**
     * Distant Horizons under Iris on 26.2, measured in game: sky 0, ground at 53 blocks 0.067,
     * horizon 0.005, in its reported layout, reversed and 0..1. Iris's planes were not written
     * down with the measurement: the near plane is the one the ground's reading gives, for far
     * planes of several sizes.
     */
    @Test
    void distantHorizonsUnderIrisOn262() {
        float ground = 0.067f, sky = 0f, horizon = 0.005f;
        for (float far : new float[]{2048f, 8192f, 32768f}) {
            // Reversed 0..1: depth = near (far - d) / (d (far - near)), solved for near.
            float near = ground * 53f * far / (far - 53f + ground * 53f);
            assertTrue(near > 1f && near < 8f, "near " + near);
            float[] t = DepthMath.terms(near, far, true, true);
            assertEquals(53f, DepthMath.distance(t[0], t[1], true, ground), 0.05f);
            // Empty is 0: the far plane itself.
            assertEquals(far, DepthMath.distance(t[0], t[1], true, sky), far * 1e-3f);
            float there = DepthMath.distance(t[0], t[1], true, horizon);
            assertTrue(there > 5 * 53f && there < far, "horizon at " + there + " of " + far);
            // Read in OpenGL's usual layout, the same ground would stand at the camera.
            float[] wrong = DepthMath.terms(near, far, false, false);
            assertTrue(DepthMath.distance(wrong[0], wrong[1], false, ground) < 10f);
        }
    }

    @Test
    void rectanglesAroundFiguresInFront() {
        Matrix4f viewProjection = perspective(NEAR, FAR, false, false);
        int width = 1600, height = 900;
        float[] spheres = {
                0, 0, -100, 1,      // straight ahead
                0, 0, 10, 1,        // behind the camera
                0, 0, -0.5f, 1,     // around the camera
                5000, 0, -10, 1,    // far off to the right
                -125, 0, -100, 4,   // across the left edge
        };
        int[] out = new int[4 * 8];
        int count = DepthMath.rectangles(viewProjection, spheres, 5, out, width, height);
        assertEquals(2, count);

        // Ahead: centred, as wide as the sphere's box seen from 99 blocks, two pixels more each side.
        float focal = viewProjection.m11();
        float half = 1f / 99f * focal * height / 2f;
        assertEquals(width / 2f, out[0] + out[2] / 2f, 1.5f);
        assertEquals(height / 2f, out[1] + out[3] / 2f, 1.5f);
        assertEquals(2 * half + 4, out[3], 2f);
        assertEquals(2 * half * 1f + 4, out[2], 2f);

        // Across the edge: held to the picture.
        assertEquals(0, out[4]);
        assertTrue(out[6] > 0 && out[6] < width / 4);

        // Never more than the room given.
        assertEquals(1, DepthMath.rectangles(viewProjection, spheres, 5, new int[4], width, height));
    }
}
