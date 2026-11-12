package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Ino's Shintenshin and Shinranshin on the player's side.
// Piloting: the camera goes into the body she took and the player's own keys drive it (WASD walks, space jumps, a click
// hits); the player's body lies still, its keys sent to the server each tick (Ino.input) instead of moving it. The mouse
// still turns the player, and the camera follows that right away (no wait for the server).
// Inverted (Shinranshin on a player): walking and looking are reversed for 7 s (3 s on a boss).
public final class InoClient {
    private static int host = -1;
    private static int pilotTicks;
    private static int invertTicks;
    private static int attacks;
    private static float forward;
    private static float strafe;
    private static boolean jump;

    private InoClient() {
    }

    // From InoFx: INO_PILOT (id = the body, -1 ends; a = ticks) and INO_INVERT (a = ticks).
    public static void receive(int kind, int id, float a) {
        if (kind == SubNet.INO_PILOT) {
            if (id < 0) {
                stop();
                return;
            }
            host = id;
            pilotTicks = (int) a + 40;
            attacks = 0;
            forward = strafe = 0;
            jump = false;
            follow();
        } else if (kind == SubNet.INO_INVERT) {
            invertTicks = Math.max(invertTicks, (int) a);
        }
    }

    public static boolean piloting() {
        return host >= 0;
    }

    private static void follow() {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(host);
        if (e != null && mc.getCameraEntity() != e) {
            mc.setCameraEntity(e);
        }
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (host >= 0 && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        host = -1;
        pilotTicks = 0;
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            host = -1;
            invertTicks = 0;
            return;
        }
        if (invertTicks > 0) {
            invertTicks--;
        }
        if (host < 0) {
            return;
        }
        Entity e = mc.level.getEntity(host);
        if (--pilotTicks <= 0 || !mc.player.isAlive() || e != null && !e.isAlive()) {
            stop();
            return;
        }
        follow();
        PacketDistributor.sendToServer(new ModPayloads.InoPilotInput(forward, strafe, jump, mc.player.getYRot(), mc.player.getXRot(), attacks));
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        Input in = event.getInput();
        if (host >= 0) {
            forward = in.forwardImpulse;
            strafe = in.leftImpulse;
            jump = in.jumping;
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
            return;
        }
        if (invertTicks > 0) {
            in.forwardImpulse = -in.forwardImpulse;
            in.leftImpulse = -in.leftImpulse;
            boolean up = in.up, left = in.left;
            in.up = in.down;
            in.down = up;
            in.left = in.right;
            in.right = left;
        }
    }

    // A click while piloting: the body hits (counted and sent with the keys), never the player's own hand.
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (host < 0) {
            return;
        }
        if (event.isAttack()) {
            attacks++;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // Inverted: the mouse turns the other way (sensitivity s gives the factor s * 0.6 + 0.2, cubed; this makes it negative).
    public static void onTurn(CalculatePlayerTurnEvent event) {
        if (invertTicks > 0) {
            double s = event.getMouseSensitivity();
            event.setMouseSensitivity((-(s * 0.6 + 0.2) - 0.2) / 0.6);
        }
    }

    // Piloting: the camera looks where the player's mouse points this frame.
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (host < 0 || mc.player == null || mc.getCameraEntity() == mc.player) {
            return;
        }
        float partial = (float) event.getPartialTick();
        event.setYaw(mc.player.getViewYRot(partial));
        event.setPitch(mc.player.getViewXRot(partial));
    }
}
