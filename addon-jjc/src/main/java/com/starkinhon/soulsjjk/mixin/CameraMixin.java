package com.starkinhon.soulsjjk.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.soulsjjk.client.BossCutscene;
import com.starkinhon.soulsjjk.client.EsferaCutscene;
import com.starkinhon.soulsjjk.client.FaceCutscene;
import com.starkinhon.soulsjjk.client.BopCutscene;
import com.starkinhon.soulsjjk.client.GrabCutscene;
import com.starkinhon.soulsjjk.client.HakaiCutscene;
import com.starkinhon.soulsjjk.client.ScreenShake;
import com.starkinhon.soulsjjk.client.ShoulderCutscene;
import com.starkinhon.soulsjjk.client.cutscene.CutscenePlayer;

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
    private void soulsjjk$cutscene(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        // A cutscene from the Editor de Cutscenes has the camera to itself (null between its stretches: the player's view).
        // Sukuna's Expansão de Domínio holds the camera of everyone caught in it while its cutscene plays.
        // The Duelo de Expansões holds the camera of the two (and of those watching inside) on their faces.
        FaceCutscene.Shot shot = com.starkinhon.soulsjjk.client.fx.DuelScene.cutscene() ? com.starkinhon.soulsjjk.client.fx.DuelScene.shot(partialTick)
                : com.starkinhon.soulsjjk.client.fx.SukunaDomainScene.cutscene() ? com.starkinhon.soulsjjk.client.fx.SukunaDomainScene.shot(partialTick)
                : CutscenePlayer.active() ? CutscenePlayer.shot(partialTick)
                : BossCutscene.active() ? BossCutscene.shot(partialTick)
                : HakaiCutscene.active() ? HakaiCutscene.shot(partialTick)
                : EsferaCutscene.active() ? EsferaCutscene.shot(partialTick)
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
        float[] domainShake = com.starkinhon.soulsjjk.client.fx.SukunaDomainScene.shake();
        if (domainShake != null) {
            Camera camera = (Camera) (Object) this;
            setRotation(camera.getYRot() + domainShake[0], camera.getXRot() + domainShake[1]);
        }
    }
}
