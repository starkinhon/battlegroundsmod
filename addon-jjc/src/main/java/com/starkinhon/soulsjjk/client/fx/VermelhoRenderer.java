package com.starkinhon.soulsjjk.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Gojo's Red (power/powers/gojo/Vermelho), drawn the same way as the Skill Visualizer's drawVermelho (aba
// "Gojo · Vermelho", the version Stark approved: bright core; in the game 2x smaller, radius 0.0465). The orb (souls_vfx shape 20) sits at his
// fingertip. From the start white-hot sparks leak out of it in curved dotted threads (the Visualizer's vmSparks); past
// half charge its tails (shape 19, strips with u along them) form one by one, thin and faint with a lit tip, and spin
// around it, never leaving it. Around it a red haze and flakes spin along, and the space bends a little on the orb.
// The server says it is alive every tick (with its charge); where it is comes from the owner's hand here, so it never
// lags. Fired, the orb bursts into the blast: a violent shockwave of repulsion running straight ahead (no beam of
// light: the space bends at its front, thin rings of pressure, speed lines, the dust of the path thrown aside) and,
// where it was fired, a burst with dust thrown aside in a funnel. Shift + X: he hangs upside down (the Toji scene) while
// the orb forms and a little after the shot (onRenderPre turns his body).
public final class VermelhoRenderer {
    private static final float[] DEEP = FxDraw.rgb(0x52000A);
    private static final float[] RED = FxDraw.rgb(0xFF1020);
    private static final float[] HOT = FxDraw.rgb(0xFF9AA0);
    private static final float[] PALE = FxDraw.rgb(0xFFD6D8);

    // The Visualizer's VERMELHO.
    // Half the Visualizer's 0.093 (Stark: the whole Red 2x smaller after testing it).
    private static final float RADIUS = 0.0465f;
    // The blast's look against its tunnel's radius (2x smaller too; the tunnel itself keeps its size).
    private static final float BLAST_LOOK = 0.5f;
    private static final int TAILS = 7;
    private static final int THREADS = 5;
    private static final float SPIN = 0.24f;
    private static final float FORM = 50;
    private static final int SEGMENTS = 24;
    private static final float LENGTH_MIN = 5;
    private static final float LENGTH_MAX = 12;
    private static final float CURL_MIN = 1.0f;
    private static final float CURL_MAX = 2.3f;
    private static final float TAIL_WIDTH = 2.6f;
    private static final float THREAD_WIDTH = 0.8f;
    private static final int FLAKES = 22;
    // Appearing, and fading when cut short (the tails pull in first, then the orb goes out); fired, it is gone in POP.
    private static final float SPAWN = 4;
    private static final float FADE = 24;
    private static final float POP = 4;
    // Fully charged it is this many times bigger (gojo.vermelho.json orbe.escalaMax), and its tails form past TAILS_AT.
    private static final float SCALE_MAX = 1.8f;
    private static final float TAILS_AT = 0.5f;
    // The sparks (the Visualizer's VM_FAISCA).
    private static final int SPARK_THREADS = 20;
    private static final int SPARK_DOTS = 14;
    private static final float SPARK_REACH_MIN = 4;
    private static final float SPARK_REACH_MAX = 11;
    private static final int SPARK_LOOSE = 40;
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] DUST = FxDraw.rgb(0x4A3A36);

    private static final class Orb {
        final int owner;
        final long born;
        long updated;
        long endAt = -1;
        boolean fired;
        float prevCharge, charge;
        // Worked out in render, used again by the overlay.
        Vector3f at;
        float drawR;
        float spawn;
        int seed;

        Orb(int owner, long now) {
            this.owner = owner;
            this.born = now;
            this.updated = now;
        }
    }

    private static final Map<Integer, Orb> ORBS = new HashMap<>();
    private static final List<Orb> DRAWN = new ArrayList<>();

    private VermelhoRenderer() {
    }

    // Upside down (Shift + X): until when, per entity id.
    private static final Map<Integer, Long> FLIP = new HashMap<>();
    private static final java.util.Set<Integer> FLIPPED = new java.util.HashSet<>();
    // How long he stays upside down after the shot, and how long turning back takes.
    private static final int FLIP_AFTER = 14;
    private static final float FLIP_BACK = 4;

    public static void orb(int id, int owner, int mode, float charge, boolean flip) {
        long now = ScreenFx.now();
        if (flip) {
            FLIP.merge(owner, now + (mode == 1 ? FLIP_AFTER : mode == 0 ? 3 : 0), Long::max);
        }
        Orb o = ORBS.get(id);
        if (o == null) {
            if (mode == 0) {
                o = new Orb(owner, now);
                o.prevCharge = o.charge = charge;
                ORBS.put(id, o);
            }
            return;
        }
        o.updated = now;
        if (mode != 0) {
            if (o.endAt < 0) {
                o.endAt = now;
                o.fired = mode == 1;
            }
            return;
        }
        o.prevCharge = o.charge;
        o.charge = charge;
    }

    private record Blast(Vec3 from, Vec3 dir, float range, float radius, float speed, float charge, long born) {
        float reach() {
            return range / Math.max(0.5f, speed);
        }
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    // After the front reaches the end, the blast's light and dust fade for this long.
    private static final float BLAST_FADE = 14;
    private static final float DUST_LIFE = 30;

    public static void blast(Vec3 from, Vec3 dir, float range, float radius, float speed, float charge) {
        BLASTS.add(new Blast(from, dir.normalize(), range, radius, speed, charge, ScreenFx.now()));
    }

    public static boolean active() {
        return !ORBS.isEmpty() || !BLASTS.isEmpty();
    }

    public static void clear() {
        FLIP.clear();
        ORBS.clear();
        DRAWN.clear();
        BLASTS.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        DRAWN.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        // The server stopped talking about it (out of range, say): it fades like at the end.
        for (Orb o : ORBS.values()) {
            if (o.endAt < 0 && now - o.updated > 6) {
                o.endAt = o.updated;
            }
        }
        ORBS.values().removeIf(o -> o.endAt >= 0 && now - o.endAt > (o.fired ? POP : FADE));
        BLASTS.removeIf(b -> now - b.born() > Math.max(b.reach() + BLAST_FADE, DUST_LIFE));
        Camera view = mc.gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector());
        Vector3f up = new Vector3f(view.getUpVector());
        for (Map.Entry<Integer, Orb> entry : ORBS.entrySet()) {
            Orb o = entry.getValue();
            Entity owner = mc.level.getEntity(o.owner);
            if (owner == null) {
                continue;
            }
            float age = (float) (now - o.born);
            float charge = o.endAt >= 0 ? o.charge : Mth.lerp(partial, o.prevCharge, o.charge);
            float spawn = smooth(age / SPAWN);
            float sparks = smooth(age / 8);
            // The tails only past half charge, forming as it charges on.
            float grow = smooth((charge - TAILS_AT) / (1 - TAILS_AT));
            // Past half charge the sparks go out: only the tails (Stark).
            sparks *= 1 - smooth((charge - TAILS_AT) / 0.12f);
            float flash = 0;
            if (o.endAt >= 0) {
                float e = (float) (now - o.endAt);
                if (o.fired) {
                    // It doesn't fly: it bursts at once into the blast.
                    flash = 1 - e / POP;
                    spawn *= 1 - smooth(e / POP);
                    grow *= 1 - smooth(e / 2);
                } else {
                    grow *= 1 - smooth(e / (FADE - 4));
                    sparks *= 1 - smooth(e / (FADE - 4));
                    spawn *= 1 - smooth((e - 6) / (FADE - 6));
                }
            }
            Vec3 world = fingertip(owner, view, camera, partial);
            o.at = world.subtract(camera).toVector3f();
            o.seed = entry.getKey() * 2027 + 4049;
            o.spawn = spawn;
            o.drawR = RADIUS * (1 + (SCALE_MAX - 1) * smooth(charge)) * spawn;
            if (flash > 0) {
                FxDraw.billboard(o.at, RADIUS * (8 + 30 * (1 - flash)), 0, FxDraw.SOFT, 0.6f, RED, flash, true);
            }
            if (o.drawR < 0.01f) {
                continue;
            }
            draw(o.at, o.drawR, age, o.seed, grow, sparks, spawn, left, up);
            ScreenFx.holdWarp(world, o.drawR * 2.4f, 0.25f * spawn, 2, new Vec3(0, 1, 0), age / 20);
            DRAWN.add(o);
        }
        for (Blast b : BLASTS) {
            drawBlast(b, camera, (float) (now - b.born()), left, up);
        }
    }

    // The fired Red: a wall of repulsion running from b.from along b.dir.
    private static void drawBlast(Blast b, Vec3 camera, float age, Vector3f left, Vector3f up) {
        float reach = b.reach();
        float s = Math.min(b.range(), b.speed() * age);
        Vector3f dir = b.dir().toVector3f();
        Vector3f from = b.from().subtract(camera).toVector3f();
        Vector3f front = new Vector3f(dir).mul(s).add(from);
        float r = b.radius() * BLAST_LOOK;
        float tunnel = b.radius();
        int seed = (int) (b.born() * 131 + (long) (b.from().x * 7));
        Vector3f side = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f other = new Vector3f(dir).cross(side).normalize();
        // No beam of light (Stark): the force itself is a violent shockwave of repulsion running down the path. Its front
        // bends the space (a ripple and a lens moving with it) and is drawn as a few thin rings of pressure bursting out;
        // behind it the dust of the path is thrown aside.
        if (age < reach + 2) {
            float o = age < reach ? 1 : 1 - (age - reach) / 2;
            Vec3 frontWorld = b.from().add(b.dir().scale(s));
            ScreenFx.holdWarp(frontWorld, tunnel * 1.3f, 0.7f * o, 0, b.dir(), age / 20);
            ScreenFx.holdWarp(frontWorld, tunnel * 0.8f, 0.45f * o, 2, b.dir(), age / 20);
            for (int i = 0; i < 3; i++) {
                float back = i * r * 0.9f;
                float k = 1 - i / 3f;
                Vector3f at = new Vector3f(dir).mul(Math.max(0, s - back)).add(from);
                FxDraw.disk(at, dir, r * (2.2f + 1.2f * i), FxDraw.RING, 0.08f, PALE, 0.55f * o * k, true);
                FxDraw.disk(at, dir, r * (2.6f + 1.2f * i), FxDraw.RING, 0.14f, RED, 0.35f * o * k, true);
            }
            // Speed lines along the tunnel, just behind the front.
            for (int k = 0; k < 16; k++) {
                float a = FxWorld.hash(seed, k, 71) * Mth.TWO_PI;
                float off = r * (0.3f + 0.8f * FxWorld.hash(seed, k, 72));
                Vector3f base = new Vector3f(side).mul(Mth.cos(a) * off).add(new Vector3f(other).mul(Mth.sin(a) * off));
                float len = Math.min(s, b.speed() * (2 + 3 * FxWorld.hash(seed, k, 73)));
                Vector3f head = new Vector3f(dir).mul(s - r * 0.3f).add(from).add(base);
                Vector3f tail = new Vector3f(dir).mul(-len).add(head);
                FxDraw.ribbon(tail, head, Math.max(0.03f, r * 0.05f), FxDraw.STREAK, 0.9f, k % 3 == 0 ? WHITE : PALE, 0.7f * o, true);
            }
        }
        // The shockwave where it was fired, and only there: a ring bursting out around the axis and dust thrown aside
        // in a funnel.
        float w = smooth(age / 10);
        if (age < 12) {
            float o = 1 - age / 12;
            FxDraw.disk(from, dir, r * (1.5f + 7 * w) * (1 + b.charge()), FxDraw.RING, 0.18f, PALE, 0.8f * o, true);
            FxDraw.disk(new Vector3f(dir).mul(r * 1.5f * w).add(from), dir, r * (1 + 5 * w) * (1 + b.charge()), FxDraw.RING, 0.3f, RED, 0.6f * o, true);
            FxDraw.billboard(from, r * (2 + 4 * w), 0, FxDraw.SOFT, 0.5f, RED, 0.7f * o, true);
        }
        // Dust along the whole path, thrown aside as the front passes.
        int path = Math.min(48, Math.round(b.range() / Math.max(0.5f, tunnel) * 2));
        for (int k = 0; k < path; k++) {
            float along = b.range() * FxWorld.hash(seed, k, 91);
            float t0 = along / Math.max(0.5f, b.speed());
            float t = (age - t0) / DUST_LIFE;
            if (t <= 0 || t >= 1) {
                continue;
            }
            float a = FxWorld.hash(seed, k, 92) * Mth.TWO_PI;
            float out = tunnel * (0.9f + 1.6f * (1 - (float) Math.exp(-(age - t0) / 5)) * (0.6f + 0.6f * FxWorld.hash(seed, k, 93)));
            Vector3f p = new Vector3f(side).mul(Mth.cos(a) * out).add(new Vector3f(other).mul(Mth.sin(a) * out)).add(new Vector3f(dir).mul(along)).add(from);
            float o = Mth.sin(Math.min(1, t * 5) * Mth.HALF_PI) * (1 - smooth(t));
            float size = r * (1.4f + 1.4f * FxWorld.hash(seed, k, 94)) * (0.6f + 0.7f * t);
            FxDraw.billboard(p, size, FxWorld.hash(seed, k, 95) * Mth.TWO_PI, FxDraw.SMOKE, 0.2f + 0.6f * t, DUST, 0.5f * o, false);
        }
        if (age < DUST_LIFE) {
            float t = age / DUST_LIFE;
            float o = Mth.sin(Math.min(1, t * 4) * Mth.HALF_PI) * (1 - smooth(t));
            int puffs = 10 + Math.round(10 * b.charge());
            for (int k = 0; k < puffs; k++) {
                float a = FxWorld.hash(seed, k, 81) * Mth.TWO_PI;
                float along = r * (0.5f + 3 * FxWorld.hash(seed, k, 82));
                float out = r * (0.8f + (2.5f + 3 * b.charge()) * (1 - (float) Math.exp(-age / 6)) * (0.6f + 0.6f * FxWorld.hash(seed, k, 83)));
                Vector3f p = new Vector3f(side).mul(Mth.cos(a) * out).add(new Vector3f(other).mul(Mth.sin(a) * out)).add(new Vector3f(dir).mul(along)).add(from);
                float size = r * (1.2f + 1.4f * FxWorld.hash(seed, k, 84)) * (0.6f + 0.6f * t);
                FxDraw.billboard(p, size, FxWorld.hash(seed, k, 85) * Mth.TWO_PI, FxDraw.SMOKE, 0.2f + 0.6f * t, DUST, 0.55f * o, false);
            }
        }
    }

    // His body upside down, turned around the way he faces, about its middle.
    public static void onRenderPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() || FLIP.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Long until = FLIP.get(entity.getId());
        if (until == null) {
            return;
        }
        double now = ScreenFx.now() + event.getPartialTick();
        float k = Mth.clamp((float) ((until + FLIP_BACK - now) / FLIP_BACK), 0, 1);
        if (k <= 0) {
            FLIP.remove(entity.getId());
            return;
        }
        float half = entity.getBbHeight() / 2;
        float yaw = Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
        com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, half, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180 * smooth(k)));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -half, 0);
        FLIPPED.add(entity.getId());
    }

    public static void onRenderPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (FLIPPED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    static boolean flipped(int id) {
        Long until = FLIP.get(id);
        return until != null && ScreenFx.now() <= until + 1;
    }

    // Drawn after the glow: the orb over the roots of its tails, and the light on top of it.
    static void overlay() {
        for (Orb o : DRAWN) {
            float R = o.drawR;
            float[] seedColor = {0.2f + 0.6f * FxWorld.hash(o.seed, 0, 33), 1, 0.2f + 0.6f * FxWorld.hash(o.seed, 1, 33)};
            // param 0.75: the bright core (the dark one, param 0.25, is kept in the shader).
            FxDraw.billboard(o.at, R * 4, 0, FxDraw.VORBE, 0.75f, seedColor, o.spawn, false);
            FxDraw.billboard(o.at, R * 1.3f, 0, FxDraw.SOFT, 0.5f, PALE, 0.35f * o.spawn, true);
        }
        DRAWN.clear();
    }

    // His fingertip at the end of the outstretched right arm (the held pose has the Red side forward); in first
    // person, low on the right of the view.
    private static Vec3 fingertip(Entity owner, Camera view, Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vector3f look = view.getLookVector();
            Vector3f left = view.getLeftVector();
            Vector3f up = view.getUpVector();
            return camera.add(look.x * 0.85 - left.x * 0.3 - up.x * 0.16, look.y * 0.85 - left.y * 0.3 - up.y * 0.16,
                    look.z * 0.85 - left.z * 0.3 - up.z * 0.16);
        }
        float yaw = (owner instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : owner.getYRot())
                * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // His right, facing forward.
        double rx = -fz, rz = fx;
        double scale = owner.getBbHeight() / 1.8;
        if (flipped(owner.getId())) {
            // Upside down, turned about his middle: his right hand is on the other side, low.
            double half = owner.getBbHeight() / 2;
            return owner.getPosition(partial).add((fx * 0.93 - rx * 0.27) * scale, 2 * half - 1.37 * scale, (fz * 0.93 - rz * 0.27) * scale);
        }
        return owner.getPosition(partial).add((fx * 0.93 + rx * 0.27) * scale, 1.37 * scale, (fz * 0.93 + rz * 0.27) * scale);
    }

    // Everything but the orb itself. R = the orb's radius; grow 0 to 1 = the tails forming; spawn 0 to 1.
    // sparks 0 to 1 = the sparks leaking out (from the start).
    private static void draw(Vector3f at, float R, float age, int seed, float grow, float sparks, float spawn, Vector3f X, Vector3f Y) {
        Vector3f look = at.lengthSquared() > 1.0E-6f ? new Vector3f(at).normalize() : new Vector3f(0, 0, 1);
        // The red haze around it (goes to the bloom).
        float haze = Math.max(sparks * 0.5f, grow);
        FxDraw.billboard(at, R * 18 * (0.4f + 0.6f * haze), 0, FxDraw.SOFT, 0, DEEP, 0.3f * spawn, false);
        FxDraw.billboard(at, R * 10 * (0.4f + 0.6f * haze), 0, FxDraw.SOFT, 0, DEEP, 0.25f * spawn, true);
        FxDraw.billboard(at, R * 6, 0, FxDraw.SOFT, 0.1f, RED, 0.35f * spawn, true);
        // Tails: the body in the alpha layer (strong red, it doesn't blow out white) and a glow copy added on top.
        List<Tail> tails = new ArrayList<>();
        for (int k = 0; k < (grow > 0.001f ? TAILS + THREADS : 0); k++) {
            float f = smooth(Mth.clamp(grow * 1.7f - 0.7f * FxWorld.hash(seed, k, 7), 0, 1));
            if (f <= 0.01f) {
                continue;
            }
            Tail t = tail(at, R, age, seed, k, f, X, Y, look);
            t.o = Math.min(1, f * 2.5f) * spawn;
            tails.add(t);
            // The tip lit while it is still growing.
            if (f < 0.999f) {
                FxDraw.billboard(t.pts[SEGMENTS], R * (t.thin ? 0.6f : 1.1f), 0, FxDraw.SOFT, 0.5f, HOT, 2.4f * f * (1 - f) * spawn, true);
            }
        }
        // A wide faint smear behind each tail (the references' "diffuse").
        for (Tail t : tails) {
            if (!t.thin) {
                float[] c = {0.3f + 0.5f * FxWorld.hash(seed, t.k, 34), 1, 0.3f};
                FxDraw.strip(t.pts, scaled(t.ws, 2.4f), t.us, FxDraw.VRABO, 0.4f, c, 0.3f * t.o, false);
            }
        }
        for (Tail t : tails) {
            FxDraw.strip(t.pts, t.ws, t.us, FxDraw.VRABO, 0.25f, t.color(seed), 0.95f * t.o, false);
        }
        for (Tail t : tails) {
            FxDraw.strip(t.pts, scaled(t.ws, 0.8f), t.us, FxDraw.VRABO, 0.75f, t.color(seed), 0.3f * t.o, true);
        }
        sparks(at, R, age, seed, sparks, spawn, X, Y, look);
        // Red flakes turning along, slowly.
        for (int k = 0; k < FLAKES; k++) {
            float life = 30 + 30 * FxWorld.hash(seed, k, 41);
            float ph = (age + FxWorld.hash(seed, k, 42) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            float a = FxWorld.hash(seed + cyc * 5, k, 43) * Mth.TWO_PI + age * SPIN * 0.6f;
            float rad = R * (2 + 7 * FxWorld.hash(seed + cyc * 5, k, 44)) * (1 - 0.5f * u) * haze;
            Vector3f p = new Vector3f(X).mul(Mth.cos(a) * rad).add(new Vector3f(Y).mul(Mth.sin(a) * rad))
                    .add(new Vector3f(look).mul((FxWorld.hash(seed, k, 45) - 0.5f) * R * 2)).add(at);
            float o = Mth.sin(u * Mth.PI) * spawn;
            float s = R * (0.18f + 0.3f * FxWorld.hash(seed, k, 46));
            FxDraw.billboard(p, s, 0, FxDraw.SOFT, 0.2f, RED, 0.9f * o, false);
            FxDraw.billboard(p, s * 2.2f, 0, FxDraw.SOFT, 0.4f, RED, 0.35f * o, true);
        }
        // The orb's glow under it (the orb is drawn over the glow, in overlay).
        FxDraw.billboard(at, R * 2.6f, 0, FxDraw.SOFT, 0.3f, PALE, 0.45f * spawn, true);
    }

    // Sparks leaking out of it (Stark's reference): curved dotted threads of white-hot dots turning the same way as the
    // tails, curling and fading; each dot blinks. And loose specks crackling around it.
    private static void sparks(Vector3f at, float R, float age, int seed, float grow, float spawn, Vector3f X, Vector3f Y, Vector3f look) {
        int on = Math.round(SPARK_THREADS * grow);
        for (int k = 0; k < on; k++) {
            float life = 16 + 14 * FxWorld.hash(seed, k, 51);
            float ph = (age + FxWorld.hash(seed, k, 52) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            int sd = seed + cyc * 11;
            float a0 = FxWorld.hash(sd, k, 53) * Mth.TWO_PI + age * SPIN * 0.5f;
            float reach = R * Mth.lerp(FxWorld.hash(sd, k, 54), SPARK_REACH_MIN, SPARK_REACH_MAX);
            float curl = 0.8f + 1.6f * FxWorld.hash(sd, k, 55);
            float wig = 0.25f + 0.35f * FxWorld.hash(sd, k, 56);
            float freq = 7 + 6 * FxWorld.hash(sd, k, 57);
            float depth = (FxWorld.hash(sd, k, 58) - 0.5f) * R * 4;
            float head = smooth(u) * 1.15f;
            float fade = Mth.sin(Math.min(1, u * 1.1f) * Mth.PI) * spawn;
            Vector3f last = null;
            for (int j = 0; j < SPARK_DOTS; j++) {
                float s = head - j * 0.04f;
                if (s < 0 || s > 1) {
                    last = null;
                    continue;
                }
                float ang = a0 - curl * s + wig * Mth.sin(s * freq + k);
                float rad = R * 0.9f + reach * s;
                Vector3f p = new Vector3f(X).mul(Mth.cos(ang) * rad).add(new Vector3f(Y).mul(Mth.sin(ang) * rad)).add(new Vector3f(look).mul(depth * s)).add(at);
                float flick = FxWorld.hash(sd + (int) Math.floor(age * 1.5f), k * 40 + j, 59) > 0.3f ? 1 : 0.25f;
                float tail = 1 - j / (float) SPARK_DOTS;
                float sz = R * (0.22f + 0.26f * FxWorld.hash(sd, k * 40 + j, 60)) * (0.55f + 0.45f * tail);
                FxDraw.billboard(p, sz, 0, FxDraw.SOFT, 0.9f, WHITE, fade * flick * tail, true);
                FxDraw.billboard(p, sz * 3.4f, 0, FxDraw.SOFT, 0.1f, RED, 0.7f * fade * flick * tail, false);
                FxDraw.billboard(p, sz * 2.2f, 0, FxDraw.SOFT, 0.3f, RED, 0.35f * fade * flick * tail, true);
                if (last != null) {
                    FxDraw.ribbon(last, p, R * 0.07f, FxDraw.STREAK, 0.85f, PALE, 0.6f * fade * tail, true);
                }
                last = p;
            }
        }
        int loose = Math.round(SPARK_LOOSE * grow);
        for (int k = 0; k < loose; k++) {
            float life = 6 + 8 * FxWorld.hash(seed, k, 61);
            float ph = (age + FxWorld.hash(seed, k, 62) * life) / life;
            float u = ph - (float) Math.floor(ph);
            int sd = seed + (int) Math.floor(ph) * 13;
            float ang = FxWorld.hash(sd, k, 63) * Mth.TWO_PI - u * 0.6f;
            float rad = R * (1.2f + 4 * FxWorld.hash(sd, k, 64)) * (1 + 0.6f * u);
            Vector3f p = new Vector3f(X).mul(Mth.cos(ang) * rad).add(new Vector3f(Y).mul(Mth.sin(ang) * rad))
                    .add(new Vector3f(look).mul((FxWorld.hash(sd, k, 65) - 0.5f) * R * 3)).add(at);
            float o = Mth.sin(u * Mth.PI) * spawn * (FxWorld.hash(sd + (int) Math.floor(age * 2), k, 66) > 0.4f ? 1 : 0.2f);
            float sz = R * (0.16f + 0.2f * FxWorld.hash(sd, k, 67));
            FxDraw.billboard(p, sz, 0, FxDraw.SOFT, 0.9f, WHITE, o, true);
            FxDraw.billboard(p, sz * 3, 0, FxDraw.SOFT, 0.1f, RED, 0.6f * o, false);
        }
    }

    private static final class Tail {
        final Vector3f[] pts = new Vector3f[SEGMENTS + 1];
        final float[] ws = new float[SEGMENTS + 1];
        final float[] us = new float[SEGMENTS + 1];
        int k;
        boolean thin;
        float o;

        float[] color(int seed) {
            return new float[] {0.2f + 0.6f * FxWorld.hash(seed, k, 31), 1, 0.2f + 0.6f * FxWorld.hash(seed, k, 32)};
        }
    }

    // One tail: a spiral out of the orb, pulled back by the spin, with a little depth (Z = towards the camera's look).
    private static Tail tail(Vector3f at, float r, float age, int seed, int k, float grow, Vector3f X, Vector3f Y, Vector3f Z) {
        Tail t = new Tail();
        t.k = k;
        t.thin = k >= TAILS;
        float len = r * Mth.lerp(FxWorld.hash(seed, k, 1), LENGTH_MIN, LENGTH_MAX) * (t.thin ? 1.25f : 1) * grow;
        float spin = SPIN * (0.85f + 0.3f * FxWorld.hash(seed, k, 2));
        float a0 = FxWorld.hash(seed, k, 3) * Mth.TWO_PI + age * spin;
        float curl = Mth.lerp(FxWorld.hash(seed, k, 4), CURL_MIN, CURL_MAX);
        float tilt = (FxWorld.hash(seed, k, 5) - 0.5f) * 1.1f;
        float w0 = r * (t.thin ? THREAD_WIDTH : TAIL_WIDTH) * (0.8f + 0.5f * FxWorld.hash(seed, k, 6));
        for (int j = 0; j <= SEGMENTS; j++) {
            float s = j / (float) SEGMENTS;
            float rad = r * 0.7f + len * s;
            float ang = a0 - curl * (float) Math.pow(s, 1.2) + 0.12f * Mth.sin(age * 0.09f + s * 4 + k * 1.7f);
            float lift = Mth.sin(tilt) * len * s * 0.6f + r * 0.4f * Mth.sin(age * 0.07f + k + s * 3) * s;
            t.pts[j] = new Vector3f(X).mul(Mth.cos(ang) * rad).add(new Vector3f(Y).mul(Mth.sin(ang) * rad)).add(new Vector3f(Z).mul(lift)).add(at);
            t.ws[j] = w0 * (0.3f + 0.7f * (float) Math.pow(1 - s, 0.8)) * (0.25f + 0.75f * grow);
            t.us[j] = s;
        }
        return t;
    }

    private static float[] scaled(float[] ws, float k) {
        float[] out = new float[ws.length];
        for (int i = 0; i < ws.length; i++) {
            out[i] = ws[i] * k;
        }
        return out;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
