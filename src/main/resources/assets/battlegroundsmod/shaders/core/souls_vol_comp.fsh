#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The end-game bosses' fire and dust volumes are raymarched at half size (client/fx/epic/EpicFx: a quarter of the
// pixels, Stark 2026-11-19 with the GPU at 95% inside the Buddha's dust) and put back here, smoothed, premultiplied.
uniform sampler2D DiffuseSampler;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, uv);
    if (c.a < 0.002) discard;
    fragColor = c;
}
