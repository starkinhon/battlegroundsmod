#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The end-game bosses' fire and dust volumes are raymarched at half size (client/fx/epic/EpicFx: a quarter of the
// pixels, Stark 2026-11-19 with the GPU at 95% inside the Buddha's dust) and put back here, smoothed, premultiplied.
// Smoothed for real: a 3x3 gaussian over the half-size texels (9 filtered reads), which melts the raymarch's
// start-offset pattern (souls_boom_vol) into a smooth cloud. With a single read the pattern stayed as a grain of
// dots doubled in size by the half layer (Stark 2026-11-26: "pixelated, low resolution").
uniform sampler2D DiffuseSampler;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec2 px = 1.0 / vec2(textureSize(DiffuseSampler, 0));
    vec4 c = texture(DiffuseSampler, uv) * 4.0;
    c += (texture(DiffuseSampler, uv + vec2(px.x, 0.0)) + texture(DiffuseSampler, uv - vec2(px.x, 0.0))
        + texture(DiffuseSampler, uv + vec2(0.0, px.y)) + texture(DiffuseSampler, uv - vec2(0.0, px.y))) * 2.0;
    c += texture(DiffuseSampler, uv + px) + texture(DiffuseSampler, uv - px)
        + texture(DiffuseSampler, uv + vec2(px.x, -px.y)) + texture(DiffuseSampler, uv + vec2(-px.x, px.y));
    c *= 1.0 / 16.0;
    if (c.a < 0.002) discard;
    fragColor = c;
}
