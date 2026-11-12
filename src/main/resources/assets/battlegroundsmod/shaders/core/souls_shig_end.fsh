#version 150

// The wall of dust riding the Decadência's front (the preview's FM_FS): a volume in a ring of radius R and width W,
// up to H high, its density torn by 3D noise rising with time, lit in three anime tones. Part 0 is the near side (the
// cylinder's front faces), Part 1 the far side (its back faces). C and Gy: the centre and the ground, camera-relative.
float h31(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float vn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3. - 2. * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z) * 2. - 1.;
}
float fbm(vec3 p) { return vn(p) * .55 + vn(p * 2.07 + 11.3) * .3 + vn(p * 4.13 + 5.1) * .15; }

in vec3 vW;

uniform vec2 C;
uniform float R;
uniform float W;
uniform float H;
uniform float T;
uniform float A;
uniform float Part;
uniform float Gy;

out vec4 fragColor;

vec2 circ(vec2 o, vec2 d, float r) { float a = dot(d, d), b = dot(o, d), c = dot(o, o) - r * r, h = b * b - a * c; if (h < 0.) return vec2(1e9, -1e9); h = sqrt(h); return vec2((-b - h) / a, (-b + h) / a); }

float dens(vec3 p) {
    p.y -= Gy;
    float r = length(p.xz - C), u = (R - r) / W;
    if (u < -0.1 || u > 1. || p.y < -1.) return 0.;
    float ft = smoothstep(-0.08, 0.1, u) * (1. - smoothstep(0.45, 1., u));
    float n = fbm(vec3(p.x * 0.11, p.y * 0.13 - T * 0.9, p.z * 0.11) + vec3(0., 0., T * 0.2));
    float h = H * (0.35 + 0.65 * smoothstep(0., 0.5, u)) * (0.75 + 0.5 * n);
    return ft * smoothstep(h, h * 0.45, p.y) * clamp(0.55 + 0.9 * n + 0.35 * vn(p * 0.37 + T), 0., 1.6);
}

void main() {
    vec3 rd = normalize(vW);
    vec2 o = -C;
    vec2 to = circ(o, rd.xz, R + W * 0.12), ti = circ(o, rd.xz, max(R - W, 0.01));
    float a0, a1;
    bool inner = ti.x < ti.y;
    float tf = length(vW);
    if (Part < 0.5) {
        if (length(o) < R + W * 0.12) discard;
        a0 = max(to.x, 0.);
        a1 = inner ? ti.x : to.y;
    } else {
        if (inner) {
            a0 = max(ti.y, 0.);
            a1 = to.y;
        } else {
            if (length(o) >= R + W * 0.12) discard;
            a0 = 0.;
            a1 = to.y;
        }
    }
    a1 = min(a1, tf + 0.01);
    if (a1 <= a0) discard;
    vec3 L = normalize(vec3(0.35, 0.85, -0.3));
    float dt = (a1 - a0) / 22., acc = 0.;
    vec3 col = vec3(0.);
    for (int i = 0; i < 22; i++) {
        vec3 p = rd * (a0 + (float(i) + 0.5) * dt);
        float d = dens(p) * 0.5 * dt;
        if (d < 1e-4) continue;
        float sh = dens(p + L * 2.2) + dens(p + L * 4.5) * 0.6, lt = exp(-sh * 1.4);
        float band = mix(0.5, 0.78, smoothstep(0.28, 0.34, lt));
        band = mix(band, 1., smoothstep(0.62, 0.68, lt));
        vec3 c = mix(vec3(0.36, 0.33, 0.30), vec3(0.80, 0.75, 0.67), band);
        col += (1. - acc) * c * min(d, 1.);
        acc += (1. - acc) * min(d, 1.);
        if (acc > 0.97) break;
    }
    if (acc < 0.01) discard;
    fragColor = vec4(col / acc, acc * A);
}
