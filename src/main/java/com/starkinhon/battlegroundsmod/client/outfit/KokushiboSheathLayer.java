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
import com.starkinhon.battlegroundsmod.combat.SwordArts;
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
// Drawing it, the hand never lets go of the sword (Stark, 2026-11-07); the right arm can't quite reach the sheath where it hangs,
// so while the blade comes out the sheath leaves the hip to meet it (the left hand holds it there), SHEATH_OUT
// pixels of blade still out, and goes back after.
// Drawing it: the combo's first click is the quick draw cut (espada.sacar_kokushibo, tools/espada/guardar.js): the
// hand on the grip pulls the blade out along the sheath, then cuts. The sword is just the held item.
// Putting it away: when it stops being drawn while still carried (combat mode ended, or switched to another slot) the
// sword goes back by itself, as living flesh (FleshSheathe, Stark 2026-11-07): it melts out of the hand, flies over
// his left side and the sheath swallows it. Only the right arm is held out (holdArm) until the hand lets go: walking,
// running and flying go on as normal (Stark, 2026-11-07: the whole body froze while walking).
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

    // Ticks into putting it away until the new item may show: when the hand has let go of the flesh.
    private static final float DONE = FleshSheathe.LET_GO;
    // Ticks into the quick draw when the blade has left the sheath, and when the sheath is back on the hip.
    private static final float DRAWN_OUT = 2, DRAW_BACK = 4;
    // Pixels of blade out of the sheath over time, {tick, pixels} pairs, as the animations' keys place the hand.
    private static final float[] QUICK_DRAW_OUT = {0, 0, 1, 4, 2, 8};

    private static final class State {
        boolean out;
        float putStart = -1;
        // The sheath where the blade left it, relative to its place on the hip (the quick draw).
        Matrix4f leftAt;
        FleshSheathe.Run flesh;
        int seen = Integer.MIN_VALUE;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ItemStack swordStack;
    private static boolean fleshBroken;

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

    // The right arm held out with the sword while it comes alive, on top of whatever the body is doing (walking,
    // flying), until the hand lets go; then it blends back. From PlayerModelMixin, after the vanilla pose.
    private static final float HOLD_X = (float) Math.toRadians(-18), LET_GO_BLEND = 6;

    public static void holdArm(LivingEntity entity, net.minecraft.client.model.HumanoidModel<?> model, float ageInTicks) {
        State state = STATES.get(entity.getId());
        if (state == null || state.putStart < 0 || ageInTicks == 0) {
            return;
        }
        float t = ageInTicks - state.putStart;
        if (t < 0 || t > DONE + LET_GO_BLEND) {
            return;
        }
        float w = t < DONE ? Math.min(1, t / 3) : 1 - (t - DONE) / LET_GO_BLEND;
        model.rightArm.xRot += (HOLD_X - model.rightArm.xRot) * w;
        model.rightArm.yRot += (0 - model.rightArm.yRot) * w;
        model.rightArm.zRot += (0 - model.rightArm.zRot) * w;
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
        // The sword stretched (H): the sheath is eaten into the blade, and grows back when it shrinks (Stark, 2026-11-08).
        float left = SwordStretch.sheathLeft(SwordStretch.tick(player, partialTick));
        if (left < 1) {
            // nothing to put away into while the sheath is gone or going
            state.putStart = -1;
            state.flesh = null;
            state.leftAt = null;
            state.out = out;
            state.seen = player.tickCount;
            if ((out || carrying) && left > 0 && KokushiboSwordRenderer.ready()) {
                poseStack.pushPose();
                try {
                    Matrix4f rest = sheathMatrix(poseStack, 0);
                    poseStack.last().pose().set(rest);
                    poseStack.last().normal().set(new Matrix3f(rest).normal());
                    if (!SwordStretch.renderSheath(left, ageInTicks / 20f, poseStack.last(), buffer, light)) {
                        KokushiboSwordRenderer.emit(poseStack.last(), buffer, light, LivingEntityRenderer.getOverlayCoords(player, 0), false, true);
                    }
                } catch (RuntimeException e) {
                    KokushiboSwordRenderer.fail(e);
                } finally {
                    poseStack.popPose();
                }
            }
            return;
        }
        if (out) {
            state.putStart = -1;
        } else if (state.out) {
            state.putStart = watching && carrying ? player.tickCount : -1;
            if (state.putStart >= 0) {
                state.flesh = new FleshSheathe.Run();
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
            Matrix4f rest = sheathMatrix(poseStack, 0);
            Matrix4f sheath = rest;
            Matrix4f hand = null;
            if (out) {
                String move = SkillAnimations.swordMoveName(player);
                float t = ageInTicks - SkillAnimations.swordMoveStart(player);
                if (SwordArts.QUICK_DRAW.equals(move) && t >= 0 && t < DRAW_BACK) {
                    if (t <= DRAWN_OUT) {
                        hand = handMatrix(poseStack, player);
                        sheath = new Matrix4f(hand).translate(0, sheathOut(QUICK_DRAW_OUT, t), 0);
                        state.leftAt = new Matrix4f(rest).invert().mul(sheath);
                    } else if (state.leftAt != null) {
                        sheath = new Matrix4f(rest).mul(blend(new Matrix4f(), state.leftAt, 1 - (t - DRAWN_OUT) / (DRAW_BACK - DRAWN_OUT)));
                    }
                } else {
                    state.leftAt = null;
                }
                draw(poseStack, sheath, buffer, light, overlay, false, true);
                return;
            }
            float t = state.putStart < 0 ? Float.MAX_VALUE : ageInTicks - state.putStart;
            if (t >= 0 && t < FleshSheathe.END && state.flesh != null && !fleshBroken) {
                PoseStack local = new PoseStack();
                getParentModel().rightArm.translateAndRotate(local);
                Vector3f palm = local.last().pose().transformPosition(new Vector3f(-1 / 16f, 9.5f / 16, 0));
                try {
                    FleshSheathe.render(state.flesh, poseStack.last(), handMatrix(new PoseStack(), player), sheathMatrix(new PoseStack(), 0), palm, t,
                            ageInTicks, buffer, light, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
                    return;
                } catch (RuntimeException e) {
                    // Only the flesh goes: the sword just rests in the sheath from then on.
                    fleshBroken = true;
                    com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Kokushibo's flesh sword failed; it is put away plainly until the game restarts", e);
                }
            }
            state.flesh = null;
            draw(poseStack, sheath, buffer, light, overlay, true, true);
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
            KokushiboSwordRenderer.turnWrist(poseStack, wrist, swordStack);
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(swordStack, player.level(), player, player.getId());
        model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poseStack, false);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        KokushiboSwordRenderer.toSword(poseStack, true);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return m;
    }

    private static float sheathOut(float[] keys, float t) {
        if (t <= keys[0]) {
            return keys[1];
        }
        for (int i = 2; i < keys.length; i += 2) {
            if (t <= keys[i]) {
                float u = (t - keys[i - 2]) / (keys[i] - keys[i - 2]);
                return keys[i - 1] + (keys[i + 1] - keys[i - 1]) * u;
            }
        }
        return keys[keys.length - 1];
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
