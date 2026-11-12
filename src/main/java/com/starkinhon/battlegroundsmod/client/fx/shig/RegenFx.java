package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Shift+Z, Regeneração (preview v6): he hunches over and the body remakes itself. The lost right arm braids back out of
// the shoulder from 30 muscle fibres, each its own path, thickness and twist (0.3 to 1.7 s), the skin comes down over
// them (1.5 to 2.8 s); the wounds on the chest, the belly and the back are sewn from inside and close; the heart beats
// (tum-tum, 1.55 a second) in the fibres' glow and in a red vignette for him. The arm and the body are the real model's
// (their pose this frame, read when the body is drawn), so the fibres follow the animation.
public final class RegenFx {
    private static final float LEN = 4.4f;

    private static final class Fiber {
        float[][] pts;
        float r, delay, seed;
    }

    private static final class Wound {
        float x, y, z, w, h, seed, at;
        boolean back;
        List<Fiber> stitches = new ArrayList<>();
    }

    private static final List<Fiber> FIBERS = new ArrayList<>();
    private static final List<Wound> WOUNDS = new ArrayList<>();

    private static final class R {
        final double start;
        double cutAt = -1;
        Matrix4f arm, body;

        R(double start) {
            this.start = start;
        }
    }

    private static final Map<Integer, R> ACTIVE = new HashMap<>();

    static {
        HandGen.Rng r = new HandGen.Rng(31337);
        for (int i = 0; i < 30; i++) {
            float a0 = (float) (r.next() * Math.PI * 2), rr = 0.045f + 0.08f * r.next(), tw = (r.next() - 0.5f) * 3.2f, end = -0.62f - 0.12f * r.next();
            float[][] pts = new float[10][];
            for (int k = 0; k <= 9; k++) {
                float u = k / 9f;
                float a = a0 + tw * u + (float) Math.sin(u * 7 + i) * 0.25f;
                float bul = 1 + 0.35f * (float) Math.sin(u * Math.PI * (1 + r.next())) * (0.4f + 0.6f * r.next());
                float rad = rr * bul * (u < 0.12f ? 0.6f + u * 3.3f : 1) * (u > 0.85f ? 1 - (u - 0.85f) * 2.5f : 1);
                pts[k] = new float[] {(float) Math.cos(a) * rad, 0.02f + (end - 0.02f) * u, (float) Math.sin(a) * rad * 0.9f};
            }
            Fiber f = new Fiber();
            f.pts = pts;
            f.r = 0.014f + 0.022f * r.next();
            f.delay = r.next() * 0.45f;
            f.seed = i * 7.3f + 1;
            FIBERS.add(f);
        }
        // chest, belly, back (the preview's addWound, in the body part's frame: x to his left, y down from the neck, z back)
        wound(0.08f, 0.3f, -0.128f, false, 0.26f, 0.2f, 11);
        wound(-0.12f, 0.58f, -0.128f, false, 0.18f, 0.22f, 23);
        wound(0.1f, 0.45f, 0.135f, true, 0.22f, 0.24f, 37);
    }

    private static void wound(float x, float y, float z, boolean back, float w, float h, float seed) {
        Wound wd = new Wound();
        wd.x = x;
        wd.y = y;
        wd.z = z;
        wd.back = back;
        wd.w = w;
        wd.h = h;
        wd.seed = seed;
        wd.at = 0.4f + 0.5f * WOUNDS.size();
        HandGen.Rng r = new HandGen.Rng((long) (seed * 977 + 5));
        int n = 4 + (int) Math.floor(r.next() * 3);
        for (int i = 0; i < n; i++) {
            float a = (float) ((i / (double) n) * Math.PI + (r.next() - 0.5f) * 0.4f), ex = (float) Math.cos(a) * w * 0.55f, ey = (float) Math.sin(a) * h * 0.55f,
                    off = (r.next() - 0.5f) * 0.3f;
            float[][] pts = new float[7][];
            for (int k = 0; k <= 6; k++) {
                float u = k / 6f;
                pts[k] = new float[] {-ex + 2 * ex * u + off * ey, -ey + 2 * ey * u - off * ex, 0.012f + 0.035f * (float) Math.sin(u * Math.PI) * (0.6f + 0.6f * r.next())};
            }
            Fiber f = new Fiber();
            f.pts = pts;
            f.r = 0.008f + 0.008f * r.next();
            f.delay = r.next() * 0.4f;
            f.seed = seed + i * 1.7f;
            wd.stitches.add(f);
        }
        WOUNDS.add(wd);
    }

    private RegenFx() {
    }

    static boolean active() {
        return !ACTIVE.isEmpty();
    }

    static void clear() {
        ACTIVE.clear();
    }

    // Shigaraki.REGEN: b = 1 cut short.
    static void receive(int id, float b) {
        if (b < 0.5f) {
            ACTIVE.put(id, new R(ShigFx.clock()));
        } else {
            R r = ACTIVE.get(id);
            if (r != null && r.cutAt < 0) {
                r.cutAt = ShigFx.clock();
            }
        }
    }

    // tum-tum
    static float hb(float t) {
        float ph = (t * 1.55f) % 1;
        return (float) Math.exp(-ph * 18) + (ph > 0.16f ? 0.6f * (float) Math.exp(-(ph - 0.16f) * 22) : 0);
    }

    private static float time(R r, double now) {
        float t = (float) (now - r.start);
        // cut short: the arm finishes at once
        return r.cutAt >= 0 ? Math.max(t, 3.4f + (float) (now - r.cutAt)) : t;
    }

    static void tick() {
        double now = ShigFx.clock();
        ACTIVE.values().removeIf(r -> time(r, now) > LEN + 0.2f);
        var me = Minecraft.getInstance().player;
        R r = me == null ? null : ACTIVE.get(me.getId());
        if (r != null) {
            float t = time(r, now);
            float v = (0.25f + 0.75f * hb(t)) * (1 - ShigDraw.smooth((t - 3.6f) / 0.6f));
            ScreenFx.vignette(0xB0101A, 0.55f * v, 3);
        }
    }

    // ---------- the real body: hide the arm while it is gone, grow it back, read where the parts are ----------

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderPre(RenderLivingEvent.Pre event) {
        R r = ACTIVE.get(event.getEntity().getId());
        if (r == null || !(event.getRenderer().getModel() instanceof HumanoidModel<?> m)) {
            return;
        }
        m.rightArm.visible = false;
        if (m instanceof PlayerModel<?> p) {
            p.rightSleeve.visible = false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderPost(RenderLivingEvent.Post event) {
        LivingEntity e = event.getEntity();
        R r = ACTIVE.get(e.getId());
        if (r == null || !(event.getRenderer().getModel() instanceof HumanoidModel<?> m)) {
            return;
        }
        float t = time(r, ShigFx.clock());
        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float bodyRot = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180 - bodyRot));
        ps.scale(-1, -1, 1);
        float s = 0.9375f * e.getScale();
        ps.scale(s, s, s);
        ps.translate(0, -1.501, 0);
        // the skin coming back over the fibres, from the shoulder down
        float skin = ShigDraw.smooth((t - 1.5f) / 1.3f);
        m.rightArm.visible = true;
        PlayerModel<?> pm = m instanceof PlayerModel<?> p ? p : null;
        if (pm != null) {
            pm.rightSleeve.visible = true;
        }
        if (skin > 0.001f) {
            ResourceLocation tex = ((LivingEntityRenderer) event.getRenderer()).getTextureLocation(e);
            var buf = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(tex));
            float ys = m.rightArm.yScale;
            m.rightArm.yScale = Math.max(skin, 0.001f);
            m.rightArm.render(ps, buf, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            m.rightArm.yScale = ys;
            if (pm != null) {
                float ss = pm.rightSleeve.yScale;
                pm.rightSleeve.yScale = Math.max(skin, 0.001f);
                pm.rightSleeve.render(ps, buf, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                pm.rightSleeve.yScale = ss;
            }
        }
        ps.pushPose();
        m.rightArm.translateAndRotate(ps);
        r.arm = new Matrix4f(ps.last().pose());
        ps.popPose();
        ps.pushPose();
        m.body.translateAndRotate(ps);
        r.body = new Matrix4f(ps.last().pose());
        ps.popPose();
        ps.popPose();
    }

    // ---------- drawing (with the world effects, from the matrices read above) ----------

    static void render(Vec3 camera) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        double now = ShigFx.clock();
        ShaderInstance fs = ShigShaders.fiber, ws = ShigShaders.wound;
        var level = Minecraft.getInstance().level;
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        for (Map.Entry<Integer, R> en : ACTIVE.entrySet()) {
            R r = en.getValue();
            Entity ent = level == null ? null : level.getEntity(en.getKey());
            if (r.arm == null || ent == null || (firstPerson && ent == Minecraft.getInstance().player)) {
                continue;
            }
            float t = time(r, now), hbv = hb(t);
            float g = ShigDraw.smooth((t - 0.3f) / 1.4f), skin = ShigDraw.smooth((t - 1.5f) / 1.3f);
            if (fs != null && t < 3.3f) {
                fs.safeGetUniform("G").set(g);
                fs.safeGetUniform("HB").set(hbv);
                fs.safeGetUniform("Cut").set(0.055f - 0.78f * skin + 0.02f);
                fs.safeGetUniform("A").set(1 - ShigDraw.smooth((t - 2.7f) / 0.5f));
                BufferBuilder b = ShigDraw.begin();
                for (Fiber f : FIBERS) {
                    fiber(b, f, r.arm, true);
                }
                ShigDraw.draw(b, fs, ShigDraw.OPAQUE, false, false);
            }
            if (ws != null && t < 1.2f) {
                // the stump at the shoulder while the arm is not back
                ws.safeGetUniform("S").set(1 - ShigDraw.smooth((t - 0.5f) / 0.7f));
                ws.safeGetUniform("Seed").set(51f);
                ws.safeGetUniform("HB").set(hbv);
                BufferBuilder b = ShigDraw.begin();
                plane(b, r.arm, -1 / 16f, 0, 0, 0.3f, 0.3f, true);
                ShigDraw.draw(b, ws, ShigDraw.OPAQUE, false, false);
            }
            if (r.body != null) {
                for (Wound w : WOUNDS) {
                    float k = ShigDraw.smooth((t - w.at) / 1.1f), sk = ShigDraw.smooth((t - w.at - 0.25f) / 1.3f);
                    if (sk >= 0.999f) {
                        continue;
                    }
                    if (ws != null) {
                        ws.safeGetUniform("S").set(1 - sk);
                        ws.safeGetUniform("Seed").set(w.seed);
                        ws.safeGetUniform("HB").set(hbv);
                        BufferBuilder b = ShigDraw.begin();
                        plane(b, r.body, w.x, w.y, w.z, w.w, w.h, false);
                        ShigDraw.draw(b, ws, ShigDraw.OPAQUE, false, false);
                    }
                    if (fs != null) {
                        fs.safeGetUniform("G").set(k);
                        fs.safeGetUniform("HB").set(hbv);
                        fs.safeGetUniform("Cut").set(9f);
                        fs.safeGetUniform("A").set(1 - ShigDraw.smooth((sk - 0.6f) / 0.4f));
                        BufferBuilder b = ShigDraw.begin();
                        Matrix4f m = new Matrix4f(r.body).translate(w.x, w.y, w.z);
                        if (w.back) {
                            m.rotateY((float) Math.PI);
                        }
                        // the stitches stand out of the skin towards the front (the plane's +z is out of the body: -z of the part)
                        m.scale(1, -1, -1);
                        for (Fiber f : w.stitches) {
                            fiber(b, f, m, false);
                        }
                        ShigDraw.draw(b, fs, ShigDraw.OPAQUE, false, false);
                    }
                }
            }
        }
    }

    // A fibre as a tube (TubeGeometry 48 x 7) in a part's frame. arm: the preview's arm frame (y up the arm) turned into
    // the model's (y down), and its height kept for the skin's cut.
    private static void fiber(BufferBuilder b, Fiber f, Matrix4f m, boolean arm) {
        float[][] pts = new float[f.pts.length][];
        for (int i = 0; i < pts.length; i++) {
            float[] p = f.pts[i];
            pts[i] = arm ? new float[] {p[0] - 1 / 16f, -p[1], p[2]} : p.clone();
        }
        Matrix3f n3 = new Matrix3f(m).invert().transpose();
        int u1 = Math.round(f.delay * 10000), v1 = Math.round(f.seed * 100);
        ShigDraw.tube(b, pts, 48, 7, u -> f.r, (q, x, y, z, nx, ny, nz, u, v) -> {
            Vector3f p = m.transformPosition(new Vector3f(x, y, z));
            Vector3f n = n3.transform(new Vector3f(nx, ny, nz)).normalize();
            float ly = arm ? -y : 0;
            ShigDraw.v(q, p.x, p.y, p.z, 1, 1, 1, 1, u, v, u1, v1, Math.round((ly + 2) * 1000), 0, n.x, n.y, n.z);
        });
    }

    // A flat wound: in the arm's frame lying flat at the shoulder; on the body facing out of the front (or the back).
    private static void plane(BufferBuilder b, Matrix4f m, float x, float y, float z, float w, float h, boolean flat) {
        float[][] c = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int i : order) {
            Vector3f p = flat ? new Vector3f(x + c[i][0], y, z + c[i][1]) : new Vector3f(x + c[i][0], y - c[i][1], z);
            m.transformPosition(p);
            ShigDraw.v(b, p.x, p.y, p.z, 1, 1, 1, 1, uv[i][0], uv[i][1], 0, 0, 0, 0, 0, 1, 0);
        }
    }
}
