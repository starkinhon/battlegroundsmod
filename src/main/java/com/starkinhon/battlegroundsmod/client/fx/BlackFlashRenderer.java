package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Black Flash (power/powers/gojo/AzulFlash), drawn with shader shapes only, the same as the Skill Visualizer's
// "Gojo · Black Flash" scene that Stark approved.
// Slow motion (event 0): black sparks crackle around Gojo's cocked fist, the space around it bends, and the screens
// nearby go dark and blue (ScreenFx grade 10).
// The punch (event 1): the impact frames come from the skill file; then, while the target stays stuck on the fist,
// waves of black lightning burst out of the contact: vivid red glow outside, black inside, bolts in the air with
// branches and bolts that hit the ground, stick to it (a black scorch with a red glow), crawl along it and rise back
// up. The screens nearby go red with black ink blots (ScreenFx grade 11) until a while after the throw.
// The throw (event 2): two last, bigger waves.
// The red glow goes in render() (to the bloom); the black cores go in overlay(), after the glow, so the light doesn't
// wash them out (the same as the hakai's dark arcs).
public final class BlackFlashRenderer {
    private record Slow(int entity, Vec3 contact, long born, int ticks) {
    }

    private record Punch(Vec3 pos, long born, int fit) {
    }

    private record Wave(Vec3 pos, Vector3f dir, long born, float power, int seed, double ground) {
    }

    // The waves after the punch (ticks, the first once the impact frames are over), as in the Visualizer.
    private static final int[] WAVES = {8, 15, 21, 27, 33, 38};
    private static final int WAVE_LIFE = 16;
    private static final double GRADE_RANGE = 28;
    private static final List<Slow> SLOWS = new ArrayList<>();
    private static final List<Punch> PUNCHES = new ArrayList<>();
    private static final List<Wave> WAVE_LIST = new ArrayList<>();
    private static final float[] BLACK = {0, 0, 0};
    private static final float[] RED = FxDraw.rgb(0xFF0018);
    private static final float[] DEEP = FxDraw.rgb(0xB0000E);
    private static final int RED_HEX = 0xFF0018;
    private static int seeds;

    private BlackFlashRenderer() {
    }

    public static void event(int kind, int entity, Vec3 pos, Vec3 dir, float charge, int ticks) {
        long now = ScreenFx.now();
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        float c = Mth.clamp(charge, 0, 1);
        double ground = groundUnder(pos);
        switch (kind) {
            case 0 -> SLOWS.add(new Slow(entity, pos, now, Math.max(1, ticks)));
            case 1 -> {
                SLOWS.removeIf(s -> s.entity() == entity);
                PUNCHES.add(new Punch(pos, now, Math.max(1, ticks)));
                for (int i = 0; i < WAVES.length; i++) {
                    WAVE_LIST.add(new Wave(pos, d.toVector3f(), now + WAVES[i], (0.4f + 0.12f * i) * (0.75f + 0.25f * c), 701 + i * 13 + 977 * ++seeds, ground));
                }
            }
            default -> {
                WAVE_LIST.add(new Wave(pos, d.toVector3f(), now, 1.3f, 991 + 977 * ++seeds, ground));
                WAVE_LIST.add(new Wave(pos.add(d.scale(0.6)), d.toVector3f(), now + 3, 1.0f, 993 + 977 * ++seeds, ground));
            }
        }
    }

    public static boolean active() {
        return !SLOWS.isEmpty() || !PUNCHES.isEmpty() || !WAVE_LIST.isEmpty();
    }

    public static void clear() {
        SLOWS.clear();
        PUNCHES.clear();
        WAVE_LIST.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        SLOWS.removeIf(s -> now - s.born() > s.ticks() + 2);
        PUNCHES.removeIf(p -> now - p.born() > p.fit() + 44);
        WAVE_LIST.removeIf(w -> now - w.born() > WAVE_LIFE);
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player == null ? camera : mc.player.getEyePosition(partial);
        for (Slow s : SLOWS) {
            float age = (float) (now - s.born());
            float k = smooth(age / s.ticks());
            Vec3 fist = fist(s, partial);
            if (fist != null) {
                fistCharge(fist.subtract(camera).toVector3f(), fist, now, k, false);
            }
            ScreenFx.sustainGrade(10, smooth(age / 6f) * near(eye, s.contact()), 0x1A3A8A, s.contact());
        }
        for (Punch p : PUNCHES) {
            float age = (float) (now - p.born());
            if (age < 8) {
                continue;
            }
            float k = age < p.fit() ? 1 : 1 - smooth((age - p.fit()) / 40f);
            ScreenFx.sustainGrade(11, k * near(eye, p.pos()), RED_HEX, p.pos());
        }
        for (Wave w : WAVE_LIST) {
            float age = (float) (now - w.born());
            if (age >= 0) {
                wave(w, camera, age, false);
            }
        }
    }

    // The black cores, over the glow.
    static void overlay(Vec3 camera, double now, float partial) {
        for (Slow s : SLOWS) {
            float age = (float) (now - s.born());
            Vec3 fist = fist(s, partial);
            if (fist != null) {
                fistCharge(fist.subtract(camera).toVector3f(), fist, now, smooth(age / s.ticks()), true);
            }
        }
        for (Wave w : WAVE_LIST) {
            float age = (float) (now - w.born());
            if (age >= 0) {
                wave(w, camera, age, true);
            }
        }
    }

    // How much of the screen grade someone sees: all of it close by, none past GRADE_RANGE.
    private static float near(Vec3 eye, Vec3 at) {
        return 1 - smooth((float) ((eye.distanceTo(at) - GRADE_RANGE * 0.6) / (GRADE_RANGE * 0.4)));
    }

    // Gojo's right fist, cocked back during the slow motion.
    private static Vec3 fist(Slow s, float partial) {
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(s.entity());
        if (!(e instanceof LivingEntity living)) {
            return null;
        }
        float yaw = Mth.lerp(partial, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = living.getBbHeight() / 1.8;
        return living.getPosition(partial).add(forward.scale(-0.12 * scale)).add(right.scale(0.4 * scale)).add(0, 1.2 * scale, 0);
    }

    // Black sparks crackling around the fist while it cocks back (k 0 to 1).
    private static void fistCharge(Vector3f f, Vec3 world, double now, float k, boolean dark) {
        if (k <= 0.01f) {
            return;
        }
        int ep = (int) Math.floor(now / 2);
        int n = 2 + Math.round(7 * k);
        for (int i = 0; i < n; i++) {
            int s = ep * 37 + i * 11;
            Vector3f d = FxWorld.sphere(s, i);
            float len = 0.25f + 0.55f * k * FxWorld.hash(s, i, 2);
            Vector3f[] pts = jag(f, new Vector3f(d).mul(len).add(f), 4, 0.3f, s);
            line(pts, 0.018f + 0.02f * k, k * (FxWorld.hash(s, i, 3) > 0.25f ? 1 : 0.3f), 0.7f, dark);
        }
        if (dark) {
            FxDraw.billboard(f, 0.18f + 0.12f * k, 0, FxDraw.SOFT, 0.95f, BLACK, 0.8f * k, false);
        } else {
            FxDraw.billboard(f, 0.8f + 1.6f * k, (float) now * 0.1f, FxDraw.SOFT, 0.3f, RED, 0.55f * k, true);
            if (k > 0.3f) {
                ScreenFx.holdWarp(world, 0.9f, 0.35f * k, 2, new Vec3(0, 1, 0), (float) (now / 20));
            }
        }
    }

    // One wave of black lightning out of the contact: bolts in the air (more along the punch) with branches, and bolts
    // that hit the ground, stick, crawl and rise back up. Grows in ~4 ticks, crackles every 2, gone in ~16.
    private static void wave(Wave w, Vec3 camera, float age, boolean dark) {
        if (age > WAVE_LIFE) {
            return;
        }
        float p = w.power();
        Vector3f at = w.pos().subtract(camera).toVector3f();
        float grow = FxWorld.easeOut(age / 4);
        float fade = age < 6 ? 1 : 1 - smooth((age - 6) / 10);
        int ep = (int) Math.floor(age / 2);
        int seed = w.seed();
        int nAir = Math.round(10 + 8 * p);
        for (int i = 0; i < nAir; i++) {
            int s = seed * 131 + ep * 17 + i;
            if (FxWorld.hash(s, i, 9) > 0.85f) {
                continue;
            }
            Vector3f d = FxWorld.sphere(seed, i);
            if (FxWorld.hash(seed, i, 3) < 0.6f) {
                d.add(new Vector3f(w.dir()).mul(1.4f));
            }
            d.normalize();
            if (d.y < -0.3f) {
                d.y = -0.3f;
                d.normalize();
            }
            float len = (2.2f + 3.4f * FxWorld.hash(seed, i, 4)) * (0.7f + 0.5f * p);
            Vector3f[] pts = jag(at, new Vector3f(d).mul(len).add(at), 9, 0.16f, s);
            line(reveal(pts, grow), 0.05f + 0.04f * p, fade, 1, dark);
            if (FxWorld.hash(seed, i, 5) < 0.7f) {
                Vector3f m = pts[3 + (int) (FxWorld.hash(s, i, 6) * 3)];
                Vector3f bd = new Vector3f(d).add(FxWorld.sphere(s, 2).mul(0.9f)).normalize();
                line(reveal(jag(m, new Vector3f(bd).mul(len * 0.45f).add(m), 5, 0.2f, s + 7), grow), 0.03f + 0.02f * p, fade * 0.85f, 1, dark);
            }
        }
        // The ones that hit the ground: down, stick, crawl, up.
        float ground = (float) (w.ground() - camera.y) + 0.02f;
        Vector3f fwd = new Vector3f(w.dir().x, 0, w.dir().z);
        if (fwd.lengthSquared() < 1.0E-4f) {
            fwd.set(0, 0, 1);
        }
        fwd.normalize();
        Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
        int nG = Math.round(4 + 4 * p);
        for (int i = 0; i < nG; i++) {
            int s = seed * 977 + ep * 29 + i;
            float a = (FxWorld.hash(seed, i, 21) - 0.5f) * Mth.PI * 1.6f;
            float r1 = 1.6f + 2.2f * FxWorld.hash(seed, i, 22);
            Vector3f dir = new Vector3f(right).mul(Mth.sin(a)).add(new Vector3f(fwd).mul(Mth.cos(a)));
            Vector3f g1 = new Vector3f(dir).mul(r1).add(at.x, 0, at.z);
            g1.y = ground;
            Vector3f g2 = new Vector3f(dir).mul(1.8f + 2.6f * FxWorld.hash(seed, i, 23)).add(g1).add(new Vector3f(right).mul((FxWorld.hash(seed, i, 24) - 0.5f) * 1.4f));
            g2.y = ground;
            Vector3f up = new Vector3f(dir).mul(0.8f + FxWorld.hash(seed, i, 25)).add(g2).add(0, 1.8f + 2.4f * FxWorld.hash(seed, i, 26), 0);
            Vector3f[] down = jag(at, g1, 6, 0.12f, s);
            Vector3f[] crawl = jag(g1, g2, 8, 0.1f, s + 3);
            for (Vector3f c : crawl) {
                c.y = ground + 0.03f * FxWorld.hash(s, Math.round(c.x * 10), 4);
            }
            Vector3f[] rise = jag(g2, up, 6, 0.18f, s + 5);
            Vector3f[] all = new Vector3f[down.length + crawl.length - 1 + rise.length - 1];
            System.arraycopy(down, 0, all, 0, down.length);
            System.arraycopy(crawl, 1, all, down.length, crawl.length - 1);
            System.arraycopy(rise, 1, all, down.length + crawl.length - 1, rise.length - 1);
            line(reveal(all, FxWorld.easeOut(age / 6)), 0.045f + 0.03f * p, fade, 1, dark);
            // Where it stuck to the ground: a black scorch under a red glow.
            float stick = smooth(age / 3) * fade;
            Vector3f n = new Vector3f(0, 1, 0);
            if (dark) {
                FxDraw.disk(new Vector3f(g1.x, ground - 0.008f, g1.z), n, 1.2f + 0.6f * p, FxDraw.SOFT, 0.9f, BLACK, 0.8f * stick, false);
                FxDraw.disk(new Vector3f(g2.x, ground - 0.008f, g2.z), n, 0.9f, FxDraw.SOFT, 0.9f, BLACK, 0.6f * stick, false);
            } else {
                FxDraw.disk(new Vector3f(g1.x, ground + 0.01f, g1.z), n, 2.4f, FxDraw.SOFT, 0.2f, RED, 0.6f * stick, true);
            }
        }
        // The core: a black hole on the fist with the red bursting around it, and the space bending.
        float core = age < 8 ? 1 : 1 - smooth((age - 8) / 8);
        Vector3f toCam = new Vector3f(at).negate();
        float ring = (0.8f + 3.2f * FxWorld.easeOut(age / 10)) * (0.7f + 0.5f * p);
        if (dark) {
            FxDraw.billboard(at, (0.7f + 0.5f * p) * (1 - 0.4f * age / WAVE_LIFE), age * 0.3f, FxDraw.SOFT, 0.97f, BLACK, core, false);
            FxDraw.disk(at, toCam, ring, FxDraw.RING, 0.07f, BLACK, 0.9f * (1 - age / WAVE_LIFE), false);
        } else {
            FxDraw.billboard(at, (2.2f + 2 * p) * grow, seed + age * 0.04f, FxDraw.FLARE, 0.4f, RED, 0.85f * core, true);
            FxDraw.billboard(at, (3.5f + 2 * p) * grow, 0, FxDraw.SOFT, 0.25f, DEEP, 0.35f * core, true);
            FxDraw.disk(at, toCam, ring * 1.06f, FxDraw.RING, 0.12f, RED, 0.6f * (1 - age / WAVE_LIFE), true);
            if (age < 10) {
                ScreenFx.holdWarp(w.pos(), 3 + 2 * p, 1.3f * (1 - age / 10), 2, new Vec3(w.dir().x, w.dir().y, w.dir().z), age / 20);
            }
        }
    }

    // A broken bolt through the points: vivid red glow (to the bloom), or (dark) the black core over it.
    private static void line(Vector3f[] pts, float w, float op, float glowK, boolean dark) {
        if (op <= 0.01f) {
            return;
        }
        for (int i = 1; i < pts.length; i++) {
            float f = i / (float) (pts.length - 1);
            float ww = w * (1.15f - 0.7f * f);
            float o = op * (1 - 0.4f * f);
            if (dark) {
                FxDraw.ribbon(pts[i - 1], pts[i], ww * 1.7f, FxDraw.BEAM, 0, BLACK, Math.min(1, o * 1.25f), false);
            } else {
                FxDraw.ribbon(pts[i - 1], pts[i], ww * 5 * glowK, FxDraw.BEAM, 0.15f, DEEP, o * 0.45f, true);
                FxDraw.ribbon(pts[i - 1], pts[i], ww * 2.8f * glowK, FxDraw.BEAM, 0.5f, RED, o * 0.85f, true);
            }
        }
    }

    // The path a -> b broken into n crooked pieces (the crooks change with the seed).
    private static Vector3f[] jag(Vector3f a, Vector3f b, int n, float jag, int seed) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        Vector3f dn = len < 1.0E-5f ? new Vector3f(0, 1, 0) : new Vector3f(d).div(len);
        Vector3f s1 = new Vector3f(dn).cross(Math.abs(dn.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f s2 = new Vector3f(dn).cross(s1);
        Vector3f[] out = new Vector3f[n + 1];
        out[0] = new Vector3f(a);
        for (int i = 1; i < n; i++) {
            float f = i / (float) n;
            float j = jag * len * (float) Math.pow(Mth.sin(f * Mth.PI), 0.6);
            out[i] = new Vector3f(d).mul(f).add(a)
                    .add(new Vector3f(s1).mul((FxWorld.hash(seed, i, 1) * 2 - 1) * j))
                    .add(new Vector3f(s2).mul((FxWorld.hash(seed, i, 2) * 2 - 1) * j));
        }
        out[n] = new Vector3f(b);
        return out;
    }

    // The points cut at k (0 to 1) of the length: the bolt grows from its start to its end.
    private static Vector3f[] reveal(Vector3f[] pts, float k) {
        if (k >= 1) {
            return pts;
        }
        float total = 0;
        float[] seg = new float[pts.length - 1];
        for (int i = 1; i < pts.length; i++) {
            seg[i - 1] = pts[i].distance(pts[i - 1]);
            total += seg[i - 1];
        }
        float left = total * Math.max(0, k);
        List<Vector3f> out = new ArrayList<>();
        out.add(pts[0]);
        for (int i = 1; i < pts.length; i++) {
            if (left >= seg[i - 1]) {
                out.add(pts[i]);
                left -= seg[i - 1];
            } else {
                out.add(new Vector3f(pts[i - 1]).lerp(pts[i], seg[i - 1] < 1.0E-5f ? 0 : left / seg[i - 1]));
                break;
            }
        }
        return out.toArray(new Vector3f[0]);
    }

    // The top of the ground under the point (up to 6 blocks down), or a little under the point in the air.
    private static double groundUnder(Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return at.y - 1.3;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1;
            }
            pos.move(0, -1, 0);
        }
        return at.y - 1.3;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
