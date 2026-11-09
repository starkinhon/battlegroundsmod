#version 150

// Sukuna's thrown Dismantle (the crescent of the approved 3D preview, forja/sukuna-desmantelar crescentMat, as used in
// forja/sukuna-combo): convex side ahead (+v). Tongues of flame along the inside, white-hot when born (Heat 1), then
// solid black with a white outline and dry-brush edges (Heat 0). It never breaks apart: Diss burns it away from the
// inside out with a glowing edge, like Kokushibo's moons (souls_moon3d). Output is premultiplied (ONE, 1 - SRC_ALPHA).
uniform float FTime;
uniform float Heat;
uniform float Diss;
uniform float Seed;
uniform float Alpha;

in vec2 vUv;
out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    vec2 q = p - vec2(0.0, -0.72);
    float r = length(q);
    float s = atan(q.x, q.y);
    float body = 1.0 - s * s;
    float rag = (vn(vec2(s * 60.0 + Seed, 1.0)) - 0.5) * 0.05 + (vn(vec2(s * 170.0 + Seed, 3.0)) - 0.5) * 0.025;
    float Ro = 1.28 + 0.025 * sin(s * 9.0 + Seed) + rag * pow(max(body, 0.0), 0.5);
    float w = 0.3 * pow(max(body, 0.0), 0.85);
    float strand = pow(max(0.0, sin(s * 13.0 + Seed * 3.0 + vn(vec2(s * 6.0, FTime * 1.5)) * 2.5)), 2.5);
    float Ri = Ro - w - strand * 0.32 * pow(max(body, 0.0), 1.3) * (0.6 + 0.4 * vn(vec2(s * 9.0, FTime * 3.0)));
    float sd = min(Ro - r, r - Ri);
    if (abs(s) > 1.0) {
        sd = min(sd, -(abs(s) - 1.0) * r);
    }
    // the moons' dissolve: a noise front burning it away with a glowing edge
    float nd = clamp((vn(p * 4.5 + Seed) * 0.6 + vn(p * 9.0 + Seed * 1.7) * 0.3 + vn(p * 18.0 + Seed * 0.3) * 0.1) * 1.8 - 0.4, 0.0, 1.0);
    float dz = mix(-0.2, 1.05, Diss);
    float keep = smoothstep(dz, dz + 0.025, nd);
    float ember = smoothstep(dz - 0.02, dz + 0.02, nd) * (1.0 - smoothstep(dz + 0.03, dz + 0.1, nd)) * step(0.001, Diss) * step(0.0, sd);
    float inside = step(0.0, sd) * keep;
    float edge = inside * (1.0 - smoothstep(0.0, 0.035, sd));
    vec3 bodyC = mix(vec3(0.02, 0.01, 0.02), vec3(1.0), Heat);
    float sc = vn(vec2(s * 5.0 + Seed, (Ro - r) / max(w, 0.02) * 3.5));
    float scratch = inside * step(0.8, sc) * step(0.2, body) * (1.0 - Heat);
    vec3 col = mix(bodyC, vec3(1.0), max(edge, scratch));
    float a = inside;
    // white halo outside: big while hot, thin afterwards
    float ou = max(-sd, 0.0);
    float halo = exp(-ou * mix(20.0, 6.0, Heat)) * mix(0.35, 0.85, Heat) * step(abs(s), 1.15);
    col = a > 0.0 ? col : vec3(1.0);
    a = max(a, halo * (1.0 - Diss));
    float e = clamp(ember * 1.4, 0.0, 1.0);
    col = mix(col, vec3(1.0), e);
    a = max(a, e) * Alpha;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(col * a, a);
}
