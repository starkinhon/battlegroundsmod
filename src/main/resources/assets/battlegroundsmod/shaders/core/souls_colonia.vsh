#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The Colônia do Jogo do Abate's barrier and sky (client/fx/ColoniaAirFx): Position is relative to the camera.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vRel;

void main() {
    vRel = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
