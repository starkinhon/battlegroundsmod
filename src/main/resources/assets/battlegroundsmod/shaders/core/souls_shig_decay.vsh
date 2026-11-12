#version 150

// The Decadência over a block (client/fx/shig/DecayBlocks), the preview's VOX_FS part that dries, cracks and greys the
// block: Color.r = how light the block's own colour is, Color.g = its light, Color.b = how much of it shows, UV0 = (seconds since the Decadência got
// there, the block's random number). Camera-relative positions; Origin folds the camera into the world noise.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Origin;

out vec3 vW;
out vec3 vN;
out float vAge;
out float vLum;
out float vLight;
out float vFade;

void main() {
    vW = Position + Origin;
    vN = Normal;
    vAge = UV0.x;
    vLum = Color.r;
    vLight = Color.g;
    vFade = Color.b;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
