#version 150

float h31(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float vn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3. - 2. * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z) * 2. - 1.;
}
float fbm(vec3 p) { return vn(p) * .55 + vn(p * 2.07 + 11.3) * .3 + vn(p * 4.13 + 5.1) * .15; }
in vec2 vUv;
in vec3 vW;
in vec3 vN;
in vec4 vC;

uniform float Mode;
uniform float Reveal;
uniform float A;
uniform float Len;
uniform float Mark;

out vec4 fragColor;

float cap(vec2 p, vec2 a, vec2 b, float r) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0., 1.); return length(pa - ba * h) - r; }

void main() {
    if (Mode < 0.5) {
        float x = vUv.x * Len, y = (vUv.y - .5) * 1.2;
        float off = 0.22 * vn(vec3(x * 0.7, 3.1, 0.)) + 0.08 * vn(vec3(x * 2.3, 7., 0.));
        float mainL = 1. - smoothstep(0.015, 0.05, abs(y - off));
        float br = 1. - smoothstep(0.01, 0.035, abs(y - off - 0.25 * vn(vec3(x * 1.7, 9., 0.)) * step(0.4, fract(x * 0.37))));
        float a = max(mainL, br * 0.6) * step(x, Reveal * Len) * A * smoothstep(0., 0.6, x);
        if (a < 0.01) discard;
        fragColor = vec4(0.09, 0.06, 0.04, a);
    } else {
        vec2 p = (vUv - .5) * 5.;
        p.y += 0.35;
        float d = length(p * vec2(1., 0.9)) - 0.55;
        d = min(d, cap(p, vec2(-0.36, 0.45), vec2(-0.55, 1.25), 0.11));
        d = min(d, cap(p, vec2(-0.12, 0.55), vec2(-0.16, 1.5), 0.12));
        d = min(d, cap(p, vec2(0.13, 0.55), vec2(0.2, 1.42), 0.12));
        d = min(d, cap(p, vec2(0.36, 0.45), vec2(0.58, 1.12), 0.1));
        d = min(d, cap(p, vec2(0.45, -0.1), vec2(1.0, 0.3), 0.12));
        d += 0.04 * vn(vec3(p * 6., 1.));
        float rim = 1. - smoothstep(0.02, 0.08, abs(d));
        float inner = step(d, 0.) * (1. - smoothstep(0.0, 0.05, abs(vn(vec3(p * 3.2, 2.))))) * 0.8;
        float ang = atan(p.y, p.x), rr = length(p);
        float rays = (1. - smoothstep(0.0, 0.04, abs(vn(vec3(ang * 4., rr * 0.8, 5.))))) * step(0., d) * (1. - smoothstep(0.6, 2.3, rr));
        float a = max(rim, max(inner, rays * 0.7)) * Mark;
        if (a < 0.01) discard;
        fragColor = vec4(0.09, 0.06, 0.04, a);
    }
}
