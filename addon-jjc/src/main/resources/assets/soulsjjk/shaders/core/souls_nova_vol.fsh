#version 150

// Freeza's Supernova landing (approved preview forja/freeza-supernova, "Realista", 2026-11-09): ONE dome of fire
// raymarched in a box, never a pile of copies. The shape is a flattened ellipsoid (radius R, height H) plus a skirt of
// fire hugging the ground past its rim; 3D noise read relative to the impact point, flowing out and up (Phase is
// integrated on the CPU), carves its tongues. As Life goes 0 -> 1 the core burns out (the dome becomes a torn shell),
// the fire cools to dark smoke and rises. Emission + absorption front to back, premultiplied.
uniform vec3 E;
uniform float R;
uniform float H;
uniform float Life;
uniform float Phase;
uniform float Seed;
uniform float Fade;
uniform vec3 Bmin;
uniform vec3 Bmax;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 vW;
out vec4 fragColor;

vec3 h33(vec3 p) { p = fract(p * vec3(0.1031, 0.1030, 0.0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2.0 - 1.0; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)),
          d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)),
          k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0.0, w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

float field(vec3 p, out float temp) {
    vec3 q = p - E;
    q.y -= Life * Life * H * 0.45;                       // at the end the cloud rises
    float r = length(q.xz);
    vec2 dir = q.xz / max(r, 1e-3);
    float yy = max(q.y, 0.0);
    float d = length(vec3(q.x / R, yy / H, q.z / R));
    vec3 np = vec3(q.x, q.y * 1.5, q.z) / R * 3.4;
    np -= vec3(dir.x, 0.0, dir.y) * Phase * 0.55;        // fire running outward
    np.y -= Phase * 0.4;                                 // and up
    float w = fbm3(np * 0.7 + Seed);
    float n = fbm3(np + vec3(w * 1.4, -w, w * 0.9) + Seed * 1.7);
    // the skirt along the ground, past the dome's rim (the low tongues of the reference)
    float skirt = exp(-pow(yy / (H * 0.17), 2.0)) * (1.0 - smoothstep(0.92, 1.2 + n * 0.18, r / R));
    float body = 1.0 - d + n * 0.5;
    float f = max(body, skirt * 0.85 + n * 0.35 - 0.05);
    f -= Life * 0.6 * (1.0 - smoothstep(0.3, 0.9, d));   // the core burns out: a torn shell
    float th = 0.02 + Life * 0.32;
    float dT = mix(d, 0.35 + 0.5 * r / R, clamp(skirt, 0.0, 1.0));
    float lick = gn(np * 3.1 + vec3(0.0, -Phase * 1.3, 0.0) + Seed);
    temp = clamp(1.2 - dT * 0.6 + n * 0.85 + lick * 0.25 - Life * 0.6, 0.0, 1.0);
    return q.y < -0.3 ? 0.0 : smoothstep(th, th + 0.2, f) * Fade;
}

vec3 ramp(float t) {
    float x = t * 4.0;
    vec3 c0 = vec3(0.07, 0.025, 0.02), c1 = vec3(0.5, 0.03, 0.01), c2 = vec3(1.0, 0.17, 0.02), c3 = vec3(1.0, 0.52, 0.07), c4 = vec3(1.0, 0.93, 0.7);
    return x < 1.0 ? mix(c0, c1, x) : x < 2.0 ? mix(c1, c2, x - 1.0) : x < 3.0 ? mix(c2, c3, x - 2.0) : mix(c3, c4, clamp(x - 3.0, 0.0, 1.0));
}

// Depth of a camera-relative point, so walls in front hide the fire and bodies in front of it stay in front.
float depthOf(vec3 rel) { vec4 c = ProjMat * ModelViewMat * vec4(rel, 1.0); return c.z / c.w * 0.5 + 0.5; }

void main() {
    vec3 rd = normalize(vW);
    // Only march inside the ellipsoid that holds the fire (not the whole box): most of the box's pixels are empty
    // and were costing a full march each (the lag Stark saw, 2026-11-09; the rule from #236).
    vec3 rad = vec3((Bmax.x - Bmin.x) * 0.5, Bmax.y - E.y, (Bmax.z - Bmin.z) * 0.5);
    vec3 oc = -E / rad, dr = rd / rad;
    float qa = dot(dr, dr), qb = dot(oc, dr), qc = dot(oc, oc) - 1.0;
    float disc = qb * qb - qa * qc;
    if (disc <= 0.0) discard;
    float sq = sqrt(disc);
    float t0 = max((-qb - sq) / qa, 0.0), t1 = (-qb + sq) / qa;
    if (rd.y < 0.0) t1 = min(t1, (E.y - 0.3) / rd.y);     // stops at the ground
    if (t1 <= t0) discard;
    const int STEPS = 44;
    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 acc = vec3(0.0);
    float T = 1.0;
    float hitT = -1.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = rd * t;
        float temp;
        float dn = field(p, temp);
        if (dn > 0.002) {
            if (hitT < 0.0 && dn > 0.3) hitT = t;
            float a = 1.0 - exp(-dn * dt * mix(0.9, 0.55, temp));
            vec3 col = ramp(temp) * mix(0.25, 2.3, smoothstep(0.08, 0.7, temp)) * mix(0.3, 1.0, smoothstep(0.05, 0.7, dn));
            acc += T * a * col;
            T *= 1.0 - a;
            if (T < 0.015) break;
        }
        t += dt;
    }
    float al = 1.0 - T;
    if (al < 0.003) discard;
    gl_FragDepth = depthOf(rd * (hitT > 0.0 ? hitT : t0));
    fragColor = vec4(acc, al);
}
