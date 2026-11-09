#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float A;
uniform float Seed;
uniform float W;
uniform float H;

in vec2 vUv;
in vec3 vW;
out vec4 fragColor;

float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float n3(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
float fb3(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * n3(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; } return s; }
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y); }
mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }

void main() {
    float x = abs(vUv.x - 0.5) * 2.0;
    float f = n3(vec3(vUv.x * 3.0 + Seed, vUv.y * (H / 3.0) - T * 0.05, T * 0.02));
    float core = exp(-x * x * (6.0 / W)) * (0.55 + 0.6 * f) * smoothstep(0.0, 0.15, vUv.y) * (1.0 - smoothstep(0.55, 1.0, vUv.y));
    fragColor = vec4(vec3(0.25, 0.95, 1.0) * core * A, 1.0);
}
