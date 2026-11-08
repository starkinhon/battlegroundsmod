#version 150

// The Awakened Moon's screen (Kokushibo, Stark 2026-11-08): the same pass as the approved preview
// (forja/kokushibo-formas/impacto-desperta.html). Shake (roll, push-in, offset), a shockwave ring, the screen itself cut
// in two (Split), colour split and radial blur, the night closing in (Grade), impact frames in four lilac palettes
// (Imp 0 moon paper, 1 negative, 2 ink, 3 lilac), speed lines, vignette, flash and letterbox.
uniform sampler2D DiffuseSampler;
uniform vec2 Screen;
uniform float Imp, Seed, Blur, CA, Vig, Grade, Letter, Lines, Roll, Zoom, Night;
uniform vec2 BlurCenter, LinesCenter, Offset, SplitCenter;
uniform vec4 Split, Shock, Flash;

in vec2 uv;
out vec4 fragColor;

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float h11(float n) { return fract(sin(n * 127.1) * 43758.5453); }
vec3 tex(vec2 p) { return texture(DiffuseSampler, clamp(p, vec2(0.001), vec2(0.999))).rgb; }
vec3 texCA(vec2 p) { vec2 d = (p - 0.5) * CA * 0.014; return vec3(tex(p + d).r, tex(p).g, tex(p - d).b); }

float edge(vec2 p) {
    vec2 px = 1.5 / Screen;
    float tl = lum(tex(p + px * vec2(-1.0, 1.0))), t = lum(tex(p + px * vec2(0.0, 1.0))), tr = lum(tex(p + px * vec2(1.0, 1.0)));
    float l = lum(tex(p + px * vec2(-1.0, 0.0))), r = lum(tex(p + px * vec2(1.0, 0.0)));
    float bl = lum(tex(p + px * vec2(-1.0, -1.0))), b = lum(tex(p + px * vec2(0.0, -1.0))), br = lum(tex(p + px * vec2(1.0, -1.0)));
    return length(vec2(tr + 2.0 * r + br - tl - 2.0 * l - bl, tl + 2.0 * t + tr - bl - 2.0 * b - br));
}

void main() {
    vec2 asp = vec2(Screen.x / Screen.y, 1.0);
    vec2 q = (uv - 0.5) * asp;
    float cr = cos(Roll), sr = sin(Roll);
    q = mat2(cr, sr, -sr, cr) * q / Zoom;
    vec2 p = q / asp + 0.5 + Offset;
    if (Shock.w > 0.0) {
        vec2 d = (p - Shock.xy) * asp;
        float L = length(d);
        float ring = exp(-pow((L - Shock.z) / 0.05, 2.0));
        p -= d / max(L, 1e-4) / asp * ring * Shock.w * 0.035;
    }
    float sl = 0.0;
    if (Split.x > 0.0) {
        vec2 dir = vec2(cos(Split.y), sin(Split.y)), n = vec2(-dir.y, dir.x);
        float s = dot((p - SplitCenter) * asp, n), sg = s > 0.0 ? 1.0 : -1.0;
        p -= (dir * sg * Split.z + n * sg * Split.z * 0.3) * Split.x / asp;
        sl = Split.x * (exp(-abs(s) * 900.0) * 1.4 + exp(-abs(s) * 110.0) * 0.45);
    }
    vec3 c;
    if (Blur > 0.002) {
        vec3 acc = vec3(0.0);
        float W = 0.0;
        vec2 toC = BlurCenter - p;
        for (int i = 0; i < 12; i++) {
            float f = float(i) / 11.0, w = 1.0 - 0.6 * f;
            acc += texCA(p + toC * f * Blur * 0.22) * w;
            W += w;
        }
        c = acc / W;
    } else {
        c = texCA(p);
    }
    float l = lum(c);
    c = mix(c, mix(vec3(l), c, 0.4) * vec3(0.78, 0.68, 1.06) * (0.62 + 0.2 * Night), Grade);
    vec3 INK = vec3(0.06, 0.03, 0.10), PAPER = vec3(0.95, 0.93, 1.0), LIL = vec3(0.70, 0.50, 1.0), BLU = vec3(0.62, 0.88, 1.0);
    float lineCol = 1.0;
    if (Imp > -0.5) {
        vec3 s0 = tex(p);
        float L = lum(s0), e = edge(p);
        float hot = smoothstep(0.62, 0.8, L);
        float blue = smoothstep(0.06, 0.2, s0.b - s0.r) * hot;
        if (Imp < 0.5) {
            c = mix(PAPER, mix(LIL, vec3(1.0), 0.25), hot);
            c = mix(c, BLU * 0.85, blue * 0.7);
            c = mix(c, INK, smoothstep(0.14, 0.28, e));
            lineCol = 0.0;
        } else if (Imp < 1.5) {
            c = mix(LIL * 1.05, INK, smoothstep(0.14, 0.55, L));
            c = mix(c, vec3(1.0), smoothstep(0.16, 0.30, e));
        } else if (Imp < 2.5) {
            c = INK * 0.4;
            c = mix(c, vec3(1.0), max(smoothstep(0.14, 0.28, e), hot));
            c = mix(c, LIL, hot * (1.0 - smoothstep(0.85, 0.95, L)) * 0.6);
        } else {
            c = mix(vec3(0.0), LIL * 0.95, smoothstep(0.12, 0.35, L));
            c = mix(c, vec3(1.0), smoothstep(0.7, 0.9, L));
            c = mix(c, vec3(0.0), smoothstep(0.2, 0.34, e));
            lineCol = 0.0;
        }
    }
    if (Lines > 0.01) {
        vec2 d = (p - LinesCenter) * asp;
        float a = atan(d.y, d.x) / 6.2831853 * 140.0, r = length(d);
        float k = floor(a), n = h11(k + Seed * 17.0), w = abs(fract(a) - 0.5);
        float line = step(0.80, n) * smoothstep(0.5, 0.1, w) * smoothstep(0.12 + 0.18 * h11(k + 3.0 + Seed), 0.6, r);
        c = mix(c, vec3(lineCol), line * Lines);
    }
    c += vec3(0.90, 0.84, 1.0) * sl;
    float v = length((uv - 0.5) * vec2(asp.x * 0.8, 1.0));
    c = mix(c, c * vec3(0.21, 0.13, 0.27), smoothstep(0.25, 0.95, v) * Vig);
    c = mix(c, Flash.rgb, Flash.a);
    float bar = Letter * 0.12;
    if (uv.y < bar || uv.y > 1.0 - bar) {
        c = vec3(0.0);
    }
    fragColor = vec4(c, 1.0);
}
