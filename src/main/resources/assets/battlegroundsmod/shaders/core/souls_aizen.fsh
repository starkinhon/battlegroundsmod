#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
// Aizen's kit (forja/aizen-kit/aizen-kit.html, approved v2.3): the page's fragment shaders, one per Kind, the same text and
// numbers. Everything comes out premultiplied (color * alpha, alpha). In the glow pass (GlowPass 1) only what the page's
// bloom would catch (threshold 0.6) goes into the glow layer.
in vec3 vN;
in vec3 vV;
in vec3 vO;
in vec3 vQ;
in vec2 vU;
in float vH;
in float vDisp;

uniform float Kind;
uniform float Time;
uniform float Seed;
uniform float Alpha;
uniform float GlowPass;
uniform vec4 A;
uniform vec4 B;
uniform vec3 TintA;
uniform vec3 TintB;
uniform vec3 TintC;

out vec4 fragColor;

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
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float hs(float n) { return fract(sin(n * 91.345) * 47453.13); }
float hs1(float n) { return fract(sin(n * 91.7) * 43758.5); }
vec3 h3(vec3 p) { return fract(sin(vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)))) * 43758.5453); }
vec2 h2(vec2 p) { return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453); }

// the page's light (uLight / uLightV), here in the world's axes
const vec3 LIGHT = vec3(-0.4, 0.8, 0.45);

void emit(vec3 premul, float a) {
    if (GlowPass > 0.5) {
        float k = smoothstep(0.55, 0.7, dot(premul, vec3(0.299, 0.587, 0.114)));
        if (k < 0.003) discard;
        fragColor = vec4(premul * k * 0.6, 0.);
        return;
    }
    fragColor = vec4(premul, a);
}

void main() {
    vec3 N = normalize(vN), V = normalize(vV);
    float f = abs(dot(N, V));

    if (isK(K_EN)) {
        // EN_F: A = (uFlow, uHalo, uFadeEnds, -); TintA core, TintB body, TintC rim
        float a0 = vU.y * 6.2831853; vec3 q = vec3(cos(a0) * 1.2, sin(a0) * 1.2, vU.x * 14. - A.x) + Seed;
        float n = fbm3(q) * 0.5 + 0.5;
        float ends = mix(1., smoothstep(0., 0.06, vU.x) * (1. - smoothstep(0.9, 1., vU.x)), A.z);
        if (A.y > 0.5) { float g = pow(f, 2.2) * (0.6 + 0.6 * n); float a = g * Alpha * 0.28 * ends; if (a < 0.003) discard; emit(TintB * a, a * 0.15); return; }
        float heat = clamp(pow(f, 1.3) * 1.15 + (n - 0.5) * 0.7, 0., 1.);
        vec3 col = mix(TintC, TintB, smoothstep(0.08, 0.45, heat)); col = mix(col, TintA, smoothstep(0.6, 0.92, heat));
        float a = mix(0.7, 1., f) * Alpha * ends; if (a < 0.003) discard;
        emit(col * a, a);
        return;
    }

    if (isK(K_DRAG)) {
        // DRAG_F: A.x = uLen
        float s = vU.x; float a0 = vU.y * 6.2831853;
        vec3 q = vec3(s * A.x * 0.55 + Time * 3.2, cos(a0) * 1.4, sin(a0) * 1.4) + Seed;
        float n = fbm4(q) * 0.5 + 0.5;
        float dis = n * 0.55 + (1. - smoothstep(0.62, 1., s)) - (1. - Alpha) * 1.6;
        if (dis < 0.32) discard;
        float lam = dot(N, normalize(LIGHT)) * 0.5 + 0.5 + (n - 0.5) * 0.5;
        vec3 dark = vec3(0.34, 0.08, 0.7), mid = vec3(0.78, 0.26, 0.98), hi = vec3(0.98, 0.62, 1.0);
        vec3 col = lam > 0.64 ? hi : lam > 0.40 ? mid : dark;
        float streak = smoothstep(0.62, 0.8, gn(q * vec3(0.6, 2.2, 2.2) + vec3(Time * 2., 0., 0.)) * 0.5 + 0.5);
        col = mix(col, vec3(1.0, 0.92, 1.0), streak * 0.45);
        float ink = 1. - smoothstep(0.16, 0.28, f); col = mix(col, vec3(0.12, 0.08, 0.5), ink);
        float burn = 1. - smoothstep(0.32, 0.4, dis); col = mix(col, vec3(1., 0.85, 1.), burn);
        emit(col * 0.74 + vec3(0.5, 0.1, 0.7) * pow(1. - f, 3.) * 0.5 * (1. - ink), 1.);
        return;
    }

    if (isK(K_DHEAD)) {
        // DHEAD_F: A.x = uKind (0 skin, 1 teeth, 2 throat, 3 the lit eyes)
        if (A.x > 2.5) { emit(vec3(1.0, 1.0, 1.0), 1.); return; }
        vec3 Nf = gl_FrontFacing ? N : -N;
        vec3 q = vO * 1.6 + vec3(0., 0., Time * 2.4) + Seed; float n = fbm3(q) * 0.5 + 0.5;
        if (Alpha < 0.99 && n * 0.6 + 0.4 * gn(vO * 3.) - (1. - Alpha) * 1.4 < 0.05) discard;
        float lam = dot(Nf, normalize(LIGHT)) * 0.5 + 0.5 + (n - 0.5) * 0.45;
        vec3 col = lam > 0.64 ? vec3(0.98, 0.62, 1.0) : lam > 0.40 ? vec3(0.78, 0.26, 0.98) : vec3(0.34, 0.08, 0.7);
        if (A.x > 0.5) col = mix(vec3(0.95, 0.85, 1.), vec3(0.65, 0.5, 0.9), smoothstep(0.3, 0.8, 1. - lam));
        if (A.x > 1.5) col = vec3(0.12, 0.03, 0.25) + vec3(0.5, 0.1, 0.6) * pow(n, 3.);
        float streak = smoothstep(0.66, 0.82, gn(q * vec3(2., 2., 0.6)) * 0.5 + 0.5); col = mix(col, vec3(1., 0.9, 1.), streak * 0.35 * step(A.x, 0.5));
        float ink = 1. - smoothstep(0.16, 0.28, f); col = mix(col, vec3(0.1, 0.06, 0.45), ink * step(A.x, 1.5));
        emit(col * 0.74, 1.);
        return;
    }

    if (isK(K_SOKA)) {
        // SOKA_F: A = (uLen, uFlow, uHead, uTail), B.x = uDiss
        float uLen = A.x, uHead = A.z, uTail = A.w, uDiss = B.x, vUa = vU.x;
        float fire = fbm4(vQ * 1.3 + fbm3(vQ * 0.7 + 3.) * 0.9) * 0.5 + 0.5;
        float dens = fire + 0.5 * f + 0.4 * vDisp - 0.25 * (vUa / max(uLen, 0.1));
        dens -= smoothstep(uHead - 1.2, uHead, vUa) * 1.2;
        dens -= (1. - smoothstep(uTail, uTail + 1.2, vUa)) * 1.4;
        dens -= uDiss * (0.55 + 0.9 * (1. - fire)) * (1.05 - 0.35 * vUa / max(uLen, 0.1));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(0.62 - aa, 0.62 + aa, dens); if (m < 0.003) discard;
        float heat = clamp(pow(f, 1.6) * 0.8 + (fire - 0.5) * 1.1 - 0.18 * vUa / max(uLen, 0.1), 0., 1.) * (1. - 0.65 * uDiss);
        vec3 col = mix(vec3(0.08, 0.2, 0.9), vec3(0.25, 0.75, 1.0), smoothstep(0.1, 0.45, heat)); col = mix(col, vec3(0.9, 1., 1.), smoothstep(0.62, 0.95, heat));
        float a = m * mix(0.5, 1., f) * Alpha;
        // Stark 2026-11-25 ("perdeu o brilho, parece desaturado"): the luminance gate in emit() let only the white heart into
        // the glow, the blue body (luma ~0.25) never bloomed and read grey against the sky. The fire's glow is gated by
        // its brightest channel instead, so the blue itself blooms, blue. Steady (no flicker): it follows heat and alpha.
        if (GlowPass > 0.5) {
            vec3 pm = col * a * 0.8;
            float g = smoothstep(0.3, 0.65, max(pm.b, max(pm.r, pm.g)));
            if (g < 0.003) discard;
            fragColor = vec4(pm * g * 0.75, 0.);
            return;
        }
        emit(col * a * 0.8, a * 0.9);
        return;
    }

    if (isK(K_WALL)) {
        // WALL_F: A = (uRise, uSplat, uCrack), B = (uH, uW, uHit.x, uHit.y)
        float uRise = A.x, uSplat = A.y, uCrack = A.z, uH = B.x, uW = B.y; vec2 uHit = B.zw;
        vec3 vP = vO;
        float y01 = vP.y / uH + 0.5; float front = uRise * 1.04;
        float nz = gn(vec3(vP.x * 2., 0., Time)) * 0.03;
        if (y01 > front + nz) discard;
        vec2 q = vec2(vP.x, vP.y);
        float streak = gn(vec3(q.x * 3.2, q.y * 0.25 - Time * 0.25, 1.)) * 0.5 + 0.5;
        float edgeD = min(uW * 0.5 - abs(vP.x), uH * 0.5 - abs(vP.y));
        float border = 1. - smoothstep(0.0, 0.18, edgeD);
        vec3 col = vec3(0.70, 0.88, 1.0); float a = 0.13 + 0.22 * pow(1. - f, 2.) + 0.12 * smoothstep(0.62, 0.9, streak) + 0.35 * border;
        float riseBand = 1. - smoothstep(0., 0.07, front - y01); a += riseBand * 0.5 * step(uRise, 0.999); col = mix(col, vec3(1.), riseBand * 0.5);
        vec2 d = q - uHit; float r = length(d), th = atan(d.y, d.x);
        float petal = pow(abs(cos(th * 4. + 0.35 * gn(vec3(r * 2., th, 3.)))), 1.6);
        float R = uSplat * (0.32 + 1.05 * petal) * (1. + 0.12 * gn(vec3(th * 3., r, Time)));
        float ins = 1. - smoothstep(R - 0.03, R + 0.03, r);
        vec3 ink = mix(vec3(0.05, 0.07, 0.2), vec3(0.25, 0.42, 0.85), smoothstep(R - 0.25, R, r));
        col = mix(col, ink, ins * 0.92); a = mix(a, 0.9, ins);
        float cr = 0.;
        for (int k = 0; k < 11; k++) {
            float fk = float(k); float ang = fk / 11. * 6.2831 + (hs(fk + 3.) - 0.5) * 0.5; float len = (0.9 + 1.6 * hs(fk + 9.)) * uCrack;
            float jit = gn(vec3(r * 2.4, fk * 7.1, 2.)) * 0.18 * r; float da = abs(mod(th - ang + jit + 3.14159, 6.2831) - 3.14159) * r;
            float w = 0.018 * (1. - r / max(len, 0.01)); cr = max(cr, (1. - smoothstep(w * 0.4, w, da)) * step(r, len));
        }
        float ringC = (1. - smoothstep(0., 0.02, abs(r - 0.75 * uCrack - gn(vec3(th * 2., 4., 1.)) * 0.1))) * step(0.3, uCrack) * step(0.5, petal + 0.3);
        cr = max(cr, ringC * 0.8);
        col = mix(col, vec3(0.95, 1., 1.), cr); a = max(a, cr * 0.95);
        a *= Alpha; if (a < 0.003) discard; emit(col * a, a);
        return;
    }

    if (isK(K_CRES)) {
        // the answering cut's crescent: A.x = uSweep
        float u = vU.x, w = vU.y, uSweep = A.x;
        float head = smoothstep(uSweep + 0.02, uSweep - 0.02, u), tail = smoothstep(uSweep - 0.75, uSweep - 0.1, u);
        float n = gn(vec3(u * 9. - uSweep * 6., w * 3., Seed)) * 0.5 + 0.5;
        float body = smoothstep(0., 0.35 + 0.3 * n, w) * (1. - smoothstep(0.88, 1., w));
        float a = head * tail * body * Alpha; if (a < 0.003) discard;
        vec3 c = mix(vec3(0.45, 0.25, 0.95), vec3(0.95, 0.92, 1.), smoothstep(0.55, 0.95, w) * (0.6 + 0.4 * n));
        emit(c * a * 1.3, a * 0.6);
        return;
    }

    if (isK(K_COC)) {
        // COC_F: A = (uGrow, uCrack)
        float uGrow = A.x, uCrack = A.y;
        float front = uGrow * 1.18 - 0.08 + gn(vec3(vO.xz * 4., Time * 0.6)) * 0.08;
        if (vH > front) discard;
        float lam = dot(N, normalize(vec3(-0.4, 0.8, 0.5))) * 0.5 + 0.5 + (fbm3(vO * 5.) * 0.15);
        vec3 col = lam > 0.66 ? vec3(0.96, 0.94, 0.99) : lam > 0.42 ? vec3(0.84, 0.80, 0.92) : vec3(0.66, 0.60, 0.80);
        float edge = 1. - smoothstep(0., 0.07, front - vH); col = mix(col, vec3(0.75, 0.55, 1.), edge * 0.8);
        vec3 p = vO * 3.4; vec3 ip = floor(p); float F1 = 9., F2 = 9.; vec3 c1 = vec3(0.);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            vec3 c = ip + vec3(x, y, z); vec3 qq = c + h3(c); float d = length(p - qq);
            if (d < F1) { F2 = F1; F1 = d; c1 = c; } else if (d < F2) F2 = d;
        }
        float on = step(h3(c1 + 5.).x, uCrack * 1.5 - 0.15);
        float w = 0.02 + 0.07 * uCrack;
        float cr = (1. - smoothstep(w * 0.5, w, F2 - F1)) * on * step(0.02, uCrack);
        float glow = (1. - smoothstep(w, w * 3.5, F2 - F1)) * on * uCrack;
        col = mix(col, vec3(0.62, 0.3, 1.0), glow * 0.6); col = mix(col, vec3(0.98, 0.9, 1.), cr);
        emit(col * 0.7 + vec3(0.5, 0.2, 1.) * cr * 0.9 + vec3(0.3, 0.1, 0.6) * glow * 0.6, 1.);
        return;
    }

    if (isK(K_ORB)) {
        // ORB_F: the Hogyoku on his chest (the page's sphere is 0.11 across: vO is the unit sphere here)
        vec3 o = vO * 0.11;
        float cross1 = 1. - smoothstep(0.04, 0.08, min(abs(o.x), abs(o.y)));
        vec3 c = mix(vec3(0.05, 0.02, 0.1), vec3(0.55, 0.2, 1.0), pow(1. - f, 2.)); c = mix(c, vec3(0.9, 0.8, 1.), cross1 * step(0., -o.z) * 0.8);
        if (Alpha < 0.003) discard;
        emit(c * Alpha, Alpha);
        return;
    }

    if (isK(K_OHALO)) {
        float ang = atan(N.y, N.x);
        float rays = pow(gn(vec3(cos(ang) * 7., sin(ang) * 7., Time * 0.5)) * 0.5 + 0.5, 2.);
        float g = pow(smoothstep(0., 0.85, f), 2.) * (0.4 + 0.9 * rays); vec3 col = mix(vec3(0.45, 0.15, 1.), vec3(0.85, 0.7, 1.), g * g);
        float al = g * Alpha * 0.6; if (al < 0.003) discard; emit(col * al, al * 0.1);
        return;
    }

    if (isK(K_ORBE)) {
        // ORBE_F: Fragor's sphere, A.x = uHeat
        float n = fbm3(vO * 3. + vec3(0., -Time * 0.8, Time * 0.3)) * 0.5 + 0.5;
        float k = clamp(f + (n - 0.5) * 0.25, 0., 1.);
        vec3 rim = vec3(0.75, 0.12, 0.03), body = vec3(1.0, 0.62, 0.12), core = vec3(1.0, 0.97, 0.82);
        vec3 c = mix(rim, body, smoothstep(0.08, 0.5, k)); c = mix(c, core, smoothstep(0.62, 0.95, k) * A.x);
        if (Alpha < 0.003) discard;
        emit(c * Alpha * 1.15, Alpha);
        return;
    }

    if (isK(K_EHALO)) {
        float g = pow(smoothstep(0., 0.9, f), 2.2); vec3 c = mix(vec3(0.9, 0.3, 0.05), vec3(1., 0.75, 0.3), g); float a = g * Alpha * 0.5;
        if (a < 0.003) discard; emit(c * a, a * 0.1);
        return;
    }

    if (isK(K_SPIKE)) {
        float vY = vU.y;
        vec3 c = mix(vec3(0.95, 0.85, 1.), vec3(0.42, 0.1, 0.85), smoothstep(0.0, 0.6, vY)); c = mix(c, vec3(0.16, 0.04, 0.35), smoothstep(0.75, 1., vY));
        if (Alpha < 0.003) discard;
        emit(c * Alpha, Alpha);
        return;
    }

    if (isK(K_CORE)) {
        vec3 c = mix(vec3(0.4, 0.1, 0.9), vec3(1., 0.95, 1.), pow(f, 1.5));
        if (Alpha < 0.003) discard;
        emit(c * 1.3 * Alpha, Alpha);
        return;
    }

    if (isK(K_CRACK)) {
        // CRACK_F: the golden cracks round Hado 99's target, A.x = uR (the plane is 110 across)
        float uR = A.x;
        vec2 p = (vU - 0.5) * 110.; float r = length(p), th = atan(p.y, p.x);
        vec2 g = p * 0.17 + vec2(gn(vec3(p * 0.05, 1.)), gn(vec3(p * 0.05, 7.))) * 0.9;
        vec2 ip = floor(g); float F1 = 9., F2 = 9.;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) { vec2 c = ip + vec2(x, y); vec2 qq = c + h2(c); float d = length(g - qq); if (d < F1) { F2 = F1; F1 = d; } else if (d < F2) F2 = d; }
        float keep = smoothstep(0.42, 0.6, gn(vec3(g * 0.55, 3.)) * 0.5 + 0.5);
        float reach = uR * (0.8 + 0.4 * (gn(vec3(cos(th) * 2., sin(th) * 2., 1.)) * 0.5 + 0.5));
        float on = 1. - smoothstep(reach - 5., reach, r);
        float w = 0.025 + 0.03 * on;
        float fine = (1. - smoothstep(w * 0.4, w, F2 - F1)) * keep;
        float main1 = 0.;
        for (int k = 0; k < 9; k++) {
            float fk = float(k); float ang = fk / 9. * 6.2831 + (hs(fk + 2.) - 0.5) * 0.6;
            float jit = gn(vec3(r * 0.1, fk * 5.3, 1.)) * 0.35 + gn(vec3(r * 0.4, fk * 2.1, 4.)) * 0.08;
            float da = abs(mod(th - ang + jit + 3.14159, 6.2831) - 3.14159) * r; float wd = 0.7 * (1. - smoothstep(0., reach, r)) + 0.15;
            main1 = max(main1, 1. - smoothstep(wd * 0.5, wd, da));
        }
        float line = max(fine * 0.8, main1) * on;
        float glow = max((1. - smoothstep(w, w * 6., F2 - F1)) * keep * 0.5, main1) * on;
        float pulse = 0.75 + 0.25 * sin(Time * 6. - r * 0.25);
        vec3 c = vec3(1.0, 0.62, 0.12) * glow * 0.55 * pulse + vec3(1., 0.9, 0.6) * line * 0.85;
        float a = max(line, glow * 0.4) * Alpha * (1. - smoothstep(46., 54., r)); if (a < 0.003) discard;
        emit(c * Alpha, a);
        return;
    }

    if (isK(K_RA)) {
        // RA_F: the black flames of the Reiatsu
        float dens = fbm4(vO * vec3(2.4, 1.5, 2.4) - vec3(0., Time * 2.6, 0.)) * 0.5 + 0.5 + 0.3 * f - 0.5 * vH;
        float thr = 0.52, aa = max(fwidth(dens), 1e-3), m = smoothstep(thr - aa, thr + aa, dens); if (m < 0.003) discard;
        float edge = 1. - smoothstep(thr, thr + 0.11, dens);
        vec3 col = mix(vec3(0.025, 0.01, 0.06), vec3(0.62, 0.28, 1.0), edge); col = mix(col, vec3(0.9, 0.8, 1.), edge * edge * 0.5);
        float a = m * Alpha * mix(0.75, 0.95, edge); emit(col * a, a);
        return;
    }

    if (isK(K_PDOME)) {
        float rim = pow(1. - f, 3.);
        float ang = atan(vO.z, vO.x), id = floor(ang * 70.); float on = step(0.82, hs1(id));
        float sg = fract(vO.y * 2.2 + Time * (1.6 + hs1(id + 3.) * 1.4) + hs1(id + 7.) * 10.);
        float line = on * smoothstep(0.0, 0.05, sg) * (1. - smoothstep(0.1, 0.3, sg)) * (1. - smoothstep(0.5, 1., vO.y));
        float w = gn(vec3(vO * 3. + Time * 0.3)) * 0.5 + 0.5;
        float a = (rim * (0.08 + 0.1 * w) + line * 0.4) * Alpha; if (a < 0.003) discard;
        vec3 col = mix(vec3(0.3, 0.1, 0.7), vec3(0.75, 0.55, 1.), line);
        emit(col * a, a * 0.7);
        return;
    }

    if (isK(K_WING)) {
        // WING_F: A.x = uGlow; vU.x = aR (0 root .. 1 edge), vO.xy = the wing's own plane
        float vR = vU.x; vec2 vQw = vO.xy;
        float ang = atan(vQw.y, vQw.x);
        float vein = 1. - smoothstep(0.0, 0.05, abs(fract(ang * 4.2 + gn(vec3(vQw * 0.8, 1.)) * 0.35) - 0.5) - 0.43);
        float lit = 0.78 + 0.22 * f + 0.08 * gn(vec3(vQw * 2.5, Time * 0.2));
        vec3 c = mix(vec3(0.80, 0.78, 0.88), vec3(0.97, 0.96, 1.0), smoothstep(0.55, 0.95, lit));
        c = mix(c, vec3(0.70, 0.62, 0.90), vein * 0.35 * smoothstep(0.1, 0.5, vR));
        float edge = smoothstep(0.78, 0.97, vR);
        c = mix(c, vec3(0.62, 0.38, 1.0), edge * 0.55);
        c += vec3(0.55, 0.25, 1.0) * A.x * edge * 1.4;
        c = mix(c, vec3(0.16, 0.08, 0.3), smoothstep(0.975, 1.0, vR));
        if (Alpha < 0.99 && gn(vec3(vQw * 3., 2.)) * 0.5 + 0.5 > Alpha * 1.2) discard;
        emit(c * 0.62 + vec3(0.55, 0.25, 1.0) * A.x * edge * 0.6, 1.);
        return;
    }

    // K_LIT: the page's Lambert pieces (shards, the wing tendrils): TintA color, TintB emissive, Alpha its opacity
    vec3 Nf = gl_FrontFacing ? N : -N;
    float lam = 0.42 + 0.58 * max(dot(Nf, normalize(LIGHT)), 0.);
    vec3 c = TintA * lam + TintB;
    if (Alpha < 0.003) discard;
    emit(c * Alpha, Alpha);
}
