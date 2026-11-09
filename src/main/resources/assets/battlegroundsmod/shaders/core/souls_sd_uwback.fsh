#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Fade;
uniform float Depth;
uniform vec3 CamL;

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
    vec3 d = normalize(vW - CamL);
    float up = d.y;
    vec3 c = mix(vec3(0.13, 0.006, 0.004), vec3(0.2, 0.014, 0.01), smoothstep(-0.6, 0.95, up));
    float bl = fb3(d * 3.0 + vec3(T * 0.45, T * 0.8, -T * 0.35)) * 1.2 * fb3(d * 6.5 - vec3(T * 0.7, -T * 0.3, T * 0.9) + fb3(d * 2.0 + T * 0.4));
    c *= 0.45 + 1.3 * bl;
    vec2 q = d.xz / (up + 1.25);
    float r = fb3(vec3(q * vec2(9.0, 2.2) + vec2(sin(T * 1.3) * 0.4, 0.0), T * 0.9));
    float ray = pow(clamp(r * 1.6 - 0.55, 0.0, 1.0), 2.0) * smoothstep(0.05, 0.9, up);
    c += vec3(0.42, 0.04, 0.03) * ray * 0.5;
    c *= exp(max(Depth, -2.0) * 0.32);
    fragColor = vec4(c * Fade, 1.0);
}
