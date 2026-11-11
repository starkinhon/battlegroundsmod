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

uniform vec3 uCol; uniform float uPow; uniform float uMode; uniform float uTime;
in vec3 vN; in vec3 vW; in vec2 vUv;
void main(){
  vec3 n = normalize(vN), v = normalize(CamL - vW);
  float f = abs(dot(n, v));
  float a;
  vec3 c = uCol;
  if (uMode < 0.5) { a = pow(f, 2.2); }                       // halo: forte no meio, some na borda
  else {                                                       // pilar: faixas subindo, miolo branco
    float y = vUv.y * 40.0 - uTime * 6.0;
    float st = fbm3(vec3(vUv.x * 8.0, y * 0.35, uTime * 0.5));
    a = pow(f, 1.4) * (0.55 + 0.6 * smoothstep(0.45, 0.6, st));
    a *= smoothstep(0.0, 0.04, vUv.y) * (1.0 - smoothstep(0.75, 1.0, vUv.y));
    c = mix(uCol, vec3(1.0, 0.97, 0.85), smoothstep(0.75, 0.95, f));
  }
  fragColor = vec4(c * a * uPow, 1.0);
}
