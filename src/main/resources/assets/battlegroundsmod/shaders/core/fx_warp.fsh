#version 150

// Bends the finished frame inside invisible spheres placed in the world (the "sky" bending of the Uro style).
// Each source is one mat4, column by column:
//   0: center in view space (xyz), radius
//   1: strength, age in seconds, wave speed in blocks per second, mode
//   2: twist axis in view space (xyz), unused
//   3: tint (rgb), tint amount
// Modes: 0 ripple (rings running outwards), 1 swirl around the axis, 2 lens (pulls the middle in, like a black hole),
// 3 whole screen (the edges of the screen wobble).
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 WarpProj;
uniform mat4 InverseProj;
uniform float DepthOn;
uniform float Count;
uniform mat4 Src0;
uniform mat4 Src1;
uniform mat4 Src2;
uniform mat4 Src3;
uniform mat4 Src4;
uniform mat4 Src5;
uniform mat4 Src6;
uniform mat4 Src7;

in vec2 uv;
out vec4 fragColor;

vec2 screenOf(vec3 view) {
    vec4 clip = WarpProj * vec4(view, 1.0);
    return clip.xy / clip.w * 0.5;
}

void main() {
    mat4 sources[8] = mat4[](Src0, Src1, Src2, Src3, Src4, Src5, Src6, Src7);
    vec2 ndc = uv * 2.0 - 1.0;
    // The projection carries the walking bob (a small move of the eye), so the ray starts where the eye really
    // is: the point on the near plane, heading to a point deeper along the same pixel.
    vec4 nearPoint = InverseProj * vec4(ndc, -1.0, 1.0);
    vec4 deepPoint = InverseProj * vec4(ndc, 0.5, 1.0);
    vec3 ro = nearPoint.xyz / nearPoint.w;
    vec3 rd = normalize(deepPoint.xyz / deepPoint.w - ro);

    float sceneDist = 1.0e9;
    if (DepthOn > 0.5) {
        float d = texture(DepthSampler, uv).r;
        if (d < 1.0) {
            vec4 scene = InverseProj * vec4(ndc, d * 2.0 - 1.0, 1.0);
            sceneDist = length(scene.xyz / scene.w - ro);
        }
    }

    vec2 off = vec2(0.0);
    vec3 tint = vec3(1.0);
    for (int i = 0; i < 8; i++) {
        if (float(i) >= Count) {
            break;
        }
        mat4 s = sources[i];
        vec3 c = s[0].xyz;
        float radius = max(s[0].w, 0.01);
        float strength = s[1].x;
        float age = s[1].y;
        float speed = s[1].z;
        float mode = s[1].w;

        if (mode > 2.5) {
            // Whole screen: the edges of the screen push outwards in quick waves.
            float r = length(ndc);
            float edge = smoothstep(0.35, 1.15, r);
            vec2 dir = r > 0.0001 ? ndc / r : vec2(0.0, 1.0);
            off += dir * edge * (0.5 + 0.5 * sin(r * 14.0 - age * 24.0)) * 0.025 * strength;
            tint = mix(tint, s[3].rgb, s[3].a * edge);
            continue;
        }

        vec3 oc = c - ro;
        float b = dot(rd, oc);
        float h = b * b - dot(oc, oc) + radius * radius;
        if (h < 0.0) {
            continue;
        }
        float root = sqrt(h);
        float t0 = b - root;
        float t1 = b + root;
        if (t1 < 0.0) {
            continue;
        }
        // From inside the sphere (your own Susanoo, say) the bending would sit on your eyes and smear the whole
        // screen, so it fades out as the eye gets near the middle and is never measured closer than a block.
        float inside = smoothstep(radius * 0.55, radius * 1.05, length(oc));
        if (inside <= 0.0) {
            continue;
        }
        float entry = max(t0, 0.0);
        if (sceneDist < entry) {
            continue; // a wall is in front of the sphere
        }
        // The point of the ray closest to the center: the middle of the sphere is where it bends most.
        vec3 q = ro + rd * max(clamp(b, entry, t1), 1.0);
        vec3 rel = q - c;
        float dist = length(rel) / radius;
        float fade = 1.0 - smoothstep(0.65, 1.0, dist);
        vec3 radial = length(rel) > 0.0001 ? rel / length(rel) : vec3(0.0, 1.0, 0.0);
        vec3 world;
        if (mode < 0.5) {
            // Rings running outwards from the center, strongest around the moving front.
            float wavelength = radius * 0.28;
            float front = age * speed;
            float x = dist * radius;
            float wave = sin((x - front) * 6.28318 / wavelength);
            float env = 0.35 + 0.65 * exp(-pow((x - front) / (radius * 0.3), 2.0));
            world = radial * wave * env * radius * 0.06;
        } else if (mode < 1.5) {
            vec3 axis = normalize(s[2].xyz + vec3(0.0, 0.0001, 0.0));
            vec3 around = normalize(cross(axis, rel) + vec3(0.00001));
            float k = (1.0 - dist) * (1.0 - dist);
            world = around * k * radius * 0.35 * (0.8 + 0.2 * sin(age * 5.0 + dist * 9.0));
        } else {
            float k = (1.0 - dist);
            vec3 around = normalize(cross(vec3(0.0, 0.0, 1.0), rel) + vec3(0.00001));
            world = (-rel * k * 0.45) + around * k * radius * 0.08 * sin(age * 3.0);
        }
        off += (screenOf(q + world) - screenOf(q)) * strength * fade * inside;
        tint = mix(tint, s[3].rgb, s[3].a * fade * inside);
    }

    // Never more than a few percent of the screen, whatever the sources add up to.
    float pull = length(off);
    if (pull > 0.06) {
        off *= 0.06 / pull;
    }
    vec2 at = clamp(uv + off, 0.001, 0.999);
    // A hair of color split along the bend, like light through glass.
    vec2 split = off * 0.08;
    vec3 col = vec3(texture(DiffuseSampler, clamp(at + split, 0.001, 0.999)).r, texture(DiffuseSampler, at).g,
            texture(DiffuseSampler, clamp(at - split, 0.001, 0.999)).b);
    fragColor = vec4(col * tint, 1.0);
}
