#version 150

// Kokushibo's sword turning into living flesh as it goes back to the sheath by itself (client/outfit/FleshSheathe),
// the material of the approved visualizer page (forja/kokushibo-espada/espada-carne-viva.html). Vanilla entity layout:
//   Color: r = how much flesh, g = how wet, a = 0 for plain slime (strings and drops, no texture), 1 for the blade.
//   UV1 (the overlay slot, unused here): x = the sword-space height * 100, y = (x * 5 + z * 7) * 100 of the
//   unbent mesh, so the veins stay put on the blade while it bends.
// Lit like an entity: the two world lights and the lightmap.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec4 lightColor;
out vec4 data;
out vec2 texCoord0;
out vec2 vein;
out vec3 wNormal;
out vec3 wPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec3 n = normalize(Normal);
    float a = max(0.0, dot(normalize(Light0_Direction), n));
    float b = max(0.0, dot(normalize(Light1_Direction), n));
    float diffuse = min(1.0, (a + b) * 0.6 + 0.4);
    lightColor = vec4(vec3(diffuse), 1.0) * texelFetch(Sampler2, UV2 / 16, 0);
    data = Color;
    texCoord0 = UV0;
    vein = vec2(UV1) / 100.0;
    wNormal = Normal;
    wPos = Position;
}
