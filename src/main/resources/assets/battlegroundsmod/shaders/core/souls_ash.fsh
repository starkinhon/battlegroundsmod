#version 150

// Burn: 0 = the normal skin, 1 = charred black with glowing cracks.
// Progress: 0 = whole body, 1 = all crumbled away, top first, along a ragged glowing edge.
uniform sampler2D Sampler0;
uniform float GameTime;
uniform float AshHeight;
uniform float Burn;
uniform float Progress;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 local;
in vec3 normal;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.1) {
        discard;
    }
    float h = clamp(local.y / max(AshHeight, 0.1), 0.0, 1.0);
    float n = noise(local * 7.0) * 0.6 + noise(local * 19.0) * 0.4;
    // The crumbling front runs from above the head to below the feet.
    float front = 1.05 - Progress * 1.35;
    float d = h + (n - 0.5) * 0.4 - front;
    if (d > 0.0) {
        discard;
    }
    vec3 base = tex.rgb * vertexColor.rgb;
    float shade = 0.55 + 0.45 * max(dot(normalize(normal), normalize(vec3(0.3, 1.0, 0.2))), 0.0);
    vec3 charred = vec3(0.04, 0.032, 0.028) + base * 0.05;
    vec3 color = mix(base, charred, Burn) * shade;
    // Embers in the cracks, breathing slowly, dimming as the body goes.
    float t = GameTime * 24000.0;
    float crack = smoothstep(0.66, 0.74, noise(local * 11.0 + vec3(0.0, t * 0.01, 0.0)));
    float breathe = 0.7 + 0.3 * sin(t * 0.35 + n * 6.28);
    color += vec3(1.0, 0.32, 0.05) * crack * breathe * Burn * (1.0 - Progress * 0.6);
    // Bright ragged edge where it falls apart.
    float edge = smoothstep(-0.09, 0.0, d) * step(0.001, Progress);
    color = mix(color, vec3(1.0, 0.5, 0.15) * 1.4, edge);
    fragColor = vec4(color, 1.0);
}
