package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.Shigaraki;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// B, Absorver (All For One; preview v5, "PERFEITO, uma das melhores"). He sees everyone's power (a shell of it on each,
// torn by noise in three colour bands: Fogo orange, Raio yellow, Gelo blue), grabs the closest, and a twisted cord pulls
// its power to his hand; then its shell is his for 15 s. B again uses it: a fire ball that bursts, a bolt redrawn at
// anime's 12 frames a second, or a row of ice spikes each its own shape and a block of ice over the target.
public final class AbsorbFx {
    // core, body, edge (the preview's ELEM)
    static final float[][][] ELEM = {
            {{1, 0.95f, 0.7f}, {1, 0.48f, 0.08f}, {0.55f, 0.06f, 0.02f}},
            {{1, 1, 0.92f}, {1, 0.86f, 0.25f}, {0.85f, 0.45f, 0.02f}},
            {{0.95f, 1, 1}, {0.5f, 0.85f, 1}, {0.12f, 0.42f, 0.85f}}};

    private static final class Grab {
        final int caster;
        final double start;
        double grabAt = -1;
        int victim = -1, element;
        Vec3 dir = new Vec3(0, 0, 1);

        Grab(int caster, double start) {
            this.caster = caster;
            this.start = start;
        }
    }

    private static final class Stolen {
        double start, until;
        int element;
    }

    private static final class Use {
        final int caster, element, target;
        final double start;
        final Vec3 from, to;
        final long seed;
        float[][] spikes; // per spike: base x, y, z, h, rad, seg, tilt x, yaw, tilt z, at, then the jitter
        float[][] jit;

        Use(int caster, int element, int target, double start, Vec3 from, Vec3 to) {
            this.caster = caster;
            this.element = element;
            this.target = target;
            this.start = start;
            this.from = from;
            this.to = to;
            this.seed = (long) (start * 1000);
        }
    }

    private static final Map<Integer, Grab> GRABS = new HashMap<>();
    private static final Map<Integer, Stolen> STOLEN = new HashMap<>();
    private static final List<Use> USES = new ArrayList<>();

    private AbsorbFx() {
    }

    static boolean active() {
        return !GRABS.isEmpty() || !STOLEN.isEmpty() || !USES.isEmpty();
    }

    static void clear() {
        GRABS.clear();
        STOLEN.clear();
        USES.clear();
    }

    // Shigaraki.ABSORB: a = 0 the vision, 1 the drain (b = victim, c = element, dir = his way), 2 stolen (b = ticks, c =
    // element), 3 lost, 4 the victim wilts.
    static void receive(int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ShigFx.clock();
        switch (Math.round(a)) {
            case 0 -> GRABS.put(id, new Grab(id, now));
            case 1 -> {
                Grab g = GRABS.computeIfAbsent(id, k -> new Grab(id, now - 0.65));
                g.grabAt = now;
                g.victim = Math.round(b);
                g.element = Math.round(c);
                g.dir = dir;
            }
            case 2 -> {
                Stolen s = new Stolen();
                s.start = now;
                s.until = now + b / 20.0;
                s.element = Math.round(c);
                STOLEN.put(id, s);
            }
            case 3 -> {
                Grab g = GRABS.get(id);
                if (g != null && g.grabAt < 0) {
                    GRABS.remove(id);
                }
                Stolen s = STOLEN.get(id);
                if (s != null) {
                    s.until = Math.min(s.until, now + 1.2);
                }
            }
            default -> {
            }
        }
    }

    // Shigaraki.USE: pos = from, dir = to, a = element, b = target.
    static void use(int id, Vec3 from, Vec3 to, float a, float b) {
        Use u = new Use(id, Math.round(a), Math.round(b), ShigFx.clock(), from, to);
        if (u.element == Shigaraki.ICE) {
            spikes(u);
        }
        USES.add(u);
    }

    // The power the client sees on someone (the server's Absorver.elementOf without the soul: by what it is).
    static int elementOf(Entity e) {
        EntityType<?> t = e.getType();
        if (t == EntityType.BLAZE || t == EntityType.MAGMA_CUBE || t == EntityType.GHAST || t == EntityType.STRIDER || t == EntityType.WITHER_SKELETON
                || t == EntityType.PIGLIN || t == EntityType.HOGLIN || t == EntityType.ZOMBIFIED_PIGLIN) {
            return Shigaraki.FIRE;
        }
        if (t == EntityType.STRAY || t == EntityType.SNOW_GOLEM || t == EntityType.POLAR_BEAR || t == EntityType.DROWNED || t == EntityType.GUARDIAN
                || t == EntityType.ELDER_GUARDIAN || t == EntityType.SQUID || t == EntityType.GLOW_SQUID) {
            return Shigaraki.ICE;
        }
        if (t == EntityType.CREEPER || t == EntityType.BEE || t == EntityType.PHANTOM || t == EntityType.VEX || t == EntityType.ALLAY) {
            return Shigaraki.LIGHTNING;
        }
        return Math.floorMod(e.getUUID().hashCode(), 3);
    }

    static void render(Vec3 camera, float partial) {
        double now = ShigFx.clock();
        GRABS.values().removeIf(g -> now - g.start > 2.6);
        STOLEN.values().removeIf(s -> now > s.until + 0.1);
        USES.removeIf(u -> now - u.start > 4.2);
        ClientLevel level = Minecraft.getInstance().level;
        ShaderInstance sh = ShigShaders.shell;
        if (level == null || sh == null) {
            return;
        }
        float T = (float) (now % 1000);
        float[] o = ShigDraw.origin(camera);
        sh.safeGetUniform("Origin").set(o[0], o[1], o[2]);
        sh.safeGetUniform("T").set(T);
        var me = Minecraft.getInstance().player;
        for (Grab g : GRABS.values()) {
            float t = (float) (now - g.start);
            Entity caster = level.getEntity(g.caster);
            // the vision: only through his own eyes (everyone's power shows for him)
            float vision = ShigDraw.smooth(t / 0.3f) * (1 - ShigDraw.smooth((t - 2.0f) / 0.4f));
            if (caster != null && me != null && caster == me && vision > 0.01f) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(caster.position(), caster.position()).inflate(24),
                        e -> e != caster && e.isAlive())) {
                    float life = 1 - vision;
                    if (e.getId() == g.victim && g.grabAt >= 0) {
                        life = Math.max(life, ShigDraw.smooth((float) (now - g.grabAt - 0.05) / 1.1f) * 0.95f);
                    }
                    aura(sh, e, g.victim == e.getId() ? g.element : elementOf(e), 0.05f + life, camera, partial, 0.32f, 0.75f, 1.35f);
                }
            } else if (g.grabAt >= 0) {
                // everyone else sees the victim's power being pulled out of it
                Entity v = level.getEntity(g.victim);
                if (v instanceof LivingEntity lv) {
                    float life = Math.max(0.3f, ShigDraw.smooth((float) (now - g.grabAt - 0.05) / 1.1f) * 0.95f);
                    aura(sh, lv, g.element, 0.05f + life, camera, partial, 0.32f, 0.75f, 1.35f);
                }
            }
            if (g.grabAt >= 0 && caster != null) {
                cord(g, caster, level.getEntity(g.victim), now, camera, partial);
            }
        }
        for (Map.Entry<Integer, Stolen> en : STOLEN.entrySet()) {
            Entity caster = level.getEntity(en.getKey());
            if (!(caster instanceof LivingEntity lc)) {
                continue;
            }
            if (lc == me && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                continue; // his own body isn't drawn in first person: a shell around nothing, on the screen
            }
            Stolen s = en.getValue();
            float gIn = ShigDraw.smooth((float) (now - s.start) / 0.4f), out = ShigDraw.smooth((float) (now - s.until + 1.2) / 1.2f);
            aura(sh, lc, s.element, 0.08f + (1 - gIn) + out, camera, partial, 0.35f, 0.8f, 1.4f);
        }
        for (Use u : USES) {
            use(u, level, now, camera, sh);
        }
    }

    // A shell of power around a body (sphere 40 x 28, the preview's scale for a 1.9-block body, fitted to the box).
    private static void aura(ShaderInstance sh, LivingEntity e, int el, float life, Vec3 camera, float partial, float amp, float sxz, float sy) {
        Vec3 p = e.getPosition(partial);
        float w = e.getBbWidth() / 0.6f, h = e.getBbHeight() / 1.8f;
        shell(sh, el, 0, amp, 1, life, 1, (float) (p.x - camera.x), (float) (p.y + e.getBbHeight() * 0.55 - camera.y), (float) (p.z - camera.z),
                sxz * w, sy * h, sxz * w, 40, 28);
    }

    private static void shell(ShaderInstance sh, int el, float mode, float amp, float rise, float life, float a, float cx, float cy, float cz, float sx,
            float sy, float sz, int ws, int hs) {
        // The camera inside the shell (his own aura in first person, a burst going off a few blocks away, the shoulder
        // camera close behind him): its inside faces would cover the whole screen, see-through, over his body and
        // everything. It fades out as the camera gets in (the vertices are camera-relative: the camera is 0, 0, 0).
        float pad = 1 + Math.abs(amp) * 1.6f;
        float qx = cx / (sx * pad), qy = cy / (sy * pad), qz = cz / (sz * pad);
        float in = ShigDraw.smooth(((float) Math.sqrt(qx * qx + qy * qy + qz * qz) - 1.05f) / 0.6f);
        if (in < 0.01f) {
            return;
        }
        a *= in;
        float[][] E = ELEM[Math.floorMod(el, 3)];
        sh.safeGetUniform("Amp").set(amp);
        sh.safeGetUniform("Rise").set(rise);
        sh.safeGetUniform("Life").set(life);
        sh.safeGetUniform("Mode").set(mode);
        sh.safeGetUniform("A").set(a);
        sh.safeGetUniform("C0").set(E[0][0], E[0][1], E[0][2]);
        sh.safeGetUniform("C1").set(E[1][0], E[1][1], E[1][2]);
        sh.safeGetUniform("C2").set(E[2][0], E[2][1], E[2][2]);
        BufferBuilder b = ShigDraw.begin();
        ShigDraw.sphere(b, cx, cy, cz, sx, sy, sz, ws, hs, (bb, x, y, z, nx, ny, nz, u, v) -> ShigDraw.v(bb, x, y, z, 1, 1, 1, 1, u, v, 0, 0, 0, 0, nx, ny, nz));
        ShigDraw.draw(b, sh, ShigDraw.ALPHA, false, false);
    }

    // The cord from the victim's chest to his hand, twisting, redone every frame (the preview's TubeGeometry 40 x 10).
    private static void cord(Grab g, Entity caster, Entity victim, double now, Vec3 camera, float partial) {
        ShaderInstance dr = ShigShaders.drain;
        float t = (float) (now - g.grabAt);
        float drain = ShigDraw.smooth((t - 0.03f) / 0.12f) * (1 - ShigDraw.smooth((t - 1.2f) / 0.2f));
        if (dr == null || victim == null || drain < 0.01f) {
            return;
        }
        float T = (float) (now % 1000);
        Vec3 cp = caster.getPosition(partial), vp = victim.getPosition(partial);
        Vec3 dir = new Vec3(vp.x - cp.x, 0, vp.z - cp.z);
        dir = dir.lengthSqr() < 1e-4 ? g.dir : dir.normalize();
        Vec3 a = new Vec3(vp.x, vp.y + victim.getBbHeight() * 0.6, vp.z), b = cp.add(dir.scale(0.75)).add(0, 1.3, 0);
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        float[][] pts = new float[17][];
        for (int i = 0; i <= 16; i++) {
            double u = i / 16.0;
            Vec3 p = a.add(b.subtract(a).scale(u));
            p = p.add(side.scale(Math.sin(u * Math.PI * 3 + T * 9) * 0.18 * Math.sin(u * Math.PI)))
                    .add(0, Math.sin(u * Math.PI) * 0.55 + Math.cos(u * 11 + T * 7) * 0.06, 0);
            pts[i] = new float[] {(float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)};
        }
        float[][] E = ELEM[Math.floorMod(g.element, 3)];
        dr.safeGetUniform("T").set(T);
        dr.safeGetUniform("A").set(drain);
        dr.safeGetUniform("C0").set(E[0][0], E[0][1], E[0][2]);
        dr.safeGetUniform("C1").set(E[1][0], E[1][1], E[1][2]);
        dr.safeGetUniform("C2").set(E[2][0], E[2][1], E[2][2]);
        float rad = 0.13f + 0.05f * (float) Math.sin(T * 20);
        BufferBuilder bb = ShigDraw.begin();
        ShigDraw.tube(bb, pts, 40, 10, u -> rad, (q, x, y, z, nx, ny, nz, u, v) -> ShigDraw.v(q, x, y, z, 1, 1, 1, 1, u, v, 0, 0, 0, 0, nx, ny, nz));
        ShigDraw.draw(bb, dr, ShigDraw.ALPHA, false, false);
    }

    // ---------- the stolen power used ----------

    private static void spikes(Use u) {
        HandGen.Rng r = new HandGen.Rng(u.seed);
        Vec3 d = new Vec3(u.to.x - u.from.x, 0, u.to.z - u.from.z);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        Vec3 side = new Vec3(d.z, 0, -d.x);
        double L = u.from.distanceTo(u.to);
        Vec3 feet = u.from.subtract(d.scale(0.8)).subtract(0, 1.35, 0);
        int n = Math.max(6, (int) Math.round(L * 1.3));
        u.spikes = new float[n][];
        u.jit = new float[n][];
        for (int i = 0; i < n; i++) {
            float k = (i + 0.5f) / n;
            int seg = 4 + (int) Math.floor(r.next() * 4);
            float h = 0.9f + r.next() * 1.6f + k * 1.2f, rad = 0.18f + r.next() * 0.22f + k * 0.15f;
            float[] jit = new float[seg * 2];
            for (int j = 0; j < jit.length; j++) {
                jit[j] = 0.8f + r.next() * 0.4f;
            }
            Vec3 p = feet.add(d.scale(1.2 + k * (L - 1.2))).add(side.scale((r.next() - 0.5f) * 1.2f));
            float rx = (r.next() - 0.5f) * 0.5f + 0.35f, ry = (float) Math.atan2(d.x, d.z) + (r.next() - 0.5f) * 0.8f, rz = (r.next() - 0.5f) * 0.5f;
            u.spikes[i] = new float[] {(float) p.x, (float) p.y - 0.05f, (float) p.z, h, rad, seg, rx, ry, rz, k * 0.45f};
            u.jit[i] = jit;
        }
    }

    private static void use(Use u, ClientLevel level, double now, Vec3 camera, ShaderInstance sh) {
        float t = (float) (now - u.start);
        double L = u.from.distanceTo(u.to);
        Entity target = u.target >= 0 ? level.getEntity(u.target) : null;
        Vec3 to = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : u.to;
        if (u.element == Shigaraki.FIRE) {
            float fly = (float) (L / 26);
            float tt = ShigDraw.clamp((t - 0.12f) / fly, 0, 1);
            if (tt < 1) {
                Vec3 p = u.from.add(to.subtract(u.from).scale(tt)).subtract(camera);
                float s = 0.55f * ShigDraw.smooth((t - 0.05f) / 0.1f);
                shell(sh, Shigaraki.FIRE, 1, 0.25f, 0.3f, 0.32f, 1, (float) p.x, (float) p.y, (float) p.z, s, s, s, 48, 32);
            } else {
                float e = t - 0.12f - fly;
                if (e < 1.2f) {
                    Vec3 p = to.subtract(camera);
                    float s = 0.5f + 3.6f * ShigDraw.easeOut3(e / 0.55f);
                    shell(sh, Shigaraki.FIRE, 1, 0.6f, 1.2f, 0.3f + ShigDraw.smooth((e - 0.15f) / 1.0f) * 0.8f, 1, (float) p.x, (float) p.y, (float) p.z, s, s, s,
                            64, 40);
                }
            }
        } else if (u.element == Shigaraki.LIGHTNING) {
            if (t > 0.1f && t < 0.65f) {
                bolt(u, to, now, camera);
            }
            float e = t - 0.12f;
            if (e > 0 && e < 0.6f) {
                Vec3 p = to.subtract(camera);
                float s = 0.4f + 1.8f * ShigDraw.easeOut3(e / 0.25f);
                shell(sh, Shigaraki.LIGHTNING, 1, 0.35f, 0.2f, 0.1f + ShigDraw.smooth(e / 0.6f), 1, (float) p.x, (float) p.y, (float) p.z, s, s, s, 48, 32);
            }
        } else {
            ice(u, target, t, camera, sh);
        }
    }

    // The bolt: one zigzag tube, a new shape 12 times a second; white core, yellow halo.
    private static void bolt(Use u, Vec3 to, double now, Vec3 camera) {
        ShaderInstance sh = ShigShaders.shell;
        long step = (long) Math.floor(now * 12);
        HandGen.Rng r = new HandGen.Rng(step * 7919 + 13);
        Vec3 dir = to.subtract(u.from), side = new Vec3(dir.z, 0, -dir.x).normalize();
        float[][] pts = new float[15][];
        for (int i = 0; i <= 14; i++) {
            double k = i / 14.0, s = Math.sin(k * Math.PI);
            Vec3 p = u.from.add(dir.scale(k)).add(side.scale((r.next() - 0.5f) * 1.3f * s)).add(0, (r.next() - 0.5f) * 0.9f * s, 0).subtract(camera);
            pts[i] = new float[] {(float) p.x, (float) p.y, (float) p.z};
        }
        float[][] cols = {{1, 1, 1, 1}, {1, 0.824f, 0.227f, 0.55f}};
        float[] rads = {0.05f, 0.17f};
        sh.safeGetUniform("Amp").set(0f);
        sh.safeGetUniform("Mode").set(2f);
        sh.safeGetUniform("A").set(1f);
        for (int k = 0; k < 2; k++) {
            float[] c = cols[k];
            // emissive only: Mode 2 with a black base and the colour as C0
            sh.safeGetUniform("C0").set(c[0], c[1], c[2]);
            BufferBuilder b = ShigDraw.begin();
            float rr = rads[k];
            ShigDraw.tube(b, pts, 60, 6, q -> rr, (q, x, y, z, nx, ny, nz, uu, vv) -> ShigDraw.v(q, x, y, z, 0, 0, 0, c[3], uu, vv, 0, 0, 0, 0, nx, ny, nz));
            ShigDraw.draw(b, sh, k == 0 ? ShigDraw.OPAQUE : ShigDraw.ALPHA, false, false);
        }
    }

    // The ice: spikes born one after the other to the target (0.16 s each, overshooting), breaking at 3.3 s, and a
    // jagged block of ice over the target from 0.6 s to 3.4 s.
    private static void ice(Use u, Entity target, float t, Vec3 camera, ShaderInstance sh) {
        sh.safeGetUniform("Amp").set(0f);
        sh.safeGetUniform("Mode").set(2f);
        sh.safeGetUniform("A").set(0.92f);
        sh.safeGetUniform("C0").set(0x1d / 255f, 0x4a / 255f, 0x6b / 255f);
        BufferBuilder b = ShigDraw.begin();
        int drawn = 0;
        for (int i = 0; i < u.spikes.length; i++) {
            float[] s = u.spikes[i];
            float g = ShigDraw.clamp((t - 0.1f - s[9]) / 0.16f, 0, 1);
            float up = g < 1 ? 1 + 2.4f * (float) Math.pow(g - 1, 3) + 1.4f * (float) Math.pow(g - 1, 2) : 1;
            float brk = ShigDraw.smooth((t - 3.3f - s[9] * 0.5f) / 0.5f);
            float sc = Math.max(0.001f, up * (1 - brk));
            if (sc <= 0.002f) {
                continue;
            }
            cone(b, s, u.jit[i], sc, camera);
            drawn++;
        }
        ShigDraw.draw(b, sh, ShigDraw.ALPHA, false, false);
        if (target != null && t > 0.6f) {
            float e = t - 0.6f, g = ShigDraw.clamp(e / 0.18f, 0, 1), brk = ShigDraw.smooth((e - 2.8f) / 0.35f);
            float k = Math.max(0.001f, g * (1 - brk));
            if (k > 0.002f) {
                sh.safeGetUniform("A").set(0.72f);
                sh.safeGetUniform("C0").set(0x24 / 255f, 0x57 / 255f, 0x7a / 255f);
                Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(camera);
                float w = target.getBbWidth() / 0.6f, h = target.getBbHeight() / 1.8f;
                BufferBuilder bi = ShigDraw.begin();
                icosa(bi, (float) c.x, (float) c.y, (float) c.z, 0.95f * k * w, 1.35f * k * h, 0.95f * k * w, u.seed);
                ShigDraw.draw(bi, sh, ShigDraw.ALPHA, false, false);
            }
        }
    }

    // A cone (ConeGeometry(rad, h, seg, 2) with its ring points pulled in and out), standing on its base, flat shaded.
    private static void cone(BufferBuilder b, float[] s, float[] jit, float sc, Vec3 camera) {
        int seg = (int) s[5];
        float h = s[3], rad = s[4];
        float[][] ring0 = new float[seg][], ring1 = new float[seg][];
        for (int j = 0; j < seg; j++) {
            double an = j / (double) seg * Math.PI * 2;
            ring0[j] = new float[] {(float) Math.sin(an) * rad * jit[j * 2], 0, (float) Math.cos(an) * rad * jit[j * 2 + 1]};
            ring1[j] = new float[] {ring0[j][0] * 0.5f, h * 0.5f, ring0[j][2] * 0.5f};
        }
        float[] tip = {0, h, 0};
        org.joml.Matrix3f m = new org.joml.Matrix3f().rotationXYZ(s[6], s[7], s[8]).scale(sc);
        float bx = (float) (s[0] - camera.x), by = (float) (s[1] - camera.y), bz = (float) (s[2] - camera.z);
        float[] col = {0xa8 / 255f, 0xe4 / 255f, 1f};
        for (int j = 0; j < seg; j++) {
            int j1 = (j + 1) % seg;
            tri(b, m, bx, by, bz, ring0[j], ring0[j1], ring1[j1], col);
            tri(b, m, bx, by, bz, ring0[j], ring1[j1], ring1[j], col);
            tri(b, m, bx, by, bz, ring1[j], ring1[j1], tip, col);
        }
    }

    private static void tri(BufferBuilder b, org.joml.Matrix3f m, float bx, float by, float bz, float[] p0, float[] p1, float[] p2, float[] col) {
        org.joml.Vector3f a = m.transform(new org.joml.Vector3f(p0[0], p0[1], p0[2])), c = m.transform(new org.joml.Vector3f(p1[0], p1[1], p1[2])),
                d = m.transform(new org.joml.Vector3f(p2[0], p2[1], p2[2]));
        org.joml.Vector3f n = new org.joml.Vector3f(c).sub(a).cross(new org.joml.Vector3f(d).sub(a)).normalize();
        for (org.joml.Vector3f p : new org.joml.Vector3f[] {a, c, d}) {
            ShigDraw.v(b, bx + p.x, by + p.y, bz + p.z, col[0], col[1], col[2], 1, 0, 0, 0, 0, 0, 0, n.x, n.y, n.z);
        }
    }

    // IcosahedronGeometry(1, 1) with each corner pulled in or out a little, flat shaded.
    private static void icosa(BufferBuilder b, float cx, float cy, float cz, float sx, float sy, float sz, long seed) {
        float p = (1 + (float) Math.sqrt(5)) / 2;
        float[][] v = {{-1, p, 0}, {1, p, 0}, {-1, -p, 0}, {1, -p, 0}, {0, -1, p}, {0, 1, p}, {0, -1, -p}, {0, 1, -p}, {p, 0, -1}, {p, 0, 1}, {-p, 0, -1},
                {-p, 0, 1}};
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8}, {3, 9, 4}, {3, 4, 2},
                {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        HandGen.Rng r = new HandGen.Rng(seed ^ 0x5bd1e995L);
        Map<Long, Float> jit = new HashMap<>();
        float[] col = {0xc8 / 255f, 0xf0 / 255f, 1f};
        for (int[] t : f) {
            float[][] tri = {v[t[0]], v[t[1]], v[t[2]]};
            // detail 1: each face split in four, the new corners on the sphere
            float[][] m = {mid(tri[0], tri[1]), mid(tri[1], tri[2]), mid(tri[2], tri[0])};
            float[][][] sub = {{tri[0], m[0], m[2]}, {m[0], tri[1], m[1]}, {m[2], m[1], tri[2]}, {m[0], m[1], m[2]}};
            for (float[][] s : sub) {
                float[][] q = new float[3][];
                for (int k = 0; k < 3; k++) {
                    float[] n = s[k].clone();
                    ShigDraw.norm(n);
                    long key = Math.round(n[0] * 1000) * 1000003L + Math.round(n[1] * 1000) * 1009L + Math.round(n[2] * 1000);
                    float j = jit.computeIfAbsent(key, kk -> 0.85f + r.next() * 0.3f);
                    q[k] = new float[] {cx + n[0] * j * sx, cy + n[1] * j * sy, cz + n[2] * j * sz};
                }
                float[] e1 = {q[1][0] - q[0][0], q[1][1] - q[0][1], q[1][2] - q[0][2]}, e2 = {q[2][0] - q[0][0], q[2][1] - q[0][1], q[2][2] - q[0][2]};
                float[] n = ShigDraw.cross(e1, e2);
                ShigDraw.norm(n);
                for (float[] pp : q) {
                    ShigDraw.v(b, pp[0], pp[1], pp[2], col[0], col[1], col[2], 1, 0, 0, 0, 0, 0, 0, n[0], n[1], n[2]);
                }
            }
        }
    }

    private static float[] mid(float[] a, float[] b) {
        float[] m = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
        ShigDraw.norm(m);
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new float[] {m[0] * l, m[1] * l, m[2] * l};
    }
}
