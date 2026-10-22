#version 150

// Impact frame: for a tick or two the frame is redrawn in two or three flat colors, anime style.
// Mode 0 white: cream paper, hot spots in the attack color, thin colored outlines.
// Mode 1 color: shadows go black, everything lit takes the attack color, hot spots almost white, black outlines.
// Mode 2 inverted: lit goes black, shadows take the attack color, hot spots white.
// Mode 3 manga: black ink on paper where a shape is darker than around it or where the depth breaks, plus speed
// lines shot out of Center. Mode 4 is mode 3 with black paper and ink in the attack color.
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
    if (Mode < 0.5) {
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
