#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Itadori's Expansão de Domínio (client/fx/ItadoriDomainScene): the station, its light baked in the colours. Offset moves
// a piece (a train) within the domain's frame; the frame's own position goes to the fragment for the reveal.
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Offset;

out vec4 vColor;
out vec3 vLocal;
out float vDist;

void main() {
    vec3 p = Position + Offset;
    vLocal = p;
    vec4 view = ModelViewMat * vec4(p, 1.0);
    vDist = length(view.xyz);
    vColor = Color;
    gl_Position = ProjMat * view;
}
