package com.starkinhon.battlegroundsmod.client.fx;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.powers.gojo.VazioRelampago;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Gojo's Expansão de 0,2 s on the screen (server: power/powers/gojo/VazioRelampago; the look approved in the
// Visualizador, scene gojo-dom02, from Stark's reference video). Over the finished world, in screen space:
// the hand sign (0-7), one tick of a spiky black blot round Gojo (7), black with everyone dark (8-10), the void of our
// own Expansão lighting up (10-30: the black hole above him, ink, the water floor, those caught in grey; fx_void_flash),
// then it closes in an X across the whole screen that turns while its arms thin into two lines and vanish (30-49).
// The people near are drawn again over it (the world's depth is kept, so walls still hide them).
public final class VoidFlashScene {
    private static final ResourceLocation INK = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/fx/gojo_ink_atlas.png");
    private static final int BLOT = VazioRelampago.SNAP;
    private static final int BLACK = BLOT + 1;
    private static final int VOID = BLOT + 3;
    private static final int GROWN = VOID + 10;
    private static final int X_START = 30;
    private static final int X_THIN = 44;
    private static final int END = 49;
    // The people drawn over it: this close to where it opened.
    private static final double PEOPLE_REACH = 48;

    private static int caster = -1;
    private static Vec3 center = Vec3.ZERO;
    private static double start = -1e9;

    private VoidFlashScene() {
    }

    public static void start(int casterId, Vec3 pos) {
        caster = casterId;
        center = pos;
        start = ScreenFx.now();
    }

    public static void clear() {
        start = -1e9;
        caster = -1;
    }

    public static boolean active() {
        double age = ScreenFx.now() - start;
        return age >= 0 && age < END + 1;
    }

    static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = FxShaders.voidFlash;
        if (minecraft.level == null || shader == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double age = ScreenFx.now() + partial - start;
        int mode = age < BLOT ? 0 : age < BLACK ? 1 : age < X_START ? 2 : age < END ? 3 : 0;
        if (mode == 0) {
            return;
        }
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        var target = minecraft.getMainRenderTarget();
        float w = target.width;
        float h = target.height;

        Entity gojo = minecraft.level.getEntity(caster);
        Vec3 feet = gojo != null ? gojo.getPosition(partial) : center;
        float yaw = gojo instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD : 0;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f camRight = new Vector3f(cam.getLeftVector()).negate();
        Vec3 xPoint = feet.add(0, 1.15, 0);
        Vec3 holePoint = feet.add(right.scale(0.35)).add(forward.scale(0.4)).add(0, 2.6, 0);
        if (gojo == cam.getEntity() && !cam.isDetached()) {
            // Gojo in first person: the X crosses in front of him and the hole opens ahead, up in the sky.
            Vec3 lookV = new Vec3(look.x(), look.y(), look.z());
            xPoint = camera.add(lookV.scale(4)).add(0, -0.6, 0);
            holePoint = camera.add(lookV.scale(12)).add(0, 2.5, 0);
        }
        float[] xc = screen(xPoint.subtract(camera), proj, view, w, h);
        float[] hc = screen(holePoint.subtract(camera), proj, view, w, h);
        Vec3 flat = new Vec3(look.x(), 0, look.z());
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        float horizon = screen(flat.scale(1000), proj, view, w, h)[1];
        LivingEntity victim = nearest(minecraft, gojo, feet, partial);
        Vec3 victimFeet = victim != null ? victim.getPosition(partial) : camera.add(1000, 0, 1000);

        double xu = Mth.clamp((age - X_START) / (END - X_START), 0, 1);
        float turn = (float) (0.75 * Math.sin(Math.PI / 2 * xu));
        double thin = Mth.clamp((age - X_START) / (X_THIN - X_START), 0, 1);
        double arm = age < X_THIN ? 0.5 * Math.pow(1 - thin, 1.7) + 0.0012 : 0.0012 * (1 - (age - X_THIN) / (END - X_THIN));

        shader.safeGetUniform("Screen").set(w, h);
        shader.safeGetUniform("XCenter").set(xc[0], xc[1]);
        shader.safeGetUniform("HoleCenter").set(hc[0], hc[1]);
        shader.safeGetUniform("HoleSize").set(0.24f * h);
        shader.safeGetUniform("Horizon").set(horizon);
        shader.safeGetUniform("Mode").set((float) mode);
        shader.safeGetUniform("BlotSize").set((float) Mth.lerp(Mth.clamp(age - BLOT, 0, 1), 0.14, 0.26) * h);
        shader.safeGetUniform("Fade").set((float) smooth((age - VOID) / 8));
        shader.safeGetUniform("Grow").set((float) Mth.lerp(easeOut((age - VOID) / (GROWN - VOID)), 0.75, 1));
        shader.safeGetUniform("ArmWidth").set((float) (arm * Math.hypot(w, h)));
        shader.safeGetUniform("Angles").set(-0.45f + turn, 1.18f + turn * 1.1f);
        shader.safeGetUniform("Now").set((float) age);
        shader.safeGetUniform("CamFwd").set(look.x(), look.y(), look.z());
        shader.safeGetUniform("CamRight").set(camRight.x(), camRight.y(), camRight.z());
        shader.safeGetUniform("CamUp").set(up.x(), up.y(), up.z());
        shader.safeGetUniform("TanHalf").set(1 / proj.m00(), 1 / proj.m11());
        shader.safeGetUniform("FloorDepth").set((float) Math.max(0.3, camera.y - feet.y));
        shader.safeGetUniform("CasterRel").set((float) (feet.x - camera.x), (float) (feet.z - camera.z));
        shader.safeGetUniform("TargetRel").set((float) (victimFeet.x - camera.x), (float) (victimFeet.z - camera.z));
        quad(shader);

        // Black: everyone dark. In the void: Gojo fully lit, the rest drained grey (like inside the Expansão).
        people(event, minecraft, gojo, partial, age < VOID);
    }

    private static void quad(ShaderInstance shader) {
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
        RenderSystem.setShaderTexture(0, INK);
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(0, 0, 0);
        builder.addVertex(1, 0, 0);
        builder.addVertex(1, 1, 0);
        builder.addVertex(0, 1, 0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void people(RenderLevelStageEvent event, Minecraft minecraft, Entity gojo, float partial, boolean dark) {
        Vec3 camera = event.getCamera().getPosition();
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
            for (int pass = 0; pass < 2; pass++) {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (!(entity instanceof LivingEntity) || entity.isInvisible() || (entity == viewer && !detached)
                            || entity.getPosition(partial).distanceTo(center) > PEOPLE_REACH) {
                        continue;
                    }
                    boolean caught = entity != gojo;
                    if (caught != (pass == 1)) {
                        continue;
                    }
                    Vec3 rel = entity.getPosition(partial).subtract(camera);
                    float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                    dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
                }
                if (dark) {
                    RenderSystem.setShaderColor(0.16f, 0.16f, 0.2f, 1);
                } else if (pass == 1) {
                    RenderSystem.setShaderColor(0.58f, 0.6f, 0.66f, 1);
                }
                buffers.endBatch();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // The nearest one to Gojo besides him (the ripples on the floor under them).
    private static LivingEntity nearest(Minecraft minecraft, Entity gojo, Vec3 feet, float partial) {
        LivingEntity best = null;
        double bestD = VazioRelampago.RADIUS * VazioRelampago.RADIUS;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity != gojo) {
                double d = entity.getPosition(partial).distanceToSqr(feet);
                if (d < bestD) {
                    bestD = d;
                    best = living;
                }
            }
        }
        return best;
    }

    // A camera-relative point in screen pixels (y up, like gl_FragCoord).
    private static float[] screen(Vec3 rel, Matrix4f proj, Matrix4f view, float w, float h) {
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1);
        view.transform(clip);
        proj.transform(clip);
        if (clip.w <= 0.01f) {
            return new float[] {w * 0.5f, h * 0.5f};
        }
        return new float[] {(clip.x / clip.w * 0.5f + 0.5f) * w, (clip.y / clip.w * 0.5f + 0.5f) * h};
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double easeOut(double x) {
        double u = 1 - Mth.clamp(x, 0, 1);
        return 1 - u * u * u;
    }
}
