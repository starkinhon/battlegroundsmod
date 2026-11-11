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

uniform float T;
uniform float A;
uniform vec3 C0;
uniform vec3 C1;
uniform vec3 C2;

out vec4 fragColor;

void main() {
    float f = fbm(vec3(vUv.x * 9. + T * 7., vUv.y * 4., 1.)) * .5 + .5;
    float c2 = abs(vUv.y * 2. - 1.);
    float d = f + (1. - c2) * 0.35 - 0.35;
    if (d <= 0.) discard;
    vec3 c = mix(C2, C1, smoothstep(0.1, 0.16, d));
    c = mix(c, C0, smoothstep(0.34, 0.4, d));
    fragColor = vec4(c, A * smoothstep(0., 0.05, d));
}
