package com.starkinhon.battlegroundsmod.client;

import java.lang.reflect.Method;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ViewportEvent;

// A short cutscene: the camera sits in front of someone's face and slowly pushes in. Uses the third person camera
// so the local player's own body is drawn when they are the one being filmed.
public final class FaceCutscene {
    private static final Method SET_POSITION = ObfuscationReflectionHelper.findMethod(Camera.class, "setPosition", Vec3.class);

    private static int entityId = -1;
    private static int ticks;
    private static int age;
    private static float startDistance;
    private static float endDistance;
    private static float height;
    private static boolean hideHud;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private FaceCutscene() {
    }

    public static void start(int id, int duration, float fromDistance, float toDistance, float yOffset, boolean hide) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        entityId = id;
        ticks = Math.max(1, duration);
        age = 0;
        startDistance = fromDistance;
        endDistance = toDistance;
        height = yOffset;
        hideHud = hide;
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (hideHud) {
            minecraft.options.hideGui = true;
        }
    }

    public static void tick() {
        if (entityId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= ticks || minecraft.level.getEntity(entityId) == null) {
            stop(minecraft);
            return;
        }
        // F5 would put the camera back on the player.
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stop(Minecraft minecraft) {
        entityId = -1;
        minecraft.options.setCameraType(previousCameraType);
        minecraft.options.hideGui = previousHideGui;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = entityId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (entity == null) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        double progress = Mth.clamp((age + partialTick) / ticks, 0, 1);
        progress = progress * progress * (3 - 2 * progress);
        double distance = Mth.lerp(progress, startDistance, endDistance);
        Vec3 eyes = entity.getEyePosition(partialTick);
        Vec3 facing = Vec3.directionFromRotation(0, entity.getViewYRot(partialTick));
        Vec3 camera = eyes.add(facing.scale(distance)).add(0, height, 0);
        Vec3 toFace = eyes.subtract(camera);
        try {
            SET_POSITION.invoke(event.getCamera(), camera);
        } catch (ReflectiveOperationException e) {
            BattlegroundsMod.LOGGER.error("Cutscene could not move the camera", e);
            stop(minecraft);
            return;
        }
        event.setYaw((float) (Mth.atan2(toFace.z, toFace.x) * Mth.RAD_TO_DEG) - 90);
        event.setPitch((float) (-Mth.atan2(toFace.y, toFace.horizontalDistance()) * Mth.RAD_TO_DEG));
        event.setRoll(0);
    }
}
