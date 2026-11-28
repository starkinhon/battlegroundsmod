// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;

// The shape of Itadori's Teia de Tesoura (the X), the same on the server (who is on a thread, where the ground bursts) and
// on the client (client/fx/Jj2Fx draws it), from one seed: N spokes out from the palm, each its own length, and rings
// between them at a few radii, each ring piece bent a little in and running a touch past its threads (preview
// forja/itadori-kit/shinjuku.js, SCENES.teia: R 6, N 8, rings 1.8 / 3.6 / 5.2; held X, the big one: R 9, N 12, rings 2.2 /
// 4.4 / 6.6 / 8.2, threads 1.2x thicker).
public final class ItadoriWeb {
    private static final float[] RINGS = {1.8f, 3.6f, 5.2f};
    private static final float[] RINGS_BIG = {2.2f, 4.4f, 6.6f, 8.2f};

    public static float radius(boolean big) {
        return big ? 9 : 6;
    }

    // One thread, offsets from the palm on the ground (x, z): from (ax, az) to (bx, bz). r0 and r1: the radii where it
    // starts and ends (a ring's piece has both at its ring); spoke: one of the N lines out; thick: its width.
    public record Line(float ax, float az, float bx, float bz, float r0, float r1, boolean spoke, float thick, int k) {
    }

    private ItadoriWeb() {
    }

    public static List<Line> lines(int seed, boolean big) {
        float rad = radius(big);
        int count = big ? 12 : 8;
        float[] rings = big ? RINGS_BIG : RINGS;
        float th = big ? 1.2f : 1;
        List<Line> out = new ArrayList<>();
        float[] dx = new float[count];
        float[] dz = new float[count];
        float[] len = new float[count];
        for (int i = 0; i < count; i++) {
            float a = (i / (float) count + (hash(seed + i, 3, big ? 9 : 5) - 0.5f) * 0.06f) * Mth.TWO_PI;
            dx[i] = Mth.sin(a);
            dz[i] = -Mth.cos(a);
            len[i] = rad * (0.82f + 0.28f * hash(seed + i, 7, 1));
            out.add(new Line(dx[i] * 0.35f, dz[i] * 0.35f, dx[i] * len[i], dz[i] * len[i], 0.35f, len[i], true,
                    (0.42f + 0.3f * hash(seed + i, 5, 2)) * th, out.size()));
        }
        for (int j = 0; j < rings.length; j++) {
            float r = rings[j];
            for (int i = 0; i < count; i++) {
                int n = (i + 1) % count;
                if (r > Math.min(len[i], len[n]) - 0.3f || hash(seed + i, j, 77) < 0.12f) {
                    continue;
                }
                float rr0 = r * (1 + (hash(seed + i, j, 4) - 0.5f) * 0.12f);
                float rr1 = r * (1 + (hash(seed + n, j, 4) - 0.5f) * 0.12f);
                float ax = dx[i] * rr0;
                float az = dz[i] * rr0;
                float bx = dx[n] * rr1;
                float bz = dz[n] * rr1;
                float mx = (ax + bx) * 0.5f * 0.93f;
                float mz = (az + bz) * 0.5f * 0.93f;
                float thick = (0.2f + 0.12f * hash(seed + i, j, 9)) * th;
                piece(out, ax, az, mx, mz, r, thick);
                piece(out, mx, mz, bx, bz, r, thick);
            }
        }
        return out;
    }

    // A ring piece runs 6% past each end, as the Sukuna's web does.
    private static void piece(List<Line> out, float ax, float az, float bx, float bz, float r, float thick) {
        float ex = (bx - ax) * 0.06f;
        float ez = (bz - az) * 0.06f;
        out.add(new Line(ax - ex, az - ez, bx + ex, bz + ez, r, r, false, thick, out.size()));
    }

    // How far (x, z offsets from the palm) is from the line, on the ground.
    public static double distance(Line l, double x, double z) {
        double vx = l.bx() - l.ax();
        double vz = l.bz() - l.az();
        double t = Mth.clamp(((x - l.ax()) * vx + (z - l.az()) * vz) / Math.max(1.0E-6, vx * vx + vz * vz), 0, 1);
        double px = l.ax() + vx * t - x;
        double pz = l.az() + vz * t - z;
        return Math.sqrt(px * px + pz * pz);
    }
}
