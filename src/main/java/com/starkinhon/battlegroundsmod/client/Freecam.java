package com.starkinhon.battlegroundsmod.client;

import java.lang.reflect.Method;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// F6: detaches the camera from the player to watch moves from afar. The player stays still (abilities still work)
// while WASD, space and shift fly the camera and the mouse turns it. Ctrl flies faster.
// Built on the third person camera so the player keeps rendering; only the camera position and angles are replaced.
public final class Freecam {
    public static final KeyMapping KEY = new KeyMapping("key.battlegroundsmod.freecam", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6, "key.categories.battlegroundsmod");
    private static final double SPEED = 0.5;
    private static final double SPRINT_MULTIPLIER = 3;
    private static final Method SET_POSITION = ObfuscationReflectionHelper.findMethod(Camera.class, "setPosition", Vec3.class);

    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 prevPos = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    // The player's real rotation, kept while the mouse turns the camera instead.
    private static float lockedYaw;
    private static float lockedPitch;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static float forward;
    private static float strafe;
    private static boolean up;
    private static boolean down;

    private Freecam() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (KEY.consumeClick()) {
            if (player != null && minecraft.screen == null) {
                if (active) {
                    disable(minecraft);
                } else {
                    enable(minecraft, player);
                }
            }
        }
        if (!active) {
            return;
        }
        if (player == null || !player.isAlive()) {
            disable(minecraft);
            return;
        }
        absorbMouse(player);
        // F5 would put the camera back on the player.
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        Vec3 motion = Vec3.directionFromRotation(pitch, yaw).scale(forward)
                .add(Vec3.directionFromRotation(0, yaw - 90).scale(strafe))
                .add(0, (up ? 1 : 0) - (down ? 1 : 0), 0);
        if (motion.lengthSqr() > 1) {
            motion = motion.normalize();
        }
        double speed = SPEED * (minecraft.options.keySprint.isDown() ? SPRINT_MULTIPLIER : 1);
        prevPos = pos;
        pos = pos.add(motion.scale(speed));
    }

    private static void enable(Minecraft minecraft, LocalPlayer player) {
        active = true;
        yaw = lockedYaw = player.getYRot();
        pitch = lockedPitch = player.getXRot();
        // Start a little behind the player so they are in view right away.
        pos = prevPos = player.getEyePosition().subtract(Vec3.directionFromRotation(pitch, yaw).scale(3));
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.freecam_on"), true);
    }

    private static void disable(Minecraft minecraft) {
        active = false;
        forward = strafe = 0;
        up = down = false;
        minecraft.options.setCameraType(previousCameraType);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.battlegroundsmod.freecam_off"), true);
        }
    }

    // The mouse still turns the player; move that turn over to the camera and put the player back.
    private static void absorbMouse(LocalPlayer player) {
        float dYaw = player.getYRot() - lockedYaw;
        float dPitch = player.getXRot() - lockedPitch;
        if (dYaw == 0 && dPitch == 0) {
            return;
        }
        yaw += dYaw;
        pitch = Mth.clamp(pitch + dPitch, -90, 90);
        player.setYRot(lockedYaw);
        player.setXRot(lockedPitch);
        player.yRotO = lockedYaw;
        player.xRotO = lockedPitch;
    }

    // Movement keys drive the camera, so the player gets no input.
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active) {
            return;
        }
        Input input = event.getInput();
        forward = input.forwardImpulse;
        strafe = input.leftImpulse;
        up = input.jumping;
        down = input.shiftKeyDown;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active || player == null) {
            return;
        }
        absorbMouse(player);
        try {
            SET_POSITION.invoke(event.getCamera(), prevPos.lerp(pos, event.getPartialTick()));
        } catch (ReflectiveOperationException e) {
            BattlegroundsMod.LOGGER.error("Freecam could not move the camera", e);
            disable(Minecraft.getInstance());
            return;
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(0);
    }
}
