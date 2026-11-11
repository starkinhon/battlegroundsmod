package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

// The preview's 3D value noise (forja/shigaraki-tentaculos, NOISE: h31, vn, fbm), the same numbers on the server (which
// block goes when) and on the client (the cracks, the dry grey, the dust), so both agree on the Decadência's shape.
public final class DecayNoise {
    private DecayNoise() {
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    // h31: p = fract(p * (.1031, .1030, .0973)); p += dot(p, p.yzx + 33.33); fract((p.x + p.y) * p.z)
    public static float h31(float x, float y, float z) {
        x = fract(x * 0.1031f);
        y = fract(y * 0.1030f);
        z = fract(z * 0.0973f);
        float d = x * (y + 33.33f) + y * (z + 33.33f) + z * (x + 33.33f);
        x += d;
        y += d;
        z += d;
        return fract((x + y) * z);
    }

    // Value noise in -1..1, smoothstep between the lattice's corners.
    public static float vn(float px, float py, float pz) {
        float ix = (float) Math.floor(px), iy = (float) Math.floor(py), iz = (float) Math.floor(pz);
        float fx = px - ix, fy = py - iy, fz = pz - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float a = lerp(lerp(h31(ix, iy, iz), h31(ix + 1, iy, iz), fx), lerp(h31(ix, iy + 1, iz), h31(ix + 1, iy + 1, iz), fx), fy);
        float b = lerp(lerp(h31(ix, iy, iz + 1), h31(ix + 1, iy, iz + 1), fx), lerp(h31(ix, iy + 1, iz + 1), h31(ix + 1, iy + 1, iz + 1), fx), fy);
        return lerp(a, b, fz) * 2 - 1;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // How long (seconds) the Decadência takes to reach a block centre c from a seed at distance d with this speed, or a
    // negative number when the block is out of its reach (the preview's decayTime for one seed: noise n1 roughens the
    // edge, n2 and the veins make it run along cracks instead of a perfect sphere).
    public static float delay(float cx, float cy, float cz, float d, float speed, float maxR) {
        float n1 = vn(cx * 0.33f, cy * 0.33f, cz * 0.33f);
        if (d >= maxR + n1 * 1.6f) {
            return -1;
        }
        float n2 = vn(cx * 0.9f + 7, cy * 0.9f + 7, cz * 0.9f + 7);
        float vein = 1 - Math.abs(vn(cx * 0.22f + 3.1f, cy * 0.22f + 3.1f, cz * 0.22f + 3.1f));
        return d / speed * (0.8f + 0.45f * n2 - 0.35f * vein * vein);
    }

    // The Fim do Mundo's front reaching a column at horizontal distance dh (seconds after the touch), and a block at
    // height above the ground climbing at climb blocks per second.
    public static float wave(float cx, float cy, float cz, float dh, float speed, float above, float climb) {
        float n2 = vn(cx * 0.9f + 7, cy * 0.9f + 7, cz * 0.9f + 7);
        float vein = 1 - Math.abs(vn(cx * 0.22f + 3.1f, cy * 0.22f + 3.1f, cz * 0.22f + 3.1f));
        return dh / speed * (0.93f + 0.2f * n2 - 0.16f * vein * vein) + Math.max(above, 0) / climb;
    }
}
