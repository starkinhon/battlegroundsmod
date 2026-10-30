#version 150

// Shapes drawn from the quad's own coordinates, no textures:
//   0 soft    round glow, param = how much of the middle burns white
//   1 ring    thin circle, param = thickness
//   2 streak  long tapered line (u along it), param = white core
//   3 beam    ribbon with a hot core across v, param = core width
//   4 fire    soft blob eaten by moving noise, param = how burnt out it is (0 fresh, 1 gone)
//   5 smoke   lumpy blob for the alpha layer, param = how dissolved it is
//   6 flare   glow with sharp rays, param = number of rays / 32
//   7 solid   the plain color
//   8 disc    a filled circle with a soft edge, param = how wide the edge is
// Near walls and the ground the effect fades out instead of cutting a hard line (soft particles).
uniform sampler2D Sampler1;
uniform vec2 Screen;
uniform vec2 DepthRange;
uniform float SoftOn;
uniform float GameTime;

in vec2 uv;
in vec4 color;
flat in int shape;
in float param;
in float viewDist;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * DepthRange.x * DepthRange.y / (DepthRange.y + DepthRange.x - z * (DepthRange.y - DepthRange.x));
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float alpha = 0.0;
    float core = 0.0;
    float seed = color.r * 7.0 + color.b * 13.0;
    float time = GameTime * 1200.0;

    if (shape == 0) {
        alpha = pow(max(0.0, 1.0 - r), 2.2);
        core = smoothstep(1.0 - param, 0.0, r) * param;
    } else if (shape == 1) {
        float w = max(0.02, param * 0.5);
        alpha = smoothstep(w, 0.0, abs(r - (1.0 - w))) * step(r, 1.0);
        core = alpha * alpha * 0.5;
    } else if (shape == 2) {
        float across = 1.0 - abs(p.y);
        float along = sin(uv.x * 3.14159);
        alpha = pow(max(0.0, across), 2.0) * pow(max(0.0, along), 0.6);
        core = pow(max(0.0, across), 8.0) * along * param;
    } else if (shape == 3) {
        float across = abs(p.y);
        alpha = pow(max(0.0, 1.0 - across), 1.6);
        core = smoothstep(max(0.02, param * 0.6), 0.0, across);
    } else if (shape == 4) {
        float n = fbm(p * 2.2 + vec2(seed, -time * 0.9));
        float body = 1.0 - r * (0.7 + 0.6 * n);
        alpha = smoothstep(param, param + 0.25, body);
        core = smoothstep(param + 0.35, param + 0.8, body) * (1.0 - param);
    } else if (shape == 5) {
        float n = fbm(p * 1.8 + vec2(seed, time * 0.15));
        float body = 1.0 - r * (0.75 + 0.5 * n);
        alpha = smoothstep(param * 0.8, param * 0.8 + 0.4, body);
    } else if (shape == 6) {
        float rays = max(2.0, floor(param * 32.0 + 0.5));
        float a = atan(p.y, p.x);
        float ray = pow(abs(cos(a * rays * 0.5)), 24.0) * pow(max(0.0, 1.0 - r), 1.5);
        alpha = pow(max(0.0, 1.0 - r), 3.0) + ray;
        core = smoothstep(0.35, 0.0, r);
    } else if (shape == 8) {
        alpha = smoothstep(1.0, 1.0 - max(0.02, param), r);
    } else {
        alpha = 1.0;
    }

    float fade = 1.0;
    if (SoftOn > 0.5) {
        float scene = linearDepth(texture(Sampler1, gl_FragCoord.xy / Screen).r);
        fade = clamp((scene - viewDist) / 0.6, 0.0, 1.0);
    }
    alpha = clamp(alpha, 0.0, 1.0) * color.a * fade;
    if (alpha < 0.003) {
        discard;
    }
    vec3 rgb = mix(color.rgb, vec3(1.0), clamp(core, 0.0, 1.0));
    fragColor = vec4(rgb, alpha);
}
