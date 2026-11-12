#version 150

// Luffy's moves (client/fx/LuffyFx), the approved page's shaders (forja/luffy-gears: luffy.js, ults.js, lib.js) carried
// over line by line. The mesh comes in as a unit shape (with its normal) and is bent here as there; Model places it
// relative to the camera. Kind: 0 the rubber arm (square, thins as it stretches, swells with air, wobbles like a spring,
// twists for the Rifle, cartoon in G5), 1 an anime air burst (ring of points or a half moon, faces the camera),
// 2 the steam shell of G2/G4, 3 the cloud collar of G5, 4 the fire shell of the fist, 5 the blast dome (fire or smoke),
// 6 the lightning of the Kaminari, 7 a solid piece (fist, balloon, cannonball, joints; P0.x = 1 the King Kong Gun's
// black with red lines), 8 Boundman's haki, 9 his steam, 10 the Conqueror's Haki bolt, 11 its burnt mark on the ground,
// 12 its pressure ring, 13 its shock shell.
in vec3 Position;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform float Kind;
uniform float T;
uniform float Seed;
uniform vec4 P0;
uniform vec4 P1;
uniform vec4 P2;
uniform vec4 P3;

out vec3 vN;
out vec3 vV;
out vec3 vP;
out vec2 vUv;
out float vU;
out float vH;
out float vBack;
out float vD;
out vec3 vO;
out vec3 vL;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float hz(float i) { return fract(sin(i * 91.7 + Seed * 13.1) * 43758.5453) * 2. - 1.; }

void main() {
    int k = int(Kind + 0.5);
    vec3 P = Position;
    vec3 Nl = Normal;
    vUv = vec2(0.); vU = 0.; vH = 0.; vBack = 0.; vD = 0.; vO = normalize(Position + vec3(1e-6));
    vL = mat3(ModelViewMat) * normalize(P1.xyz + vec3(0., 1e-4, 0.));
    if (k == 0) {
        // ARM_V: P0 = (L, R, Infl, Amp), P1 = (Ph, N, Bx, Ba), P2 = (Toon, Tw, Hx, Blur)
        float uL = P0.x, uR = P0.y, uInfl = P0.z, uAmp = P0.w, uPh = P1.x, uN = P1.y, uBx = P1.z, uBa = P1.w, uToon = P2.x, uTw = P2.y, uHx = P2.z;
        float u = Position.z; vec2 cs = Position.xy;
        float thin = clamp(sqrt(0.75 / max(uL, 0.75)), 0.42, 1.);
        float bub = uBa * exp(-pow((u - uBx) / 0.11, 2.));
        float r = uR * mix(thin, 1., smoothstep(0.5, 1., uInfl - 1.)) * uInfl * (1. + bub) * (1. + 0.18 * uToon * sin(u * 18. - uPh * 2.));
        r *= mix(1., 0.82, smoothstep(0.92, 1., u)) * mix(0.9, 1., smoothstep(0., 0.08, u));
        float env = sin(u * 3.14159);
        vec2 wob = vec2(sin(u * 3.14159 * uN - uPh), 0.6 * cos(u * 3.14159 * uN * 0.7 - uPh * 1.3)) * uAmp * env;
        float ang = uTw * u; mat2 rot = mat2(cos(ang), sin(ang), -sin(ang), cos(ang));
        vec2 hel = vec2(cos(ang), sin(ang)) * uHx * env;
        P = vec3(wob + hel + rot * cs * r, u * uL);
        Nl = vec3(rot * Normal.xy, 0.);
        vU = u;
    } else if (k == 6) {
        // BOLT_V: P0 = (L, R, Zig, Ph), P1 = (Bend, Wob, K, Toon)
        float u = Position.z; vec2 cs = Position.xy; float env = sin(u * 3.14159);
        float f = u * P1.z, i = floor(f), t = fract(f);
        vec2 zig = mix(vec2(hz(i), hz(i + 31.)), vec2(hz(i + 1.), hz(i + 32.)), t);
        vec2 off = zig * P0.z * (0.3 + 0.7 * env) + vec2(sin(u * 6.2832 - P0.w), 0.7 * cos(u * 4.7 - P0.w * 1.3)) * P1.y * env + vec2(0., P1.x * env);
        float r = P0.y * (0.55 + 0.45 * env + 0.25 * P1.y * sin(u * 20. - P0.w * 2.));
        P = vec3(off + cs * r, u * P0.x);
        vU = u;
    } else if (k == 2) {
        // STEAM_V: P0.x = Rise
        vec3 n = normalize(Position);
        float up = max(n.y, 0.);
        P.y += up * up * P0.x * (0.6 + 0.8 * (fbm3(n * 2.3 + vec3(0., -T * 0.8, 0.)) * 0.5 + 0.5));
        P.xz *= 1. + up * 0.35 * P0.x;
        Nl = n; vH = n.y * 0.5 + 0.5;
    } else if (k == 3) {
        // COLLAR_V (a torus lying flat: its up is local -z, its front local +y), forja/luffy-g5: thin and low in front (the
        // face and the chest show), full on the shoulders, high behind the head; P0.x = the flames' height (0 = none,
        // the fur hems): the top of the tube pulled up in tongues that change and rise
        vec3 n = normalize(Normal);
        float b = fbm3(P * 3.1 + vec3(T * 0.5, 0., -T * 0.3)) * 0.5 + 0.5;
        P += n * (b * 0.22 + 0.06 * sin(atan(P.z, P.x) * 7. + T * 2.));
        vec3 UPL = vec3(0., 0., -1.);
        float ang = atan(Position.y, Position.x); vU = ang;
        float front = max(0., sin(ang)), back = max(0., -sin(ang));
        vec3 C = vec3(cos(ang), sin(ang), 0.) * .4;
        P = C + (P - C) * mix(1., .38, smoothstep(.2, .95, front));
        P += UPL * (back * back * .32 - front * .12);
        float up = max(dot(n, UPL), 0.);
        float tg = fbm3(vec3(cos(ang) * 4.2, sin(ang) * 4.2, -T * 1.6) + Seed) * .5 + .5;
        float lick = smoothstep(.42, .74, tg);
        float h = pow(up, 1.4) * P0.x * (.18 + 1.25 * lick) * (.35 + .9 * back + .25 * (1. - front));
        vec3 tn = vec3(-sin(ang), cos(ang), 0.);
        P += UPL * h + tn * sin(h * 6. - T * 3.2 + ang * 3.) * h * .22;
        vH = P0.x > 0. ? h / (P0.x * 1.43) : 0.;
        Nl = n;
    } else if (k == 4) {
        // FIRE_V: P0.x = Trail
        vec3 n = normalize(Position); float back = max(0., -n.z);
        float w = fbm3(n * 2.2 + vec3(0., 0., T * 2.6) + Seed);
        P = n * (1. + 0.28 * w);
        P.z -= back * back * P0.x * (0.7 + 0.9 * (fbm3(n * 3.4 + vec3(T * 3., 0., 0.) + Seed) * 0.5 + 0.5));
        P.xy *= 1. + back * 0.25 * P0.x;
        Nl = n; vBack = back;
    } else if (k == 5) {
        // BLAST_V: P0 = (Life, Flat, Smoke, Time)
        vec3 n = normalize(Position);
        float d = (fbm3(n * 2.4 + vec3(0., -P0.w * 0.7, 0.) + Seed) * 0.16 + gn(n * 6.3 + Seed * 2. + vec3(P0.w * 0.5)) * 0.05) * (0.5 + P0.x);
        P = n * (1. + d); P.y *= mix(1., 0.72, P0.y);
        Nl = n; vD = d; vO = n;
    } else if (k == 1) {
        vUv = Position.xy * 0.5 + 0.5;
    } else if (k == 8) {
        // HAKI_V (forja/luffy-g4): a box of P0.xyz pixels with its corners rounded by P0.w (the page's hakiBox);
        // P1.xyz = the page's light, relative to the camera
        vec3 hs = P0.xyz * 0.5; float r = min(P0.w, min(hs.x, min(hs.y, hs.z)) - 1e-3);
        vec3 p = Position * P0.xyz, c = clamp(p, -hs + r, hs - r), d = p - c; float l = length(d);
        P = l > 1e-5 ? c + d / l * min(r, l) : p;
        Nl = l > 1e-5 ? d / l : Normal;
        vO = P / 16.;
    } else if (k == 9) {
        // CLOUD_V (forja/luffy-g4 steam): a lump of the steam. P2.xyz = where its noise is anchored (blocks), P2.w = its
        // radius (blocks), so the lumps next to each other share one noise and read as one tube; P0.w = how far the noise
        // pushes it out, P0.y = the outline's extra size; P3.x = the page's sway (in radii), P3.y = its place along the tube
        vec3 n = normalize(Position);
        vec3 wp = P2.xyz + n * P2.w;
        float lump = gn(wp * 3.2 + vec3(Seed, -T * 0.6, 0.)) * 0.7 + gn(wp * 7.5 + vec3(0., -T, Seed)) * 0.22;
        vec3 sway = vec3(gn(vec3(P3.y * 5. - T * 0.7, T * 0.25, 1.3)), gn(vec3(P3.y * 5. - T * 0.7, T * 0.25, 7.1)) * 0.7, gn(vec3(P3.y * 5. - T * 0.7, T * 0.25, 4.2)));
        P = n * (1. + lump * P0.w + P0.y) + sway * P3.x;
        Nl = normalize(n + vec3(0., 0.35, 0.));
        vO = wp;
    } else if (k == 10) {
        // HAO_V (forja/luffy-haki, the Conqueror's Haki): the BOLT's zigzag, thick in the middle and pointed at both ends.
        // P0 = (L, R, Zig, Ph), P1 = (Bend, Wob, K, Halo: the soft glow is the same bolt, wider); vL = its axis in view space
        float u = Position.z; vec2 cs = Position.xy; float env = sin(u * 3.14159);
        float f = u * P1.z, i = floor(f), t = fract(f);
        vec2 zig = mix(vec2(hz(i), hz(i + 31.)), vec2(hz(i + 1.), hz(i + 32.)), t);
        vec2 off = zig * P0.z * (0.3 + 0.7 * env) + vec2(sin(u * 6.2832 - P0.w), 0.7 * cos(u * 4.7 - P0.w * 1.3)) * P1.y * env + vec2(0., P1.x * env);
        float r = P0.y * (0.1 + 0.9 * pow(env, 0.45)) * (1. + 0.25 * P1.y * sin(u * 20. - P0.w * 2.)) * P1.w;
        P = vec3(off + cs * r, u * P0.x);
        Nl = vec3(Normal.xy, 0.);
        vU = u;
        vL = normalize(mat3(ModelViewMat * Model) * vec3(0., 0., 1.));
    } else if (k == 11 || k == 12 || k == 13) {
        // the haki's burnt mark and pressure ring (a flat quad) and its shock shell (a sphere): shaped in the fragment
        vO = Position;
    }
    vec4 mv = ModelViewMat * (Model * vec4(P, 1.));
    mat3 nm = transpose(inverse(mat3(ModelViewMat * Model)));
    vN = nm * Nl;
    vV = -mv.xyz;
    vP = P;
    gl_Position = ProjMat * mv;
}
