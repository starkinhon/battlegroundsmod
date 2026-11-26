#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The round Earth of the Esfera da Destruição Planetária (client/fx/SpaceScene): a unit sphere (three.js's
// SphereGeometry layout, uv with v from the top) placed by Model (camera-relative world).
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
out vec3 vN;
out vec3 vW;
out vec2 vU;
void main() {
    vec4 w = Model * vec4(Position, 1.0);
    vW = w.xyz;
    vN = normalize(mat3(Model) * Position);
    vU = UV0;
    gl_Position = ProjMat * ModelViewMat * w;
}
