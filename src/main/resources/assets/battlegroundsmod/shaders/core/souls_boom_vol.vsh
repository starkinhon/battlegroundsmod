#version 150

// The end-game bosses' fire, dust and smoke volumes (client/fx/epic/EpicFx): the back faces of the box each volume is
// raymarched in. Positions are camera-relative, so the ray starts at 0 and goes through vW.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vW;

void main() {
    vW = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
