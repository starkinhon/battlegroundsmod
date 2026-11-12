#version 150

// Shigaraki's X, the Onda Sônica (client/fx/shig/ScreamFx): the wave's shell and the scream's funnel, their shape
// built on the CPU as in the preview; UV0 = (angle from the axis / half the cone or distance along, angle around).
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
