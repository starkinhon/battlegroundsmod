#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Fade;

in vec2 vUv;
in vec4 vC;
in vec3 vW;
out vec4 fragColor;

float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float n3(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
float fb3(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * n3(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; } return s; }
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y); }
mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }

void main() {
    // Each its own (test 2026-11-09: they all read alike): its own tilt, squash and wobble (two to five lobes), its own
    // light, rim and inner glints; the small ones stay nearly round, the big ones are caps with a wobbling skirt.
    float seed = vC.r * 100.0 + vC.g * 37.0, fl = vC.g;
    float h1 = fract(sin(seed * 12.9898) * 43758.5453), h2 = fract(sin(seed * 78.233) * 24634.6345), h3 = fract(sin(seed * 39.425) * 11943.1231);
    vec2 p = rot2((h1 - 0.5) * (0.5 + fl * 2.0)) * vUv;
    p.x *= 0.88 + 0.26 * h2;
    p.y = p.y / (1.0 - fl * 0.8);
    if (fl > 0.05 && p.y > 0.0) p.y *= 1.0 + fl * (0.4 + 0.5 * h3);
    float an = atan(p.y, p.x);
    float lobes = 2.0 + floor(h3 * 4.0);
    float sp = 2.0 + 4.0 * h2 + fl * 5.0;
    float k = fb3(vec3(cos(an) * (1.2 + h1) + seed, sin(an) * (1.2 + h1) + T * sp, seed * 0.37)) - 0.5;
    k += 0.12 * (0.3 + fl) * sin(an * lobes + T * sp * 1.7 + seed);
    // the big ones: the bottom pulled in and rippling, a jellyfish skirt
    if (fl > 0.12 && p.y < 0.0) k += fl * 0.5 * (0.5 + 0.5 * sin(an * (5.0 + lobes) + T * 9.0 + seed)) * smoothstep(0.0, -0.8, p.y) - fl * 0.25;
    float edge = 1.0 + k * (0.22 + fl * 0.6);
    float r = length(p) / edge;
    if (r > 1.0) discard;
    vec3 n = vec3(p / edge, sqrt(max(1.0 - r * r, 0.0)));
    float rimw = 1.8 + 2.2 * h1;
    float f = pow(1.0 - n.z, rimw);
    vec3 L = normalize(vec3(-0.25 + (h2 - 0.5) * 0.6, 0.75, 0.6));
    float spec = pow(max(dot(n, L), 0.0), 40.0 + 60.0 * h3);
    // a second, softer glint low on the far side: the light bent through it
    float glint = pow(max(dot(n, normalize(vec3(0.3 - h1 * 0.4, -0.55, 0.75))), 0.0), 12.0) * (0.15 + 0.35 * h2);
    // the red surface above caught inside the top as a band
    float band = smoothstep(0.35, 0.7, n.y) * (1.0 - smoothstep(0.7, 0.95, r)) * (0.25 + 0.3 * h3);
    float under = pow(max(-n.y, 0.0), 3.0) * 0.5;
    vec3 c = vec3(0.06, 0.004, 0.003) * (1.0 - f) + vec3(0.85, 0.22, 0.16) * f * 1.2 + vec3(1.0, 0.55, 0.45) * spec * 1.6
           + vec3(0.9, 0.35, 0.25) * glint + vec3(0.7, 0.12, 0.08) * band + vec3(0.4, 0.05, 0.03) * under;
    float a = clamp(0.22 + 0.06 * h2 + 0.9 * f + spec + glint * 0.5 + band * 0.4, 0.0, 1.0) * vC.a;
    fragColor = vec4(c * Fade * vC.b * a, a * Fade);
}
