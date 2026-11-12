#version 150

// Gaara's sand, the page's SAND_F: it rises from the ground (RiseY, camera-relative blocks), runs along its path (Prog on
// uv.x), crumbles (Dis), three bands of light, dry and damp patches, streaks running down, grains per world cell, and it
// stays under the bloom's threshold (x0.7): real sand doesn't shine.
in vec3 vW;
in vec3 vR;
in vec3 vN;
in vec2 vUv;
in vec4 vCol;

uniform float Time;
uniform float Flow;
uniform float Dis;
uniform float RiseY;
uniform float Prog;
uniform float Grain;
uniform vec3 Base;
uniform vec3 Shade;
uniform vec3 Deep;
uniform vec3 Light;

out vec4 fragColor;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

float h1(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }

void main() {
    vec3 L = normalize(vec3(-0.45, 0.8, 0.35));
    if (Grain > 0.5) {
        float l = max(0., dot(normalize(vN), L)) * 0.7 + 0.45;
        fragColor = vec4(vCol.rgb * l * 0.7, 1.);
        return;
    }
    if (vR.y > RiseY + gn(vW * 2.6) * 0.27) discard;
    if (vUv.x > Prog + gn(vW * 2.2) * 0.06) discard;
    float dn = fbm3(vW * 2.1 + 5.3) * 0.5 + 0.5;
    if (dn < Dis) discard;
    vec3 N = normalize(vN);
    if (!gl_FrontFacing) N = -N;
    float l = dot(N, L) * 0.5 + 0.5;
    vec3 col = l > 0.64 ? Base : l > 0.4 ? Shade : Deep;
    float m = fbm3(vW * 0.8 + 3.1); col *= 0.9 + 0.2 * m;
    float s = gn(vec3(vW.x * 5., vW.y * 0.9 + Time * Flow * 2.2, vW.z * 5.));
    col = mix(col, Deep, smoothstep(0.5, 0.85, s) * 0.32);
    float g = h1(floor(vW * 44.)); col = mix(col, Light, step(0.975, g) * 0.3); col *= 1. - step(g, 0.05) * 0.22;
    col = mix(col, Deep * 0.75, smoothstep(Dis + 0.07, Dis, dn) * step(0.001, Dis));
    fragColor = vec4(col * 0.7, 1.);
}
