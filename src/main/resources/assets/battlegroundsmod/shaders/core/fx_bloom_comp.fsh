#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Adds the glow layer and its bloom onto the frame. Light that piles up is squeezed keeping its hue, so many red
// flames make a deep red, not a white sheet.
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform float Intensity;

in vec2 uv;
out vec4 fragColor;

vec3 squeeze(vec3 c, float from, float top) {
    float peak = max(c.r, max(c.g, c.b));
    if (peak <= from) {
        return c;
    }
    float squeezed = from + (top - from) * (1.0 - exp(-(peak - from) / (top - from)));
    return c * (squeezed / peak);
}

void main() {
    vec3 sharp = squeeze(texture(DiffuseSampler, uv).rgb, 0.6, 1.0);
    vec3 glow = squeeze(texture(BloomSampler, uv).rgb * Intensity, 0.3, 0.7);
    fragColor = vec4(sharp + glow, 1.0);
}
