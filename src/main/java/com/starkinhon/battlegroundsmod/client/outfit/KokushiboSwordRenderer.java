package com.starkinhon.battlegroundsmod.client.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Draws Kokushibo's sword item with the 3D model Stark sent (KokushiboSword.blend, baked to models/mesh/kokushibo_*.bin
// with its own texture). Laid out like AizenSwordRenderer: in hand the fist holds the handle just under the guard, tip
// forward and up in third person; in the inventory, on the ground and in frames it is shrunk to fit, centred. The
// sheath is baked in the same space, so drawing both with no move puts the blade exactly inside it. The blocky
// item uses a voxel remake (half a pixel per block, Minecraft style) baked to the same space.
public class KokushiboSwordRenderer extends BlockEntityWithoutLevelRenderer {
    // Index 0 = realistic (the .blend as sent), 1 = blocky (voxel remake, Minecraft style).
    private static final ResourceLocation[] TEXTURE = {
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/kokushibo_sword.png"),
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/kokushibo_sword_blocky.png")};
    private static final String[] SUFFIX = {"", "_blocky"};
    // Sword space: 1 = one pixel of the player model, 0 = the guard, +y the tip.
    public static final float GRIP = -3.6f, BLADE_BASE = 1.2f, TIP = 34.4f, BUTT = -10.6f;

    private static final MeshModel[] KATANA = new MeshModel[2];
    private static final MeshModel[] SAYA = new MeshModel[2];
    private static boolean broken;

    public KokushiboSwordRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    // False once loading or drawing failed: the sword and its sheath stay invisible instead of crashing every frame.
    static boolean ready(int look) {
        if (broken) {
            return false;
        }
        if (KATANA[look] == null) {
            try {
                KATANA[look] = MeshModel.load("kokushibo_katana" + SUFFIX[look]);
                SAYA[look] = MeshModel.load("kokushibo_saya" + SUFFIX[look]);
            } catch (RuntimeException e) {
                fail(e);
                return false;
            }
        }
        return true;
    }

    static void fail(RuntimeException e) {
        broken = true;
        BattlegroundsMod.LOGGER.error("Drawing Kokushibo's sword failed; it is invisible until the game restarts", e);
    }

    // Blade, guard and handle (and the sheath too when sheathed), in sword space through pose.
    static void emit(int look, PoseStack.Pose pose, MultiBufferSource buffer, int light, int overlay, boolean sword, boolean sheath) {
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE[look]));
        if (sword) {
            KATANA[look].emit(out, pose, light, overlay);
        }
        if (sheath) {
            SAYA[look].emit(out, pose, light, overlay);
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        int look = stack.getItem() instanceof KokushiboSwordItem sword && sword.blocky ? 1 : 0;
        if (!ready(look)) {
            return;
        }
        poseStack.pushPose();
        try {
            boolean firstPerson = context.firstPerson();
            boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            if (firstPerson || thirdPerson) {
                toSword(poseStack, thirdPerson);
            } else {
                poseStack.translate(0.5f, 0.5f, 0.5f);
                toSwordSpace(poseStack, 0.45f, (TIP + BUTT) / 2);
            }
            emit(look, poseStack.last(), buffer, light, overlay, true, false);
        } catch (RuntimeException e) {
            fail(e);
        } finally {
            poseStack.popPose();
        }
    }

    // In hand: from the item's 0..1 square (after its display transform) to sword space. Also where
    // client/fx/SwordFx finds the blade.
    public static void toSword(PoseStack poseStack, boolean thirdPerson) {
        poseStack.translate(3 / 16f, 3 / 16f, 0.5f);
        if (thirdPerson) {
            poseStack.mulPose(Axis.ZP.rotation(-0.62f));
        }
        toSwordSpace(poseStack, thirdPerson ? 1.15f : 1.0f, GRIP);
    }

    private static void toSwordSpace(PoseStack poseStack, float scale, float anchor) {
        poseStack.mulPose(Axis.ZP.rotation((float) -Math.PI / 4));
        poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
        poseStack.scale(scale / 16, scale / 16, scale / 16);
        poseStack.translate(0, -anchor, 0);
    }
}
