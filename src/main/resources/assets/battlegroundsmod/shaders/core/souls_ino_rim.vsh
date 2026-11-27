#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Ino's lilac outline over a body (InoRim, forja/ino-kit/ino-aura.js iaRimV/iaRimF): the body drawn once more a little
// bigger, bright only where its surface turns away from the camera. Vanilla entity vertex layout; positions come in
// relative to the camera, so the camera direction is just -Position (same space as Normal).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec3 vN;
out vec3 vV;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    vN = Normal;
    vV = -Position;
}
