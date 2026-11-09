#version 150

// The Clivar's grid (the preview's gridMat): fine white lines "measure" the body, each drawn in at its own moment and
// fading in along its length as Measure goes 0 -> 1, so the grid closes smoothly; on the snap (Sil 1) the body is a black
// silhouette under the white grid; then the cut marks fade (Fade). The skin's see-through pixels (outer layers) stay clear.
uniform sampler2D Sampler0;
uniform float Measure;
uniform float Sil;
uniform float Fade;
uniform float N;
uniform float Seed;

in vec2 texCoord;
in vec3 local;
in vec3 normal;

out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y); }

void main() {
    if (texture(Sampler0, texCoord).a < 0.1) {
        discard;
    }
    vec3 q = local * N + 0.5 + vec3(Seed * 0.37, Seed * 0.53, Seed * 0.29);
    vec3 f = abs(fract(q) - 0.5);
    vec3 id = floor(q);
    vec3 tn = 1.0 - abs(normalize(normal));
    float line = 0.0;
    for (int i = 0; i < 3; i++) {
        float w = i == 0 ? tn.x : i == 1 ? tn.y : tn.z;
        if (w < 0.5) {
            continue;
        }
        float fi = i == 0 ? f.x : i == 1 ? f.y : f.z;
        float ii = i == 0 ? id.x : i == 1 ? id.y : id.z;
        float h = h21(vec2(ii * 1.7 + float(i) * 31.0, 7.0));
        float pr = clamp((Measure * 1.45 - h) / 0.45, 0.0, 1.0);
        float r = vn(vec2(dot(local, vec3(1.3, 2.1, 0.9)) * 2.2 + ii * 0.37, float(i) * 5.0 + ii));
        float show = smoothstep(r - 0.12, r + 0.12, pr * 1.24 - 0.12) * smoothstep(0.0, 0.35, pr);
        float wd = mix(0.02, 0.06, smoothstep(0.0, 1.0, pr));
        line = max(line, (1.0 - smoothstep(wd, wd + 0.05, fi)) * show);
    }
    float a = max(line, Sil) * Fade;
    if (a < 0.004) {
        discard;
    }
    fragColor = vec4(mix(vec3(0.02, 0.01, 0.02), vec3(1.0), line), a);
}
