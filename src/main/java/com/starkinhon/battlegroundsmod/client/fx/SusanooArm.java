package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.SusanooSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// The Susanoo's arm: one giant armored arm of translucent blue chakra, a real 3D model built here out of simple
// shapes (shoulder guard in three layered skirts, eight-sided arm and forearm, spiked elbow, a hand with four
// two-part fingers and a thumb), a shield of square plates strapped to the forearm and a blade coming out of the fist.
// Drawn with souls_susanoo (see FxRenderTypes.SUS_*): depth first, then color, then a copy into the bloom.
// The arm is posed with a two-bone reach (shoulder to hand, elbow pointing at a pole) every frame.
//
// It shows in two skills: the C counter (driven by the server through update: guard with the shield up, grab,
// crush, throw) and the Shift + C slash (the bracoEspada vfx, drawn from FxWorld through sword).
public final class SusanooArm {
    private static final float L1 = 2.1f;
    private static final float L2 = 2.0f;
    private static final float[] COR = FxDraw.rgb(0x2F8FFF);
    private static final float[] COR2 = FxDraw.rgb(0xDFF4FF);
    private static final float[] RIM = FxDraw.rgb(0xBFEAFF);
    private static final float[] MIST = FxDraw.rgb(0x7A6AD8);

    // Bones of the model.
    private static final int UPPER = 0, ELBOW = 1, WRIST = 2, KNUCKLE = 3, JOINT = 7, THUMB = 11, BLADE = 12, SHIELD = 13, BONES = 14;
    // Triangles per bone: 9 floats of position, 3 of normal each.
    private static final List<List<float[]>> MESH = new ArrayList<>();

    static {
        for (int b = 0; b < BONES; b++) {
            MESH.add(new ArrayList<>());
        }
        build();
    }

    // ---------------------------------------------------------------- the C counter, as the server tells it
    // Phases sent by the server (ModPayloads.SusanooArm).
    public static final int GUARD = 0, GRAB = 1, HIT = 2, THROW = 3, END = 4;

    private static final class Counter {
        int caster;
        int target = -1;
        long guardStart;
        long grabStart = -1;
        int reach = 4;
        int crush = 60;
        long lastHit = -100;
        long throwStart = -1;
        long endStart = -1;
    }

    private static final Map<Integer, Counter> COUNTERS = new HashMap<>();

    public static void update(int caster, int target, int phase, int a, int b) {
        long now = ScreenFx.now();
        Counter c = COUNTERS.get(caster);
        if (phase == GUARD || c == null) {
            if (phase != GUARD && phase != GRAB) {
                return;
            }
            c = new Counter();
            c.caster = caster;
            c.guardStart = now;
            COUNTERS.put(caster, c);
        }
        switch (phase) {
            case GRAB -> {
                c.target = target;
                c.grabStart = now;
                c.reach = Math.max(1, a);
                c.crush = Math.max(1, b);
                c.endStart = -1;
            }
            case HIT -> c.lastHit = now;
            case THROW -> c.throwStart = now;
            case END -> {
                if (c.endStart < 0) {
                    c.endStart = now;
                }
            }
            default -> {
            }
        }
    }

    public static boolean active() {
        return !COUNTERS.isEmpty();
    }

    public static void clear() {
        COUNTERS.clear();
    }

    static double time() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // Called from FxWorld while its buffers are open.
    static void render(MultiBufferSource buffers, boolean flat, Vec3 camera, double now, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            COUNTERS.clear();
            return;
        }
        COUNTERS.values().removeIf(c -> level.getEntity(c.caster) == null || now - c.guardStart > 20 * 30
                || c.endStart >= 0 && now - c.endStart > 14 || c.throwStart >= 0 && now - c.throwStart > 36);
        for (Counter c : COUNTERS.values()) {
            counter(buffers, flat, c, camera, now, partial);
        }
    }

    private static void counter(MultiBufferSource buffers, boolean flat, Counter c, Vec3 camera, double now, float partial) {
        Entity caster = FxAnchor.entity(c.caster);
        if (caster == null) {
            return;
        }
        SusanooSpec spec = SkillSpecs.get(Susanoo.SKILL, SusanooSpec.class, false);
        Vec3 feetW = caster.getPosition(partial);
        Vec3 look = caster.getViewVector(partial);
        Vector3f f = new Vector3f((float) look.x, 0, (float) look.z);
        if (f.lengthSquared() < 1.0E-4f) {
            f.set(0, 0, 1);
        }
        f.normalize();
        Vector3f left = new Vector3f(f.z, 0, -f.x);
        Vector3f feet = feetW.subtract(camera).toVector3f();
        Frame fr = new Frame(feet, left, f);
        float s = 1.15f;
        Vector3f shoulder = fr.at(-1.5f, 3.4f, -1.0f);
        Vector3f guard = fr.at(0.5f, 2.1f, 2.5f);
        Vector3f hold = fr.at(0, (float) spec.agarrar().altura(), (float) spec.agarrar().frente());

        float sinceGuard = (float) (now - c.guardStart);
        float form = smooth(sinceGuard / 12f);
        if (c.endStart >= 0) {
            form *= 1 - smooth((float) (now - c.endStart) / 12f);
        }
        if (c.throwStart >= 0) {
            form *= 1 - smooth(((float) (now - c.throwStart) - 22) / 12f);
        }
        Vector3f hand;
        float grip;
        float bend = 0.2f;
        float hit = 0;
        Vector3f pole = fr.dir(-1, -0.6f, 0);
        Vector3f facing = fr.dir(0, 0.1f, 1);
        if (c.grabStart < 0) {
            // Guard: the shield across the front, rising out of the ground behind him while it forms.
            hand = lerp(fr.at(-1.6f, 0.8f, 0.4f), guard, smooth(sinceGuard / 12f));
            grip = 0.85f;
        } else {
            float g = (float) (now - c.grabStart);
            Entity target = FxAnchor.entity(c.target);
            Vector3f held = target == null ? hold
                    : target.getPosition(partial).add(0, target.getBbHeight() / 2, 0).subtract(camera).toVector3f();
            float recoil = g < 4 ? Mth.sin(g / 4 * Mth.PI) * 0.45f : 0;
            facing = fr.dir(-1, 0.25f, -0.2f);
            pole = fr.dir(-1, -0.6f, -0.5f);
            bend = -0.2f;
            float throwAt = c.reach + c.crush;
            if (c.throwStart >= 0) {
                // Wound back, then flung forward with the hand opening.
                float t = (float) (now - c.throwStart);
                hand = lerp(fr.at(-0.9f, 4.0f, 1.6f), fr.at(0.4f, 3.2f, 5.6f), smooth(t / 4f));
                hand = lerp(hand, fr.at(0.2f, 2.8f, 4.6f), smooth((t - 6) / 20f));
                grip = t < 2 ? 1 : 0.1f;
            } else if (g > throwAt - 6) {
                hand = lerp(held, fr.at(-0.9f, 4.0f, 1.6f), smooth((g - (throwAt - 6)) / 5f));
                grip = 1;
            } else {
                // Reaching: the open hand shoots out to the attacker, then closes around them and holds them up.
                hand = g < 1 ? lerp(guard, held, 0.5f) : held;
                hand.add(fr.dir(0, 0, -recoil));
                float pulse = Math.max(0, 1 - (float) (now - c.lastHit) / 4f);
                hit = pulse;
                if (pulse > 0) {
                    hand.add(fr.dir(hash(c.caster, (int) now, 1) - 0.5f, hash(c.caster, (int) now, 2) - 0.5f, 0).mul(0.25f * pulse));
                }
                grip = g < c.reach ? 0 : 1 + 0.35f * pulse;
                if (pulse > 0) {
                    FxDraw.billboard(held, 2.2f + pulse, 0, FxDraw.SOFT, 0.4f, COR, (0.35f + 0.3f * pulse) * form, true);
                }
            }
        }
        float opacity = FxAnchor.firstPersonSelf(c.caster) ? 0.35f : 1;
        Pose p = pose(shoulder, hand, pole, s, grip, bend);
        Shield sh = shield(p, facing, s);
        emit(buffers, flat, p, sh, shoulder, form * 14, opacity, hit, true, false);
        flames(points(p, sh), (float) now, s, form * opacity, c.caster * 31 + 9);
        mist(feet, (float) now, form * opacity);
    }

    // ---------------------------------------------------------------- Shift + C: the arm with the blade
    // feet: camera-relative; look: where the caster faces. Swings like the old ribbon arm: pulls back until golpe,
    // sweeps from right to left in 4 ticks, then fades by life. The blade lights up on the swing and dies in 6 ticks.
    static void sword(MultiBufferSource buffers, boolean flat, Vector3f feet, Vector3f look, float age, int life, float golpe, float scale, float opacity,
            int seed) {
        Vector3f f = new Vector3f(look.x, 0, look.z);
        if (f.lengthSquared() < 1.0E-4f) {
            return;
        }
        f.normalize();
        Vector3f left = new Vector3f(f.z, 0, -f.x);
        float s = 1.2f * scale;
        Vector3f shoulder = new Vector3f(feet).add(0, 4.2f * s, 0).add(new Vector3f(f).mul(-0.6f * s)).add(new Vector3f(left).mul(-1.6f * s));
        float th = swordAngle(age, golpe);
        float form = smooth(age / 5) * (1 - smooth((age - life + 6) / 10)) * opacity;
        if (form <= 0.001f) {
            return;
        }
        float swing = age - golpe;
        float glow = swing >= 0 ? Math.max(0, 1 - swing / 6) : 0;
        if (glow > 0) {
            for (int k = 6; k >= 1; k--) {
                float a = age - k * 0.4f;
                if (a < golpe) {
                    continue;
                }
                float ta = swordAngle(a, golpe);
                Pose g = pose(shoulder, swordHand(shoulder, f, left, ta, s), swordPole(f, left, ta), s, 1, -0.15f);
                float o = glow * 0.55f * (1 - k / 7f) * form;
                FxDraw.ribbon(g.bladeBase, g.tip, 1.1f * s, FxDraw.BEAM, 0.2f, COR, o * 0.8f, true);
                FxDraw.ribbon(g.bladeBase, g.tip, 0.35f * s, FxDraw.BEAM, 0.85f, COR2, o, true);
            }
        }
        Pose p = pose(shoulder, swordHand(shoulder, f, left, th, s), swordPole(f, left, th), s, 1, -0.15f);
        if (glow > 0) {
            FxDraw.ribbon(p.bladeBase, p.tip, 1.4f * s, FxDraw.BEAM, 0.3f, COR, 0.7f * glow * form, true);
            FxDraw.ribbon(p.bladeBase, p.tip, 0.45f * s, FxDraw.BEAM, 0.9f, COR2, glow * form, true);
        }
        emit(buffers, flat, p, null, shoulder, form * 18, form, swing >= 0 && swing < 3 ? 1 : 0, false, true);
        List<Vector3f> pts = points(p, null);
        pts.add(lerp(p.bladeBase, p.tip, 0.35f));
        pts.add(lerp(p.bladeBase, p.tip, 0.7f));
        flames(pts, age, s, form, seed);
        mist(feet, age, form);
    }

    private static float swordAngle(float a, float golpe) {
        return a < golpe ? Mth.lerp(FxWorld.easeOut(a / golpe), -1.0f, -2.1f) : Mth.lerp(FxWorld.easeOut(Math.min(1, (a - golpe) / 4)), -2.1f, 1.4f);
    }

    private static Vector3f swordHand(Vector3f shoulder, Vector3f f, Vector3f left, float th, float s) {
        Vector3f d = new Vector3f(f).mul(Mth.cos(th)).add(new Vector3f(left).mul(Mth.sin(th)));
        return d.mul(3.85f * s).add(shoulder).add(0, -0.5f, 0);
    }

    private static Vector3f swordPole(Vector3f f, Vector3f left, float th) {
        Vector3f d = new Vector3f(f).mul(Mth.cos(th)).add(new Vector3f(left).mul(Mth.sin(th)));
        return d.negate().add(0, 0.3f, 0);
    }

    // ---------------------------------------------------------------- posing
    // A point or direction in the caster's frame: x to his left, y up, z ahead.
    private record Frame(Vector3f feet, Vector3f left, Vector3f fwd) {
        Vector3f at(float x, float y, float z) {
            return new Vector3f(feet).add(dir(x, y, z));
        }

        Vector3f dir(float x, float y, float z) {
            return new Vector3f(left).mul(x).add(0, y, 0).add(new Vector3f(fwd).mul(z));
        }
    }

    private record Pose(Matrix4f[] bones, Vector3f shoulder, Vector3f elbow, Vector3f wrist, Vector3f bladeBase, Vector3f tip, float scale) {
    }

    private record Shield(Matrix4f m, Vector3f center, Vector3f up, Vector3f side) {
    }

    // Shoulder at shoulder, hand reaching for hand (two bones), the elbow bent toward pole. grip 0 = open hand,
    // 1 = fist (more squeezes harder).
    private static Pose pose(Vector3f shoulder, Vector3f hand, Vector3f pole, float scale, float grip, float wristBend) {
        Vector3f to = new Vector3f(hand).sub(shoulder);
        Vector3f d = new Vector3f(to).normalize();
        float dist = Mth.clamp(to.length() / scale, 0.6f, (L1 + L2) * 0.995f);
        Vector3f pz = new Vector3f(pole).sub(new Vector3f(d).mul(pole.dot(d)));
        if (pz.lengthSquared() < 1.0E-6f) {
            pz.set(0, 1, 0).sub(new Vector3f(d).mul(d.y));
        }
        pz.normalize().negate();
        Vector3f y = new Vector3f(d).negate();
        Vector3f x = new Vector3f(y).cross(pz);
        float a1 = (float) Math.acos(Mth.clamp((L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist), -1, 1));
        float inner = (float) Math.acos(Mth.clamp((L1 * L1 + L2 * L2 - dist * dist) / (2 * L1 * L2), -1, 1));
        Matrix4f[] m = new Matrix4f[BONES];
        Matrix4f root = new Matrix4f().translate(shoulder)
                .mul(new Matrix4f(x.x, x.y, x.z, 0, y.x, y.y, y.z, 0, pz.x, pz.y, pz.z, 0, 0, 0, 0, 1)).scale(scale);
        m[UPPER] = new Matrix4f(root).rotateX(a1);
        m[ELBOW] = new Matrix4f(m[UPPER]).translate(0, -L1, 0).rotateX(-(Mth.PI - inner));
        m[WRIST] = new Matrix4f(m[ELBOW]).translate(0, -L2, 0).rotateX(wristBend);
        for (int k = 0; k < 4; k++) {
            m[KNUCKLE + k] = new Matrix4f(m[WRIST]).translate(-0.29f + 0.193f * k, -0.88f, 0).rotateX(0.15f + grip * (1.35f + 0.05f * k));
            m[JOINT + k] = new Matrix4f(m[KNUCKLE + k]).translate(0, -0.45f, 0).rotateX(0.1f + grip * 1.5f);
        }
        m[THUMB] = new Matrix4f(m[WRIST]).translate(0.45f, -0.3f, -0.1f).rotateXYZ(grip * 0.9f, 0, 0.5f - grip * 0.9f);
        m[BLADE] = new Matrix4f(m[WRIST]).translate(0, -0.85f, 0);
        return new Pose(m, new Vector3f(shoulder), m[ELBOW].transformPosition(new Vector3f()), m[WRIST].transformPosition(new Vector3f()),
                m[BLADE].transformPosition(new Vector3f(0, -0.2f, 0)), m[BLADE].transformPosition(new Vector3f(0, -9.6f, 0)), scale);
    }

    // The shield on the forearm, turned to face facing.
    private static Shield shield(Pose p, Vector3f facing, float scale) {
        Vector3f fd = new Vector3f(p.wrist).sub(p.elbow).normalize();
        Vector3f n = new Vector3f(facing).sub(new Vector3f(fd).mul(facing.dot(fd))).normalize();
        Vector3f x = new Vector3f(fd).cross(n);
        Vector3f at = lerp(p.elbow, p.wrist, 0.5f).add(new Vector3f(n).mul(0.55f * scale));
        Matrix4f m = new Matrix4f().translate(at).mul(new Matrix4f(x.x, x.y, x.z, 0, fd.x, fd.y, fd.z, 0, n.x, n.y, n.z, 0, 0, 0, 0, 1)).scale(scale);
        return new Shield(m, at, fd.mul(scale), x.mul(scale));
    }

    // ---------------------------------------------------------------- drawing
    // reach: how far from the shoulder the arm has formed (blocks). hit: white flash of a squeeze or swing.
    private static void emit(MultiBufferSource buffers, boolean flat, Pose p, Shield sh, Vector3f origin, float reach, float opacity, float hit,
            boolean withShield, boolean withBlade) {
        if (opacity <= 0.002f || reach <= 0.01f) {
            return;
        }
        if (flat) {
            VertexConsumer out = buffers.getBuffer(FxRenderTypes.SUS_FLAT);
            forEach(p, sh, withShield, withBlade, (pos, nrm) -> {
                for (int v = 0; v < 3; v++) {
                    Vector3f q = pos[v];
                    float form = q.distance(origin) - reach;
                    float a = form > 0 ? 0 : 0.45f * opacity;
                    out.addVertex(q.x, q.y, q.z).setColor(0.25f, 0.6f, 1f, a);
                }
            });
            return;
        }
        VertexConsumer depth = buffers.getBuffer(FxRenderTypes.SUS_DEPTH);
        VertexConsumer color = buffers.getBuffer(FxRenderTypes.SUS_COLOR);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.SUS_GLOW);
        forEach(p, sh, withShield, withBlade, (pos, nrm) -> {
            for (int v = 0; v < 3; v++) {
                Vector3f q = pos[v];
                float r = Mth.clamp((q.distance(origin) - reach + 8) / 10, 0, 1);
                for (VertexConsumer out : new VertexConsumer[] {depth, color, glow}) {
                    out.addVertex(q.x, q.y, q.z).setColor(r, hit, 0, opacity).setNormal(nrm.x, nrm.y, nrm.z);
                }
            }
        });
    }

    private interface TriSink {
        void accept(Vector3f[] pos, Vector3f normal);
    }

    private static void forEach(Pose p, Shield sh, boolean withShield, boolean withBlade, TriSink sink) {
        Vector3f[] pos = {new Vector3f(), new Vector3f(), new Vector3f()};
        Vector3f nrm = new Vector3f();
        for (int b = 0; b < BONES; b++) {
            Matrix4f m;
            if (b == SHIELD) {
                if (!withShield || sh == null) {
                    continue;
                }
                m = sh.m;
            } else if (b == BLADE && !withBlade) {
                continue;
            } else {
                m = p.bones[b];
            }
            for (float[] t : MESH.get(b)) {
                for (int v = 0; v < 3; v++) {
                    m.transformPosition(t[v * 3], t[v * 3 + 1], t[v * 3 + 2], pos[v]);
                }
                m.transformDirection(t[9], t[10], t[11], nrm).normalize();
                sink.accept(pos, nrm);
            }
        }
    }

    // Blue chakra flames rising off the joints and the shield corners.
    private static void flames(List<Vector3f> pts, float now, float scale, float form, int seed) {
        if (form <= 0.01f) {
            return;
        }
        for (int k = 0; k < pts.size(); k++) {
            for (int j = 0; j < 2; j++) {
                float life = 14 + 8 * hash(seed, k * 2 + j, 1);
                float u = ((now + hash(seed, k * 2 + j, 2) * life) % life) / life;
                Vector3f at = FxWorld.sphere(seed + j, k).mul(0.35f * scale).add(pts.get(k)).add(0, u * 1.6f * scale, 0);
                FxDraw.billboard(at, scale * (1.1f - 0.5f * u), hash(seed, k, 3) * 6, FxDraw.FIRE, 0.15f + u * 0.7f, FxDraw.mix(RIM, COR, u),
                        0.55f * form * (1 - u * 0.6f), true);
            }
        }
    }

    // Purple mist low around the caster's feet, like the reference.
    private static void mist(Vector3f feet, float now, float form) {
        if (form <= 0.01f) {
            return;
        }
        for (int k = 0; k < 7; k++) {
            float a = hash(71, k, 1) * Mth.TWO_PI + now * 0.004f;
            float r = 1.2f + 1.6f * hash(71, k, 2);
            Vector3f at = new Vector3f(feet).add(Mth.cos(a) * r, 0.5f + 0.4f * hash(71, k, 3), Mth.sin(a) * r);
            FxDraw.billboard(at, 2.4f + hash(71, k, 4), now * 0.01f + k, FxDraw.SMOKE, 0.3f, MIST, 0.28f * form, false);
        }
    }

    private static List<Vector3f> points(Pose p, Shield sh) {
        List<Vector3f> pts = new ArrayList<>(List.of(p.shoulder, lerp(p.shoulder, p.elbow, 0.5f), p.elbow, lerp(p.elbow, p.wrist, 0.5f), p.wrist));
        if (sh != null) {
            for (int s = -1; s <= 1; s += 2) {
                pts.add(new Vector3f(sh.center).add(new Vector3f(sh.up).mul(1.6f * s)).add(new Vector3f(sh.side).mul(1.05f)));
                pts.add(new Vector3f(sh.center).add(new Vector3f(sh.up).mul(1.6f * s)).add(new Vector3f(sh.side).mul(-1.05f)));
            }
        }
        return pts;
    }

    // ---------------------------------------------------------------- the model
    private static void build() {
        Matrix4f id = new Matrix4f();
        // shoulder: ball + three layered skirts (a samurai shoulder guard)
        ico(UPPER, at(0, 0, 0), 0.72f);
        for (int i = 0; i < 3; i++) {
            cylinder(UPPER, at(0, 0.1f - 0.36f * i, 0), 0.6f + 0.13f * i, 1.0f + 0.16f * i, 0.55f, 8, true);
        }
        cylinder(UPPER, at(0, -L1 / 2, 0), 0.5f, 0.42f, L1, 8, false);
        cylinder(UPPER, at(0, -L1 * 0.74f, 0), 0.56f, 0.56f, 0.24f, 8, false);
        // elbow with a spike pointing back, forearm with three gauntlet rings
        ico(ELBOW, id, 0.48f);
        cone(ELBOW, at(0, 0.1f, -0.45f).rotateX(-1.9f), 0.28f, 0.8f, 5);
        cylinder(ELBOW, at(0, -L2 / 2, 0), 0.44f, 0.36f, L2, 8, false);
        for (int i = 0; i < 3; i++) {
            cylinder(ELBOW, at(0, -0.45f - 0.55f * i, 0), 0.53f - 0.03f * i, 0.47f - 0.03f * i, 0.32f, 8, false);
        }
        // hand: palm facing -z, four fingers of two parts, the thumb
        box(WRIST, at(0, -0.45f, 0), 0.85f, 0.9f, 0.42f);
        for (int k = 0; k < 4; k++) {
            box(KNUCKLE + k, at(0, -0.22f, 0), 0.17f, 0.48f, 0.2f);
            box(JOINT + k, at(0, -0.18f, 0), 0.15f, 0.4f, 0.18f);
        }
        box(THUMB, at(0, -0.22f, 0), 0.2f, 0.5f, 0.22f);
        // blade out of the fist, with its guard
        box(BLADE, id, 1.25f, 0.2f, 0.6f);
        blade(BLADE, at(0, -0.1f, 0), 9.5f, 0.75f);
        // shield: 4 x 5 square plates, slightly curved, a frame and a crystal in the middle
        float sp = 0.62f;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 5; r++) {
                float x = (c - 1.5f) * sp, y = (r - 2) * sp;
                box(SHIELD, at(x, y, bow(x)).rotateY(-(float) Math.atan(0.18f * x)), 0.56f, 0.56f, 0.12f);
            }
        }
        for (float y : new float[] {-1.6f, 1.6f}) {
            box(SHIELD, at(0, y, -0.12f), 2.7f, 0.14f, 0.2f);
        }
        for (float x : new float[] {-1.3f, 1.3f}) {
            box(SHIELD, at(x, 0, bow(x) + 0.02f).rotateY(-(float) Math.atan(0.18f * x)), 0.14f, 3.3f, 0.2f);
        }
        ico(SHIELD, at(0, 0, 0.14f), 0.34f);
    }

    private static float bow(float x) {
        return -0.12f * x * x;
    }

    private static Matrix4f at(float x, float y, float z) {
        return new Matrix4f().translate(x, y, z);
    }

    private static void tri(int bone, Matrix4f m, float[] a, float[] b, float[] c) {
        Vector3f pa = m.transformPosition(a[0], a[1], a[2], new Vector3f());
        Vector3f pb = m.transformPosition(b[0], b[1], b[2], new Vector3f());
        Vector3f pc = m.transformPosition(c[0], c[1], c[2], new Vector3f());
        Vector3f n = new Vector3f(pb).sub(pa).cross(new Vector3f(pc).sub(pa));
        if (n.lengthSquared() < 1.0E-12f) {
            return;
        }
        n.normalize();
        MESH.get(bone).add(new float[] {pa.x, pa.y, pa.z, pb.x, pb.y, pb.z, pc.x, pc.y, pc.z, n.x, n.y, n.z});
    }

    private static void quad(int bone, Matrix4f m, float[] a, float[] b, float[] c, float[] d) {
        tri(bone, m, a, b, c);
        tri(bone, m, a, c, d);
    }

    private static float[] v(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    private static void box(int bone, Matrix4f m, float w, float h, float d) {
        float x = w / 2, y = h / 2, z = d / 2;
        float[][] p = {v(-x, -y, -z), v(x, -y, -z), v(x, y, -z), v(-x, y, -z), v(-x, -y, z), v(x, -y, z), v(x, y, z), v(-x, y, z)};
        quad(bone, m, p[4], p[5], p[6], p[7]);
        quad(bone, m, p[1], p[0], p[3], p[2]);
        quad(bone, m, p[5], p[1], p[2], p[6]);
        quad(bone, m, p[0], p[4], p[7], p[3]);
        quad(bone, m, p[7], p[6], p[2], p[3]);
        quad(bone, m, p[0], p[1], p[5], p[4]);
    }

    // Centered on y, top radius at +h/2.
    private static void cylinder(int bone, Matrix4f m, float rTop, float rBottom, float h, int sides, boolean open) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
            float[] t0 = v(Mth.sin(a0) * rTop, h / 2, Mth.cos(a0) * rTop), t1 = v(Mth.sin(a1) * rTop, h / 2, Mth.cos(a1) * rTop);
            float[] b0 = v(Mth.sin(a0) * rBottom, -h / 2, Mth.cos(a0) * rBottom), b1 = v(Mth.sin(a1) * rBottom, -h / 2, Mth.cos(a1) * rBottom);
            quad(bone, m, b0, b1, t1, t0);
            if (!open) {
                tri(bone, m, v(0, h / 2, 0), t0, t1);
                tri(bone, m, v(0, -h / 2, 0), b1, b0);
            }
        }
    }

    // Tip at +h/2.
    private static void cone(int bone, Matrix4f m, float r, float h, int sides) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
            float[] b0 = v(Mth.sin(a0) * r, -h / 2, Mth.cos(a0) * r), b1 = v(Mth.sin(a1) * r, -h / 2, Mth.cos(a1) * r);
            tri(bone, m, b0, b1, v(0, h / 2, 0));
            tri(bone, m, v(0, -h / 2, 0), b1, b0);
        }
    }

    private static void ico(int bone, Matrix4f m, float r) {
        float t = (1 + Mth.sqrt(5)) / 2;
        float[][] p = {v(-1, t, 0), v(1, t, 0), v(-1, -t, 0), v(1, -t, 0), v(0, -1, t), v(0, 1, t), v(0, -1, -t), v(0, 1, -t), v(t, 0, -1), v(t, 0, 1),
                v(-t, 0, -1), v(-t, 0, 1)};
        float k = r / Mth.sqrt(1 + t * t);
        for (float[] q : p) {
            q[0] *= k;
            q[1] *= k;
            q[2] *= k;
        }
        int[] f = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8, 3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8,
                3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        for (int i = 0; i < f.length; i += 3) {
            tri(bone, m, p[f[i]], p[f[i + 1]], p[f[i + 2]]);
        }
    }

    // A flat blade going down -y: wide at the guard, tapering to a point at len.
    private static void blade(int bone, Matrix4f m, float len, float w) {
        float[][] shape = {{-w * 0.5f, 0}, {w * 0.5f, 0}, {w * 0.42f, -len * 0.82f}, {0, -len}, {-w * 0.36f, -len * 0.78f}};
        float z = 0.07f;
        for (int i = 1; i < shape.length - 1; i++) {
            tri(bone, m, v(shape[0][0], shape[0][1], z), v(shape[i][0], shape[i][1], z), v(shape[i + 1][0], shape[i + 1][1], z));
            tri(bone, m, v(shape[0][0], shape[0][1], -z), v(shape[i + 1][0], shape[i + 1][1], -z), v(shape[i][0], shape[i][1], -z));
        }
        for (int i = 0; i < shape.length; i++) {
            float[] a = shape[i], b = shape[(i + 1) % shape.length];
            quad(bone, m, v(a[0], a[1], -z), v(b[0], b[1], -z), v(b[0], b[1], z), v(a[0], a[1], z));
        }
    }

    // ---------------------------------------------------------------- small math
    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, Mth.clamp(t, 0, 1));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float hash(int seed, int k, int salt) {
        return FxWorld.hash(seed, k, salt);
    }
}
