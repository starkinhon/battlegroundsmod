package com.starkinhon.battlegroundsmod.client.pedestre;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.npc.Pedestre;
import com.starkinhon.battlegroundsmod.npc.PedestreLook;
import com.starkinhon.battlegroundsmod.npc.PedestreLook.Rand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Shibuya's pedestrians: the player's model (64x64, wide arms) with the skin painted from the look, the walk of the
// approved preview (legs and arms swinging with the pace, a bag held still, the phone raised to the face, the elders
// bent at the hip, arms up running from a fight) and the pieces the skin can't do drawn as boxes on the right bone:
// hair, hats, headphones, skirt, backpack, camera, briefcase, handbag, school bag, cane, phone (Pieces).
// Every piece sits a hair off the body's faces (never on the same plane, Stark 2026-11-14: no flickering faces).
public class PedestreRenderer extends HumanoidMobRenderer<Pedestre, PlayerModel<Pedestre>> {
    public PedestreRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.45f);
        addLayer(new Pieces(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Pedestre entity) {
        return PedestreSkins.get(entity.look());
    }

    // ------------------------------------------------------------ the walk and the poses
    public static final class Model extends PlayerModel<Pedestre> {
        public Model(ModelPart root) {
            super(root, false);
            hat.visible = false;
            jacket.visible = false;
            leftSleeve.visible = false;
            rightSleeve.visible = false;
            leftPants.visible = false;
            rightPants.visible = false;
        }

        @Override
        public void setupAnim(Pedestre p, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(p, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            hat.visible = jacket.visible = leftSleeve.visible = rightSleeve.visible = leftPants.visible = rightPants.visible = false;
            PedestreLook S = PedestreLook.of(p.look());
            boolean flee = p.fleeing();
            if (p.deathTime > 0) {
                flee = false;
            }
            // the pace: the preview's stride (phase 4.8 rad per block, amplitude growing with the speed); limbSwing
            // runs about 0.2 per block walked, limbSwingAmount is about bps / 5
            float speed = limbSwingAmount;
            float phase = limbSwing * 1.2f;
            float amp = (flee ? 1f : Math.min(1f, 0.55f + 0.6f * speed)) * Math.min(1f, speed / 0.12f) * (S.elder && !flee ? 0.55f : 1f);
            float sw = Mth.sin(phase) * amp;
            rightLeg.xRot = sw;
            leftLeg.xRot = -sw;
            rightLeg.yRot = leftLeg.yRot = rightLeg.zRot = leftLeg.zRot = 0;
            boolean carry = S.held != null && !S.phone();
            float ar = -sw * (carry ? 0.25f : 0.8f), al = sw * 0.8f;
            float lean = S.elder ? (float) S.hunch : 0f;
            float headX = S.elder ? -0.12f : 0f;
            float headY = netHeadYaw * Mth.DEG_TO_RAD;
            int pose = p.pose();
            float arz = 0.04f, alz = -0.04f;
            if (flee) {
                // running scared: arms up, looking back now and then
                ar = -2.7f + Mth.sin(phase * 1.3f) * 0.4f;
                al = -2.7f + Mth.cos(phase * 1.3f) * 0.4f;
                arz = 0.3f;
                alz = -0.3f;
                lean = 0.25f;
                headX = 0;
                headY = Mth.sin(phase * 0.4f) > 0.7f ? 1.4f : 0f;
            } else if (pose == Pedestre.POSE_PHONE) {
                ar = -1.05f;
                headX = 0.42f;
                headY = 0;
            } else if (pose == Pedestre.POSE_LOOK) {
                headY = Mth.sin(ageInTicks / 18f + p.getId() * 1.7f) * 0.9f;
            }
            // bent at the hip: the trunk turns about the hip (not the neck), the head and arms go with it
            float c = Mth.cos(lean), s = Mth.sin(lean);
            body.setPos(0, 12 - 12 * c, -12 * s);
            body.xRot = lean;
            body.yRot = body.zRot = 0;
            head.setPos(0, 12 - 12 * c, -12 * s);
            head.xRot = lean + headX;
            head.yRot = headY;
            head.zRot = 0;
            rightArm.setPos(-5, 12 - 10 * c, -10 * s);
            leftArm.setPos(5, 12 - 10 * c, -10 * s);
            rightArm.xRot = lean + ar;
            leftArm.xRot = lean + al;
            rightArm.yRot = leftArm.yRot = 0;
            rightArm.zRot = arz;
            leftArm.zRot = alz;
            if (!flee && p.walkSpeed() < 0.02f && pose != Pedestre.POSE_PHONE) {
                // standing still: a slow breath in the arms
                rightArm.zRot += Mth.cos(ageInTicks * 0.09f) * 0.03f;
                leftArm.zRot -= Mth.cos(ageInTicks * 0.09f) * 0.03f;
            }
            hat.copyFrom(head);
        }
    }

    // ------------------------------------------------------------ the pieces
    // Small textures for the pieces, tinted by the vertex color: grain, hair strands, skirt pleats.
    private static ResourceLocation GRAIN, STRANDS, PLEAT;

    private static ResourceLocation tex(String name, int kind) {
        NativeImage img = new NativeImage(16, 16, true);
        Rand r = new Rand(kind * 7919 + 13);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double k = 0.92 + r.next() * 0.12;
                if (kind == 2) {
                    k *= x % 2 != 0 ? 0.8 : 1.02;
                }
                if (kind == 1) {
                    k *= 0.86 + 0.18 * Math.abs(Math.sin(x * 1.7 + (r.next() - 0.5)));
                }
                if (kind == 2 && y >= 15) {
                    k *= 0.8;
                }
                int v = (int) Math.min(255, 255 * k * 0.98);
                img.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        return Minecraft.getInstance().getTextureManager().register("battlegroundsmod_pedestre_" + name, new DynamicTexture(img));
    }

    static final class Pieces extends RenderLayer<Pedestre, PlayerModel<Pedestre>> {
        Pieces(RenderLayerParent<Pedestre, PlayerModel<Pedestre>> parent) {
            super(parent);
        }

        private PoseStack pose;
        private MultiBufferSource buffers;
        private int light, overlay;

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Pedestre p, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (p.isInvisible()) {
                return;
            }
            if (GRAIN == null) {
                GRAIN = tex("grain", 0);
                STRANDS = tex("strands", 1);
                PLEAT = tex("pleat", 2);
            }
            this.pose = pose;
            this.buffers = buffers;
            this.light = light;
            this.overlay = LivingEntityRenderer.getOverlayCoords(p, 0);
            PedestreLook S = PedestreLook.of(p.look());
            PlayerModel<Pedestre> m = getParentModel();
            Rand r = new Rand(S.look ^ 0x7A11);
            boolean hatOn = S.hat != null;

            // the head: hair, hat, headphones
            pose.pushPose();
            m.head.translateAndRotate(pose);
            double[] hair = S.hairC;
            String hs = S.hairStyle;
            if (hs.equals("long") || hs.equals("wolf")) {
                headBox(STRANDS, hair, 0, hs.equals("wolf") ? 3.6 : 2.6, -4.25, 8.4, hs.equals("wolf") ? 9 : 11, 1.3, 0, 0);
            }
            if (hs.equals("perm")) {
                headBox(STRANDS, hair, 0, 7, 0, 9, 4, 9, 0, 0);
            }
            if (hs.equals("pony")) {
                headBox(STRANDS, hair, 0, 3.6, -5, 2.4, 6, 2.2, 0.25, 0);
                headBox(GRAIN, PedestreLook.mul(hair, 0.6), 0, 6.6, -4.5, 1.6, 1.6, 1.4, 0, 0);
            }
            if (hs.equals("bun") && !hatOn) {
                headBox(STRANDS, hair, 0, 8.6, -2.6, 3.4, 3.2, 3.2, 0, 0);
            }
            if (hs.equals("twin")) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    headBox(STRANDS, hair, sx * 4.6, 2.4, -2.4, 2.2, 7, 2.2, 0, sx * 0.12);
                }
            }
            if (hs.equals("messy") || hs.equals("wolf")) {
                for (int i = 0; i < 4; i++) {
                    double w = r.range(1.2, 2.2), h = r.range(0.8, 1.6), d = r.range(1.2, 2.2), x = r.range(-3, 3), z = r.range(-3, 3);
                    if (!hatOn) {
                        headBox(STRANDS, hair, x, 8.3, z, w, h, d, 0, 0);
                    }
                }
            }
            PedestreLook.Piece H = S.hat;
            if (H != null) {
                switch (H.kind) {
                    case "cap" -> {
                        pose.pushPose();
                        pose.translate(0, -7.6 / 16, 0);
                        if (H.flag) {
                            pose.mulPose(Axis.YP.rotation((float) Math.PI));
                        }
                        // in the cap's own frame (head space, y down, front -z)
                        box(GRAIN, H.c, 0, -0.6, 0, 8.7, 2.4, 8.7);
                        box(GRAIN, PedestreLook.mul(H.c, 0.85), 0, 0.3, -5.9, 7.4, 0.6, 3.6);
                        pose.popPose();
                    }
                    case "bucket" -> {
                        headBox(GRAIN, H.c, 0, 8.1, 0, 8.8, 2.6, 8.8, 0, 0);
                        headBox(GRAIN, PedestreLook.mul(H.c, 0.9), 0, 6.9, 0, 11.2, 0.5, 11.2, 0, 0);
                    }
                    case "helmet" -> {
                        headBox(GRAIN, H.c, 0, 8, 0, 9.2, 3.2, 9.2, 0, 0);
                        headBox(GRAIN, PedestreLook.mul(H.c, 0.92), 0, 6.6, 0.4, 10.2, 0.6, 10.6, 0, 0);
                        headBox(GRAIN, new double[] {60, 160, 60}, 0, 8.2, 4.75, 9.4, 0.8, 0.4, 0, 0); // the site's green band
                    }
                    default -> {
                    }
                }
            }
            if (S.headphones != null) {
                headBox(GRAIN, S.headphones, 0, 8.6, 0, 9.4, 0.8, 1.4, 0, 0);
                for (int sx = -1; sx <= 1; sx += 2) {
                    headBox(GRAIN, S.headphones, sx * 4.65, 4.2, 0, 1.2, 3, 3, 0, 0);
                }
            }
            pose.popPose();

            // the skirt: a box at the hip, not swinging with the legs
            if (S.skirt()) {
                double L = switch (S.bottom.kind) {
                    case "pencil" -> 6.4;
                    case "pleated" -> 5.4;
                    case "mini" -> 3.6;
                    default -> 9.4;
                };
                double wide = S.bottom.kind.equals("pleated") ? 9.2 : S.bottom.kind.equals("longskirt") ? 9 : 8.6;
                // rig space: y = 24 - preview y
                box(S.bottom.kind.equals("pleated") ? PLEAT : GRAIN, S.bottom.c, 0, 24 - (12.2 - L / 2), 0, wide, L,
                        S.bottom.kind.equals("pleated") ? 5 : 4.7);
            }

            // the back and the chest
            pose.pushPose();
            m.body.translateAndRotate(pose);
            if (S.back != null) {
                torsoBox(GRAIN, S.back.c, 0, 6.4, -3.55, 7, 8, 3);
                torsoBox(GRAIN, PedestreLook.mul(S.back.c, 0.85), 0, 4.4, -5.45, 5, 3, 1);
            }
            if (S.camera) {
                torsoBox(GRAIN, new double[] {28, 28, 30}, 1.2, 5.4, 3.05, 3.2, 2.2, 2);
                torsoBox(GRAIN, new double[] {60, 60, 66}, 1.2, 5.4, 4.45, 1.4, 1.4, 1.2);
            }
            pose.popPose();

            // in the right hand
            PedestreLook.Piece held = S.held;
            if (held != null && !(p.fleeing() && held.kind.equals("phone"))) {
                pose.pushPose();
                m.rightArm.translateAndRotate(pose);
                switch (held.kind) {
                    case "briefcase" -> {
                        armBox(held.c, 0, -12.6, 0.4, 1.6, 5, 7);
                        armBox(PedestreLook.mul(held.c, 0.7), 0, -9.8, 0.4, 0.6, 1, 2.4);
                    }
                    case "handbag" -> {
                        armBox(held.c, 0, -11.6, 0.2, 1.8, 3.6, 4.6);
                        armBox(PedestreLook.mul(held.c, 0.75), 0, -9.4, 0.2, 0.5, 1.4, 2.6);
                    }
                    case "schoolbag" -> {
                        armBox(held.c, 0, -12.8, 0.3, 1.8, 5.4, 7.6);
                        armBox(PedestreLook.mul(held.c, 0.7), 0, -9.6, 0.3, 0.5, 1.2, 2.8);
                    }
                    case "cane" -> {
                        armBox(held.c, 0, -16, 1.2, 0.8, 12.6, 0.8);
                        armBox(held.c, 0, -9.8, 0.4, 0.8, 0.8, 2.4);
                    }
                    case "phone" -> phone(p, p.pose() == Pedestre.POSE_PHONE);
                    default -> {
                    }
                }
                pose.popPose();
            }
        }

        // the phone: along the forearm when down; raised, the screen turns to the face (lit at night)
        private void phone(Pedestre p, boolean up) {
            pose.pushPose();
            // arm space: x - 1, y and z flipped
            pose.translate(-1 / 16f, 10.2 / 16f, -(up ? 1.6f : 0.6f) / 16f);
            pose.mulPose(Axis.XP.rotation(up ? -0.5f : (float) Math.PI / 2));
            box(GRAIN, new double[] {22, 22, 26}, 0, 0, 0, 1.8, 0.4, 3.2);
            boolean night = p.level().isNight() || LightTexture.sky(light) < 8 && LightTexture.block(light) < 8;
            int color = night ? 0xBFE2FF : 0x8AA0B4;
            // the screen: a quad facing preview +y (model -y), 0.02 px off the phone's face
            float hw = 1.5f / 32, hd = 2.8f / 32, y = -0.22f / 16;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GRAIN));
            PoseStack.Pose e = pose.last();
            int l = night ? LightTexture.FULL_BRIGHT : light;
            int cr = color >> 16 & 255, cg = color >> 8 & 255, cb = color & 255;
            vc.addVertex(e, -hw, y, -hd).setColor(cr, cg, cb, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(e, 0, -1, 0);
            vc.addVertex(e, hw, y, -hd).setColor(cr, cg, cb, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(e, 0, -1, 0);
            vc.addVertex(e, hw, y, hd).setColor(cr, cg, cb, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(e, 0, -1, 0);
            vc.addVertex(e, -hw, y, hd).setColor(cr, cg, cb, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(e, 0, -1, 0);
            pose.popPose();
        }

        // A box given in the preview's head space (y up from the neck, front +z), rotated about x then z.
        private void headBox(ResourceLocation t, double[] c, double x, double y, double z, double w, double h, double d, double rx, double rz) {
            pose.pushPose();
            pose.translate(x / 16, -y / 16, -z / 16);
            if (rz != 0) {
                pose.mulPose(Axis.ZP.rotation((float) -rz));
            }
            if (rx != 0) {
                pose.mulPose(Axis.XP.rotation((float) rx));
            }
            box(t, c, 0, 0, 0, w, h, d);
            pose.popPose();
        }

        // The preview's trunk space: y up from the hip, 12 at the neck.
        private void torsoBox(ResourceLocation t, double[] c, double x, double y, double z, double w, double h, double d) {
            box(t, c, x, 12 - y, -z, w, h, d);
        }

        // The preview's right arm space: centered on the arm, y up from the shoulder pivot.
        private void armBox(double[] c, double x, double y, double z, double w, double h, double d) {
            box(GRAIN, c, x - 1, -y, -z, w, h, d);
        }

        // A box in model space (pixels, y down), centered at (x, y, z), the texture whole on each face.
        private void box(ResourceLocation t, double[] c, double x, double y, double z, double w, double h, double d) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(t));
            PoseStack.Pose e = pose.last();
            float x0 = (float) ((x - w / 2) / 16), x1 = (float) ((x + w / 2) / 16);
            float y0 = (float) ((y - h / 2) / 16), y1 = (float) ((y + h / 2) / 16);
            float z0 = (float) ((z - d / 2) / 16), z1 = (float) ((z + d / 2) / 16);
            int cr = (int) Mth.clamp(c[0], 0, 255), cg = (int) Mth.clamp(c[1], 0, 255), cb = (int) Mth.clamp(c[2], 0, 255);
            quad(vc, e, cr, cg, cb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
            quad(vc, e, cr, cg, cb, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
            quad(vc, e, cr, cg, cb, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
            quad(vc, e, cr, cg, cb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
            quad(vc, e, cr, cg, cb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
            quad(vc, e, cr, cg, cb, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        }

        private void quad(VertexConsumer vc, PoseStack.Pose e, int r, int g, int b, float ax, float ay, float az, float bx, float by, float bz,
                float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
            vc.addVertex(e, ax, ay, az).setColor(r, g, b, 255).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(e, bx, by, bz).setColor(r, g, b, 255).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(e, cx, cy, cz).setColor(r, g, b, 255).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(e, dx, dy, dz).setColor(r, g, b, 255).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
        }
    }
}
