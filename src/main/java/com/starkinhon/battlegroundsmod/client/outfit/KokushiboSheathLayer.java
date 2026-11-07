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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Kokushibo's sheath on the left hip, samurai style: mouth just in front of the hip, edge up, the scabbard running
// back and down past the leg. With the sword in hand the sheath is empty; with it only in the inventory the sword
// rests inside (the two meshes share one space, so it fits the blade exactly).
// Putting it away (Stark, 2026-11-07): switching from the sword to anything else while still carrying it plays
// espada.guardar_kokushibo (tools/espada/guardar.js): a flick of the blade, the hand brings it across to the hip with
// the blade pointing back along the sheath, then it slides in. Up to HANDOVER the sword is drawn here in the hand
// (the same transforms as a held item, wrist turn included), then it blends onto the sheath's axis and slides home.
// Every client sees it on its own: it only needs the held item, which everyone already knows.
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
    // Ticks into the animation: in the hand until HANDOVER, blended onto the sheath by HANDOVER + BLEND, home at HOME;
    // the new item stays hidden until DONE.
    private static final float HANDOVER = 9, BLEND = 1.5f, HOME = 16, DONE = 22;

    private static final class State {
        boolean held;
        float start = -1;
        float slide = -1;
        int seen = Integer.MIN_VALUE;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ItemStack swordStack;

    // True while this entity is putting the sword away: its new main hand item is not drawn yet (ItemInHandLayerMixin).
    public static boolean puttingAway(net.minecraft.world.entity.LivingEntity entity) {
        State state = STATES.get(entity.getId());
        if (state == null || state.start < 0) {
            return false;
        }
        float t = entity.tickCount - state.start;
        return t >= -1 && t < DONE;
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
        boolean inHand = player.getMainHandItem().getItem() instanceof KokushiboSwordItem
                || player.getOffhandItem().getItem() instanceof KokushiboSwordItem;
        boolean carrying = Sheaths.carrying(player.getId());
        State state = STATES.computeIfAbsent(player.getId(), id -> {
            State s = new State();
            s.held = inHand;
            return s;
        });
        if (inHand) {
            state.start = -1;
        } else if (state.held && carrying && player.tickCount - state.seen <= 2) {
            // Only a switch seen as it happens: not one made while this body wasn't drawn (first person, out of view).
            state.start = player.tickCount;
            state.slide = -1;
            SkillAnimations.play(player.getId(), PUT_AWAY, false);
        }
        state.held = inHand;
        state.seen = player.tickCount;
        if (!inHand && !carrying) {
            return;
        }
        if (!KokushiboSwordRenderer.ready()) {
            return;
        }
        float t = state.start < 0 ? Float.MAX_VALUE : ageInTicks - state.start;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
        poseStack.pushPose();
        try {
            Matrix4f sheath = sheathMatrix(poseStack, 0);
            draw(poseStack, sheath, buffer, light, overlay, false, true);
            if (inHand) {
                return;
            }
            if (t >= HOME) {
                draw(poseStack, sheath, buffer, light, overlay, true, false);
                return;
            }
            Matrix4f hand = handMatrix(poseStack, player);
            if (t < HANDOVER) {
                draw(poseStack, hand, buffer, light, overlay, true, false);
                return;
            }
            if (state.slide < 0) {
                // How far out of the sheath the blade is when the hand lets it take over: the guard's place along it.
                Vector3f guard = hand.transformPosition(new Vector3f());
                new Matrix4f(sheath).invert().transformPosition(guard);
                state.slide = Math.max(0, Math.min(20, -guard.y));
            }
            float u = Math.min(1, (t - HANDOVER - BLEND) / (HOME - HANDOVER - BLEND));
            float out = t < HANDOVER + BLEND ? state.slide : state.slide * (1 - u * u * u);
            Matrix4f slid = sheathMatrix(poseStack, out);
            Matrix4f m = t < HANDOVER + BLEND ? blend(hand, slid, (t - HANDOVER) / BLEND) : slid;
            draw(poseStack, m, buffer, light, overlay, true, false);
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

    private static Matrix4f blend(Matrix4f a, Matrix4f b, float u) {
        u = Math.max(0, Math.min(1, u));
        Vector3f ta = a.getTranslation(new Vector3f()), tb = b.getTranslation(new Vector3f());
        Vector3f sa = a.getScale(new Vector3f()), sb = b.getScale(new Vector3f());
        Quaternionf qa = a.getNormalizedRotation(new Quaternionf()), qb = b.getNormalizedRotation(new Quaternionf());
        return new Matrix4f().translationRotateScale(ta.lerp(tb, u), qa.slerp(qb, u), sa.lerp(sb, u));
    }

    private static void draw(PoseStack poseStack, Matrix4f m, MultiBufferSource buffer, int light, int overlay, boolean sword, boolean sheath) {
        poseStack.pushPose();
        poseStack.last().pose().set(m);
        poseStack.last().normal().set(new Matrix3f(m).normal());
        KokushiboSwordRenderer.emit(poseStack.last(), buffer, light, overlay, sword, sheath);
        poseStack.popPose();
    }
}
