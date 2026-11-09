#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float A;
uniform float Open;
uniform float Seed;
uniform float Asp;

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
    float x = vUv.x, y = (vUv.y - 0.5) * 2.0;
    float taper = pow(max(sin(x * 3.1416), 0.0), 0.55);
    float rag = (vn(vec2(x * Asp * 3.0 + Seed, Seed)) - 0.5) * 0.5 + (vn(vec2(x * Asp * 9.0, Seed + 7.0)) - 0.5) * 0.25;
    float hw = (0.18 + 0.32 * Open) * taper * (1.0 + rag);
    float d = abs(y + 0.12 * sin(x * 6.0 + Seed)) - hw;
    float core = 1.0 - smoothstep(-0.06, 0.0, d);
    float lip = exp(-max(d, 0.0) * 9.0) * (1.0 - core);
    float spray = step(0.86, vn(vec2(x * Asp * 14.0 + Seed, y * 6.0))) * exp(-max(d, 0.0) * 3.0) * (1.0 - core) * Open;
    vec3 col = mix(vec3(1.0), vec3(0.015, 0.004, 0.008), core);
    float a = max(core * 0.95, max(lip * 0.85, spray * 0.7)) * taper * A;
    if (a < 0.003) discard;
    fragColor = vec4(col * a, a);
}
