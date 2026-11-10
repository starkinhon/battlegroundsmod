#version 150

// The Susanoo's armored arm (see client/fx/SusanooArm). Positions come in relative to the camera; CamPos turns them
// back into world positions so the chakra noise stays on the arm while the camera moves.
in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CamPos;

out vec4 vertexColor;
out vec3 world;
out vec3 rel;
out vec3 normal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    rel = Position;
    world = Position + CamPos;
    normal = Normal;
}
