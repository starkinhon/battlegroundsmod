#version 150

// The Clivar's grid over a body (client/fx/ClivarRenderer): the entity's own model drawn again, each box grown a hair.
// Positions come in relative to the camera; `local` is relative to the body's feet and turned with the body's yaw, so
// the grid's lines are in block units, continuous across the boxes, and stay on the body as it turns.
in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Origin;
uniform float Yaw;

out vec2 texCoord;
out vec3 local;
out vec3 normal;

vec3 turn(vec3 v) {
    float c = cos(Yaw), s = sin(Yaw);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    local = turn(Position - Origin);
    normal = turn(Normal);
}
