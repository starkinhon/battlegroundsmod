#version 150

// A body burned to ash (see AshDeath). Same vertex layout as vanilla entities; positions come in relative to the
// camera, so they are made relative to the body's feet for the noise to stay put while the camera moves.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 AshOrigin;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 local;
out vec3 normal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    local = Position - AshOrigin;
    normal = Normal;
}
