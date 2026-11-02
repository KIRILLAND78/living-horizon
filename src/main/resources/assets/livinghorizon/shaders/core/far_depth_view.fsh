#version 330
#extension GL_ARB_separate_shader_objects : require

#include <livinghorizon:far_depth.glsl>

// The depth view of the debug screen, drawn through the game's own device: near white to far
// black, the sky in dark blue, in a corner of the picture. The same as FarDepth's OpenGL one
// (compileView), both from far_depth.glsl.

uniform sampler2D InSampler;

layout(std140) uniform LhDepthView {
    // The corner it is drawn in, in screen fractions: x, y, width, height.
    vec4 Area;
    // The game's projection terms a and b, its near plane, and the distance shaded black.
    vec4 Terms;
    // 1 when the depth is reversed, 1 when it is 0..1.
    vec4 Layout;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = (texCoord - Area.xy) / Area.zw;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) discard;
    fragColor = lhDepthShade(texture(InSampler, uv).r, Terms.xy, Terms.zw, Layout.x > 0.5, Layout.y > 0.5);
}
