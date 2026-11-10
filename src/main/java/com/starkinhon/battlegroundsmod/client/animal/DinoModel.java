package com.starkinhon.battlegroundsmod.client.animal;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.Dinosaur;

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

// One theropod for both dinosaurs (the renderer scales it up for the tyrannosaur): a long body held level, a neck up
// and forward with the big head and a jaw that opens on the bite, a tail in two pieces that sways as it runs, strong
// legs in three pieces and the little arms. The texture is drawn by tools/dino.py from these same boxes.
public class DinoModel extends HierarchicalModel<Dinosaur> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dinosaur"), "main");

    private final ModelPart root, body, neck, head, jaw, tail1, tail2, rightLeg, leftLeg, rightShin, leftShin, rightArm, leftArm;

    public DinoModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.tail1 = body.getChild("tail1");
        this.tail2 = tail1.getChild("tail2");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightShin = rightLeg.getChild("shin");
        this.leftShin = leftLeg.getChild("shin");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3f, -3f, -6f, 6, 6, 12), PartPose.offset(0f, 13f, 0f));
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-2f, -5f, -3f, 4, 6, 4), PartPose.offset(0f, -1f, -5f));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-2.5f, -3f, -6f, 5, 4, 6), PartPose.offset(0f, -4f, -1.5f));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(36, 10)
                .addBox(-2f, 0f, -5f, 4, 2, 5), PartPose.offset(0f, 1f, -0.5f));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(16, 18)
                .addBox(-2f, -2f, 0f, 4, 4, 8), PartPose.offset(0f, -1f, 5.5f));
        tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(40, 18)
                .addBox(-1f, -1.5f, 0f, 2, 3, 8), PartPose.offset(0f, 0f, 7.5f));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 30)
                .addBox(-0.5f, 0f, -1f, 1, 3, 1), PartPose.offset(-2.6f, 2f, -4.5f));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 30).mirror()
                .addBox(-0.5f, 0f, -1f, 1, 3, 1), PartPose.offset(2.6f, 2f, -4.5f));
        for (int side = -1; side <= 1; side += 2) {
            CubeListBuilder thigh = CubeListBuilder.create().texOffs(0, 30);
            CubeListBuilder shin = CubeListBuilder.create().texOffs(14, 30);
            CubeListBuilder foot = CubeListBuilder.create().texOffs(22, 30);
            if (side > 0) {
                thigh.mirror();
                shin.mirror();
                foot.mirror();
            }
            PartDefinition leg = root.addOrReplaceChild(side < 0 ? "right_leg" : "left_leg",
                    thigh.addBox(-1.5f, -1f, -2f, 3, 6, 4), PartPose.offset(side * 3f, 14f, 2f));
            PartDefinition lower = leg.addOrReplaceChild("shin", shin.addBox(-1f, 0f, -1f, 2, 5, 2), PartPose.offset(0f, 5f, 1f));
            lower.addOrReplaceChild("foot", foot.addBox(-1.5f, -1f, -3f, 3, 1, 4), PartPose.offset(0f, 5f, 0f));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Dinosaur entity, float limbSwing, float amount, float age, float netHeadYaw, float headPitch) {
        float walk = limbSwing * 0.6662f;
        amount = Math.min(amount, 1f);
        rightLeg.xRot = Mth.cos(walk) * 0.9f * amount;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 0.9f * amount;
        rightShin.xRot = Math.max(0f, -Mth.cos(walk)) * 0.7f * amount;
        leftShin.xRot = Math.max(0f, Mth.cos(walk)) * 0.7f * amount;
        body.y = 13f - Math.abs(Mth.sin(walk)) * 0.6f * amount;
        body.zRot = Mth.cos(walk) * 0.05f * amount;
        float breathe = Mth.sin(age * 0.08f);
        neck.xRot = 0.45f + breathe * 0.03f + headPitch * Mth.DEG_TO_RAD * 0.3f;
        neck.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.35f;
        head.xRot = -0.45f + headPitch * Mth.DEG_TO_RAD * 0.5f;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.45f;
        // the bite opens the jaw wide and snaps it shut; at rest it hangs a little open
        jaw.xRot = 0.08f + Mth.sin(attackTime * Mth.PI) * 0.95f;
        float sway = Mth.sin(age * 0.07f) * 0.12f + Mth.cos(walk) * 0.25f * amount;
        tail1.xRot = -0.12f + Mth.sin(walk * 2f) * 0.04f * amount;
        tail1.yRot = sway;
        tail2.yRot = sway * 1.4f;
        tail2.xRot = 0.1f;
        rightArm.xRot = 0.6f + breathe * 0.05f + Mth.sin(attackTime * Mth.PI) * 0.6f;
        leftArm.xRot = 0.6f - breathe * 0.05f + Mth.sin(attackTime * Mth.PI) * 0.6f;
    }
}
