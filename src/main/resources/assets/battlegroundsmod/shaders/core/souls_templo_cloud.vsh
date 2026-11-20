#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The Templos Antigos' sea of clouds (client/fx/TemploAirFx), the approved preview's cloud (forja/jujutsu-templo-ambiente
// amb.js, cloudMat): a flat grid that follows the camera (snapped to its own spacing, so it doesn't swim), lifted here by
// the cloud's thickness at that spot of the WORLD: the top waves and drifts slowly with the wind. Position is relative
// to the grid's origin.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Origin;
uniform vec3 OriginW;
uniform float Time;
uniform float CloudY;
uniform vec2 Wind;

out float vTh;
out vec3 vN;
out vec3 vRel;

float h2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), f.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) { return n2(p) * 0.55 + n2(p * 2.1 + 3.7) * 0.3 + n2(p * 4.3 + 9.1) * 0.15; }
float thick(vec2 xz) {
    vec2 q = xz * 0.022 - Wind * Time * 0.012;
    float b = fbm(q);
    float r = fbm(q * 2.6 + vec2(Time * 0.01, 0.0));
    return smoothstep(0.32, 0.72, b) * (0.7 + 0.5 * r);
}

void main() {
    vec2 w = Position.xz + OriginW.xz;
    float t = thick(w), tx = thick(w + vec2(2.0, 0.0)), tz = thick(w + vec2(0.0, 2.0));
    vec3 rel = vec3(Position.x + Origin.x, CloudY + t * 5.5 - (OriginW.y - Origin.y), Position.z + Origin.z);
    vN = normalize(vec3(-(tx - t) * 3.5, 1.0, -(tz - t) * 3.5));
    vTh = t;
    vRel = rel;
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
}
