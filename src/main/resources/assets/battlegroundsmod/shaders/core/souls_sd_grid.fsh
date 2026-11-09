#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float Measure;
uniform float Fade;
uniform float NGrid;

in vec3 vP;
in vec3 vN;
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
    vec3 q = vP * NGrid + 0.5;
    vec3 f = abs(fract(q) - 0.5);
    vec3 id = floor(q);
    vec3 tn = 1.0 - abs(vN);
    float line = 0.0;
    for (int i = 0; i < 3; i++) {
        float w = i == 0 ? tn.x : i == 1 ? tn.y : tn.z;
        if (w < 0.5) continue;
        float fi = i == 0 ? f.x : i == 1 ? f.y : f.z;
        float ii = i == 0 ? id.x : i == 1 ? id.y : id.z;
        float hh = h21(vec2(ii * 1.7 + float(i) * 31.0, 7.0));
        float pr = clamp((Measure * 1.45 - hh) / 0.45, 0.0, 1.0);
        float r = vn(vec2(dot(vP, vec3(1.3, 2.1, 0.9)) * 2.2 + ii * 0.37, float(i) * 5.0 + ii));
        float show = smoothstep(r - 0.12, r + 0.12, pr * 1.24 - 0.12) * smoothstep(0.0, 0.35, pr);
        float wd = mix(0.02, 0.06, pr);
        line = max(line, (1.0 - smoothstep(wd, wd + 0.05, fi)) * show);
    }
    float a = line * Fade;
    if (a < 0.004) discard;
    vec3 col = mix(vec3(0.02, 0.01, 0.02), vec3(1.0), line);
    fragColor = vec4(col * a, a);
}
