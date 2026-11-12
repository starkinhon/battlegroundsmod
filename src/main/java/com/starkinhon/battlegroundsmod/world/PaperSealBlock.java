package com.starkinhon.battlegroundsmod.world;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// A paper seal (fūinjutsu) stuck on a face (Naruto structures, Stark 2026-11-12): the five seals of the Sealed Hideout,
// the "forbidden" seal on its boulder, the papers of the Chunin Exam's altar and gates. FACING = where the paper's front
// looks (away from what holds it). A strip of paper, no collision.
public class PaperSealBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(5, 1, 15, 11, 15, 16));
        SHAPES.put(Direction.SOUTH, Block.box(5, 1, 0, 11, 15, 1));
        SHAPES.put(Direction.EAST, Block.box(0, 1, 5, 1, 15, 11));
        SHAPES.put(Direction.WEST, Block.box(15, 1, 5, 16, 15, 11));
        SHAPES.put(Direction.UP, Block.box(5, 0, 1, 11, 1, 15));
        SHAPES.put(Direction.DOWN, Block.box(5, 15, 1, 11, 16, 15));
    }

    public PaperSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
