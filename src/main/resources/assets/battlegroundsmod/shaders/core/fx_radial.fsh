#version 150

// Radial blur: the frame smeared towards Center (a dash, the beat of a heavy hit). Nothing moves near the center,
// the full smear is at the edges of the screen.
uniform sampler2D DiffuseSampler;
uniform vec2 Screen;
uniform vec2 Center;
uniform float Strength;

in vec2 uv;
out vec4 fragColor;

const int STEPS = 14;

void main() {
    vec2 d = uv - Center;
    float r = length(d * vec2(Screen.x / Screen.y, 1.0));
    float k = Strength * smoothstep(0.05, 0.55, r) * 0.14;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < STEPS; i++) {
        float f = float(i) / float(STEPS - 1);
        float w = 1.0 - 0.7 * f;
        sum += texture(DiffuseSampler, uv - d * k * f).rgb * w;
        total += w;
    }
    fragColor = vec4(sum / total, 1.0);
}
