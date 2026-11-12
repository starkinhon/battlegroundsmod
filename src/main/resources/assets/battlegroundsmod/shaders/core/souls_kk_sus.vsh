#version 150

// Kakashi's Perfect Susanoo (client/fx/KakashiSusanoo), the approved page's shaders (forja/kakashi-kit/kakashi.js v13:
// SUS_V, SUS_OUT_V, AURA_V, KSH) carried over line by line. Position is the baked model at rest in the Susanoo's own
// units (models/mesh/kakashi_susanoo.bin); Bone poses the arm it hangs from; ObjScale (1.45, the page's SUS_SCALE) turns it
// into the page's world units, where every noise and height is measured; Model places that in the world (camera-relative,
// turned to face, 0.9 blocks per unit). Pass: 0 the solid black/red bits, 1 the wing feathers, 2 the ink outline (back
// faces pushed out), 3 the body, 4 the additive chakra flames. Mat 9 is the Kamui shuriken (its own Model, ObjScale 1).
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform mat4 Bone;
uniform mat4 MeshInv;
uniform float Pass;
uniform float OutW;
uniform float Time;
uniform float ObjScale;
uniform float Flame;
uniform float Push;

out vec3 vN;
out vec3 vV;
out vec3 vW;
out vec3 vO;
out vec2 vUv;
out float vTg;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

void main() {
    vUv = UV0;
    vec3 n = normalize(mat3(Bone) * Normal);
    vO = (MeshInv * vec4(Position, 1.)).xyz;
    vec3 w = (Bone * vec4(Position, 1.)).xyz * ObjScale;
    int pass = int(Pass + 0.5);
    vTg = 0.;
    if (pass == 2) {
        w += n * OutW * ObjScale;
    } else if (pass == 4) {
        vec3 q = w * vec3(0.42, 0.26, 0.42) + vec3(0., -Time * 1.25, 0.);
        float tg = smoothstep(0.05, 0.75, fbm3(q + vec3(fbm3(q * 0.7 + 4.1), 0., fbm3(q * 0.7 + 9.3)) * 0.8) * 0.5 + 0.5);
        w += n * (Push + 0.75 * tg) + vec3(0., 0.9 * tg * tg, 0.);
        vTg = tg;
    } else if (Flame > 0.) {
        float up = clamp(vO.y, 0., 1.);
        w += vec3(gn(w * 1.4 + vec3(0., -Time * 3., 0.)), 0.5, gn(w * 1.4 + 7. + vec3(0., -Time * 3., 0.))) * 0.22 * up * Flame;
    }
    vW = w;
    mat4 mv = ModelViewMat * Model;
    vN = normalize(mat3(mv) * n);
    vec4 p = mv * vec4(w, 1.);
    vV = -p.xyz;
    gl_Position = ProjMat * p;
}
