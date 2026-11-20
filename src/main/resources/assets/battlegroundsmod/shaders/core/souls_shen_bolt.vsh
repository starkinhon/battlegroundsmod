#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Lightning and the balls' trails (BOLT_V of the preview): ribbons facing the camera. UV0 = (aE, aA).
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// scene space (the preview's: the summoner at the origin looking +Z) -> camera-relative world
uniform mat4 SceneMat;
out float vE; out float vA;
void main(){ vE = UV0.x; vA = UV0.y; gl_Position = ProjMat * ModelViewMat * SceneMat * vec4(Position, 1.0); }
