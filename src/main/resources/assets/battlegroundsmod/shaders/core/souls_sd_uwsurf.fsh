#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Fade;
uniform float Depth;
uniform float Ring;
uniform vec2 Hit;
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
    float cosT = d.y;
    vec2 p = vW.xz;
    float w = fb3(vec3(p * 1.4, T * 1.8)), w2 = fb3(vec3(p * 3.1 + 7.0, T * 2.6));
    float win = smoothstep(0.62, 0.70, cosT + 0.06 * (w - 0.5));
    vec3 sky = vec3(0.42, 0.05, 0.04) * (0.7 + 0.6 * w) + vec3(0.8, 0.15, 0.1) * pow(w2, 6.0) * 1.5;
    vec3 tir = vec3(0.07, 0.006, 0.005) * (0.6 + 0.8 * w) + vec3(0.28, 0.03, 0.02) * pow(w2, 5.0);
    vec3 c = mix(tir, sky, win);
    float rr = length(p - Hit);
    float ring = Ring > 0.0 ? exp(-pow((rr - Ring) / 0.10, 2.0)) * smoothstep(0.0, 0.5, Ring) : 0.0;
    c += vec3(0.7, 0.95, 1.0) * ring * 0.45 * smoothstep(0.3, 2.5, -Depth + 0.3);
    float dist = length(vW - CamL);
    c = mix(vec3(0.12, 0.01, 0.008), c, exp(-dist * 0.07));
    c *= exp(max(Depth, -2.0) * 0.32);
    fragColor = vec4(c * Fade, Fade);
}
