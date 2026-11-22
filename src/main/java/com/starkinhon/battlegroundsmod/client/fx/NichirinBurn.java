// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.fx.SmokeOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Nichirin's Feridas do Sol (universe/NichirinWounds) burning on a demon's body, as in the preview Stark approved on
// 2026-11-22 (/mnt/project-files/previas/nichirin/queimadura.html):
//   - each cut is a seam of sunlight stuck to a face of the body (torso, arms, legs): gold-white inside, orange and red
//     at its ragged edges, embers running along it, weaker stretches so it never reads as a wire or a lightning bolt;
//     it burns itself in along the cut in 3 ticks;
//   - the skin round it is charred: raw red flesh near the seam, burnt brown further out, a gnawed edge, blisters;
//     everything is clamped to the face, so nothing hangs off the body;
//   - square embers rise off the seams (more with more wounds), and white steam: the regeneration fighting the wound,
//     less with each one and none at 5, when the wounds pulse (he heals no more);
//   - the neck cut: a burst of embers and a burning ring round the neck that stays (the boss: the whole fight);
//   - the wounds cool over the last 1.5 s of their 8 and are gone.
// No dynamic light (the game has none): the seams only glow. Everything is drawn in the body part's own space, so it
// follows the pose; only the embers live in the world.
public final class NichirinBurn {
    private static final int MAX = 5;
    private static final float COOL = 30;
    private static final int PTS = 28;
    private static final int MAX_EMBERS = 160;
    private static final RandomSource R = RandomSource.createThreadSafe();

    // part: 0 body, 1 right arm, 2 left arm, 3 right leg, 4 left leg, 5 head. face: 0 front, 1 back, 2 +x, 3 -x.
    private static final int[][] SPOTS = { { 0, 0, 40 }, { 0, 1, 30 }, { 1, 0, 10 }, { 1, 3, 10 }, { 2, 0, 10 }, { 2, 2, 10 }, { 3, 0, 10 },
            { 4, 0, 10 }, { 0, 2, 6 }, { 0, 3, 6 } };
    // nominal face sizes in pixels (a wide player model), per part and face: the path is laid out on these and scaled
    // to the real cube (slim arms)
    private static final float[][] NOMINAL = { { 8, 12, 4 }, { 4, 12, 4 }, { 4, 12, 4 }, { 4, 12, 4 }, { 4, 12, 4 }, { 8, 8, 8 } };

    private static final class Wound {
        final int part, face;
        final float nomW, nomH;
        final float[] u = new float[PTS], v = new float[PTS], t = new float[PTS], nu = new float[PTS], nv = new float[PTS], w = new float[PTS];
        final float[] roughL = new float[PTS], roughR = new float[PTS], mottle = new float[PTS * 2];
        final float[] blister; // t, across, size
        final float seed;
        final float born;
        final boolean permanent;
        boolean burst;
        float emberAcc, steamAcc;

        Wound(int part, int face, float born, boolean permanent, int n) {
            this.part = part;
            this.face = face;
            this.nomW = face >= 2 ? NOMINAL[part][2] : NOMINAL[part][0];
            this.nomH = NOMINAL[part][1];
            this.seed = R.nextFloat() * 100;
            this.born = born;
            this.permanent = permanent;
            this.blister = new float[n * 3];
            for (int i = 0; i < n; i++) {
                blister[i * 3] = 0.15f + R.nextFloat() * 0.7f;
                blister[i * 3 + 1] = (R.nextBoolean() ? 1 : -1) * (0.9f + R.nextFloat() * 0.8f);
                blister[i * 3 + 2] = 0.18f + R.nextFloat() * 0.14f;
            }
        }

        // the normals across the path, smoothed over a few points (no spikes at the bends), and the gnawed edges
        void finish() {
            for (int i = 0; i < PTS; i++) {
                int a = Math.max(0, i - 3), b = Math.min(PTS - 1, i + 3);
                float tx = u[b] - u[a], ty = v[b] - v[a];
                float l = Mth.sqrt(tx * tx + ty * ty);
                if (l < 1.0E-4f) {
                    l = 1;
                }
                nu[i] = -ty / l;
                nv[i] = tx / l;
                roughL[i] = 0.62f + 0.38f * vn(t[i] * 22 + seed * 5);
                roughR[i] = 0.62f + 0.38f * vn(t[i] * 22 + seed * 5 + 37);
                mottle[i * 2] = vn(t[i] * 30 + seed * 3);
                mottle[i * 2 + 1] = vn(t[i] * 30 + seed * 3 + 11);
            }
        }
    }

    private static final class Ember {
        double x, y, z;
        float vx, vy, vz, age, life, size, ph;
    }

    private static final class State {
        final List<Wound> wounds = new ArrayList<>();
        Wound[] neck;
        float until;
        float neckUntil;
        boolean neckBurst;
        final List<Ember> embers = new ArrayList<>();
        float lastT = -1;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    // Times are kept as ticks since this, so a float holds them exactly however old the world is.
    private static long base = Long.MIN_VALUE;

    private NichirinBurn() {
    }

    public static void clear() {
        STATES.clear();
        base = Long.MIN_VALUE;
    }

    private static float now(long gameTime) {
        if (base == Long.MIN_VALUE) {
            base = gameTime;
        }
        return gameTime - base;
    }

    // From the server (universe/NichirinWounds): see ModPayloads.NichirinBurn.
    public static void set(int entityId, int wounds, int ticks, int neckTicks, int flags) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        float now = now(level.getGameTime());
        State s = STATES.computeIfAbsent(entityId, k -> new State());
        if (wounds <= 0 || now >= s.until) {
            s.wounds.clear();
        }
        if ((flags & 1) != 0 && wounds > 0) {
            if (s.wounds.size() >= MAX) {
                s.wounds.remove(0); // the 6th cut renews them: the oldest gives way to the new one
            }
            s.wounds.add(slash(now));
        }
        // someone who starts seeing him later gets the wounds already burnt in (no burst)
        while (s.wounds.size() < wounds) {
            Wound w = slash(now - 20);
            w.burst = true;
            s.wounds.add(0, w);
        }
        while (s.wounds.size() > Math.max(0, wounds)) {
            s.wounds.remove(0);
        }
        s.until = now + ticks;
        if ((flags & 2) != 0 || neckTicks != 0) {
            if (s.neck == null) {
                s.neck = ring((flags & 2) != 0 ? now : now - 20);
                if ((flags & 2) == 0) {
                    for (Wound w : s.neck) {
                        w.burst = true;
                    }
                }
            }
            // a common oni dies of it: its ring only flares a moment
            s.neckUntil = neckTicks < 0 ? Float.MAX_VALUE : now + (neckTicks > 0 ? neckTicks : 20);
            if ((flags & 2) != 0) {
                s.neckBurst = true;
            }
        }
    }

    private static float rnd(float a, float b) {
        return a + R.nextFloat() * (b - a);
    }

    // A cut on a face: a diagonal, a little shaky, thicker in the middle (the preview's slashPath).
    private static Wound slash(float now) {
        int total = 0;
        for (int[] s : SPOTS) {
            total += s[2];
        }
        int r = R.nextInt(total);
        int[] spot = SPOTS[0];
        for (int[] s : SPOTS) {
            r -= s[2];
            if (r < 0) {
                spot = s;
                break;
            }
        }
        Wound w = new Wound(spot[0], spot[1], now, false, 3 + R.nextInt(3));
        float W = w.nomW, H = w.nomH;
        double ang = (R.nextBoolean() ? 1 : -1) * rnd(0.45f, 1.15f) + (R.nextFloat() < 0.15f ? Math.PI / 2 : 0);
        float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
        float diag = Mth.sqrt(W * W + H * H);
        float len = Math.min(diag * 0.85f, rnd(0.75f, 1.05f) * diag * 0.7f);
        float cx = rnd(-W / 2 + 0.5f, W / 2 - 0.5f) * 0.4f, cy = rnd(-H / 2 + 0.5f, H / 2 - 0.5f) * 0.55f;
        float px = -dy, py = dx, j = 0;
        float wMax = rnd(0.7f, 0.95f);
        for (int i = 0; i < PTS; i++) {
            float t = i / (float) (PTS - 1);
            j += (R.nextFloat() - 0.5f) * 0.12f;
            j *= 0.88f;
            w.u[i] = cx + dx * (t - 0.5f) * len + px * j;
            w.v[i] = cy + dy * (t - 0.5f) * len + py * j;
            w.t[i] = t;
            w.w[i] = wMax * (float) Math.pow(Math.sin(Math.PI * t), 0.65) * (0.75f + 0.25f * Mth.sin(t * 9 + wMax * 20));
        }
        w.finish();
        return w;
    }

    // The neck cut: a band round the bottom of the head, one piece on each of its four faces.
    private static Wound[] ring(float now) {
        Wound[] ring = new Wound[4];
        for (int f = 0; f < 4; f++) {
            Wound w = new Wound(5, f, now, true, 2);
            float j = 0;
            for (int i = 0; i < PTS; i++) {
                float t = i / (float) (PTS - 1);
                j += (R.nextFloat() - 0.5f) * 0.14f;
                j *= 0.7f;
                w.u[i] = (t - 0.5f) * w.nomW * 1.02f;
                w.v[i] = -w.nomH / 2 + 0.9f + j;
                w.t[i] = t;
                w.w[i] = 0.55f * (0.8f + 0.2f * Mth.sin(t * 11 + f));
            }
            w.finish();
            ring[f] = w;
        }
        return ring;
    }

    // ---------- noise (the preview's shader functions) ----------
    private static float h1(float n) {
        float x = (float) (Math.sin(n) * 43758.5453);
        return x - Mth.floor(x);
    }

    static float vn(float x) {
        float i = Mth.floor(x), f = x - i;
        f = f * f * (3 - 2 * f);
        return Mth.lerp(f, h1(i), h1(i + 1));
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ---------- the layer ----------
    public static class Layer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        public Layer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            State s = STATES.get(entity.getId());
            if (s == null) {
                return;
            }
            NichirinBurn.render(s, entity, poseStack, buffer, packedLight, getParentModel(), partialTick);
        }
    }

    private static final Vector3f TMP = new Vector3f();

    private static void render(State s, LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidModel<?> model,
            float partialTick) {
        float time = now(entity.level().getGameTime()) + partialTick;
        // the GUI's little body (inventory) is drawn flat: the burns yes, but the world's embers and steam no
        boolean world = RenderSystem.getVertexSorting() != VertexSorting.ORTHOGRAPHIC_Z;
        float dt = 0;
        if (world) {
            dt = s.lastT < 0 ? 0 : Mth.clamp((time - s.lastT) / 20f, 0, 0.1f);
            s.lastT = time;
        }
        if (time >= s.until && !s.wounds.isEmpty()) {
            s.wounds.clear();
        }
        boolean neckOn = s.neck != null && time < s.neckUntil;
        if (s.wounds.isEmpty() && !neckOn && s.embers.isEmpty()) {
            STATES.remove(entity.getId());
            return;
        }
        if (entity.isInvisible()) {
            return;
        }
        int count = s.wounds.size();
        float sec = time / 20f;
        float cool = Mth.clamp((s.until - time) / COOL, 0, 1);
        float pulse = count >= MAX ? (float) Math.pow(0.5 + 0.5 * Mth.sin(sec * 5.5f), 2) : 0;
        float regen = neckOn ? 0 : Math.max(0, 1 - 0.2f * count);
        float light = 0.35f + 0.65f * Math.max(LightTexture.block(packedLight), LightTexture.sky(packedLight)) / 15f;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();

        List<Wound> all = new ArrayList<>(s.wounds);
        if (neckOn) {
            for (Wound w : s.neck) {
                all.add(w);
            }
        }
        VertexConsumer area = null;
        for (Wound w : all) {
            ModelPart part = part(model, w.part);
            if (part == null || !part.visible) {
                continue;
            }
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            Face face = face(part, w);
            if (face != null) {
                if (area == null) {
                    area = buffer.getBuffer(FxRenderTypes.AREA);
                }
                float draw = Math.min(1, (time - w.born) / 3f);
                float heat = w.permanent ? (s.neckUntil == Float.MAX_VALUE ? 1 : Mth.clamp((s.neckUntil - time) / COOL, 0, 1)) : cool;
                charred(area, poseStack.last(), face, w, draw, heat, Math.min(1, heat * 1.6f), light);
            }
            poseStack.popPose();
        }
        VertexConsumer glow = null;
        for (Wound w : all) {
            ModelPart part = part(model, w.part);
            if (part == null || !part.visible) {
                continue;
            }
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            Face face = face(part, w);
            if (face != null) {
                if (glow == null) {
                    glow = buffer.getBuffer(FxRenderTypes.GLOW);
                }
                float age = (time - w.born) / 20f;
                float draw = Math.min(1, (time - w.born) / 3f);
                float heat = w.permanent ? (s.neckUntil == Float.MAX_VALUE ? 1 : Mth.clamp((s.neckUntil - time) / COOL, 0, 1)) : cool;
                float boost = w.permanent ? 1.25f : (0.78f + 0.06f * count) * (heat > 0 ? 1 : 0) + (age < 0.35f ? (0.35f - age) * 2 : 0);
                float pul = w.permanent ? 0.4f + 0.4f * Mth.sin(sec * 3) : pulse;
                halo(glow, poseStack.last(), face, w, draw, heat, boost, pul, sec);
                seam(glow, poseStack.last(), face, w, draw, heat, boost, pul, sec);
                if (world && draw >= 1) {
                    emit(s, w, face, poseStack.last(), cam, dt, heat, count, regen, entity);
                }
            }
            poseStack.popPose();
        }
        if (world) {
            s.neckBurst = false;
            embers(s, buffer, camera, cam, dt);
        }
    }

    private static ModelPart part(HumanoidModel<?> model, int part) {
        return switch (part) {
            case 0 -> model.body;
            case 1 -> model.rightArm;
            case 2 -> model.leftArm;
            case 3 -> model.rightLeg;
            case 4 -> model.leftLeg;
            default -> model.head;
        };
    }

    // A face of the part's cube, in pixels: centre, the two axes along it, the normal out, the size, and the scale from
    // the nominal size the path was laid out on.
    private record Face(float cx, float cy, float cz, float ux, float uy, float uz, float vx, float vy, float vz, float nx, float ny, float nz, float W,
            float H, float su, float sv) {
        void at(float u, float v, float lift, Vector3f out) {
            out.set((cx + ux * u + vx * v + nx * lift) / 16f, (cy + uy * u + vy * v + ny * lift) / 16f, (cz + uz * u + vz * v + nz * lift) / 16f);
        }
    }

    private static Face face(ModelPart part, Wound w) {
        ModelPart.Cube c;
        try {
            c = part.getRandomCube(R); // body parts have one cube
        } catch (RuntimeException e) {
            return null;
        }
        float mx = (c.minX + c.maxX) / 2, my = (c.minY + c.maxY) / 2, mz = (c.minZ + c.maxZ) / 2;
        float wx = c.maxX - c.minX, hy = c.maxY - c.minY, dz = c.maxZ - c.minZ;
        // model space: y points down, the front is -z
        return switch (w.face) {
            case 0 -> new Face(mx, my, c.minZ, 1, 0, 0, 0, -1, 0, 0, 0, -1, wx, hy, wx / w.nomW, hy / w.nomH);
            case 1 -> new Face(mx, my, c.maxZ, -1, 0, 0, 0, -1, 0, 0, 0, 1, wx, hy, wx / w.nomW, hy / w.nomH);
            case 2 -> new Face(c.maxX, my, mz, 0, 0, 1, 0, -1, 0, 1, 0, 0, dz, hy, dz / w.nomW, hy / w.nomH);
            default -> new Face(c.minX, my, mz, 0, 0, -1, 0, -1, 0, -1, 0, 0, dz, hy, dz / w.nomW, hy / w.nomH);
        };
    }

    // A point across the path at sample i, on the face (clamped to it: nothing hangs off the body), in block units.
    private static void across(Face f, Wound w, int i, float off, float lift, Vector3f out) {
        float u = Mth.clamp(w.u[i] * f.su + w.nu[i] * off, -f.W / 2, f.W / 2);
        float v = Mth.clamp(w.v[i] * f.sv + w.nv[i] * off, -f.H / 2, f.H / 2);
        f.at(u, v, lift, out);
    }

    private static float taper(float t) {
        return smooth(0, 0.08f, t) * smooth(1, 0.92f, t);
    }

    private static int last(Wound w, float draw) {
        int n = 1;
        while (n < PTS - 1 && w.t[n] <= draw) {
            n++;
        }
        return n;
    }

    private static final float[] CHAR_ROWS = { -1, -0.5f, 0, 0.5f, 1 };
    private static final float[] SEAM_ROWS = { -1, -0.45f, 0, 0.45f, 1 };
    private static final float[] HALO_ROWS = { -1, 0, 1 };
    // overlay layers (jacket, sleeves, pants) sit 0.25 px off the body: the burn goes just over them
    private static final float LIFT_CHAR = 0.30f, LIFT_HALO = 0.31f, LIFT_SEAM = 0.33f;

    // The skin round the seam: raw red flesh near it, burnt brown further out, a gnawed edge, blisters.
    private static void charred(VertexConsumer out, PoseStack.Pose pose, Face f, Wound w, float draw, float heat, float fade, float light) {
        if (fade <= 0) {
            return;
        }
        int n = last(w, draw + 0.04f);
        Vector3f p = TMP;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < CHAR_ROWS.length - 1; k++) {
                int[] ii = { i, i + 1, i + 1, i };
                int[] kk = { k, k, k + 1, k + 1 };
                for (int q = 0; q < 4; q++) {
                    int si = ii[q];
                    float x = CHAR_ROWS[kk[q]];
                    float e = Math.abs(x);
                    float rough = x < 0 ? w.roughL[si] : w.roughR[si];
                    float half = (w.w[si] * 2.2f + 0.4f) * rough;
                    float nse = w.mottle[si * 2 + (x < 0 ? 0 : 1)];
                    float bl = smooth(0.15f, 0.55f, e + nse * 0.2f - 0.1f);
                    float r = Mth.lerp(bl, 0.42f, 0.09f) * light, g = Mth.lerp(bl, 0.07f, 0.04f) * light, b = Mth.lerp(bl, 0.05f, 0.03f) * light;
                    float a = 0.86f * taper(w.t[si]) * fade;
                    across(f, w, si, x * half, LIFT_CHAR, p);
                    out.addVertex(pose, p.x, p.y, p.z).setColor(r, g, b, a);
                }
            }
        }
        // blisters: little raw diamonds by the seam
        for (int b = 0; b < w.blister.length / 3; b++) {
            float t = w.blister[b * 3];
            if (t > draw) {
                continue;
            }
            int i = Math.min(PTS - 1, Math.round(t * (PTS - 1)));
            float off = w.blister[b * 3 + 1] * w.w[i];
            float sz = w.blister[b * 3 + 2];
            float u = w.u[i] * f.su + w.nu[i] * off, v = w.v[i] * f.sv + w.nv[i] * off;
            float a = 0.7f * heat * fade;
            float cr = 0.7f * light, cg = 0.25f * light, cb = 0.12f * light;
            float[][] d = { { 0, -sz }, { sz, 0 }, { 0, sz }, { -sz, 0 } };
            for (float[] o : d) {
                f.at(Mth.clamp(u + o[0], -f.W / 2, f.W / 2), Mth.clamp(v + o[1], -f.H / 2, f.H / 2), LIFT_CHAR + 0.005f, p);
                out.addVertex(pose, p.x, p.y, p.z).setColor(cr, cg, cb, a);
            }
        }
    }

    // The soft warm glow round the seam.
    private static void halo(VertexConsumer out, PoseStack.Pose pose, Face f, Wound w, float draw, float heat, float boost, float pulse, float sec) {
        if (heat <= 0) {
            return;
        }
        int n = last(w, draw);
        Vector3f p = TMP;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < HALO_ROWS.length - 1; k++) {
                int[] ii = { i, i + 1, i + 1, i };
                int[] kk = { k, k, k + 1, k + 1 };
                for (int q = 0; q < 4; q++) {
                    int si = ii[q];
                    float x = HALO_ROWS[kk[q]];
                    float e = Math.abs(x);
                    float soft = (1 - smooth(0, 1, e));
                    soft *= soft;
                    float flick = 0.72f + 0.28f * vn(w.t[si] * 9 - sec * 5 + w.seed * 3);
                    float a = soft * 0.28f * flick * (boost + pulse * 0.6f) * heat * taper(w.t[si]);
                    float mix = 0.6f * heat;
                    across(f, w, si, x * (w.w[si] * 3.0f + 0.9f), LIFT_HALO, p);
                    out.addVertex(pose, p.x, p.y, p.z).setColor(Mth.lerp(mix, 0.62f, 1f), Mth.lerp(mix, 0.07f, 0.42f), Mth.lerp(mix, 0.03f, 0.08f),
                            Mth.clamp(a, 0, 1));
                }
            }
        }
    }

    // The seam itself: gold-white inside, orange and red at the edges, embers crawling along, a burning tip while it
    // draws itself in.
    private static void seam(VertexConsumer out, PoseStack.Pose pose, Face f, Wound w, float draw, float heat, float boost, float pulse, float sec) {
        int n = last(w, draw);
        Vector3f p = TMP;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < SEAM_ROWS.length - 1; k++) {
                int[] ii = { i, i + 1, i + 1, i };
                int[] kk = { k, k, k + 1, k + 1 };
                for (int q = 0; q < 4; q++) {
                    int si = ii[q];
                    float t = w.t[si];
                    float x = SEAM_ROWS[kk[q]];
                    float e = Math.abs(x);
                    float rag = vn(t * 18 + w.seed * 7) * 0.35f;
                    float core = 1 - smooth(0, 0.42f + rag * 0.4f, e);
                    core *= 0.45f + 0.55f * smooth(0.25f, 0.7f, vn(t * 6 + w.seed * 13 + sec * 0.4f));
                    float rim = 1 - smooth(0.25f, 1, e + rag * 0.5f);
                    float flick = 0.72f + 0.28f * vn(t * 9 - sec * 5 + w.seed * 3);
                    float crawl = smooth(0.82f, 1, Mth.sin(t * 34 - sec * 7 + w.seed * 11)) * core;
                    float head = draw < 1 ? smooth(0.12f, 0, draw - t) * 1.6f : 0;
                    // red -> orange (rim), -> gold (core), -> white (hottest)
                    float m1 = rim * heat, m2 = core * heat, m3 = Mth.clamp((core * core * 0.7f + crawl + head) * heat * heat, 0, 1);
                    float r = Mth.lerp(m1, 0.62f, 1f), g = Mth.lerp(m1, 0.07f, 0.42f), b = Mth.lerp(m1, 0.03f, 0.08f);
                    r = Mth.lerp(m2, r, 1f);
                    g = Mth.lerp(m2, g, 0.86f);
                    b = Mth.lerp(m2, b, 0.45f);
                    r = Mth.lerp(m3, r, 1f);
                    g = Mth.lerp(m3, g, 0.97f);
                    b = Mth.lerp(m3, b, 0.84f);
                    float a = (rim * 0.55f + core * 0.9f + crawl * 0.6f + head) * flick * (boost + pulse * 0.35f);
                    a *= taper(t) * Mth.lerp(heat, 0.25f, 1f);
                    if (a > 1) {
                        // past full: brighter instead (the preview adds it)
                        r = Math.min(1, r * a);
                        g = Math.min(1, g * a);
                        b = Math.min(1, b * a);
                        a = 1;
                    }
                    float off = x * w.w[si];
                    across(f, w, si, off, LIFT_SEAM, p);
                    out.addVertex(pose, p.x, p.y, p.z).setColor(r, g, b, Math.max(0, a));
                }
            }
        }
    }

    // ---------- embers and steam ----------
    private static final Vector3f NRM = new Vector3f();

    private static void emit(State s, Wound w, Face f, PoseStack.Pose pose, Vec3 cam, float dt, float heat, int count, float regen, LivingEntity entity) {
        boolean burst = !w.burst || (w.permanent && s.neckBurst);
        if (burst) {
            w.burst = true;
            int n = w.permanent ? 55 : 26;
            for (int i = 0; i < n; i++) {
                ember(s, w, f, pose, cam, true);
            }
            if (!w.permanent) {
                for (int i = 0; i < 4; i++) {
                    steam(w, f, pose, cam, entity);
                }
            }
        }
        if (dt <= 0) {
            return;
        }
        w.emberAcc += dt * (w.permanent ? 9 : 4 + 2.2f * count) * heat;
        while (w.emberAcc > 1) {
            w.emberAcc--;
            ember(s, w, f, pose, cam, false);
        }
        // the steam is the regeneration fighting the wound: none once it is dead (5 wounds, or the neck cut)
        if (!w.permanent) {
            w.steamAcc += dt * 3.2f * regen * heat;
            while (w.steamAcc > 1) {
                w.steamAcc--;
                steam(w, f, pose, cam, entity);
            }
        }
    }

    // A random point on the seam, in the world; NRM gets the face's normal there.
    private static Vec3 seamPoint(Wound w, Face f, PoseStack.Pose pose, Vec3 cam) {
        float pos = R.nextFloat() * (PTS - 1);
        int i = Math.min(PTS - 2, (int) pos);
        float k = pos - i;
        float u = Mth.clamp(Mth.lerp(k, w.u[i], w.u[i + 1]) * f.su, -f.W / 2, f.W / 2);
        float v = Mth.clamp(Mth.lerp(k, w.v[i], w.v[i + 1]) * f.sv, -f.H / 2, f.H / 2);
        Vector3f p = new Vector3f();
        f.at(u, v, 0.4f, p);
        Matrix4f m = pose.pose();
        m.transformPosition(p);
        Matrix3f nm = pose.normal();
        NRM.set(f.nx, f.ny, f.nz);
        nm.transform(NRM);
        NRM.normalize();
        // the entity's pose is relative to the camera
        return new Vec3(p.x + cam.x, p.y + cam.y, p.z + cam.z);
    }

    private static void ember(State s, Wound w, Face f, PoseStack.Pose pose, Vec3 cam, boolean burst) {
        if (s.embers.size() >= MAX_EMBERS) {
            return;
        }
        Vec3 at = seamPoint(w, f, pose, cam);
        Ember e = new Ember();
        e.x = at.x;
        e.y = at.y;
        e.z = at.z;
        float out = burst ? rnd(4, 14) : 0;
        e.vx = NRM.x * rnd(2, 6) + rnd(-1.5f, 1.5f) + NRM.x * out;
        e.vy = rnd(3, 7) + (burst ? rnd(2, 8) : 0) + NRM.y * out;
        e.vz = NRM.z * rnd(2, 6) + rnd(-1.5f, 1.5f) + NRM.z * out;
        e.life = rnd(0.5f, 1.3f) * (burst ? 1.2f : 1);
        e.size = rnd(0.45f, 0.95f);
        e.ph = R.nextFloat() * 6;
        s.embers.add(e);
    }

    private static void steam(Wound w, Face f, PoseStack.Pose pose, Vec3 cam, LivingEntity entity) {
        Vec3 at = seamPoint(w, f, pose, cam);
        float out = 1.5f / 16f / 20f;
        int life = 22 + R.nextInt(16);
        entity.level().addParticle(ModParticles.fx(SmokeOptions.BILLOW, 0xE6E2DE, 0.32f, rnd(0.07f, 0.11f), 2.2f, life, 0.96f, 0, 1, 0),
                at.x, at.y, at.z, NRM.x * out + rnd(-0.6f, 0.6f) / 320f, rnd(3, 5.5f) / 320f, NRM.z * out + rnd(-0.6f, 0.6f) / 320f);
    }

    // Square embers (a pixel each, like the game's), gold -> orange -> red -> out, drifting up and curling.
    private static void embers(State s, MultiBufferSource buffer, Camera camera, Vec3 cam, float dt) {
        if (s.embers.isEmpty()) {
            return;
        }
        VertexConsumer out = buffer.getBuffer(FxRenderTypes.GLOW);
        Quaternionf rot = camera.rotation();
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f(), d = new Vector3f();
        for (int k = s.embers.size() - 1; k >= 0; k--) {
            Ember e = s.embers.get(k);
            if (dt > 0) {
                e.age += dt;
                if (e.age >= e.life) {
                    s.embers.remove(k);
                    continue;
                }
                e.vy += 4 * dt;
                float drag = 1 - 1.6f * dt;
                e.vx *= drag;
                e.vy *= drag;
                e.vz *= drag;
                e.vx += Mth.sin(e.ph + e.age * 7) * 6 * dt;
                e.vz += Mth.cos(e.ph + e.age * 6) * 6 * dt;
                // the preview's units are pixels: a block is 16
                e.x += e.vx * dt / 16;
                e.y += e.vy * dt / 16;
                e.z += e.vz * dt / 16;
            }
            float t = e.age / e.life;
            float r, g, bl;
            if (t < 0.3f) {
                r = 1;
                g = 0.88f;
                bl = 0.5f - t;
            } else if (t < 0.7f) {
                r = 1;
                g = 0.55f - (t - 0.3f) * 0.6f;
                bl = 0.12f;
            } else {
                r = 0.75f;
                g = 0.18f;
                bl = 0.05f;
            }
            float alpha = (t < 0.1f ? t / 0.1f : 1) * (1 - (float) Math.pow(t, 1.5)) * (0.7f + 0.3f * Mth.sin(e.age * 40 + e.ph));
            float sz = e.size * (1 - t * 0.5f) / 16f / 2f;
            float x = (float) (e.x - cam.x), y = (float) (e.y - cam.y), z = (float) (e.z - cam.z);
            rot.transform(a.set(-sz, -sz, 0)).add(x, y, z);
            rot.transform(b.set(sz, -sz, 0)).add(x, y, z);
            rot.transform(c.set(sz, sz, 0)).add(x, y, z);
            rot.transform(d.set(-sz, sz, 0)).add(x, y, z);
            out.addVertex(a.x, a.y, a.z).setColor(r, g, bl, Math.max(0, alpha));
            out.addVertex(b.x, b.y, b.z).setColor(r, g, bl, Math.max(0, alpha));
            out.addVertex(c.x, c.y, c.z).setColor(r, g, bl, Math.max(0, alpha));
            out.addVertex(d.x, d.y, d.z).setColor(r, g, bl, Math.max(0, alpha));
        }
    }
}
