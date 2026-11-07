package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import net.minecraft.world.phys.Vec3;

// Shared by the Dragon Ball SubAlmas' server moves and their drawings (client/fx/DbFx): the Visualizador's hash and
// sphereDir (the very same numbers on both sides, so a rain of ki lands where the server hits) and the ki colors.
public final class Db {
    // Colors the server names in SubNet messages (DbFx.COLORS has the same order), the Visualizador's DBC.
    public static final int KAME = 0;
    public static final int KAME_W = 1;
    public static final int GOLD = 2;
    public static final int GOLD_W = 3;
    public static final int RED = 4;
    public static final int RED_W = 5;
    public static final int PURPLE = 6;
    public static final int PINK = 7;
    public static final int PINK_W = 8;
    public static final int GREEN = 9;
    public static final int WHITE = 10;
    public static final int ORANGE = 11;
    public static final int BLUE = 12;
    public static final int BIG_BANG = 13;

    private Db() {
    }

    // The Visualizador's hash(seed, k, salt): 0..1.
    public static float hash(int seed, int k, int salt) {
        int h = seed * 374761393 + k * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    // The Visualizador's sphereDir(seed, k): a point on the unit sphere.
    public static Vec3 sphereDir(int seed, int k) {
        double y = hash(seed, k, 20) * 2 - 1;
        double a = hash(seed, k, 21) * Math.PI * 2;
        double r = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    // Kakusandan: where bolt i of a rain falls (relative to the middle of the area, on the ground) and when, in ticks
    // after the ball splits. The client draws the same (DbFx.rain).
    public static Vec3 rainSpot(int seed, int i, double area) {
        return new Vec3((hash(i, 10 + seed, 2) - 0.5) * area, 0, (hash(i, 10 + seed, 3) - 0.5) * area);
    }

    public static float rainTime(int seed, int i, float spread) {
        return hash(i, 10 + seed, 1) * spread;
    }
}
