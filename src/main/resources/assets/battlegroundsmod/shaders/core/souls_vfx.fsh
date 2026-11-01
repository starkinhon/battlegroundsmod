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
//   9 sun     a boiling star: white-hot middle, orange cells and bright streaks, a glowing rim and flames licking out
//             past it (the color tints it, white for a real sun), param = how far the flames reach
//  10 black hole  Infinite Void's: photon ring and swirling blue rings around an empty middle, param = the hole's size
//  11 splat   an ink splash with tendrils and droplets (black blots, white ink), param = which one
// A very dark color never burns white (dark rims and lines stay dark).
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
    vec3 base = color.rgb;

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
    } else if (shape == 9) {
        float edge = 1.0 / (1.0 + max(0.05, param));
        vec2 q = p / edge;
        float rr = length(q);
        float t = time * 0.12;
        // Seen on a ball: the surface pattern bunches up towards the rim and slides around.
        float z = sqrt(max(0.0, 1.0 - min(1.0, rr * rr)));
        vec2 sp = q / (0.35 + z);
        float n = fbm(sp * 2.6 + vec2(seed + t, -t * 0.6));
        float n2 = fbm(sp * 6.0 + vec2(-t * 1.4, seed * 2.0 + t));
        vec3 deep = vec3(0.88, 0.22, 0.02);
        vec3 mid = vec3(1.0, 0.52, 0.04);
        vec3 hot = vec3(1.0, 0.86, 0.32);
        vec3 surf = mix(deep, mid, smoothstep(0.3, 0.7, n));
        surf = mix(surf, hot, smoothstep(0.5, 0.85, n2) * 0.85);
        // Bright yellow streaks across it, like the reference's filaments.
        float streak = pow(abs(sin((sp.x * 0.8 + sp.y) * 4.0 + n * 7.0 + t * 2.0)), 14.0);
        surf = mix(surf, vec3(1.0, 0.92, 0.45), streak * 0.7);
        // White-hot in the middle, a glowing white-yellow rim.
        surf = mix(surf, vec3(1.0, 0.98, 0.88), smoothstep(0.6, 0.0, rr) * 0.95);
        surf = mix(surf, vec3(1.0, 0.95, 0.75), smoothstep(0.82, 1.0, rr) * 0.85);
        float disc = smoothstep(1.0, 0.985, rr);
        // Flames and loops of fire past the rim, moving.
        float a = atan(p.y, p.x);
        float fl = fbm(vec2(a * 2.5 + seed, r * 5.0 - time * 0.9));
        float fl2 = fbm(vec2(a * 6.0 - seed, r * 9.0 - time * 1.6));
        float reach = edge * (1.0 + (0.12 + 0.55 * fl * fl2) * max(0.05, param) * 2.2);
        float flame = smoothstep(reach, edge, r) * (1.0 - disc);
        vec3 flameColor = mix(vec3(1.0, 0.3, 0.04), vec3(1.0, 0.82, 0.35), pow(flame, 1.5));
        base = mix(flameColor, surf, disc) * color.rgb;
        alpha = max(disc, flame * 0.95);
    } else if (shape == 10) {
        // Infinite Void's black hole (the hole itself is a black disc drawn over it): a thin white-hot photon ring at the
        // hole's edge, then a disk of concentric blue and cyan rings swirling around it, the inner ones faster, torn
        // into streaks, and two faint outer rings. param: the hole's radius on the quad.
        float hole = max(0.05, param);
        float a = atan(p.y, p.x);
        float t = time * 0.05;
        float x = (r - hole) / (1.0 - hole);
        float spin = a + t * (1.6 / (0.25 + max(x, 0.0)));
        float n = fbm(vec2(spin * 1.2 + seed, x * 6.0));
        float n2 = fbm(vec2(spin * 3.0 - seed, x * 18.0 - t));
        float bands = 0.55 + 0.45 * sin(x * 38.0 + n * 5.0);
        float disk = smoothstep(0.0, 0.04, x) * pow(max(0.0, 1.0 - x), 1.4);
        float streaks = pow(n2, 2.0) * 1.6;
        float photon = exp(-pow((r - hole) / 0.012, 2.0));
        float glowIn = exp(-pow((r - hole) / 0.06, 2.0));
        float outer = exp(-pow((x - 0.55) / 0.05, 2.0)) * 0.5 + exp(-pow((x - 0.8) / 0.03, 2.0)) * 0.35;
        float body = disk * (0.35 + 0.65 * bands) * (0.5 + streaks) + outer * (0.6 + 0.4 * n);
        vec3 deep = vec3(0.1, 0.35, 0.85);
        vec3 cyan = vec3(0.6, 0.92, 1.0);
        base = mix(deep, cyan, clamp(body * 0.8 + glowIn, 0.0, 1.0)) * color.rgb;
        alpha = (body * 0.9 + photon * 1.5 + glowIn * 0.6) * step(hole * 0.97, r) * step(r, 1.0);
        core = photon + glowIn * 0.3;
    } else if (shape == 11) {
        // An ink splash: a lumpy blot with tendrils thrown out and droplets around it. param picks which one (0 to 1).
        float s = param * 37.0 + seed;
        float a = atan(p.y, p.x);
        float lumps = fbm(vec2(a * 1.6 + s, s * 0.3));
        float spikes = pow(fbm(vec2(a * 5.0 - s, s + 4.0)), 3.0) * 1.8;
        float edge = 0.36 + 0.25 * lumps + spikes * 0.45;
        float body = smoothstep(edge, edge - 0.04, r);
        vec2 g = p * 6.0 + s;
        vec2 f = fract(g) - 0.5;
        float h = hash(floor(g));
        float drop = step(0.82, h) * smoothstep(0.18 * h, 0.12 * h, length(f)) * smoothstep(1.0, 0.5, r) * step(edge, r);
        float tex = 0.85 + 0.15 * fbm(p * 5.0 + s);
        alpha = max(body * tex, drop);
    } else {
        alpha = 1.0;
    }
    if (max(color.r, max(color.g, color.b)) < 0.15) {
        core = 0.0;
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
    vec3 rgb = mix(base, vec3(1.0), clamp(core, 0.0, 1.0));
    fragColor = vec4(rgb, alpha);
}
