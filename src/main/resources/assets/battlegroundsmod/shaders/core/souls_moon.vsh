#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// Kokushibo's Moon Breathing slashes (client/fx/MoonSlashRenderer). One quad per slash: UV0 runs 0..1 across it,
// UV2.x is the slash's age in milliseconds, UV2.y which block of rows in Sampler0 holds its moons, Color.r picks the
// pass (1 the body, 0 the glow) and Color.a fades it.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 color;
flat out ivec2 data;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    color = Color;
    data = UV2;
}
