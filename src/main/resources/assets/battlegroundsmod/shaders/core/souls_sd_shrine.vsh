#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ModelMat;

out vec2 vUv;
out vec3 vW;
out vec3 vN;

void main() {
    vec4 w = ModelMat * vec4(Position, 1.0);
    vW = w.xyz;
    vN = normalize(mat3(ModelMat) * Normal);
    vUv = UV0;
    gl_Position = ProjMat * ModelViewMat * w;
}
