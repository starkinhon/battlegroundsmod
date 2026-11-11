#version 150

// The Esferas do Dragão (client/DragonBallRenderer): the ball of the approved Shenlong cutscene preview
// (forja/shenlong, BALL_V/BALL_F). Vanilla entity layout:
//   Color: rgb = the sphere's own normal (0..1), so the stars stay put on the ball; a = stars / 7.
// The fresnel and the highlight are worked out in view space, so the ball looks the same in the hand, on the
// ground and in the inventory (orthographic there: the view direction is just +z).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vLocal;
out vec3 vViewN;
out vec3 vViewDir;
out float vStars;
out vec4 vLight;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vLocal = Color.rgb * 2.0 - 1.0;
    vStars = floor(Color.a * 7.0 + 0.5);
    vViewN = mat3(ModelViewMat) * Normal;
    bool ortho = ProjMat[3][3] > 0.5;
    vViewDir = ortho ? vec3(0.0, 0.0, 1.0) : -viewPos.xyz;
    // the inventory's view space has y going down the screen: flip it so the highlight stays up and to the left
    if (ortho) vViewN.y = -vViewN.y;
    vLight = texelFetch(Sampler2, UV2 / 16, 0);
}
