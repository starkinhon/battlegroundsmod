package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Holds nothing saved: the client keeps here the next power line its cable runs to (client/PowerLineRenderer).
public class PowerLineBlockEntity extends BlockEntity {
    public BlockPos cableTo;
    public long nextLook;

    public PowerLineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.POWER_LINE.get(), pos, state);
    }
}
