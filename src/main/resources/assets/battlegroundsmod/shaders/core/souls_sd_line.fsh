#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float Kind;
uniform float Life;
uniform float Grow;
uniform float Seed;
uniform float Tap;
uniform float A;

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
    if (x > Grow) discard;
    float pk = 0.35 + 0.3 * h21(vec2(Seed, 1.0));
    float tp = x < pk ? x / pk : (1.0 - x) / (1.0 - pk);
    float taper = pow(max(tp, 0.0), Tap);
    float th = taper * (1.0 - Life * Life);
    float wob = 1.0 + 0.25 * (vn(vec2(x * 40.0 + Seed, Seed)) - 0.5);
    float d = abs(y) / max(th * wob, 1e-3);
    float tipFlash = exp(-pow((x - Grow) * 30.0, 2.0)) * (1.0 - Life);
    vec3 col = vec3(0.0);
    float a = 0.0;
    if (Kind < 0.5) {
        float core = 1.0 - smoothstep(0.45, 0.85, d);
        float edge = step(0.5, h21(vec2(Seed, 3.0))) * (1.0 - smoothstep(0.85, 1.0, d)) * smoothstep(0.6, 0.85, d);
        col = vec3(1.0) * core;
        a = max(core, edge * 0.9);
    } else if (Kind < 1.5) {
        float g = exp(-d * d * 2.5);
        col = mix(vec3(1.0, 0.82, 0.84), vec3(1.0), g) * g * 0.6;
        a = g * 0.6;
    } else if (Kind < 2.5) {
        float body = 1.0 - smoothstep(0.8, 1.0, d);
        float line = 1.0 - smoothstep(0.06, 0.16, d);
        float rim = smoothstep(0.55, 0.9, d) * body;
        col = mix(vec3(0.9, 0.04, 0.07), vec3(0.03, 0.0, 0.01), max(line, rim * 0.8)) * body;
        a = body;
    } else if (Kind < 3.5) {
        float core = 1.0 - smoothstep(0.35, 0.6, d);
        float halo = exp(-max(d - 0.5, 0.0) * 3.0) * (1.0 - core);
        col = vec3(1.0) * halo * 0.85;
        a = max(core, halo * 0.85);
    } else {
        float cy = y - 0.6 * sin(x * 6.2832 * (0.6 + h21(vec2(Seed, 5.0)) * 0.8) + Seed);
        float dd = abs(cy) / max(0.08 * (1.0 - Life), 1e-3);
        float s = 1.0 - smoothstep(0.5, 1.0, dd);
        s *= step(0.35, vn(vec2(x * 9.0 + Seed, 2.0)));
        col = vec3(1.0) * s;
        a = s;
    }
    col += vec3(1.0) * tipFlash * step(0.5, a);
    a = clamp(a + tipFlash * 0.5 * step(0.01, a), 0.0, 1.0);
    a *= A;
    if (a < 0.003) discard;
    fragColor = vec4(col * A, a);
}
