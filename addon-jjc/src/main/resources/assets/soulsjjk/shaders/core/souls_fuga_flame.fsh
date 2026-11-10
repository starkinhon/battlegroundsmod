#version 150

// Sukuna's Fuga: one flame ribbon (the aura strips), exactly the preview's flameFS (forja/sukuna-fuga, approved
// 2026-11-08). The strip runs base (vUv.x 0) to tip (1). Cartoon fire: hard-edged tongues in five bands (white-hot core,
// yellow, orange, red, dark-red rim), white streaks racing along it, red pockets, the tip shredding into licks, redrawn 12
// times a second. Halo > 0.5: the same shape, soft, for the additive glow pass behind it.
uniform float FTime;
uniform float Seed;
uniform float Alpha;
uniform float Grow;
uniform float Heat;
uniform float Len;
uniform float Halo;
uniform float Tail;
uniform float Flow;
uniform float Root;

in vec2 vUv;
out vec4 fragColor;

float sm(float a, float b, float x) { float u = clamp((x - a) / (b - a), 0.0, 1.0); return u * u * (3.0 - 2.0 * u); }
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.0, w = 0.5; for (int i = 0; i < 4; i++) { a += w * vn(p); p = p * 2.03 + vec2(1.7, 9.2); w *= 0.5; } return a; }
vec3 pal(float h) { return h > 0.66 ? vec3(1.0, 0.97, 0.84) : h > 0.46 ? vec3(1.0, 0.84, 0.28) : h > 0.26 ? vec3(1.0, 0.52, 0.1) : h > 0.1 ? vec3(0.88, 0.2, 0.05) : vec3(0.5, 0.05, 0.03); }

void main() {
    float t = floor(FTime * 12.0 + Seed * 7.0) / 12.0 * 4.0;
    float a = vUv.x, s = vUv.y * 2.0 - 1.0;
    float e = 1.0 - abs(s);
    vec2 q = vec2(a * Len * 1.5 - t * Flow, s * 1.1 + Seed * 13.0);
    float n = fbm(q), n2 = fbm(q * 2.1 + vec2(t * 1.7, 3.1));
    float tip = sm(Tail, 1.0, a);
    float d = e * 1.3 - 0.45 + (n - 0.5) * 1.05 + (n2 - 0.5) * 0.4 - tip * (0.95 + 0.7 * n2);
    d -= sm(Grow - 0.12, Grow, a) * 3.0;
    d -= (1.0 - sm(0.0, 0.05, a)) * Root;
    d -= (1.0 - Alpha) * 1.3;
    if (Halo > 0.5) {
        float h = sm(-0.5, 0.02, d) * (1.0 - sm(0.02, 0.4, d) * 0.4);
        fragColor = vec4(vec3(1.0, 0.36, 0.06) * h * 0.42 * Alpha, 1.0);
        return;
    }
    if (d < 0.0) discard;
    float st = fbm(vec2(a * Len * 4.0 - t * Flow * 2.0, s * 4.0 + Seed * 5.0));
    float hot = d * 1.05 + Heat * 0.22 + (st - 0.5) * 0.35;
    if (st > 0.74 && d > 0.12) hot = 0.9;
    float pk = fbm(vec2(a * Len * 2.6 - t * Flow * 0.8, s * 2.2 + Seed * 3.0) + vec2(7.1, 2.3));
    if (d > 0.2 && pk < 0.34) hot = min(hot, pk < 0.27 ? 0.18 : 0.38);
    if (d < 0.06) hot = 0.05;
    fragColor = vec4(pal(hot), 1.0);
}
