#version 150

// Translucent blue armor: bright rim where the surface turns away from the camera (fresnel), chakra noise flowing up
// over it, and a white edge where the arm is still forming out of (or fading into) the shoulder.
// Color.r carries how far past the forming edge this point is (d = r * 10 - 8, positive = not formed yet),
// Color.g the flash of a hit and Color.a the opacity. The same shader draws a depth-only pass first (so only the
// front surface shows, no stacked glass), the color, and with GlowOnly the copy that goes into the bloom.
uniform float Time;
uniform float GlowOnly;
uniform vec3 Deep;
uniform vec3 Mid;
uniform vec3 Rim;

in vec4 vertexColor;
in vec3 world;
in vec3 rel;
in vec3 normal;

out vec4 fragColor;

float h3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float n3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h3(i), h3(i + vec3(1, 0, 0)), f.x), mix(h3(i + vec3(0, 1, 0)), h3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h3(i + vec3(0, 0, 1)), h3(i + vec3(1, 0, 1)), f.x), mix(h3(i + vec3(0, 1, 1)), h3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    vec3 flow = vec3(0.0, -Time * 1.8, 0.0);
    float n = n3(world * 1.4 + flow) * 0.6 + n3(world * 3.3 + flow * 1.6) * 0.4;
    float d = vertexColor.r * 10.0 - 8.0 + (n - 0.5) * 1.4;
    if (d > 0.0) {
        discard;
    }
    vec3 N = normalize(normal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(-rel);
    float fres = pow(1.0 - clamp(abs(dot(N, V)), 0.0, 1.0), 2.6);
    float lit = 0.55 + 0.45 * max(dot(N, normalize(vec3(0.3, 1.0, 0.2))), 0.0);
    float flame = smoothstep(0.5, 0.95, n);
    float edge = smoothstep(-0.9, 0.0, d);
    float hit = vertexColor.g;
    float alpha = vertexColor.a;
    if (GlowOnly > 0.5) {
        fragColor = vec4((Mid * fres * 0.28 + Mid * flame * 0.1 + Rim * edge * 0.8 + Mid * hit * 0.35) * alpha, 1.0);
        return;
    }
    vec3 col = mix(Deep, Mid, 0.15 + 0.6 * n) * lit + mix(Mid, Rim, fres) * fres * 0.9 + Mid * flame * 0.3;
    col = mix(col, vec3(1.0), edge * 0.85) + Rim * hit * 0.5;
    fragColor = vec4(col, clamp((0.55 + 0.4 * fres + 0.15 * flame + edge) * alpha, 0.0, 1.0));
}
