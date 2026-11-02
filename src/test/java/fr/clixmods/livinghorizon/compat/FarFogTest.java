package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FarFogTest {
    /** The game's shaders: linear between start and end, times the fog colour's alpha. */
    private static float game(float distance, float start, float end, float alpha) {
        float fog = distance <= start ? 0 : distance >= end ? 1 : (distance - start) / (end - start);
        return fog * alpha;
    }

    /** Voxy's terrain: the same fog, which stops growing at the limit. */
    private static float voxy(float distance, float start, float end, float limit) {
        float cap = Math.clamp((limit - start) / (end - start), 0, 1);
        return Math.clamp((distance - start) / (end - start), 0, cap);
    }

    @Test
    void entitiesGetVoxysFogAtEveryDistance() {
        // A clear overworld, rain, and a start past the camera; Voxy's limit at 12 and 32 chunks.
        float[][] fogs = {{0, 1024}, {-160, 768}, {40, 900}};
        float[] limits = {(float) (320 * Math.sqrt(3)), (float) (512 * Math.sqrt(3))};
        for (float[] fog : fogs) {
            for (float limit : limits) {
                // Past the fog's end the limit changes nothing, and the game's fog stays.
                float[] far = FarFog.limited(fog[0], fog[1], limit);
                if (far == null) far = new float[]{fog[1], 1};
                for (float distance = 0; distance < 3000; distance += 7) {
                    assertEquals(voxy(distance, fog[0], fog[1], limit), game(distance, fog[0], far[0], far[1]), 1e-5,
                            "at " + distance + " blocks, fog " + fog[0] + ".." + fog[1] + ", limit " + limit);
                }
            }
        }
    }

    @Test
    void nothingChangesWhereTheLimitIsPastTheFog() {
        assertNull(FarFog.limited(0, 1024, 1200));
        assertNull(FarFog.limited(0, 1024, Float.NaN));
    }

    @Test
    void noFogWhereTheLimitIsBeforeItStarts() {
        float[] far = FarFog.limited(600, 1024, 554);
        assertNotNull(far);
        for (float distance = 0; distance < 3000; distance += 50) assertEquals(0, game(distance, 600, far[0], far[1]), 1e-6);
    }
}
