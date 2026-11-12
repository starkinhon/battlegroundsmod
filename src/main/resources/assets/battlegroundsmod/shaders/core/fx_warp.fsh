#version 150

// Bends the finished frame inside invisible spheres placed in the world (the "sky" bending of the Uro style).
// Each source is one mat4, column by column:
//   0: center in view space (xyz), radius
//   1: strength, age in seconds, wave speed in blocks per second, mode
//   2: twist axis in view space (xyz), unused
//   3: tint (rgb), tint amount
// Modes: 0 ripple (rings running outwards), 1 swirl around the axis, 2 lens (pulls the middle in, like a black hole),
// 3 whole screen (the edges of the screen wobble), 4 whirl only around the sphere (Gojo's Purple in flight),
// 5 shock ring (Luffy's page: a ring on the screen that pushes out in front and pulls behind, each color shifted its own
//   amount, with a glow on the ring; column 0 w = power, column 1 = glow, age, duration, 5, column 3 rgb = glow color).
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
    vec2 shockOff = vec2(0.0);
    vec3 shockGlow = vec3(0.0);
    float aspect = WarpProj[1][1] / max(WarpProj[0][0], 1.0e-4);
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

        if (mode > 4.5) {
            // The page's SHOCK pass, line by line (its radius and width come from the distance and the power).
            float t = age / max(speed, 0.01);
            if (t >= 1.0 || c.z > -0.05) {
                continue;
            }
            float pw = s[0].w;
            float e = 1.0 - pow(1.0 - t, 3.0);
            float dist = length(c);
            vec2 center = screenOf(c) + 0.5;
            float uRadius = (0.03 + 1.6 * e) / max(dist, 1.5) * pw;
            float uWidth = (0.05 + 0.05 * e) * pw / max(dist / 4.0, 0.6);
            float uStrength = 0.05 * (1.0 - t) * min(pw, 2.5);
            vec2 d = uv - center;
            d.x *= aspect;
            float rr = length(d);
            float sv = (rr - uRadius) / max(uWidth, 1.0e-4);
            float prof = sv * exp(-sv * sv) * 2.33;
            vec2 dir = d / max(rr, 1.0e-4);
            dir.x /= aspect;
            shockOff += dir * prof * uStrength;
            shockGlow += s[3].rgb * exp(-sv * sv * 1.5) * strength * (1.0 - t) * (1.0 - t);
            continue;
        }
        if (mode > 2.5 && mode < 3.5) {
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
        } else if (mode > 3.5) {
            // Gojo's Purple in flight: a whirl only around the ball, its middle left untouched.
            vec3 axis = normalize(s[2].xyz + vec3(0.0, 0.0001, 0.0));
            vec3 around = normalize(cross(axis, rel) + vec3(0.00001));
            float k = smoothstep(0.3, 0.5, dist) * (1.0 - dist) * 2.0;
            world = around * k * radius * 0.3 * (0.8 + 0.2 * sin(age * 5.0 + dist * 9.0));
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
    vec3 col = vec3(texture(DiffuseSampler, clamp(at + split - shockOff * 1.18, 0.001, 0.999)).r,
            texture(DiffuseSampler, clamp(at - shockOff, 0.001, 0.999)).g,
            texture(DiffuseSampler, clamp(at - split - shockOff * 0.82, 0.001, 0.999)).b);
    fragColor = vec4(col * tint + shockGlow, 1.0);
}
