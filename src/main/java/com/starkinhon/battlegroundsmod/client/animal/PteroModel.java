package com.starkinhon.battlegroundsmod.client.animal;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.Pterodactyl;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// The pterodactyl: a slim body, a long neck and the head with its long beak and the crest pointing back, wide wings in
// two pieces (the arm and the long finger) that beat slow and deep to climb and spread flat to glide, a short tail and
// the legs tucked back. The texture is drawn by tools/mobs/ptero.py from these same boxes.
public class PteroModel extends HierarchicalModel<Pterodactyl> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "pterodactyl"), "main");

    private final ModelPart root, body, neck, head, rightWing, leftWing, rightTip, leftTip, tail;

    public PteroModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.rightWing = body.getChild("right_wing");
        this.leftWing = body.getChild("left_wing");
        this.rightTip = rightWing.getChild("tip");
        this.leftTip = leftWing.getChild("tip");
        this.tail = body.getChild("tail");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2f, -1.5f, -4f, 4, 3, 8), PartPose.offset(0f, 18f, 0f));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 11)
                .addBox(-1f, -1f, -4f, 2, 2, 4), PartPose.offset(0f, -0.5f, -3.5f));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(12, 11)
                .addBox(-1.5f, -1.5f, -3f, 3, 3, 3), PartPose.offset(0f, 0f, -3.5f));
        head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(24, 11)
                .addBox(-0.5f, -0.5f, -7f, 1, 2, 7), PartPose.offset(0f, 0f, -2.5f));
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-0.5f, -1f, 0f, 1, 2, 6), PartPose.offsetAndRotation(0f, -1f, -0.5f, 0.35f, 0f, 0f));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-0.5f, -0.5f, 0f, 1, 1, 5), PartPose.offset(0f, -0.5f, 4f));
        body.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(40, 8)
                .addBox(-1.5f, 0f, 0f, 3, 1, 4), PartPose.offsetAndRotation(0f, 1f, 2f, 0.3f, 0f, 0f));
        PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 18).mirror()
                .addBox(-9f, -0.5f, -3f, 9, 1, 6), PartPose.offset(-2f, -1f, -1f));
        right.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 26).mirror()
                .addBox(-11f, -0.5f, -2.5f, 11, 1, 4), PartPose.offset(-9f, 0f, -0.5f));
        PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 18)
                .addBox(0f, -0.5f, -3f, 9, 1, 6), PartPose.offset(2f, -1f, -1f));
        left.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 26)
                .addBox(0f, -0.5f, -2.5f, 11, 1, 4), PartPose.offset(9f, 0f, -0.5f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Pterodactyl entity, float limbSwing, float amount, float age, float netHeadYaw, float headPitch) {
        float beat;
        if (entity.flapping()) {
            // slow deep beats: the wing comes down hard, the tip trails behind it
            beat = Mth.sin(age * 0.32f);
            rightWing.zRot = beat * 0.75f;
            leftWing.zRot = -beat * 0.75f;
            rightTip.zRot = Mth.sin(age * 0.32f - 0.9f) * 0.45f;
            leftTip.zRot = -Mth.sin(age * 0.32f - 0.9f) * 0.45f;
        } else {
            // gliding: wings spread, a slight dihedral and the tips feeling the air
            beat = Mth.sin(age * 0.05f) * 0.05f;
            rightWing.zRot = -0.08f + beat;
            leftWing.zRot = 0.08f - beat;
            rightTip.zRot = 0.06f + Mth.sin(age * 0.11f) * 0.03f;
            leftTip.zRot = -0.06f - Mth.sin(age * 0.11f) * 0.03f;
        }
        body.y = 18f - beat * 0.6f;
        neck.xRot = -0.15f + Mth.sin(age * 0.06f) * 0.05f;
        head.xRot = 0.1f;
        head.yRot = Mth.sin(age * 0.03f) * 0.25f;
        tail.xRot = 0.1f + Mth.sin(age * 0.08f) * 0.08f;
    }
}
