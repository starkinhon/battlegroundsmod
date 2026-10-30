package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.skill.HakaiSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

// Bills' Hakai, seen by him and his target only, in three shots: his eyes up close while he says "Hakai." (the line
// shows as a subtitle), then from the side as his hand opens at the target in the flash, then on the target as it lights
// up and falls apart. Then the camera glides back to the player's own view. Shots and timing from bills.hakai.json.
public final class HakaiCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static int targetId = -1;
    private static HakaiSpec spec;
    private static int age;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;
    // Where the target last was: the shot on it stays put after its body is gone.
    private static Vec3 lastTarget = Vec3.ZERO;

    private HakaiCutscene() {
    }

    public static void start(int caster, int target, boolean awakened) {
        Minecraft minecraft = Minecraft.getInstance();
        HakaiSpec next = HakaiSpec.get(awakened);
        Entity targetEntity = minecraft.level == null ? null : minecraft.level.getEntity(target);
        if (!next.cutscene().ativa() || minecraft.level == null || minecraft.level.getEntity(caster) == null || targetEntity == null) {
            return;
        }
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        casterId = caster;
        targetId = target;
        spec = next;
        age = 0;
        lastTarget = targetEntity.position();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return casterId >= 0;
    }

    public static void tick() {
        if (casterId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= spec.cutsceneEnd() + Math.max(1, spec.cutscene().voltarTicks())
                || minecraft.level.getEntity(casterId) == null) {
            stop(minecraft);
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (target != null) {
            lastTarget = target.position();
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

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        Entity caster = casterId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(casterId);
        if (caster == null || viewer == null) {
            return null;
        }
        Entity targetEntity = minecraft.level.getEntity(targetId);
        Vec3 target = targetEntity != null ? targetEntity.getPosition(partialTick) : lastTarget;
        double targetHeight = targetEntity != null ? targetEntity.getBbHeight() : 1.8;
        double t = age + partialTick;
        HakaiSpec.Cutscene c = spec.cutscene();
        Vec3 feet = caster.getPosition(partialTick);
        Vec3 eyes = caster.getEyePosition(partialTick);
        Vec3 toTarget = target.subtract(feet);
        Vec3 forward = new Vec3(toTarget.x, 0, toTarget.z).lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot())
                : new Vec3(toTarget.x, 0, toTarget.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        int touch = Math.max(1, spec.tempo().atrasoTicks());
        int dissolve = spec.dissolveTick();

        Vec3 pos;
        Vec3 look;
        if (t < touch) {
            // His eyes from a little to the side, pushing in slowly as he speaks.
            double k = smooth(t / touch);
            double distance = Mth.lerp(k, c.distanciaOlhos() * 1.35, c.distanciaOlhos());
            pos = eyes.add(forward.scale(distance)).add(right.scale(c.ladoOlhos())).add(0, 0.05, 0);
            look = eyes.add(right.scale(c.ladoOlhos() * 0.15));
        } else if (t < dissolve) {
            // From the side, both of them in the shot and his hand between, drifting a little.
            double k = (t - touch) / Math.max(1, dissolve - touch);
            Vec3 middle = feet.lerp(target, 0.5).add(0, 1.2, 0);
            double side = Math.max(2, c.distanciaMao());
            pos = middle.add(right.scale(side)).add(forward.scale(-0.4 - 0.3 * k)).add(0, 0.1, 0);
            look = middle.add(forward.scale(0.15));
        } else {
            // On the target, from Bills' side, pulling back and rising with the sparks.
            double k = smooth((t - dissolve) / Math.max(1, spec.killTick() - dissolve));
            Vec3 chest = target.add(0, targetHeight * 0.6, 0);
            double distance = c.distanciaAlvo() * (1 + 0.45 * k);
            pos = chest.add(forward.scale(-distance)).add(right.scale(distance * 0.35)).add(0, 0.2 + 0.9 * k, 0);
            look = chest.add(0, 0.5 * k, 0);
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
        return 1 - smooth((t - spec.cutsceneEnd()) / Math.max(1, spec.cutscene().voltarTicks()));
    }

    // Cinema bars, and the line he says at the bottom like a subtitle.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
        int bar = spec.cutscene().barrasPretas() ? (int) Math.round(graphics.guiHeight() * BAR_HEIGHT * blend(t)) : 0;
        if (bar > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
        HakaiSpec.Legenda legenda = spec.legenda();
        if (legenda == null || legenda.texto() == null || legenda.texto().isEmpty() || t < legenda.inicioTick() || t > legenda.fimTick()) {
            return;
        }
        // Fades in and out over a couple of ticks.
        float alpha = (float) Mth.clamp(Math.min(t - legenda.inicioTick(), legenda.fimTick() - t) / 2, 0, 1);
        if (alpha <= 0.02f) {
            return;
        }
        int color = parse(legenda.cor(), 0xFFFFFF) | Math.max(4, (int) (alpha * 255)) << 24;
        float scale = (float) Math.max(0.5, legenda.tamanho());
        Component text = Component.literal(legenda.texto());
        var font = Minecraft.getInstance().font;
        float y = graphics.guiHeight() - Math.max(bar, 8) - 10 - font.lineHeight * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
