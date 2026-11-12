package com.starkinhon.soulsdev.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.soulsdev.client.CameraDev;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

// Câmera de teste do Souls Dev (/devc camera). Roda depois do mixin de cutscene do mod (prioridade maior), então a
// câmera de teste manda até durante cutscene: pra ver a cutscene como o jogador vê, use /devc camera jogador.
@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraDevMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void soulsdev$camera(BlockGetter level, Entity entity, boolean detachedArg, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        CameraDev.Pose pose = CameraDev.pose(partialTick);
        if (pose != null) {
            setRotation(pose.yaw(), pose.pitch());
            setPosition(pose.pos());
            // "Solta": o jogador aparece na imagem.
            detached = true;
        }
    }
}
