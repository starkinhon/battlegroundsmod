#version 150

// Sukuna's Fuga (client/fx/FugaRenderer): a small sprite, camera-relative, UV0 across it, its look in Color.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vCol;

void main() {
    vUv = UV0;
    vCol = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
