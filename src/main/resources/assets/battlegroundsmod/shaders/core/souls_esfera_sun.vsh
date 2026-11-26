#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The sun of destruction of the Esfera da Destruição Planetária (client/fx/SpaceScene): quads facing the camera, kept
// upright in the world (they never turn with the camera's tilt), uv 0..1 to the right and up.
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 vu;
void main() {
    vu = UV0 * 2.0 - 1.0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
