#version 150

// Sukuna's Fuga: the shock ring on the ground (the preview's ringMat). One ring running out from the impact, thin and
// hot at its front, scorching behind it. vUv is the position on the ground in blocks from the centre. Additive.
uniform float R;
uniform float A;
uniform float FTime;

in vec2 vUv;
out vec4 fragColor;

float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float vn3(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
float fb3(vec3 p) { float a = 0.0, w = 0.5; for (int i = 0; i < 4; i++) { a += w * vn3(p); p = p * 2.07 + vec3(3.1, 1.7, 5.3); w *= 0.5; } return a; }

void main() {
    float r = length(vUv);
    float an = atan(vUv.y, vUv.x);
    float n = vn3(vec3(cos(an) * 3.0, sin(an) * 3.0, floor(FTime * 12.0) / 12.0 * 2.0));
    float front = R * (1.0 + 0.06 * (n - 0.5));
    float e = step(r, front);
    float bodyA = e * smoothstep(front - 2.5, front, r) * 0.35;
    float hot = smoothstep(front - 0.35, front, r) * step(r, front);
    vec3 c = mix(vec3(0.9, 0.25, 0.04), vec3(1.0, 0.85, 0.4), hot);
    fragColor = vec4(c * (bodyA * 0.55 + hot * 0.9) * A, 1.0);
}
