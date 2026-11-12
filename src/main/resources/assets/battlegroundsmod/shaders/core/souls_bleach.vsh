#version 150
// GERADO por forja/bleach/mod-shaders.sh: bleach_common.glsl + bleach_v.glsl, o mesmo texto da previa (nao editar aqui)
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
#define BL_POS Position
#define BL_VP (ProjMat * ModelViewMat)
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
// ===== Bleach: vertice (mesmo texto na previa e no jogo). Antes de incluir: bleach_common.glsl, BL_POS (atributo de
// posicao da malha unitaria) e BL_VP (projecao * vista). A malha chega unitaria (esfera, tubo y 0..1, grade x/z -1..1,
// cristal de 6 lados) e e entortada aqui; Model poe no lugar.
out vec3 vL;    // posicao na malha unitaria, ja deformada (padroes presos ao efeito)
out vec3 vW;    // posicao depois do Model (mesmo espaco de BL_CAM)
out vec2 vQ;    // coordenadas proprias de cada tipo
out float vD;   // deslocamento / altura relativa

const float BL_TAU = 6.2831853;

void main() {
    vec3 p = BL_POS;
    vec3 P = p;
    vQ = vec2(0.);
    vD = 0.;
    if (isK(K_FROST) || isK(K_KIDO) || isK(K_PETAL)) {
        vQ = p.xz;
        if (isK(K_KIDO)) {
            // barra de luz achatada: x atravessa (A.y de largura), z ao longo (A.x de comprimento), ponta em cunha
            float z = p.z * 0.5 + 0.5;
            float w = A.y * (1. - 0.55 * smoothstep(0.72, 1., z));
            float bend = (h13(vec3(Seed, 3., 1.)) - 0.5) * 0.12 * A.x;
            P = vec3(p.x * w, bend * z * z, z * A.x);
        } else if (isK(K_PETAL)) {
            // petala de gelo: base na origem, cresce em +z (A.x), largura A.y, concha A.z, ponta curvada pra fora (B.x)
            float z = p.z * 0.5 + 0.5;
            float sw = 0.9 + 0.25 * h13(vec3(Seed, 7., 2.));
            float wz = pow(sin(3.14159 * pow(z, 0.75)), 0.85) * sw;
            float x = p.x * A.y * wz;
            float cup = A.z * (p.x * p.x) * A.y * wz;
            float curl = B.x * z * z * A.x;
            float wav = gn(vec3(p.x * 2., z * 3., Seed)) * 0.04 * A.x;
            P = vec3(x, cup + curl + wav, z * A.x);
            vD = z;
        } else {
            P = vec3(p.x, 0., p.z);
        }
    } else if (isK(K_CRYSTAL) || isK(K_STAKE)) {
        // cristal de gelo hexagonal: cada vertice com o proprio desvio (nenhum cristal igual ao outro)
        float a = atan(p.x, p.z);
        float side = mod(floor(a / (BL_TAU / 6.) + 0.5) + 6., 6.);
        float ring = floor(p.y * 5. + 0.5);
        vec3 hv = h33(vec3(side, ring, Seed * 17.31));
        float y = p.y;
        float prof = y < 0.72 ? mix(1., 0.8, y / 0.72) : (1. - (y - 0.72) / 0.28) * 0.8;
        float r = A.y * prof * (0.8 + 0.22 * hv.x);
        float ang = a + hv.y * 0.22;
        float g = A.z;                                    // crescendo: sai do chao (ou da lamina) pela ponta
        float L = A.x * g;
        vec3 tip = vec3(h33(vec3(Seed, 9., 1.)).xy * 0.18 * A.y, 0.).xzy;
        P = vec3(sin(ang) * r * mix(0.45, 1., g), y * L, cos(ang) * r * mix(0.45, 1., g));
        P.xz += B.yz * y * L + tip.xz * smoothstep(0.7, 1., y);
        P.y += hv.z * 0.05 * A.x * step(0.5, y) * step(y, 0.95);
        vD = y;
    } else if (isK(K_PILLAR)) {
        // coluna / estatua de gelo: chapas planas (celulas de voronoi) em volta, topo fechado e irregular
        float a = atan(p.x, p.z);
        float H = A.y * A.z;
        vec3 cyl = vec3(sin(a) * A.x, p.y * A.y, cos(a) * A.x);
        vec3 v = vor3(vec3(a * A.x * 0.9, p.y * A.y * 0.55, Seed));
        float plate = (v.z - 0.5) * 0.16 + (v.x - 0.4) * 0.1;
        float top = smoothstep(0.86, 1., p.y);
        float r = A.x * (1. + plate) * mix(1., 0.06 + 0.25 * (h13(vec3(floor(a * 3.), Seed, 4.)) ), top);
        float yy = p.y * H * (1. + top * 0.12 * gn(vec3(a * 2.5, Seed, 1.)));
        P = vec3(sin(a) * r, yy, cos(a) * r);
        vD = p.y;
        vQ = vec2(a * A.x, p.y * A.y);
    } else if (isK(K_LIGHT)) {
        float a = atan(p.x, p.z);
        float r = A.x * (1. + 0.08 * gn(vec3(a * 2., p.y * 4. - Time * 3., Seed)));
        P = vec3(sin(a) * r, p.y * A.y, cos(a) * r);
        vQ = vec2(a, p.y);
    } else if (isK(K_BLIZ)) {
        // avalanche branca: cone que corre pra frente (eixo y do tubo = direcao), boca larga e frente que rola
        float a = atan(p.x, p.z);
        float y = p.y;
        float reach = A.w;
        float Y = y * A.x * reach;
        float n = fbm3(vec3(a * 1.6, Y * 0.35 - Time * 1.5, Seed));
        float front = smoothstep(0.78, 1., y);
        float r = mix(A.y, A.z, pow(y, 0.8)) * (1. + 0.3 * n + 0.35 * front * (0.5 + 0.5 * gn(vec3(a * 3., Time * 2., Seed + 4.))));
        r *= mix(1., 0.15, smoothstep(0.93, 1., y));
        P = vec3(sin(a) * r, Y + front * r * 0.35, cos(a) * r * (B.y > 0. ? B.y : 1.));
        vD = n;
        vQ = vec2(a, y);
    } else if (isK(K_FIRE)) {
        // Soren Sokatsui: um tubo de fogo azul torcido; B.x = fase do par (os dois se enroscam)
        float a = atan(p.x, p.z);
        float Y = p.y * A.x;
        float n = fbm3(vec3(a * 1.3, Y * 0.5 - Time * A.z, Seed));
        float tw = Y * 0.55 + B.x * 3.14159 + Time * 4.;
        vec2 c = vec2(cos(tw), sin(tw)) * A.y * 0.55 * smoothstep(0., 2., Y);
        float r = A.y * (0.75 + 0.45 * n) * mix(0.35, 1., smoothstep(0., 1.5, Y)) * mix(1., 1.35, smoothstep(0.85, 1., p.y));
        P = vec3(c.x + sin(a) * r, Y, c.y + cos(a) * r);
        vD = n;
        vQ = vec2(a, p.y);
    } else if (isK(K_SWARM)) {
        // Senbonzakura: mil petalas numa malha so. Position = (id da petala, canto 0..3, 0). Cada petala com tamanho,
        // formato, giro e rota proprios (sorteados pelo id). B.w = modo: 0 rio que segue a mira, 1 esfera (Gokei),
        // 2 tempestade em volta do usuario, 3 estouro pra fora.
        float id = p.x, corner = p.y;
        vec3 hv = h33(vec3(id, 1.7, Seed)) * 0.5 + 0.5, hw = h33(vec3(id, 5.3, Seed)) * 0.5 + 0.5;
        vec2 cq = corner < 0.5 ? vec2(-1., -1.) : corner < 1.5 ? vec2(1., -1.) : corner < 2.5 ? vec2(1., 1.) : vec2(-1., 1.);
        float sz = 0.06 + 0.07 * hv.x + 0.04 * hv.y * hv.y;
        vec3 q = vec3(cq.x * sz * (0.55 + 0.3 * hv.z), cq.y * sz, 0.);
        vec3 ax = normalize(h33(vec3(id, 9.1, Seed)) + 1e-3);
        q = rotAxis(q, ax, Time * (3. + 7. * hw.x) + hw.y * 6.283);
        float mode = B.w;
        vec3 c = vec3(0.);
        float vis = step(id / 1500., A.w);
        if (mode < 0.5) {
            float u = fract(hv.y + Time * A.z / max(A.x, 1.));
            vis *= step(u, 1. - B.z);
            float L = A.x;
            float th = hw.z * 6.283 + Time * (2. + 3. * hv.x) + u * 7.;
            float r = A.y * (0.25 + 0.75 * sqrt(hw.x)) * (0.35 + 0.65 * sin(3.14159 * min(u * 1.25, 1.)));
            c = vec3(B.x * sin(3.14159 * u) * L * 0.18 + cos(th) * r, u * L, B.y * sin(3.14159 * u) * L * 0.18 + sin(th) * r);
        } else if (mode < 1.5) {
            vec3 d = normalize(h33(vec3(id, 3.3, Seed)) + 1e-3);
            d = rotAxis(d, vec3(0., 1., 0.), Time * A.z * (0.5 + hv.y));
            float R = A.x * (1. + (hv.z - 0.5) * A.y) * mix(1., 0.12, B.z);
            c = d * R;
        } else if (mode < 2.5) {
            float r = A.x * (0.4 + 0.6 * hv.x);
            float an = hv.z * 6.283 + Time * A.z * (0.6 + 0.8 * hw.z) / (r * 0.35 + 0.6);
            c = vec3(cos(an) * r, A.y * hv.y + sin(an * 2. + hv.x * 6.) * 0.35, sin(an) * r);
        } else {
            vec3 d = normalize(h33(vec3(id, 3.3, Seed)) + vec3(0., 0.35, 0.));
            float e = 1. - pow(1. - clamp(Life, 0., 1.), 3.);
            c = d * A.x * (0.25 + 0.75 * hv.x) * e;
            c.y -= Life * Life * 1.2;
        }
        P = c + q * vis;
        vQ = cq;
        vD = hv.x + id * 0.0001;
    } else if (isK(K_BLADES)) {
        // laminas gigantes do Bankai numa malha so. Position = (id, u -1..1 atravessando, v 0..1 ao longo). Cada lamina
        // com comprimento, largura, inclinacao e atraso proprios. B.w = modo: 0 Kageyoshi (2 voltas sobem do chao),
        // 1 Senkei (3 voltas em volta do alvo; A.z fecha: voam pro centro).
        float id = p.x, u = p.y, v = p.z;
        vec3 hv = h33(vec3(id, 2.9, Seed)) * 0.5 + 0.5;
        float S = 3.4 + 2.4 * hv.x, W = 0.62 + 0.3 * hv.y;
        float hw = W * 0.5 * (1. - 0.12 * v);
        float tip = smoothstep(0.84, 1., v);
        float x = u * hw * (1. - tip) + hw * 0.85 * tip;
        float z = 0.07 * W * (1. - abs(u - 0.2)) * (1. - tip);
        vec3 bl = vec3(x - 0.035 * S * v * v, v * S, z);
        float mode = B.w, n, k, R, del;
        if (mode < 0.5) { n = id < 26. ? 26. : 34.; k = id < 26. ? id : id - 26.; R = id < 26. ? 3.6 : 5.3; }
        else { n = id < 36. ? 36. : id < 80. ? 44. : 52.; k = id < 36. ? id : id < 80. ? id - 36. : id - 80.; R = id < 36. ? 5. : id < 80. ? 6.3 : 7.6; bl *= 0.8; S *= 0.8; }
        float vis = mode < 0.5 ? step(id, 59.5) : step(id, 131.5);
        float an = (k + (hv.z - 0.5) * 0.4) / n * 6.283 + (mode > 0.5 ? id * 0.0 + (id < 36. ? 0. : id < 80. ? 0.07 : 0.03) : (id < 26. ? 0. : 0.09));
        vec3 rad = vec3(cos(an), 0., sin(an));
        del = hv.z * 0.35;
        float g = clamp((A.x - del) / 0.4, 0., 1.);
        g = 1. - pow(1. - g, 3.);
        // a face larga olha pro centro, inclinada pra fora
        vec3 tng = vec3(-sin(an), 0., cos(an));
        float lean = 0.06 + 0.1 * hv.y;
        vec3 up = normalize(vec3(0., 1., 0.) + rad * lean);
        vec3 nrm = normalize(cross(tng, up));
        vec3 base = rad * R;
        vec3 wp = base + tng * bl.x + up * bl.y + nrm * bl.z;
        if (mode < 0.5) wp.y -= S * (1. - g);
        else wp = base + (wp - base) * (0.2 + 0.8 * g);
        if (mode > 0.5 && A.z > 0.) {
            float cg = clamp((A.z - hv.y * 0.3) / 0.45, 0., 1.);
            cg = cg * cg;
            vec3 dirIn = normalize(vec3(0., 1.1, 0.) - (base + up * S * 0.5));
            vec3 mid = mix(base + up * S * 0.45, vec3(0., 1.1, 0.) - dirIn * S * 0.45, cg);
            vec3 ax2 = normalize(cross(up, dirIn) + 1e-4);
            float ang = acos(clamp(dot(up, dirIn), -1., 1.)) * smoothstep(0., 0.35, cg);
            vec3 rel = wp - (base + up * S * 0.45);
            wp = mid + rotAxis(rel, ax2, ang);
        }
        P = wp * vis;
        vQ = vec2(u, v);
        vD = hv.x;
    } else if (isK(K_SLASH) || isK(K_WALL) || isK(K_RIPPLE) || isK(K_WING)) {
        vQ = p.xz;
        if (isK(K_WING)) {
            // asa de luz (Hakuteiken): x = envergadura 0..1 pra fora (B.x lado), z = corda
            float sx = p.x * 0.5 + 0.5, sz = p.z * 0.5 + 0.5;
            float span = A.x * A.z, chord = A.y * (1. - 0.55 * sx) * (0.35 + 0.65 * A.z);
            float flap = sin(Time * 2.2 + B.y) * 0.18 * A.w;
            P = vec3(B.x * sx * span, (sz - 0.35) * chord + sx * sx * span * 0.35 + flap * sx * span, -sx * sx * 0.4 * span * (0.3 + flap));
            vQ = vec2(sx, sz);
        } else {
            P = vec3(p.x, 0., p.z);
        }
    } else if (isK(K_BOLT)) {
        // Byakurai: um raio fino com o caminho quebrado (o desenho troca 24 vezes por segundo)
        float a = atan(p.x, p.z);
        float Y = p.y * A.x;
        float fr = floor(Time * 24.);
        vec2 off = vec2(gn(vec3(Y * 0.9, fr, Seed)), gn(vec3(Y * 0.9, fr + 7., Seed))) * 0.22 * sin(3.14159 * p.y);
        off += vec2(gn(vec3(Y * 4., fr, Seed + 3.)), gn(vec3(Y * 4., fr + 5., Seed + 3.))) * 0.05;
        float r = A.y * (0.7 + 0.3 * gn(vec3(Y * 3., fr, Seed + 9.)));
        P = vec3(off.x + sin(a) * r, Y, off.y + cos(a) * r);
        vQ = vec2(a, p.y);
    } else if (isK(K_SHARD)) {
        // estilhaco: esfera de poucos vertices, cada vertice puxado pelo proprio sorteio, achatado num eixo sorteado
        vec3 n = normalize(p + 1e-5);
        vec3 hv = h33(floor(p * 64. + 0.5) + Seed * 31.7);
        vec3 ax = normalize(h33(vec3(Seed, 1., 5.)) + 1e-3);
        P = n * (0.55 + 0.5 * (hv.x * 0.5 + 0.5));
        P -= ax * dot(P, ax) * (0.55 + 0.3 * h13(vec3(Seed, 2., 2.)));
        P *= A.x;
        P.y *= 1. + 0.8 * h13(vec3(Seed, 4., 4.));
    } else if (isK(K_MIST) || isK(K_BURST)) {
        vec3 n = normalize(p);
        float d = (fbm3(n * 2.4 + vec3(0., -Time * 0.35, 0.) + Seed) * 0.18 + gn(n * 6.3 + Seed * 2. + vec3(Time * 0.25)) * 0.06) * (0.6 + Life);
        P = n * (1. + d);
        P.y *= mix(1., 0.4, A.x);
        vD = d;
    } else if (isK(K_HALO) || isK(K_FLASH)) {
        P = normalize(p);
    }
    vL = P;
    vec4 w = Model * vec4(P, 1.);
    vW = w.xyz;
    gl_Position = BL_VP * w;
}
