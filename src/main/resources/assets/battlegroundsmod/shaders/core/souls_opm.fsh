#version 150

// See souls_opm.vsh. Everything comes out premultiplied (color * alpha, alpha), like the page's PREMUL materials; in the
// glow pass (GlowPass 1) only what spills light on the page's bloom (threshold 0.97: the white-hot parts) is drawn.
uniform float Kind;
uniform float T;
uniform float TP;
uniform float Seed;
uniform float Halo;
uniform float Core;
uniform float A;
uniform float Ink;
uniform float Life;
uniform float Fwd;
uniform float RingR;
uniform float RingMax;
uniform float RingW;
uniform float Hole;
uniform vec2 HC;
uniform vec3 ColA;
uniform vec3 ColB;
uniform vec3 Light;
uniform float GlowPass;

in vec3 vN;
in vec3 vV;
in vec3 vP;
in vec2 vUv;
in float vZ;
in float vK;
in float vLife;
in float vA;
in float vSide;
in float vH;

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

void emit(vec3 premul, float a) {
    if (GlowPass > 0.5) {
        float k = smoothstep(0.6, 0.85, dot(premul, vec3(0.299, 0.587, 0.114)));
        if (k < 0.003) discard;
        fragColor = vec4(premul * k * 0.4, 0.);
    } else {
        fragColor = vec4(premul, a);
    }
}

float cloudDens(vec2 p) {
    vec3 q = vec3(p * 0.011, T * 0.01);
    float w = fbm3(q * 0.7 + 3.);
    return fbm4(q + vec3(w, -w, 0.) * 0.9) * 0.5 + 0.5 + 0.12;
}

void main() {
    int k = int(Kind + 0.5);
    if (k == 0) {
        vec3 N = normalize(vN), V = normalize(vV);
        float f = abs(dot(N, V)), rim = 1. - f;
        vec2 cs = vec2(cos(vA), sin(vA));
        float life = min(1., vLife * (1. + Core * 0.8));
        vec3 q = vec3(cs * (2. + Core * 1.5), vZ * (0.035 + Core * 0.02) - T * (5. + Core * 9.));
        float w = fbm3(q * 0.9 + Seed);
        float n = fbm4(vec3(q.xy * 1.3, q.z) + w) * 0.5 + 0.5;
        if (Halo > 0.5) {
            // the halo: a cold glow behind, with rays running along it
            if (Ink > 0.5) discard;
            float rays = pow(gn(vec3(cs * 4., vZ * 0.02 - T * 6.)) * 0.5 + 0.5, 2.);
            float g = pow(f, 1.6) * (0.5 + 0.9 * rays) * (1. - vLife) * (0.6 + 0.8 * vK);
            float al = g * 0.4 * A;
            emit(mix(vec3(0.55, 0.78, 1.), vec3(1.), g) * al, al * 0.15);
            return;
        }
        float dens = 0.3 + n * 0.75 + rim * 0.35 + vK * 0.45;
        float th = mix(0.12, 1.3, pow(life, 1.1));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        float ridge = 1. - abs(gn(vec3(cs * 5. + w, vZ * 0.015 - T * 7.) + Seed + 3.));
        float line = smoothstep(0.9, 0.97, ridge);
        float heat = clamp(f * 0.85 + vK * 0.6 + (n - 0.5) * 0.6 + line * 0.8 - life * 0.7 + Core * 0.45, 0., 1.);
        vec3 col = mix(vec3(0.42, 0.56, 0.74), vec3(0.8, 0.9, 1.), smoothstep(0.12, 0.45, heat));
        col = mix(col, vec3(1.), smoothstep(0.55, 0.95, heat));
        // the torn edge darker (reads against a bright sky)
        float e = smoothstep(th + aa, th + 0.07, dens);
        col = mix(col, vec3(0.3, 0.4, 0.55), e * 0.7);
        float al = m * (mix(0.34 + Core * 0.3, 0.9, pow(rim, 1.2)) + line * 0.5 + vK * 0.3) * (1. - life * 0.45);
        al = min(al, 0.92) * A * smoothstep(1.5, 9., vZ);
        if (Ink > 0.5) col = vec3(0.);
        emit(col * al * 1.1, al);
    } else if (k == 1) {
        // the Mach disc: a torn ring with radial streaks
        if (Ink > 0.5) discard;
        vec2 p = vUv * 2. - 1.;
        float r = length(p);
        vec2 d = p / max(r, 1e-3);
        float n = fbm3(vec3(d * 2.5, r * 2. - T * 3.) + Seed) * 0.5 + 0.5;
        float streak = 1. - abs(gn(vec3(d * 9., Seed + T)));
        float band = exp(-pow((r - 0.62 - n * 0.2) / 0.16, 2.)) + 0.35 * exp(-pow(r / 0.4, 2.));
        float dens = band * (0.6 + 0.8 * n) + smoothstep(0.85, 0.98, streak) * 0.6 * (1. - smoothstep(0.7, 1., r)) * smoothstep(0.2, 0.5, r);
        float m = smoothstep(0.45, 0.55, dens) * (1. - smoothstep(0.85, 1., r));
        if (m < 0.003) discard;
        vec3 col = mix(vec3(0.62, 0.78, 0.95), vec3(1.), smoothstep(0.6, 1.1, dens));
        float al = m * 0.85 * A;
        emit(col * al, al);
    } else if (k == 2) {
        // the steam cone at the fist
        float f = abs(dot(normalize(vN), normalize(vV))), rim = 1. - f;
        float a = atan(vP.z, vP.x);
        vec2 cs = vec2(cos(a), sin(a));
        float n = fbm4(vec3(cs * 2.2, vUv.y * 3. - Life * 2.) + Seed) * 0.5 + 0.5;
        float streak = 1. - abs(gn(vec3(cs * 7., vUv.y * 0.8 + Seed)));
        float dens = n * 0.9 + streak * 0.35 + (1. - vUv.y) * 0.25;
        float th = mix(0.25, 1.25, Life);
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        vec3 col = mix(vec3(0.62, 0.74, 0.88), vec3(1.), smoothstep(0.3, 0.9, dens - th + f * 0.4));
        float al = m * (0.35 + 0.5 * rim) * (1. - Life * 0.5);
        if (Ink > 0.5) {
            col = vec3(0.);
            al = m;
        }
        emit(col * al, al);
    } else if (k == 3) {
        // the dust walls, cel lit in 3 bands
        if (vP.z > Fwd - 3.) discard;
        vec3 q = vec3(vP.x * 0.18, vP.y * 0.25 - T * 0.35, vP.z * 0.09) + Seed;
        float w = fbm3(q * 0.8);
        float n = fbm4(q + vec3(w, -w, w)) * 0.5 + 0.5;
        float dens = n + 0.45 * pow(vSide, 0.6) - 0.35 * smoothstep(0.4, 1., vH);
        float th = mix(0.42, 1.2, pow(vLife, 0.9));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        float lit = dot(normalize(vN), normalize(Light)) * 0.5 + 0.5 + (n - 0.5) * 0.7;
        float band = lit > 0.64 ? 1. : lit > 0.4 ? 0.78 : 0.56;
        vec3 col = ColA * band * mix(0.7, 1., smoothstep(th, th + 0.1, dens));
        float al = m * 0.97 * (1. - smoothstep(0.75, 1., vLife));
        if (al < 0.003) discard;
        if (GlowPass > 0.5) discard;
        fragColor = vec4(col * al, al);
    } else if (k == 4) {
        // the dust ring running out over the ground
        vec2 p = (vUv * 2. - 1.) * RingMax;
        float r = length(p);
        vec2 dir = p / max(r, 1e-3);
        float n = fbm3(vec3(dir * 2.4 + Seed, r * 0.25 - Life * 2.)) * 0.5 + 0.5;
        float band = (r - RingR) / RingW;
        float dens = exp(-band * band * 1.6) * (0.5 + 0.9 * n) * (1. - smoothstep(RingMax * 0.85, RingMax, r));
        float a = clamp(dens, 0., 1.) * pow(1. - Life, 1.4) * 0.8;
        if (a < 0.003 || GlowPass > 0.5) discard;
        vec3 col = mix(ColA, ColB, smoothstep(0.2, 1., -band + n * 0.5));
        fragColor = vec4(col * a, a);
    } else if (k == 5) {
        // the sneeze: cel mist in 3 bands with hard droplets where the shell is dense
        vec3 N = normalize(vN), V = normalize(vV);
        float f = abs(dot(N, V)), rim = 1. - f;
        vec3 q = vP * vec3(0.08, 0.1, 0.05) - vec3(0., T * 0.2, T * 2.2) + Seed;
        float w = fbm3(q * 0.8);
        float n = fbm4(q + vec3(w, -w, w) * 1.3) * 0.5 + 0.5;
        float dens = n * 1.15 + rim * 0.12 + vK * 0.35 + 0.05;
        float th = mix(0.5, 1.35, pow(vLife, 1.05));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        float n2 = fbm3(q * 2.3 + 5.);
        float lit = dot(N, normalize(Light)) * 0.35 + 0.5 + (n - 0.5) * 1.1 + n2 * 0.35;
        vec3 col = lit > 0.62 ? vec3(0.97, 0.99, 1.) : lit > 0.4 ? vec3(0.8, 0.88, 0.96) : vec3(0.6, 0.7, 0.82);
        col = mix(col, vec3(0.42, 0.52, 0.66), smoothstep(th + aa, th + 0.06, dens) * 0.6);
        vec3 g = vP * 0.7;
        vec3 gi = floor(g);
        float hh = fract(sin(dot(gi, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
        float drop = step(0.95, hh) * (1. - smoothstep(0.1, 0.18, length(fract(g) - 0.5))) * (1. - vLife) * (1. - vK);
        col = mix(col, vec3(1.), drop);
        float al = m * 0.96 * (1. - vLife * 0.25) * A * smoothstep(0.5, 4., vZ);
        al = min(al, 0.95);
        if (Ink > 0.5) col = vec3(0.);
        emit(col * al, al);
    } else if (k == 6 || k == 7) {
        // the clouds round the hole (6), or the hole itself (7: depth only, where the game's clouds must not show)
        vec2 d = vec2(vP.x, -vP.y) * RingMax;
        vec2 p = d + HC;
        d.y /= 1.7;
        float r = length(d);
        vec2 dir = d / max(r, 1e-3);
        float edgeN = gn(vec3(dir * 3., 7.)) * 45. + gn(vec3(dir * 9., 2.)) * 14.;
        float R = Hole + edgeN * smoothstep(0., 60., Hole);
        if (k == 7) {
            if (r > R + 6.) discard;
            fragColor = vec4(0.);
            return;
        }
        if (Ink > 0.5 || GlowPass > 0.5) discard;
        float push = 70. * exp(-pow((r - R) / 55., 2.)) * smoothstep(0., 30., Hole);
        vec2 ps = p - vec2(dir.x, dir.y * 1.7) * push;
        float n = cloudDens(ps);
        n += 0.45 * exp(-pow((r - R - 18.) / 26., 2.)) * smoothstep(0., 40., Hole);
        n *= smoothstep(R - 4., R + 10., r);
        float cover = smoothstep(0.47, 0.55, n);
        // only the clouds piled up round the hole: further out the game's own clouds take over
        float near = 1. - smoothstep(R + 40., R + 110., r);
        float a = cover * near * 0.95 * A;
        if (a < 0.004) discard;
        float sh = cloudDens(ps + vec2(-16., 10.)) - n;
        float lit = 0.6 + sh * 2.2 + (n - 0.6) * 0.8;
        vec3 col = lit > 0.62 ? vec3(1.) : lit > 0.42 ? vec3(0.86, 0.9, 0.96) : vec3(0.68, 0.74, 0.84);
        col *= ColA;
        fragColor = vec4(col * a, a);
    } else if (k == 8) {
        // a small hit flash: a shell lit from the middle, each with its own noise
        if (Ink > 0.5) discard;
        float f = abs(dot(normalize(vN), normalize(vV)));
        float n = fbm3(vP * 2.5 + Seed) * 0.5 + 0.5;
        float core = pow(f, 3.) * (0.6 + 0.8 * n);
        vec3 c = mix(ColA, vec3(1.), core);
        float a = (core * 0.9 + 0.15 * pow(1. - f, 2.)) * A;
        emit(c * a * 1.6, a * 0.2);
    } else if (k == 9) {
        // a ghost arm of the Socos Consecutivos (sleeve or glove)
        if (Ink > 0.5 || GlowPass > 0.5) discard;
        vec3 N = normalize(vN);
        float rim = 1. - abs(dot(N, normalize(vV)));
        float lit = dot(N, normalize(mat3(1.) * vec3(0.3, 0.8, 0.4))) > 0.1 ? 1. : 0.78;
        vec3 col = mix(ColA * lit, vec3(1., 0.98, 0.93), 0.12 + 0.55 * pow(rim, 2.));
        float a = A * (0.75 + 0.25 * rim);
        fragColor = vec4(col * a, a);
    } else {
        // the star twinkle on the horizon
        vec2 p = vUv * 2. - 1.;
        float r = length(p);
        float an = atan(p.y, p.x);
        float st = pow(abs(cos(an * 2.)), 40.) * pow(max(0., 1. - r), 1.2) + pow(max(0., 1. - r * 3.), 3.);
        float al = clamp(st, 0., 1.) * A;
        if (al < 0.003) discard;
        emit(vec3(1.) * al, al);
    }
}
