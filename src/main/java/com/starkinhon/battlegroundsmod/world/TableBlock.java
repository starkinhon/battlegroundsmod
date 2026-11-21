// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// A table whose top is a full block high (Stark 2026-11-21, casa do Goku): the old fence + pressure plate table left a
// flower pot floating over the thin plate. Here the top's upper face is at 16/16, so a pot, a candle or a lantern placed
// on it stands right on the wood. Tables side by side join: the legs only stay on the corners with no table next to them,
// so a 2x2 is one table with four legs. Structures carry the north/east/south/west values in the .nbt (no neighbour
// updates when a template is placed).
public class TableBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape TOP = Block.box(0, 14, 0, 16, 16, 16);
    private static final VoxelShape LEG_NW = Block.box(1, 0, 1, 3, 14, 3);
    private static final VoxelShape LEG_NE = Block.box(13, 0, 1, 15, 14, 3);
    private static final VoxelShape LEG_SE = Block.box(13, 0, 13, 15, 14, 15);
    private static final VoxelShape LEG_SW = Block.box(1, 0, 13, 3, 14, 15);

    public TableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(property(d), level.getBlockState(pos.relative(d)).getBlock() instanceof TableBlock);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), neighborState.getBlock() instanceof TableBlock);
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = TOP;
        boolean n = state.getValue(NORTH), e = state.getValue(EAST), s = state.getValue(SOUTH), w = state.getValue(WEST);
        if (!n && !w) shape = Shapes.or(shape, LEG_NW);
        if (!n && !e) shape = Shapes.or(shape, LEG_NE);
        if (!s && !e) shape = Shapes.or(shape, LEG_SE);
        if (!s && !w) shape = Shapes.or(shape, LEG_SW);
        return shape;
    }

    // A rotated or mirrored template keeps the joins on the right sides.
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(property(rotation.rotate(d)), state.getValue(property(d)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(property(mirror.mirror(d)), state.getValue(property(d)));
        }
        return out;
    }

    private static BooleanProperty property(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }
}
