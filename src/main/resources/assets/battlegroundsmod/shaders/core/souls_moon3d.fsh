#version 150

// The approved 3D pages' fragment shader (forja/kokushibo-formas/moon3d.html): the 1st form's cut and moon shading, on
// strips and billboards. The light goes UNDER the body as a warm see-through halo (as souls_moon.fsh), never bloom, so it
// looks the same on a bright sky, at night or in a cave.
in vec2 uv;
in vec4 color;
flat in ivec2 data;

uniform float GameTime;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

void compose(vec3 body, float cov, vec3 glow, float edge) {
    float lum = dot(glow, vec3(0.333));
    float ha = clamp(lum * 0.55, 0.0, 0.85);
    vec3 hc = mix(vec3(1.0, 0.66, 0.30), vec3(1.0, 0.93, 0.72), smoothstep(0.15, 0.8, ha));
    vec3 outCol = body * cov + hc * ha * (1.0 - cov);
    float outA = cov + ha * (1.0 - cov);
    if (outA * edge < 0.002) {
        discard;
    }
    fragColor = vec4(outCol * edge, outA * edge);
}

void main() {
    int kind = data.x / 4096;
    float low = float(data.x - kind * 4096);
    float uTime = GameTime * 1200.0;
    if (kind == 0) {
        // ---- a cut: s along it (0 start, 1 end), q across it (+ = outer, band edge at +-1 at full width)
        float s = uv.x, q0 = uv.y;
        float head = color.r * 1.2, tl = color.g, bandA = color.b;
        int shape = int(color.a * 3.0 + 0.5);
        float seed = low / 100.0, lf = float(data.y) / 100.0;
        float headT = smoothstep(0.0, 0.075, head - s);
        float tailT = pow(smoothstep(0.0, 0.42, s - tl), 0.8);
        float wf = headT * tailT * (0.85 + 0.15 * sin(s * PI));
        if (shape == 2) {
            wf = headT * pow(s, 1.6) * 0.55;                 // streak behind a flying blade
        } else if (shape == 1) {
            wf = headT * tailT * pow(sin(s * PI), 0.75);     // a whole crescent blade: both ends pointed
        }
        if (wf <= 0.0 || bandA <= 0.0) {
            discard;
        }
        float d = abs(q0) - wf;
        float aa = fwidth(q0) * 1.2 + 1e-4;
        float cov = (1.0 - smoothstep(-aa, aa, d)) * bandA;
        float q = clamp(q0 / wf, -1.0, 1.0);
        vec3 cIn = vec3(0.78, 0.40, 0.12), cMid = vec3(0.97, 0.65, 0.28), cOut = vec3(1.0, 0.93, 0.70), cRim = vec3(1.0, 0.995, 0.93);
        vec3 b = mix(cIn, cMid, smoothstep(-1.0, 0.15, q));
        b = mix(b, cOut, smoothstep(0.05, 0.72, q));
        b = mix(b, cRim, smoothstep(0.86, 0.99, q));
        float streak = vnoise(vec2(s * 95.0 * lf + seed * 13.0, q * 2.6)) * vnoise(vec2(s * 31.0 * lf + 7.0, q * 1.3));
        b *= 1.0 - 0.22 * streak * (1.0 - q) * 0.5;
        b = mix(b, cIn * 0.8, 0.25 * smoothstep(-0.55, -1.0, q));
        float burn = mix(1.0, smoothstep(0.0, 0.22, s - tl), clamp(tl * 12.0, 0.0, 1.0));
        float n = fbm(vec2(s * 40.0 * lf + seed * 5.0, q * 3.0));
        cov *= smoothstep(1.0 - burn - 0.1, 1.0 - burn + 0.05, n + 0.35);
        float k = bandA * headT * tailT;
        vec3 glow = vec3(1.0, 0.62, 0.22) * exp(-max(d, 0.0) / 0.6) * 0.55 * k + vec3(1.0, 0.9, 0.6) * exp(-max(d, 0.0) / 0.16) * 0.35 * k;
        float edge = 1.0 - smoothstep(1.7, 2.2, abs(q0));
        compose(b, cov, glow, edge);
        return;
    }
    if (kind == 1) {
        // ---- a moon (same as the 1st form), uv in units of its radius
        float rot = float(data.y) / 1000.0;
        float ca = cos(rot), sa = sin(rot);
        vec2 l = vec2(ca * uv.x + sa * uv.y, -sa * uv.x + ca * uv.y);
        float thin = color.r, alpha = color.g, diss = color.b, cutr = 0.9 + color.a * 0.2;
        float fi = low;
        float dO = length(l) - 1.0;
        float dI = length(l - vec2(thin, 0.0)) - cutr;
        float d = max(dO, -dI);
        float aa = fwidth(d) + 1e-5;
        float cov = 1.0 - smoothstep(-aa, aa, d);
        float ta = max(-dO, 0.0), tb = max(dI, 0.0);
        float t = tb / (ta + tb + 1e-5);
        float core = clamp(2.0 * min(ta, tb) / (ta + tb + 1e-5), 0.0, 1.0);
        float ang = atan(l.y, l.x), rad = length(l);
        float flow = fbm(vec2(ang * 2.2 - uTime * 1.8 + fi, rad * 9.0));
        float grain = vnoise(l * 22.0 + fi * 3.1);
        float heat = core * (0.85 + 0.55 * (flow - 0.5) * 2.0) + 0.10 * (t - 0.5) + 0.06 * (grain - 0.5);
        vec3 cEdge = vec3(0.92, 0.64, 0.20), cGold = vec3(0.99, 0.82, 0.36), cYel = vec3(1.0, 0.94, 0.62), cHot = vec3(1.0, 0.99, 0.90);
        vec3 m = mix(cEdge, cGold, smoothstep(0.0, 0.28, heat));
        m = mix(m, cYel, smoothstep(0.25, 0.55, heat));
        m = mix(m, cHot, smoothstep(0.62, 0.95, heat));
        float rim = 1.0 - smoothstep(0.0, 0.07 + aa, -d);
        m = mix(m, vec3(0.84, 0.56, 0.16), rim * 0.6);
        float n = clamp(fbm(l * 4.5 + fi * 7.13) * 1.8 - 0.4, 0.0, 1.0);
        float dz = mix(-0.2, 1.05, diss);
        float keep = smoothstep(dz, dz + 0.025, n);
        float ember = smoothstep(dz - 0.02, dz + 0.02, n) * (1.0 - smoothstep(dz + 0.03, dz + 0.10, n)) * step(0.001, diss) * cov;
        cov *= keep * alpha;
        float reach = 1.24;
        vec3 glow = vec3(1.0, 0.66, 0.25) * ember * 1.4 * alpha
                + vec3(1.0, 0.80, 0.38) * exp(-max(d, 0.0) / 0.23) * alpha * (1.0 - diss) * (1.0 - smoothstep(reach * 0.55, reach, length(l))) * 0.42;
        compose(m, cov, glow, 1.0);
        return;
    }
    // ---- a sparkle, uv in units of its size
    vec2 l = uv;
    float st = exp(-abs(l.x) * 6.0) * exp(-abs(l.y) * 0.9) + exp(-abs(l.y) * 6.0) * exp(-abs(l.x) * 0.9) + exp(-length(l) * 3.0) * 0.8;
    float edge = 1.0 - smoothstep(3.0, 4.0, max(abs(l.x), abs(l.y)));
    compose(vec3(0.0), 0.0, vec3(1.0, 0.88, 0.55) * st * color.r * 1.5, edge);
}
