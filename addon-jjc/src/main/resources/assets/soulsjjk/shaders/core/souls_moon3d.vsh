#version 150

// Kokushibo's Moon Breathing forms 2, 3, 5 and 6 (client/fx/MoonFormsRenderer): the cuts are strips in the world and the
// moons and sparkles billboards, as on the approved 3D pages (forja/kokushibo-formas/moon3d.html).
// UV0: where the pixel is on its piece (a cut: s along it and q across it; a moon or sparkle: its own units).
// Color: the piece's numbers (a cut: point, tail, fade, shape; a moon: thinness, alpha, dissolve, hollow).
// UV2.x: kind * 4096 + seed (a cut: its streak seed x 100; a moon: its index), UV2.y: a cut's length x 100 or a moon's turn x 1000.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 color;
flat out ivec2 data;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    color = Color;
    data = UV2;
}
