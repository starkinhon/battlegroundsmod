#version 150

float h31(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float vn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3. - 2. * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z) * 2. - 1.;
}
float fbm(vec3 p) { return vn(p) * .55 + vn(p * 2.07 + 11.3) * .3 + vn(p * 4.13 + 5.1) * .15; }

in vec3 vW;
in vec3 vN;
in float vAge;
in float vLum;
in float vLight;
in float vFade;

out vec4 fragColor;

void main() {
    if (vAge <= -0.4) discard;
    vec3 L = normalize(vec3(0.35, 0.85, -0.3));
    vec3 n = normalize(vN);
    float shade = (0.62 + 0.42 * max(dot(n, L), 0.) + 0.08 * n.y) * vLight;
    float k = smoothstep(-0.4, 0.45, vAge);
    vec3 dry = vec3(0.70, 0.66, 0.60) * (vLum * 1.3 + 0.25) * (0.9 + 0.2 * vn(vW * 3.1)) * shade;
    float cr = 1. - smoothstep(0.0, 0.07 + 0.05 * k, abs(fbm(vW * 1.9)));
    // over the block's own colour: mix(mix(block, dry, a1), dark, a2) in one blend
    float a1 = k * 0.9 * vFade, a2 = cr * k * vFade;
    float A = 1. - (1. - a1) * (1. - a2);
    if (A < 0.004) discard;
    vec3 X = (dry * a1 * (1. - a2) + vec3(0.07, 0.05, 0.04) * a2) / A;
    fragColor = vec4(X, A);
}
