#version 150

// Bloom, going down: each step is half the size of the last and averages a wide, smooth footprint, so the glow
// spreads without flickering dots. The first step only lets bright light through.
uniform sampler2D DiffuseSampler;
uniform vec2 Texel;
uniform float Threshold;

in vec2 uv;
out vec4 fragColor;

vec3 at(float x, float y) {
    return texture(DiffuseSampler, uv + vec2(x, y) * Texel).rgb;
}

void main() {
    vec3 inner = (at(-1.0, -1.0) + at(1.0, -1.0) + at(-1.0, 1.0) + at(1.0, 1.0)) * 0.125;
    vec3 cross = (at(0.0, -2.0) + at(-2.0, 0.0) + at(2.0, 0.0) + at(0.0, 2.0)) * 0.0625;
    vec3 corners = (at(-2.0, -2.0) + at(2.0, -2.0) + at(-2.0, 2.0) + at(2.0, 2.0)) * 0.03125;
    vec3 col = at(0.0, 0.0) * 0.125 + inner + cross + corners;
    if (Threshold > 0.0) {
        float peak = max(col.r, max(col.g, col.b));
        col *= smoothstep(Threshold, Threshold + 0.3, peak);
    }
    fragColor = vec4(col, 1.0);
}
