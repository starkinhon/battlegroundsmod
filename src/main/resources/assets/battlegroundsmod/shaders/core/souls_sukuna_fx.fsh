#version 150

// Sukuna's Dismantles (client/fx/SukunaCutsRenderer), the shapes of the approved 3D previews
// (forja/sukuna-desmantelar: crescentMat, trailMat, chipMat, dashMat, sparkMat4, barMat, slitMat; forja/sukuna-corte-mundo
// for the World Cutting Slash, which uses the same ones bigger). Mode picks the shape. Black with a white outline, dry-brush
// edges; output is premultiplied (ONE, 1 - SRC_ALPHA) so black and white both read on any sky.
//   0 crescent: convex side ahead (+v); white-hot when born (Heat 1), then black with a white outline; S0..S1 keeps one
//     broken piece of it; Tear eats it into tatters with white rims.
//   1 trail: the space it swept, streaks flowing back and tattering away (Len: crescent units behind its front).
//   2 chip: a small jagged polygon (N sides, Asp, Rot).
//   3 dash: Kind 0 a white glowing sliver, 1 a black hatched streak with a white rim.
//   4 star: the four-point star.
//   5 bar: a cut line (Shift+Z, the waffle): a spindle tapering to needle tips (Skew), drawn across (Grow), thinning
//     (Close), breaking and burning (Tear); S0..S1 is the part of the whole stroke this quad holds. The quad is twice as
//     tall as the line so the glow has room.
//   6 slit: the groove left on the ground, black with a white rim (Blood: a hint of red).
uniform float Mode;
uniform float A;
uniform float Heat;
uniform float Tear;
uniform float T;
uniform float Seed;
uniform float S0;
uniform float S1;
uniform float TearSeed;
uniform float Len;
uniform float N;
uniform float Asp;
uniform float Rot;
uniform float Kind;
uniform float Grow;
uniform float Close;
uniform float Flash;
uniform float Skew;
uniform float Rough;
uniform float O;
uniform float W;
uniform float Blood;
// The World Cutting Slash (forja/sukuna-corte-mundo): Thin < 1 makes the crescent and its trail thin ("enormous in size,
// not in thickness"), Px 1 keeps every line at least a pixel and a half wide however far, Fine streaks the trail finer
// across (it is 60 times wider than the Z's), Halo is the star's dark halo (stronger far away on a bright sky).
uniform float Thin;
uniform float Px;
uniform float Fine;
uniform float Halo;

in vec2 vUv;
out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }

vec4 crescent() {
    vec2 p = vUv * 2.0 - 1.0;
    vec2 q = p - vec2(0.0, -0.72);
    float r = length(q);
    float s = atan(q.x, q.y);
    float body = 1.0 - s * s;
    float px = length(fwidth(p)) * Px;
    float thinRag = Thin < 0.999 ? Thin * 1.6 : 1.0;
    float rag = ((vn(vec2(s * 60.0 + Seed, 1.0)) - 0.5) * 0.05 + (vn(vec2(s * 170.0 + Seed, 3.0)) - 0.5) * 0.025) * thinRag;
    float Ro = 1.28 + 0.025 * sin(s * 9.0 + Seed) * (Thin < 0.999 ? Thin * 2.0 : 1.0) + rag * pow(max(body, 0.0), 0.5);
    float w = 0.3 * Thin * pow(max(body, 0.0), 0.85);
    float strand = pow(max(0.0, sin(s * 13.0 + Seed * 3.0 + vn(vec2(s * 6.0, T * 1.5)) * 2.5)), 2.5);
    float Ri = Ro - w - strand * 0.32 * Thin * pow(max(body, 0.0), 1.3) * (0.6 + 0.4 * vn(vec2(s * 9.0, T * 3.0)));
    float sd = min(Ro - r, r - Ri);
    if (abs(s) > 1.0) {
        sd = min(sd, -(abs(s) - 1.0) * r);
    }
    // one broken piece: between two jagged break lines (the same lines for both neighbours, so they fit)
    float jag = (vn(vec2(r * 11.0, Seed)) - 0.5) * 0.16 + (vn(vec2(r * 29.0, Seed + 4.0)) - 0.5) * 0.06;
    sd = min(sd, min(s - S0 - jag, S1 + jag - s) * r);
    float n = vn(p * 5.5 + Seed + TearSeed) * 0.65 + vn(p * 13.0 - T * 0.8 + TearSeed) * 0.35;
    float tear = n - Tear * 1.15;
    sd += 1.2 * px;
    float rim = max(0.035 * (Thin < 0.999 ? Thin * 1.4 : 1.0), 0.8 * px);
    float inside = step(0.0, sd) * step(0.0, tear);
    float edge = inside * (1.0 - smoothstep(0.0, rim, min(sd, tear * 0.6)));
    vec3 bodyC = mix(vec3(0.02, 0.01, 0.02), vec3(1.0), Heat);
    float sc = vn(vec2(s * 5.0 + Seed, (Ro - r) / max(w, Thin < 0.999 ? 0.0005 : 0.02) * 3.5));
    float scratch = inside * step(0.8, sc) * step(0.2, body) * (1.0 - Heat);
    vec3 col = mix(bodyC, vec3(1.0), max(edge, scratch));
    float a = inside;
    float ou = max(-sd, 0.0);
    float halo = exp(-ou * mix(20.0, 6.0, Heat) / (Thin < 0.999 ? Thin * 3.0 : 1.0)) * mix(0.35, 0.85, Heat) * step(0.0, tear + 0.05) * step(abs(s), 1.15);
    col = a > 0.0 ? col : vec3(1.0);
    a = max(a, halo);
    return vec4(col, a);
}

vec4 trail() {
    float px = vUv.x * 2.0 - 1.0;
    float back = (1.0 - vUv.y) * Len;
    float arc = -0.72 + sqrt(max(1.28 * 1.28 - px * px, 0.0));
    float s = atan(px, arc + 0.72);
    float body = 1.0 - s * s;
    if (body <= 0.0) {
        return vec4(0.0);
    }
    float dist = back - 0.62 + arc;
    if (dist < 0.05 * Thin) {
        return vec4(0.0);
    }
    float L = Len * body;
    float flow = Fine > 0.5
            ? vn(vec2(px * 48.0 + Seed, dist * 1.3 / Len * 1.6 - T * 0.9)) * 0.65 + vn(vec2(px * 140.0, dist * 3.0 / Len * 1.6 - T * 1.6)) * 0.35
            : vn(vec2(px * 9.0 + Seed, dist * 1.3 - T * 0.9)) * 0.65 + vn(vec2(px * 27.0, dist * 3.0 - T * 1.6)) * 0.35;
    float keep = flow - pow(dist / max(L, 0.01), 1.2) * 0.7 - 0.2;
    if (keep < 0.0) {
        return vec4(0.0);
    }
    float rimv = 1.0 - smoothstep(0.0, Fine > 0.5 ? 0.12 : 0.07, keep);
    vec3 col = mix(vec3(0.02, 0.01, 0.02), vec3(1.0), rimv);
    float a = smoothstep(0.05 * Thin, 0.15 * Thin, dist) * (1.0 - smoothstep(L * 0.7, L, dist));
    return vec4(col, a);
}

vec4 chip() {
    vec2 p = vUv * 2.0 - 1.0;
    p.x *= Asp;
    float c = cos(Rot), s = sin(Rot);
    p = mat2(c, -s, s, c) * p;
    float an = atan(p.y, p.x);
    float seg = 6.2832 / N;
    float d = cos(floor(0.5 + an / seg) * seg - an) * length(p) - 0.55;
    float core = step(d, 0.0);
    float rimv = core * smoothstep(-0.14, 0.0, d);
    vec3 col = mix(vec3(0.02), vec3(1.0), rimv);
    float a = max(core, (1.0 - smoothstep(0.0, 0.08, d)) * 0.9);
    col = core > 0.0 ? col : vec3(1.0);
    return vec4(col, a);
}

vec4 dash() {
    vec2 p = vUv * 2.0 - 1.0;
    float sh = pow(max(1.0 - p.x * p.x, 0.0), 0.8);
    if (Kind < 0.5) {
        float d = abs(p.y) - 0.42 * sh;
        float core = step(d, 0.0);
        float a = max(core, exp(-max(d, 0.0) * 9.0) * 0.55 * sh);
        return vec4(vec3(1.0), a);
    }
    float d = abs(p.y) - 0.5 * sh;
    float hatch = step(0.42, fract(p.x * 9.0 - p.y * 2.6 + Seed));
    float core = step(d, 0.0) * hatch;
    float rimv = core * smoothstep(-0.12, 0.0, d);
    float a = max(core, step(d, 0.1) * hatch * 0.9);
    vec3 col = core > 0.0 ? mix(vec3(0.02), vec3(1.0), rimv) : vec3(1.0);
    return vec4(col, a);
}

float spikes(vec2 p, float k) {
    vec2 q = abs(p);
    float c = exp(-q.y * k) * exp(-q.x * 2.4) + exp(-q.x * k) * exp(-q.y * 2.4);
    vec2 r = abs(vec2(p.x + p.y, p.x - p.y) * 0.7071);
    c += (exp(-r.y * k) * exp(-r.x * 6.0) + exp(-r.x * k) * exp(-r.y * 6.0)) * 0.45;
    return c;
}

// The star is the one shape that is not black and white: white spikes over a faint dark halo. Returned already
// premultiplied (alpha < 0 marks it).
vec4 star() {
    vec2 p = vUv * 2.0 - 1.0;
    float rr = dot(p, p);
    float w = spikes(p, 44.0) + exp(-rr * 70.0) * 1.5;
    float b = spikes(p, 16.0) * 0.55 + exp(-rr * 30.0) * 0.6;
    float aw = clamp(w, 0.0, 1.0);
    float ab = clamp(b, 0.0, 1.0) * Halo;
    return vec4(vec3(aw), -(aw + ab * (1.0 - aw)));
}

vec4 bar() {
    vec2 p = vUv * 2.0 - 1.0;
    float y = abs(p.y) * 2.0;
    float x = vUv.x;
    float X = mix(S0, S1, x);
    float drawn = step(x, Grow);
    float prof = pow(max(sin(3.14159 * pow(clamp(X, 0.0, 1.0), Skew)), 0.0), 0.55);
    float e = (vn(vec2(X * 70.0 + Seed, sign(p.y) * 5.0)) - 0.5) * 0.5 + (vn(vec2(X * 190.0 + Seed * 2.0, sign(p.y) * 9.0)) - 0.5) * 0.3;
    float px = fwidth(p.y) * 2.0 * Px;
    float w = 0.28 * prof * (1.0 - Close) * (1.0 + e * Rough * 0.8);
    float rimW = Px > 0.5 ? max(0.3 * prof * (1.0 - Close * 0.7) * 0.6, 0.8 * px) * step(0.02, prof) : 0.3 * prof * (1.0 - Close * 0.7);
    float d = y - w - 1.2 * px * step(0.05, prof);
    float n = vn(vec2(x * 7.0 + Seed, p.y * 1.5 + Seed)) * 0.75 + vn(vec2(x * 23.0, p.y * 4.0)) * 0.25;
    n = n * 0.8 + 0.2 * abs(x * 2.0 - 1.0);
    if (Tear > 0.0 && n < Tear) {
        return vec4(0.0);
    }
    float burn = Tear > 0.0 ? smoothstep(Tear, Tear + 0.08, n) : 1.0;
    float core = step(d, 0.0) * step(0.001, w) * drawn;
    float rimv = step(d, rimW) * (1.0 - core) * drawn;
    float st = vn(vec2(X * 5.0 + Seed, (p.y * 2.0) / max(w, 0.01) * 3.5));
    float scratch = core * step(0.8 - Rough * 0.12, st) * step(0.25, prof);
    float g = max(d - rimW, 0.0);
    float glow = exp(-g * (11.0 - 4.0 * Flash)) * (0.6 + 0.4 * Flash) * (1.0 - Close) * (1.0 - Tear) * drawn * smoothstep(0.0, 0.25, prof);
    float tip = exp(-pow((x - Grow) * 28.0, 2.0)) * exp(-y * 3.0) * (1.0 - step(0.999, Grow)) * 1.6;
    vec3 col = core > 0.0 ? mix(vec3(1.0), vec3(0.02, 0.01, 0.02), burn * (1.0 - scratch)) : vec3(1.0);
    float a = core > 0.0 || rimv > 0.0 ? 1.0 : glow;
    col = mix(col, vec3(1.0), clamp(tip, 0.0, 1.0) * (1.0 - core));
    a = max(a, clamp(tip, 0.0, 1.0));
    return vec4(col, a);
}

vec4 slit() {
    vec2 p = vUv * 2.0 - 1.0;
    float L = abs(p.x);
    if (L > O) {
        return vec4(0.0);
    }
    float w = max(W * 0.32 * (1.0 - pow(L / max(O, 0.001), 2.0)), 0.01);
    float d = abs(p.y) - w;
    float core = step(d, 0.0);
    float rimv = (1.0 - core) * exp(-d * 60.0);
    float blood = (1.0 - core) * exp(-d * 14.0) * 0.35 * Blood;
    rimv = mix((1.0 - core) * (1.0 - smoothstep(0.05, 0.075, d)), rimv, Blood);
    vec3 col = core > 0.0 ? vec3(0.02, 0.0, 0.01) : mix(vec3(0.6, 0.0, 0.05), vec3(1.0), clamp(rimv * 1.5, 0.0, 1.0));
    float a = clamp(core + rimv + blood, 0.0, 1.0);
    return vec4(col, a);
}

// 7 strip: the plane a waffle line sweeps, streaks fading behind it (Len: blocks behind).
vec4 strip() {
    float back = (1.0 - vUv.y) * Len;
    float lane = floor(vUv.x * 70.0);
    float hl = h21(vec2(lane, Seed)), hl2 = h21(vec2(lane, Seed + 5.0));
    float len = Len * (0.2 + 0.8 * hl);
    float on = step(0.5, hl2) * (1.0 - smoothstep(len * 0.25, len, back));
    float wob = abs(fract(vUv.x * 70.0) - 0.5);
    on *= 1.0 - smoothstep(0.1, 0.3, wob);
    float ends = smoothstep(0.0, 0.08, vUv.x) * smoothstep(1.0, 0.92, vUv.x);
    vec3 c = hl > 0.6 ? vec3(0.03) : vec3(1.0);
    return vec4(c, on * ends * 0.7);
}

void main() {
    vec4 c;
    if (Mode < 0.5) {
        c = crescent();
    } else if (Mode < 1.5) {
        c = trail();
    } else if (Mode < 2.5) {
        c = chip();
    } else if (Mode < 3.5) {
        c = dash();
    } else if (Mode < 4.5) {
        c = star();
        float a = -c.a * A;
        if (a < 0.004) {
            discard;
        }
        fragColor = vec4(c.rgb * A, a);
        return;
    } else if (Mode < 5.5) {
        c = bar();
    } else if (Mode < 6.5) {
        c = slit();
    } else {
        c = strip();
    }
    float a = c.a * A;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb * a, a);
}
