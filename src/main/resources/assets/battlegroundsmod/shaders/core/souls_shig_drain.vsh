#version 150

// The cord of power pulled from the victim to Shigaraki's hand (client/fx/shig/AbsorbFx), the preview's drainMesh;
// UV0 = (along from the victim, around).
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
