#version 150

// Kokushibo's 1st Form, Yoru no Miya (client/fx/MoonSlashRenderer): the shader of the approved Visualizer page
// (forja/kokushibo-formas/yoru-no-miya.html) put on a quad in the world. A giant crescent slash, pale cream outside and
// ember inside, with chains of moons that pop out as its point passes, glow, and break into flakes of light.
// The page's "p" space: the quad covers p = MID +- HALF. The moons and sparkles are worked out in Java every frame and
// sent in Sampler0 (a float texture, 3 rows per slash):
//   row 0, texel i < 40: moon i (x, y, radius, turn)       row 1: (thickness, alpha, dissolve, cut circle size)
//   row 2, texel i < 28: sparkle i (x, y, size, alpha)    texel 28: (head, tail, slash alpha, glint)
// Body pass (Color.r 1): premultiplied color. Glow pass (Color.r 0): only the light, added (and bloomed).
uniform sampler2D Sampler0;

in vec2 uv;
in vec4 color;
flat in ivec2 data;

out vec4 fragColor;

#define NC 40
#define NS 28
const float PI = 3.14159265;
const float TAU = 6.2831853;
const vec2 C = vec2(0.06, -0.04);
const float R = 0.60;
const float A0 = 3.4906585;      // 200 degrees
const float SPAN = -4.6251225;   // -265 degrees
const float W = 0.15;
const vec2 MID = vec2(0.06, -0.04);
const float HALF = 1.2;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm4(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

vec4 texel(int x, int row) {
    return texelFetch(Sampler0, ivec2(x, data.y * 3 + row), 0);
}

void main() {
    float time = float(data.x) / 1000.0;
    bool body = color.r > 0.5;
    vec2 p = MID + (uv * 2.0 - 1.0) * HALF;
    vec4 band = texel(28, 2);
    float uHead = band.x, uTail = band.y, uBandA = band.z, uGlint = band.w;

    vec3 col = vec3(0.0);   // premultiplied
    float alpha = 0.0;
    vec3 glow = vec3(0.0);

    // ---------------- the slash: a crescent arc, pale outside, ember inside ----------------
    vec2 rel = p - C;
    float r = length(rel);
    float th = atan(rel.y, rel.x);
    float s = mod(A0 - th, TAU) / abs(SPAN);
    if (s < 1.08 && uHead > 0.0) {
        float Ru = R * (1.0 + 0.07 * s);
        float headT = smoothstep(0.0, 0.075, uHead - s);
        float tailT = pow(smoothstep(0.0, 0.42, s - uTail), 0.8);
        float w = W * headT * tailT * (0.85 + 0.15 * sin(s * PI));
        if (w > 0.0) {
            float dr = r - Ru;
            float d = abs(dr) - 0.5 * w;
            float aa = fwidth(r) * 1.2;
            float cov = (1.0 - smoothstep(-aa, aa, d)) * uBandA;
            float q = clamp(dr / (0.5 * w), -1.0, 1.0);
            vec3 cIn = vec3(0.78, 0.40, 0.12), cMid = vec3(0.97, 0.65, 0.28), cOut = vec3(1.0, 0.93, 0.70), cRim = vec3(1.0, 0.995, 0.93);
            vec3 b = mix(cIn, cMid, smoothstep(-1.0, 0.15, q));
            b = mix(b, cOut, smoothstep(0.05, 0.72, q));
            b = mix(b, cRim, smoothstep(0.86, 0.99, q));
            float streak = vnoise(vec2(s * 95.0, q * 2.6)) * vnoise(vec2(s * 31.0 + 7.0, q * 1.3));
            b *= 1.0 - 0.22 * streak * (1.0 - q) * 0.5;
            b = mix(b, cIn * 0.8, 0.25 * smoothstep(-0.55, -1.0, q));
            float burn = mix(1.0, smoothstep(0.0, 0.22, s - uTail), clamp(uTail * 12.0, 0.0, 1.0));
            float n = fbm4(vec2(s * 40.0, q * 3.0));
            cov *= smoothstep(1.0 - burn - 0.1, 1.0 - burn + 0.05, n + 0.35);
            col = col * (1.0 - cov) + b * cov;
            alpha = alpha * (1.0 - cov) + cov;
            float halo = exp(-max(d, 0.0) / 0.045) * uBandA * headT * tailT;
            glow += vec3(1.0, 0.62, 0.22) * halo * 0.55 + vec3(1.0, 0.9, 0.6) * exp(-max(d, 0.0) / 0.012) * 0.35 * uBandA * headT * tailT;
        }
    }

    // ---------------- the moons ----------------
    for (int i = 0; i < NC; i++) {
        vec4 a = texel(i, 0);
        if (a.z < 0.0008) {
            continue;
        }
        vec2 l = p - a.xy;
        float rr = a.z;
        float reach = rr * 1.15 + 0.09;
        if (dot(l, l) > reach * reach) {
            continue;
        }
        float cs = cos(a.w), sn = sin(a.w);
        l = vec2(cs * l.x + sn * l.y, -sn * l.x + cs * l.y);
        vec4 b = texel(i, 1);
        float off = b.x * rr;
        float dO = length(l) - rr;
        float dI = length(l - vec2(off, 0.0)) - rr * b.w;
        float d = max(dO, -dI);
        float aa = fwidth(d) + 1e-5;
        float cov = 1.0 - smoothstep(-aa, aa, d);
        // a hot core along the middle of the crescent, gold, then a golden edge; the round side a touch lighter
        float ta = max(-dO, 0.0), tb = max(dI, 0.0);
        float t = tb / (ta + tb + 1e-5);
        float core = clamp(2.0 * min(ta, tb) / (ta + tb + 1e-5), 0.0, 1.0);
        // energy streaks running around the moon and a finer grain
        float ang = atan(l.y, l.x), rad = length(l) / rr;
        float flow = fbm4(vec2(ang * 2.2 - time * 1.8 + float(i), rad * 9.0));
        float grain = vnoise(l / rr * 22.0 + float(i) * 3.1);
        float heat = core * (0.85 + 0.55 * (flow - 0.5) * 2.0) + 0.10 * (t - 0.5) + 0.06 * (grain - 0.5);
        vec3 cEdge = vec3(0.92, 0.64, 0.20), cGold = vec3(0.99, 0.82, 0.36), cYel = vec3(1.0, 0.94, 0.62), cHot = vec3(1.0, 0.99, 0.90);
        vec3 m = mix(cEdge, cGold, smoothstep(0.0, 0.28, heat));
        m = mix(m, cYel, smoothstep(0.25, 0.55, heat));
        m = mix(m, cHot, smoothstep(0.62, 0.95, heat));
        float rim = 1.0 - smoothstep(0.0, rr * 0.07 + aa, -d);
        m = mix(m, vec3(0.84, 0.56, 0.16), rim * 0.6);
        // dissolve into flakes of light
        float n = clamp(fbm4(l / rr * 4.5 + float(i) * 7.13) * 1.8 - 0.4, 0.0, 1.0);
        float dz = mix(-0.2, 1.05, b.z);
        float keep = smoothstep(dz, dz + 0.025, n);
        float ember = smoothstep(dz - 0.02, dz + 0.02, n) * (1.0 - smoothstep(dz + 0.03, dz + 0.10, n)) * step(0.001, b.z) * cov;
        cov *= keep * b.y;
        col = col * (1.0 - cov) + m * cov;
        alpha = alpha * (1.0 - cov) + cov;
        glow += vec3(1.0, 0.66, 0.25) * ember * 1.4 * b.y;
        float g = exp(-max(d, 0.0) / (rr * 0.22 + 0.012)) * b.y * (1.0 - b.z) * (1.0 - smoothstep(reach * 0.55, reach, length(l)));
        glow += vec3(1.0, 0.80, 0.38) * g * 0.42;
    }

    // ---------------- sparkles ----------------
    for (int i = 0; i < NS; i++) {
        vec4 s4 = texel(i, 2);
        if (s4.w < 0.01) {
            continue;
        }
        vec2 l = p - s4.xy;
        float z = s4.z;
        if (dot(l, l) > z * z * 16.0) {
            continue;
        }
        float st = exp(-abs(l.x) / z * 6.0) * exp(-abs(l.y) / z * 0.9) + exp(-abs(l.y) / z * 6.0) * exp(-abs(l.x) / z * 0.9);
        st += exp(-length(l) / z * 3.0) * 0.8;
        glow += vec3(1.0, 0.88, 0.55) * st * s4.w;
    }

    // ---------------- the draw's glint ----------------
    if (uGlint > 0.0) {
        vec2 g0 = C + R * vec2(cos(A0), sin(A0));
        vec2 l = p - g0;
        float d = length(l);
        vec2 tg = normalize(vec2(sin(A0), -cos(A0)));
        float along = dot(l, tg), across = abs(dot(l, vec2(-tg.y, tg.x)));
        float beam = exp(-across / 0.004) * exp(-abs(along) / 0.35);
        glow += vec3(1.0, 0.95, 0.8) * (beam * 1.2 + exp(-d / 0.05) * 0.9) * uGlint;
    }

    // the edge of the quad never shows
    vec2 e = abs(uv * 2.0 - 1.0);
    float edge = 1.0 - smoothstep(0.92, 1.0, max(e.x, e.y));
    float fade = color.a * edge;
    if (body) {
        if (alpha * fade < 0.002) {
            discard;
        }
        fragColor = vec4(col * fade, alpha * fade);
    } else {
        vec3 light = glow * 0.2 * fade;
        if (max(light.r, max(light.g, light.b)) < 0.002) {
            discard;
        }
        fragColor = vec4(min(light, vec3(1.0)), 1.0);
    }
}
