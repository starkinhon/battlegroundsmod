package com.starkinhon.battlegroundsmod.mixin;

import com.starkinhon.battlegroundsmod.universe.EnergyControl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Controle de Energia (universe/EnergyControl): the water's top holds up a player who learned it.
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void souls$walkOnWater(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape shape = EnergyControl.waterShape(state, level, pos, context);
        if (shape != null) {
            cir.setReturnValue(shape);
        }
    }
}
