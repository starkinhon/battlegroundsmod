#version 150

// Sukuna's Fuga (client/fx/FugaRenderer): the fist's glow as a ball of light fixed in the world. A box around it,
// camera-relative; the fragment shader finds the glow along each view ray.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vRel;

void main() {
    vRel = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
