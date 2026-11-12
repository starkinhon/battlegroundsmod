#version 150

// Gaara's real sand (client/fx/GaaraSand), the approved page's SAND_V (forja/gaara-kit/gaara.js v1) line by line: the
// shell is pushed along its normal by 3D noise flowing over time and the normal is found again from two nearby points.
// Position is camera-relative in blocks; the noise lives in the page's world units (0.9 blocks), so CamOff (the camera
// position, wrapped) puts every vertex back in the world. Grain = 1: the loose grains, plain lit pieces in their own color.
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CamOff;
uniform float Time;
uniform float Lump;
uniform float Flow;
uniform float Scale;
uniform float Grain;

out vec3 vW;
out vec3 vR;
out vec3 vN;
out vec2 vUv;
out vec4 vCol;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

float lump(vec3 w) { return fbm3(w * 1.5 * Scale + vec3(0., -Time * Flow, Time * Flow * 0.3)); }

void main() {
    vUv = UV0;
    vCol = Color;
    vec3 n = normalize(Normal);
    vec3 w = (Position + CamOff) / 0.9;
    if (Grain > 0.5) {
        vW = w; vN = n; vR = Position;
        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.);
        return;
    }
    vec3 t = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0., 1., 0.) : vec3(1., 0., 0.))), b = cross(n, t);
    float e = 0.07;
    vec3 P = w + n * lump(w) * Lump, P1 = w + t * e + n * lump(w + t * e) * Lump, P2 = w + b * e + n * lump(w + b * e) * Lump;
    vec3 nn = cross(P1 - P, P2 - P);
    nn = length(nn) > 1e-6 ? normalize(nn) : n;
    if (dot(nn, n) < 0.) nn = -nn;
    vW = P;
    vN = nn;
    vR = P * 0.9 - CamOff;
    gl_Position = ProjMat * ModelViewMat * vec4(vR, 1.);
}
