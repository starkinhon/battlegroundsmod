#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float K;
uniform float Asp;
uniform float Six;
uniform float Red;

in vec2 vUv;
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

float eye(vec2 p, vec2 a, vec2 b, float w, float hp, float bend, float sd, float st) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    vec2 nr = normalize(vec2(-ba.y, ba.x));
    vec2 q = pa - ba * h - nr * bend * sin(h * 3.1416);
    float prof = h < hp ? pow(h / hp, 0.5) : pow((1.0 - h) / (1.0 - hp), 0.7);
    float wob = 1.0 + 0.55 * (fb3(vec3(h * 4.0 + sd, st * 2.1, sd)) - 0.5);
    return length(q) - w * 1.5 * prof * wob + (fb3(vec3(p * 9.0 + sd, st * 1.3)) - 0.5) * 0.06;
}

vec3 eyeCol(vec2 p, float d, float sd, float st, inout float a) {
    float core = 1.0 - smoothstep(-0.05, 0.035, d);
    float glow = exp(-max(d, 0.0) * 14.0);
    float blot = smoothstep(0.52, 0.62, fb3(vec3(p * 11.0 + sd, st * 1.9)) + 0.1 * smoothstep(-0.03, -0.09, d));
    float hot = fb3(vec3(p * 5.0 - sd, st * 2.6));
    vec3 c = mix(vec3(1.0, 0.38, 0.5), vec3(1.0, 0.9, 0.92), smoothstep(0.35, 0.72, hot) - smoothstep(-0.06, 0.0, d) * 0.5);
    c = mix(c, vec3(0.28, 0.0, 0.05), blot * 0.92 * smoothstep(-0.02, -0.06, d));
    a = max(a, core);
    return c * core * 1.25 + vec3(0.8, 0.05, 0.16) * glow * 0.5 * (1.0 - core);
}

void main() {
    vec2 p = (vUv - 0.5) * 2.0;
    p.x *= Asp;
    float k = K, st = floor(k * 0.6) / 0.6;
    vec3 col = vec3(0.0);
    float al = 0.0;
    vec2 c0 = vec2(0.08, 0.45) + vec2(0.04 * sin(k * 0.3), 0.03 * k / 10.0);
    float burst = smoothstep(7.0, 10.5, k);
    float rb = mix(0.6 * smoothstep(0.0, 6.5, k), 3.4, burst * burst);
    vec2 wq = p + 0.32 * rb * vec2(fb3(vec3(p * 1.8, k * 0.12)) - 0.5, fb3(vec3(p * 1.8 + 7.0, k * 0.12)) - 0.5);
    float d = length((wq - c0) * vec2(1.0, 1.25)) - rb;
    float smoke = fb3(vec3(p * 3.0, k * 0.2));
    float mass = (1.0 - smoothstep(-0.2 - 0.12 * smoke, 0.12 + 0.1 * smoke, d)) * step(0.01, k);
    float dim = 0.35 * smoothstep(0.0, 7.0, k);
    vec3 navy = vec3(0.010, 0.012, 0.03) * (0.7 + 0.6 * fb3(vec3(p * 1.2, k * 0.05)));
    float rim = exp(-pow((d - 0.06) / 0.14, 2.0));
    float low = smoothstep(c0.y + 0.1, c0.y - 0.35, p.y);
    float tongue = pow(smoothstep(0.4, 0.8, fb3(vec3(p.x * 17.0, p.y * 1.4 - k * 0.55, st * 0.4))), 1.8)
            * smoothstep(0.2, 0.7, n3(vec3(p.x * 6.0, st * 0.7, 1.0))) * rim * low * smoothstep(2.5, 4.5, k) * (1.0 - burst);
    float ang = (fb3(vec3(p * 0.7, 3.0)) - 0.5) * 4.0;
    vec2 r = rot2(ang) * (p + 0.25 * vec2(fb3(vec3(p * 1.4, k * 0.08)), fb3(vec3(p * 1.4 + 5.0, k * 0.08))));
    float wisp = pow(smoothstep(0.46, 0.76, fb3(vec3(r.x * 2.6, r.y * 13.0 + k * 0.18, st * 0.37))), 1.3)
            * smoothstep(0.5, 0.68, fb3(vec3(p * 1.3 + st * 0.21, st * 0.13)));
    wisp *= smoothstep(8.0, 10.0, k) * (1.0 - smoothstep(14.0, 21.0, k));
    float speck = smoothstep(0.975, 0.995, n3(vec3(p * 9.0, st * 3.0))) * smoothstep(14.0, 17.0, k) * (1.0 - smoothstep(19.0, 22.0, k));
    al = max(mass, dim);
    col = navy * mass;
    col += vec3(1.0, 0.07, 0.04) * (tongue * 2.4 + wisp * 1.5 + speck);
    if (Six > 0.0) {
        float k2 = k - 19.0;
        float ap = 1.0 - pow(1.0 - clamp(k2 / 8.0, 0.0, 1.0), 3.0);
        float ru = smoothstep(6.0, 15.0, k2);
        float z = mix(0.16, 1.0, ap) * (1.0 + pow(ru, 2.0) * 4.0);
        vec2 q = rot2(-0.35 * ru * ru) * (p - vec2(0.05, 0.12) * ru) / z;
        q += 0.01 * vec2(sin(k * 2.1), cos(k * 1.6));
        float ea = 0.0;
        float d1 = eye(q, vec2(-0.80, 1.05), vec2(-0.30, 0.43), 0.07, 0.5, 0.03, 2.0, st);
        float d2 = eye(q, vec2(0.58, 1.12), vec2(0.22, 0.35), 0.11, 0.35, -0.05, 3.0, st);
        float d3 = eye(q, vec2(-0.86, 0.6), vec2(-0.27, -0.16), 0.075, 0.55, 0.05, 5.0, st);
        float d4 = eye(q, vec2(0.8, 0.6), vec2(0.24, -0.11), 0.08, 0.45, -0.05, 6.0, st);
        float d5 = eye(q, vec2(-0.36, -0.45), vec2(-0.03, -0.8), 0.06, 0.7, 0.02, 7.0, st);
        float d6 = eye(q, vec2(0.33, -0.44), vec2(0.0, -0.79), 0.065, 0.65, -0.03, 8.0, st);
        float dd = min(min(min(d1, d2), min(d3, d4)), min(d5, d6));
        vec3 e = eyeCol(q * 0.9, dd, 3.0, st, ea);
        float sm2 = pow(smoothstep(0.55, 0.85, fb3(vec3((p.x + 1.3) * 2.5, (p.y + 0.35 + p.x * 0.4) * 6.0, st * 0.3))), 1.5)
                * exp(-length((p - vec2(-1.35, -0.3)) * vec2(1.0, 2.2)) * 2.0) * (1.0 - ru);
        col = navy * (1.0 - ea * Six) + e * Six + vec3(1.0, 0.06, 0.04) * sm2 * 1.4 * Six;
        al = 1.0;
        col = mix(col, vec3(1.0, 0.82, 0.86), smoothstep(0.85, 1.0, ru) * 0.85);
    }
    col *= Red;
    al *= Red;
    fragColor = vec4(col, al);
}
