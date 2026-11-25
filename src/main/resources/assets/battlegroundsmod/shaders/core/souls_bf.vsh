#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The Black Flash (client/fx/BlackFlashRenderer): positions camera-relative, UV0 and Color carry the look (see souls_bf.fsh).
in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vCol;

void main() {
    vUv = UV0;
    vCol = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
