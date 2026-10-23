package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.skill.AgarraoSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

// Madara's neck grab, seen by the two fighters only: the camera cuts in on the grab, holds through the lift and the
// slam, backs off and rises to show the hole, then glides back to the player's own view. The shots are the Forja's,
// measured from where Madara stood when he grabbed, facing the target.
public final class GrabCutscene {
    private static final int BACK_OFF_TICKS = 5;
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static int targetId = -1;
    private static AgarraoSpec spec;
    private static int impact;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private GrabCutscene() {
    }

    public static void start(int caster, int target, boolean awakened, int impactTick) {
        Minecraft minecraft = Minecraft.getInstance();
        AgarraoSpec next = AgarraoSpec.get(awakened);
        Entity casterEntity = minecraft.level == null ? null : minecraft.level.getEntity(caster);
        Entity targetEntity = minecraft.level == null ? null : minecraft.level.getEntity(target);
        if (!next.cutscene().ativa() || casterEntity == null || targetEntity == null) {
            return;
        }
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        casterId = caster;
        targetId = target;
        spec = next;
        impact = impactTick;
        age = 0;
        origin = casterEntity.position();
        Vec3 to = targetEntity.position().subtract(origin);
        Vec3 flat = new Vec3(to.x, 0, to.z);
        forward = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, casterEntity.getYRot()) : flat.normalize();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return casterId >= 0;
    }

    private static int end() {
        return impact + Math.max(0, spec.cutscene().depoisTicks());
    }

    public static void tick() {
        if (casterId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= end() + Math.max(1, spec.cutscene().voltarTicks())
                || minecraft.level.getEntity(casterId) == null || minecraft.level.getEntity(targetId) == null) {
            stop(minecraft);
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stop(Minecraft minecraft) {
        casterId = -1;
        targetId = -1;
        minecraft.options.setCameraType(previousCameraType);
        minecraft.options.hideGui = previousHideGui;
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (casterId < 0) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // Forja coordinates: x to Madara's left, y up, z towards the target, from his feet.
    private static Vec3 world(double x, double y, double z) {
        Vec3 left = new Vec3(forward.z, 0, -forward.x);
        return origin.add(left.scale(x)).add(0, y, 0).add(forward.scale(z));
    }

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        if (casterId < 0 || minecraft.level == null || viewer == null) {
            return null;
        }
        double t = age + partialTick;
        AgarraoSpec.Agarrar agarrar = spec.agarrar();
        Vec3 pos;
        Vec3 look;
        switch (String.valueOf(spec.cutscene().camera())) {
            case "lado" -> {
                pos = world(4.2, 1.4, 0.4);
                look = world(0, 1.6, 0.7);
            }
            case "ombro" -> {
                pos = world(-0.9, 2.4, -1.8);
                look = world(0, 1.4, 1.4);
            }
            case "alvo" -> {
                // Riding the victim's head: up in the air, then slammed down.
                int slam = impact - Math.max(1, spec.esmagar().descidaTicks());
                double k = t < slam ? smooth(t / Math.max(1, agarrar.levantarTicks())) : 1 - Math.min(1, (t - slam) / Math.max(1, impact - slam));
                pos = world(0, 0.4 + 1.2 * k, 1.5);
                look = world(0, 2.4 * k + 0.6, -0.5);
            }
            default -> {
                pos = world(3.6, 0.35, -0.6);
                look = world(-0.2, 1.7, 0.8);
            }
        }
        // After the slam the camera backs off and rises so the hole fits in the shot.
        if (t >= impact) {
            double k = smooth((t - impact) / BACK_OFF_TICKS);
            pos = pos.lerp(world(6.5, 3, -2.5), k);
            look = look.lerp(world(0, 0.4, 1.3), k);
        }
        Vec3 dir = look.subtract(pos);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        double blend = blend(t);
        if (blend < 1) {
            Vec3 own = viewer.getEyePosition(partialTick);
            float ownYaw = viewer.getViewYRot(partialTick);
            float ownPitch = viewer.getViewXRot(partialTick);
            pos = own.lerp(pos, blend);
            yaw = ownYaw + (float) (Mth.wrapDegrees(yaw - ownYaw) * blend);
            pitch = (float) Mth.lerp(blend, ownPitch, pitch);
        }
        return new FaceCutscene.Shot(pos, yaw, pitch);
    }

    // Cuts straight in, eases back out over voltarTicks.
    private static double blend(double t) {
        return 1 - smooth((t - end()) / Math.max(1, spec.cutscene().voltarTicks()));
    }

    // Cinema bars, sliding away with the camera at the end.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0 || !spec.cutscene().barrasPretas()) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
        int height = (int) Math.round(graphics.guiHeight() * BAR_HEIGHT * blend(t));
        if (height > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
