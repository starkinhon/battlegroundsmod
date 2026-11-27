#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The station shows from the inside out (Reveal: how far from where he stood, flat) and goes into the black far off (the
// preview's fog, FogNear to FogFar from the eye). Nothing else: no light here but what the colours carry.
uniform float Reveal;
uniform float FogNear;
uniform float FogFar;

in vec4 vColor;
in vec3 vLocal;
in float vDist;

out vec4 fragColor;

void main() {
    if (length(vLocal.xz) > Reveal) {
        discard;
    }
    float fog = smoothstep(FogNear, FogFar, vDist);
    fragColor = vec4(vColor.rgb * (1.0 - fog), 1.0);
}
