package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.FlightClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

// The server setting the local player's speed (a skill's dash, a launch): the Dragon Ball flight, which moves the player
// itself every tick, takes it instead of wiping it out (FlightClient.serverMotion).
@Mixin(ClientPacketListener.class)
public abstract class EntityMotionMixin {
    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void battlegroundsmod$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && packet.getId() == minecraft.player.getId()) {
            FlightClient.serverMotion(new Vec3(packet.getXa(), packet.getYa(), packet.getZa()));
        }
    }
}
