#version 150

// The Duelo de Expansões (client/fx/DuelScene), from the preview Stark approved (forja/duelo-expansoes).
// Mode 0, the duel in Gojo's tunnel: the sky split in two, the Santuário's red on Sukuna's side (Axis points at him)
// eating the tunnel, Gojo's cyan on his, and the line between them pushed by every move (Push: +1 Gojo winning, -1
// Sukuna). The line is warped by noise and burns white, with sparks thrown off it.
// Mode 1, the tie in Gojo's void: the Santuário cracking the barrier from outside, red cracks spreading over the sky
// as Crack goes from 0 to 1.
// Output premultiplied (drawn with ONE, ONE_MINUS_SRC_ALPHA).
uniform float T;
uniform float Push;
uniform vec3 Axis;
uniform float Mode;
uniform float Crack;
uniform float Alpha;
uniform float Flash;
uniform float Seed;
// The last move that scored: 1 right when it lands, fading to 0; HitSide 0 for Gojo, 1 for Sukuna.
uniform float Hit;
uniform float HitSide;

in vec3 vW;
out vec4 fragColor;

float h31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
vec3 h33(vec3 p) { return vec3(h31(p), h31(p + vec3(17.3, 5.1, 9.7)), h31(p + vec3(3.9, 21.7, 13.3))); }
float n3(vec3 x) { vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z); }
float fb3(vec3 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { s += a * n3(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; } return s; }

// Distance to the nearest edge between Voronoi cells, and the nearest cell's id.
vec2 voronoi(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    float id = 0.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 g = vec3(float(x), float(y), float(z));
                vec3 o = h33(i + g);
                float d = length(g + o - f);
                if (d < d1) { d2 = d1; d1 = d; id = h31(i + g + 7.0); }
                else if (d < d2) { d2 = d; }
            }
        }
    }
    return vec2(d2 - d1, id);
}

vec4 duel(vec3 d) {
    float s = dot(d, normalize(Axis));
    // the line: where the two skies meet, wandering and boiling
    float n = fb3(d * 2.6 + vec3(0.0, T * 0.55, Seed)) - 0.5;
    float n2 = fb3(d * 8.0 - vec3(T * 1.4, 0.0, Seed)) - 0.5;
    float b = Push * 0.85 + n * 0.42 + n2 * 0.1;
    float e = s - b;
    float red = smoothstep(-0.012, 0.03, e);

    // the Santuário's sky: deep crimson, its clouds torn into streaks, black cuts raking across it
    vec3 q = d * 5.0 + vec3(T * 0.18, 0.0, -T * 0.11);
    float veins = fb3(q + fb3(q * 0.7) * 1.6);
    vec3 rc = mix(vec3(0.16, 0.0, 0.015), vec3(0.82, 0.05, 0.08), smoothstep(0.32, 0.78, veins));
    float rake = pow(1.0 - abs(2.0 * fb3(vec3(d.x * 3.0, d.y * 22.0 + d.z * 3.0, d.z * 3.0) + vec3(T * 0.4, 0.0, Seed)) - 1.0), 22.0);
    rc = mix(rc, vec3(0.01, 0.0, 0.0), rake * 0.8);
    rc += vec3(1.0, 0.3, 0.12) * exp(-max(e, 0.0) * 10.0) * 0.9;

    // the seam burning white, a cyan halo on Gojo's side, an orange one on Sukuna's
    float seam = exp(-abs(e) * (46.0 - 26.0 * Hit)) * (1.0 + 1.6 * Hit);
    float halo = exp(-abs(e) * (8.0 - 4.0 * Hit));
    vec3 col = rc * red;
    float a = red * 0.94;
    float cy = (1.0 - red) * halo;
    col += vec3(0.3, 0.92, 1.0) * cy * 1.1;
    a = max(a, cy * 0.55);
    col += vec3(1.0, 0.55, 0.25) * halo * red * 0.35;
    col += vec3(1.0, 0.98, 0.95) * seam * 1.8;
    // the side that scored flares along the whole line
    col += mix(vec3(0.35, 0.95, 1.0), vec3(1.0, 0.18, 0.12), HitSide) * Hit * halo * 1.2;
    a = max(a, seam);

    // sparks thrown off the seam: round, each its own size and life
    vec3 g = d * 150.0;
    vec3 cell = floor(g);
    vec3 r = h33(cell + floor(T * 9.0));
    float life = fract(T * (1.2 + r.x) + r.y);
    float dot1 = smoothstep(0.32 * r.z + 0.08, 0.0, length(fract(g) - 0.5 - (r - 0.5) * 0.4));
    float spark = step(0.93, r.x) * dot1 * (1.0 - life) * exp(-abs(e) * 14.0);
    col += mix(vec3(0.5, 0.95, 1.0), vec3(1.0, 0.6, 0.3), step(0.0, e)) * spark * 2.4;
    a = max(a, spark);

    col += vec3(1.0) * Flash;
    a = max(a, Flash);
    return vec4(col, a);
}

vec4 tie(vec3 d) {
    // the cracks: Voronoi edges, warped, opening from where the Santuário strikes (Axis) outward
    vec3 p = d * 3.2 + (vec3(fb3(d * 2.0 + Seed), fb3(d * 2.0 + 4.1 + Seed), fb3(d * 2.0 + 8.3 + Seed)) - 0.5) * 0.9;
    vec2 v = voronoi(p);
    vec2 v2 = voronoi(p * 2.7 + 11.0);
    float from = 1.0 - (dot(d, normalize(Axis)) * 0.5 + 0.5);
    float reach = Crack * 1.25 - from * 0.9 - v.y * 0.25;
    float open = smoothstep(0.0, 0.08, reach);
    float w = 0.02 + 0.05 * Crack * Crack;
    float line = (1.0 - smoothstep(0.0, w, v.x)) * open;
    float fine = (1.0 - smoothstep(0.0, w * 0.5, v2.x)) * smoothstep(0.0, 0.1, reach - 0.25);
    float glow = exp(-v.x * 9.0) * open * 0.6;
    float pulse = 0.75 + 0.25 * sin(T * 6.0 + v.y * 20.0);
    vec3 col = vec3(1.0, 0.12, 0.14) * (line * 2.2 + fine * 1.4) * pulse + vec3(0.6, 0.02, 0.04) * glow;
    col += vec3(1.0, 0.9, 0.85) * line * smoothstep(0.75, 1.0, Crack) * 0.8;
    float a = max(max(line, fine * 0.9), glow * 0.7);
    col += vec3(1.0, 0.2, 0.2) * Flash;
    a = max(a, Flash * 0.6);
    return vec4(col, a);
}

void main() {
    vec3 d = normalize(vW);
    vec4 c = Mode < 0.5 ? duel(d) : tie(d);
    fragColor = vec4(c.rgb * c.a, c.a) * Alpha;
}
