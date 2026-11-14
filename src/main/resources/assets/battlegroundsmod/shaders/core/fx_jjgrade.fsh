#version 150

// The Colônia do Jogo do Abate's screen grade (client/fx/ColoniaAirFx), the approved preview's CSS in the same order
// (forja/jujutsu-colonia-ambiente amb.js ambRender): the frame desaturated and a touch more contrast (saturate(Sat)
// contrast(Contrast), the CSS filter matrices), then the layers over it, bottom first: the far blast's violet-white
// flash, the grey-violet wash, at night the fires' warmth rising from the bottom edge, and the purple vignette
// (an ellipse at 50% 45%, clear to 48%, its color at the corners). Never darkens the night: Java keeps Wash at 0 there.
uniform sampler2D DiffuseSampler;
uniform float Sat;
uniform float Contrast;
uniform vec4 Wash;
uniform float Warm;
uniform vec4 Vig;
uniform float Flash;

in vec2 uv;
out vec4 fragColor;

vec3 over(vec3 c, vec3 l, float a) { return mix(c, l, clamp(a, 0.0, 1.0)); }

void main() {
    vec3 c = texture(DiffuseSampler, uv).rgb;
    float s = Sat;
    c = vec3(dot(c, vec3(0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s)),
             dot(c, vec3(0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s)),
             dot(c, vec3(0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s)));
    c = (c - 0.5) * Contrast + 0.5;
    c = over(c, vec3(210.0, 170.0, 255.0) / 255.0, Flash * 0.14);
    c = over(c, Wash.rgb, Wash.a);
    c = over(c, vec3(1.0, 110.0 / 255.0, 40.0 / 255.0), Warm * max(0.0, 1.0 - uv.y / 0.35));
    // CSS ellipse farthest-corner from (50%, 45%): radii sqrt(2) * (0.5, 0.55) of the screen
    vec2 d = (uv - vec2(0.5, 0.55)) / (1.41421 * vec2(0.5, 0.55));
    float t = length(d);
    float k = clamp((t - 0.48) / 0.52, 0.0, 1.0);
    c = over(c, Vig.rgb, Vig.a * k);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
