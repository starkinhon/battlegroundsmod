#version 150

// The wounds Shigaraki's Shift+Z closes (client/fx/shig/RegenFx), the preview's WOUND_FS: a ragged hole of raw flesh,
// shrinking (S). UV0 is the wound's own 0..1.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;

void main() {
    vUv = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
