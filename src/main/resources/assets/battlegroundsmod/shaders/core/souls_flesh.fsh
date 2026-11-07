#version 150

// See souls_flesh.vsh. The blade's own texture turns into meat: veins running along it and throbbing toward the tip,
// the gold eyes staying open, a dark red glow from inside, and a wet shine. Slime is glossy blood red.
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform float GameTime;
uniform vec3 Light0_Direction;

in vec4 lightColor;
in vec4 data;
in vec2 texCoord0;
in vec2 vein;
in vec3 wNormal;
in vec3 wPos;

out vec4 fragColor;

float h(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float nz(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h(i), h(i + vec2(1.0, 0.0)), f.x), mix(h(i + vec2(0.0, 1.0)), h(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    // The page's clock: seconds, played 1.8 times slower (FleshSheathe.SLOW).
    float time = GameTime * 1200.0 / 1.8;
    float flesh = data.r;
    float wet = data.g;
    vec3 base;
    vec3 glow;
    if (data.a < 0.5) {
        base = vec3(0.61, 0.10, 0.20);
        glow = vec3(0.23, 0.02, 0.06);
        wet = 1.0;
    } else {
        vec4 tex = texture(Sampler0, texCoord0);
        if (tex.a < 0.1) {
            discard;
        }
        base = tex.rgb;
        float sy = vein.x;
        float along = sy * 0.9 - time * 7.0;
        float v = smoothstep(0.62, 0.95, nz(vec2(sy * 1.6, vein.y + 3.0)) + 0.18 * sin(along));
        float pulse = 0.5 + 0.5 * sin(along * 0.7);
        vec3 meat = mix(vec3(0.36, 0.03, 0.07), vec3(0.62, 0.08, 0.14), pulse);
        meat = mix(meat, vec3(0.30, 0.05, 0.32), v);
        float eye = smoothstep(0.35, 0.6, base.r * 0.3 + base.g * 0.7);
        base = mix(base, meat, flesh * (1.0 - eye * 0.85));
        glow = vec3(0.35, 0.02, 0.05) * flesh * (0.25 + 0.25 * sin(along));
    }
    vec3 n = normalize(wNormal);
    vec3 view = normalize(-wPos);
    if (dot(n, view) < 0.0) {
        n = -n;
    }
    vec3 half_ = normalize(normalize(Light0_Direction) + view);
    float spec = pow(max(dot(n, half_), 0.0), 48.0) * 0.9 + pow(1.0 - max(dot(n, view), 0.0), 4.0) * 0.3;
    vec3 col = base * lightColor.rgb + glow + vec3(1.0, 0.9, 0.92) * spec * wet * lightColor.rgb;
    fragColor = vec4(col, 1.0);
}
