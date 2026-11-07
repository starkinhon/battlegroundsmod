#version 150

// Gojo's Expansão de 0,2 s (client/fx/VoidFlashScene; the same look as the Visualizador's scene gojo-dom02). Drawn over
// the finished world (the people are drawn again over it), only where the mask lets it through:
// Mode 1: a spiky black blot round Gojo (XCenter, BlotSize pixels). Mode 2: the whole screen. Mode 3: an X through
// XCenter whose arms (half width ArmWidth pixels, at Angles) thin away. Inside is the void of our own Expansão
// (DomainScene.drawVoid and overlay): stars and blue haze on every side, the galaxy band through the black hole (the
// souls_vfx shape 10 rings, HoleCenter, HoleSize pixels), sparks spiralling in, Stark's black ink (Sampler0, the 12 splashes
// of textures/fx/gojo_ink in a 4x3 atlas) orbiting and falling into it, his white ink drifting by, light dust, and the
// still water floor (FloorDepth below the camera) glowing blue, with wide rings, ripples under Gojo and his target, and
// the hole mirrored in it. Fade lights it up, Grow is the hole's opening.
uniform sampler2D Sampler0;
uniform vec2 Screen;
uniform vec2 XCenter;
uniform vec2 HoleCenter;
uniform float HoleSize;
uniform float Horizon;
uniform float Mode;
uniform float BlotSize;
uniform float Fade;
uniform float Grow;
uniform float ArmWidth;
uniform vec2 Angles;
uniform float Now;
uniform vec3 CamFwd;
uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec2 TanHalf;
uniform float FloorDepth;
uniform vec2 CasterRel;
uniform vec2 TargetRel;

in vec2 uv;
out vec4 fragColor;

const vec3 RING = vec3(0.56, 0.9, 1.0);
const vec3 HAZE = vec3(0.18, 0.36, 1.0);
const vec3 SILVER = vec3(0.78, 0.82, 0.9);
const vec3 WHITE = vec3(1.0);

float vfHash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vfH(float a, float b) {
    return vfHash3(vec3(a, b, a * 0.37 + 1.3));
}

float vfHash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vfNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(vfHash2(i), vfHash2(i + vec2(1.0, 0.0)), f.x), mix(vfHash2(i + vec2(0.0, 1.0)), vfHash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

float vfFbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += vfNoise2(p) * a;
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float vfNoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(vfHash3(i), vfHash3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(vfHash3(i + vec3(0.0, 1.0, 0.0)), vfHash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(vfHash3(i + vec3(0.0, 0.0, 1.0)), vfHash3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(vfHash3(i + vec3(0.0, 1.0, 1.0)), vfHash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float vfFbm3(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * vfNoise3(p);
        p = p * 2.03 + 1.7;
        a *= 0.5;
    }
    return s;
}

// souls_vfx shape 10 (the domain's black hole), its color already times its alpha.
vec3 vfHole(vec2 p, float hole, float time, float seed, vec3 color) {
    float r = length(p);
    if (r > 1.0) {
        return vec3(0.0);
    }
    float a = atan(p.y, p.x);
    float t = time * 0.05;
    float x = (r - hole) / (1.0 - hole);
    float spin = a + t * (1.6 / (0.25 + max(x, 0.0)));
    float n = vfFbm2(vec2(spin * 1.2 + seed, x * 6.0));
    float n2 = vfFbm2(vec2(spin * 3.0 - seed, x * 18.0 - t));
    float bands = 0.55 + 0.45 * sin(x * 38.0 + n * 5.0);
    float disk = smoothstep(0.0, 0.04, x) * pow(max(0.0, 1.0 - x), 1.4);
    float streaks = pow(n2, 2.0) * 1.6;
    float photon = exp(-pow((r - hole) / 0.012, 2.0));
    float glowIn = exp(-pow((r - hole) / 0.06, 2.0));
    float outer = exp(-pow((x - 0.55) / 0.05, 2.0)) * 0.5 + exp(-pow((x - 0.8) / 0.03, 2.0)) * 0.35;
    float body = disk * (0.35 + 0.65 * bands) * (0.5 + streaks) + outer * (0.6 + 0.4 * n);
    vec3 base = mix(vec3(0.1, 0.35, 0.85), vec3(0.6, 0.92, 1.0), clamp(body * 0.8 + glowIn, 0.0, 1.0)) * color;
    float alpha = clamp((body * 0.9 + photon * 1.5 + glowIn * 0.6) * step(hole * 0.97, r), 0.0, 1.0);
    return mix(base, WHITE, clamp(photon + glowIn * 0.3, 0.0, 1.0)) * alpha;
}

// One of Stark's 12 ink splashes: q from -1 to 1, turned by ang.
float vfInk(vec2 q, float ang, float k) {
    float c = cos(ang);
    float s = sin(ang);
    vec2 t = vec2(c * q.x - s * q.y, s * q.x + c * q.y) * 0.5 + 0.5;
    if (t.x < 0.0 || t.y < 0.0 || t.x > 1.0 || t.y > 1.0) {
        return 0.0;
    }
    float kk = mod(floor(k), 12.0);
    vec2 cell = vec2(mod(kk, 4.0), floor(kk / 4.0));
    return texture(Sampler0, (cell + vec2(t.x, 1.0 - t.y)) / vec2(4.0, 3.0)).a;
}

float vfSoft(float r) {
    return pow(max(0.0, 1.0 - r), 2.0);
}

float vfSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = uv * Screen;
    vec2 q = p - XCenter;
    if (Mode < 0.5) {
        discard;
    }
    if (Mode < 1.5) {
        float an = atan(q.y, q.x);
        float s = 1.0 - abs(fract(an / 6.2831853 * 15.0 + 0.3 * sin(an * 3.0)) * 2.0 - 1.0);
        if (length(q) > BlotSize * (0.62 + 0.55 * pow(s, 2.5) * (0.7 + 0.3 * sin(an * 5.0 + 1.0)))) {
            discard;
        }
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    if (Mode > 2.5) {
        float d1 = abs(dot(q, vec2(-sin(Angles.x), cos(Angles.x))));
        float d2 = abs(dot(q, vec2(-sin(Angles.y), cos(Angles.y))));
        if (min(d1, d2) > ArmWidth) {
            discard;
        }
    }
    float now = Now;
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 dir = normalize(CamFwd + CamRight * ndc.x * TanHalf.x + CamUp * ndc.y * TanHalf.y);
    vec3 col = vec3(0.004, 0.006, 0.016);
    // the sky: wide faint clouds of blue and silver haze, and the stars (a few big ones, twinkling)
    float cl = vfFbm3(dir * 1.6 + 3.0);
    float cl2 = vfFbm3(dir * 3.1 - 5.0);
    col += HAZE * smoothstep(0.42, 0.85, cl) * 0.16 + SILVER * smoothstep(0.55, 0.9, cl2) * 0.06;
    vec3 sp = dir * 120.0;
    vec3 id = floor(sp);
    float st = vfHash3(id);
    float dd = length(fract(sp) - 0.5 - (vec3(vfHash3(id + 1.1), vfHash3(id + 2.3), vfHash3(id + 3.7)) - 0.5) * 0.5);
    float tw = 0.45 + 0.55 * sin(now * (0.06 + 0.12 * vfHash3(id + 5.0)) + st * 60.0);
    col += (vfHash3(id + 7.0) < 0.25 ? SILVER : WHITE) * step(0.972, st) * smoothstep(0.22 + 0.2 * vfHash3(id + 9.0), 0.0, dd) * tw;
    vec3 bp = dir * 22.0;
    vec3 bid = floor(bp);
    float bs = vfHash3(bid + 11.0);
    float bd = length(fract(bp) - 0.5);
    col += WHITE * step(0.994, bs) * (smoothstep(0.35, 0.0, bd) * 0.5 + smoothstep(0.05, 0.0, bd)) * (0.5 + 0.5 * sin(now * 0.08 + bs * 40.0));
    // the black hole, on the screen where the reference had its splash
    float R = HoleSize * Grow;
    vec2 hq = (p - HoleCenter) / R;
    float hr = length(hq);
    // the galaxy band through it, side to side
    float m = clamp(hq.x / 4.0, -1.0, 1.0);
    float fade = (1.0 - m * m) * (1.0 - m * m);
    float ac = hq.y;
    col += SILVER * exp(-pow(ac / (0.12 + 0.12 * fade), 2.0)) * 0.16 * fade
            + HAZE * exp(-pow(ac / (0.27 + 0.27 * fade), 2.0)) * 0.12 * fade
            + HAZE * exp(-pow(ac / (0.42 + 0.4 * fade), 2.0)) * 0.08 * fade;
    // dust of light hanging around
    vec3 dp = dir * 9.0 + vec3(sin(now * 0.01), sin(now * 0.013), cos(now * 0.011)) * 0.05;
    vec3 did = floor(dp);
    float ds = vfHash3(did + 21.0);
    col += (ds > 0.66 ? RING : WHITE) * step(0.93, ds) * smoothstep(0.06, 0.0, length(fract(dp) - 0.5)) * (0.4 + 0.6 * max(0.0, sin(now * 0.08 + ds * 50.0))) * 0.8;
    // the floor: a dark mirror of water, a faint blue glow fading into the depth, wide rings, the ripples of the steps
    if (dir.y < -0.002) {
        vec2 P = dir.xz * (FloorDepth / -dir.y);
        float dg = length(P - CasterRel);
        float rr = 40.0;
        col += HAZE * vfSoft(dg / (rr * 1.3)) * 0.16 + SILVER * vfSoft(dg / (rr * 0.6)) * 0.05;
        for (int k = 0; k < 4; k++) {
            float u = fract(now * 0.004 + float(k) / 4.0);
            col += SILVER * exp(-pow((dg - rr * 1.2 * u) / (0.04 * rr * 1.2), 2.0)) * 0.08 * (1.0 - u) * min(1.0, u * 6.0);
        }
        for (int b = 0; b < 2; b++) {
            vec2 at = b == 0 ? CasterRel : TargetRel;
            float age = mod(now + float(b) * 17.0, 40.0);
            float u0 = age / 40.0;
            float rad = 2.6 * (1.0 - pow(1.0 - u0, 3.0));
            float d = length(P - at);
            col += WHITE * exp(-pow((d - rad) / max(0.03, 0.06 * rad), 2.0)) * 0.7 * (1.0 - u0) * (1.0 - u0)
                    + SILVER * vfSoft(d) * 0.25 * (1.0 - min(1.0, age / 20.0));
        }
        // the hole mirrored in the water, as far below the horizon as it is above
        vec2 mq = (vec2(p.x, 2.0 * Horizon - p.y) - HoleCenter) / R;
        col += vfHole(mq, 0.27, -now, 3.0, RING) * 0.22 * smoothstep(0.0, 0.1, -dir.y);
    }
    // Stark's white ink drifting through the void
    for (int k = 0; k < 9; k++) {
        float fk = float(k);
        float life = 160.0 + 120.0 * vfH(fk, 1.0);
        float cyc = (now + vfH(fk, 2.0) * life) / life;
        float rd = floor(cyc);
        float u = cyc - rd;
        vec2 c = HoleCenter + vec2((vfH(fk + rd * 13.0, 3.0) - 0.5) * 2.6, (vfH(fk + rd * 13.0, 4.0) - 0.45) * 1.5) * Screen.y
                + vec2(u * 0.25 * (vfH(fk, 8.0) - 0.5), u * 0.08) * Screen.y;
        float sz = (0.05 + 0.12 * vfH(fk + rd * 13.0, 5.0)) * Screen.y;
        float fa = smoothstep(0.0, 0.15, u) * (1.0 - smoothstep(0.7, 1.0, u));
        col = mix(col, WHITE, vfInk((p - c) / sz, vfH(fk, 6.0) * 6.0 + u * 0.6, fk + rd) * 0.9 * fa);
    }
    // the hole's glows, its turning rings, the white ring inside and the sparks spiralling in
    col += HAZE * vfSoft(hr / 2.4) * 0.35 + RING * vfSoft(hr / 1.3) * 0.2;
    col += vfHole(hq, 0.27, now, 5.0, RING);
    float ra = now * 0.01;
    vec2 hq2 = mat2(cos(ra), -sin(ra), sin(ra), cos(ra)) * hq / 0.62;
    col += vfHole(hq2, 0.44, now, 9.0, WHITE) * 0.4;
    if (hr < 1.2) {
        for (int k = 0; k < 48; k++) {
            float fk = float(k);
            float life = 30.0 + 40.0 * vfH(fk, 11.0);
            float cyc = (now + vfH(fk, 12.0) * life) / life;
            float rd = floor(cyc);
            float u = cyc - rd;
            float lane = vfH(fk + rd * 7.0, 13.0) * 6.2831853;
            float r0 = 0.14 + 0.5 * u;
            float a0 = lane + (1.0 - u) * 3.2;
            float u1 = max(0.0, u - 0.05);
            float r1 = 0.14 + 0.5 * u1;
            float a1 = lane + (1.0 - u1) * 3.2;
            vec2 p0 = vec2(cos(a0) * r0, sin(a0) * r0 * 0.85);
            vec2 p1 = vec2(cos(a1) * r1, sin(a1) * r1 * 0.85);
            float w = (0.25 + 0.4 * vfH(fk, 14.0)) / 70.0;
            col += (mod(fk, 4.0) < 0.5 ? RING : WHITE) * smoothstep(w, 0.0, vfSeg(hq, p0, p1)) * sin(u * 3.14159);
        }
    }
    // over the glow: the black hole itself and the black ink orbiting and falling into it
    col = mix(col, vec3(0.0), smoothstep(0.27, 0.27 * 0.88, hr));
    for (int k = 0; k < 14; k++) {
        float fk = float(k);
        float life = 90.0 + 80.0 * vfH(fk, 21.0);
        float cyc = (now + vfH(fk, 22.0) * life) / life;
        float rd = floor(cyc);
        float u = cyc - rd;
        float ang = vfH(fk + rd * 5.0, 23.0) * 6.2831853 + u * 2.6;
        float rr2 = 0.5 - 0.36 * u * u;
        vec2 c = vec2(cos(ang) * rr2, sin(ang) * rr2 * 0.8);
        float s = (0.07 + 0.12 * vfH(fk + rd * 5.0, 24.0)) * (1.0 - 0.6 * u);
        float fa = min(1.0, u * 6.0) * min(1.0, (1.0 - u) * 5.0);
        col = mix(col, vec3(0.0), vfInk((hq - c) / s, ang + u, fk * 3.0 + rd) * 0.95 * fa);
    }
    fragColor = vec4(col * Fade, 1.0);
}
