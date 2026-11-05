package com.starkinhon.battlegroundsmod.client.outfit;

import org.joml.Vector3f;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

// Where the player's body and legs are this frame, in rig pixels (y up, facing +z, feet at 0). The model's own space
// is y down and facing -z, so a point (x, y, z) of the model is (x, 24 - y, -z) here; a turn around x keeps its angle.
// Body points are written as on a standing player (the body's pivot is at height 24), leg points from the leg's pivot.
final class Rig {
    enum Bone {
        // HAND_R rides the right arm like ARM_R, but is only drawn while the main hand is empty (the sword).
        HEAD, BODY, ARM_R, ARM_L, LEG_R, LEG_L, HAND_R
    }

    private final float[][] pivot = new float[Bone.values().length][3];
    private final float[] rot = new float[Bone.values().length];

    Rig() {
        set(Bone.BODY, 0, 24, 0, 0);
        set(Bone.LEG_R, -1.9f, 12, 0, 0);
        set(Bone.LEG_L, 1.9f, 12, 0, 0);
    }

    private void set(Bone bone, float x, float y, float z, float xRot) {
        float[] p = pivot[bone.ordinal()];
        p[0] = x;
        p[1] = y;
        p[2] = z;
        rot[bone.ordinal()] = xRot;
    }

    private void from(Bone bone, ModelPart part) {
        set(bone, part.x, 24 - part.y, -part.z, part.xRot);
    }

    void update(HumanoidModel<?> model) {
        from(Bone.BODY, model.body);
        from(Bone.LEG_R, model.rightLeg);
        from(Bone.LEG_L, model.leftLeg);
    }

    // A body point (as on a standing player) to where it is now.
    Vector3f body(float x, float y, float z, Vector3f out) {
        return toWorld(Bone.BODY, out.set(x, y, z), out);
    }

    Vector3f bodyDir(float x, float y, float z, Vector3f out) {
        float a = rot[Bone.BODY.ordinal()], c = (float) Math.cos(a), s = (float) Math.sin(a);
        return out.set(x, y * c - z * s, y * s + z * c);
    }

    // A point given from the bone's pivot (body: as on a standing player) to the player's space.
    Vector3f toWorld(Bone bone, Vector3f local, Vector3f out) {
        float a = rot[bone.ordinal()], c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] p = pivot[bone.ordinal()];
        float ly = bone == Bone.BODY ? local.y - 24 : local.y;
        float y = ly * c - local.z * s, z = ly * s + local.z * c;
        return out.set(local.x + p[0], y + p[1], z + p[2]);
    }

    // The other way. Body colliders are written as on a standing player too, so they get the 24 back.
    Vector3f toLocal(Bone bone, Vector3f world, Vector3f out) {
        float a = -rot[bone.ordinal()], c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] p = pivot[bone.ordinal()];
        float x = world.x - p[0], y0 = world.y - p[1], z0 = world.z - p[2];
        float y = y0 * c - z0 * s, z = y0 * s + z0 * c;
        if (bone == Bone.BODY) {
            return out.set(x, y + 24, z);
        }
        return out.set(x, y, z);
    }
}
