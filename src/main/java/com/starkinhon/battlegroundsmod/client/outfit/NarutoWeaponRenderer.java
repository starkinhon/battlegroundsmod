// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.outfit;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.client.SkillAnimations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom 3D renderer for the low-poly Naruto weapons (Gunbai, Kubikiribōchō, Kusanagi, Kunai, Hiraishin, Shuriken).
 * Applies exact 1:1 calibrated 3rd-person, 1st-person, and (for Gunbai) 1st-person defense transforms from the
 * hand previewer, renders centered 3D icons in GUI/frames/ground, and emits raw model space for thrown projectiles.
 */
public class NarutoWeaponRenderer extends BlockEntityWithoutLevelRenderer {
    public record Pose3D(float px, float py, float pz, float rx, float ry, float rz, float sc) {
        public Quaternionf quat() {
            return new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(ry),
                    (float) Math.toRadians(rx),
                    (float) Math.toRadians(rz));
        }
    }

    // Calibrated 3rd-person poses (also used by SwordFx to align trails)
    public static final Pose3D GUNBAI_3P       = new Pose3D(0.00f,  0.01f, -0.06f,   0f,  90f, -86f, 1.15f);
    public static final Pose3D KUBIKI_3P       = new Pose3D(0.01f, -0.01f, -0.14f,   1f,  90f, -91f, 1.25f);
    public static final Pose3D KUSANAGI_3P     = new Pose3D(0.00f, -0.03f, -0.09f,   1f,  90f, -93f, 1.20f);
    public static final Pose3D KUNAI_3P        = new Pose3D(0.00f,  0.00f, -0.03f,   3f,  89f, -87f, 0.72f);
    public static final Pose3D HIRAISHIN_3P    = new Pose3D(0.00f,  0.00f, -0.03f,   0f,  90f, -89f, 0.72f);
    public static final Pose3D SHURIKEN_3P     = new Pose3D(0.00f, -0.04f,  0.02f, -20f,  -1f, -90f, 0.42f);

    // Calibrated 1st-person poses
    public static final Pose3D GUNBAI_1P       = new Pose3D(0.30f, -0.09f,  0.14f,  -6f,  170f,  15f, 0.70f);
    public static final Pose3D GUNBAI_1P_DEF   = new Pose3D(0.01f, -0.16f, -0.01f,  21f,   49f,  25f, 1.04f);
    public static final Pose3D KUBIKI_1P       = new Pose3D(0.35f, -0.09f,  0.12f,  -5f,  165f,  15f, 0.76f);
    public static final Pose3D KUSANAGI_1P     = new Pose3D(0.34f, -0.03f,  0.20f,  -4f,  165f,  12f, 0.84f);
    public static final Pose3D KUNAI_1P        = new Pose3D(0.34f,  0.04f,  0.18f, -15f, -174f,  13f, 0.68f);
    public static final Pose3D HIRAISHIN_1P    = new Pose3D(0.34f,  0.04f,  0.18f,  -9f,  168f,   9f, 0.72f);
    public static final Pose3D SHURIKEN_1P     = new Pose3D(0.32f,  0.02f,  0.16f,  15f,  -20f, -37f, 0.65f);

    private final String meshName;
    private final ResourceLocation texture;
    private final Pose3D pose3p;
    private final Pose3D pose1p;
    private final Pose3D pose1pDef;
    private final float guiCenterY;
    private final float guiScale;
    private final float guiRotX;
    private final float guiRotY;
    private final float guiRotZ;

    private MeshModel mesh;
    private boolean broken;
    private float defBlend = 0f;
    private long lastNs = 0L;

    public NarutoWeaponRenderer(String name, Pose3D pose3p, Pose3D pose1p, Pose3D pose1pDef,
                                float guiCenterY, float guiScale, float guiRotX, float guiRotY, float guiRotZ) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.meshName = name;
        this.texture = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/" + name + ".png");
        this.pose3p = pose3p;
        this.pose1p = pose1p;
        this.pose1pDef = pose1pDef;
        this.guiCenterY = guiCenterY;
        this.guiScale = guiScale;
        this.guiRotX = guiRotX;
        this.guiRotY = guiRotY;
        this.guiRotZ = guiRotZ;
    }

    public static void applyThirdPerson(PoseStack poseStack, Pose3D p) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));
        poseStack.translate(p.px, p.py, p.pz);
        poseStack.mulPose(p.quat());
        poseStack.scale(p.sc, p.sc, p.sc);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    /**
     * Used by SwordFx to transform blade base/tip points into the exact calibrated 3rd-person hand pose.
     * Returns true if the stack is one of the Naruto weapons handled by this renderer.
     */
    public static boolean toThirdPersonForStack(ItemStack stack, PoseStack poseStack) {
        Item item = stack.getItem();
        if (item == ModItems.KUSANAGI.get()) {
            applyThirdPerson(poseStack, KUSANAGI_3P);
            return true;
        }
        if (item == ModItems.GUNBAI.get()) {
            applyThirdPerson(poseStack, GUNBAI_3P);
            return true;
        }
        if (item == ModItems.KUBIKIRIBOCHO.get()) {
            applyThirdPerson(poseStack, KUBIKI_3P);
            return true;
        }
        if (item == ModItems.KUNAI.get()) {
            applyThirdPerson(poseStack, KUNAI_3P);
            return true;
        }
        if (item == ModItems.HIRAISHIN_KUNAI.get()) {
            applyThirdPerson(poseStack, HIRAISHIN_3P);
            return true;
        }
        return false;
    }

    private boolean ready() {
        if (broken) {
            return false;
        }
        if (mesh == null) {
            try {
                mesh = MeshModel.load(meshName);
            } catch (RuntimeException e) {
                broken = true;
                BattlegroundsMod.LOGGER.error("Failed to load {} mesh", meshName, e);
                return false;
            }
        }
        return true;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (!ready()) {
            return;
        }
        poseStack.pushPose();
        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            applyThirdPerson(poseStack, pose3p);
        } else if (context.firstPerson()) {
            poseStack.translate(0.5f, 0.5f, 0.5f);
            if (pose1pDef != null) {
                Minecraft mc = Minecraft.getInstance();
                boolean defending = mc.player != null
                        && (mc.player.isBlocking() || SkillAnimations.guarding(mc.player));
                long now = System.nanoTime();
                float dt = lastNs == 0L ? 0.016f : Math.min(0.05f, (now - lastNs) * 1.0e-9f);
                lastNs = now;
                float target = defending ? 1f : 0f;
                defBlend += (target - defBlend) * Math.min(1f, dt * 18f);
                if (Math.abs(target - defBlend) < 0.002f) {
                    defBlend = target;
                }
                float t = defBlend;
                float px = Mth.lerp(t, pose1p.px, pose1pDef.px);
                float py = Mth.lerp(t, pose1p.py, pose1pDef.py);
                float pz = Mth.lerp(t, pose1p.pz, pose1pDef.pz);
                float sc = Mth.lerp(t, pose1p.sc, pose1pDef.sc);
                Quaternionf q = pose1p.quat().slerp(pose1pDef.quat(), t);
                poseStack.translate(px, py, pz);
                poseStack.mulPose(q);
                poseStack.scale(sc, sc, sc);
            } else {
                poseStack.translate(pose1p.px, pose1p.py, pose1p.pz);
                poseStack.mulPose(pose1p.quat());
                poseStack.scale(pose1p.sc, pose1p.sc, pose1p.sc);
            }
            poseStack.translate(-0.5f, -0.5f, -0.5f);
        } else if (context == ItemDisplayContext.NONE) {
            // Raw [0..1] space for NinjaThrownRenderer (thrown Kunai & Shuriken)
        } else {
            // GUI / Ground / Fixed / Head icon pose
            float scaleMult = (context == ItemDisplayContext.GROUND) ? 0.55f
                    : (context == ItemDisplayContext.FIXED) ? 0.90f : 1.0f;
            poseStack.translate(0.5f, 0.5f, 0.5f);
            if (guiRotX != 0f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(guiRotX));
            }
            if (guiRotY != 0f) {
                poseStack.mulPose(Axis.YP.rotationDegrees(guiRotY));
            }
            if (guiRotZ != 0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(guiRotZ));
            }
            float s = guiScale * scaleMult;
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, -guiCenterY, -0.5f);
        }
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        mesh.emit(out, poseStack.last(), light, overlay);
        poseStack.popPose();
    }
}
