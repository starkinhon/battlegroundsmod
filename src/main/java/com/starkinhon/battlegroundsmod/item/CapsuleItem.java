package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

// Cápsula Hoi-Poi (Dragon Ball): click the ground and, in a puff of smoke, a small Capsule Corp house pops up there
// with its door facing the player: bed, crafting table, furnace, chest and light. A base anywhere, at once.
public class CapsuleItem extends Item {
    private static final int HALF = 3;
    private static final int HEIGHT = 4;

    public CapsuleItem(Properties properties) {
        super(properties.stacksTo(4));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        Direction front = context.getPlayer().getDirection().getOpposite();
        BlockPos center = context.getClickedPos().above().relative(front.getOpposite(), HALF + 1);
        build(level, center, front);
        Fx.burst(level, ParticleTypes.CLOUD, center.getCenter().add(0, 1.5, 0), 160, 3, 0.05);
        Fx.burst(level, ParticleTypes.POOF, center.getCenter().add(0, 1, 0), 60, 3, 0.05);
        Fx.sound(level, center.getCenter(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.7f);
        if (!context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    // Local coordinates: x across (right), z depth (front = -HALF, back = +HALF), y up from the floor.
    private static void build(ServerLevel level, BlockPos center, Direction front) {
        Direction right = front.getCounterClockWise();
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState roof = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                for (int y = 0; y <= HEIGHT + 1; y++) {
                    boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                    BlockState state;
                    if (y == 0) {
                        state = floor;
                    } else if (y == HEIGHT + 1) {
                        state = roof;
                    } else if (edge) {
                        boolean window = y == 2 && (Math.abs(x) == 1 && Math.abs(z) == HALF || Math.abs(z) == 1 && Math.abs(x) == HALF);
                        state = window ? glass : wall;
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(level, center, front, right, x, y, z, state);
                }
            }
        }
        // Door in the middle of the front wall.
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, front.getOpposite());
        set(level, center, front, right, 0, 1, -HALF, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, center, front, right, 0, 2, -HALF, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, center, front, right, 1, 1, -HALF - 1, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, front));
        set(level, center, front, right, 1, 1, -HALF + 1, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, front.getOpposite()));
        // Bed along the back wall, head against it.
        BlockState bed = Blocks.ORANGE_BED.defaultBlockState().setValue(BedBlock.FACING, front.getOpposite());
        set(level, center, front, right, -2, 1, HALF - 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
        set(level, center, front, right, -2, 1, HALF - 2, bed.setValue(BedBlock.PART, BedPart.FOOT));
        set(level, center, front, right, 2, 1, HALF - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, center, front, right, 2, 1, HALF - 2, Blocks.FURNACE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, front));
        set(level, center, front, right, 0, 1, HALF - 1, Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, front));
        set(level, center, front, right, 0, HEIGHT, 0, Blocks.LANTERN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    private static void set(ServerLevel level, BlockPos center, Direction front, Direction right, int x, int y, int z, BlockState state) {
        BlockPos pos = center.relative(right, x).above(y).relative(front, -z);
        if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0) {
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.capsula_hoipoi.desc").withStyle(s -> s.withColor(0x8FD3FF)));
    }
}
