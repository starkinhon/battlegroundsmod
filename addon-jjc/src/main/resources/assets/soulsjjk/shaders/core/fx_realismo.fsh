#version 150

// Realismo: volumes in the world (tornado, fire, smoke, fireball, lava, wind) drawn by one screen pass over the
// finished frame. The technique was learned from Orbital Laser (Stink-O, MIT): every pixel rebuilds where it is in the
// world from the depth buffer, each view ray is marched only through the bounds of each volume (stopping at the
// scene's depth, so walls and hills hide what is behind them), density and temperature come from noise anchored in
// the world, light is gathered front to back (Beer-Lambert), and the volumes are lit by the game's own sun, moon and sky
// colours, so they sit in whatever the world looks like, a shader pack's picture included.
//
// Each volume is one mat4, column by column (client/fx/Realismo.java):
//   0: its foot or middle, camera-relative world (xyz), radius
//   1: height, age in seconds, life in seconds, type (0 tornado, 1 fire, 2 smoke, 3 fireball, 4 lava, 5 wind)
//   2: direction (wind, lean, travel; xyz, world), intensity
//   3: tint (rgb, 1 1 1 the natural colour), seed
// Volumes arrive far to near, so each is laid over what is behind it.
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseProj;
uniform mat4 ViewToWorld;
uniform float DepthOn;
uniform float Count;
uniform float Quality;
uniform float Frame;
uniform vec3 SunDir;
uniform vec3 SkyColor;
uniform float Rain;
uniform mat4 Vol0;
uniform mat4 Vol1;
uniform mat4 Vol2;
uniform mat4 Vol3;
uniform mat4 Vol4;
uniform mat4 Vol5;
uniform mat4 Vol6;
uniform mat4 Vol7;

in vec2 uv;
out vec4 fragColor;

// ---- noise: gradient noise (never value noise: its lattice shows as straight cuts at any sharp threshold) ----------
vec3 rzGrad(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float rzNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float n000 = dot(rzGrad(i), f);
    float n100 = dot(rzGrad(i + vec3(1.0, 0.0, 0.0)), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(rzGrad(i + vec3(0.0, 1.0, 0.0)), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(rzGrad(i + vec3(1.0, 1.0, 0.0)), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(rzGrad(i + vec3(0.0, 0.0, 1.0)), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(rzGrad(i + vec3(1.0, 0.0, 1.0)), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(rzGrad(i + vec3(0.0, 1.0, 1.0)), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(rzGrad(i + vec3(1.0, 1.0, 1.0)), f - vec3(1.0, 1.0, 1.0));
    float x0 = mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y);
    float x1 = mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y);
    return mix(x0, x1, u.z) * 0.85 + 0.5;
}

float rzFbm(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * rzNoise(p);
        norm += amp;
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return sum / norm;
}

// Cell noise on the plane: x the distance to the nearest point, y to the second (the cracks run where they meet).
vec2 rzCells(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = fract(sin(vec2(dot(i + g, vec2(127.1, 311.7)), dot(i + g, vec2(269.5, 183.3)))) * 43758.5453);
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

// A dither that changes from pixel to pixel without clumping: hides the steps of a march.
float rzDither(vec2 px) {
    return fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
}

// What something glowing looks like at temperature t (0 cold, 1 white-hot): dark red, orange, gold, white.
vec3 rzHeat(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c = mix(vec3(0.02, 0.0, 0.0), vec3(0.75, 0.06, 0.0), smoothstep(0.0, 0.3, t));
    c = mix(c, vec3(1.0, 0.38, 0.03), smoothstep(0.25, 0.55, t));
    c = mix(c, vec3(1.0, 0.82, 0.35), smoothstep(0.5, 0.8, t));
    return mix(c, vec3(1.0, 0.97, 0.9), smoothstep(0.8, 1.0, t));
}

vec2 rzRot(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// How much of a volume there is over its life: in over the first moment, out over the last quarter.
float rzEnvelope(float age, float life) {
    return smoothstep(0.0, min(0.6, life * 0.15), age) * (1.0 - smoothstep(life * 0.75, life, age));
}

// Ray against a vertical cylinder (foot c, radius r, height h): entry and exit distances (exit < entry: missed).
vec2 rzCylinder(vec3 ro, vec3 rd, vec3 c, float r, float h) {
    vec3 oc = ro - c;
    float a = dot(rd.xz, rd.xz);
    float b = dot(oc.xz, rd.xz);
    float k = dot(oc.xz, oc.xz) - r * r;
    float t0 = -1.0e9;
    float t1 = 1.0e9;
    if (a > 1.0e-8) {
        float disc = b * b - a * k;
        if (disc < 0.0) {
            return vec2(1.0, -1.0);
        }
        float sq = sqrt(disc);
        t0 = (-b - sq) / a;
        t1 = (-b + sq) / a;
    } else if (k > 0.0) {
        return vec2(1.0, -1.0);
    }
    if (abs(rd.y) > 1.0e-6) {
        float ya = (c.y - ro.y) / rd.y;
        float yb = (c.y + h - ro.y) / rd.y;
        t0 = max(t0, min(ya, yb));
        t1 = min(t1, max(ya, yb));
    } else if (oc.y > 0.0 || oc.y < -h) {
        return vec2(1.0, -1.0);
    }
    return vec2(max(t0, 0.0), t1);
}

vec2 rzSphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float k = dot(oc, oc) - r * r;
    float h = b * b - k;
    if (h < 0.0) {
        return vec2(1.0, -1.0);
    }
    float sq = sqrt(h);
    return vec2(max(-b - sq, 0.0), -b + sq);
}

// ---- the light of the scene, worked out once per pixel --------------------------------------------------------------
vec3 gSun;      // towards the sun (or the moon at night)
vec3 gSunCol;
vec3 gAmb;

void rzLight() {
    float up = SunDir.y;
    gSun = normalize((up > -0.05 ? SunDir : -SunDir) + vec3(0.0, 0.03, 0.0));
    float day = clamp(up * 2.5 + 0.35, 0.0, 1.0);
    float sunUp = smoothstep(-0.12, 0.06, up);
    float warm = (1.0 - smoothstep(0.02, 0.4, up)) * smoothstep(-0.2, -0.02, up);
    float moon = smoothstep(-0.02, -0.2, up);
    vec3 sky = pow(clamp(SkyColor, 0.0, 1.0), vec3(2.2));
    gAmb = mix(vec3(0.02, 0.025, 0.035), sky * 0.55 + vec3(0.03), day) * (1.0 - 0.35 * Rain);
    gSunCol = (mix(vec3(1.25, 1.18, 1.05), vec3(1.4, 0.62, 0.3), warm) * sunUp + vec3(0.05, 0.07, 0.11) * moon) * (1.0 - 0.6 * Rain);
}

// Forward scattering: a cloud is brightest looking towards the sun through its edge.
float rzPhase(vec3 rd) {
    return 0.75 + 0.6 * pow(max(dot(rd, gSun), 0.0), 6.0);
}

// ---- the volumes: each returns premultiplied colour (rgb) and how much of what is behind still shows (a) -------------
// p is camera-relative; q is p from the volume's foot.

// 0 tornado: a funnel hanging from a turning cloud, narrow at the land and flaring into a cone, its axis bending in a
// slow S that writhes as it turns, wound with striations that spiral up it (faster near the ground), a squat cloud of
// dust whirling round its foot.
vec3 tornadoAxis(float s, float h, float r, float t, float seed, vec3 lean) {
    vec2 side = normalize(vec2(-lean.z, lean.x) + vec2(1.0e-3, 0.0));
    float amp = r * 0.35 * sin(3.14159 * s);
    vec2 w = side * sin(s * 5.0 - t * 0.9 + seed) * amp + vec2(side.y, -side.x) * cos(s * 3.0 - t * 0.6 + seed * 1.3) * amp * 0.6;
    return vec3(w.x + lean.x * r * 0.8 * s, 0.0, w.y + lean.z * r * 0.8 * s);
}

vec4 tornadoDen(vec3 q, float r, float h, float t, float seed, vec3 lean) {
    float s = clamp(q.y / h, 0.0, 1.0);
    vec3 ax = tornadoAxis(s, h, r, t, seed, lean);
    vec2 e = q.xz - ax.xz;
    float d = length(e);
    float th = atan(e.y, e.x);
    float spin = mix(9.0, 2.5, s);
    float thr = th - spin * t - seed;
    float stri = 0.5 + 0.5 * sin(6.0 * thr + q.y * 0.55);
    float n = rzNoise(vec3(cos(thr) * 2.0, sin(thr) * 2.0, q.y * 0.09 - t * 1.4) + vec3(seed));
    float foot = max(r * 0.16, 0.9);
    // (it lowers out of the cloud over its first second and a half, its tip a narrowing point)
    float low = 1.0 - smoothstep(0.0, 1.5, t);
    float tip = smoothstep(low - 0.02, low + 0.3, s);
    float rs = mix(foot, r, pow(s, 2.2)) * mix(0.25, 1.0, tip) * (0.9 + 0.12 * stri + 0.18 * (n - 0.5));
    float vis = smoothstep(low - 0.03, low + 0.03, s);
    float fun = smoothstep(rs, rs * 0.75, d) * (0.75 + 0.25 * stri) * vis;
    // (the dust whirled round its foot: squat, ragged, wider than the funnel, turning fast)
    float dust = 0.0;
    float rd0 = max(3.0, r * 0.7);
    if (q.y < rd0 * 3.0) {
        vec2 f = q.xz - tornadoAxis(0.0, h, r, t, seed, lean).xz;
        float dd = length(f);
        float thd = atan(f.y, f.x) - 7.0 * t - seed;
        float rr = rd0 * (0.8 + 0.45 * rzNoise(vec3(cos(thd) * 1.8, sin(thd) * 1.8, q.y * 0.12 - t * 0.6 + seed)));
        float top = rd0 * 0.8 + rd0 * 1.6 * smoothstep(rr, 0.0, dd);
        dust = smoothstep(rr, rr * 0.45, dd) * smoothstep(top, top * 0.3, q.y) * smoothstep(-1.5, 1.0, q.y);
        dust *= (0.55 + 0.45 * sin(3.0 * thd + q.y * 0.4)) * 1.7 * smoothstep(0.6, 1.5, t);
    }
    // (the turning cloud it hangs from: a flat dark deck, thickest over the funnel)
    float deck = 0.0;
    float dy = q.y - h;
    if (dy > -r * 0.6) {
        float rr = length(q.xz - ax.xz);
        float ang = atan(q.z - ax.z, q.x - ax.x) - t * 0.35;
        float nd = rzFbm(vec3(cos(ang) * rr * 0.08, sin(ang) * rr * 0.08, dy * 0.12 + t * 0.05) + vec3(seed), int(Quality) + 1);
        float thick = r * (0.5 + 0.6 * smoothstep(r * 3.2, 0.0, rr));
        deck = smoothstep(r * 3.2, r * 1.5, rr) * smoothstep(-r * 0.6, 0.0, dy) * smoothstep(thick, thick * 0.4, dy);
        deck *= smoothstep(0.35, 0.6, nd) * smoothstep(0.0, 1.0, t + 1.0);
    }
    return vec4(fun, dust, deck, clamp(d / max(rs, 0.5), 0.0, 1.0));
}

vec4 marchTornado(vec3 ro, vec3 rd, float sceneLen, mat4 v, float jit, int steps) {
    vec3 c = v[0].xyz;
    float r = v[0].w;
    float h = v[1].x;
    float t = v[1].y;
    float env = rzEnvelope(t, v[1].z);
    vec3 lean = v[2].xyz;
    float seed = v[3].w;
    vec2 seg = rzCylinder(ro, rd, c, r * 3.4 + 2.0, h + r * 1.2);
    seg.y = min(seg.y, sceneLen);
    if (seg.y <= seg.x || env <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float dt = (seg.y - seg.x) / float(steps);
    vec3 acc = vec3(0.0);
    float tr = 1.0;
    vec3 Lxz = normalize(vec3(gSun.x, 0.0, gSun.z) + vec3(1.0e-3, 0.0, 0.0));
    float ph = rzPhase(rd);
    for (int i = 0; i < 64; i++) {
        if (i >= steps || tr < 0.03) {
            break;
        }
        float ts = seg.x + (float(i) + jit) * dt;
        vec3 q = ro + rd * ts - c;
        vec4 den = tornadoDen(q, r, h, t, seed, lean) * env;
        float total = den.x + den.y + den.z;
        if (total < 0.002) {
            continue;
        }
        float s = clamp(q.y / h, 0.0, 1.0);
        vec3 ax = tornadoAxis(s, h, r, t, seed, lean);
        vec3 nrm = normalize(vec3(q.x - ax.x, 0.0, q.z - ax.z) + vec3(0.0, 1.0e-3, 0.0));
        float lit = 0.55 + 0.45 * dot(nrm, Lxz);
        float rim = smoothstep(0.55, 1.0, den.w);
        vec3 funnel = mix(vec3(0.16, 0.18, 0.18), vec3(0.3, 0.31, 0.32), s * 0.5 + 0.5 * rim);
        vec3 dust = vec3(0.36, 0.3, 0.22);
        vec3 deck = mix(vec3(0.12, 0.13, 0.13), vec3(0.3, 0.31, 0.32), smoothstep(0.0, r, q.y - h));
        vec3 alb = (funnel * den.x + dust * den.y + deck * den.z) / total * v[3].rgb;
        vec3 col = alb * (gAmb * (0.75 + 0.6 * lit) + gSunCol * 0.35 * lit * ph);
        float a = 1.0 - exp(-total * 0.9 * dt);
        acc += tr * a * col;
        tr *= 1.0 - a;
    }
    return vec4(acc, tr);
}

// 1 fire: a blaze that narrows as it climbs, its tongues carried up through noise that turns slowly round its axis,
// white-hot at its root and cooling through gold and orange to dark red, turning to smoke over its top.
vec4 marchFire(vec3 ro, vec3 rd, float sceneLen, mat4 v, float jit, int steps, int oct) {
    vec3 c = v[0].xyz;
    float r = v[0].w;
    float h = v[1].x;
    float t = v[1].y;
    float env = rzEnvelope(t, v[1].z);
    float I = v[2].w;
    vec3 wind = v[2].xyz;
    float seed = v[3].w;
    vec2 seg = rzCylinder(ro, rd, c, r * 1.4 + length(wind.xz) * h * 0.3 + 0.5, h * 1.9);
    seg.y = min(seg.y, sceneLen);
    if (seg.y <= seg.x || env <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float dt = (seg.y - seg.x) / float(steps);
    vec3 acc = vec3(0.0);
    float tr = 1.0;
    float ph = rzPhase(rd);
    for (int i = 0; i < 64; i++) {
        if (i >= steps || tr < 0.03) {
            break;
        }
        float ts = seg.x + (float(i) + jit) * dt;
        vec3 q = ro + rd * ts - c;
        float s = q.y / h;
        // (the wind bends it over as it climbs)
        q.xz -= wind.xz * h * 0.3 * s * s;
        float rr = length(q.xz) / r;
        float width = max(1.0 - 0.8 * clamp(s, 0.0, 1.0), 0.08);
        float coarse = 1.0 - rr / width - max(s - 0.85, 0.0) * 3.0;
        float smokeZone = smoothstep(0.65, 1.0, s) * (1.0 - smoothstep(1.6, 1.9, s));
        if ((coarse < -1.0 && smokeZone < 0.01) || q.y < -0.5) {
            continue;
        }
        vec2 sw = rzRot(q.xz, q.y * 0.15 / max(r, 0.5) + t * 0.8);
        vec3 np = vec3(sw.x / r * 1.6, q.y / r - t * 2.2, sw.y / r * 1.6) + vec3(seed);
        float nz = rzFbm(np, oct);
        float body = coarse + (nz - 0.5) * 1.9;
        float dens = clamp(body * 1.5, 0.0, 1.0) * smoothstep(-0.5, 0.5, q.y) * (1.0 - smoothstep(0.9, 1.25, s));
        float temp = clamp(body * 0.75 + 0.3 - s * 0.6, 0.0, 1.0);
        // (light comes from how hot it is: the cool tips barely glow and darken into soot)
        vec3 emit = rzHeat(temp) * v[3].rgb * 1.8 * I * (0.15 + 0.85 * temp * temp);
        // (the smoke over it: dark, widening, lit from below by the fire)
        float sr = length(q.xz) / (r * (0.6 + 1.2 * max(s - 0.65, 0.0)));
        float sn = rzFbm(np * 0.6 + vec3(0.0, -t * 0.3, 0.0), oct);
        float smoke = smokeZone * clamp((sn - 0.42) * 3.0 * (1.0 - sr), 0.0, 1.0) * 0.6;
        vec3 smokeCol = vec3(0.09, 0.085, 0.08) * (0.5 + sn) * (gAmb * 3.0 + gSunCol * 0.5 * ph) + rzHeat(0.5) * v[3].rgb * 0.15 * (1.0 - smoothstep(1.0, 1.4, s));
        float k = dt / max(r * 0.35, 0.3);
        // (the flame adds its light and hides little behind it; the soot and the smoke hide more)
        float aFire = 1.0 - exp(-dens * 0.9 * k);
        float aSoot = 1.0 - exp(-(dens * (1.0 - temp) * 0.35 + smoke * 0.5) * k);
        vec3 soot = vec3(0.05, 0.04, 0.035) * (gAmb * 2.0 + gSunCol * 0.3);
        acc += tr * env * (aFire * emit + aSoot * mix(soot, smokeCol, smoke / max(smoke + dens * (1.0 - temp), 1.0e-4)));
        tr *= 1.0 - aSoot * env;
    }
    return vec4(acc, tr);
}

// 2 smoke: a plume rising and widening, bent over by the wind, lit by the sun from above and the sky round it.
vec4 marchSmoke(vec3 ro, vec3 rd, float sceneLen, mat4 v, float jit, int steps, int oct, bool fine) {
    vec3 c = v[0].xyz;
    float r = v[0].w;
    float h = v[1].x;
    float t = v[1].y;
    float env = rzEnvelope(t, v[1].z);
    vec3 wind = v[2].xyz;
    float seed = v[3].w;
    float bend = h * 0.6;
    vec2 seg = rzCylinder(ro, rd, c, r * 2.0 + length(wind.xz) * bend + 1.0, h * 1.1);
    seg.y = min(seg.y, sceneLen);
    if (seg.y <= seg.x || env <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float dt = (seg.y - seg.x) / float(steps);
    vec3 acc = vec3(0.0);
    float tr = 1.0;
    float ph = rzPhase(rd);
    // (it climbs as it is let out: the column is only as tall as the time it has had)
    float reach = min(1.0, t * 2.5 / h + 0.15);
    for (int i = 0; i < 64; i++) {
        if (i >= steps || tr < 0.03) {
            break;
        }
        float ts = seg.x + (float(i) + jit) * dt;
        vec3 q = ro + rd * ts - c;
        float s = q.y / h;
        q.xz -= wind.xz * bend * pow(max(s, 0.0), 1.5);
        float width = r * (0.45 + 1.4 * clamp(s, 0.0, 1.0));
        float rr = length(q.xz) / width;
        if (rr > 1.4 || q.y < -0.5 || s > reach + 0.1) {
            continue;
        }
        vec3 np = vec3(q.x / width * 1.4, q.y / r * 0.5 - t * 0.6, q.z / width * 1.4) + vec3(seed);
        float nz = rzFbm(np, oct);
        float dens = clamp((nz - 0.4) * 2.4 + (1.0 - rr) * 1.1 - 0.3, 0.0, 1.0) * (1.0 - smoothstep(reach - 0.1, reach + 0.1, s));
        if (dens < 0.003) {
            continue;
        }
        // (a look towards the sun through its own lumps: dark inside and under, bright where it faces the light)
        float sunT = exp(-(1.0 - clamp(s, 0.0, 1.0)) * 0.6);
        if (fine) {
            vec3 q2 = q + gSun * width * 0.5;
            float n2 = rzFbm(vec3(q2.x / width * 1.4, q2.y / r * 0.5 - t * 0.6, q2.z / width * 1.4) + vec3(seed), 2);
            sunT *= exp(-clamp((n2 - 0.4) * 2.6, 0.0, 1.0) * 1.6);
        }
        vec3 alb = mix(vec3(0.16, 0.15, 0.145), vec3(0.42, 0.41, 0.4), clamp(s, 0.0, 1.0)) * v[3].rgb * (0.8 + 0.4 * nz);
        vec3 col = alb * (gAmb * (1.0 + 0.6 * s) + gSunCol * sunT * 0.7 * ph);
        float a = 1.0 - exp(-dens * 0.9 * dt / max(width * 0.5, 0.5));
        acc += tr * a * col * env;
        tr *= 1.0 - a * env;
    }
    return vec4(acc, tr);
}

// 3 fireball: a burst that swells fast and lobed, white-hot at its heart, rising and cooling into dark smoke lit from
// within by the fire still in it.
vec4 marchFireball(vec3 ro, vec3 rd, float sceneLen, mat4 v, float jit, int steps, int oct) {
    float r = v[0].w;
    float t = v[1].y;
    float life = v[1].z;
    float env = 1.0 - smoothstep(life * 0.7, life, t);
    float I = v[2].w;
    float seed = v[3].w;
    vec3 c = v[0].xyz + vec3(0.0, r * 0.25 * t, 0.0);
    float Rv = r * (1.0 - exp(-t * 1.6));
    vec2 seg = rzSphere(ro, rd, c, r * 1.7 + 1.0);
    seg.y = min(seg.y, sceneLen);
    if (seg.y <= seg.x || env <= 0.0 || Rv < 0.05) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float dt = (seg.y - seg.x) / float(steps);
    vec3 acc = vec3(0.0);
    float tr = 1.0;
    float cool = t / max(life, 0.1);
    for (int i = 0; i < 64; i++) {
        if (i >= steps || tr < 0.04) {
            break;
        }
        float ts = seg.x + (float(i) + jit) * dt;
        vec3 q = ro + rd * ts - c;
        float rl = length(q);
        vec3 dirn = q / max(rl, 1.0e-3);
        float lobe = rzNoise(dirn * 2.4 + vec3(seed * 0.37, 3.1, seed * 0.11));
        float Re = Rv * (0.6 + 0.75 * lobe);
        float coarse = (1.0 - rl / max(Re, 0.1)) * 1.5 - 0.2;
        if (coarse < -1.3) {
            continue;
        }
        float nz = rzFbm(q / r * 2.2 + vec3(0.0, -t * 0.9, seed), oct);
        float body = coarse + (nz - 0.5) * 2.6;
        float dens = clamp(body * 1.2, 0.0, 1.0);
        if (dens < 0.003) {
            continue;
        }
        float temp = clamp(body * 0.55 + 0.55 - cool * 1.5 - max(q.y, 0.0) / (r * 2.5), 0.0, 1.0);
        vec3 emit = rzHeat(temp) * v[3].rgb * 2.6 * I * temp * temp;
        // (smoke, lit by the sky and the sun from outside and from underneath by the fire still in it)
        vec3 smoke = vec3(0.09, 0.075, 0.065) * (0.5 + nz) * (gAmb * 2.5 + gSunCol * 0.45 * (0.6 + 0.4 * clamp(q.y / r, 0.0, 1.0)))
                + rzHeat(0.45) * v[3].rgb * 0.25 * smoothstep(0.0, 0.4, temp) * I;
        float a = (1.0 - exp(-dens * dt * 0.9 / max(r * 0.25, 0.3))) * 0.95;
        acc += tr * a * (emit + smoke * (1.0 - temp * 0.7)) * env;
        tr *= 1.0 - a * env;
    }
    return vec4(acc, tr);
}

// 5 wind: the air made visible: thin sheets and streaks drawn out along the wind and carried with it, curling as they
// go, gusting, with dust skimming the land under them.
vec4 marchWind(vec3 ro, vec3 rd, float sceneLen, mat4 v, float jit, int steps) {
    vec3 c = v[0].xyz;
    float r = v[0].w;
    float h = v[1].x;
    float t = v[1].y;
    float env = rzEnvelope(t, v[1].z);
    float I = v[2].w;
    vec2 wd = normalize(v[2].xz + vec2(1.0e-4, 0.0));
    float seed = v[3].w;
    float speed = 14.0;
    vec2 seg = rzCylinder(ro, rd, c, r, h);
    seg.y = min(seg.y, sceneLen);
    if (seg.y <= seg.x || env <= 0.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float dt = (seg.y - seg.x) / float(steps);
    vec3 acc = vec3(0.0);
    float tr = 1.0;
    float ph = rzPhase(rd);
    for (int i = 0; i < 64; i++) {
        if (i >= steps || tr < 0.05) {
            break;
        }
        float ts = seg.x + (float(i) + jit) * dt;
        vec3 q = ro + rd * ts - c;
        float along = dot(q.xz, wd);
        float across = dot(q.xz, vec2(-wd.y, wd.x));
        float edge = (1.0 - smoothstep(r * 0.6, r, length(q.xz))) * smoothstep(3.0, 12.0, ts);
        float a0 = along - speed * t;
        // (the sheets curl as they go: lifted and let down in slow waves)
        float y = q.y + sin(a0 * 0.18 + seed) * 1.4 + sin(a0 * 0.07 + across * 0.2) * 2.0;
        float gust = 0.55 + 0.45 * sin(a0 * 0.05 + t * 1.3 + seed);
        // (thin filaments: where a slow field crosses its middle value, drawn out along the wind)
        float sheet = rzNoise(vec3(a0 * 0.03, across * 0.22, y * 0.3) + vec3(seed));
        float line = 1.0 - smoothstep(0.0, 0.035, abs(sheet - 0.5));
        float streak = line * smoothstep(0.4, 0.7, rzNoise(vec3(a0 * 0.08, across * 0.5, y * 0.5) + vec3(seed + 4.0)));
        float air = streak * gust * edge * (1.0 - smoothstep(h * 0.6, h, q.y));
        // (dust skimming the land)
        float dust = 0.0;
        if (q.y < 2.5) {
            float dn = rzFbm(vec3(a0 * 0.12, across * 0.25, q.y * 0.6 - t * 0.5) + vec3(seed), 3);
            dust = smoothstep(0.58, 0.8, dn) * smoothstep(2.0, 0.0, q.y) * smoothstep(-0.5, 0.2, q.y) * gust * edge * 0.6;
        }
        float total = air * 0.5 + dust * 0.3;
        if (total < 0.002) {
            continue;
        }
        vec3 airCol = vec3(0.86, 0.89, 0.93) * (gAmb * 1.6 + gSunCol * 0.6 * ph);
        vec3 dustCol = vec3(0.45, 0.39, 0.3) * v[3].rgb * (gAmb * 1.3 + gSunCol * 0.5);
        vec3 col = (airCol * air * 0.5 + dustCol * dust * 0.3) / total;
        float a = 1.0 - exp(-total * dt * I);
        acc += tr * a * col * env;
        tr *= 1.0 - a * env;
    }
    return vec4(acc, tr);
}

// 4 lava on the real blocks the pixel shows: a dark crust cracked into plates, molten rivers flowing slowly between
// them, glowing hottest in the cracks; tops of blocks most, their sides a little.
vec3 lavaOn(vec3 scene, vec3 p, vec3 nrm, mat4 v) {
    vec3 q = p - v[0].xyz;
    float r = v[0].w;
    float t = v[1].y;
    float life = v[1].z;
    float env = rzEnvelope(t, life);
    float I = v[2].w;
    float seed = v[3].w;
    float rr = length(q.xz);
    if (rr > r || abs(q.y) > 3.0 || env <= 0.0) {
        return scene;
    }
    float edge = smoothstep(r, r * 0.88, rr + (rzNoise(vec3(q.xz * 0.25, seed)) - 0.5) * r * 0.3);
    float near = smoothstep(3.0, 1.0, abs(q.y));
    float k = edge * near * env;
    if (k <= 0.0) {
        return scene;
    }
    // (the plates warped by noise so their edges are jagged, not a honeycomb; the rivers creep)
    vec2 warp = vec2(rzNoise(vec3(q.xz * 0.3, seed)), rzNoise(vec3(q.xz * 0.3 + 7.1, seed))) - 0.5;
    vec2 flow = q.xz * 0.6 + warp * 1.4 + vec2(t * 0.05, -t * 0.03);
    vec2 cells = rzCells(flow + vec2(seed));
    float gap = cells.y - cells.x + (rzNoise(vec3(q.xz * 2.5, seed)) - 0.5) * 0.08;
    float crack = 1.0 - smoothstep(0.0, 0.07, gap);
    crack *= 0.6 + 0.4 * rzNoise(vec3(q.xz * 0.8, t * 0.3 + seed));
    float river = smoothstep(0.6, 0.72, rzFbm(vec3(q.xz * 0.1 + vec2(t * 0.02, 0.0), t * 0.05 + seed), 3));
    float pulse = 0.85 + 0.15 * sin(t * 2.3 + rr * 0.6);
    // (it cools from the edge in as it ends: the crust spreading over the rivers)
    float cooling = smoothstep(life * 0.55, life, t);
    float heat = max(crack * 0.85, river) * pulse * (1.0 - cooling * smoothstep(r, 0.0, rr * (1.0 + cooling)));
    float top = mix(0.35, 1.0, smoothstep(0.2, 0.7, nrm.y));
    // (the crust: dark cooled rock, a little of the block's own colour left in it)
    vec3 crust = mix(scene, vec3(0.035, 0.03, 0.028) + scene * 0.05, k * 0.97);
    vec3 glow = rzHeat(0.2 + heat * 0.6) * v[3].rgb * 1.8 * I * heat * top;
    return crust + glow * k;
}

vec3 rzShoulder(vec3 x) {
    // (only light past white is pressed down: the scene itself is left as it was)
    vec3 over = max(x - 0.92, 0.0);
    return min(x, 0.92) + 0.08 * (1.0 - exp(-over / 0.08));
}

void main() {
    mat4 vols[8] = mat4[](Vol0, Vol1, Vol2, Vol3, Vol4, Vol5, Vol6, Vol7);
    rzLight();
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 nearPoint = InverseProj * vec4(ndc, -1.0, 1.0);
    vec4 deepPoint = InverseProj * vec4(ndc, 0.5, 1.0);
    vec3 roV = nearPoint.xyz / nearPoint.w;
    vec3 rdV = normalize(deepPoint.xyz / deepPoint.w - roV);
    vec3 ro = (ViewToWorld * vec4(roV, 1.0)).xyz;
    vec3 rd = normalize((ViewToWorld * vec4(rdV, 0.0)).xyz);

    float sceneLen = 1.0e9;
    bool sky = true;
    vec3 scenePos = vec3(0.0);
    if (DepthOn > 0.5) {
        float d = texture(DepthSampler, uv).r;
        if (d < 1.0) {
            vec4 sv = InverseProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
            vec3 sView = sv.xyz / sv.w;
            sceneLen = length(sView - roV);
            scenePos = ro + rd * sceneLen;
            sky = false;
        }
    }
    int count = int(Count + 0.5);

    // ---- hot air: the picture behind fire, fireballs and lava shimmers --------------------------------------------
    vec2 haze = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= count) {
            break;
        }
        mat4 v = vols[i];
        int type = int(v[1].w + 0.5);
        if (type != 1 && type != 3 && type != 4) {
            continue;
        }
        float env = rzEnvelope(v[1].y, v[1].z);
        vec3 c = v[0].xyz;
        float r = v[0].w;
        float h = type == 4 ? r * 0.5 : (type == 3 ? r * 2.0 : v[1].x * 1.4);
        // (how close the ray passes the hot column, in front of the scene)
        vec3 a = c;
        vec3 b = c + vec3(0.0, h, 0.0);
        vec3 ab = b - a;
        float tt = clamp(dot(ro + rd * dot(a - ro, rd) - a, ab) / dot(ab, ab), 0.0, 1.0);
        vec3 pa = a + ab * tt;
        float along = dot(pa - ro, rd);
        float miss = length(ro + rd * along - pa);
        if (along <= 0.0 || along > sceneLen + r) {
            continue;
        }
        float k = exp(-pow(miss / (r * 1.3 + 0.5), 2.0)) * env * v[2].w * smoothstep(0.0, 4.0, along);
        float tm = v[1].y * 2.3;
        if (k > 0.01) {
            haze += (vec2(rzNoise(vec3(gl_FragCoord.xy / 9.0, tm)), rzNoise(vec3(gl_FragCoord.xy / 9.0 + 17.0, tm + 5.0))) - 0.5) * k;
        }
    }
    vec2 suv = clamp(uv + haze * 0.006, 0.001, 0.999);
    vec3 col = pow(texture(DiffuseSampler, suv).rgb, vec3(2.2));

    // ---- the ground: lava painted on the real blocks, and the light of the fires on everything round them ---------
    if (!sky) {
        vec3 nrm = normalize(cross(dFdx(scenePos), dFdy(scenePos)));
        if (dot(nrm, rd) > 0.0) {
            nrm = -nrm;
        }
        vec3 lamp = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            if (i >= count) {
                break;
            }
            mat4 v = vols[i];
            int type = int(v[1].w + 0.5);
            float env = rzEnvelope(v[1].y, v[1].z);
            if (type == 4) {
                col = lavaOn(col, scenePos, nrm, v);
            }
            if (type == 1 || type == 3 || type == 4) {
                float r = v[0].w;
                vec3 lc = v[0].xyz + vec3(0.0, type == 1 ? v[1].x * 0.3 : (type == 3 ? 0.0 : 0.5), 0.0);
                vec3 tl = lc - scenePos;
                float dl = length(tl);
                float facing = 0.35 + 0.65 * max(dot(nrm, tl / max(dl, 1.0e-3)), 0.0);
                float flick = 0.85 + 0.15 * sin(v[1].y * 17.0 + v[3].w) * sin(v[1].y * 7.3);
                float reach = type == 3 ? r * 3.0 : r * 2.5;
                float burst = type == 3 ? exp(-v[1].y * 0.8) * 2.0 : 1.0;
                lamp += rzHeat(0.55) * v[3].rgb * v[2].w * env * flick * facing * burst * 1.6 / (1.0 + dl * dl / (reach * reach));
            }
        }
        col += col * lamp + lamp * 0.03;
    }

    // ---- the volumes, far to near ------------------------------------------------------------------------------------
    float jit = rzDither(gl_FragCoord.xy + vec2(Frame * 5.588238, Frame * 7.137));
    int q = int(Quality + 0.5);
    int steps = q <= 1 ? 16 : (q == 2 ? 28 : 44);
    int oct = q <= 1 ? 2 : (q == 2 ? 3 : 4);
    for (int i = 0; i < 8; i++) {
        if (i >= count) {
            break;
        }
        mat4 v = vols[i];
        int type = int(v[1].w + 0.5);
        vec4 res = vec4(0.0, 0.0, 0.0, 1.0);
        if (type == 0) {
            res = marchTornado(ro, rd, sceneLen, v, jit, steps + 8);
        } else if (type == 1) {
            res = marchFire(ro, rd, sceneLen, v, jit, steps, oct);
        } else if (type == 2) {
            res = marchSmoke(ro, rd, sceneLen, v, jit, steps, oct, q >= 2);
        } else if (type == 3) {
            res = marchFireball(ro, rd, sceneLen, v, jit, steps, oct);
        } else if (type == 5) {
            res = marchWind(ro, rd, sceneLen, v, jit, steps);
        }
        col = col * res.a + res.rgb;
    }

    fragColor = vec4(pow(rzShoulder(col), vec3(1.0 / 2.2)), 1.0);
}
