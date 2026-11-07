// The depth arithmetic every far-depth shader shares: those FarDepth compiles for OpenGL
// splice this file in, those of FarDepthGpu (26.3) take it with #include. Mirrored in Java by
// DepthMath (dhViewZ, depth, distance), which DepthMathTest checks: a change here goes there too.

// How far in front of the camera a stored depth is, through a projection's two terms a and b
// (see DepthMath.terms), its depth 0..1 or -1..1.
float lhDistance(float depth, vec2 terms, bool zeroToOne) {
    float ndc = zeroToOne ? depth : depth * 2.0 - 1.0;
    return terms.y / (ndc + terms.x);
}

// The depth the game stores for a point at view-space z (negative in front of the camera),
// through its projection's two terms a and b, before any reversal: 0..1 or -1..1 mapped to 0..1.
float lhGameDepth(float z, vec2 terms, bool zeroToOne) {
    float depth = (terms.x * z + terms.y) / -z;
    return zeroToOne ? depth : depth * 0.5 + 0.5;
}

// Distant Horizons' stored depth d back to view-space z (negative in front of the camera).
// dh: its projection's terms a and b, 1 when its depth is -1..1, its empty depth. With its whole
// projection's inverse known (exact), through that: view bobbing moves its depth too. at: where
// on the screen, 0..1 from the bottom left corner.
float lhDhViewZ(float d, vec4 dh, bool exact, mat4 dhInverse, vec2 at) {
    float ndc = dh.z > 0.5 ? d * 2.0 - 1.0 : d;
    if (exact) {
        vec4 p = dhInverse * vec4(at * 2.0 - 1.0, ndc, 1.0);
        return -1.0 / p.w;
    }
    return -dh.y / (ndc + dh.x);
}

// The depth view's colour for a stored depth: near white to black at planes.y, logarithmic so
// that a block away and the horizon are both readable; the sky in dark blue.
vec4 lhDepthShade(float d, vec2 terms, vec2 planes, bool reversed, bool zeroToOne) {
    if (reversed ? d <= 0.0 : d >= 1.0) return vec4(0.08, 0.12, 0.32, 1.0);
    float near = planes.x, far = planes.y;
    float linear = lhDistance(d, terms, zeroToOne);
    float shade = 1.0 - log(max(linear, near) / near) / log(far / near);
    return vec4(vec3(clamp(shade, 0.0, 1.0)), 1.0);
}
