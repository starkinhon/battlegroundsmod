#version 150

// Sukuna's thrown Dismantle (client/fx/DismantleRenderer): a crescent quad fixed in the world, camera-relative, UV0 across it.
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;

void main() {
    vUv = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
