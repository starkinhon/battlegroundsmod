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
    if (kind == 17 || kind == 18) {                                    // Inumaki's Cursed Speech: ink rings (forja/inumaki-kit VOICE_F / BIND_F, line by line)
        float ang = atan(vDir.y, vDir.x), a, rm, w;
        if (kind == 17) {
            // Flow = speed (blocks/s), Life = how long a ring lives (s), Pitch = ring density, Time in seconds
            float s = vH * Len;
            float k = (Time - s / Flow) / Life;                        // each ring born as the front passes
            if (k < 0. || k > 1.) discard;
            w = fbm3(vec3(cos(ang) * 1.4, sin(ang) * 1.4, s * 0.3 + Seed));
            float ph = (s + w * 0.9) * Pitch - Time * Flow * Pitch * 0.18;
            float r = fract(ph);
            float body = smoothstep(0.0, 0.05, r) * (1. - smoothstep(0.26 + 0.12 * w, 0.36 + 0.12 * w, r));
            rm = smoothstep(0.22 + 0.12 * w, 0.28 + 0.12 * w, r) * (1. - smoothstep(0.3 + 0.12 * w, 0.36 + 0.12 * w, r));
            float tear = fbm3(vec3(cos(ang) * 3., sin(ang) * 3., s * 0.8 + Seed * 2.)) * 0.5 + 0.5;
            a = body * smoothstep(k * 0.95, k * 0.95 + 0.12, tear);   // tears instead of fading
            a *= (0.55 + 0.45 * rim) * smoothstep(0., 0.6, s) * Alpha;
        } else {
            // Growth = how closed (0..1)
            w = fbm3(vec3(cos(ang) * 1.6, sin(ang) * 1.6, vH * 3. + Seed));
            float r = fract(vH * 4.3 + w * 0.22 + floor(Time * 12.) * 0.013);
            float body = smoothstep(0., 0.04, r) * (1. - smoothstep(0.16, 0.24, r));
            rm = smoothstep(0.13, 0.17, r) * (1. - smoothstep(0.19, 0.24, r));
            float tear = fbm3(vec3(cos(ang) * 4., sin(ang) * 4., vH * 9. + Seed)) * 0.5 + 0.5;
            a = body * smoothstep(1. - Growth, 1.12 - Growth, tear) * smoothstep(0., 0.08, vH) * smoothstep(1., 0.9, vH) * Alpha;
        }
        if (a < 0.01) discard;
        vec3 c = mix(Color, Color2, rm);
        if (kind == 17) c = mix(c, vec3(1.), rm * smoothstep(0.6, 1., w * 0.5 + 0.5) * 0.7);
        if (GlowPass > 0.5) {                                          // only the violet edge glows (the ink stays ink)
            if (rm * a < 0.02) discard;
            fragColor = vec4(Color2 * rm * a * 0.35, 0.);
            return;
        }
        fragColor = vec4(c * a, a);
        return;
    }
    if (kind >= 23 && kind <= 27) {                                    // Miwa, Realista+Anime (forja/miwa-kit SLASH_F / CUT_F / DOMAIN_F / WALL_F / STREAK_F, line by line)
        vec3 c; float a;
        if (kind == 23) {                                              // the draw's arc: Life = uK, Thresh = uFade, RingR = uArc, W = uW, Pitch = uDir, Tail = uTail
            vec2 p = (vUv * 2. - 1.) * 1.25; float r = length(p), ang = atan(p.y, p.x) * Pitch;
            float e = 1. - pow(1. - clamp(Life, 0., 1.), 3.);
            float a0 = -RingR * .5, head = a0 + RingR * e, tail = max(a0, head - RingR * Tail);
            float s = (ang - tail) / max(head - tail, 1e-3);
            if (ang < a0 - 0.02 || s < 0. || s > 1.) discard;
            float w = W * pow(s, 0.7);
            float d = (r - 1.) / max(w, 1e-4);
            if (d > 1.5 || d < -1.3) discard;
            float px = fwidth(d), u = clamp(-d, 0., 1.);
            float tear = fbm3(vec3(ang * 2.2, r * 3., Seed)) * .5 + .5;
            float live = smoothstep(Thresh * 1.2 - 0.15, Thresh * 1.2, tear * 0.85 + s * 0.15);
            float outer = 1. - smoothstep(0., px * 1.5, d);
            float streak = pow(gn(vec3(ang * 2.5, r * 38., Seed)) * .5 + .5, 1.5);
            a = outer * pow(1. - u, 1.3) * (0.55 + 0.6 * streak) * smoothstep(0., 0.12, s) * live;
            c = mix(vec3(0.17, 0.45, 1.), vec3(0.6, 0.87, 1.), smoothstep(0.8, 0.25, u));
            c = mix(c, vec3(0.97, 0.99, 1.), smoothstep(0.22, 0.02, u) * smoothstep(0.2, 0.8, s));
        } else if (kind == 24) {                                       // the cut on the body: Len = uL, W = uH, Life = uDraw, Growth = uOpen, Thresh = uFade, Pulse = uMark
            float x = vUv.x, y = (vUv.y - .5) * 2. * W;
            if (x > Life) discard;
            float env = pow(sin(3.14159 * x), .5), fr = gn(vec3(x * Len * 6., Seed, Time * 2.));
            float g = Growth * 0.075 * env * (0.8 + 0.4 * fr), d = abs(y) - g, px = fwidth(y) * 1.2;
            float tear = fbm3(vec3(x * Len * 3., y * 8., Seed)) * .5 + .5, live = smoothstep(Thresh - 0.1, Thresh + 0.05, tear * 0.9 + 0.1);
            float ma = mix(1., 0.45, Pulse);
            float edge = exp(-pow(d / (px + 0.005), 2.)) * env, inside = step(d, 0.) * step(0.001, g), q = clamp(abs(y) / max(g, 1e-4), 0., 1.);
            float halo = exp(-max(d, 0.) * 22.) * 0.5 * Growth * env;
            vec3 ci = mix(vec3(0.97, 0.99, 1.), vec3(0.55, 0.85, 1.), q), co = mix(vec3(0.16, 0.48, 1.), vec3(0.96, 0.99, 1.), clamp(edge, 0., 1.));
            a = max(max(edge, inside), halo) * live * ma; c = mix(co, ci, inside);
        } else if (kind == 25) {                                       // the domain on the ground: RingMax = uR, Life = uK, Thresh = uFade, Rot = uFA, Phase = uFT
            vec2 p = (vUv * 2. - 1.) * RingMax * 1.15; float r = length(p), ang = atan(p.y, p.x);
            float R = RingMax * (1. - pow(1. - clamp(Life, 0., 1.), 3.)); if (R < 0.01) discard;
            float px = fwidth(r), lv = 1. - Thresh;
            float fd = abs(mod(ang - Rot + 3.14159, 6.28318) - 3.14159), fl = exp(-fd * fd * 8.) * exp(-Phase * 5.);
            float e = r - R, edge = exp(-pow(e / (px + 0.025), 2.)), band = smoothstep(-0.55, 0., e) * step(e, 0.) * 0.55;
            float sw = gn(vec3(ang * 3. - Time * 1.6 + r * 1.1, r * 2.2, Seed)) * .5 + .5;
            float swirl = pow(sw, 4.) * 1.3 * step(r, R) * smoothstep(0.2, R, r);
            float dash = pow(gn(vec3(cos(ang - Time * 2.) * R * 2., sin(ang - Time * 2.) * R * 2., Seed + 3.)) * .5 + .5, 6.) * 3. * exp(-e * e * 40.);
            a = clamp(edge + band * (0.5 + 0.5 * sw) + swirl * 0.5 + dash + fl * (edge * 2. + band), 0., 1.) * lv;
            c = mix(vec3(0.15, 0.45, 1.), vec3(0.62, 0.88, 1.), clamp(band + swirl, 0., 1.));
            c = mix(c, vec3(0.97, 0.99, 1.), clamp(edge * 0.9 + fl + dash * 0.5, 0., 1.));
        } else if (kind == 26) {                                       // the wall of streaks: W = the domain's radius, Alpha = uA
            float ang = atan(vDir.y, vDir.x), h = vH;
            float s = pow(gn(vec3(cos(ang) * W * 6., sin(ang) * W * 6., h * 1.2 - Time * 2.2 + Seed)) * .5 + .5, 7.) * 2.2;
            a = s * pow(1. - h, 2.) * Alpha; if (a < .003) discard;
            c = mix(vec3(0.3, 0.65, 1.), vec3(0.9, 0.97, 1.), smoothstep(0.4, 1., s));
            if (GlowPass > 0.5) { fragColor = vec4(c * a * 0.3, 0.); return; }
            fragColor = vec4(c * a, a * 0.6);
            return;
        } else {                                                       // the Line Cut's trail: Len = uL, Life = uK, Thresh = uFade
            float x = vUv.x, y = vUv.y * 2. - 1.;
            float head = 1. - pow(1. - clamp(Life, 0., 1.), 3.); if (x > head) discard;
            float s = x / max(head, 1e-3);
            float tear = fbm3(vec3(x * Len * 1.5, y * 2., Seed)) * .5 + .5, live = smoothstep(Thresh * 1.2 - 0.15, Thresh * 1.2, tear * 0.8 + s * 0.2);
            float w = 0.25 + 0.75 * s, d = abs(y) / w, streak = pow(gn(vec3(x * Len * 0.6, y * 9., Seed)) * .5 + .5, 1.5);
            a = exp(-d * d * 3.) * (0.5 + 0.7 * streak) * live; float core = exp(-d * d * 40.);
            c = mix(vec3(0.17, 0.45, 1.), vec3(0.97, 0.99, 1.), clamp(core + streak * 0.3, 0., 1.));
        }
        a *= Alpha;
        if (a < 0.003) discard;
        if (GlowPass > 0.5) { fragColor = vec4(c * a * 0.45, 0.); return; }
        fragColor = vec4(c * a, a);
        return;
    }
    if (kind >= 19 && kind <= 22) {                                    // Nobara (forja/nobara-kit TRAIL_F / THORN_F / BURST_F / LINK_F, line by line)
        // Color = ink, Color2 = rim; Time in seconds; Life = the preview's uK (age 0..1); Pulse = the thread's pulse
        vec3 c; float a;
        if (kind == 19) {
            float s = vH * Len;
            float n = fbm3(vec3(vDir * 1.3, s * 1.6 + Time * 14. + Seed)) * 0.5 + 0.5;
            a = smoothstep(vH * 1.05 - 0.12, vH * 1.05 + 0.04, n + (1. - vH) * 0.35) * Alpha;
            if (a < 0.01) discard;
            float rm = 1. - smoothstep(0.12, 0.45, f);
            c = mix(Color, Color2, clamp(rm * 0.9 + smoothstep(0.62, 0.85, n) * 0.5 * (1. - vH), 0., 1.));
            c = mix(c, vec3(0.92, 0.97, 1.), rm * (1. - smoothstep(0., 0.25, vH)) * 0.6);
        } else if (kind == 20) {
            float s = vH * Len, q = floor(Time * 12.);
            float v = abs(gn(vec3(vDir * 2.2, s * 1.3 + q * 0.41 + Seed)));
            float vein = 1. - smoothstep(0.015, 0.045, v);
            float tear = fbm3(vec3(vDir * 2.5, s * 2. + Seed * 3.)) * 0.5 + 0.5;
            float th = Life * 1.15 - 0.1 + vH * 0.35 * Life;
            a = smoothstep(th, th + 0.1, tear) * Alpha;
            if (a < 0.01) discard;
            float rm = 1. - smoothstep(0.12, 0.42, f);
            c = mix(Color, Color2, max(rm * 0.7, vein * 0.9));
            c = mix(c, vec3(0.92, 0.97, 1.), vein * step(0.5, fract(q * 0.5 + vH * 2.)) * 0.6 + rm * (1. - Life) * 0.25);
        } else if (kind == 21) {
            float tear = fbm3(vO * 4. + Seed * 2.) * 0.5 + 0.5;
            a = smoothstep(Life - 0.08, Life + 0.06, tear * 0.85 + vD * 0.25) * Alpha;
            if (a < 0.01) discard;
            float rm = 1. - smoothstep(0.08, 0.3, f), hot = 1. - smoothstep(0.04, 0.12, Life), tip = smoothstep(0.55, 0.9, vD);
            c = mix(Color, Color2, clamp(rm * 0.85 + tip * 0.5, 0., 1.));
            c = mix(c, vec3(0.9, 0.96, 1.), max(hot * 0.9, tip * (1. - smoothstep(0., 0.3, Life)) * 0.6));
        } else {
            float s = vH * Len, n = gn(vec3(vDir * 1.5, s * 2. - Time * 3. + Seed)) * 0.5 + 0.5;
            float pulse = exp(-pow((vH - Pulse) * Len * 0.9, 2.));
            float tear = fbm3(vec3(vDir * 2., s * 1.4 + Seed)) * 0.5 + 0.5;
            a = (0.75 + 0.25 * n) * smoothstep(Life - 0.05, Life + 0.08, tear) * Alpha;
            if (a < 0.01) discard;
            c = mix(Color, Color2, clamp(smoothstep(0.55, 0.8, n) * 0.45 + pulse, 0., 1.)); c = mix(c, vec3(1., 0.88, 0.88), pulse * 0.6);
        }
        if (GlowPass > 0.5) {                                          // the ink stays ink: only the lit rim and veins reach the bloom
            fragColor = vec4(c * a * 0.35, 0.);
            return;
        }
        fragColor = vec4(c * a, a);
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
    } else {
        discard;
    }
}
