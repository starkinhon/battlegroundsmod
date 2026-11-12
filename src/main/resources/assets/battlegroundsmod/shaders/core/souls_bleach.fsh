#version 150
// GERADO por forja/bleach/mod-shaders.sh: bleach_common.glsl + bleach_f.glsl, o mesmo texto da previa (nao editar aqui)
out vec4 fragColor;
#define BL_CAM vec3(0.)
// ===== Bleach (Rukia e Byakuya): o MESMO texto roda na previa (three.js, GLSL 3.00 es) e no jogo
// (shaders/include/bleach_common.glsl, #version 150). Nada de efeito diferente entre os dois lados.
// O lado que inclui define antes: BL_CAM (posicao da camera no espaco de Model: jogo vec3(0.), previa cameraPosition).
uniform mat4 Model;
uniform float Kind;
uniform float Life;
uniform float Seed;
uniform float Time;
uniform float Alpha;
uniform float GlowPass;
uniform vec4 A;
uniform vec4 B;
uniform vec3 Color;
uniform vec3 Color2;
// soma em vW pra prender padroes no mundo/corpo (previa 0; jogo: camera - pes do corpo, ja que la vW e relativo a camera)
uniform vec3 Anchor;

#define K_FROST 0.
#define K_CRYSTAL 1.
#define K_PILLAR 2.
#define K_LIGHT 3.
#define K_MIST 4.
#define K_SHARD 5.
#define K_BLIZ 6.
#define K_KIDO 7.
#define K_FIRE 8.
#define K_PETAL 9.
#define K_HALO 10.
#define K_ICE 11.
#define K_FLASH 12.
#define K_STAKE 13.
#define K_BURST 14.
#define K_SWARM 15.
#define K_BLADES 16.
#define K_SLASH 17.
#define K_BOLT 18.
#define K_WALL 19.
#define K_RIPPLE 20.
#define K_WING 21.
#define K_TINT 22.

bool isK(float k) { return abs(Kind - k) < 0.5; }

vec3 h33(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2. - 1.; }
float h13(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6. - 15.) + 10.);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 BL_ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = BL_ROT * p * 2.03; w *= 0.5; } return s; }
float fbm4(vec3 p) { float s = 0., w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = BL_ROT * p * 2.03; w *= 0.5; } return s; }
// voronoi 3D: (distancia ao centro mais perto, ao segundo, id da celula)
vec3 vor3(vec3 x) {
    vec3 i = floor(x), f = fract(x); float d1 = 8., d2 = 8., id = 0.;
    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
        vec3 g = vec3(float(a), float(b), float(c)), o = h33(i + g) * 0.5 + 0.5;
        float d = length(g + o - f);
        if (d < d1) { d2 = d1; d1 = d; id = h13(i + g); } else if (d < d2) { d2 = d; }
    }
    return vec3(d1, d2, id);
}
vec2 vor2(vec2 x) {
    vec2 i = floor(x), f = fract(x); float d1 = 8., d2 = 8.;
    for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
        vec2 g = vec2(float(a), float(b)), o = h33(vec3(i + g, Seed)).xy * 0.5 + 0.5;
        float d = length(g + o - f);
        if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
    }
    return vec2(d1, d2);
}
const vec3 BL_LIGHT = vec3(-0.40, 0.80, 0.45);

vec3 rotAxis(vec3 v, vec3 k, float a) { float c = cos(a), s = sin(a); return v * c + cross(k, v) * s + k * dot(k, v) * (1. - c); }
// ===== Bleach: fragmento (mesmo texto na previa e no jogo). Antes de incluir: bleach_common.glsl, BL_CAM e
// `out vec4 fragColor`. Saida pre-multiplicada (blend ONE, ONE_MINUS_SRC_ALPHA). GlowPass 1 = camada de brilho (bloom):
// so sai o que brilha de verdade (luz, Kido, fogo, faiscas do gelo), nunca o corpo do gelo nem a nevoa.
in vec3 vL;
in vec3 vW;
in vec2 vQ;
in float vD;

const float BL_TAU2 = 6.2831853;

void emit(vec3 c, float a, float g) {
    a = clamp(a, 0., 1.) * Alpha;
    if (GlowPass > 0.5) {
        a *= g;
        if (a < 0.002) discard;
        fragColor = vec4(c * a, a);
    } else {
        if (a < 0.002) discard;
        fragColor = vec4(c * a, a);
    }
}

vec3 facetN(vec3 V) {
    vec3 N = normalize(cross(dFdx(vW), dFdy(vW)));
    return dot(N, V) < 0. ? -N : N;
}

// gelo: corpo azul-claro translucido, fundo azul profundo com rachaduras por dentro (deslocadas pela vista = parece
// ter profundidade), veios brancos na superficie, reflexo duro nas faces e pontos que piscam. g = quanto vai pro brilho.
vec4 iceLook(vec3 N, vec3 V, vec3 lp, out float g) {
    float f = abs(dot(N, V)), fres = pow(1. - f, 2.2);
    vec3 inner = vor3(lp * 2.2 - V * 0.4);
    float crackIn = 1. - smoothstep(0., 0.07, inner.y - inner.x);
    vec3 surf = vor3(lp * 4.6 + 3.1);
    float crackS = 1. - smoothstep(0., 0.035, surf.y - surf.x);
    vec3 L = normalize(BL_LIGHT);
    float spec = pow(max(0., dot(reflect(-V, N), L)), 40.);
    float lam = dot(N, L) * 0.5 + 0.5;
    vec3 deep = vec3(0.09, 0.27, 0.58), body = vec3(0.52, 0.78, 0.97), pale = vec3(0.90, 0.97, 1.);
    float t = clamp(0.18 + 0.45 * lam + 0.6 * fres + 0.22 * inner.z - 0.22 * crackIn, 0., 1.);
    vec3 c = mix(deep, body, smoothstep(0., 0.55, t));
    c = mix(c, pale, smoothstep(0.6, 1., t));
    float tw = step(0.965, surf.z) * pow(max(0., sin(Time * 3. + surf.z * 90.)), 10.);
    c += vec3(0.8, 0.93, 1.) * crackS * 0.45 + vec3(0.55, 0.8, 1.) * crackIn * 0.22 + spec * 1.1 + tw * 1.4;
    g = clamp(spec * 0.9 + tw, 0., 1.);
    return vec4(c, clamp(0.6 + 0.32 * fres + crackS * 0.25 + spec * 0.5, 0., 1.));
}

void main() {
    vec3 V = normalize(BL_CAM - vW);
    float g = 0.;
    if (isK(K_FROST)) {
        // geada no chao (circulo ou capsula) + o circulo branco da Tsukishiro
        vec2 q = vQ;
        float d = length(vec2(q.x, max(abs(q.y) - B.y, 0.)));
        vec2 w = q * B.x;
        float e = A.x * (1. + 0.14 * fbm3(vec3(q * 3.2, Seed)));
        float inside = e - d;
        vec3 c = vec3(0.);
        float a = 0.;
        if (A.x > 0.001 && inside > -0.02) {
            vec2 v1 = vor2(w * 1.7), v2 = vor2(w * 4.3 + 7.);
            float vein = 1. - smoothstep(0., 0.07, v1.y - v1.x);
            float fine = 1. - smoothstep(0., 0.05, v2.y - v2.x);
            float fern = smoothstep(0.55, 0.9, gn(vec3(w * 2.6, Seed + 3.)) * 0.5 + 0.5);
            float melt = A.w;
            float keep = smoothstep(melt * 1.1 - 0.05, melt * 1.1 + 0.05, fbm3(vec3(w * 0.9, Seed + 9.)) * 0.5 + 0.5 + 0.08);
            float aa = fwidth(inside) * 1.5 + 1e-4;
            float m = smoothstep(-aa, aa, inside) * keep;
            if (B.z > 0.) m *= smoothstep(0., 0.03, B.z - (q.y * 0.5 + 0.5) + 0.04 * gn(vec3(q.x * 9., Seed, 2.)));
            float front = 1. - smoothstep(0., 0.07, inside);
            c = mix(vec3(0.52, 0.74, 0.95), vec3(0.97, 0.99, 1.), 0.25 + 0.45 * vein + 0.3 * fine + 0.25 * fern);
            c += front * 0.35;
            a = m * (0.55 + 0.3 * vein + 0.2 * fine + 0.15 * fern + 0.3 * front);
            g = front * 0.6;
        }
        if (A.z > 0.001 && A.y > 0.) {
            float rr = length(q);
            float lw = 0.16 / max(B.x, 0.1);
            float ang = fract(atan(q.x, -q.y) / BL_TAU2 + 1.);
            float drawn = step(ang, A.y);
            float head = exp(-pow((A.y - ang) * 40., 2.)) * step(A.y, 0.999);
            float ring = exp(-pow((rr - A.z) / lw, 2.)) * drawn;
            float glowR = exp(-pow((rr - A.z) / (lw * 5.), 2.)) * drawn * 0.5;
            float rg = ring + glowR + head * exp(-pow((rr - A.z) / (lw * 3.), 2.)) * 1.5;
            c = c * a + mix(vec3(0.45, 0.78, 1.), vec3(1.), clamp(ring + head, 0., 1.)) * rg;
            a = clamp(a + rg, 0., 1.);
            c /= max(a, 1e-3);
            g = max(g, clamp(rg, 0., 1.));
        }
        emit(c, a, g);
        return;
    }
    if (isK(K_SWARM)) {
        // petala-lamina: rosa com a ponta mais clara, borda branca, chanfro no topo; brilha quando a face pega a luz
        vec2 q = vQ;
        float d = length(vec2(q.x * (1.05 + 0.25 * fract(vD * 13.)), q.y));
        float notch = q.y > 0.55 ? smoothstep(0.0, 0.2, abs(q.x) - (q.y - 0.55) * 0.55) : 1.;
        if (d > 1. || notch < 0.5) discard;
        vec3 N = facetN(V);
        float glint = pow(max(0., dot(reflect(-V, N), normalize(BL_LIGHT))), 10.);
        float rim = smoothstep(0.72, 1., d);
        vec3 base = mix(vec3(0.86, 0.32, 0.55), vec3(0.98, 0.7, 0.84), smoothstep(-1., 0.6, q.y));
        vec3 c = mix(base, vec3(1., 0.95, 0.98), rim * 0.7);
        c *= 0.75 + 0.35 * abs(dot(N, normalize(BL_LIGHT)));
        c += glint * 1.2;
        emit(c, 0.96 * (1. - smoothstep(0.85, 1., Life * step(2.5, B.w))), glint);
        return;
    }
    if (isK(K_BLADES)) {
        // aco das laminas gigantes: reflexo do ceu, gume branco ondulado (hamon), brilho rosa na borda;
        // A.y desfaz em petalas de cima pra baixo (borda rosa acesa)
        vec2 q = vQ;
        vec3 N = facetN(V);
        float n = gn(vec3(q.x * 2., q.y * 9., vD * 40.)) * 0.5 + 0.5;
        float dis = A.y * 1.25 - (1. - q.y) * 0.6 - n * 0.45;
        if (dis > 0.) discard;
        vec3 R = reflect(-V, N);
        vec3 env = mix(vec3(0.34, 0.37, 0.44), vec3(0.88, 0.92, 1.), smoothstep(-0.3, 0.6, R.y));
        float spec = pow(max(0., dot(R, normalize(BL_LIGHT))), 60.);
        float fres = pow(1. - abs(dot(N, V)), 3.);
        float hamon = smoothstep(0.55 + 0.12 * sin(q.y * 40. + vD * 9.), 0.9, q.x);
        float lam = dot(N, normalize(BL_LIGHT)) * 0.5 + 0.5;
        vec3 steel = mix(vec3(0.30, 0.33, 0.40), vec3(0.80, 0.84, 0.92), smoothstep(0., 0.9, q.y) * 0.6 + lam * 0.5);
        float spine = smoothstep(-0.55, -0.95, q.x);
        float bevel = smoothstep(0.05, 0.0, abs(q.x - 0.2));
        vec3 c = mix(steel, env, 0.35) + spec * 1.6;
        c = mix(c, vec3(0.97, 0.99, 1.), hamon * 0.75);
        c *= 1. - spine * 0.45;
        c += vec3(1.) * bevel * 0.25;
        c += vec3(0.95, 0.55, 0.75) * fres * 0.45;
        float burn = smoothstep(-0.12, 0., dis) * step(0.001, A.y);
        c = mix(c, vec3(1., 0.55, 0.78) * 1.4, burn);
        emit(c, 1., spec * 0.6 + burn + hamon * 0.15);
        return;
    }
    if (isK(K_SLASH)) {
        // corte (Senka / Hakuteiken): meia-lua fina presa no mundo, desenhada da ponta pra tras (A.z), apaga pela cauda
        vec2 q = vQ;
        float r = length(q - vec2(0., -0.6));
        float ang = atan(q.x, q.y + 0.6);
        float u = clamp(ang / 2.2 + 0.5, 0., 1.);
        float th = A.y * sin(3.14159 * u);
        float band = 1. - smoothstep(0., th + 0.004, abs(r - 1.)) ;
        float drawn = smoothstep(0., 0.05, A.z - u) * smoothstep(0., 0.2, u - A.w);
        float core = 1. - smoothstep(0., th * 0.35 + 0.002, abs(r - 1.));
        vec3 c = mix(Color, vec3(1.), core);
        emit(c * 1.3, band * drawn, 1.);
        return;
    }
    if (isK(K_BOLT)) {
        vec3 Nr = normalize(vec3(vL.x, 0., vL.z));
        float f = abs(dot(normalize(mat3(Model) * Nr), V));
        float core = pow(f, 2.);
        float y = vQ.y;
        float a = smoothstep(0., 0.03, y) * (1. - smoothstep(A.z, A.z + 0.02, y)) * (0.35 + 0.65 * core);
        emit(mix(Color, vec3(1.), core) * 1.6, a, 1.);
        return;
    }
    if (isK(K_WALL)) {
        // Danku: parede quase invisivel; borda acesa, linhas finas de selo, onda onde algo bate (B.xy, idade B.z)
        vec2 q = vQ * vec2(A.x, A.y);
        float up = vQ.y * 0.5 + 0.5;
        float rise = smoothstep(A.z - 0.04, A.z, up);
        if (rise > 0.999) discard;
        float ex = min(A.x - abs(q.x), A.y - abs(q.y));
        float edge = exp(-ex * 9.);
        float grid = 1. - smoothstep(0., 0.025, abs(fract(q.y * 0.9 + 0.5 * step(0., q.x)) - 0.5) - 0.47);
        float hd = length(q - B.xy);
        float hit = B.z < 1. ? exp(-pow((hd - B.z * 3.5) * 3., 2.)) * (1. - B.z) : 0.;
        float scan = exp(-pow((up - A.z) * 30., 2.));
        vec3 c = mix(Color, vec3(1.), edge * 0.6 + hit);
        float a = (0.07 + 0.55 * edge + 0.12 * grid + 0.9 * hit + 0.8 * scan) * (1. - rise);
        emit(c, a, edge * 0.7 + hit + scan);
        return;
    }
    if (isK(K_RIPPLE)) {
        // o chao vira agua quando a espada afunda: aneis escuros com crista branca
        vec2 q = vQ * B.x;
        float r = length(q);
        float w = r - A.x;
        float rings = sin(w * 5.) * exp(-max(0., -w) * 0.35) * step(w, 0.);
        float crest = smoothstep(0.75, 1., rings), trough = smoothstep(0.4, 1., -rings);
        float fade = (1. - A.y) * (1. - smoothstep(B.x * 0.8, B.x, r));
        vec3 c = mix(vec3(0.15, 0.17, 0.24), vec3(0.95, 0.97, 1.), crest);
        emit(c, (crest * 0.8 + trough * 0.35) * fade, crest * 0.3 * fade);
        return;
    }
    if (isK(K_WING)) {
        // asa de luz: penas como faixas que saem da raiz, borda serrilhada, branca com miolo dourado claro
        vec2 q = vQ;
        float feathers = 7.;
        float fi = floor(q.y * feathers), fu = fract(q.y * feathers);
        float len = 0.55 + 0.45 * h13(vec3(fi, Seed, 2.)) ;
        float tipL = mix(len, 1., smoothstep(0.65, 1., q.y));
        if (q.x > tipL - 0.06 * sin(fu * 3.14159)) discard;
        float vein = 1. - smoothstep(0., 0.08, abs(fu - 0.5));
        float body = 1. - q.x / max(tipL, 0.01);
        vec3 c = mix(vec3(1., 0.94, 0.8), vec3(1.), 0.4 + 0.6 * vein);
        c = mix(vec3(1., 0.86, 0.6), c, smoothstep(0., 0.5, q.x));
        float a = (0.55 + 0.35 * body + 0.3 * vein) * smoothstep(0., 0.05, q.x);
        emit(c * 1.25, a, 1.);
        return;
    }
    if (isK(K_LIGHT)) {
        // coluna de luz: miolo branco onde a parede encara a vista, borda ciano, riscos subindo
        vec3 Nr = normalize(vec3(vL.x, 0., vL.z));
        float f = abs(dot(Nr, V));
        float y = vQ.y;
        float str = fbm3(vec3(vQ.x * 3., y * A.y * 0.18 - Time * 2.6, Seed)) * 0.5 + 0.5;
        float fade = smoothstep(0., 0.05, y) * (1. - smoothstep(0.55, 1., y));
        float core = pow(f, 3.);
        vec3 c = mix(vec3(0.55, 0.85, 1.), vec3(1.), core * 0.85 + 0.15);
        float a = (pow(f, 1.4) * 0.7 + core * 0.6) * (0.65 + 0.6 * str) * fade * A.z;
        emit(c, a * 0.85, 1.);
        return;
    }
    if (isK(K_MIST)) {
        // nevoa gelada cel: 3 faixas de luz, borda mais escura, rasga com a vida (saia baixa = A.y)
        vec3 N = normalize(vL);
        vec3 q = vL * 3. + vec3(0., -Time * 0.25, 0.) + Seed;
        float w = fbm3(q * 0.8 + 7.) * 0.8;
        float n = fbm4(q + vec3(w, 0., -w)) * 0.5 + 0.5;
        vec3 Nw = normalize(mat3(Model) * N);
        float rim = 1. - abs(dot(Nw, V));
        float dens = n + 0.14 * smoothstep(0.2, 0.9, rim) + vD * 2.;
        float th = mix(0.22, 1.08, pow(Life, 1.2));
        float aa = max(fwidth(dens), 1e-3);
        float m = smoothstep(th - aa, th + aa, dens);
        float lit = dot(Nw, normalize(BL_LIGHT)) * 0.5 + 0.5 + (n - 0.5) * 0.6;
        float band = lit > 0.62 ? 1. : lit > 0.38 ? 0.8 : 0.62;
        vec3 c = mix(Color2, Color, band) * mix(0.8, 1., smoothstep(th, th + 0.1, dens));
        float skirt = 1. - smoothstep(A.y - 0.05, A.y + 0.5, N.y + (n - 0.5) * 0.5);
        emit(c, m * (0.5 + 0.3 * rim) * (1. - smoothstep(0.8, 1., Life)) * skirt, 0.);
        return;
    }
    if (isK(K_BURST)) {
        // domo de fogo (Soren Sokatsui no alvo): casca que se rasga em linguas, faixas do branco ao azul escuro
        vec3 N = normalize(vL);
        vec3 Nw = normalize(mat3(Model) * N);
        float rim = 1. - abs(dot(Nw, V));
        vec3 q = vL * 3. + vec3(0., -Time * 1.1, 0.) + Seed;
        float w = fbm3(q * 0.8 + 7.) * 0.8;
        float fire = fbm4(q + vec3(w, 0., -w)) * 0.5 + 0.5;
        float dens = fire + 0.4 * smoothstep(0.2, 0.9, rim) + vD * 2.2;
        float th = mix(0.2, 1.08, pow(Life, 1.25));
        float aa = max(fwidth(dens), 1e-3);
        float m = smoothstep(th - aa, th + aa, dens);
        float heat = clamp(0.95 - Life * 1.3 + (fire - 0.5) * 1.2 + 0.25 * (1. - rim), 0., 1.);
        vec3 c = heat > 0.82 ? vec3(0.93, 0.98, 1.) : heat > 0.55 ? Color : heat > 0.3 ? Color * 0.62 : Color2;
        emit(c * (0.9 + 0.5 * heat), m * mix(0.45, 1., pow(rim, 1.1)) * (1. - smoothstep(0.55, 0.95, Life)), heat);
        return;
    }
    if (isK(K_BLIZ)) {
        // avalanche: veu branco riscado de linhas de velocidade, frente clara, rasga de tras pra frente
        vec3 N = facetN(V);
        float y = vQ.y;
        float str = gn(vec3(vQ.x * 7., y * A.x * 0.25 - Time * 9., Seed)) * 0.5 + 0.5;
        float n = fbm3(vec3(vQ.x * 2., y * A.x * 0.3 - Time * 3., Seed + 2.)) * 0.5 + 0.5;
        float dens = n * 0.8 + str * 0.45 + smoothstep(0.7, 1., y) * 0.35 + vD * 0.6;
        float th = mix(0.52, 1.2, 1. - smoothstep(B.x - 0.12, B.x + 0.08, y));
        float aa = max(fwidth(dens), 1e-3);
        float m = smoothstep(th - aa, th + aa, dens);
        float lit = dot(N, normalize(BL_LIGHT)) * 0.5 + 0.5 + (n - 0.5) * 0.4;
        float band = lit > 0.6 ? 1. : lit > 0.36 ? 0.86 : 0.7;
        vec3 c = mix(vec3(0.62, 0.76, 0.92), vec3(0.97, 0.99, 1.), band) + smoothstep(0.8, 1., str) * 0.15;
        float rimB = 1. - abs(dot(N, V));
        emit(c, m * (0.45 + 0.4 * rimB + 0.25 * smoothstep(0.75, 1., y)), smoothstep(0.85, 1., str) * 0.3);
        return;
    }
    if (isK(K_KIDO)) {
        // barra de luz do Rikujokoro: miolo branco, corpo dourado, borda que treme
        float across = abs(vQ.x);
        float z = vQ.y * 0.5 + 0.5;
        float n = gn(vec3(z * 6. - Time * 4., across * 3., Seed)) * 0.5 + 0.5;
        float edge = 1. - smoothstep(0.75 + 0.15 * n, 1., across);
        float core = exp(-across * across * 10.);
        vec3 c = mix(Color, vec3(1.), core * 0.85);
        float a = edge * (0.55 + 0.45 * core) * smoothstep(0., 0.04, z);
        emit(c * (1. + 0.4 * core), a, 1.);
        return;
    }
    if (isK(K_FIRE)) {
        // fogo azul em faixas: borda azul escura, corpo ciano, miolo branco; some pela cauda (B.y)
        vec3 Nr = normalize(vec3(vL.x, 0., vL.z));
        float f = abs(dot(normalize(mat3(Model) * Nr), V));
        float n = vD * 0.5 + 0.5;
        float y = vQ.y;
        float tongue = fbm3(vec3(vQ.x * 2.2, y * A.x * 0.6 - Time * A.z * 1.4, Seed + 5.)) * 0.5 + 0.5;
        float heat = pow(f, 1.6) * 1.05 + tongue * 0.5 - 0.22 + n * 0.2;
        float th = 0.35 + (1. - smoothstep(B.y - 0.15, B.y + 0.05, y)) * 0.9;
        float aa = max(fwidth(heat), 1e-3);
        float m = smoothstep(th - aa, th + aa, heat);
        vec3 c = heat > 0.9 ? vec3(0.94, 0.99, 1.) : heat > 0.7 ? vec3(0.5, 0.86, 1.) : heat > 0.5 ? vec3(0.22, 0.55, 1.) : vec3(0.1, 0.22, 0.8);
        emit(c * 1.25, m * smoothstep(0., 0.04, y), 1.);
        return;
    }
    if (isK(K_HALO)) {
        vec3 Nw = normalize(mat3(Model) * normalize(vL));
        float f = abs(dot(Nw, V));
        float a = pow(f, 2.2) * A.x;
        emit(mix(Color, vec3(1.), pow(f, 6.) * 0.6), a * 0.6, 1.);
        return;
    }
    if (isK(K_FLASH)) {
        vec3 Nw = normalize(mat3(Model) * normalize(vL));
        float f = abs(dot(Nw, V));
        float n = fbm3(vL * 2.5 + Seed) * 0.5 + 0.5;
        float core = pow(f, 3.) * (0.6 + 0.8 * n);
        emit(mix(Color, vec3(1.), core) * 1.6, core * 0.9 + 0.15 * pow(1. - f, 2.), 1.);
        return;
    }
    if (isK(K_ICE)) {
        // casca de gelo no corpo, pixel a pixel (1/16 de bloco): A = (progresso, modo 0 pes / 1 ponto, altura, quebra),
        // B.xyz = de onde o gelo comeca
        vec3 local = vW - B.xyz;
        vec3 cell = floor((vW + Anchor) * 16.);
        float h = h13(cell + Seed);
        float field = A.y < 0.5 ? local.y / A.z : length(local) / A.z;
        float d = A.x * 1.12 - (field + (h - 0.5) * 0.1 + 0.05);
        if (d <= 0.) discard;
        if (A.w > 0. && h13(cell + 7.7) < A.w) discard;
        vec3 N = facetN(V);
        vec4 ic = iceLook(N, V, (cell + 0.5) / 16. * 1.5, g);
        float edge = 1. - smoothstep(0., 0.07, d);
        ic.rgb = mix(ic.rgb, vec3(1.2, 1.35, 1.45), edge);
        emit(ic.rgb, ic.a * smoothstep(0., 0.025, d) * (0.85 + 0.15 * h), max(g, edge * 0.5));
        return;
    }
    if (isK(K_TINT)) {
        // o corpo ficando branco (Bankai da Rukia): a mesma pele pintada de branco-gelo por cima, A.x = quanto
        vec3 N = facetN(V);
        float lam = dot(N, normalize(BL_LIGHT)) * 0.5 + 0.5;
        emit(vec3(0.95, 0.97, 0.99) * (0.62 + 0.38 * lam), A.x * 0.92, 0.);
        return;
    }
    // gelo solido: cristal, estaca, coluna, estilhaco, petala
    vec3 N = facetN(V);
    vec3 lp = vL * 1.4 + Seed * 3.7;
    float shatter = A.w;
    if (isK(K_PETAL)) {
        float edgeN = gn(vec3(vQ.x * 4., vD * 9., Seed)) * 0.5 + 0.5;
        if (abs(vQ.x) > 1. - 0.18 * edgeN * smoothstep(0.2, 0.9, vD)) discard;
        lp = vec3(vQ.x * A.y, vD * A.x, Seed) * 1.6;
        shatter = 0.;
    }
    if (isK(K_SHARD)) shatter = 0.;
    vec4 ic = iceLook(N, V, lp, g);
    if (isK(K_PETAL)) {
        float vein = exp(-vQ.x * vQ.x * 60.) * smoothstep(0.95, 0.1, vD);
        float rimP = smoothstep(0.6, 1., abs(vQ.x));
        ic.rgb += vec3(0.85, 0.95, 1.) * (vein * 0.6 + rimP * 0.5);
        ic.a = clamp(ic.a + vein * 0.2 + rimP * 0.2, 0., 1.);
    }
    if (isK(K_PILLAR)) {
        vec3 v = vor3(vec3(vQ.x * 0.9, vQ.y * 0.55, Seed));
        float seam = 1. - smoothstep(0., 0.05, v.y - v.x);
        float lightIn = B.y * (0.6 + 0.4 * sin(vQ.y * 0.6 - Time * 5.));
        ic.rgb += vec3(0.9, 0.97, 1.) * seam * 0.55 + vec3(0.7, 0.9, 1.) * lightIn * 0.5;
        ic.a = clamp(ic.a + seam * 0.2, 0., 1.);
        g = max(g, lightIn * 0.4 + seam * 0.15);
    }
    if (isK(K_CRYSTAL) || isK(K_STAKE)) {
        float tipW = smoothstep(0.75, 1., vD);
        ic.rgb = mix(ic.rgb, vec3(0.95, 0.99, 1.), tipW * 0.45);
    }
    if (shatter > 0.) {
        // rachando antes de estourar: rachaduras brancas que se espalham e o gelo que perde o corpo
        vec3 cv = vor3(lp * 1.3 + 11.);
        float crack = 1. - smoothstep(0., 0.02 + 0.08 * shatter, cv.y - cv.x);
        ic.rgb = mix(ic.rgb, vec3(1.25, 1.35, 1.4), crack * smoothstep(0., 0.3, shatter));
        g = max(g, crack * shatter);
        ic.a *= 1. - smoothstep(0.7, 1., shatter) * step(cv.z, smoothstep(0.7, 1., shatter) * 1.1);
    }
    emit(ic.rgb, ic.a, g);
}
