package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.FaceCutscene;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

// Places the cutscene camera after vanilla is done, so the third person pull back can't move it again.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void battlegroundsmod$cutscene(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        FaceCutscene.Shot shot = FaceCutscene.shot(partialTick);
        if (shot != null) {
            setRotation(shot.yaw(), shot.pitch());
            setPosition(shot.position());
        }
    }
}
