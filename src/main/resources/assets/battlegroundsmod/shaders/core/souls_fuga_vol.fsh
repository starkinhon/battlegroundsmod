#version 150

// Sukuna's Fuga: the fire ball that stretches into the arrow, ONE volume of fire raymarched in a box riding the arrow
// (the preview's arrVolFS, approved 2026-11-08). The shape is a distance field in the arrow's own frame (x along it,
// tail at 0, tip at L): a sphere (the ball between the palms) that stretches into an egg and sharpens into the real
// arrow (round shaft, cross broadhead, cross fletching) as M goes 0 -> 1. The fire is the region around it, sampled a
// little ahead and below so it streams back off every edge and rises, carved by 3D noise read in the world (no pattern
// pop when the frame moves); a comet tail tapers behind it. Skin as the ribbons: depth bands, white streaks, red
// pockets, dark-red rim. The flow phases Fx, Fy are integrated on the CPU (a clock times a changing speed raced).
uniform mat4 ToFrame;
uniform mat4 FromFrame;
uniform mat4 ToNoise;
uniform vec3 Ball;
uniform float BR;
uniform float M;
uniform float Fx;
uniform float Fy;
uniform float L;
uniform float FTime;
uniform vec3 Bmin;
uniform vec3 Bmax;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 vW;
out vec4 fragColor;

float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float vn3(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
float fb3(vec3 p) { float a = 0.0, w = 0.5; for (int i = 0; i < 4; i++) { a += w * vn3(p); p = p * 2.07 + vec3(3.1, 1.7, 5.3); w *= 0.5; } return a; }
vec3 pal(float h) { return h > 0.66 ? vec3(1.0, 0.97, 0.84) : h > 0.46 ? vec3(1.0, 0.84, 0.28) : h > 0.26 ? vec3(1.0, 0.52, 0.1) : h > 0.1 ? vec3(0.88, 0.2, 0.05) : vec3(0.5, 0.05, 0.03); }
// Depth of a camera-relative point, so the volume hides behind walls but shows in front of the bodies inside its box.
float depthOf(vec3 rel) { vec4 c = ProjMat * ModelViewMat * vec4(rel, 1.0); return c.z / c.w * 0.5 + 0.5; }

float sdSeg(vec3 p, vec3 a, vec3 b) { vec3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float tri2(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 q0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0), q1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0),
         q2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(q0, q0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(q1, q1), s * (v1.x * e1.y - v1.y * e1.x))),
                 vec2(dot(q2, q2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
float head2(vec2 q) { q.y = abs(q.y);
    float h = min(tri2(q, vec2(L, 0.0), vec2(L - 0.78, 0.34), vec2(L - 0.62, 0.0)), tri2(q, vec2(L - 0.62, 0.0), vec2(L - 0.78, 0.34), vec2(L - 1.02, 0.36)));
    return min(h, tri2(q, vec2(L - 0.62, 0.0), vec2(L - 1.02, 0.36), vec2(L - 0.86, 0.06))); }
float fl2(vec2 q) { q.y = abs(q.y); return min(tri2(q, vec2(0.02, 0.03), vec2(0.12, 0.27), vec2(0.5, 0.05)), tri2(q, vec2(0.12, 0.27), vec2(0.62, 0.22), vec2(0.5, 0.05))); }
float slab(float d2, float w) { return max(d2, abs(w) - 0.035); }
float arrowSd(vec3 p) {
    float sh = sdSeg(p, vec3(0.1, 0.0, 0.0), vec3(L - 0.7, 0.0, 0.0)) - 0.055;
    float hd = min(slab(head2(p.xy), p.z), slab(head2(p.xz), p.y));
    float fe = min(slab(fl2(p.xy), p.z), slab(fl2(p.xz), p.y));
    return min(sh, min(hd, fe));
}
float shape(vec3 p) {
    vec3 c = Ball;
    float sph = length(p - c) - BR;
    if (M <= 0.0) return sph;
    float s1 = smoothstep(0.0, 0.6, M), s2 = smoothstep(0.5, 1.0, M);
    vec3 cc = vec3(mix(c.x, (L + 0.1) * 0.5, s1), c.y * (1.0 - s1), c.z * (1.0 - s1));
    float rx = mix(BR, (L - 0.1) * 0.5, s1);
    float u = clamp((p.x - cc.x) / max(rx, 0.01), -1.0, 1.0);
    float rt = mix(BR, 0.11, s1) * (1.0 + 0.45 * s1 * u);
    vec3 r = vec3(rx, rt, rt);
    vec3 d = p - cc;
    float k0 = length(d / r), k1 = length(d / (r * r));
    float egg = k0 * (k0 - 1.0) / max(k1, 1e-4);
    return mix(egg, arrowSd(p), s2);
}
void main() {
    float t = floor(FTime * 12.0) / 12.0 * 4.0;
    vec3 ro = (ToFrame * vec4(0.0, 0.0, 0.0, 1.0)).xyz, rw = (ToFrame * vec4(vW, 1.0)).xyz, rd = normalize(rw - ro);
    vec3 i0 = (Bmin - ro) / rd, i1 = (Bmax - ro) / rd;
    vec3 tn = min(i0, i1), tf = max(i0, i1);
    float a = max(max(tn.x, tn.y), max(tn.z, 0.0)), b = min(tf.x, min(tf.y, tf.z));
    if (b <= a) discard;
    float acc = 0.0, peak = 0.0;
    vec3 hitW = vec3(0.0), hitP = vec3(0.0);
    float got = 0.0;
    const int N = 64;
    float dt = (b - a) / float(N);
    float s = a + dt * 0.5;
    for (int k = 0; k < N; k++) {
        vec3 p = ro + rd * s;
        vec3 q = p - vec3(0.25, 0.0, 0.0);
        vec3 w = (ToNoise * vec4(q, 1.0)).xyz;
        float n = fb3(vec3(w.x * 3.2, w.y * 3.2 - Fy, (w.z + Fx) * mix(3.2, 2.2, M)));
        float n2 = vn3(vec3(w.x * 7.0, w.y * 7.0 - Fy * 2.0, (w.z + Fx * 0.5) * mix(7.0, 6.0, M)));
        float lick = 0.06 + n * n * n * mix(1.2, 2.1, M);
        float d0 = shape(p);
        float tp = mix(1.0, 0.18 + 0.82 * smoothstep(-0.2, 2.2, q.x), M);
        float fl = shape(q + vec3(lick * 0.55 * tp * M, -lick * tp * mix(0.75, 0.12, M), 0.0)) - lick * 0.5 * tp - (n2 - 0.5) * 0.07 * tp - 0.03;
        fl = min(fl, d0);
        {
            float TL = 2.5;
            float u = clamp(-q.x / TL, 0.0, 1.0);
            float wv = 1.0 - u;
            vec2 c = vec2(q.y - 0.32 * u * u - 0.09 * u * sin(q.x * 2.6 + t * 1.2), q.z - 0.07 * u * cos(q.x * 2.1 + t * 1.0));
            float r = 0.2 * pow(wv, 1.6) + lick * 0.08 * wv - (n2 - 0.5) * 0.03 * wv;
            float td = max(length(c) - r, max(q.x - 0.35, -q.x - TL * M)) + (1.0 - M) * 3.0;
            fl = min(fl, td);
        }
        float dens = clamp(-fl * 11.0, 0.0, 1.0);
        if (got < 0.5 && dens > 0.5) { got = 1.0; hitW = w; hitP = p; }
        acc += dens * dt * 10.0;
        peak = max(peak, dens * (1.0 - clamp(d0 * 4.0, 0.0, 1.0) * 0.75));
        s += dt;
        if (acc > 3.5) break;
    }
    float v = acc * 0.5 + peak * 0.35;
    if (v < 0.5) discard;
    vec3 h = hitW;
    vec3 sb = vec3(h.x * 5.0, h.y * 1.3 - Fy * 1.6, h.z * 5.0), sa = vec3((h.z + Fx * 2.2) * 1.1, h.y * 5.0, h.x * 5.0);
    vec3 sp = mix(sb, sa, M);
    float dd = clamp((v - 0.5) * 0.45, 0.0, 0.5);
    float st = fb3(sp + vec3(3.1, 0.0, 7.7));
    float pk = fb3(sp * vec3(0.9, 0.8, 0.8) + vec3(7.1, 2.3, 1.7));
    float bd = fb3(sp * vec3(0.7, 0.6, 0.6) + vec3(1.3, 8.2, 4.4));
    float de = dd * (0.25 + 1.5 * bd);
    float hot = 0.06 + de * 0.9 + (st - 0.5) * 0.6;
    if (st > 0.72 && de > 0.18) hot = 0.9;
    if (de > 0.15 && pk < 0.33) hot = min(hot, pk < 0.27 ? 0.18 : 0.38);
    if (dd < 0.1) hot = min(hot, 0.38);
    if (dd < 0.04) hot = 0.05;
    gl_FragDepth = depthOf((FromFrame * vec4(got > 0.5 ? hitP : rw, 1.0)).xyz);
    fragColor = vec4(pal(hot), 1.0);
}
