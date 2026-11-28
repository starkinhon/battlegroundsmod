#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// forja/itadori-kit shinjuku.js VEIN_F line by line (additive): the noise's valleys are the veins (thick and thin) with a
// blurred glow round them (the light through the skin), spreading out of the heart (Rev 0..1 reaches 2.2 blocks), the
// tip that runs ahead brighter, all of it beating (Beat). The noise is laid round the heart (blocks x 4.2, as the page's
// part-local vP x 4.2), so it rides the body. Plus the page's body tint: the skin 12% toward red while it lasts.
uniform vec3 Heart;
uniform float Rev;
uniform float Beat;
uniform float Amt;
uniform float Scale;
uniform float Seed;

in vec3 vP;

out vec4 fragColor;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

void main() {
    vec3 d = (vP - Heart) / Scale;
    vec3 q = d * 4.2 + Seed;
    float r1 = 1. - abs(fbm3(q)), r2 = 1. - abs(fbm3(q * 2.1 + 5.));
    float vein = smoothstep(.962, .988, r1) + .6 * smoothstep(.972, .992, r2);
    float halo = smoothstep(.91, .975, r1) * .28 + smoothstep(.94, .985, r2) * .12;
    float reach = length(d) - Rev * 2.2 + fbm3(q * .5) * .35;
    float tot = vein + halo;
    vec3 tint = vec3(1., 0.23, 0.16) * 0.12 * 0.5 * Amt;
    if (reach > 0. || tot < .03) {
        if (Amt < 0.01) discard;
        fragColor = vec4(tint, 1.);
        return;
    }
    float edge = smoothstep(-.25, 0., reach);
    vec3 c = mix(vec3(.45, .02, .05), vec3(1., .25, .22), clamp(Beat * .8 + edge, 0., 1.));
    vec3 col = mix(vec3(1., .1, .06) * halo, c * 1.25, clamp(vein, 0., 1.));
    fragColor = vec4(col * Amt + tint, 1.);
}
