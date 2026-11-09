#version 150

// Sukuna's Fuga: the glow at the fist, a sphere of light in the world (the preview's worldGlowMat), never a sprite
// turned to the camera. Along each view ray it takes the point closest to the centre: (1 - d/R)^2.2, added. That point's
// depth is written, so the body and the walls in front of it hide it. Only the box's far faces are kept (one per pixel,
// also with the camera inside the box).
in vec3 vRel;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Center;
uniform float Radius;
uniform vec3 Tint;

out vec4 fragColor;

float depthOf(vec3 rel) { vec4 c = ProjMat * ModelViewMat * vec4(rel, 1.0); return c.z / c.w * 0.5 + 0.5; }

void main() {
    if (gl_FrontFacing) {
        discard;
    }
    vec3 rd = normalize(vRel);
    float tc = max(0.0, dot(Center, rd));
    vec3 p = rd * tc;
    float d = length(p - Center) / Radius;
    if (d >= 1.0) {
        discard;
    }
    float g = pow(1.0 - d, 2.2);
    gl_FragDepth = depthOf(p);
    fragColor = vec4(Tint * g, 1.0);
}
