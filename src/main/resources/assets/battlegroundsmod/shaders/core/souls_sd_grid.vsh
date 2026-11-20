#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 BoxInv;
uniform vec3 Off;

out vec3 vP;
out vec3 vN;

void main() {
    vP = (BoxInv * vec4(Position, 1.0)).xyz + Off;
    vN = Normal;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
