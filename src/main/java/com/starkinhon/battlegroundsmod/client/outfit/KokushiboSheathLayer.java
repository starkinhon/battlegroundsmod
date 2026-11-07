package com.starkinhon.battlegroundsmod.client.outfit;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.client.SkillAnimations;
import com.starkinhon.battlegroundsmod.item.KokushiboSwordItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Kokushibo's sheath on the left hip, samurai style: mouth just in front of the hip, edge up, the scabbard running
// back and down past the leg. The sword rests inside whenever it isn't drawn: carried in the inventory, and also held
// out of combat mode (Stark, 2026-11-07: it only comes out to fight; Sheaths, from power/PowerLogic.sheathCode).
// Drawing it: the combo's first click is the quick draw cut (espada.sacar_kokushibo), whose first frame has the hand
// holding the sword in the sheath and pulls it out along it; it is just the held item from the start.
// Putting it away: when it stops being drawn while still carried (combat mode ended, or switched to another slot) it
// plays espada.guardar_kokushibo (tools/espada/guardar.js): a flick of the blade, the hand brings the tip to the
// sheath's mouth and pushes it home along the sheath. Until HOME the sword is drawn here in the hand (the same
// transforms as a held item, wrist turn included; the hand never lets go, Stark 2026-11-07), then in the sheath, where
// the hand left it (the animation is fitted to within about 2 px).
// Every client sees it on its own, from the held item and the drawn flag the server sends.
public class KokushiboSheathLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // Model space (1 = one pixel, +y down, -z the front, +x the player's left), relative to the body's pivot.
    private static final Vector3f GUARD_AT = new Vector3f(4.9f, 10.6f, -3.2f);
    private static final Quaternionf TURN;

    static {
        Vector3f tip = new Vector3f(0.12f, 0.3f, 0.95f).normalize();
        Vector3f edge = new Vector3f(0, -1, 0);
        edge.sub(new Vector3f(tip).mul(edge.dot(tip))).normalize();
        Vector3f side = new Vector3f(edge).cross(tip);
        TURN = new Matrix3f(edge, tip, side).getNormalizedRotation(new Quaternionf());
    }

    public static final String PUT_AWAY = "espada.guardar_kokushibo";
    // Ticks into putting it away: in the hand until HOME; the new item stays hidden until DONE.
    private static final float HOME = 16, DONE = 22;

    private static final class State {
        boolean out;
        float putStart = -1;
        int seen = Integer.MIN_VALUE;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ItemStack swordStack;

    private static boolean holdsMain(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof KokushiboSwordItem;
    }

    // True while the right hand's item is not drawn by Minecraft (ItemInHandLayerMixin, FirstPersonAnimation): the sword
    // is in the sheath though selected, or it is being put away (drawn here, also after switching to something else).
    public static boolean hidesHand(LivingEntity entity) {
        if (holdsMain(entity) && !Sheaths.drawn(entity.getId())) {
            return true;
        }
        State state = STATES.get(entity.getId());
        if (state == null) {
            return false;
        }
        if (state.putStart >= 0) {
            float t = entity.tickCount - state.putStart;
            return t >= -1 && t < DONE;
        }
        return false;
    }

    public KokushiboSheathLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator() || !getParentModel().body.visible) {
            return;
        }
        boolean holds = holdsMain(player) || player.getOffhandItem().getItem() instanceof KokushiboSwordItem;
        boolean out = holds && Sheaths.drawn(player.getId());
        boolean carrying = Sheaths.carrying(player.getId());
        State state = STATES.computeIfAbsent(player.getId(), id -> {
            State s = new State();
            s.out = out;
            return s;
        });
        // Only a change seen as it happens: not one made while this body wasn't drawn (first person, out of view).
        boolean watching = player.tickCount - state.seen <= 2;
        if (out) {
            state.putStart = -1;
        } else if (state.out) {
            state.putStart = watching && carrying ? player.tickCount : -1;
            if (state.putStart >= 0) {
                SkillAnimations.play(player.getId(), PUT_AWAY, false);
            }
        }
        state.out = out;
        state.seen = player.tickCount;
        if (!out && !carrying) {
            return;
        }
        if (!KokushiboSwordRenderer.ready()) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        poseStack.pushPose();
        try {
            Matrix4f sheath = sheathMatrix(poseStack, 0);
            draw(poseStack, sheath, buffer, light, overlay, false, true);
            if (out) {
                return;
            }
            float t = state.putStart < 0 ? Float.MAX_VALUE : ageInTicks - state.putStart;
            draw(poseStack, t >= HOME ? sheath : handMatrix(poseStack, player), buffer, light, overlay, true, false);
        } catch (RuntimeException e) {
            KokushiboSwordRenderer.fail(e);
        } finally {
            poseStack.popPose();
        }
    }

    // The sheath's space (the sword's own space, sheathed), the blade pulled out of it by out pixels.
    private Matrix4f sheathMatrix(PoseStack poseStack, float out) {
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        poseStack.translate(GUARD_AT.x, GUARD_AT.y, GUARD_AT.z);
        poseStack.mulPose(TURN);
        poseStack.translate(0, -out, 0);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return m;
    }

    // The sword's space as if held in the right hand: ItemInHandLayer's transforms, the wrist turn of the animation,
    // the item's third person display transform and KokushiboSwordRenderer.toSword.
    private Matrix4f handMatrix(PoseStack poseStack, AbstractClientPlayer player) {
        if (swordStack == null) {
            swordStack = new ItemStack(ModItems.KOKUSHIBO_SWORD.get());
        }
        poseStack.pushPose();
        getParentModel().rightArm.translateAndRotate(poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.translate(1 / 16f, 0.125f, -0.625f);
        float[] wrist = SkillAnimations.wrist(player);
        if (wrist != null) {
            poseStack.mulPose(new Quaternionf().rotationZYX(wrist[2], wrist[1], wrist[0]));
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(swordStack, player.level(), player, player.getId());
        model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poseStack, false);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        KokushiboSwordRenderer.toSword(poseStack, true);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return m;
    }

    private static void draw(PoseStack poseStack, Matrix4f m, MultiBufferSource buffer, int light, int overlay, boolean sword, boolean sheath) {
        poseStack.pushPose();
        poseStack.last().pose().set(m);
        poseStack.last().normal().set(new Matrix3f(m).normal());
        KokushiboSwordRenderer.emit(poseStack.last(), buffer, light, overlay, sword, sheath);
        poseStack.popPose();
    }
}
