#version 150

// The Colônia do Jogo do Abate (client/fx/ColoniaAirFx), line by line from the approved preview
// (forja/jujutsu-colonia-ambiente: page.html domeMat and amb.js skyGlow).
// Mode 0, the barrier: a dark see-through dome, stronger at the edge (fresnel), with veins of energy running slowly
//   over it (3D noise in world space). Drawn with SRC_ALPHA, ONE_MINUS_SRC_ALPHA.
// Mode 1, the sky behind everything, added (SRC_ALPHA, ONE): from inside the barrier the top of the sky goes violet;
//   the fires' orange low on the horizon, in patches that breathe (strong at night); the far blast's violet-white
//   glow around its direction.
uniform float Mode;
uniform float Time;
uniform float Night;
uniform float Fade;
uniform vec3 Cam;
uniform vec3 Center;
uniform float Inside;
uniform float Fire;
uniform vec3 FDir;
uniform float FAmt;

in vec3 vRel;
out vec4 fragColor;

float h(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h(i), h(i + vec3(1, 0, 0)), f.x), mix(h(i + vec3(0, 1, 0)), h(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h(i + vec3(0, 0, 1)), h(i + vec3(1, 0, 1)), f.x), mix(h(i + vec3(0, 1, 1)), h(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    if (Mode < 0.5) {
        vec3 wp = Cam + vRel;
        vec3 nw = normalize(wp - Center);
        vec3 v = normalize(-vRel);
        float fr = pow(1.0 - abs(dot(v, nw)), 2.2);
        vec3 q = wp * 0.035 + vec3(0.0, Time * 0.05, 0.0);
        float vein = 1.0 - abs(n(q) * 2.0 - 1.0);
        vein = pow(vein, 10.0);
        vec3 base = mix(vec3(0.05, 0.02, 0.09), vec3(0.02, 0.01, 0.04), Night);
        vec3 c = base + vec3(0.32, 0.12, 0.55) * (fr * 0.6 + vein * 0.5);
        fragColor = vec4(c, (0.24 + fr * 0.6 + vein * 0.18) * Fade);
        return;
    }
    vec3 vp = normalize(vRel);
    float y = vp.y;
    float hi = smoothstep(0.05, 0.7, y);
    vec3 c = vec3(0.16, 0.06, 0.24) * hi;
    float band = exp(-pow((y + 0.01) / 0.05, 2.0));
    float ang = atan(vp.z, vp.x);
    float patchy = 0.82 + 0.18 * sin(ang * 5.0 + sin(ang * 13.0 + Time * 0.2) * 1.5 + Time * 0.07);
    vec3 fc = vec3(1.0, 0.42, 0.12) * band * patchy;
    float fd = max(dot(vp, normalize(FDir)), 0.0);
    vec3 flc = vec3(0.85, 0.6, 1.0) * (pow(fd, 18.0) * 1.6 + pow(fd, 3.0) * 0.35) * FAmt;
    fragColor = vec4((c * Inside * 0.55 * hi + fc * Fire * 0.35 + flc) * Fade, 1.0);
}
