package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Rukia's and Byakuya's skills on the client, the approved pages (forja/bleach/rukia.js, byakuya.js) carried over line
// by line: each skill is a Run with the same pieces and the same update(dt) as the page's module, with the same numbers;
// the server only says when and where (SubNet.BL_*) and who gets frozen. The pages' world has Rukia at the origin looking
// down -z: a Run keeps that frame (origin + a turn round y), so every position below is the page's own.
public final class BleachFx {
    private static final List<Run> RUNS = new ArrayList<>();
    private static final Map<Integer, Body> BODIES = new HashMap<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Snow> SNOW = new ArrayList<>();
    private static final Random RND = new Random();
    private static double last = -1;
    private static float clock;
    private static Vec3 cam = Vec3.ZERO;

    private BleachFx() {
    }

    // ---------- the pages' little helpers ----------

    static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    static float smooth(float x) {
        x = clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float ease3(float x) {
        float u = 1 - clamp(x, 0, 1);
        return 1 - u * u * u;
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float rnd() {
        return RND.nextFloat();
    }

    // The pages' hsh(x, y, z), bit for bit (JavaScript multiplies in doubles before cutting to 32 bits).
    static float hsh(int x, int y, int z) {
        int h = (int) ((long) x * 374761393L + (long) y * 668265263L + (long) z * 1442695041L);
        int m = h ^ (h >>> 13);
        h = (int) (long) ((double) m * 1274126177.0);
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    static float[] rgb(int hex) {
        return FxDraw.rgb(hex);
    }

    // ---------- the screen (the pages' impactFrame, shock, shake, speedLines), only felt close by ----------

    static float near(Vec3 at) {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return 0;
        }
        return (float) Mth.clamp(1 - (p.position().distanceTo(at) - 24) / 30, 0, 1);
    }

    // frames: pairs of (seconds, negative 0/1); a manga frame each, one tick each.
    static void impact(Vec3 at, int tint, int... negs) {
        if (near(at) <= 0) {
            return;
        }
        int[] modes = new int[negs.length];
        for (int i = 0; i < negs.length; i++) {
            modes[i] = negs[i] == 1 ? 4 : 3;
        }
        ScreenFx.impactFrame(modes, 1, tint, at, 0);
    }

    static void shock(Vec3 at, float pow, int tint) {
        if (near(at) > 0) {
            ScreenFx.warp(at, 1.6f * pow * 1.8f, 0.3f * pow, 20, 0, new Vec3(0, 1, 0), tint, 0.08f, 11, -1, null, 0);
        }
    }

    static void shake(Vec3 at, float a) {
        float k = near(at);
        if (k > 0) {
            ScreenShake.start(a * 1.6f * k, Math.max(4, Math.round(a / 1.4f * 20)));
        }
    }

    static void speedLines(Vec3 at) {
        if (near(at) > 0) {
            ScreenFx.radialBlur(at, 0.35f, 8);
        }
    }

    static Vector3f toV(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    static Vec3 toVec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    private static final float[] RING_DUST = rgb(0x8A8D94);

    // The pages' makeRing (the approved kits' dust ring running over the ground), drawn by KiMesh.
    static void dustRing(Vec3 floor, float ringR, float max, float life, float[] tint, float seed) {
        if (life >= 1 || max <= 0.05f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.RING);
        p.ringR = ringR;
        p.ringMax = max;
        p.life = Math.max(0, life);
        p.color = tint;
        p.dust = RING_DUST;
        p.seed = seed;
        p.glow = false;
        Vector3f c = new Vector3f((float) (floor.x - cam.x), (float) (floor.y - cam.y) + 0.06f, (float) (floor.z - cam.z));
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(c, max), p, KiMesh.BOTH, 7);
    }

    // ---------- a piece: one of the page's piece() objects ----------

    static final class Pc {
        final BleachMesh.Mesh mesh;
        final BleachMesh.Piece p;
        boolean on;
        // in the Run's frame unless world is set (then it is relative to the run's origin, in world axes)
        boolean world;
        final Vector3f pos = new Vector3f();
        final Quaternionf q = new Quaternionf();
        final Vector3f scl = new Vector3f(1, 1, 1);

        Pc(int kind, BleachMesh.Mesh mesh, int sides, int order, float seed) {
            this.mesh = mesh;
            p = BleachMesh.piece(kind);
            p.sides = sides;
            p.order = order;
            p.seed = seed;
        }

        Pc color(int c) {
            p.color = rgb(c);
            return this;
        }

        Pc color2(int c) {
            p.color2 = rgb(c);
            return this;
        }

        Pc noGlow() {
            p.glow = false;
            return this;
        }

        Pc show(boolean v) {
            on = v;
            return this;
        }

        Pc at(float x, float y, float z) {
            pos.set(x, y, z);
            return this;
        }

        Pc at(Vector3f v) {
            pos.set(v);
            return this;
        }

        Pc scale(float s) {
            scl.set(s, s, s);
            return this;
        }

        Pc scale(float x, float y, float z) {
            scl.set(x, y, z);
            return this;
        }

        // setFromUnitVectors((0,1,0), d)
        Pc up(Vector3f d) {
            q.rotationTo(0, 1, 0, d.x, d.y, d.z);
            return this;
        }
    }

    static float seed() {
        return rnd() * 50;
    }

    static Pc crystal(float seed) {
        return new Pc(BleachMesh.CRYSTAL, BleachMesh.Mesh.CRY, BleachMesh.BOTH, 14, seed);
    }

    static List<Pc> crystals(int n, float seed0) {
        List<Pc> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(crystal(seed0 + i * 3.71f));
        }
        return l;
    }

    static Pc frost(float half) {
        Pc pc = new Pc(BleachMesh.FROST, BleachMesh.Mesh.GRID, BleachMesh.FRONT, 3, seed());
        pc.scl.set(half, 1, half);
        pc.p.b[0] = half;
        return pc;
    }

    static Pc mist(int order, int light, int shade) {
        return new Pc(BleachMesh.MIST, BleachMesh.Mesh.SPH, BleachMesh.BOTH, order, seed()).noGlow().color(light).color2(shade);
    }

    static Pc mist(int order) {
        return mist(order, 0xf2f8ff, 0x8ea8c4);
    }

    static Pc flash(int color) {
        return new Pc(BleachMesh.FLASH, BleachMesh.Mesh.HALO, BleachMesh.FRONT, 22, seed()).color(color);
    }

    // ---------- a running skill ----------

    abstract static class Run {
        final List<Pc> pcs = new ArrayList<>();
        Vec3 origin;
        final Quaternionf rot = new Quaternionf();
        float t;
        boolean done;

        Run(Vec3 origin, Vec3 forward) {
            this.origin = origin;
            face(forward);
        }

        // Local -z (the page's "forward") turned to `forward` round y.
        final void face(Vec3 forward) {
            if (forward == null || forward.x * forward.x + forward.z * forward.z < 1.0E-6) {
                rot.identity();
                return;
            }
            rot.rotationY((float) Math.atan2(-forward.x, -forward.z));
        }

        <T extends Pc> T add(T pc) {
            pcs.add(pc);
            return pc;
        }

        void hideAll() {
            for (Pc pc : pcs) {
                pc.on = false;
            }
        }

        // A point of the page's world, in the game's world.
        Vec3 world(float x, float y, float z) {
            Vector3f v = rot.transform(new Vector3f(x, y, z));
            return origin.add(v.x, v.y, v.z);
        }

        Vec3 world(Vector3f v) {
            return world(v.x, v.y, v.z);
        }

        Vector3f local(Vec3 w) {
            Vector3f v = new Vector3f((float) (w.x - origin.x), (float) (w.y - origin.y), (float) (w.z - origin.z));
            return new Quaternionf(rot).conjugate().transform(v);
        }

        // A direction of the game's world in this frame.
        Vector3f localDir(Vec3 d) {
            return new Quaternionf(rot).conjugate().transform(new Vector3f((float) d.x, (float) d.y, (float) d.z)).normalize();
        }

        Vec3 dirWorld(float x, float y, float z) {
            Vector3f v = rot.transform(new Vector3f(x, y, z));
            return new Vec3(v.x, v.y, v.z);
        }

        void snow(float x, float y, float z, float vx, float vy, float vz, float life, float size) {
            emit(world(x, y, z), dirWorld(vx, vy, vz), life, size);
        }

        abstract void update(float dt);

        void queue() {
            Matrix4f base = new Matrix4f().translation((float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z));
            for (Pc pc : pcs) {
                if (!pc.on) {
                    continue;
                }
                Matrix4f m = new Matrix4f(base);
                if (!pc.world) {
                    m.rotate(rot);
                }
                m.translate(pc.pos).rotate(pc.q).scale(pc.scl);
                BleachMesh.queue(pc.mesh, m, pc.p);
            }
        }
    }

    // ---------- ice over a body (the page's iceBody + victim): the body's own skin drawn again through the ICE kind ----------

    static final class Body {
        final int id;
        int mode;
        float h = 2.1f;
        Vec3 off = Vec3.ZERO;
        float speed;
        float prog;
        float sh = -1;
        boolean thaw;
        // the Bankai drives Rukia's own shell and white skin itself
        boolean ctrl;
        float tint;
        final float seed = rnd() * 50;
        VertexBuffer ice;
        VertexBuffer skin;
        float pending = -1;

        Body(int id) {
            this.id = id;
        }
    }

    // Senka's afterimage: the body drawn again, see-through white, where it stood.
    static final class Ghost {
        Vec3 at;
        float alpha;
        VertexBuffer vbo;
    }

    private static final Map<Integer, Ghost> GHOSTS = new HashMap<>();

    static void ghost(int id, Vec3 at, float alpha) {
        Ghost g = GHOSTS.computeIfAbsent(id, k -> new Ghost());
        g.at = at;
        g.alpha = alpha;
    }

    static Body body(int id) {
        return BODIES.computeIfAbsent(id, Body::new);
    }

    // Freezes id: mode 0 from the feet up, 1 out from `from`; h = the page's height for a body 2 blocks tall; speed =
    // how much of the body per second (the page's v.update(dt, speed)).
    static void freeze(int id, Vec3 from, int mode, float h, float speed) {
        Entity e = FxAnchor.entity(id);
        Body b = body(id);
        b.mode = mode;
        b.h = h;
        b.speed = speed;
        b.prog = 0;
        b.sh = -1;
        b.thaw = false;
        b.ctrl = false;
        b.off = e == null ? Vec3.ZERO : from.subtract(e.position());
    }

    // ---------- shards (the page's makeShards): each with its own shape, size, spin and fall ----------

    static final class Shard {
        final Pc pc = new Pc(BleachMesh.SHARD, BleachMesh.Mesh.SPH_LO, BleachMesh.FRONT, 16, 0);
        Vec3 pos;
        Vec3 vel;
        float t;
        float life;
        float size;
        Vector3f ax;
        float spin;
        final Quaternionf q = new Quaternionf();
        boolean rest;
    }

    static void burst(Vec3 at, int n, float spread, float h, float pow, float up, float size, Vec3 dir, float push) {
        for (int j = 0; j < n; j++) {
            Shard s = new Shard();
            s.life = 1.6f + rnd() * 1.6f;
            Vec3 off = new Vec3((rnd() - 0.5) * spread, rnd() * h, (rnd() - 0.5) * spread);
            s.pos = at.add(off);
            Vec3 d = new Vec3(off.x, Math.abs(off.y) * 0.5 + 0.3, off.z).normalize();
            s.vel = d.scale(pow * (0.4 + 0.9 * rnd())).add(0, up * rnd(), 0);
            if (dir != null) {
                s.vel = s.vel.add(dir.scale(push * (0.5 + rnd())));
            }
            s.size = size * (0.35f + 1.1f * rnd() * rnd() + 0.2f * rnd());
            s.ax = new Vector3f(rnd() - 0.5f, rnd() - 0.5f, rnd() - 0.5f).normalize();
            s.spin = 4 + rnd() * 14;
            s.q.rotationXYZ(rnd() * 6, rnd() * 6, rnd() * 6);
            s.pc.p.seed = rnd() * 99;
            SHARDS.add(s);
        }
        while (SHARDS.size() > 260) {
            SHARDS.remove(0);
        }
    }

    static void burst(Vec3 at, int n, float spread, float h, float pow, float up, float size) {
        burst(at, n, spread, h, pow, up, size, null, 0);
    }

    // The ground under a point: the top of the first solid block a few blocks down.
    static double groundAt(double x, double y, double z) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return y - 8;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(y + 0.5); by > Mth.floor(y) - 8; by--) {
            p.set(bx, by, bz);
            var st = level.getBlockState(p);
            if (!st.getCollisionShape(level, p).isEmpty()) {
                return by + st.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return y - 8;
    }

    // ---------- snow sparks (the page's makeSnow): each with its own size, life, path and twinkle ----------

    record Snow(Vec3 p, Vec3 v, float life, float size, float r, float born) {
    }

    static void emit(Vec3 p, Vec3 v, float life, float size) {
        SNOW.add(new Snow(p, v, life, size, rnd(), clock));
        if (SNOW.size() > 900) {
            SNOW.remove(0);
        }
    }

    private static final float[] SNOW_BLUE = {0.7f, 0.88f, 1f};
    private static final float[] SNOW_WHITE = {1f, 1f, 1f};

    // ---------- from the server ----------

    public static boolean handles(int kind) {
        return kind >= SubNet.BL_FIRST && kind <= SubNet.BL_LAST;
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        switch (kind) {
            case SubNet.BL_TSUKI -> RUNS.add(new Rk.Tsukishiro(pos, a));
            case SubNet.BL_HAKUREN -> RUNS.add(new Rk.Hakuren(pos, dir));
            case SubNet.BL_SHIRAFUNE -> RUNS.add(new Rk.Shirafune(id, pos, dir, a, (int) b));
            case SubNet.BL_RIKU -> RUNS.add(new Rk.Riku(id, pos));
            case SubNet.BL_SOREN -> {
                for (Run r : RUNS) {
                    if (r instanceof Rk.Soren s && s.caster == id && !s.done) {
                        if (c >= 1) {
                            s.fire(pos);
                        } else if (c < 0) {
                            s.cancel();
                        }
                        return;
                    }
                }
                if (c == 0) {
                    RUNS.add(new Rk.Soren(id, pos));
                }
            }
            case SubNet.BL_BANKAI -> {
                for (Run r : RUNS) {
                    if (r instanceof Rk.Bankai bk && bk.caster == id && !bk.done) {
                        if (c >= 1) {
                            bk.end();
                        }
                        return;
                    }
                }
                if (c == 0) {
                    RUNS.add(new Rk.Bankai(id, pos));
                }
            }
            case SubNet.BL_ICE -> ice(id, pos, dir, a, (int) b, (int) c);
            case SubNet.BL_FLOWER -> RUNS.add(new Rk.Flower(id, pos));
            case SubNet.BL_SENBON -> {
                for (Run r : RUNS) {
                    if (r instanceof Bk.Senbon s && s.caster == id && !s.done && s.rel < 0) {
                        if (c >= 1) {
                            s.release();
                        }
                        return;
                    }
                }
                if (c == 0) {
                    RUNS.add(new Bk.Senbon(id, pos));
                }
            }
            case SubNet.BL_CUT -> RUNS.add(new Bk.Cut(pos));
            case SubNet.BL_GOKEI -> {
                for (Run r : RUNS) {
                    if (r instanceof Bk.Gokei g && g.key == id && !g.done) {
                        if (c >= 1) {
                            g.release();
                        }
                        return;
                    }
                }
                if (c == 0) {
                    Bk.Gokei g = new Bk.Gokei(pos);
                    g.key = id;
                    RUNS.add(g);
                }
            }
            case SubNet.BL_SENKA -> RUNS.add(new Bk.Senka(id, pos, (int) a));
            case SubNet.BL_BYAKURAI -> RUNS.add(new Bk.Byakurai(id, pos));
            case SubNet.BL_DANKU -> {
                if (c == 0) {
                    RUNS.add(new Bk.Danku(id, pos, dir));
                    return;
                }
                for (Run r : RUNS) {
                    if (r instanceof Bk.Danku dk && dk.caster == id && !dk.done) {
                        if (c >= 2) {
                            dk.done = true;
                        } else {
                            dk.hit(pos);
                        }
                    }
                }
            }
            case SubNet.BL_KAGEYOSHI, SubNet.BL_SENKEI, SubNet.BL_HAKUTEI -> {
                Bk.Kageyoshi k = null;
                for (Run r : RUNS) {
                    if (r instanceof Bk.Kageyoshi kg && kg.caster == id && !kg.done && kg.end < 0) {
                        k = kg;
                    }
                }
                if (kind == SubNet.BL_KAGEYOSHI) {
                    if (c >= 1) {
                        if (k != null) {
                            k.stop();
                        }
                    } else if (k == null) {
                        RUNS.add(new Bk.Kageyoshi(id, pos));
                    }
                } else if (k != null) {
                    if (kind == SubNet.BL_SENKEI) {
                        k.senkei((int) a, pos);
                    } else {
                        k.hakuteiken((int) a);
                    }
                }
            }
            default -> {
            }
        }
    }

    // BL_ICE: c = 0 freeze (dir.x = speed, a = height, b = mode + 2 * style), 1 shatter (b = style), 2 thaw.
    private static void ice(int id, Vec3 pos, Vec3 dir, float a, int b, int c) {
        Entity e = FxAnchor.entity(id);
        if (c == 0) {
            freeze(id, pos, b & 1, a, (float) dir.x);
            int style = b >> 1;
            Vec3 at = e == null ? pos : e.position();
            if (style == Rk.HAKUREN) {
                for (Run r : RUNS) {
                    if (r instanceof Rk.Hakuren hk && !hk.done) {
                        hk.hit(at);
                    }
                }
                impact(at, 0xcdeeff, 1, 0, 1);
                shock(at.add(0, 1.1, 0), 1.2f, 0xe3f6ff);
                shake(at, 0.4f);
            } else if (style == Rk.TRAP) {
                impact(at, 0xcdeeff, 1, 0);
                shake(at, 0.35f);
                shock(at.add(0, 1, 0), 1, 0xe3f6ff);
            }
            return;
        }
        Body bd = BODIES.get(id);
        if (bd == null) {
            return;
        }
        if (c == 2) {
            bd.thaw = true;
            return;
        }
        bd.sh = 0;
        Vec3 at = e == null ? pos : e.position();
        switch (b) {
            case Rk.TSUKI -> burst(at.add(0, 0.3, 0), 26, 0.8f, 1.8f, 3.6f, 2, 0.17f);
            case Rk.HAKUREN -> burst(at, 30, 1.4f, 2.4f, 4.5f, 3, 0.3f);
            case Rk.FLOWER -> burst(at, 46, 1.8f, 2.8f, 5, 3, 0.32f);
            default -> burst(at, 26, 0.9f, 2, 3.5f, 3, 0.17f);
        }
        if (b != Rk.FLOWER) {
            shake(at, 0.3f);
        }
    }

    public static boolean active() {
        return !RUNS.isEmpty() || !BODIES.isEmpty() || !SHARDS.isEmpty() || !SNOW.isEmpty() || !GHOSTS.isEmpty();
    }

    public static void clear() {
        RUNS.clear();
        for (Body b : BODIES.values()) {
            close(b);
        }
        BODIES.clear();
        for (Ghost g : GHOSTS.values()) {
            if (g.vbo != null) {
                g.vbo.close();
            }
        }
        GHOSTS.clear();
        SHARDS.clear();
        SNOW.clear();
        last = -1;
    }

    private static void close(Body b) {
        if (b.ice != null) {
            b.ice.close();
        }
        if (b.skin != null) {
            b.skin.close();
        }
    }

    // ---------- every frame (from SubFx: glow = the first call of the frame, which steps and queues the 3D pieces) ----------

    static void draw(Vec3 camera, float partial, boolean glow) {
        cam = camera;
        if (!glow) {
            return;
        }
        double now = (ScreenFx.now() + partial) / 20.0;
        float dt = last < 0 ? 0 : (float) Mth.clamp(now - last, 0, 0.1);
        last = now;
        clock += dt;
        BleachMesh.time(clock);
        Iterator<Run> it = RUNS.iterator();
        while (it.hasNext()) {
            Run r = it.next();
            if (r.done) {
                it.remove();
                continue;
            }
            r.update(dt);
            if (r.done) {
                it.remove();
                continue;
            }
            r.queue();
        }
        stepShards(dt);
        stepSnow();
        bodies(partial, dt);
        ghosts(Minecraft.getInstance(), partial);
    }

    private static void stepShards(float dt) {
        Iterator<Shard> it = SHARDS.iterator();
        while (it.hasNext()) {
            Shard s = it.next();
            s.t += dt;
            if (s.t > s.life) {
                it.remove();
                continue;
            }
            if (!s.rest) {
                s.vel = s.vel.add(0, -18 * dt, 0);
                s.pos = s.pos.add(s.vel.scale(dt));
                s.q.premul(new Quaternionf().rotationAxis(s.spin * dt, s.ax));
                double gy = groundAt(s.pos.x, s.pos.y, s.pos.z) + s.size * 0.3;
                if (s.pos.y < gy) {
                    s.pos = new Vec3(s.pos.x, gy, s.pos.z);
                    if (Math.abs(s.vel.y) < 2) {
                        s.rest = true;
                    } else {
                        s.vel = new Vec3(s.vel.x * 0.45, s.vel.y * -0.25, s.vel.z * 0.45);
                        s.spin *= 0.4f;
                    }
                }
            }
            s.pc.p.a(s.size, 0, 0, 0);
            s.pc.p.alpha = 1 - smooth((s.t - s.life + 0.5f) / 0.5f);
            Matrix4f m = new Matrix4f().translation((float) (s.pos.x - cam.x), (float) (s.pos.y - cam.y), (float) (s.pos.z - cam.z)).rotate(s.q);
            BleachMesh.queue(s.pc.mesh, m, s.pc.p);
        }
    }

    private static void stepSnow() {
        Iterator<Snow> it = SNOW.iterator();
        while (it.hasNext()) {
            Snow s = it.next();
            float age = clock - s.born();
            float l = age / s.life();
            if (l > 1) {
                it.remove();
                continue;
            }
            float a = smooth(l / 0.1f) * (1 - smooth((l - 0.6f) / 0.4f)) * (0.5f + 0.5f * Mth.sin(clock * 14 + s.r() * 40));
            if (a < 0.01f) {
                continue;
            }
            Vec3 p = s.p().add(s.v().scale(age)).add(Mth.sin(clock * 2 + s.r() * 9) * 0.06, 0, Mth.cos(clock * 1.7f + s.r() * 7) * 0.06);
            Vector3f c = new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
            FxDraw.billboard(c, s.size() * 1.1f, s.r() * 6, FxDraw.FLARE, 4 / 32f, SNOW_BLUE, a * 0.9f, true);
            FxDraw.billboard(c, s.size() * 0.45f, 0, FxDraw.ORB, 0.9f, SNOW_WHITE, a * 0.8f, true);
        }
    }

    // ---------- the ice over bodies ----------

    private static ByteBufferBuilder bytes;

    private static void bodies(float partial, float dt) {
        Minecraft mc = Minecraft.getInstance();
        Iterator<Body> it = BODIES.values().iterator();
        while (it.hasNext()) {
            Body b = it.next();
            Entity ent = FxAnchor.entity(b.id);
            if (!b.ctrl) {
                if (b.thaw) {
                    b.prog -= dt / 0.6f;
                } else {
                    b.prog = Math.min(1, b.prog + dt * b.speed);
                }
            }
            if (b.sh >= 0) {
                b.sh = Math.min(1, b.sh + dt / 0.3f);
            }
            boolean gone = !(ent instanceof LivingEntity) || b.sh >= 1 || (!b.ctrl && b.thaw && b.prog <= 0) || (b.ctrl && b.prog <= 0.001f && b.tint <= 0.001f && b.pending < 0);
            if (gone) {
                close(b);
                it.remove();
                continue;
            }
            LivingEntity e = (LivingEntity) ent;
            if (e.isInvisible() || FxAnchor.firstPersonSelf(b.id) || !BleachMesh.ready()) {
                continue;
            }
            Vec3 feet = e.getPosition(partial);
            if (feet.distanceToSqr(cam) > 128 * 128) {
                continue;
            }
            float hs = e.getBbHeight() / 2f;
            Vec3 rel = feet.subtract(cam);
            if (b.prog > 0.001f) {
                b.ice = capture(mc, e, rel, partial, 0.035f, b.ice);
                if (b.ice != null) {
                    BleachMesh.Piece p = BleachMesh.piece(BleachMesh.ICE);
                    p.sides = BleachMesh.FRONT;
                    p.order = 12;
                    p.seed = b.seed;
                    Vec3 o = (b.mode == 0 ? feet : feet.add(b.off)).subtract(cam);
                    p.a(b.prog, b.mode, b.h * hs, Math.max(0, b.sh));
                    p.b((float) o.x, (float) o.y, (float) o.z, 0);
                    p.anchor[0] = (float) -rel.x;
                    p.anchor[1] = (float) -rel.y;
                    p.anchor[2] = (float) -rel.z;
                    BleachMesh.queue(b.ice, p);
                }
            }
            if (b.tint > 0.001f) {
                b.skin = capture(mc, e, rel, partial, 0.006f, b.skin);
                if (b.skin != null) {
                    BleachMesh.Piece p = BleachMesh.piece(BleachMesh.TINT);
                    p.sides = BleachMesh.FRONT;
                    p.order = 11;
                    p.a(b.tint, 0, 0, 0);
                    p.glow = false;
                    BleachMesh.queue(b.skin, p);
                }
            }
        }
    }

    private static void ghosts(Minecraft mc, float partial) {
        Iterator<Map.Entry<Integer, Ghost>> it = GHOSTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Ghost> en = it.next();
            Ghost g = en.getValue();
            Entity ent = FxAnchor.entity(en.getKey());
            if (g.alpha <= 0.002f || !(ent instanceof LivingEntity e)) {
                if (g.vbo != null) {
                    g.vbo.close();
                }
                it.remove();
                continue;
            }
            if (!BleachMesh.ready()) {
                continue;
            }
            g.vbo = capture(mc, e, g.at.subtract(cam), partial, 0.004f, g.vbo);
            if (g.vbo != null) {
                BleachMesh.Piece p = BleachMesh.piece(BleachMesh.TINT);
                p.sides = BleachMesh.FRONT;
                p.order = 11;
                p.a(g.alpha / 0.92f, 0, 0, 0);
                p.glow = false;
                BleachMesh.queue(g.vbo, p);
            }
        }
    }

    // The entity's model, every box grown by `grow`, into a buffer of its own (positions relative to the camera).
    private static VertexBuffer capture(Minecraft mc, LivingEntity e, Vec3 rel, float partial, float grow, VertexBuffer into) {
        if (bytes == null) {
            bytes = new ByteBufferBuilder(262144);
        }
        BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        Grow out = new Grow(bb, grow);
        MultiBufferSource src = type -> {
            ResourceLocation texture = FxRenderTypes.textureOf(type);
            if (texture == null) {
                return new Nothing();
            }
            String path = texture.getPath();
            if (!(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint") || path.contains("atlas")) {
                return new Nothing();
            }
            return out;
        };
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        int hurt = e.hurtTime;
        e.hurtTime = 0;
        try {
            dispatcher.render(e, rel.x, rel.y, rel.z, Mth.lerp(partial, e.yRotO, e.getYRot()), partial, new PoseStack(), src, 0xF000F0);
        } catch (RuntimeException ex) {
            // drawn without a shell this frame
        } finally {
            e.hurtTime = hurt;
        }
        MeshData mesh = bb.build();
        if (mesh == null) {
            return into;
        }
        VertexBuffer vbo = into != null ? into : new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vbo.bind();
        vbo.upload(mesh);
        VertexBuffer.unbind();
        return vbo;
    }

    // Grows every face of the model's boxes: pushed out along its normal and widened in its own plane, so each box gets
    // a shell a little bigger than itself (the page scales each part by 1.1).
    private static final class Grow implements VertexConsumer {
        private final VertexConsumer out;
        private final float thick;
        private final float[][] v = new float[4][];
        private int n;

        Grow(VertexConsumer out, float thick) {
            this.out = out;
            this.thick = thick;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float vv, int overlay, int light, float nx, float ny, float nz) {
            v[n] = new float[] {x, y, z, nx, ny, nz};
            if (++n < 4) {
                return;
            }
            n = 0;
            float cx = (v[0][0] + v[1][0] + v[2][0] + v[3][0]) / 4;
            float cy = (v[0][1] + v[1][1] + v[2][1] + v[3][1]) / 4;
            float cz = (v[0][2] + v[1][2] + v[2][2] + v[3][2]) / 4;
            Vector3f a = new Vector3f(v[1][0] - v[0][0], v[1][1] - v[0][1], v[1][2] - v[0][2]);
            Vector3f b = new Vector3f(v[3][0] - v[0][0], v[3][1] - v[0][1], v[3][2] - v[0][2]);
            if (a.lengthSquared() > 1.0E-10f) {
                a.normalize();
            }
            if (b.lengthSquared() > 1.0E-10f) {
                b.normalize();
            }
            for (int i = 0; i < 4; i++) {
                float[] p = v[i];
                Vector3f d = new Vector3f(p[0] - cx, p[1] - cy, p[2] - cz);
                float sa = Math.signum(d.dot(a)) * thick;
                float sb = Math.signum(d.dot(b)) * thick;
                out.addVertex(p[0] + a.x * sa + b.x * sb + p[3] * thick, p[1] + a.y * sa + b.y * sb + p[4] * thick, p[2] + a.z * sa + b.z * sb + p[5] * thick);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final class Nothing implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
