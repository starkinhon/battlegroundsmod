#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The approved preview's sun (forja/bills-esfera-cutscene/page.html, sunMat, sunGlowMat and softSprite), same code.
// Mode 0, the body (normal blend): the inside is exactly the mod's Esfera (souls_vfx.fsh shape 9), and past its edge
// the tongues of fire bubbling and the blobs that break off. Mode 1, its light (added): the hot corona, the rays, the
// crooked waves of flame spreading out and the white-hot core. Mode 2, a soft round glow (added; Inner = where it is at
// 0.45, Tint = color and opacity). The quad reaches 3.2 radii from the middle.
uniform float Mode;
uniform float Time;
uniform float Seed;
uniform float Inner;
uniform vec4 Tint;
in vec2 vu;
out vec4 fragColor;

const float SOL_S = 3.2;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { float v = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { v += noise(p) * a; p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; } return v; }

void main() {
    float time = Time;
    float seed = Seed;
    if (Mode > 1.5) {
        float d = length(vu);
        float a = d < Inner ? mix(1.0, 0.45, d / max(Inner, 0.001)) : mix(0.45, 0.0, clamp((d - Inner) / max(1.0 - Inner, 0.001), 0.0, 1.0));
        if (d >= 1.0 || a * Tint.a < 0.002) discard;
        fragColor = vec4(Tint.rgb, a * Tint.a);
        return;
    }
    vec2 q = vu * SOL_S;
    float rr = length(q);
    float a = atan(q.y, q.x);
    if (Mode < 0.5) {
        float t = time * 0.12;
        float z = sqrt(max(0.0, 1.0 - min(1.0, rr * rr)));
        vec2 sp = q / (0.35 + z);
        float n = fbm(sp * 2.6 + vec2(seed + t, -t * 0.6));
        float n2 = fbm(sp * 6.0 + vec2(-t * 1.4, seed * 2.0 + t));
        vec3 deep = vec3(0.88, 0.22, 0.02), mid = vec3(1.0, 0.52, 0.04), hot = vec3(1.0, 0.86, 0.32);
        vec3 surf = mix(deep, mid, smoothstep(0.3, 0.7, n));
        surf = mix(surf, hot, smoothstep(0.5, 0.85, n2) * 0.85);
        float streak = pow(abs(sin((sp.x * 0.8 + sp.y) * 4.0 + n * 7.0 + t * 2.0)), 14.0);
        surf = mix(surf, vec3(1.0, 0.92, 0.45), streak * 0.7);
        surf = mix(surf, vec3(1.0, 0.98, 0.88), smoothstep(0.6, 0.0, rr) * 0.95);
        surf = mix(surf, vec3(1.0, 0.95, 0.75), smoothstep(0.82, 1.0, rr) * 0.85);
        float o = rr - 1.0;
        float l1 = fbm(vec2(a * 3.0 + seed, o * 4.0 - time * 1.1));
        float l2 = fbm(vec2(a * 8.0 - seed, o * 9.0 - time * 1.9));
        float l3 = fbm(vec2(a * 16.0 + seed * 2.0, o * 14.0 - time * 2.6));
        float ferve = 0.5 + 0.5 * sin(a * 5.0 + time * 0.7 + l1 * 6.0);
        float reach = 0.10 + 0.50 * l1 * l2 * (0.6 + 0.8 * ferve) + 0.10 * l3;
        float flame = smoothstep(reach, 0.0, o) * smoothstep(-0.03, 0.0, o);
        vec2 bq = vec2(a * 7.0, o * 7.0 - time * 1.3);
        float bol = smoothstep(0.62, 0.8, fbm(bq + seed * 3.0)) * smoothstep(0.55, 0.05, o) * step(0.02, o);
        flame = max(flame, bol * 0.9);
        float disc = smoothstep(1.0, 0.985, rr);
        vec3 flameColor = mix(vec3(0.95, 0.22, 0.03), vec3(1.0, 0.85, 0.4), pow(clamp(flame, 0.0, 1.0), 1.5));
        flameColor = mix(flameColor, vec3(1.0, 0.97, 0.8), smoothstep(0.12, 0.0, o) * 0.6);
        vec3 col = mix(flameColor, surf, disc);
        float alpha = max(disc, flame * 0.95);
        if (alpha < 0.01) discard;
        fragColor = vec4(col, alpha);
        return;
    }
    float o = max(rr - 1.0, 0.0);
    float fora = step(1.0, rr);
    float corona = exp(-o * 3.2) * 0.55 * fora + exp(-abs(rr - 1.0) * 18.0) * 0.5;
    float r1 = pow(fbm(vec2(a * 6.0 + seed, time * 0.25)), 3.0);
    float r2 = pow(fbm(vec2(a * 15.0 - seed, time * 0.4 - o * 0.6)), 4.0);
    float raios = (r1 * 1.8 + r2 * 2.4) * exp(-o * 1.3) * fora;
    float w = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float fase = fract(time * 0.09 + fi / 3.0);
        float onda = fase * 1.9;
        float torto = (fbm(vec2(a * 3.0 + fi * 5.0 + seed, time * 0.2)) - 0.5) * 0.35;
        float band = exp(-pow((o - onda - torto) / (0.05 + 0.1 * fase), 2.0));
        float rasgo = smoothstep(0.35, 0.7, fbm(vec2(a * 5.0 - fi * 3.0, time * 0.15 + fi)));
        w += band * rasgo * (1.0 - fase) * (1.0 - fase);
    }
    w *= fora * 0.9;
    float nucleo = exp(-rr * rr * 6.0) * (0.85 + 0.15 * sin(time * 3.1));
    vec3 cRaio = mix(vec3(1.0, 0.55, 0.12), vec3(1.0, 0.9, 0.55), exp(-o * 2.0));
    vec3 col = cRaio * (corona + raios) + vec3(1.0, 0.62, 0.2) * w + vec3(1.0, 0.97, 0.88) * nucleo;
    float fade = smoothstep(1.0, 0.8, length(vu));
    fragColor = vec4(col * fade, 1.0);
}
