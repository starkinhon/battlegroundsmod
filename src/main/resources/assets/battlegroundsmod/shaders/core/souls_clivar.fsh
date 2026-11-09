#version 150

// The Clivar's cuts over a body (client/fx/ClivarRenderer). Stark 2026-11-09: the old grid (the same lattice of lines
// every 1/9 block, lit all over at once) looked like a repeated texture. Now each cut is its own plane through the body,
// laid out by the renderer like a measure being taken: slices across at different heights and tilts, a few lengthwise,
// a few diagonal, every one at its own angle and depth. Where a plane meets the model is a line that runs across the
// body from one side, with a white-hot head, leaving a thin black slit rimmed in white; Measure 0 -> 1 runs them in one
// after another. On the snap (Sil 1) the body is a black silhouette under the cuts, then they fade (Fade).
// Planes: P0..P2 hold 4 each as columns (normal xyz in the body's frame, offset from Center); Q0..Q2 their timing
// (start and length in Measure, width in blocks, sweep angle in the plane). Lines are antialiased with fwidth and never
// thinner than about a pixel and a half, near or far.
uniform sampler2D Sampler0;
uniform float Measure;
uniform float Sil;
uniform float Fade;
uniform float Center;
uniform float Radius;
uniform mat4 P0;
uniform mat4 P1;
uniform mat4 P2;
uniform mat4 Q0;
uniform mat4 Q1;
uniform mat4 Q2;

in vec2 texCoord;
in vec3 local;
in vec3 normal;

out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(float x) { float i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f); return mix(h21(vec2(i, 3.1)), h21(vec2(i + 1.0, 3.1)), f); }

vec4 plane(int i) { return i < 4 ? P0[i] : i < 8 ? P1[i - 4] : P2[i - 8]; }
vec4 timing(int i) { return i < 4 ? Q0[i] : i < 8 ? Q1[i - 4] : Q2[i - 8]; }

void main() {
    if (texture(Sampler0, texCoord).a < 0.1) {
        discard;
    }
    vec3 p = local - vec3(0.0, Center, 0.0);
    float white = 0.0, black = 0.0, glow = 0.0;
    for (int i = 0; i < 12; i++) {
        vec4 P = plane(i), Q = timing(i);
        if (Q.y <= 0.0) {
            continue;
        }
        float prog = clamp((Measure - Q.x) / Q.y, 0.0, 1.0);
        if (prog <= 0.0) {
            continue;
        }
        vec3 n = P.xyz;
        vec3 ax = abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 u1 = normalize(cross(n, ax));
        vec3 u2 = cross(n, u1);
        vec3 s = u1 * cos(Q.w) + u2 * sin(Q.w);
        float along = dot(p, s) / Radius;
        // the head runs across fast and slows into the far side
        float front = -1.25 + 2.5 * (1.0 - pow(1.0 - prog, 2.2));
        float behind = front - along;
        float sd = dot(p, n) - P.w;
        float dist = abs(sd);
        float px = fwidth(sd);
        // the slit opens behind the head; its width breathes along the cut so it reads drawn by hand, not ruled
        float wob = 0.65 + 0.7 * vn(along * 7.0 + float(i) * 13.7);
        float open = smoothstep(0.0, 0.35, behind);
        float w = max(Q.z * wob * mix(0.45, 1.0, open), px * 0.8);
        // where the plane runs almost along a face, its line would smear over the whole face: let it go there
        float graze = 1.0 - smoothstep(0.82, 0.95, abs(dot(n, normalize(normal))));
        float on = step(0.0, behind) * graze;
        float line = (1.0 - smoothstep(w, w + px * 1.25, dist)) * on;
        float core = (1.0 - smoothstep(w * 0.3, w * 0.3 + px, dist)) * on * step(px * 3.5, w) * open;
        float hot = exp(-max(behind, 0.0) * 9.0) * (1.0 - step(0.999, prog));
        white = max(white, line);
        black = max(black, core * (1.0 - hot));
        // a soft white bloom around the running head only
        glow = max(glow, exp(-dist / (w * 6.0 + px * 2.0)) * hot * smoothstep(-0.08, 0.0, behind) * 0.8);
    }
    float a = max(max(white, glow), Sil) * Fade;
    if (a < 0.004) {
        discard;
    }
    vec3 col = vec3(1.0);
    if (white <= 0.0 && glow <= 0.0) {
        col = vec3(0.02, 0.01, 0.02);
    }
    col = mix(col, vec3(0.03, 0.0, 0.02), black);
    fragColor = vec4(col, a);
}
