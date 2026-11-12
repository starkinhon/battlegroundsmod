#version 150

// See souls_luffy.vsh. Premultiplied output (ONE, ONE_MINUS_SRC_ALPHA), as the page's PREMUL materials; the solid pieces
// (kind 7, the arm and the bolt) are opaque.
uniform float Kind;
uniform float T;
uniform float A;
uniform float Seed;
uniform float GlowPass;
uniform vec3 Col;
uniform vec3 Col2;
uniform vec4 P0;
uniform vec4 P1;
uniform vec4 P2;
uniform vec4 P3;

in vec3 vN;
in vec3 vV;
in vec3 vP;
in vec2 vUv;
in float vU;
in float vH;
in float vBack;
in float vD;
in vec3 vO;
in vec3 vL;

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
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float hemi(vec3 N) { return N.y * 0.5 + 0.5; }
float h1(float x) { return fract(sin(x * 127.1 + Seed * 31.7) * 43758.5453); }
// the page's HemisphereLight(0xffffff, 0x445566, 1) on a MeshLambertMaterial
vec3 lambert(vec3 c, vec3 N) { return c * mix(vec3(0.267, 0.333, 0.4), vec3(1.), hemi(N)); }

void main() {
    int k = int(Kind + 0.5);
    vec3 N = normalize(vN), V = normalize(vV);
    float f = abs(dot(N, V)), rim = 1. - f;
    // the world's up in view space: the page lights in world space
    vec3 Nw = N;
    if (k == 0) {
        // ARM_F: P2 = (Toon, Tw, Hx, Blur), P3.x = Haki; Col = uCol, Col2 = uRimC
        float lit = dot(Nw, normalize(vec3(0.4, 0.8, -0.45))) * 0.5 + 0.5; float band = lit > 0.55 ? 1. : lit > 0.3 ? 0.82 : 0.66;
        vec3 c = Col * band * (0.75 + 0.25 * hemi(Nw));
        c += Col2 * pow(rim, 3.) * (0.35 + 0.9 * P3.x);
        if (P2.x > 0.5 && rim > 0.82) c = vec3(0.06, 0.05, 0.08);
        // the Kaminari's light on the rubber (the page's boltLight): P3.y = strength, P3.z = blocks from the hand it reaches fully
        if (P3.y > 0.) { float bl = P3.y * exp(-max(0., (1. - vU) * P0.x - P3.z) / 14.); c = mix(c, vec3(1., 0.97, 0.84), clamp(bl, 0., 1.)) + vec3(0.25, 0.2, 0.05) * clamp(bl - 1., 0., 1.); }
        float al = 1. - P2.w * (0.35 + 0.5 * smoothstep(0.1, 1., vU));
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        fragColor = vec4(c * al, al);
    } else if (k == 1) {
        // BURST_F: P0 = (K, Arc, Sp, Thin); Col = tint
        float uK = P0.x, uArc = P0.y, uSp = P0.z, uThin = P0.w;
        vec2 p = vUv * 2. - 1.; float r = length(p), a = atan(p.y, p.x);
        float ff = (a / 6.2831853 + 0.5) * uSp, i = floor(ff), fr = fract(ff);
        if (i >= uSp) i = 0.;
        float tri = 1. - abs(fr - 0.5) * 2.;
        float Ro = 0.68 + (0.08 + 0.26 * h1(i) * h1(i + 7.3)) * pow(tri, 2.4) - 0.05 * h1(i + 3.1);
        float hole = mix(0.1, 0.9, pow(uK, 0.55)) + 0.04 * sin(a * 5. + Seed);
        hole = mix(hole, max(hole, 0.8), uThin);
        if (uArc > 0.) { float e = abs(a) / uArc; if (e > 1.) discard; hole = mix(hole, 1.02, smoothstep(0.2, 1., e)); }
        float ho = hole * Ro;
        if (r > Ro || r < ho) discard;
        float w = (r - ho) / max(Ro - ho, 1e-3);
        vec3 c = mix(Col, vec3(1.), smoothstep(0.05, 0.55, w));
        if (Ro - r < 0.016 || r - ho < 0.012) c = mix(Col, vec3(0.42, 0.58, 0.86), 0.55);
        float al = (1. - smoothstep(0.62, 1., uK)) * A;
        fragColor = vec4(c * 0.86 * al, al) * (GlowPass > 0.5 ? 0.35 : 1.);
    } else if (k == 2) {
        // STEAM_F
        vec3 q = vP * vec3(3.2, 1.6, 3.2) - vec3(0., T * 2.4, 0.); float w = fbm3(q * 0.7); float n = fbm4(q + vec3(w, -w, w)) * 0.5 + 0.5;
        float dens = n * (0.6 + 0.4 * rim) + vH * 0.15;
        float m = smoothstep(0.6, 0.68, dens) * (1. - smoothstep(0.55, 0.95, rim)); if (m * A < 0.01) discard;
        float lit = n + dot(Nw, vec3(0.4, 0.8, -0.45)) * 0.25; vec3 c = lit > 0.62 ? vec3(1.) : lit > 0.48 ? vec3(0.9, 0.9, 0.93) : vec3(0.74, 0.76, 0.8);
        c = mix(c, Col, 0.18); float a = m * A * (1. - smoothstep(0.75, 1., vH)) * 0.85;
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        fragColor = vec4(c * a, a);
    } else if (k == 3) {
        // COLLAR_F (forja/luffy-g5): the flames' tips tear into wisps that rise (vH = how far up the flame, vU = round the ring)
        if (vH > .4) { float w = gn(vec3(vU * 4., vH * 3.2 - T * 2.6, Seed)); if (w < (vH - .4) * 2.6 - .75) discard; }
        float l = dot(Nw, normalize(vec3(0.4, 0.8, -0.45))) * 0.5 + 0.5 + (fbm3(vP * 6.) * 0.2);
        vec3 c = l > 0.6 ? vec3(1.) : l > 0.38 ? vec3(0.88, 0.9, 0.95) : vec3(0.72, 0.76, 0.86);
        if (rim > 0.9) c = vec3(0.55, 0.6, 0.72);
        c = mix(c, c * vec3(.9, .86, 1.), smoothstep(.3, 1., vH));
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        fragColor = vec4(c * A, A);
    } else if (k == 4) {
        // FIRE_F
        vec3 q = vP * 2.1 + vec3(0., 0., T * 4.) + Seed; float w = fbm3(q * 0.8); float n = fbm4(q + vec3(w, -w, w) * 1.2) * 0.5 + 0.5;
        float heat = f * 0.75 + n * 0.55 - vBack * 0.5;
        float m = smoothstep(0.32, 0.4, n + f * 0.25 - vBack * 0.35); if (m * A < 0.01) discard;
        vec3 c = heat > 0.95 ? vec3(1., 0.97, 0.78) : heat > 0.72 ? vec3(1., 0.74, 0.22) : heat > 0.5 ? vec3(0.98, 0.42, 0.1) : vec3(0.72, 0.13, 0.06);
        float a = m * A;
        fragColor = GlowPass > 0.5 ? vec4(c * a * 0.6, 0.) : vec4(c * a * 1.25, a);
    } else if (k == 5) {
        // BLAST_F: P0 = (Life, Flat, Smoke, Time); Col = color, Col2 = light
        float uLife = P0.x, uTime = P0.w; bool smoke = P0.z > 0.5;
        vec3 q = vO * 3. + vec3(0., -uTime * (smoke ? 0.22 : 1.1), 0.) + Seed;
        float w = fbm3(q * 0.8 + 7.) * 0.8; float fire = fbm4(q + vec3(w, 0., -w)) * 0.5 + 0.5;
        float dens = fire + (smoke ? 0.12 : 0.4) * smoothstep(0.2, 0.9, rim) + vD * 2.2;
        float th = mix(0.2, 1.08, pow(uLife, 1.25));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        if (smoke) {
            float lit = dot(Nw, normalize(Col2)) * 0.5 + 0.5 + (fire - 0.5) * 0.6;
            float band = lit > 0.62 ? 1. : lit > 0.38 ? 0.74 : 0.52;
            vec3 col = Col * band * mix(0.62, 1., smoothstep(th, th + 0.1, dens));
            float a = m * 0.85 * (1. - smoothstep(0.8, 1., uLife)) * (1. - smoothstep(-0.05, 0.55, vO.y + (fire - 0.5) * 0.5)); if (a < 0.003) discard;
            if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
            fragColor = vec4(col * a, a);
        } else {
            float heat = clamp(1.15 - uLife * 1.25 + (fire - 0.5) * 1.3 + 0.3 * f, 0., 1.);
            vec3 b = Col / max(max(Col.r, max(Col.g, Col.b)), 1e-4);
            vec3 col = mix(Col * 0.5, Col, smoothstep(0.05, 0.4, heat)); col = mix(col, mix(b, vec3(1.), 0.55), smoothstep(0.45, 0.95, heat));
            float a = m * mix(0.3, 1., pow(rim, 1.1));
            fragColor = GlowPass > 0.5 ? vec4(col * a * 0.5, 0.) : vec4(col * a * (0.8 + 1.1 * heat), a * 0.6);
        }
    } else if (k == 6) {
        // BOLT_F: P1.w = Toon
        vec3 c = f > 0.45 ? vec3(1., 1., 0.92) : f > 0.18 ? vec3(1., 0.9, 0.35) : vec3(1., 0.76, 0.16);
        if (P1.w > 0.5 && f < 0.07) c = vec3(0.08, 0.05, 0.1);
        fragColor = GlowPass > 0.5 ? vec4(c * 0.8 * A, 0.) : vec4(c * 1.6 * A, A);
    } else if (k == 8) {
        // HAKI_F (forja/luffy-g4): black, a wine half-tone, red veins, a hard varnish highlight, the red rim
        vec3 L = normalize(vL); float d = dot(N, L);
        vec3 c = d > .4 ? vec3(.12, .025, .035) : d > -.1 ? vec3(.055, .014, .02) : vec3(.018, .006, .009);
        float vn = gn(vO * .9 + Seed); float vein = 1. - smoothstep(.02, .07, abs(vn));
        c = mix(c, vec3(.62, .06, .09), vein * .8);
        vec3 hh = normalize(L + V); float sp = pow(max(dot(N, hh), 0.), 38.);
        c = mix(c, vec3(.85, .25, .28), step(.4, sp) * .75); c = mix(c, vec3(1., .88, .88), step(.85, sp));
        c = mix(c, vec3(.6, .05, .08), smoothstep(.7, .76, rim) * .85);
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        fragColor = vec4(c, 1.);
    } else if (k == 9) {
        // CLOUD_F (forja/luffy-g4 steam): torn by noise as it ages (P0.x), cel in 3 bands, P0.z = the outline
        float n = gn(vO * 2.7 + vec3(0., -T * .5, 0.)) * .8 + gn(vO * 6.6) * .2;
        float thr = -1.25 + 2.25 * pow(P0.x, 3.2);
        if (n < thr) discard;
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        if (P0.z > 0.5) { fragColor = vec4(.42, .47, .58, 1.); return; }
        float d = dot(N, normalize(vL));
        vec3 c = d > .25 ? vec3(.97, .98, 1.) : d > -.25 ? vec3(.84, .87, .93) : vec3(.64, .69, .8);
        if (n < thr + .08) c = vec3(.72, .77, .87);
        fragColor = vec4(c, 1.);
    } else if (k == 10) {
        // HAO_F (forja/luffy-haki): black core, a thin edge in the haki's color (Col) with a light rim, eaten from the root
        // (P2.y) and grown to the tip (P2.x); P2.z = 1 the soft glow (Col2) around it. The light is measured across the tube,
        // so a bolt seen along its length does not turn into a bright plate.
        if (vU > P2.x || vU < P2.y) discard;
        vec3 Tt = normalize(vL), Vp = V - Tt * dot(V, Tt);
        float fb = length(Vp) < 1e-3 ? 1. : abs(dot(N, normalize(Vp)));
        float tip = (1. - smoothstep(P2.x - 0.06, P2.x, vU)) * smoothstep(P2.y, P2.y + 0.06, vU);
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        if (P2.z > 0.5) {
            float a = pow(fb, 3.) * 0.55 * A * tip;
            fragColor = vec4(mix(Col2, vec3(1.), pow(fb, 6.) * 0.25) * a, a);
            return;
        }
        vec3 c = fb > 0.3 ? vec3(0.02, 0.003, 0.01) : fb > 0.13 ? Col : mix(Col, vec3(1.), 0.4);
        float a = A * tip;
        fragColor = vec4(c * a, a);
    } else if (k == 11) {
        // the burnt mark where a bolt hit the ground: P0 = (age s, half size of the quad, mark size, 0), in 1/16 pixels
        vec2 q = floor(vO.xy * P0.y * 16.) / 16. + 1. / 32.;
        float r = length(q), an = atan(q.y, q.x), m = P0.z;
        float cr = abs(gn(vec3(an * 2.6, r * 1.5, Seed)));
        float crack = step(cr, 0.06) * (1. - smoothstep(m * 0.4, m * 2.6, r));
        float burn = step(r + gn(vec3(q * 7., Seed)) * 0.05, m * 0.55);
        float fade = 1. - smoothstep(1.4, 2.6, P0.x);
        float hot = (1. - smoothstep(0., 0.5, P0.x)) * 0.9;
        vec3 c = crack > 0.5 ? mix(vec3(0.03, 0.02, 0.02), Col, hot) : vec3(0.1, 0.08, 0.07);
        float a = max(burn * 0.8, crack * 0.95) * fade * A;
        if (a < 0.01 || GlowPass > 0.5) discard;
        fragColor = vec4(c * a, a);
    } else if (k == 12) {
        // the pressure ring running out over the ground: P0 = (age s, half size of the quad); a dark band, the haki's edge
        // and the white dust raised in swirls just behind it
        vec2 q = floor(vO.xy * P0.y * 16.) / 16. + 1. / 32.;
        float r = length(q), ag = P0.x, R = ag * 15., fl = 1. - ag / 1.4;
        float band = (1. - smoothstep(0., 0.5, abs(r - R))) * fl;
        float edge = (1. - smoothstep(0., 0.08, abs(r - R - 0.4))) * fl * 0.8;
        float an = atan(q.y, q.x);
        float sw = gn(vec3(an * 3. + r * 0.9 - ag * 4., r * 0.6, Seed));
        float dust = smoothstep(0.15, 0.3, sw) * (1. - smoothstep(0., 1.6, abs(r - R * 0.82 - 0.4))) * (1. - smoothstep(0.5, 1.4, ag)) * step(1.2, r) * 0.9;
        vec4 o = vec4(0., 0., 0., band * 0.55);
        o = o * (1. - edge) + vec4(Col * edge, edge);
        o = o * (1. - dust) + vec4(vec3(0.96, 0.96, 0.98) * dust, dust);
        if (o.a < 0.01 || GlowPass > 0.5) discard;
        fragColor = o * A;
    } else if (k == 13) {
        // the shock shell: dark and see-through, a thin edge in the haki's color, torn by noise as it fades (P0.x = 1 .. 0)
        float n = fbm3(vO * 3.2 + vec3(0., -T * 2., Seed)) * 0.5 + 0.5;
        float d = rim * (0.65 + 0.7 * n) - (1. - P0.x) * 0.9;
        if (d < 0.45 || GlowPass > 0.5) discard;
        vec3 c = d > 0.86 ? Col : vec3(0.05, 0.005, 0.02);
        float a = smoothstep(0.45, 0.6, d) * (d > 0.86 ? 0.7 : 0.18) * A;
        fragColor = vec4(c * a, a);
    } else {
        // a solid piece (Lambert under the page's hemisphere light); P0.x = 1: the King Kong Gun's fist
        vec3 c = Col;
        if (P0.x > 0.5) {
            vec3 cell = floor(vP * 16. + 0.5);
            float h = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719)) + Seed) * 43758.5453);
            float vein = gn(vP * 5. + Seed) ;
            c = vec3(0.078, 0.063, 0.082);
            if (abs(vein) < 0.08) c = h > 0.66 ? vec3(1., 0.353, 0.227) : vec3(0.816, 0.094, 0.094);
            c += vec3(0.165, 0.024, 0.024);
        }
        if (GlowPass > 0.5) { fragColor = vec4(0.); return; }
        fragColor = vec4(lambert(c, Nw) * A, A);
    }
}
