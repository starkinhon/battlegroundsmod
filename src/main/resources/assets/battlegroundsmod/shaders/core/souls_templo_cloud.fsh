#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The sea of clouds' color: the side facing the sun bright, the folds in shade; near the camera it fades (inside a cloud
// there is no "surface") and far away it goes into the haze.
uniform vec3 SunDir;
uniform vec3 Top;
uniform vec3 Shade;
uniform float Alpha;
uniform float Far;

in float vTh;
in vec3 vN;
in vec3 vRel;

out vec4 fragColor;

void main() {
    float lam = clamp(dot(normalize(vN), normalize(SunDir)) * 0.8 + 0.25, 0.0, 1.0);
    vec3 c = mix(Shade, Top, lam);
    float d = length(vRel);
    float a = smoothstep(0.03, 0.3, vTh) * Alpha * smoothstep(8.0, 45.0, d) * (1.0 - smoothstep(Far * 0.62, Far, d));
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(c, a);
}
