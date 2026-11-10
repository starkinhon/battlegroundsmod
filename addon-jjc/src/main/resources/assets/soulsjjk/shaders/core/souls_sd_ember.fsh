#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float A;

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
    float sd = vC.r;
    vec2 p = vUv;
    float a0 = sd * 40.0 + T * (1.0 + sd * 3.0);
    p = rot2(a0) * p;
    p.x *= 1.0 + sd * 1.6;
    float d = length(p) + (n3(vec3(p * 3.0, sd * 50.0)) - 0.5) * 0.6;
    if (d > 1.0) discard;
    float lf = vC.b;
    float life = smoothstep(0.0, 0.12, lf) * (1.0 - smoothstep(0.75, 1.0, lf));
    float fl = 0.6 + 0.4 * sin(T * 9.0 + sd * 70.0);
    vec3 ember = mix(vec3(1.0, 0.15, 0.08), vec3(1.0, 0.75, 0.6), smoothstep(0.6, 0.0, d)) * fl;
    vec3 ash = mix(vec3(0.03, 0.02, 0.02), vec3(0.9, 0.85, 0.85), smoothstep(0.75, 1.0, d));
    bool isAsh = vC.g > 0.5;
    vec3 col = isAsh ? ash : ember;
    float a = (1.0 - smoothstep(0.6, 1.0, d)) * life * A;
    fragColor = vec4(col * a, a * (isAsh ? 1.0 : 0.85));
}
