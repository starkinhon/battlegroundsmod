#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform sampler2D Sampler0;
uniform float T;
uniform float Yaw;
uniform float A;
uniform float Quake;
uniform float Texel;
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

vec2 face(vec3 d, vec3 f, vec3 r, vec3 u, float k) {
    float z = dot(d, f);
    vec2 uv = vec2(dot(d, r), dot(d, u)) / z * 0.5 + 0.5;
    uv = clamp(uv, vec2(Texel), vec2(1.0 - Texel));
    return vec2((mod(k, 3.0) + uv.x) / 3.0, (floor(k / 3.0) + uv.y) / 2.0);
}

vec3 pano(vec3 d) {
    vec3 a = abs(d);
    vec2 uv;
    if (a.y >= a.x && a.y >= a.z) {
        uv = d.y > 0.0 ? face(d, vec3(0.0, 1.0, 0.0), vec3(-1.0, 0.0, 0.0), vec3(0.0, 0.0, -1.0), 4.0)
                       : face(d, vec3(0.0, -1.0, 0.0), vec3(-1.0, 0.0, 0.0), vec3(0.0, 0.0, 1.0), 5.0);
    } else if (a.z >= a.x) {
        uv = d.z > 0.0 ? face(d, vec3(0.0, 0.0, 1.0), vec3(-1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), 0.0)
                       : face(d, vec3(0.0, 0.0, -1.0), vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), 2.0);
    } else {
        uv = d.x < 0.0 ? face(d, vec3(-1.0, 0.0, 0.0), vec3(0.0, 0.0, -1.0), vec3(0.0, 1.0, 0.0), 1.0)
                       : face(d, vec3(1.0, 0.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), 3.0);
    }
    return texture(Sampler0, uv).rgb;
}

void main() {
    vec3 v = normalize(vW - CamL);
    if (v.y > -0.002) discard;
    // where the view ray meets the water, for the ripples (the water is still, only a slow breath and the cuts' shivers)
    vec2 hit = CamL.xz + v.xz * (CamL.y / max(-v.y, 0.002));
    float b1 = n3(vec3(hit * 0.21, T * 0.05)) - 0.5;
    float b2 = n3(vec3(hit * 0.83 + 7.0, T * 0.11)) - 0.5;
    float qk = (n3(vec3(hit * 2.3, T * 12.0)) - 0.5) * Quake;
    vec3 r = vec3(v.x + b1 * 0.012 + b2 * 0.006 + qk * 0.03, -v.y, v.z + b2 * 0.012 - b1 * 0.006 + qk * 0.03);
    r = normalize(r);
    float c = cos(Yaw), s = sin(Yaw);
    vec3 w = vec3(r.x * c - r.z * s, r.y, r.x * s + r.z * c);
    vec3 col = pano(w);
    // the domain stains what it mirrors: darker, pulled toward its red, deeper away from the horizon
    float lum = dot(col, vec3(0.3, 0.55, 0.15));
    // (mostly red: at 0.6 a blue day sky came through grey-blue under the red one, test 2026-11-09)
    col = mix(col, vec3(lum) * vec3(1.0, 0.3, 0.3), 0.85) * 0.4;
    col *= mix(1.0, 0.55, smoothstep(0.0, 0.6, -v.y));
    fragColor = vec4(col * A, 1.0);
}
