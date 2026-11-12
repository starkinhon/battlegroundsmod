#version 150

// Shigaraki's Shift+Z (client/fx/shig/RegenFx): the muscle fibres that braid the arm back and sew the wounds, the
// preview's FIB_VS/FIB_FS. UV0 = (along the fibre, around), UV1 = (its delay x 10000, its seed x 100), UV2.x = its height
// on the arm x 1000 + 2000 (the skin closes over it from the shoulder down).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec3 vN;
out vec3 vV;
out float vLy;
out float vDelay;
out float vSeed;

void main() {
    vUv = UV0;
    vN = Normal;
    vV = normalize(-Position);
    vLy = (float(UV2.x) - 2000.) / 1000.;
    vDelay = float(UV1.x) / 10000.;
    vSeed = float(UV1.y) / 100.;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
