package com.starkinhon.battlegroundsmod.client.ui;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

// The Minecraft body in 3D from a 64 x 64 skin, posed (the preview's drawSkin): the challenger on the book's stage, the
// busts on the ladder, the heads on the tabs and the NPC in the mission panel. The real player model of the game with the
// skin of that character, turned by yaw (around itself) and pitch (the camera a little above), arms, legs and head in
// radians with the preview's signs: arm/leg [x, z], x negative = forward, z negative on the right one = outwards;
// head positive = looks down; lift = floats up, in skin pixels. k = grid pixels per skin pixel.
public final class SkinDraw {
    public static final int HEAD = 1, BODY = 2, RARM = 4, LARM = 8, RLEG = 16, LLEG = 32, ALL = 63, BUST = HEAD | BODY | RARM | LARM;

    public static final class Pose {
        public float yaw = -0.5f, pitch = 0.18f, head, lift;
        public float[] rarm = {0, 0}, larm = {0, 0}, rleg = {0, 0}, lleg = {0, 0};

        public Pose() {
        }

        public Pose(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Pose copy() {
            Pose p = new Pose(yaw, pitch);
            p.head = head;
            p.lift = lift;
            p.rarm = rarm.clone();
            p.larm = larm.clone();
            p.rleg = rleg.clone();
            p.lleg = lleg.clone();
            return p;
        }
    }

    private static PlayerModel<LivingEntity> model;
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();

    private SkinDraw() {
    }

    // The texture of a character or NPC by its skin name (textures/entity/<name>.png or the Naruto folder), or null.
    public static ResourceLocation skin(String name) {
        if (name == null) {
            return null;
        }
        return SKINS.computeIfAbsent(name, n -> {
            var manager = Minecraft.getInstance().getResourceManager();
            String[] tries = {"textures/entity/" + n + ".png", "textures/entity/naruto/" + n + ".png",
                    "textures/entity/" + n.replaceAll("_\\d+$", "") + ".png", "textures/entity/naruto/" + n.replaceAll("_\\d+$", "") + ".png",
                    "textures/entity/" + n.replaceAll("_\\d+$", "") + "_0.png"};
            for (String t : tries) {
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, t);
                if (manager.getResource(rl).isPresent()) {
                    return rl;
                }
            }
            return null;
        });
    }

    private static PlayerModel<LivingEntity> model() {
        if (model == null) {
            model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            // a new model starts as a baby (EntityModel.young = true: big head, half body); the game sets it per entity
            model.young = false;
        }
        return model;
    }

    // Draws the body standing on (cx, footY) on the grid. tint = ARGB multiplied in (0xFFFFFFFF = as is; a dark one makes
    // the silhouette of a locked challenger).
    public static void draw(ResourceLocation skin, float cx, float footY, float k, Pose pose, int parts, int tint) {
        if (skin == null || Px.g == null) {
            return;
        }
        PlayerModel<LivingEntity> m = model();
        for (ModelPart part : new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve,
                m.rightLeg, m.rightPants, m.leftLeg, m.leftPants}) {
            part.resetPose();
        }
        m.head.visible = m.hat.visible = (parts & HEAD) != 0;
        m.body.visible = m.jacket.visible = (parts & BODY) != 0;
        m.rightArm.visible = m.rightSleeve.visible = (parts & RARM) != 0;
        m.leftArm.visible = m.leftSleeve.visible = (parts & LARM) != 0;
        m.rightLeg.visible = m.rightPants.visible = (parts & RLEG) != 0;
        m.leftLeg.visible = m.leftPants.visible = (parts & LLEG) != 0;
        m.head.xRot = pose.head;
        m.rightArm.xRot = pose.rarm[0];
        m.rightArm.zRot = -pose.rarm[1];
        m.leftArm.xRot = pose.larm[0];
        m.leftArm.zRot = -pose.larm[1];
        m.rightLeg.xRot = pose.rleg[0];
        m.rightLeg.zRot = -pose.rleg[1];
        m.leftLeg.xRot = pose.lleg[0];
        m.leftLeg.zRot = -pose.lleg[1];
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);

        var g = Px.g;
        g.flush();
        Lighting.setupForEntityInInventory();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, footY - pose.lift * k, 150);
        float s = 16 * k;
        ps.scale(s, s, -s);
        ps.mulPose(Axis.XP.rotation(pose.pitch));
        ps.mulPose(Axis.YP.rotation(-pose.yaw));
        ps.translate(0, -1.5f, 0);
        var buffer = g.bufferSource().getBuffer(RenderType.entityTranslucent(skin));
        m.renderToBuffer(ps, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        g.flush();
        ps.popPose();
        Lighting.setupFor3DItems();
        // what is drawn after (names over the bust, the ladder's strip) must not hide behind the model's depth
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public static void draw(String skinName, float cx, float footY, float k, Pose pose, int parts, int tint) {
        draw(skin(skinName), cx, footY, k, pose, parts, tint);
    }

    // A head turned three quarters, centered on (cx, cy) (the preview's drawHead).
    public static void head(String skinName, float cx, float cy, float k, int tint) {
        draw(skin(skinName), cx, cy + 28 * k, k, new Pose(-0.55f, 0.3f), HEAD, tint);
    }
}
