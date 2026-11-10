package com.starkinhon.battlegroundsmod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// Blocks for the look of the universes that vanilla has no color for (Stark, 2026-11-10: Namek's pink-beige cliffs).
// Their items have their own register, so no creative tab lists them: they are for building the worlds, not for players.
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BattlegroundsMod.MODID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(BattlegroundsMod.MODID);

    // Namek's cliffs and mesas: pale pink-beige rock, and a darker band for the layers.
    public static final DeferredBlock<Block> NAMEK_ROCK = rock("namek_rock", MapColor.TERRACOTTA_WHITE);
    public static final DeferredBlock<Block> NAMEK_ROCK_LAYER = rock("namek_rock_layer", MapColor.TERRACOTTA_PINK);
    // The tan patches of bare ground between the blue grass.
    public static final DeferredBlock<Block> NAMEK_SAND = BLOCKS.registerSimpleBlock("namek_sand",
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.6f).sound(SoundType.GRAVEL));

    static {
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_ROCK);
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_ROCK_LAYER);
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_SAND);
    }

    private ModBlocks() {
    }

    private static DeferredBlock<Block> rock(String name, MapColor color) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.STONE));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
    }
}
