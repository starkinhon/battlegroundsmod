#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
uniform float T;
uniform float Water;
uniform float Ring;
uniform float RingA;
uniform float Light;
uniform float Arena;
uniform float Quake;
uniform vec2 Hit;
uniform vec2 Mid;
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
    vec2 xz = vW.xz;
    vec3 eye = normalize(CamL - vW);
    float fres = 0.18 + 0.6 * pow(1.0 - max(eye.y, 0.0), 4.0);
    if (Arena > 0.5) {
        // fine shivers over the mirror when the cuts shake the domain
        float sh = 0.0;
        if (Quake > 0.0) {
            float q1 = n3(vec3(xz * 1.7, T * 9.0)), q2 = n3(vec3(xz * 6.0 + 3.0, T * 15.0));
            sh = (smoothstep(0.62, 0.8, q1) * 0.6 + smoothstep(0.7, 0.9, q2) * 0.4) * Quake;
        }
        float a = clamp(fres * 0.95 - sh * 0.12, 0.0, 1.0);
        vec3 deep = vec3(0.004, 0.006, 0.01);
        fragColor = vec4(deep * (1.0 - a) + vec3(0.5, 0.08, 0.1) * sh * 0.04, 1.0 - a);
        return;
    }
    vec2 cell = floor(xz);
    float cd = length(cell + 0.5 - Mid) + h21(cell * 0.37) * 1.2;
    if (cd > Water) discard;
    vec2 rel = xz - Hit;
    float dh = length(rel);
    float x = dh - Ring;
    float pulse = exp(-x * x / 0.9) * sin(x * 2.4) * RingA;
    vec2 slope = normalize(rel + 1e-4) * pulse * 0.035;
    float sway = (n3(vec3(xz * 0.35, T * 0.03)) - 0.5) * 0.004;
    // the mirror of the day sky above, bent by the ripple
    vec3 r = reflect(-eye, normalize(vec3(-slope.x * 6.0 - sway * 40.0, 1.0, -slope.y * 6.0)));
    vec3 day = mix(vec3(0.62, 0.75, 0.9), vec3(0.32, 0.5, 0.82), clamp(r.y * 1.4, 0.0, 1.0)) * Light;
    vec3 deep = vec3(0.006, 0.016, 0.022) * Light;
    vec3 water = mix(deep, day, fres * 0.8);
    float crest = exp(-x * x / 0.012) * RingA * smoothstep(0.15, 0.9, Ring);
    float ang = atan(rel.y, rel.x);
    float along = 0.35 + 0.65 * smoothstep(0.35, 0.8, n3(vec3(ang * 3.0, dh * 0.2, T * 0.05)));
    vec3 cc = mix(vec3(0.55, 0.75, 1.0), vec3(1.0), smoothstep(0.6, 0.9, along));
    water += cc * crest * along * 1.3 + vec3(0.2, 0.45, 1.0) * crest * pow(along, 8.0) * 2.0;
    water *= 1.0 + pulse * 0.25;
    float fresh = exp(-max(0.0, Water - cd) * 0.6) * step(Water, 40.0);
    water += vec3(0.05, 0.12, 0.16) * fresh;
    fragColor = vec4(water, 1.0);
}
