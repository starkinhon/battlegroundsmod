package com.starkinhon.battlegroundsmod.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

// A boss's ult cutscene: the camera pushes in on its face, then swings behind its shoulder and looks up at the sky
// (at lookAt, where the meteor comes from), then glides back to the player's view. HUD hidden, movement keys ignored.
public final class BossCutscene {
    private static final int TURN_TICKS = 18;

    private static int entityId = -1;
    private static int zoomTicks;
    private static int skyTicks;
    private static int backTicks;
    private static Vec3 lookAt = Vec3.ZERO;
    private static int age;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private BossCutscene() {
    }

    public static void start(int id, int zoom, int sky, int back, Vec3 skyPoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        entityId = id;
        zoomTicks = Math.max(1, zoom);
        skyTicks = Math.max(1, sky);
        backTicks = Math.max(1, back);
        lookAt = skyPoint;
        age = 0;
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return entityId >= 0;
    }

    public static void tick() {
        if (entityId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= zoomTicks + skyTicks + backTicks || minecraft.level.getEntity(entityId) == null) {
            entityId = -1;
            minecraft.options.setCameraType(previousCameraType);
            minecraft.options.hideGui = previousHideGui;
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
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

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = entityId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        LocalPlayer viewer = minecraft.player;
        if (entity == null || viewer == null) {
            return null;
        }
        double t = age + partialTick;
        Vec3 eyes = entity.getEyePosition(partialTick);
        Vec3 facing = Vec3.directionFromRotation(0, entity.getViewYRot(partialTick));
        Vec3 right = new Vec3(-facing.z, 0, facing.x);

        // Face: slow push in from 3.5 to 1.1 blocks in front of the eyes.
        Vec3 face = eyes.add(facing.scale(Mth.lerp(smooth(t / zoomTicks), 3.5, 1.1)));
        // Sky: low behind the right shoulder, looking up past the head at where the meteor appears.
        Vec3 sky = eyes.add(facing.scale(-2.2)).add(right.scale(0.9)).add(0, -0.4, 0);
        double turn = smooth((t - zoomTicks) / TURN_TICKS);
        Vec3 position = face.lerp(sky, turn);
        Vec3 target = eyes.lerp(lookAt, turn);
        Vec3 dir = target.subtract(position);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);

        double out = smooth((t - zoomTicks - skyTicks) / backTicks);
        if (out > 0) {
            position = position.lerp(viewer.getEyePosition(partialTick), out);
            yaw += (float) (Mth.wrapDegrees(viewer.getViewYRot(partialTick) - yaw) * out);
            pitch = (float) Mth.lerp(out, pitch, viewer.getViewXRot(partialTick));
        }
        return new FaceCutscene.Shot(position, yaw, pitch);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
