#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Bloom, coming back up: a small tent blur of the smaller step, added onto the bigger one by the blend mode.
uniform sampler2D DiffuseSampler;
uniform vec2 Texel;

in vec2 uv;
out vec4 fragColor;

vec3 at(float x, float y) {
    return texture(DiffuseSampler, uv + vec2(x, y) * Texel).rgb;
}

void main() {
    vec3 col = at(0.0, 0.0) * 4.0
            + (at(-1.0, 0.0) + at(1.0, 0.0) + at(0.0, -1.0) + at(0.0, 1.0)) * 2.0
            + at(-1.0, -1.0) + at(1.0, -1.0) + at(-1.0, 1.0) + at(1.0, 1.0);
    fragColor = vec4(col / 16.0, 1.0);
}
