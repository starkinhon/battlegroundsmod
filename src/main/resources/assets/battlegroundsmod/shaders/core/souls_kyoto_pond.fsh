#version 150
// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.

// The pond's dark water over its mirror (forja/jujutsu-kyoto-ambiente amb.js pondMeshes, steps 3 and 5): the mirrored
// world drawn this frame into Refl (alpha 1 where there is something), and where there is nothing the sky seen in the
// water (from the horizon's color, the fog's, to the top's, and the sun's glint). R (Fresnel, with a high floor because
// the water is dark and deep) is how much is mirror: the lower the look, the more mirror.
uniform sampler2D Refl;
uniform vec2 ScreenSize;
uniform vec3 Deep;
uniform float Rfl;
uniform vec3 SkyTop;
uniform vec3 SkyHor;
uniform vec3 SunDir;
uniform float Night;
uniform float Fade;
uniform float UseRefl;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 vRel;

out vec4 fragColor;

void main() {
    vec3 d = normalize(vRel);
    vec3 r = vec3(d.x, -d.y, d.z);
    float e = clamp(r.y, 0.0, 1.0);
    vec3 sky = mix(SkyHor, SkyTop, pow(e, 0.55));
    float s = max(0.0, dot(r, SunDir));
    vec3 sunCol = mix(vec3(1.0, 0.88, 0.62), vec3(0.55, 0.6, 0.85), Night);
    float up = clamp(SunDir.y * 6.0 + 0.3, 0.0, 1.0);
    sky += sunCol * (pow(s, 900.0) * 2.5 + pow(s, 40.0) * 0.18) * up * (1.0 - Night * 0.7);
    vec4 rf = texture(Refl, gl_FragCoord.xy / ScreenSize) * UseRefl;
    vec3 mir = mix(sky, rf.rgb, rf.a);
    float f = 0.02 + 0.98 * pow(1.0 - abs(d.y), 5.0);
    float R = Rfl + (1.0 - Rfl) * f;
    // the fog: the mirror already has it by the mirrored distance (souls_kyoto_mirror); the dark water by its own
    float fg = clamp((length(vRel) - FogStart) / max(1.0, FogEnd - FogStart), 0.0, 1.0) * FogColor.a;
    vec3 deep = mix(Deep, FogColor.rgb, fg);
    fragColor = vec4(mix(deep, mir, R), Fade);
}
