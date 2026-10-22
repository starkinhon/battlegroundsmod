package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.TeleporteSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// Turns the keys into the standard moves: back + jump on the ground is the dodge, jump again in the air is the air
// jump, in combat mode left/right + jump on the ground is the side dodge and the dash key dashes, and a double tap of the
// sprint key (Ctrl) teleports to the target (that one also starts combat mode). Runs before the player ticks, so a dodge replaces the normal jump of that same tick.
// The player is moved here right away and the server is told (see Movement); the teleport is done by the server.
public final class MovementInput {
    private static boolean jumpWasDown = false;
    private static boolean sprintWasDown = false;
    private static int lastSprintTap = -100;
    // Creative players toggle flying with a double tap; when the air jump is on in creative, that toggle is undone.
    private static boolean undoFlight = false;

    private MovementInput() {
    }

    public static void tickPre() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Options options = minecraft.options;
        boolean down = options.keyJump.isDown();
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        // A quick tap can press and release Ctrl between two ticks, which isDown misses, so the key's clicks count too.
        boolean sprintDown = options.keySprint.isDown();
        boolean sprintTap = sprintDown && !sprintWasDown;
        sprintWasDown = sprintDown;
        while (options.keySprint.consumeClick()) {
            sprintTap = true;
        }
        boolean dash = false;
        while (Keybinds.DASH.consumeClick()) {
            dash = true;
        }
        if (player == null) {
            return;
        }
        Movement.tick(player);
        if (minecraft.screen != null) {
            return;
        }
        boolean combat = player.getData(ModAttachments.POWER).combat;
        if (sprintTap) {
            if (player.tickCount - lastSprintTap <= TeleporteSpec.get().toquesJanelaTicks()) {
                PacketDistributor.sendToServer(new ModPayloads.Teleport());
                lastSprintTap = -100;
            } else {
                lastSprintTap = player.tickCount;
            }
        }
        float forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        float strafe = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
        Movement.Kind kind = null;
        if (dash) {
            kind = Movement.Kind.DASH;
        } else if (pressed && player.onGround()) {
            kind = forward < 0 ? Movement.Kind.ESQUIVA : forward == 0 && strafe != 0 && combat ? Movement.Kind.DESVIO : null;
        } else if (pressed) {
            kind = Movement.Kind.PULO_DUPLO;
        }
        if (kind == null || !Movement.allowed(player, kind, 0)) {
            return;
        }
        player.setDeltaMovement(Movement.velocity(player, kind, forward, strafe));
        Movement.spend(player, kind);
        undoFlight = kind == Movement.Kind.PULO_DUPLO && player.getAbilities().mayfly && !player.getAbilities().flying;
        PacketDistributor.sendToServer(new ModPayloads.UseMovement(kind.ordinal(), forward, strafe));
    }

    public static void tickPost() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (undoFlight && player != null && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        undoFlight = false;
    }
}
