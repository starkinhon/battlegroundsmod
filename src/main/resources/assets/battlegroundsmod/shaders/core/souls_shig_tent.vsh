#version 150

// Shigaraki's V, the Tentáculos de Mãos (client/fx/shig/TentacleFx), the preview's TENT_VS line for line: every hand
// is anchored on the spine (Anc: where along it 0..1, the hand's seed or -1 for the dark core, where around it), sprouts
// with an ease-out-back as the growth front passes and moves on its own rhythm. Spine points are camera-relative.
#define KK 40
in vec4 aAnc;
in vec3 aRel;
in vec3 aNrm;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 uP[KK];
uniform vec3 uN[KK];
uniform vec3 uB[KK];
uniform float L;
uniform float Grow;
uniform float Time;
uniform float Out;
uniform float Wnd;
uniform vec3 Rad;

out vec3 vN;
out float vAO;
out float vSeed;
out float vCore;
out vec3 vW;

float radAt(float d) { float r = mix(Rad.x, Rad.y, smoothstep(1.5, 10.5, d)); return r * (1. + (Rad.z - 1.) * pow(clamp(d / L, 0., 1.), 1.3)); }

void main() {
    float au = aAnc.x, sd = aAnc.y;
    float g = clamp((Grow - au) * L / Wnd, 0., 1.);
    float s = g < 1. ? 1. + 2.7 * pow(g - 1., 3.) + 1.7 * pow(g - 1., 2.) : 1.;
    s = max(s, 0.);
    vec3 rel = aRel, nr = aNrm;
    if (sd >= 0.) {
        float a1 = 0.16 * sin(Time * (1.7 + sd * 1.9) + sd * 41.) + 0.05 * sin(Time * 7.3 + sd * 90.);
        float a2 = 0.14 * sin(Time * (1.3 + sd * 1.4) + sd * 67.);
        float c = cos(a1), si = sin(a1);
        rel.yz = mat2(c, si, -si, c) * rel.yz;
        nr.yz = mat2(c, si, -si, c) * nr.yz;
        c = cos(a2);
        si = sin(a2);
        rel.xy = mat2(c, si, -si, c) * rel.xy;
        nr.xy = mat2(c, si, -si, c) * nr.xy;
    }
    vec3 F = vec3(au * L, aAnc.z * s, aAnc.w * s) + rel * s;
    float x = clamp(F.x / L, 0., 1.) * float(KK - 1);
    float fi = floor(min(x, float(KK - 2)));
    int i = int(fi);
    float f = x - fi;
    vec3 P0 = uP[i], P1 = uP[i + 1];
    vec3 T = normalize(P1 - P0);
    vec3 P = mix(P0, P1, f) + T * (F.x - clamp(F.x, 0., L));
    vec3 N = normalize(mix(uN[i], uN[i + 1], f)), B = normalize(mix(uB[i], uB[i + 1], f));
    vec3 pos = P + N * F.y + B * F.z;
    vec3 n = normalize(T * nr.x + N * nr.y + B * nr.z);
    pos += n * Out * s;
    vN = n;
    vSeed = sd;
    vCore = sd < 0. ? 1. : 0.;
    vAO = smoothstep(0.3, 1.05, length(F.yz) / max(radAt(F.x), .1));
    vW = pos;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.);
}
