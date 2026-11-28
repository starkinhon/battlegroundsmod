#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Itadori's Escama Vermelha (ItadoriVeins): the body drawn once more a hair bigger, the veins lighting up under the skin.
// Vanilla entity vertex layout; positions come in relative to the camera, like Heart.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vP;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vP = Position;
}
