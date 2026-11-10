#version 150

#moj_import <fog.glsl>

// The mod's procedural smoke particles (client/fx/SmokeParticle), the same shader as the Fumaça Nova preview.
// Nothing is sampled from a texture; the particle's numbers ride in the vertex:
//   UV0  = (corner x + 2 * life in thousandths, corner y)   corners go 0..1
//   UV1  = (height above the ground in 1/256 block + 8 blocks, style + 16 * seed)
//   UV2  = world light, as vanilla
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out vec2 corner;
out vec4 vertexColor;
out vec4 lightColor;
out float vertexDistance;
out float height;
flat out float life;
flat out float seed;
flat out int style;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);
    corner = vec2(mod(UV0.x, 2.0), UV0.y) * 2.0 - 1.0;
    life = floor(UV0.x / 2.0) / 1000.0;
    height = float(UV1.x) / 256.0 - 8.0;
    style = UV1.y % 16;
    seed = float(UV1.y / 16) / 1024.0;
    lightColor = texelFetch(Sampler2, UV2 / 16, 0);
    vertexColor = Color;
}
