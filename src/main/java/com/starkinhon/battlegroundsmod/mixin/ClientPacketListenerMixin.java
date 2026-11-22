// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

// The particles the server sends (most powers' sparks, dust and smoke go through ServerLevel.sendParticles), cut down
// by the Experiência do jogador menu (client/SoulsSettingsScreen): the effects quality keeps a part of them, and the
// effects range drops the ones farther than it from the camera. On high quality with no range (the defaults) nothing
// changes.
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    // Right after the packet is moved to the game thread (the first run, on the network thread, throws there).
    @Inject(method = "handleParticleEvent", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"),
            cancellable = true)
    private void battlegroundsmod$cull(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        double range = FxConfig.effectRange();
        if (range < Double.MAX_VALUE) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double dx = packet.getX() - camera.x, dy = packet.getY() - camera.y, dz = packet.getZ() - camera.z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                ci.cancel();
                return;
            }
        }
        // a lone particle (count 0, thrown with its own speed): kept by chance on low and medium
        if (packet.getCount() == 0 && FxConfig.effectQuality() < 2 && Math.random() > FxConfig.particleScale()) {
            ci.cancel();
        }
    }

    // A burst: fewer of them (the same number every time the count is read, never 0).
    @ModifyExpressionValue(method = "handleParticleEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;getCount()I"))
    private int battlegroundsmod$count(int count) {
        return FxConfig.particles(count);
    }
}
