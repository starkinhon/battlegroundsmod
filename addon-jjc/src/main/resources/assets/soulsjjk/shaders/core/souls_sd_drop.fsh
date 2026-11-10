#version 150

// Sukuna's Santuário Malevolente (client/fx/SukunaDomainScene), ported from the approved preview (forja/sukuna-dominio).
in vec2 vUv;
in vec3 vW;
out vec4 fragColor;

void main() {
    float r = length(vUv);
    if (r > 1.0) discard;
    vec3 n = vec3(vUv, sqrt(1.0 - r * r));
    float spec = pow(max(dot(n, normalize(vec3(0.4, 0.7, 0.6))), 0.0), 120.0);
    float f = pow(1.0 - n.z, 2.0);
    vec3 c = vec3(0.62, 0.85, 1.0) * (0.35 + 0.65 * n.z) + vec3(0.04, 0.12, 0.19) + vec3(1.0) * spec * 1.5 + vec3(0.7, 0.9, 1.0) * f * 0.5;
    fragColor = vec4(c * 0.85, 0.85);
}
