#version 150

// Generated from forja/shenlong/glsl.js (the approved Shenlong preview) by port/gen.js: same code.
uniform vec3 CamL;
out vec4 fragColor;
uniform vec3 uCol; in float vE; in float vA;
void main(){ float e = 1.0 - abs(vE); float core = smoothstep(0.55, 0.8, e);
  vec3 c = mix(uCol, vec3(1.0), core); fragColor = vec4(c * pow(e, 1.5) * vA, 1.0); }
