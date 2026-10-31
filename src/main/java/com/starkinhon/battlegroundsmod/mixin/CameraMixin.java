package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.BossCutscene;
import com.starkinhon.battlegroundsmod.client.EsferaCutscene;
import com.starkinhon.battlegroundsmod.client.PlanetCutscene;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.BopCutscene;
import com.starkinhon.battlegroundsmod.client.GrabCutscene;
import com.starkinhon.battlegroundsmod.client.HakaiCutscene;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.client.ShoulderCutscene;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

// Places the cutscene camera after vanilla is done, so the third person pull back can't move it again.
// Screen shake is added on top.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void battlegroundsmod$cutscene(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        FaceCutscene.Shot shot = BossCutscene.active() ? BossCutscene.shot(partialTick)
                : HakaiCutscene.active() ? HakaiCutscene.shot(partialTick)
                : EsferaCutscene.active() ? EsferaCutscene.shot(partialTick)
                : PlanetCutscene.active() ? PlanetCutscene.shot(partialTick)
                : GrabCutscene.active() ? GrabCutscene.shot(partialTick)
                : BopCutscene.active() ? BopCutscene.shot(partialTick)
                : ShoulderCutscene.active() ? ShoulderCutscene.shot(partialTick) : FaceCutscene.shot(partialTick);
        if (shot != null) {
            setRotation(shot.yaw(), shot.pitch());
            setPosition(shot.position());
        }
        float[] shake = ScreenShake.offsets();
        if (shake != null) {
            Camera camera = (Camera) (Object) this;
            setRotation(camera.getYRot() + shake[0], camera.getXRot() + shake[1]);
        }
    }
}
