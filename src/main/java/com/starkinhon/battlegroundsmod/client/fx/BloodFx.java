// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.combat.Blood;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// The pixel blood (Stark 2026-11-23, approved on the web preview "Sangue Pixel"): square drops like a Minecraft particle,
// stains that sit on the block's own 16 pixel grid, dry in three shades and go away in about 20 seconds, a small pool
// under a body that died, and (gore) a few flesh bits on the finishers. Little of it: a detail you notice in the fight,
// never the screen covered. Nothing flashes.
//
// The caps (Stark: "tem q ter um teto"): every body has its own bucket of drops (a strong blow can take a lot of it, a
// barrage of hits empties it and then only drips come out), and the whole screen has a limit of drops in the air, drops
// born per tick, stain pixels and bits. Low effect quality cuts all of it.
public final class BloodFx {
    private static final RandomSource R = RandomSource.create();

    // ---- the caps
    private static final int MAX_DROPS = 400;
    private static final int MAX_BORN_PER_TICK = 90;
    private static final int MAX_STAINS = 3000;
    private static final int MAX_BITS = 24;
    private static final float BUCKET_CAP = 90;
    private static final float BUCKET_REFILL = 1.5f; // per tick: 30 drops a second for one body, at most
    private static final double MAX_DISTANCE = 64;

    // ---- the motion (per tick; the preview's 20 blocks/s² gravity and 0.5/s drag)
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.975;
    private static final int DRY1 = 120, DRY2 = 264;

    private static final List<Drop> DROPS = new ArrayList<>();
    private static final LinkedHashMap<Key, Stain> STAINS = new LinkedHashMap<>();
    private static final List<Bit> BITS = new ArrayList<>();
    private static final List<Pool> POOLS = new ArrayList<>();
    private static final List<Run> RUNS = new ArrayList<>();
    private static final List<Later> LATER = new ArrayList<>();
    private static final Map<Integer, float[]> BUCKETS = new HashMap<>();

    private static int tick;
    private static int bornThisTick;
    private static ClientLevel lastLevel;

    private BloodFx() {
    }

    private static final class Drop {
        double x, y, z, xo, yo, zo, vx, vy, vz;
        int size, band, color, age;
    }

    // A stain pixel: which face (axis 0 x, 1 y, 2 z; sign = the face's normal), the plane and the pixel on it.
    private record Key(int axis, int sign, long plane16, long a, long b) {
    }

    private static final class Stain {
        double plane;
        int band, color, born, die, light;
        boolean shine;
    }

    private static final class Bit {
        double x, y, z, xo, yo, zo, vx, vy, vz;
        float size, rx, ry, rz, wx, wy, wz;
        int color, age;
        boolean rest;
    }

    private static final class Pool {
        double x, z, plane;
        int color, start, dur;
        float radius, seed;
    }

    // A drip: down a wall pixel by pixel (entity < 0), or drops falling from a wound (entity = the body's id).
    private static final class Run {
        int entity = -1, axis, sign, left, next, band, color;
        double plane, height;
        long a, b;
    }

    private record Later(int at, Runnable run) {
    }

    // ------------------------------------------------------------------ a hit from the server

    public static void hit(ModPayloads.BloodHit h) {
        int amount = FxConfig.blood();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (amount <= 0 || level == null || h.color() == 0 || !(level.getEntity(h.entityId()) instanceof LivingEntity e)) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (e.position().distanceTo(cam) > Math.min(MAX_DISTANCE, FxConfig.effectRange())) {
            return;
        }
        float mult = (amount == 1 ? 0.55f : amount == 3 ? 1.6f : 1f) * (FxConfig.effectQuality() == 0 ? 0.5f : FxConfig.effectQuality() == 1 ? 0.75f : 1f);
        boolean fatal = (h.flags() & Blood.FLAG_FATAL) != 0;
        boolean gore = FxConfig.gore();
        int kind = h.kind();
        float power = kind == Blood.BANG || kind == Blood.CLEAVE ? Mth.clamp(h.power(), 1f, 1.3f) : Mth.clamp(h.power(), 0.6f, 1.8f);
        Body b = new Body(e, h.dirX(), h.dirZ(), h.color());
        Vec3 cut = new Vec3(h.cutX(), h.cutY(), h.cutZ());

        switch (kind) {
            case Blood.PUNCH, Blood.HEAVY -> {
                boolean heavy = kind == Blood.HEAVY;
                int n = budget(e, Math.round((heavy ? 9 : 4) * power * mult));
                Vec3 o = b.at(0.82);
                for (int i = 0; i < n; i++) {
                    drop(o.add(b.side.scale(rnd(-0.1, 0.1))), b.v(rnd(1.2, heavy ? 4.2 : 3.0), rnd(-1.3, 1.3), rnd(1.0, heavy ? 3.4 : 2.6)),
                            sizePick(heavy), R.nextFloat() < 0.6f ? 0 : 1, b.color);
                }
                wound(e, 0.8, heavy ? 2 : 1, 9, b.color);
            }
            case Blood.SLASH -> slash(b, cut, budget(e, Math.round(11 * power * mult)), 1.4, 3.6, false);
            case Blood.CLEAVE -> {
                slash(b, cut, budget(e, Math.round(9 * power * mult)), 1.5, 4.0, true);
                if (gore && R.nextFloat() < 0.75f * Math.min(1, mult)) {
                    bit(b.at(0.6), b.v(rnd(-0.6, 2.0), rnd(-1.6, 1.6), rnd(1.2, 2.6)), b.color);
                }
            }
            case Blood.PIERCE -> {
                int n = budget(e, Math.round(5 * power * mult));
                Vec3 o = b.at(0.6);
                for (int i = 0; i < n; i++) {
                    drop(o, b.v(rnd(1.0, 3.2), rnd(-0.8, 0.8), rnd(0.3, 1.6)), sizePick(false), R.nextFloat() < 0.5f ? 0 : 1, b.color);
                }
                wound(e, 0.6, 1, 12, b.color);
            }
            case Blood.EXPLOSION -> {
                int n = budget(e, Math.round(6 * power * mult));
                Vec3 o = b.at(0.5);
                for (int i = 0; i < n; i++) {
                    double an = R.nextDouble() * Math.PI * 2, sp = rnd(1.0, 3.0) / 20;
                    drop(o, new Vec3(Math.cos(an) * sp, rnd(1.0, 3.0) / 20, Math.sin(an) * sp), sizePick(false), 1, b.color);
                }
            }
            case Blood.BANG -> {
                Vec3 front = b.at(0.62).subtract(b.dir.scale(0.2)), back = b.at(0.62).add(b.dir.scale(0.2));
                int n = budget(e, Math.round(27 * power * mult));
                int nf = Math.max(1, n * 4 / 27), nb = n * 16 / 27, nfar = n - nf - nb;
                for (int i = 0; i < nf; i++) {
                    drop(front, b.v(-rnd(0.4, 1.4), rnd(-0.6, 0.6), rnd(0.3, 1.4)), 1, 0, b.color);
                }
                for (int i = 0; i < nb; i++) {
                    double s = rnd(3, 7);
                    drop(back, b.v(s, rnd(-1, 1) * s * 0.3, rnd(0.15, 0.55) * s), sizePick(true), R.nextFloat() < 0.5f ? 0 : 1, b.color);
                }
                // a few go far and mark the wall behind
                for (int i = 0; i < nfar; i++) {
                    drop(back, b.v(rnd(8, 10.5), rnd(-1.6, 1.6), rnd(1.4, 3)), sizePick(false), 0, b.color);
                }
                if (gore) {
                    int bits = Math.max(1, Math.round(2 * Math.min(1, mult)));
                    for (int i = 0; i < bits; i++) {
                        bit(back, b.v(rnd(3, 5), rnd(-1.2, 1.2), rnd(1, 2.5)), b.color);
                    }
                }
                wound(e, 0.62, 3, 10, b.color);
                pool(e, fatal ? 0.42f : 0.25f, 14, b.color);
            }
            case Blood.BURST -> {
                // Inumaki's "Explode" (the approved preview): a jet out of the swollen body, a few high, bits and a pool
                Vec3 o = b.at(0.6);
                int n = budget(e, Math.round((fatal ? 40 : 22) * mult));
                for (int i = 0; i < n; i++) {
                    drop(o, burstV(0, 2.5, 8), sizePick(true), R.nextFloat() < 0.3f ? 1 : 0, b.color);
                }
                for (int i = 0, high = Math.round((fatal ? 14 : 6) * Math.min(1, mult)); i < high; i++) {
                    drop(o, burstV(0, 6, 4), sizePick(true), 0, b.color);
                }
                if (gore) {
                    for (int i = 0, bits = fatal ? 8 : 2; i < bits; i++) {
                        bit(o.add(rnd(-0.2, 0.2), rnd(-0.3, 0.3), rnd(-0.2, 0.2)), new Vec3(rnd(-4.5, 4.5), rnd(3, 9), rnd(-4.5, 4.5)).scale(1 / 20.0), b.color);
                    }
                }
                if (fatal) {
                    pool(e, 1.6f, 6, b.color);
                } else {
                    wound(e, 0.6, 3, 8, b.color);
                }
            }
            case Blood.SQUASH -> {
                // Inumaki's "Get crushed" finishing: the body becomes a pool, drops spraying round it
                Vec3 o = e.position().add(0, 0.15, 0);
                int n = budget(e, Math.round(26 * mult));
                for (int i = 0; i < n; i++) {
                    drop(o, burstV(0, 2.2, 4.5), sizePick(true), R.nextFloat() < 0.3f ? 1 : 0, b.color);
                }
                if (gore) {
                    for (int i = 0; i < 3; i++) {
                        bit(o.add(0, 0.05, 0), new Vec3(rnd(-2.5, 2.5), rnd(3, 6), rnd(-2.5, 2.5)).scale(1 / 20.0), b.color);
                    }
                }
                pool(e, 1.35f, 2, b.color);
            }
            default -> {
            }
        }
        if (fatal && kind != Blood.BANG && kind != Blood.BURST && kind != Blood.SQUASH) {
            // the killing blow: a small pool where the body falls and, with gore, a bit or two
            pool(e, kind == Blood.CLEAVE ? 0.36f : kind == Blood.HEAVY || kind == Blood.SLASH ? 0.3f : 0.22f, 12, b.color);
            if (gore && (kind == Blood.CLEAVE || kind == Blood.HEAVY || kind == Blood.SLASH)) {
                for (int i = 0; i < 1 + R.nextInt(2); i++) {
                    bit(b.at(0.55), b.v(rnd(0.5, 2.5), rnd(-1.5, 1.5), rnd(1.2, 2.6)), b.color);
                }
            }
        }
    }

    // Where a body bleeds from and which way: dir = the blow's way (the drops fly on with it), side = across.
    private static final class Body {
        final Entity e;
        final Vec3 dir, side;
        final int color;

        Body(Entity e, float dx, float dz, int color) {
            this.e = e;
            this.color = color;
            Vec3 d = new Vec3(dx, 0, dz);
            if (d.lengthSqr() < 1e-4) {
                // no attacker: out the back
                d = Vec3.directionFromRotation(0, e.getYRot()).scale(-1);
                d = new Vec3(d.x, 0, d.z);
            }
            this.dir = d.normalize();
            this.side = new Vec3(-dir.z, 0, dir.x);
        }

        Vec3 at(double height) {
            return e.position().add(0, e.getBbHeight() * height, 0);
        }

        // along the blow, across it, up: blocks per second like the preview, turned into per tick
        Vec3 v(double along, double across, double up) {
            return dir.scale(along).add(side.scale(across)).add(0, up, 0).scale(1 / 20.0);
        }
    }

    private static void slash(Body b, Vec3 cut, int n, double lo, double hi, boolean cleave) {
        Vec3 axis;
        if (cut.lengthSqr() > 1e-4) {
            axis = cut.normalize();
        } else {
            double a = (R.nextBoolean() ? 1 : -1) * rnd(0.5, 1.1);
            axis = b.side.scale(Math.cos(a)).add(0, Math.sin(a), 0).normalize();
        }
        double len = Math.min(0.75, b.e.getBbWidth() * 1.1);
        Vec3 center = b.at(0.6);
        for (int i = 0; i < n; i++) {
            Vec3 o = center.add(axis.scale(rnd(-0.5, 0.5) * len));
            double sp = rnd(lo, hi), sgn = R.nextFloat() < 0.7f ? 1 : -1;
            Vec3 v = axis.scale(sp * sgn).add(0, Math.abs(axis.y * sp) * 0.4 + rnd(0.6, cleave ? 2.4 : 2.2), 0)
                    .add(b.dir.scale(cleave ? rnd(-0.9, 2.6) : rnd(-0.8, 1.4))).add(b.side.scale(rnd(-0.6, 0.6)));
            drop(o, v.scale(1 / 20.0), sizePick(cleave), R.nextFloat() < 0.55f ? 0 : 1, b.color);
        }
        wound(b.e, 0.6, cleave ? 2 : 3, 10, b.color);
    }

    // The preview's PB.burst: up along y at `up` blocks/s (0.6..1.4 of it) with a spread, per tick.
    private static Vec3 burstV(double along, double up, double spread) {
        double k = 0.6 + 0.8 * R.nextDouble();
        return new Vec3((R.nextDouble() - 0.5) * spread, up * k + R.nextDouble() * spread * 0.8, (R.nextDouble() - 0.5) * spread).scale(1 / 20.0);
    }

    private static double rnd(double a, double b) {
        return a + R.nextDouble() * (b - a);
    }

    private static int sizePick(boolean big) {
        float r = R.nextFloat();
        return big ? (r < 0.5f ? 1 : r < 0.92f ? 2 : 3) : (r < 0.7f ? 1 : 2);
    }

    // How many of n drops this body may still shed now (its bucket refills over time).
    private static int budget(Entity e, int n) {
        float[] bucket = BUCKETS.computeIfAbsent(e.getId(), k -> new float[] {BUCKET_CAP, tick});
        bucket[0] = Math.min(BUCKET_CAP, bucket[0] + (tick - bucket[1]) * BUCKET_REFILL);
        bucket[1] = tick;
        int take = Math.max(0, Math.min(n, (int) bucket[0]));
        bucket[0] -= take;
        return take;
    }

    private static void drop(Vec3 p, Vec3 v, int size, int band, int color) {
        int cap = FxConfig.effectQuality() == 0 ? MAX_DROPS / 2 : MAX_DROPS;
        if (DROPS.size() >= cap || bornThisTick >= MAX_BORN_PER_TICK) {
            return;
        }
        bornThisTick++;
        Drop d = new Drop();
        d.x = d.xo = p.x;
        d.y = d.yo = p.y;
        d.z = d.zo = p.z;
        d.vx = v.x;
        d.vy = v.y;
        d.vz = v.z;
        d.size = size;
        d.band = band;
        d.color = color;
        DROPS.add(d);
    }

    private static void bit(Vec3 p, Vec3 v, int color) {
        if (BITS.size() >= MAX_BITS || FxConfig.effectQuality() == 0) {
            return;
        }
        Bit b = new Bit();
        b.x = b.xo = p.x;
        b.y = b.yo = p.y;
        b.z = b.zo = p.z;
        b.vx = v.x;
        b.vy = v.y;
        b.vz = v.z;
        b.size = (float) rnd(1.4, 2.4) / 16f;
        b.wx = (float) rnd(-0.45, 0.45);
        b.wy = (float) rnd(-0.45, 0.45);
        b.wz = (float) rnd(-0.45, 0.45);
        b.color = color;
        BITS.add(b);
    }

    // A few drops falling from the wound over the next seconds (a small detail after the blow).
    private static void wound(Entity e, double height, int count, int every, int color) {
        Run r = new Run();
        r.entity = e.getId();
        r.height = height;
        r.left = count;
        r.next = tick + every;
        r.band = 1;
        r.color = color;
        r.axis = every;
        RUNS.add(r);
    }

    private static void pool(Entity e, float radius, int delay, int color) {
        int id = e.getId();
        Vec3 at0 = e.position();
        LATER.add(new Later(tick + delay, () -> {
            ClientLevel level = Minecraft.getInstance().level;
            Entity now = level == null ? null : level.getEntity(id);
            Vec3 at = now != null ? now.position() : at0;
            double floor = floorUnder(level, at);
            if (Double.isNaN(floor)) {
                return;
            }
            Pool p = new Pool();
            p.x = at.x;
            p.z = at.z;
            p.plane = floor;
            p.radius = radius;
            p.color = color;
            p.start = tick;
            p.dur = 60;
            p.seed = R.nextFloat() * 100;
            POOLS.add(p);
        }));
    }

    private static double floorUnder(ClientLevel level, Vec3 at) {
        if (level == null) {
            return Double.NaN;
        }
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy >= -4; dy--) {
            bp.set(Mth.floor(at.x), Mth.floor(at.y + 0.3) + dy, Mth.floor(at.z));
            BlockState s = level.getBlockState(bp);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                continue;
            }
            VoxelShape shape = s.getCollisionShape(level, bp);
            if (!shape.isEmpty()) {
                double top = bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (top <= at.y + 0.6) {
                    return top;
                }
            }
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ stains

    private static double coord(int axis, double x, double y, double z) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    // The two directions along a face: for the floor x and z, for a wall facing x the z and y, facing z the x and y.
    private static int axisA(int axis) {
        return axis == 0 ? 2 : 0;
    }

    private static int axisB(int axis) {
        return axis == 1 ? 2 : 1;
    }

    private static Vec3 point(int axis, double plane, double a, double b) {
        int ia = axisA(axis), ib = axisB(axis);
        double[] p = new double[3];
        p[axis] = plane;
        p[ia] = a;
        p[ib] = b;
        return new Vec3(p[0], p[1], p[2]);
    }

    private static boolean solidAt(ClientLevel level, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        BlockState s = level.getBlockState(bp);
        if (s.isAir()) {
            return false;
        }
        VoxelShape shape = s.getCollisionShape(level, bp);
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.move(bp).contains(p)) {
                return true;
            }
        }
        return false;
    }

    // Paints one pixel if the face really is there under it (a splat never hangs over the edge of a block).
    private static boolean paint(ClientLevel level, int axis, int sign, double plane, long a, long b, int band, int color, boolean shine) {
        double ca = (a + 0.5) / 16.0, cb = (b + 0.5) / 16.0;
        Vec3 in = point(axis, plane - sign * 0.01, ca, cb), out = point(axis, plane + sign * 0.01, ca, cb);
        if (!solidAt(level, in) || solidAt(level, out)) {
            return false;
        }
        Key key = new Key(axis, sign, Math.round(plane * 16), a, b);
        int life = 320 + R.nextInt(240);
        Stain s = STAINS.get(key);
        if (s != null) {
            s.die = Math.max(s.die, tick + life);
            if (band >= s.band) {
                return true;
            }
            STAINS.remove(key);
        } else {
            s = new Stain();
        }
        s.plane = plane;
        s.band = Math.min(3, band);
        s.color = color;
        s.born = tick;
        s.die = tick + life;
        s.shine = shine;
        s.light = LevelRenderer.getLightColor(level, BlockPos.containing(out));
        STAINS.put(key, s);
        while (STAINS.size() > MAX_STAINS) {
            Iterator<Key> it = STAINS.keySet().iterator();
            it.next();
            it.remove();
        }
        return true;
    }

    // A drop landing: one pixel, two for a bigger drop (stretched the way it flew), a little plus for the biggest, and
    // sometimes a tiny satellite a pixel or two away. A big one on a wall runs down a few pixels.
    private static void splat(ClientLevel level, int axis, int sign, double plane, Vec3 p, Vec3 v, int size, int band, int color) {
        long a = Mth.floor(coord(axisA(axis), p.x, p.y, p.z) * 16), b = Mth.floor(coord(axisB(axis), p.x, p.y, p.z) * 16);
        double va = coord(axisA(axis), v.x, v.y, v.z), vb = coord(axisB(axis), v.x, v.y, v.z);
        if (!paint(level, axis, sign, plane, a, b, band, color, size >= 2 && R.nextFloat() < 0.35f)) {
            return;
        }
        if (size >= 2) {
            int oa = Math.abs(va) > Math.abs(vb) ? (int) Math.signum(va) : 0;
            int ob = oa != 0 ? 0 : (vb == 0 ? 1 : (int) Math.signum(vb));
            paint(level, axis, sign, plane, a + oa, b + ob, band + 1, color, false);
        }
        if (size >= 3) {
            paint(level, axis, sign, plane, a - 1, b, band, color, false);
            paint(level, axis, sign, plane, a, b + 1, band + 1, color, false);
            if (R.nextBoolean()) {
                paint(level, axis, sign, plane, a + 1, b - 1, band + 1, color, false);
            }
        }
        if (size >= 2 && R.nextFloat() < 0.4f) {
            int k = 2 + R.nextInt(2);
            paint(level, axis, sign, plane, a + Math.round(Math.signum(va) * k), b + Math.round(Math.signum(vb) * k * 0.5), band + 1, color, false);
        }
        if (size >= 2 && axis != 1 && R.nextFloat() < 0.55f) {
            Run r = new Run();
            r.axis = axis;
            r.sign = sign;
            r.plane = plane;
            r.a = a;
            r.b = b - 1;
            r.left = 2 + R.nextInt(4);
            r.next = tick + 6 + R.nextInt(8);
            r.band = band + 1;
            r.color = color;
            RUNS.add(r);
        }
    }

    // ------------------------------------------------------------------ the tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel || level == null || FxConfig.blood() <= 0) {
            clear();
            lastLevel = level;
            if (level == null || FxConfig.blood() <= 0) {
                return;
            }
        }
        if (mc.isPaused()) {
            return;
        }
        tick++;
        bornThisTick = 0;
        if (tick % 200 == 0) {
            BUCKETS.entrySet().removeIf(en -> tick - en.getValue()[1] > 200);
        }
        for (int i = LATER.size() - 1; i >= 0; i--) {
            if (tick >= LATER.get(i).at()) {
                LATER.remove(i).run().run();
            }
        }
        tickDrops(level);
        tickBits(level);
        tickPools(level);
        tickRuns(level);
        if (tick % 10 == 0) {
            tickStains(level);
        }
    }

    private static void clear() {
        DROPS.clear();
        STAINS.clear();
        BITS.clear();
        POOLS.clear();
        RUNS.clear();
        LATER.clear();
        BUCKETS.clear();
    }

    private record Hit(int axis, int sign, double plane, boolean water) {
    }

    // Did the way from q to p go into a block? Which face it went through, and where that face is.
    private static Hit collide(ClientLevel level, Vec3 q, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        BlockState s = level.getBlockState(bp);
        if (s.isAir()) {
            return null;
        }
        if (!s.getFluidState().isEmpty()) {
            return new Hit(0, 0, 0, true);
        }
        VoxelShape shape = s.getCollisionShape(level, bp);
        if (shape.isEmpty()) {
            return null;
        }
        for (AABB box0 : shape.toAabbs()) {
            AABB box = box0.move(bp);
            if (!box.contains(p)) {
                continue;
            }
            // the floor first (most drops land on it), then the walls
            int[] order = {1, 0, 2};
            for (int axis : order) {
                double qc = coord(axis, q.x, q.y, q.z);
                double lo = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
                double hi = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
                if (qc <= lo) {
                    return new Hit(axis, -1, lo, false);
                }
                if (qc >= hi) {
                    return new Hit(axis, 1, hi, false);
                }
            }
            // started inside a block: gone without a stain
            return new Hit(0, 0, 0, true);
        }
        return null;
    }

    private static void tickDrops(ClientLevel level) {
        for (int i = DROPS.size() - 1; i >= 0; i--) {
            Drop d = DROPS.get(i);
            d.xo = d.x;
            d.yo = d.y;
            d.zo = d.z;
            d.vy -= GRAVITY;
            d.vx *= DRAG;
            d.vy *= DRAG;
            d.vz *= DRAG;
            double len = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
            int steps = Math.max(1, (int) Math.ceil(len / 0.25));
            boolean dead = ++d.age > 80;
            Vec3 q = new Vec3(d.x, d.y, d.z);
            for (int k = 1; k <= steps && !dead; k++) {
                Vec3 p = new Vec3(d.xo + d.vx * k / steps, d.yo + d.vy * k / steps, d.zo + d.vz * k / steps);
                Hit hit = collide(level, q, p);
                if (hit != null) {
                    if (!hit.water()) {
                        Vec3 at = point(hit.axis(), hit.plane(), coord(axisA(hit.axis()), p.x, p.y, p.z), coord(axisB(hit.axis()), p.x, p.y, p.z));
                        splat(level, hit.axis(), hit.sign(), hit.plane(), at, new Vec3(d.vx, d.vy, d.vz), d.size, d.band, d.color);
                    }
                    dead = true;
                }
                q = p;
            }
            if (dead) {
                DROPS.remove(i);
            } else {
                d.x += d.vx;
                d.y += d.vy;
                d.z += d.vz;
            }
        }
    }

    private static void tickBits(ClientLevel level) {
        for (int i = BITS.size() - 1; i >= 0; i--) {
            Bit b = BITS.get(i);
            b.xo = b.x;
            b.yo = b.y;
            b.zo = b.z;
            if (++b.age > 300) {
                BITS.remove(i);
                continue;
            }
            if (b.rest) {
                continue;
            }
            b.vy -= GRAVITY;
            b.vx *= DRAG;
            b.vz *= DRAG;
            Vec3 q = new Vec3(b.x, b.y, b.z), p = q.add(b.vx, b.vy, b.vz);
            Hit hit = collide(level, q, p);
            b.rx += b.wx;
            b.ry += b.wy;
            b.rz += b.wz;
            if (hit == null) {
                b.x = p.x;
                b.y = p.y;
                b.z = p.z;
                continue;
            }
            if (hit.water()) {
                BITS.remove(i);
                continue;
            }
            Vec3 at = point(hit.axis(), hit.plane(), coord(axisA(hit.axis()), p.x, p.y, p.z), coord(axisB(hit.axis()), p.x, p.y, p.z));
            splat(level, hit.axis(), hit.sign(), hit.plane(), at, new Vec3(b.vx, b.vy, b.vz), 1, 1, b.color);
            double half = b.size / 2;
            if (hit.axis() == 1) {
                b.y = hit.plane() + hit.sign() * half;
                if (hit.sign() > 0 && Math.abs(b.vy) < 0.08) {
                    b.rest = true;
                    b.rx = 0;
                    b.rz = 0;
                } else {
                    b.vy *= -0.3;
                    b.vx *= 0.55;
                    b.vz *= 0.55;
                }
            } else if (hit.axis() == 0) {
                b.x = hit.plane() + hit.sign() * half;
                b.vx *= -0.3;
                b.z = p.z;
                b.y = p.y;
            } else {
                b.z = hit.plane() + hit.sign() * half;
                b.vz *= -0.3;
                b.x = p.x;
                b.y = p.y;
            }
            b.wx *= 0.5f;
            b.wy *= 0.5f;
            b.wz *= 0.5f;
        }
    }

    private static float hash(float x, float y) {
        float h = Mth.sin(x * 127.1f + y * 311.7f) * 43758.547f;
        return h - Mth.floor(h);
    }

    // A pool grows for 3 seconds: pixels within a radius that wobbles (a blob on the block grid, never a circle).
    private static void tickPools(ClientLevel level) {
        for (int i = POOLS.size() - 1; i >= 0; i--) {
            Pool p = POOLS.get(i);
            float k = Mth.clamp((tick - p.start) / (float) p.dur, 0, 1);
            float r = p.radius * 16 * (1 - (1 - k) * (1 - k));
            long cx = Mth.floor(p.x * 16), cz = Mth.floor(p.z * 16);
            int rr = (int) Math.ceil(p.radius * 16) + 2;
            for (int dz = -rr; dz <= rr; dz++) {
                for (int dx = -rr; dx <= rr; dx++) {
                    float d = Mth.sqrt(dx * dx + dz * dz * 1.25f) + hash(dx + p.seed, dz) * 2.2f + Mth.sin((float) Math.atan2(dz, dx) * 3 + p.seed) * 1.2f;
                    if (d < r && !STAINS.containsKey(new Key(1, 1, Math.round(p.plane * 16), cx + dx, cz + dz))) {
                        paint(level, 1, 1, p.plane, cx + dx, cz + dz, d < r * 0.5f ? 2 : 1, p.color, false);
                    }
                }
            }
            if (k >= 1) {
                POOLS.remove(i);
            }
        }
    }

    private static void tickRuns(ClientLevel level) {
        for (int i = RUNS.size() - 1; i >= 0; i--) {
            Run r = RUNS.get(i);
            if (tick < r.next) {
                continue;
            }
            if (r.entity >= 0) {
                Entity e = level.getEntity(r.entity);
                if (e == null || !e.isAlive()) {
                    RUNS.remove(i);
                    continue;
                }
                Vec3 o = e.position().add(rnd(-0.12, 0.12), e.getBbHeight() * r.height, rnd(-0.12, 0.12));
                drop(o, new Vec3(0, -0.01, 0), 1, r.band, r.color);
                r.next = tick + r.axis + R.nextInt(r.axis + 1);
            } else {
                if (!paint(level, r.axis, r.sign, r.plane, r.a, r.b, Math.min(3, r.band), r.color, false)) {
                    RUNS.remove(i);
                    continue;
                }
                r.b--;
                r.next = tick + 5 + R.nextInt(8);
            }
            if (--r.left <= 0) {
                RUNS.remove(i);
            }
        }
    }

    // Every half second: dried ones go, ones whose block was broken go, the light is read again.
    private static void tickStains(ClientLevel level) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Iterator<Map.Entry<Key, Stain>> it = STAINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Stain> en = it.next();
            Key k = en.getKey();
            Stain s = en.getValue();
            if (tick > s.die) {
                it.remove();
                continue;
            }
            double ca = (k.a() + 0.5) / 16.0, cb = (k.b() + 0.5) / 16.0;
            Vec3 out = point(k.axis(), s.plane + k.sign() * 0.01, ca, cb);
            if (out.distanceToSqr(cam) > 128 * 128 || !solidAt(level, point(k.axis(), s.plane - k.sign() * 0.01, ca, cb))) {
                it.remove();
                continue;
            }
            if ((tick + (int) (k.a() * 7 + k.b())) % 40 < 10) {
                s.light = LevelRenderer.getLightColor(level, BlockPos.containing(out));
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    // The four shades of one blood: fresh, darker, darker, dried.
    private static int shade(int color, int band, boolean shine) {
        float k = shine ? 1.25f : band <= 0 ? 1f : band == 1 ? 0.78f : band == 2 ? 0.58f : 0.42f;
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        if (band >= 3) {
            // dried: toward a brown
            r = (int) (r * 0.8f + 60 * 0.2f);
            g = (int) (g * 0.8f + 30 * 0.2f);
            b = (int) (b * 0.8f + 20 * 0.2f);
        }
        return (Mth.clamp((int) (r * k), 0, 255) << 16) | (Mth.clamp((int) (g * k), 0, 255) << 8) | Mth.clamp((int) (b * k), 0, 255);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || (DROPS.isEmpty() && STAINS.isEmpty() && BITS.isEmpty())) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        if (!STAINS.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(FxRenderTypes.BLOOD_DECAL);
            for (Map.Entry<Key, Stain> en : STAINS.entrySet()) {
                Key k = en.getKey();
                Stain s = en.getValue();
                int age = tick - s.born;
                int band = s.band + (age > DRY1 ? 1 : 0) + (age > DRY2 ? 1 : 0);
                int c = shade(s.color, band, s.shine && age < 24);
                double plane = s.plane + k.sign() * 0.0015;
                double a0 = k.a() / 16.0, a1 = (k.a() + 1) / 16.0, b0 = k.b() / 16.0, b1 = (k.b() + 1) / 16.0;
                quad(vc, m, cam, point(k.axis(), plane, a0, b0), point(k.axis(), plane, a1, b0), point(k.axis(), plane, a1, b1),
                        point(k.axis(), plane, a0, b1), c, s.light);
            }
            buffers.endBatch(FxRenderTypes.BLOOD_DECAL);
        }

        if (!DROPS.isEmpty() || !BITS.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(FxRenderTypes.BLOOD);
            Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
            BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
            for (Drop d : DROPS) {
                double x = Mth.lerp(partial, d.xo, d.x), y = Mth.lerp(partial, d.yo, d.y), z = Mth.lerp(partial, d.zo, d.z);
                double h = d.size / 32.0;
                bp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
                int light = LevelRenderer.getLightColor(level, bp);
                int c = shade(d.color, Math.min(2, d.band), false);
                Vec3 p = new Vec3(x, y, z);
                Vec3 l = new Vec3(left.x() * h, left.y() * h, left.z() * h), u = new Vec3(up.x() * h, up.y() * h, up.z() * h);
                quad(vc, m, cam, p.add(l).add(u), p.subtract(l).add(u), p.subtract(l).subtract(u), p.add(l).subtract(u), c, light);
            }
            for (Bit b : BITS) {
                drawBit(vc, m, cam, level, b, partial, bp);
            }
            buffers.endBatch(FxRenderTypes.BLOOD);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int light) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, bl = color & 255;
        vertex(vc, m, cam, a, r, g, bl, light);
        vertex(vc, m, cam, b, r, g, bl, light);
        vertex(vc, m, cam, c, r, g, bl, light);
        vertex(vc, m, cam, d, r, g, bl, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, int r, int g, int b, int light) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, 255).setLight(light);
    }

    // A flesh bit: a tiny cube in the flesh's three shades (tinted toward the blood's color), lit by the world.
    private static final int[] FLESH = {0x962226, 0xB03438, 0xCE6C68};

    private static void drawBit(VertexConsumer vc, Matrix4f m, Vec3 cam, ClientLevel level, Bit b, float partial, BlockPos.MutableBlockPos bp) {
        float fade = b.age > 280 ? 1 - (b.age - 280 + partial) / 20f : 1;
        if (fade <= 0) {
            return;
        }
        double x = Mth.lerp(partial, b.xo, b.x), y = Mth.lerp(partial, b.yo, b.y), z = Mth.lerp(partial, b.zo, b.z);
        bp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int light = LevelRenderer.getLightColor(level, bp);
        float h = b.size / 2 * fade;
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateXYZ(b.rx, b.ry, b.rz);
        Vec3 c = new Vec3(x, y, z);
        Vector3f[] corner = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = q.transform(new Vector3f((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h));
        }
        int[][] faces = {{2, 3, 7, 6}, {0, 4, 5, 1}, {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] lit = {1f, 0.5f, 0.8f, 0.8f, 0.65f, 0.65f};
        for (int f = 0; f < 6; f++) {
            int base = mix(FLESH[Math.floorMod(f + b.color, 3)], b.color, 0.35f);
            int col = scale(base, lit[f]);
            Vector3f a0 = corner[faces[f][0]], a1 = corner[faces[f][1]], a2 = corner[faces[f][2]], a3 = corner[faces[f][3]];
            quad(vc, m, cam, c.add(a0.x, a0.y, a0.z), c.add(a1.x, a1.y, a1.z), c.add(a2.x, a2.y, a2.z), c.add(a3.x, a3.y, a3.z), col, light);
        }
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int c, float k) {
        return ((int) (((c >> 16) & 255) * k) << 16) | ((int) (((c >> 8) & 255) * k) << 8) | (int) ((c & 255) * k);
    }
}
