#version 150

// The combat flight's screen (forja db-voo-mobs/combate.html, client/fx/PreviewFx), the page's three passes in one, in
// the same order: the shockwave (SHOCK: the frame pushed out in a ring, the colors split), the speed lines (LINES: white
// lines from the edges in, redrawn 12 times a second) and the manga impact frame (IMPACT: 1 white on black, 2 negative).
uniform sampler2D DiffuseSampler;
uniform vec2 Center;
uniform float Radius;
uniform float Width;
uniform float Strength;
uniform float Aspect;
uniform float Amt;
uniform float Frame;
uniform float Res;
uniform float Mode;

in vec2 uv;
out vec4 fragColor;

float hs(float n) { return fract(sin(n * 12.9898 + 78.233) * 43758.5453); }

void main() {
    vec3 c;
    if (Strength > 0.0001) {
        vec2 d = uv - Center; d.x *= Aspect; float r = length(d); float s = (r - Radius) / Width; float prof = s * exp(-s * s) * 2.33;
        vec2 dir = d / max(r, 1e-4); dir.x /= Aspect; vec2 off = dir * prof * Strength;
        c = vec3(texture(DiffuseSampler, uv - off * 1.15).r, texture(DiffuseSampler, uv - off).g, texture(DiffuseSampler, uv - off * .85).b);
    } else {
        c = texture(DiffuseSampler, uv).rgb;
    }
    if (Amt > .001) {
        vec2 p = (uv - vec2(.5)) * 2.; float e = length(p); float ang = atan(p.y, p.x * Aspect) / 6.2831853 + .5;
        float u = ang * 170.; float k = floor(u), f = fract(u); float sd = k * 1.7 + Frame * 13.1; float line = 0.;
        if (hs(sd) < .5) {
            float inner = mix(.7, .95, hs(sd + 4.1)) + (1. - Amt) * .4; float along = clamp((e - inner) / .4, 0., 1.);
            float w = mix(.05, .28, hs(sd + 1.3)) * (.15 + .85 * along); float aa = 170. / (6.2831853 * max(e, .2) * Res * .5);
            line = (1. - smoothstep(w - aa, w + aa, abs(f - mix(.3, .7, hs(sd + 2.9))))) * smoothstep(0., .1, along);
        }
        c = mix(c, vec3(1.), line * .75 * Amt);
    }
    float l = dot(c, vec3(.299, .587, .114));
    if (Mode > 1.5) c = vec3(1.) - c; else if (Mode > .5) c = vec3(step(.62, l));
    fragColor = vec4(c, 1.);
}
