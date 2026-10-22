package com.starkinhon.battlegroundsmod.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// A very short shot when the Susanoo counter lands: the camera slides behind the caster's right shoulder, low,
// looking past them at the trapped enemy, then glides back. The player keeps full control so it never costs a
// real fight; only their head is turned up towards the target while it plays.
public final class ShoulderCutscene {
    private static final int IN_TICKS = 6;
    private static final int OUT_TICKS = 8;
    private static final double BACK = 2.1;
    private static final double SIDE = 0.85;
    private static final double LOW = -0.15;
    // Degrees the head is tilted up past the target, so Madara looks down on nobody: he looks up at the grab.
    private static final float LOOK_UP = 6;

    private static int casterId = -1;
    private static int targetId = -1;
    private static int duration;
    private static int age;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;

    private ShoulderCutscene() {
    }

    public static void start(int caster, int target, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
        }
        casterId = caster;
        targetId = target;
        duration = Math.max(IN_TICKS + OUT_TICKS, ticks);
        age = 0;
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static boolean active() {
        return casterId >= 0;
    }

    public static void tick() {
        if (casterId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(casterId);
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(targetId);
        if (caster == null || target == null || ++age >= duration) {
            stop(minecraft);
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        // Turn the caster's head to the trapped target, a little upwards (the part of the shot the player sees).
        LocalPlayer player = minecraft.player;
        if (player != null && player.getId() == casterId && age < duration - OUT_TICKS) {
            Vec3 to = target.getEyePosition().subtract(player.getEyePosition());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG) - LOOK_UP;
            float ease = age < IN_TICKS ? 0.45f : 0.8f;
            player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * ease);
            player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * ease, -90, 90));
            player.setYHeadRot(player.getYRot());
        }
    }

    private static void stop(Minecraft minecraft) {
        casterId = -1;
        targetId = -1;
        minecraft.options.setCameraType(previousCameraType);
    }

    // Behind and to the right of the caster, on the line from the caster to the target. Null when not playing.
    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (casterId < 0 || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        Entity caster = minecraft.level.getEntity(casterId);
        Entity target = minecraft.level.getEntity(targetId);
        if (caster == null || target == null) {
            return null;
        }
        LocalPlayer viewer = minecraft.player;
        double t = age + partialTick;
        Vec3 eyes = caster.getEyePosition(partialTick);
        Vec3 aim = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
        Vec3 flat = new Vec3(aim.x - eyes.x, 0, aim.z - eyes.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getViewYRot(partialTick)) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Vec3 position = eyes.subtract(flat.scale(BACK)).add(right.scale(SIDE)).add(0, LOW, 0);
        Vec3 look = aim.subtract(position);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);

        // Glide in from, and back out to, the player's own view.
        double blend = Math.min(smooth(t / IN_TICKS), 1 - smooth((t - (duration - OUT_TICKS)) / OUT_TICKS));
        if (blend < 1) {
            Vec3 own = viewer.getEyePosition(partialTick);
            float ownYaw = viewer.getViewYRot(partialTick);
            float ownPitch = viewer.getViewXRot(partialTick);
            position = own.lerp(position, blend);
            yaw = ownYaw + (float) (Mth.wrapDegrees(yaw - ownYaw) * blend);
            pitch = (float) Mth.lerp(blend, ownPitch, pitch);
        }
        return new FaceCutscene.Shot(position, yaw, pitch);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
