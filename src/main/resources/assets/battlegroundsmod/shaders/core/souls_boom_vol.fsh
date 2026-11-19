#version 150

// The end-game bosses' volumes (client/fx/epic/EpicFx), the approved page's VOL_F (forja/escala-fim-de-jogo/world.js,
// Stark 2026-11-19): ONE shape raymarched per volume, never a pile of sprites.
//   Mode  0 fire (white-hot core, orange tongues, cools to dark smoke rising), 1 dust (lit by the sun, cauliflower
//         lumps, no layers by height), 2 smoke with embers
//   Shape 0 dome with a skirt hugging the ground, 1 column that becomes a mushroom, 2 dust ring running over the
//         ground (R is the ring's radius), 3 shock ring hanging in the air (thin)
// Emission + absorption front to back, premultiplied; the depth of the first dense point is written, so the terrain
// and bodies in front hide it (the page drew over everything).
uniform vec3 E;
uniform vec3 Bmin;
uniform vec3 Bmax;
uniform vec3 Dust;
uniform float R;
uniform float H;
uniform float Life;
uniform float Phase;
uniform float Heat;
uniform float Seed;
uniform float Fade;
uniform float Mode;
uniform float Shape;
uniform float Ground;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 vW;
out vec4 fragColor;

const vec3 SUN = vec3(0.4472, 0.7751, 0.3478);

vec3 h33(vec3 p) { p = fract(p * vec3(0.1031, 0.1030, 0.0973)); p += dot(p, p.yxz + 33.33); return fract((p.xxy + p.yxx) * p.zyx) * 2.0 - 1.0; }
float gn(vec3 p) {
    vec3 i = floor(p), f = fract(p), u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(h33(i), f), b = dot(h33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), c = dot(h33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)),
          d = dot(h33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0));
    float e = dot(h33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), g = dot(h33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)),
          k = dot(h33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), l = dot(h33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(k, l, u.x), u.y), u.z) * 1.6;
}
const mat3 ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
float fbm3(vec3 p) { float s = 0.0, w = 0.5; for (int o = 0; o < 3; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }
float fbm4(vec3 p) { float s = 0.0, w = 0.5; for (int o = 0; o < 4; o++) { s += w * gn(p); p = ROT * p * 2.03; w *= 0.5; } return s; }

float field(vec3 p, out float temp) {
    vec3 q = p - E;
    float rise = Shape > 0.5 ? 0.0 : Life * Life * H * (Mode < 0.5 ? 0.4 : 0.25);
    q.y -= rise;
    float r = length(q.xz);
    vec2 dir = q.xz / max(r, 1e-3);
    float yy = max(q.y, 0.0);
    float d;
    if (Shape < 0.5) {
        d = length(vec3(q.x / R, yy / H, q.z / R));
    } else if (Shape < 1.5) {
        // a column whose top opens into a mushroom
        float capY = H * (0.55 + 0.45 * Life);
        float stem = r / (R * 0.42) + smoothstep(capY * 0.7, capY, q.y) * 3.0;
        float cap = length(vec3(q.x / R, (q.y - capY) / (H * 0.32), q.z / R));
        d = min(max(stem, step(capY + H * 0.1, q.y) * 9.0), cap);
        if (q.y < 0.0) d = 9.0;
    } else if (Shape < 2.5) {
        d = length(vec2((r - R) / max(R * 0.22, 1.5), yy / H));
    } else {
        d = length(vec2((r - R) / max(H * 2.2, 1.0), q.y / H));
    }
    float sc = Shape > 1.5 ? max(H * 3.0, 4.0) : max(R, 1.0);
    vec3 np = vec3(q.x, q.y * (Mode < 0.5 ? 1.4 : 1.0), q.z) / sc * (Mode < 0.5 ? 3.2 : 2.2);
    np -= vec3(dir.x, 0.0, dir.y) * Phase * 0.22;
    np.y -= Phase * (Mode < 0.5 ? 0.5 : 0.3);
    float w = fbm3(np * 0.6 + Seed);
    float n = fbm4(np + vec3(w * 1.8, -w * 1.2, w * 1.3) + Seed * 1.7);
    // dust: cauliflower lumps, never bands by height
    if (Mode > 0.5) n = n * 0.8 + 0.35 * (abs(gn(np * 0.9 + Seed * 3.0)) - 0.35);
    float skirt = Shape < 0.5 && Mode < 0.5 ? exp(-pow(yy / (H * 0.16), 2.0)) * (1.0 - smoothstep(0.9, 1.25 + n * 0.2, r / R)) : 0.0;
    float f = max(1.0 - d + n * (Mode < 0.5 ? 0.55 : 0.95), skirt * 0.85 + n * 0.35 - 0.05);
    f -= Life * 0.55 * (1.0 - smoothstep(0.25, 0.9, d)) * (Mode < 0.5 ? 1.0 : 0.0);
    float lick = gn(np * 3.1 + vec3(0.0, -Phase * 1.3, 0.0) + Seed);
    temp = clamp(Heat * (1.2 - d * 0.6) + n * 0.8 + lick * 0.25 - Life * 0.75, 0.0, 1.0);
    float th = 0.02 + Life * 0.3;
    return q.y < -0.6 ? 0.0 : smoothstep(th, th + (Mode < 0.5 ? 0.22 : 0.55), f) * Fade;
}

vec3 ramp(float t) {
    float x = t * 4.0;
    vec3 c0 = vec3(0.06, 0.03, 0.025), c1 = vec3(0.45, 0.04, 0.01), c2 = vec3(1.0, 0.2, 0.03), c3 = vec3(1.0, 0.55, 0.1), c4 = vec3(1.0, 0.93, 0.72);
    return x < 1.0 ? mix(c0, c1, x) : x < 2.0 ? mix(c1, c2, x - 1.0) : x < 3.0 ? mix(c2, c3, x - 2.0) : mix(c3, c4, clamp(x - 3.0, 0.0, 1.0));
}

float depthOf(vec3 rel) { vec4 c = ProjMat * ModelViewMat * vec4(rel, 1.0); return c.z / c.w * 0.5 + 0.5; }

void main() {
    vec3 rd = normalize(vW);
    vec3 inv = 1.0 / rd, t0s = Bmin * inv, t1s = Bmax * inv;
    vec3 tmn = min(t0s, t1s), tmx = max(t0s, t1s);
    float t0 = max(max(max(tmn.x, tmn.y), tmn.z), 0.0), t1 = min(min(tmx.x, tmx.y), tmx.z);
    if (rd.y < 0.0) t1 = min(t1, (Ground - 0.0) / rd.y);
    if (t1 <= t0) discard;
    const int STEPS = 52;
    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    vec3 acc = vec3(0.0);
    float T = 1.0, hitT = -1.0;
    for (int i = 0; i < STEPS; i++) {
        vec3 p = rd * t;
        float temp;
        float dn = field(p, temp);
        if (dn > 0.002) {
            if (hitT < 0.0 && dn > 0.25) hitT = t;
            vec3 col;
            float k;
            if (Mode < 0.5) {
                k = mix(0.9, 0.5, temp);
                col = ramp(temp) * mix(0.25, 2.4, smoothstep(0.08, 0.7, temp)) * mix(0.3, 1.0, smoothstep(0.05, 0.7, dn));
                // cooled down: dark smoke rising
                float sm = smoothstep(0.3, 0.06, temp) * smoothstep(0.15, 0.5, Life);
                col = mix(col, vec3(0.16, 0.14, 0.13) * (0.6 + 0.6 * smoothstep(0.0, 1.0, (p.y - E.y) / (H * 1.5))), sm);
            } else {
                // lit by the sun: how dense it is a little way towards the sun
                float tl;
                float up = field(p + SUN * max(R, 4.0) * 0.12, tl);
                float lit = clamp(1.0 - up * 1.4, 0.2, 1.0);
                k = 0.55;
                col = Dust * mix(0.42, 1.15, lit) * mix(0.75, 1.0, smoothstep(0.05, 0.6, dn));
                if (Mode > 1.5) col = mix(col, ramp(temp) * 2.0, smoothstep(0.35, 0.85, temp) * 0.85);
            }
            float a = 1.0 - exp(-dn * dt * k);
            acc += T * a * col;
            T *= 1.0 - a;
            if (T < 0.015) break;
        }
        t += dt;
    }
    float al = 1.0 - T;
    if (al < 0.003) discard;
    float firstT = hitT > 0.0 ? hitT : t0;
    float fogK = smoothstep(FogStart, FogEnd, firstT) * 0.85;
    gl_FragDepth = depthOf(rd * firstT);
    fragColor = vec4(mix(acc, FogColor.rgb * al, fogK), al);
}
