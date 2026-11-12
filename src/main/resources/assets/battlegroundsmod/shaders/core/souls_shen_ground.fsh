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

uniform vec3 uBall; uniform float uBallPow; uniform float uStorm; uniform float uFlash; uniform vec3 uFog; uniform float uFogK;
in vec3 vW;
void main(){
  vec2 b = floor(vW.xz); float t = h21(b);
  vec3 g = mix(vec3(0.36, 0.62, 0.24), vec3(0.42, 0.68, 0.28), t);
  vec2 f = fract(vW.xz); float line = step(f.x, 0.03) + step(f.y, 0.03);
  g *= 1.0 - 0.12 * min(line, 1.0);
  vec3 col = vec3(0.0);
  float d = length(vW.xz - uBall.xz);
  float gl = uBallPow / (1.0 + d * d / 12.0);
  col += g * vec3(1.0, 0.7, 0.25) * (gl > 0.7 ? 0.6 : (gl > 0.25 ? 0.3 : gl * 0.9)) * min(uBallPow, 1.5);
  col += g * vec3(0.7, 0.78, 1.0) * uFlash * 0.8;
  float fd = length(CamL - vW);
  col *= exp(-fd * uFogK) * smoothstep(60.0, 30.0, length(vW.xz - uBall.xz));
  fragColor = vec4(col, 1.0);
}
