#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform sampler2D Sampler0;
uniform float T;
uniform float Lit;
uniform float Rim;
uniform float Glow;
uniform float Amb;
uniform float Mirror;
uniform vec3 CamL;

in vec2 vUv;
in vec3 vW;
in vec3 vN;
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
    vec4 tex = texture(Sampler0, vUv);
    if (tex.a < 0.1) discard;
    if (Mirror > 0.5 && vW.y > 0.0) discard;
    vec3 n = normalize(vN);
    vec3 v = normalize(CamL - vW);
    float rim = pow(1.0 - abs(dot(n, v)), 3.0);
    // in the mirror: lit as the shrine above it is (its own normal and place)
    vec3 nl = Mirror > 0.5 ? vec3(n.x, -n.y, n.z) : n;
    vec3 wp = vec3(vW.x, abs(vW.y), vW.z);
    float spark = smoothstep(0.62, 0.8, n3(wp * 0.8 + vec3(0.0, T * 0.05, 0.0)));
    vec3 sil = vec3(0.004, 0.001, 0.002) + rim * vec3(0.8, 0.03, 0.06) * Rim * (0.12 + 0.9 * spark);
    // the domain's lights: the red sky / dark ground hemisphere, the warm key from the front and above, the cyan
    // light in the water behind it
    vec3 hemi = mix(vec3(0.125, 0.016, 0.03), vec3(1.0, 0.69, 0.69), nl.y * 0.5 + 0.5) * Amb;
    vec3 key = vec3(1.0, 0.42, 0.23) * max(dot(nl, normalize(vec3(0.0, 14.0, 24.0))), 0.0) * 0.9 * Lit;
    vec3 lc = vec3(0.0, 4.0, -9.0) - wp;
    float ld = length(lc);
    vec3 cyan = vec3(0.25, 1.0, 0.91) * max(dot(nl, lc / ld), 0.0) * 2.2 * Lit * pow(max(1.0 - ld / 26.0, 0.0), 1.6);
    vec3 lit = tex.rgb * (hemi + key + cyan) + tex.rgb * max(Glow, 0.0) * 0.55 * vec3(1.0, 0.55, 0.4);
    vec3 col = mix(sil, lit, Lit);
    if (Glow < 0.0) col = vec3(0.0);
    fragColor = vec4(col, 1.0);
}
