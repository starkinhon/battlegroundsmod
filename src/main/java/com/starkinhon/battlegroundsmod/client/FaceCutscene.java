package com.starkinhon.battlegroundsmod.client;

import java.lang.reflect.Method;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.skill.CutsceneSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// A short cutscene: the camera jumps in front of someone's face, pushes in, holds, then glides back to the
// player's own view. Uses the third person camera so the local player's body is drawn when they are the one filmed.
// The server freezes everyone involved; here the movement keys are also ignored so nothing jitters.
public final class FaceCutscene {
    private static final Method SET_POSITION = ObfuscationReflectionHelper.findMethod(Camera.class, "setPosition", Vec3.class);

    private static int entityId = -1;
    private static CutsceneSpec spec;
    private static int age;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private FaceCutscene() {
    }

    public static void start(int id, CutsceneSpec cutscene) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        entityId = id;
        spec = cutscene;
        age = 0;
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (spec.esconderHud()) {
            minecraft.options.hideGui = true;
        }
    }

    public static void tick() {
        if (entityId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= spec.duracaoTicks() || minecraft.level.getEntity(entityId) == null) {
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

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (entityId < 0) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = entityId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        LocalPlayer viewer = minecraft.player;
        if (entity == null || viewer == null) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        double t = age + partialTick;
        int zoom = Math.max(1, spec.zoomTicks());
        int back = Math.max(1, spec.returnTicks());
        double distance = Mth.lerp(smooth(t / zoom), spec.distanciaInicio(), spec.distanciaFim());

        Vec3 eyes = entity.getEyePosition(partialTick);
        Vec3 facing = Vec3.directionFromRotation(0, entity.getViewYRot(partialTick));
        Vec3 position = eyes.add(facing.scale(distance)).add(0, spec.altura(), 0);
        Vec3 toFace = eyes.subtract(position);
        float yaw = (float) (Mth.atan2(toFace.z, toFace.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(toFace.y, toFace.horizontalDistance()) * Mth.RAD_TO_DEG);

        // Last part: glide from the face shot back to the viewer's own first person view.
        double out = smooth((t - (spec.duracaoTicks() - back)) / back);
        if (out > 0) {
            position = position.lerp(viewer.getEyePosition(partialTick), out);
            yaw += (float) (Mth.wrapDegrees(viewer.getViewYRot(partialTick) - yaw) * out);
            pitch = (float) Mth.lerp(out, pitch, viewer.getViewXRot(partialTick));
        }
        try {
            SET_POSITION.invoke(event.getCamera(), position);
        } catch (ReflectiveOperationException e) {
            BattlegroundsMod.LOGGER.error("Cutscene could not move the camera", e);
            stop(minecraft);
            return;
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(0);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
