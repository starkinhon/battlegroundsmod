package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

// The universes' NPCs: the player model with a skin (textures/entity/<id>.png).
public class NpcRenderer extends HumanoidMobRenderer<UniverseNpc, PlayerModel<UniverseNpc>> {
    private final ResourceLocation skin;
    private final float size;

    public NpcRenderer(EntityRendererProvider.Context context, String skin) {
        this(context, skin, false);
    }

    public NpcRenderer(EntityRendererProvider.Context context, String skin, boolean slim) {
        this(context, skin, slim, 1f);
    }

    // size: drawn smaller or bigger (Bubbles, Senhor Kaioh's monkey, is 0.68)
    public NpcRenderer(EntityRendererProvider.Context context, String skin, boolean slim, float size) {
        super(context, new PlayerModel<UniverseNpc>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim) {
            // in the talk scene (client/TalkScreen) the NPC gestures while its line types
            @Override
            public void setupAnim(UniverseNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                float[] g = TalkScreen.gesture(npc.getId(), ageInTicks);
                if (g != null) {
                    rightArm.xRot = g[0];
                    rightArm.zRot = g[1];
                    leftArm.xRot = g[2];
                    leftArm.zRot = g[3];
                    rightSleeve.copyFrom(rightArm);
                    leftSleeve.copyFrom(leftArm);
                }
            }
        }, 0.5f * size);
        this.size = size;
        this.skin = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + skin + ".png");
    }

    @Override
    protected void scale(UniverseNpc npc, com.mojang.blaze3d.vertex.PoseStack pose, float partial) {
        if (size != 1f) {
            pose.scale(size, size, size);
        }
    }

    // The Caminho (client/PathClient): a small gold diamond over the head of who the corner sends the player to, bobbing,
    // seen from 48 blocks; up close, how to talk to them.
    @Override
    public void render(UniverseNpc npc, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        super.render(npc, yaw, partial, pose, buffers, light);
        if (PathClient.npc.isEmpty() || !PathClient.npc.equals(npc.npcId())) {
            return;
        }
        double d = entityRenderDispatcher.distanceToSqr(npc);
        if (d > 48 * 48) {
            return;
        }
        float bob = (float) Math.sin((npc.tickCount + partial) * 0.12f) * 0.06f;
        // bigger far away so it reads; its bottom stays over the name
        float sc = 0.025f * (float) Math.max(1, Math.sqrt(d) / 12);
        pose.pushPose();
        pose.translate(0, npc.getBbHeight() + 0.6f + 9 * sc + bob, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(sc, -sc, sc);
        org.joml.Matrix4f m = pose.last().pose();
        net.minecraft.client.gui.Font font = getFont();
        String mark = "\u25C6";
        font.drawInBatch(mark, -font.width(mark) / 2f + 0.6f, 0.6f, 0x80000000 | 0x3A2A08, false, m, buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        font.drawInBatch(mark, -font.width(mark) / 2f, 0, 0xFFF2C94C, false, m, buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
        if (d < 6 * 6) {
            pose.pushPose();
            pose.translate(0, npc.getBbHeight() + 1.15f, 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            pose.scale(0.016f, -0.016f, 0.016f);
            String hint = net.minecraft.network.chat.Component.translatable("hud.battlegroundsmod.caminho.conversar").getString();
            font.drawInBatch(hint, -font.width(hint) / 2f, 0, 0xFFE6E0EE, false, pose.last().pose(), buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x60000000, light);
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(UniverseNpc entity) {
        return skin;
    }
}
