package com.starkinhon.battlegroundsmod.client.fx;

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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.EsferaCutscene;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Outer space for the Esfera da Destruição Planetária cutscene (client/EsferaCutscene), drawn over the whole world at the
// end of the level: a deep navy sky, a band of small stars, soft nebulas in the espaco.corNebulosa colors, the square
// Earth (a giant textured cube, turning slowly) far below, Bills floating, the target lying adrift between him and the
// Earth and the sphere of destruction (charging between his hands, thrown, imploding into a giant sun, then carrying the
// target down). Only the two players in the cutscene see it.
public final class SpaceScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLUE_STAR = FxDraw.rgb(0xBFD8FF);
    private static final float[] WARM_STAR = FxDraw.rgb(0xFFE6C0);
    private static final float[][] NEBULA = {FxDraw.rgb(0x3B2BD9), FxDraw.rgb(0x1F5BD9), FxDraw.rgb(0x8A2BD9), FxDraw.rgb(0xD94A8A)};
    private static final float[] ATMOSPHERE = FxDraw.rgb(0x59A8FF);
    private static final float[] GOLD = FxDraw.rgb(0xFFD040);
    private static final float[] FIRE_HOT = FxDraw.rgb(0xFFF2B0);
    private static final float[] FIRE_MID = FxDraw.rgb(0xFF8A10);
    private static final float[] FIRE_DEEP = FxDraw.rgb(0xC02008);
    private static final float[] SOOT = FxDraw.rgb(0x2A1C18);
    private static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/earth.png");

    private SpaceScene() {
    }

    public static boolean active() {
        return EsferaCutscene.inSpace();
    }

    // Effects of this skill still show in space (the streaks, the flashes); everything else stays in the world below.
    static boolean owns(String skill) {
        return EsferaSpec.SKILL.equals(skill);
    }

    // First part, before the other world effects: covers the world with the sky, then draws the stars, the planet, Bills
    // and the target on a clean depth buffer.
    static void backdrop(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(EsferaCutscene.casterId());
        if (caster == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(EsferaCutscene.targetId());
        EsferaSpec.Espaco espaco = EsferaCutscene.spec().espaco();
        sky(FxDraw.parse(espaco.corCeu(), 0x050A24), FxDraw.parse(espaco.corHorizonte(), 0x0E1E5A));
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        try {
            // Stars and nebulas, far away around the camera, behind everything.
            boolean flat = FxShaders.flat();
            MultiBufferSource.BufferSource fx = FxWorld.buffers();
            FxDraw.begin(fx, event.getCamera(), flat);
            stars(espaco, ScreenFx.now() + partial);
            fx.endBatch(flat ? FxRenderTypes.FLAT_ALPHA : FxRenderTypes.VFX_ALPHA);
            fx.endBatch(flat ? FxRenderTypes.FLAT_ADD : FxRenderTypes.VFX_ADD);

            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            planet(buffers, camera, espaco, EsferaCutscene.spaceAge(partial));
            buffers.endBatch();
            dispatcher.setRenderShadow(false);
            Vec3 rel = caster.getPosition(partial).subtract(camera);
            dispatcher.render(caster, rel.x, rel.y, rel.z, Mth.lerp(partial, caster.yRotO, caster.getYRot()), partial, new PoseStack(), buffers,
                    0xF000F0);
            if (target != null && target != caster) {
                // Lying on its back, adrift, slowly turning.
                Vec3 drift = EsferaCutscene.targetPos(partial).subtract(camera);
                PoseStack pose = new PoseStack();
                pose.translate(drift.x, drift.y, drift.z);
                pose.mulPose(Axis.YP.rotationDegrees(EsferaCutscene.targetSpin(partial)));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.translate(0, -target.getBbHeight() / 2, 0);
                // Cancel the renderer's own turn by its body yaw, so the face ends up looking up.
                float body = target instanceof LivingEntity living ? Mth.lerp(partial, living.yBodyRotO, living.yBodyRot) : target.getYRot();
                pose.mulPose(Axis.YP.rotationDegrees(body));
                dispatcher.render(target, 0, 0, 0, target.getYRot(), partial, pose, buffers, 0xF000F0);
            }
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // Second part, with the other world effects (additive, into the bloom): the planet's glow, the sphere and the
    // explosion.
    static void render(Vec3 camera, double now, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(EsferaCutscene.casterId());
        if (caster == null) {
            return;
        }
        EsferaSpec spec = EsferaCutscene.spec();
        float size = (float) spec.espaco().tamanho();
        Vector3f planet = EsferaCutscene.planetCenter().subtract(camera).toVector3f();
        FxDraw.billboard(planet, size * 2.3f, 0, FxDraw.SOFT, 0.1f, ATMOSPHERE, 0.35f, true);
        FxDraw.billboard(planet, size * 1.55f, 0, FxDraw.SOFT, 0.5f, ATMOSPHERE, 0.25f, true);

        double t = EsferaCutscene.time(partial);
        double[] radius = {0};
        Vec3 ballWorld = EsferaCutscene.spherePos(caster, partial, radius);
        Vector3f ball = ballWorld.subtract(camera).toVector3f();
        float r = (float) radius[0];
        if (t >= spec.chargeTick() && t < spec.throwTick()) {
            charging(ball, r, (float) (t - spec.chargeTick()), (float) ((t - spec.chargeTick()) / Math.max(1, spec.throwTick() - spec.chargeTick())));
        }
        if (r > 0.001f) {
            double implodeEnd = spec.flyTick() + EsferaCutscene.implodeTicks();
            if (t >= spec.flyTick() && t < implodeEnd) {
                imploding(ball, r, (float) ((t - spec.flyTick()) / EsferaCutscene.implodeTicks()), (float) now);
            }
            // Just the ball, no trail behind it (Stark: the trail gets in the way of the view).
            EsferaRenderer.drawSun(ball, null, r, (float) now, 4441);
        }
    }

    // Light gathering in between his hands while the sphere forms, and little sparks turning around it.
    private static void charging(Vector3f ball, float r, float age, float k) {
        float reach = 1.4f + r * 2;
        for (int i = 0; i < 26; i++) {
            float life = 12 + 10 * FxWorld.hash(77, i, 1);
            float u = ((age + FxWorld.hash(77, i, 2) * life) % life) / life;
            Vector3f d = FxWorld.sphere(77, i);
            float out = reach * (1 - u);
            Vector3f from = new Vector3f(d).mul(out).add(ball);
            Vector3f to = new Vector3f(d).mul(Math.max(r, out - 0.5f)).add(ball);
            FxDraw.ribbon(from, to, 0.05f + 0.04f * k, FxDraw.STREAK, 0.85f, i % 3 == 0 ? WHITE : GOLD, u * (1 - u) * 3.5f, true);
        }
        for (int i = 0; i < 8; i++) {
            float a = age * (0.18f + 0.05f * i) + i * 0.8f;
            Vector3f axis = FxWorld.sphere(78, i);
            Vector3f x = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
            Vector3f y = new Vector3f(axis).cross(x);
            float out = r * 1.8f + 0.1f;
            Vector3f at = new Vector3f(x).mul(Mth.cos(a) * out).add(new Vector3f(y).mul(Mth.sin(a) * out)).add(ball);
            FxDraw.billboard(at, 0.08f + 0.06f * k, 0, FxDraw.SOFT, 0.9f, FIRE_HOT, 0.9f, true);
        }
        // The glow it throws on his hands.
        FxDraw.billboard(ball, 1.2f + r * 6, 0, FxDraw.SOFT, 0.1f, FIRE_MID, 0.18f + 0.2f * k, true);
    }

    // Everything pulled into a point (streaks rushing in, a dark ring closing) right before it bursts into the giant sun.
    private static void imploding(Vector3f ball, float r, float k, float now) {
        float reach = 3 + r * 6;
        for (int i = 0; i < 40; i++) {
            Vector3f d = FxWorld.sphere(93, i);
            float out = reach * (1 - k) * (0.6f + 0.6f * FxWorld.hash(93, i, 1)) + r;
            Vector3f from = new Vector3f(d).mul(out + 1.5f + 2 * (1 - k)).add(ball);
            Vector3f to = new Vector3f(d).mul(out).add(ball);
            FxDraw.ribbon(from, to, 0.06f, FxDraw.STREAK, 0.9f, i % 2 == 0 ? WHITE : GOLD, 0.9f, true);
        }
        FxDraw.billboard(ball, reach * 2 * (1 - k) + 0.5f, 0, FxDraw.RING, 0.05f, FIRE_MID, 0.8f * k, true);
        FxDraw.billboard(ball, 0.6f + 2 * k, now * 0.1f, FxDraw.FLARE, 0.25f, WHITE, k, true);
    }

    // The explosion of flames, once (vfx "chamas", where the target lands with the sun): a white flash, a burning core, balls of fire rolling out, tongues
    // of flame, a shock ring, sparks, and dark soot left as it burns out. u runs 0 to 1 over its life.
    static void flames(Vector3f at, float big, float u, float age) {
        float in = FxWorld.easeOut(Math.min(1, u * 4));
        float out = 1 - u;
        // Flash and the hot core.
        float flash = Math.max(0, 1 - u * 6);
        FxDraw.billboard(at, big * 5 * flash + 0.01f, age * 0.02f, FxDraw.FLARE, 0.5f, WHITE, flash, true);
        FxDraw.billboard(at, big * 1.6f * in * (1 - 0.5f * u), 0, FxDraw.SOFT, 0.85f, FxDraw.mix(WHITE, FIRE_HOT, Math.min(1, u * 3)), out * 1.2f, true);
        FxDraw.billboard(at, big * 3.2f * in, 0, FxDraw.SOFT, 0.15f, FIRE_MID, 0.55f * out, true);
        // Balls of fire rolling out on every side, hot white-yellow first, deep red at the end.
        for (int k = 0; k < 70; k++) {
            float delay = FxWorld.hash(301, k, 1) * 0.25f;
            float lt = (u - delay) / (1 - delay);
            if (lt <= 0 || lt >= 1) {
                continue;
            }
            Vector3f d = FxWorld.sphere(301, k);
            float dist = big * (0.15f + 0.9f * FxWorld.easeOut(lt * 1.3f)) * (0.55f + 0.55f * FxWorld.hash(301, k, 2));
            Vector3f pos = new Vector3f(d).mul(dist).add(at).add(0, lt * big * 0.25f, 0);
            float s = big * (0.35f + 0.6f * lt) * (0.6f + 0.6f * FxWorld.hash(301, k, 3));
            float[] color = lt < 0.35f ? FxDraw.mix(FIRE_HOT, FIRE_MID, lt / 0.35f) : FxDraw.mix(FIRE_MID, FIRE_DEEP, (lt - 0.35f) / 0.65f);
            FxDraw.billboard(pos, s, FxWorld.hash(301, k, 4) * 6 + age * 0.03f, FxDraw.FIRE, lt, color, (1 - lt * lt), true);
        }
        // Tongues of flame licking out.
        for (int k = 0; k < 16; k++) {
            Vector3f d = FxWorld.sphere(302, k);
            float len = big * (0.9f + 0.7f * FxWorld.hash(302, k, 1)) * FxWorld.easeOut(Math.min(1, u * 3));
            Vector3f from = new Vector3f(d).mul(big * 0.2f).add(at);
            Vector3f to = new Vector3f(d).mul(len).add(at);
            FxDraw.ribbon(from, to, big * 0.35f * out, FxDraw.FIRE, Math.min(1, u * 1.5f), FxDraw.mix(FIRE_HOT, FIRE_MID, u), out, true);
        }
        // The shock ring, flat across the view, and a second one tilted.
        float ring = big * 4.5f * FxWorld.easeOut(Math.min(1, u * 2.2f));
        float thick = Mth.clamp(big * 0.12f / Math.max(0.5f, ring), 0.02f, 0.5f);
        FxDraw.billboard(at, ring, 0, FxDraw.RING, thick, FIRE_HOT, out * out, true);
        FxDraw.disk(at, new Vector3f(0.2f, 1, 0.1f), ring * 1.2f, FxDraw.RING, thick, FIRE_MID, out * out * 0.8f, true);
        // Sparks thrown far out.
        for (int k = 0; k < 90; k++) {
            float life = 0.5f + 0.5f * FxWorld.hash(303, k, 1);
            if (u > life) {
                continue;
            }
            float lu = u / life;
            Vector3f d = FxWorld.sphere(303, k);
            float dist = big * (0.3f + 2.6f * FxWorld.easeOut(lu)) * (0.6f + 0.5f * FxWorld.hash(303, k, 2));
            Vector3f pos = new Vector3f(d).mul(dist).add(at);
            Vector3f tail = new Vector3f(d).mul(dist - big * 0.35f * (1 - lu)).add(at);
            FxDraw.ribbon(tail, pos, 0.08f + big * 0.012f, FxDraw.STREAK, 0.9f, FxDraw.mix(FIRE_HOT, FIRE_MID, lu), 1 - lu, true);
        }
        // Soot left hanging as it burns out.
        if (u > 0.35f) {
            float su = (u - 0.35f) / 0.65f;
            for (int k = 0; k < 18; k++) {
                Vector3f d = FxWorld.sphere(304, k);
                Vector3f pos = new Vector3f(d).mul(big * (0.5f + 0.6f * su) * (0.6f + 0.5f * FxWorld.hash(304, k, 1))).add(at);
                FxDraw.billboard(pos, big * (0.7f + 0.6f * su), FxWorld.hash(304, k, 2) * 6, FxDraw.SMOKE, su, SOOT, 0.55f * su * (1 - su) * 4, false);
            }
        }
    }

    // A screen-wide gradient: dark at the top, the glow of the planet's air at the bottom.
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

    // Fixed stars on a sphere around the camera, twinkling: many tiny ones, more of them packed along a tilted band (like
    // the milky way), a few bright ones with rays, and wide soft nebulas in the espaco.corNebulosa colors.
    private static void stars(EsferaSpec.Espaco espaco, double now) {
        float[][] nebula = NEBULA;
        if (espaco.corNebulosa() != null && !espaco.corNebulosa().isEmpty()) {
            nebula = new float[espaco.corNebulosa().size()][];
            for (int k = 0; k < nebula.length; k++) {
                nebula[k] = FxDraw.rgb(FxDraw.parse(espaco.corNebulosa().get(k), 0x3B2BD9));
            }
        }
        Vector3f bandAxis = new Vector3f(0.35f, 0.8f, 0.48f).normalize();
        Vector3f bandX = new Vector3f(bandAxis).cross(new Vector3f(0, 0, 1)).normalize();
        Vector3f bandY = new Vector3f(bandAxis).cross(bandX);
        // Nebulas: soft glow blobs along the band, with wisps of smoke texture on top.
        for (int k = 0; k < 16; k++) {
            float a = FxWorld.hash(911, k, 1) * Mth.TWO_PI;
            Vector3f d = new Vector3f(bandX).mul(Mth.cos(a)).add(new Vector3f(bandY).mul(Mth.sin(a)))
                    .add(new Vector3f(bandAxis).mul((FxWorld.hash(911, k, 2) - 0.5f) * 0.5f)).normalize().mul(95);
            float[] color = nebula[k % nebula.length];
            FxDraw.billboard(d, 45 + 45 * FxWorld.hash(911, k, 3), FxWorld.hash(911, k, 4) * 6, FxDraw.SOFT, 0.05f, color,
                    0.1f + 0.08f * FxWorld.hash(911, k, 5), true);
            FxDraw.billboard(new Vector3f(d).mul(0.98f), 30 + 30 * FxWorld.hash(911, k, 6), FxWorld.hash(911, k, 7) * 6 + (float) now * 0.0004f,
                    FxDraw.SMOKE, 0.3f + 0.4f * FxWorld.hash(911, k, 8), color, 0.12f, true);
        }
        int count = Math.max(0, espaco.estrelas());
        for (int k = 0; k < count; k++) {
            Vector3f d;
            if (k % 2 == 0) {
                d = FxWorld.sphere(313, k);
            } else {
                float a = FxWorld.hash(314, k, 1) * Mth.TWO_PI;
                float spread = (FxWorld.hash(314, k, 2) - 0.5f) * (FxWorld.hash(314, k, 3) * 0.6f);
                d = new Vector3f(bandX).mul(Mth.cos(a)).add(new Vector3f(bandY).mul(Mth.sin(a))).add(new Vector3f(bandAxis).mul(spread)).normalize();
            }
            d.mul(90);
            float twinkle = 0.55f + 0.45f * Mth.sin((float) now * (0.08f + 0.1f * FxWorld.hash(313, k, 1)) + k);
            float size = 0.12f + 0.4f * FxWorld.hash(313, k, 2) * FxWorld.hash(313, k, 3);
            boolean big = k % 53 == 0;
            float[] color = k % 3 == 0 ? BLUE_STAR : k % 7 == 0 ? WARM_STAR : WHITE;
            FxDraw.billboard(d, big ? size * 7 : size, 0, big ? FxDraw.FLARE : FxDraw.SOFT, big ? 4 / 32f : 0.9f, color, twinkle, true);
        }
    }

    // The square Earth: a cube planet-sized, wrapped in Stark's earth texture (textures/fx/earth.png, six 256 pixel faces
    // side by side, 3 by 2). On top, where the sphere lands, the face with the big green continent; the icy, cloudy one is
    // underneath. Turning slowly with a little tilt. Fully lit.
    private static void planet(MultiBufferSource buffers, Vec3 camera, EsferaSpec.Espaco espaco, double age) {
        Vec3 rel = EsferaCutscene.planetCenter().subtract(camera);
        float size = (float) espaco.tamanho();
        PoseStack pose = new PoseStack();
        pose.translate(rel.x, rel.y, rel.z);
        pose.mulPose(Axis.YP.rotationDegrees((float) (age * espaco.girar()) + 30));
        pose.mulPose(Axis.XP.rotationDegrees(8));
        pose.scale(size / 2, size / 2, size / 2);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(EARTH));
        PoseStack.Pose last = pose.last();
        // Corners of each face and its tile in the texture (drawn both ways, so the winding does not matter).
        face(out, last, 0, new float[] {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1}, 0, 0, -1);
        face(out, last, 1, new float[] {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1}, 1, 0, 0);
        face(out, last, 2, new float[] {1, -1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1}, 0, 0, 1);
        face(out, last, 4, new float[] {-1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1}, -1, 0, 0);
        face(out, last, 3, new float[] {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1}, 0, 1, 0);
        face(out, last, 5, new float[] {-1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1}, 0, -1, 0);
    }

    // One face: corners bottom-left, bottom-right, top-right, top-left; tile 0-5 in reading order. A few pixels are
    // left off each tile's edge so the grey lines between them don't show.
    private static void face(VertexConsumer out, PoseStack.Pose pose, int tile, float[] c, float nx, float ny, float nz) {
        float inset = 4 / 768f;
        float u0 = (tile % 3) / 3f + inset;
        float u1 = (tile % 3 + 1) / 3f - inset;
        float v0 = (tile / 3) / 2f + inset * 1.5f;
        float v1 = (tile / 3 + 1) / 2f - inset * 1.5f;
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
        }
    }
}
