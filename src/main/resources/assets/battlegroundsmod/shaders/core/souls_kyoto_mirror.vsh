#version 150

// The Outono de Kyoto's pond mirror (client/fx/KyotoAirFx), the preview's mirrored world (forja/jujutsu-kyoto-ambiente
// amb.js pondMeshes, step 4): the blocks above the water near the pond, built once, mirrored here on the water's plane
// (y' = 2P - y). The ripple bends the reflection more the farther what is reflected is from the water. The light is
// worked out with the face's true normal (before mirroring): the reflection shows the underside, which is dark.
in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// the pond's origin from the camera (Position is relative to it), and in the world (for the ripple's phase)
uniform vec3 Origin;
uniform vec3 OriginW;
uniform float Plane;
uniform float Time;
uniform float Ripple;
uniform vec3 SunDir;
uniform float Night;
uniform float Bright;

out vec4 vColor;
out float vDist;

void main() {
    vec3 w = Position;
    w.y = 2.0 * Plane - w.y;
    vec3 wq = w + OriginW;
    float dd = max(0.0, Plane - w.y);
    w.x += (sin(wq.y * 1.35 + Time * 1.7 + wq.z * 0.23) + 0.5 * sin(wq.y * 2.9 - Time * 2.3)) * 0.010 * dd * Ripple;
    w.z += sin(wq.y * 1.05 - Time * 1.3 + wq.x * 0.19) * 0.006 * dd * Ripple;
    float lamDay = max(0.0, dot(Normal, SunDir));
    float lamNight = max(0.0, dot(Normal, vec3(0.32, 0.75, -0.43)));
    float light = mix(0.5 + 0.55 * lamDay, 0.28 + 0.2 * lamNight, Night);
    // (alpha 0: a block that gives light, lit by itself at night)
    if (Color.a < 0.5) {
        light = mix(light, 1.0, Night);
    }
    vColor = vec4(Color.rgb * light * Bright, 1.0);
    vec3 rel = w + Origin;
    vDist = length(rel);
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
}
