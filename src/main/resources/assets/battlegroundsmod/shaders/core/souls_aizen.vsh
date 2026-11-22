#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
// Aizen's kit (forja/aizen-kit/aizen-kit.html, approved v2.3): the page's vertex shaders, one per Kind, carried over line by
// line. Positions arrive relative to the camera (Model places the piece), so the view vector is just -world.
// UV0 = the page's aU / uv (tubes: x along 0..1, y round 0..1); Normal = the geometry's normal; Color unused.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform float Kind;
uniform float Time;
uniform float Seed;
uniform vec4 A;
uniform vec4 B;

out vec3 vN;
out vec3 vV;
out vec3 vO;
out vec3 vQ;
out vec2 vU;
out float vH;
out float vDisp;

#define K_EN 0.
#define K_DRAG 1.
#define K_DHEAD 2.
#define K_SOKA 3.
#define K_WALL 4.
#define K_CRES 5.
#define K_COC 6.
#define K_ORB 7.
#define K_OHALO 8.
#define K_ORBE 9.
#define K_EHALO 10.
#define K_SPIKE 11.
#define K_CORE 12.
#define K_CRACK 13.
#define K_RA 14.
#define K_PDOME 15.
#define K_WING 16.
#define K_LIT 17.

bool isK(float k) { return abs(Kind - k) < 0.5; }

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

void main() {
    vec3 P = Position;
    vec3 n = Normal;
    vO = Position;
    vU = UV0;
    vQ = vec3(0.);
    vH = 0.;
    vDisp = 0.;
    if (isK(K_EN) || isK(K_DRAG)) {
        // EN_V: the back shell inflates into the halo (A.w = uInflate)
        P = Position + Normal * A.w;
    } else if (isK(K_SOKA)) {
        // SOKA_V: A = (uLen, uFlow, uHead, uTail), B.x = uDiss; the open cylinder runs along +y (0..1)
        float uLen = A.x, uFlow = A.y, uDiss = B.x;
        float along = Position.y * uLen; vec2 d = normalize(Position.xz + 1e-5);
        vec3 q = vec3(d.x * 1.5, d.y * 1.5, along * 1.1 - uFlow);
        float tg = smoothstep(0.3, 0.95, fbm3(q) * 0.5 + 0.5);
        float r = (0.08 + along * 0.12) * (1. + 0.7 * tg) * (1. + 0.08 * sin(along * 4. - Time * 14.)) * (1. + 0.9 * uDiss);
        P = vec3(d.x * r, along, d.y * r); P.z += uDiss * (0.5 + along * 0.09) * (0.7 + 0.6 * tg);
        P.x += gn(vec3(along * 0.5 - uFlow * 0.3, 1., 2.)) * 0.18 * along * 0.3; P.z += gn(vec3(along * 0.5 - uFlow * 0.3, 5., 2.)) * 0.18 * along * 0.3;
        vU = vec2(along, 0.); vDisp = tg; vQ = q; n = vec3(d.x, 0., d.y);
    } else if (isK(K_COC)) {
        // COC_V: A = (uGrow, uCrack, uPulse)
        vec3 n0 = normalize(Position); vO = n0; vH = n0.y * 0.5 + 0.5;
        float bump = fbm3(n0 * 3.1 + 4.) * 0.07 + gn(n0 * 9.) * 0.015;
        P = vec3(n0.x * 0.78, n0.y * 1.28 + 0.06, n0.z * 0.7) * (1. + bump + A.z * 0.03 * sin(Time * 18. + n0.y * 6.));
        n = n0;
    } else if (isK(K_RA)) {
        // RA_V: A.x = uPow
        vec3 p = Position; float h = p.y * 0.5 + 0.5; vH = h;
        float nn = fbm3(vec3(p.x * 2., p.y * 1.5 - Time * 2.2, p.z * 2.)) * 0.5 + 0.5;
        float sq = (0.75 + 0.35 * nn) * (1. - 0.5 * h * h);
        P = vec3(p.x * sq, p.y + max(0., p.y) * nn * 1.4 * A.x, p.z * sq);
    }
    vec4 w = Model * vec4(P, 1.);
    mat3 nm = transpose(inverse(mat3(Model)));
    vN = nm * n;
    if (isK(K_WING)) {
        vN = normalize(mat3(Model) * vec3(0., 0., 1.));
    }
    vV = -w.xyz;
    gl_Position = ProjMat * ModelViewMat * w;
}
