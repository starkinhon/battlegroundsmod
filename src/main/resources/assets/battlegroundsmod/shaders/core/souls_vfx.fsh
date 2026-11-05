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
//  12 coffin  a wall of Aizen's Black Box: black light with faint violet veins and a crisp violet rim (alpha layer);
//             param < 0.5: a wall rising from the bottom (rise = param * 2) with a ragged burning top,
//             param >= 0.5: the lid shutting from the edges in ((param - 0.5) * 2 shut)
//  13 lance   a lance of black light on a ribbon (u from base to tip); param < 0.5: the black body with a violet rim
//             (alpha layer), param >= 0.5: its soft glow (additive)
//  14 shard   a shard of glass or eggshell; param < 0.5 a quad, else a triangle; fract(param * 2) slides the glint
//  15 wing    a black butterfly wing (u root to tip); param < 0.5 the body, else its glow
//  16 orb     the soft glow of shape 0 that is never faded near surfaces (something sitting right on a body)
//  17 aura    Aizen's reiatsu, cartoon: a black-violet mass full of stars with a sharp, boiling violet rim and glow
//             spilling out past it; param < 0.5 the body (alpha layer), else the glow (additive);
//             fract(param * 2) < 0.5 a flame rising from the bottom of the quad, else a round burst
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
    } else if (shape == 12) {
        // Time in steps of 12 a second, like a drawing: the burning edge boils instead of sliding.
        float tq = floor(time * 12.0) / 12.0;
        float e = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float front;
        if (param < 0.5) {
            float rise = param * 2.0;
            float jag = (fbm(vec2(uv.x * 7.0 + seed, tq * 1.7)) - 0.5) * 0.16 * (1.0 - smoothstep(0.85, 1.0, rise));
            front = rise * 1.08 + jag - uv.y;
        } else {
            float k = (param - 0.5) * 2.0;
            float a = atan(uv.y - 0.5, uv.x - 0.5);
            float jag = (fbm(vec2(a * 3.0 + seed, tq * 1.7)) - 0.5) * 0.08 * (1.0 - smoothstep(0.85, 1.0, k));
            front = k * 0.56 + jag - e;
        }
        float fw = max(fwidth(front), 1.0e-4);
        float cover = smoothstep(0.0, 1.5 * fw, front);
        float burn = 1.0 - smoothstep(0.0, 9.0 * fw, front);
        float n = fbm(uv * 5.0 + vec2(seed, tq * 0.35));
        float vein = (1.0 - smoothstep(0.0, 0.035, abs(n - 0.5))) * 0.3;
        float ew = max(fwidth(e), 1.0e-4);
        float rim = 1.0 - smoothstep(1.0, 6.0, e / ew);
        float rimHot = 1.0 - smoothstep(0.5, 2.5, e / ew);
        float rimSoft = exp(-e * 9.0) * 0.45;
        vec3 black = vec3(0.02, 0.012, 0.035);
        vec3 violet = vec3(0.55, 0.16, 1.0);
        vec3 lilac = vec3(0.86, 0.66, 1.0);
        vec3 c = black + violet * (vein + rimSoft);
        c = mix(c, violet, rim * 0.9);
        c = mix(c, lilac, rimHot * 0.8);
        c = mix(c, mix(violet, lilac, 0.5), burn * 0.9);
        base = c;
        alpha = cover * 0.97;
    } else if (shape == 13) {
        float hw = pow(max(0.0, 1.0 - uv.x), 0.9);
        float across = abs(p.y) / max(hw, 1.0e-3);
        if (param < 0.5) {
            float d = 1.0 - across;
            float fw = max(fwidth(d), 1.0e-4);
            alpha = smoothstep(0.0, 1.5 * fw, d) * step(uv.x, 0.995);
            float rim = 1.0 - smoothstep(1.0, 4.0, d / fw);
            base = mix(vec3(0.02, 0.012, 0.035), vec3(0.62, 0.25, 1.0), rim);
        } else {
            float soft = max(0.0, 1.0 - across * 0.5);
            alpha = pow(soft, 2.0) * smoothstep(0.0, 0.06, uv.x) * (1.0 - smoothstep(0.75, 1.0, uv.x));
            core = pow(soft, 8.0) * (1.0 - uv.x) * 0.5;
        }
    } else if (shape == 14) {
        // A shard of glass or eggshell (Kyoka Suigetsu's mirror, the Hogyoku cocoon): param < 0.5 a quad, else a
        // triangle (a, b, c); bright rim and a glint that slides across with fract(param * 2).
        float e = param >= 0.5 ? min(min(uv.y, 1.0 - uv.x), (uv.x - uv.y) * 0.7071) : min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float ew = max(fwidth(e), 1.0e-4);
        float rim = 1.0 - smoothstep(0.5, 3.0, e / ew);
        float k = fract(param * 2.0);
        float glint = pow(max(0.0, 1.0 - abs(uv.x + uv.y - 0.6 - k) * 5.0), 3.0);
        base = mix(color.rgb, vec3(1.0), clamp(rim * 0.85 + glint * 0.5, 0.0, 1.0));
        alpha = (0.8 + 0.2 * rim) * step(0.0, e);
    } else if (shape == 15) {
        // A black butterfly wing (the Hogyoku evolution), root at u = 0 and tip at u = 1: veins, an eye spot and a
        // violet rim. param < 0.5 the dark body (fract(param * 2) varies the outline), else its glow.
        vec2 q = vec2(uv.x, uv.y - 0.5);
        float a = atan(q.y, max(q.x, 1.0e-4));
        float rr = length(q);
        float vr = fract(param * 2.0);
        float R = 0.97 * pow(max(0.0, cos(a)), 0.55) * (1.0 - 0.07 * pow(abs(sin(a * 5.0 + vr * 2.0)), 3.0));
        R = min(R, 0.5 / max(abs(sin(a)), 1.0e-3));
        float d = R - rr;
        float fw = max(fwidth(d), 1.0e-4);
        float body = smoothstep(0.0, 1.5 * fw, d);
        float rim = 1.0 - smoothstep(0.0, 0.07, d);
        float vein = pow(abs(cos(a * 8.0 + sin(rr * 6.0) * 0.4)), 30.0) * smoothstep(0.08, 0.35, rr);
        float eye = length((q - vec2(0.62, 0.06 * (vr - 0.5))) / vec2(1.0, 0.8));
        float eyeRing = 1.0 - smoothstep(0.0, 0.025, abs(eye - 0.11));
        float eyeIn = smoothstep(0.08, 0.05, eye);
        float n = fbm(q * 6.0 + vec2(seed, time * 0.2));
        if (param < 0.5) {
            vec3 black = vec3(0.02, 0.012, 0.035);
            vec3 deep = vec3(0.23, 0.06, 0.56);
            vec3 violet = vec3(0.55, 0.16, 1.0);
            vec3 lilac = vec3(0.86, 0.66, 1.0);
            vec3 c = mix(black, deep, smoothstep(0.3, 1.0, rr / max(R, 1.0e-3)) * 0.6 + n * 0.15);
            c = mix(c, violet, vein * 0.7);
            c = mix(c, violet, eyeRing);
            c = mix(c, lilac, eyeIn * 0.6);
            c = mix(c, lilac, rim * 0.85);
            base = c;
            alpha = body * 0.96;
        } else {
            alpha = body * (rim * 0.9 + vein * 0.5 + eyeRing * 0.7 + 0.08) + (1.0 - body) * exp(-max(0.0, -d) * 14.0) * 0.35;
            core = rim * body * 0.3;
        }
    } else if (shape == 16) {
        alpha = pow(max(0.0, 1.0 - r), 2.2);
        core = smoothstep(1.0 - param, 0.0, r) * param;
    } else if (shape == 17) {
        // Drawn on twos-and-threes like the anime: the outline boils 10 times a second instead of sliding.
        float tq = floor(time * 10.0) / 10.0;
        bool burst = fract(param * 2.0) >= 0.5;
        float d;
        if (!burst) {
            float x = (uv.x - 0.5) * 2.0;
            float y = uv.y;
            float n = fbm(vec2(x * 2.5 + seed, y * 3.0 - tq * 2.2));
            float n2 = fbm(vec2(x * 7.0 - seed, y * 5.0 - tq * 3.5));
            float width = 0.6 * pow(max(0.0, 1.0 - y), 0.65) * (0.7 + 0.6 * n) + 0.1 * (n2 - 0.5);
            d = width - abs(x) * (1.0 + 0.5 * y);
        } else {
            float a = atan(p.y, p.x);
            float n = fbm(vec2(a * 2.2 + seed, r * 2.0 - tq * 1.5));
            float n2 = fbm(vec2(a * 6.0 - seed, tq * 2.0));
            d = 0.7 + 0.3 * (n - 0.5) + 0.1 * (n2 - 0.5) - r;
        }
        float fw = max(fwidth(d), 1.0e-4);
        float inside = smoothstep(0.0, 1.5 * fw, d);
        float rimW = 0.075;
        float rim = inside * (1.0 - smoothstep(rimW * 0.55, rimW, d));
        float rimHot = inside * (1.0 - smoothstep(rimW * 0.15, rimW * 0.35, d));
        // A second, broken outline just outside the first: the light spilling out of the contour.
        float wisp = fbm(vec2(uv.x * 9.0 + seed, uv.y * 6.0 - tq * 4.0));
        float d2 = d + 0.1 + 0.08 * wisp;
        float spill = (1.0 - inside) * smoothstep(0.0, 1.5 * fw, d2) * (1.0 - smoothstep(0.03, 0.06, d2)) * step(0.45, wisp);
        vec2 g = uv * vec2(26.0, 38.0);
        float h = hash(floor(g) + vec2(seed, 3.0));
        float twinkle = 0.5 + 0.5 * sin(time * 5.0 + h * 40.0);
        float star = step(0.92, h) * smoothstep(0.38, 0.0, length(fract(g) - 0.5)) * twinkle * smoothstep(0.0, 0.12, d);
        vec3 black = vec3(0.04, 0.01, 0.08);
        vec3 deep = vec3(0.24, 0.05, 0.46);
        vec3 violet = vec3(0.66, 0.24, 1.0);
        vec3 pink = vec3(1.0, 0.7, 1.0);
        if (param < 0.5) {
            vec3 c = mix(deep, black, smoothstep(0.0, 0.3, d));
            c = mix(c, vec3(0.9, 0.75, 1.0), star);
            c = mix(c, violet, rim);
            c = mix(c, pink, rimHot);
            base = c;
            alpha = inside;
        } else {
            float outside = (1.0 - inside) * exp(-max(0.0, -d) * 7.0);
            alpha = rim + spill * 0.9 + outside * 0.55 + star * 0.5;
            core = rimHot * 0.7 + spill * 0.3;
        }
    } else {
        alpha = 1.0;
    }
    if (max(color.r, max(color.g, color.b)) < 0.15) {
        core = 0.0;
    }

    float fade = 1.0;
    // The Black Box's walls and lances stay solid right in front of whoever is inside, and shards, wings and orbs sit
    // right on bodies (no soft fade).
    if (SoftOn > 0.5 && (shape < 12 || shape > 16)) {
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
