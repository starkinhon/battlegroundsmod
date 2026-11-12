#version 150

// The hands of Shigaraki's C trap (client/fx/shig/TrapFx), the preview's HAND_VS/HAND_FS: positions camera-relative,
// pushed out along the normal for the outline (Out).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec3 vW;
out vec3 vN;
out vec4 vC;

void main() {
    vUv = UV0;
    vW = Position;
    vN = Normal;
    vC = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
