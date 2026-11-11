#version 150

// See souls_ki.vsh. Every effect comes out premultiplied (color * alpha, alpha); in the glow pass (GlowPass 1) the same
// light goes into the bloom layer instead, added (alpha 0), dimmer.
uniform float Kind;
uniform float Life;
uniform float Seed;
uniform float Time;
uniform float Smoke;
uniform float Skirt;
uniform vec3 Color;
uniform vec3 Color2;
uniform vec3 LightDir;
uniform float Alpha;
uniform float Tail;
uniform float Len;
uniform float Flow;
uniform float Pulse;
uniform float Thresh;
uniform float Growth;
uniform float Phase;
uniform float RingR;
uniform float RingMax;
uniform vec3 Dust;
uniform float Rot;
uniform float Teeth;
uniform float Pitch;
uniform float Twist;
uniform float GlowPass;
uniform float W;
uniform vec3 Size;

in vec3 vN;
in vec3 vV;
in vec3 vO;
in float vD;
in float vU;
in vec2 vQ;
in float vH;
in vec2 vUv;
in vec2 vDir;
in vec3 vNv;
in vec4 vC;

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
vec3 bright(vec3 c) { return c / max(max(c.r, max(c.g, c.b)), 1e-4); }

// Balls, halos and blast domes glow like the pages' bloom (threshold 0.6): only their white-hot parts spill light, so the
// colored body keeps its color (all of it in the glow layer washed the Genki Dama and the Supernova out). The rest (aura,
// beams, discs, wind) keeps the full glow.
uniform float HighPass;

void emit(vec3 premul, float a) {
    if (GlowPass > 0.5) {
        if (HighPass > 0.5) {
            float k = smoothstep(0.55, 0.7, dot(premul, vec3(0.299, 0.587, 0.114)));
            if (k < 0.003) discard;
            fragColor = vec4(premul * k * 0.5, 0.);
            return;
        }
        fragColor = vec4(premul * 0.45, 0.);
    } else {
        fragColor = vec4(premul, a);
    }
}

// How much a ball faces the camera: 0 on its outline, 1 in the middle of the disc it makes on the screen. On the pages
// the camera is always far, so dot(N, V) does that; here the ball can hang right over a third-person camera, and then
// every point seen is at a grazing angle (dot(N, V) near 0 all over), so the whole ball took the outline's dark color.
// Measured instead from where the pixel sits in the ball's disc on the screen, the ball looks like the page's ball at
// any distance, only bigger.
// Sasuke's Katon: the Supernova's fire ramp (approved) cut in flat anime bands.
vec3 katonRamp(float t) {
    float x = clamp(t, 0., 1.) * 4.; float fl = floor(x); x = fl + smoothstep(0.3, 0.7, fract(x));
    vec3 c0 = vec3(0.09, 0.03, 0.02), c1 = vec3(0.62, 0.06, 0.01), c2 = vec3(1., 0.24, 0.02), c3 = vec3(1., 0.58, 0.08), c4 = vec3(1., 0.95, 0.72);
    return x < 1. ? mix(c0, c1, x) : x < 2. ? mix(c1, c2, x - 1.) : x < 3. ? mix(c2, c3, x - 2.) : mix(c3, c4, clamp(x - 3., 0., 1.));
}

float facing() {
    float d = length(vC.xyz), s = clamp(vC.w / max(d, 1e-4), 0., 0.999);
    float c = clamp(dot(normalize(-vV), vC.xyz / max(d, 1e-4)), 1e-3, 1.);
    float rho = (sqrt(1. - c * c) / c) / (s / sqrt(1. - s * s));
    return sqrt(clamp(1. - rho * rho, 0., 1.));
}

float spiralBand(float s, float w, out float core) {
    float d = abs(fract(s) - 0.5); float aa = clamp(fwidth(s), 1e-3, 0.05);
    core = 1. - smoothstep(0., w * 0.32, d); return 1. - smoothstep(w * 0.5 - aa, w * 0.5 + aa, d);
}

void main() {
    vec3 N = normalize(vN), V = normalize(vV);
    float f = abs(dot(N, V)), rim = 1. - f;
    int kind = int(Kind + 0.5);
    if (kind == 15 || kind == 16) {                                    // Shikamaru's shadow: ONE flat shape on the ground (an SDF), opaque, no glow
        if (GlowPass > 0.5) discard;
        vec2 q = vQ; float d;
        if (kind == 15) {
            // Len = the whole way to the target, Growth = how far it ran (0..1), Thresh = the blot that catches it, W = width,
            // Twist 1 = his own shadow stretched by the flare (no fingers, thinning out), Pulse = his own round shadow.
            q.y += Len * 0.5;                                          // from his feet (the square sits on the middle of the way)
            float L = max(Len, 0.01), tip = L * Growth;
            float h = clamp(q.y / max(tip, 1e-3), 0., 1.);
            float wob = gn(vec3(h * L * 0.35, Seed, Time * 0.25)) * 0.18;   // the way snakes a little
            float s = abs(q.x - wob * (1. - h) * h * 4.);
            float w = W * (1.05 - 0.45 * sin(3.1416 * h));             // thinner in the middle, like ink running
            if (Twist > 0.5) w = W * (1. - 0.35 * h) - gn(vec3(h * tip * 0.5, 9., Time * 0.3)) * 0.06;
            if (q.y > tip) { s = length(q - vec2(0., tip)); w = W * 0.9; }
            if (q.y < 0.) s = length(q);
            vec2 t = q - vec2(0., tip); float ang = atan(t.y, t.x);
            float fing = Twist > 0.5 ? 0. : smoothstep(0.8, 0., length(t)) * (gn(vec3(ang * 2.2, Seed + 3., Time * 1.6)) * 0.5 + 0.5) * 0.45 * (1. - Thresh);
            d = length(q) - 0.62 * Pulse;
            if (Growth > 0.) d = min(d, s - w - fing);                // the fingers at the tip feel ahead as it runs
            d = min(d, length(q - vec2(0., L)) - 0.75 * Thresh);
        } else {
            // RingR = the flood's radius: its edge licks forward in tongues of its own.
            float ang = atan(q.y, q.x);
            float lick = (gn(vec3(cos(ang) * 3.2, sin(ang) * 3.2, Time * 0.5)) * 0.5 + 0.5) * 1.6 + fbm3(vec3(q * 0.35, Time * 0.2)) * 0.8;
            d = min(length(q) - 0.62, length(q) - RingR - lick * min(1., RingR / 4.));
        }
        d += fbm3(vec3(q * 2.4, Time * 0.35)) * 0.07;                  // a living edge
        float aa = max(fwidth(d), 0.01), a = smoothstep(aa, -aa, d) * Alpha * 0.93;
        if (a < 0.003) discard;
        float edge = smoothstep(-0.09, -0.02, d);                      // a lighter thread on the edge, to read the shape
        fragColor = vec4(mix(vec3(0.018, 0.015, 0.024), vec3(0.09, 0.08, 0.11), edge * 0.6) * a, a);
        return;
    }
    if (kind == 13 || kind == 14) {                                    // Sasuke's Katon (RingR = the ground's height, camera-relative)
        if (-vV.y < RingR) discard;
        float fz = kind == 13 ? facing() : f, edge = 1. - fz;
        float n = fbm3(vO * (kind == 13 ? 3.2 : 1.) + vec3(0., -Time * (Smoke > 0.5 ? 0.25 : 1.3), 0.) + Seed * 1.7);
        float dens, heat;
        if (kind == 13) {
            dens = 0.62 + n * 0.9 - smoothstep(0.35, 1., edge) * 0.55 - vD * 0.55 - Thresh;   // the outline and the tail tear into tongues
            heat = Life * (0.55 + n * 0.55 + fz * fz * 0.5 - vD * 0.75);                       // seen face on: the white core
        } else {
            dens = 0.72 + n * 0.7 - smoothstep(0.45, 1., vH) * 0.9 - Thresh;
            heat = Life * (0.95 - vH * 0.7 + n * 0.4);
        }
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(-aa, aa, dens);
        if (m < 0.003) discard;
        vec3 col = katonRamp(heat);
        if (Smoke > 0.5) {
            float lit = 0.5 + N.y * 0.4 + n * 0.5, b = lit > 0.62 ? 1. : lit > 0.4 ? 0.72 : 0.5;   // smoke lit in 3 bands
            col = mix(col * 0.95, vec3(0.21, 0.18, 0.18) * b * 1.2, smoothstep(0., 0.3, 1. - heat * 2.2));
            float a = m * Alpha * mix(1., 0.62, smoothstep(0.3, 0., heat));
            emit(col * a, a);
        } else {
            float a = m * Alpha * mix(0.75, 1., edge);
            emit(col * (0.85 + 0.35 * smoothstep(0.75, 1., heat)) * a, a * 0.85);
        }
        return;
    }
    if (kind == 0) {                                                   // blast / smoke
        vec3 q = vO * 3. + vec3(0., -Time * (Smoke > 0.5 ? 0.22 : 1.1), 0.) + Seed;
        float w = fbm3(q * 0.8 + 7.) * 0.8; float fire = fbm4(q + vec3(w, 0., -w)) * 0.5 + 0.5;
        float dens = fire + (Smoke > 0.5 ? 0.12 : 0.4) * smoothstep(0.2, 0.9, rim) + vD * 2.2;
        float th = mix(0.2, 1.08, pow(Life, 1.25));
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(th - aa, th + aa, dens);
        if (m < 0.003) discard;
        if (Smoke > 0.5) {
            float lit = dot(N, normalize(LightDir)) * 0.5 + 0.5 + (fire - 0.5) * 0.6;
            float band = lit > 0.62 ? 1. : lit > 0.38 ? 0.74 : 0.52;                  // light in 3 bands
            vec3 col = Color * band * mix(0.62, 1., smoothstep(th, th + 0.1, dens));   // darker rim
            float a = m * 0.85 * (1. - smoothstep(0.8, 1., Life)) * mix(1., 1. - smoothstep(-0.05, 0.55, vO.y + (fire - 0.5) * 0.5), Skirt) * Alpha;
            if (a < 0.003) discard;
            emit(col * a, a);
        } else {
            float heat = clamp(1.15 - Life * 1.25 + (fire - 0.5) * 1.3 + 0.3 * f, 0., 1.);
            vec3 b = bright(Color);
            vec3 col = mix(Color * 0.5, Color, smoothstep(0.05, 0.4, heat)); col = mix(col, mix(b, vec3(1.), 0.55), smoothstep(0.45, 0.95, heat));
            float a = m * mix(0.3, 1., pow(rim, 1.1)) * Alpha;
            emit(col * a * (0.8 + 1.1 * heat), a * 0.6);
        }
    } else if (kind == 1) {                                            // Genki core: light ball, white middle, no dark outline
        float fz = facing();
        float a = Time * 0.35; vec3 o = vO; o.xz = mat2(cos(a), -sin(a), sin(a), cos(a)) * o.xz;
        vec3 q = o * 2.6 + vec3(0., -Time * 0.25, 0.);
        float w = fbm3(q * 0.9 + 4.); float pl = fbm4(q + vec3(w, -w, w) * 1.2) * 0.5 + 0.5;
        float veins = pow(1. - abs(gn(q * 2.2 + w * 2.)), 9.);
        float k = fz + (pl - 0.5) * 0.25;
        vec3 edge = vec3(0.1, 0.48, 0.95), body = vec3(0.3, 0.74, 1.0), wht = vec3(0.92, 0.99, 1.);
        vec3 col = mix(edge, body, smoothstep(0.0, 0.5, k)) * 0.78; col = mix(col, wht, smoothstep(0.72, 1.02, k) * 0.9);
        col = mix(col, wht, veins * 0.35 * (1. - fz));
        emit(col * (1. + 0.25 * Pulse) * Alpha, Alpha);
    } else if (kind == 2 || kind == 8) {                               // halo with rays (Genki blue, sun orange)
        vec3 Nv = normalize(vNv); float ang = atan(Nv.y, Nv.x);         // rays round the screen, as on the pages (view space)
        float rays, g; vec3 col; float al;
        if (kind == 2) {
            rays = pow(gn(vec3(cos(ang) * 9., sin(ang) * 9., Time * 0.6)) * 0.5 + 0.5, 2.5) + 0.5 * pow(gn(vec3(cos(ang) * 23., sin(ang) * 23., Time * 0.9 + 3.)) * 0.5 + 0.5, 3.);
            g = pow(smoothstep(0.0, 0.78, f), 1.8) * (0.5 + 0.9 * rays);
            col = mix(vec3(0.15, 0.6, 1.0), vec3(0.6, 0.9, 1.), g * g); al = g * Alpha * 0.5;
        } else {
            rays = pow(gn(vec3(cos(ang) * 8., sin(ang) * 8., Time * 0.5)) * 0.5 + 0.5, 2.5) + 0.5 * pow(gn(vec3(cos(ang) * 21., sin(ang) * 21., Time * 0.8 + 3.)) * 0.5 + 0.5, 3.);
            g = pow(smoothstep(0.0, 0.8, f), 1.7) * (0.55 + 0.9 * rays);
            col = mix(vec3(1.0, 0.22, 0.03), vec3(1.0, 0.7, 0.3), g * g); al = g * Alpha * 0.75;
        }
        if (al < 0.002) discard;
        emit(col * al, al * 0.1);
    } else if (kind == 3) {                                            // ki beam
        vec3 q = vec3(cos(vQ.x) * 1.6, sin(vQ.x) * 1.6, vQ.y * 1.1 - Flow * 1.4);
        float fire = fbm4(q + fbm3(q * 0.7 + 5.) * 0.8) * 0.5 + 0.5;
        float dens = fire + 0.55 * f + 0.45 * vD - 0.08;
        float tail = smoothstep(Tail - 0.4, Tail + 0.6, vU);            // the end of the beam leaves the hand
        dens -= (1. - tail) * 1.2;
        float aa = max(fwidth(dens), 1e-3), m = smoothstep(0.55 - aa, 0.55 + aa, dens);
        if (m < 0.003) discard;
        float heat = clamp(pow(f, 2.) * 1.1 + (fire - 0.5) * 0.9, 0., 1.);
        vec3 b = bright(Color);
        vec3 col = mix(Color * 0.6, Color, smoothstep(0.05, 0.35, heat)); col = mix(col, mix(b, vec3(1.), 0.5), smoothstep(0.55, 0.95, heat));
        float a = m * mix(0.45, 1., f) * Alpha;
        emit(col * a * (0.55 + 0.55 * heat), a * 0.7);
    } else if (kind == 4) {                                            // dust ring running over the ground
        vec2 p = (vUv * 2. - 1.) * RingMax; float r = length(p); vec2 dir = p / max(r, 1e-3);
        float n = fbm3(vec3(dir * 2.2 + Seed, r * 0.55 - Life * 2.5)) * 0.5 + 0.5, n2 = gn(vec3(p * 1.6, Life * 3. + Seed)) * 0.5 + 0.5;
        float w = 0.45 + 1.1 * Life, band = (r - RingR) / w;
        float dens = exp(-band * band * 2.) * (0.55 + 0.9 * n) + 0.3 * smoothstep(0.2, 1., r / max(RingR, 0.01)) * step(r, RingR) * (1. - Life) * n2;
        dens *= 1. - smoothstep(RingMax * 0.82, RingMax, r);
        float a = clamp(dens, 0., 1.) * pow(1. - Life, 1.3) * 0.85 * Alpha; if (a < 0.003) discard;
        vec3 col = mix(Dust, Color, smoothstep(0.4, 1., -band) * (1. - Life));
        if (GlowPass > 0.5) discard;
        emit(col * a, a);
    } else if (kind == 5) {                                            // aura shell
        float face = f;
        vec3 q = vec3(vO.x * 2.3, vO.y * 1.7 - Phase * 1.35, vO.z * 2.3) + Seed * 3.;
        float warp = fbm3(q * 0.8 + 11.) * 0.9; float big = fbm4(q + vec3(warp, 0., -warp)); float fine = gn(q * 3.3 + vec3(0., -Phase * 1.9, 0.));
        float fire = (0.68 * big + 0.32 * fine) * 0.5 + 0.5;
        float dens = fire + 0.42 * smoothstep(0.25, 0.85, rim) + 0.6 * clamp(vD * 1.6, 0., 1.) + 0.18 * (1. - smoothstep(0., 0.3, vH)) - 0.22 * vH * vH;
        float front = Growth * 1.4; dens -= 0.7 * smoothstep(front - 0.3, front, vH);
        float aa = max(fwidth(dens), 1e-3), flame = smoothstep(Thresh - aa, Thresh + aa, dens), edge = 1. - smoothstep(Thresh, Thresh + 0.09, dens);
        float heat = clamp(0.25 + 1.2 * (fire - 0.5) + 0.45 * face - 0.3 * vH + 0.35 * clamp(vD, 0., 1.) - 0.25 * edge, 0., 1.);
        vec3 b = bright(Color);
        vec3 col = mix(Color * 0.55, Color, smoothstep(0.08, 0.45, heat)); col = mix(col, mix(b, vec3(1.), 0.42), smoothstep(0.5, 0.95, heat));
        float a = flame * mix(0.10, 1.0, pow(rim, 1.3)); a = max(a, 0.22 * flame * clamp(vD * 2., 0., 1.)); a = max(a, 0.05 * Growth);
        a *= smoothstep(0., 0.08, vH) * (1. - 0.55 * smoothstep(0.82, 1., vH)) * Alpha; if (a < 0.003) discard;
        emit(col * a * (0.75 + 0.9 * heat), a * 0.55);
    } else if (kind == 6) {                                            // hit flash: a fresnel shell, one per hit
        float n = fbm3(vO * 2.5 + Seed) * 0.5 + 0.5;
        float core = pow(f, 3.) * (0.6 + 0.8 * n); vec3 c = mix(Color, vec3(1.), core); float a = (core * 0.9 + 0.15 * pow(1. - f, 2.)) * Alpha;
        if (a < 0.003) discard;
        emit(c * a * 1.6, a * 0.2);
    } else if (kind == 7) {                                            // the Supernova's red sun
        float fz = facing();
        float a = Time * 0.3; vec3 o = vO; o.xz = mat2(cos(a), -sin(a), sin(a), cos(a)) * o.xz;
        vec3 q = o * 2.4 + vec3(0., -Time * 0.35, 0.);
        float w = fbm3(q * 0.9 + 2.); float pl = fbm4(q + vec3(w, -w, w) * 1.4) * 0.5 + 0.5;
        float cells = pow(1. - abs(gn(q * 2.6 + w * 2.)), 6.);
        float k = fz * 0.75 + (pl - 0.5) * 0.7;
        vec3 deep = vec3(0.32, 0.02, 0.0), mid = vec3(0.95, 0.2, 0.02), hi = vec3(1.0, 0.58, 0.12), wht = vec3(1., 0.9, 0.62);
        vec3 col = mix(deep, mid, smoothstep(0.05, 0.45, k)); col = mix(col, hi, smoothstep(0.45, 0.8, k)); col = mix(col, wht, smoothstep(0.82, 1.05, k + cells * 0.25));
        float aa = fwidth(fz) * 1.5, line = 1. - smoothstep(0.1 - aa, 0.1 + aa, fz);
        col = mix(col, vec3(0.12, 0.0, 0.0), line);
        emit(col * (0.9 + 0.3 * Pulse) * Alpha, Alpha);
    } else if (kind == 9) {                                            // Freeza's saw: 18 slanted teeth
        vec2 p = vUv * 2. - 1.; float r = length(p), a = atan(p.y, p.x) - Rot;
        float u = fract(a / 6.2831853 * 18.);
        float edgeR = 0.78 + 0.15 * u;
        float aa = fwidth(r) * 1.5, body = 1. - smoothstep(edgeR - aa, edgeR + aa, r);
        float spin = fbm3(vec3(cos(a) * r * 3., sin(a) * r * 3., Time * 0.5)) * 0.5 + 0.5;
        float streak = smoothstep(0.5, 0.85, gn(vec3(r * 16., a * 1.5, 1.)) * 0.5 + 0.5);
        float rimD = smoothstep(edgeR - 0.14, edgeR - 0.02, r) * body;
        float hub = (1. - smoothstep(0.14, 0.18, r)) + (1. - smoothstep(0., 0.025, abs(r - 0.42))) * 0.7;
        vec3 b = bright(Color);
        vec3 col = mix(Color * 0.45, Color, spin); col = mix(col, mix(b, vec3(1.), 0.78), clamp(rimD + hub + streak * 0.35, 0., 1.));
        float al = body * (0.3 + 0.7 * clamp(rimD + hub + spin * 0.45 + streak * 0.3, 0., 1.)) * Alpha * (1. - smoothstep(0.95, 1., r));
        if (al < 0.003) discard;
        emit(col * al * 1.15, al * 0.6);
    } else if (kind == 10) {                                           // Kienzan: a thin blade with small saw teeth and rings of ki
        vec2 p = vUv * 2. - 1.; float r = length(p), a = atan(p.y, p.x) - Rot;
        float u = fract(a / 6.2831853 * Teeth), edgeR = 0.86 + 0.1 * pow(u, 1.6);
        float aa = fwidth(r) * 1.5, body = 1. - smoothstep(edgeR - aa, edgeR + aa, r);
        float swirl = fbm3(vec3(r * 9., a * 2.2 + Time * 0.6, Time * 0.4)) * 0.5 + 0.5;
        float rings = smoothstep(0.55, 0.95, sin(r * 38. - Time * 14. + swirl * 4.) * 0.5 + 0.5) * smoothstep(0.2, 0.5, r);
        float rimD = smoothstep(edgeR - 0.1, edgeR - 0.015, r) * body;
        float hub = 1. - smoothstep(0.07, 0.11, r);
        vec3 b = bright(Color);
        vec3 col = mix(Color * 0.55, Color, swirl); col = mix(col, mix(b, vec3(1.), 0.8), clamp(rimD * 1.2 + hub + rings * 0.4, 0., 1.));
        float al = body * clamp(0.12 + rimD + hub + rings * 0.35 + swirl * 0.18, 0., 1.) * Alpha;
        if (al < 0.003) discard;
        emit(col * al * 1.1, al * 0.6);
    } else if (kind == 11) {                                           // Makankosappo: two spirals, yellow and pink, each with its own living thickness
        if (vU < Tail) discard;
        float s = atan(vDir.y, vDir.x) / 6.2832 + vU * Pitch - Time * 5.5;
        float w1 = 0.34 + 0.12 * gn(vec3(vU * 0.8, Time * 3.1, 1.7)), w2 = 0.3 + 0.12 * gn(vec3(vU * 0.9 + 4., Time * 2.6, 7.3));
        float c1, c2; float m1 = spiralBand(s, w1, c1), m2 = spiralBand(s + 0.5 + 0.04 * gn(vec3(vU * 1.3, Time * 4., 3.)), w2, c2);
        float tipFade = smoothstep(0., 0.4, Len - vU);
        float m = max(m1, m2) * tipFade * Alpha; if (m < 0.01) discard;
        vec3 col = m1 >= m2 ? mix(Color, vec3(1.), c1 * 0.75) : mix(Color2, vec3(1., 0.93, 0.97), c2 * 0.6);
        col *= 0.7 + 0.45 * f;
        emit(col * m, m);
    } else if (kind == 12) {                                           // wind: one shell streaked with speed lines
        float s = atan(vDir.y, vDir.x) + vH * Twist;
        float n = gn(vec3(cos(s) * 5.5, sin(s) * 5.5, vH * 2.6 - Flow)) * 0.6 + gn(vec3(cos(s) * 13., sin(s) * 13., vH * 5. - Flow * 1.7)) * 0.4;
        float st = smoothstep(0.18, 0.5, n);
        float ends = smoothstep(0., 0.12, vH) * (1. - smoothstep(0.82, 1., vH));
        float a = st * ends * Alpha * mix(1., 0.55, f); if (a < 0.004) discard;
        vec3 col = mix(Color, vec3(1.), smoothstep(0.45, 0.8, n));
        emit(col * a, a * 0.8);
    } else if (kind == 30) {                                           // Rasengan: glass core, the swirl seen through it (Rot = spin)
        float fz = facing();
        vec3 o = vO; o.xz = mat2(cos(Rot), -sin(Rot), sin(Rot), cos(Rot)) * o.xz;
        float w = fbm3(o * 2.2 + vec3(0., Time * 0.6, 0.)), ang = atan(o.z, o.x);
        float l1 = pow(1. - abs(sin(ang * 3. + o.y * 7. + w * 3.)), 7.), l2 = pow(1. - abs(sin(ang * 5. - o.y * 4. + w * 5. + 1.7)), 9.) * 0.6;
        vec3 rimC = vec3(0.03, 0.26, 0.92), body = vec3(0.22, 0.66, 1.), wh = vec3(0.93, 0.98, 1.);
        vec3 col = mix(rimC, body, smoothstep(0., 0.55, fz)); col = mix(col, wh, smoothstep(0.72, 1.02, fz + (l1 + l2) * 0.3));
        col += wh * (l1 + l2) * 0.45 * (1. - smoothstep(0.4, 0.95, fz));
        float al = mix(1., 0.62, smoothstep(0.3, 0.9, fz)) * Alpha; if (al < 0.003) discard;
        emit(col * al, al);
    } else if (kind == 31) {                                           // Rasengan: three tilted orbits of wind streaks, each streak from the noise
        vec3 o = normalize(vO); float s = 0.;
        for (int i = 0; i < 3; i++) { float fi = float(i);
            vec3 ax = normalize(vec3(sin(fi * 2.1 + 0.3), 1.0 + fi * 0.3, cos(fi * 1.7 + 0.9) * 0.8)); vec3 t1 = normalize(cross(ax, vec3(0.3, 0.1, 1.))), t2 = cross(ax, t1);
            float an = atan(dot(o, t2), dot(o, t1)) - Phase * (1. + fi * 0.37), lat = dot(o, ax);
            float n = gn(vec3(lat * 7. + fi * 5., cos(an) * 1.3, sin(an) * 1.3 + fi * 3.1)) * 0.5 + 0.5;
            s += smoothstep(0.58, 0.8, n) * (1. - smoothstep(0.55, 0.95, abs(lat))); }
        float a = clamp(s, 0., 1.) * (0.35 + 0.65 * pow(rim, 1.2)) * Alpha; if (a < 0.003) discard;
        vec3 col = mix(vec3(0.55, 0.85, 1.), vec3(1.), clamp(s - 0.4, 0., 1.));
        emit(col * a, a * 0.5);
    } else if (kind == 32) {                                           // Rasenshuriken: four saw blades of wind, each its own edge and streaks (Rot = spin)
        vec2 p = vUv * 2. - 1.; float r = length(p), a = atan(p.y, p.x) - Rot;
        float u = fract(a / 6.2831853 * 4.), id = floor(a / 6.2831853 * 4. + 8.);
        float edge = 0.3 + 0.68 * pow(1. - u, 1.35) + 0.07 * gn(vec3(r * 5., u * 4., id * 7.3 + Time * 2.));
        float blade = smoothstep(edge, edge - 0.04, r) * smoothstep(0.16, 0.24, r) * smoothstep(0., 0.05, u);
        float streak = smoothstep(0.35, 0.8, gn(vec3(r * 26., u * 2.5, id * 3.1 + Time * 4.)) * 0.5 + 0.5);
        float blur = smoothstep(1., 0.85, r) * smoothstep(0.15, 0.4, r) * 0.18;
        float hum = exp(-pow((r - 0.98) * 40., 2.)) * 0.5;
        float al = clamp(blade * (0.55 + 0.45 * streak) + blur + hum, 0., 1.) * Alpha; if (al < 0.003) discard;
        vec3 col = mix(vec3(0.55, 0.85, 1.), vec3(1.), blade * streak * 0.8 + smoothstep(0.35, 0.2, r));
        emit(col * al, al * 0.6);
    } else if (kind == 33) {                                           // Kamui: the space turns into a spiral and is swallowed (Size = axis, Rot = spin, Life = how far in)
        vec3 o = normalize(vO); vec3 ax = normalize(Size + vec3(1e-5)); vec3 sx = normalize(cross(ax, abs(ax.y) > 0.9 ? vec3(1., 0., 0.) : vec3(0., 1., 0.))), ux = cross(sx, ax);
        vec2 q = vec2(dot(o, sx), dot(o, ux)); float r = length(q), a = atan(q.y, q.x);
        float arms = sin(a * 4. + log(max(r, 0.03)) * 7. + Rot) * 0.5 + 0.5;
        float n = fbm3(vec3(q * 4., Time * 0.5)) * 0.5 + 0.5;
        float body = smoothstep(0.35, 0.75, arms + (n - 0.5) * 0.6) * (1. - smoothstep(0.92, 1., r));
        float voidc = 1. - smoothstep(0.1 + 0.5 * Life, 0.35 + 0.6 * Life, r);
        vec3 col = mix(Color2, Color, body); col = mix(col, vec3(0.015, 0.01, 0.04), voidc);
        float al = (0.5 * body * smoothstep(0.15, 0.6, r) + 0.95 * voidc) * Alpha * f; if (al < 0.004) discard;
        if (GlowPass > 0.5) { fragColor = vec4(col * al * body * 0.45, 0.); return; }
        fragColor = vec4(col * al, al * 0.85);
    } else if (kind == 34) {                                           // Ninken seal: rings, spokes and writing in dark blood, a stain in the middle (Pulse = it lights up)
        if (GlowPass > 0.5 && Pulse < 0.05) discard;
        vec2 p = vUv * 2. - 1.; float r = length(p);
        float ring = smoothstep(0.035, 0., abs(r - 0.86)) + smoothstep(0.025, 0., abs(r - 0.56)) * 0.8;
        float n = gn(vec3(p * 5., 1.7)) * 0.5 + 0.5;
        float spokes = 0.; for (int i = 0; i < 8; i++) { float ai = float(i) * 0.7854 + 0.2; vec2 d = vec2(cos(ai), sin(ai)); float t = dot(p, d), q2 = abs(dot(p, vec2(-d.y, d.x)));
            float len = 0.62 + 0.2 * fract(sin(float(i) * 12.9898) * 437.5); spokes += smoothstep(0.03, 0.0, q2) * step(0.1, t) * step(t, len); }
        float script = step(0.62, n) * smoothstep(0.62, 0.66, r) * smoothstep(0.84, 0.8, r);
        float blood = smoothstep(0.2, 0., r) * (0.6 + 0.4 * n);
        float m = clamp(ring + spokes * 0.85 + script * 0.7 + blood, 0., 1.) * Alpha; if (m < 0.01) discard;
        vec3 col = mix(vec3(0.32, 0.03, 0.03), vec3(1., 0.35, 0.2), Pulse * (0.6 + 0.4 * sin(Time * 20.)));
        emit(col * m, m * 0.9);
    } else if (kind == 35) {                                           // Gaara's quicksand: a spiral of sand turning into the middle (Rot = spin, Pulse = sinking; Color/Color2/Dust/Size = base/shade/deep/light)
        if (GlowPass > 0.5) discard;
        vec2 p = vUv * 2. - 1.; float r = length(p), a = atan(p.y, p.x);
        float sw = a - Rot + log(max(r, 0.04)) * 2.4;
        float n = fbm3(vec3(cos(sw) * r * 3.2, sin(sw) * r * 3.2, Time * 0.15)) * 0.5 + 0.5;
        float arms = sin(sw * 3. + n * 2.) * 0.5 + 0.5, l = n * 0.65 + arms * 0.35 * smoothstep(1., 0.3, r);
        vec3 col = l > 0.6 ? Color : l > 0.42 ? Color2 : Dust;
        col = mix(col, Dust * 0.55, smoothstep(0.42, 0., r) * (0.45 + 0.4 * Pulse));
        float g = fract(sin(dot(floor((p + vec2(cos(sw), sin(sw)) * 0.02) * 140.), vec2(12.9898, 78.233))) * 43758.5453);
        col = mix(col, Size, step(0.975, g) * 0.3); col *= 0.7;
        float edge = 1. - smoothstep(0.7, 1.0, r + fbm3(vec3(p * 3.4, 2.)) * 0.18);
        float al = edge * Alpha; if (al < 0.003) discard;
        emit(col * al, al);
    } else {
        discard;
    }
}
