package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// The combat flight's effects, the approved page's own (forja db-voo-mobs/combate.html, Stark 2026-11-13: "deixa os
// efeitos do nosso mod normal. Só que quando entrar nesse modo de voo combate, deixa os mesmos efeitos da prévia"):
// ringAt (souls_ki 18), starAt (19), dustAt (20), craterAt (24), the ki balls (21 core + 22 halo, the tail a trail strip),
// the beam (23), ghostOf (the body held still, see-through, fading in 0.4 s) and the screen (shockAt, the speed lines,
// impactFrame) in one pass, fx_pvscreen. client/PreviewFight starts them at the page's moments and steps them with the
// page's clock (slowed by the hitstop), and they live on after the fight lets go.
public final class PreviewFx {
    public static final float[] WHITE = {1, 1, 1};
    private static final int RING = 0, STAR = 1, DUST = 2, CRATER = 3;

    private static final class Piece {
        int kind;
        Vec3 p, n;
        double s, t, life;
        float[] col;
        float seed;
        Quaternionf rot;
    }

    // a ki ball (PreviewFight moves it); the tail is where it has been (newest first)
    public static final class Ball {
        public Vec3 pos = Vec3.ZERO;
        public float size = 0.32f;
        public float[] col = WHITE;
        public final float seed = (float) (Math.random() * 20);
        public final ArrayDeque<Vec3> tail = new ArrayDeque<>();
        public boolean tailed = true;

        public void push() {
            tail.addFirst(pos);
            while (tail.size() > 18) {
                tail.removeLast();
            }
        }
    }

    // a beam (the page's fireBeam): from, dir, its length now, radius, width wobble and fade
    public static final class Beam {
        public Vec3 from = Vec3.ZERO, dir = new Vec3(0, 0, 1);
        public double len, r = 1.15, w = 1, fade = 1;
        public float[] col = WHITE;
        public final float seed = (float) (Math.random() * 20);
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    public static final List<Ball> BALLS = new ArrayList<>();
    public static final List<Beam> BEAMS = new ArrayList<>();
    // the page's clock (seconds, slowed in the hitstop) for the shaders' time
    public static double clock;

    // the screen: shockwave, speed lines, impact frames
    private static double shkT = 9, shkP = 1;
    private static Vec3 shkAt = Vec3.ZERO;
    public static double linesKick;
    public static double boostView;
    private static final ArrayDeque<Integer> IMPACT = new ArrayDeque<>();
    private static double impactT;

    private PreviewFx() {
    }

    private static float rnd() {
        return (float) Math.random();
    }

    public static void ringAt(Vec3 p, Vec3 n, double s, float[] col, double life) {
        Piece f = new Piece();
        f.kind = RING;
        f.p = p;
        f.n = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : n.normalize();
        f.s = s;
        f.col = col == null ? WHITE : col;
        f.life = life;
        f.seed = rnd() * 50;
        PIECES.add(f);
    }

    public static void ringAt(Vec3 p, Vec3 n, double s, float[] col) {
        ringAt(p, n, s, col, 0.42);
    }

    public static void starAt(Vec3 p, double s, float[] col, double life) {
        Piece f = new Piece();
        f.kind = STAR;
        f.p = p;
        f.s = s;
        f.col = col == null ? WHITE : col;
        f.life = life;
        f.seed = rnd() * 50;
        f.rot = new Quaternionf().rotateXYZ(rnd() * 6, rnd() * 6, rnd() * 6);
        PIECES.add(f);
    }

    public static void starAt(Vec3 p, double s, float[] col) {
        starAt(p, s, col, 0.3);
    }

    // on the ground under p (groundY: the block top there)
    public static void dustAt(Vec3 p, double groundY, double s, double life) {
        Piece f = new Piece();
        f.kind = DUST;
        f.p = new Vec3(p.x, groundY + 0.06, p.z);
        f.s = s;
        f.life = life;
        f.seed = rnd() * 50;
        f.rot = new Quaternionf().rotateY(rnd() * 6);
        PIECES.add(f);
    }

    public static void craterAt(Vec3 p, double groundY) {
        Piece f = new Piece();
        f.kind = CRATER;
        f.p = new Vec3(p.x, groundY + 0.05, p.z);
        f.s = 9;
        f.life = 7;
        f.seed = rnd() * 40;
        f.rot = new Quaternionf().rotateY(rnd() * 6);
        PIECES.add(f);
    }

    public static void shockAt(Vec3 p, double pow) {
        shkT = 0;
        shkAt = p;
        shkP = pow;
    }

    public static void impactFrame(int... seq) {
        IMPACT.clear();
        for (int s : seq) {
            IMPACT.add(s);
        }
        impactT = 0;
    }

    // ------------------------------------------------------------------ ghosts (the page's ghostOf)

    private record Ghost(EntityModel<?> model, ResourceLocation skin, PartPose[] parts, Vec3 pos, Quaternionf turn, float[] col, double[] t) {
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final Map<Integer, float[]> WANT = new HashMap<>();

    // A see-through copy of that body as it is drawn next, in its ki color halfway to white, gone in 0.4 s.
    public static void ghostOf(LivingEntity e, float[] col) {
        WANT.put(e.getId(), col);
    }

    private static ModelPart[] parts(HumanoidModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
    }

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (WANT.isEmpty()) {
            return;
        }
        LivingEntity e = event.getEntity();
        float[] col = WANT.remove(e.getId());
        if (col == null || !(event.getRenderer().getModel() instanceof HumanoidModel<?> model)) {
            return;
        }
        ModelPart[] ps = parts(model);
        PartPose[] poses = new PartPose[ps.length];
        for (int i = 0; i < ps.length; i++) {
            poses[i] = ps[i].storePose();
        }
        float partial = event.getPartialTick();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ResourceLocation skin = ((net.minecraft.client.renderer.entity.EntityRenderer) event.getRenderer()).getTextureLocation(e);
        Quaternionf turn = com.starkinhon.battlegroundsmod.client.FlightPose.bodyTurn(e, partial);
        if (turn == null) {
            turn = new Quaternionf().rotateY((float) Math.toRadians(180f - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
        }
        float[] g = {Mth.lerp(0.5f, col[0], 1), Mth.lerp(0.5f, col[1], 1), Mth.lerp(0.5f, col[2], 1)};
        GHOSTS.add(new Ghost(model, skin, poses, e.getPosition(partial), turn, g, new double[] {0}));
        if (GHOSTS.size() > 40) {
            GHOSTS.remove(0);
        }
    }

    private static void ghosts(Vec3 cam) {
        if (GHOSTS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        for (Ghost g : GHOSTS) {
            float a = (float) (0.55 * (1 - g.t()[0] / 0.4));
            if (a < 0.01f || !(g.model() instanceof HumanoidModel<?> m)) {
                continue;
            }
            ModelPart[] ps = parts(m);
            PartPose[] keep = new PartPose[ps.length];
            for (int i = 0; i < ps.length; i++) {
                keep[i] = ps[i].storePose();
                ps[i].loadPose(g.parts()[i]);
            }
            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            pose.translate(0, com.starkinhon.battlegroundsmod.client.FlightPose.pivot(), 0);
            pose.mulPose(g.turn());
            pose.translate(0, -com.starkinhon.battlegroundsmod.client.FlightPose.pivot(), 0);
            pose.scale(-1, -1, 1);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0, -1.501, 0);
            int color = FastColor.ARGB32.color(Math.round(a * 255), Math.round(g.col()[0] * 255), Math.round(g.col()[1] * 255), Math.round(g.col()[2] * 255));
            m.renderToBuffer(pose, source.getBuffer(RenderType.entityTranslucentEmissive(g.skin())), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            pose.popPose();
            for (int i = 0; i < ps.length; i++) {
                ps[i].loadPose(keep[i]);
            }
        }
        source.endBatch();
    }

    // ------------------------------------------------------------------ the page's frame

    // dt: the page's dt (slowed by the hitstop); camDt: the real one (the screen's own timers run on it).
    public static void step(double dt, double camDt) {
        clock += dt;
        for (Iterator<Piece> it = PIECES.iterator(); it.hasNext();) {
            Piece f = it.next();
            f.t += dt;
            if (f.t >= f.life) {
                it.remove();
            }
        }
        for (Iterator<Ghost> it = GHOSTS.iterator(); it.hasNext();) {
            Ghost g = it.next();
            g.t()[0] += dt;
            if (g.t()[0] > 0.4) {
                it.remove();
            }
        }
        shkT += dt;
        linesKick = Math.max(0, linesKick - camDt * 2.2);
        if (!IMPACT.isEmpty()) {
            impactT += camDt;
            if (impactT > 1 / 24.0) {
                impactT = 0;
                IMPACT.poll();
            }
        }
    }

    public static boolean active() {
        return !PIECES.isEmpty() || !BALLS.isEmpty() || !BEAMS.isEmpty() || !GHOSTS.isEmpty();
    }

    public static void clear() {
        PIECES.clear();
        BALLS.clear();
        BEAMS.clear();
        GHOSTS.clear();
        WANT.clear();
        IMPACT.clear();
        shkT = 9;
        linesKick = 0;
        boostView = 0;
    }

    static void render(Vec3 cam, float partial) {
        ghosts(cam);
        if (!KiMesh.ready()) {
            return;
        }
        float time = (float) clock;
        for (Piece f : PIECES) {
            float L = (float) Math.min(f.t / f.life, 1);
            Vector3f c = f.p.subtract(cam).toVector3f();
            KiMesh.P k;
            switch (f.kind) {
                case RING -> {
                    float s2 = (float) ((1.4 + 7 * (1 - Math.pow(1 - L, 3))) * f.s);
                    k = KiMesh.p(KiMesh.FLIGHT_RING);
                    k.life = L;
                    k.seed = f.seed;
                    k.color = f.col;
                    Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), f.n.toVector3f());
                    KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(c, s2, q), k, KiMesh.BOTH, 12);
                }
                case STAR -> {
                    float s2 = (float) (f.s * (0.5 + 0.8 * (1 - Math.pow(1 - L, 2))));
                    k = KiMesh.p(19);
                    k.life = L;
                    k.seed = f.seed;
                    k.color = f.col;
                    KiMesh.queue(KiMesh.Mesh.BALL, new Matrix4f().translation(c).rotate(f.rot).scale(s2), k, KiMesh.BOTH, 12);
                }
                case DUST -> {
                    float s2 = (float) (f.s * (0.4 + 1.0 * (1 - Math.pow(1 - L, 3))));
                    k = KiMesh.p(20);
                    k.life = L;
                    k.seed = f.seed;
                    k.glow = false;
                    KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(c, s2, f.rot), k, KiMesh.BOTH, 12);
                }
                default -> {
                    k = KiMesh.p(24);
                    k.life = L;
                    k.seed = f.seed;
                    k.glow = false;
                    KiMesh.queue(KiMesh.Mesh.QUAD, KiMesh.disc(c, (float) f.s * 0.5f, f.rot), k, KiMesh.BOTH, 2);
                }
            }
        }
        for (Ball b : BALLS) {
            Vector3f c = b.pos.subtract(cam).toVector3f();
            KiMesh.P k = KiMesh.p(21);
            k.time = time;
            k.seed = b.seed;
            k.color = b.col;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(c, b.size), k, KiMesh.FRONT, 11);
            KiMesh.P h = KiMesh.p(22);
            h.time = time;
            h.seed = b.seed;
            h.color = b.col;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(c, b.size * 1.9f), h, KiMesh.BACK, 11);
            if (b.tailed && b.tail.size() > 1) {
                int n = b.tail.size() + 1;
                Vector3f[] pts = new Vector3f[n];
                float[] widths = new float[n];
                float[] us = new float[n];
                pts[0] = c;
                Iterator<Vec3> it = b.tail.iterator();
                for (int i = 1; i < n; i++) {
                    pts[i] = it.next().subtract(cam).toVector3f();
                }
                for (int i = 0; i < n; i++) {
                    us[i] = i / (float) (n - 1);
                    widths[i] = 0.44f;
                }
                FxDraw.strip(pts, widths, us, 35, 0, b.col, 1, true);
            }
        }
        for (Beam b : BEAMS) {
            Vector3f from = b.from.subtract(cam).toVector3f();
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), b.dir.toVector3f().normalize());
            float L = (float) Math.max(0.1, b.len);
            float rw = (float) (b.r * b.w);
            KiMesh.P k = KiMesh.p(23);
            k.time = time;
            k.seed = b.seed;
            k.color = b.col;
            k.alpha = (float) b.fade;
            k.pulse = 1;
            KiMesh.queue(KiMesh.Mesh.TUBE, new Matrix4f().translation(from).rotate(q).scale(rw, L, rw), k, KiMesh.BOTH, 11);
            KiMesh.P h = KiMesh.p(23);
            h.time = time;
            h.seed = b.seed;
            h.color = b.col;
            h.alpha = (float) (b.fade * 0.5);
            h.pulse = 0;
            KiMesh.queue(KiMesh.Mesh.TUBE, new Matrix4f().translation(from).rotate(q).scale(rw * 1.9f, L, rw * 1.9f), h, KiMesh.BOTH, 11);
            Vector3f head = b.from.add(b.dir.scale(L)).subtract(cam).toVector3f();
            KiMesh.P hc = KiMesh.p(21);
            hc.time = time;
            hc.seed = b.seed;
            hc.color = b.col;
            float hs = (float) (b.r * 1.6 * b.w);
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(head, hs), hc, KiMesh.FRONT, 11);
            KiMesh.P hh = KiMesh.p(22);
            hh.time = time;
            hh.seed = b.seed;
            hh.color = b.col;
            KiMesh.queue(KiMesh.Mesh.BALL, KiMesh.sphere(head, hs * 1.9f), hh, KiMesh.BACK, 11);
        }
    }

    // ------------------------------------------------------------------ the screen pass

    static boolean screenOn() {
        return shkT < 0.5 || Math.max(linesKick, boostView) > 0.001 || !IMPACT.isEmpty();
    }

    static void screenPass(float[] center, boolean ahead) {
        var shader = FxShaders.pvScreen;
        if (shader == null) {
            return;
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        double st = shkT / 0.5;
        float strength = 0, radius = 0, width = 0.06f;
        if (st < 1) {
            double e = 1 - Math.pow(1 - st, 3);
            radius = (float) ((0.04 + 0.6 * e) * shkP);
            width = (float) (0.05 + 0.05 * e);
            strength = ahead && FxConfig.on(FxConfig.DISTORTION) ? (float) (0.05 * (1 - st) * shkP) : 0;
        }
        int mode = IMPACT.isEmpty() || !FxConfig.on(FxConfig.IMPACT_FRAMES) ? 0 : IMPACT.peek();
        shader.safeGetUniform("Center").set(center[0], center[1]);
        shader.safeGetUniform("Radius").set(radius);
        shader.safeGetUniform("Width").set(width);
        shader.safeGetUniform("Strength").set(strength);
        shader.safeGetUniform("Aspect").set(target.width / (float) Math.max(1, target.height));
        shader.safeGetUniform("Amt").set((float) Math.max(linesKick, boostView));
        shader.safeGetUniform("Frame").set((float) Math.floor(clock * 12));
        shader.safeGetUniform("Res").set((float) target.height);
        shader.safeGetUniform("Mode").set((float) mode);
        FxPost.pass(shader);
    }

    static Vec3 shockPos() {
        return shkAt;
    }
}
