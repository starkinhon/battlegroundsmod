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

uniform float T;
uniform float Life;
uniform float Mode;
uniform float A;
uniform float Rise;
uniform vec3 C0;
uniform vec3 C1;
uniform vec3 C2;
uniform vec3 Origin;

out vec4 fragColor;

void main() {
    if (Mode > 1.5) {
        vec3 n = normalize(vN);
        vec3 L = normalize(vec3(0.35, 0.85, -0.3));
        vec3 c = vC.rgb * (0.45 + 0.65 * max(dot(n, L), 0.)) + C0;
        fragColor = vec4(c, A * vC.a);
        return;
    }
    vec3 vV = normalize(-vW);
    float rim = 1. - abs(dot(normalize(vN), vV));
    float f = fbm((vW + Origin) * 2.4 - vec3(0., T * 2.6 * Rise, 0.) + T * 0.3) * .5 + .5;
    float body = Mode < 0.5 ? (f * 0.6 + rim * 0.85 - 0.42) : (f * 0.8 + (1. - rim) * 0.5);
    float d = body - Life;
    if (d <= 0.) discard;
    float fw = clamp(fwidth(d), 1e-4, 0.08);
    vec3 c = mix(C2, C1, smoothstep(0.08, 0.14, d));
    c = mix(c, C0, smoothstep(0.32, 0.38, d));
    float a = smoothstep(0., 1.5 * fw, d) * A * (Mode < 0.5 ? 0.7 : 1.);
    fragColor = vec4(c, a);
}
