#version 150

// Saitama's serious moves (client/fx/OpmFx), the approved page's shaders (forja/saitama-soco-serio/scene.js) carried
// over line by line. The mesh comes in as a unit shape and is bent here exactly as there; Model places it (relative to
// the camera, in the move's own frame: Saitama's feet at the origin, looking down +Z, y up). Kind:
// 0 the air tube of the Soco Serio (Halo/Core pick the halo shell and the inner core), 1 the Mach disc at its front,
// 2 the steam cone at the fist, 3 the dust walls along the trench, 4 the dust ring on the ground, 5 the sneeze fan,
// 6 the clouds pushed out round the hole, 7 the hole's mask (depth only, hides the game's clouds), 8 a small hit flash,
// 9 a ghost (the arms of the Socos Consecutivos), 10 the star where the target vanishes on the horizon.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform float Kind;
uniform float Len;
uniform float T;
uniform float TP;
uniform float Seed;
uniform float Scale;

out vec3 vN;
out vec3 vV;
out vec3 vP;
out vec2 vUv;
out float vZ;
out float vK;
out float vLife;
out float vA;
out float vSide;
out float vH;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

// the punch's shape, the same numbers as OnePunch.java
float ycG(float z) { return 1.4 + 0.05 * z; }
float radG(float z) { return max(0.4, min(1.0 + 0.13 * z, 20.)); }
float passG(float z) { return 1.40 * (1. - pow(1. - clamp(z / 200.0, 0., 1.), 0.4)); }
float groundWG(float z) { float r = radG(z), y = ycG(z); return max(0.75 * r, sqrt(max(r * r - y * y, 0.))) * 1.2; }

void main() {
    int k = int(Kind + 0.5);
    vec3 P = Position;
    vec3 Nl = vec3(0., 1., 0.);
    vUv = vec2(0.);
    vZ = 0.; vK = 0.; vLife = 0.; vA = 0.; vSide = 0.; vH = 0.;
    if (k == 0) {
        // the air tube: a cone that starts at the fist, rises 5 cm a block and closes in a round nose at the front
        float a = atan(Position.y, Position.x), z = Position.z * Len;
        float r = radG(z), y0 = ycG(z);
        float nose = min(r * 1.5, Len * 0.5);
        float kk = clamp((z - (Len - nose)) / max(nose, 1e-3), 0., 1.);
        float bul = 1. + 0.24 * exp(-pow((z - (Len - nose * 1.15)) / (nose * 0.7 + 0.01), 2.));
        r *= bul * sqrt(max(1. - kk * kk, 0.));
        float n = gn(vec3(cos(a) * 1.5, sin(a) * 1.5, z * 0.06 - T * 2.) + Seed);
        r *= (1. + 0.14 * n) * Scale;
        P = vec3(cos(a) * r, y0 + sin(a) * r, z);
        Nl = normalize(vec3(cos(a), sin(a), kk * 1.6 - 0.11));
        vZ = z; vK = kk; vA = a;
        vLife = clamp((TP - passG(z)) / 1.05, 0., 1.);
    } else if (k == 3) {
        // the dust walls: half a shell on each side of the trench, rising as the front passes
        float a = atan(Position.y, Position.x), z = Position.z * Len;
        float age = TP - passG(z);
        vLife = clamp(age / 5.5, 0., 1.);
        float grow = 1. - pow(1. - clamp(age / 1.6, 0., 1.), 3.);
        float w = groundWG(z) * (1.25 + 0.45 * grow), h = (2. + min(z * 0.09, 9.)) * (0.3 + 1.3 * grow) * smoothstep(2., 10., z);
        float n = gn(vec3(cos(a) * 2., sin(a) * 2., z * 0.05 + Seed));
        P = vec3(cos(a) * w * (1. + 0.18 * n), sin(a) * h * (1. + 0.25 * n) + vLife * 1.8, z);
        vSide = abs(cos(a)); vH = sin(a);
        Nl = normalize(vec3(cos(a) / w, sin(a) / h, 0.));
    } else if (k == 5) {
        // the sneeze: wider and shorter than the punch, flattened so it opens more to the sides
        float a = atan(Position.y, Position.x), z = Position.z * Len;
        float r = 0.25 + z * 0.62, y0 = 1.75 + z * 0.1;
        float nose = min(r * 0.9, Len * 0.6);
        float kk = clamp((z - (Len - nose)) / max(nose, 1e-3), 0., 1.);
        r *= sqrt(max(1. - kk * kk, 0.)) * (1. + 0.1 * exp(-pow((z - (Len - nose * 1.2)) / (nose * 0.6 + 0.01), 2.)));
        vec2 cs = vec2(cos(a), sin(a));
        r *= 1. + 0.32 * fbm3(vec3(cs * 2.2, z * 0.05 - T * 1.6) + Seed);
        P = vec3(cs.x * r, y0 + cs.y * r * 0.7, z);
        Nl = normalize(vec3(cs.x, cs.y / 0.7, kk * 1.4 - 0.4));
        vZ = z; vK = kk; vA = a;
        vLife = clamp((TP - 1.10 * (1. - sqrt(max(1. - z / 150.0, 0.)))) / 1.3, 0., 1.);
    } else if (k == 2) {
        // the steam cone (THREE.ConeGeometry(1, 1) laid out the same way: apex up at y = 0.5)
        vUv = vec2(0., Position.y + 0.5);
        vec2 d = normalize(Position.xz + vec2(1e-6, 0.));
        Nl = normalize(vec3(d.x, 1., d.y));
    } else if (k == 8 || k == 9) {
        // a ball (normal = position) or a box (normal = its face)
        if (k == 8) {
            Nl = normalize(Position);
        } else {
            vec3 q = abs(Position);
            Nl = q.x > q.y && q.x > q.z ? vec3(sign(Position.x), 0., 0.) : q.y > q.z ? vec3(0., sign(Position.y), 0.) : vec3(0., 0., sign(Position.z));
        }
    } else {
        // flat squares from -1 to 1 (THREE.PlaneGeometry(2, 2))
        vUv = Position.xy * 0.5 + 0.5;
    }
    vec4 W = Model * vec4(P, 1.);
    vec4 mv = ModelViewMat * W;
    mat3 nm = transpose(inverse(mat3(ModelViewMat * Model)));
    vN = nm * Nl;
    vV = -mv.xyz;
    vP = P;
    gl_Position = ProjMat * mv;
}
