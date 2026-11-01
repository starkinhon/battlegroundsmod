package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Gojo's Expansão de Domínio: Vazio Infinito (DominioSpec, gojo.dominio.json), seen by everyone near it.
// From outside: a black sphere bursting out of him with a white-hot front, light veins crawling over its skin and a
// thin glowing rim; it hides whatever is inside. From inside, once it is complete: the world is gone. Endless stars, a
// haze of violet and blue, streams of light flowing across the sky into a giant eye of light far behind Gojo, and a
// dark mirror floor with ripples running out from his feet; only the people inside are left, standing in it. Those
// caught (everyone but Gojo) get the information flicker on their screen. At the end, cracks of light run across the
// void, it flashes white and the black shell fades away in the world.
// Everything is a function of the time since it started (no state but the start), smooth at any frame rate.
public final class DomainScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] VOID_BLACK = FxDraw.rgb(0x020207);
    private static final int VIEW = 160;

    private static final class Domain {
        final int caster;
        final Vec3 center;
        final DominioSpec spec;
        final double start;
        // Seen from where Gojo faced when he started: the eye of the void is behind him.
        final Vec3 forward;
        double closeStart = -1;
        boolean flashed;
        boolean hit;
        boolean shattered;

        Domain(int caster, Vec3 center, DominioSpec spec, double start, Vec3 forward) {
            this.caster = caster;
            this.center = center;
            this.spec = spec;
            this.start = start;
            this.forward = forward;
        }

        double radiusMax() {
            return Math.max(2, spec.dominio().raio());
        }

        // When the cracking starts, in ticks since the start (early if Gojo fell).
        double closeAt() {
            return closeStart >= 0 ? Math.min(spec.closeTick(), closeStart - start) : spec.closeTick();
        }

        double goneAt() {
            return closeAt() + spec.closeTicks();
        }

        // How big the sphere is at time t.
        double radius(double t) {
            if (t < spec.expandTick()) {
                return 0;
            }
            double k = Math.min(1, (t - spec.expandTick()) / Math.max(1, spec.dominio().expandirTicks()));
            return radiusMax() * (1 - Math.pow(1 - k, 3));
        }

        // 0 to 1 through the cracking and shattering (0 before it).
        double closing(double t) {
            return Mth.clamp((t - closeAt()) / spec.closeTicks(), 0, 1);
        }

        // The void fills the view of a camera inside it from when it is complete until halfway through the shattering.
        boolean voidAt(double t) {
            return t >= spec.fullTick() && closing(t) < 0.5;
        }

        boolean contains(Vec3 pos) {
            return pos.distanceToSqr(center) < radiusMax() * radiusMax();
        }
    }

    private static final List<Domain> DOMAINS = new ArrayList<>();

    private DomainScene() {
    }

    public static void start(int casterId, Vec3 center, boolean awakened, boolean close) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = ScreenFx.now();
        if (close) {
            for (Domain d : DOMAINS) {
                if (d.caster == casterId && d.closeStart < 0) {
                    d.closeStart = now;
                }
            }
            return;
        }
        DOMAINS.removeIf(d -> d.caster == casterId);
        Entity caster = minecraft.level.getEntity(casterId);
        Vec3 forward = Vec3.directionFromRotation(0, caster != null ? caster.getYRot() : 0);
        DOMAINS.add(new Domain(casterId, center, DominioSpec.get(awakened), now, forward));
        if (minecraft.player != null && minecraft.player.getId() == casterId) {
            CutscenePlayer.start(DominioSpec.SKILL, casterId, -1, false);
        }
    }

    public static void clear() {
        DOMAINS.clear();
    }

    public static boolean active() {
        return !DOMAINS.isEmpty();
    }

    private static double time(Domain d, float partial) {
        return ScreenFx.now() + partial - d.start;
    }

    // The domain whose void the camera is in right now, or null.
    private static Domain voidAround(Vec3 camera, float partial) {
        for (Domain d : DOMAINS) {
            if (d.voidAt(time(d, partial)) && d.contains(camera)) {
                return d;
            }
        }
        return null;
    }

    public static boolean inVoid() {
        Minecraft minecraft = Minecraft.getInstance();
        return !DOMAINS.isEmpty() && voidAround(minecraft.gameRenderer.getMainCamera().getPosition(), 0) != null;
    }

    // Once a tick: the moments that hit the screen (the sure hit, the shattering) and dropping finished domains.
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            DOMAINS.clear();
            return;
        }
        Vec3 me = minecraft.player.getEyePosition();
        DOMAINS.removeIf(d -> time(d, 0) > d.goneAt() + 1 || minecraft.player.position().distanceToSqr(d.center) > VIEW * VIEW * 4);
        for (Domain d : DOMAINS) {
            double t = time(d, 0);
            boolean inside = d.contains(me);
            boolean caster = minecraft.player.getId() == d.caster;
            if (!d.hit && t >= d.spec.fullTick()) {
                d.hit = true;
                if (inside) {
                    // Swallowed: a white flash hides the cut from the world to the void.
                    ScreenFx.flash(0xFFFFFF, 1, 10);
                    ScreenFx.fovKick(caster ? -6 : 10, 14);
                    if (!caster) {
                        ScreenFx.vignette(FxDraw.parse(d.spec.visual().corBrilho(), 0x6FD8FF), 0.55f,
                                (int) (d.closeAt() - d.spec.fullTick()));
                    }
                }
            }
            if (!d.shattered && d.closing(t) >= 0.5) {
                d.shattered = true;
                if (inside) {
                    ScreenFx.flash(0xFFFFFF, 0.9f, 8);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Inside: the void covers the world before anything else is drawn, and the people inside are drawn on it again.

    static void backdrop(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Domain d = voidAround(camera, partial);
        if (d == null || minecraft.level == null) {
            return;
        }
        sky(0x000000, 0x05060F);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Entity viewer = event.getCamera().getEntity();
        boolean detached = event.getCamera().isDetached();
        try {
            dispatcher.setRenderShadow(false);
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity) || entity.isInvisible() || (entity == viewer && !detached)
                        || !d.contains(entity.getPosition(partial).add(0, entity.getBbHeight() / 2, 0))) {
                    continue;
                }
                Vec3 rel = entity.getPosition(partial).subtract(camera);
                float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                // Fully lit: there is no sky or sun here, the void glows on them.
                dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
            }
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // With the other world effects (FxDraw is begun): the shell from outside, or the void from inside.
    static void render(Vec3 camera, double now, float partial) {
        for (Domain d : DOMAINS) {
            double t = time(d, partial);
            if (t < 0) {
                continue;
            }
            boolean inside = d.contains(camera);
            if (inside && d.voidAt(t)) {
                drawVoid(d, camera, t, (float) now, partial);
            } else {
                drawShell(d, camera, t, (float) now);
            }
        }
    }

    // ---- the void ----------------------------------------------------------------------------------------------------

    private static void drawVoid(Domain d, Vec3 camera, double t, float now, float partial) {
        DominioSpec.Visual v = d.spec.visual();
        float[] ring = FxDraw.rgb(FxDraw.parse(v.corAnel(), 0xFFFFFF));
        float[] glow = FxDraw.rgb(FxDraw.parse(v.corBrilho(), 0x6FD8FF));
        float[] haze = FxDraw.rgb(FxDraw.parse(v.corFundo(), 0x5A3CFF));
        // Comes in over the first ticks after the flash, so the cut isn't a hard switch.
        float in = (float) Mth.clamp((t - d.spec.fullTick()) / 10.0, 0, 1);
        float k = (float) d.closing(t) * 2;
        float life = in * (1 - 0.6f * k);

        // The eye of the void: far behind Gojo, a little above the horizon. Drawn around the camera so it never moves.
        float lift = (float) Math.toRadians(v.olhoAltura());
        Vector3f eyeDir = new Vector3f((float) -d.forward.x * Mth.cos(lift), Mth.sin(lift), (float) -d.forward.z * Mth.cos(lift)).normalize();
        Vector3f eye = new Vector3f(eyeDir).mul(95);
        float size = (float) v.olhoTamanho();

        stars(v, eyeDir, now, life, glow, haze);
        streams(eyeDir, now, life, ring, glow);

        // The eye itself: the ring of light with its turning arms (shader shape 10), a starburst, a halo and the long
        // flat streak of light across it.
        float pulse = 0.85f + 0.15f * Mth.sin(now * 0.08f);
        FxDraw.billboard(eye, size * 2.6f, 0, FxDraw.RING, 0.45f, haze, 0.12f * life, true);
        FxDraw.billboard(eye, size * 1.6f, 0, FxDraw.SOFT, 0.05f, haze, 0.18f * life, true);
        FxDraw.billboard(eye, size, now * 0.004f, FxDraw.VOID_EYE, 0.32f, ring, life * pulse, true);
        FxDraw.billboard(eye, size * 0.42f, now * 0.002f, FxDraw.FLARE, 8 / 32f, WHITE, 0.55f * life * pulse, true);
        FxDraw.billboard(eye, size * 0.12f, 0, FxDraw.SOFT, 0.9f, WHITE, life, true);
        Vector3f side = new Vector3f(eyeDir).cross(new Vector3f(0, 1, 0)).normalize();
        FxDraw.ribbon(new Vector3f(eye).sub(new Vector3f(side).mul(size * 1.8f)), new Vector3f(eye).add(new Vector3f(side).mul(size * 1.8f)),
                size * 0.035f, FxDraw.BEAM, 0.6f, glow, 0.55f * life * pulse, true);
        FxDraw.ribbon(new Vector3f(eye).sub(new Vector3f(side).mul(size * 0.9f)), new Vector3f(eye).add(new Vector3f(side).mul(size * 0.9f)),
                size * 0.012f, FxDraw.BEAM, 0.9f, WHITE, 0.7f * life, true);

        floor(d, camera, eyeDir, now, t, life, glow);
        people(d, camera, now, partial, life, glow);
        if (k > 0) {
            cracks(camera, d, (float) Math.min(1, k), now, true);
        }
    }

    // Countless tiny stars on every side, a few big bright ones, and a soft violet and blue haze wound around the eye.
    private static void stars(DominioSpec.Visual v, Vector3f eyeDir, float now, float life, float[] glow, float[] haze) {
        for (int k = 0; k < 14; k++) {
            Vector3f d = FxWorld.sphere(611, k).add(new Vector3f(eyeDir).mul(0.9f)).normalize().mul(92);
            float[] color = k % 2 == 0 ? haze : glow;
            FxDraw.billboard(d, 40 + 40 * FxWorld.hash(611, k, 1), FxWorld.hash(611, k, 2) * 6 + now * 0.0006f, FxDraw.SMOKE,
                    0.3f + 0.3f * FxWorld.hash(611, k, 3), color, 0.1f * life, true);
        }
        int count = Math.max(0, v.estrelas());
        for (int k = 0; k < count; k++) {
            Vector3f d = FxWorld.sphere(612, k);
            // Packed a little towards the eye, like they all fall into it.
            if (k % 3 == 0) {
                d.add(new Vector3f(eyeDir).mul(0.8f)).normalize();
            }
            d.mul(90);
            float twinkle = 0.45f + 0.55f * Mth.sin(now * (0.06f + 0.12f * FxWorld.hash(612, k, 1)) + k);
            float s = 0.1f + 0.35f * FxWorld.hash(612, k, 2) * FxWorld.hash(612, k, 3);
            boolean big = k % 61 == 0;
            float[] color = k % 4 == 0 ? glow : WHITE;
            FxDraw.billboard(d, big ? s * 8 : s, 0, big ? FxDraw.FLARE : FxDraw.SOFT, big ? 4 / 32f : 0.9f, color, twinkle * life, true);
        }
    }

    // Streams of light on the far sky, flowing along spirals into the eye, like endless information pouring in.
    private static void streams(Vector3f eyeDir, float now, float life, float[] ring, float[] glow) {
        Vector3f x = new Vector3f(eyeDir).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f y = new Vector3f(x).cross(eyeDir).normalize();
        for (int k = 0; k < 110; k++) {
            float lane = FxWorld.hash(613, k, 1) * Mth.TWO_PI;
            float speed = 0.004f + 0.006f * FxWorld.hash(613, k, 2);
            // u: 1 far from the eye (behind the viewer) down to 0 at the eye, again and again.
            float u = 1 - ((now * speed + FxWorld.hash(613, k, 3)) % 1f);
            float len = 0.05f + 0.07f * FxWorld.hash(613, k, 4);
            Vector3f a = spiral(eyeDir, x, y, lane, u);
            Vector3f b = spiral(eyeDir, x, y, lane, Math.max(0, u - len));
            float fade = Mth.sin(u * Mth.PI);
            FxDraw.ribbon(a.mul(88), b.mul(88), 0.25f + 0.35f * FxWorld.hash(613, k, 5), FxDraw.STREAK, 0.8f, k % 3 == 0 ? ring : glow,
                    0.55f * fade * life, true);
        }
    }

    // A point on a spiral lane around the eye: u 0 at the eye, 1 straight behind the viewer (the far side of the sky).
    private static Vector3f spiral(Vector3f eyeDir, Vector3f x, Vector3f y, float lane, float u) {
        float polar = u * Mth.PI * 0.92f;
        float around = lane + u * 2.4f;
        Vector3f across = new Vector3f(x).mul(Mth.cos(around)).add(new Vector3f(y).mul(Mth.sin(around)));
        return new Vector3f(eyeDir).mul(Mth.cos(polar)).add(across.mul(Mth.sin(polar))).normalize();
    }

    // The dark mirror they stand on: ripples running out from Gojo's feet, glitter, and the eye's light stretched over it.
    private static void floor(Domain d, Vec3 camera, Vector3f eyeDir, float now, double t, float life, float[] glow) {
        Vector3f c = d.center.subtract(camera).toVector3f().add(0, 0.04f, 0);
        Vector3f up = new Vector3f(0, 1, 0);
        float big = (float) d.radiusMax();
        for (int k = 0; k < 4; k++) {
            float u = (float) (((t - d.spec.fullTick()) / 50.0 + k / 4.0) % 1.0);
            if (u < 0) {
                continue;
            }
            float r = big * 2.4f * FxWorld.easeOut(u);
            FxDraw.disk(c, up, r, FxDraw.RING, Mth.clamp(0.6f / Math.max(1, r), 0.01f, 0.2f), glow, 0.4f * (1 - u) * life, true);
        }
        FxDraw.disk(c, up, 3.5f, FxDraw.RING, 0.08f, WHITE, 0.6f * life, true);
        FxDraw.disk(c, up, big * 2, FxDraw.SOFT, 0.02f, glow, 0.06f * life, true);
        for (int k = 0; k < 220; k++) {
            float a = FxWorld.hash(614, k, 1) * Mth.TWO_PI;
            float r = big * 2.2f * (float) Math.sqrt(FxWorld.hash(614, k, 2));
            Vector3f at = new Vector3f(c).add(Mth.cos(a) * r, 0, Mth.sin(a) * r);
            float twinkle = Math.max(0, Mth.sin(now * (0.05f + 0.1f * FxWorld.hash(614, k, 3)) + k * 1.7f));
            FxDraw.billboard(at, 0.1f + 0.15f * FxWorld.hash(614, k, 4), 0, FxDraw.SOFT, 0.9f, k % 3 == 0 ? glow : WHITE,
                    twinkle * twinkle * 0.8f * life, true);
        }
        // The eye's glow laid over the mirror, a long shimmering path towards it.
        Vector3f flat = new Vector3f(eyeDir.x, 0, eyeDir.z).normalize();
        Vector3f near = new Vector3f(c).add(new Vector3f(flat).mul(3));
        Vector3f far = new Vector3f(c).add(new Vector3f(flat).mul(big * 3.5f));
        Vector3f side = new Vector3f(flat).cross(up).normalize();
        for (int k = 0; k < 3; k++) {
            float w = 2.5f + k * 3f;
            Vector3f s = new Vector3f(side).mul(w / 2);
            FxDraw.quad(new Vector3f(near).sub(s), new Vector3f(far).sub(s), new Vector3f(far).add(s), new Vector3f(near).add(s), FxDraw.BEAM,
                    0.3f, glow, (0.12f - k * 0.03f) * life * (0.85f + 0.15f * Mth.sin(now * 0.2f + k)), true);
        }
    }

    // Gojo's eyes glowing blue; on everyone caught, thin streams of light pouring into their head.
    private static void people(Domain d, Vec3 camera, float now, float partial, float life, float[] glow) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity viewer = minecraft.gameRenderer.getMainCamera().getEntity();
        boolean detached = minecraft.gameRenderer.getMainCamera().isDetached();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.isInvisible()) {
                continue;
            }
            Vec3 pos = entity.getPosition(partial);
            if (!d.contains(pos.add(0, entity.getBbHeight() / 2, 0))) {
                continue;
            }
            if (entity.getId() == d.caster) {
                if (entity == viewer && !detached) {
                    continue;
                }
                float yaw = Mth.rotLerp(partial, living.yHeadRotO, living.yHeadRot) * Mth.DEG_TO_RAD;
                Vector3f fwd = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
                Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
                Vector3f eyes = pos.add(0, entity.getEyeHeight() + 0.03, 0).subtract(camera).toVector3f().add(new Vector3f(fwd).mul(0.27f));
                for (int s = -1; s <= 1; s += 2) {
                    Vector3f e = new Vector3f(eyes).add(new Vector3f(right).mul(0.12f * s));
                    FxDraw.billboard(e, 0.32f, now * 0.02f, FxDraw.FLARE, 6 / 32f, glow, 0.9f * life, true);
                    FxDraw.billboard(e, 0.1f, 0, FxDraw.SOFT, 0.9f, WHITE, life, true);
                }
                continue;
            }
            Vector3f head = pos.add(0, entity.getEyeHeight(), 0).subtract(camera).toVector3f();
            for (int k = 0; k < 10; k++) {
                float life2 = 10 + 8 * FxWorld.hash(615 + entity.getId(), k, 1);
                float u = ((now + FxWorld.hash(615 + entity.getId(), k, 2) * life2) % life2) / life2;
                Vector3f dir = FxWorld.sphere(615 + entity.getId(), k + (int) ((now + FxWorld.hash(615 + entity.getId(), k, 2) * life2) / life2) * 13);
                float out = 3.5f * (1 - u) + 0.25f;
                Vector3f from = new Vector3f(dir).mul(out + 1.2f).add(head);
                Vector3f to = new Vector3f(dir).mul(out).add(head);
                FxDraw.ribbon(from, to, 0.05f, FxDraw.STREAK, 0.9f, k % 2 == 0 ? WHITE : glow, u * (1 - u) * 3 * life, true);
            }
            FxDraw.billboard(head, 0.9f, 0, FxDraw.SOFT, 0.2f, glow, (0.25f + 0.15f * Mth.sin(now * 0.7f + entity.getId())) * life, true);
        }
    }

    // ---- the shell, from outside (and from inside while it is still growing) -----------------------------------------

    private static void drawShell(Domain d, Vec3 camera, double t, float now) {
        double r = d.radius(t);
        if (r < 0.05) {
            return;
        }
        double k = d.closing(t);
        float[] glow = FxDraw.rgb(FxDraw.parse(d.spec.visual().corBrilho(), 0x6FD8FF));
        // Fully black until it shatters, then fades out.
        float solid = (float) (k < 0.5 ? 1 : Math.max(0, 1 - (k - 0.5) * 2.4));
        if (solid <= 0.002f) {
            return;
        }
        Vector3f c = d.center.subtract(camera).toVector3f();
        float rr = (float) r;
        double grow = t < d.spec.fullTick() ? (t - d.spec.expandTick()) / Math.max(1, d.spec.dominio().expandirTicks()) : 1;
        float front = (float) Math.max(0, 1 - grow);
        sphere(c, rr, VOID_BLACK, 0.985f * solid, 18, 32);

        // Light crawling over its skin: short arcs sliding around it, and specks of the void's stars.
        Vector3f axis = new Vector3f(0.2f, 1, 0.1f).normalize();
        for (int k2 = 0; k2 < 46; k2++) {
            Vector3f p0 = FxWorld.sphere(621, k2);
            float a = now * (0.01f + 0.02f * FxWorld.hash(621, k2, 1)) + FxWorld.hash(621, k2, 2) * 6;
            Vector3f a1 = rotate(p0, axis, a).mul(rr * 1.003f).add(c);
            Vector3f a2 = rotate(p0, axis, a + 0.18f + 0.2f * FxWorld.hash(621, k2, 3)).mul(rr * 1.003f).add(c);
            FxDraw.ribbon(a1, a2, 0.08f + rr * 0.006f, FxDraw.STREAK, 0.8f, k2 % 4 == 0 ? WHITE : glow, (0.3f + 0.5f * front) * solid, true);
        }
        for (int k2 = 0; k2 < 90; k2++) {
            Vector3f p = FxWorld.sphere(622, k2).mul(rr * 1.002f).add(c);
            float twinkle = Math.max(0, Mth.sin(now * (0.08f + 0.1f * FxWorld.hash(622, k2, 1)) + k2));
            FxDraw.billboard(p, 0.15f + rr * 0.01f, 0, FxDraw.SOFT, 0.9f, WHITE, twinkle * 0.8f * solid, true);
        }
        double dist = camera.distanceTo(d.center);
        if (dist > r * 1.02) {
            // The rim of light around its outline (a ring facing the camera hugs a sphere's silhouette).
            float apparent = (float) (rr * dist / Math.sqrt(Math.max(1.0E-3, dist * dist - r * r)));
            FxDraw.billboard(c, apparent * 2.02f, 0, FxDraw.RING, Mth.clamp(0.35f / apparent, 0.01f, 0.2f), WHITE, (0.5f + 0.5f * front) * solid, true);
            FxDraw.billboard(c, apparent * 2.3f, 0, FxDraw.RING, 0.25f, glow, (0.25f + 0.5f * front) * solid, true);
        }
        if (front > 0) {
            // The growing front: a white-hot shock ring along the ground and streaks flung out ahead of it.
            Vector3f up = new Vector3f(0, 1, 0);
            FxDraw.disk(new Vector3f(c).add(0, 0.06f, 0), up, rr * 2.15f, FxDraw.RING, Mth.clamp(1.2f / rr, 0.02f, 0.3f), WHITE, front, true);
            FxDraw.disk(new Vector3f(c).add(0, 0.05f, 0), up, rr * 2.6f, FxDraw.RING, 0.3f, glow, front * 0.6f, true);
            for (int k2 = 0; k2 < 40; k2++) {
                Vector3f dir = FxWorld.sphere(623, k2);
                dir.y = Math.abs(dir.y);
                Vector3f from = new Vector3f(dir).mul(rr).add(c);
                Vector3f to = new Vector3f(dir).mul(rr + 2 + 5 * front).add(c);
                FxDraw.ribbon(from, to, 0.12f, FxDraw.STREAK, 0.9f, k2 % 2 == 0 ? WHITE : glow, front, true);
            }
        }
        if (k > 0) {
            cracks(camera, d, (float) Math.min(1, k * 2), now, false);
        }
    }

    // Cracks of light running over the void (on the far sky when inside) or over the shell (from outside).
    private static void cracks(Vec3 camera, Domain d, float k, float now, boolean sky) {
        float[] glow = FxDraw.rgb(FxDraw.parse(d.spec.visual().corBrilho(), 0x6FD8FF));
        Vector3f c = sky ? new Vector3f() : d.center.subtract(camera).toVector3f();
        float rr = sky ? 80 : (float) d.radiusMax() * 1.004f;
        float fade = sky ? 1 : Math.max(0, 1 - Math.max(0, k - 0.5f) * 2);
        for (int n = 0; n < 34; n++) {
            Vector3f p = FxWorld.sphere(631, n);
            int steps = 7;
            float shown = Mth.clamp(k * 1.6f - FxWorld.hash(631, n, 1) * 0.6f, 0, 1) * steps;
            for (int s = 0; s < steps && s < shown; s++) {
                Vector3f jag = FxWorld.sphere(632 + n, s).mul(0.16f);
                Vector3f q = new Vector3f(p).add(jag).normalize();
                float part = Math.min(1, shown - s);
                Vector3f end = new Vector3f(p).lerp(q, part).normalize();
                Vector3f a = new Vector3f(p).mul(rr).add(c);
                Vector3f b = new Vector3f(end).mul(rr).add(c);
                float w = (sky ? 0.7f : 0.15f + rr * 0.006f) * (1 - s / (float) steps * 0.6f);
                FxDraw.ribbon(a, b, w * 2.5f, FxDraw.BEAM, 0.2f, glow, 0.5f * fade, true);
                FxDraw.ribbon(a, b, w, FxDraw.BEAM, 0.9f, WHITE, fade, true);
                p = q;
            }
        }
    }

    // A sphere of quads (the shell), seen from both sides.
    private static void sphere(Vector3f c, float r, float[] rgb, float opacity, int rings, int segments) {
        for (int i = 0; i < rings; i++) {
            float t0 = Mth.PI * i / rings - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
            for (int j = 0; j < segments; j++) {
                float p0 = Mth.TWO_PI * j / segments;
                float p1 = Mth.TWO_PI * (j + 1) / segments;
                FxDraw.quad(point(c, r, t0, p0), point(c, r, t0, p1), point(c, r, t1, p1), point(c, r, t1, p0), FxDraw.SOLID, 0, rgb, opacity, false);
            }
        }
    }

    private static Vector3f point(Vector3f c, float r, float lat, float lon) {
        return new Vector3f(Mth.cos(lat) * Mth.cos(lon) * r, Mth.sin(lat) * r, Mth.cos(lat) * Mth.sin(lon) * r).add(c);
    }

    private static Vector3f rotate(Vector3f v, Vector3f axis, float angle) {
        return new Vector3f(v).rotateAxis(angle, axis.x, axis.y, axis.z);
    }

    // A screen-wide gradient: dark at the top, a faint blue-black at the bottom.
    private static void sky(int top, int bottom) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 1, 0, 1, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] low = FxDraw.rgb(bottom);
        float[] high = FxDraw.rgb(top);
        builder.addVertex(0, 0, 0).setColor(low[0], low[1], low[2], 1);
        builder.addVertex(1, 0, 0).setColor(low[0], low[1], low[2], 1);
        builder.addVertex(1, 1, 0).setColor(high[0], high[1], high[2], 1);
        builder.addVertex(0, 1, 0).setColor(high[0], high[1], high[2], 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // ---- the screen of those caught -----------------------------------------------------------------------------------

    // Infinite information: thin lines of light flickering across the screen every tick, rushing in from the edges, and a
    // slow white pulse. Only for those caught inside (not Gojo), while the void lasts.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (DOMAINS.isEmpty() || minecraft.player == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 me = minecraft.player.getEyePosition(partial);
        Domain d = null;
        for (Domain each : DOMAINS) {
            if (each.caster != minecraft.player.getId() && each.contains(me) && each.voidAt(time(each, partial))) {
                d = each;
            }
        }
        if (d == null) {
            return;
        }
        float strength = (float) Mth.clamp(d.spec.visual().telaVitima(), 0, 2) * (FxConfig.on(FxConfig.REDUCED_FLASH) ? 0.4f : 1);
        if (strength <= 0) {
            return;
        }
        double t = time(d, partial);
        float in = (float) Mth.clamp((t - d.spec.fullTick()) / 8.0, 0, 1);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        long tick = ScreenFx.now();
        int glow = FxDraw.parse(d.spec.visual().corBrilho(), 0x6FD8FF) & 0xFFFFFF;
        // Lines across the screen, new ones every tick.
        for (int k = 0; k < 46; k++) {
            float y = FxWorld.hash((int) tick, k, 1) * h;
            float x = FxWorld.hash((int) tick, k, 2) * w;
            float len = (0.05f + 0.35f * FxWorld.hash((int) tick, k, 3)) * w;
            int a = (int) (255 * strength * in * (0.08f + 0.22f * FxWorld.hash((int) tick, k, 4)));
            int color = (Math.min(255, a) << 24) | (k % 3 == 0 ? glow : 0xFFFFFF);
            graphics.fill((int) x, (int) y, (int) Math.min(w, x + len), (int) y + 1, color);
        }
        // Streaks rushing from the edges towards the middle.
        float cx = w / 2f;
        float cy = h / 2f;
        for (int k = 0; k < 28; k++) {
            float life = 6 + 6 * FxWorld.hash(641, k, 1);
            float u = (float) (((t + FxWorld.hash(641, k, 2) * life) % life) / life);
            float ang = FxWorld.hash(641, k + (int) ((t + FxWorld.hash(641, k, 2) * life) / life) * 7, 3) * Mth.TWO_PI;
            float reach = Math.max(w, h) * 0.75f * (1 - u);
            int x = (int) (cx + Mth.cos(ang) * reach);
            int y = (int) (cy + Mth.sin(ang) * reach);
            int a = (int) (255 * strength * in * 0.35f * Mth.sin(u * Mth.PI));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, (Math.min(255, a) << 24) | 0xFFFFFF);
        }
        // A slow white pulse, like the mind overloading.
        float pulse = (float) Math.pow(Math.max(0, Mth.sin((float) t * 0.12f)), 8);
        int a = (int) (255 * strength * in * 0.22f * pulse);
        if (a > 0) {
            graphics.fill(0, 0, w, h, (Math.min(255, a) << 24) | 0xFFFFFF);
        }
    }
}
