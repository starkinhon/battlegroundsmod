package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// The shape of the piece of ground the Ilha Telecinética tears out (the preview's makeIsland: a lumpy rim, a root of
// rock under it like a bowl, and whatever stands on it, an Ajisa tree included). Shared by the server (which takes the
// blocks away) and the client (which copies them first and draws the copy flying), so both see the same piece.
public final class FzIsland {
    // How high above the ground the copy goes (trees and houses standing on it).
    public static final int ABOVE = 7;

    // One column of the piece: dx, dz from the middle block, how deep it goes under the ground block (1 = only that one).
    public record Column(int dx, int dz, int depth) {
    }

    private FzIsland() {
    }

    // The ground block under the point the island is torn from.
    public static int topY(Vec3 at) {
        return Mth.floor(at.y - 0.01);
    }

    public static List<Column> columns(Vec3 at, double radius, double depth) {
        int cx = Mth.floor(at.x), cz = Mth.floor(at.z);
        // Its own lumps, from where it is (the same on both sides).
        double s1 = hash(cx, cz, 1) * 6.283, s2 = hash(cx, cz, 2) * 6.283;
        List<Column> out = new ArrayList<>();
        int m = (int) Math.ceil(radius * 1.2) + 1;
        for (int dx = -m; dx <= m; dx++) {
            for (int dz = -m; dz <= m; dz++) {
                double x = dx + 0.5 - (at.x - cx), z = dz + 0.5 - (at.z - cz);
                double r = Math.sqrt(x * x + z * z), a = Math.atan2(z, x);
                double rim = radius * (0.9 + 0.12 * Math.sin(3 * a + s1) + 0.06 * Math.sin(5 * a + s2));
                if (r >= rim) {
                    continue;
                }
                // the bowl of the preview: r = rim (1 - u)^0.75, its bottom at depth u^1.15
                double u = 1 - Math.pow(r / rim, 1 / 0.75);
                int d = 1 + (int) Math.floor(depth * Math.pow(Math.max(0, u), 1.15) + hash(cx + dx, cz + dz, 3) * 0.6);
                out.add(new Column(dx, dz, d));
            }
        }
        return out;
    }

    public static double hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }
}
