package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.FxRenderTypes;
import com.starkinhon.battlegroundsmod.item.DragonBallItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// The Esferas do Dragão as the 3D ball of the Shenlong cutscene (Stark, 2026-11-11: "usa esse modelo pras esferas"):
// an orange glass sphere with its red stars floating inside, drawn with souls_dball, the same colors and star layout
// as BALL_F of the approved preview (forja/shenlong). The stars sit on the ball's +z, which faces the viewer in the
// inventory. Without the shader (missing, or a shader pack on) it is a plain orange sphere.
public class DragonBallRenderer extends BlockEntityWithoutLevelRenderer {
    private static final int LAT = 18, LON = 28;
    private static final float R = 0.4f;
    private static boolean broken;

    public DragonBallRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (broken || !(stack.getItem() instanceof DragonBallItem ball)) {
            return;
        }
        try {
            boolean shader = FxRenderTypes.dballReady();
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID,
                    "textures/item/esfera_dragao_" + ball.stars() + ".png");
            VertexConsumer out = buffer.getBuffer(shader ? FxRenderTypes.DBALL : RenderType.entitySolid(sprite));
            PoseStack.Pose pose = poseStack.last();
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    vertex(out, pose, i, j, ball.stars(), shader, light, overlay);
                    vertex(out, pose, i + 1, j, ball.stars(), shader, light, overlay);
                    vertex(out, pose, i + 1, j + 1, ball.stars(), shader, light, overlay);
                    vertex(out, pose, i, j + 1, ball.stars(), shader, light, overlay);
                }
            }
        } catch (RuntimeException e) {
            broken = true;
            BattlegroundsMod.LOGGER.error("Drawing the Dragon Ball failed; it is invisible until the game restarts", e);
        }
    }

    // One point of the sphere; latitude runs from the +y pole to the -y pole, longitude goes around y, ordered so
    // the quads face outward.
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int i, int j, int stars, boolean shader, int light, int overlay) {
        double th = Math.PI * i / LAT, ph = 2 * Math.PI * j / LON;
        float nx = (float) (Math.sin(th) * Math.sin(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.cos(ph));
        out.addVertex(pose, 0.5f + nx * R, 0.5f + ny * R, 0.5f + nz * R);
        if (shader) {
            out.setColor((nx + 1) * 0.5f, (ny + 1) * 0.5f, (nz + 1) * 0.5f, stars / 7f);
            out.setUv(0, 0);
        } else {
            // the middle of the old ball sprite (orange), shaded lighter toward the viewer
            float k = 0.7f + 0.3f * Math.max(0, nz);
            out.setColor(k, k, k, 1f);
            out.setUv(4.5f / 16, 8.5f / 16);
        }
        out.setOverlay(overlay);
        out.setLight(light);
        out.setNormal(pose, nx, ny, nz);
    }
}
