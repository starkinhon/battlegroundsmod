package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// The power line (Stark 2026-11-14: "cria um bloco que eh fio, como se fosse fio de rede eletrica"): a crossarm on a
// pole, a thin bar reaching to the pole it faces, with a white insulator on top. With "cabo" on, the client draws a real
// sagging cable from its insulator to the next power line along the street (client/PowerLineRenderer), so the wires are
// smooth curves between the poles instead of blocks. Without the cable it is just an arm (a lamp's, a traffic light's).
public class PowerLineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CABLE = BooleanProperty.create("cabo");
    public static final BooleanProperty REACH = BooleanProperty.create("alcanca");
    private static final VoxelShape SHAPE = Block.box(4, 9, 4, 12, 16, 12);

    public PowerLineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CABLE, true).setValue(REACH, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CABLE, REACH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerLineBlockEntity(pos, state);
    }
}
