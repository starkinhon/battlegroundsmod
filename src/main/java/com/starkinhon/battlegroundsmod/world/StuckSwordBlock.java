// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Kubikiribocho stuck point down in the swordsman's grave of the Mar da Nevoa (Stark 2026-11-22: the old sword of
// iron blocks was ugly and the mission confused players; "pega o model da espada e fica fixada no chão"). The lower half
// draws the whole sword, the item's own 3D mesh (Stark's, #529) turned blade down (models/block/kubikiribocho_fincada.obj,
// made by tools/structures/stuck_sword.py from models/mesh/kubikiribocho.bin); the upper half draws nothing and is only there so a
// click on the hilt hits a block. NarutoActivities.sword takes both halves as the sword's marks. Unbreakable: the mission
// removes it and puts it back. No neighbour updates (a template places it with no updates).
public class StuckSwordBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StuckSwordBlock> CODEC = simpleCodec(StuckSwordBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    // the blade lies across x when FACING is north/south (flat side to north and south)
    private static final VoxelShape LOWER_NS = Block.box(3.5, 0, 6.5, 12.5, 16, 9.5);
    private static final VoxelShape LOWER_EW = Block.box(6.5, 0, 3.5, 9.5, 16, 12.5);
    private static final VoxelShape UPPER_NS = Block.box(3.5, 0, 6.5, 12.5, 16, 9.5);
    private static final VoxelShape UPPER_EW = Block.box(6.5, 0, 3.5, 9.5, 16, 12.5);

    public StuckSwordBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return ns ? LOWER_NS : LOWER_EW;
        }
        return ns ? UPPER_NS : UPPER_EW;
    }
}
