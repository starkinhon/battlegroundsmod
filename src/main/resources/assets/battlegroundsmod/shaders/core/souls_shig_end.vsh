#version 150

// Shigaraki's Shift+V, the Fim do Mundo (client/fx/shig/EndFx): the open cylinder the wall of dust is raymarched in
// (the preview's FM_VS). Positions camera-relative, so the ray starts at 0.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vW;

void main() {
    vW = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
