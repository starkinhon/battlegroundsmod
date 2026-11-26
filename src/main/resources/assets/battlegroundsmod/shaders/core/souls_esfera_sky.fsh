#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The sky of the approved preview (forja/bills-esfera-cutscene/page.html, the sky's material), same code: a navy
// gradient and the milky way's band of blue, violet and pink gas with dark dust across it.
uniform vec3 Top;
uniform vec3 Low;
in vec3 vD;
out vec4 fragColor;

float h(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h(i), h(i + vec3(1, 0, 0)), f.x), mix(h(i + vec3(0, 1, 0)), h(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h(i + vec3(0, 0, 1)), h(i + vec3(1, 0, 1)), f.x), mix(h(i + vec3(0, 1, 1)), h(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 6; i++) { s += a * n3(p); p *= 2.07; a *= 0.5; } return s; }

void main() {
    vec3 d = normalize(vD);
    float k = smoothstep(-0.6, 0.5, d.y);
    vec3 c = mix(Low, Top, k) * 0.55;
    vec3 axis = normalize(vec3(0.35, 0.8, 0.48));
    float band = exp(-pow(dot(d, axis) / 0.28, 2.0));
    float gas = fbm(d * 3.2 + 4.0);
    float gas2 = fbm(d * 6.5 - 2.0);
    float dust = smoothstep(0.45, 0.7, fbm(d * 9.0 + 11.0));
    vec3 neb = mix(vec3(0.17, 0.23, 0.85), vec3(0.42, 0.17, 0.85), smoothstep(0.35, 0.65, gas2));
    neb = mix(neb, vec3(0.75, 0.25, 0.55), smoothstep(0.6, 0.8, gas) * 0.6);
    float amt = band * smoothstep(0.3, 0.75, gas) * (1.0 - 0.75 * dust);
    c += neb * amt * 0.55 + vec3(0.8, 0.85, 1.0) * band * smoothstep(0.55, 0.8, gas2) * 0.12 * (1.0 - dust);
    c += vec3(0.1, 0.35, 0.8) * smoothstep(0.62, 0.85, fbm(d * 2.0 + 20.0)) * 0.25;
    fragColor = vec4(c, 1.0);
}
