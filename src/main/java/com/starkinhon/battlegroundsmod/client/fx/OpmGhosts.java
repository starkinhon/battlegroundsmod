package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// The page's afterimages (snapshot): Saitama's body held still at one instant, see-through and fading. Each is taken from
// the frame the game really drew of him (every part's pose as the animation had it), so it is his own skin and pose.
public final class OpmGhosts {
    private record Ghost(PlayerModel<AbstractClientPlayer> model, ResourceLocation skin, PartPose[] parts, Vec3 pos, float yaw, double born, float peak,
            float life) {
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    // Who wants a snapshot on their next drawn frame: id -> {peak, life}.
    private static final Map<Integer, float[]> WANT = new HashMap<>();

    private OpmGhosts() {
    }

    static boolean active() {
        return !GHOSTS.isEmpty() || !WANT.isEmpty();
    }

    static void clear() {
        GHOSTS.clear();
        WANT.clear();
    }

    // A snapshot of entity id the next time it is drawn: peak opacity, gone after life seconds.
    static void want(int id, float peak, float life) {
        WANT.put(id, new float[] {peak, life});
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants,
                m.leftPants};
    }

    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (WANT.isEmpty()) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        float[] w = WANT.remove(player.getId());
        if (w == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ModelPart[] ps = parts(model);
        PartPose[] poses = new PartPose[ps.length];
        for (int i = 0; i < ps.length; i++) {
            poses[i] = ps[i].storePose();
        }
        float partial = event.getPartialTick();
        GHOSTS.add(new Ghost(model, event.getRenderer().getTextureLocation(player), poses, player.getPosition(partial),
                Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot), ScreenFx.now() + partial, w[0], w[1]));
        if (GHOSTS.size() > 80) {
            GHOSTS.remove(0);
        }
    }

    static void render(Vec3 cam, double now) {
        if (GHOSTS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        // (the view is already in the shader's ModelViewMat: positions only relative to the camera)
        PoseStack pose = new PoseStack();
        GHOSTS.removeIf(g -> (now - g.born()) / 20 > g.life());
        for (Ghost g : GHOSTS) {
            double k = (now - g.born()) / 20 / g.life();
            float a = (float) (g.peak() * Math.pow(Math.max(0, 1 - k), 1.5));
            if (a < 0.01f) {
                continue;
            }
            ModelPart[] ps = parts(g.model());
            for (int i = 0; i < ps.length; i++) {
                ps[i].loadPose(g.parts()[i]);
            }
            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180 - g.yaw()));
            pose.scale(-1, -1, 1);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0, -1.501, 0);
            int color = FastColor.ARGB32.color(Math.round(a * 255), 228, 240, 255);
            g.model().renderToBuffer(pose, source.getBuffer(RenderType.entityTranslucent(g.skin())), 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            pose.popPose();
        }
        source.endBatch();
    }
}
