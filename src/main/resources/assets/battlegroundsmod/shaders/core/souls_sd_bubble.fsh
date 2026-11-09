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
    float seed = vC.r * 100.0, fl = vC.g;
    vec2 p = vUv;
    p.y = p.y / (1.0 - fl * 0.8);
    if (fl > 0.05 && p.y > 0.0) p.y *= 1.0 + fl * 0.6;
    float an = atan(p.y, p.x);
    float k = fb3(vec3(cos(an) * 1.7 + seed, sin(an) * 1.7 + T * (2.6 + fl * 5.0), seed * 0.37)) - 0.5;
    float edge = 1.0 + k * (0.26 + fl * 0.6);
    float r = length(p) / edge;
    if (r > 1.0) discard;
    vec3 n = vec3(p / edge, sqrt(max(1.0 - r * r, 0.0)));
    float f = pow(1.0 - n.z, 2.5);
    float spec = pow(max(dot(n, normalize(vec3(-0.25, 0.75, 0.6))), 0.0), 60.0);
    float under = pow(max(-n.y, 0.0), 3.0) * 0.5;
    vec3 c = vec3(0.06, 0.004, 0.003) * (1.0 - f) + vec3(0.85, 0.22, 0.16) * f * 1.2 + vec3(1.0, 0.55, 0.45) * spec * 1.6 + vec3(0.4, 0.05, 0.03) * under;
    float a = clamp(0.28 + 0.9 * f + spec, 0.0, 1.0) * vC.a;
    fragColor = vec4(c * Fade * vC.b * a, a * Fade);
}
