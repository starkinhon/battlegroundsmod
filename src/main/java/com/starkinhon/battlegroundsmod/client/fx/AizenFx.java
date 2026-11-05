package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenNet;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Aizen's effects besides the Black Box (power/powers/aizen), the same "black glowing violet" look, drawn with shader
// shapes only, as in the Visualizador's Aizen tab:
// - Reiatsu: black flames with violet edges rising off him, a dark column, a faint dome of pressure, the ring on the
//   ground and lines of pressure falling; it bursts outwards when let go.
// - Evolucao do Hogyoku: the orb lights up in his chest with veins of light, white threads wind up into a cocoon that
//   cracks with violet light and bursts into shards; then six black butterfly wings (shape 15) and a dark aura.
// - Cuts in space (the dash, Um Dedo's sword): a black slit with violet light around it.
// - Kyoka Suigetsu's clone breaking: cracks over it, then it falls apart like a mirror in shards (shape 14).
// Glow goes in render (into the bloom); the dark parts in overlay, drawn after the glow so the light doesn't wash them.
public final class AizenFx {
    private static final float[] DARK = {0.02f, 0.012f, 0.035f};
    private static final float[] DEEP = FxDraw.rgb(0x3A0F8F);
    private static final float[] VIOLET = FxDraw.rgb(0x7A2CFF);
    private static final float[] LILAC = FxDraw.rgb(0xC79BFF);
    private static final float[] PALE = FxDraw.rgb(0xF1E6FF);
    private static final float[] SILK = {0.95f, 0.93f, 1f};
    private static final int SHARD = 14;
    private static final int WING = 15;
    private static final float TWO_PI = Mth.TWO_PI;

    // ---------- state ----------

    private static final class Pressure {
        Vec3 feet;
        float radius;
        float power;
        int mode;
        final double born;
        double updated;
        double burstAt = -1;

        Pressure(double now) {
            this.born = now;
        }
    }

    private static final class Evolution {
        double start;
        double total;
        double endAt = -1;
        Vec3 feet;
    }

    private record Cut(Vec3 center, Vector3f dir, double born, float len, float big) {
    }

    private record Mirror(Vec3 feet, double crackAt, float crackTicks, double[] breakAt, Vector3f[] frame) {
    }

    private static final Map<Integer, Pressure> PRESSURE = new HashMap<>();
    private static final Map<Integer, Evolution> EVOLVED = new HashMap<>();
    private static final List<Cut> CUTS = new ArrayList<>();
    private static final Map<Integer, Mirror> MIRRORS = new HashMap<>();

    private AizenFx() {
    }

    public static void receive(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        double now = ScreenFx.now();
        switch (kind) {
            case AizenNet.REIATSU_HELD, AizenNet.REIATSU_CLICK -> {
                Pressure p = PRESSURE.get(entityId);
                if (p == null || p.burstAt >= 0) {
                    p = new Pressure(now);
                    PRESSURE.put(entityId, p);
                }
                p.feet = pos;
                p.radius = a;
                p.power = b;
                p.mode = kind;
                p.updated = now;
                if (kind == AizenNet.REIATSU_CLICK) {
                    p.burstAt = now;
                }
            }
            case AizenNet.REIATSU_BURST -> {
                Pressure p = PRESSURE.get(entityId);
                if (p != null && p.burstAt < 0) {
                    p.burstAt = now;
                    if (a > 0) {
                        p.radius = a;
                        p.power = 1;
                    } else {
                        // Cut short: it just fades.
                        p.mode = -1;
                    }
                }
            }
            case AizenNet.HOGYOKU -> {
                Evolution e = EVOLVED.computeIfAbsent(entityId, k -> new Evolution());
                e.start = now - a;
                e.total = b;
                e.endAt = -1;
                e.feet = pos;
            }
            case AizenNet.HOGYOKU_END -> {
                Evolution e = EVOLVED.get(entityId);
                if (e != null && e.endAt < 0) {
                    e.endAt = now;
                }
            }
            case AizenNet.CUT -> CUTS.add(new Cut(pos, dir.toVector3f().normalize(), now, a, b));
            case AizenNet.MIRROR_CRACK -> MIRRORS.put(entityId, new Mirror(pos, now, Math.max(1, a), new double[] {-1}, new Vector3f[2]));
            case AizenNet.MIRROR_BREAK -> {
                Mirror m = MIRRORS.computeIfAbsent(entityId, k -> new Mirror(pos, now - 1, 1, new double[] {-1}, new Vector3f[2]));
                m.breakAt()[0] = now;
            }
            default -> {
            }
        }
    }

    public static boolean active() {
        return !PRESSURE.isEmpty() || !EVOLVED.isEmpty() || !CUTS.isEmpty() || !MIRRORS.isEmpty();
    }

    public static void clear() {
        PRESSURE.clear();
        EVOLVED.clear();
        CUTS.clear();
        MIRRORS.clear();
    }

    // ---------- the two passes ----------

    static void render(Vec3 camera, double now, float partial) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        prune(now);
        draw(camera, now, partial, true);
    }

    static void overlay(Vec3 camera, double now, float partial) {
        draw(camera, now, partial, false);
    }

    private static void prune(double now) {
        PRESSURE.values().removeIf(p -> p.burstAt >= 0 ? now - p.burstAt > 40 : now - p.updated > 8);
        EVOLVED.entrySet().removeIf(en -> {
            Evolution e = en.getValue();
            return e.endAt >= 0 ? now - e.endAt > 12 : now - e.start > e.total + 40;
        });
        CUTS.removeIf(c -> now - c.born() > 14);
        MIRRORS.values().removeIf(m -> m.breakAt()[0] >= 0 ? now - m.breakAt()[0] > 32 : now - m.crackAt() > 60);
    }

    private static void draw(Vec3 camera, double now, float partial, boolean glow) {
        for (Map.Entry<Integer, Pressure> en : PRESSURE.entrySet()) {
            drawPressure(en.getKey(), en.getValue(), camera, now, partial, glow);
        }
        for (Map.Entry<Integer, Evolution> en : EVOLVED.entrySet()) {
            drawEvolution(en.getKey(), en.getValue(), camera, now, partial, glow);
        }
        for (Cut c : CUTS) {
            float age = (float) (now - c.born());
            if (c.big() > 1.2f) {
                age *= 0.7f;
            }
            spaceCut(rel(c.center(), camera), c.dir(), age, c.len(), c.big(), glow);
        }
        for (Map.Entry<Integer, Mirror> en : MIRRORS.entrySet()) {
            drawMirror(en.getValue(), camera, now, glow);
        }
    }

    // ---------- Reiatsu ----------

    private static void drawPressure(int id, Pressure p, Vec3 camera, double now, float partial, boolean glow) {
        Entity owner = FxAnchor.entity(id);
        Vec3 feetWorld = owner != null ? owner.getPosition(partial) : p.feet;
        Vector3f feet = rel(feetWorld, camera);
        float t = (float) now;
        float radius;
        float power;
        float vis;
        if (p.mode == AizenNet.REIATSU_CLICK) {
            float s = (float) (now - p.burstAt);
            radius = p.radius * easeOut(s / 4);
            power = 0.25f;
            vis = s < 2 ? smooth(s / 2) : 1 - smooth((s - 4) / 16);
        } else if (p.burstAt >= 0) {
            float s = (float) (now - p.burstAt);
            if (p.mode < 0) {
                radius = p.radius;
                power = p.power;
                vis = 1 - smooth(s / 6);
            } else {
                radius = p.radius * (1 + 0.15f * easeOut(s / 10));
                power = 1;
                vis = 1 - smooth(s / 30);
            }
        } else {
            radius = p.radius;
            power = p.power;
            vis = smooth((float) (now - p.born) / 6);
        }
        boolean self = FxAnchor.firstPersonSelf(id);
        drawReiatsu(feet, Math.max(0.3f, radius), power, t, 41 + id * 7, vis, glow, self);
    }

    private static void drawReiatsu(Vector3f feet, float radius, float power, float t, int seed, float vis, boolean glow, boolean self) {
        if (vis <= 0.01f) {
            return;
        }
        if (!self) {
            darkAura(feet, 2.4f + power * 2.5f, t, power, seed, vis, glow);
        }
        Vector3f top = new Vector3f(feet).add(0, 4 + 8 * power, 0);
        if (glow) {
            if (!self) {
                FxDraw.ribbon(feet, top, 1.6f + power * 2, FxDraw.BEAM, 0, DEEP, 0.35f * vis, true);
                FxDraw.ribbon(feet, top, 0.6f + power * 0.8f, FxDraw.BEAM, 0, VIOLET, 0.25f * vis, true);
            }
            // The dome of pressure: faint rings of latitude and the ring on the ground.
            for (int k = 1; k <= 5; k++) {
                float lat = k / 6f * Mth.HALF_PI;
                float r = radius * Mth.cos(lat);
                float y = radius * Mth.sin(lat) * 0.6f;
                FxDraw.disk(new Vector3f(feet).add(0, y, 0), new Vector3f(0, 1, 0), r * 2, FxDraw.RING,
                        Mth.clamp(0.06f / Math.max(0.5f, r), 0.01f, 0.2f), VIOLET, 0.08f * vis * (1 - k / 7f), true);
            }
            FxDraw.disk(new Vector3f(feet).add(0, 0.06f, 0), new Vector3f(0, 1, 0), radius * 2, FxDraw.RING,
                    Mth.clamp(0.5f / (radius * 2), 0.02f, 0.4f), VIOLET, 0.4f * vis, true);
            // Lines of pressure falling: the air turning heavy.
            int lines = (int) (12 + 40 * power);
            for (int k = 0; k < lines; k++) {
                float period = 6 + 4 * FxWorld.hash(seed, k, 11);
                float ph = (t + FxWorld.hash(seed, k, 12) * period) / period;
                float life = ph % 1;
                int cycle = (int) Math.floor(ph);
                float a = FxWorld.hash(seed + cycle, k, 13) * TWO_PI;
                float r = radius * Mth.sqrt(FxWorld.hash(seed + cycle, k, 14));
                float y = Mth.lerp(life, radius * 0.7f + 1, 0);
                Vector3f at = new Vector3f(feet).add(Mth.cos(a) * r, y, Mth.sin(a) * r);
                FxDraw.ribbon(at, new Vector3f(at).add(0, 1.2f + power, 0), 0.05f + 0.03f * power, FxDraw.STREAK, 0.6f, LILAC,
                        0.35f * vis * Mth.sin(life * Mth.PI), true);
            }
        } else {
            if (!self) {
                FxDraw.ribbon(feet, top, 0.25f + power * 0.3f, FxDraw.BEAM, 0, DARK, 0.7f * vis, false);
            }
            FxDraw.disk(new Vector3f(feet).add(0, 0.04f, 0), new Vector3f(0, 1, 0), radius * 2, FxDraw.DISC, 0.5f, DARK, 0.3f * vis, false);
        }
    }

    // Black flames with violet edges rising off the body.
    private static void darkAura(Vector3f feet, float height, float t, float power, int seed, float vis, boolean glow) {
        int count = 10 + (int) (power * 14);
        for (int k = 0; k < count; k++) {
            float period = 12 + 8 * FxWorld.hash(seed, k, 1);
            float ph = (t + FxWorld.hash(seed, k, 2) * period) / period;
            float life = ph % 1;
            int cycle = (int) Math.floor(ph);
            float a = FxWorld.hash(seed + cycle, k, 3) * TWO_PI;
            float r = (0.3f + 0.35f * FxWorld.hash(seed + cycle, k, 4)) * (1 + power * 0.4f);
            Vector3f at = new Vector3f(feet).add(Mth.cos(a) * r, 0.1f + life * height, Mth.sin(a) * r);
            float s = (0.6f + 0.5f * FxWorld.hash(seed, k, 5)) * (1 - 0.5f * life) * (0.8f + power * 0.7f);
            if (glow) {
                FxDraw.billboard(at, s * 1.9f, 0, FxDraw.FIRE, Math.min(0.95f, 0.35f + life * 0.6f), VIOLET, 0.4f * vis, true);
            } else {
                FxDraw.billboard(at, s * 0.85f, 0, FxDraw.FIRE, Math.min(0.95f, 0.15f + life * 0.8f), DARK, 0.75f * vis * (1 - life * 0.5f), false);
            }
        }
    }

    // ---------- Evolucao do Hogyoku ----------

    // The body's axes this frame: right, up, forward (from where the body faces).
    private record Body(Vector3f feet, Vector3f x, Vector3f y, Vector3f z) {
        Vector3f at(float right, float up, float forward) {
            return new Vector3f(feet).add(new Vector3f(x).mul(right)).add(new Vector3f(y).mul(up)).add(new Vector3f(z).mul(forward));
        }
    }

    private static Body body(int id, Evolution e, Vec3 camera, float partial) {
        Entity entity = FxAnchor.entity(id);
        Vec3 feet = entity != null ? entity.getPosition(partial) : e.feet;
        float yaw = entity instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot)
                : entity != null ? entity.getViewYRot(partial) : 0;
        float rad = yaw * Mth.DEG_TO_RAD;
        Vector3f forward = new Vector3f(-Mth.sin(rad), 0, Mth.cos(rad));
        Vector3f right = new Vector3f(-Mth.cos(rad), 0, -Mth.sin(rad));
        return new Body(rel(feet, camera), right, new Vector3f(0, 1, 0), forward);
    }

    private static void drawEvolution(int id, Evolution e, Vec3 camera, double now, float partial, boolean glow) {
        AizenSpecs.Hogyoku spec = AizenSpecs.Hogyoku.get(false);
        float hatch = Math.max(20, spec.eclosaoTicks());
        // The Visualizador's timeline is laid out for a 140 tick cocoon; it stretches to the file's.
        float k = 140f / hatch;
        float T = (float) (now - e.start) * k;
        float end = e.endAt >= 0 ? 1 - smooth((float) (now - e.endAt) / 10) : 1;
        Body b = body(id, e, camera, partial);
        boolean self = FxAnchor.firstPersonSelf(id);
        if (T < 140) {
            float awake = smooth((T - 4) / 20);
            orb(b, awake * end, (float) now, glow);
            if (glow) {
                veins(b, smooth((T - 8) / 20) * end, (float) now);
            }
            cocoon(b, T, (float) now, glow, self, end);
            return;
        }
        if (T < 172) {
            eggShards(b, T - 140, glow);
        }
        float unfold = easeOut((T - 142) / 22) * end;
        if (unfold <= 0.01f) {
            return;
        }
        if (!self) {
            wings(b, unfold, (float) now, glow);
            darkAura(b.at(0, -0.1f, 0), 2.6f, (float) now, 0.5f * unfold, 57 + id, unfold, glow);
        }
        orb(b, unfold, (float) now, glow);
        if (glow && !self) {
            FxDraw.billboard(b.at(0, 1.0f, 0), 3.2f, 0, FxDraw.SOFT, 0, DEEP, 0.25f * unfold, true);
        }
    }

    private static Vector3f chest(Body b) {
        return b.at(0, 1.27f, 0.16f);
    }

    private static void orb(Body b, float a, float t, boolean glow) {
        if (a <= 0.01f) {
            return;
        }
        Vector3f at = chest(b);
        if (glow) {
            FxDraw.billboard(at, 0.9f * a, 0, FxDraw.SOFT, 0.2f, VIOLET, 0.6f * a, true);
            FxDraw.billboard(at, 0.7f * a, t * 0.03f, FxDraw.FLARE, 6 / 32f, LILAC, 0.7f * a, true);
        } else {
            FxDraw.billboard(at, 0.16f, 0, FxDraw.DISC, 0.3f, DARK, a, false);
            FxDraw.billboard(at, 0.08f, 0, FxDraw.SOFT, 0.9f, PALE, a, true);
        }
    }

    // Veins of light running from the Hogyoku over the body.
    private static void veins(Body b, float k, float t) {
        if (k <= 0.01f) {
            return;
        }
        Vector3f center = new Vector3f(chest(b)).add(new Vector3f(b.z()).mul(0.01f));
        for (int i = 0; i < 9; i++) {
            float a = FxWorld.hash(3, i, 1) * TWO_PI;
            Vector3f last = center;
            Vector3f dir = new Vector3f(b.x()).mul(Mth.cos(a)).add(new Vector3f(b.y()).mul(Mth.sin(a)));
            int steps = (int) (6 * k);
            for (int s = 1; s <= steps; s++) {
                dir.rotateAxis((FxWorld.hash(3 + (int) Math.floor(t / 3), i * 8 + s, 2) - 0.5f) * 0.9f, b.z().x, b.z().y, b.z().z);
                Vector3f next = new Vector3f(last).add(new Vector3f(dir).mul(0.08f + 0.02f * s));
                FxDraw.ribbon(last, next, 0.03f, FxDraw.BEAM, 0.7f, PALE, 0.8f * (1 - s / 8f), true);
                FxDraw.ribbon(last, next, 0.12f, FxDraw.BEAM, 0.1f, VIOLET, 0.5f, true);
                last = next;
            }
        }
    }

    // The cocoon: threads winding up around him, closing into a white egg that pulses and cracks with violet light.
    private static final float CY = 1.0f;
    private static final float RX = 0.66f;
    private static final float RY = 1.25f;

    private static void cocoon(Body b, float T, float now, boolean glow, boolean self, float end) {
        float reveal = smooth((T - 40) / 50);
        float crack = smooth((T - 96) / 40);
        float pulse = T > 90 ? 0.5f + 0.5f * Mth.sin((T - 90) * (0.25f + (T - 90) * 0.006f)) : 0;
        float swell = 1 + (T > 90 ? 0.03f * pulse : 0);
        Vector3f center = new Vector3f(b.feet()).add(0, CY, 0);
        if (glow && T >= 34 && T < 96) {
            float top = CY - RY + 2 * RY * smooth((T - 34) / 40);
            for (int k = 0; k < 7; k++) {
                Vector3f last = null;
                for (int j = 0; j <= 30; j++) {
                    float y = CY - RY + (top - CY + RY) * j / 30f;
                    float u = (y - CY) / RY;
                    float r = RX * Mth.sqrt(Math.max(0, 1 - u * u)) + 0.04f;
                    float a = k * TWO_PI / 7 + y * 2.6f + T * 0.09f;
                    Vector3f p = new Vector3f(b.feet()).add(Mth.cos(a) * r, y, Mth.sin(a) * r);
                    if (last != null) {
                        FxDraw.ribbon(last, p, 0.07f, FxDraw.BEAM, 0.6f, SILK, 0.7f * (1 - reveal) * end, true);
                    }
                    last = p;
                }
            }
        }
        if (T < 40) {
            return;
        }
        int rows = 18;
        int cols = 28;
        if (!glow && !self) {
            // The shell, lit from above, built up from the bottom along the threads.
            Vector3f light = new Vector3f(0.3f, 1, 0.4f).normalize();
            for (int i = 0; i < rows; i++) {
                float v0 = i / (float) rows;
                float v1 = (i + 1) / (float) rows;
                float h = (v0 + v1) / 2;
                if (h > reveal * 1.5f) {
                    continue;
                }
                for (int j = 0; j < cols; j++) {
                    float u0 = j / (float) cols;
                    float u1 = (j + 1) / (float) cols;
                    float ang = (u0 + u1) * Mth.PI;
                    float yMid = Mth.lerp(h, -1, 1);
                    float strand = (ang / TWO_PI * 9 + yMid * 1.4f) % 1;
                    if (strand < 0) {
                        strand += 1;
                    }
                    if (strand > reveal * 1.1f + 0.05f) {
                        continue;
                    }
                    Vector3f n = shell(ang, h);
                    float lit = 0.62f + 0.38f * Math.max(0, n.dot(light));
                    float silk = 0.88f + 0.12f * Math.abs(Mth.sin(ang * 18 + yMid * 9));
                    float[] c = {SILK[0] * lit * silk, SILK[1] * lit * silk, SILK[2] * lit * silk};
                    c = FxDraw.mix(c, new float[] {0.7f, 0.5f, 1f}, 0.25f * pulse * crack);
                    FxDraw.quad(point(center, u0, v0, swell), point(center, u1, v0, swell), point(center, u1, v1, swell), point(center, u0, v1, swell),
                            FxDraw.SOLID, 0, c, end, false);
                }
            }
        }
        if (glow && crack > 0.01f) {
            // Cracks of violet light over it, more and more, and the glow through them.
            for (int k = 0; k < 14; k++) {
                if (FxWorld.hash(17, k, 30) > crack * 1.15f) {
                    continue;
                }
                float ang = FxWorld.hash(17, k, 31) * TWO_PI;
                float h = 0.15f + 0.7f * FxWorld.hash(17, k, 32);
                Vector3f last = point(center, ang / TWO_PI, h, swell * 1.01f);
                for (int s = 1; s <= 6; s++) {
                    ang += (FxWorld.hash(17, k * 8 + s, 33) - 0.5f) * 0.7f;
                    h = Mth.clamp(h + (FxWorld.hash(17, k * 8 + s, 34) - 0.5f) * 0.14f, 0.05f, 0.95f);
                    Vector3f next = point(center, ang / TWO_PI, h, swell * 1.01f);
                    FxDraw.ribbon(last, next, 0.05f, FxDraw.BEAM, 0.7f, PALE, (0.6f + 0.4f * pulse) * end, true);
                    FxDraw.ribbon(last, next, 0.22f, FxDraw.BEAM, 0.1f, VIOLET, (0.5f + 0.4f * pulse) * end, true);
                    last = next;
                }
            }
            for (int i = 0; i < 10; i++) {
                Vector3f d = FxWorld.sphere(17, i);
                FxDraw.billboard(new Vector3f(center).add(d.x * RX, d.y * RY, d.z * RX), 0.6f + crack, 0, FxDraw.SOFT, 0.3f, VIOLET,
                        0.35f * crack * (0.6f + 0.4f * pulse) * end, true);
            }
            FxDraw.billboard(center, 3.5f, 0, FxDraw.SOFT, 0, DEEP, 0.3f * crack * end, true);
        }
    }

    private static Vector3f shell(float ang, float h) {
        float y = Mth.lerp(h, -1, 1);
        float r = Mth.sqrt(Math.max(0, 1 - y * y));
        return new Vector3f(Mth.cos(ang) * r / RX, y / RY, Mth.sin(ang) * r / RX).normalize();
    }

    private static Vector3f point(Vector3f center, float u, float v, float scale) {
        float ang = u * TWO_PI;
        float y = Mth.lerp(v, -1, 1);
        float r = Mth.sqrt(Math.max(0, 1 - y * y));
        return new Vector3f(center).add(Mth.cos(ang) * r * RX * scale, y * RY * scale, Mth.sin(ang) * r * RX * scale);
    }

    // The egg bursting into white shards.
    private static void eggShards(Body b, float t, boolean glow) {
        Vector3f center = new Vector3f(b.feet()).add(0, CY, 0);
        float fade = 1 - smooth((t - 18) / 14);
        if (fade <= 0) {
            return;
        }
        for (int k = 0; k < 80; k++) {
            Vector3f dir = FxWorld.sphere(91, k);
            Vector3f p0 = new Vector3f(center).add(dir.x * RX, dir.y * RY, dir.z * RX);
            Vector3f t1 = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f t2 = new Vector3f(dir).cross(t1);
            float sz = 0.2f + 0.18f * FxWorld.hash(91, k, 1);
            float tt = easeOut(t / 16) * 16;
            Vector3f to = new Vector3f(p0).add(new Vector3f(dir).mul((0.12f + 0.18f * FxWorld.hash(91, k, 2)) * tt)).add(0, -0.008f * t * t, 0);
            Vector3f axis = new Vector3f(FxWorld.hash(91, k, 3) - 0.5f, FxWorld.hash(91, k, 4) - 0.5f, FxWorld.hash(91, k, 5) - 0.5f).normalize();
            Quaternionf q = new Quaternionf().rotateAxis(t * 0.25f, axis);
            t1.rotate(q);
            t2.rotate(q);
            if (glow) {
                if (k % 3 == 0) {
                    FxDraw.billboard(to, 0.3f, 0, FxDraw.SOFT, 0.4f, LILAC, 0.4f * fade, true);
                }
                continue;
            }
            Vector3f a = new Vector3f(to).add(new Vector3f(t1).mul(-sz)).add(new Vector3f(t2).mul(-sz * 0.6f));
            Vector3f bb = new Vector3f(to).add(new Vector3f(t1).mul(sz * 0.8f)).add(new Vector3f(t2).mul(-sz * 0.7f));
            Vector3f c = new Vector3f(to).add(new Vector3f(t2).mul(sz));
            FxDraw.quad(a, bb, c, c, SHARD, 0.5f + 0.49f * FxWorld.hash(91, k, 6), SILK, 0.95f * fade, false);
        }
    }

    // Three pairs of black butterfly wings with violet rims, beating slowly.
    private static final float[] ELEV = {0.62f, 0.02f, -0.62f};
    private static final float[] LEN = {2.2f, 1.8f, 1.25f};
    private static final float[] WID = {1.7f, 1.25f, 0.95f};

    private static void wings(Body b, float unfold, float t, boolean glow) {
        Vector3f root = b.at(0, 1.35f, -0.16f);
        for (int p = 0; p < 3; p++) {
            for (int s = 1; s >= -1; s -= 2) {
                float flap = 0.5f + 0.2f * Mth.sin(t * 0.11f + p * 0.7f) - 0.4f * (1 - unfold);
                Vector3f out = new Vector3f(b.x()).mul(s * Mth.cos(ELEV[p])).add(new Vector3f(b.y()).mul(Mth.sin(ELEV[p])));
                out.mul(Mth.cos(flap)).add(new Vector3f(b.z()).mul(-Mth.sin(flap))).normalize();
                Vector3f perp = new Vector3f(b.y()).sub(new Vector3f(out).mul(b.y().dot(out))).normalize();
                float l = LEN[p] * unfold;
                float w = WID[p] * unfold;
                Vector3f base = new Vector3f(root).add(new Vector3f(b.x()).mul(s * 0.08f)).add(new Vector3f(b.y()).mul(-0.12f * p));
                Vector3f half = new Vector3f(perp).mul(w / 2);
                Vector3f tip = new Vector3f(base).add(new Vector3f(out).mul(l));
                Vector3f a = new Vector3f(base).sub(half);
                Vector3f bb = new Vector3f(tip).sub(half);
                Vector3f c = new Vector3f(tip).add(half);
                Vector3f d = new Vector3f(base).add(half);
                if (glow) {
                    FxDraw.quad(a, bb, c, d, WING, 0.75f, VIOLET, 0.8f * unfold, true);
                } else {
                    FxDraw.quad(a, bb, c, d, WING, 0.1f + 0.15f * p, DARK, unfold, false);
                }
            }
        }
    }

    // ---------- cuts ----------

    // A straight cut in space: violet light around it and a black slit down the middle.
    private static void spaceCut(Vector3f center, Vector3f dir, float age, float len, float big, boolean glow) {
        if (age < 0 || age > 8) {
            return;
        }
        float grow = easeOut(age / 1.5f);
        float fade = age < 2 ? 1 : 1 - (age - 2) / 6;
        Vector3f a = new Vector3f(center).add(new Vector3f(dir).mul(-len * grow));
        Vector3f b = new Vector3f(center).add(new Vector3f(dir).mul(len * grow));
        if (glow) {
            FxDraw.ribbon(a, b, 0.9f * big, FxDraw.BEAM, 0.1f, VIOLET, 0.55f * fade, true);
            FxDraw.ribbon(a, b, 0.3f * big, FxDraw.BEAM, 0.6f, LILAC, 0.8f * fade, true);
        } else {
            FxDraw.ribbon(a, b, 0.07f * big, FxDraw.BEAM, 0.5f, DARK, fade, false);
        }
    }

    // ---------- Kyoka Suigetsu's mirror ----------

    private static final int COLS = 4;
    private static final int ROWS = 9;
    private static final float[][][] GRID = new float[COLS + 1][ROWS + 1][];
    private static final List<float[][]> TRIS = new ArrayList<>();

    static {
        for (int i = 0; i <= COLS; i++) {
            for (int j = 0; j <= ROWS; j++) {
                boolean inner = i > 0 && i < COLS && j > 0 && j < ROWS;
                GRID[i][j] = new float[] {i / (float) COLS + (inner ? (FxWorld.hash(77, i * 31 + j, 1) - 0.5f) * 0.18f : 0),
                        j / (float) ROWS + (inner ? (FxWorld.hash(77, i * 31 + j, 2) - 0.5f) * 0.08f : 0)};
            }
        }
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                float[] a = GRID[i][j];
                float[] b = GRID[i + 1][j];
                float[] c = GRID[i + 1][j + 1];
                float[] d = GRID[i][j + 1];
                if (FxWorld.hash(77, i * 17 + j, 3) < 0.5f) {
                    TRIS.add(new float[][] {a, b, c});
                    TRIS.add(new float[][] {a, c, d});
                } else {
                    TRIS.add(new float[][] {a, b, d});
                    TRIS.add(new float[][] {b, c, d});
                }
            }
        }
    }

    // His look in broad strokes, for the shards: brown hair, skin, the white robe and its violet sash, dark sandals.
    private static float[] mirrorColor(float u, float v) {
        if (v > 0.93f) {
            return FxDraw.rgb(0x5A3A22);
        }
        if (v > 0.77f) {
            return Math.abs(u - 0.5f) < 0.28f ? FxDraw.rgb(0xE7C6A5) : new float[] {0.94f, 0.93f, 0.9f};
        }
        if (v > 0.36f && v < 0.43f) {
            return FxDraw.rgb(0x5B2A86);
        }
        return v < 0.05f ? FxDraw.rgb(0x1A1620) : new float[] {0.94f, 0.93f, 0.9f};
    }

    private static Vector3f onMirror(Vector3f feet, Vector3f right, Vector3f n, float[] uv) {
        return new Vector3f(feet).add(new Vector3f(right).mul((uv[0] - 0.5f) * 0.8f)).add(0, uv[1] * 1.95f, 0).add(new Vector3f(n).mul(0.2f));
    }

    private static void drawMirror(Mirror m, Vec3 camera, double now, boolean glow) {
        Vector3f feet = rel(m.feet(), camera);
        // The mirror faces whoever looks at it (worked out once, so the shards don't turn with the camera).
        if (m.frame()[0] == null) {
            Vector3f n = new Vector3f(feet).negate();
            n.y = 0;
            if (n.lengthSquared() < 1.0E-4f) {
                n.set(0, 0, 1);
            }
            n.normalize();
            m.frame()[0] = n;
            m.frame()[1] = new Vector3f(0, 1, 0).cross(n).normalize();
        }
        Vector3f n = m.frame()[0];
        Vector3f right = m.frame()[1];
        double breakAt = m.breakAt()[0];
        if (breakAt < 0 || now < breakAt) {
            if (!glow) {
                return;
            }
            float k = (float) ((now - m.crackAt()) / m.crackTicks()) + 0.15f;
            for (int i = 0; i < TRIS.size(); i++) {
                float[][] tri = TRIS.get(i);
                for (int e = 0; e < 3; e++) {
                    float[] p = tri[e];
                    float[] q = tri[(e + 1) % 3];
                    float mx = (p[0] + q[0]) / 2 - 0.5f;
                    float my = ((p[1] + q[1]) / 2 - 0.55f) * 0.45f;
                    if (Mth.sqrt(mx * mx + my * my) > k * 0.9f || FxWorld.hash(77, i * 3 + e, 9) > 0.7f) {
                        continue;
                    }
                    Vector3f a = onMirror(feet, right, n, p);
                    Vector3f b = onMirror(feet, right, n, q);
                    FxDraw.ribbon(a, b, 0.03f, FxDraw.BEAM, 0.8f, PALE, 0.9f, true);
                    FxDraw.ribbon(a, b, 0.12f, FxDraw.BEAM, 0.2f, VIOLET, 0.5f, true);
                }
            }
            return;
        }
        float t = (float) (now - breakAt);
        float fade = 1 - smooth((t - 16) / 14);
        if (fade <= 0) {
            return;
        }
        Vector3f middle = new Vector3f(feet).add(0, 1.0f, 0);
        for (int i = 0; i < TRIS.size(); i++) {
            float[][] tri = TRIS.get(i);
            Vector3f[] p = {onMirror(feet, right, n, tri[0]), onMirror(feet, right, n, tri[1]), onMirror(feet, right, n, tri[2])};
            Vector3f c = new Vector3f(p[0]).add(p[1]).add(p[2]).mul(1 / 3f);
            float u = (tri[0][0] + tri[1][0] + tri[2][0]) / 3;
            float v = (tri[0][1] + tri[1][1] + tri[2][1]) / 3;
            Vector3f out = new Vector3f(c).sub(middle).normalize().add(new Vector3f(n).mul(0.5f + 0.6f * FxWorld.hash(5, i, 1))).normalize();
            float speed = 0.08f + 0.12f * FxWorld.hash(5, i, 2);
            float tt = easeOut(t / 14) * 14;
            Vector3f to = new Vector3f(c).add(new Vector3f(out).mul(speed * tt)).add(0, -0.006f * t * t, 0);
            if (glow) {
                if (i % 2 == 0) {
                    FxDraw.billboard(to, 0.35f, 0, FxDraw.SOFT, 0.3f, LILAC, 0.3f * fade * (1 - t / 30), true);
                }
                continue;
            }
            Vector3f axis = new Vector3f(FxWorld.hash(5, i, 3) - 0.5f, FxWorld.hash(5, i, 4) - 0.5f, FxWorld.hash(5, i, 5) - 0.5f).normalize();
            Quaternionf spin = new Quaternionf().rotateAxis(t * (0.1f + 0.25f * FxWorld.hash(5, i, 6)), axis);
            Vector3f[] turned = new Vector3f[3];
            for (int j = 0; j < 3; j++) {
                turned[j] = new Vector3f(p[j]).sub(c).mul(0.94f).rotate(spin).add(to);
            }
            FxDraw.quad(turned[0], turned[1], turned[2], turned[2], SHARD, 0.5f + 0.49f * FxWorld.hash(5, i, 7), mirrorColor(u, v), 0.92f * fade, false);
        }
    }

    // ---------- helpers ----------

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        float i = 1 - x;
        return 1 - i * i * i;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
