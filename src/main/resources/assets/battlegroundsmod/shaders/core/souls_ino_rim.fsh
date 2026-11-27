#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The page's iaRimF line by line: a = pow(1 - |N.V|, 2.2) * (0.75 + 0.25 sin(3 t)) * uA, lilac * a * 1.3 (additive,
// src alpha + one). uA comes in the vertex color's alpha, t in seconds from GameTime.
uniform float GameTime;

in vec4 vertexColor;
in vec3 vN;
in vec3 vV;

out vec4 fragColor;

void main() {
    float f = 1.0 - abs(dot(normalize(vN), normalize(vV)));
    float t = GameTime * 1200.0;
    float a = pow(f, 2.2) * (0.75 + 0.25 * sin(t * 3.0)) * vertexColor.a;
    if (a < 0.002) {
        discard;
    }
    fragColor = vec4(vec3(0.86, 0.66, 1.0) * a * 1.3, a);
}
