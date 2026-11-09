#version 150

// The Duelo de Expansões sky (client/fx/DuelScene): a sphere round the camera, Position relative to it.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vW;

void main() {
    vW = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
