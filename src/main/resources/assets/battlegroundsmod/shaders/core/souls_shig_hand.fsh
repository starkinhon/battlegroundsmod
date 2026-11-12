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

uniform float Dis;
uniform float Out;
uniform float Seed;
uniform float Ground;
uniform vec3 Origin;

out vec4 fragColor;

void main() {
    if (vW.y < Ground) discard;
    vec3 w = vW + Origin;
    float n = vn(w * 3.7 + Seed * 20.) * .5 + .5;
    if (n < Dis) discard;
    if (Out > 0.) { fragColor = vec4(0.11, 0.07, 0.045, 1.); return; }
    vec3 L = normalize(vec3(0.35, 0.85, -0.3));
    float lit = smoothstep(0.02, 0.1, dot(normalize(vN), L));
    vec3 c = mix(vec3(0.64, 0.53, 0.38), vec3(0.90, 0.82, 0.66), lit);
    c = mix(c, vec3(0.62, 0.6, 0.56), smoothstep(0.0, 0.3, Dis));
    if (n < Dis + 0.07) c = vec3(0.12, 0.08, 0.05);
    float wr = vn(w * 6.5 + Seed * 50.);
    c *= 1. - 0.3 * (1. - smoothstep(0.0, 0.05, abs(wr))) * (1. - lit);
    fragColor = vec4(c, 1.);
}
