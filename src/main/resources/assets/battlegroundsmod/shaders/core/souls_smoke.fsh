#version 150

// Styles: 0 billow (thick smoke lit from above)  1 dust (grainy ground dust)  2 wisp (wind line along the movement)
//         3 ink (shadow smoke drawn at 12 a second, rim in the soul color)  4 energy (additive streak)
//         5 speck (additive soft spark)  6 ring (glowing ring lying on the ground)  7 shadow (dark stain on the ground)
//         8 glow (soft glowing disc on the ground)
// Additive ones (4, 5, 6, 8) write premultiplied color with alpha 1 and are drawn with ONE, ONE.

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in vec2 corner;
in vec4 vertexColor;
in vec4 lightColor;
in float vertexDistance;
in float height;
flat in float life;
flat in float seed;
flat in int style;

out vec4 fragColor;

float hh(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vn(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hh(i), hh(i + vec2(1.0, 0.0)), u.x), mix(hh(i + vec2(0.0, 1.0)), hh(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm5(vec2 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 5; i++) {
        s += a * vn(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

float fbm3(vec2 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 3; i++) {
        s += a * vn(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s / 0.875;
}

float cloudDensity(vec2 p, float sd, float t, float grain, out float n) {
    vec2 q = p * 1.25 + sd * 17.0;
    vec2 w = vec2(fbm5(q * 1.1), fbm5(q * 1.1 + vec2(5.2, 1.3)));
    n = fbm5(q * 1.7 + (w - 0.5) * 1.7 + vec2(0.0, -t * 0.9));
    n += (vn(q * 9.0) - 0.5) * grain;
    return (1.0 - length(p) * 1.18) + (n - 0.5) * 1.25;
}

float fogKeep() {
    if (vertexDistance <= FogStart) {
        return 1.0;
    }
    return 1.0 - smoothstep(FogStart, FogEnd, vertexDistance);
}

void main() {
    float t = life;
    vec2 p = corner;
    // Each puff turned its own way, slowly spinning; the light stays on top of the screen.
    float ang = seed * 6.2832 + (seed - 0.5) * t * 1.2;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 pr = vec2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
    vec2 lightDir = vec2(-sa, ca);
    float r = length(p);
    float ground = smoothstep(0.0, 0.3, height);
    float keep = fogKeep();
    vec3 tint = vertexColor.rgb;
    float strength = vertexColor.a;

    if (style <= 1) {
        bool dust = style == 1;
        float grain = dust ? 0.28 : 0.06;
        float n;
        float n2;
        float d0 = cloudDensity(pr, seed, t, grain, n);
        float erode = t * t * (dust ? 1.1 : 0.95) + t * 0.15;
        float d = d0 - erode * (1.3 - n * 0.75);
        float a = smoothstep(0.0, dust ? 0.22 : 0.34, d) * smoothstep(1.0, 0.82, r);
        float d1 = cloudDensity(pr + lightDir * 0.2, seed, t, grain, n2);
        float lit = clamp((d0 - d1) * 1.6 + 0.5, 0.0, 1.0);
        float shade = mix(dust ? 0.72 : 0.66, dust ? 1.1 : 1.14, smoothstep(0.12, 0.88, lit));
        shade *= mix(0.9, 1.04, clamp(dot(pr, lightDir) * 0.5 + 0.5, 0.0, 1.0));
        shade *= mix(0.88, 1.0, smoothstep(0.0, 0.8, d));
        vec3 col = tint * shade * lightColor.rgb;
        a *= strength * ground;
        if (a < 0.003) {
            discard;
        }
        fragColor = vec4(mix(FogColor.rgb, col, mix(1.0, keep, FogColor.a)), a) * ColorModulator;
    } else if (style == 2) {
        float band = exp(-pow(p.y * 2.3, 2.0));
        float along = smoothstep(-1.0, -0.35, p.x) * smoothstep(1.0, 0.45, p.x);
        float n = fbm5(vec2(p.x * 1.6 - t * 2.6 + seed * 9.0, p.y * 4.5 + seed * 3.0));
        float a = band * along * smoothstep(0.36 + t * 0.32, 0.72, n) * (1.0 - t * 0.5) * strength * ground;
        if (a < 0.003) {
            discard;
        }
        vec3 col = mix(tint, vec3(1.0), 0.35) * lightColor.rgb;
        fragColor = vec4(mix(FogColor.rgb, col, mix(1.0, keep, FogColor.a)), a) * ColorModulator;
    } else if (style == 3) {
        // 12 drawings a second, like hand drawn anime smoke.
        float frame = floor(GameTime * 24000.0 * 0.6);
        vec2 boil = vec2(hh(vec2(frame, seed * 91.0)), hh(vec2(seed * 37.0, frame))) - 0.5;
        vec2 q = (pr + boil * 0.06) * 1.1 + seed * 17.0;
        vec2 w = vec2(fbm3(q * 0.9), fbm3(q * 0.9 + vec2(5.2, 1.3)));
        float n = fbm3(q * 1.3 + (w - 0.5) * 1.5 + vec2(0.0, -t * 0.8));
        float d0 = (1.0 - r * 1.15) + (n - 0.5) * 1.3;
        float d = d0 - (t * t * 1.05 + t * 0.2) * (1.3 - n * 0.8);
        float fw = max(fwidth(d), 0.001);
        float a = smoothstep(0.0, fw * 2.5 + 0.015, d) * smoothstep(1.0, 0.82, r);
        float rim = 1.0 - smoothstep(fw, fw + 0.2, d);
        vec3 dark = (vec3(0.035, 0.03, 0.045) + tint * 0.07) * lightColor.rgb;
        vec3 edge = mix(tint, vec3(1.0), 0.25) * 1.25;
        vec3 col = mix(dark, edge, rim * 0.95);
        a *= strength * ground;
        if (a < 0.003) {
            discard;
        }
        fragColor = vec4(mix(FogColor.rgb, col, mix(1.0, keep, FogColor.a)), a) * ColorModulator;
    } else if (style == 4) {
        float band = exp(-pow(p.y * 2.6, 2.0));
        float core = exp(-pow(p.y * 7.0, 2.0));
        float along = smoothstep(-1.0, -0.3, p.x) * smoothstep(1.0, 0.55, p.x);
        float n = fbm5(vec2(p.x * 1.4 - t * 3.0 + seed * 9.0, p.y * 3.0 + seed * 2.0));
        float k = band * along * smoothstep(0.3 + t * 0.3, 0.7, n) * strength * ground * keep;
        fragColor = vec4((tint * 1.2 + vec3(core * 0.9)) * k, 1.0);
    } else if (style == 5) {
        float g = exp(-r * r * 7.0);
        float c2 = exp(-r * r * 45.0);
        float k = g * strength * smoothstep(1.0, 0.7, r) * ground * keep;
        fragColor = vec4((tint * g + vec3(c2)) * k, 1.0);
    } else {
        float time = GameTime * 1200.0;
        float a2 = atan(p.y, p.x);
        if (style == 6) {
            float n = fbm5(vec2(a2 * 3.0 + time * 1.5, r * 3.0 - time * 2.0));
            float wdt = 0.06 * (0.7 + 0.6 * n);
            float ring = exp(-pow((r - 0.82) / wdt, 2.0)) * smoothstep(1.0, 0.9, r);
            float inner = smoothstep(0.85, 0.0, r) * 0.18 * (0.6 + 0.4 * n);
            float k = (ring * (0.75 + 0.5 * n) + inner) * strength * keep;
            fragColor = vec4((tint + vec3(ring * ring * 0.5)) * k, 1.0);
        } else if (style == 7) {
            float n = fbm5(p * 2.5 + 7.0 + seed * 13.0);
            float a = smoothstep(1.0, 0.25, r + (n - 0.5) * 0.35) * strength;
            if (a < 0.003) {
                discard;
            }
            fragColor = vec4(FogColor.rgb * (1.0 - keep), a);
        } else {
            float k = exp(-r * r * 3.0) * smoothstep(1.0, 0.8, r) * strength * keep;
            fragColor = vec4(tint * k, 1.0);
        }
    }
}
