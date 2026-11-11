package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.power.powers.makima.MkNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Makima's effects (power/powers/makima, MkNet), each one on the timeline of the preview Stark approved on 2026-11-11
// (forja/makima-*). Chains link by link (MkChain); her ring eyes and the rings in the eyes of whoever obeys her; the
// world going gray when she speaks and dark through her rats (ScreenFx grades 12 and 13), the red ghosts through walls;
// rats each with its own shape; pools of shadow and blood, cracks, pieces and red mist each piece and drop its own; the
// Bang's air ripple, shock shell and impact frame; the Ritual's column of pressure; bodies sinking and rising jerkily.
public final class MakimaFx {
    static final float[] CHAIN_RED = FxDraw.rgb(0xC0101E);
    static final float[] CHAIN_HOT = FxDraw.rgb(0xFF6A60);
    private static final float[] GOLD = FxDraw.rgb(0xFFB020);
    private static final float[] GOLD_W = FxDraw.rgb(0xFFF0A8);
    // Her rings over the rats in the vision (forja/makima-ratos): orange, brighter on the one picked.
    private static final float[] RAT_RING = {1f, 0.58f, 0.12f};
    private static final float[] RAT_SEL = {1f, 0.86f, 0.42f};
    private static final float[] RED = FxDraw.rgb(0xE0283C);
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] SHADOW = {0.02f, 0.016f, 0.024f};
    private static final float[] SHADOW_RIM = {0.16f, 0.03f, 0.05f};
    private static final float[] BLOOD = {0.36f, 0.02f, 0.04f};
    private static final float[] BLOOD_D = {0.2f, 0.01f, 0.02f};
    private static final float[] BLOOD_GLOSS = {0.62f, 0.16f, 0.16f};
    private static final float[] CRACK = {0.05f, 0.035f, 0.03f};
    private static final float[] CRACK_EDGE = {0.2f, 0.15f, 0.1f};
    private static final float[] AZUL = FxDraw.rgb(0x3A7BFF);
    private static final Vector3f SUN = new Vector3f(0.45f, 0.8f, 0.35f).normalize();

    private record Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double born) {
    }

    private static final class XChain {
        int caster;
        int target;
        Vec3 end;
        Vec3 lastTarget;
        double born;
        float hold;
        double released = -1;
    }

    private static final class VBind {
        int target;
        int caster;
        int order;
        Vec3 last;
        float height = 1.8f;
        float width = 0.6f;
        double born;
        double morram = -1;
        double back = -1;
    }

    private static final class Leash {
        int puppet;
        int owner;
        int kind;
        double updated;
        double risen;
        double pulse = -1;
        int pulseKind;
    }

    private static final class Rat {
        int caster;
        int index;
        Vec3 pos;
        int seed;
        double born;
        double until;
        double hop = -1;
        double run = -1;
        int rank;
        float yaw;
        Vec3 away = Vec3.ZERO;
        float scale = 1;
    }

    private record Sink(boolean rise, double born, float ticks) {
    }

    private static final List<Fx> SHOTS = new ArrayList<>();
    private static final Map<Integer, XChain> X = new HashMap<>();
    private static final Map<Integer, VBind> V = new HashMap<>();
    private static final Map<Integer, Leash> LEASH = new HashMap<>();
    private static final Map<Integer, double[]> EYES = new HashMap<>();
    private static final Map<Integer, double[]> MARKS = new HashMap<>();
    private static final Map<Long, Rat> RATS = new HashMap<>();
    private static final Map<Integer, Sink> SINKS = new HashMap<>();
    private static final Map<Integer, double[]> CRUSHED = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();
    // Seeing through her rats (only on her own screen): from when, until when, the rat picked for the jump.
    private static double visionStart = -1;
    private static double visionUntil = -1;
    private static int visionPick = -1;
    private static double visionPickAt = -1;
    private static int visionCaster = -1;
    private static MultiBufferSource.BufferSource ghostBuffers;

    private MakimaFx() {
    }

    // ---------- messages ----------

    public static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ScreenFx.now();
        switch (kind) {
            case MkNet.EYES -> {
                if (a <= 0) {
                    EYES.remove(id);
                } else {
                    double[] e = EYES.get(id);
                    double until = now + a;
                    EYES.put(id, new double[] {e != null && e[0] > now ? e[1] : now, Math.max(until, e != null && e[1] == b ? 0 : 0), b});
                    EYES.get(id)[1] = until;
                }
            }
            case MkNet.X_CHAIN -> {
                XChain x = X.get(id);
                if (c == 1) {
                    if (x != null && x.released < 0) {
                        x.released = now;
                    }
                    return;
                }
                x = new XChain();
                x.caster = id;
                x.target = (int) dir.x;
                x.end = pos;
                x.lastTarget = pos;
                x.born = now;
                x.hold = a;
                X.put(id, x);
            }
            case MkNet.V_CHAINS -> {
                VBind v = V.get(id);
                if (c == 0) {
                    v = new VBind();
                    v.target = id;
                    v.caster = (int) a;
                    v.order = (int) b;
                    v.last = pos;
                    v.born = now;
                    if (entity(id) instanceof LivingEntity le) {
                        v.height = le.getBbHeight();
                        v.width = le.getBbWidth();
                    }
                    V.put(id, v);
                } else if (v != null && c == 1) {
                    v.morram = now;
                } else if (v != null && c == 2 && v.back < 0) {
                    v.back = now;
                }
            }
            case MkNet.LEASH -> {
                Leash l = LEASH.get(id);
                if (l == null) {
                    l = new Leash();
                    l.puppet = id;
                    l.risen = now - c;
                    LEASH.put(id, l);
                }
                l.owner = (int) a;
                l.kind = (int) b;
                l.updated = now;
            }
            case MkNet.PULSE -> {
                Leash l = LEASH.get(id);
                if (l != null) {
                    l.pulse = now;
                    l.pulseKind = (int) b;
                }
            }
            case MkNet.MARK -> MARKS.put(id, new double[] {now, now + a, b});
            case MkNet.SINK -> SINKS.put(id, new Sink(a >= 1, now, Math.max(1, b)));
            case MkNet.CRUSH -> CRUSHED.put(id, new double[] {now, a});
            case MkNet.RAT -> rat(id, pos, a, b, (int) c, now);
            case MkNet.RAT_VISION -> {
                if (a <= 0) {
                    visionUntil = Math.min(visionUntil, now);
                    visionPick = -1;
                    return;
                }
                if (visionUntil < now) {
                    visionStart = now;
                }
                visionCaster = id;
                visionUntil = now + a;
                if (b >= 0) {
                    visionPick = (int) b;
                    visionPickAt = now;
                }
            }
            case MkNet.BANG -> {
                SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
                bang(pos, dir, a);
            }
            case MkNet.IMPACT -> impact(pos, (int) a);
            case MkNet.VOICE -> {
                SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
                ScreenFx.warp(pos.add(dir.scale(0.5)), (float) Math.max(2, dir.length() * 0.6), 0.35f, 1.4f, 0, dir.normalize(), 0, 0, 10, id, null, 0);
            }
            default -> SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, now));
        }
    }

    private static void rat(int caster, Vec3 pos, float a, float b, int index, double now) {
        long key = ((long) caster << 8) | (index & 0xFF);
        Rat r = RATS.get(key);
        if (b > 0) {
            r = new Rat();
            r.caster = caster;
            r.index = index;
            r.pos = pos;
            r.seed = (int) (a * 7.31f) + index * 131;
            r.born = now;
            r.until = now + b;
            r.yaw = hash(r.seed, 1, 70) * Mth.TWO_PI;
            Entity c = entity(caster);
            r.rank = c == null ? index : 0;
            RATS.put(key, r);
            if (c != null) {
                // The rings light from the nearest out.
                List<Rat> mine = new ArrayList<>();
                for (Rat o : RATS.values()) {
                    if (o.caster == caster && o.run < 0) {
                        mine.add(o);
                    }
                }
                Vec3 at = c.position();
                mine.sort((p, q) -> Double.compare(p.pos.distanceToSqr(at), q.pos.distanceToSqr(at)));
                for (int i = 0; i < mine.size(); i++) {
                    mine.get(i).rank = i;
                }
            }
            return;
        }
        if (r == null) {
            return;
        }
        if (b < 0) {
            r.hop = now;
        } else if (r.run < 0) {
            r.run = now;
            Entity c = entity(caster);
            Vec3 from = c == null ? r.pos.add(1, 0, 0) : c.position();
            Vec3 d = r.pos.subtract(from);
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() < 1e-4) {
                d = new Vec3(Math.cos(r.yaw), 0, Math.sin(r.yaw));
            }
            double turn = (hash(r.seed, 2, 71) - 0.5) * 1.2;
            d = d.normalize().yRot((float) turn);
            r.away = d;
        }
    }

    // ---------- the screen ----------

    private static void bang(Vec3 tip, Vec3 dir, float kind) {
        // The air ripples along the line, from the finger out.
        double len = dir.length();
        if (len < 0.1) {
            return;
        }
        Vec3 n = dir.normalize();
        int steps = (int) Math.min(8, Math.max(2, len / 4));
        for (int i = 0; i < steps; i++) {
            double u = (i + 0.5) / steps;
            ScreenFx.warp(tip.add(dir.scale(u)), 1.6f + (kind > 1 ? 0.8f : 0), 0.45f, 2.2f, 0, n, 0, 0, 6 + i, -1, null, 0);
        }
        HUD_BANGS.add(new double[] {ScreenFx.now(), tip.x, tip.y, tip.z, kind});
    }

    private static void impact(Vec3 pos, int kind) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.position().distanceTo(pos) > 48) {
            return;
        }
        int[] modes = switch (kind) {
            case 1 -> new int[] {3, 4, 3, 4};
            case 2 -> new int[] {4, 3, 4};
            case 3 -> new int[] {3, 4};
            default -> new int[] {3, 4};
        };
        ScreenFx.impactFrame(modes, 1, 0xC0101E, pos, 0);
        ScreenFx.radialBlur(pos, kind == 1 || kind == 2 ? 0.5f : 0.3f, 6);
    }

    // The BANG over her hand on everyone's screen (MakimaHud draws it; pos, born, kind).
    static final List<double[]> HUD_BANGS = new ArrayList<>();

    public static List<double[]> bangs() {
        HUD_BANGS.removeIf(b -> ScreenFx.now() - b[0] > 22);
        return HUD_BANGS;
    }

    public static boolean active() {
        double now = ScreenFx.now();
        return !SHOTS.isEmpty() || !X.isEmpty() || !V.isEmpty() || !LEASH.isEmpty() || !EYES.isEmpty() || !MARKS.isEmpty() || !RATS.isEmpty()
                || visionUntil + 10 > now;
    }

    public static void clear() {
        SHOTS.clear();
        X.clear();
        V.clear();
        LEASH.clear();
        EYES.clear();
        MARKS.clear();
        RATS.clear();
        SINKS.clear();
        CRUSHED.clear();
        HUD_BANGS.clear();
        visionUntil = -1;
    }

    private static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static Vector3f rel(Vec3 p, Vec3 cam) {
        return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
    }

    // ---------- every frame ----------

    static void render(MultiBufferSource.BufferSource source, Vec3 cam, double now, float partial) {
        VertexConsumer solid = source.getBuffer(FxRenderTypes.SOLID);
        prune(now);
        grades(now, cam);
        for (XChain x : X.values()) {
            xChain(solid, x, cam, now, partial);
        }
        for (VBind v : V.values()) {
            vChains(solid, v, cam, now, partial);
        }
        for (Leash l : LEASH.values()) {
            leash(solid, l, cam, now, partial);
        }
        eyes(cam, now, partial);
        marks(cam, now, partial);
        rats(solid, cam, now, partial);
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born);
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case MkNet.CRACK -> crack(solid, f, cam, age);
                case MkNet.POOL -> pool(solid, f, cam, age);
                case MkNet.BITS -> bits(solid, f, cam, age);
                case MkNet.MIST -> mist(f, cam, age);
                case MkNet.SHELL -> shell(f, cam, age);
                case MkNet.DUST -> dust(f, cam, age);
                case MkNet.BANG -> bangFx(f, cam, age);
                case MkNet.COLUMN -> column(f, cam, age);
                case MkNet.VOICE -> voice(f, cam, age);
                case MkNet.STORE -> store(solid, f, cam, age, partial);
                case MkNet.RATS_FLEE -> flee(solid, f, cam, age);
                case MkNet.DRIP -> drip(f, cam, age, partial);
                case MkNet.SOUL_KILL -> soulKill(f, cam, age, partial);
                default -> {
                }
            }
        }
    }

    private static float life(Fx f) {
        return switch (f.kind) {
            case MkNet.CRACK -> 220;
            case MkNet.POOL -> f.b + 14;
            case MkNet.BITS -> 70;
            case MkNet.MIST -> 50;
            case MkNet.SHELL -> 12;
            case MkNet.DUST -> 30;
            case MkNet.BANG -> 10;
            case MkNet.COLUMN -> f.b + 10;
            case MkNet.VOICE -> 12;
            case MkNet.STORE -> 34;
            case MkNet.RATS_FLEE -> 26;
            case MkNet.DRIP -> f.a + 20;
            case MkNet.SOUL_KILL -> 40;
            case MkNet.GRAY -> f.a;
            default -> 20;
        };
    }

    private static void prune(double now) {
        SHOTS.removeIf(f -> now - f.born > life(f));
        X.values().removeIf(x -> now - x.born > x.hold + 16 || (x.released >= 0 && now - x.released > 14));
        V.values().removeIf(v -> (v.back >= 0 && now - v.back > 14) || now - v.born > 400);
        LEASH.values().removeIf(l -> now - l.updated > 24);
        EYES.values().removeIf(e -> e[1] + 8 < now);
        MARKS.values().removeIf(m -> m[1] + 6 < now);
        RATS.values().removeIf(r -> (r.run >= 0 && now - r.run > 18) || now > r.until + 20);
        SINKS.values().removeIf(s -> now - s.born() > s.ticks() + 40);
        CRUSHED.values().removeIf(c -> now - c[0] > 80);
    }

    // ---------- the gray of her words, the dark of the rats ----------

    private static void grades(double now, Vec3 cam) {
        float gray = 0;
        Vec3 at = cam;
        for (Fx f : SHOTS) {
            if (f.kind != MkNet.GRAY || f.pos.distanceTo(cam) > 80) {
                continue;
            }
            float age = (float) (now - f.born);
            float k = smooth(age / 4) * (1 - smooth((age - f.a + 16) / 16)) * f.b;
            if (k > gray) {
                gray = k;
                at = f.pos;
            }
        }
        float vis = vision(now);
        if (vis > 0.004f) {
            ScreenFx.sustainGrade(13, vis, 0xFF2238, cam);
        } else if (gray > 0.004f) {
            ScreenFx.sustainGrade(12, gray, 0xA3122A, at);
        }
    }

    private static float vision(double now) {
        if (visionStart < 0) {
            return 0;
        }
        return smooth((float) (now - visionStart - 2) / 7) * (1 - smooth((float) (now - visionUntil + 9) / 9));
    }

    // ---------- chains ----------

    // Makima's right hand held out (as power/powers/makima/Makima.hand), interpolated.
    private static Vec3 hand(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity le ? le.getViewYRot(partial) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 look = e.getViewVector(partial);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return e.getEyePosition(partial).add(0, -0.32, 0).add(right.scale(0.34)).add(look.scale(0.75));
    }

    private static Vec3 chest(Entity e, float partial) {
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.68, 0);
    }

    // Her back, where the puppets' chains come out.
    private static Vec3 back(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.7, 0).subtract(fwd.scale(e.getBbWidth() * 0.4));
    }

    private static Vec3 nape(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.86 + sinkOffset(e, partial), 0).subtract(fwd.scale(e.getBbWidth() * 0.4));
    }

    private static List<Vector3f> line(Vec3 a, Vec3 b, Vec3 cam, int n, double sag) {
        List<Vector3f> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n;
            Vec3 p = a.lerp(b, u).add(0, -sag * 4 * u * (1 - u), 0);
            out.add(rel(p, cam));
        }
        return out;
    }

    // A coil round a body: center axis at c (feet), radius, from height h0 to h1, turns, starting at angle a0.
    private static List<Vector3f> coil(Vec3 c, double r, double h0, double h1, double turns, double a0, Vec3 cam, double grow) {
        List<Vector3f> out = new ArrayList<>();
        int n = (int) Math.max(4, Math.ceil(turns * 18 * grow));
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n * grow;
            double an = a0 + u * turns * Math.PI * 2;
            out.add(rel(c.add(Math.cos(an) * r, h0 + (h1 - h0) * u, Math.sin(an) * r), cam));
        }
        return out;
    }

    private static void xChain(VertexConsumer solid, XChain x, Vec3 cam, double now, float partial) {
        Entity caster = entity(x.caster);
        if (caster == null) {
            return;
        }
        float t = (float) (now - x.born);
        Vec3 from = hand(caster, partial);
        Entity target = x.target >= 0 ? entity(x.target) : null;
        List<Vector3f> poly;
        float vis;
        float glow = 0.5f, wave = -1, surge = (float) Math.exp(-Math.pow((t - 15) / 1.6, 2)) * 0.9f;
        if (target == null) {
            Vec3 end = x.target >= 0 ? x.lastTarget : x.end;
            poly = line(from, end, cam, 24, 0.08);
            float len = (float) from.distanceTo(end);
            vis = x.target < 0 ? len * easeOut(t / 15) * (1 - smooth((t - 17) / 10)) : len * (1 - smooth((t - 1) / 10));
            if (x.target >= 0 && t > 20) {
                return;
            }
        } else {
            Vec3 c = chest(target, partial);
            x.lastTarget = c;
            Vec3 feet = target.getPosition(partial);
            double w = Math.max(0.3, target.getBbWidth() * 0.62);
            double h = target.getBbHeight();
            Vec3 toHer = new Vec3(from.x - c.x, 0, from.z - c.z);
            double a0 = Math.atan2(toHer.z, toHer.x);
            double wrap = smooth((t - 15) / 11);
            poly = line(from, c.add(Math.cos(a0) * w, 0, Math.sin(a0) * w), cam, 22, 0.05 * (1 - wrap));
            // The wrap round the chest (it squeezes as it closes), then it holds them kneeling: the chest is lower.
            List<Vector3f> loop = coil(feet, w * (1 - 0.12 * wrap), h * 0.68, h * 0.46, 2.3, a0, cam, Math.max(0.02, wrap));
            poly.addAll(loop.subList(1, loop.size()));
            float len = (float) from.distanceTo(c);
            float loopLen = (float) (2.3 * Math.PI * 2 * w);
            vis = len * easeOut(t / 15) + loopLen * (float) wrap;
            if (t > 26 && t < x.hold) {
                // Red pulses running from them down the chain to her hand: the stamina leaving.
                wave = 1 - (float) (((t - 26) * 0.09) % 1);
                glow = 0.7f;
            }
        }
        double end = x.released >= 0 ? x.released : x.born + x.hold;
        float r = smooth((float) (now - end) / 12);
        if (r > 0) {
            vis *= 1 - r;
        }
        Vector3f[] arr = poly.toArray(new Vector3f[0]);
        MkChain.Light light = new MkChain.Light(glow, wave, surge, (float) now);
        MkChain.draw(solid, arr, vis, 0, 1000 + x.caster, light);
        MkChain.halo(arr, vis, light, 1);
    }

    private static void vChains(VertexConsumer solid, VBind v, Vec3 cam, double now, float partial) {
        Entity target = entity(v.target);
        Vec3 feet = target != null && target.isAlive() ? target.getPosition(partial) : v.last;
        if (target != null && target.isAlive()) {
            v.last = feet;
        }
        float t = (float) (now - v.born) - v.order;
        if (t < 13) {
            return;
        }
        float rise = smooth((t - 13) / 22);
        float squeeze = v.morram < 0 ? 0 : smooth((float) (now - v.morram) / 9);
        float back = v.back < 0 ? 0 : smooth((float) (now - v.back) / 12);
        double w = Math.max(0.3, v.width * 0.6);
        double h = v.height;
        // Neck, chest, arms and waist, legs: each chain its own way out of the ground and its own coil.
        double[] heights = {0.86, 0.68, 0.5, 0.2};
        double[] turns = {1.4, 2.1, 1.6, 1.8};
        for (int j = 0; j < 4; j++) {
            int seed = v.target * 13 + j * 7;
            double an = j * Math.PI / 2 + (hash(seed, 1, 5) - 0.5) * 0.9;
            double r0 = 1.2 + hash(seed, 2, 5) * 0.7;
            Vec3 ground = feet.add(Math.cos(an) * r0, -0.35, Math.sin(an) * r0);
            double hb = h * heights[j];
            double rr = w * (j == 2 ? 1.25 : j == 3 ? 0.8 : 1.0) * (1 - 0.45 * squeeze);
            Vec3 entry = feet.add(Math.cos(an + 0.3) * rr, hb - 0.12, Math.sin(an + 0.3) * rr);
            List<Vector3f> poly = new ArrayList<>();
            // Out of the ground in an arc (up first, then in).
            Vec3 mid = ground.lerp(entry, 0.5).add(0, 0.55 + hash(seed, 3, 5) * 0.4, 0);
            for (int i = 0; i <= 14; i++) {
                double u = i / 14.0;
                Vec3 p = ground.scale((1 - u) * (1 - u)).add(mid.scale(2 * u * (1 - u))).add(entry.scale(u * u));
                poly.add(rel(p, cam));
            }
            List<Vector3f> c = coil(feet, rr, hb - 0.12, hb + 0.14, turns[j], an + 0.3, cam, 1);
            poly.addAll(c.subList(1, c.size()));
            Vector3f[] arr = poly.toArray(new Vector3f[0]);
            float total = 0;
            for (int k = 1; k < arr.length; k++) {
                total += arr[k].distance(arr[k - 1]);
            }
            float vis = total * rise * (1 - back);
            float surge = squeeze > 0 ? (1 - squeeze) * 1.2f + 0.4f : (float) Math.exp(-Math.pow((t - 35) / 2.5, 2)) * 0.7f;
            MkChain.Light light = new MkChain.Light(0.45f + 0.5f * squeeze, -1, surge, (float) now + j);
            MkChain.draw(solid, arr, vis, 0, seed, light);
            MkChain.halo(arr, vis, light, 0.8f);
        }
    }

    private static void leash(VertexConsumer solid, Leash l, Vec3 cam, double now, float partial) {
        Entity puppet = entity(l.puppet);
        Entity owner = entity(l.owner);
        if (puppet == null || owner == null || !puppet.isAlive()) {
            return;
        }
        Vec3 a = back(owner, partial);
        Vec3 b = nape(puppet, partial);
        double d = a.distanceTo(b);
        if (d > 64) {
            return;
        }
        float age = (float) (now - l.risen);
        List<Vector3f> poly = line(a, b, cam, Math.max(8, (int) (d * 3)), Math.max(0.15, 0.12 * d + 0.25) * (age < 20 ? 0.4 : 1));
        Vector3f[] arr = poly.toArray(new Vector3f[0]);
        float vis = (float) (d * 1.3) * smooth(age / 8);
        float wave = -1, surge = 0;
        if (l.pulse >= 0) {
            float p = (float) (now - l.pulse);
            if (p < 7) {
                // The blow runs from her down to it (Primeiro-Ministro), or the name from it up to her (Ritual).
                wave = l.pulseKind == 0 ? p / 5 : 1 - p / 6;
                surge = l.pulseKind == 0 ? Math.max(0, 1 - Math.abs(p - 4) / 2.4f) * 1.2f : 0.7f;
            }
        }
        MkChain.Light light = new MkChain.Light(0.25f, wave, surge, (float) now);
        MkChain.draw(solid, arr, vis, 0, 3000 + l.puppet, light);
        MkChain.halo(arr, vis, light, 0.7f);
        if (l.kind == 1) {
            // A soul: its body bound at the chest and arms by two more chains (left from the ground that raised it).
            Vec3 feet = puppet.getPosition(partial).add(0, sinkOffset(puppet, partial), 0);
            double h = puppet.getBbHeight();
            double w = Math.max(0.3, puppet.getBbWidth() * 0.62);
            float grow = smooth(age / 30);
            for (int j = 0; j < 2; j++) {
                List<Vector3f> c = coil(feet, w * (j == 0 ? 1.0 : 1.2), h * (j == 0 ? 0.66 : 0.5), h * (j == 0 ? 0.78 : 0.56), j == 0 ? 2.2 : 1.6,
                        j * 2.1 + l.puppet, cam, Math.max(0.05, grow));
                Vector3f[] ca = c.toArray(new Vector3f[0]);
                float len = (float) (Math.PI * 2 * w * 2.4);
                MkChain.Light bl = new MkChain.Light(0.35f, -1, 0, (float) now + j);
                MkChain.draw(solid, ca, len * grow, 0, 4000 + l.puppet * 3 + j, bl);
            }
        }
    }

    // ---------- the eyes ----------

    private static void eyes(Vec3 cam, double now, float partial) {
        var mc = Minecraft.getInstance();
        for (Map.Entry<Integer, double[]> en : EYES.entrySet()) {
            double[] e = en.getValue();
            float k = smooth((float) (now - e[0]) / 5) * (1 - smooth((float) (now - e[1]) / 8));
            ringEyes(entity(en.getKey()), k, cam, now, partial, mc);
        }
        for (Leash l : LEASH.values()) {
            if (EYES.containsKey(l.puppet)) {
                continue;
            }
            float k = smooth((float) (now - l.risen - 18) / 3);
            ringEyes(entity(l.puppet), k, cam, now, partial, mc);
        }
    }

    private static void ringEyes(Entity e, float k, Vec3 cam, double now, float partial, Minecraft mc) {
        if (!(e instanceof LivingEntity le) || k <= 0.01f || !le.isAlive() || le.position().distanceTo(cam) > 48) {
            return;
        }
        if (le == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float s = Mth.clamp(le.getBbWidth() / 0.6f, 0.6f, 2.2f);
        float yaw = Mth.rotLerp(partial, le.yHeadRotO, le.yHeadRot) * Mth.DEG_TO_RAD;
        float pitch = le.getViewXRot(partial) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(fwd).scale(-1);
        Vec3 eye = le.getEyePosition(partial).add(0, sinkOffset(le, partial), 0);
        Vector3f n = new Vector3f((float) fwd.x, (float) fwd.y, (float) fwd.z);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 at = eye.add(fwd.scale(0.262 * s)).add(right.scale(0.125 * side * s)).add(up.scale(0.01 * s));
            Vector3f p = rel(at, cam);
            float spin = (float) now * 0.12f;
            // Concentric rings, each its own width and pulse: her eyes.
            FxDraw.disk(p, n, 0.16f * s, FxDraw.RING, 0.18f, GOLD, 0.85f * k * (0.85f + 0.15f * Mth.sin((float) now * 0.4f)), true);
            FxDraw.disk(p, n, 0.11f * s, FxDraw.RING, 0.22f, GOLD, 0.95f * k, true);
            FxDraw.disk(p, n, 0.065f * s, FxDraw.RING, 0.3f, GOLD_W, k, true);
            FxDraw.disk(p, n, 0.03f * s, FxDraw.DISC, 0.5f, GOLD_W, 0.9f * k, true);
            FxDraw.billboard(p, 0.22f * s, spin, FxDraw.SOFT, 0.2f, GOLD, 0.35f * k, true);
        }
    }

    // The eyes' mark over a target: her lock (yellow rings) or the Ritual's mark (red).
    private static void marks(Vec3 cam, double now, float partial) {
        for (Map.Entry<Integer, double[]> en : MARKS.entrySet()) {
            Entity e = entity(en.getKey());
            double[] m = en.getValue();
            if (e == null) {
                continue;
            }
            float age = (float) (now - m[0]);
            float k = smooth(age / 3) * (1 - smooth((float) (now - m[1]) / 6));
            float pop = 1 + 0.6f * (1 - smooth(age / 5));
            Vector3f p = rel(e.getPosition(partial).add(0, e.getBbHeight() + 0.55, 0), cam);
            float[] col = m[2] == 1 ? RED : GOLD;
            FxDraw.billboard(p, 0.7f * pop, 0, FxDraw.RING, 0.12f, col, 0.9f * k, true);
            FxDraw.billboard(p, 0.46f * pop, (float) now * 0.1f, FxDraw.RING, 0.18f, col, 0.8f * k, true);
            FxDraw.billboard(p, 0.24f * pop, 0, FxDraw.RING, 0.3f, m[2] == 1 ? CHAIN_HOT : GOLD_W, k, true);
            FxDraw.billboard(p, 0.9f, 0, FxDraw.SOFT, 0.1f, col, 0.25f * k, true);
        }
    }

    // ---------- rats ----------

    private static float[] hsl(float h, float s, float l) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        return new float[] {hue(p, q, h + 1 / 3f), hue(p, q, h), hue(p, q, h - 1 / 3f)};
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1 / 6f) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1 / 2f) {
            return q;
        }
        if (t < 2 / 3f) {
            return p + (q - p) * (2 / 3f - t) * 6;
        }
        return p;
    }

    // A box in a frame (o + x*X + y*Y + z*Z), each face lit by the fixed sun.
    private static void box(VertexConsumer out, Vector3f o, Vector3f ax, Vector3f ay, Vector3f az, float hx, float hy, float hz, float[] col) {
        Vector3f x = new Vector3f(ax).mul(hx), y = new Vector3f(ay).mul(hy), z = new Vector3f(az).mul(hz);
        face(out, o, x, y, z, ax, col);
        face(out, o, new Vector3f(x).negate(), y, new Vector3f(z).negate(), new Vector3f(ax).negate(), col);
        face(out, o, y, z, x, ay, col);
        face(out, o, new Vector3f(y).negate(), z, new Vector3f(x).negate(), new Vector3f(ay).negate(), col);
        face(out, o, z, x, y, az, col);
        face(out, o, new Vector3f(z).negate(), x, new Vector3f(y).negate(), new Vector3f(az).negate(), col);
    }

    // The face at o + n, spanned by u and v.
    private static void face(VertexConsumer out, Vector3f o, Vector3f n, Vector3f u, Vector3f v, Vector3f normal, float[] col) {
        float l = 0.42f + 0.25f * (0.5f + 0.5f * normal.y) + 0.55f * Math.max(0, normal.dot(SUN));
        float r = Math.min(1, col[0] * l), g = Math.min(1, col[1] * l), b = Math.min(1, col[2] * l);
        Vector3f c = new Vector3f(o).add(n);
        put(out, new Vector3f(c).sub(u).sub(v), r, g, b);
        put(out, new Vector3f(c).add(u).sub(v), r, g, b);
        put(out, new Vector3f(c).add(u).add(v), r, g, b);
        put(out, new Vector3f(c).sub(u).add(v), r, g, b);
    }

    private static void put(VertexConsumer out, Vector3f p, float r, float g, float b) {
        out.addVertex(p.x, p.y, p.z).setColor(r, g, b, 1f);
    }

    // One rat, built from its seed as the preview's makeRat: proportions, fur, ears and a tail of its own.
    private static void drawRat(VertexConsumer out, int seed, Vector3f at, float yaw, float scale, float run, float now) {
        float l0 = (0.3f + hash(seed, 1, 1) * 0.18f) * scale, w = (0.15f + hash(seed, 2, 1) * 0.08f) * scale, h = (0.12f + hash(seed, 3, 1) * 0.07f) * scale;
        float[] fur = hsl(0.06f + hash(seed, 4, 1) * 0.05f, 0.12f + hash(seed, 5, 1) * 0.2f, 0.22f + hash(seed, 6, 1) * 0.2f);
        float[] belly = {Math.min(1, fur[0] + 0.12f), Math.min(1, fur[1] + 0.11f), Math.min(1, fur[2] + 0.1f)};
        float[] pink = {0.85f, 0.54f, 0.59f};
        float[] tailCol = {0.72f, 0.54f, 0.54f};
        Vector3f fw = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f rt = new Vector3f(fw).cross(up);
        float ph = hash(seed, 7, 1) * 6.28f;
        float bob = run > 0 ? Math.abs(Mth.sin(now * 1.6f + ph)) * 0.04f * scale : 0;
        Vector3f body = new Vector3f(at).add(0, h / 2 + 0.02f * scale + bob, 0);
        box(out, body, rt, up, fw, w / 2, h / 2, l0 / 2, fur);
        box(out, new Vector3f(at).add(0, 0.025f * scale, 0), rt, up, fw, w * 0.4f, 0.01f * scale, l0 * 0.4f, belly);
        float hs = w * (0.75f + hash(seed, 8, 1) * 0.15f);
        float sniff = run > 0 ? 0 : Mth.sin(now * 0.9f + ph) * 0.02f * scale;
        Vector3f head = new Vector3f(at).add(new Vector3f(fw).mul(l0 / 2 + hs * 0.35f + sniff)).add(0, h * 0.7f + bob, 0);
        box(out, head, rt, up, fw, hs / 2, hs * 0.4f, hs * 0.55f, fur);
        Vector3f snout = new Vector3f(head).add(new Vector3f(fw).mul(hs * 0.7f)).add(0, -hs * 0.12f, 0);
        box(out, snout, rt, up, fw, hs * 0.22f, hs * 0.2f, hs * 0.25f, fur);
        box(out, new Vector3f(head).add(new Vector3f(fw).mul(hs * 0.96f)).add(0, -hs * 0.06f, 0), rt, up, fw, hs * 0.09f, hs * 0.07f, 0.01f * scale, pink);
        for (int s = -1; s <= 1; s += 2) {
            float ew = hs * (0.32f + hash(seed, 9 + s, 1) * 0.15f), eh = hs * (0.35f + hash(seed, 11 + s, 1) * 0.2f);
            Vector3f ear = new Vector3f(head).add(new Vector3f(rt).mul(s * hs * 0.4f)).add(0, hs * 0.5f, 0).add(new Vector3f(fw).mul(-hs * 0.1f));
            box(out, ear, rt, up, fw, ew / 2, eh / 2, 0.01f * scale, pink);
            Vector3f eye = new Vector3f(head).add(new Vector3f(rt).mul(s * hs * 0.3f)).add(0, hs * 0.12f, 0).add(new Vector3f(fw).mul(hs * 0.56f));
            box(out, eye, rt, up, fw, 0.0125f * scale, 0.0125f * scale, 0.006f * scale, new float[] {0.04f, 0.04f, 0.04f});
        }
        // The tail: segments thinning out, its own curl, swaying.
        int seg = 7 + (int) (hash(seed, 13, 1) * 4);
        float tl = (0.4f + hash(seed, 14, 1) * 0.3f) / seg * scale;
        float curl = (hash(seed, 15, 1) - 0.5f) * 0.5f;
        float tw = (0.04f + hash(seed, 16, 1) * 0.02f) * scale;
        Vector3f p = new Vector3f(at).add(new Vector3f(fw).mul(-l0 / 2)).add(0, h * 0.4f + bob, 0);
        float dir = yaw + Mth.PI;
        for (int i = 0; i < seg; i++) {
            dir += curl + (hash(seed, 20 + i, 1) - 0.5f) * 0.2f + Mth.sin(now * (run > 0 ? 0.9f : 0.25f) + ph + i * 0.6f) * 0.12f;
            Vector3f d = new Vector3f(-Mth.sin(dir), -0.08f, Mth.cos(dir)).normalize();
            Vector3f c = new Vector3f(p).add(new Vector3f(d).mul(tl / 2));
            Vector3f r2 = new Vector3f(d).cross(up).normalize();
            float wi = tw * (1 - i / (float) seg * 0.8f);
            box(out, c, r2, up, d, wi / 2, wi / 2, tl / 2, tailCol);
            p.add(new Vector3f(d).mul(tl));
        }
    }

    private static void rats(VertexConsumer solid, Vec3 cam, double now, float partial) {
        var mc = Minecraft.getInstance();
        float vis = vision(now);
        boolean mine = mc.player != null && mc.player.getId() == visionCaster;
        for (Rat r : RATS.values()) {
            if (r.pos.distanceTo(cam) > 96) {
                continue;
            }
            float t = (float) (now - r.born);
            Vec3 at = r.pos;
            float run = 0;
            float yaw = r.yaw + Mth.sin((float) now * 0.05f + r.seed) * 0.3f;
            if (r.run >= 0) {
                run = (float) (now - r.run);
                at = at.add(r.away.scale(run * 0.32));
                yaw = (float) Math.atan2(-r.away.x, r.away.z);
            }
            double hop = r.hop >= 0 ? now - r.hop : -1;
            if (hop >= 0 && hop < 6) {
                at = at.add(0, Math.sin(hop / 6 * Math.PI) * 0.25, 0);
            }
            float appear = smooth(t / 6);
            float scale = 0.9f * appear * (run > 0 ? 1 - smooth((run - 10) / 8) : 1);
            if (scale < 0.02f) {
                continue;
            }
            drawRat(solid, r.seed, rel(at, cam), yaw, scale * 1.15f, run, (float) now);
            if (mine && r.caster == visionCaster && vis > 0.01f && run <= 0) {
                // Her rings over each rat, lit from the nearest out; the one picked for the jump grows and pulses.
                float on = smooth((float) (now - visionStart - 7 - r.rank * 1.5f) / 2.4f) * vis;
                boolean sel = visionPick == r.index && now - visionPickAt < 12;
                float s = sel ? 1.35f + 0.25f * Mth.sin((float) now * 1.4f) : 1;
                float pop = 1 + 0.6f * (1 - smooth((float) (now - visionStart - 7 - r.rank * 1.5f) / 4));
                Vector3f p = rel(at.add(0, 0.62, 0), cam);
                float dim = visionPick >= 0 && !sel ? 0.4f : 1;
                // The preview's thin orange rings (1, .58, .12), a pulsing inner one and a small core; no soft halo, which
                // the gray of the vision washed into a white bubble.
                float inner = 0.62f + 0.03f * Mth.sin((float) now * 0.25f + r.index);
                FxDraw.billboard(p, 0.5f * s * pop, 0, FxDraw.RING, 0.08f, RAT_RING, on * dim, false);
                FxDraw.billboard(p, 0.5f * inner * s * pop, 0, FxDraw.RING, 0.1f, RAT_RING, 0.8f * on * dim, false);
                FxDraw.billboard(p, 0.09f * s * pop, 0, FxDraw.DISC, 0.5f, sel ? RAT_SEL : RAT_RING, 0.7f * on * dim, false);
            }
        }
    }

    private static void flee(VertexConsumer solid, Fx f, Vec3 cam, float age) {
        int n = (int) f.a;
        int base = (int) f.c;
        for (int i = 0; i < n; i++) {
            int seed = base * 31 + i * 17;
            float an = i / (float) n * Mth.TWO_PI + (hash(seed, 1, 9) - 0.5f) * 0.9f;
            float sp = 0.22f + hash(seed, 2, 9) * 0.16f;
            float t = Math.max(0, age - hash(seed, 3, 9) * 3);
            Vec3 at = f.pos.add(Math.cos(an) * (0.3 + t * sp), 0, Math.sin(an) * (0.3 + t * sp));
            float s = 0.8f * smooth(t / 3) * (1 - smooth((age - 18) / 6));
            if (s > 0.02f) {
                drawRat(solid, seed, rel(at, cam), (float) Math.atan2(-Math.cos(an), Math.sin(an)) * 0 + an - Mth.HALF_PI, s, 1, age);
            }
        }
    }

    // ---------- ground ----------

    // A pool: a blob of its own shape (the edge from its seed), growing, staying, shrinking away.
    private static void pool(VertexConsumer solid, Fx f, Vec3 cam, float age) {
        if (f.pos.distanceTo(cam) > 96) {
            return;
        }
        float grow = easeOut(age / 8) * (1 - smooth((age - f.b) / 12));
        if (grow <= 0.01f) {
            return;
        }
        boolean blood = f.c == 1;
        int seed = (int) (f.pos.x * 13 + f.pos.z * 7) + f.id;
        float r = f.a / 2 * grow;
        int n = 30;
        Vector3f c = rel(f.pos.add(0, blood ? 0.018 : 0.022, 0), cam);
        Vector3f[] ring = new Vector3f[n];
        Vector3f[] rim = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            float an = i / (float) n * Mth.TWO_PI;
            float k = 0.78f + 0.16f * Mth.sin(an * 2 + hash(seed, 1, 3) * 6) + 0.1f * Mth.sin(an * 5 + hash(seed, 2, 3) * 6)
                    + 0.06f * Mth.sin(an * 9 + hash(seed, 3, 3) * 6);
            ring[i] = new Vector3f(c).add(Mth.cos(an) * r * k, 0, Mth.sin(an) * r * k);
            rim[i] = new Vector3f(c).add(Mth.cos(an) * r * (k + 0.08f), -0.002f, Mth.sin(an) * r * (k + 0.08f));
        }
        float[] body = blood ? BLOOD : SHADOW;
        float[] edge = blood ? BLOOD_D : SHADOW_RIM;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            flat(solid, rim[i], rim[j], ring[j], ring[i], edge);
            flat(solid, c, ring[i], ring[j], c, body);
        }
        if (blood) {
            // A gloss on the blood: a thin light streak of its own.
            Vector3f g = new Vector3f(c).add(r * 0.2f * (hash(seed, 4, 3) - 0.5f), 0.002f, r * 0.2f * (hash(seed, 5, 3) - 0.5f));
            flat(solid, new Vector3f(g).add(-r * 0.22f, 0, -r * 0.05f), new Vector3f(g).add(r * 0.18f, 0, -r * 0.09f),
                    new Vector3f(g).add(r * 0.2f, 0, -r * 0.02f), new Vector3f(g).add(-r * 0.2f, 0, 0.02f * r), BLOOD_GLOSS);
        } else {
            // A faint red light breathing at its edge.
            FxDraw.disk(new Vector3f(c).add(0, 0.01f, 0), new Vector3f(0, 1, 0), r * 2.4f, FxDraw.RING, 0.08f, CHAIN_RED, 0.25f * grow, true);
        }
    }

    private static void flat(VertexConsumer out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float[] col) {
        put(out, a, col[0], col[1], col[2]);
        put(out, b, col[0], col[1], col[2]);
        put(out, c, col[0], col[1], col[2]);
        put(out, d, col[0], col[1], col[2]);
    }

    // Cracks: every crack its own stroke (width, bends, branches), radial (V, kneel) or a web (Ritual).
    private static void crack(VertexConsumer solid, Fx f, Vec3 cam, float age) {
        if (f.pos.distanceTo(cam) > 96) {
            return;
        }
        float grow = easeOut(age / (f.b == 1 ? 3 : 7));
        float fade = 1 - smooth((age - 180) / 40);
        if (fade <= 0) {
            return;
        }
        int seed = (int) (f.c * 97);
        Vector3f c = rel(f.pos.add(0, 0.016, 0), cam);
        int n = f.b == 2 ? 11 : 9;
        float radius = f.a;
        List<float[]> ends = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float an = i * Mth.TWO_PI / n + (hash(seed, i, 1) - 0.5f) * 0.6f;
            float len = (0.55f + 0.45f * hash(seed, i, 2)) * radius * grow;
            float x = 0, z = 0;
            float dir = an;
            float w = (0.05f + 0.04f * hash(seed, i, 3)) * Math.min(1.6f, 0.6f + radius * 0.15f) * fade;
            for (int k = 0; k < 5; k++) {
                float step = len / 5;
                dir += (hash(seed, i * 11 + k, 4) - 0.5f) * 0.8f;
                float nx = x + Mth.cos(dir) * step, nz = z + Mth.sin(dir) * step;
                float wk = w * (1 - k * 0.17f);
                stroke(solid, c, x, z, nx, nz, wk);
                if (k == 2 && hash(seed, i, 5) < 0.55f) {
                    float bd = dir + (hash(seed, i, 6) < 0.5f ? 0.7f : -0.7f);
                    float bl = step * (1.2f + hash(seed, i, 7));
                    stroke(solid, c, nx, nz, nx + Mth.cos(bd) * bl, nz + Mth.sin(bd) * bl, wk * 0.6f);
                }
                x = nx;
                z = nz;
            }
            ends.add(new float[] {x, z});
        }
        if (f.b == 2) {
            // The Ritual's web: rings across the strokes.
            for (int ringI = 1; ringI <= 2; ringI++) {
                float rr = radius * grow * (0.35f + 0.3f * ringI);
                for (int i = 0; i < n; i++) {
                    float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
                    if (hash(seed, i + ringI * 50, 8) < 0.25f) {
                        continue;
                    }
                    stroke(solid, c, Mth.cos(a0) * rr, Mth.sin(a0) * rr, Mth.cos(a1) * rr * 0.96f, Mth.sin(a1) * rr * 0.96f, 0.035f * fade);
                }
            }
        }
        // The pit in the middle.
        if (f.b != 1) {
            Vector3f pc = new Vector3f(c).add(0, 0.002f, 0);
            float pr = radius * 0.16f * grow * fade;
            for (int i = 0; i < 12; i++) {
                float a0 = i * Mth.TWO_PI / 12, a1 = (i + 1) * Mth.TWO_PI / 12;
                float k0 = 0.8f + 0.3f * hash(seed, i, 9), k1 = 0.8f + 0.3f * hash(seed, (i + 1) % 12, 9);
                flat(solid, pc, new Vector3f(pc).add(Mth.cos(a0) * pr * k0, 0, Mth.sin(a0) * pr * k0),
                        new Vector3f(pc).add(Mth.cos(a1) * pr * k1, 0, Mth.sin(a1) * pr * k1), pc, CRACK);
            }
        }
    }

    private static void stroke(VertexConsumer out, Vector3f c, float x0, float z0, float x1, float z1, float w) {
        float dx = x1 - x0, dz = z1 - z0;
        float l = Mth.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f || w <= 0.002f) {
            return;
        }
        float px = -dz / l * w / 2, pz = dx / l * w / 2;
        flat(out, new Vector3f(c).add(x0 - px * 1.8f, -0.001f, z0 - pz * 1.8f), new Vector3f(c).add(x1 - px * 1.8f, -0.001f, z1 - pz * 1.8f),
                new Vector3f(c).add(x1 + px * 1.8f, -0.001f, z1 + pz * 1.8f), new Vector3f(c).add(x0 + px * 1.8f, -0.001f, z0 + pz * 1.8f), CRACK_EDGE);
        flat(out, new Vector3f(c).add(x0 - px, 0, z0 - pz), new Vector3f(c).add(x1 - px, 0, z1 - pz), new Vector3f(c).add(x1 + px, 0, z1 + pz),
                new Vector3f(c).add(x0 + px, 0, z0 + pz), CRACK);
    }

    // ---------- pieces, mist, shell, dust ----------

    private static void bits(VertexConsumer solid, Fx f, Vec3 cam, float age) {
        int n = (int) f.a;
        int style = (int) f.c;
        float force = f.b;
        int seed = f.id * 7 + (int) (f.pos.y * 3);
        double ground = f.pos.y - (style == 0 ? 0.1 : style == 2 ? 0.3 : 1.0);
        for (int i = 0; i < n; i++) {
            float an = hash(seed, i, 1) * Mth.TWO_PI;
            float up = style == 2 ? 0.05f + hash(seed, i, 2) * 0.12f : 0.15f + hash(seed, i, 2) * 0.4f;
            float sp = (0.08f + hash(seed, i, 3) * 0.22f) * force * (style == 2 ? 1.6f : 1);
            double vx = Math.cos(an) * sp + f.dir.x * 0.08 * hash(seed, i, 4);
            double vz = Math.sin(an) * sp + f.dir.z * 0.08 * hash(seed, i, 4);
            double vy = up * force + f.dir.y * 0.05;
            float t = Math.min(age, 40);
            double y = f.pos.y + vy * t - 0.04 * t * t;
            double land = ground + 0.02;
            float tl = t;
            if (y < land) {
                // Where it hit the ground it stays (rolls a little).
                double a = 0.04, b = -vy, cc = land - f.pos.y;
                double disc = b * b - 4 * a * cc;
                tl = disc > 0 ? (float) ((-b + Math.sqrt(disc)) / (2 * a)) : t;
                tl = Math.max(0, Math.min(t, tl));
                y = land;
            }
            double x = f.pos.x + vx * tl + vx * 0.25 * (t - tl);
            double z = f.pos.z + vz * tl + vz * 0.25 * (t - tl);
            float size = (style == 0 ? 0.05f + hash(seed, i, 5) * 0.08f : 0.06f + hash(seed, i, 5) * 0.12f) * (1 - smooth((age - 50) / 20));
            if (size <= 0.004f) {
                continue;
            }
            float[] col;
            if (style == 0) {
                float h = hash(seed, i, 6);
                col = h < 0.25f ? new float[] {0.42f, 0.6f, 0.3f} : h < 0.6f ? new float[] {0.36f, 0.27f, 0.19f} : new float[] {0.29f, 0.23f, 0.16f};
            } else {
                float h = hash(seed, i, 6);
                col = h < 0.45f ? new float[] {0.42f + 0.2f * h, 0.02f, 0.04f} : h < 0.75f ? new float[] {0.25f, 0.01f, 0.03f}
                        : new float[] {0.72f, 0.42f, 0.38f};
            }
            float spin = tl * (0.3f + hash(seed, i, 7) * 0.5f);
            Vector3f o = rel(new Vec3(x, y + size * 0.5, z), cam);
            Vector3f ax = new Vector3f(Mth.cos(spin), 0, Mth.sin(spin));
            Vector3f ay = new Vector3f(-Mth.sin(spin) * 0.3f, 1, Mth.cos(spin) * 0.3f).normalize();
            Vector3f az = new Vector3f(ax).cross(ay).normalize();
            float sx = size * (0.6f + hash(seed, i, 8) * 0.8f), sy = size * (style == 2 ? 0.35f : 0.5f + hash(seed, i, 9) * 0.6f),
                    sz = size * (0.6f + hash(seed, i, 10) * 0.7f);
            box(solid, o, ax, ay, az, sx / 2, sy / 2, sz / 2, col);
        }
    }

    private static void mist(Fx f, Vec3 cam, float age) {
        int n = (int) Math.min(160, f.a);
        int seed = f.id * 3 + (int) (f.pos.x * 5);
        Vec3 d = f.dir.lengthSqr() > 1e-4 ? f.dir.normalize() : Vec3.ZERO;
        for (int i = 0; i < n; i++) {
            float t = age - hash(seed, i, 1) * 3;
            if (t < 0) {
                continue;
            }
            Vector3f s = DbFx.sphere(seed, i);
            float sp = 0.05f + hash(seed, i, 2) * 0.16f;
            double vx = s.x * sp + d.x * (0.1 + 0.25 * hash(seed, i, 3));
            double vy = s.y * sp * 0.7 + d.y * 0.2 + 0.03;
            double vz = s.z * sp + d.z * (0.1 + 0.25 * hash(seed, i, 3));
            float drag = (1 - (float) Math.pow(0.9, t)) / 0.1f;
            Vec3 p = f.pos.add(vx * drag, vy * drag - 0.004 * t * t, vz * drag);
            float life = 26 + hash(seed, i, 4) * 20;
            float k = (1 - smooth((t - life * 0.6f) / (life * 0.4f)));
            if (k <= 0) {
                continue;
            }
            float size = (0.05f + hash(seed, i, 5) * 0.16f) * (1 + t * 0.03f);
            float[] col = hash(seed, i, 6) < 0.6f ? BLOOD : BLOOD_D;
            FxDraw.billboard(rel(p, cam), size, hash(seed, i, 7) * 6, FxDraw.SMOKE, 0.15f + 0.6f * (1 - k), col, 0.85f * k, false);
        }
    }

    // The shock shell of a hit: torn rings round the point, each its own tilt, quick.
    private static void shell(Fx f, Vec3 cam, float age) {
        int seed = (int) f.c;
        float k = 1 - smooth(age / 10);
        float g = easeOut(age / 6);
        Vector3f c = rel(f.pos, cam);
        for (int i = 0; i < 4; i++) {
            Vector3f n = DbFx.sphere(seed, i);
            float s = f.a * (0.6f + 0.5f * hash(seed, i, 1)) * g;
            FxDraw.disk(c, n, s * 2, FxDraw.RING, 0.06f + 0.06f * hash(seed, i, 2), i == 0 ? WHITE : CHAIN_HOT, 0.8f * k, true);
        }
        FxDraw.billboard(c, f.a * 1.6f * g, seed, FxDraw.FLARE, 10 / 32f, CHAIN_HOT, 0.7f * k, true);
        FxDraw.billboard(c, f.a * 0.8f, 0, FxDraw.SOFT, 0.8f, WHITE, 0.9f * (1 - smooth(age / 4)), true);
        if (age < 1.5f) {
            ScreenFx.warp(f.pos, f.a * 1.4f, 0.5f, 3f, 0, new Vec3(0, 1, 0), 0, 0, 8, -1, null, 0);
        }
    }

    private static void dust(Fx f, Vec3 cam, float age) {
        int n = (int) (10 + f.a * 4);
        int seed = f.id * 5 + (int) f.pos.y;
        for (int i = 0; i < n; i++) {
            float an = i / (float) n * Mth.TWO_PI + hash(seed, i, 1) * 0.5f;
            float r = f.a * (0.3f + easeOut(age / 14) * (0.7f + 0.4f * hash(seed, i, 2)));
            Vec3 p = f.pos.add(Math.cos(an) * r, 0.15 + age * 0.012 * hash(seed, i, 3), Math.sin(an) * r);
            float k = 1 - smooth((age - 10) / 20);
            float size = (0.4f + 0.5f * hash(seed, i, 4)) * (1 + age * 0.04f);
            float[] col = hash(seed, i, 5) < 0.5f ? new float[] {0.55f, 0.5f, 0.44f} : new float[] {0.45f, 0.4f, 0.35f};
            FxDraw.billboard(rel(p, cam), size, hash(seed, i, 6) * 6, FxDraw.SMOKE, 0.2f + 0.6f * (1 - k), col, 0.5f * k, false);
        }
    }

    // The Bang: nothing flies; a flash at the fingertip and a thin ripple of air along the line.
    private static void bangFx(Fx f, Vec3 cam, float age) {
        Vector3f tip = rel(f.pos, cam);
        float k = 1 - smooth(age / 4);
        FxDraw.billboard(tip, 0.35f + 0.3f * f.a, 0, FxDraw.SOFT, 0.7f, WHITE, 0.8f * k, true);
        FxDraw.billboard(tip, 0.6f, age, FxDraw.FLARE, 6 / 32f, CHAIN_HOT, 0.5f * k, true);
        Vector3f end = rel(f.pos.add(f.dir), cam);
        float u = easeOut(age / 3);
        Vector3f head = new Vector3f(tip).lerp(end, u);
        FxDraw.ribbon(tip, head, 0.08f, FxDraw.STREAK, 0.2f, WHITE, 0.3f * k, true);
        Vector3f n = new Vector3f(end).sub(tip);
        if (n.lengthSquared() > 1e-4f) {
            n.normalize();
            for (int i = 0; i < 4; i++) {
                float s = (i + 1) / 5f;
                FxDraw.disk(new Vector3f(tip).lerp(end, s * u), n, 0.3f + 0.4f * i * 0.25f, FxDraw.RING, 0.05f, WHITE, 0.25f * k, true);
            }
        }
    }

    // The Ritual's pressure coming down on a body: a column of dark air pressing from above.
    private static void column(Fx f, Vec3 cam, float age) {
        float k = smooth(age / 3) * (1 - smooth((age - f.b) / 8));
        if (k <= 0) {
            return;
        }
        int seed = f.id * 11 + (int) f.pos.x;
        float r = f.a;
        int n = 14;
        for (int i = 0; i < n; i++) {
            float an = i / (float) n * Mth.TWO_PI + hash(seed, i, 1);
            float rr = r * (0.5f + 0.5f * hash(seed, i, 2));
            float top = 4 + hash(seed, i, 3) * 4;
            float y = top - (age * (0.9f + hash(seed, i, 4) * 0.6f)) % top;
            Vec3 a = f.pos.add(Math.cos(an) * rr, y + 1.6, Math.sin(an) * rr);
            Vec3 b = f.pos.add(Math.cos(an) * rr * 0.9, Math.max(0.1, y - 1.2), Math.sin(an) * rr * 0.9);
            FxDraw.ribbon(rel(a, cam), rel(b, cam), 0.08f + 0.06f * hash(seed, i, 5), FxDraw.STREAK, 0.1f, SHADOW, 0.6f * k, false);
            FxDraw.ribbon(rel(a, cam), rel(b, cam), 0.05f, FxDraw.STREAK, 0.3f, CHAIN_RED, 0.25f * k, true);
        }
        FxDraw.disk(rel(f.pos.add(0, 0.05, 0), cam), new Vector3f(0, 1, 0), r * 2.6f * easeOut(age / 4), FxDraw.RING, 0.08f, CHAIN_RED, 0.4f * k, true);
    }

    // Her voice through the air, from her mouth to the target: rings riding the line.
    private static void voice(Fx f, Vec3 cam, float age) {
        Vec3 n = f.dir.normalize();
        float u = easeOut(age / 7);
        float k = 1 - smooth((age - 5) / 6);
        Vector3f nn = new Vector3f((float) n.x, (float) n.y, (float) n.z);
        for (int i = 0; i < 5; i++) {
            float s = u - i * 0.12f;
            if (s <= 0) {
                continue;
            }
            Vec3 p = f.pos.add(f.dir.scale(s));
            FxDraw.disk(rel(p, cam), nn, 0.5f + i * 0.18f, FxDraw.RING, 0.06f, WHITE, 0.3f * k * (1 - i * 0.15f), true);
        }
    }

    // A body she killed: a chain from her back hooks it and drags it to her shadow, where it sinks.
    private static void store(VertexConsumer solid, Fx f, Vec3 cam, float age, float partial) {
        Entity owner = entity((int) f.a);
        if (owner == null) {
            return;
        }
        Vec3 from = back(owner, partial);
        Vec3 drop = owner.position();
        float drag = (float) Math.floor(smooth((age - 8) / 14) * 10) / 10f;
        Vec3 body = f.pos.lerp(drop, drag * 0.85).add(0, 0.3, 0);
        if (age > 22) {
            return;
        }
        List<Vector3f> poly = line(from, body, cam, 20, 0.2);
        Vector3f[] arr = poly.toArray(new Vector3f[0]);
        float vis = (float) from.distanceTo(body) * 1.2f * easeOut(age / 6);
        MkChain.Light l = new MkChain.Light(0.6f, -1, (float) Math.exp(-Math.pow((age - 6) / 1.5, 2)) * 0.9f, age);
        MkChain.draw(solid, arr, vis, 0, 5000 + f.id, l);
        MkChain.halo(arr, vis, l, 0.7f);
        if (age > 6 && age < 7.5f) {
            SHOTS.add(new Fx(MkNet.POOL, f.id, drop.add(0, 0, 0), Vec3.ZERO, 1.6f, 30, 0, ScreenFx.now() + 12));
        }
    }

    // Her own blood dripping from the hand that signed the Ritual.
    private static void drip(Fx f, Vec3 cam, float age, float partial) {
        Entity e = entity(f.id);
        if (e == null || age > f.a + 18) {
            return;
        }
        Vec3 h = hand(e, partial).add(0, 0.05, 0);
        double ground = e.getPosition(partial).y + 0.03;
        for (int i = 0; i < 14; i++) {
            float born = hash(f.id, i, 2) * f.a;
            float t = age - born;
            if (t < 0 || t > 16) {
                continue;
            }
            Vec3 p = h.add((hash(f.id, i, 3) - 0.5) * 0.08, -0.02 * t * t, (hash(f.id, i, 4) - 0.5) * 0.08);
            if (p.y < ground) {
                FxDraw.disk(rel(new Vec3(p.x, ground, p.z), cam), new Vector3f(0, 1, 0), 0.12f + 0.08f * hash(f.id, i, 5), FxDraw.DISC, 0.3f, BLOOD,
                        0.9f * (1 - smooth((t - 10) / 6)), false);
            } else {
                FxDraw.billboard(rel(p, cam), 0.05f + 0.03f * hash(f.id, i, 6), 0, FxDraw.DISC, 0.3f, BLOOD, 0.95f, false);
            }
        }
    }

    // A soul puppet's "Morram": a Gojo's Blue crushing the target, or a Sukuna's burst of cuts.
    private static void soulKill(Fx f, Vec3 cam, float age, float partial) {
        Entity soul = entity(f.id);
        Entity target = entity((int) f.dir.x);
        if (target == null) {
            return;
        }
        Vec3 c = target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
        String type = soul == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(soul.getType()).getPath();
        boolean blue = type.contains("gojo") || type.contains("strongest");
        if (blue) {
            float d = age - 19;
            float s = d < 0 ? 0.2f + 0.8f * smooth((age - 2) / 6) : Math.max(0.02f, 1 + 1.5f * easeOut(d / 2.4f) - 2.5f * smooth((d - 3) / 6));
            float k = smooth((age - 2) / 3) * (1 - smooth((d - 7) / 4));
            Vector3f p = rel(c, cam);
            FxDraw.billboard(p, 1.2f * s, age * 0.2f, FxDraw.SOFT, 0.8f, AZUL, 0.9f * k, true);
            FxDraw.billboard(p, 2.2f * s, -age * 0.1f, FxDraw.RING, 0.1f, AZUL, 0.6f * k, true);
            FxDraw.billboard(p, 0.5f * s, 0, FxDraw.SOFT, 1f, WHITE, k, true);
            if (age < 19) {
                ScreenFx.warp(c, 2.2f, 0.4f, 1, 1, new Vec3(0, 1, 0), 0x3A7BFF, 0.1f, 2, f.id, null, 0);
            }
        } else {
            int tick = (int) Math.floor(age);
            for (int j = 0; j < 3; j++) {
                int s = tick * 3 + j;
                if (age > 22) {
                    break;
                }
                Vector3f a = DbFx.sphere(s + f.id, 1);
                Vector3f p = rel(c.add(a.x * 0.3, a.y * 0.5, a.z * 0.3), cam);
                Vector3f dir = DbFx.sphere(s + f.id, 2).mul(0.9f + hash(s, 3, 1) * 0.8f);
                FxDraw.ribbon(new Vector3f(p).sub(dir), new Vector3f(p).add(dir), 0.06f + 0.05f * hash(s, 4, 1), FxDraw.STREAK, 0.9f, WHITE, 0.9f, true);
                FxDraw.ribbon(new Vector3f(p).sub(dir), new Vector3f(p).add(dir), 0.04f, FxDraw.STREAK, 0.2f, RED, 0.8f, false);
            }
        }
    }

    // ---------- bodies sinking, rising, crushed ----------

    // How far below its place an entity is drawn (sinking into a pool, rising out of one jerkily).
    public static float sinkOffset(Entity e, float partial) {
        Sink s = SINKS.get(e.getId());
        if (s == null) {
            return 0;
        }
        double now = ScreenFx.now() + partial;
        float t = (float) ((now - s.born()) / s.ticks());
        float depth = Math.max(1.95f, e.getBbHeight() * 1.1f);
        if (s.rise()) {
            if (t >= 1) {
                // A little settle once up.
                float d = (float) (now - s.born() - s.ticks());
                return d < 6 ? (float) Math.exp(-d * 0.4) * 0.07f * Mth.sin(d * 1.5f) : 0;
            }
            float q = (float) Math.floor(Mth.clamp(t, 0, 1) * 10) / 10f;
            return -depth * (1 - q * (2 - q));
        }
        float k = smooth(t);
        return -depth * k;
    }

    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() || (SINKS.isEmpty() && CRUSHED.isEmpty())) {
            return;
        }
        LivingEntity e = event.getEntity();
        float off = sinkOffset(e, event.getPartialTick());
        float sy = 1, sxz = 1;
        double[] c = CRUSHED.get(e.getId());
        if (c != null) {
            float d = (float) (ScreenFx.now() + event.getPartialTick() - c[0]);
            int lvl = (int) c[1];
            float bump = (float) Math.exp(-d * 0.45) * 0.18f;
            float keep = lvl >= 3 ? smooth(d / 1.5f) : 1 - smooth((d - 50) / 10);
            float sq = lvl >= 3 ? 0.18f : lvl == 2 ? 0.86f : 0.95f;
            sy = 1 - (1 - sq) * keep - bump * 0.5f;
            sxz = 1 + (1 - sy) * 0.45f;
        }
        if (off == 0 && sy == 1) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, off, 0);
        pose.scale(sxz, sy, sxz);
        PUSHED.add(e.getId());
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ---------- the rats' vision: red ghosts through walls ----------

    static void ghosts(RenderLevelStageEvent event, Vec3 cam, float partial) {
        double now = ScreenFx.now() + partial;
        float vis = vision(now);
        var mc = Minecraft.getInstance();
        if (vis <= 0.01f || mc.player == null || mc.level == null || mc.player.getId() != visionCaster) {
            return;
        }
        if (ghostBuffers == null) {
            ghostBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        }
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        float alpha = 0.6f * vis * (0.85f + 0.15f * Mth.sin((float) now * 0.3f));
        try {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive() || le.distanceTo(mc.player) > 48) {
                    continue;
                }
                Leash leash = LEASH.get(le.getId());
                if (leash != null && leash.owner == mc.player.getId()) {
                    continue;
                }
                Vec3 at = le.getPosition(partial).subtract(cam);
                MultiBufferSource red = type -> {
                    ResourceLocation tex = FxRenderTypes.textureOf(type);
                    String path = tex == null ? "" : tex.getPath();
                    if (tex == null || !(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint")) {
                        return new Tint(null, 0, 0, 0, 0);
                    }
                    return new Tint(ghostBuffers.getBuffer(FxRenderTypes.GHOST), 1f, 0.13f, 0.22f, alpha);
                };
                try {
                    dispatcher.render(le, at.x, at.y, at.z, Mth.lerp(partial, le.yRotO, le.getYRot()), partial, new PoseStack(), red, 0xF000F0);
                } catch (RuntimeException ex) {
                    // A renderer that can't be drawn this way: no ghost for it.
                }
            }
            ghostBuffers.endBatch();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // Every vertex in one flat color (or nothing when out is null).
    private static final class Tint implements VertexConsumer {
        private final VertexConsumer out;
        private final float r;
        private final float g;
        private final float b;
        private final float a;

        Tint(VertexConsumer out, float r, float g, float b, float a) {
            this.out = out;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (out != null) {
                out.addVertex(x, y, z).setColor(r, g, b, a);
            }
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
