// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.ui;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.util.Mth;
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
        // when set, the whole body in absolute numbers (the book's idles, IdlePoses): body, head, right arm, left arm,
        // right leg, left leg as [x, y, z pivot in skin pixels, xRot, yRot, zRot in degrees]; the arrays above are ignored
        public float[] abs;

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
            p.abs = abs == null ? null : abs.clone();
            return p;
        }
    }

    private static PlayerModel<LivingEntity> model, slimModel;
    private static final Map<String, ResourceLocation> SKINS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> SLIM = new HashMap<>();

    private SkinDraw() {
    }

    // The texture of a character or NPC by its skin name (textures/entity/<name>.png or the Naruto folder), or null.
    // Mobs drawn with a blocky model (textures/entity/naruto/*, namekiano_*) have no player-skin layout, so a head drawn
    // from them came out garbled (Stark 2026-11-22: the faces on the mission panels). Their faces use the nearest player
    // skin of the same place instead.
    private static String playerSkin(String n) {
        if (n.startsWith("namekiano")) {
            return "piccolo";
        }
        if (n.startsWith("ninja_som")) {
            return "ninja_som";
        }
        if (n.startsWith("marionet")) {
            return "gaara";
        }
        if (n.startsWith("zetsu")) {
            return "madara";
        }
        if (n.startsWith("sapo")) {
            return "npc_jiraiya";
        }
        if (n.startsWith("espadachim_nevoa") || n.startsWith("ninja_chuva")) {
            return "ninja_renegado_1";
        }
        return n;
    }

    public static ResourceLocation skin(String name) {
        if (name == null) {
            return null;
        }
        return SKINS.computeIfAbsent(name, n0 -> {
            String n = playerSkin(n0);
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

    private static PlayerModel<LivingEntity> model(boolean slim) {
        if (model == null) {
            model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            // a new model starts as a baby (EntityModel.young = true: big head, half body); the game sets it per entity
            model.young = false;
            slimModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            slimModel.young = false;
        }
        return slim ? slimModel : model;
    }

    // A slim skin (3 px arms, the Alex layout: Kuririn, Bills, Gojo...) drawn on the wide model leaves a strip of the arms
    // with no texture (Stark 2026-11-21). Told apart by the texture itself: the wide arm's last column (54, 20) and the
    // corner of its top (50, 16) are see-through on a slim skin. A texture that is not a resource (the player's own
    // downloaded skin) stays wide, as before.
    private static boolean slim(ResourceLocation skin) {
        return SLIM.computeIfAbsent(skin, rl -> {
            var res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isEmpty()) {
                return false;
            }
            try (var in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                if (img.getWidth() != 64 || img.getHeight() != 64) {
                    return false;
                }
                return (img.getPixelRGBA(54, 20) >>> 24) == 0 && (img.getPixelRGBA(50, 16) >>> 24) == 0;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static void set(ModelPart part, float[] a, int i) {
        part.x = a[i];
        part.y = a[i + 1];
        part.z = a[i + 2];
        part.xRot = a[i + 3] * Mth.DEG_TO_RAD;
        part.yRot = a[i + 4] * Mth.DEG_TO_RAD;
        part.zRot = a[i + 5] * Mth.DEG_TO_RAD;
    }

    // Draws the body standing on (cx, footY) on the grid. tint = ARGB multiplied in (0xFFFFFFFF = as is; a dark one makes
    // the silhouette of a locked challenger).
    public static void draw(ResourceLocation skin, float cx, float footY, float k, Pose pose, int parts, int tint) {
        draw(skin, null, cx, footY, k, pose, parts, tint);
    }

    // The same, worn by a player: with Customizable Player Models, their CPM model and texture (Stark 2026-11-27: the
    // challenge fight's loading screen showed the vanilla skin). Without CPM, or no model for them, the skin as above.
    public static void draw(ResourceLocation skin, net.minecraft.world.entity.player.Player wearer, float cx, float footY, float k, Pose pose, int parts,
            int tint) {
        if (skin == null || Px.g == null) {
            return;
        }
        PlayerModel<LivingEntity> m = model(slim(skin));
        // reset before CPM binds: its swapped-in parts take this pose (their own reset pose is not the vanilla one)
        for (ModelPart part : new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve,
                m.rightLeg, m.rightPants, m.leftLeg, m.leftPants}) {
            part.resetPose();
        }
        // CPM swaps the model's parts while bound: everything below reads them from m after this
        com.starkinhon.battlegroundsmod.client.compat.CpmCompat.Bound cpm = wearer == null ? null
                : com.starkinhon.battlegroundsmod.client.compat.CpmCompat.bind(m, wearer, skin, Px.g.bufferSource(), RenderType::entityTranslucent);
        try {
            pose(m, skin, cx, footY, k, pose, parts, tint, cpm);
        } finally {
            if (cpm != null) {
                cpm.end();
            }
        }
        Px.g.flush();
        Lighting.setupFor3DItems();
        // what is drawn after (names over the bust, the ladder's strip) must not hide behind the model's depth
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void pose(PlayerModel<LivingEntity> m, ResourceLocation skin, float cx, float footY, float k, Pose pose, int parts, int tint,
            com.starkinhon.battlegroundsmod.client.compat.CpmCompat.Bound cpm) {
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
        if (pose.abs != null) {
            set(m.body, pose.abs, 0);
            set(m.head, pose.abs, 6);
            set(m.rightArm, pose.abs, 12);
            set(m.leftArm, pose.abs, 18);
            set(m.rightLeg, pose.abs, 24);
            set(m.leftLeg, pose.abs, 30);
        }
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
        var buffer = g.bufferSource().getBuffer(cpm != null ? cpm.renderType() : RenderType.entityTranslucent(skin));
        m.renderToBuffer(ps, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        if (cpm != null) {
            // CPM batches its boxes: out into the buffers while the pose is still this one
            cpm.end();
        }
        g.flush();
        ps.popPose();
    }

    public static void draw(String skinName, float cx, float footY, float k, Pose pose, int parts, int tint) {
        draw(skin(skinName), cx, footY, k, pose, parts, tint);
    }

    // A head turned three quarters, centered on (cx, cy) (the preview's drawHead).
    public static void head(String skinName, float cx, float cy, float k, int tint) {
        draw(skin(skinName), cx, cy + 28 * k, k, new Pose(-0.55f, 0.3f), HEAD, tint);
    }
}
