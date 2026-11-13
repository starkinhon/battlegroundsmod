#version 150

// Hashirama's Mokuton wood, the approved page's WOOD_F (tubes: long grain on the tube's own coordinates, grooves, knots,
// fine carved lines, growth rings, cel light in 3 bands, fresh pale wood at the growing tip, splintering away along the
// grain) and WOODS_F (carved surfaces: grain following the piece, late-wood rings, fibres, knots, tone per stretch).
// Bright scales everything by the light where the wood stands, so it is never lit at night like a lamp.
in vec3 vN;
in vec3 vP;
in vec3 vV;
in vec3 vD;
in vec2 vB;
in float vU;
in float vK;
in float vO;
in vec4 vCol;

uniform float Mode;
uniform float Grow;
uniform vec3 Light;
uniform vec3 Wood;
uniform vec3 Dark;
uniform vec3 Tint;
uniform float Amb;
uniform float Bright;
uniform float Seed;
uniform float Fresh;
uniform float Dis;
uniform float Gr;
uniform float Fine;
uniform float Ring;
uniform float Cav;
uniform float Grain;
uniform float Alpha;
uniform vec4 Crack;
uniform vec4 Crack2;

out vec4 fragColor;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}

vec3 tube(vec3 N) {
    float fib = gn(vec3(vB.x * 6., vB.y * 0.7, Seed + 3.)) * 0.5 + 0.5;
    if (Dis > 0. && fib + 0.3 * gn(vec3(vB * 2.5, Seed)) < Dis * 1.4 - 0.2) discard;
    float gr = pow(1. - abs(gn(vec3(vB.x * 1.4, vB.y * 0.16, Seed))), 6.);
    float gr2 = pow(1. - abs(gn(vec3(vB.x * 3.1 + 7., vB.y * 0.3, Seed))), 9.) * 0.6;
    float knot = smoothstep(0.5, 0.8, gn(vec3(vB.x * 0.7, vB.y * 0.35, Seed + 9.)));
    float ndl = dot(N, Light), band = ndl > 0.42 ? 1. : ndl > -0.08 ? 0.72 : 0.5;
    vec3 base = mix(Dark, Wood, 0.5 + 0.5 * fib);
    base = mix(base, Dark * 0.55, clamp(gr + gr2, 0., 1.) * 0.8 * Gr);
    base = mix(base, Dark * 0.7, knot * 0.35);
    if (Fine > 0.) {
        float w = gn(vec3(vB.x * 0.6, vB.y * 0.25, Seed + 5.)) * 4.5 + gn(vec3(vB.x * 2.2, vB.y * 0.9, Seed + 1.)) * 1.3;
        float ph = vB.x * Fine * (0.85 + 0.3 * (gn(vec3(vB.x * 0.4, vB.y * 0.2, Seed + 8.)) * 0.5 + 0.5)) + w;
        float g1 = abs(sin(ph)), f1 = fwidth(ph);
        float wid = 0.06 + 0.16 * (gn(vec3(vB.x * 1.7, vB.y * 0.5, Seed + 4.)) * 0.5 + 0.5);
        float line = (1. - smoothstep(0., f1 * 1.2 + wid, g1)) * smoothstep(-0.3, 0.4, gn(vec3(vB.x * 1.1, vB.y * 0.7, Seed + 6.)));
        base = mix(base, Dark * 0.62, line * 0.42);
        base *= 0.95 + 0.1 * (1. - g1);
    }
    if (Ring > 0.) {
        float rn = gn(vec3(vB.x * 0.8, vB.y * 0.4, Seed));
        float r = abs(sin(vB.y * Ring + rn * 1.4)), fr = fwidth(vB.y * Ring);
        base = mix(base, Dark * 0.55, (1. - smoothstep(0., fr * 1.5 + 0.06, r)) * 0.35 * smoothstep(-0.2, 0.5, gn(vec3(vB.x * 0.9, vB.y * 0.6, Seed + 2.))));
    }
    base = mix(base, vec3(0.9, 0.8, 0.58), (1. - vK) * 0.55 * Fresh);
    float lit = Amb + band * 0.62;
    if (Cav > 0.) {
        float cv = smoothstep(0.25, 1., 0.5 - 0.5 * cos(6.2831853 * vB.x / 1.5));
        base *= mix(1., 0.32, cv * Cav);
        lit = mix(lit, Amb * 0.8 + band * 0.35, cv * Cav * 0.6);
    }
    return base * lit;
}

vec3 surface(vec3 N) {
    vec3 D = normalize(vD), X = normalize(cross(D, abs(D.y) < 0.9 ? vec3(0., 1., 0.) : vec3(1., 0., 0.))), Y = cross(D, X);
    float al = dot(vP, D);
    vec2 pp = vec2(dot(vP, X), dot(vP, Y));
    float kn = smoothstep(0.5, 0.8, gn(vec3(pp * 0.45, al * 0.22) + Seed * 1.7 + 11.));
    float warp = gn(vec3(pp * 0.3, al * 0.018) + Seed) * 2.2 + gn(vec3(pp * 1.1, al * 0.05) + Seed + 3.) * 0.45 + kn * 3.5;
    float ph = (pp.x * 1.0 + pp.y * 0.35) * Grain * 0.55 + warp * 2.0, f1 = fwidth(ph);
    float ring = abs(fract(ph) - 0.5) * 2.;
    float late = smoothstep(0.55 - f1, 0.85 + f1, ring);
    float line = 1. - smoothstep(0., f1 * 1.5 + 0.06, abs(fract(ph * 0.5 + 0.25) - 0.5) * 2.);
    float fib = gn(vec3(pp * 7.5, al * 0.35) + Seed + 7.), fib2 = gn(vec3(pp * 18., al * 0.6) + Seed + 9.);
    float tone = gn(vec3(pp * 0.18, al * 0.03) + Seed + 5.);
    if (Dis > 0. && fib * 0.5 + 0.5 + 0.3 * gn(vP * 1.7) < Dis * 1.4 - 0.2) discard;
    vec3 wood = Wood * (1. + 0.16 * tone);
    vec3 base = mix(wood * 1.06, mix(wood, Dark, 0.34), late);
    base = mix(base, Dark * 0.9, line * 0.32 + kn * 0.35);
    base *= 1. + 0.12 * fib + 0.06 * fib2;
    float ndl = dot(N, Light), band = ndl > 0.42 ? 1. : ndl > -0.08 ? 0.72 : 0.5, lit = mix(band, 0.55 + 0.45 * ndl, 0.45);
    base *= mix(0.38, 1., vO);
    base *= mix(0.6, 1., smoothstep(0.05, 0.6, abs(dot(N, normalize(vV)))));
    return base * (Amb + lit * 0.62);
}

// ---------- ground warnings (the page's CRACK_F and SHADOW_F) ----------
vec2 h22(vec2 p) { p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3))); return fract(sin(p) * 43758.5453); }
float vedge(vec2 x) {
    vec2 n = floor(x), f = fract(x), mg = vec2(0.), mr = vec2(0.);
    float md = 8.;
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) { vec2 g = vec2(float(i), float(j)), r = g + h22(n + g) - f; float d = dot(r, r); if (d < md) { md = d; mr = r; mg = g; } }
    md = 8.;
    for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) { vec2 g = mg + vec2(float(i), float(j)), r = g + h22(n + g) - f; if (dot(mr - r, mr - r) > 1e-5) md = min(md, dot(0.5 * (mr + r), normalize(r - mr))); }
    return md;
}

// Crack = (origin.xy, dir.xy) in page xz; Crack2 = (front, tan, mode, half); Alpha = uA; Seed = uSeed. vP.xz = page xz.
vec4 crack() {
    vec2 uO = Crack.xy, uD = Crack.zw;
    float uFront = Crack2.x, uTan = Crack2.y, uMode = Crack2.z, uHalf = Crack2.w;
    vec2 p = vP.xz - uO;
    float m = 0., mainL = 0.;
    if (uMode < 0.5) {
        float d = dot(p, uD), lat = p.x * uD.y - p.y * uD.x, w = d * uTan + 0.9;
        m = step(0.4, d) * (1. - smoothstep(w * 0.6, w, abs(lat))) * (1. - smoothstep(uFront - 1.5, uFront, d));
        float l2 = lat + sin(d * 1.3 + Seed) * 0.35 + sin(d * 3.7 + Seed * 2.) * 0.12;
        mainL = (1. - smoothstep(0.05, 0.16, abs(l2))) * step(0.4, d) * (1. - smoothstep(uFront - 0.4, uFront, d));
    } else if (uMode < 1.5) {
        vec2 q = abs(p);
        float e = max(q.x, q.y);
        m = (1. - smoothstep(0.3, 1.0, abs(e - uHalf))) * clamp(uFront, 0., 1.);
        mainL = (1. - smoothstep(0.04, 0.12, abs(e - uHalf + sin(p.x * 2.1 + p.y * 1.7 + Seed) * 0.08))) * clamp(uFront * 1.5, 0., 1.);
    } else {
        float rr = length(p), an = atan(p.y, p.x);
        m = (1. - smoothstep(uHalf - 0.2, uHalf + 0.5, rr)) * clamp(uFront, 0., 1.);
        float sp = abs(sin(an * 3.5 + Seed + sin(rr * 2.3 + Seed) * 0.6));
        mainL = (1. - smoothstep(0.03, 0.09, sp * rr * 0.55)) * (1. - smoothstep(uHalf * 0.85, uHalf + 0.3, rr)) * clamp(uFront * 1.5, 0., 1.)
            + (1. - smoothstep(0.03, 0.09, abs(rr - uHalf + sin(an * 5. + Seed) * 0.18))) * clamp(uFront, 0., 1.);
    }
    vec2 wp = vP.xz * 0.9 + Seed;
    wp += vec2(sin(wp.y * 1.7), cos(wp.x * 1.3)) * 0.25;
    float e = vedge(wp), aa = fwidth(e) * 1.5, line = 1. - smoothstep(0.025, 0.025 + aa + 0.035, e);
    float a = max(line * m, mainL) * Alpha;
    if (a < 0.01) discard;
    return vec4(vec3(0.07, 0.045, 0.03) * a, a * 0.92);
}

// the hand's shadow closing on the ground (Grow = uK), vB = quad coords -1..1
vec4 shadow() {
    vec2 p = vB;
    float r = length(p), a = atan(p.y, p.x), uK = Grow;
    float edge = 0.86 + 0.08 * gn(vec3(cos(a) * 2., sin(a) * 2., Seed));
    float inside = 1. - smoothstep(edge - 0.18, edge, r);
    float ring = (1. - smoothstep(0.0, 0.05, abs(r - edge * mix(1.25, 0.98, uK)))) * smoothstep(0.1, 0.5, uK);
    float al = (inside * (0.15 + 0.55 * uK * uK) + ring * 0.8) * Alpha;
    if (al < 0.01) discard;
    vec3 c = mix(vec3(0.02, 0.015, 0.01), vec3(0.95, 0.42, 0.18), ring * (0.4 + 0.6 * uK));
    return vec4(c * al, al);
}

// a soft round grain facing the camera (pollen, stolen chakra): vB = corner -1..1, vCol = outer color + alpha, Tint = core
vec4 mote() {
    float d = length(vB);
    float a = exp(-d * d * 3.) * (1. - smoothstep(0.75, 1., d)) * vCol.a;
    if (a < 0.01) discard;
    vec3 c = mix(vCol.rgb, Tint, exp(-d * d * 10.));
    return vec4(c * a, a * Alpha);
}

void main() {
    if (Mode > 3.5) {
        fragColor = Mode < 4.5 ? crack() : Mode < 5.5 ? shadow() : mote();
        return;
    }
    if (Mode < 0.5 && vU > Grow) discard;
    vec3 N = normalize(vN);
    if (!gl_FrontFacing) N = -N;
    vec3 c;
    if (Mode < 0.5) {
        c = tube(N) * Bright;
    } else if (Mode < 1.5) {
        c = surface(N) * Bright;
    } else if (Mode < 2.5) {
        // plain lit color (petals, flower disk, eye sockets: the page's Lambert), Tint = its emissive
        c = (vCol.rgb * (0.45 + 0.65 * max(dot(N, Light), 0.)) + Tint) * Bright;
    } else {
        // the dragon's lit eye (page EYE_F): bright yellow facing you, deeper at the rim; it glows, so no Bright
        float f = clamp(dot(N, normalize(vV)), 0., 1.);
        c = mix(vec3(0.55, 0.36, 0.02), vec3(0.98, 0.84, 0.12), smoothstep(0.05, 0.6, f));
        c = mix(c, vec3(1., 0.98, 0.72), smoothstep(0.78, 1., f)) * Tint.x;
    }
    fragColor = vec4(c, 1.);
}
