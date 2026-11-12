#version 150

uniform float Time;
uniform float A;
uniform float Rise;
uniform float GlowPass;
uniform vec3 BoxMin;
uniform vec3 BoxMax;
uniform vec3 CamObj;
uniform vec3 MistCol;
uniform vec3 MistGlow;

in vec3 vW;

out vec4 fragColor;

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

vec2 box(vec3 ro, vec3 rd) { vec3 t0 = (BoxMin - ro) / rd, t1 = (BoxMax - ro) / rd, a = min(t0, t1), b = max(t0, t1); return vec2(max(max(a.x, a.y), a.z), min(min(b.x, b.y), b.z)); }

void main() {
    vec3 ro = CamObj, rd = normalize(vW - ro); vec2 tb = box(ro, rd); tb.x = max(tb.x, 0.); if (tb.y <= tb.x) discard;
    float dt = (tb.y - tb.x) / 28., T = 1., j = fract(52.98 * fract(dot(gl_FragCoord.xy, vec2(0.0671, 0.00584)))); vec3 acc = vec3(0.);
    vec3 c = (BoxMin + BoxMax) * 0.5; float R = (BoxMax.x - BoxMin.x) * 0.5, Hh = BoxMax.y - BoxMin.y;
    for (int i = 0; i < 28; i++) { vec3 p = ro + rd * (tb.x + (float(i) + j) * dt); float h = (p.y - BoxMin.y) / Hh, r = length(p.xz - c.xz) / R;
        float n = fbm3(p * vec3(0.16, 0.2, 0.16) + vec3(0., -Time * 0.35, Time * 0.05));
        float d = max(0., n * 1.4 + 0.7 - h * 1.5 - r * r * 1.0) * smoothstep(1., 0.7, r);
        float a = 1. - exp(-d * dt * 0.75); vec3 col = mix(MistCol, MistGlow, smoothstep(0.35, 0.9, n + 0.4 - h));
        acc += T * a * col; T *= 1. - a; if (T < 0.03) break; }
    float k = A * smoothstep(0., 0.25, Rise);
    if (GlowPass > 0.5) discard;
    fragColor = vec4(acc * k, (1. - T) * k);
}
