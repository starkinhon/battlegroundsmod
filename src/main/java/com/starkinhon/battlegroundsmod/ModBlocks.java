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

    // The Deserto do Diabo (Stark 2026-11-11, after his Sand Land reference): warm orange-yellow sand that falls like sand,
    // and the sandstone rock of its flats and towers, darker and lighter (tools/blocks/desert_blocks.py).
    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> DESERT_SAND = BLOCKS.register("deserto_areia",
            () -> new net.minecraft.world.level.block.ColoredFallingBlock(new net.minecraft.util.ColorRGBA(0xE2AC62),
                    BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.SAND)));
    public static final DeferredBlock<Block> DESERT_ROCK = rock("deserto_rocha", MapColor.TERRACOTTA_ORANGE);
    public static final DeferredBlock<Block> DESERT_ROCK_LIGHT = rock("deserto_rocha_clara", MapColor.SAND);

    static {
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_ROCK);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_SAND);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_ROCK);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_ROCK_LIGHT);
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
