#version 150

// See souls_kk_sus.vsh. Premultiplied out (color * alpha, alpha); in the glow pass only the bright parts spill light,
// like the page's bloom (threshold 0.6).
uniform mat4 ModelViewMat;
uniform float Pass;
uniform float Mat;
uniform float Time;
uniform float Rise;
uniform float H;
uniform float A;
uniform float Stripe;
uniform float Flame;
uniform float Opq;
uniform float FadeY;
uniform float FadeW;
uniform float Gain;
uniform float Spin;
uniform float GlowPass;
uniform vec3 Base;
uniform vec3 Shade;
uniform vec3 Deep;
uniform vec3 Ink;
uniform vec3 Glow;
uniform vec3 LightDir;

in vec3 vN;
in vec3 vV;
in vec3 vW;
in vec3 vO;
in vec2 vUv;
in float vTg;

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

// the legs melt into chakra mist: below FadeY gone, whole again at FadeY + FadeW, the edge alive with rising noise
float legFade(vec3 w) { return (w.y - FadeY) / FadeW + fbm3(w * vec3(0.32, 0.22, 0.32) + vec3(0., -Time * 0.55, 0.)) * 0.6; }

void emit(vec3 premul, float a) {
    if (GlowPass > 0.5) {
        float k = smoothstep(0.55, 0.7, dot(premul, vec3(0.299, 0.587, 0.114)));
        if (k < 0.003) discard;
        fragColor = vec4(premul * k * 0.5, 0.);
    } else {
        fragColor = vec4(premul, a);
    }
}

void main() {
    int pass = int(Pass + 0.5), mat = int(Mat + 0.5);
    if (mat == 9) {                                                     // Kamui shuriken: four curved blades, the Kamui swirl in the middle
        vec2 p = vO.xz; float r = length(p), a = atan(p.y, p.x);
        float sw = sin(a * 3. + log(max(r, 0.02)) * 5. - Spin * 2.) * 0.5 + 0.5;
        float voidc = 1. - smoothstep(0.08, 0.32, r);
        float rim = 1. - abs(dot(normalize(vN), normalize(vV)));
        float n = fbm3(vec3(p * 3., Time * 0.8)) * 0.5 + 0.5;
        float k = smoothstep(0.55, 1.0, r) * 0.6 + rim * 0.7 + n * 0.3;
        vec3 col = mix(Deep, Shade, smoothstep(0.25, 0.7, k)); col = mix(col, Base, smoothstep(0.8, 1.1, k));
        col = mix(col, Deep * 0.25, smoothstep(0.35, 0.05, r) * sw);
        col = mix(col, vec3(0.02, 0.01, 0.05), voidc);
        float al = A * (0.75 + 0.25 * k);
        emit(col * al * 1.1, al * 0.9);
        return;
    }
    float cut = vW.y - Rise * H + gn(vW * 1.1 + Time * 0.7) * 0.5;
    if (cut > 0.) discard;
    if (pass == 0) {                                                    // eye sockets, eyes, the two in the gem: flat colors
        if (legFade(vW) < 0.) discard;
        if (GlowPass > 0.5) discard;
        fragColor = vec4(Base * A, A);
        return;
    }
    if (pass == 2) {                                                    // ink outline
        float lf = legFade(vW); if (lf < 0.15) discard;
        float a = A * 0.8 * smoothstep(0.15, 0.6, lf);
        if (GlowPass > 0.5) discard;
        fragColor = vec4(Ink * a, a);
        return;
    }
    vec3 N = normalize(vN), V = normalize(vV);
    if (pass == 4) {                                                    // chakra flames: tongues of ONE world noise field, brightest on the outline
        float lf = legFade(vW) + 0.25; if (lf < 0.) discard;
        float rim = 1. - abs(dot(N, V)), tg = vTg;
        float fl = fbm3(vW * vec3(0.9, 0.5, 0.9) + vec3(0., -Time * 2.2, 0.)) * 0.5 + 0.5;
        float body = smoothstep(0.25, 0.85, rim) * (0.35 + 0.9 * tg) * smoothstep(0.3, 0.6, fl + tg * 0.3);
        float hot = smoothstep(0.62, 0.8, fl + tg * 0.25);
        vec3 col = mix(Shade * 1.15, Glow * 1.25, hot);
        float a = body * 0.36 * Gain * A * smoothstep(0., 0.35, lf);
        if (a < 0.002) discard;
        if (GlowPass > 0.5) { fragColor = vec4(col * a * 0.45, 0.); return; }
        fragColor = vec4(col * a, 0.);                                  // added (the page's AdditiveBlending)
        return;
    }
    float lf = legFade(vW); if (lf < 0.) discard;
    vec3 L = normalize(mat3(ModelViewMat) * LightDir);
    float l = dot(N, L) * 0.5 + 0.5, f = abs(dot(N, V));
    vec3 col = l > 0.62 ? Base : l > 0.36 ? Shade : Deep;                                    // 3 bands of light
    col = mix(col, Glow, smoothstep(0.35, 0.05, f) * (Stripe < 0. ? 0.15 : 0.55));          // light chakra rim
    float ink = 0.;
    if (Stripe > 0.) ink = max(ink, smoothstep(0.43, 0.47, abs(fract(atan(vO.x, vO.z) * Stripe) - 0.5)));   // kimono pleats
    if (Stripe < 0.) { vec2 b = min(vUv, 1. - vUv); ink = max(ink, smoothstep(0.12, 0.07, min(b.x, b.y))); col *= 0.92; }   // each scale's edge
    vec3 tq = vW * 0.75 + vec3(0., -Time * 0.18, Time * 0.07);                               // the anime's marbled chakra
    vec3 tw = vec3(fbm3(tq * 0.6 + 2.7), fbm3(tq * 0.6 + 8.1), fbm3(tq * 0.6 + 5.3)) * 1.3;
    float tn = fbm4(tq + tw), tn2 = gn(tq * 2.6 + tw * 0.5);
    float cl = smoothstep(-0.12, 0.3, tn); col = mix(col, Deep * 0.42, cl * 0.95); col *= 1. - 0.25 * cl;
    col = mix(col, Glow * 0.95, smoothstep(-0.25, -0.6, tn) * 0.55);
    col *= 0.94 + 0.1 * tn2;
    col = mix(col, Ink, ink * 0.85);
    float edge = smoothstep(-0.45, 0., cut); col = mix(col, Glow * 1.3, edge);              // the lit edge where it is still being born
    float flame = Flame > 0. ? smoothstep(0.25, 1., vO.y) * min(1., Flame) : 0.; col = mix(col, Glow, flame * 0.6);
    col = mix(col, Glow * 1.35, smoothstep(0.32, 0.0, lf));                                 // lit edge where the body turns to mist
    float a = mix(A * mix(0.86, 0.6, flame), A, Opq);
    a *= mix(mix(0.62, 1., Opq), 1., smoothstep(0.75, 0.15, f));                             // see-through middle, full outline
    a *= smoothstep(0., 0.3, lf) * (0.88 + 0.12 * gn(vW * 0.6 + vec3(0., -Time * 0.8, 0.)));
    emit(col * a, a);
}
