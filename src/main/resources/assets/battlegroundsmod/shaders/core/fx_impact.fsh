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
// Modes 6 to 9: the Black Flash's frames (Gojo, Shift+Z), diffuse and shaking instead of flat cartoon colors: the frame
// itself in hard contrast, a zoom blur pulled into Center (the fist), slices torn sideways, a red ghost out of place,
// blurred impact lines, smeared ink and grain, all re-rolled with Seed (three times a tick). 6 inverted manga, 7 manga,
// 8 red, 9 inverted red.
// Modes 10 and 11 are not frames but a grade over the real frame, Age being how strong (0 to 1): 10 the slow motion
// (dark, washed out, blue), 11 the Black Flash's red (saturated, red, with black ink blots with red edges flickering
// around Center).
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

float inkHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float inkNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(inkHash(i), inkHash(i + vec2(1.0, 0.0)), f.x), mix(inkHash(i + vec2(0.0, 1.0)), inkHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float inkFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += inkNoise(p) * a;
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

vec3 inkFrame(vec3 c, float l, float edge, float depth) {
    float aspect = Screen.x / Screen.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    vec2 cen = (Center - 0.5) * vec2(aspect, 1.0);
    float grow = smoothstep(0.0, 0.35, Age);

    // Rice paper: warm white with fibers.
    float grain = inkFbm(uv * vec2(aspect, 1.0) * 140.0);
    vec3 paper = vec3(0.97, 0.955, 0.92) * (0.93 + 0.07 * grain);

    // Silhouettes: everything near (the people in the void), plus dark shapes and edges, in brushed black.
    float near = DepthOn > 0.5 ? 1.0 - smoothstep(18.0, 40.0, depth) : 0.0;
    float sil = max(near, max(edge, step(l, 0.12)));
    float rough = inkFbm(uv * vec2(aspect, 1.0) * 60.0);
    sil = smoothstep(0.35, 0.55, sil * (0.75 + 0.5 * rough));

    // The red stroke: a wide ragged band across the screen, swept in by the brush, with dry streaks along it.
    float ang = -0.42;
    vec2 q = vec2(cos(ang) * p.x - sin(ang) * p.y, sin(ang) * p.x + cos(ang) * p.y);
    float wob = (inkFbm(vec2(q.x * 2.5, 1.7)) - 0.5) * 0.22;
    float bandD = abs(q.y - 0.06 + wob);
    float width = 0.16 + 0.06 * inkFbm(vec2(q.x * 4.0, 9.0));
    float sweep = smoothstep(-0.05, 0.05, (-1.2 + 3.2 * grow) - q.x);
    float bristles = 0.6 + 0.4 * inkNoise(vec2(q.x * 3.0, q.y * 70.0));
    float band = smoothstep(width, width - 0.025, bandD) * sweep;
    float dry = smoothstep(0.25, 0.55, inkNoise(vec2(q.x * 1.5, q.y * 55.0)) + (1.0 - bandD / width) * 0.5);
    band *= dry;
    vec3 red = mix(Tint, Tint * 0.55, 1.0 - bristles);

    // The black ink jet: a tapering stroke thrown out of Center, plus droplets and splatter around it.
    vec2 jetDir = normalize(vec2(0.85, 0.5));
    float len = 0.75 * grow;
    float d = segment(p, cen, cen + jetDir * len);
    float along = clamp(dot(p - cen, jetDir) / max(len, 1e-3), 0.0, 1.0);
    float jetW = mix(0.07, 0.012, along) * (0.7 + 0.6 * inkFbm(p * 18.0));
    float jet = smoothstep(jetW, jetW * 0.6, d) * step(0.001, len);
    float drops = 0.0;
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float a = atan(jetDir.y, jetDir.x) + (inkHash(vec2(fi, 3.0)) - 0.5) * 1.6;
        float dist = (0.1 + 0.8 * inkHash(vec2(fi, 7.0))) * grow;
        vec2 at = cen + vec2(cos(a), sin(a)) * dist;
        float r = (0.006 + 0.03 * inkHash(vec2(fi, 11.0)) * inkHash(vec2(fi, 13.0))) * (0.85 + 0.3 * inkFbm(p * 40.0 + fi));
        drops = max(drops, smoothstep(r, r * 0.7, length(p - at)));
    }
    // A blot where it came out.
    float blot = smoothstep(0.09 * grow + 0.01, 0.06 * grow, length(p - cen) * (0.8 + 0.5 * inkFbm(p * 9.0)));

    vec3 outc = paper;
    outc = mix(outc, red, band * 0.95);
    outc = mix(outc, vec3(0.03, 0.025, 0.03), clamp(max(sil, max(jet, max(drops, blot))), 0.0, 1.0));
    // The red shows through thin ink at the stroke's edge.
    outc = mix(outc, red * 0.4, band * sil * 0.25);
    return outc;
}

float bfLum(vec2 at) {
    return lum(texture(DiffuseSampler, clamp(at, vec2(0.001), vec2(0.999))).rgb);
}

// Hard black and white of the night scene (it is dark, so lifted first).
float bfInk(vec2 at) {
    return smoothstep(0.2, 0.5, bfLum(at) * 2.4);
}

vec3 bfFrame(float variant) {
    float aspect = Screen.x / Screen.y;
    float s = Seed * 0.37 + 1.3;
    // The whole frame shakes.
    vec2 p = uv + (vec2(inkHash(vec2(s, 1.0)), inkHash(vec2(s, 2.0))) - 0.5) * 0.035;
    // Slices torn sideways.
    float row = floor(p.y * 38.0 + inkHash(vec2(s, 3.0)) * 5.0);
    if (inkHash(vec2(row, s)) > 0.78) {
        p.x += (inkHash(vec2(row, s + 7.0)) - 0.5) * 0.16;
    }
    // Zoom blur into the fist: the sharp frame under copies pulled out from Center.
    float zoom = 0.06 + 0.06 * inkHash(vec2(s, 4.0));
    float acc = 0.0;
    float total = 0.0;
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        vec2 q = Center + (p - Center) * (1.0 - fi * zoom * 0.11);
        float w = i == 0 ? 2.5 : 1.0 - fi / 10.0;
        acc += bfInk(q) * w;
        total += w;
    }
    float v = acc / total;
    // The ghost: the same frame a little out of place.
    float ghost = bfInk(p + vec2(0.022 * sign(inkHash(vec2(s, 5.0)) - 0.5), -0.009));
    bool inverted = variant < 6.5 || variant > 8.5;
    if (inverted) {
        v = 1.0 - v;
        ghost = 1.0 - ghost;
    }
    vec3 col;
    if (variant < 7.5) {
        col = vec3(v);
        col = mix(col, Tint, abs(ghost - v) * 0.75);
    } else {
        col = mix(vec3(0.02, 0.0, 0.005), Tint, v);
        col = mix(col, vec3(1.0, 0.85, 0.87), max(0.0, ghost - v) * 0.6);
        col = mix(col, vec3(0.0), max(0.0, v - ghost) * 0.6);
    }
    // Smeared ink out of the fist: a ragged soft blot and a burst of soft spikes.
    vec2 d = (uv - Center) * vec2(aspect, 1.0);
    float dist = length(d);
    float r = 0.08 + 0.32 * inkHash(vec2(s, 6.0));
    float blot = 1.0 - smoothstep(r * 0.55, r * 1.3, dist * (0.7 + 0.6 * inkFbm(d * 5.0 + s)));
    float ang = atan(d.y, d.x) / 6.28318 + 0.5;
    float cell = floor(ang * 48.0);
    float spike = step(0.55, inkHash(vec2(cell, s))) * (1.0 - smoothstep(0.0, 0.5, abs(fract(ang * 48.0) - 0.5) * 2.0))
            * (1.0 - smoothstep(r * 0.8, r * (1.6 + 1.6 * inkHash(vec2(cell, s + 1.0))), dist));
    col = mix(col, vec3(0.0), clamp(max(blot, spike) * 0.9, 0.0, 1.0));
    // Impact lines: blurred wedges, the color flipped under them so they always show.
    float lc = floor(ang * 180.0);
    float h = inkHash(vec2(lc, s + 9.0));
    float across = abs(fract(ang * 180.0) - 0.5);
    float line = step(0.62, h) * (1.0 - smoothstep(0.0, (h - 0.55) * 0.9 * smoothstep(0.05, 0.8, dist) + 0.02, across))
            * smoothstep(0.1 + 0.25 * inkHash(vec2(lc, 3.0)), 0.35 + 0.3 * inkHash(vec2(lc, 5.0)), dist);
    col = mix(col, vec3(1.0) - col, line * 0.85);
    // Grain.
    col += (inkHash(floor(uv * Screen * 0.5) + s) - 0.5) * 0.22;
    return clamp(col, 0.0, 1.0);
}

vec3 bfGrade(vec3 c, float k, float red) {
    if (red < 0.5) {
        float g = lum(c);
        vec3 out1 = mix(c, vec3(g), 0.65 * k) * (1.0 - 0.2 * k);
        out1 = (out1 - 0.5) * (1.0 + 0.15 * k) + 0.5;
        return clamp(out1 * mix(vec3(1.0), vec3(0.10, 0.23, 0.54), 0.55 * k), 0.0, 1.0);
    }
    float g = lum(c);
    vec3 sat = mix(vec3(g), c, 1.0 + 0.8 * k);
    sat = (sat - 0.5) * (1.0 + 0.35 * k) + 0.5;
    vec3 outc = clamp(sat, 0.0, 1.0) * mix(vec3(1.0), Tint, 0.5 * k);
    // The ink blots around the fist, re-rolled every two ticks.
    float aspect = Screen.x / Screen.y;
    vec2 p = (uv - Center) * vec2(aspect, 1.0);
    float s = floor(Seed / 2.0);
    float ink = 0.0;
    float rim = 0.0;
    for (int i = 0; i < 16; i++) {
        float fi = float(i);
        if (inkHash(vec2(s + fi, 1.0)) > 0.75) {
            continue;
        }
        bool near = i < 10;
        float a = inkHash(vec2(s + fi, 2.0)) * 6.28318;
        float dist = near ? 0.05 + 0.3 * inkHash(vec2(s + fi, 3.0)) : 0.2 + 0.5 * inkHash(vec2(s + fi, 3.0));
        float r = (near ? 0.03 + 0.07 * inkHash(vec2(s + fi, 4.0)) : 0.015 + 0.04 * inkHash(vec2(s + fi, 4.0))) * (0.6 + 0.6 * k);
        vec2 at = vec2(cos(a), sin(a) * 0.7) * dist;
        float e = length(p - at) * (0.75 + 0.5 * inkFbm((p - at) * 30.0 + fi));
        ink = max(ink, 1.0 - smoothstep(r * 0.8, r, e));
        rim = max(rim, 1.0 - smoothstep(r, r * 1.6, e));
    }
    outc = mix(outc, Tint, rim * (1.0 - ink) * 0.7 * k);
    return mix(outc, vec3(0.0), ink * k);
}

void main() {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float l = lum(c);
    if (Mode > 5.5) {
        vec3 bf = Mode > 9.5 ? bfGrade(c, Age, Mode - 10.0) : bfFrame(Mode);
        if (Reduced > 0.5) {
            bf = Mode > 9.5 ? mix(c, bf, 0.5) : mix(c, vec3(0.35 + 0.3 * lum(bf)), 0.45);
        }
        fragColor = vec4(bf, 1.0);
        return;
    }

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
        outc = inkFrame(c, l, edge, depth);
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
