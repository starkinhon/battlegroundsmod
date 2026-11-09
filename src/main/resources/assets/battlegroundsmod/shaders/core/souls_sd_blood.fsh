#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float R;
uniform float A;
uniform float Seed;

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
    // A pool, not a stain (test 2026-11-09: it read as one flat red disc): a thick wet body that darkens where it
    // clots at the rim, a glossy sheen across it that moves as it spreads, and drops thrown out around it, each its own.
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    float an = atan(p.y, p.x);
    float edge = R * (0.72 + 0.34 * fb3(vec3(cos(an) * 1.3 + Seed, sin(an) * 1.3, Seed * 0.7 + R * 2.0)) + 0.12 * vn(p * 7.0 + Seed));
    float body = 1.0 - smoothstep(edge - 0.025, edge, r);
    // the drops: a few cells around the rim, each a blob of its own size, only some of them filled
    vec2 q = p * 7.0 + Seed * 3.1;
    vec2 ci = floor(q), cf = fract(q) - 0.5;
    float hc = h21(ci + Seed);
    vec2 off = vec2(h21(ci + 1.7), h21(ci + 9.3)) - 0.5;
    float dr = 0.07 + 0.16 * h21(ci + 4.4);
    float ring = smoothstep(R * 0.8, R * 1.0, r) * (1.0 - smoothstep(R * 1.15, R * 1.45 + 0.05, r));
    float drop = (1.0 - smoothstep(dr - 0.04, dr, length(cf - off * 0.5))) * step(0.55, hc) * ring;
    float a = max(body, drop) * A;
    if (a < 0.004) discard;
    float depth = smoothstep(edge, edge * 0.35, r) * body;
    float clot = smoothstep(edge * 0.7, edge, r) * body;
    vec3 col = mix(vec3(0.20, 0.0, 0.01), vec3(0.50, 0.015, 0.035), depth * (0.7 + 0.3 * vn(p * 3.0 + Seed)));
    col = mix(col, vec3(0.09, 0.0, 0.005), clot * 0.75);
    col = mix(col, vec3(0.26, 0.0, 0.015), drop * (1.0 - body));
    float sheen = smoothstep(0.62, 0.9, fb3(vec3(p * 2.2 + vec2(Seed, R * 1.5), R * 3.0))) * depth;
    col += vec3(0.85, 0.30, 0.28) * sheen * 0.55;
    fragColor = vec4(col * a, a);
}
