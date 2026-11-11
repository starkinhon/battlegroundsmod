package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.world.phys.Vec3;

// Kakashi's Perfect Susanoo as the approved page builds it (forja/kakashi-kit/kakashi.js v13, makeSusanoo): the numbers
// both sides need. The model is baked from the page (models/mesh/kakashi_susanoo.bin) in its own units at rest, facing -z;
// the page scales it by SUS_SCALE (1.45) into its world, and here one page unit is 0.9 blocks (the page's Kakashi is
// 2 units tall, a player 1.8 blocks).
public final class KakashiRig {
    public static final float SUS_SCALE = 1.45f;
    public static final float BLOCK = 0.9f;
    // Where Kakashi stands inside it on the page (user.g.position.y = 13.5, in the page's world units).
    public static final float SEAT = 13.5f;
    // The arms' pivots at rest (baked): shoulder and elbow of the left (s = -1) and right (s = 1) arm.
    private static final float[][] SH = {{-1.62f, 10.35f, 0}, {1.62f, 10.35f, 0}};
    private static final float[][] EL = {{-1.78f, 8.45f, 0}, {1.78f, 8.45f, 0}};
    // The hand group hangs 1.58 below the elbow (page: hand.position.y = -1.58).
    private static final float HAND = 1.58f;

    private KakashiRig() {
    }

    // The page's armPose(a, k, throwK): sh.rotation = (-1.0 k - 0.9 throwK, 0, (0.25 - 0.4 throwK) s), el.rotation.x = 0.9 k - 0.9 throwK.
    // Returns {shoulder bone, elbow bone} for arm i (0 left, 1 right), each mapping rest model units to posed model units.
    public static Matrix4f[] bones(int i, float k, float throwK) {
        float s = i == 0 ? -1 : 1;
        float[] sp = SH[i], ep = EL[i];
        Matrix4f sh = new Matrix4f().translation(sp[0], sp[1], sp[2]).rotateX(-1.0f * k - 0.9f * throwK).rotateZ((0.25f - 0.4f * throwK) * s)
                .translate(-sp[0], -sp[1], -sp[2]);
        Matrix4f el = new Matrix4f(sh).translate(ep[0], ep[1], ep[2]).rotateX(0.9f * k - 0.9f * throwK).translate(-ep[0], -ep[1], -ep[2]);
        return new Matrix4f[] {sh, el};
    }

    // Where hand i is, in model units (the page puts the shuriken at the hand + (0, 0.1, -0.4) world units).
    public static Vector3f hand(int i, float k, float throwK) {
        Matrix4f el = bones(i, k, throwK)[1];
        float[] ep = EL[i];
        return el.transformPosition(new Vector3f(ep[0], ep[1] - HAND, ep[2]));
    }

    // Model units to the world: feet = where it stands, yaw in radians (the entity's), -z of the model to the front.
    public static Vec3 world(Vec3 feet, float yaw, Vector3f model) {
        float th = (float) Math.PI - yaw;
        float x = model.x * SUS_SCALE * BLOCK, y = model.y * SUS_SCALE * BLOCK, z = model.z * SUS_SCALE * BLOCK;
        float c = (float) Math.cos(th), sn = (float) Math.sin(th);
        return feet.add(x * c + z * sn, y, -x * sn + z * c);
    }

    // The page's world units (already SUS_SCALE) to the world.
    public static Vec3 pageWorld(Vec3 feet, float yaw, float x, float y, float z) {
        return world(feet, yaw, new Vector3f(x / SUS_SCALE, y / SUS_SCALE, z / SUS_SCALE));
    }

    // Where the shuriken sits in hand i (the page: hand + (0, 0.1, -0.4) in its world, scale 1.1).
    public static Vec3 shurikenInHand(Vec3 feet, float yaw, int i, float k, float throwK) {
        Vector3f h = hand(i, k, throwK).mul(SUS_SCALE);
        return pageWorld(feet, yaw, h.x, h.y + 0.1f, h.z - 0.4f);
    }
}
