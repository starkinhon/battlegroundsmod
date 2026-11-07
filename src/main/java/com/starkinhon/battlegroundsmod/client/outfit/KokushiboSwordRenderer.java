package com.starkinhon.battlegroundsmod.client.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

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
// sheath is baked in the same space, so drawing both with no move puts the blade exactly inside it.
public class KokushiboSwordRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/kokushibo_sword.png");
    // Sword space: 1 = one pixel of the player model, 0 = the guard, +y the tip.
    public static final float GRIP = -3.6f, BLADE_BASE = 1.2f, TIP = 34.4f, BUTT = -10.6f;
    // Where the fist holds the grip, in the frame ItemInHandLayer draws the held item in (third person display transform
    // and toSword applied to (0, GRIP, 0); tools/espada/guardar.js uses the same). The sword moves turn the wrist about
    // it, so the grip stays in the fist (about the item's own pivot it slid out, Stark 2026-11-07).
    private static final org.joml.Vector3f GRIP_PIVOT = new org.joml.Vector3f(0f, -0.125f, 0.0875f);

    // Turns the held item about the wrist by a sword move's wrist angles (SkillAnimations.wrist).
    public static void turnWrist(PoseStack pose, float[] wrist, ItemStack stack) {
        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotationZYX(wrist[2], wrist[1], wrist[0]);
        if (isKokushibo(stack)) {
            pose.translate(GRIP_PIVOT.x, GRIP_PIVOT.y, GRIP_PIVOT.z);
            pose.mulPose(turn);
            pose.translate(-GRIP_PIVOT.x, -GRIP_PIVOT.y, -GRIP_PIVOT.z);
        } else {
            pose.mulPose(turn);
        }
    }

    // The awakened sword (KokushiboAwakenedSwordItem): the blade stretched to LONG_TIP, three blades branching off it.
    public static final float LONG_TIP = 39.4f;

    private static MeshModel katana;
    private static MeshModel katanaLong;
    private static MeshModel saya;
    private static boolean broken;

    private final boolean awakened;

    public KokushiboSwordRenderer() {
        this(false);
    }

    public KokushiboSwordRenderer(boolean awakened) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.awakened = awakened;
    }

    // Kokushibo's swords, plain or awakened: the same grip, so they are held and turned the same way.
    public static boolean isKokushibo(ItemStack stack) {
        return stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem
                || stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboAwakenedSwordItem;
    }

    // False once loading or drawing failed: the sword and its sheath stay invisible instead of crashing every frame.
    static boolean ready() {
        if (broken) {
            return false;
        }
        if (katana == null) {
            try {
                katana = MeshModel.load("kokushibo_katana");
                katanaLong = MeshModel.load("kokushibo_katana_long");
                saya = MeshModel.load("kokushibo_saya");
            } catch (RuntimeException e) {
                fail(e);
                return false;
            }
        }
        return true;
    }

    // The meshes, once ready() said true (FleshSheathe bends them).
    static MeshModel katana() {
        return katana;
    }

    static MeshModel saya() {
        return saya;
    }

    static void fail(RuntimeException e) {
        broken = true;
        BattlegroundsMod.LOGGER.error("Drawing Kokushibo's sword failed; it is invisible until the game restarts", e);
    }

    // Blade, guard and handle (and the sheath too when sheathed), in sword space through pose.
    static void emit(PoseStack.Pose pose, MultiBufferSource buffer, int light, int overlay, boolean sword, boolean sheath) {
        VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        if (sword) {
            katana.emit(out, pose, light, overlay);
        }
        if (sheath) {
            saya.emit(out, pose, light, overlay);
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (!ready()) {
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
                toSwordSpace(poseStack, awakened ? 0.39f : 0.45f, ((awakened ? LONG_TIP : TIP) + BUTT) / 2);
            }
            if (awakened) {
                katanaLong.emit(buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), poseStack.last(), light, overlay);
            } else {
                emit(poseStack.last(), buffer, light, overlay, true, false);
            }
        } catch (RuntimeException e) {
            fail(e);
        } finally {
            poseStack.popPose();
        }
    }

    // In hand: from the item's 0..1 square (after its display transform) to sword space. Also where
    // client/fx/SwordFx finds the blade.
    // Third person (Stark, 2026-11-07): a bit lower, a bit closer to the body, pointing straight ahead and laid
    // flatter (about 11 degrees up instead of the Aizen sword's 36).
    public static void toSword(PoseStack poseStack, boolean thirdPerson) {
        if (thirdPerson) {
            // The grip in the middle of the fist (Stark, 2026-11-07: it sat off to the side of the hand).
            poseStack.translate(2.825f / 16, 3.084f / 16, 0.5f);
            poseStack.mulPose(Axis.ZP.rotation(-0.2f));
        } else {
            poseStack.translate(3 / 16f, 3 / 16f, 0.5f);
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
