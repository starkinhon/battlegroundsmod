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
in vec3 vN;
in vec3 vV;
in float vLy;
in float vDelay;
in float vSeed;

uniform float G;
uniform float Cut;
uniform float HB;
uniform float A;

out vec4 fragColor;

void main() {
    float g = clamp((G - vDelay) / (1. - vDelay * 0.6), 0., 1.) * 1.12;
    if (vUv.x > g || vLy > Cut) discard;
    if (h31(vec3(vUv * vec2(60., 9.), vSeed)) > A) discard;
    vec3 n = normalize(vN);
    vec3 L = normalize(vec3(0.3, 0.6, 0.75));
    float lit = max(dot(n, L), 0.), band = mix(0.55, 1., smoothstep(0.18, 0.24, lit));
    band = mix(band, 1.15, smoothstep(0.7, 0.76, lit));
    float stri = smoothstep(0.35, 0.9, vn(vec3(vUv.x * 55., vUv.y * 8., vSeed)));
    vec3 c = mix(vec3(0.62, 0.07, 0.09), vec3(0.95, 0.45, 0.44), 0.35 + 0.4 * stri) * band;
    float tip = smoothstep(g - 0.1, g, vUv.x);
    c = mix(c, vec3(1., 0.78, 0.72), tip * 0.6);
    float rim = pow(1. - abs(dot(n, normalize(vV))), 3.);
    c += vec3(0.9, 0.25, 0.25) * rim * (0.35 + 0.6 * HB);
    fragColor = vec4(c, 1.);
}
