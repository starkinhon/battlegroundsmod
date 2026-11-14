#version 150

// Burn: 0 = the normal skin, 1 = charred black with glowing cracks.
// Progress: 0 = whole body, 1 = all crumbled away, top first, along a ragged glowing edge.
// Style 2: stone, see main.
// Style 3: Shigaraki's Decadência (client/fx/shig), the preview's look on the voxels: from Touch the body dries to a
// pale grey and cracks (Front = how far it has got, in blocks); Recover brings the colour back; Progress crumbles it to
// dust from the touch out, bit by bit.
// Style 4: a curse exorcised (animal/jjmob, the preview's JJFX.dissolve): Progress takes the body apart texel by texel
// (1/16 of a block), from the head down but each texel at its own moment; the texels about to go turn black first.
// Bright is the light where it lies (no lightmap here).
// Style 1 (the Hakai): instead of charring, the body lights up white with purple veins, and the edge breaks into
// pixel-sized bits (1/16 of a block, like the skins) burning white in the middle and purple outside.
uniform sampler2D Sampler0;
uniform float GameTime;
uniform float AshHeight;
uniform float Burn;
uniform float Progress;
uniform float Style;
uniform vec3 GlowColor;
uniform vec3 EdgeColor;
uniform vec3 Touch;
uniform float Front;
uniform float Recover;
uniform float Bright;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 local;
in vec3 normal;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}

// Distances to the nearest and second nearest cell point (stones and the cracks between them).
vec2 voronoi(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float d1 = 9.0;
    float d2 = 9.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 g = vec3(float(x), float(y), float(z));
                float d = length(g + hash3(i + g) - f);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    return vec2(d1, d2);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.1) {
        discard;
    }
    if (Style > 3.5) {
        vec3 skin = tex.rgb * vertexColor.rgb;
        float lit = (0.6 + 0.4 * max(dot(normalize(normal), normalize(vec3(0.3, 1.0, 0.2))), 0.0)) * Bright;
        if (Progress > 0.0) {
            vec3 cell = floor(local * 16.0 + 0.001);
            float hy = clamp(local.y / max(AshHeight, 0.1), 0.0, 1.0);
            float v = hash(cell + vec3(0.71, 0.11, 0.42)) * 0.62 + (1.0 - hy) * 0.38;
            float edge = v - (Progress * 1.25 - 0.12);
            if (edge < 0.0) {
                discard;
            }
            if (edge < 0.05) {
                skin = vec3(0.02, 0.01, 0.03);
            } else if (edge < 0.1) {
                skin = mix(skin, vec3(0.05, 0.03, 0.07), 0.7);
            }
        }
        fragColor = vec4(skin * lit, 1.0);
        return;
    }
    if (Style > 2.5) {
        vec3 skin = tex.rgb * vertexColor.rgb;
        float lit = 0.62 + 0.38 * max(dot(normalize(normal), normalize(vec3(0.35, 0.85, -0.3))), 0.0);
        float d = distance(local, Touch);
        float n1 = noise(local * 3.3), n2 = noise(local * 9.0);
        float dd = d * (0.8 + 0.45 * n2) + (n1 - 0.5) * 0.35 - Front;
        if (Progress > 0.001) {
            float cf = Progress * (AshHeight + 1.2) * 1.25;
            float e = d + (n1 - 0.5) * 0.3 - cf;
            vec3 cell = floor(local * 16.0 + 0.001);
            float bit = hash(cell + vec3(5.3, 1.7, 9.1));
            if (e < 0.0 || bit < smoothstep(0.25, 0.0, e) * 0.9) {
                discard;
            }
        }
        float k = smoothstep(0.0, 0.3, -dd) * (1.0 - Recover);
        float lum = dot(skin, vec3(0.3, 0.55, 0.15));
        vec3 dry = vec3(0.70, 0.66, 0.60) * (lum * 1.3 + 0.25) * (0.9 + 0.2 * noise(local * 6.2));
        vec2 v = voronoi(local * 5.0);
        float crack = 1.0 - smoothstep(0.02, 0.06 + 0.04 * k, v.y - v.x);
        vec3 c = mix(skin, dry, k * 0.9);
        c = mix(c, vec3(0.07, 0.05, 0.04), crack * k);
        // the dark seam right at the front, where it is drying now
        float seam = (1.0 - smoothstep(0.0, 0.07, abs(dd))) * step(0.001, Front) * (1.0 - Recover);
        c = mix(c, vec3(0.09, 0.06, 0.05), seam * 0.8);
        fragColor = vec4(c * lit, 1.0);
        return;
    }
    if (Style > 1.5) {
        // Style 2, stone (Vegeta's Explosao Final): grey rock rising from the feet (Burn) with dark cracks between
        // stones; Progress lights the cracks up before it bursts.
        vec3 skin = tex.rgb * vertexColor.rgb;
        float lit = 0.55 + 0.45 * max(dot(normalize(normal), normalize(vec3(0.4, 0.8, 0.3))), 0.0);
        float frontS = Burn * (AshHeight + 0.15) + (noise(local * 3.0) - 0.5) * 0.12;
        if (local.y > frontS) {
            fragColor = vec4(skin * lit, 1.0);
            return;
        }
        vec2 v = voronoi(local * 4.5);
        float crack = 1.0 - smoothstep(0.02, 0.07, v.y - v.x);
        float grain = noise(local * 18.0);
        vec3 st = vec3(0.47, 0.45, 0.42) * (0.75 + 0.35 * grain) * lit;
        st = mix(st, vec3(0.08, 0.07, 0.07), crack * (0.55 + 0.45 * Progress));
        st += vec3(1.0, 0.8, 0.35) * crack * Progress * 1.4;
        float rim = 1.0 - smoothstep(0.0, 0.06, frontS - local.y);
        fragColor = vec4(mix(st, vec3(0.8, 0.78, 0.72) * lit, rim * 0.6), 1.0);
        return;
    }
    float h = clamp(local.y / max(AshHeight, 0.1), 0.0, 1.0);
    float n = noise(local * 7.0) * 0.6 + noise(local * 19.0) * 0.4;
    // The crumbling front runs from above the head to below the feet.
    float front = 1.05 - Progress * 1.35;
    float d = h + (n - 0.5) * 0.4 - front;
    if (d > 0.0) {
        discard;
    }
    vec3 base = tex.rgb * vertexColor.rgb;
    if (Style > 0.5) {
        float t1 = GameTime * 24000.0;
        // Pixels near the edge drop out one by one before the edge gets to them, so it crumbles into specks.
        vec3 cell = floor(local * 16.0 + 0.001);
        float bit = hash(cell + vec3(3.1, 7.7, 1.3));
        if (Progress > 0.001 && d > -0.16 && bit < smoothstep(-0.16, 0.0, d)) {
            discard;
        }
        float lit = 0.55 + 0.45 * max(dot(normalize(normal), normalize(vec3(0.3, 1.0, 0.2))), 0.0);
        vec3 white = mix(base * lit, EdgeColor * (0.85 + 0.15 * lit), Burn * 0.8);
        // Purple veins crawling over the body, brighter as it goes.
        float vein = 1.0 - smoothstep(0.0, 0.06, abs(noise(local * 6.0 + vec3(0.0, -t1 * 0.03, 0.0)) - 0.5));
        float pulse = 0.75 + 0.25 * sin(t1 * 0.5 + n * 6.28);
        vec3 color = white + GlowColor * vein * pulse * Burn * (0.9 + Progress);
        // Wide purple band, white-hot right at the edge; bits about to fall shine.
        float outer = smoothstep(-0.2, -0.05, d) * step(0.001, Progress);
        float inner = smoothstep(-0.06, 0.0, d) * step(0.001, Progress);
        color = mix(color, GlowColor * 1.7, outer);
        color = mix(color, EdgeColor * 1.6, inner);
        color += GlowColor * step(0.93, bit) * outer * 1.5;
        fragColor = vec4(color, 1.0);
        return;
    }
    float shade = 0.55 + 0.45 * max(dot(normalize(normal), normalize(vec3(0.3, 1.0, 0.2))), 0.0);
    vec3 charred = vec3(0.04, 0.032, 0.028) + base * 0.05;
    vec3 color = mix(base, charred, Burn) * shade;
    // Embers in the cracks, breathing slowly, dimming as the body goes.
    float t = GameTime * 24000.0;
    float crack = smoothstep(0.66, 0.74, noise(local * 11.0 + vec3(0.0, t * 0.01, 0.0)));
    float breathe = 0.7 + 0.3 * sin(t * 0.35 + n * 6.28);
    color += vec3(1.0, 0.32, 0.05) * crack * breathe * Burn * (1.0 - Progress * 0.6);
    // Bright ragged edge where it falls apart.
    float edge = smoothstep(-0.09, 0.0, d) * step(0.001, Progress);
    color = mix(color, vec3(1.0, 0.5, 0.15) * 1.4, edge);
    fragColor = vec4(color, 1.0);
}
