package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

// Cápsula Hoi-Poi (Dragon Ball): Bulma builds one at Capsule Corp from iron and redstone (DbActivities.bulma). Used on
// the ground it pops in a cloud into a little Capsule Corp dome house in front of you: white walls with a blue band and
// round windows, a door facing you, a bed, a crafting table, a furnace, a chest and lights. Only where there is room
// (nothing solid in the way but plants and snow); the capsule is spent.
public class HoiPoiCapsuleItem extends Item {
    private static final double RADIUS = 4.6;

    public HoiPoiCapsuleItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Direction facing = player.getDirection();
        BlockPos floor = context.getClickedPos().relative(facing, 5);
        if (!level.getBlockState(context.getClickedPos()).isSolid() || context.getClickedFace() != Direction.UP) {
            player.displayClientMessage(Component.translatable("item.battlegroundsmod.capsula_hoipoi.chao"), true);
            return InteractionResult.FAIL;
        }
        if (!room(level, floor)) {
            player.displayClientMessage(Component.translatable("item.battlegroundsmod.capsula_hoipoi.sem_espaco"), true);
            return InteractionResult.FAIL;
        }
        build(level, floor, facing);
        Fx.burst(level, ParticleTypes.CLOUD, net.minecraft.world.phys.Vec3.atCenterOf(floor.above(2)), 160, 2.6, 0.12);
        Fx.burst(level, ParticleTypes.POOF, net.minecraft.world.phys.Vec3.atCenterOf(floor.above(2)), 60, 2.2, 0.08);
        Fx.sound(level, net.minecraft.world.phys.Vec3.atCenterOf(floor), SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1.4f);
        Fx.sound(level, net.minecraft.world.phys.Vec3.atCenterOf(floor), SoundEvents.PISTON_EXTEND, 0.8f, 1.6f);
        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    // Nothing solid inside the dome's space (plants, snow and the like are swept away).
    private static boolean room(ServerLevel level, BlockPos floor) {
        int r = (int) Math.ceil(RADIUS);
        int solid = 0;
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                if (i * i + k * k > RADIUS * RADIUS) {
                    continue;
                }
                for (int j = 1; j <= r; j++) {
                    BlockPos p = floor.offset(i, j, k);
                    BlockState st = level.getBlockState(p);
                    if (!st.canBeReplaced() && !st.isAir()) {
                        solid++;
                    }
                }
            }
        }
        return solid <= 6;
    }

    private static void put(ServerLevel level, BlockPos p, BlockState state) {
        level.setBlock(p, state, Block.UPDATE_ALL);
    }

    private static void build(ServerLevel level, BlockPos floor, Direction facing) {
        int r = (int) Math.ceil(RADIUS);
        BlockState shell = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState band = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        // floor: a disc of smooth stone (and a foot under it where the ground dips)
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                double d = Math.sqrt(i * i + k * k);
                if (d > RADIUS) {
                    continue;
                }
                put(level, floor.offset(i, 0, k), d > RADIUS - 1.2 ? shell : Blocks.SMOOTH_STONE.defaultBlockState());
                for (int j = -1; j >= -3 && level.getBlockState(floor.offset(i, j, k)).canBeReplaced(); j--) {
                    put(level, floor.offset(i, j, k), Blocks.STONE.defaultBlockState());
                }
            }
        }
        // the dome: a band and round windows around it
        for (int i = -r; i <= r; i++) {
            for (int j = 1; j <= r; j++) {
                for (int k = -r; k <= r; k++) {
                    double d = Math.sqrt(i * i + (j - 0.5) * (j - 0.5) + k * k);
                    BlockPos p = floor.offset(i, j, k);
                    if (d <= RADIUS && d > RADIUS - 1.25) {
                        BlockState st = shell;
                        if (j == 1) {
                            st = band;
                        } else if (j == 2 && Math.floorMod(i * 3 + k * 5, 4) == 0) {
                            st = glass;
                        }
                        put(level, p, st);
                    } else if (d <= RADIUS - 1.25) {
                        put(level, p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        put(level, floor.above(r), Blocks.SEA_LANTERN.defaultBlockState());
        // the door, facing the player
        Direction toPlayer = facing.getOpposite();
        BlockPos door = floor.relative(toPlayer, r - 1).above();
        for (int s = 0; s <= 1; s++) {
            BlockPos out = floor.relative(toPlayer, r - s).above();
            put(level, out, Blocks.AIR.defaultBlockState());
            put(level, out.above(), Blocks.AIR.defaultBlockState());
        }
        BlockState d = Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        put(level, door, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, door.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // inside: the bed at the back, a table, a furnace and a chest along the sides, lanterns
        Direction left = facing.getCounterClockWise(), right = facing.getClockWise();
        BlockPos back = floor.relative(facing, 2).above();
        // a bed's facing points from its foot to its head: the head at the back, the foot toward the door
        BlockState bed = Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        put(level, back.relative(left, 1), bed.setValue(BedBlock.PART, BedPart.HEAD));
        put(level, back.relative(left, 1).relative(toPlayer), bed.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, back.relative(right, 1), Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, toPlayer));
        put(level, floor.above().relative(right, 2), Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level, floor.above().relative(right, 2).relative(toPlayer), Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, left));
        put(level, floor.above().relative(left, 2).relative(toPlayer), Blocks.LANTERN.defaultBlockState());
        put(level, floor.above().relative(right, 2).relative(facing), Blocks.LANTERN.defaultBlockState());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.capsula_hoipoi.desc").withStyle(s -> s.withColor(0x8FD3FF)));
    }
}
