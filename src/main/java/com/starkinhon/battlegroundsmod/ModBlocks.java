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

    // King Kai's little planet (Stark 2026-11-13, after his anime frames; tools/blocks/kaioh_blocks.py): the lawn one flat
    // cartoon green on every face so the sphere reads as a smooth ball, the road's pale flagstones, the houses' orange
    // plaster and its light cap.
    public static final DeferredBlock<Block> KAIOH_GRASS = BLOCKS.registerSimpleBlock("kaioh_grama",
            BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6f).sound(SoundType.GRASS));
    public static final DeferredBlock<Block> KAIOH_ROAD = rock("kaioh_lajota", MapColor.QUARTZ);
    public static final DeferredBlock<Block> KAIOH_HOUSE = rock("kaioh_casa", MapColor.COLOR_ORANGE);
    public static final DeferredBlock<Block> KAIOH_HOUSE_LIGHT = rock("kaioh_casa_clara", MapColor.COLOR_YELLOW);

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
    // Deserto do Vento (Stark 2026-11-11): the dunes' smooth sand, in eighths of a block like snow layers (no random
    // ticks, so it never melts away), and the dry grass between the rocks.
    public static final DeferredBlock<net.minecraft.world.level.block.SnowLayerBlock> SAND_LAYER = BLOCKS.register("camada_de_areia",
            () -> new net.minecraft.world.level.block.SnowLayerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .replaceable().forceSolidOff().strength(0.5f).sound(SoundType.SAND)
                    .isViewBlocking((state, level, pos) -> state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 8)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.ThinPlantBlock> DRY_GRASS = BLOCKS.register("capim_seco",
            () -> new com.starkinhon.battlegroundsmod.world.ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .replaceable().noCollission().noOcclusion().instabreak().sound(SoundType.GRASS).pushReaction(
                            net.minecraft.world.level.material.PushReaction.DESTROY),
                    Block.box(2, 0, 2, 14, 13, 14)));

    // Mar da Névoa (Stark 2026-11-11): the coastal pines' needles in two tones (the pads' tops and their dark undersides),
    // and the rope of the bridges, a thin cord on an axis like the chain.
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> PINE_LEAVES = leaves("folhas_de_pinheiro");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> PINE_LEAVES_DARK = leaves("folhas_de_pinheiro_escura");
    public static final DeferredBlock<net.minecraft.world.level.block.ChainBlock> ROPE = BLOCKS.register("corda",
            () -> new net.minecraft.world.level.block.ChainBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion().ignitedByLava()));

    // The Naruto structures (Stark 2026-11-12, world/NarutoSites): the paper seals stuck on rocks and altars, and the
    // chakra threads the puppets of the Puppeteer's Workshop hang from (a faint blue glow, nothing to bump into).
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.PaperSealBlock> PAPER_SEAL = BLOCKS.register("selo_de_papel",
            () -> new com.starkinhon.battlegroundsmod.world.PaperSealBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.3f).sound(SoundType.WOOL).noCollission().noOcclusion().ignitedByLava()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredBlock<net.minecraft.world.level.block.ChainBlock> CHAKRA_THREAD = BLOCKS.register("fio_de_chakra",
            () -> new net.minecraft.world.level.block.ChainBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.2f).sound(SoundType.WOOL).noCollission().noOcclusion().lightLevel(state -> 6)));

    // Vila da Chuva (Stark 2026-11-11): the towers' dark metal in panels, the reinforcing rings, rust in two tones, the
    // windows (dark, and lit amber at night), the pipes and the crown's spikes.
    public static final DeferredBlock<Block> RAIN_METAL = metal("metal_da_chuva", 0);
    public static final DeferredBlock<Block> RAIN_METAL_DARK = metal("metal_da_chuva_escuro", 0);
    public static final DeferredBlock<Block> RAIN_RIB = metal("anel_de_metal", 0);
    public static final DeferredBlock<Block> RUST = metal("ferrugem", 0);
    public static final DeferredBlock<Block> RUST_LIGHT = metal("ferrugem_clara", 0);
    public static final DeferredBlock<Block> RAIN_WINDOW = metal("janela_escura", 0);
    public static final DeferredBlock<Block> RAIN_WINDOW_LIT = metal("janela_acesa", 12);
    public static final DeferredBlock<Block> RAIN_PIPE = metal("cano_de_metal", 0);
    public static final DeferredBlock<Block> RAIN_SPIKE = metal("espeto_de_metal", 0);
    // Vale do Fim: the two statues' weathered stone (light, darker, mossy, and the dark of the belt and face shadows)
    public static final DeferredBlock<Block> STATUE_STONE = rock("pedra_da_estatua", MapColor.STONE);
    public static final DeferredBlock<Block> STATUE_STONE_DARK = rock("pedra_da_estatua_escura", MapColor.STONE);
    public static final DeferredBlock<Block> STATUE_STONE_MOSS = rock("pedra_da_estatua_musgo", MapColor.COLOR_GREEN);
    public static final DeferredBlock<Block> STATUE_STONE_SHADOW = rock("pedra_da_estatua_sombra", MapColor.COLOR_GRAY);
    // Monte Myoboku: the spires' warm stone in three layers, and the giant plants (stalk, leaves, petals, the core)
    public static final DeferredBlock<Block> SPIRE_STONE = rock("pedra_do_pico", MapColor.TERRACOTTA_LIGHT_GRAY);
    public static final DeferredBlock<Block> SPIRE_STONE_DARK = rock("pedra_do_pico_escura", MapColor.TERRACOTTA_GRAY);
    public static final DeferredBlock<Block> SPIRE_STONE_LIGHT = rock("pedra_do_pico_clara", MapColor.SAND);
    public static final DeferredBlock<Block> GIANT_STALK = plant("caule_gigante", MapColor.COLOR_GREEN, 0, 1.0f);
    public static final DeferredBlock<Block> GIANT_LEAF = plant("folha_gigante", MapColor.PLANT, 0, 0.3f);
    public static final DeferredBlock<Block> GIANT_LEAF_YELLOW = plant("folha_gigante_amarela", MapColor.COLOR_YELLOW, 0, 0.3f);
    public static final DeferredBlock<Block> GIANT_LEAF_DARK = plant("folha_gigante_escura", MapColor.COLOR_GREEN, 0, 0.3f);
    public static final DeferredBlock<Block> GIANT_PETAL = plant("petala_gigante", MapColor.COLOR_ORANGE, 0, 0.3f);
    public static final DeferredBlock<Block> GIANT_PETAL_TIP = plant("petala_gigante_ponta", MapColor.COLOR_YELLOW, 0, 0.3f);
    public static final DeferredBlock<Block> FLOWER_CORE = plant("miolo_de_flor", MapColor.GOLD, 7, 0.4f);
    // Templos Antigos (Jujutsu, Stark 2026-11-12): the temples' vermilion lacquered wood (posts and torii), the torii's
    // black lacquer top beam, the courtyard's raked white gravel, the shoji lattice with its paper, the white plaster,
    // the stone lantern's lit fire box, the shimenawa rope and its zigzag paper strips (shide); the cedars' leaves in two
    // greens and the sakuras' in three more tones besides vanilla's cherry (they shed petals like it).
    public static final DeferredBlock<Block> VERMILION_WOOD = BLOCKS.registerSimpleBlock("madeira_vermelha",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<Block> BLACK_LACQUER = BLOCKS.registerSimpleBlock("laca_preta",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<Block> WHITE_GRAVEL = BLOCKS.registerSimpleBlock("cascalho_branco",
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.6f).sound(SoundType.GRAVEL));
    public static final DeferredBlock<Block> SHOJI = BLOCKS.registerSimpleBlock("shoji",
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<Block> PLASTER = BLOCKS.registerSimpleBlock("reboco",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).requiresCorrectToolForDrops().strength(1.2f, 4.0f)
                    .sound(SoundType.STONE));
    public static final DeferredBlock<Block> STONE_LANTERN_LIGHT = BLOCKS.registerSimpleBlock("lanterna_de_pedra_acesa",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5f, 6.0f)
                    .sound(SoundType.STONE).lightLevel(state -> 15));
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.ThinPlantBlock> SHIMENAWA = BLOCKS.register("shimenawa",
            () -> new com.starkinhon.battlegroundsmod.world.ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0.8f).sound(SoundType.WOOL).noOcclusion().ignitedByLava(), Block.box(0, 5, 0, 16, 11, 16)));
    public static final DeferredBlock<com.starkinhon.battlegroundsmod.world.ThinPlantBlock> SHIDE = BLOCKS.register("shide",
            () -> new com.starkinhon.battlegroundsmod.world.ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .noCollission().noOcclusion().instabreak().sound(SoundType.WOOL).pushReaction(
                            net.minecraft.world.level.material.PushReaction.DESTROY), Block.box(5, 2, 5, 11, 16, 11)));
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> CEDAR_LEAVES = leaves("folhas_de_cedro");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> CEDAR_LEAVES_LIGHT = leaves("folhas_de_cedro_clara");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> SAKURA_LEAVES_DARK = sakura("folhas_de_sakura_escura");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> SAKURA_LEAVES_LIGHT = sakura("folhas_de_sakura_clara");
    public static final DeferredBlock<net.minecraft.world.level.block.LeavesBlock> SAKURA_LEAVES_WHITE = sakura("folhas_de_sakura_branca");

    static {
        BLOCK_ITEMS.registerSimpleBlockItem(VERMILION_WOOD);
        BLOCK_ITEMS.registerSimpleBlockItem(BLACK_LACQUER);
        BLOCK_ITEMS.registerSimpleBlockItem(WHITE_GRAVEL);
        BLOCK_ITEMS.registerSimpleBlockItem(SHOJI);
        BLOCK_ITEMS.registerSimpleBlockItem(PLASTER);
        BLOCK_ITEMS.registerSimpleBlockItem(STONE_LANTERN_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(SHIMENAWA);
        BLOCK_ITEMS.registerSimpleBlockItem(SHIDE);
        BLOCK_ITEMS.registerSimpleBlockItem(CEDAR_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(CEDAR_LEAVES_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(SAKURA_LEAVES_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(SAKURA_LEAVES_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(SAKURA_LEAVES_WHITE);
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
        BLOCK_ITEMS.registerSimpleBlockItem(SAND_LAYER);
        BLOCK_ITEMS.registerSimpleBlockItem(DRY_GRASS);
        BLOCK_ITEMS.registerSimpleBlockItem(PINE_LEAVES);
        BLOCK_ITEMS.registerSimpleBlockItem(PINE_LEAVES_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(ROPE);
        BLOCK_ITEMS.registerSimpleBlockItem(PAPER_SEAL);
        BLOCK_ITEMS.registerSimpleBlockItem(CHAKRA_THREAD);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_METAL);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_METAL_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_RIB);
        BLOCK_ITEMS.registerSimpleBlockItem(RUST);
        BLOCK_ITEMS.registerSimpleBlockItem(RUST_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_WINDOW);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_WINDOW_LIT);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_PIPE);
        BLOCK_ITEMS.registerSimpleBlockItem(RAIN_SPIKE);
        BLOCK_ITEMS.registerSimpleBlockItem(STATUE_STONE);
        BLOCK_ITEMS.registerSimpleBlockItem(STATUE_STONE_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(STATUE_STONE_MOSS);
        BLOCK_ITEMS.registerSimpleBlockItem(STATUE_STONE_SHADOW);
        BLOCK_ITEMS.registerSimpleBlockItem(SPIRE_STONE);
        BLOCK_ITEMS.registerSimpleBlockItem(SPIRE_STONE_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(SPIRE_STONE_LIGHT);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_STALK);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_LEAF);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_LEAF_YELLOW);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_LEAF_DARK);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_PETAL);
        BLOCK_ITEMS.registerSimpleBlockItem(GIANT_PETAL_TIP);
        BLOCK_ITEMS.registerSimpleBlockItem(FLOWER_CORE);
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
        BLOCK_ITEMS.registerSimpleBlockItem(KAIOH_GRASS);
        BLOCK_ITEMS.registerSimpleBlockItem(KAIOH_ROAD);
        BLOCK_ITEMS.registerSimpleBlockItem(KAIOH_HOUSE);
        BLOCK_ITEMS.registerSimpleBlockItem(KAIOH_HOUSE_LIGHT);
    }

    private ModBlocks() {
    }

    private static DeferredBlock<net.minecraft.world.level.block.LeavesBlock> leaves(String name) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.LeavesBlock(
                BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_LEAVES)));
    }

    // The sakuras' extra tones: vanilla's cherry leaves (so they shed the same falling petals).
    private static DeferredBlock<net.minecraft.world.level.block.LeavesBlock> sakura(String name) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.CherryLeavesBlock(
                BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.CHERRY_LEAVES)));
    }

    private static DeferredBlock<Block> flesh(String name, MapColor color) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color).strength(0.4f).sound(SoundType.WART_BLOCK));
    }

    private static DeferredBlock<net.minecraft.world.level.block.CarpetBlock> carpet(String name) {
        return BLOCKS.register(name, () -> new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT).strength(0.1f).sound(SoundType.GRASS).noOcclusion().pushReaction(
                        net.minecraft.world.level.material.PushReaction.DESTROY)));
    }

    private static DeferredBlock<Block> metal(String name, int light) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(4.0f, 6.0f).sound(SoundType.METAL).lightLevel(state -> light));
    }

    // The giant plants of Myoboku: soft plant tissue (axe/hoe, grass sound), the stalk a bit harder.
    private static DeferredBlock<Block> plant(String name, MapColor color, int light, float strength) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color).strength(strength)
                .sound(SoundType.GRASS).lightLevel(state -> light));
    }

    private static DeferredBlock<Block> rock(String name, MapColor color) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.STONE));
    }

    public static void register(IEventBus bus) {
        JujutsuBlocks.init();
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
    }
}
