// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Shibuya's traffic light (Stark 2026-11-20: the old one was a black cube with a red cube stuck to it): Tokyo's flat signal,
// three lenses in a row under visors (green, yellow, red from the driver's left), hanging from the arm above by a rod.
// FACING is where the lenses look. The block itself is all dark; the lit lens is drawn by the client
// (client/TrafficLightRenderer) from the world's clock, so every signal in the city changes together with no block
// update, no tick and no chunk rebuild: the ones along the x streets (facing east/west) and the ones along the z
// streets are never green at the same time.
public class TrafficLightBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // a full cycle, in ticks: 12 s green, 3 s yellow, 15 s red
    public static final int CYCLE = 600, GREEN = 240, YELLOW = 60;
    // the lenses' middles across the housing (model px, facing north): red, yellow, green
    public static final float[] LENS_X = {1.3f, 8f, 14.7f};
    private static final VoxelShape SHAPE = Block.box(0, 3, 0, 16, 16, 16);

    public TrafficLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // 0 green, 1 yellow, 2 red, for a signal facing that way at that game time
    public static int light(long time, Direction facing) {
        long t = Math.floorMod(time + (facing.getAxis() == Direction.Axis.Z ? CYCLE / 2 : 0), CYCLE);
        return t < GREEN ? 0 : t < GREEN + YELLOW ? 1 : 2;
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrafficLightBlockEntity(pos, state);
    }
}
