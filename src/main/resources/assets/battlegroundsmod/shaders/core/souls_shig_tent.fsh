#version 150

// The hands' look (the preview's TENT_FS): two-tone cel in the skin's dry colours, dark lines of wrinkles in the shade,
// the gaps between hands black; a dark outline (Out > 0, back faces pushed out); dithered away near the lens.
float h31(vec3 p) { p = fract(p * vec3(.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); return fract((p.x + p.y) * p.z); }
float vn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3. - 2. * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z) * 2. - 1.;
}
float fbm(vec3 p) { return vn(p) * .55 + vn(p * 2.07 + 11.3) * .3 + vn(p * 4.13 + 5.1) * .15; }

in vec3 vN;
in float vAO;
in float vSeed;
in float vCore;
in vec3 vW;

uniform float Out;
uniform vec3 Origin;

out vec4 fragColor;

void main() {
    float dc = length(vW);
    float keep = smoothstep(1.2, 3.2, dc);
    vec2 q = mod(floor(gl_FragCoord.xy), 4.);
    float bay = mod(q.x * 2. + q.y * 3. + floor(q.x * .5) * 5. + floor(q.y * .5) * 7., 16.) / 16.;
    if (keep < 1. && bay + 0.03 > keep) discard;
    if (Out > 0.) { fragColor = vec4(0.11, 0.07, 0.045, 1.); return; }
    vec3 Ld = normalize(vec3(0.35, 0.85, -0.3));
    float d = dot(normalize(vN), Ld);
    float lit = smoothstep(0.02, 0.1, d);
    float tone = 0.94 + 0.12 * fract(vSeed * 13.7);
    vec3 hi = vec3(0.90, 0.82, 0.66) * tone, sh = vec3(0.64, 0.53, 0.38) * tone, deep = vec3(0.20, 0.14, 0.09);
    vec3 c = mix(sh, hi, lit);
    float w = vn((vW + Origin) * 6.5 + vSeed * 50.);
    c *= 1. - 0.3 * (1. - smoothstep(0.0, 0.05, abs(w))) * (0.4 + 0.6 * (1. - lit));
    c = mix(deep, c, smoothstep(0.05, 0.8, vAO));
    if (vCore > 0.5) c = deep * 0.75;
    fragColor = vec4(c, 1.);
}
