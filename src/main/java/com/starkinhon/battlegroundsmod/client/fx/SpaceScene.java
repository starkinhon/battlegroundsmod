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
import com.starkinhon.battlegroundsmod.client.PlanetCutscene;
import com.starkinhon.battlegroundsmod.skill.DestructionPlanetSpec;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Outer space for the Esfera da Destruição cutscene (client/EsferaCutscene), drawn over the whole world at the end of the
// level: a deep blue-purple sky, stars and soft nebulas, the square Earth (a giant textured cube, turning slowly) below,
// Bills himself floating, and the sun of destruction over his hand. Only the player in the cutscene sees it.
public final class SpaceScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLUE_STAR = FxDraw.rgb(0xBFD8FF);
    private static final float[][] NEBULA = {FxDraw.rgb(0x6A2BD9), FxDraw.rgb(0x2B5BD9), FxDraw.rgb(0xD96A2B), FxDraw.rgb(0x9B30FF)};
    private static final float[] ATMOSPHERE = FxDraw.rgb(0x59A8FF);
    static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/earth.png");

    private SpaceScene() {
    }

    public static boolean active() {
        return EsferaCutscene.inSpace() || PlanetCutscene.inSpace();
    }

    // Effects of this skill still show in space (his own flash and charge); everything else stays in the world below.
    static boolean owns(String skill) {
        return EsferaSpec.SKILL.equals(skill) || DestructionPlanetSpec.SKILL.equals(skill);
    }

    // First part, before the other world effects: covers the world with the sky, then draws the stars, the planet and
    // Bills on a clean depth buffer.
    static void backdrop(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        // The Destruction Touch's planet (Shift + V) uses the same space and Earth; PlanetScene draws it breaking.
        boolean breaking = PlanetCutscene.inSpace();
        int casterId = breaking ? PlanetCutscene.casterId() : EsferaCutscene.casterId();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(casterId);
        if (caster == null) {
            return;
        }
        EsferaSpec.Espaco espaco = breaking ? PlanetCutscene.spec().espaco() : EsferaCutscene.spec().espaco();
        sky(FxDraw.parse(espaco.corCeu(), 0x0B0626), FxDraw.parse(espaco.corHorizonte(), 0x1D2F8A));
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            // Stars and nebulas, far away around the camera, behind everything.
            boolean flat = FxShaders.flat();
            MultiBufferSource.BufferSource fx = FxWorld.buffers();
            FxDraw.begin(fx, event.getCamera(), flat);
            stars(espaco.estrelas(), ScreenFx.now() + partial);
            fx.endBatch(flat ? FxRenderTypes.FLAT_ADD : FxRenderTypes.VFX_ADD);

            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            if (breaking) {
                PlanetScene.solid(buffers, camera, partial);
            } else {
                planet(buffers, camera, espaco, EsferaCutscene.spaceAge(partial));
            }
            buffers.endBatch();
            Vec3 rel = caster.getPosition(partial).subtract(camera);
            minecraft.getEntityRenderDispatcher().render(caster, rel.x, rel.y, rel.z, Mth.lerp(partial, caster.yRotO, caster.getYRot()), partial,
                    new PoseStack(), buffers, 0xF000F0);
            buffers.endBatch();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // Second part, with the other world effects (additive, into the bloom): the planet's glow and the sphere.
    static void render(Vec3 camera, double now, float partial) {
        if (PlanetCutscene.inSpace()) {
            PlanetScene.render(camera, now, partial);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(EsferaCutscene.casterId());
        if (caster == null) {
            return;
        }
        EsferaSpec spec = EsferaCutscene.spec();
        float size = (float) spec.espaco().tamanho();
        Vector3f planet = EsferaCutscene.planetCenter().subtract(camera).toVector3f();
        FxDraw.billboard(planet, size * 2.3f, 0, FxDraw.SOFT, 0.1f, ATMOSPHERE, 0.35f, true);
        double[] radius = {1};
        Vec3 ball = EsferaCutscene.spherePos(caster, partial, radius);
        double s = EsferaCutscene.spaceAge(partial);
        int charge = Math.max(1, spec.tempo().cargaTicks());
        Vec3 flight = s < charge ? null : EsferaCutscene.planetTop().subtract(ball).normalize();
        EsferaRenderer.drawSun(ball.subtract(camera).toVector3f(), flight == null ? null : flight.toVector3f(), (float) radius[0], (float) now, 4441);
        // Light gathering into his hand while he charges.
        if (s < charge) {
            for (int k = 0; k < 24; k++) {
                float life = 14 + 10 * FxWorld.hash(77, k, 1);
                float u = (float) ((s + FxWorld.hash(77, k, 2) * life) % life) / life;
                Vector3f d = FxWorld.sphere(77, k);
                float out = (float) radius[0] * (3.5f - 2.6f * u);
                Vector3f from = new Vector3f(d).mul(out).add(ball.subtract(camera).toVector3f());
                Vector3f to = new Vector3f(d).mul(out - 1.2f).add(ball.subtract(camera).toVector3f());
                FxDraw.ribbon(from, to, 0.12f, FxDraw.STREAK, 0.8f, FxDraw.rgb(0xFFD040), u * (1 - u) * 3, true);
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

    // Fixed stars on a sphere around the camera, twinkling, and a few wide soft nebulas.
    private static void stars(int count, double now) {
        for (int k = 0; k < 7; k++) {
            Vector3f d = FxWorld.sphere(911, k).mul(95);
            FxDraw.billboard(d, 55 + 40 * FxWorld.hash(911, k, 3), FxWorld.hash(911, k, 4) * 6, FxDraw.SOFT, 0.05f, NEBULA[k % NEBULA.length],
                    0.12f + 0.08f * FxWorld.hash(911, k, 5), true);
        }
        for (int k = 0; k < count; k++) {
            Vector3f d = FxWorld.sphere(313, k).mul(90);
            float twinkle = 0.6f + 0.4f * Mth.sin((float) now * (0.08f + 0.1f * FxWorld.hash(313, k, 1)) + k);
            float size = 0.25f + 0.55f * FxWorld.hash(313, k, 2) * FxWorld.hash(313, k, 3);
            boolean big = k % 37 == 0;
            FxDraw.billboard(d, big ? size * 5 : size, 0, big ? FxDraw.FLARE : FxDraw.SOFT, big ? 4 / 32f : 0.9f, k % 3 == 0 ? BLUE_STAR : WHITE,
                    twinkle, true);
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
    static void face(VertexConsumer out, PoseStack.Pose pose, int tile, float[] c, float nx, float ny, float nz) {
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
