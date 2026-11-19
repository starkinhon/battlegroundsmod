#version 150

// The pond mirror's color (see souls_kyoto_mirror.vsh), with the world's fog by the mirrored distance, like the preview.
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vColor;
in float vDist;

out vec4 fragColor;

void main() {
    float f = clamp((vDist - FogStart) / max(1.0, FogEnd - FogStart), 0.0, 1.0);
    fragColor = vec4(mix(vColor.rgb, FogColor.rgb, f * FogColor.a), 1.0);
}
