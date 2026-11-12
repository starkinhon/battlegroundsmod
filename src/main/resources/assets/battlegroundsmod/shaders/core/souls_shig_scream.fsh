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
uniform float A;
uniform float A2;
uniform float T;
uniform float Len;

out vec4 fragColor;

void main() {
    if (Mode < 0.5) {
        // the front: a curved shell, torn at the rim, streaked by the wind, rings of pressure
        float vTh = vUv.x, vPh = vUv.y;
        float jag = vTh + 0.16 * vn(vec3(vPh * 6., 1., T * 3.));
        float edge = 1. - smoothstep(0.78, 1.0, jag);
        float streak = smoothstep(0.3, 0.75, vn(vec3(cos(vPh) * 9., sin(vPh) * 9., vTh * 3. - T * 7.)));
        float rings = pow(0.5 + 0.5 * sin(vTh * 26. - T * 34.), 6.);
        float a = A * edge * (0.28 + 0.4 * streak + 0.32 * rings);
        if (a < 0.01) discard;
        vec3 c = mix(vec3(0.74, 0.82, 0.90), vec3(1.), clamp(streak * 0.8 + rings * 0.6, 0., 1.));
        fragColor = vec4(c, a);
    } else {
        // the scream's body: speed lines and pressure rings running forward
        float vZ = vUv.x, vPh = vUv.y;
        float streak = smoothstep(0.42, 0.85, vn(vec3(cos(vPh) * 7., sin(vPh) * 7., vZ * 0.2 - T * 9.)));
        float rip = pow(0.5 + 0.5 * sin(vZ * 1.15 - T * 36.), 10.);
        float a = A2 * (1. - smoothstep(Len * 0.7, Len, vZ)) * smoothstep(0.2, 1.6, vZ) * (0.6 * streak + 0.35 * rip);
        if (a < 0.01) discard;
        fragColor = vec4(mix(vec3(0.80, 0.86, 0.92), vec3(1.), streak), a);
    }
}
