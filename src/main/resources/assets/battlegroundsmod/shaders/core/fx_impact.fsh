#version 150

// Impact frame: for a tick or two the frame is redrawn in two or three flat colors, anime style.
// Mode 0 white: cream paper, hot spots in the attack color, thin colored outlines.
// Mode 1 color: shadows go black, everything lit takes the attack color, hot spots almost white, black outlines.
// Mode 2 inverted: lit goes black, shadows take the attack color, hot spots white.
// Mode 3 manga: black ink on paper where a shape is darker than around it or where the depth breaks, plus speed
// lines shot out of Center. Mode 4 is mode 3 with black paper and ink in the attack color.
// Mode 5 ink (Gojo's domain, the killing blow): sumi-e. White paper, whatever is near drawn as black ink silhouettes,
// a ragged red brush stroke sweeping across (Tint) and a jet of black ink with droplets thrown out of Center; Age (0 to
// 1, how far through the impact) spreads the strokes.
// Reduced 1 keeps it calm for people sensitive to flashing: a grey, low contrast version over the real frame.
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 Screen;
uniform vec2 Center;
uniform vec3 Tint;
uniform float Mode;
uniform float Seed;
uniform float DepthOn;
uniform vec2 DepthRange;
uniform float Reduced;
uniform float Age;

in vec2 uv;
out vec4 fragColor;

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float lumAt(vec2 offset) {
    return lum(texture(DiffuseSampler, uv + offset / Screen).rgb);
}

float linearDepth(vec2 at) {
    float d = texture(DepthSampler, at).r * 2.0 - 1.0;
    float n = DepthRange.x;
    float f = DepthRange.y;
    return 2.0 * n * f / (f + n - d * (f - n));
}

float hash(float x) {
    return fract(sin(x * 91.17 + Seed * 17.31) * 47453.271);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += noise2(p) * a;
        p = p * 2.07 + vec2(3.1, 7.7);
        a *= 0.5;
    }
    return v;
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-5), 0.0, 1.0);
    return length(p - a - ab * t);
}

vec3 ink(vec3 c, float l, float edge, float depth) {
    float aspect = Screen.x / Screen.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    vec2 cen = (Center - 0.5) * vec2(aspect, 1.0);
    float grow = smoothstep(0.0, 0.35, Age);

    // Rice paper: warm white with fibers.
    float grain = fbm2(uv * vec2(aspect, 1.0) * 140.0);
    vec3 paper = vec3(0.97, 0.955, 0.92) * (0.93 + 0.07 * grain);

    // Silhouettes: everything near (the people in the void), plus dark shapes and edges, in brushed black.
    float near = DepthOn > 0.5 ? 1.0 - smoothstep(18.0, 40.0, depth) : 0.0;
    float sil = max(near, max(edge, step(l, 0.12)));
    float rough = fbm2(uv * vec2(aspect, 1.0) * 60.0);
    sil = smoothstep(0.35, 0.55, sil * (0.75 + 0.5 * rough));

    // The red stroke: a wide ragged band across the screen, swept in by the brush, with dry streaks along it.
    float ang = -0.42;
    vec2 q = vec2(cos(ang) * p.x - sin(ang) * p.y, sin(ang) * p.x + cos(ang) * p.y);
    float wob = (fbm2(vec2(q.x * 2.5, 1.7)) - 0.5) * 0.22;
    float bandD = abs(q.y - 0.06 + wob);
    float width = 0.16 + 0.06 * fbm2(vec2(q.x * 4.0, 9.0));
    float sweep = smoothstep(-0.05, 0.05, (-1.2 + 3.2 * grow) - q.x);
    float bristles = 0.6 + 0.4 * noise2(vec2(q.x * 3.0, q.y * 70.0));
    float band = smoothstep(width, width - 0.025, bandD) * sweep;
    float dry = smoothstep(0.25, 0.55, noise2(vec2(q.x * 1.5, q.y * 55.0)) + (1.0 - bandD / width) * 0.5);
    band *= dry;
    vec3 red = mix(Tint, Tint * 0.55, 1.0 - bristles);

    // The black ink jet: a tapering stroke thrown out of Center, plus droplets and splatter around it.
    vec2 jetDir = normalize(vec2(0.85, 0.5));
    float len = 0.75 * grow;
    float d = segment(p, cen, cen + jetDir * len);
    float along = clamp(dot(p - cen, jetDir) / max(len, 1e-3), 0.0, 1.0);
    float jetW = mix(0.07, 0.012, along) * (0.7 + 0.6 * fbm2(p * 18.0));
    float jet = smoothstep(jetW, jetW * 0.6, d) * step(0.001, len);
    float drops = 0.0;
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float a = atan(jetDir.y, jetDir.x) + (hash2(vec2(fi, 3.0)) - 0.5) * 1.6;
        float dist = (0.1 + 0.8 * hash2(vec2(fi, 7.0))) * grow;
        vec2 at = cen + vec2(cos(a), sin(a)) * dist;
        float r = (0.006 + 0.03 * hash2(vec2(fi, 11.0)) * hash2(vec2(fi, 13.0))) * (0.85 + 0.3 * fbm2(p * 40.0 + fi));
        drops = max(drops, smoothstep(r, r * 0.7, length(p - at)));
    }
    // A blot where it came out.
    float blot = smoothstep(0.09 * grow + 0.01, 0.06 * grow, length(p - cen) * (0.8 + 0.5 * fbm2(p * 9.0)));

    vec3 outc = paper;
    outc = mix(outc, red, band * 0.95);
    outc = mix(outc, vec3(0.03, 0.025, 0.03), clamp(max(sil, max(jet, max(drops, blot))), 0.0, 1.0));
    // The red shows through thin ink at the stroke's edge.
    outc = mix(outc, red * 0.4, band * sil * 0.25);
    return outc;
}

void main() {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float l = lum(c);

    // Sobel on brightness, sampled a pixel and a half apart so block edges come out as clean lines.
    float tl = lumAt(vec2(-1.5, 1.5)), t = lumAt(vec2(0.0, 1.5)), tr = lumAt(vec2(1.5, 1.5));
    float ml = lumAt(vec2(-1.5, 0.0)), mr = lumAt(vec2(1.5, 0.0));
    float bl = lumAt(vec2(-1.5, -1.5)), b = lumAt(vec2(0.0, -1.5)), br = lumAt(vec2(1.5, -1.5));
    float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    float gy = (tl + 2.0 * t + tr) - (bl + 2.0 * b + br);
    float edge = smoothstep(0.16, 0.4, length(vec2(gx, gy)));

    float depth = 0.0;
    float far = 0.0;
    if (DepthOn > 0.5) {
        depth = linearDepth(uv);
        float around = 0.0;
        around = max(around, abs(linearDepth(uv + vec2(2.0, 0.0) / Screen) - depth));
        around = max(around, abs(linearDepth(uv - vec2(2.0, 0.0) / Screen) - depth));
        around = max(around, abs(linearDepth(uv + vec2(0.0, 2.0) / Screen) - depth));
        around = max(around, abs(linearDepth(uv - vec2(0.0, 2.0) / Screen) - depth));
        // A jump of more than 7% in distance is a silhouette.
        edge = max(edge, step(0.07 * depth, around));
        far = smoothstep(50.0, 110.0, depth);
    }

    // Hot: very bright pixels, or pixels close to the attack's own color (its fire, its light).
    vec3 tintN = Tint / max(max(Tint.r, max(Tint.g, Tint.b)), 0.001);
    vec3 cN = c / max(max(c.r, max(c.g, c.b)), 0.001);
    float sameHue = 1.0 - smoothstep(0.15, 0.45, distance(cN, tintN));
    float hot = max(smoothstep(0.8, 0.96, l), sameHue * smoothstep(0.4, 0.7, l));
    float lit = smoothstep(0.3, 0.38, l);

    vec3 paper = vec3(1.0, 0.97, 0.92);
    vec3 ink = vec3(0.03, 0.02, 0.03);
    vec3 light = mix(Tint, vec3(1.0), 0.65);
    vec3 outc;
    if (Mode > 4.5) {
        outc = ink(c, l, edge, depth);
    } else if (Mode < 0.5) {
        outc = mix(paper, mix(Tint, paper, 0.25), hot * 0.9);
        outc = mix(outc, Tint * 0.8, edge * 0.45);
    } else if (Mode < 1.5) {
        outc = mix(Tint * 0.04, Tint, lit);
        outc = mix(outc, light, hot);
        outc = mix(outc, ink, edge * (1.0 - hot));
    } else if (Mode < 2.5) {
        outc = mix(Tint, ink, lit);
        outc = mix(outc, vec3(1.0), hot);
        outc = mix(outc, light, edge * (1.0 - hot) * 0.85);
    } else {
        // Manga: ink where a pixel is darker than the ring of pixels around it (folds, faces, shading).
        float ring = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7854 + 0.4;
            ring += lumAt(vec2(cos(a), sin(a)) * 16.0);
        }
        ring /= 8.0;
        float inked = max(step(l, ring - 0.05), step(l, 0.09));
        inked = max(inked, edge);
        inked *= 1.0 - far;
        // Speed lines: thin wedges around Center with random widths, none in the middle.
        vec2 p = (uv - Center) * vec2(Screen.x / Screen.y, 1.0);
        float ang = atan(p.y, p.x) / 6.28318 + 0.5;
        float r = length(p);
        float cell = floor(ang * 160.0);
        float h = hash(cell);
        float across = abs(fract(ang * 160.0) - 0.5);
        float line = step(0.7, h) * step(across, (h - 0.6) * 0.9 * smoothstep(0.08, 0.8, r))
                * smoothstep(0.12 + 0.25 * hash(cell + 3.0), 0.4 + 0.3 * hash(cell + 9.0), r);
        float v = max(inked * (1.0 - hot * 0.7), line);
        if (Mode < 3.5) {
            outc = mix(mix(paper, Tint, 0.1), ink, v);
            outc = mix(outc, Tint, hot * (1.0 - v) * 0.6);
        } else {
            outc = mix(vec3(0.02), light, v);
            outc = mix(outc, vec3(1.0), hot * 0.8);
        }
    }

    if (Reduced > 0.5) {
        float g = lum(outc);
        outc = mix(c, vec3(0.35 + 0.3 * g), 0.45);
    }
    fragColor = vec4(outc, 1.0);
}
