// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Freeza's Telecinese viva and Death Psycho Bomb (the previews Stark approved 2026-11-27, forja/freeza-kit/telec-viva.js
// and freeza-novas.js): no wire and no dome round the one held, only a purple outline that trembles over the body. The
// body is drawn once more a little bigger in a swirling energy shell (the charged creeper's), tinted purple; strength
// above 1 (the Psycho Bomb swelling) brightens it and turns it to pink-white. DbFx.telekinesis touches it every frame
// it draws the hold; it fades out on its own a few frames after the last touch.
public final class TeleRim<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation SWIRL = ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    // entity id -> {strength, game time (ticks + partial) of the last touch}
    private static final Map<Integer, double[]> HELD = new HashMap<>();
    private static final float FADE = 4;
    // where each held body was, frame by frame (game time, x, y, z), for the afterimages when it goes fast
    private static final Map<Integer, ArrayDeque<double[]>> TRAIL = new HashMap<>();
    // the preview's ghosts: 4 copies a little behind (1 to 4 ticks), shown only while it went faster than 9 blocks/s
    private static final int GHOSTS = 4;
    private static final double GHOST_SPEED = 0.45;

    public TeleRim(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void touch(int entityId, float strength, double time) {
        HELD.put(entityId, new double[] {strength, time});
    }

    public static void clear() {
        HELD.clear();
        TRAIL.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        double[] h = HELD.get(entity.getId());
        if (h == null || entity.isInvisible()) {
            return;
        }
        double now = entity.level().getGameTime() + partialTick;
        float fade = 1 - (float) Math.max(0, now - h[1]) / FADE;
        if (fade <= 0 || now < h[1] - 40) {
            HELD.remove(entity.getId());
            TRAIL.remove(entity.getId());
            return;
        }
        float s = (float) h[0] * fade;
        // it trembles: the brightness flickers a little every frame
        float flick = 0.75f + 0.25f * Mth.sin(ageInTicks * 2.7f + entity.getId()) * Mth.sin(ageInTicks * 1.9f);
        float pink = Mth.clamp(s - 1, 0, 1);
        float k = Math.min(1, s) * flick;
        int r = (int) (255 * Mth.clamp((0.77f + 0.23f * pink) * k, 0, 1));
        int g = (int) (255 * Mth.clamp((0.36f + 0.46f * pink) * k, 0, 1));
        int b = (int) (255 * Mth.clamp((1f - 0.05f * pink) * k, 0, 1));
        float scroll = ageInTicks * 0.02f;
        VertexConsumer out = buffer.getBuffer(RenderType.energySwirl(SWIRL, scroll % 1, scroll * 0.7f % 1));
        poseStack.pushPose();
        // a bit bigger than the body, round its middle (the model's y grows downward, the waist at 12 px)
        float grow = 1.08f + 0.04f * pink;
        poseStack.translate(0, 0.75, 0);
        poseStack.scale(grow, grow, grow);
        poseStack.translate(0, -0.75, 0);
        getParentModel().renderToBuffer(poseStack, out, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFF000000 | r << 16 | g << 8 | b);
        poseStack.popPose();
        ghosts(poseStack, out, entity, partialTick, now, Math.min(1, s));
    }

    // The afterimages (telec-viva.js): faint purple copies of the body left a little behind on its way while it is dragged
    // fast. The pose stack is the body's own frame, so the world offset to each old spot is turned into that frame.
    private void ghosts(PoseStack poseStack, VertexConsumer out, T entity, float partialTick, double now, float k) {
        ArrayDeque<double[]> trail = TRAIL.computeIfAbsent(entity.getId(), id -> new ArrayDeque<>());
        Vec3 at = entity.getPosition(partialTick);
        if (trail.isEmpty() || trail.peekLast()[0] < now) {
            trail.addLast(new double[] {now, at.x, at.y, at.z});
        }
        Iterator<double[]> it = trail.iterator();
        while (it.hasNext() && it.next()[0] < now - 8) {
            it.remove();
        }
        if (k <= 0.05f) {
            return;
        }
        Matrix3f inv = new Matrix3f(poseStack.last().pose()).invert();
        for (int i = 1; i <= GHOSTS; i++) {
            double[] p = sample(trail, now - i);
            double[] q = sample(trail, now - i - 1);
            if (p == null || q == null) {
                continue;
            }
            double speed = Math.sqrt((p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]) + (p[3] - q[3]) * (p[3] - q[3]));
            if (speed < GHOST_SPEED) {
                continue;
            }
            Vector3f d = inv.transform(new Vector3f((float) (p[1] - at.x), (float) (p[2] - at.y), (float) (p[3] - at.z)));
            float a = k * 0.32f * (1 - (i - 1) / (float) GHOSTS) * Math.min(1, (float) ((speed - GHOST_SPEED) / 0.3));
            int r = (int) (255 * 0.77f * a), g = (int) (255 * 0.36f * a), b = (int) (255 * a);
            poseStack.pushPose();
            poseStack.translate(d.x, d.y, d.z);
            getParentModel().renderToBuffer(poseStack, out, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFF000000 | r << 16 | g << 8 | b);
            poseStack.popPose();
        }
    }

    // The spot the body had at game time t (the nearest frame at or before it), or null when the trail doesn't go back so far.
    private static double[] sample(ArrayDeque<double[]> trail, double t) {
        double[] best = null;
        for (double[] e : trail) {
            if (e[0] <= t) {
                best = e;
            } else {
                break;
            }
        }
        return best;
    }
}
