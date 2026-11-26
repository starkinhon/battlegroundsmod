#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The approved preview's Earth (forja/bills-esfera-cutscene/page.html), same maths: the pixel map (each pixel a block,
// 512 x 256, equirectangular, sampled in 3D so there is no seam) painted by the same noise (an integer hash, so the
// continents are exactly the preview's), lit from the side by the sun, with the blue rim; the cloud shell; and the two
// shells of air. Kind 0 ground, 1 clouds, 2 air (Power, Strength; only its far side, added on top).
uniform vec3 Sun;
uniform float Kind;
uniform float Power;
uniform float Strength;
in vec3 vN;
in vec3 vW;
in vec2 vU;
out vec4 fragColor;

const float PI = 3.14159265;

float hsh(int a, int b, int c, int seed) {
    uint n = uint(a) * 374761393u + uint(b) * 668265263u + uint(c) * 2147483647u + uint(seed) * 1274126177u;
    n = (n ^ (n >> 13u)) * 1103515245u;
    return float((n ^ (n >> 16u)) & 65535u) / 65535.0;
}
float vnoise(float x, float y, float z, int seed) {
    float xf = floor(x), yf = floor(y), zf = floor(z);
    int xi = int(xf), yi = int(yf), zi = int(zf);
    float u = x - xf, v = y - yf, w = z - zf;
    u = u * u * (3.0 - 2.0 * u);
    v = v * v * (3.0 - 2.0 * v);
    w = w * w * (3.0 - 2.0 * w);
    return mix(mix(mix(hsh(xi, yi, zi, seed), hsh(xi + 1, yi, zi, seed), u), mix(hsh(xi, yi + 1, zi, seed), hsh(xi + 1, yi + 1, zi, seed), u), v),
               mix(mix(hsh(xi, yi, zi + 1, seed), hsh(xi + 1, yi, zi + 1, seed), u), mix(hsh(xi, yi + 1, zi + 1, seed), hsh(xi + 1, yi + 1, zi + 1, seed), u), v), w);
}
float fbm3(float x, float y, float z, int seed) {
    float s = 0.0, a = 0.5, f = 1.0;
    for (int i = 0; i < 4; i++) { s += a * vnoise(x * f, y * f, z * f, seed + i); f *= 2.03; a *= 0.5; }
    return s / 0.9375;
}
vec3 hex(int c) { return vec3(float((c >> 16) & 255), float((c >> 8) & 255), float(c & 255)); }

// The texel of the w x h map under this uv (nearest), and the point of the sphere it was painted from.
vec3 texel(vec2 uv, float w, float h, out vec2 px) {
    px = vec2(min(floor(clamp(uv.x, 0.0, 1.0) * w), w - 1.0), min(floor(clamp(uv.y, 0.0, 1.0) * h), h - 1.0));
    float lon = (px.x + 0.5) / w * PI * 2.0, lat = (0.5 - (px.y + 0.5) / h) * PI;
    return vec3(cos(lat) * cos(lon), sin(lat), cos(lat) * sin(lon));
}

vec4 ground(vec2 uv) {
    vec2 px;
    vec3 p = texel(uv, 512.0, 256.0, px);
    float land = fbm3(p.x * 2.0 + 5.0, p.y * 2.0, p.z * 2.0, 11);
    float dry = fbm3(p.x * 3.0 + 1.0, p.y * 3.0 + 7.0, p.z * 3.0, 31);
    float det = vnoise(px.x * 0.9, px.y * 0.9, 3.0, 91);
    float pole = abs(p.y) + (vnoise(p.x * 6.0, p.y * 6.0, p.z * 6.0, 5) - 0.5) * 0.12;
    int c;
    if (pole > 0.84) c = land > 0.5 ? 0xf4f8ff : 0xdfeaf7;
    else if (land < 0.5) c = land < 0.4 ? 0x1d4fa6 : (land < 0.47 ? 0x2a68c4 : 0x3a86d8);
    else if (land < 0.52) c = 0xe0cc8a;
    else if (land > 0.7) c = land > 0.74 ? 0xeef2f8 : 0x8b8c93;
    else if (dry > 0.62 && pole < 0.5) c = dry > 0.68 ? 0xb8613a : 0xd0aa5e;
    else if (pole > 0.68) c = 0x6f9a7a;
    else c = dry < 0.42 ? 0x2f6e2b : (land > 0.62 ? 0x5a8f3c : 0x4e9a3a);
    vec3 rgb = min(vec3(255.0), floor(hex(c) * (0.9 + 0.2 * det) + 0.5));
    return vec4(rgb / 255.0, 1.0);
}

vec4 clouds(vec2 uv) {
    vec2 px;
    vec3 p = texel(uv, 512.0, 256.0, px);
    float big = fbm3(p.x * 2.4 + 9.0, p.y * 3.6, p.z * 2.4, 51);
    float thin = fbm3(p.x * 5.0 + 3.0, p.y * 11.0, p.z * 5.0, 57);
    float n = max(big, thin * 0.93);
    float a = n > 0.56 ? (n > 0.62 ? 245.0 : 180.0) : 0.0;
    float v = n > 0.6 ? 255.0 : 236.0;
    return vec4(v / 255.0, v / 255.0, min(255.0, v + 6.0) / 255.0, a / 255.0);
}

void main() {
    vec3 n = normalize(vN);
    vec3 v = normalize(-vW);
    vec3 sun = normalize(Sun);
    float l = dot(n, sun);
    if (Kind > 1.5) {
        // air: only its far side (three.js BackSide), added on top
        if (dot(n, v) > 0.0) discard;
        float f = 1.0 - abs(dot(n, v));
        float lit = smoothstep(-0.35, 0.5, l);
        float a = pow(f, Power) * Strength * (0.2 + 0.8 * lit);
        fragColor = vec4(vec3(0.4, 0.72, 1.0) * a, a);
        return;
    }
    vec4 t = Kind > 0.5 ? clouds(vU) : ground(vU);
    if (t.a < 0.004) discard;
    float day = smoothstep(-0.2, 0.3, l);
    vec3 c = t.rgb * (0.10 + 0.95 * day * (0.55 + 0.45 * max(l, 0.0)));
    if (Kind < 0.5) {
        float f = pow(1.0 - max(dot(n, v), 0.0), 2.5);
        c += vec3(0.45, 0.75, 1.0) * f * (0.25 + 0.9 * day);
    }
    fragColor = vec4(c, t.a);
}
