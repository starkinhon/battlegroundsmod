#version 150

// Bleach (Rukia, Byakuya): the glow layer added the way the approved pages add it (forja/bleach/lib.js, GLOWADD):
// only the halo around the glowing pieces, the bloom minus the sharp layer, never the sharp layer itself (the pieces
// are already in the frame). Intensity matches three.js's UnrealBloomPass (strength 1, radius 0.6: five levels
// weighted 0.52..0.68, about 3 in all, against our five levels of 1). Bright light is squeezed above 0.95 like there.
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform float Intensity;

in vec2 uv;
out vec4 fragColor;

void main() {
    vec3 sharp = texture(DiffuseSampler, uv).rgb;
    vec3 b = max(texture(BloomSampler, uv).rgb * Intensity - sharp, 0.0);
    float m = max(b.r, max(b.g, b.b));
    if (m > 0.95) {
        b *= (0.95 + 0.25 * (1.0 - exp(-(m - 0.95) / 0.25))) / m;
    }
    fragColor = vec4(b, 1.0);
}
