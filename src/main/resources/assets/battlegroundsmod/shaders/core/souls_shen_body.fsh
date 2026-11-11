#version 150

// Generated from forja/shenlong/glsl.js (the approved Shenlong preview) by port/gen.js: same code.
uniform vec3 CamL;
out vec4 fragColor;

float h21(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
vec2 h22(vec2 p){ return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453); }
float h31(vec3 p){ return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n3(vec3 x){ vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(h31(i), h31(i + vec3(1,0,0)), f.x), mix(h31(i + vec3(0,1,0)), h31(i + vec3(1,1,0)), f.x), f.y),
             mix(mix(h31(i + vec3(0,0,1)), h31(i + vec3(1,0,1)), f.x), mix(h31(i + vec3(0,1,1)), h31(i + vec3(1,1,1)), f.x), f.y), f.z); }
float fbm3(vec3 p){ float a = 0.5, s = 0.0; for (int i = 0; i < 4; i++){ s += a * n3(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
// voronoi 2D: x = distância ao centro, y = borda (f2-f1), z = id da célula
vec3 vor(vec2 p){ vec2 i = floor(p), f = fract(p); float d1 = 8.0, d2 = 8.0; vec2 id = vec2(0.0);
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++){ vec2 g = vec2(float(x), float(y));
    vec2 o = g + 0.15 + 0.7 * h22(i + g) - f; float d = dot(o, o);
    if (d < d1){ d2 = d1; d1 = d; id = i + g; } else if (d < d2) d2 = d; }
  return vec3(sqrt(d1), sqrt(d2) - sqrt(d1), h21(id)); }
float band3(float x, float a, float b){ return x > b ? 1.0 : (x > a ? 0.66 : 0.4); }

uniform vec3 uSun; uniform vec3 uSunCol; uniform vec3 uAmb; uniform vec3 uBall; uniform float uBallPow;
uniform float uFlash; uniform float uEmerge; uniform float uDissolve; uniform float uArcMax; uniform float uTime;
uniform vec3 uFog; uniform float uFogK; uniform float uEye;
in vec3 vN; in vec3 vW; in vec3 vP; in vec4 vR; in float vMirror;
const vec3 GREEN_D = vec3(0.09, 0.30, 0.13);
const vec3 GREEN = vec3(0.18, 0.52, 0.22);
const vec3 GREEN_L = vec3(0.36, 0.70, 0.30);
const vec3 CREAM = vec3(0.95, 0.86, 0.55);
const vec3 CREAM_D = vec3(0.70, 0.56, 0.28);
void main(){
  float reg = floor(vR.x + 0.5), R = vR.y, arc = vR.z;
  // dissolve: some da cabeça pra cauda, a borda vira luz dourada
  float cutA = mix(uArcMax + 6.0, -8.0, uDissolve) + (n3(vW * 0.7) - 0.5) * 5.0;
  if (uDissolve > 0.0 && arc > cutA) discard;
  float edge = uDissolve > 0.0 ? 1.0 - smoothstep(0.0, 5.0, cutA - arc) : 0.0;
  vec3 n = normalize(vN); if (!gl_FrontFacing) n = -n; n *= vMirror;
  vec3 v = normalize(CamL - vW);
  vec3 alb; float emis = 0.0; vec3 ecol = vec3(0.0);
  if (reg < 0.5) {
    // corpo: escamas nas costas e nos lados, placas na barriga (tamanho segue o raio: nunca a mesma escama)
    float af = vP.z, belly = 1.0 - smoothstep(0.13, 0.17, abs(af - 0.5));
    float cs = 0.36 * R;
    vec3 c = vor(vec2(vP.x / cs, vP.y / cs + 0.5 * floor(vP.x / cs)));
    float side = smoothstep(0.08, 0.36, abs(af - 0.5)); // 0 na barriga ... 1 no dorso
    vec3 scale = mix(GREEN_L, GREEN, smoothstep(0.1, 0.42, abs(af - 0.5)));
    scale = mix(scale, GREEN_D, smoothstep(0.42, 0.5, abs(af - 0.5)));
    scale *= 0.86 + 0.28 * c.z;
    scale = mix(scale * 0.45, scale, smoothstep(0.03, 0.12, c.y));
    scale = mix(scale, scale * 1.15, smoothstep(0.5, 0.0, c.x) * 0.4);
    float pl = 0.55 * R, k = vP.x / pl + 0.08 * sin(vP.x * 0.37);
    float pf = fract(k), ph = h21(vec2(floor(k), 3.0));
    vec3 plate = mix(CREAM, CREAM * 1.06, ph) * (0.82 + 0.18 * smoothstep(0.0, 0.5, pf) * smoothstep(1.0, 0.6, pf));
    plate = mix(CREAM_D, plate, smoothstep(0.0, 0.07, pf));
    plate = mix(plate, CREAM_D, smoothstep(0.1, 0.17, abs(af - 0.5)) * 0.6);
    alb = mix(scale, plate, belly);
  } else if (reg < 1.5) {
    vec3 c = vor(vec2(vP.z * 4.2 + vP.y * 1.3, vP.y * 4.2 + vP.x * 2.0));
    alb = mix(GREEN, GREEN_L, smoothstep(0.35, 0.75, n3(vP * 1.7))) * (0.88 + 0.22 * c.z);
    alb = mix(alb * 0.6, alb, smoothstep(0.02, 0.1, c.y));
    alb = mix(alb, CREAM, smoothstep(-0.25, -0.6, n.y) * 0.0);
  } else if (reg < 2.5) { alb = CREAM;
  } else if (reg < 3.5) {
    // chifre de galho marrom em gomos (bambu): anel escuro entre os gomos, gomo mais claro no meio
    float t = dot(vP, vec3(0.12 * sign(vP.x), 0.78, -0.62));
    float gw = 0.36 + 0.06 * sin(t * 1.3);
    float ring = fract(t / gw + 0.15 * n3(vP * 2.0));
    vec3 hb = mix(vec3(0.42, 0.25, 0.13), vec3(0.66, 0.45, 0.25), smoothstep(0.0, 0.45, ring) * smoothstep(1.0, 0.6, ring));
    hb = mix(vec3(0.22, 0.12, 0.06), hb, smoothstep(0.0, 0.09, ring) * smoothstep(1.0, 0.93, ring));
    alb = mix(hb, hb * 1.12, smoothstep(1.5, 5.0, vP.y));
  } else if (reg < 4.5) {
    // olho: vermelho em brasa, miolo amarelo, pupila fina
    float f = max(dot(n, v), 0.0);
    alb = vec3(0.0); emis = 1.0;
    ecol = mix(vec3(0.75, 0.05, 0.04), vec3(1.0, 0.25, 0.08), smoothstep(0.2, 0.8, f));
    ecol = mix(ecol, vec3(1.0, 0.85, 0.35), smoothstep(0.85, 0.97, f) * uEye);
    ecol *= 0.35 + 0.9 * uEye;
  } else if (reg < 5.5) { alb = vec3(0.97, 0.95, 0.88);
  } else if (reg < 6.5) {
    alb = mix(GREEN_D, mix(GREEN, CREAM, 0.25), smoothstep(0.2, 1.0, vP.z));
  } else if (reg < 7.5) { alb = mix(vec3(0.92, 0.88, 0.74), vec3(0.6, 0.55, 0.42), smoothstep(0.0, 1.0, vP.z));
  } else if (reg < 8.5) { alb = mix(vec3(0.55, 0.12, 0.16), vec3(0.92, 0.36, 0.42), smoothstep(-0.6, 0.2, vP.y));
  } else if (reg < 9.5) { alb = vec3(0.04, 0.09, 0.05);
  } else {
    // crina: verde-amarelado claro, raiz mais verde, pontas quase creme; fios riscados no sentido da mecha
    float far = length(vP - vec3(sign(vP.x) * 1.15, 0.6, 0.1));
    float tip = smoothstep(0.8, 3.6, far);
    alb = mix(vec3(0.56, 0.8, 0.3), vec3(0.9, 0.97, 0.62), tip);
    float st = n3(vec3(vP.x * 7.0, vP.y * 7.0 + vP.z * 2.0, vP.z * 1.5));
    alb *= 0.84 + 0.3 * smoothstep(0.3, 0.7, st);
  }

  // luz: 3 faixas do sol/lua, luz dourada das esferas por baixo, contorno frio da tempestade, flash do raio
  float key = band3(dot(n, uSun), -0.05, 0.4);
  vec3 col = alb * (uAmb + uSunCol * key);
  vec3 tb = uBall - vW; float db = length(tb);
  float gb = max(dot(n, tb / db), 0.0) * uBallPow / (1.0 + db * db / 600.0);
  col += alb * vec3(1.0, 0.72, 0.28) * (gb > 0.35 ? 1.0 : (gb > 0.08 ? 0.5 : 0.0)) * min(uBallPow, 1.5);
  float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
  col += vec3(0.35, 0.55, 0.65) * step(0.42, rim) * 0.35 * (0.4 + uFlash);
  col += alb * vec3(0.75, 0.82, 1.0) * uFlash * (0.5 + 0.5 * key);
  col = mix(col, ecol, emis);
  // nascendo da luz: perto das esferas o corpo ainda é luz
  float born = uEmerge * (1.0 - smoothstep(0.0, 16.0, arc));
  col = mix(col, vec3(1.0, 0.8, 0.38), smoothstep(0.05, 0.45, born));
  col = mix(col, vec3(1.0, 0.96, 0.8), smoothstep(0.55, 0.9, born));
  col = mix(col, vec3(1.0, 0.82, 0.4), smoothstep(0.0, 0.5, edge));
  col = mix(col, vec3(1.0, 0.98, 0.85), smoothstep(0.7, 1.0, edge));
  float fd = length(CamL - vW);
  col = mix(col, uFog, (1.0 - exp(-fd * uFogK)) * (1.0 - emis * 0.7));
  fragColor = vec4(col, 1.0);
}
