#version 150

// Shapes drawn from the quad's own coordinates, no textures:
//   0 soft    round glow, param = how much of the middle burns white
//   1 ring    thin circle, param = thickness
//   2 streak  long tapered line (u along it), param = white core
//   3 beam    ribbon with a hot core across v, param = core width
//   4 fire    soft blob eaten by moving noise, param = how burnt out it is (0 fresh, 1 gone)
//   5 smoke   lumpy blob for the alpha layer, param = how dissolved it is
//   6 flare   glow with sharp rays, param = number of rays / 32
//   7 solid   the plain color
//   8 disc    a filled circle with a soft edge, param = how wide the edge is
//   9 sun     a boiling star: white-hot middle, orange cells and bright streaks, a glowing rim and flames licking out
//             past it (the color tints it, white for a real sun), param = how far the flames reach
//  10 black hole  Infinite Void's: photon ring and swirling blue rings around an empty middle, param = the hole's size
//  11 splat   an ink splash with tendrils and droplets (black blots, white ink), param = which one
//  12 coffin  a wall of Aizen's Black Box: black light with faint violet veins and a crisp violet rim (alpha layer);
//             param < 0.5: a wall rising from the bottom (rise = param * 2) with a ragged burning top,
//             param >= 0.5: the lid shutting from the edges in ((param - 0.5) * 2 shut)
//  13 lance   a lance of black light on a ribbon (u from base to tip); param < 0.5: the black body with a violet rim
//             (alpha layer), param >= 0.5: its soft glow (additive)
//  14 shard   a shard of glass or eggshell; param < 0.5 a quad, else a triangle; fract(param * 2) slides the glint
//  15 wing    a black butterfly wing (u root to tip); param < 0.5 the body, else its glow
//  16 orb     the soft glow of shape 0 that is never faded near surfaces (something sitting right on a body)
//  17 aura    Aizen's reiatsu, cartoon: a black-violet mass full of stars with a sharp, boiling violet rim and glow
//             spilling out past it; param < 0.5 the body (alpha layer), else the glow (additive);
//             fract(param * 2) < 0.5 a flame rising from the bottom of the quad, else a round burst
//  18 azul    Gojo's Blue: param byte = style * 64 + charge * 63; style 0 mod glow (additive), 1 cartoon (alpha
//             layer), 2 realism, a glowing volume (additive; the one in the game), 3 mix, a glass ball (alpha layer, kept
//             for later)
//  19 vermelho tail  one tail of Gojo's Red, a ribbon with u from the orb (0) to the tip (1); param < 0.3 the body with
//             a dark ink edge (alpha layer), < 0.5 the wide smear behind it (alpha layer), else its glow (additive)
//  20 vermelho orb   Gojo's Red itself, drawn over the glow; param < 0.5 dark core (kept), else the bright core (the one
//             in the game)
//  21 roxo    Gojo's Purple: the Red and the Blue pressed together and mixing (alpha layer, drawn over the glow); the quad
//             runs along the line between them (u: red 0, blue 1), param * 8 = their distance in radii, color.g = the mix,
//             color.b >= 0.95 = no neon contour (the Unlimited Purple)
//  22 roxo vortex  grey wavy rings of twisted space round the Purple in flight, param = strength
//  23 roxo rays    light leaking out of the launched Purple in wide rays (the quad spins in Java)
//  24 roxo bolt    a living lightning bolt on a strip (u along it), param = the bolt's own seed
//  25 petal   one of Senbonzakura's blade petals (Byakuya): pointed both ends, pink with a light edge and a silver
//             glint running along it (fract(param) slides the glint)
//  26 crescent  the Getsuga Tensho (Ichigo): a sickle moon bulging toward +u, thick middle, sharp tips, a ragged edge
//             with wisps; param < 0.5 the light (additive, white core), else the black body with a red rim (alpha layer)
//  27 slit    Sukuna's cut: a thin lens along u; param < 0.5 the white glowing rim (additive), else the black inside
//  28 ice     Rukia's ice shell over a face (Minecraft pixels, cracks, a bright frontier); param < 0.5 it fills from the
//             bottom (v = 1 is the bottom) up to param * 2, else cell by cell up to (param - 0.5) * 2 frozen
//  29 barrier a shield of light with a honeycomb and a bright rim, tinted by the color; param < 0.5 a triangle (tip at
//             v = 0), else a hexagon; fract(param * 2) = how hard it ripples (a hit)
//  30 saw     the Kienzan / Death Saucer (Dragon Ball): a thin flat disc with saw teeth on its rim, grooves and spin
//             marks, tinted by the color (alpha layer), param = how fast it spins
//  31 genki   the Genki Dama: the glass of the Blue's style 3 without the flames inside: smooth gradient core, thin bright
//             rim and frost leaking out of it; the orb fills the middle half of the quad, param = seed
//  32 tongue  a cartoon tongue of ki (the Zanka no Tachi study): a drop from root (u = 0) to tip (u = 1) with a whip and a
//             fork, 5 nested bands moved by their own fields, swirl strokes, a hard edge, dying torn from the top, drawn
//             at 12 frames a second (alpha layer); param byte = life (0..15) * 16 + seed (0..15); the color is its skin
//  35 flight trail  the Dragon Ball flight's trail from the feet (forja voo/voo.html): a strip with u from the feet
//             (0) to the oldest point (1), strands of ki opening back with a white core
// A very dark color never burns white (dark rims and lines stay dark).
// Near walls and the ground the effect fades out instead of cutting a hard line (soft particles).
uniform sampler2D Sampler1;
uniform vec2 Screen;
uniform vec2 DepthRange;
uniform float SoftOn;
uniform float GameTime;

in vec2 uv;
in vec4 color;
flat in int shape;
in float param;
in float viewDist;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// ---- shape 18: Gojo's Blue (Azul). The same text runs in souls_vfx.fsh and in the Visualizador. ----
// param is a byte: style * 64 + charge * 63. Styles: 0 mod glow, 1 cartoon (alpha layer), 2 realism (volume), 3 mix (glass).
// The orb fills the middle half of the quad; the rest is room for glow, spikes and frost.
float azHash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float azNoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(azHash3(i), azHash3(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(azHash3(i + vec3(0.0, 1.0, 0.0)), azHash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(azHash3(i + vec3(0.0, 0.0, 1.0)), azHash3(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(azHash3(i + vec3(0.0, 1.0, 1.0)), azHash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float azFbm3(vec3 x) {
    return azNoise3(x) * 0.55 + azNoise3(x * 2.1 + vec3(3.1, 7.7, 1.3)) * 0.3 + azNoise3(x * 4.3 + vec3(9.2, 2.4, 5.6)) * 0.15;
}

vec4 azul(vec2 p, float param, float seed, float time) {
    float b = floor(param * 255.0 + 0.5);
    float style = floor(b / 64.0);
    float ch = mod(b, 64.0) / 63.0;
    float r = length(p);
    vec2 q = p * 2.0;
    float rr = length(q);
    float ang = atan(q.y, q.x);
    vec3 deep = vec3(0.03, 0.12, 0.62);
    vec3 blue = vec3(0.1, 0.42, 1.0);
    vec3 cyan = vec3(0.38, 0.88, 1.0);
    vec3 white = vec3(1.0);
    // Everything flows the same way: it turns around the middle (faster near it) and falls inward.
    float spin = 1.5 + 3.0 * ch;
    float fall = 0.6 + 1.4 * ch;
    vec3 col = blue;
    float alpha = 0.0;

    if (style < 0.5) {
        // Mod: additive light, a white core, spiral arms and a soft halo.
        float fa = ang - time * spin / (0.35 + rr);
        float lr = log(rr + 0.02) + time * fall;
        float sp = fbm(vec2(fa * 1.6 + seed, lr * 2.5));
        float arms = pow(0.5 + 0.5 * sin(fa * 3.0 + lr * 5.0), 4.0 + 4.0 * ch);
        float disc = 1.0 - smoothstep(0.86, 1.0, rr);
        float halo = pow(max(0.0, 1.0 - r), 2.6);
        float rim = exp(-abs(rr - 1.0) * 14.0);
        float swirl = arms * smoothstep(1.8, 0.6, rr) * smoothstep(0.05, 0.3, rr) * (0.55 + 0.3 * ch);
        float core = smoothstep(0.6, 0.0, rr);
        vec3 inner = mix(blue, cyan, clamp(sp * 1.3, 0.0, 1.0));
        vec3 outer = mix(blue, cyan, clamp(swirl * 1.5 + rim * 0.6, 0.0, 1.0));
        col = mix(outer, inner, disc);
        col = mix(col, white, clamp(core * 1.2 + swirl * 0.35, 0.0, 1.0));
        alpha = halo * 0.6 + disc * (0.35 + 0.5 * sp) + swirl + rim * 0.45 + core;
    } else if (style < 1.5) {
        // Cartoon: hard edge, 4 flat bands, a navy outline and brush spikes; drawn on twos (12 fps).
        float tq = floor(time * 12.0) / 12.0;
        float fa = ang - tq * spin / (0.35 + rr);
        float lr = log(rr + 0.02) + tq * fall;
        float boil = (fbm(vec2(ang * 1.5 + seed, tq * 3.0)) - 0.5) * 0.06;
        float d = 1.0 + boil - rr;
        float fw = max(fwidth(d), 1.0e-4);
        float inside = smoothstep(0.0, 1.5 * fw, d);
        float z = sqrt(max(0.0, 1.0 - rr * rr));
        float lit = dot(vec3(q, z), normalize(vec3(-0.45, 0.55, 0.7)));
        float strokes = fbm(vec2(fa * 2.0 + seed, lr * 3.0));
        float v = 0.4 * lit + 0.6 * strokes + 0.3 * (1.0 - rr) + 0.1 * ch;
        float vw = max(fwidth(v), 0.004) * 1.5;
        vec3 navy = vec3(0.02, 0.05, 0.26);
        vec3 c = vec3(0.04, 0.2, 0.78);
        c = mix(c, vec3(0.12, 0.52, 1.0), smoothstep(0.3, 0.3 + vw, v));
        c = mix(c, vec3(0.48, 0.92, 1.0), smoothstep(0.52, 0.52 + vw, v));
        c = mix(c, white, smoothstep(0.74, 0.74 + vw, v));
        c = mix(c, navy, 1.0 - smoothstep(0.06, 0.06 + 1.5 * fw, d));
        // Spikes: thick at the orb, sharp outward, redrawn 4 times a second.
        float n = 22.0;
        float sa = ang / 6.2831853 * n + seed;
        float k = floor(sa);
        float h = azHash3(vec3(k, floor(tq * 4.0), seed));
        float len = 1.08 + h * (0.35 + 0.55 * ch);
        float across = abs(fract(sa) - 0.5);
        float t01 = clamp((rr - 1.0) / max(len - 1.0, 0.01), 0.0, 1.0);
        float sd = 0.32 * (1.0 - t01) - across;
        float sfw = max(fwidth(sd), 1.0e-4);
        float spike = smoothstep(0.0, 1.5 * sfw, sd) * step(rr, len) * step(0.4, h) * (1.0 - inside);
        vec3 sc = mix(vec3(0.48, 0.92, 1.0), white, smoothstep(0.1, 0.2, sd));
        col = mix(sc, c, inside);
        alpha = max(inside, spike * 0.95);
    } else if (style < 2.5) {
        // Realism: a glowing volume marched front to back, gas turning faster near the middle and falling in.
        float zMax = sqrt(max(0.0, 1.0 - rr * rr));
        float trans = 1.0;
        vec3 acc = vec3(0.0);
        float dt = 2.0 * zMax / 14.0;
        float ph = fract(time * 0.25 * (1.0 + ch));
        float w1 = 1.0 - abs(2.0 * ph - 1.0);
        float s1 = exp(ph * 0.7);
        float s2 = exp(fract(ph + 0.5) * 0.7);
        if (rr < 1.0) {
            for (int i = 0; i < 14; i++) {
                float z = zMax * (1.0 - 2.0 * (float(i) + 0.5) / 14.0);
                vec3 P = vec3(q, z);
                float pr = length(P);
                float a = time * spin / (0.4 + pr);
                float cs = cos(a);
                float sn = sin(a);
                vec3 R = vec3(P.x * cs - P.z * sn, P.y * cs + (P.x * sn + P.z * cs) * 0.25, P.x * sn + P.z * cs);
                vec3 o = vec3(seed, seed * 1.7, 0.0);
                float g = mix(azFbm3(R * 2.4 * s2 + o), azFbm3(R * 2.4 * s1 + o + 5.0), w1);
                float dens = smoothstep(0.38, 0.8, g) * (1.0 - smoothstep(0.7, 1.0, pr)) * 2.2 + exp(-pr * pr * 10.0) * 3.0;
                float temp = clamp(g * 0.9 + exp(-pr * pr * 5.0) * 0.8 - pr * 0.25, 0.0, 1.0);
                vec3 emit = mix(deep, blue, smoothstep(0.1, 0.4, temp));
                emit = mix(emit, cyan, smoothstep(0.4, 0.7, temp));
                emit = mix(emit, white, smoothstep(0.7, 0.95, temp));
                float aStep = 1.0 - exp(-dens * 2.4 * dt);
                acc += trans * aStep * emit * (0.6 + 1.2 * temp);
                trans *= 1.0 - aStep;
            }
        }
        float corona = pow(max(0.0, 1.0 - r), 3.0) * 0.75 + exp(-abs(rr - 1.0) * 9.0) * 0.25;
        alpha = 1.0 - trans + corona;
        col = (acc + blue * corona) / max(alpha, 1.0e-3);
    } else {
        // Mix: a glass ball (ref 3): crisp bright rim, jagged white flames inside, frost licking out of the edge.
        float fa = ang - time * spin / (0.35 + rr);
        float lr = log(rr + 0.02) + time * fall;
        float fw = max(fwidth(rr), 1.0e-4);
        float disc = smoothstep(0.0, 1.5 * fw, 1.0 - rr);
        float fl = fbm(vec2(fa * 2.5 + seed, lr * 2.0));
        float ridge = 1.0 - abs(2.0 * fl - 1.0);
        float jag = smoothstep(0.86, 0.9, ridge + (1.0 - rr) * 0.06);
        vec3 inner = mix(deep, mix(blue, cyan, 0.5), 0.45 + 0.55 * (1.0 - rr));
        inner = mix(inner, white, max(jag * 0.9, smoothstep(0.35, 0.0, rr) * 0.6));
        inner = mix(inner, vec3(0.78, 0.96, 1.0), pow(rr, 8.0) * 0.85);
        float line = 1.0 - smoothstep(0.0, 2.5 * fw, abs(1.0 - rr));
        float frost = pow(1.0 - abs(fbm(vec2(ang * 7.0 + seed, rr * 5.0 - time * 2.5)) * 2.0 - 1.0), 10.0) * exp(-max(0.0, rr - 1.0) * 5.0) * step(1.0, rr);
        float halo = pow(max(0.0, 1.0 - r), 2.5) * 0.45;
        col = mix(mix(blue, cyan, frost), inner, disc);
        col = mix(col, white, line);
        alpha = max(disc * 0.8, line) + frost * (0.6 + 0.4 * ch) + halo;
    }
    return vec4(col, clamp(alpha, 0.0, 1.0));
}

// ---- shapes 19 and 20: Gojo's Red (Vermelho). The same text runs in souls_vfx.fsh and in the Visualizador. ----
// 19 = one tail. uv.x runs from the orb (0) to the tip (1), uv.y across the strip.
//      param < 0.3: the body with a dark ink edge leaking along it (alpha layer).
//      param < 0.5: the wide smear behind it, no ink (alpha layer). param >= 0.5: the glow copy (additive, feeds the bloom).
// 20 = the orb. It fills the middle half of the quad; outside is halo. param < 0.5 dark core, >= 0.5 bright core.
float vmGrain(vec2 px, float time) {
    vec2 k = floor(px) + floor(time * 24.0) * vec2(7.1, 3.7);
    return fract(sin(dot(k, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 vermelhoRabo(vec2 uv, float param, float seed, float time) {
    float u = uv.x;
    float y = uv.y * 2.0 - 1.0;
    // The energy runs along the tail into the orb.
    float flow = time * 1.8;
    float n1 = fbm(vec2(u * 5.0 + flow + seed, y * 1.2 + seed * 0.7));
    float n2 = fbm(vec2(u * 13.0 + flow * 1.6, y * 2.6 - seed));
    // The band wanders across the strip and its edge frays, more at the tip.
    float bend = (n1 - 0.5) * 0.45;
    float e = abs(y - bend) + (n2 - 0.5) * (0.35 + 0.5 * u);
    float body = 1.0 - smoothstep(0.1, 1.0, e);
    float core = 1.0 - smoothstep(0.0, 0.16 + 0.12 * n2, abs(y - bend * 1.1));
    // Brush strands along the length.
    float strands = smoothstep(0.42, 0.78, fbm(vec2(u * 2.2 + flow * 0.8, y * 6.0 + seed * 3.0)));
    float tip = 1.0 - smoothstep(0.5, 1.0, u + (n1 - 0.5) * 0.35);
    float root = smoothstep(0.0, 0.05, u);
    float grain = vmGrain(gl_FragCoord.xy * 0.5, time);
    vec3 deep = vec3(0.32, 0.0, 0.03);
    vec3 red = vec3(1.0, 0.06, 0.1);
    vec3 hot = vec3(1.0, 0.62, 0.66);
    float heat = clamp(core * (0.65 + 0.35 * strands) * (1.0 - 0.55 * u), 0.0, 1.0);
    vec3 col = mix(deep, red, smoothstep(0.0, 0.6, body));
    col = mix(col, hot, smoothstep(0.35, 0.75, heat));
    col = mix(col, vec3(1.0), smoothstep(0.75, 1.0, heat));
    float a;
    if (param < 0.3) {
        // Dark ink leaking along the frayed edge.
        float rim = smoothstep(0.45, 0.7, e) * (1.0 - smoothstep(0.8, 1.05, e + (n1 - 0.5) * 0.3));
        col = mix(col, vec3(0.1, 0.0, 0.03), rim * 0.85);
        a = max(body * (0.45 + 0.55 * strands) + core, rim * 0.75) * tip * root * (0.85 + 0.3 * (grain - 0.5));
    } else if (param < 0.5) {
        a = (body * (0.45 + 0.55 * strands) + core) * tip * root * (0.85 + 0.3 * (grain - 0.5));
    } else {
        col = mix(red, hot, heat);
        a = (body * 0.35 + heat) * tip * root;
    }
    return vec4(col, clamp(a, 0.0, 1.0));
}

vec4 vermelhoOrbe(vec2 p, float param, float seed, float time) {
    float r = length(p);
    vec2 q = p * 2.0;
    float rr = length(q);
    float ang = atan(q.y, q.x);
    // Spins clockwise on screen, the same way and speed as the tails (giro 0.24 rad/tick = 4.8 rad/s).
    float spinA = time * 4.8;
    float cs = cos(spinA);
    float sn = sin(spinA);
    vec2 qr = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
    float n = fbm(qr * 1.6 + vec2(seed, seed * 0.5));
    float n2 = fbm(qr * 3.5 + vec2(-seed, time * 0.3));
    // The surface boils a little.
    float d = rr + (n - 0.5) * 0.14;
    vec3 black = vec3(0.03, 0.0, 0.01);
    vec3 deep = vec3(0.4, 0.0, 0.04);
    vec3 red = vec3(1.0, 0.05, 0.1);
    vec3 hot = vec3(1.0, 0.6, 0.62);
    float swirl = 0.5 + 0.5 * sin((ang + spinA) * 3.0 - rr * 6.0 + n * 4.0);
    vec3 col;
    if (param < 0.5) {
        // Dark core: a black hole ringed by white-hot light, then the red body.
        float hole = 1.0 - smoothstep(0.52, 0.58, d);
        float ring = exp(-pow((d - 0.63) / 0.06, 2.0));
        col = mix(red, hot, swirl * 0.5 * n2);
        col = mix(col, deep, smoothstep(0.75, 1.0, d) * 0.6);
        col = mix(col, vec3(1.0, 0.85, 0.86), ring);
        col = mix(col, black, hole);
    } else {
        // Bright core: white in the middle, pink, then red bands.
        float c = 1.0 - smoothstep(0.0, 0.5, d);
        col = mix(red, hot, smoothstep(0.35, 0.75, 1.0 - d + 0.2 * n2));
        col = mix(col, vec3(1.0), smoothstep(0.55, 0.85, c + 0.1 * n2));
        col = mix(col, deep, smoothstep(0.8, 1.0, d) * 0.5);
    }
    // The red halo outside (no outline on the orb: the ink lives on the tails).
    float body = 1.0 - smoothstep(0.96, 1.04, d);
    float halo = pow(max(0.0, 1.0 - r), 2.4) * (0.55 + 0.25 * n);
    col = mix(mix(deep, red, 0.6 + 0.4 * n2), col, body);
    return vec4(col, clamp(max(body, halo), 0.0, 1.0));
}

// ---- shape 21: Gojo's Purple (Roxo). The Red and the Blue pressed together, mixing into purple. ----
// The quad runs along the line between the two centres (uv.x: red at 0, blue at 1) and is 6 radii tall.
// param * 8 = distance between the centres, in radii (0 = one sphere). vCol.g = how far the mix went (0..1).
vec3 roxoRotY(vec3 n, float a) { float c = cos(a); float s = sin(a); return vec3(n.x * c - n.z * s, n.y, n.x * s + n.z * c); }

// Glitter: tiny white points twinkling inside the purple, on two layers turning at different speeds.
float roxoGlitter(vec2 q, float seed, float time) {
    float g = 0.0;
    for (int l = 0; l < 2; l++) {
        float fl = float(l);
        float a = time * (0.25 + 0.2 * fl) * (fl > 0.5 ? -1.0 : 1.0);
        vec2 r = vec2(q.x * cos(a) - q.y * sin(a), q.x * sin(a) + q.y * cos(a)) * (7.0 + 5.0 * fl);
        vec2 id = floor(r);
        float h = hash(id + vec2(seed * 3.1 + fl * 17.0, fl * 5.3));
        vec2 at = vec2(hash(id + 1.7 + fl), hash(id + 9.1 - fl)) - 0.5;
        float d = length(fract(r) - 0.5 - at * 0.6);
        float tw = 0.5 + 0.5 * sin(time * (5.0 + 4.0 * h) + h * 40.0);
        g += smoothstep(0.11, 0.0, d) * step(0.55, h) * tw * tw;
    }
    return g;
}

vec4 roxo(vec2 uv, float param, float seed, float time, float mixK, float noRim) {
    float k = param * 8.0;
    float m = clamp(mixK, 0.0, 1.0);
    vec2 q = vec2((uv.x - 0.5) * (k + 6.0), (uv.y - 0.5) * 6.0);
    vec2 a = q + vec2(k * 0.5, 0.0);
    vec2 b = q - vec2(k * 0.5, 0.0);
    float ra = length(a);
    float rb = length(b);
    // One body: two circles joined by a neck that softens as they mix. Formed, it is a single sphere a bit bigger.
    float kk = 0.25 + 0.6 * m;
    float hh = clamp(0.5 + 0.5 * (rb - ra) / kk, 0.0, 1.0);
    float du = mix(rb, ra, hh) - kk * hh * (1.0 - hh) - (1.0 + 0.1 * m);
    float fw = max(fwidth(du), 1.0e-4);
    float body = 1.0 - smoothstep(-fw, fw, du);
    float side = smoothstep(-0.25, 0.25, ra - rb);
    float touch = smoothstep(2.4, 1.8, k);
    vec3 nA = vec3(a, sqrt(max(0.0, 1.0 - dot(a, a))));
    vec3 nB = vec3(b, sqrt(max(0.0, 1.0 - dot(b, b))));
    vec3 n = normalize(mix(nA, nB, side) + vec3(0.0, 0.0, 0.001));

    // ---- the two spheres: bright marble glass (red turning one way, blue the other), dark veins, glassy light ----
    vec3 pA = roxoRotY(nA, time * 0.45);
    vec3 pB = roxoRotY(nB, -time * 0.55);
    float fA = azFbm3(pA * 2.4 + vec3(seed, 1.3, seed * 0.5));
    float fB = azFbm3(pB * 2.4 + vec3(4.1, seed, 2.2));
    float vA = azFbm3(pA * 4.6 + vec3(fA * 2.2) + vec3(seed));
    float vB = azFbm3(pB * 4.6 + vec3(fB * 2.2) + vec3(7.0));
    float veinA = 1.0 - smoothstep(0.0, 0.07, abs(vA - 0.5));
    float veinB = 1.0 - smoothstep(0.0, 0.07, abs(vB - 0.5));
    vec3 redDeep = vec3(0.22, 0.0, 0.03);
    vec3 red = vec3(1.0, 0.04, 0.16);
    vec3 redHot = vec3(1.0, 0.6, 0.72);
    vec3 bluDeep = vec3(0.0, 0.03, 0.25);
    vec3 blu = vec3(0.12, 0.38, 1.0);
    vec3 bluHot = vec3(0.7, 0.88, 1.0);
    vec3 cr = mix(redDeep, red, smoothstep(0.25, 0.6, fA));
    cr = mix(cr, redHot, smoothstep(0.62, 0.85, fA) * 0.7);
    cr *= 1.0 - 0.75 * veinA;
    vec3 cb = mix(bluDeep, blu, smoothstep(0.25, 0.6, fB));
    cb = mix(cb, bluHot, smoothstep(0.62, 0.85, fB) * 0.7);
    cb *= 1.0 - 0.75 * veinB;
    vec3 L = normalize(vec3(-0.45, 0.55, 0.7));
    float lit = 0.35 + 0.75 * max(dot(n, L), 0.0);
    vec3 col = mix(cr, cb, side) * lit;
    // the halves facing each other glow violet-white, more as they get close (the refs)
    float faceA = smoothstep(-0.1, 0.9, nA.x);
    float faceB = smoothstep(-0.1, 0.9, -nB.x);
    float face = mix(faceA, faceB, side) * (0.35 + 0.65 * smoothstep(3.2, 1.6, k));
    vec3 inner = mix(vec3(1.0, 0.35, 0.95), vec3(0.75, 0.55, 1.0), side);
    col = mix(col, inner, face * 0.75);
    col += vec3(1.0, 0.9, 1.0) * pow(face, 3.0) * 0.4;
    // glass: sharp highlight and a coloured fresnel rim
    float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 40.0);
    float fres = pow(1.0 - clamp(n.z, 0.0, 1.0), 2.5);
    col += vec3(1.0) * spec * 0.7 + mix(redHot, bluHot, side) * fres * 0.45;

    // ---- wind / air resistance: bright bands sweeping across the spheres and streaking past them ----
    float wind = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float h1 = hash(vec2(fi * 3.7, seed));
        float y0 = (h1 - 0.5) * 1.8 + 0.2 * sin(time * 0.6 + fi * 2.0);
        // over a sphere the band curves round it (a tilted ring); outside it runs straight
        float onS = mix(n.y - 0.35 * n.z * (h1 - 0.5) * 2.0, q.y, 0.0);
        float yy = mix(q.y, onS * 1.0, body);
        float w = 0.06 + 0.12 * hash(vec2(fi, seed + 1.0));
        float band = exp(-pow((yy - y0) / w, 2.0));
        float flow = fbm(vec2(q.x * 0.45 + time * (2.2 + fi * 0.7), fi * 9.0 + seed));
        band *= smoothstep(0.42, 0.72, flow);
        wind += band;
    }
    // soft at every edge of the quad, so a band never ends in a straight cut
    wind *= (1.0 - m) * (1.0 - smoothstep(k * 0.5 + 1.2, k * 0.5 + 2.8, abs(q.x))) * (1.0 - smoothstep(1.6, 2.7, abs(q.y)));
    vec3 windCol = mix(vec3(1.0, 0.7, 0.82), vec3(0.72, 0.9, 1.0), smoothstep(-0.6, 0.6, q.x));
    col = mix(col, windCol, clamp(wind, 0.0, 1.0) * body * 0.85);

    // ---- the Roxo itself (the Stark's ball video): a burst of light streaming out of a white core, purple plumes
    // racing to the edge, darker mottled purple near the rim, held in by a thick neon ring. Born where they touch. ----
    float r0 = length(q) / (1.0 + 0.1 * m);
    float ang = atan(q.y, q.x);
    vec2 cs = vec2(cos(ang), sin(ang));
    float flow = time * 1.6;
    float plume = azFbm3(vec3(cs * 2.3, r0 * 2.6 - flow) + vec3(seed));
    float plume2 = azFbm3(vec3(cs * 5.0, r0 * 4.0 - flow * 1.4) + vec3(seed * 2.0, 3.0, 1.0));
    float rays = pow(plume * 0.65 + plume2 * 0.45, 2.2);
    float mott = azFbm3(vec3(q * 2.2, time * 0.5) + vec3(seed));
    vec3 violetDeep = vec3(0.24, 0.02, 0.42);
    vec3 violet = vec3(0.62, 0.16, 0.95);
    vec3 orchid = vec3(0.92, 0.55, 1.0);
    vec3 pale = vec3(1.0, 0.9, 1.0);
    float inR = 1.0 - smoothstep(0.0, 1.0, r0);
    vec3 pc = mix(violetDeep, violet, smoothstep(0.3, 0.75, mott) * 0.8 + 0.2 * inR);
    pc = mix(pc, orchid, clamp(rays * (0.6 + 1.6 * inR), 0.0, 1.0));
    float coreR = mix(0.22, 0.42, m) * (0.9 + 0.1 * sin(time * 7.0));
    float core = exp(-pow(r0 / coreR, 2.0));
    pc = mix(pc, pale, smoothstep(0.1, 0.7, core + rays * inR * 0.5));
    pc = mix(pc, vec3(1.0), smoothstep(0.55, 1.0, core));
    pc = mix(pc, violetDeep, smoothstep(0.62, 0.92, r0) * 0.45 * (1.0 - rays));
    float reach = mix(0.25, 1.6, m) * touch;
    float glow = exp(-pow(r0 / max(reach, 0.05), 2.0) * 1.4) * touch;
    float cover = clamp(glow * 1.3 + m * m, 0.0, 1.0);
    col = mix(col, pc, cover);
    float gl = roxoGlitter(q, seed, time) * clamp(cover * 1.4 - 0.2, 0.0, 1.0);
    col += mix(vec3(1.0, 0.92, 1.0), vec3(1.0, 0.7, 0.45), step(0.7, hash(floor(q * 9.0)))) * gl * (0.6 + 0.6 * core);
    // the neon contour: thick hot pink-violet band with a white line in it, shimmering a little
    // noRim = 1: Roxo Irrestrito, no contour (only the purple with glow)
    float formed = smoothstep(0.55, 1.0, m) * (1.0 - noRim);
    float dw = du + 0.012 * (noise(vec2(ang * 4.0 + time * 3.0, seed)) - 0.5);
    float rimBand = exp(-pow(dw / 0.075, 2.0));
    float rimLine = exp(-pow(dw / 0.022, 2.0));
    vec3 neon = vec3(1.0, 0.32, 0.95);
    col = mix(col, neon, rimBand * formed * body * 0.9);
    col = mix(col, vec3(1.0, 0.9, 1.0), rimLine * formed * body);

    // ---- around: red and blue smoke beside each sphere; formed, ragged magenta flame-smoke round the ring ----
    float outside = max(du, 0.0);
    float hn = fbm(q * 1.3 + vec2(seed * 2.0, time * 0.35));
    float smoke = pow(max(0.0, 1.0 - outside * 0.8), 2.2) * smoothstep(0.25, 0.75, hn) * (1.0 - m);
    vec3 sc = mix(red * 0.75, blu * 0.75, side);
    sc = mix(sc, violet, touch * exp(-pow(length(q) / 0.9, 2.0)));
    float rag = azFbm3(vec3(cs * 2.0, outside * 3.0 - time * 1.2) + vec3(seed * 3.0));
    float flame = smoothstep(0.35, 0.6, rag - outside * 0.9) * formed;
    vec3 fc = mix(vec3(0.5, 0.05, 0.42), vec3(0.85, 0.2, 0.75), smoothstep(0.5, 0.8, rag));
    float halo = exp(-outside * 2.6) * m;
    vec3 hc = mix(violet, neon, exp(-outside * 9.0) * (1.0 - noRim));
    float og = roxoGlitter(q * 1.1, seed + 3.0, time) * exp(-outside * 1.6) * m;
    float wOut = wind * (1.0 - body) * 0.7;
    vec3 outCol = mix(sc, hc, clamp(halo / max(halo + smoke, 1.0e-3), 0.0, 1.0));
    outCol = mix(outCol, fc, flame * 0.85);
    outCol = mix(outCol, vec3(1.0, 0.85, 1.0), exp(-pow(dw / 0.05, 2.0)) * formed);
    outCol = mix(outCol, windCol, clamp(wOut, 0.0, 1.0));
    outCol += vec3(1.0, 0.9, 1.0) * og;
    float outA = clamp(max(max(smoke * 0.75, halo * 0.75), max(max(wOut, og), max(flame * 0.8, exp(-pow(dw / 0.05, 2.0)) * formed))), 0.0, 1.0);
    col = mix(outCol, col, body);
    return vec4(col, clamp(max(body, outA), 0.0, 1.0));
}

// ---- shape 22: the space twisting round the Roxo (grey wavy rings in a whirl, like the ref). param = strength. ----
vec4 roxoVortex(vec2 p, float param, float seed, float time) {
    float r = length(p);
    float ang = atan(p.y, p.x);
    float tw = ang + 1.6 / (r + 0.25) - time * 1.2;
    float n = fbm(vec2(cos(tw), sin(tw)) * 2.0 + vec2(r * 3.0, seed));
    float rings = sin(r * 30.0 - time * 5.0 + 3.0 * sin(tw * 2.0) + n * 6.0);
    float crest = smoothstep(0.55, 1.0, rings);
    float trough = smoothstep(-0.4, -1.0, rings);
    vec3 col = mix(vec3(0.05, 0.04, 0.08), vec3(0.62, 0.6, 0.7), crest);
    col = mix(col, vec3(0.35, 0.2, 0.55), 0.25 * (1.0 - crest));
    float env = smoothstep(0.28, 0.5, r) * (1.0 - smoothstep(0.72, 1.0, r));
    float a = env * (0.25 + 0.55 * crest + 0.45 * trough) * param;
    return vec4(col, clamp(a, 0.0, 1.0));
}

// ---- shape 23: light leaking out of the launched Roxo (wide rays of different widths; the quad spins in Java). ----
vec4 roxoRays(vec2 p, float param, float seed, float time) {
    float r = length(p);
    float ang = atan(p.y, p.x);
    float sum = 0.0;
    vec3 col = vec3(0.0);
    for (int i = 0; i < 11; i++) {
        float fi = float(i);
        float a0 = hash(vec2(fi, seed)) * 6.2832;
        float w = 0.03 + 0.16 * hash(vec2(fi + 4.0, seed));
        float len = 0.55 + 0.45 * hash(vec2(fi + 9.0, seed));
        float d = abs(mod(ang - a0 + 3.1416, 6.2832) - 3.1416);
        float beam = smoothstep(w, w * 0.25, d) * (1.0 - smoothstep(len * 0.6, len, r));
        beam *= 0.7 + 0.3 * sin(time * 9.0 + fi * 2.3);
        vec3 c = mix(vec3(0.55, 0.2, 1.0), vec3(0.45, 0.4, 1.0), hash(vec2(fi + 2.0, seed)));
        c = mix(c, vec3(0.95, 0.75, 1.0), smoothstep(0.35, 0.0, r) * 0.7);
        col += c * beam;
        sum += beam;
    }
    float a = clamp(sum, 0.0, 1.0) * smoothstep(0.03, 0.12, r);
    return vec4(col / max(sum, 1.0e-3), a);
}

// ---- shape 24: a living lightning bolt (the Stark's video): a curvy line that wanders, thick wet blobs on it,
// a web of thin tendrils, white core with a purple glow, redrawn 15 times a second. Drawn on a strip (uv.x along it).
// param = this bolt's own seed (0..1).
vec4 roxoRaio(vec2 uv, float param, float seed, float time) {
    float s = seed + param * 91.0;
    float tq = floor(time * 15.0);
    float u = uv.x;
    float v = uv.y * 2.0 - 1.0;
    float ends = smoothstep(0.0, 0.08, u) * smoothstep(1.0, 0.88, u);
    // main line: big slow bends plus small kinks, all changing every redraw
    float c0 = (fbm(vec2(u * 2.6, s + tq * 0.37)) - 0.5) * 1.2 + (noise(vec2(u * 7.0, s * 0.7 + tq * 1.1)) - 0.5) * 0.45 + (noise(vec2(u * 19.0, s * 1.3 + tq * 1.7)) - 0.5) * 0.22;
    float blob = pow(fbm(vec2(u * 7.0, s * 2.1 + tq * 0.9)), 3.0);
    float th = (0.022 + 0.2 * blob) * ends;
    float d0 = abs(v - c0);
    float fw = max(fwidth(v), 1.0e-4);
    float line = smoothstep(th + fw, max(th - fw, 0.0), d0);
    // tendrils: thinner lines branching off the main one, showing in pieces
    float web = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float ci = c0 + (fbm(vec2(u * 4.5 + fi * 3.1, s + fi * 7.3 + tq * 0.53)) - 0.5) * 1.5;
        float piece = smoothstep(0.42, 0.62, noise(vec2(u * 5.0 + fi * 11.0, s + tq * 0.71 + fi)));
        float ti = (0.007 + 0.01 * hash(vec2(fi, s))) * ends;
        web = max(web, smoothstep(ti + fw, max(ti - fw, 0.0), abs(v - ci)) * piece * (0.55 + 0.45 * hash(vec2(fi + tq, s))));
    }
    // some redraws flicker weak or go out, like the video
    float fl = hash(vec2(tq, s * 0.7));
    float on = fl < 0.12 ? 0.0 : fl < 0.3 ? 0.4 : 1.0;
    float glow = exp(-d0 * 7.0) * 0.45 * ends + exp(-d0 * 22.0) * 0.4 * ends;
    float core = max(line, web * 0.9);
    vec3 col = mix(vec3(0.62, 0.22, 1.0), vec3(1.0, 0.94, 1.0), clamp(line * (0.6 + 0.6 * blob * 8.0) + web * 0.5, 0.0, 1.0));
    float a = clamp(core + glow * (1.0 - core), 0.0, 1.0) * on;
    return vec4(col, a);
}

// ---- shapes 25-29: the SubAlmas (the same look as the Visualizador's Bleach and Sukuna tabs). ----
vec4 petal(vec2 p, float param, vec3 tint) {
    float yy = p.y;
    float hw = 0.92 * (1.0 - yy * yy) * (1.0 - 0.18 * yy);
    float d = hw - abs(p.x);
    float fw = max(fwidth(d), 1.0e-4);
    float body = smoothstep(0.0, 1.5 * fw, d);
    float edge = 1.0 - smoothstep(0.0, 0.22, d / max(hw, 0.05));
    float ridge = 1.0 - smoothstep(0.0, 0.1, abs(p.x));
    float g = pow(max(0.0, 1.0 - abs(yy - (fract(param) * 3.0 - 1.5)) * 2.2), 3.0);
    vec3 col = mix(tint, vec3(1.0, 0.94, 0.98), clamp(edge * 0.55 + ridge * 0.3 + g * 0.9, 0.0, 1.0));
    return vec4(col, body);
}

vec4 crescent(vec2 p, float param, float seed, float time, out float core) {
    vec2 q = vec2(p.x, p.y * 1.5);
    float n = fbm(vec2(q.x * 3.0 - time * 4.0, q.y * 2.5) + seed) - 0.5;
    float n2 = fbm(vec2(q.x * 7.0 - time * 6.0, q.y * 6.0) + seed * 1.7) - 0.5;
    float dO = 1.45 - length(q - vec2(-0.5, 0.0)) + n * 0.16;
    float dI = length(q - vec2(-1.1, 0.0)) - 1.6 + n * 0.22 + n2 * 0.12;
    float d = min(dO, dI);
    float body = smoothstep(-0.02, 0.05, d);
    float halo = exp(-max(0.0, -d) * 9.0) * 0.5;
    float front = smoothstep(0.22, 0.0, dO) * body;
    float wisp = pow(max(0.0, n2 + 0.5), 3.0) * smoothstep(-0.3, 0.0, d) * (1.0 - body);
    float border = smoothstep(1.0, 0.72, max(abs(p.x), abs(p.y)));
    if (param < 0.5) {
        core = body * (0.3 + 0.7 * front);
        return vec4(color.rgb, max(body * (0.6 + 0.4 * front), halo + wisp * 1.4) * border);
    }
    core = 0.0;
    float rim = 1.0 - smoothstep(0.0, 0.08, d);
    return vec4(mix(vec3(0.015, 0.0, 0.01), vec3(1.0, 0.1, 0.07), rim * body), body * 0.97 * border);
}

vec4 slit(vec2 p, vec2 uvq, float param, out float core) {
    float x = uvq.x * 2.0 - 1.0;
    float hw = 0.3 * pow(max(0.0, 1.0 - x * x), 0.9);
    float d = hw - abs(p.y);
    float fw = max(fwidth(d), 1.0e-4);
    float body = smoothstep(-fw, fw, d);
    float fade = smoothstep(1.0, 0.8, abs(p.y)) * smoothstep(1.0, 0.9, abs(x));
    if (param < 0.5) {
        float rim = exp(-abs(d) * 38.0) * smoothstep(0.0, 0.25, 1.0 - abs(x));
        float halo = exp(-max(0.0, -d) * 7.0) * 0.4 * (hw / 0.3 + 0.1);
        float line = exp(-abs(p.y) * 55.0) * 0.7 * (1.0 - body);
        core = rim;
        return vec4(color.rgb, max(max(rim, halo), line) * fade);
    }
    core = 0.0;
    float rim = 1.0 - smoothstep(0.0, 0.035, d);
    return vec4(mix(vec3(0.0), vec3(1.0), rim * 0.85), body * 0.98 * fade);
}

vec2 voronoi2(vec2 x, float seed) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int a = -1; a <= 1; a++) {
        for (int b = -1; b <= 1; b++) {
            vec2 g = vec2(float(a), float(b));
            vec2 o = vec2(hash(i + g + seed), hash(i + g + seed + 11.3));
            float d = length(g + o - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return vec2(d1, d2);
}

vec4 ice(vec2 uvq, float param, float seed, float time, out float core) {
    // 8 Minecraft pixels across each quad (Java cuts the shell into quads of half a block).
    vec2 cell = floor(uvq * 8.0);
    float n = hash(cell + seed);
    float d;
    if (param < 0.5) {
        float fill = param * 2.0;
        float fromBottom = 1.0 - (cell.y + 0.5) / 8.0;
        d = fill * 1.12 - (fromBottom + (n - 0.5) * 0.1 + 0.05);
    } else {
        d = (param - 0.5) * 2.0 * 1.1 - n;
    }
    core = 0.0;
    if (d <= 0.0) {
        return vec4(0.0);
    }
    float edge = 1.0 - smoothstep(0.0, 0.07, d);
    vec2 vo = voronoi2(uvq * 3.0, seed);
    float crack = 1.0 - smoothstep(0.0, 0.05, vo.y - vo.x);
    float tw = step(0.95, n) * pow(max(0.0, sin(time * 4.0 + n * 60.0)), 12.0);
    vec3 deep = vec3(0.28, 0.56, 0.9);
    vec3 pale = vec3(0.84, 0.95, 1.0);
    vec3 c = mix(deep, pale, clamp(0.35 + vo.x * 0.4 + (n - 0.5) * 0.15, 0.0, 1.0));
    c += vec3(0.75, 0.9, 1.0) * crack * 0.35 + tw;
    // color.g shades the face (the 3D ice spikes light each side differently; 0.9 = full light).
    c *= 0.6 + 0.4 * clamp(color.g / 0.9, 0.0, 1.2);
    core = edge * 0.8 + tw;
    return vec4(c, clamp(0.5 + crack * 0.25 + edge * 0.5 + tw, 0.0, 1.0));
}

vec4 barrier(vec2 p, vec2 uvq, float param, float time, out float core) {
    float d;
    if (param < 0.5) {
        // Triangle, tip at the top (v = 0): distance to its three edges.
        vec2 q = vec2(p.x, -p.y);
        float e1 = q.y + 0.9;
        float e2 = 0.866 * (1.0 - q.y) * 0.5 - abs(q.x) * 0.95;
        d = min(e1 * 0.5, e2);
    } else {
        vec2 a = abs(p);
        d = 0.92 - max(a.x * 0.866 + a.y * 0.5, a.y);
    }
    float ripple = fract(param * 2.0);
    float r = length(p);
    float wave = ripple * pow(max(0.0, sin(r * 18.0 - time * 14.0)), 3.0) * exp(-r * 1.5);
    vec2 h = p * 6.0;
    vec2 hr = vec2(h.x * 1.1547, h.y + h.x * 0.57735);
    vec2 fc = fract(hr) - 0.5;
    float hex = smoothstep(0.38, 0.5, max(abs(fc.x), abs(fc.y)));
    float inside = smoothstep(0.0, 0.02, d);
    float rim = exp(-abs(d) * 30.0);
    float sheen = 0.5 + 0.5 * sin(p.x * 3.0 + p.y * 2.0 + time * 1.5);
    core = rim * 0.8 + wave * 0.6;
    float a = inside * (0.16 + hex * 0.22 + sheen * 0.08 + wave * 0.5) + rim * 0.95;
    return vec4(color.rgb, clamp(a, 0.0, 1.0));
}

float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * DepthRange.x * DepthRange.y / (DepthRange.y + DepthRange.x - z * (DepthRange.y - DepthRange.x));
}

void main() {
    vec2 p = uv * 2.0 - 1.0;
    float r = length(p);
    float alpha = 0.0;
    float core = 0.0;
    float seed = color.r * 7.0 + color.b * 13.0;
    float time = GameTime * 1200.0;
    vec3 base = color.rgb;

    if (shape == 0) {
        alpha = pow(max(0.0, 1.0 - r), 2.2);
        core = smoothstep(1.0 - param, 0.0, r) * param;
    } else if (shape == 1) {
        float w = max(0.02, param * 0.5);
        alpha = smoothstep(w, 0.0, abs(r - (1.0 - w))) * step(r, 1.0);
        core = alpha * alpha * 0.5;
    } else if (shape == 2) {
        float across = 1.0 - abs(p.y);
        float along = sin(uv.x * 3.14159);
        alpha = pow(max(0.0, across), 2.0) * pow(max(0.0, along), 0.6);
        core = pow(max(0.0, across), 8.0) * along * param;
    } else if (shape == 3) {
        float across = abs(p.y);
        alpha = pow(max(0.0, 1.0 - across), 1.6);
        core = smoothstep(max(0.02, param * 0.6), 0.0, across);
    } else if (shape == 4) {
        float n = fbm(p * 2.2 + vec2(seed, -time * 0.9));
        float body = 1.0 - r * (0.7 + 0.6 * n);
        alpha = smoothstep(param, param + 0.25, body);
        core = smoothstep(param + 0.35, param + 0.8, body) * (1.0 - param);
    } else if (shape == 5) {
        float n = fbm(p * 1.8 + vec2(seed, time * 0.15));
        float body = 1.0 - r * (0.75 + 0.5 * n);
        alpha = smoothstep(param * 0.8, param * 0.8 + 0.4, body);
    } else if (shape == 6) {
        float rays = max(2.0, floor(param * 32.0 + 0.5));
        float a = atan(p.y, p.x);
        float ray = pow(abs(cos(a * rays * 0.5)), 24.0) * pow(max(0.0, 1.0 - r), 1.5);
        alpha = pow(max(0.0, 1.0 - r), 3.0) + ray;
        core = smoothstep(0.35, 0.0, r);
    } else if (shape == 8) {
        alpha = smoothstep(1.0, 1.0 - max(0.02, param), r);
    } else if (shape == 9) {
        float edge = 1.0 / (1.0 + max(0.05, param));
        vec2 q = p / edge;
        float rr = length(q);
        float t = time * 0.12;
        // Seen on a ball: the surface pattern bunches up towards the rim and slides around.
        float z = sqrt(max(0.0, 1.0 - min(1.0, rr * rr)));
        vec2 sp = q / (0.35 + z);
        float n = fbm(sp * 2.6 + vec2(seed + t, -t * 0.6));
        float n2 = fbm(sp * 6.0 + vec2(-t * 1.4, seed * 2.0 + t));
        vec3 deep = vec3(0.88, 0.22, 0.02);
        vec3 mid = vec3(1.0, 0.52, 0.04);
        vec3 hot = vec3(1.0, 0.86, 0.32);
        vec3 surf = mix(deep, mid, smoothstep(0.3, 0.7, n));
        surf = mix(surf, hot, smoothstep(0.5, 0.85, n2) * 0.85);
        // Bright yellow streaks across it, like the reference's filaments.
        float streak = pow(abs(sin((sp.x * 0.8 + sp.y) * 4.0 + n * 7.0 + t * 2.0)), 14.0);
        surf = mix(surf, vec3(1.0, 0.92, 0.45), streak * 0.7);
        // White-hot in the middle, a glowing white-yellow rim.
        surf = mix(surf, vec3(1.0, 0.98, 0.88), smoothstep(0.6, 0.0, rr) * 0.95);
        surf = mix(surf, vec3(1.0, 0.95, 0.75), smoothstep(0.82, 1.0, rr) * 0.85);
        float disc = smoothstep(1.0, 0.985, rr);
        // Flames and loops of fire past the rim, moving.
        float a = atan(p.y, p.x);
        float fl = fbm(vec2(a * 2.5 + seed, r * 5.0 - time * 0.9));
        float fl2 = fbm(vec2(a * 6.0 - seed, r * 9.0 - time * 1.6));
        float reach = edge * (1.0 + (0.12 + 0.55 * fl * fl2) * max(0.05, param) * 2.2);
        float flame = smoothstep(reach, edge, r) * (1.0 - disc);
        vec3 flameColor = mix(vec3(1.0, 0.3, 0.04), vec3(1.0, 0.82, 0.35), pow(flame, 1.5));
        base = mix(flameColor, surf, disc) * color.rgb;
        alpha = max(disc, flame * 0.95);
    } else if (shape == 10) {
        // Infinite Void's black hole (the hole itself is a black disc drawn over it): a thin white-hot photon ring at the
        // hole's edge, then a disk of concentric blue and cyan rings swirling around it, the inner ones faster, torn
        // into streaks, and two faint outer rings. param: the hole's radius on the quad.
        float hole = max(0.05, param);
        float a = atan(p.y, p.x);
        float t = time * 0.05;
        float x = (r - hole) / (1.0 - hole);
        float spin = a + t * (1.6 / (0.25 + max(x, 0.0)));
        float n = fbm(vec2(spin * 1.2 + seed, x * 6.0));
        float n2 = fbm(vec2(spin * 3.0 - seed, x * 18.0 - t));
        float bands = 0.55 + 0.45 * sin(x * 38.0 + n * 5.0);
        float disk = smoothstep(0.0, 0.04, x) * pow(max(0.0, 1.0 - x), 1.4);
        float streaks = pow(n2, 2.0) * 1.6;
        float photon = exp(-pow((r - hole) / 0.012, 2.0));
        float glowIn = exp(-pow((r - hole) / 0.06, 2.0));
        float outer = exp(-pow((x - 0.55) / 0.05, 2.0)) * 0.5 + exp(-pow((x - 0.8) / 0.03, 2.0)) * 0.35;
        float body = disk * (0.35 + 0.65 * bands) * (0.5 + streaks) + outer * (0.6 + 0.4 * n);
        vec3 deep = vec3(0.1, 0.35, 0.85);
        vec3 cyan = vec3(0.6, 0.92, 1.0);
        base = mix(deep, cyan, clamp(body * 0.8 + glowIn, 0.0, 1.0)) * color.rgb;
        alpha = (body * 0.9 + photon * 1.5 + glowIn * 0.6) * step(hole * 0.97, r) * step(r, 1.0);
        core = photon + glowIn * 0.3;
    } else if (shape == 11) {
        // An ink splash: a lumpy blot with tendrils thrown out and droplets around it. param picks which one (0 to 1).
        float s = param * 37.0 + seed;
        float a = atan(p.y, p.x);
        float lumps = fbm(vec2(a * 1.6 + s, s * 0.3));
        float spikes = pow(fbm(vec2(a * 5.0 - s, s + 4.0)), 3.0) * 1.8;
        float edge = 0.36 + 0.25 * lumps + spikes * 0.45;
        float body = smoothstep(edge, edge - 0.04, r);
        vec2 g = p * 6.0 + s;
        vec2 f = fract(g) - 0.5;
        float h = hash(floor(g));
        float drop = step(0.82, h) * smoothstep(0.18 * h, 0.12 * h, length(f)) * smoothstep(1.0, 0.5, r) * step(edge, r);
        float tex = 0.85 + 0.15 * fbm(p * 5.0 + s);
        alpha = max(body * tex, drop);
    } else if (shape == 12) {
        // Time in steps of 12 a second, like a drawing: the burning edge boils instead of sliding.
        float tq = floor(time * 12.0) / 12.0;
        float e = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float front;
        if (param < 0.5) {
            float rise = param * 2.0;
            float jag = (fbm(vec2(uv.x * 7.0 + seed, tq * 1.7)) - 0.5) * 0.16 * (1.0 - smoothstep(0.85, 1.0, rise));
            front = rise * 1.08 + jag - uv.y;
        } else {
            float k = (param - 0.5) * 2.0;
            float a = atan(uv.y - 0.5, uv.x - 0.5);
            float jag = (fbm(vec2(a * 3.0 + seed, tq * 1.7)) - 0.5) * 0.08 * (1.0 - smoothstep(0.85, 1.0, k));
            front = k * 0.56 + jag - e;
        }
        float fw = max(fwidth(front), 1.0e-4);
        float cover = smoothstep(0.0, 1.5 * fw, front);
        float burn = 1.0 - smoothstep(0.0, 9.0 * fw, front);
        float n = fbm(uv * 5.0 + vec2(seed, tq * 0.35));
        float vein = (1.0 - smoothstep(0.0, 0.035, abs(n - 0.5))) * 0.3;
        float ew = max(fwidth(e), 1.0e-4);
        float rim = 1.0 - smoothstep(1.0, 6.0, e / ew);
        float rimHot = 1.0 - smoothstep(0.5, 2.5, e / ew);
        float rimSoft = exp(-e * 9.0) * 0.45;
        vec3 black = vec3(0.02, 0.012, 0.035);
        vec3 violet = vec3(0.55, 0.16, 1.0);
        vec3 lilac = vec3(0.86, 0.66, 1.0);
        vec3 c = black + violet * (vein + rimSoft);
        c = mix(c, violet, rim * 0.9);
        c = mix(c, lilac, rimHot * 0.8);
        c = mix(c, mix(violet, lilac, 0.5), burn * 0.9);
        base = c;
        alpha = cover * 0.97;
    } else if (shape == 13) {
        float hw = pow(max(0.0, 1.0 - uv.x), 0.9);
        float across = abs(p.y) / max(hw, 1.0e-3);
        if (param < 0.5) {
            float d = 1.0 - across;
            float fw = max(fwidth(d), 1.0e-4);
            alpha = smoothstep(0.0, 1.5 * fw, d) * step(uv.x, 0.995);
            float rim = 1.0 - smoothstep(1.0, 4.0, d / fw);
            base = mix(vec3(0.02, 0.012, 0.035), vec3(0.62, 0.25, 1.0), rim);
        } else {
            float soft = max(0.0, 1.0 - across * 0.5);
            alpha = pow(soft, 2.0) * smoothstep(0.0, 0.06, uv.x) * (1.0 - smoothstep(0.75, 1.0, uv.x));
            core = pow(soft, 8.0) * (1.0 - uv.x) * 0.5;
        }
    } else if (shape == 14) {
        // A shard of glass or eggshell (Kyoka Suigetsu's mirror, the Hogyoku cocoon): param < 0.5 a quad, else a
        // triangle (a, b, c); bright rim and a glint that slides across with fract(param * 2).
        float e = param >= 0.5 ? min(min(uv.y, 1.0 - uv.x), (uv.x - uv.y) * 0.7071) : min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        float ew = max(fwidth(e), 1.0e-4);
        float rim = 1.0 - smoothstep(0.5, 3.0, e / ew);
        float k = fract(param * 2.0);
        float glint = pow(max(0.0, 1.0 - abs(uv.x + uv.y - 0.6 - k) * 5.0), 3.0);
        base = mix(color.rgb, vec3(1.0), clamp(rim * 0.85 + glint * 0.5, 0.0, 1.0));
        alpha = (0.8 + 0.2 * rim) * step(0.0, e);
    } else if (shape == 15) {
        // A black butterfly wing (the Hogyoku evolution), root at u = 0 and tip at u = 1: veins, an eye spot and a
        // violet rim. param < 0.5 the dark body (fract(param * 2) varies the outline), else its glow.
        vec2 q = vec2(uv.x, uv.y - 0.5);
        float a = atan(q.y, max(q.x, 1.0e-4));
        float rr = length(q);
        float vr = fract(param * 2.0);
        float R = 0.97 * pow(max(0.0, cos(a)), 0.55) * (1.0 - 0.07 * pow(abs(sin(a * 5.0 + vr * 2.0)), 3.0));
        R = min(R, 0.5 / max(abs(sin(a)), 1.0e-3));
        float d = R - rr;
        float fw = max(fwidth(d), 1.0e-4);
        float body = smoothstep(0.0, 1.5 * fw, d);
        float rim = 1.0 - smoothstep(0.0, 0.07, d);
        float vein = pow(abs(cos(a * 8.0 + sin(rr * 6.0) * 0.4)), 30.0) * smoothstep(0.08, 0.35, rr);
        float eye = length((q - vec2(0.62, 0.06 * (vr - 0.5))) / vec2(1.0, 0.8));
        float eyeRing = 1.0 - smoothstep(0.0, 0.025, abs(eye - 0.11));
        float eyeIn = smoothstep(0.08, 0.05, eye);
        float n = fbm(q * 6.0 + vec2(seed, time * 0.2));
        if (param < 0.5) {
            vec3 black = vec3(0.02, 0.012, 0.035);
            vec3 deep = vec3(0.23, 0.06, 0.56);
            vec3 violet = vec3(0.55, 0.16, 1.0);
            vec3 lilac = vec3(0.86, 0.66, 1.0);
            vec3 c = mix(black, deep, smoothstep(0.3, 1.0, rr / max(R, 1.0e-3)) * 0.6 + n * 0.15);
            c = mix(c, violet, vein * 0.7);
            c = mix(c, violet, eyeRing);
            c = mix(c, lilac, eyeIn * 0.6);
            c = mix(c, lilac, rim * 0.85);
            base = c;
            alpha = body * 0.96;
        } else {
            alpha = body * (rim * 0.9 + vein * 0.5 + eyeRing * 0.7 + 0.08) + (1.0 - body) * exp(-max(0.0, -d) * 14.0) * 0.35;
            core = rim * body * 0.3;
        }
    } else if (shape == 16) {
        alpha = pow(max(0.0, 1.0 - r), 2.2);
        core = smoothstep(1.0 - param, 0.0, r) * param;
    } else if (shape == 17) {
        // Drawn on twos-and-threes like the anime: the outline boils 10 times a second instead of sliding.
        float tq = floor(time * 10.0) / 10.0;
        bool burst = fract(param * 2.0) >= 0.5;
        float d;
        if (!burst) {
            float x = (uv.x - 0.5) * 2.0;
            float y = uv.y;
            float n = fbm(vec2(x * 2.5 + seed, y * 3.0 - tq * 2.2));
            float n2 = fbm(vec2(x * 7.0 - seed, y * 5.0 - tq * 3.5));
            float width = 0.6 * pow(max(0.0, 1.0 - y), 0.65) * (0.7 + 0.6 * n) + 0.1 * (n2 - 0.5);
            d = width - abs(x) * (1.0 + 0.5 * y);
        } else {
            float a = atan(p.y, p.x);
            float n = fbm(vec2(a * 2.2 + seed, r * 2.0 - tq * 1.5));
            float n2 = fbm(vec2(a * 6.0 - seed, tq * 2.0));
            d = 0.7 + 0.3 * (n - 0.5) + 0.1 * (n2 - 0.5) - r;
        }
        float fw = max(fwidth(d), 1.0e-4);
        float inside = smoothstep(0.0, 1.5 * fw, d);
        float rimW = 0.075;
        float rim = inside * (1.0 - smoothstep(rimW * 0.55, rimW, d));
        float rimHot = inside * (1.0 - smoothstep(rimW * 0.15, rimW * 0.35, d));
        // A second, broken outline just outside the first: the light spilling out of the contour.
        float wisp = fbm(vec2(uv.x * 9.0 + seed, uv.y * 6.0 - tq * 4.0));
        float d2 = d + 0.1 + 0.08 * wisp;
        float spill = (1.0 - inside) * smoothstep(0.0, 1.5 * fw, d2) * (1.0 - smoothstep(0.03, 0.06, d2)) * step(0.45, wisp);
        vec2 g = uv * vec2(26.0, 38.0);
        float h = hash(floor(g) + vec2(seed, 3.0));
        float twinkle = 0.5 + 0.5 * sin(time * 5.0 + h * 40.0);
        float star = step(0.92, h) * smoothstep(0.38, 0.0, length(fract(g) - 0.5)) * twinkle * smoothstep(0.0, 0.12, d);
        vec3 black = vec3(0.04, 0.01, 0.08);
        vec3 deep = vec3(0.24, 0.05, 0.46);
        vec3 violet = vec3(0.66, 0.24, 1.0);
        vec3 pink = vec3(1.0, 0.7, 1.0);
        if (param < 0.5) {
            vec3 c = mix(deep, black, smoothstep(0.0, 0.3, d));
            c = mix(c, vec3(0.9, 0.75, 1.0), star);
            c = mix(c, violet, rim);
            c = mix(c, pink, rimHot);
            base = c;
            alpha = inside;
        } else {
            float outside = (1.0 - inside) * exp(-max(0.0, -d) * 7.0);
            alpha = rim + spill * 0.9 + outside * 0.55 + star * 0.5;
            core = rimHot * 0.7 + spill * 0.3;
        }
    } else if (shape == 18) {
        vec4 az = azul(p, param, seed, time);
        base = az.rgb;
        alpha = az.a;
    } else if (shape == 19) {
        vec4 vm = vermelhoRabo(uv, param, seed, time);
        base = vm.rgb;
        alpha = vm.a;
    } else if (shape == 20) {
        vec4 vm = vermelhoOrbe(p, param, seed, time);
        base = vm.rgb;
        alpha = vm.a;
    } else if (shape == 21) {
        vec4 rx = roxo(uv, param, seed, time, color.g, step(0.95, color.b));
        base = rx.rgb;
        alpha = rx.a;
    } else if (shape == 22) {
        vec4 rv = roxoVortex(p, param, seed, time);
        base = rv.rgb;
        alpha = rv.a;
    } else if (shape == 23) {
        vec4 ry = roxoRays(p, param, seed, time);
        base = ry.rgb;
        alpha = ry.a;
    } else if (shape == 24) {
        vec4 rr = roxoRaio(uv, param, seed, time);
        base = rr.rgb;
        alpha = rr.a;
    } else if (shape == 25) {
        vec4 pt = petal(p, param, color.rgb);
        base = pt.rgb;
        alpha = pt.a;
    } else if (shape == 26) {
        vec4 cr = crescent(p, param, seed, time, core);
        base = cr.rgb;
        alpha = cr.a;
    } else if (shape == 27) {
        vec4 sl = slit(p, uv, param, core);
        base = sl.rgb;
        alpha = sl.a;
    } else if (shape == 28) {
        vec4 ic = ice(uv, param, seed, time, core);
        base = ic.rgb;
        alpha = ic.a;
    } else if (shape == 29) {
        vec4 br = barrier(p, uv, param, time, core);
        base = br.rgb;
        alpha = br.a;
    } else if (shape == 30) {
        // Same text as the Visualizador's shape 32 (SAW).
        float ang = atan(p.y, p.x) + time * (8.0 + param * 40.0);
        float saw = fract(ang * 28.0 / 6.2831853);
        float R = 0.84 + 0.13 * saw;
        float d = R - r;
        float fw = max(fwidth(d), 1.0e-4);
        float fill = smoothstep(0.0, 1.2 * fw, d);
        vec3 lite = mix(color.rgb, vec3(1.0), 0.8);
        vec3 col = mix(lite, color.rgb, smoothstep(0.25, 0.7, r));
        float groove = (1.0 - smoothstep(0.0, 0.025 + fw, abs(r - 0.42))) * 0.6 + (1.0 - smoothstep(0.0, 0.02 + fw, abs(r - 0.7))) * 0.5;
        float marks = smoothstep(0.75, 0.95, noise(vec2(ang * 6.0, floor(r * 14.0)))) * smoothstep(0.2, 0.3, r) * step(r, 0.8);
        col = mix(col, color.rgb * 0.5, max(groove, marks * 0.5));
        col = mix(col, vec3(1.0), 1.0 - smoothstep(0.0, 0.05 + fw, d));
        base = col;
        alpha = fill * 0.96;
        core = 0.0;
    } else if (shape == 31) {
        // Same text as the Visualizador's shape 33 (GENKI).
        vec2 q = p * 2.0;
        float rr = length(q);
        float ang = atan(q.y, q.x);
        float sd = param * 31.0;
        vec3 deep = vec3(0.03, 0.12, 0.5);
        vec3 blue = vec3(0.1, 0.42, 1.0);
        vec3 cyan = vec3(0.38, 0.88, 1.0);
        float fw = max(fwidth(rr), 1.0e-4);
        float disc = smoothstep(0.0, 1.5 * fw, 1.0 - rr);
        vec3 inner = mix(deep, mix(blue, cyan, 0.5), 0.45 + 0.55 * (1.0 - rr));
        inner = mix(inner, vec3(1.0), smoothstep(0.45, 0.0, rr) * 0.6);
        inner = mix(inner, vec3(0.78, 0.96, 1.0), pow(rr, 8.0) * 0.85);
        float line = 1.0 - smoothstep(0.0, 2.5 * fw, abs(1.0 - rr));
        float frost = pow(1.0 - abs(fbm(vec2(ang * 7.0 + sd, rr * 5.0 - time * 2.5)) * 2.0 - 1.0), 10.0) * exp(-max(0.0, rr - 1.0) * 5.0) * step(1.0, rr);
        float halo = pow(max(0.0, 1.0 - r), 2.5) * 0.45;
        base = mix(mix(blue, cyan, frost), inner, disc);
        base = mix(base, vec3(1.0), line);
        alpha = max(disc * 0.8, line) + frost * 0.9 + halo;
        core = 0.0;
    } else if (shape == 32) {
        // Same text as the Visualizador's shape 34 (TONGUE), but life and seed come from the param byte.
        float pb = floor(param * 255.0 + 0.5);
        float vida = floor(pb / 16.0) / 16.0;
        float sd = mod(pb, 16.0) / 16.0 * 37.0 + 1.0;
        float tq = floor(time * 12.0) / 12.0;
        float y = uv.x;
        float x = uv.y * 2.0 - 1.0;
        float die = vida * vida;
        float fy = 0.4 + 0.2 * fract(sd * 0.37);
        float fk[4];
        for (int k = 0; k < 4; k++) {
            float off = k == 0 ? 0.0 : k == 1 ? 1.7 : k == 2 ? 3.9 : 6.3;
            float tt = tq * (k == 0 ? 1.0 : k == 1 ? 1.0 : k == 2 ? 1.06 : 1.12);
            float rg = k == 0 ? 1.0 : k == 1 ? 1.1 : k == 2 ? 1.25 : 1.4;
            float w = 0.92 * pow(sin(clamp(y, 0.0, 1.0) * 3.14159), 0.6) * (1.0 - 0.6 * y) + 0.04 * (1.0 - y);
            float c = (noise(vec2(y * 2.0 + sd, tt * 2.0)) - 0.5) * 1.5 * y * y + sin(y * 6.0 - tt * 3.0 + sd) * 0.25 * y * y;
            float s = 0.28 * smoothstep(fy, 1.0, y);
            float ax = mix(abs(x - c), min(abs(x - c - s), abs(x - c + s * 0.8)), smoothstep(fy, 1.0, y));
            vec2 wp = vec2(x * 2.0 + off, y * 3.0 - tt * 2.5 + sd);
            float n = fbm(wp + 0.6 * vec2(fbm(wp + 5.2), fbm(wp + 1.3)));
            float ridge = 1.0 - abs(2.0 * n - 1.0);
            float f = w - ax + (n - 0.5) * 0.5 * (0.4 + y) * rg - ridge * 0.14 * y * rg - 0.04;
            f -= smoothstep(0.85, 1.0, y) * 0.6 + die * 0.55 * (0.3 + y * 1.4);
            fk[k] = f;
        }
        float aa0 = max(fwidth(fk[0]) * 0.9, 1.0e-4);
        float aa1 = max(fwidth(fk[1]) * 0.9, 1.0e-4);
        float aa2 = max(fwidth(fk[2]) * 0.9, 1.0e-4);
        float aa3 = max(fwidth(fk[3]) * 0.9, 1.0e-4);
        float out0 = smoothstep(-aa0, aa0, fk[0]);
        float aro = smoothstep(-aa0, aa0, fk[0] - 0.035);
        float pele = smoothstep(-aa1, aa1, fk[1] - 0.07);
        float corpo = smoothstep(-aa2, aa2, fk[2] - 0.26);
        float miolo = smoothstep(-aa3, aa3, fk[3] - 0.5);
        vec3 cB = color.rgb * 0.15;
        vec3 cA = color.rgb * 0.55;
        vec3 cP = color.rgb;
        vec3 cC = mix(color.rgb, vec3(1.0), 0.45);
        vec3 cM = mix(color.rgb, vec3(1.0), 0.85);
        vec3 cT = color.rgb * 0.7;
        vec3 col = cB;
        col = mix(col, cA, aro);
        col = mix(col, cP, pele * aro);
        col = mix(col, cC, corpo * pele * aro);
        col = mix(col, cM, miolo * corpo * pele * aro);
        float sw = fbm(vec2(x * 1.5 + sd, y * 2.0 - tq * 0.8)) * 6.0;
        float linha = 1.0 - smoothstep(0.0, fwidth(sw) * 1.4, abs(fract(sw) - 0.5) - 0.06);
        col = mix(col, cT, linha * pele * aro * (1.0 - miolo) * (1.0 - corpo * 0.5) * step(0.15, y) * 0.8);
        float soot = step(0.93, hash(floor(vec2(x * 9.0, y * 13.0)) + floor(tq * 12.0))) * (1.0 - aro) * out0;
        col = mix(col, cB * 0.4, soot);
        base = col;
        alpha = out0;
        core = 0.0;
    } else if (shape == 33) {
        // Same text as the Visualizador's shape 28 (CEINK): cursed energy anime style (Itadori's ref), flat flame with a
        // thick black ink outline. param < 0.5 a flame tongue on a ribbon (uv.x from base to tip), else a blob round the
        // fist; fract(param * 2) = seed.
        float sd = fract(param * 2.0) * 37.0 + 1.0;
        float tq = floor(time * 12.0) / 12.0;
        float d;
        float wref;
        float lay;
        if (param < 0.5) {
            float u = uv.x;
            float v = uv.y * 2.0 - 1.0;
            float c = (fbm(vec2(u * 2.4 - tq * 2.4, sd)) - 0.5) * 0.55 * u + sin(u * 6.0 - tq * 8.0 + sd) * 0.12 * u;
            float w = 0.6 * smoothstep(0.0, 0.2, u) * pow(max(0.0, 1.0 - u), 0.8) * (0.75 + 0.45 * fbm(vec2(u * 4.0 - tq * 3.2, sd * 1.7)));
            d = w - abs(v - c);
            wref = 0.42;
            lay = fbm(vec2(u * 3.0 - tq * 2.0, v * 1.5 + sd));
        } else {
            vec2 cs = p / max(r, 1.0e-4);
            float R = 0.5 + 0.22 * fbm(cs * 1.6 + vec2(sd, tq * 1.4)) + 0.16 * max(0.0, p.y) * fbm(cs * 3.5 + vec2(-sd, tq * 2.6));
            d = R - r;
            wref = 0.55;
            lay = fbm(p * 2.0 + vec2(sd, -tq * 1.5));
        }
        float fw = max(fwidth(d), 1.0e-4);
        float ow = max(0.1 * wref / 0.45, 2.2 * fw);
        float outer = smoothstep(0.0, 1.5 * fw, d);
        float fill = smoothstep(ow, ow + 1.5 * fw, d);
        float t = clamp((d - ow) / (wref * 0.7), 0.0, 1.0);
        vec3 cdeep = color.rgb * vec3(0.35, 0.62, 0.78);
        vec3 clite = mix(color.rgb, vec3(1.0), 0.55);
        vec3 col = mix(cdeep, color.rgb, smoothstep(0.0, 0.25, t));
        col = mix(col, clite, smoothstep(0.35, 0.75, t));
        col = mix(col, vec3(1.0), smoothstep(0.75, 0.95, t) * 0.85);
        // An ink line inside (the flame's layers), in pieces.
        float ld = abs(d - ow - wref * (0.22 + 0.25 * lay));
        float inkIn = (1.0 - smoothstep(0.012, 0.012 + 1.5 * fw, ld)) * smoothstep(0.45, 0.6, lay);
        vec3 ink = vec3(0.01, 0.015, 0.03);
        base = mix(ink, col, fill * (1.0 - inkIn));
        alpha = outer;
        core = 0.0;
    } else if (shape == 35) {
        float along = uv.x;
        float across = uv.y * 2.0 - 1.0;
        float strands = fbm(vec2(across * 3.5, along * 2.5 - time * 0.6) + vec2(1.7, 4.1)) * 2.0 - 1.0;
        float k = smoothstep(0.0, 0.35, strands);
        float edge = 1.0 - smoothstep(0.55, 1.0, abs(across));
        float hot = exp(-across * across * 18.0) * (1.0 - along);
        alpha = (k * edge * 0.55 + hot * 0.5) * pow(max(0.0, 1.0 - along), 1.5) * smoothstep(0.0, 0.06, along);
        base = mix(color.rgb, vec3(1.0), 0.5);
        core = hot * 0.3;
    } else if (shape == 34) {
        // Same text as the Visualizador's shape 29 (BRUSH): the Divergent Fist's aura (Stark's ref). A flat opaque blot of
        // color with a black brush outline only in parts of the edge, scratchy ink hairs, one tip of flame on top; the
        // edge runs up both sides. param = seed. Drawn at 12 frames a second.
        float sd = param * 53.0 + 1.0;
        float tq = floor(time * 12.0) / 12.0;
        vec2 q = p - vec2(0.0, -0.3);
        float rq = length(q);
        float ang = atan(q.y, q.x);
        float v = abs(mod(ang + 1.5707963 + 3.1415926, 6.2831853) - 3.1415926);
        float side = sign(q.x + 1.0e-4);
        float flow = v * 2.2 - tq * 5.0;
        float lump = (fbm(vec2(flow * 0.6 + side * 7.0 + sd, tq * 0.5)) - 0.5) * 0.08;
        float licks = pow(fbm(vec2(flow * 3.0 + side * 13.0 + sd, 1.3)), 2.0) * 0.1;
        float lean = 0.12 * sin(tq * 2.3 + sd) + 0.06 * sin(tq * 5.1 + sd * 2.0);
        float da = ang - 1.5707963 - lean * (0.6 + rq);
        float tip = 0.5 * pow(max(0.0, 1.0 - abs(da) / 1.5), 2.2);
        float R = 0.4 * (1.0 + lump) + licks + tip;
        float d = R - rq;
        float fw = max(fwidth(d), 1.0e-4);
        float fill = smoothstep(0.0, 1.2 * fw, d);
        float th = 0.018 + 0.045 * pow(fbm(vec2(flow * 1.8 + side * 5.0 + sd * 2.3, 7.0)), 1.5);
        float gap = smoothstep(0.32, 0.46, fbm(vec2(flow * 1.2 + side * 3.0 + sd * 3.1, 3.0)));
        float off = (fbm(vec2(flow * 5.0 + side * 9.0 + sd, 5.0)) - 0.5) * 0.03;
        float hair = smoothstep(0.55, 0.8, noise(vec2(flow * 25.0 + sd, rq * 8.0))) * 0.6;
        float band = 1.0 - smoothstep(th * (1.0 + hair), th * (1.0 + hair) + 1.2 * fw, abs(d + off + th * 0.5));
        float ink = band * gap;
        // Half opaque body with glints travelling up through parts of it.
        float inside = smoothstep(0.0, 0.06, d);
        float blob = smoothstep(0.6, 0.78, fbm(vec2(q.x * 3.2 + sd, q.y * 2.6 - time * 1.5)));
        float fio = 1.0 - smoothstep(0.0, 0.035, abs(fbm(vec2(q.x * 2.2 - sd, q.y * 1.4 - time * 1.1)) - 0.5));
        fio *= smoothstep(0.5, 0.7, noise(vec2(q.x * 1.5 + sd * 0.7, q.y * 2.0 - time * 2.2)));
        float glow = clamp(blob + fio * 0.9, 0.0, 1.0) * inside;
        vec3 hot = mix(color.rgb, vec3(0.85, 1.0, 1.0), 0.7);
        vec3 body = mix(color.rgb, hot, glow);
        base = mix(body, vec3(0.08, 0.08, 0.09), ink);
        alpha = max(fill * (0.58 + 0.4 * glow), ink);
        core = glow * 0.35 * (1.0 - ink);
    } else {
        alpha = 1.0;
    }
    if (max(color.r, max(color.g, color.b)) < 0.15) {
        core = 0.0;
    }

    float fade = 1.0;
    // The Black Box's walls and lances stay solid right in front of whoever is inside, and shards, wings and orbs sit
    // right on bodies (no soft fade), and so does the Red's orb at Gojo's fingertip.
    if (SoftOn > 0.5 && (shape < 12 || shape > 16) && shape != 20 && shape != 21 && shape != 28 && shape != 33 && shape != 34) {
        float scene = linearDepth(texture(Sampler1, gl_FragCoord.xy / Screen).r);
        fade = clamp((scene - viewDist) / 0.6, 0.0, 1.0);
    }
    alpha = clamp(alpha, 0.0, 1.0) * color.a * fade;
    if (alpha < 0.003) {
        discard;
    }
    vec3 rgb = mix(base, vec3(1.0), clamp(core, 0.0, 1.0));
    fragColor = vec4(rgb, alpha);
}
