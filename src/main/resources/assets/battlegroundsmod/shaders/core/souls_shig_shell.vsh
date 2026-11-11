#version 150

// Shigaraki's B (client/fx/shig/AbsorbFx): the shells of power (auras, the fire ball and its burst, the lightning's
// flash), the preview's SHELL_VS: pushed out along the normal by 3D noise rising with time. Mode 2: a plain lit solid
// (the ice), its colour in Color.
float h31(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float vn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3. - 2. * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z) * 2. - 1.;
}
float fbm(vec3 p) { return vn(p) * .55 + vn(p * 2.07 + 11.3) * .3 + vn(p * 4.13 + 5.1) * .15; }
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float T;
uniform float Amp;
uniform float Rise;
uniform vec3 Origin;

out vec2 vUv;
out vec3 vW;
out vec3 vN;
out vec4 vC;

void main() {
    vec3 n = normalize(Normal);
    vec3 w = Position;
    float f = fbm((w + Origin) * 1.6 - vec3(0., T * Rise, 0.) + T * 0.4);
    w += n * f * Amp + vec3(0., max(f, 0.) * Amp * 1.4 * Rise * 0.4, 0.);
    vUv = UV0;
    vW = w;
    vN = n;
    vC = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(w, 1.0);
}
