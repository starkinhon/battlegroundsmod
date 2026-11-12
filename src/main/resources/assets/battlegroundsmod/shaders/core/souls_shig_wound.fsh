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

uniform float S;
uniform float Seed;
uniform float HB;

out vec4 fragColor;

void main() {
    vec2 q = vUv * 2. - 1.;
    float a = atan(q.y, q.x), r = length(q);
    float edge = 0.78 + 0.18 * vn(vec3(cos(a) * 2.3, sin(a) * 2.3, Seed)) + 0.06 * vn(vec3(cos(a) * 9., sin(a) * 9., Seed + 3.));
    edge *= S;
    if (r > edge) discard;
    float k = r / max(edge, 1e-3);
    vec3 c = mix(vec3(0.16, 0.0, 0.02), vec3(0.55, 0.05, 0.07), smoothstep(0.2, 0.65, k + 0.15 * vn(vec3(q * 7., Seed))));
    c = mix(c, vec3(0.93, 0.5, 0.48), smoothstep(0.78, 0.92, k));
    c *= 1. + 0.4 * HB * (1. - k);
    fragColor = vec4(c, 1.);
}
