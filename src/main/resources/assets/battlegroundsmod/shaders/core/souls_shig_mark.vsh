#version 150

// Shigaraki's C on the ground (client/fx/shig/TrapFx): the crack that runs to the print (Mode 0) and the hand print
// (Mode 1), signed distances and noise, no texture. UV0 is the decal's own 0..1.
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
