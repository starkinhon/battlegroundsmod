package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// A plant drawn as a cross that needs nothing to hold it (Floresta da Morte, Stark 2026-11-11): the vines hanging from
// the leaves in the air, the small glowing mushrooms. Vanilla's hanging plants break as soon as a neighbor changes when
// nothing solid is above them; these stay as the forest grew them. Thin outline, no collision (set in the properties).
public class ThinPlantBlock extends Block {
    private final VoxelShape shape;

    public ThinPlantBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
