#version 150

// The mod's world effects (flares, rings, streaks, beams, fire, smoke). Every quad carries its own look:
// UV0 runs 0..1 across the quad, UV2.x picks the shape and UV2.y is a 0..255 shape setting.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 color;
flat out int shape;
out float param;
out float viewDist;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    uv = UV0;
    color = Color;
    shape = UV2.x;
    param = float(UV2.y) / 255.0;
    viewDist = -view.z;
}
