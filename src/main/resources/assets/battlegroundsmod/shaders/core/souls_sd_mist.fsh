#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Red;
uniform float Low;
uniform float Wind;
uniform float R;
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
    vec3 ro = CamL, rd = normalize(vW - ro);
    float b = dot(ro, rd), c = dot(ro, ro) - R * R, h = b * b - c;
    if (h < 0.0) discard;
    h = sqrt(h);
    float t0 = max(0.0, -b - h), t1 = -b + h;
    // The mist stands on the water: a ray looking down stops at the surface. Without this the volume under the floor
    // painted the water the same red as the sky and hid the shrine's reflection (test 2026-11-09).
    if (rd.y < 0.0) t1 = min(t1, max(0.0, -ro.y / rd.y));
    if (t1 <= t0) discard;
    // Without the red, the low fog and the wind live under y 5.5 (both are zero above it): march only that slab, and
    // with steps of about a block, so a short ray takes few steps. Same look, a fraction of the cost (Stark 2026-11-09:
    // FPS dropping inside the domain).
    if (Red <= 0.0) {
        const float TOP = 5.5;
        if (ro.y > TOP) {
            if (rd.y >= 0.0) discard;
            t0 = max(t0, (ro.y - TOP) / -rd.y);
        } else if (rd.y > 0.0) {
            t1 = min(t1, (TOP - ro.y) / rd.y);
        }
        if (t1 <= t0) discard;
    }
    // No per-pixel jitter: in the game it stays on the screen while the world moves, a grain that shimmers (Stark 06:38).
    int N = int(clamp(ceil((t1 - t0) / 1.2), 12.0, 40.0));
    float dt = (t1 - t0) / float(N);
    float t = t0 + dt * 0.5;
    vec3 col = vec3(0.0);
    float A = 0.0, S = 0.0;
    for (int i = 0; i < N; i++) {
        vec3 p = ro + rd * t;
        float r = length(p.xz);
        vec3 q = p;
        q.xz = rot2(T * 0.03 + 5.0 / (1.0 + r * 0.18)) * q.xz;
        if (Red > 0.0) {
            float f = fb3(q * 0.075 + vec3(0.0, T * 0.012, 0.0));
            float f2 = fb3(q * 0.22 + vec3(f * 2.0, -T * 0.02, 0.0));
            float dR = smoothstep(0.42, 0.78, f * 0.8 + f2 * 0.35) * Red * 0.45 * smoothstep(1.5, 6.0, length(p - vec3(0.0, 1.2, 0.0))) * (0.15 + 0.85 * smoothstep(4.0, 16.0, length(p - ro)));
            vec3 cr = mix(vec3(0.09, 0.0, 0.005), vec3(0.85, 0.05, 0.08), smoothstep(0.55, 0.82, f2 + f * 0.3));
            float a = 1.0 - exp(-dR * dt);
            col += (1.0 - A) * a * cr;
            A += (1.0 - A) * a;
        }
        if (Low > 0.0) {
            float f = fb3(vec3(p.x * 0.12 + T * 0.004, p.y * 0.5, p.z * 0.12));
            float dL = Low * 0.06 * smoothstep(1.6, 0.0, p.y) * smoothstep(0.35, 0.7, f);
            float a = 1.0 - exp(-dL * dt);
            col += (1.0 - A) * a * vec3(0.15, 0.07, 0.08) * (0.6 + 0.6 * f); // red-dark, not grey (test 2026-11-09)
            A += (1.0 - A) * a;
        }
        if (Wind > 0.0) {
            vec3 w = p;
            w.xz = rot2(T * 0.22 + 2.6 / (1.0 + r * 0.09)) * p.xz;
            float g = fb3(vec3(w.x * 0.09, T * 0.05, w.z * 0.09));
            float f = fb3(vec3(w.x * 0.2, p.y * 0.65 - T * 0.04, w.z * 0.2) + vec3(g * 1.6));
            float dW = Wind * 0.55 * smoothstep(4.2, 0.0, p.y) * smoothstep(0.36, 0.74, f) * (0.5 + 0.9 * g);
            float a = 1.0 - exp(-dW * dt);
            col += (1.0 - A) * a * mix(vec3(0.16, 0.02, 0.03), vec3(0.55, 0.1, 0.12), smoothstep(0.5, 0.85, f + g * 0.2));
            A += (1.0 - A) * a;
            vec2 tq = vec2(atan(w.z, w.x) * r * 0.5, r);
            float st = fb3(vec3(tq.x * 0.06, tq.y * 1.9 + g * 3.0, p.y * 1.3 + T * 0.03));
            float streak = smoothstep(0.69, 0.8, st) * smoothstep(5.5, 0.6, p.y) * smoothstep(0.45, 0.7, g) * Wind;
            // the streaks a grazing ray gathers are capped: crossing a gust along the floor they added up into a pale pink
            // wash over the horizon and the water for a few seconds (test 2026-11-09)
            float a2 = min(1.0 - exp(-streak * 3.0 * dt), max(0.0, 0.18 - S));
            S += a2;
            col += (1.0 - A) * a2 * vec3(0.95, 0.55, 0.55);
            A += (1.0 - A) * a2 * 0.6;
        }
        if (A > 0.97) break;
        t += dt;
    }
    // premultiplied (ONE, ONE_MINUS_SRC_ALPHA)
    fragColor = vec4(col, A);
}
