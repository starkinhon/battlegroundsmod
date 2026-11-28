#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Itadori's soul (ItadoriSoul, forja/itadori-kit GHOST_F line by line): the body once more, pale blue, faint inside and
// bright on the outline, turning white where it turns away. uA comes in the vertex color's alpha. Additive.
in vec4 vertexColor;
in vec3 vN;
in vec3 vV;

out vec4 fragColor;

void main() {
    float f = abs(dot(normalize(vN), normalize(vV)));
    float rim = pow(1.0 - f, 1.6);
    vec3 c = mix(vec3(0.55, 0.8, 1.0), vec3(1.0), rim * 0.5);
    float a = (0.18 + 0.75 * rim) * vertexColor.a;
    if (a < 0.002) {
        discard;
    }
    fragColor = vec4(c * a, 1.0);
}
