#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Red;
uniform vec3 CamL;

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
    vec3 d = normalize(vW - CamL);
    float y = d.y;
    float ay = abs(y);
    vec2 q = d.xz / (ay + 0.12);
    float c = fb3(vec3(q * 0.55, T * 0.012) + vec3(T * 0.006, 0.0, 0.0));
    float c2 = fb3(vec3(q * 1.6 + c * 1.3, T * 0.02));
    float band = exp(-pow((ay - 0.08) / 0.16, 2.0)) + 0.35 * exp(-pow((ay - 0.28) / 0.2, 2.0));
    float cloud = smoothstep(0.38, 0.72, c * 0.7 + c2 * 0.45) * band;
    vec3 red = mix(vec3(0.18, 0.0, 0.01), vec3(0.72, 0.05, 0.08), smoothstep(0.5, 0.9, c2)) * cloud;
    red += vec3(0.06, 0.0, 0.005) * band * 0.5;
    fragColor = vec4(red * Red, 1.0);
}
