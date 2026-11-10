#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vW;

void main() {
    vW = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
