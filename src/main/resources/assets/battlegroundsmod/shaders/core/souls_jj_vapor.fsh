#version 150

// The vapour rising off an exorcised curse (approved preview tools/mobs/jjmobs, JJFX.vapor, Stark 2026-11-14): dark
// grey-indigo wisps born at the edge the body evaporates along (Front, the same edge as souls_ash style 4), rising
// and opening a little, gone within a block. A volume raymarched in a box, never particles; no glow, no fire colours.
// All positions camera-relative; C is the body's feet, Half its footprint's half size.
uniform vec3 Lo;
uniform vec3 Hi;
uniform vec3 C;
uniform vec2 Half;
uniform float Front;
uniform float K;
uniform float Time;

in vec3 vW;
out vec4 fragColor;

float jh(vec3 p) { p = fract(p * 0.3183 + vec3(0.71, 0.11, 0.42)); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float jn(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(jh(i), jh(i + vec3(1, 0, 0)), f.x), mix(jh(i + vec3(0, 1, 0)), jh(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(jh(i + vec3(0, 0, 1)), jh(i + vec3(1, 0, 1)), f.x), mix(jh(i + vec3(0, 1, 1)), jh(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float jf(vec3 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 3; i++) { s += a * jn(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; } return s; }

void main() {
    vec3 rd = normalize(vW);
    vec3 t0 = Lo / rd, t1 = Hi / rd;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    float a = max(max(max(tn.x, tn.y), tn.z), 0.0), b = min(min(tf.x, tf.y), tf.z);
    if (b <= a) {
        discard;
    }
    const int N = 22;
    float st = (b - a) / float(N);
    float j = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec4 acc = vec4(0.0);
    for (int i = 0; i < N; i++) {
        vec3 p = rd * (a + st * (float(i) + j));
        vec3 l = p - C;                        // relative to the feet, so the noise stays put while the camera moves
        float rel = l.y - Front;
        vec2 q = l.xz / (Half * (1.0 + max(rel, 0.0) * 0.5));
        float foot = 1.0 - smoothstep(0.45, 1.0, length(q));
        vec3 dw = min(p - Lo, Hi - p);
        foot *= smoothstep(0.0, 0.25, min(min(dw.x, dw.z), dw.y));
        float band = smoothstep(-0.1, 0.03, rel) * exp(-max(rel, 0.0) * 2.6);
        vec3 w = vec3(l.x * 2.6, l.y * 1.8 - Time * 1.3, l.z * 2.6);
        w.xz += 0.5 * vec2(sin(w.y * 1.7 + w.z), cos(w.y * 1.3 + w.x));
        float den = max(jf(w) - 0.44, 0.0) * 1.7 * band * foot * K;
        if (den > 0.001) {
            vec3 col = mix(vec3(0.44, 0.41, 0.52), vec3(0.12, 0.10, 0.16), clamp(den * 2.0, 0.0, 1.0));
            float al = 1.0 - exp(-den * st * 7.0);
            acc.rgb += (1.0 - acc.a) * col * al;
            acc.a += (1.0 - acc.a) * al;
            if (acc.a > 0.97) {
                break;
            }
        }
    }
    if (acc.a < 0.004) {
        discard;
    }
    // premultiplied, blended ONE / ONE_MINUS_SRC_ALPHA
    float o = 0.85;
    fragColor = vec4(acc.rgb * o, acc.a * o);
}
