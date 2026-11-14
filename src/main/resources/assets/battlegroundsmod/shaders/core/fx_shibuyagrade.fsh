#version 150

// Shibuya's screen grade (client/fx/ShibuyaAirFx), the approved preview's CSS (forja/jujutsu-shibuya-ambiente amb.js
// ambRender) in the same order: the CSS filter (saturate, contrast, sepia), then the overlay layers.
// - day (Mode 0): warm smog, a little washed: saturate(0.86) contrast(1.03) sepia(0.07), a beige wash from the top and a
//   grey vignette;
// - night (Mode 1): lively color, saturate(1.18) contrast(1.06), and magenta from the bottom-left corner, cyan from the
//   top-right and purple from the bottom, added on in "screen" (it only brightens: the night is never darkened);
// - metro (Mode 2): the station's cool fluorescent light, saturate(0.95) contrast(1.05), a faint green-blue tint and a
//   dark vignette.
// Fade takes it all back to the plain frame (walking out of the biome).
uniform sampler2D DiffuseSampler;
uniform float Mode;
uniform float Night;
uniform float Fade;

in vec2 uv;
out vec4 fragColor;

vec3 over(vec3 c, vec3 l, float a) { return mix(c, l, clamp(a, 0.0, 1.0)); }
vec3 screen(vec3 c, vec3 l, float a) { return 1.0 - (1.0 - c) * (1.0 - l * clamp(a, 0.0, 1.0)); }

vec3 saturate3(vec3 c, float s) {
    return vec3(dot(c, vec3(0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s)),
                dot(c, vec3(0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s)),
                dot(c, vec3(0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s)));
}

vec3 sepia3(vec3 c, float a) {
    vec3 s = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
    return mix(c, s, a);
}

// CSS radial-gradient(ellipse at 50% 45%, ...): the distance 0..1 to the farthest corner (uv has y up: 45% from the top
// is 0.55 from the bottom)
float vig(vec2 p) {
    vec2 d = (p - vec2(0.5, 0.55)) / (1.41421 * vec2(0.5, 0.55));
    return length(d);
}

void main() {
    vec3 src = texture(DiffuseSampler, uv).rgb;
    vec3 c = src;
    if (Mode < 0.5) {
        c = saturate3(c, 0.86);
        c = (c - 0.5) * 1.03 + 0.5;
        c = sepia3(c, 0.07);
        // linear-gradient(to bottom, beige 0.16 at the top, 0.04 at 55%, nothing from 80%)
        float yt = 1.0 - uv.y;
        float a = yt < 0.55 ? mix(0.16, 0.04, yt / 0.55) : mix(0.04, 0.0, clamp((yt - 0.55) / 0.25, 0.0, 1.0));
        c = over(c, vec3(222.0, 206.0, 150.0) / 255.0, a);
        c = over(c, vec3(70.0, 72.0, 80.0) / 255.0, 0.20 * clamp((vig(uv) - 0.55) / 0.45, 0.0, 1.0));
    } else if (Mode < 1.5) {
        c = saturate3(c, 1.18);
        c = (c - 0.5) * 1.06 + 0.5;
        // from a corner the farthest-corner ellipse has radii sqrt(2) of the screen
        float m = length(uv / 1.41421);
        float cy = length((uv - vec2(1.0, 1.0)) / 1.41421);
        c = screen(c, vec3(1.0, 60.0 / 255.0, 170.0 / 255.0), 0.20 * (1.0 - clamp(m / 0.55, 0.0, 1.0)));
        c = screen(c, vec3(40.0 / 255.0, 200.0 / 255.0, 1.0), 0.16 * (1.0 - clamp(cy / 0.50, 0.0, 1.0)));
        c = screen(c, vec3(120.0, 60.0, 160.0) / 255.0, 0.10 * (1.0 - clamp(uv.y / 0.40, 0.0, 1.0)));
    } else {
        c = saturate3(c, 0.95);
        c = (c - 0.5) * 1.05 + 0.5;
        c = over(c, vec3(150.0, 220.0, 200.0) / 255.0, 0.05);
        c = over(c, vec3(20.0, 30.0, 34.0) / 255.0, 0.25 * clamp((vig(uv) - 0.55) / 0.45, 0.0, 1.0));
    }
    fragColor = vec4(clamp(mix(src, c, Fade), 0.0, 1.0), 1.0);
}
