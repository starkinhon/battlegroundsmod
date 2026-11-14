#version 150

// The vapour of an exorcised curse (client/fx/CurseVapor): the back faces of the box it is raymarched in. Positions
// are camera-relative, so the ray starts at 0 and goes through vW.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vW;

void main() {
    vW = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
