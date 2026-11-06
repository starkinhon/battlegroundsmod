package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Gojo's Purple (power/powers/gojo/Roxo), drawn the same way as the Skill Visualizer's drawRoxo (aba "Gojo · Roxo", v22,
// the version Stark approved). Forming: the Red (his right) and the Blue (his left) appear behind him at the height of
// his chest, come round to the front and press together; the body of both and the purple born between them is one quad
// (souls_vfx shape 21) drawn over the glow, with living bolts round it (shape 24), glitter, wind while they move.
// Formed, it grows while held. Launched: light leaking out of it in spinning rays (shape 23), the space twisting round
// it (shape 22 and the warp's whirl), a white flash ahead, a cape of energy streaming back, speed lines, rings of
// pressure, dust lifted off the ground and purple dust where it erased things. The Unlimited Purple: torn blue and red
// rings closing in, a Purple with no contour growing fast to radius 13, then the blast out to 100 blocks.
// The server sends the forming one every tick with its tick (this side runs the timeline and places it on his body
// itself, so it never lags); the launched one and the Unlimited one come in one message each.
public final class RoxoRenderer {
    private static final float[] RED = FxDraw.rgb(0xE01030);
    private static final float[] BLUE = FxDraw.rgb(0x2A60FF);
    private static final float[] PURPLE = FxDraw.rgb(0x8A2BE2);
    private static final float[] MAGENTA = FxDraw.rgb(0xFF4FF0);
    private static final float[] LILAC = FxDraw.rgb(0xE6B0FF);
    private static final float[] PALE = FxDraw.rgb(0xFFE6FF);
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BEIGE = FxDraw.rgb(0xEADBC4);
    private static final float[] DUST = FxDraw.rgb(0xC9B79A);
    private static final float[] VORTEX = {0.6f, 0.5f, 0.7f};

    // The Visualizer's ROXO (gojo.roxo.json).
    private static final float RAIO = 0.45f;
    private static final float SURGIR = 10;
    private static final float VIR = 30;
    private static final float MISTURAR = 60;
    private static final float ESCALA_MAX = 4;
    private static final float S1 = SURGIR + VIR;
    private static final float S2 = S1 + MISTURAR;
    // Cut short, it fades in this long.
    private static final float FADE = 12;
    // The Unlimited Purple fades for this long after its blast.
    private static final float IRR_FADE = 50;

    private static final class Orb {
        final int owner;
        int tick;
        long received;
        float prevCharge, charge;
        long endAt = -1;

        Orb(int owner) {
            this.owner = owner;
        }
    }

    private record Shot(int id, int owner, Vec3 from, Vec3 dir, float v0, float accel, float vMax, float radius, float range, long born) {
        // Blocks flown after t ticks (the server moves it the same way).
        float flown(float t) {
            float s = 0;
            float v = v0;
            int whole = (int) Math.floor(Math.max(0, t));
            for (int i = 0; i < whole && s < range; i++) {
                s += v;
                v = Math.min(vMax, v + accel);
            }
            s += v * (t - whole);
            return Math.min(range, s);
        }

        float speedAt(float t) {
            return Math.min(vMax, v0 + accel * Math.max(0, t));
        }

        // Ticks until it reaches the end.
        float end() {
            float s = 0;
            float v = v0;
            int n = 0;
            while (s < range && n < 2000) {
                s += v;
                v = Math.min(vMax, v + accel);
                n++;
            }
            return n;
        }
    }

    private record Irr(Vec3 center, float raio, float reach, float azulR, float implode, float grow, float pause, float blast, long born) {
        float bornAt() {
            return implode;
        }

        float big() {
            return implode + grow;
        }

        float boom() {
            return implode + grow + pause;
        }

        float radiusAt(float t) {
            return t < bornAt() ? 0 : t < big() ? Mth.lerp(smooth((t - bornAt()) / grow), 0.6f, raio) : raio;
        }

        float blastAt(float t) {
            return t < boom() ? 0 : reach * easeOut((t - boom()) / blast);
        }
    }

    // The quads of the Purple's body, worked out in render, drawn by overlay over the glow.
    private record Body(Vector3f a, Vector3f b, float r, float m, int seed, float opacity, boolean noRim) {
    }

    private static final Map<Integer, Orb> ORBS = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<Irr> IRRS = new ArrayList<>();
    private static final List<Body> BODIES = new ArrayList<>();
    private static Vector3f left = new Vector3f(1, 0, 0);
    private static Vector3f up = new Vector3f(0, 1, 0);
    private static Vector3f look = new Vector3f(0, 0, 1);

    private RoxoRenderer() {
    }

    public static void orb(int id, int owner, int mode, int tick, float charge) {
        long now = ScreenFx.now();
        Orb o = ORBS.get(id);
        if (o == null) {
            if (mode != 0) {
                return;
            }
            o = new Orb(owner);
            o.prevCharge = o.charge = charge;
            ORBS.put(id, o);
        }
        if (mode != 0) {
            if (o.endAt < 0) {
                o.endAt = now;
            }
            return;
        }
        o.tick = tick;
        o.received = now;
        o.prevCharge = o.charge;
        o.charge = charge;
    }

    public static void shot(int id, int owner, Vec3 from, Vec3 dir, Vec3 motion, Vec3 size) {
        ORBS.remove(id);
        SHOTS.add(new Shot(id, owner, from, dir.normalize(), (float) motion.x, (float) motion.y, (float) motion.z, (float) size.x,
                (float) size.y, ScreenFx.now()));
    }

    public static void irrestrito(Vec3 center, Vec3 size, Vec3 timing, int blast) {
        IRRS.add(new Irr(center, (float) size.x, (float) size.y, (float) size.z, (float) timing.x, (float) timing.y, (float) timing.z,
                Math.max(1, blast), ScreenFx.now()));
    }

    public static boolean active() {
        return !ORBS.isEmpty() || !SHOTS.isEmpty() || !IRRS.isEmpty();
    }

    public static void clear() {
        ORBS.clear();
        SHOTS.clear();
        IRRS.clear();
        BODIES.clear();
    }

    static void render(Vec3 camera, double now, float partial) {
        BODIES.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        Camera view = mc.gameRenderer.getMainCamera();
        left = new Vector3f(view.getLeftVector());
        up = new Vector3f(view.getUpVector());
        look = new Vector3f(view.getLookVector());
        // The server stopped talking about it (out of range, say): it fades like when cut short.
        for (Orb o : ORBS.values()) {
            if (o.endAt < 0 && now - o.received > 6) {
                o.endAt = o.received;
            }
        }
        ORBS.values().removeIf(o -> o.endAt >= 0 && now - o.endAt > FADE);
        SHOTS.removeIf(s -> now - s.born() > s.end() + 60);
        IRRS.removeIf(i -> now - i.born() > i.boom() + i.blast() + IRR_FADE + 4);
        for (Map.Entry<Integer, Orb> entry : ORBS.entrySet()) {
            Orb o = entry.getValue();
            Entity owner = mc.level.getEntity(o.owner);
            if (owner != null) {
                drawHeld(o, entry.getKey(), owner, camera, now, partial);
            }
        }
        for (Shot s : SHOTS) {
            drawShot(s, mc, camera, now, partial);
        }
        for (Irr i : IRRS) {
            drawIrr(i, mc, camera, (float) (now - i.born()));
        }
    }

    // Drawn after the glow: the body of each Purple (its light already went to the bloom under it).
    static void overlay() {
        for (Body b : BODIES) {
            Vector3f mid = new Vector3f(b.a()).add(b.b()).mul(0.5f);
            // Pulled forward to the front of the sphere (facing the camera), so the ground only hides what it really would.
            if (mid.lengthSquared() > 1.0E-6f) {
                mid.sub(new Vector3f(mid).normalize(b.r() * 0.95f));
            }
            Vector3f d = new Vector3f(b.b()).sub(b.a());
            Vector3f right = new Vector3f(left).negate();
            float ax = right.dot(d);
            float ay = up.dot(d);
            float dl = (float) Math.hypot(ax, ay);
            float ux = dl < 1.0E-5f ? 1 : ax / dl;
            float uy = dl < 1.0E-5f ? 0 : ay / dl;
            float k = Math.min(8, dl / b.r());
            Vector3f x = new Vector3f(right).mul(ux).add(new Vector3f(up).mul(uy));
            Vector3f y = new Vector3f(right).mul(-uy).add(new Vector3f(up).mul(ux));
            float hl = (k + 6) * b.r() / 2;
            float hh = 3 * b.r();
            float[] c = {0.2f + 0.6f * FxWorld.hash(b.seed(), 0, 71), Mth.clamp(b.m(), 0, 1), b.noRim() ? 1 : 0.2f + 0.6f * FxWorld.hash(b.seed(), 1, 71)};
            FxDraw.quad(corner(mid, x, y, -hl, -hh), corner(mid, x, y, hl, -hh), corner(mid, x, y, hl, hh), corner(mid, x, y, -hl, hh),
                    FxDraw.ROXO, k / 8, c, b.opacity(), false);
        }
        BODIES.clear();
    }

    private static Vector3f corner(Vector3f mid, Vector3f x, Vector3f y, float sx, float sy) {
        return new Vector3f(mid).add(new Vector3f(x).mul(sx)).add(new Vector3f(y).mul(sy));
    }

    // ---- held: forming, then growing ----

    private static void drawHeld(Orb o, int id, Entity owner, Vec3 camera, double now, float partial) {
        float t = (float) Math.min(o.tick + (now - o.received), o.tick + 2.0);
        float spawn = smooth(t / SURGIR);
        if (o.endAt >= 0) {
            spawn *= 1 - smooth((float) (now - o.endAt) / FADE);
        }
        int seed = 5150 + id * 31;
        if (t < S2) {
            float m = smooth((t - S1 - 8) / (MISTURAR - 2));
            Vec3 a = ball(owner, partial, t, -1);
            Vec3 b = ball(owner, partial, t, 1);
            // Each sphere's own way (they come round on opposite sides), so each one's wind streams off its own back.
            Vec3 stepA = a.subtract(ball(owner, partial, Math.max(0, t - 1), -1));
            Vec3 stepB = b.subtract(ball(owner, partial, Math.max(0, t - 1), 1));
            drawRoxo(rel(a, camera), rel(b, camera), RAIO, t, seed, m, spawn, stepA.lengthSqr() > 0.0004 ? stepA.scale(8) : null,
                    stepB.lengthSqr() > 0.0004 ? stepB.scale(8) : null, false, false, null);
        } else {
            float c = o.endAt >= 0 ? o.charge : Mth.lerp(partial, o.prevCharge, o.charge);
            float r = RAIO * 1.1f * (1 + (ESCALA_MAX - 1) * smooth(c));
            Vector3f at = rel(heldAt(owner, partial, r), camera);
            drawRoxo(at, at, r, t, seed, 1, spawn, null, null, false, false, null);
        }
    }

    // Where each sphere is t ticks after he pressed C (side -1 the Red on his right, 1 the Blue on his left).
    private static Vec3 ball(Entity owner, float partial, float t, int side) {
        if (t < SURGIR) {
            return local(owner, partial, side * 0.95, 1.2, -0.85);
        }
        if (t < S1) {
            float u = smooth((t - SURGIR) / VIR);
            double x = bez(side * 0.95, side * 1.3, side * 0.68, u);
            double y = bez(1.2, 1.25, 1.3, u);
            double z = bez(-0.85, 0.45, 1.35, u);
            return local(owner, partial, x, y, z);
        }
        return local(owner, partial, side * 0.68 * (1 - smooth((t - S1) / MISTURAR)), 1.3, 1.35);
    }

    private static Vec3 heldAt(Entity owner, float partial, float r) {
        float grow = r - RAIO * 1.1f;
        float z = 1.35f + grow * 1.1f;
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            // In first person a little further out, so the grown one doesn't swallow the view.
            z += grow * 0.8f;
        }
        return local(owner, partial, 0, Math.max(1.3, r + 0.2), z);
    }

    private static double bez(double a, double b, double c, double u) {
        return a * (1 - u) * (1 - u) + b * 2 * u * (1 - u) + c * u * u;
    }

    // A point in his own space: x to his left, y up, z forward (the Visualizer's Gojo, who faces +z), scaled with him.
    private static Vec3 local(Entity owner, float partial, double x, double y, double z) {
        float yaw = owner.getViewYRot(partial) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double rx = -fz;
        double rz = fx;
        double scale = owner.getBbHeight() / 1.8;
        return owner.getPosition(partial).add((fx * z - rx * x) * scale, y * scale, (fz * z - rz * x) * scale);
    }

    // ---- launched ----

    private static void drawShot(Shot s, Minecraft mc, Vec3 camera, double now, float partial) {
        float age = (float) (now - s.born());
        float end = s.end();
        float fade = 1 - smooth((age - end) / 6);
        int seed = 5150 + s.id() * 31;
        float R = s.radius();
        Vector3f fwd = s.dir().toVector3f();
        Vector3f sx = new Vector3f(fwd).cross(Math.abs(fwd.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f sy = new Vector3f(sx).cross(fwd).normalize();
        float flown = s.flown(Math.min(age, end));
        Vec3 atWorld = s.from().add(s.dir().scale(flown));
        Vector3f at = rel(atWorld, camera);
        if (fade > 0) {
            float back = Math.min(12, flown);
            FxDraw.ribbon(new Vector3f(fwd).mul(-back).add(at), at, R * 1.7f, FxDraw.BEAM, 0.1f, PURPLE, 0.3f * fade, true);
            FxDraw.ribbon(new Vector3f(fwd).mul(-back * 0.6f).add(at), at, R * 0.4f, FxDraw.BEAM, 0.7f, PALE, 0.35f * fade, true);
            Vec3 step = s.dir().scale(age < end ? s.speedAt(age) : 0);
            drawRoxo(at, at, R, age + S2, seed, 1, fade, step, null, true, false, atWorld);
            // Light leaking out of it (Stark's reference): wide rays in its color, spinning fast, two layers turning
            // opposite ways.
            FxDraw.billboard(at, R * 13, age * 0.42f, FxDraw.ROXO_RAYS, 0, PURPLE, 0.85f * fade, true);
            FxDraw.billboard(at, R * 9, -age * 0.6f + 1.3f, FxDraw.ROXO_RAYS, 0.5f, LILAC, 0.55f * fade, true);
            // The launch of the reference (the ball never deforms): a white flash ahead, a cape of energy streaming back,
            // purple flames waving behind and speed lines going past.
            Vector3f ahead = new Vector3f(fwd).mul(R * 0.85f).add(at);
            FxDraw.billboard(ahead, R * 3.2f, 0, FxDraw.SOFT, 0.85f, WHITE, 0.8f * fade, true);
            FxDraw.ribbon(new Vector3f(sx).mul(-R * 4).add(ahead), new Vector3f(sx).mul(R * 4).add(ahead), R * 0.25f, FxDraw.BEAM, 0.8f, PALE,
                    0.6f * fade, true);
            for (int c = 0; c < 12; c++) {
                float phi = c / 12f * Mth.TWO_PI + age * 0.05f + FxWorld.hash(c, 3, 41) * 0.4f;
                Vector3f rd = new Vector3f(sx).mul(Mth.cos(phi)).add(new Vector3f(sy).mul(Mth.sin(phi)));
                int n = 10;
                float len = R * (2.6f + 1.6f * FxWorld.hash(c, 1, 42));
                float wob = R * 0.12f;
                Vector3f[] pts = new Vector3f[n + 1];
                float[] ws = new float[n + 1];
                float[] us = new float[n + 1];
                for (int j = 0; j <= n; j++) {
                    float t = j / (float) n;
                    pts[j] = new Vector3f(fwd).mul(R * 0.75f - (R * 0.75f + len) * t).add(at)
                            .add(new Vector3f(rd).mul(R * (0.55f + 0.75f * (float) Math.pow(t, 0.45)) + wob * Mth.sin(t * 6 - age * 0.9f + c)));
                    ws[j] = R * (0.35f + 0.5f * t);
                    us[j] = t;
                }
                FxDraw.strip(pts, ws, us, FxDraw.STREAK, 0.4f, c % 3 != 0 ? PURPLE : LILAC, 0.32f * fade, true);
            }
            for (int c = 0; c < 6; c++) {
                float phi = FxWorld.hash(c, 5, 43) * Mth.TWO_PI;
                Vector3f rd = new Vector3f(sx).mul(Mth.cos(phi)).add(new Vector3f(sy).mul(Mth.sin(phi)));
                int n = 12;
                float len = R * (3 + 3 * FxWorld.hash(c, 6, 44));
                Vector3f[] pts = new Vector3f[n + 1];
                float[] ws = new float[n + 1];
                float[] us = new float[n + 1];
                for (int j = 0; j <= n; j++) {
                    float t = j / (float) n;
                    pts[j] = new Vector3f(fwd).mul(-R * 0.6f - len * t).add(at)
                            .add(new Vector3f(rd).mul(R * (0.7f + 0.6f * t) + R * 0.35f * Mth.sin(t * 7 - age * 1.3f + c * 2) * t));
                    ws[j] = R * 0.45f * (1 - t) + 0.05f;
                    us[j] = t;
                }
                FxDraw.strip(pts, ws, us, FxDraw.STREAK, 0.6f, MAGENTA, 0.35f * fade, true);
            }
            for (int c = 0; c < 18; c++) {
                float life = 4 + 3 * FxWorld.hash(c, 7, 45);
                float ph = age / life + FxWorld.hash(c, 8, 46);
                int cyc = (int) Math.floor(ph);
                float u = ph - cyc;
                float phi = FxWorld.hash(c + cyc * 13, 9, 47) * Mth.TWO_PI;
                float rr = R * (1.2f + 2.2f * FxWorld.hash(c + cyc * 13, 10, 48));
                Vector3f base = new Vector3f(sx).mul(Mth.cos(phi) * rr).add(new Vector3f(sy).mul(Mth.sin(phi) * rr)).add(new Vector3f(fwd).mul(R * (1 - 3 * u)))
                        .add(at);
                Vector3f tail = new Vector3f(fwd).mul(-(4 + 5 * FxWorld.hash(c, 11, 49))).add(base);
                FxDraw.ribbon(tail, base, 0.06f + 0.05f * FxWorld.hash(c, 12, 50), FxDraw.STREAK, 0.9f, c % 3 != 0 ? BEIGE : WHITE,
                        Mth.sin(u * Mth.PI) * 0.55f * fade, true);
            }
            // Rings of pressure around it in flight, one every 7 ticks.
            for (int w = 0; w < 3; w++) {
                float ph = (age / 7 + w / 3f) % 1;
                Vector3f wc = new Vector3f(fwd).mul(-ph * R * 3).add(at);
                FxDraw.disk(wc, fwd, R * (2.4f + 2.6f * easeOut(ph)), FxDraw.RING, 0.05f, PALE, 0.4f * (1 - ph) * fade, true);
            }
        }
        // His hand lit purple just after the launch.
        Entity owner = mc.level.getEntity(s.owner());
        float hl = 1 - smooth((age - 6) / 20);
        if (owner != null && hl > 0) {
            Vector3f hand = rel(local(owner, partial, -0.3, 1.4, 0.95), camera);
            FxDraw.billboard(hand, 1.4f, 0, FxDraw.SOFT, 0.2f, PURPLE, 0.6f * hl, true);
            FxDraw.billboard(hand, 0.4f, 0, FxDraw.SOFT, 0.9f, WHITE, 0.8f * hl, true);
        }
        // Dust lifted off the ground where it passed low, on both sides; purple dust and bubbles where it erased.
        Vector3f flat = new Vector3f(-fwd.z, 0, fwd.x);
        if (flat.lengthSquared() < 1.0E-4f) {
            flat.set(1, 0, 0);
        }
        flat.normalize();
        int ticks = (int) Math.ceil(end);
        for (int j = 0; j < ticks; j += 3) {
            float a = age - j;
            if (a < 0 || a > 50) {
                continue;
            }
            Vec3 f = s.from().add(s.dir().scale(s.flown(j)));
            float t = a / 50;
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(f.x), Mth.floor(f.z));
            if (f.y - ground > R + 3 || f.y < ground - R) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                Vector3f p = rel(new Vec3(f.x, ground + 0.4 + 0.8 * t, f.z), camera).add(new Vector3f(flat).mul(side * (R * 0.9f + 1.6f * easeOut(t))));
                FxDraw.billboard(p, (1.2f + 2.2f * easeOut(t)) * (0.7f + 0.3f * FxWorld.hash(j, side + 2, 7)), FxWorld.hash(j, side, 8) * 6 + a * 0.02f,
                        FxDraw.SMOKE, t, DUST, 0.6f * (1 - t), false);
            }
        }
        for (int j = 0; j < ticks; j++) {
            float a = age - j;
            if (a < 0 || a > 40) {
                continue;
            }
            Vec3 f = s.from().add(s.dir().scale(s.flown(j)));
            Vector3f p = rel(f, camera).add(sphereDir(seed + j, 3).mul(R * 0.8f));
            dust(p, a, seed + j * 7, 1);
        }
    }

    // ---- the Unlimited Purple ----

    private static void drawIrr(Irr i, Minecraft mc, Vec3 camera, float age) {
        Vector3f a = rel(i.center(), camera);
        // The implosion: torn blue and red rings turning and closing in on the middle, the Blue going out, all pulled in.
        if (age < i.bornAt() + 4) {
            float u = age / i.implode();
            float op = 1 - smooth((u - 0.9f) / 0.2f);
            tornRing(a, Mth.lerp(smooth(u), 9, 0.3f), age * 0.3f, BLUE, RED, op, 91, 11);
            tornRing(a, Mth.lerp(smooth(u), 6.5f, 0.2f), -age * 0.4f, RED, BLUE, op, 93, 9);
            FxDraw.billboard(a, i.azulR() * 3 * (1 - smooth(u)), 0, FxDraw.SOFT, 0.5f, BLUE, 0.6f * (1 - smooth(u)), true);
            FxDraw.billboard(a, Mth.lerp(Math.min(1, u), 1, 6), 0, FxDraw.SOFT, 0.9f, WHITE, smooth(u), true);
            ScreenFx.holdWarp(i.center(), 14, 0.9f, 2, new Vec3(0, 1, 0), age / 20);
        }
        // The Purple born and growing fast (no contour: only the purple with its glow, the bolts and the distortion).
        float r = i.radiusAt(age);
        float boomAge = age - i.boom();
        if (r > 0 && boomAge < 4) {
            drawRoxo(a, a, r, age, 3011, 1, 1, null, null, true, true, i.center());
            bolts(a, r, age, 3013, 0.9f, 10);
        }
        if (boomAge < 0) {
            return;
        }
        // The blast: a purple dome out to the edge and a wall of dust running over the ground.
        float b = i.blastAt(age);
        float fade = 1 - smooth((boomAge - i.blast()) / IRR_FADE);
        if (fade <= 0) {
            return;
        }
        float big = Math.max(r, b);
        float dist = a.length();
        if (dist < big * 1.05f) {
            // Inside it: the whole view tinted purple, fading as it spreads past.
            float k = fade * (1 - 0.6f * smooth(b / i.reach()));
            FxDraw.billboard(new Vector3f(look).mul(0.6f), 4, 0, FxDraw.SOLID, 0, PURPLE, 0.32f * k, false);
            FxDraw.billboard(new Vector3f(look).mul(0.6f), 4, 0, FxDraw.SOLID, 0, LILAC, 0.18f * k, true);
        } else {
            drawRoxo(a, a, big, age, 3017, 1, fade, null, null, false, true, i.center());
        }
        FxDraw.billboard(a, b * 2.6f, 0, FxDraw.SOFT, 0, PURPLE, 0.35f * fade, true);
        float[] wall = FxDraw.mix(DUST, PURPLE, 0.35f);
        for (int k = 0; k < 48; k++) {
            float ang = k / 48f * Mth.TWO_PI + FxWorld.hash(k, 1, 51) * 0.1f;
            float rr = b * (0.92f + 0.08f * FxWorld.hash(k, 2, 52));
            double x = i.center().x + Mth.cos(ang) * rr;
            double z = i.center().z + Mth.sin(ang) * rr;
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            Vector3f p = rel(new Vec3(x, ground + 3 + 6 * FxWorld.hash(k, 3, 53), z), camera);
            FxDraw.billboard(p, 10 + 12 * FxWorld.hash(k, 4, 54), FxWorld.hash(k, 5, 55) * 6, FxDraw.SMOKE, 0.3f, wall,
                    0.55f * fade * smooth(boomAge / 4), false);
        }
    }

    // A ring in pieces (the Stark's gif), facing the camera, in one color with the edge of another, turning.
    private static void tornRing(Vector3f at, float rad, float rot, float[] col, float[] edge, float op, int seed, int pieces) {
        if (op <= 0.01f || rad < 0.02f) {
            return;
        }
        int n = 10;
        for (int i = 0; i < pieces; i++) {
            float a0 = rot + (i + 0.15f * FxWorld.hash(seed, i, 1)) / pieces * Mth.TWO_PI;
            float span = Mth.TWO_PI / pieces * (0.6f + 0.3f * FxWorld.hash(seed, i, 2));
            Vector3f[] pts = new Vector3f[n + 1];
            Vector3f[] front = new Vector3f[n + 1];
            float[] us = new float[n + 1];
            float[] glow = new float[n + 1];
            float[] rim = new float[n + 1];
            float[] body = new float[n + 1];
            float w = rad * (0.06f + 0.05f * FxWorld.hash(seed, i, 3));
            for (int j = 0; j <= n; j++) {
                float a = a0 + span * j / n;
                float rr = rad * (1 + 0.04f * Mth.sin(j * 0.9f + i));
                pts[j] = new Vector3f(left).mul(Mth.cos(a) * rr).add(new Vector3f(up).mul(Mth.sin(a) * rr)).add(at);
                // The body a hair nearer the camera than its edge, so it always lands on top.
                front[j] = new Vector3f(pts[j]).mul(0.998f);
                us[j] = j / (float) n;
                float s = Mth.sin(j / (float) n * Mth.PI);
                glow[j] = w * 3 * s + 0.01f;
                rim[j] = w * 1.5f * s + 0.01f;
                body[j] = w * s + 0.01f;
            }
            // Solid ink (alpha layer, it doesn't blow out white) with the edge of the other color, and a faint glow.
            FxDraw.strip(pts, glow, us, FxDraw.BEAM, 0, col, 0.25f * op, true);
            FxDraw.strip(pts, rim, us, FxDraw.SOLID, 0, edge, 0.95f * op, false);
            FxDraw.strip(front, body, us, FxDraw.SOLID, 0, col, op, false);
        }
    }

    // ---- the Purple itself (the Visualizer's drawRoxo) ----

    // The two spheres (a the Red, b the Blue) and the purple born between them; m 0 to 1 = how far they mixed. With
    // a = b it is the formed Purple. step: how far it moved this tick, scaled (wind streaming past), or null; stepB the Blue's own (null: the same). voo: in
    // flight (the twisted space round it, only then and faint). noRim: the Unlimited one, no contour. world: its middle
    // in the world (for the warp), needed only in flight.
    private static void drawRoxo(Vector3f a, Vector3f b, float radius, float age, int seed, float m, float spawn, Vec3 step, Vec3 stepB, boolean voo,
            boolean noRim, Vec3 world) {
        float r = radius * spawn;
        if (r < 0.01f) {
            return;
        }
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        float dist = a.distance(b) / r;
        float touch = smooth((2.4f - dist) / 0.6f);
        float formed = smooth((m - 0.55f) / 0.45f);
        Vector3f eye = mid.lengthSquared() > 1.0E-6f ? new Vector3f(mid).normalize() : new Vector3f(look);
        if (step != null) {
            AzulRenderer.wind(a, step, r, age, seed);
        }
        // The Blue streams off its own way (stepB), or the Red's when they move together.
        Vec3 windB = stepB != null ? stepB : step;
        if (windB != null && a.distanceSquared(b) > 1.0E-4f) {
            AzulRenderer.wind(b, windB, r, age, seed + 17);
        }
        // Each sphere's glow (goes to the bloom), going out as they mix.
        FxDraw.billboard(a, r * 4.5f, 0, FxDraw.SOFT, 0, RED, 0.3f * (1 - m) * spawn, true);
        FxDraw.billboard(a, r * 2.3f, 0, FxDraw.SOFT, 0.1f, RED, 0.25f * (1 - m) * spawn, true);
        FxDraw.billboard(b, r * 4.5f, 0, FxDraw.SOFT, 0, BLUE, 0.3f * (1 - m) * spawn, true);
        FxDraw.billboard(b, r * 2.3f, 0, FxDraw.SOFT, 0.1f, BLUE, 0.25f * (1 - m) * spawn, true);
        // The purple: a glow where they meet, growing with the mix; formed, its white core blazing.
        float g = Math.max(touch * (0.35f + 0.65f * m), m);
        FxDraw.billboard(mid, r * (3 + 5 * m), 0, FxDraw.SOFT, 0, PURPLE, 0.5f * g * spawn, true);
        FxDraw.billboard(mid, r * (1.2f + 1.8f * m), 0, FxDraw.SOFT, 0.5f, MAGENTA, 0.4f * g * spawn, true);
        FxDraw.billboard(mid, r * (0.6f + 1.4f * formed), 0, FxDraw.SOFT, 0.9f, WHITE, (0.35f * touch + 0.65f * formed) * spawn, true);
        if (g > 0.02f) {
            FxDraw.billboard(mid, r * (2 + 3 * m), age * 0.02f, FxDraw.FLARE, 6 / 32f, PALE, 0.25f * g * spawn, true);
        }
        // The space twisting round it (grey rings in a whirl and the world behind turning): only in flight, and faint
        // (Stark).
        if (voo && world != null) {
            FxDraw.billboard(mid, r * 7, 0, FxDraw.ROXO_VORTEX, 0.35f * spawn, VORTEX, 1, false);
            ScreenFx.holdWarp(world, r * 3.4f, 0.35f * spawn, 4, new Vec3(eye.x, eye.y, eye.z), age / 20);
        }
        // Purple bolts: from when they touch, many once it is formed.
        if (touch > 0.05f) {
            bolts(mid, r * (1 + 0.1f * m), age, seed, 0.35f * touch + 0.65f * formed, 3 + Math.round(6 * formed));
        }
        // Glitter and bubbles around it: red and blue before, lilac and white after.
        int n = Math.round(10 + 26 * g);
        for (int k = 0; k < n; k++) {
            float life = 24 + 30 * FxWorld.hash(seed, k, 81);
            float ph = (age + FxWorld.hash(seed, k, 82) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            Vector3f own = new Vector3f(k % 2 != 0 ? b : a).lerp(mid, g);
            float ang = FxWorld.hash(seed + cyc * 7, k, 83) * Mth.TWO_PI + age * 0.08f * (k % 2 != 0 ? 1 : -1);
            float rad = r * (1.15f + 2.4f * FxWorld.hash(seed + cyc * 7, k, 84)) * (0.8f + 0.5f * u);
            Vector3f p = new Vector3f(left).mul(Mth.cos(ang) * rad).add(new Vector3f(up).mul(Mth.sin(ang) * rad))
                    .add(new Vector3f(eye).mul((FxWorld.hash(seed, k, 85) - 0.5f) * r * 2)).add(own);
            float o = Mth.sin(u * Mth.PI) * spawn * (0.6f + 0.4f * Mth.sin(age * 0.7f + k));
            float size = r * (0.04f + 0.06f * FxWorld.hash(seed, k, 86));
            float[] col = FxDraw.mix(k % 2 != 0 ? BLUE : RED, k % 3 != 0 ? LILAC : WHITE, g);
            FxDraw.billboard(p, size, 0, FxDraw.SOFT, 0.9f, col, o, true);
            if (k % 5 == 0) {
                FxDraw.billboard(p, size * 5, 0, FxDraw.RING, 0.15f, FxDraw.mix(col, LILAC, 0.5f), 0.3f * o, true);
            }
        }
        BODIES.add(new Body(new Vector3f(a), new Vector3f(b), r, m, seed, spawn, noRim));
    }

    // Living bolts round the ball (Stark's video): each one a curved strip hugging the ball, the bolt itself drawn by
    // shape 24 (a wandering line, thick drops, a web of thin threads, redrawn 15 times a second, flickering). Each lives
    // 8 to 16 ticks on an arc turning round the ball, then another is born somewhere else.
    private static void bolts(Vector3f at, float r, float age, int seed, float amount, int n) {
        Vector3f eye = at.lengthSquared() > 1.0E-6f ? new Vector3f(at).normalize() : new Vector3f(look);
        int segs = 14;
        for (int i = 0; i < n; i++) {
            float life = 8 + 8 * FxWorld.hash(seed, i, 90);
            float ph = (age + FxWorld.hash(seed, i, 89) * life) / life;
            int cyc = (int) Math.floor(ph);
            float u = ph - cyc;
            int sd = seed + cyc * 37 + i * 5;
            if (FxWorld.hash(sd, i, 91) > amount) {
                continue;
            }
            // The arc's plane: nearly facing the camera, tilted at random (so it goes round the ball on screen).
            Vector3f tilt = sphereDir(sd, i).add(new Vector3f(eye).mul(-2)).normalize();
            Vector3f x = new Vector3f(tilt).cross(Math.abs(tilt.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f y = new Vector3f(tilt).cross(x);
            float a0 = FxWorld.hash(sd, i, 92) * Mth.TWO_PI + age * 0.12f * (FxWorld.hash(sd, i, 93) < 0.5f ? 1 : -1);
            float span = 1.4f + 1.8f * FxWorld.hash(sd, i, 94);
            float rad = r * (1.08f + 0.45f * FxWorld.hash(sd, i, 95));
            boolean out = FxWorld.hash(sd, i, 96) < 0.3f;
            Vector3f[] pts = new Vector3f[segs + 1];
            float[] ws = new float[segs + 1];
            float[] us = new float[segs + 1];
            float w = r * (0.55f + 0.35f * FxWorld.hash(sd, i, 97));
            for (int j = 0; j <= segs; j++) {
                float t = j / (float) segs;
                float ang = a0 + span * t;
                float rr = out ? rad * (1 + 1.4f * t) : rad * (1 + 0.15f * Mth.sin(t * Mth.PI));
                pts[j] = new Vector3f(x).mul(Mth.cos(ang) * rr).add(new Vector3f(y).mul(Mth.sin(ang) * rr)).add(at);
                ws[j] = w;
                us[j] = t;
            }
            float f = (float) Math.pow(Mth.sin(u * Mth.PI), 0.4);
            float[] c = {0.55f + 0.4f * FxWorld.hash(sd, i, 99), 0.3f, 0.9f};
            FxDraw.strip(pts, ws, us, FxDraw.ROXO_RAIO, FxWorld.hash(sd, i, 98), c, f, true);
        }
    }

    // Purple dust and bubbles where something was erased.
    private static void dust(Vector3f at, float age, int seed, float big) {
        if (age < 0 || age > 40) {
            return;
        }
        float t = age / 40;
        FxDraw.billboard(at, 1.2f * big * (1 - t), 0, FxDraw.SOFT, 0.3f, PURPLE, 0.3f * (1 - t), true);
        for (int k = 0; k < 4 * big; k++) {
            Vector3f p = sphereDir(seed, k).mul((0.3f + 0.6f * easeOut(t)) * big).add(at).add(0, t * 1.2f * (0.5f + FxWorld.hash(seed, k, 3)), 0);
            float tw = 0.5f + 0.5f * Mth.sin(age * 0.8f + k * 2);
            FxDraw.billboard(p, 0.07f + 0.05f * FxWorld.hash(seed, k, 4), 0, FxDraw.SOFT, 0.9f, k % 2 != 0 ? WHITE : LILAC, (1 - t) * tw, true);
        }
        if (FxWorld.hash(seed, 9, 5) < 0.5f) {
            FxDraw.billboard(new Vector3f(at).add(0, t * 1.6f, 0), (0.25f + 0.35f * easeOut(t)) * big, 0, FxDraw.RING, 0.12f, LILAC, 0.5f * (1 - t), true);
        }
    }

    private static Vector3f sphereDir(int seed, int k) {
        float y = FxWorld.hash(seed, k, 20) * 2 - 1;
        float a = FxWorld.hash(seed, k, 21) * Mth.TWO_PI;
        float r = (float) Math.sqrt(Math.max(0, 1 - y * y));
        return new Vector3f(Mth.cos(a) * r, y, Mth.sin(a) * r);
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return world.subtract(camera).toVector3f();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOut(float t) {
        float u = 1 - Mth.clamp(t, 0, 1);
        return 1 - u * u * u;
    }
}
