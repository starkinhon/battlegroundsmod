#version 150

// Rukia's ice shell over a body (see IceShell). Same vertex layout as vanilla entities; the boxes come in already
// grown a little (a shell around each part, like the photonic body of the visualizer). Positions come in relative to
// the camera: `rel` keeps that (for the view direction), `local` is relative to where the ice starts.
in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 IceOrigin;

out vec2 texCoord;
out vec3 local;
out vec3 rel;
out vec3 normal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    local = Position - IceOrigin;
    rel = Position;
    normal = Normal;
}
