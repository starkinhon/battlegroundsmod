#version 150

// Sukuna's Fuga: the soft glows (the ball's core, the fist, the ground under him) and the embers. Color.a < 0.5: a glow,
// vCol.rgb already scaled by its strength (the preview's glowMat: (1 - r)^2.2, added). Otherwise an ember: a square spark
// (Minecraft-ish), hard yellow core, orange rim, soft dark glow, its heat in vCol.r.
in vec2 vUv;
in vec4 vCol;
out vec4 fragColor;

void main() {
    if (vCol.a < 0.5) {
        float r = length(vUv * 2.0 - 1.0);
        float g = pow(max(0.0, 1.0 - r), 2.2);
        fragColor = vec4(vCol.rgb * g, 1.0);
        return;
    }
    float heat = vCol.r;
    vec2 c = abs(vUv - 0.5);
    float m = max(c.x, c.y);
    vec3 col = m < 0.16 ? mix(vec3(1.0, 0.6, 0.15), vec3(1.0, 0.95, 0.7), heat) : m < 0.24 ? vec3(1.0, 0.42, 0.06) : vec3(1.0, 0.3, 0.05) * 0.35 * (1.0 - m * 2.0);
    fragColor = vec4(col * (heat * 0.6 + 0.4), 1.0);
}
