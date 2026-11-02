package fr.clixmods.livinghorizon.compat;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons' depth of this frame, as {@link DhDepth} read it: where it is, and how to
 * turn it back into a distance in front of the camera.
 *
 * @param texture its OpenGL texture name; 0 when it was read as a {@code view}
 * @param view    the game's own texture view of it, when it draws through Blaze3D (26.3, see
 *                {@link DhDepth#readView()}); null when it was read as a {@code texture}
 * @param a       the first of the two terms of its projection that matter to depth
 * @param b       the second
 * @param signed  whether its depth runs from -1 to 1, OpenGL's usual layout (else 0 to 1)
 * @param nothing the depth of a pixel with no terrain
 * @param inverse the inverse of its whole projection, its own copy: view bobbing moves its depth
 *                in a way the two terms alone do not say. Null where only the terms are known
 *                (before 3.2, or under a shader pack)
 */
record DhFrame(int texture, @Nullable Object view, float a, float b, boolean signed, float nothing,
               @Nullable Matrix4fc inverse) {
    /** {@link #signed} as the shaders take it, third of {@code (a, b, signed, nothing)}: 1 or 0. */
    float signedFlag() {
        return signed ? 1.0f : 0.0f;
    }
}
