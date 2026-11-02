#version 330
#extension GL_ARB_separate_shader_objects : require

#include <livinghorizon:far_depth.glsl>

// Distant Horizons' depth, turned into the game's: back to a distance in front of the camera
// through Distant Horizons' projection, then forward through the game's. Drawn where it is
// nearer than what is there, before the entities, so that its hills hide the distant mobs.
// The same as FarDepth's OpenGL program (compileDh), both from far_depth.glsl.

uniform sampler2D DhDepth;

layout(std140) uniform LhFarDepth {
    // Distant Horizons' projection terms a and b, 1 when its depth is -1..1, its empty depth.
    vec4 Dh;
    // The game's terms a and b, 1 when its depth is reversed, 1 when it is 0..1.
    vec4 Game;
    // The inverse of Distant Horizons' whole projection: view bobbing moves its depth too.
    mat4 DhInverse;
    // 1 when DhInverse is known.
    vec4 Exact;
};

layout(location = 0) in vec2 texCoord;

void main() {
    float d = texture(DhDepth, texCoord).r;
    if (d == Dh.w) discard;
    float z = lhDhViewZ(d, Dh, Exact.x > 0.5, DhInverse, texCoord);
    if (!(z < 0.0)) discard;
    float depth = lhGameDepth(z, Game.xy, Game.w > 0.5);
    if (Game.z > 0.5 ? !(depth > 0.0) : !(depth < 1.0)) discard;
    gl_FragDepth = clamp(depth, 0.0, 1.0);
}
