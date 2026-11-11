#version 150

// The Dragon Ball kits' 3D effects (client/fx/KiMesh), the approved pages' shaders (forja/goku-kit, freeza-kit,
// vegeta-kit, piccolo-kit, kuririn-kit, kame-kit) carried over line by line. The mesh comes in as a unit shape (sphere,
// open tube with y from 0 to 1, or a flat square in x/z) and is bent here exactly as on the pages; Model places it
// (relative to the camera). Kind says which effect: 0 blast/smoke, 1 Genki core, 2 halo (Genki), 3 beam, 4 dust ring,
// 5 aura shell, 6 hit flash, 7 sun core, 8 sun halo, 9 saw disc, 10 Kienzan disc, 11 Makankosappo spirals, 12 wind,
// 13 Sasuke's Katon shell (forja/sasuke-chidori/katon.js), 14 its wall of fire on the ground, 15 Shikamaru's shadow running
// on the ground, 16 his Xeque-mate's flood of shadow (forja/shikamaru/shika.js; both flat squares), 17 Inumaki's voice (a cone
// of ink rings) and 18 the rings that hold the one he stopped (forja/inumaki-kit/inumaki.js VOICE_F / BIND_F); 19-22 Nobara's
// nail sheath, Resonance thorn, Hairpin burst and the ult's thread (forja/nobara-kit/nobara.js).
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform float Kind;
uniform float Life;
uniform float Seed;
uniform float Time;
uniform float Flat;
uniform float Len;
uniform float W;
uniform float Flow;
// aura shell
uniform float Phase;
uniform float Growth;
uniform float FlashAmt;
uniform float Tongue;
uniform float Spike;
uniform float SpikeFreq;
uniform float SpikeSharp;
uniform float Crown;
uniform float Up;
uniform vec3 Size;
// wind
uniform float R0;
uniform float R1;
uniform float Flare;
uniform float Bend;

out vec3 vN;
out vec3 vV;
out vec3 vO;
out float vD;
out float vU;
out vec2 vQ;
out float vH;
out vec2 vUv;
out vec2 vDir;
out vec3 vNv;
out vec4 vC;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

// ---- aura shell (the approved "casca") ----
float hgt(vec3 p) { return clamp(p.y * 0.5 + 0.5, 0., 1.); }
vec3 egg(vec3 p) { float h = hgt(p), k = 0.9 + 0.28 * pow(1. - h, 1.4); return vec3(p.x * k, p.y, p.z * k); }
vec3 shape(vec3 p, out float disp) {
    float h = hgt(p); vec3 base = egg(p) * Size;
    float swell = Growth * Growth * (3. - 2. * Growth); base.xz *= mix(0.35, 1.0, swell);
    float front = Growth * 1.5, lit = 1. - smoothstep(front - 0.35, front, h), grow = Growth * Growth * lit;
    vec3 q = vec3(p.x * 1.7, p.y * 1.25 - Phase, p.z * 1.7) + Seed;
    vec2 w = vec2(fbm3(q * 0.7 + 3.1), fbm3(q * 0.7 + 7.7)) * 0.7;
    float tongues = smoothstep(0.38, 0.95, fbm3(q + vec3(w.x, 0., w.y)) * 0.5 + 0.5);
    vec3 sq = vec3(p.x * SpikeFreq, p.y * SpikeFreq * 0.55 - Phase * 1.8, p.z * SpikeFreq) + vec3(Time * 0.83, 0., -Time * 0.61) + Seed * 2.;
    float spikes = pow(min(max(gn(sq) * 0.9 + 0.1 * gn(sq * 2.7 + 5.), 0.) * 1.7, 1.), SpikeSharp) * (1. + 1.4 * FlashAmt);
    float env = smoothstep(0.02, 0.22, h) * (1. - 0.25 * smoothstep(0.92, 1., h));
    float spikeEnv = smoothstep(0.18, 0.5, h) * (1. - 0.5 * smoothstep(0.85, 1., h));
    float crown = smoothstep(0.55, 1., h);
    disp = tongues * Tongue * env + spikes * Spike * spikeEnv;
    vec3 out1 = normalize(vec3(p.x, p.y * 0.35, p.z) + 1e-4);
    vec3 dir = normalize(mix(out1, vec3(0., 1., 0.), clamp(Up * smoothstep(0.1, 0.7, h) + crown * 0.55, 0., 0.95)));
    vec3 sdir = normalize(mix(out1, vec3(0., 1., 0.), 0.62 + 0.3 * h));
    vec3 pos = base + (dir * tongues * Tongue * env + sdir * spikes * Spike * spikeEnv) * grow;
    pos.xz *= 1. - 0.38 * crown * crown * (0.4 + 0.6 * tongues);
    pos.y += crown * crown * (0.22 + 0.6 * tongues) * Crown * grow;
    pos.xz += vec2(gn(vec3(0.3, h * 0.6 - Phase * 0.18, 1.7)), gn(vec3(2.1, h * 0.6 - Phase * 0.16, 0.4))) * 0.12 * h * h * grow;
    disp *= grow; return pos;
}

void main() {
    vec3 P = Position;
    vec3 n = vec3(0., 1., 0.);
    vD = 0.; vU = 0.; vQ = vec2(0.); vH = 0.; vUv = vec2(0.); vDir = vec2(1., 0.); vO = vec3(0.);
    int kind = int(Kind + 0.5);
    if (kind == 0) {                                                   // blast / smoke: the shell that tears into tongues
        n = normalize(Position);
        float d = (fbm3(n * 2.4 + vec3(0., -Time * 0.7, 0.) + Seed) * 0.16 + gn(n * 6.3 + Seed * 2. + vec3(Time * 0.5)) * 0.05) * (0.5 + Life);
        P = n * (1. + d); P.y *= mix(1., 0.72, Flat);
        vO = n; vD = d;
    } else if (kind == 1 || kind == 7) {                               // Genki core / sun core: a softly breathing ball
        n = normalize(Position);
        float b = gn(n * 3.5 + vec3(0., -Time * 0.7, Time * 0.3)) * 0.03 + gn(n * 9. + Time * 1.1) * 0.01;
        P = n * (1. + b); vO = n;
    } else if (kind == 2 || kind == 6 || kind == 8) {                  // plain spheres (halos, hit flash)
        n = normalize(Position); P = Position; vO = n;
    } else if (kind == 3) {                                            // ki beam: one tube, twisted and swollen
        float along = Position.y * Len; vec2 d = normalize(Position.xz + 1e-5); float ang = atan(d.y, d.x);
        float tw = ang + along * 0.45 - Time * 2.2;
        vec3 q = vec3(cos(tw) * 1.3, sin(tw) * 1.3, along * 0.8 - Flow);
        float tg = smoothstep(0.35, 0.95, fbm3(q) * 0.5 + 0.5);
        float prof = mix(0.42, 1., smoothstep(0., 1.6, along)) * (1. + 0.1 * sin(along * 1.3 - Time * 9.));
        float r = W * prof * (1. + 0.4 * tg);
        P = vec3(d.x * r, along, d.y * r); n = vec3(d.x, 0., d.y);
        vU = along; vD = tg; vQ = vec2(tw, along);
    } else if (kind == 4 || kind == 9 || kind == 10 || kind == 15 || kind == 16) {   // flat square: dust ring, saw and Kienzan discs, shadows
        P = Position; n = vec3(0., 1., 0.); vUv = Position.xz * 0.5 + 0.5;
        vQ = Position.xz * vec2(length(Model[0].xyz), length(Model[2].xyz));   // in blocks, across and along the square
    } else if (kind == 5) {                                            // aura shell
        vec3 p = normalize(Position); float d0, d1, d2; P = shape(p, d0);
        vec3 t1 = normalize(cross(p, abs(p.y) > 0.99 ? vec3(1, 0, 0) : vec3(0, 1, 0))), t2 = cross(p, t1);
        vec3 Pa = shape(normalize(p + t1 * 0.012), d1), Pb = shape(normalize(p + t2 * 0.012), d2);
        n = normalize(cross(Pa - P, Pb - P));
        if (dot(n, P - vec3(0., P.y, 0.) + vec3(0., 0.001, 0.)) < 0. && hgt(p) > 0.05 && hgt(p) < 0.95) n = -n;
        vO = p; vH = hgt(p); vD = d0;
    } else if (kind == 11) {                                           // Makankosappo: the tube the two spirals ride on
        float along = Position.y * Len; vec2 d = normalize(Position.xz + 1e-5);
        float r = W * (0.55 + 0.45 * smoothstep(0., 0.7, along)) * (1. + 0.06 * sin(along * 5. - Time * 30.));
        P = vec3(d.x * r, along, d.y * r); n = vec3(d.x, 0., d.y); vDir = d; vU = along;
    } else if (kind == 12) {                                           // wind: the gale's cone, the tornado's funnel
        float h = Position.y; vec2 d = normalize(Position.xz + 1e-5);
        float r = mix(R0, R1, pow(h, Flare)) * (1. + 0.1 * sin(h * 11. - Time * 8. + d.x * 3.));
        P = vec3(d.x * r + Bend * h * h, h * Len, d.y * r); n = vec3(d.x, 0., d.y); vDir = d; vH = h;
    } else if (kind == 13) {                                           // Katon: ONE fire shell (the ball with its tail, the blast dome turning to smoke)
        vec3 nr = normalize(Position), ax = normalize(vec3(0.25, 1., 0.15));
        float ca = cos(-Phase), sa = sin(-Phase);                       // Phase = how far the drawing has turned (with the ball)
        vec3 q = nr * ca + cross(ax, nr) * sa + ax * dot(ax, nr) * (1. - ca);
        float nn = fbm3(q * 2.1 + vec3(0., -Time * 0.55, 0.) + Seed);
        float sp = pow(clamp(gn(q * 4.7 + Seed * 2. + vec3(0., -Time * 0.8, 0.)) * 0.5 + 0.5, 0., 1.), 3.);   // tongues of the shell itself
        vec3 td = normalize(Size + vec3(1e-5));                         // Size = where the tail runs, Len = how long
        float tl = Len * pow(max(0., dot(nr, td)), 2.6);
        P = nr * (1. + Spike * (nn * 0.55 + sp * 1.4)) + td * tl * (0.85 + 0.35 * nn);
        P.y += Up * smoothstep(-0.1, 0.5, nr.y) * (0.7 + 0.6 * nn);    // the dome rising as smoke
        P.xz *= 1. + Growth * (0.6 + 0.5 * nn);
        n = normalize(nr + vec3(0., 0.4 * Up, 0.)); vO = q; vD = Len > 0. ? tl / Len : 0.;
    } else if (kind == 14) {                                           // Katon: the wall of fire running round on the ground (tube, y 0..1)
        vec2 d = normalize(Position.xz + 1e-5); float ang = atan(d.y, d.x), h = Position.y;
        vec3 c = vec3(cos(ang) * 2.6, sin(ang) * 2.6, Time * 0.7 + Seed);
        float nn = fbm3(c) * 0.5 + 0.5, n2 = gn(c * 2.3 + 4.) * 0.5 + 0.5;
        float top = 0.35 + 1.25 * nn * nn + 0.5 * n2;                   // each flame its own height
        float rr = 1. + 0.05 * (nn - 0.5) - 0.08 * h;
        P = vec3(d.x * rr, h * top, d.y * rr); n = normalize(vec3(d.x, 0.3, d.y));
        vH = h; vO = vec3(cos(ang) * 4., P.y * 2.2 - Time * 2.2, sin(ang) * 4.) + Seed;
    } else if (kind == 17 || kind == 18) {                             // Inumaki: the voice's open cone (R0 at the mouth, R1 at the end) / the rings' cylinder (W)
        float h = Position.y; vec2 d = normalize(Position.xz + 1e-5);
        float r = kind == 17 ? mix(R0, R1, h) : W;
        P = vec3(d.x * r, h * Len, d.y * r); n = vec3(d.x, 0., d.y); vDir = d; vH = h;
    } else if (kind == 19 || kind == 20 || kind == 22) {               // Nobara's tubes (forja/nobara-kit/nobara.js TRAIL_V / THORN_V / LINK_V, line by line):
        float h = Position.y; vec2 d = normalize(Position.xz + 1e-5); // 19 the nail's sheath (W = width), 20 Resonance's barbed thorn (W, Growth, Bend),
        float r, L = Len;                                              // 22 the ult's thread (W, Growth)
        if (kind == 19) {
            r = W * (0.3 + 0.7 * smoothstep(0., 0.1, h)) * pow(1. - h, 0.8);
        } else if (kind == 20) {
            L = Len * Growth;
            float barb = 1. + 0.55 * pow(fract(h * (3. + Len * 0.8) + Seed), 3.);   // barbs: each segment swells and thins
            r = W * pow(1. - h, 1.15) * (0.72 + 0.56 * (gn(vec3(d * 1.7, h * 5. + Seed)) * 0.5 + 0.5)) * barb;
        } else {
            L = Len * Growth;
            r = W * (0.8 + 0.4 * (gn(vec3(d, h * Len * 1.2 - Time * 2. + Seed)) * 0.5 + 0.5));
        }
        P = vec3(d.x * r + (kind == 20 ? Bend * h * h * L : 0.), h * L, d.y * r); n = vec3(d.x, 0., d.y); vDir = d; vH = h;
    } else if (kind == 21) {                                           // Nobara's Hairpin: ONE sphere bursting into spikes (BURST_V; Life = age 0..1, radius = Model)
        vec3 d = normalize(Position);
        float ri = 1. - abs(gn(d * 3.1 + Seed)), sp = pow(ri, 7.);
        float lump = fbm3(d * 2. + Seed * 1.7) * 0.5 + 0.5;
        float grow = 1. - pow(1. - clamp(Life * 4., 0., 1.), 3.);
        P = d * grow * (0.5 + 0.35 * lump + 1.25 * sp); n = d; vO = d; vD = sp;
    }
    vec4 w = Model * vec4(P, 1.0);
    vN = mat3(Model) * n;
    vNv = mat3(ModelViewMat) * vN;                                     // the normal as the camera sees it (halo rays)
    vV = -w.xyz;
    vC = vec4((Model * vec4(0., 0., 0., 1.)).xyz, length(Model[0].xyz));   // the piece's center (camera-relative) and size
    gl_Position = ProjMat * ModelViewMat * w;
}
