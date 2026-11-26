// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
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
import com.starkinhon.battlegroundsmod.skill.EsferaCena;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Outer space for the Esfera da Destruição Planetária cutscene (client/EsferaCutscene), drawn over the whole world at the
// end of the level, as on the web preview approved by Stark on 2026-11-26 (forja/bills-esfera-cutscene): the navy sky
// with the milky way, the stars, the far sun the light comes from, the round Earth below (its block-pixel map, the cloud
// shell turning a little faster, lit from the side, the blue rim and two shells of air; no moon), Bills and the target
// posed by skill/EsferaCena, and the sun of destruction (the mod's Esfera inside, the tongues of fire, corona, rays and
// waves of flame outside). The preview's scene is laid out in its own frame (x to Bills' LEFT, y up, z ahead, from his
// feet); here it is turned to the way he faced and moved to where he stood. Only the two players in it see it.
public final class SpaceScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLUE_STAR = {0.75f, 0.85f, 1};
    private static final float[] WARM_STAR = {1, 0.9f, 0.75f};
    private static final float[] SPARK = FxDraw.rgb(0xFFD36A);
    private static final float[] RING_COLOR = FxDraw.rgb(0xFFB040);
    private static final float[] FIRE_HOT = FxDraw.rgb(0xFFF2B0);
    private static final float[] FIRE_MID = FxDraw.rgb(0xFF8A10);
    private static final float[] FIRE_DEEP = FxDraw.rgb(0xC02008);
    private static final float[] SOOT = FxDraw.rgb(0x2A1C18);
    private static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/earth.png");
    // Where the light comes from, in the preview's frame.
    private static final Vector3f SUN_DIR = new Vector3f(0.75f, 0.55f, -0.35f).normalize();
    // The ball's quad reaches this many radii from its middle (room for the rays of light).
    private static final float SOL_S = 3.2f;

    private SpaceScene() {
    }

    public static boolean active() {
        return EsferaCutscene.inSpace();
    }

    // Effects of this skill still show in space (the streaks, the flashes); everything else stays in the world below.
    static boolean owns(String skill) {
        return EsferaSpec.SKILL.equals(skill);
    }

    // Builds the spheres' meshes ahead of time (called as the cutscene starts).
    public static void warm() {
        if (GROUND == null) {
            GROUND = new Mesh(96, 64);
            AIR = new Mesh(64, 48);
        }
    }

    // ---------- the preview's frame ----------

    // A point of the preview's frame (x left, y up, z ahead, from Bills' feet) in the world, relative to the camera.
    private static Vector3f scene(double x, double y, double z, Vec3 camera) {
        Vec3 f = EsferaCutscene.forward();
        Vec3 o = EsferaCutscene.origin();
        return new Vector3f((float) (o.x - camera.x + f.z * x + f.x * z), (float) (o.y - camera.y + y), (float) (o.z - camera.z - f.x * x + f.z * z));
    }

    // The turn from the preview's frame to the world's (the way Bills faced).
    private static Matrix4f sceneRot() {
        return new Matrix4f().rotationY((float) Math.toRadians(-EsferaCutscene.yaw()));
    }

    // A point of Bills' frame (EsferaCena: x right) relative to the camera.
    private static Vector3f cena(double[] p, Vec3 camera) {
        return EsferaCutscene.world(p).subtract(camera).toVector3f();
    }

    // ---------- first part: before the world's effects ----------

    // Covers the world with the sky, then draws the stars, the far sun, the Earth, Bills, the target and the air around
    // the Earth on a clean depth buffer.
    static void backdrop(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(EsferaCutscene.casterId());
        if (caster == null) {
            return;
        }
        warm();
        Entity target = minecraft.level.getEntity(EsferaCutscene.targetId());
        EsferaSpec.Espaco espaco = EsferaCutscene.spec().espaco();
        EsferaCena cena = EsferaCutscene.cena();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        EsferaCutscene.Frame frame = EsferaCutscene.frame(partial);
        double t = frame.t();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f rot = sceneRot();
        Vector3f sunWorld = rot.transformDirection(new Vector3f(SUN_DIR));
        boolean shaders = FxShaders.esferaSky != null && FxShaders.esferaPlanet != null && FxShaders.esferaSun != null;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        try {
            int top = FxDraw.parse(espaco.corCeu(), 0x030820);
            int low = FxDraw.parse(espaco.corHorizonte(), 0x0C1C5C);
            if (shaders) {
                sky(rot, top, low);
            } else {
                flatSky(top, low);
            }
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            // The stars, far away around the camera, behind everything.
            boolean flat = FxShaders.flat();
            MultiBufferSource.BufferSource fx = FxWorld.buffers();
            FxDraw.begin(fx, event.getCamera(), flat);
            stars(espaco, rot);
            fx.endBatch(flat ? FxRenderTypes.FLAT_ALPHA : FxRenderTypes.VFX_ALPHA);
            fx.endBatch(flat ? FxRenderTypes.FLAT_ADD : FxRenderTypes.VFX_ADD);

            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            double spin = Math.toRadians(Math.max(0, t - cena.espaco) * espaco.girar() + 30);
            if (shaders) {
                // The real sun, far away (where the Earth's light comes from).
                Vector3f far = new Vector3f(sunWorld).mul(95);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                halo(far, 6.05f, 0.08f, FxDraw.rgb(0xFFF4D6), 1, (float) t);
                halo(far, 22.45f, 0.2f, FxDraw.rgb(0xFFC870), 0.45f, (float) t);
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                earth(cena, camera, spin, sunWorld);
            } else {
                cubePlanet(buffers, cena, camera, spin);
                buffers.endBatch();
            }

            dispatcher.setRenderShadow(false);
            body(dispatcher, caster, frame.bills(), new double[] {0, 0, 0}, camera, partial, buffers);
            if (target != null && target != caster) {
                double[] at = frame.alvoAt();
                body(dispatcher, target, frame.alvo(), new double[] {at[0], at[1] - 1, at[2]}, camera, partial, buffers);
            }
            buffers.endBatch();
            if (shaders) {
                air(cena, camera, sunWorld);
            }
        } finally {
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // One of the two bodies, posed by EsferaCena: its feet at base (Bills' frame) moved by the whole-body transform,
    // turned around its middle (a block over the feet). The entity is drawn straightened (body and head at yaw 180, so
    // its renderer adds no turn of its own) and the PoseStack does the rest: in that frame the body faces -z with +x on
    // its right, so the preview's Ry(guinada) Rx(inclinar) Rz(-rolar) becomes Ry(guinada) Rx(-inclinar) Rz(rolar).
    private static void body(EntityRenderDispatcher dispatcher, Entity entity, EsferaCena.Corpo corpo, double[] base, Vec3 camera, float partial,
            MultiBufferSource buffers) {
        if (!corpo.visivel()) {
            return;
        }
        EsferaCena.Raiz r = corpo.raiz();
        Vector3f feet = cena(new double[] {base[0] + r.x(), base[1] + r.y(), base[2] + r.z()}, camera);
        PoseStack pose = new PoseStack();
        pose.translate(feet.x, feet.y, feet.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - EsferaCutscene.yaw()));
        pose.translate(0, 1, 0);
        pose.mulPose(Axis.YP.rotationDegrees((float) r.guinada()));
        pose.mulPose(Axis.XP.rotationDegrees((float) -r.inclinar()));
        pose.mulPose(Axis.ZP.rotationDegrees((float) r.rolar()));
        pose.translate(0, -1, 0);
        float[] turn = straighten(entity, 180);
        try {
            dispatcher.render(entity, 0, 0, 0, 180, partial, pose, buffers, 0xF000F0);
        } finally {
            restore(entity, turn);
        }
    }

    // ---------- second part: with the world's effects ----------

    // The sun of destruction (charging on his finger, thrown, imploding, the giant sun), the sparks rushing into it
    // while it charges and the ring as it bursts.
    static void render(Vec3 camera, double now, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(EsferaCutscene.casterId()) == null) {
            return;
        }
        EsferaCena cena = EsferaCutscene.cena();
        EsferaCutscene.Frame frame = EsferaCutscene.frame(partial);
        double t = frame.t();
        EsferaCena.Bola bola = frame.bola();
        if (frame.esf() == null || bola.raio() <= 0) {
            return;
        }
        Vector3f ball = cena(frame.esf(), camera);
        float r = (float) bola.raio();
        if (bola.mao()) {
            // sparks rushing in while it charges
            float reach = 1.4f + r * 2.2f;
            for (int i = 0; i < 40; i++) {
                float life = 10 + 8 * FxWorld.hash(511, i, 1);
                float off = FxWorld.hash(511, i, 2);
                float u = (float) (((t + off * life) % life) / life);
                Vector3f d = FxWorld.sphere(511, i);
                float out = reach * (1 - u);
                Vector3f a = new Vector3f(d).mul(out + 0.5f).add(ball);
                Vector3f b = new Vector3f(d).mul(Math.max(r, out)).add(ball);
                FxDraw.ribbon(a, b, 0.035f, FxDraw.STREAK, 0.6f, SPARK, u * (1 - u) * 3.5f, true);
            }
        }
        // the ring as it bursts into the giant sun
        double rk = (t - cena.gigante) / 10;
        if (rk >= 0 && rk < 1) {
            float size = (float) (6 + 40 * EsferaCena.ease(EsferaCena.Ease.OUT, rk));
            FxDraw.billboard(ball, size, 0, FxDraw.RING, 0.094f, RING_COLOR, (float) ((1 - rk) * (1 - rk)), true);
        }
        if (FxShaders.esferaSun == null) {
            EsferaRenderer.drawSun(ball, null, r, (float) now, 4441);
            return;
        }
        float time = (float) (t * 0.05);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // the soft glows around it, then the body, then its light on top
        halo(ball, r * 5, 0.3f, FxDraw.rgb(0xD8420E), 0.3f, time);
        halo(ball, r * 3, 0.15f, FxDraw.rgb(0xFF9A1E), 0.22f, time);
        sun(ball, r * SOL_S, 0, time);
        sun(ball, r * SOL_S, 1, time);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---------- the sun (souls_esfera_sun) ----------

    // A square facing the camera but kept upright in the world (it never turns with the camera's tilt), reaching half
    // from its middle; uv 0..1 to its right and up.
    private static void upright(BufferBuilder b, Vector3f center, float half) {
        Vector3f z = new Vector3f(center).negate();
        if (z.lengthSquared() < 1.0E-8f) {
            z.set(0, 0, 1);
        }
        z.normalize();
        Vector3f x = new Vector3f(0, 1, 0).cross(z);
        if (x.lengthSquared() < 1.0E-8f) {
            x.set(1, 0, 0);
        }
        x.normalize();
        Vector3f y = new Vector3f(z).cross(x).normalize();
        x.mul(half);
        y.mul(half);
        b.addVertex(center.x - x.x - y.x, center.y - x.y - y.y, center.z - x.z - y.z).setUv(0, 0);
        b.addVertex(center.x + x.x - y.x, center.y + x.y - y.y, center.z + x.z - y.z).setUv(1, 0);
        b.addVertex(center.x + x.x + y.x, center.y + x.y + y.y, center.z + x.z + y.z).setUv(1, 1);
        b.addVertex(center.x - x.x + y.x, center.y - x.y + y.y, center.z - x.z + y.z).setUv(0, 1);
    }

    // Mode 0 the body (normal blend), 1 its light (added).
    private static void sun(Vector3f center, float half, int mode, float time) {
        ShaderInstance sh = FxShaders.esferaSun;
        sh.safeGetUniform("Mode").set((float) mode);
        sh.safeGetUniform("Time").set(time);
        sh.safeGetUniform("Seed").set(20f);
        RenderSystem.enableBlend();
        if (mode == 0) {
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        upright(b, center, half);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    // The preview's soft round sprite (added): full in the middle, 0.45 at inner, nothing at the edge; size = its width.
    // The caller sets depth and culling.
    private static void halo(Vector3f center, float size, float inner, float[] rgb, float opacity, float time) {
        ShaderInstance sh = FxShaders.esferaSun;
        sh.safeGetUniform("Mode").set(2f);
        sh.safeGetUniform("Time").set(time);
        sh.safeGetUniform("Inner").set(inner);
        sh.safeGetUniform("Tint").set(rgb[0], rgb[1], rgb[2], opacity);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        upright(b, center, size / 2);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---------- the sky (souls_esfera_sky) ----------

    private static void sky(Matrix4f rot, int top, int low) {
        ShaderInstance sh = FxShaders.esferaSky;
        float[] hi = FxDraw.rgb(top);
        float[] lo = FxDraw.rgb(low);
        sh.safeGetUniform("SceneRot").set(rot);
        sh.safeGetUniform("Top").set(hi[0], hi[1], hi[2]);
        sh.safeGetUniform("Low").set(lo[0], lo[1], lo[2]);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int la = 24, lo2 = 48;
        float radius = 100;
        for (int i = 0; i < la; i++) {
            for (int j = 0; j < lo2; j++) {
                for (int[] c : new int[][] {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}}) {
                    double th = Math.PI * c[0] / la, ph = 2 * Math.PI * c[1] / lo2;
                    b.addVertex((float) (Math.sin(th) * Math.cos(ph) * radius), (float) (Math.cos(th) * radius), (float) (Math.sin(th) * Math.sin(ph) * radius));
                }
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    // Without the shaders: a screen-wide gradient, dark at the top.
    private static void flatSky(int top, int bottom) {
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

    // The preview's stars: tiny fixed points, half anywhere and half packed along the milky way's band, some blue, a few
    // warm, each its own brightness.
    private static void stars(EsferaSpec.Espaco espaco, Matrix4f rot) {
        Vector3f axis = new Vector3f(0.35f, 0.8f, 0.48f).normalize();
        Vector3f bx = new Vector3f(axis).cross(new Vector3f(0, 0, 1)).normalize();
        Vector3f by = new Vector3f(axis).cross(bx);
        int count = Math.max(0, espaco.estrelas());
        for (int k = 0; k < count; k++) {
            Vector3f d;
            if (k % 2 == 1) {
                float a = FxWorld.hash(314, k, 1) * Mth.TWO_PI;
                float spread = (FxWorld.hash(314, k, 2) - 0.5f) * FxWorld.hash(314, k, 3) * 0.6f;
                d = new Vector3f(bx).mul(Mth.cos(a)).add(new Vector3f(by).mul(Mth.sin(a))).add(new Vector3f(axis).mul(spread));
            } else {
                d = FxWorld.sphere(313, k);
            }
            rot.transformDirection(d.normalize()).mul(90);
            float[] color = k % 3 == 0 ? BLUE_STAR : k % 7 == 0 ? WARM_STAR : WHITE;
            float bright = 0.4f + 0.6f * FxWorld.hash(313, k, 2);
            FxDraw.billboard(d, 0.32f, 0, FxDraw.SOFT, 0.9f, color, bright, true);
        }
    }

    // ---------- the Earth (souls_esfera_planet) ----------

    // A unit sphere laid out like three.js's SphereGeometry (so the map sits where it did on the preview): vertex
    // (ix, iy) at u = ix / w, v = iy / h from the top.
    private static final class Mesh {
        final int w;
        final int h;
        final float[] pos;

        Mesh(int w, int h) {
            this.w = w;
            this.h = h;
            pos = new float[(w + 1) * (h + 1) * 3];
            for (int iy = 0; iy <= h; iy++) {
                double theta = Math.PI * iy / h;
                for (int ix = 0; ix <= w; ix++) {
                    double phi = 2 * Math.PI * ix / w;
                    int k = (iy * (w + 1) + ix) * 3;
                    pos[k] = (float) (-Math.cos(phi) * Math.sin(theta));
                    pos[k + 1] = (float) Math.cos(theta);
                    pos[k + 2] = (float) (Math.sin(phi) * Math.sin(theta));
                }
            }
        }

        void draw(BufferBuilder b) {
            for (int iy = 0; iy < h; iy++) {
                for (int ix = 0; ix < w; ix++) {
                    vertex(b, ix + 1, iy);
                    vertex(b, ix, iy);
                    vertex(b, ix, iy + 1);
                    vertex(b, ix + 1, iy + 1);
                }
            }
        }

        private void vertex(BufferBuilder b, int ix, int iy) {
            int k = (iy * (w + 1) + ix) * 3;
            b.addVertex(pos[k], pos[k + 1], pos[k + 2]).setUv((float) ix / w, (float) iy / h);
        }
    }

    private static Mesh GROUND;
    private static Mesh AIR;

    // Where the Earth's middle is, relative to the camera, and its radius.
    private static Matrix4f earthAt(EsferaCena cena, Vec3 camera) {
        double[] p = cena.planeta;
        Vector3f o = scene(0, 0, 0, camera);
        return new Matrix4f().translation(o).mul(sceneRot()).translate((float) -p[0], (float) p[1], (float) p[2]);
    }

    private static void earth(EsferaCena cena, Vec3 camera, double spin, Vector3f sun) {
        float radius = (float) cena.raioTerra;
        Matrix4f at = earthAt(cena, camera);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        // the ground (solid; both faces drawn, the near one wins on depth)
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        sphere(GROUND, new Matrix4f(at).rotateX((float) Math.toRadians(72)).rotateZ((float) spin).scale(radius), 0, 0, 0, sun);
        // the clouds (see-through, only their near side)
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        sphere(GROUND, new Matrix4f(at).rotateX((float) Math.toRadians(72)).rotateZ((float) (spin * 1.4 + 0.4)).scale(radius * 1.015f), 1, 0, 0, sun);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    // The two shells of air (their far side, added on top), after the bodies like the preview's see-through things.
    private static void air(EsferaCena cena, Vec3 camera, Vector3f sun) {
        float radius = (float) cena.raioTerra;
        Matrix4f at = earthAt(cena, camera);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        sphere(AIR, new Matrix4f(at).scale(radius * 1.06f), 2, 4.0f, 1.6f, sun);
        sphere(AIR, new Matrix4f(at).scale(radius * 1.18f), 2, 2.2f, 0.5f, sun);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void sphere(Mesh mesh, Matrix4f model, int kind, float power, float strength, Vector3f sun) {
        ShaderInstance sh = FxShaders.esferaPlanet;
        sh.safeGetUniform("Model").set(model);
        sh.safeGetUniform("Sun").set(sun.x, sun.y, sun.z);
        sh.safeGetUniform("Kind").set((float) kind);
        sh.safeGetUniform("Power").set(power);
        sh.safeGetUniform("Strength").set(strength);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        mesh.draw(b);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    // Without the shaders: the old square Earth (Stark's earth texture, six 256 pixel faces side by side, 3 by 2) in
    // the round one's place.
    private static void cubePlanet(MultiBufferSource buffers, EsferaCena cena, Vec3 camera, double spin) {
        float half = (float) cena.raioTerra;
        PoseStack pose = new PoseStack();
        pose.mulPose(earthAt(cena, camera));
        pose.mulPose(Axis.YP.rotation((float) spin));
        pose.scale(half, half, half);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(EARTH));
        PoseStack.Pose last = pose.last();
        face(out, last, 0, new float[] {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1}, 0, 0, -1);
        face(out, last, 1, new float[] {1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1}, 1, 0, 0);
        face(out, last, 2, new float[] {1, -1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1}, 0, 0, 1);
        face(out, last, 4, new float[] {-1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1}, -1, 0, 0);
        face(out, last, 3, new float[] {-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1}, 0, 1, 0);
        face(out, last, 5, new float[] {-1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1}, 0, -1, 0);
    }

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

    // ---------- the explosion where the target lands (vfx "chamas", in the world) ----------
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

    // Turns body, head and look to one fixed yaw (level, head straight) just for drawing; gives back the old values.
    private static float[] straighten(Entity entity, float yaw) {
        float[] old = {entity.getYRot(), entity.yRotO, entity.getXRot(), entity.xRotO, 0, 0, 0, 0};
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0);
        entity.xRotO = 0;
        if (entity instanceof LivingEntity living) {
            old[4] = living.yBodyRot;
            old[5] = living.yBodyRotO;
            old[6] = living.yHeadRot;
            old[7] = living.yHeadRotO;
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
        }
        return old;
    }

    private static void restore(Entity entity, float[] old) {
        entity.setYRot(old[0]);
        entity.yRotO = old[1];
        entity.setXRot(old[2]);
        entity.xRotO = old[3];
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = old[4];
            living.yBodyRotO = old[5];
            living.yHeadRot = old[6];
            living.yHeadRotO = old[7];
        }
    }
}
