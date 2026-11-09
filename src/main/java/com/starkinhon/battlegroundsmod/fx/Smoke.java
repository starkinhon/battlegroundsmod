package com.starkinhon.battlegroundsmod.fx;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

// The smoke of each context, built from the procedural smoke (SmokeOptions / SmokeParticle). A copy of the emitters
// of the Fumaça Nova preview Stark approved on 2026-11-08 (/mnt/project-files/forja/fumaca-nova/tpl.html): same counts,
// speeds, sizes and lifetimes. Server side; every particle goes out with Fx.moving.
public final class Smoke {
    // Thread safe: bosses and skills call this from the server thread while the client runs in single player.
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int DIRT = 0xB9AA90;
    private static final double TAU = Math.PI * 2;

    private Smoke() {
    }

    private static double r() {
        return RANDOM.nextDouble();
    }

    private static double g() {
        return RANDOM.nextGaussian();
    }

    private static int ri(int bound) {
        return RANDOM.nextInt(bound);
    }

    private static void put(ServerLevel level, int style, int color, double alpha, double size, double grow, int life, double friction,
            double gravity, double flat, double stretch, Vec3 pos, Vec3 velocity) {
        Fx.moving(level, ModParticles.fx(style, color, (float) alpha, (float) size, (float) grow, life, (float) friction, (float) gravity,
                (float) flat, (float) stretch), pos, velocity);
    }

    private static void decal(ServerLevel level, int style, int color, double alpha, double size, double endScale, int life, int fade, Vec3 at) {
        put(level, style, color, alpha, size, endScale, life, 1, 0, 1, fade, at, Vec3.ZERO);
    }

    public static int mix(int a, int b, double k) {
        int r = (int) Math.round(((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k));
        int gr = (int) Math.round(((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k));
        int bl = (int) Math.round((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * k));
        return r << 16 | gr << 8 | bl;
    }

    private static Vec3 side(Vec3 dir) {
        Vec3 s = new Vec3(dir.z, 0, -dir.x);
        return s.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : s.normalize();
    }

    private static Vec3 flat(Vec3 dir, Vec3 fallback) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        return d.lengthSqr() < 1.0E-6 ? fallback : d.normalize();
    }

    // Dust in the color of the ground under the feet (grass kicks up dirt), a little washed out.
    public static int groundColor(net.minecraft.world.level.Level level, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
        for (int i = 0; i < 3; i++, pos = pos.below()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(BlockTags.DIRT)) {
                return DIRT;
            }
            MapColor map = state.getMapColor(level, pos);
            if (map == null || map == MapColor.NONE || map.col == 0) {
                return DIRT;
            }
            return mix(map.col, 0xC8BEB0, 0.35);
        }
        return DIRT;
    }

    // TP backwards (and the other blinks): the body left behind dissolves as shadow smoke, wind along the path, dust
    // kicked forward where it left and dragged where it lands, a quick shadow on the ground.
    public static void shadowStep(ServerLevel level, Vec3 from, Vec3 to, int soul) {
        Vec3 dir = flat(to.subtract(from), new Vec3(0, 0, 1));
        Vec3 side = side(dir);
        Vec3 path = to.subtract(from);
        int dust = groundColor(level, from);
        for (int i = 0; i < 9; i++) {
            double y = 0.15 + i / 8.0 * 1.75;
            put(level, SmokeOptions.INK, soul, 1, 0.32 + r() * 0.16, 0.7, 10 + ri(6), 0.86, 0, 1, 0, from.add(g() * 0.12, y, g() * 0.12),
                    dir.scale(0.05 + r() * 0.05).add(g() * 0.015, 0.01, g() * 0.015));
        }
        for (int i = 0; i < 9; i++) {
            Vec3 at = from.add(path.scale(r())).add(0, 0.3 + r() * 1.5, 0).add(side.scale(g() * 0.25));
            put(level, SmokeOptions.WISP, mix(0xFFFFFF, soul, 0.25), 0.8, 0.12 + r() * 0.06, 0, 6 + ri(4), 0.75, 0, 1, 9 + r() * 7, at,
                    path.normalize().scale(0.35 + r() * 0.2));
        }
        for (int i = 0; i < 7; i++) {
            double s = (r() - 0.5) * 1.4;
            put(level, SmokeOptions.DUST, dust, 0.9, 0.4 + r() * 0.25, 1.2, 14 + ri(8), 0.84, 0, 0.55, 0, from.add(0, 0.15, 0),
                    dir.scale(-(0.12 + r() * 0.1)).add(side.scale(s * 0.12)).add(0, 0.02 + r() * 0.03, 0));
        }
        int landDust = groundColor(level, to);
        for (int i = 0; i < 6; i++) {
            put(level, SmokeOptions.DUST, landDust, 0.85, 0.32 + r() * 0.2, 1, 12 + ri(6), 0.85, 0, 0.5, 0, to.add(0, 0.12, 0).add(dir.scale(-i * 0.25)),
                    dir.scale(0.07 + r() * 0.05).add(side.scale(g() * 0.04)));
        }
        for (int i = 0; i < 6; i++) {
            double a = r() * TAU;
            double rr = 0.8 + r() * 0.3;
            Vec3 at = to.add(Math.cos(a) * rr, 0.3 + r() * 1.5, Math.sin(a) * rr);
            put(level, SmokeOptions.INK, soul, 1, 0.25 + r() * 0.12, -0.4, 7 + ri(3), 0.8, 0, 1, 0, at,
                    new Vec3(to.x, at.y, to.z).subtract(at).scale(0.16));
        }
        decal(level, SmokeOptions.SHADOW, 0, 0.45, 0.9, 1.45, 18, 0, from);
    }

    // A fast dash (boss): dust kicked back at the feet and wind along the body.
    public static void dash(ServerLevel level, Vec3 feet, Vec3 dir, int soul) {
        Vec3 d = flat(dir, new Vec3(0, 0, 1));
        kick(level, feet, d, groundColor(level, feet), 1, 1, 1, 9);
        Vec3 side = side(d);
        for (int i = 0; i < 6; i++) {
            put(level, SmokeOptions.WISP, mix(0xFFFFFF, soul, 0.2), 0.75, 0.1, 0, 5 + ri(3), 0.8, 0, 1, 8 + r() * 6,
                    feet.add(0, 0.3 + r() * 1.4, 0).add(side.scale(g() * 0.3)), d.scale(-0.25));
        }
    }

    // Where a shadow vanishes: smoke and energy pulled into the body, a stain left on the ground.
    public static void vanish(ServerLevel level, Vec3 at, int soul) {
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * TAU + r() * 0.3;
            double rr = 1.1 + r() * 0.4;
            double y = 0.2 + r() * 1.8;
            Vec3 p = at.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            put(level, SmokeOptions.INK, soul, 1, 0.34 + r() * 0.2, -0.5, 8 + ri(3), 0.82, 0, 1, 0, p, at.add(0, y, 0).subtract(p).scale(0.2));
        }
        for (int i = 0; i < 10; i++) {
            double a = r() * TAU;
            double y = 0.2 + r() * 1.8;
            Vec3 p = at.add(Math.cos(a) * 1.6, y, Math.sin(a) * 1.6);
            put(level, SmokeOptions.ENERGY, soul, 0.9, 0.08, 0, 7, 0.85, 0, 1, 7, p, at.add(0, y, 0).subtract(p).scale(0.22));
        }
        decal(level, SmokeOptions.SHADOW, 0, 0.55, 1.2, 0.75, 26, 2, at);
    }

    // Where a shadow comes back: smoke bursting out with sparks of the soul color, a ring and a stain on the ground.
    public static void appear(ServerLevel level, Vec3 at, int soul) {
        for (int i = 0; i < 18; i++) {
            double a = r() * TAU;
            double y = 0.2 + r() * 1.7;
            double sp = 0.1 + r() * 0.12;
            put(level, SmokeOptions.INK, soul, 1, 0.35 + r() * 0.25, 1.1, 13 + ri(9), 0.8, 0, 1, 0, at.add(g() * 0.15, y, g() * 0.15),
                    new Vec3(Math.cos(a) * sp, (y - 1) * 0.04 + 0.01, Math.sin(a) * sp));
        }
        for (int i = 0; i < 14; i++) {
            Vec3 v = new Vec3(g(), Math.abs(g()) * 0.6 + 0.2, g()).normalize();
            put(level, SmokeOptions.SPECK, soul, 1, 0.1 + r() * 0.06, 0, 10 + ri(8), 0.86, 0.3, 1, 0, at.add(0, 1, 0), v.scale(0.2 + r() * 0.2));
        }
        int dust = groundColor(level, at);
        for (int i = 0; i < 8; i++) {
            double a = r() * TAU;
            put(level, SmokeOptions.DUST, dust, 0.8, 0.4 + r() * 0.2, 1.2, 14 + ri(6), 0.84, 0, 0.5, 0, at.add(0, 0.12, 0),
                    new Vec3(Math.cos(a) * 0.18, 0.02, Math.sin(a) * 0.18));
        }
        decal(level, SmokeOptions.RING, soul, 1.2, 0.4, 6.5, 10, 0, at);
        decal(level, SmokeOptions.SHADOW, 0, 0.5, 1.3, 1, 24, 0, at);
    }

    // The warning before a boss move, called every tick with progress 0 -> 1: a ring of light closing in at the
    // feet, low mist turning inwards along the ground, energy climbing to where the move comes from.
    public static void tell(ServerLevel level, Vec3 feet, Vec3 focus, int color, double progress) {
        double radius = 2.2 * (1 - progress) + 0.4;
        Vec3 at = feet.add(0, -0.08, 0);
        double in = Math.min(1, progress * 6 + 0.15);
        decal(level, SmokeOptions.RING, color, 0.75 * in, radius / 0.82, 1, 2, 1, at);
        decal(level, SmokeOptions.GLOW, color, 0.28 * in, 2.6, 1, 2, 1, at);
        int mist = mix(0x29242A, color, 0.35);
        for (int i = 0; i < 2; i++) {
            double a = r() * TAU;
            double rr = radius + 0.4 + r() * 0.5;
            Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a));
            Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a));
            put(level, SmokeOptions.BILLOW, mist, 0.55, 0.5 + r() * 0.3, 0.9, 16 + ri(6), 0.94, 0, 0.45, 0,
                    at.add(Math.cos(a) * rr, 0.22, Math.sin(a) * rr), inward.scale(0.05).add(tangent.scale(0.06)));
        }
        for (int i = 0; i < 2; i++) {
            double a = r() * TAU;
            Vec3 p = at.add(Math.cos(a) * radius, 0.13, Math.sin(a) * radius);
            put(level, SmokeOptions.ENERGY, color, 0.9, 0.07, 0, 8, 0.97, 0, 1, 6, p, focus.subtract(p).scale(0.1));
        }
        if (r() < 0.7) {
            Vec3 p = focus.add((r() - 0.5) * 2, (r() - 0.5) * 2, (r() - 0.5) * 2);
            put(level, SmokeOptions.SPECK, color, 1, 0.09, 0, 6, 0.95, 0, 1, 0, p, focus.subtract(p).scale(0.2));
        }
    }

    // Dust kicked back in a half moon, low and rolling on the ground. scale* come from the move's "poeira" settings.
    public static void kick(ServerLevel level, Vec3 feet, Vec3 dir, int color, double sizeScale, double lifeScale, double speedScale, int count) {
        Vec3 side = side(dir);
        for (int i = 0; i < count; i++) {
            double a = (r() - 0.5) * 2.4;
            Vec3 out = dir.scale(-Math.cos(a)).add(side.scale(Math.sin(a)));
            double sp = (0.16 + r() * 0.12) * speedScale;
            put(level, SmokeOptions.DUST, color, 0.9, (0.4 + r() * 0.3) * sizeScale, 1.3, (int) Math.max(4, (14 + ri(8)) * lifeScale), 0.86, 0, 0.5,
                    0, feet.add(0, 0.14, 0), out.scale(sp).add(0, 0.02 + r() * 0.03, 0));
        }
        decal(level, SmokeOptions.SHADOW, 0, 0.25, 0.6, 1.5, 12, 0, feet);
    }

    // Each tick of a dash on the ground: a short trail of dust at the feet (first ticks) and wind along the body.
    public static void dashTrail(ServerLevel level, Vec3 feet, Vec3 dir, int color, int tick) {
        Vec3 d = flat(dir, new Vec3(0, 0, 1));
        Vec3 side = side(d);
        if (tick <= 3) {
            for (int i = 0; i < 2; i++) {
                put(level, SmokeOptions.DUST, color, 0.7, 0.26 + r() * 0.15, 1, 10 + ri(5), 0.85, 0, 0.5, 0, feet.add(0, 0.1, 0),
                        side.scale(g() * 0.05).add(0, 0.02, 0));
            }
        }
        for (int i = 0; i < 3; i++) {
            put(level, SmokeOptions.WISP, 0xFFFFFF, 0.7, 0.1, 0, 5 + ri(3), 0.8, 0, 1, 8 + r() * 6, feet.add(0, 0.3 + r() * 1.4, 0).add(side.scale(g() * 0.3)),
                    d.scale(-0.25));
        }
    }

    // Jump in the air: a ring of wind opening under the feet, a flat disc of air and wisps thrown out.
    public static void airJump(ServerLevel level, Vec3 feet, int color, double sizeScale) {
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * TAU + r() * 0.3;
            double sp = 0.16 + r() * 0.08;
            put(level, SmokeOptions.BILLOW, color, 0.6, (0.45 + r() * 0.2) * sizeScale, 1.2, 12 + ri(5), 0.86, 0, 0.4, 0,
                    feet.add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3), new Vec3(Math.cos(a) * sp, -0.02, Math.sin(a) * sp));
        }
        for (int i = 0; i < 8; i++) {
            double a = r() * TAU;
            put(level, SmokeOptions.WISP, 0xFFFFFF, 0.7, 0.08, 0, 6, 0.78, 0, 1, 6, feet.add(Math.cos(a) * 0.3, -0.1, Math.sin(a) * 0.3),
                    new Vec3(Math.cos(a) * 0.3, -0.05, Math.sin(a) * 0.3));
        }
        decal(level, SmokeOptions.RING, 0xD8E6FF, 0.9, 0.3, 6, 9, 0, feet.add(0, -0.04, 0));
    }

    // Boss entrance / phase change, every tick while it stands: shadow smoke and energy climbing round it in a spiral.
    public static void aura(ServerLevel level, Vec3 at, int soul, int age) {
        for (int i = 0; i < 2; i++) {
            double a = r() * TAU;
            double rr = 0.7 + r() * 0.6;
            put(level, SmokeOptions.INK, soul, 1, 0.3 + r() * 0.15, 0.5, 16 + ri(6), 0.95, 0, 1, 0, at.add(Math.cos(a) * rr, r() * 0.3, Math.sin(a) * rr),
                    new Vec3(-Math.sin(a) * 0.07, 0.09 + r() * 0.06, Math.cos(a) * 0.07));
        }
        for (int i = 0; i < 2; i++) {
            double a = r() * TAU;
            double rr = 0.5 + r() * 0.6;
            put(level, SmokeOptions.ENERGY, soul, 0.8, 0.07, 0, 9, 0.96, 0, 1, 7, at.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr),
                    new Vec3(-Math.sin(a) * 0.04, 0.18 + r() * 0.1, Math.cos(a) * 0.04));
        }
        if (age % 6 == 0) {
            decal(level, SmokeOptions.RING, soul, 0.7, 1.2, 2, 12, 0, at);
        }
    }

    // The burst that ends an entrance or a phase change: a ring of smoke rolling out along the ground, a dark
    // column going up and a wave of light in the soul color.
    public static void shockwave(ServerLevel level, Vec3 at, int soul, double radius) {
        int smoke = mix(0x332F36, soul, 0.15);
        for (int i = 0; i < 30; i++) {
            double a = i / 30.0 * TAU + r() * 0.2;
            double sp = (0.4 + r() * 0.15) * radius / 6;
            put(level, SmokeOptions.BILLOW, smoke, 0.9, 0.7 + r() * 0.4, 1.6, 26 + ri(10), 0.87, 0, 0.55, 0, at.add(Math.cos(a) * 0.6, 0.25, Math.sin(a) * 0.6),
                    new Vec3(Math.cos(a) * sp, 0.01, Math.sin(a) * sp));
        }
        for (int i = 0; i < 9; i++) {
            put(level, SmokeOptions.INK, soul, 1, 0.5 + r() * 0.3, 1.2, 22 + ri(8), 0.93, 0, 1, 0, at.add(g() * 0.3, 0.4 + i * 0.35, g() * 0.3),
                    new Vec3(g() * 0.02, 0.1 + r() * 0.06, g() * 0.02));
        }
        decal(level, SmokeOptions.RING, soul, 1.3, 0.5, radius * 2.5, 14, 0, at);
        decal(level, SmokeOptions.GLOW, soul, 0.8, radius * 0.7, 1, 10, 0, at);
        decal(level, SmokeOptions.SHADOW, 0, 0.5, 1.6, 1, 50, 0, at);
    }

    // The quick puff where someone vanishes or comes back (Bop, Agarrão): shadow smoke with the rim in the soul color.
    public static void puff(ServerLevel level, Vec3 at, int soul, double height, boolean back) {
        double h = Math.max(0.6, height * 0.9);
        for (int i = 0; i < 9; i++) {
            double y = 0.2 + r() * h;
            Vec3 v = back ? new Vec3(g() * 0.05, 0, g() * 0.05) : new Vec3(g() * 0.03, 0.01, g() * 0.03);
            put(level, SmokeOptions.INK, soul, 1, 0.28 + r() * 0.15, back ? 0.9 : 0.6, 8 + ri(5), 0.84, 0, 1, 0, at.add(g() * 0.15, y, g() * 0.15), v);
        }
        if (back) {
            for (int i = 0; i < 8; i++) {
                Vec3 v = new Vec3(g(), Math.abs(g()) * 0.5, g()).normalize();
                put(level, SmokeOptions.SPECK, soul, 1, 0.08, 0, 8 + ri(5), 0.85, 0, 1, 0, at.add(0, 1, 0), v.scale(0.15 + r() * 0.1));
            }
        }
    }

    // Dust wave of a hit on the ground: rolling low along the ground, a lighter layer above and grains of dirt.
    public static void groundWave(ServerLevel level, Vec3 at, int color, double speed) {
        for (int i = 0; i < 40; i++) {
            double a = i / 40.0 * TAU + r() * 0.1;
            double sp = speed * (0.85 + r() * 0.3);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            put(level, SmokeOptions.DUST, color, 0.95, 0.55 + r() * 0.3, 1.4, 16 + ri(8), 0.9, 0, 0.55, 0, at.add(out.scale(0.6)).add(0, 0.2, 0),
                    out.scale(sp).add(0, 0.015, 0));
        }
        for (int i = 0; i < 18; i++) {
            double a = r() * TAU;
            double sp = speed * (0.5 + r() * 0.3);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            put(level, SmokeOptions.DUST, mix(color, 0xFFFFFF, 0.15), 0.6, 0.5 + r() * 0.3, 1.2, 14 + ri(8), 0.9, 0, 1, 0, at.add(out.scale(0.4)).add(0, 0.6, 0),
                    out.scale(sp).add(0, 0.05, 0));
        }
        int grain = mix(color, 0x000000, 0.3);
        for (int i = 0; i < 16; i++) {
            Vec3 v = new Vec3(g(), 1.5 + r(), g()).normalize();
            put(level, SmokeOptions.SPECK, grain, 1, 0.07, 0, 14 + ri(6), 0.97, 1, 1, 0, at.add(0, 0.2, 0), v.scale(0.25 + r() * 0.15));
        }
        decal(level, SmokeOptions.SHADOW, 0, 0.45, 1.3, 1.25, 40, 0, at);
    }

    // A small puff of ground dust at the feet (running trails).
    public static void feetDust(ServerLevel level, Vec3 feet, Vec3 velocity) {
        put(level, SmokeOptions.DUST, groundColor(level, feet), 0.8, 0.28 + r() * 0.15, 1.1, 9 + ri(4), 0.85, 0, 0.5, 0, feet.add(0, 0.12, 0), velocity);
    }
}
