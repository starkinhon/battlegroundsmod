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

uniform float uStorm; uniform float uFlash; uniform float uTime; uniform vec3 uBolt;
in vec3 vD;
void main(){
  vec3 d = normalize(vD); float h = d.y;
  vec3 day = mix(vec3(0.70, 0.84, 0.98), vec3(0.32, 0.56, 0.92), smoothstep(0.0, 0.6, h));
  vec3 storm = mix(vec3(0.10, 0.16, 0.14), vec3(0.03, 0.05, 0.08), smoothstep(-0.05, 0.7, h));
  // nuvens: de dia soltas; na tempestade um redemoinho sobre as esferas, girando, em 3 tons
  float ang = atan(d.z, d.x), r = acos(clamp(h, -1.0, 1.0));
  float sw = ang + uStorm * (2.2 / (0.25 + r)) - uTime * 0.18 * uStorm;
  vec2 cp = vec2(cos(sw), sin(sw)) * r * 3.2;
  float cl = fbm3(vec3(cp * 1.3, uTime * 0.05)) + (1.0 - uStorm) * (fbm3(vec3(d.xz / max(h, 0.08) * 0.8, 0.0)) - 0.55);
  float cb = smoothstep(0.48, 0.52, cl), cb2 = smoothstep(0.62, 0.66, cl);
  vec3 dayC = mix(day, vec3(1.0), cb * 0.85 * smoothstep(0.02, 0.2, h));
  vec3 stC = mix(storm, vec3(0.13, 0.2, 0.2), cb); stC = mix(stC, vec3(0.2, 0.28, 0.27), cb2);
  // o olho do redemoinho brilha dourado quando o dragão nasce
  vec3 col = mix(dayC, stC, uStorm);
  float flashArea = pow(max(dot(d, normalize(uBolt)), 0.0), 6.0);
  col += vec3(0.7, 0.78, 1.0) * uFlash * (0.25 + 0.9 * flashArea) * (0.4 + 0.6 * cb);
  // in the game it lies over the game sky: only the storm, as much as there is
  col = stC + vec3(0.7, 0.78, 1.0) * uFlash * (0.25 + 0.9 * flashArea) * (0.4 + 0.6 * cb);
  fragColor = vec4(col, uStorm);
}
