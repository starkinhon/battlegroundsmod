// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// An old tube TV with rabbit-ear antennas (Stark 2026-11-22: the TVs of the houses were a black block on a trapdoor top,
// floating). It stands on the floor or on a table (mesa_de_carvalho / mesa_de_cerejeira), the screen on its FACING side.
// Rotates and mirrors with the template through HorizontalDirectionalBlock.
public class TvBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TvBlock> CODEC = simpleCodec(TvBlock::new);
    private static final VoxelShape NS = Block.box(1, 0, 3, 15, 12, 15);
    private static final VoxelShape EW = Block.box(1, 0, 1, 13, 12, 15);
    private static final VoxelShape SOUTH = Block.box(1, 0, 1, 15, 12, 13);
    private static final VoxelShape WEST = Block.box(3, 0, 1, 15, 12, 15);

    public TvBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> EW;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NS;
        };
    }
}
