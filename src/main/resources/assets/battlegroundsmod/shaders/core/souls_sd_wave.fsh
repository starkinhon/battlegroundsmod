#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float R;
uniform float A;
uniform float T;
uniform float Wall;

in vec2 vUv;
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
    float an = atan(vUv.y, vUv.x);
    float n = fb3(vec3(cos(an) * 4.0, sin(an) * 4.0, T * 3.0));
    float front = R * (1.0 + 0.05 * (n - 0.5));
    float r = Wall > 0.5 ? front : length(vUv);
    float h = Wall > 0.5 ? vW.y : 0.0;
    float x = (r - front);
    float band = exp(-x * x / 0.35);
    float trail = step(r, front) * smoothstep(front - 6.0, front, r) * 0.35;
    float rag = smoothstep(0.3, 0.75, fb3(vec3(an * 9.0, h * 1.6 - T * 2.0, T * 1.5)));
    float hgt = Wall > 0.5 ? (1.0 - smoothstep(0.0, 1.8 + 0.8 * n, h)) * rag : 1.0;
    float hot = exp(-x * x / 0.03);
    vec3 col = mix(vec3(0.05, 0.0, 0.01), vec3(0.9, 0.08, 0.1), band) + vec3(1.0, 0.75, 0.75) * hot * 0.6;
    float a = clamp((band * 0.85 + trail) * hgt, 0.0, 1.0) * A;
    if (a < 0.003) discard;
    fragColor = vec4(col * a, a);
}
