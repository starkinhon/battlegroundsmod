#version 150

#moj_import <fog.glsl>

// Shibuya's big screens (client/TelaoRenderer): each face of a Telão block shows its piece of one procedural video
// that runs over the whole screen in world space, the same as the approved preview (forja/jujutsu-shibuya/page.html).
// Positions come in relative to the camera; CamPos puts them back in the world so the video stays put.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CamPos;
uniform int FogShape;

out vec3 wp;
out float vertexDistance;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    wp = Position + CamPos;
    vertexDistance = fog_distance(Position, FogShape);
}
