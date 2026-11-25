#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The Black Flash (client/fx/BlackFlashRenderer), the approved page's GLSL (forja/black-flash-v3) line by line.
// Layers: 0 bolt body, 1 bolt glow (the bloom), 2 core, 3 core glow (the bloom), 4 cracks in the ground. Per vertex:
//   bolt: UV0 = (distance along in blocks, -1..1 across); Color = (seed, life 0 -> 1, tip 0 -> 1, opacity)
//   core and ground: UV0 0..1 over the square; Color = (seed, life, grow, opacity)
// BfTime in seconds, drawn at 12 frames a second (anime). The seed is rounded to a whole number: the vertex color comes
// with a hair of error, and hashing a fractional number turns that into per-pixel noise (the striped ground).
// Everything comes out premultiplied.
uniform float Layer;
uniform float BfTime;

in vec2 vUv;
in vec4 vCol;

out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * vn(p); p = p * 2.03 + vec2(17.1, 9.3); a *= 0.5; } return s; }
const vec3 BF_RED = vec3(1.0, 0.07, 0.11);
const vec3 BF_HOT = vec3(1.0, 0.62, 0.66);
const vec3 BF_INK = vec3(0.03, 0.0, 0.012);

// A bolt: black inside, red rim (never thinner than ~2.5 px), its edge gnawed by the noise, dark red veins inside;
// it dies eaten from the tip to the root.
vec4 bfBolt(vec2 uv, vec4 c, float tq) {
  float seed = floor(c.r * 61.0 + 0.5), er = smoothstep(0.25, 1.0, c.g);
  float m = 1.0 - abs(uv.y) + (fbm(vec2(uv.x * 2.6 + seed, uv.y * 0.9 + tq * 0.35)) - 0.5) * 0.6;
  m -= er * (0.5 + 1.1 * c.b) * (0.45 + 1.1 * fbm(vec2(uv.x * 1.3 - seed * 1.7, seed + tq * 0.15)));
  float fw = max(fwidth(m), 1.0e-4);
  float a = smoothstep(0.0, 1.5 * fw, m);
  if (a < 0.004) discard;
  float rimW = max(0.34, 2.5 * fw) * (1.0 - 0.35 * er);
  float rim = 1.0 - smoothstep(rimW * 0.6, rimW, m);
  float hot = (1.0 - smoothstep(0.0, rimW * 0.45, m)) * (1.0 - er);
  float vein = (1.0 - smoothstep(0.0, 0.07, abs(fbm(vec2(uv.x * 1.9 + seed * 2.3, uv.y * 1.2 - tq * 0.4)) - 0.5))) * smoothstep(rimW, rimW * 2.2, m);
  vec3 col = BF_INK + vec3(0.42, 0.0, 0.04) * vein * 0.7;
  col = mix(col, BF_RED, rim);
  col = mix(col, BF_HOT, hot * 0.55);
  float al = a * c.a;
  return vec4(col * al, al);
}

// The red glow round a bolt (the bloom only): gone before the body.
vec4 bfGlow(vec2 uv, vec4 c) {
  float g = exp(-uv.y * uv.y * 5.0) * (1.0 - smoothstep(0.15, 0.8, c.g)) * c.a;
  return vec4(BF_RED * g * 0.42, g);
}

// The core: a black hole with a torn edge on the fist, red rim; implodes (grow 0) and bursts.
vec4 bfCore(vec2 uv, vec4 c, float tq) {
  vec2 p = uv * 2.0 - 1.0;
  float r = length(p), ang = atan(p.y, p.x), seed = floor(c.r * 61.0 + 0.5), er = smoothstep(0.2, 1.0, c.g);
  float edge = 0.62 + 0.3 * (fbm(vec2(ang * 1.6 + seed, tq * 0.8 + seed)) - 0.5);
  float m = edge - r - er * (0.35 + 0.6 * fbm(p * 3.0 + seed));
  float fw = max(fwidth(m), 1.0e-4);
  float a = smoothstep(0.0, 1.5 * fw, m);
  if (a < 0.004) discard;
  float rim = 1.0 - smoothstep(max(0.05, 2.5 * fw), max(0.1, 4.0 * fw), m);
  float hot = (1.0 - smoothstep(0.0, max(0.03, 1.5 * fw), m)) * (1.0 - er);
  vec3 col = mix(BF_INK, BF_RED, rim * (0.6 + 0.4 * c.b));
  col = mix(col, BF_HOT, hot * 0.5);
  float al = a * c.a;
  return vec4(col * al, al);
}

// The core's red crown (the bloom only): crooked rays that pulse at 12 fps.
vec4 bfCoreGlow(vec2 uv, vec4 c, float tq) {
  vec2 p = uv * 2.0 - 1.0;
  float r = length(p), ang = atan(p.y, p.x), seed = floor(c.r * 61.0 + 0.5);
  float rays = 0.55 + 0.45 * fbm(vec2(ang * 3.5 + seed, r * 2.0 - tq * 2.0));
  float g = exp(-max(r - 0.3, 0.0) * 4.5) * smoothstep(1.0, 0.75, r) * rays * (1.0 - smoothstep(0.1, 0.85, c.g)) * c.a;
  return vec4(BF_RED * g * 0.5, g);
}

// Cracks in the ground: cracks out of the middle (each sector its own angle, length and wobble), a broken ring and the
// scorch in the middle; black with a red glow at the edge, growing with c.b and cooling with the life.
vec4 bfGround(vec2 uv, vec4 c) {
  vec2 p = uv * 2.0 - 1.0;
  float r = length(p), ang = atan(p.y, p.x), seed = floor(c.r * 61.0 + 0.5), er = smoothstep(0.3, 1.0, c.g);
  // The pixel's size measured on the square itself (smooth). A crack thinner than a pixel is not thickened: it gets
  // fainter by as much, or far away and edge-on it turns into stripes and dots.
  float fw = max(length(fwidth(p)) * 0.7, 1.0e-4);
  float sector = 6.28318 / 11.0, crack = 0.0, glow = 0.0;
  for (int k = -1; k <= 1; k++) {
    float ci = floor(ang / sector) + float(k);
    float len = 0.45 + 0.55 * h21(vec2(ci, seed + 3.1));
    float ca = (ci + 0.25 + 0.5 * h21(vec2(ci, seed))) * sector;
    float wob = (fbm(vec2(r * 4.0 + ci * 1.7, seed)) - 0.5) * 0.35;
    float da = mod(ang - ca - wob * 0.6 + 3.14159, 6.28318) - 3.14159;
    float w = 0.035 * (1.0 - r / len) + 0.006;
    if (r < len) {
      float dist = abs(da) * r;
      crack = max(crack, (1.0 - smoothstep(w - fw, w + fw, dist)) * clamp(2.0 * w / fw, 0.0, 1.0));
      glow = max(glow, exp(-max(dist - w, 0.0) / (0.025 + fw)));
    }
  }
  float rr = 0.32 + 0.1 * (fbm(vec2(ang * 3.0, seed)) - 0.5);
  if (h21(vec2(floor(ang * 2.2), seed + 7.0)) > 0.35) {
    float w = 0.008 + 0.01 * h21(vec2(floor(ang * 6.0), seed)), dist = abs(r - rr);
    crack = max(crack, (1.0 - smoothstep(w - fw, w + fw, dist)) * clamp(2.0 * w / fw, 0.0, 1.0));
    glow = max(glow, exp(-max(dist - w, 0.0) / (0.025 + fw)));
  }
  glow *= 1.0 - crack;
  float scorch = 1.0 - smoothstep(0.1, 0.26, r + (fbm(p * 4.0 + seed) - 0.5) * 0.14);
  float mask = 1.0 - smoothstep(c.b - 0.06, c.b, r);
  float ink = max(crack, scorch * 0.85);
  vec3 col = mix(BF_RED * (1.0 - 0.6 * er), BF_INK, ink / max(ink + glow * 0.8, 1.0e-4));
  float al = clamp(ink + glow * 0.75 * (1.0 - er), 0.0, 1.0) * mask * (1.0 - smoothstep(0.55, 1.0, c.g)) * c.a;
  if (al < 0.004) discard;
  return vec4(col * al, al);
}

void main() {
  float tq = floor(BfTime * 12.0) / 12.0;
  vec4 o;
  if (Layer < 0.5) o = bfBolt(vUv, vCol, tq);
  else if (Layer < 1.5) o = bfGlow(vUv, vCol);
  else if (Layer < 2.5) o = bfCore(vUv, vCol, tq);
  else if (Layer < 3.5) o = bfCoreGlow(vUv, vCol, tq);
  else o = bfGround(vUv, vCol);
  fragColor = o;
}