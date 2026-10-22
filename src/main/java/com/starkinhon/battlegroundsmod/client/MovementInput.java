package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// Turns the jump key into the standard moves: back + jump on the ground is the dodge, jump again in the air is the
// air jump. Runs before the player ticks, so the dodge replaces the normal jump of that same tick. The player is
// moved here right away and the server is told (see Movement).
public final class MovementInput {
    private static boolean jumpWasDown = false;
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
        if (player == null) {
            return;
        }
        Movement.tick(player);
        if (!pressed || minecraft.screen != null) {
            return;
        }
        float forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        float strafe = (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0);
        Movement.Kind kind = player.onGround() ? (forward < 0 ? Movement.Kind.ESQUIVA : null) : Movement.Kind.PULO_DUPLO;
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
