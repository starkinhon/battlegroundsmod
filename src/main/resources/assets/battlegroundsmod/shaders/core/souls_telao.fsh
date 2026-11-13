#version 150

// The preview's screenMat fragment shader, line for line (time in seconds, wp = world position).
uniform float Time;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 wp;
in float vertexDistance;

out vec4 fragColor;

float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    float time = Time;
    vec2 q = vec2(wp.x + wp.z, wp.y) * 0.18;
    float cut = floor(time / 3.0);
    float k = h(vec2(cut, floor((wp.x - wp.z) / 40.0)));
    vec3 a = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + k + q.x * 0.3 + time * 0.1));
    float bands = smoothstep(0.3, 0.7, sin(q.y * 6.0 + time * 2.0 + k * 9.0));
    vec3 c = mix(a, a.zxy, bands);
    float px = h(floor(wp.xy * 4.0) + floor(time * 8.0)) * 0.12;
    vec3 col = c * 0.9 + px;
    // the world's fog over it, so a far screen fades like the buildings around it (it is its own light otherwise)
    float keep = vertexDistance <= FogStart ? 1.0 : 1.0 - smoothstep(FogStart, FogEnd, vertexDistance);
    fragColor = vec4(mix(FogColor.rgb, col, mix(1.0, keep, FogColor.a)), 1.0);
}
