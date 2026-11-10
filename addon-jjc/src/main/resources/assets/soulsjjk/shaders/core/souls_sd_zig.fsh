#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float A;
uniform float Seed;

in vec2 vUv;
in vec3 vW;
out vec4 fragColor;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float k = 2.0 + floor(Seed * 3.0);
    float tri = abs(fract(p.x * k * 0.5 + 0.25) - 0.5) * 2.0 - 0.5;
    float d = abs(p.y - tri * 0.7) - 0.13;
    float a = (1.0 - smoothstep(0.0, 0.08, d)) * step(abs(p.x), 0.95) * A;
    if (a < 0.004) discard;
    fragColor = vec4(vec3(a), a);
}
