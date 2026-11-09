#version 150

// Sukuna's Fuga: the explosion (the preview's expFS, shot design v1). ONE fire volume bursting out of the impact: a dome
// that rises into a head on a stem as it grows, tongues licking up out of its skin (the arrow's skin); then it burns from
// the inside out, holes opening where the noise is thin until only rags of flame are left. Coordinates are relative to
// the burst's centre: Cam is the camera there, Bmin/Bmax the box, Ground the floor's height (the fire stops at it).
uniform vec3 Cam;
uniform float R;
uniform float Rise;
uniform float Burn;
uniform float Hot;
uniform float Fy;
uniform float Ground;
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

float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
float body(vec3 q, float n) {
    float dome = length(q * vec3(1.0, 1.15, 1.0)) - R;
    vec3 cap = q - vec3(0.0, Rise, 0.0);
    float head = length(cap * vec3(0.85, 1.4, 0.85)) - R * 0.75 * min(1.0, Rise / max(R, 0.01) + 0.3);
    float stem = length(q.xz) - R * 0.38 * (1.0 - clamp(q.y / max(Rise, 0.01), 0.0, 1.0) * 0.4);
    stem = max(stem, max(-q.y - 0.2, q.y - Rise));
    float d = smin(smin(dome, head, R * 0.35), stem, R * 0.3);
    return d - (n - 0.5) * R * 0.7;
}
void main() {
    vec3 ro = Cam, rd = normalize(vW);
    vec3 i0 = (Bmin - ro) / rd, i1 = (Bmax - ro) / rd;
    vec3 tn = min(i0, i1), tf = max(i0, i1);
    float a = max(max(tn.x, tn.y), max(tn.z, 0.0)), b = min(tf.x, min(tf.y, tf.z));
    if (b <= a) discard;
    float acc = 0.0, peak = 0.0;
    vec3 hitW = vec3(0.0), hitP = vec3(0.0);
    float got = 0.0;
    const int N = 72;
    float dt = (b - a) / float(N);
    float s = a + dt * 0.5;
    for (int k = 0; k < N; k++) {
        vec3 p = ro + rd * s;
        if (p.y < Ground - 0.05) break;
        vec3 w = p / max(R, 0.6);
        float n = fb3(vec3(w.x * 2.6, w.y * 2.6 - Fy, w.z * 2.6));
        float n2 = vn3(vec3(w.x * 6.0, w.y * 6.0 - Fy * 1.7, w.z * 6.0));
        float lick = n * n * n * 2.4 * R * 0.55;
        float d = body(p - vec3(0.0, lick, 0.0), n) - lick * 0.25;
        float holes = Burn * 1.25 - n2 * 0.9 - n * 0.35;
        d = max(d, holes * R * 0.6);
        float dens = clamp(-d * 8.0 / max(R, 0.5), 0.0, 1.0);
        if (got < 0.5 && dens > 0.5) { got = 1.0; hitW = w; hitP = p; }
        acc += dens * dt * 6.0 / max(R * 0.35, 0.4);
        peak = max(peak, dens);
        s += dt;
        if (acc > 3.5) break;
    }
    float v = acc * 0.5 + peak * 0.35;
    if (v < 0.5) discard;
    vec3 sp = vec3(hitW.x * 3.0, hitW.y * 1.3 - Fy * 1.2, hitW.z * 3.0);
    float dd = clamp((v - 0.5) * 0.45, 0.0, 0.5);
    float st = fb3(sp + vec3(3.1, 0.0, 7.7));
    float pk = fb3(sp * vec3(0.9, 0.8, 0.8) + vec3(7.1, 2.3, 1.7));
    float bd = fb3(sp * vec3(0.7, 0.6, 0.6) + vec3(1.3, 8.2, 4.4));
    float de = dd * (0.25 + 1.5 * bd) + Hot * 0.35;
    float hot = 0.06 + de * 0.9 + (st - 0.5) * 0.6;
    if (st > 0.72 && de > 0.18) hot = 0.9;
    if (de > 0.15 && pk < 0.33) hot = min(hot, pk < 0.27 ? 0.18 : 0.38);
    if (dd < 0.1) hot = min(hot, 0.38);
    if (dd < 0.04) hot = 0.05;
    gl_FragDepth = depthOf(hitP - Cam);
    fragColor = vec4(pal(hot), 1.0);
}
