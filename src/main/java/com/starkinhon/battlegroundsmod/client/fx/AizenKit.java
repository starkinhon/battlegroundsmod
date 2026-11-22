// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenNet;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Aizen's kit on the client (forja/aizen-kit, approved v2.3): each skill is a Run carrying the page's scene update()
// line by line, with the same numbers; the server only says when and where (AizenNet 3-4 and 20-29). A Run keeps the
// page's frame (origin + a turn round y, the page's forward being -z), so positions below are the page's own. The
// people (Aizen and his targets) are the real ones: their poses are the keyframe animations; blocks breaking are the
// server's (Ruin); the camera stays the player's.
public final class AizenKit {
    private static final List<Run> RUNS = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final Random RND = new Random();
    private static double last = -1;
    private static float clock;
    private static Vec3 cam = Vec3.ZERO;
    private static float partial;
    // the people of the page are 2 blocks tall; the game's 1.8
    private static final float PS = 0.9f;

    // the page's shared unit meshes
    private static AizenMesh.Geo sphLo, sph, sphHalo, sphHi, sphCoc, sphRa, dome, cyl, wallBox, crackPlane, tetra, icosa;
    private static final AizenMesh.Geo[] WINGS = new AizenMesh.Geo[3];
    private static AizenMesh.Geo tendril;

    private AizenKit() {
    }

    private static void meshes() {
        if (sph != null) {
            return;
        }
        sphLo = AizenMesh.sphere(1, 24, 18);
        sph = AizenMesh.sphere(1, 96, 64);
        sphHalo = AizenMesh.sphere(1, 32, 24);
        sphHi = AizenMesh.sphere(1, 48, 32);
        sphCoc = AizenMesh.sphere(1.15f, 128, 96);
        sphRa = AizenMesh.sphere(1, 96, 72);
        dome = AizenMesh.sphere(1, 96, 40, 0, (float) Math.PI / 2);
        cyl = AizenMesh.openCylinder(64, 120);
        wallBox = AizenMesh.box(3.4f, 4.4f, 0.18f);
        crackPlane = AizenMesh.plane(48);
        tetra = AizenMesh.tetra();
        icosa = AizenMesh.icosa();
        for (int k = 0; k < 3; k++) {
            WINGS[k] = AizenGeo.wing(k, 11 + k * 7);
        }
        tendril = AizenGeo.tendril();
    }

    // ---------- the page's little helpers ----------

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

    static float easeIn2(float x) {
        x = clamp(x, 0, 1);
        return x * x;
    }

    static float seg(float t, float a, float b) {
        return clamp((t - a) / (b - a), 0, 1);
    }

    static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    static float rnd() {
        return RND.nextFloat();
    }

    static float[] rgb(int hex) {
        return FxDraw.rgb(hex);
    }

    static Vector3f randDir() {
        float z = rnd() * 2 - 1, a = rnd() * 6.283f, r = (float) Math.sqrt(1 - z * z);
        return new Vector3f(r * Mth.cos(a), z, r * Mth.sin(a));
    }

    static Vector3f rel(Vec3 w) {
        return new Vector3f((float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z));
    }

    static Vec3 vec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    // the ground under a point, or the point itself less `drop` when there is none close below
    static double floor(Vec3 at, double drop) {
        double g = BleachFx.groundAt(at.x, at.y + 1, at.z);
        return g < at.y - 7 ? at.y - drop : g;
    }

    // ---------- the screen (the page's shock, speedLines, mangaFrame, shake), only felt close by ----------

    static void shock(Vec3 at, float pow, int tint) {
        BleachFx.shock(at, pow, tint);
    }

    static void speedLines(Vec3 at) {
        BleachFx.speedLines(at);
    }

    // one manga frame only, never alternating (safe for epilepsy)
    static void manga(Vec3 at, int tint) {
        BleachFx.impact(at, tint, 0);
    }

    static void shake(Vec3 at, float a) {
        BleachFx.shake(at, a);
    }

    // ---------- the page's makeBlast / makeRing / makeFlash, drawn by KiMesh ----------

    private static final float[] RING_DUST = rgb(0x8A8D94);

    static void blast(Vec3 c, float r, float life, int color, float seed, boolean smoke, float flat) {
        blast(new Matrix4f().translation(rel(c)).scale(Math.max(r, 0.02f)), r, life, color, seed, smoke, flat);
    }

    static void blast(Matrix4f m, float r, float life, int color, float seed, boolean smoke, float flat) {
        if (life >= 1 || r <= 0.02f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.BLAST);
        p.life = Math.max(0, life);
        p.seed = seed;
        p.time = clock;
        p.flat = flat;
        p.color = rgb(color);
        p.smoke = smoke ? 1 : 0;
        p.glow = !smoke;
        KiMesh.queue(KiMesh.Mesh.BLAST, m, p, KiMesh.BOTH, smoke ? 6 : 14);
    }

    static void ring(Vec3 floor, float ringR, float max, float life, int tint, float seed) {
        if (life >= 1 || max <= 0.05f) {
            return;
        }
        KiMesh.P p = KiMesh.p(KiMesh.RING);
        p.ringR = ringR;
        p.ringMax = max;
        p.life = Math.max(0, life);
        p.color = rgb(tint);
        p.dust = RING_DUST;
        p.seed = seed;
        p.glow = false;
        KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.flat(rel(floor).add(0, 0.06f, 0), max), p, KiMesh.BOTH, 7);
    }

    // the hit flash (stepFlash): size grows with sqrt(f), fades over dur
    static final class Flash {
        Vec3 at;
        float t = 9, size, dur = 0.35f, seed;
        int color;

        void hit(Vec3 at, int color, float size, float dur) {
            this.at = at;
            this.color = color;
            this.size = Math.min(size, 3.5f);
            this.dur = dur;
            this.seed = rnd() * 10;
            t = 0;
        }

        void step(float dt) {
            if (at == null || t >= dur) {
                return;
            }
            t += dt;
            float f = t / dur;
            if (f >= 1) {
                return;
            }
            KiMesh.P p = KiMesh.p(KiMesh.FLASH);
            p.alpha = 1 - f;
            p.color = rgb(color);
            p.seed = seed;
            KiMesh.queue(KiMesh.Mesh.FLASH, KiMesh.sphere(rel(at), size * (0.3f + 0.7f * Mth.sqrt(f))), p, KiMesh.FRONT, 20);
        }
    }

    // ---------- the page's makeSparks: each spark with its own life and path ----------

    record Spark(Vec3 p, Vec3 v, float born, float life, float size, float r, float[] a, float[] b, float grav, float drag) {
    }

    static void spark(Vec3 p, Vec3 v, float life, float size, int a, int b, float grav, float drag) {
        SPARKS.add(new Spark(p, v, clock, life, size, rnd(), rgb(a), rgb(b), grav, drag));
        if (SPARKS.size() > 2600) {
            SPARKS.remove(0);
        }
    }

    private static final float[] WHITE = {1, 1, 1};

    private static void stepSparks() {
        Iterator<Spark> it = SPARKS.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            float t = clock - s.born(), k = t / s.life();
            if (k >= 1) {
                it.remove();
                continue;
            }
            float va = t > 0 ? smooth(k / 0.08f) * (1 - smooth((k - 0.6f) / 0.4f)) : 0;
            if (va < 0.01f) {
                continue;
            }
            float d = s.drag();
            float go = d == 0 ? t : (1 - (float) Math.exp(-d * t)) / d;
            Vec3 p = s.p().add(s.v().scale(go)).add(0, -0.5 * s.grav() * t * t, 0);
            p = p.add(Mth.sin(t * 3 + s.r() * 20) * 0.15 * s.r(), 0, Mth.cos(t * 2.4f + s.r() * 13) * 0.15 * s.r());
            Vector3f c = rel(p);
            float[] col = {lerp(s.a()[0], s.b()[0], s.r()), lerp(s.a()[1], s.b()[1], s.r()), lerp(s.a()[2], s.b()[2], s.r())};
            FxDraw.billboard(c, s.size() * 1.3f, s.r() * 6, FxDraw.ORB, 0.9f, col, va, true);
            FxDraw.billboard(c, s.size() * 0.45f, 0, FxDraw.ORB, 0.9f, WHITE, va * 0.7f, true);
        }
    }

    // ---------- shards (the page's InstancedMesh pieces): each with its own shape, size, spin and fall ----------

    static final class Shard {
        Vec3 p;
        Vec3 v;
        final Quaternionf q = new Quaternionf();
        Vector3f ax;
        float sp, life, grav, bounce, f0, f1, floorY;
        final Vector3f s = new Vector3f();
        boolean ico;
        float[] color, emissive;
        float alpha = 1;
        boolean liftByScale;
    }

    static Shard shard(Vec3 p, Vec3 v, Vector3f size, boolean ico, int color, int emissive, float alpha, float grav, float bounce, float f0, float f1) {
        Shard s = new Shard();
        s.p = p;
        s.v = v;
        s.q.rotationXYZ(rnd() * 6, rnd() * 6, rnd() * 6);
        s.ax = randDir();
        s.s.set(size);
        s.ico = ico;
        s.color = rgb(color);
        s.emissive = rgb(emissive);
        s.alpha = alpha;
        s.grav = grav;
        s.bounce = bounce;
        s.f0 = f0;
        s.f1 = f1;
        s.floorY = (float) floor(p, 30);
        SHARDS.add(s);
        while (SHARDS.size() > 320) {
            SHARDS.remove(0);
        }
        return s;
    }

    private static void stepShards(float dt) {
        Iterator<Shard> it = SHARDS.iterator();
        while (it.hasNext()) {
            Shard s = it.next();
            s.life += dt;
            float k = 1 - seg(s.life, s.f0, s.f1);
            if (k <= 0) {
                it.remove();
                continue;
            }
            s.v = s.v.add(0, -s.grav * dt, 0);
            s.p = s.p.add(s.v.scale(dt));
            float rest = s.floorY + (s.liftByScale ? s.s.y : 0.02f);
            if (s.p.y < rest) {
                s.p = new Vec3(s.p.x, rest, s.p.z);
                s.v = s.v.scale(s.bounce);
                if (s.liftByScale) {
                    s.sp *= 0.6f;
                }
            }
            s.q.premul(new Quaternionf().rotationAxis(s.sp * dt, s.ax));
            AizenMesh.Piece pc = new AizenMesh.Piece(AizenMesh.LIT);
            pc.tintA = s.color;
            pc.tintB = s.emissive;
            pc.alpha = s.alpha;
            pc.glow = false;
            if (s.alpha >= 0.99f) {
                pc.solid();
            } else {
                pc.order(16);
            }
            Matrix4f m = new Matrix4f().translation(rel(s.p)).rotate(s.q).scale(new Vector3f(s.s).mul(Math.max(k, 0.001f)));
            AizenMesh.queue(s.ico ? icosa : tetra, m, pc);
        }
    }

    // ---------- an energy tube (the page's energyMats): the body and, when it inflates, the halo behind ----------

    static final class Energy {
        final float[] core, body, rim;
        final float inflate, seed = rnd() * 20;
        float alpha = 1, flow, fadeEnds = 1;
        int order = 14;

        Energy(int core, int body, int rim, float inflate) {
            this.core = rgb(core);
            this.body = rgb(body);
            this.rim = rgb(rim);
            this.inflate = inflate;
        }

        void queue(AizenMesh.Geo geo, Matrix4f m) {
            if (alpha <= 0.002f) {
                return;
            }
            if (inflate > 0) {
                AizenMesh.Piece h = piece(true);
                AizenMesh.queue(geo, m, h);
            }
            AizenMesh.queue(geo, m, piece(false));
        }

        AizenMesh.Piece piece(boolean halo) {
            AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.EN);
            p.alpha = alpha;
            p.seed = seed;
            p.a(flow, halo ? 1 : 0, fadeEnds, halo ? inflate : 0);
            p.tintA = core;
            p.tintB = body;
            p.tintC = rim;
            p.sides = halo ? AizenMesh.BACK : AizenMesh.FRONT;
            p.order = halo ? order : order + 1;
            return p;
        }
    }

    // ---------- a running skill ----------

    abstract static class Run {
        Vec3 origin;
        final Quaternionf rot = new Quaternionf();
        float t;
        boolean done;
        int caster = -1;
        final List<AizenMesh.Geo> owned = new ArrayList<>();

        Run(Vec3 origin, Vec3 forward) {
            this.origin = origin;
            face(forward);
        }

        // local -z (the page's forward) turned to forward round y
        final void face(Vec3 forward) {
            if (forward == null || forward.x * forward.x + forward.z * forward.z < 1.0E-6) {
                rot.identity();
                return;
            }
            rot.rotationY((float) Math.atan2(-forward.x, -forward.z));
        }

        Vec3 world(float x, float y, float z) {
            Vector3f v = rot.transform(new Vector3f(x, y, z));
            return origin.add(v.x, v.y, v.z);
        }

        Vec3 dirWorld(float x, float y, float z) {
            Vector3f v = rot.transform(new Vector3f(x, y, z));
            return new Vec3(v.x, v.y, v.z);
        }

        Vector3f local(Vec3 w) {
            Vector3f v = new Vector3f((float) (w.x - origin.x), (float) (w.y - origin.y), (float) (w.z - origin.z));
            return new Quaternionf(rot).conjugate().transform(v);
        }

        // the page frame placed relative to the camera
        Matrix4f frame() {
            return new Matrix4f().translation(rel(origin)).rotate(rot);
        }

        AizenMesh.Geo own(AizenMesh.Geo g) {
            owned.add(g);
            return g;
        }

        abstract void update(float dt);

        void dispose() {
            for (AizenMesh.Geo g : owned) {
                g.close();
            }
        }
    }

    static Run find(Class<? extends Run> type, int caster) {
        for (Run r : RUNS) {
            if (type.isInstance(r) && r.caster == caster && !r.done) {
                return r;
            }
        }
        return null;
    }

    static Vec3 handOf(Entity e, Vec3 fallback) {
        if (e == null) {
            return fallback;
        }
        return e.getEyePosition(partial).add(e.getViewVector(partial).scale(0.6)).add(0, -0.35, 0);
    }

    // the page's person frame for a body: feet, turned to where the body faces, scaled to 1.8 blocks
    static Matrix4f bodyFrame(Entity e) {
        Vec3 feet = e.getPosition(partial);
        float yaw = e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(partial);
        float rad = yaw * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
        return new Matrix4f().translation(rel(feet)).rotateY((float) Math.atan2(-fwd.x, -fwd.z)).scale(PS);
    }

    // ---------- from the server ----------

    public static boolean handles(int kind) {
        return kind == AizenNet.HOGYOKU || kind == AizenNet.HOGYOKU_END || (kind >= AizenNet.RAIKO && kind <= AizenNet.SOKA_AIM);
    }

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b) {
        switch (kind) {
            case AizenNet.RAIKO -> {
                Raiko r = (Raiko) find(Raiko.class, id);
                if (a < 1) {
                    if (r != null) {
                        r.done = true;
                    }
                    RUNS.add(new Raiko(id, pos, dir));
                } else {
                    if (r == null) {
                        r = new Raiko(id, pos, dir);
                        RUNS.add(r);
                    }
                    r.fire(pos, dir);
                }
            }
            case AizenNet.SOKA -> {
                Soka old = (Soka) find(Soka.class, id);
                if (old != null) {
                    old.loose();
                }
                RUNS.add(new Soka(id, pos, dir, a));
            }
            case AizenNet.SOKA_AIM -> {
                Soka s = (Soka) find(Soka.class, id);
                if (s != null) {
                    s.aim(dir, a);
                }
            }
            case AizenNet.DEDO_STOP -> RUNS.add(new Dedo(id, pos, dir));
            case AizenNet.DEDO_BEHIND -> {
                Dedo d = (Dedo) find(Dedo.class, id);
                if (d != null) {
                    d.landed(dir);
                } else {
                    landSparks(dir);
                }
            }
            case AizenNet.DEDO_CUT -> RUNS.add(new Cut(pos, dir));
            case AizenNet.DANKU -> {
                Danku d = (Danku) find(Danku.class, id);
                if (a < 0.5f) {
                    if (d != null) {
                        d.done = true;
                    }
                    RUNS.add(new Danku(id, pos, dir));
                } else if (d != null) {
                    if (a < 1.5f) {
                        d.hit(pos);
                    } else {
                        d.shatter();
                    }
                }
            }
            case AizenNet.FRAGOR -> RUNS.add(new Fragor(id, pos, dir, a));
            case AizenNet.HADO99 -> RUNS.add(new Goryu(id, pos, dir, a));
            case AizenNet.PRESS -> {
                Press p = (Press) find(Press.class, id);
                if (p == null || p.boomed) {
                    p = new Press(id, pos);
                    RUNS.add(p);
                }
                p.feed(pos, a, b);
            }
            case AizenNet.PRESS_BOOM -> {
                Press p = (Press) find(Press.class, id);
                if (p != null && !p.boomed) {
                    p.boom(pos, a);
                }
            }
            case AizenNet.HOGYOKU -> {
                Evo e = (Evo) find(Evo.class, id);
                if (e == null) {
                    RUNS.add(new Evo(id, pos, a / 20f, b / 20f));
                } else {
                    e.total = b / 20f;
                    if (Math.abs(e.t - a / 20f) > 0.5f) {
                        e.t = a / 20f;
                    }
                }
            }
            case AizenNet.HOGYOKU_END -> {
                Evo e = (Evo) find(Evo.class, id);
                if (e != null) {
                    e.end();
                }
            }
            default -> {
            }
        }
    }

    public static boolean active() {
        return !RUNS.isEmpty() || !SPARKS.isEmpty() || !SHARDS.isEmpty();
    }

    public static void clear() {
        for (Run r : RUNS) {
            r.dispose();
        }
        RUNS.clear();
        SPARKS.clear();
        SHARDS.clear();
        last = -1;
    }

    // Every frame, before the passes: steps the timelines and queues the pieces.
    static void render(Vec3 camera, double now, float pt) {
        cam = camera;
        partial = pt;
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        double sec = now / 20.0;
        float dt = last < 0 ? 0 : (float) Mth.clamp(sec - last, 0, 0.1);
        last = sec;
        clock += dt;
        AizenMesh.time(clock);
        if (!AizenMesh.ready()) {
            // flat (a shader pack) or the shader missing: the timelines still run, only the 3D pieces are skipped
        }
        meshes();
        Iterator<Run> it = RUNS.iterator();
        while (it.hasNext()) {
            Run r = it.next();
            if (!r.done) {
                r.t += dt;
                r.update(dt);
            }
            if (r.done) {
                r.dispose();
                it.remove();
            }
        }
        stepShards(dt);
        stepSparks();
    }

    // =====================================================================================================
    // C · Hado 63 Raikoho
    // =====================================================================================================

    static final class Raiko extends Run {
        static final int GOLD = 0xFFD23A;
        final Energy beamE = new Energy(0xFFFBE6, 0xFFC81E, 0xB34A06, 0.16f);
        final AizenGeo.Tube beam = new AizenGeo.Tube(64, 14, true);
        final Arc[] arcs = new Arc[3];
        final Branch[] branches = new Branch[7];
        final Vector3f[] bp = AizenGeo.pts(64);
        final float[] br = new float[65];
        String phase = "carga";
        float flow, ts;
        boolean hit;
        Vec3 hand, end, boomAt, floorAt;
        boolean grounded;
        float seedD = rnd() * 10, seedS = rnd() * 10, seedR = rnd() * 10;

        static final class Arc {
            Energy e;
            AizenGeo.Tube tb;
            Vector3f[] p = AizenGeo.pts(40);
            float rr, span, w, spd, tilt, ph;
        }

        static final class Branch {
            Energy e;
            AizenGeo.Tube tb;
            Vector3f[] p = AizenGeo.pts(20);
            float life, t = 9, w, amp;
            Vector3f a, bp;
            int sa, sb;
        }

        Raiko(int id, Vec3 hand, Vec3 end) {
            super(hand, end.subtract(hand));
            caster = id;
            this.hand = hand;
            this.end = end;
            own(beam.geo);
            for (int i = 0; i < 3; i++) {
                Arc a = new Arc();
                a.e = new Energy(0xFFFBE6, 0xFFE060, 0xD06A10, 0);
                a.tb = new AizenGeo.Tube(40, 8, true);
                own(a.tb.geo);
                a.rr = 0.55f + 0.2f * i + 0.1f * rnd();
                a.span = 2.2f + 0.9f * rnd();
                a.w = 0.045f + 0.03f * rnd();
                a.spd = (i % 2 == 1 ? -1 : 1) * (7 + 4 * rnd());
                a.tilt = rnd() * 1.2f - 0.6f;
                a.ph = rnd() * 6;
                arcs[i] = a;
            }
            for (int i = 0; i < 7; i++) {
                Branch b = new Branch();
                b.e = new Energy(0xFFFBE6, 0xFFE060, 0xC05A08, 0.1f);
                b.e.fadeEnds = 0;
                b.tb = new AizenGeo.Tube(20, 6, true);
                own(b.tb.geo);
                branches[i] = b;
            }
        }

        void fire(Vec3 h, Vec3 e) {
            hand = h;
            end = e;
            phase = "tiro";
            ts = 0;
        }

        @Override
        void update(float dt) {
            flow += dt * 18;
            Entity ent = FxAnchor.entity(caster);
            Matrix4f id = new Matrix4f().translation(rel(Vec3.ZERO));
            if (phase.equals("carga")) {
                if (t > 1.6f) {
                    done = true;
                    return;
                }
                Vec3 h = handOf(ent, hand);
                Vec3 look = ent != null ? ent.getViewVector(partial) : end.subtract(hand).normalize();
                Vector3f fwd = new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
                Vector3f right = new Vector3f(fwd).cross(0, 1, 0);
                if (right.lengthSquared() < 1.0E-6f) {
                    right.set(1, 0, 0);
                }
                right.normalize();
                Vector3f upv = new Vector3f(right).cross(fwd).normalize();
                Vector3f back = new Vector3f(fwd).negate();
                float k = seg(t, 0, 0.7f), close = lerp(1, 0.55f, smooth(k));
                Vector3f hc = rel(h);
                for (Arc a : arcs) {
                    Vector3f b1 = right, b2 = new Vector3f(upv).mul(Mth.cos(a.tilt)).fma(Mth.sin(a.tilt), back);
                    float a0 = a.ph + clock * a.spd;
                    for (int i = 0; i <= 40; i++) {
                        float u = i / 40f, an = a0 + a.span * u, r = a.rr * close * (1 + 0.12f * Mth.sin(u * 9 + clock * 20 * (a.spd > 0 ? 1 : -1)));
                        a.p[i].set(hc).fma(Mth.cos(an) * r, b1).fma(Mth.sin(an) * r, b2).fma(0.15f + 0.1f * Mth.sin(an * 2), fwd);
                    }
                    float kk = k;
                    a.tb.update(a.p, i -> a.w * Math.pow(Math.sin(Math.PI * i / 40), 0.7) * (0.6 + 0.6 * kk) + 0.004, null);
                    a.e.alpha = smooth(k * 3);
                    a.e.flow = flow;
                    a.e.queue(a.tb.geo, new Matrix4f());
                }
                if (rnd() < 0.6f) {
                    Vector3f d = randDir().mul(2 + rnd() * 3);
                    spark(h, vec(d), 0.2f + rnd() * 0.25f, 0.03f + rnd() * 0.04f, 0xFFCC33, 0xFFFBE0, 9, 2.5f);
                }
                if (((int) (t * 20)) % 4 == 0) {
                    shake(h, 0.04f * k);
                }
                return;
            }
            // the shot
            ts += dt;
            Vector3f hc = rel(hand), ec = rel(end);
            float grow = seg(ts, 0, 0.09f), fade = 1 - seg(ts, 0.75f, 1.0f);
            Vector3f tip = new Vector3f(hc).lerp(ec, grow);
            float per = 0.085f;
            int si = (int) Math.floor(clock / per);
            float mk = smooth((clock / per) % 1);
            AizenGeo.jag(hc, tip, 64, si * 3 + 1, si * 3 + 4, mk, 2.2f * Math.max(0.2f, grow), 6, bp, true);
            for (int i = 0; i <= 64; i++) {
                float u = i / 64f;
                br[i] = (0.22f + 0.75f * smooth(u * 2.2f)) * (0.8f + 0.45f * lerp(AizenGeo.hsh(si, i >> 2, 9), AizenGeo.hsh(si + 1, i >> 2, 9), mk)) * (0.25f + 0.75f * fade);
            }
            if (fade > 0.01f) {
                beam.update(bp, i -> br[i], null);
                beamE.flow = flow;
                beamE.alpha = fade;
                beamE.queue(beam.geo, new Matrix4f());
            }
            Vector3f dir = new Vector3f(ec).sub(hc).normalize();
            for (Branch b : branches) {
                b.t += dt;
                if (b.t > b.life && fade > 0.3f && grow >= 1 && rnd() < 0.25f) {
                    spawn(b, bp[6 + (int) (rnd() * 54)], dir);
                }
                float k2 = b.t / b.life;
                if (k2 < 1 && b.a != null) {
                    float g2 = smooth(k2 / 0.3f);
                    Vector3f tip2 = new Vector3f(b.a).lerp(b.bp, g2);
                    AizenGeo.jag(b.a, tip2, 20, b.sa, b.sb, k2, b.amp, 4, b.p, false);
                    b.tb.update(b.p, i -> b.w * Math.pow(1 - i / 20.0, 0.9) + 0.006, null);
                    b.e.alpha = (1 - smooth((k2 - 0.5f) * 2)) * fade;
                    b.e.flow = flow;
                    b.e.queue(b.tb.geo, new Matrix4f());
                }
            }
            if (!hit && grow >= 1) {
                boom();
            }
            if (hit) {
                float tb = ts - 0.05f;
                Vec3 c = new Vec3(end.x, floorAt.y + 0.4, end.z);
                blast(grounded ? c : end, 13 * ease3(tb / 0.9f), clamp(tb / 2.6f, 0, 1), 0xFFCF3A, seedD, false, 0.3f);
                float late = Math.max(0, tb - 0.6f);
                Vec3 sc = new Vec3(end.x, (grounded ? floorAt.y : end.y - 0.6) + 0.5 + late * 1.4, end.z);
                blast(sc, 12 * (1 + late * 0.12f), tb < 0.6f ? 1 : 0.3f + 0.7f * clamp((tb - 0.6f) / 5, 0, 1), 0x9C8A6E, seedS, true, 0.3f);
                if (grounded) {
                    ring(floorAt, 34 * ease3(tb / 1.8f), 38, clamp(tb / 2.8f, 0, 1), GOLD, seedR);
                }
                if (tb > 5.7f) {
                    done = true;
                }
            }
        }

        void spawn(Branch b, Vector3f a, Vector3f dir) {
            Vector3f side = randDir();
            side.fma(-side.dot(dir), dir).normalize();
            float ang = 0.35f + rnd() * 0.6f, len = 3 + rnd() * 9;
            Vector3f d = new Vector3f(dir).mul(Mth.cos(ang)).fma(Mth.sin(ang), side);
            b.a = new Vector3f(a);
            b.bp = new Vector3f(a).fma(len, d);
            b.w = 0.1f + rnd() * 0.22f;
            b.life = 0.18f + rnd() * 0.22f;
            b.t = 0;
            b.sa = (int) (rnd() * 1e5);
            b.sb = b.sa + 1;
            b.amp = len * 0.12f;
        }

        void boom() {
            hit = true;
            double fy = floor(end, 0.7);
            grounded = fy > end.y - 7;
            floorAt = new Vec3(end.x, fy, end.z);
            Vec3 at = end;
            shock(at, 3.2f, 0xFFE9A0);
            speedLines(at);
            manga(at, GOLD);
            shake(at, 1.25f);
            for (int i = 0; i < 260; i++) {
                Vector3f d = randDir();
                d.y = Math.abs(d.y) * 0.8f + 0.1f;
                spark(at, vec(d.mul(10 + rnd() * 30)), 0.4f + rnd() * 0.8f, 0.12f + rnd() * 0.25f, 0xFFCC33, 0xFFFBE0, 9, 2.5f);
            }
        }
    }

    // =====================================================================================================
    // Shift+X · Hado 33 Sokatsui
    // =====================================================================================================

    static final class Soka extends Run {
        // While it pours the fire follows the caster's aim (AizenNet.SOKA_AIM, Stark 2026-11-22: mobile like the Katon):
        // dir and len ease toward the server's newest aim; once it comes loose from the hand (STREAM) they stay put.
        Vec3 dir, wantDir;
        float len, wantLen;
        Vec3 firePos;
        // where the fire first landed: the burst and its smoke stay there, in the world, whatever the caster does
        Vec3 burstAt;
        boolean hit;
        final float seedB = rnd() * 10, seedS = rnd() * 10, seedW = rnd() * 10;
        static final float STREAM = 0.55f;

        Soka(int id, Vec3 hand, Vec3 dir, float reach) {
            super(hand, dir);
            caster = id;
            this.dir = this.wantDir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
            this.len = this.wantLen = Math.max(2, reach);
            this.firePos = hand;
        }

        void aim(Vec3 d, float reach) {
            if (t < STREAM && d.lengthSqr() > 1.0E-6) {
                wantDir = d.normalize();
                wantLen = Math.max(2, reach);
            }
        }

        // a new Sokatsui from the same caster: this one is let go where it is
        void loose() {
            t = Math.max(t, STREAM);
        }

        Matrix4f basis(Vec3 at, float sx, float sy, float sz) {
            Vector3f y = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
            Vector3f z = new Vector3f(0, 1, 0).fma(-y.y, y);
            if (z.lengthSquared() < 1.0E-6f) {
                z.set(1, 0, 0);
            }
            z.normalize();
            Vector3f x = new Vector3f(y).cross(z).normalize();
            Matrix4f m = new Matrix4f().translation(rel(at));
            Matrix4f r = new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1);
            return m.mul(r).scale(sx, sy, sz);
        }

        @Override
        void update(float dt) {
            Entity ent = FxAnchor.entity(caster);
            if (t < STREAM) {
                float k = 1 - (float) Math.exp(-dt * 14);
                Vec3 nd = dir.add(wantDir.subtract(dir).scale(k));
                dir = nd.lengthSqr() < 1.0E-6 ? wantDir : nd.normalize();
                len += (wantLen - len) * k;
                Vec3 h = handOf(ent, firePos);
                firePos = h.add(dir.scale(0.1));
            }
            float flow = clock * 9;
            float head = len * ease3(seg(t, 0.12f, 0.32f)) + 1.2f, tail = -2 + len * 0.5f * smooth(seg(t, 0.55f, 2.4f));
            float diss = (float) Math.pow(seg(t, 0.5f, 2.6f), 1.5), alpha = 1 - smooth(seg(t, 1.9f, 2.7f));
            if (t > 0.12f && t < 2.75f) {
                AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.SOKA);
                p.a(len, flow, head, tail).b(diss, 0, 0, 0);
                p.alpha = alpha;
                p.sides = AizenMesh.BOTH;
                p.order = 14;
                AizenMesh.queue(cyl, basis(firePos, 1, 1, 1), p);
            }
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = dir.cross(up).lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.cross(up).normalize();
            if (t > 0.5f && t < 2.5f) {
                int n = t < 1.5f ? 3 : 1;
                for (int i = 0; i < n; i++) {
                    float u = tail + rnd() * (len - tail);
                    if (u < 0.5f) {
                        continue;
                    }
                    Vec3 p = firePos.add(right.scale((rnd() - 0.5f) * 0.6f * (0.3f + u * 0.12f))).add(0, (rnd() - 0.3f) * 0.5f + diss * 0.8f, 0).add(dir.scale(u));
                    spark(p, new Vec3((rnd() - 0.5f) * 0.8f, 0.6f + rnd() * 1.4f, (rnd() - 0.5f) * 0.8f), 0.8f + rnd() * 0.8f, 0.03f + rnd() * 0.05f, 0x4AA8FF, 0xE8FBFF, -1.5f, 2);
                }
            }
            if (t > 0.6f) {
                float w = t - 0.6f;
                Vec3 c = firePos.add(0, 0.5f + w * 0.7f, 0).add(dir.scale(7 * len / 14));
                float r = 2.4f + w * 1.5f, life = 0.12f + 0.88f * clamp(w / 2.4f, 0, 1);
                Vec3 flat = new Vec3(dir.x, 0, dir.z);
                Vec3 fd = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
                Matrix4f m = new Matrix4f().translation(rel(c)).rotateY((float) Math.atan2(fd.x, fd.z)).scale(r * 0.9f, r * 0.7f, r * 2.6f);
                blast(m, r, life, 0x5A78B0, seedW, true, 0.3f);
            }
            if (!hit && t >= 0.34f) {
                hit = true;
                burstAt = firePos.add(dir.scale(len));
                shock(burstAt.add(0, 0.3, 0), 1.8f, 0xA8D8FF);
                shake(burstAt, 0.55f);
            }
            if (hit) {
                float k = t - 0.34f;
                Vec3 at = burstAt;
                blast(at, 4.2f * ease3(k / 0.45f), clamp(k / 1.4f, 0, 1), 0x3A8CFF, seedB, false, 0.3f);
                double fy = floor(at, 1.1);
                blast(new Vec3(at.x, fy + 0.6 + k * 0.9, at.z), 4.2f, 0.35f + 0.65f * clamp(k / 2.5f, 0, 1), 0x4A5468, seedS, true, 0.3f);
                if (k < 0.6f && rnd() < 0.8f) {
                    spark(at.add(rnd() - 0.5, rnd() - 0.5, rnd() - 0.5), new Vec3((rnd() - 0.5f) * 2, 1 + rnd() * 3, (rnd() - 0.5f) * 2), 0.4f + rnd() * 0.5f, 0.04f + rnd() * 0.06f,
                            0x4AA8FF, 0xE8FBFF, -1.5f, 2);
                }
            }
            if (t > 3.1f) {
                done = true;
            }
        }
    }

    // =====================================================================================================
    // B · Um Dedo: the blow stops on a finger, he vanishes and lands behind; DEDO_CUT then shows the cut
    // =====================================================================================================

    static final int DANKU_SPARK_A = 0xA98BFF, DANKU_SPARK_B = 0xFFFFFF;

    static void landSparks(Vec3 at) {
        for (int i = 0; i < 40; i++) {
            Vec3 p = at.add((rnd() - 0.5) * 0.6 * PS, (0.3 + rnd() * 1.7) * PS, (rnd() - 0.5) * 0.6 * PS);
            spark(p, vec(randDir().mul(1 + rnd() * 2)), 0.3f + rnd() * 0.3f, 0.03f + rnd() * 0.04f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
        }
    }

    static final class Dedo extends Run {
        final Vec3 tip;
        final Flash flash = new Flash();
        boolean behind;
        final float seedD = rnd() * 10, seedR = rnd() * 10;
        final Vec3 ground;

        Dedo(int id, Vec3 tip, Vec3 toward) {
            super(feetOf(id, tip), toward);
            caster = id;
            this.tip = tip;
            Vec3 g = world(0, 0, -0.9f);
            ground = new Vec3(g.x, floor(origin.add(0, 0.5, 0), 0), g.z);
            shock(tip, 2.6f, 0xD9C8FF);
            manga(tip, 0xB89CFF);
            shake(tip, 0.75f);
            flash.hit(tip, 0xD8C8FF, 0.8f, 0.3f);
            for (int i = 0; i < 90; i++) {
                spark(tip, vec(randDir().mul(3 + rnd() * 8)), 0.25f + rnd() * 0.4f, 0.03f + rnd() * 0.05f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
            }
            // the attacker's blow breaks on the fingertip
            for (int i = 0; i < 26; i++) {
                Vector3f d = randDir();
                Shard s = shard(tip, vec(d.mul(2 + rnd() * 6)).add(0, 2, 0), new Vector3f(0.03f + rnd() * 0.08f, 0.06f + rnd() * 0.22f, 0.01f + rnd() * 0.02f), false,
                        0xCFEFFF, 0x2A5A7A, 0.55f, 9, 0.3f, 1.2f, 2.0f);
                s.sp = 8 + rnd() * 14;
            }
        }

        static Vec3 feetOf(int id, Vec3 fallback) {
            Entity e = FxAnchor.entity(id);
            return e == null ? fallback.add(0, -1.2, 0) : e.position();
        }

        void landed(Vec3 at) {
            landSparks(at);
        }

        @Override
        void update(float dt) {
            flash.step(dt);
            // the page's k: its clock stands still through the hitstop (0.55 s)
            float k = Math.max(0, t - 0.55f);
            if (k > 0) {
                ring(ground, 22 * ease3(k / 1.2f), 24, clamp(k / 2.2f, 0, 1), 0xC9B4FF, seedR);
            }
            blast(new Vec3(ground.x, ground.y + 0.4 + k * 0.4, ground.z), 5 + k * 2.5f, k < 0.05f ? 1 : 0.35f + 0.65f * clamp(k / 2.4f, 0, 1), 0x8A8494, seedD, true, 0.3f);
            if (!behind && k >= 0.22f) {
                behind = true;
                Entity e = FxAnchor.entity(caster);
                Vec3 at = e == null ? origin : e.position();
                for (int i = 0; i < 60; i++) {
                    Vec3 p = at.add((rnd() - 0.5) * 0.6 * PS, (0.3 + rnd() * 1.7) * PS, (rnd() - 0.5) * 0.6 * PS);
                    spark(p, vec(randDir().mul(1 + rnd() * 3)), 0.3f + rnd() * 0.3f, 0.03f + rnd() * 0.04f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
                }
                shock(at.add(0, 1.2 * PS, 0), 1.0f, 0xCDB8FF);
            }
            if (k > 2.6f) {
                done = true;
            }
        }
    }

    // The cut that had already happened: the crescent sweeping, the dome, the smoke, the dust ring.
    static final class Cut extends Run {
        final Vec3 at, ground;
        final AizenMesh.Geo cres = own(new AizenMesh.Geo(false));
        final Flash flash = new Flash();
        final float seedB = rnd() * 10, seedS = rnd() * 10, seedR = rnd() * 10;

        Cut(Vec3 at, Vec3 fly) {
            // the page: the attacker flies toward +z
            super(at, fly.reverse());
            this.at = at;
            ground = new Vec3(at.x, floor(at, 1.2), at.z);
            shock(at, 3.4f, 0xE8DCFF);
            manga(at, 0xFFFFFF);
            speedLines(at);
            shake(at, 1.1f);
            flash.hit(at, 0xD9C8FF, 2.2f, 0.3f);
            for (int i = 0; i < 120; i++) {
                spark(at, vec(randDir().mul(4 + rnd() * 10)), 0.3f + rnd() * 0.5f, 0.04f + rnd() * 0.06f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
            }
            // crescent(cutC, 6.5, 2.2, 5.2, 0.55, 2.4), in the page's frame round the cut's middle (local origin = at)
            Quaternionf q = new Quaternionf().rotationXYZ(0.55f, 0, 0.5f);
            Vector3f cutC = new Vector3f(0, 0, 0).sub(q.transform(new Vector3f(Mth.cos(3.7f) * 6.5f, 0, Mth.sin(3.7f) * 6.5f)));
            int n = 64;
            float r = 6.5f, w = 2.4f;
            Vector3f[] inn = new Vector3f[n + 1], out = new Vector3f[n + 1];
            for (int i = 0; i <= n; i++) {
                float k = (float) i / n, a = lerp(2.2f, 5.2f, k), wd = w * (float) Math.pow(Math.sin(Math.PI * k), 0.8);
                out[i] = q.transform(new Vector3f(Mth.cos(a) * r, 0, Mth.sin(a) * r)).add(cutC);
                inn[i] = q.transform(new Vector3f(Mth.cos(a) * (r - wd), 0, Mth.sin(a) * (r - wd))).add(cutC);
            }
            for (int i = 0; i < n; i++) {
                float k0 = (float) i / n, k1 = (float) (i + 1) / n;
                // (a, a+1, a+2), (a+1, a+3, a+2) with a = inner i, a+1 = outer i, a+2 = inner i+1, a+3 = outer i+1
                cv(inn[i], k0, 0); cv(out[i], k0, 1); cv(inn[i + 1], k1, 0);
                cv(out[i], k0, 1); cv(out[i + 1], k1, 1); cv(inn[i + 1], k1, 0);
            }
        }

        private void cv(Vector3f p, float u, float v) {
            cres.v(p.x, p.y, p.z, u, v, 0, 1, 0);
        }

        @Override
        void update(float dt) {
            flash.step(dt);
            float r = t;
            AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.CRES);
            p.alpha = 1 - seg(r, 0.25f, 0.7f);
            p.a(Math.min(1.4f, r / 0.12f * 1.4f), 0, 0, 0);
            p.seed = 3;
            p.sides = AizenMesh.DOUBLE;
            p.order = 17;
            AizenMesh.queue(cres, frame(), p);
            blast(at, 3.6f * ease3(r / 0.4f), clamp(0.25f + r / 0.9f, 0, 1), 0x6A3CD0, seedB, false, 0.3f);
            Vec3 sc = world(0, 0, 3);
            blast(new Vec3(sc.x, ground.y + 0.6 + r * 1.1, sc.z), 8 * (1 + r * 0.15f), 0.3f + 0.7f * clamp(r / 3, 0, 1), 0x6E6680, seedS, true, 0.3f);
            ring(ground, 38 * ease3(r / 1.6f), 42, clamp(r / 2.8f, 0, 1), 0xB48CFF, seedR);
            if (r > 3.2f) {
                done = true;
            }
        }
    }

    // =====================================================================================================
    // B · Danku: the glass pane rises, the blow spreads on it like a dark star, it cracks and shatters
    // =====================================================================================================

    static final class Danku extends Run {
        static final float WW = 3.4f, WH = 4.4f;
        float hitT = -1, pendingHit = -1;
        final float[] uHit = {0, 0.2f};
        Vec3 hitAt;
        boolean shattered;
        final Flash flash = new Flash();

        Danku(int id, Vec3 foot, Vec3 face) {
            super(foot.subtract(face.scale(2.3)), face);
            caster = id;
        }

        void hit(Vec3 at) {
            hitAt = at;
            Vector3f l = local(at);
            uHit[0] = clamp(l.x, -WW / 2 + 0.3f, WW / 2 - 0.3f);
            uHit[1] = clamp(l.y - WH / 2, -WH / 2 + 0.3f, WH / 2 - 0.3f);
            pendingHit = Math.max(t, 0.2f);
        }

        void shatter() {
            if (shattered) {
                return;
            }
            shattered = true;
            Vec3 c = world(0, WH / 2, -2.3f);
            shake(c, 0.3f);
            for (int i = 0; i < 90; i++) {
                float px = (rnd() - 0.5f) * WW, py = rnd() * WH;
                float fromHit = (float) Math.hypot(px - uHit[0], py - (uHit[1] + WH / 2));
                Vec3 p = world(px, py, -2.3f);
                Vec3 v = dirWorld((px - uHit[0]) * (1 + rnd()), 1 + rnd() * 3, 1.5f + rnd() * 3 - fromHit * 0.2f);
                Shard s = shard(p, v, new Vector3f(0.05f + rnd() * 0.28f, 0.05f + rnd() * 0.35f, 0.01f + rnd() * 0.03f), false, 0xCFEFFF, 0x2A5A7A, 0.55f, 9, 0.3f, 1.2f, 2.0f);
                s.sp = 4 + rnd() * 10;
            }
        }

        @Override
        void update(float dt) {
            flash.step(dt);
            if (pendingHit >= 0 && t >= pendingHit) {
                hitT = pendingHit;
                pendingHit = -1;
                Vec3 at = world(uHit[0], uHit[1] + WH / 2, -2.45f);
                shock(at, 1.6f, 0xBFE6FF);
                manga(at, 0x9FDCFF);
                shake(at, 0.5f);
                flash.hit(at, 0xFFB48A, 1.4f, 0.3f);
                for (int i = 0; i < 60; i++) {
                    Vector3f d = randDir();
                    d.z = Math.abs(d.z);
                    spark(at, dirWorld(d.x, d.y, d.z).scale(2 + rnd() * 5), 0.25f + rnd() * 0.4f, 0.03f + rnd() * 0.05f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
                }
            }
            if (!shattered) {
                float k = hitT < 0 ? -1 : t - hitT;
                AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.WALL);
                float splat = k < 0 ? 0 : ease3(k / 0.22f) * (1 - 0.25f * seg(k, 0.6f, 1.4f)), crack = k < 0 ? 0 : ease3(seg(k, 0.12f, 0.6f));
                p.a(smooth(seg(t, 0, 0.27f)), splat, crack, 0).b(WH, WW, uHit[0], uHit[1]);
                p.sides = AizenMesh.DOUBLE;
                p.order = 16;
                AizenMesh.queue(wallBox, frame().translate(0, WH / 2, -2.3f), p);
            }
            if (t > (shattered ? 2.6f : 3.5f)) {
                if (!shattered) {
                    shatter();
                }
                done = t > 4.5f || shattered && t > 2.6f;
            }
        }
    }

    // =====================================================================================================
    // V · the Hogyoku's evolution: the orb, the cocoon cracking, the burst and the six wings for the whole form
    // =====================================================================================================

    static final class Evo extends Run {
        float total;
        float endAt = -1;
        boolean burst;
        float open;
        final Flash flash = new Flash();
        final float seedR = rnd() * 10, seedS = rnd() * 10;
        static final float[][] BASE = {{0.12f, -0.38f}, {-0.12f, -0.46f}, {-0.06f, -0.34f}};
        static final float BURST = 2.75f;

        Evo(int id, Vec3 feet, float age, float total) {
            super(feet, null);
            caster = id;
            t = age;
            this.total = total;
            burst = age > BURST + 0.2f;
        }

        void end() {
            if (endAt < 0) {
                endAt = t;
            }
        }

        @Override
        void update(float dt) {
            flash.step(dt);
            Entity e = FxAnchor.entity(caster);
            if (e == null || !e.isAlive()) {
                done = true;
                return;
            }
            origin = e.getPosition(partial);
            boolean self = FxAnchor.firstPersonSelf(caster);
            Matrix4f body = bodyFrame(e);
            if (!burst) {
                float a = smooth(seg(t, 0, 0.6f));
                if (!self) {
                    AizenMesh.Piece orb = new AizenMesh.Piece(AizenMesh.ORB);
                    orb.alpha = a;
                    orb.order = 15;
                    Matrix4f om = new Matrix4f(body).translate(0, 1.25f, -0.15f);
                    AizenMesh.queue(sphLo, new Matrix4f(om).scale(0.11f), orb);
                    AizenMesh.Piece halo = new AizenMesh.Piece(AizenMesh.OHALO);
                    halo.alpha = a * (0.8f + 0.2f * Mth.sin(clock * 9));
                    halo.sides = AizenMesh.BACK;
                    halo.order = 15;
                    AizenMesh.queue(sphHalo, new Matrix4f(om).scale(0.35f + 0.25f * a), halo);
                }
                if (t > 0.7f) {
                    AizenMesh.Piece c = new AizenMesh.Piece(AizenMesh.COC);
                    c.a(smooth(seg(t, 0.7f, 1.5f)), easeIn2(seg(t, 1.6f, 2.7f)), seg(t, 1.8f, 2.7f), 0);
                    c.sides = AizenMesh.DOUBLE;
                    c.solid();
                    c.glow = true;
                    // from inside (first person) the cocoon would cover the screen: there it stays see-through-free
                    if (!self) {
                        AizenMesh.queue(sphCoc, new Matrix4f(body).translate(0, 1.2f, 0), c);
                    }
                }
                if (((int) (t * 20)) % 4 == 0) {
                    shake(origin, 0.08f * seg(t, 1.6f, 2.7f));
                }
                if (t >= BURST) {
                    burst = true;
                    Vec3 c = origin.add(0, 1.2 * PS, 0);
                    shock(c, 2.4f, 0xD8C4FF);
                    speedLines(c);
                    manga(c, 0xB48CFF);
                    shake(c, 0.9f);
                    flash.hit(c, 0xE6DCFF, 1.5f, 0.45f);
                    for (int i = 0; i < 70; i++) {
                        Vector3f d = randDir();
                        Vector3f sz = new Vector3f(0.08f + rnd() * 0.3f, 0.08f + rnd() * 0.25f, 0.03f + rnd() * 0.08f).mul(PS);
                        Shard s = shard(c.add(d.x * 0.7 * PS, d.y * 1.2 * PS, d.z * 0.7 * PS), vec(new Vector3f(d).mul(5 + rnd() * 9)).add(0, 3, 0), sz, true, 0xF2EEF8, 0x241040, 1,
                                14, 0.25f, 2.2f, 3.2f);
                        s.sp = 3 + rnd() * 12;
                        s.liftByScale = true;
                    }
                }
            }
            if (burst) {
                float k = t - BURST;
                float target = endAt >= 0 ? 0 : 1;
                if (endAt >= 0) {
                    open = Math.max(0, open - dt / 0.6f);
                } else {
                    open = Math.max(open, ease3(k / 0.7f));
                    open = Math.min(target, open);
                }
                if (k < 3.6f) {
                    Vec3 g = new Vec3(origin.x, floor(origin.add(0, 0.5, 0), 0), origin.z);
                    ring(g, 32 * ease3(k / 1.6f), 36, clamp(k / 2.4f, 0, 1), 0xB48CFF, seedR);
                    blast(g.add(0, 0.2, 0), 9 + k * 1.2f, clamp(0.3f + k / 3.5f, 0, 1), 0xC9C2D8, seedS, true, 0.3f);
                }
                float glow = 1 - 0.7f * smooth(k / 1.5f);
                if (open > 0.001f && !self) {
                    wings(body, glow);
                }
                if (open > 0.3f && k > 0.4f && rnd() < (k < 3 ? 0.7f : 0.25f)) {
                    float sg = rnd() < 0.5f ? 1 : -1;
                    Vector3f lp = new Vector3f(sg * (0.4f + rnd() * 1.8f), 1.0f + rnd() * 2.2f, 0.3f + rnd() * 0.3f);
                    Vector3f wp = body.transformPosition(lp);
                    Vec3 p = new Vec3(wp.x + cam.x, wp.y + cam.y, wp.z + cam.z);
                    spark(p, new Vec3((rnd() - 0.5f) * 0.5f, -0.2f - rnd() * 0.4f, (rnd() - 0.5f) * 0.5f), 1.6f + rnd() * 1.6f, 0.03f + rnd() * 0.05f, 0xB48CFF, 0xFFFFFF, 0.6f,
                            1.2f);
                }
                if (endAt >= 0 && open <= 0) {
                    done = true;
                }
            }
            if (endAt < 0 && t > total + 3) {
                end();
            }
        }

        void wings(Matrix4f body, float glow) {
            Matrix4f root = new Matrix4f(body).translate(0, 1.38f, 0.16f);
            for (int sgI = 0; sgI < 2; sgI++) {
                float sg = sgI == 0 ? 1 : -1;
                for (int kind = 0; kind < 3; kind++) {
                    // A butterfly's beat (Stark 2026-11-22: "faz elas baterem"): about one beat a second, the stroke
                    // quicker than the return (the warped phase), the upper pair leading and the lower ones a beat behind,
                    // each wing tipping a little at the end of the stroke so it doesn't swing like a rigid board.
                    float ph = clock * 5.8f - kind * 0.55f;
                    ph += 0.35f * Mth.sin(ph);
                    float beat = Mth.sin(ph);
                    beat = Math.signum(beat) * (float) Math.pow(Math.abs(beat), 0.75);
                    float flap = (0.42f - kind * 0.06f) * beat * open;
                    float tip = 0.12f * Mth.cos(ph) * open;
                    float ry = sg * lerp(1.45f, BASE[kind][1] + flap, open), rz = sg * lerp(0.1f, BASE[kind][0] + tip, open);
                    Matrix4f piv = new Matrix4f(root).rotateXYZ(0, ry, rz).scale(lerp(0.15f, 1, open)).scale(sg * 0.78f, 0.78f, 0.78f);
                    AizenMesh.Piece w = new AizenMesh.Piece(AizenMesh.WING);
                    w.a(glow, 0, 0, 0);
                    w.alpha = 1;
                    w.sides = AizenMesh.DOUBLE;
                    w.solid();
                    AizenMesh.queue(WINGS[kind], piv, w);
                    if (kind == 2) {
                        AizenMesh.Piece tl = new AizenMesh.Piece(AizenMesh.LIT);
                        tl.tintA = rgb(0xECE8F6);
                        tl.tintB = rgb(0x2A1450);
                        tl.sides = AizenMesh.DOUBLE;
                        tl.glow = false;
                        tl.solid();
                        AizenMesh.queue(tendril, piv, tl);
                    }
                }
            }
        }
    }

    // =====================================================================================================
    // V again · Fragor: a star of spikes on the raised hand flies in an arc and blows into a smooth sphere
    // =====================================================================================================

    static final class Fragor extends Run {
        final Vec3 end, floorAt;
        final float r, s;
        final AizenMesh.Geo spikes = own(new AizenMesh.Geo(false));
        final float[][] spikeInfo = new float[9][];
        final Vector3f[] spikeDir = new Vector3f[9];
        final AizenMesh.Geo[] spikeGeo = new AizenMesh.Geo[9];
        float rx, ry;
        Vec3 sp, star;
        String phase = "carga";
        float tp;
        final float seedS = rnd() * 10, seedR = rnd() * 10;

        Fragor(int id, Vec3 hand, Vec3 end, float r) {
            super(hand, end.subtract(hand));
            caster = id;
            this.end = end;
            this.r = Math.max(4, r);
            this.s = this.r / 24f;
            floorAt = new Vec3(end.x, floor(end, 1), end.z);
            sp = hand;
            for (int i = 0; i < 9; i++) {
                float len = 0.4f + rnd() * 0.7f, w = 0.07f + rnd() * 0.08f;
                spikeGeo[i] = own(new AizenMesh.Geo(false));
                AizenGeo.cone(spikeGeo[i], new Matrix4f(), w, len, 4, len / 2);
                spikeDir[i] = randDir();
                spikeInfo[i] = new float[] {len, rnd() * 6};
            }
        }

        Vec3 raised(Entity e) {
            if (e == null) {
                return sp;
            }
            Vec3 look = e.getViewVector(partial);
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            return e.getEyePosition(partial).add(0, 0.55, 0).add(right.scale(0.3)).add(flat.scale(0.25));
        }

        void drawStar(Vec3 at, float k, float dt) {
            ry += dt * (phase.equals("voo") ? 8 : 2.4f);
            if (phase.equals("carga")) {
                rx += dt * 1.3f;
            }
            Matrix4f m = new Matrix4f().translation(rel(at)).rotateXYZ(rx, ry, 0);
            for (int i = 0; i < 9; i++) {
                AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.SPIKE);
                p.sides = AizenMesh.DOUBLE;
                p.order = 14;
                float sy = Math.max(0.01f, k * (0.85f + 0.15f * Mth.sin(clock * 9 + spikeInfo[i][1])));
                Matrix4f sm = new Matrix4f(m).rotate(new Quaternionf().rotationTo(0, 1, 0, spikeDir[i].x, spikeDir[i].y, spikeDir[i].z)).scale(1, sy, 1);
                AizenMesh.queue(spikeGeo[i], sm, p);
            }
            AizenMesh.Piece c = new AizenMesh.Piece(AizenMesh.CORE);
            c.order = 15;
            AizenMesh.queue(sphLo, new Matrix4f(m).scale(0.14f * (0.4f + 0.8f * k)), c);
        }

        @Override
        void update(float dt) {
            Entity e = FxAnchor.entity(caster);
            tp += dt;
            switch (phase) {
                case "carga" -> {
                    float k = smooth(seg(tp, 0.15f, 0.9f));
                    sp = raised(e);
                    star = sp;
                    drawStar(sp, k, dt);
                    if (((int) (tp * 20)) % 4 == 0) {
                        shake(sp, 0.05f * k);
                    }
                    if (tp >= 0.95f) {
                        phase = "voo";
                        tp = 0;
                    }
                }
                case "voo" -> {
                    float k = seg(tp, 0, 0.8f);
                    Vec3 p = sp.lerp(end, k * k * 0.4f + k * 0.6f).add(0, Mth.sin(k * Mth.PI) * 4, 0);
                    drawStar(p, 1, dt);
                    if (k >= 1) {
                        phase = "det";
                        tp = 0;
                        Vec3 at = floorAt.add(0, 1, 0);
                        shock(at, 4.5f, 0xFFD090);
                        speedLines(at);
                        manga(at, 0xFFB050);
                        shake(at, 1.6f);
                        for (int i = 0; i < 320; i++) {
                            Vector3f d = randDir();
                            d.y = Math.abs(d.y);
                            Vec3 o = floorAt.add(0, 0.8, 0).add(vec(new Vector3f(d).mul(10 * s)));
                            spark(o, vec(d.mul((20 + rnd() * 40) * s)), 0.6f + rnd() * 1.0f, (0.2f + rnd() * 0.4f) * Math.max(0.5f, s), 0xFFB040, 0xFFF4D0, 9, 2.5f);
                        }
                    }
                }
                default -> {
                    float tt = tp;
                    float rr = r * ease3(tt / 0.35f) * (1 + 0.012f * Mth.sin(clock * 40)), out = seg(tt, 1.2f, 1.8f);
                    if (out < 1) {
                        AizenMesh.Piece o = new AizenMesh.Piece(AizenMesh.ORBE);
                        o.alpha = 1 - out;
                        o.a(1 - 0.6f * seg(tt, 0.3f, 1.0f), 0, 0, 0);
                        if (o.alpha >= 0.99f) {
                            o.solid();
                        } else {
                            o.order = 13;
                        }
                        AizenMesh.queue(sph, new Matrix4f().translation(rel(floorAt)).scale(Math.max(rr * (1 - 0.15f * out), 0.01f)), o);
                        AizenMesh.Piece h = new AizenMesh.Piece(AizenMesh.EHALO);
                        h.alpha = 1 - out;
                        h.sides = AizenMesh.BACK;
                        h.order = 15;
                        AizenMesh.queue(sphHi, new Matrix4f().translation(rel(floorAt)).scale(Math.max(rr * 1.28f, 0.01f)), h);
                    }
                    ring(floorAt, 64 * s * ease3(tt / 1.8f), 70 * s, clamp(tt / 3, 0, 1), 0xFFB040, seedR);
                    float late = Math.max(0, tt - 1.4f);
                    blast(floorAt.add(0, 1 + late * 3, 0), 22 * s * (1 + late * 0.1f), tt < 1.4f ? 1 : 0.32f + 0.68f * clamp(late / 5, 0, 1), 0x8A6C58, seedS, true, 0.3f);
                    if (tt > 6.5f) {
                        done = true;
                    }
                }
            }
        }
    }

    // =====================================================================================================
    // Shift+V · Hado 99 Goryutenmetsu: the golden cracks, then five dragons from the ground, all landing together
    // =====================================================================================================

    static final float[] STARTS = {0, 0.16f, 0.3f, 0.42f, 0.55f};
    static final float CHANT = 2.0f, ARRIVE = 2.4f;

    static final class Dragon {
        final int seed;
        final float size, len, jawOpen;
        final int ns = 130;
        final AizenGeo.Tube tube = new AizenGeo.Tube(130, 18, true);
        final Vector3f[] p = AizenGeo.pts(130);
        final AizenGeo.Head head;
        final Energy halo;
        AizenGeo.Curve curve;
        float pathLen, speed;
        Vector3f spawn;
        boolean spawned;
        float alpha;

        Dragon(int seed) {
            this.seed = seed;
            size = 3.4f + 1.4f * r(1);
            len = 36 + 14 * r(2);
            jawOpen = 0.45f + 0.25f * r(3);
            head = AizenGeo.head(seed);
            halo = new Energy(0xFFFFFF, 0xC44CFF, 0x3A1890, 0.45f * size);
            halo.fadeEnds = 1;
        }

        float r(int k) {
            return AizenGeo.hsh(seed, k, 31);
        }

        // rises from the ground, arcs and dives on the target (page coordinates round the target)
        void path(Vector3f sp, Vector3f target, float h) {
            Vector3f side = new Vector3f(sp).sub(target).normalize();
            Vector3f perp = new Vector3f(-side.z, 0, side.x).mul(r(4) > 0.5f ? 1 : -1);
            Vector3f[] ctrl = {new Vector3f(sp.x, -12, sp.z), new Vector3f(sp.x, 4, sp.z), new Vector3f(sp).fma(4, side).add(0, h * 0.75f, 0).fma(6, perp),
                    new Vector3f(sp).lerp(target, 0.35f).add(0, h, 0).fma(8, perp), new Vector3f(sp).lerp(target, 0.75f).add(0, h * 0.7f, 0).fma(3, perp),
                    new Vector3f(target.x, 1.5f, target.z), new Vector3f(target.x, -12, target.z)};
            curve = new AizenGeo.Curve(ctrl, false);
            pathLen = curve.length;
        }

        double radius(float s) {
            float sz = size, rr;
            if (s < 0.08f) {
                rr = sz * lerp(0.55f, 0.5f, s / 0.08f);
            } else if (s < 0.42f) {
                rr = sz * lerp(0.5f, 0.74f, smooth((s - 0.08f) / 0.34f));
            } else {
                rr = sz * (0.74f * (float) Math.pow(1 - (s - 0.42f) / 0.58f, 0.85) + 0.04f);
            }
            return rr * (1 + 0.04f * Mth.sin(s * 70));
        }

        void update(float headAt, float time, float alpha) {
            this.alpha = alpha;
            Vector3f tmp = new Vector3f();
            for (int i = 0; i <= ns; i++) {
                float s = (float) i / ns, d = headAt - s * len, u = clamp(d / pathLen, 0, 1);
                curve.pointAt(u, p[i]);
                if (d < 0) {
                    p[i].y += d;
                }
                if (d > pathLen) {
                    curve.tangentAt(1, tmp);
                    p[i].fma(d - pathLen, tmp);
                }
                float w = Mth.sin(s * 11 - time * 5 + seed) * 0.32f * size * smooth(s * 4);
                p[i].x += w * 0.7f;
                p[i].z += w * 0.7f * Mth.cos(seed);
            }
            float sz = size;
            tube.update(p, i -> radius((float) i / ns), (i, ang, o) -> {
                float s = (float) i / ns, sn = (float) Math.sin(ang);
                if (sn > 0.55f && s > 0.04f && s < 0.7f) {
                    o[0] += sz * 0.42f * (float) Math.pow(Math.max(0, Mth.sin(s * 95 + seed)), 3) * (1 - s) * (sn - 0.55f) * 2.2f;
                }
            });
            AizenGeo.whiskers(head, time);
        }

        void queue(Matrix4f base, float time) {
            if (alpha <= 0.002f) {
                return;
            }
            // only the halo behind (the page: Tube(..., [haloE.mats[0], mat])); the energy tube's own white-core body over the
            // dragon's body painted the dragons white (Stark 2026-11-22: not like the page)
            halo.alpha = alpha * 0.9f;
            AizenMesh.queue(tube.geo, base, halo.piece(true));
            AizenMesh.Piece body = new AizenMesh.Piece(AizenMesh.DRAG);
            body.alpha = alpha;
            body.seed = seed * 1.7f;
            body.a(len, 0, 0, 0);
            body.sides = AizenMesh.DOUBLE;
            body.solid();
            AizenMesh.queue(tube.geo, base, body);
            if (alpha > 0.02f && p[0].y > -1.5f) {
                Vector3f z = new Vector3f(tube.tg[0]);
                Vector3f y = new Vector3f(0, 1, 0).fma(-z.y, z).normalize();
                Vector3f x = new Vector3f(y).cross(z);
                Vector3f hp = new Vector3f(p[0]).fma(-0.55f * size, z);
                Matrix4f hm = new Matrix4f(base).translate(hp).mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, z.x, z.y, z.z, 0, 0, 0, 0, 1)).scale(size);
                headPiece(head.skin, hm, 0);
                headPiece(head.teeth, hm, 1);
                headPiece(head.throat, hm, 2);
                headPiece(head.eyes, hm, 3);
                headPiece(head.whiskers[0].geo, hm, 0);
                headPiece(head.whiskers[1].geo, hm, 0);
                Matrix4f jm = new Matrix4f(hm).translate(0, -0.05f, 0.4f).rotateX(jawOpen * (0.85f + 0.15f * Mth.sin(time * 5 + seed)));
                headPiece(head.jawSkin, jm, 0);
                headPiece(head.jawTeeth, jm, 1);
            }
        }

        void headPiece(AizenMesh.Geo g, Matrix4f m, int kind) {
            AizenMesh.Piece p = new AizenMesh.Piece(AizenMesh.DHEAD);
            p.alpha = alpha;
            p.seed = seed * 1.7f;
            p.a(kind, 0, 0, 0);
            p.sides = AizenMesh.DOUBLE;
            p.solid();
            AizenMesh.queue(g, m, p);
        }

        void close() {
            tube.geo.close();
            head.close();
        }
    }

    static final class Goryu extends Run {
        final float r, s;
        final Dragon[] dragons = new Dragon[5];
        final Energy pillarE = new Energy(0xFFF0FF, 0xE070FF, 0x4A20B0, 4);
        final AizenGeo.Tube pillar = new AizenGeo.Tube(30, 32, true);
        boolean boom;
        float kBoom;
        final float seedD = rnd() * 10, seedS = rnd() * 10, seedR = rnd() * 10;

        Goryu(int id, Vec3 feet, Vec3 target, float r) {
            super(new Vec3(target.x, floor(target, 0), target.z), target.subtract(feet));
            caster = id;
            this.r = Math.max(8, r);
            this.s = this.r / 32f;
            own(pillar.geo);
            pillarE.fadeEnds = 1;
            Vector3f tgt = new Vector3f();
            for (int i = 0; i < 5; i++) {
                Dragon d = new Dragon(101 + i * 17);
                float a = Mth.PI / 2 + (i - 2) * 1.05f + (AizenGeo.hsh(i, 3, 3) - 0.5f) * 0.35f, rr = 28 + 8 * AizenGeo.hsh(i, 4, 4);
                // page: TGT.x + cos(a) R 1.15, TGT.z - sin(a) R 0.75; here the target is the frame's origin
                d.spawn = new Vector3f(Mth.cos(a) * rr * 1.15f, 0, -Mth.sin(a) * rr * 0.75f);
                d.path(d.spawn, tgt, 30 + 12 * AizenGeo.hsh(i, 5, 5));
                d.speed = (d.pathLen * 0.86f + 1.5f) / (ARRIVE - STARTS[i]);
                d.update(-5, 0, 0);
                dragons[i] = d;
            }
        }

        // the page's frame round the target, scaled down for a near target
        Matrix4f base() {
            return frame().scale(s);
        }

        @Override
        void update(float dt) {
            Matrix4f base = base();
            Vec3 center = origin;
            if (t < CHANT || !boom) {
                // the golden cracks spread round the target
                AizenMesh.Piece c = new AizenMesh.Piece(AizenMesh.CRACK);
                c.alpha = smooth(seg(t, 0.2f, 0.6f));
                c.a(48 * ease3(seg(t, 0.2f, 2.0f)), 0, 0, 0);
                c.sides = AizenMesh.DOUBLE;
                c.order = 7;
                AizenMesh.queue(crackPlane, new Matrix4f(base).translate(0, 0.08f / s, 0).scale(55, 1, 55), c);
                if (((int) (t * 20)) % 4 == 0) {
                    shake(center, 0.06f * seg(t, 0.5f, 2.0f));
                }
            }
            if (t >= CHANT) {
                float t2 = t - CHANT;
                for (int i = 0; i < 5; i++) {
                    Dragon d = dragons[i];
                    float tt = (boom ? t2 + 99 : t2) - STARTS[i];
                    if (!boom && !d.spawned && tt >= 0) {
                        d.spawned = true;
                        Vec3 sp = world(d.spawn.x * s, 0.5f, d.spawn.z * s);
                        shake(sp, 0.6f);
                        for (int k = 0; k < 60; k++) {
                            Vector3f v = randDir();
                            v.y = Math.abs(v.y) + 0.3f;
                            spark(sp, vec(v.mul((10 + rnd() * 20) * s)), 0.5f + rnd() * 0.6f, (0.15f + rnd() * 0.25f) * Math.max(0.5f, s), 0xE070FF, 0xFFE8FF, 6, 2.5f);
                        }
                    }
                    if (!boom) {
                        d.update(tt * d.speed - 1.5f, clock, tt > -0.2f ? 1 : 0);
                    }
                }
                if (!boom && t2 >= ARRIVE) {
                    boom = true;
                    kBoom = 0;
                    Vec3 at = center.add(0, 1, 0);
                    shock(at, 5, 0xF0A0FF);
                    speedLines(at);
                    manga(at, 0xE070FF);
                    shake(at, 1.8f);
                    for (int k = 0; k < 400; k++) {
                        Vector3f v = randDir();
                        v.y = Math.abs(v.y) * 1.3f;
                        spark(at, vec(v.mul((20 + rnd() * 50) * s)), 0.6f + rnd() * 1.2f, (0.25f + rnd() * 0.5f) * Math.max(0.5f, s), 0xE070FF, 0xFFE8FF, 6, 2.5f);
                    }
                }
                if (boom) {
                    kBoom += dt;
                    float k = kBoom;
                    for (Dragon d : dragons) {
                        d.update(d.pathLen + 2 + k * d.speed * 0.3f, clock, 1 - seg(k, 0, 0.9f));
                    }
                    blast(center.add(0, 0.5 * s, 0), 40 * s * ease3(k / 1.1f), clamp(k / 3.4f, 0, 1), 0xD24CFF, seedD, false, 0.3f);
                    float late = Math.max(0, k - 1.6f);
                    blast(center.add(0, (1 + late * 3.5f) * s, 0), 34 * s * (1 + late * 0.1f), k < 1.6f ? 1 : 0.32f + 0.68f * clamp(late / 5, 0, 1), 0x5A4A6A, seedS, true, 0.3f);
                    ring(center, 90 * s * ease3(k / 2.2f), 100 * s, clamp(k / 3.4f, 0, 1), 0xE070FF, seedR);
                    if (k < 1.8f) {
                        Vector3f[] pp = AizenGeo.pts(30);
                        float hh = 160 / s * ease3(k / 0.4f) * Math.max(0.6f, s), w = 12 * (0.5f + 0.5f * ease3(k / 0.5f)) * (1 - seg(k, 1.0f, 1.8f));
                        for (int i = 0; i <= 30; i++) {
                            pp[i].set(0, -4 + hh * i / 30, 0);
                        }
                        float ww = w;
                        pillar.update(pp, i -> ww * (1 - 0.35 * i / 30) * (1 + 0.06 * Math.sin(i * 1.7 - clock * 20)), null);
                        pillarE.flow = clock * 20;
                        pillarE.alpha = 1 - seg(k, 1.1f, 1.8f);
                        pillarE.queue(pillar.geo, base);
                    }
                    if (k > 6.8f) {
                        done = true;
                    }
                }
                for (Dragon d : dragons) {
                    d.queue(base, clock);
                }
            }
        }

        @Override
        void dispose() {
            super.dispose();
            for (Dragon d : dragons) {
                d.close();
            }
        }
    }

    // =====================================================================================================
    // Shift+B · Reiatsu: the black aura and the pressure dome while held, then it explodes
    // =====================================================================================================

    static final class Press extends Run {
        float radius = 6, power, sinceFeed;
        boolean boomed, cut;
        float kBoom, cutAt = -1;
        float boomR;
        int steps;
        final Flash flash = new Flash();
        final float seedB = rnd() * 10, seedS = rnd() * 10, seedR = rnd() * 10;
        Vec3 feet;

        Press(int id, Vec3 feet) {
            super(feet, null);
            caster = id;
            this.feet = feet;
        }

        void feed(Vec3 at, float r, float strength) {
            feet = at;
            radius = r;
            power = clamp((strength - 0.3f) / 0.7f, 0, 1);
            sinceFeed = 0;
        }

        void boom(Vec3 at, float r) {
            feet = at;
            if (r <= 0) {
                cut = true;
                cutAt = t;
                return;
            }
            boomed = true;
            boomR = r;
            kBoom = 0;
            Vec3 c = at.add(0, 1.4 * PS, 0);
            shock(c, 3.6f, 0xD8C4FF);
            speedLines(c);
            manga(c, 0xA07CFF);
            shake(c, 1.3f);
            flash.hit(c, 0xC9B4FF, 1.6f, 0.3f);
            for (int i = 0; i < 160; i++) {
                spark(c, vec(randDir().mul(6 + rnd() * 14)), 0.4f + rnd() * 0.5f, 0.05f + rnd() * 0.06f, DANKU_SPARK_A, DANKU_SPARK_B, 6, 2.5f);
            }
        }

        @Override
        void update(float dt) {
            flash.step(dt);
            sinceFeed += dt;
            Entity e = FxAnchor.entity(caster);
            Vec3 at = e != null ? e.getPosition(partial) : feet;
            double gy = floor(at.add(0, 0.5, 0), 0);
            Vec3 ground = new Vec3(at.x, gy, at.z);
            if (!boomed) {
                if (!cut && sinceFeed > 0.6f) {
                    cut = true;
                    cutAt = t;
                }
                float fade = cut ? 1 - seg(t - cutAt, 0, 0.3f) : 1;
                if (cut && fade <= 0) {
                    done = true;
                    return;
                }
                float s = power;
                if (e != null && !FxAnchor.firstPersonSelf(caster)) {
                    AizenMesh.Piece a = new AizenMesh.Piece(AizenMesh.RA);
                    a.alpha = smooth(seg(t, 0, 0.35f)) * fade;
                    a.a(0.4f + 0.6f * s, 0, 0, 0);
                    a.sides = AizenMesh.DOUBLE;
                    a.order = 15;
                    Matrix4f m = new Matrix4f().translation(rel(at)).translate(0, 1.15f * PS, 0).scale(PS).scale(0.95f + 0.4f * s, 1.35f + 0.9f * s, 0.95f + 0.4f * s);
                    AizenMesh.queue(sphRa, m, a);
                }
                AizenMesh.Piece d = new AizenMesh.Piece(AizenMesh.PDOME);
                d.alpha = smooth(seg(t, 0.1f, 0.6f)) * (0.6f + 0.4f * s) * fade;
                d.sides = AizenMesh.DOUBLE;
                d.order = 9;
                AizenMesh.queue(dome, new Matrix4f().translation(rel(ground)).scale(radius, radius * 0.55f, radius), d);
                if (((int) (t * 20)) % 4 == 0) {
                    shake(at, 0.04f + 0.22f * s);
                }
                float[] marks = {0.27f, 0.69f};
                while (steps < 2 && s >= marks[steps]) {
                    steps++;
                    shake(at, 0.35f);
                    shock(ground.add(0, 0.2, 0), 0.8f + 0.3f * steps, 0xB49CFF);
                }
                int n = Math.round((10 + 40 * s) * 0.6f * fade);
                for (int i = 0; i < n; i++) {
                    float a = rnd() * 6.283f, rr = (float) Math.sqrt(rnd()) * radius;
                    spark(ground.add(Mth.cos(a) * rr, 7 + rnd() * 9, Mth.sin(a) * rr), new Vec3(0, -16 - rnd() * 10, 0), 0.5f + rnd() * 0.3f, 0.025f + rnd() * 0.03f, 0x7A4CFF, 0xE6DCFF,
                            34, 0.2f);
                }
                return;
            }
            kBoom += dt;
            float k = kBoom, sc = boomR / 25f;
            blast(ground.add(0, 1, 0), 24 * sc * ease3(k / 0.9f), clamp(0.42f + k / 2.4f, 0, 1), 0x4A1CB0, seedB, false, 0.3f);
            blast(ground.add(0, 1.5 + k * 1.6, 0), 22 * sc * (1 + k * 0.1f), k < 0.5f ? 1 : 0.3f + 0.7f * clamp((k - 0.5f) / 5, 0, 1), 0x4E4860, seedS, true, 0.3f);
            ring(ground, 70 * sc * ease3(k / 2), 76 * sc, clamp(k / 3.2f, 0, 1), 0xA07CFF, seedR);
            if (k > 5.6f) {
                done = true;
            }
        }
    }
}
