#version 150

// Sukuna's Fuga: the painted arrow (the preview's arrowFS), one shape as a distance field in a plane holding the arrow's
// axis (two crossed planes fixed in the world, never facing the camera). Shaft, broadhead with swept barbs, fletching; the
// fire lives inside the shape and licks stream off its edges toward the tail. Plane coords: x along the arrow in blocks
// (0 = tail, L = tip), y across. Halo > 0.5: the soft additive glow pass.
uniform float FTime;
uniform float L;
uniform float Grow;
uniform float Fist;
uniform float Alpha;
uniform float Halo;
uniform float W;
uniform float X0;

in vec2 vUv;
out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.0, w = 0.5; for (int i = 0; i < 4; i++) { a += w * vn(p); p = p * 2.03 + vec2(1.7, 9.2); w *= 0.5; } return a; }
vec3 pal(float h) { return h > 0.66 ? vec3(1.0, 0.97, 0.84) : h > 0.46 ? vec3(1.0, 0.84, 0.28) : h > 0.26 ? vec3(1.0, 0.52, 0.1) : h > 0.1 ? vec3(0.88, 0.2, 0.05) : vec3(0.5, 0.05, 0.03); }
float sm(float a, float b, float x) { float u = clamp((x - a) / (b - a), 0.0, 1.0); return u * u * (3.0 - 2.0 * u); }

float sdSeg(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sdTri(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c, v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0), pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0),
         pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)), vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                 vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}
float arrowSd(vec2 p) {
    vec2 q = vec2(p.x, abs(p.y));
    float shaft = sdSeg(p, vec2(0.1, 0.0), vec2(L - 0.7, 0.0)) - 0.05;
    float head = min(sdTri(q, vec2(L, 0.0), vec2(L - 0.78, 0.34), vec2(L - 0.62, 0.0)), sdTri(q, vec2(L - 0.62, 0.0), vec2(L - 0.78, 0.34), vec2(L - 1.02, 0.36)));
    head = min(head, sdTri(q, vec2(L - 0.62, 0.0), vec2(L - 1.02, 0.36), vec2(L - 0.86, 0.06))) - 0.012;
    float fl = min(sdTri(q, vec2(0.02, 0.03), vec2(0.12, 0.27), vec2(0.5, 0.05)), sdTri(q, vec2(0.12, 0.27), vec2(0.62, 0.22), vec2(0.5, 0.05))) - 0.01;
    return min(min(shaft, head), fl);
}
void main() {
    float t = floor(FTime * 12.0) / 12.0 * 1.5;
    vec2 p = vec2(X0 + vUv.x * (L - X0 + 0.15), (vUv.y * 2.0 - 1.0) * W);
    float n = fbm(vec2((p.x + t * 2.2) * 2.4, p.y * 7.0)), n2 = fbm(vec2((p.x + t * 3.1) * 5.0, p.y * 13.0 + 4.0)), n3 = fbm(vec2((p.x + t * 1.6) * 1.3, p.y * 3.0 + 9.0));
    float d0 = arrowSd(p);
    float lick = 0.05 + 0.38 * n3 * n3;
    float d1 = arrowSd(p + vec2(lick, 0.0)) + 0.025, d2 = arrowSd(p + vec2(lick * 2.2, (n - 0.5) * 0.08)) + 0.07;
    float d = min(d0, min(d1, d2));
    d += (n - 0.5) * 0.06 + (n2 - 0.5) * 0.035;
    float reveal = Grow * (L + 0.6) - abs(p.x - Fist);
    d += max(0.0, -reveal) * 2.0;
    d += (1.0 - Alpha) * 0.4;
    if (Halo > 0.5) {
        float edge = sm(0.0, 0.12, vUv.x) * sm(1.0, 0.9, vUv.x) * sm(0.0, 0.25, vUv.y) * sm(1.0, 0.75, vUv.y);
        float h = exp(-max(d, 0.0) * 9.0) * 0.5 * Alpha * edge;
        fragColor = vec4(vec3(1.0, 0.38, 0.07) * h, 1.0);
        return;
    }
    if (d > 0.0) discard;
    float depth = -d;
    float core = exp(-abs(p.y) * 22.0) * step(0.15, p.x) * step(p.x, L - 0.15);
    float hc = exp(-length((p - vec2(L - 0.55, 0.0)) * vec2(1.6, 4.0)) * 3.0);
    float hot = depth * 5.0 + core * 0.5 + hc * 0.22 + (n2 - 0.5) * 0.3;
    float pk = fbm(vec2((p.x + t * 2.0) * 3.4, p.y * 9.0 + 2.0));
    if (depth > 0.04 && pk < 0.33 && core < 0.3) hot = min(hot, 0.3);
    if (depth < 0.012) hot = 0.05;
    fragColor = vec4(pal(hot), 1.0);
}
