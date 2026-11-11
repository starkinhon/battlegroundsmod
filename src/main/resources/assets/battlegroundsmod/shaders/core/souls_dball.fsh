#version 150

// Same colors and stars as BALL_F of the Shenlong preview (forja/shenlong/glsl.js). Pulse: the heartbeat of the
// summon (0 on the item). Stone: turning to stone after the wish (0 on the item).
uniform float Pulse;
uniform float Stone;

in vec3 vLocal;
in vec3 vViewN;
in vec3 vViewDir;
in float vStars;
in vec4 vLight;

out vec4 fragColor;

float h31(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n3(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h31(i), h31(i + vec3(1, 0, 0)), f.x), mix(h31(i + vec3(0, 1, 0)), h31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h31(i + vec3(0, 0, 1)), h31(i + vec3(1, 0, 1)), f.x), mix(h31(i + vec3(0, 1, 1)), h31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// five-pointed star (iq)
float star(vec2 p, float r) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-k1.x, k1.y);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = 0.382 * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

void main() {
    vec3 n = normalize(vViewN), v = normalize(vViewDir);
    float f = max(dot(n, v), 0.0);
    // orange glass: yellow core where it is seen straight on, red rim, hard highlight
    vec3 col = mix(vec3(0.85, 0.28, 0.04), vec3(1.0, 0.62, 0.12), smoothstep(0.1, 0.6, f));
    col = mix(col, vec3(1.0, 0.86, 0.38), smoothstep(0.75, 0.95, f));
    // red stars in the middle of the ball, seen through the glass (fixed on the ball)
    vec3 ln = normalize(vLocal);
    float s = 1e3;
    for (int i = 0; i < 7; i++) {
        if (float(i) >= vStars) break;
        float a = float(i) * 2.399 + 0.6, rr = vStars < 1.5 ? 0.0 : 0.42 + 0.06 * mod(float(i), 2.0);
        vec3 c = normalize(vec3(cos(a) * rr, sin(a) * rr, 1.0));
        vec3 t1 = normalize(cross(vec3(0.0, 1.0, 0.0), c)), t2 = cross(c, t1);
        vec3 d = ln - c;
        s = min(s, star(vec2(dot(d, t1), dot(d, t2)) / 0.2, 0.55) * 0.2);
    }
    col = mix(vec3(0.8, 0.05, 0.03), col, smoothstep(0.0, 0.012, s));
    vec3 L = normalize(vec3(-0.4, 0.8, 0.45));
    vec3 hv = normalize(L + v);
    col = mix(col, vec3(1.0), step(0.985, dot(n, hv)) * 0.9);
    col += vec3(1.0, 0.75, 0.3) * Pulse * 0.6;
    vec3 stone = vec3(0.42, 0.4, 0.38) * (0.6 + 0.4 * f) * (0.85 + 0.3 * n3(vLocal * 9.0));
    col = mix(col, stone, Stone);
    // the glass glows a little on its own: darker at night, never black
    col *= mix(vLight.rgb, vec3(1.0), 0.45 * (1.0 - Stone));
    fragColor = vec4(col, 1.0);
}
