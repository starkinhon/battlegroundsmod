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

    // The Floresta da Folha (Stark 2026-11-11, the approved preview): the leaves of its trees in three tones of their own
    // (the cluster's light top, the middle, the dark underside), not tinted by the biome, so the forest looks in the game
    // as in the preview; and the fallen leaves lying under the crowns (tools/blocks/leaf_forest_blocks.py).
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> LEAF_LEAVES = leaves("folhas_da_folha");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> LEAF_LEAVES_LIGHT = leaves("folhas_da_folha_clara");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> LEAF_LEAVES_DARK = leaves("folhas_da_folha_escura");
    public static final DeferredBlock<net.minecraft.world.level.block.CarpetBlock> FALLEN_LEAVES = BLOCKS.register("folhas_caidas",
            () -> new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .strength(0.1f).sound(SoundType.GRASS).noOcclusion().pushReaction(
                            net.minecraft.world.level.material.PushReaction.DESTROY)));

    // The Floresta da Morte (Stark 2026-11-11, the approved preview; tools/blocks/death_forest_blocks.py): its dark leaves in
    // three tones, the thick lianas, the vines hanging from the leaves (climbable), the giant mushrooms' rust-red cap, the
    // carnivorous flower's petals and mouth, the rotten leaves and bare roots on the ground, and the small mushrooms that
    // glow faintly in the dark.
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> DEATH_LEAVES = leaves("folhas_da_morte");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> DEATH_LEAVES_LIGHT = leaves("folhas_da_morte_clara");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> DEATH_LEAVES_DARK = leaves("folhas_da_morte_escura");
    public static final DeferredBlock<Block> LIANA = BLOCKS.registerSimpleBlock("cipo",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.0f).sound(SoundType.WOOD));
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.ThinPlantBlock> HANGING_VINE = BLOCKS.register("trepadeira",
            () -> new com.starkinhon.battlegroundsmod.world.ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .noCollission().noOcclusion().instabreak().sound(SoundType.VINE).pushReaction(
                            net.minecraft.world.level.material.PushReaction.DESTROY),
                    Block.box(4, 0, 4, 12, 16, 12)));
    public static final DeferredBlock<Block> MUSHROOM_CAP = BLOCKS.registerSimpleBlock("chapeu_cogumelo",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2f).sound(SoundType.WOOD));
    public static final DeferredBlock<Block> PETAL = flesh("petala_carnivora", MapColor.CRIMSON_STEM);
    public static final DeferredBlock<Block> PETAL_SPOTTED = flesh("petala_carnivora_pintada", MapColor.CRIMSON_STEM);
    public static final DeferredBlock<Block> MAW = flesh("boca_carnivora", MapColor.COLOR_BLACK);
    public static final DeferredBlock<net.minecraft.world.level.block.CarpetBlock> ROTTEN_LEAVES = carpet("folhas_podres");
    public static final DeferredBlock<net.minecraft.world.level.block.CarpetBlock> BARE_ROOTS = carpet("raizes_expostas");
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.ThinPlantBlock> GLOW_MUSHROOM = BLOCKS.register("cogumelo_brilhante",
            () -> new com.starkinhon.battlegroundsmod.world.ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .noCollission().noOcclusion().instabreak().sound(SoundType.FUNGUS).lightLevel(state -> 6).pushReaction(
                            net.minecraft.world.level.material.PushReaction.DESTROY),
                    Block.box(2, 0, 2, 14, 10, 14)));

    static {
        BLOCK_ITEMS.registerSimpleBlockItem(DEATH_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(DEATH_LEAVES_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(DEATH_LEAVES_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(LIANA);
        BLOCK_ITEMS.registerSimpleBlockItem(HANGING_VINE);
        BLOCK_ITEMS.registerSimpleBlockItem(MUSHROOM_CAP);
        BLOCK_ITEMS.registerSimpleBlockItem(PETAL);
        BLOCK_ITEMS.registerSimpleBlockItem(PETAL_SPOTTED);
        BLOCK_ITEMS.registerSimpleBlockItem(MAW);
        BLOCK_ITEMS.registerSimpleBlockItem(ROTTEN_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(BARE_ROOTS);
        BLOCK_ITEMS.registerSimpleBlockItem(GLOW_MUSHROOM);
        BLOCK_ITEMS.registerSimpleBlockItem(LEAF_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(LEAF_LEAVES_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(LEAF_LEAVES_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(FALLEN_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_ROCK);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_SAND);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_ROCK);
        BLOCK_ITEMS.registerSimpleBlockItem(DESERT_ROCK_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_ROCK_LAYER);
        BLOCK_ITEMS.registerSimpleBlockItem(NAMEK_SAND);
    }

    private ModBlocks() {
    }

    private static DeferredBlock<net.minecraft.world.level.block.LeavesBlock> leaves(String name) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.LeavesBlock(
                BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LEAVES)));
    }

    private static DeferredBlock<Block> flesh(String name, MapColor color) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color).strength(0.4f).sound(SoundType.WART_BLOCK));
    }

    private static DeferredBlock<net.minecraft.world.level.block.CarpetBlock> carpet(String name) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT).strength(0.1f).sound(SoundType.GRASS).noOcclusion().pushReaction(
                        net.minecraft.world.level.material.PushReaction.DESTROY)));
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
