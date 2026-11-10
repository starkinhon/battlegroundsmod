#version 150

// Rukia's ice over a body, a port of the visualizer's ICEB_FS (photonic body of ice):
// every pixel of the skin (one texel = one Minecraft pixel) freezes or thaws on its own, along a field:
//   IceMode 0: from the feet up (local.y / IceHeight);  IceMode 1: out from the point IceOrigin (distance / IceHeight).
// Progress 0..1 = how far the ice has gone (the front glows white); Shatter 0..1 = pixels breaking away.
// Translucent blue-white ice with voronoi cracks, fresnel and twinkles; never cut hard (fades in over the body).
uniform sampler2D Sampler0;
uniform float GameTime;
uniform float IceMode;
uniform float IceHeight;
uniform float Progress;
uniform float Shatter;
uniform float Seed;

in vec2 texCoord;
in vec3 local;
in vec3 rel;
in vec3 normal;

out vec4 fragColor;

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7)) + Seed * 13.7) * 43758.5453);
}

vec2 cellNoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int a = -1; a <= 1; a++) {
        for (int b = -1; b <= 1; b++) {
            for (int c = -1; c <= 1; c++) {
                vec3 g = vec3(float(a), float(b), float(c));
                vec3 o = vec3(hash3(i + g), hash3(i + g + 11.3), hash3(i + g + 27.1));
                float d = length(g + o - f);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
    }
    return vec2(d1, d2);
}

float iceField(vec3 p) {
    return IceMode < 0.5 ? p.y / IceHeight : length(p) / IceHeight;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord);
    if (tex.a < 0.1) {
        discard;
    }
    // The skin pixel this fragment is in, and where its middle is in the world: the ice goes pixel by pixel.
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 px = texCoord * size;
    vec2 cellUv = floor(px);
    vec2 toMiddle = (cellUv + 0.5) - px;
    mat2 duv = mat2(dFdx(px), dFdy(px));
    vec3 middle = local;
    if (abs(determinant(duv)) > 1.0e-8) {
        vec2 k = inverse(duv) * toMiddle;
        middle = local + dFdx(local) * k.x + dFdy(local) * k.y;
    }
    vec3 cell = vec3(cellUv, floor(dot(normal, vec3(1.0, 2.0, 3.0)) * 4.0));
    float n = hash3(cell);
    float d = Progress * 1.12 - (iceField(middle) + (n - 0.5) * 0.1 + 0.05);
    if (d <= 0.0) {
        discard;
    }
    if (Shatter > 0.0 && hash3(cell + 7.7) < Shatter) {
        discard;
    }
    float edge = 1.0 - smoothstep(0.0, 0.07, d);
    vec3 N = normalize(normal);
    vec3 V = normalize(-rel);
    float fres = pow(1.0 - abs(dot(N, V)), 2.0);
    vec2 vo = cellNoise(local * 6.0 + Seed);
    float crack = 1.0 - smoothstep(0.0, 0.05, vo.y - vo.x);
    float glint = pow(max(0.0, dot(reflect(-V, N), normalize(vec3(0.3, 0.8, 0.5)))), 18.0);
    float time = GameTime * 24000.0;
    float tw = step(0.95, n) * pow(max(0.0, sin(time * 0.2 + n * 60.0)), 12.0);
    vec3 deep = vec3(0.28, 0.56, 0.9);
    vec3 pale = vec3(0.84, 0.95, 1.0);
    vec3 c = mix(deep, pale, clamp(0.3 + fres * 0.75 + vo.x * 0.35 + (n - 0.5) * 0.12, 0.0, 1.0));
    c += vec3(0.75, 0.9, 1.0) * crack * 0.35 + glint * 0.7 + tw;
    c = mix(c, vec3(1.25, 1.4, 1.5), edge);
    float alpha = clamp(0.46 + fres * 0.4 + crack * 0.25 + edge * 0.5 + tw, 0.0, 1.0);
    // Fresh ice fades in over a few pixels instead of popping (no hard cut).
    alpha *= smoothstep(0.0, 0.025, d);
    fragColor = vec4(c, alpha);
}
