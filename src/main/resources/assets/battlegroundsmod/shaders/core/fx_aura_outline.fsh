#version 150

// The aura's glowing outline, drawn into the glow layer (which gets the bloom). The mask holds the bodies with an
// aura: color, and in alpha how far their glow reaches (Radius = 1). Outside a body, each pixel looks back toward
// it along a ring of directions; the closest body it finds lights it, fading with the distance. The reach wobbles
// with a noise that scrolls up, longer above the body, so the edge burns in tongues of flame rising off it. Inside
// a body, only a thin rim near its edge lights up.
uniform sampler2D MaskSampler;
uniform vec2 ScreenSize;
uniform float Radius;
uniform float Time;
uniform float Flames;
uniform float Speed;
uniform float Edge;

in vec2 uv;
out vec4 fragColor;

const int DIRS = 24;
const int STEPS = 7;
const float TAU = 6.2831853;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    return noise(p) * 0.6 + noise(p * 2.3 + 7.1) * 0.3 + noise(p * 5.1 + 3.7) * 0.1;
}

void main() {
    vec2 px = 1.0 / ScreenSize;
    vec2 pos = uv * ScreenSize;
    vec4 self = texture(MaskSampler, uv);
    float t = Time * Speed;

    if (self.a > 0.02) {
        // Inside: how much of the ring around this pixel is outside the body.
        float r = max(2.0, self.a * Radius * 0.12);
        float outside = 0.0;
        for (int i = 0; i < 12; i++) {
            float a = float(i) / 12.0 * TAU;
            outside += texture(MaskSampler, uv + vec2(cos(a), sin(a)) * r * px).a > 0.02 ? 0.0 : 1.0;
        }
        float rim = clamp(outside / 5.0, 0.0, 1.0);
        float flicker = 0.75 + 0.25 * noise(pos / (r * 4.0) - vec2(0.0, t * 3.0));
        vec3 hot = mix(self.rgb, vec3(self.rgb.r + self.rgb.g + self.rgb.b), 0.35);
        fragColor = vec4(hot * 2.0 * rim * Edge * flicker, 1.0);
        return;
    }

    vec3 best = vec3(0.0);
    float bestW = 0.0;
    float jitter = hash(pos) * 0.5;
    for (int d = 0; d < DIRS; d++) {
        float a = (float(d) + jitter) / float(DIRS) * TAU;
        vec2 dir = vec2(cos(a), sin(a));
        for (int s = 1; s <= STEPS; s++) {
            float f = float(s) / float(STEPS);
            float dist = f * f * Radius * (1.0 + Flames * 1.6 * max(dir.y, 0.0));
            vec2 at = uv - dir * dist * px;
            if (at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0) {
                break;
            }
            vec4 m = texture(MaskSampler, at);
            if (m.a > 0.02) {
                float reach = m.a * Radius;
                // dir points from the body to this pixel: up (y > 0) is where the flames rise.
                float n = fbm(vec2(pos.x / (reach * 0.9), pos.y / (reach * 1.6) - t * 2.2));
                reach *= (0.55 + 0.7 * n) * (1.0 + Flames * max(dir.y, 0.0) * (0.4 + 2.0 * n));
                float w = 1.0 - dist / max(reach, 1.0);
                if (w > bestW) {
                    bestW = w;
                    best = m.rgb;
                }
                break;
            }
        }
    }
    if (bestW <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    // Bright and a little whiter right at the body, falling off softly outwards.
    vec3 color = best * 2.0;
    vec3 core = mix(color, vec3(max(color.r, max(color.g, color.b))), 0.55);
    vec3 glow = color * bestW * bestW * 0.9 + core * smoothstep(0.82, 1.0, bestW) * 0.8;
    fragColor = vec4(glow, 1.0);
}
