#version 330
#extension GL_ARB_separate_shader_objects : require

#include <livinghorizon:far_depth.glsl>

// Before Distant Horizons fades the game's picture into its terrain: whatever is not the game's
// own terrain reads as sky, which the fade leaves alone. Outside the game's chunks, that is
// everything - the distant figures, and Distant Horizons' merged depth. Inside them, whatever
// stands clearly in front of Distant Horizons' terrain: an entity, the terrain matching there.

uniform sampler2D InSampler;
uniform sampler2D DhDepth;

layout(std140) uniform LhFadeMask {
    // From the screen back to the world, relative to the camera.
    mat4 InverseViewProjection;
    // The game's chunks, relative to the camera: lowest x, lowest z, highest x, highest z.
    vec4 Chunks;
    // 1 when the depth is 0..1, the empty depth, 1 when Distant Horizons' depth is bound.
    vec4 Layout;
    // Distant Horizons' projection terms a and b, 1 when its depth is -1..1, its empty depth.
    vec4 Dh;
    // The game's projection terms a and b.
    vec4 Game;
    // The inverse of Distant Horizons' whole projection: view bobbing moves its depth too.
    mat4 DhInverse;
    // 1 when DhInverse is known.
    vec4 Exact;
};

layout(location = 0) in vec2 texCoord;

void main() {
    float d = texture(InSampler, texCoord).r;
    if (d == Layout.y) discard;
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, Layout.x > 0.5 ? d : d * 2.0 - 1.0, 1.0);
    vec4 world = InverseViewProjection * ndc;
    vec3 at = world.xyz / world.w;
    if (at.x >= Chunks.x && at.x <= Chunks.z && at.z >= Chunks.y && at.z <= Chunks.w) {
        if (Layout.z < 0.5) discard;
        float dh = texture(DhDepth, texCoord).r;
        if (dh == Dh.w) discard;
        float here = lhDistance(d, Game.xy, Layout.x > 0.5);
        float terrain = -lhDhViewZ(dh, Dh, Exact.x > 0.5, DhInverse, texCoord);
        // The game's own terrain: at Distant Horizons' depth, give or take its blocks.
        if (!(here < terrain - max(0.5, terrain * 0.01))) discard;
    }
    gl_FragDepth = Layout.y;
}
