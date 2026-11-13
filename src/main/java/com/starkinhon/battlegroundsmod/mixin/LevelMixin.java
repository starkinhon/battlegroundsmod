package com.starkinhon.battlegroundsmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Izanami;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// Every block change goes through here: the arenas keep their invisible wall and bedrock floor whatever a skill
// destroys, and the Izanami keeps the old block so it can put it back.
@Mixin(Level.class)
public abstract class LevelMixin {
    @ModifyVariable(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), argsOnly = true)
    private BlockState battlegroundsmod$arenaRing(BlockState state, BlockPos pos, BlockState original, int flags, int recursionLeft) {
        return ChallengeArenas.ringFill((Level) (Object) this, pos, state);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void battlegroundsmod$beforeSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (ChallengeArenas.protectedBlock((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
            return;
        }
        Izanami.beforeSetBlock((Level) (Object) this, pos);
    }
}
