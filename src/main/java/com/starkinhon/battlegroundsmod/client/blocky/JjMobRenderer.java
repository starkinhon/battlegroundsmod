package com.starkinhon.battlegroundsmod.client.blocky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjMob;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjTravel;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;

// The Jujutsu curses (animal/jjmob): the preview's models and baked animation on the Naruto creatures' renderer, and
// what only these show over their heads: the Jogador do Abate's plate of points and the Kogane's paper bubble (its text a
// lang key, "key|arg|arg", so it is in the game's language).
public class JjMobRenderer extends NarutoMobRenderer {
    private static final double TEXT_RANGE = 24;

    public JjMobRenderer(EntityRendererProvider.Context context, String id) {
        super(context, id, JjMobSpec.get(id), "jj", JjTravel::of);
    }

    @Override
    public void render(NarutoMob e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(e, yaw, partial, pose, buffers, light);
        if (!(e instanceof JjMob j) || j.dying >= 0 || j.isInvisible()
                || entityRenderDispatcher.distanceToSqr(e) > TEXT_RANGE * TEXT_RANGE) {
            return;
        }
        if (j.kind().equals("abate") && j.points() > 0) {
            label(e, pose, buffers, Component.translatable("jj.abate.placa", j.points()), 0.45f, 0xFFF4F0FF, 0xC0141020);
        } else if (j.isKogane() && !j.say().isEmpty()) {
            String[] parts = j.say().split("\\|");
            Object[] args = new Object[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, args.length);
            label(e, pose, buffers, Component.translatable(parts[0], args), 0.35f, 0xFF1A1410, 0xF0F2EBD8);
        }
    }

    // a line of text facing the camera, over the head
    private void label(NarutoMob e, PoseStack pose, MultiBufferSource buffers, Component text, float over, int color, int back) {
        Font font = getFont();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() + over, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, back, 0xF000F0);
        pose.popPose();
    }
}
