package com.starkinhon.battlegroundsmod;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.world.ThinPlantBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

// The blocks of the new Jujutsu biomes (Stark 2026-11-13, "pode portar tudo"), in the colors of the approved previews
// (forja/jujutsu-<bioma>). Textures, models, names and tags come from tools/blocks/jjk_blocks.py. Registered in
// ModBlocks' registers (loaded from ModBlocks.register), and like those their items are in no creative tab.
public final class JujutsuBlocks {
    private static final List<DeferredBlock<? extends Block>> ALL = new ArrayList<>();

    // ---- Floresta Negra: the black trees (bark, dark bark, roots, leaves in three tones), the black soil, the dead
    // grass, the eyes in the trunks, the cursed energy's veins, the Árvore Matriz's flesh and maw, the rotten torii's
    // wood, the hanging black vines and the purple glowing fungi.
    public static final DeferredBlock<Block> BLACK_WOOD = cube("madeira_negra", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> BLACK_WOOD_DARK = cube("madeira_negra_escura", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> BLACK_ROOT = cube("raiz_negra", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<LeavesBlock> BLACK_LEAVES = leaves("folhas_negras");
    public static final DeferredBlock<LeavesBlock> BLACK_LEAVES_MOSS = leaves("folhas_negras_musgo");
    public static final DeferredBlock<LeavesBlock> VIOLET_LEAVES = leaves("folhas_violeta");
    public static final DeferredBlock<Block> BLACK_SOIL = cube("terra_negra", MapColor.COLOR_BLACK, 0.6f, SoundType.ROOTED_DIRT, 0);
    public static final DeferredBlock<Block> DEAD_GRASS = cube("capim_morto", MapColor.TERRACOTTA_GRAY, 0.6f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> CURSED_EYE = cube("olho_amaldicoado", MapColor.COLOR_RED, 1.0f, SoundType.SLIME_BLOCK, 7);
    public static final DeferredBlock<Block> CURSED_VEIN = stone("veio_amaldicoado", MapColor.COLOR_PURPLE, 10);
    public static final DeferredBlock<Block> CURSED_FLESH = cube("carne_amaldicoada", MapColor.CRIMSON_NYLIUM, 1.0f, SoundType.SLIME_BLOCK, 0);
    public static final DeferredBlock<Block> MAW = cube("carne_escura", MapColor.COLOR_BLACK, 1.0f, SoundType.SLIME_BLOCK, 0);
    public static final DeferredBlock<Block> ROTTEN_RED_WOOD = cube("madeira_vermelha_podre", MapColor.CRIMSON_STEM, 1.5f, SoundType.WOOD, 0);
    public static final DeferredBlock<ThinPlantBlock> BLACK_VINE = plant("cipo_negro", MapColor.COLOR_BLACK, 0, SoundType.VINE,
            Block.box(2, 0, 2, 14, 16, 14));
    public static final DeferredBlock<ThinPlantBlock> CURSED_FUNGUS = plant("fungo_amaldicoado", MapColor.COLOR_PURPLE, 6,
            SoundType.FUNGUS, Block.box(2, 0, 2, 14, 10, 14));

    // ---- Aldeia Amaldiçoada: the soaked grass by the water, the farmhouses' woods (boards over the swamp, posts,
    // light and dark walls, the roof ridge, the burnt house), the thick thatch and the rotten one, the torn shoji, the
    // swamp cypress (bark, knees, two leaves, old man's beard), the reeds, the rice, the faded torii and the paddies' dikes.
    public static final DeferredBlock<Block> SOAKED_GRASS = cube("capim_encharcado", MapColor.GRASS, 0.6f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> SWAMP_BOARDS = cube("tabua_do_brejo", MapColor.WOOD, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> FARM_WOOD = cube("madeira_de_fazenda", MapColor.WOOD, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> FARM_WOOD_DARK = cube("madeira_de_fazenda_escura", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> SWAMP_POST = cube("poste_do_brejo", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> THATCH = cube("palha_de_telhado", MapColor.COLOR_YELLOW, 0.5f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> THATCH_ROTTEN = cube("palha_podre", MapColor.COLOR_BROWN, 0.5f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> ROOF_RIDGE = cube("cumeeira", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CHARRED_WOOD = cube("madeira_queimada", MapColor.COLOR_BLACK, 1.5f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> TORN_SHOJI = cube("shoji_rasgado", MapColor.TERRACOTTA_WHITE, 0.5f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CYPRESS_BARK = cube("casca_de_cipreste", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CYPRESS_KNEE = cube("raiz_de_cipreste", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<LeavesBlock> CYPRESS_LEAVES = leaves("folhas_de_cipreste");
    public static final DeferredBlock<LeavesBlock> CYPRESS_LEAVES_LIGHT = leaves("folhas_de_cipreste_claras");
    public static final DeferredBlock<ThinPlantBlock> OLD_MANS_BEARD = plant("barba_de_velho", MapColor.PLANT, 0, SoundType.VINE,
            Block.box(2, 0, 2, 14, 16, 14));
    public static final DeferredBlock<ThinPlantBlock> REED = plant("junco", MapColor.PLANT, 0, SoundType.GRASS,
            Block.box(2, 0, 2, 14, 15, 14));
    public static final DeferredBlock<ThinPlantBlock> RICE = plant("arroz", MapColor.PLANT, 0, SoundType.CROP,
            Block.box(2, 0, 2, 14, 13, 14));
    public static final DeferredBlock<Block> FADED_TORII = cube("madeira_de_torii_desbotada", MapColor.TERRACOTTA_RED, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CLAY_DIKE = cube("dique_de_barro", MapColor.DIRT, 0.6f, SoundType.MUD, 0);

    // ---- Mar de Dagon: the white sand and the wet sand, the coconut palm (trunk, two fronds, coconut), the four living
    // corals that glow faintly with plankton, the shells and starfish lying on the sand, the tentacles of the Nest (light
    // and dark flesh, suckers) and the bottomless well.
    public static final DeferredBlock<Block> DAGON_SAND = cube("areia_de_dagon", MapColor.SAND, 0.5f, SoundType.SAND, 0);
    public static final DeferredBlock<Block> WET_SAND = cube("areia_molhada", MapColor.SAND, 0.5f, SoundType.SAND, 0);
    public static final DeferredBlock<Block> PALM_TRUNK = cube("tronco_de_coqueiro", MapColor.WOOD, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<LeavesBlock> PALM_FROND = leaves("folha_de_coqueiro");
    public static final DeferredBlock<LeavesBlock> PALM_FROND_LIGHT = leaves("folha_de_coqueiro_clara");
    public static final DeferredBlock<Block> COCONUT = cube("coco", MapColor.COLOR_BROWN, 1.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CORAL_PINK = coral("coral_rosa", MapColor.COLOR_PINK);
    public static final DeferredBlock<Block> CORAL_ORANGE = coral("coral_laranja", MapColor.COLOR_ORANGE);
    public static final DeferredBlock<Block> CORAL_PURPLE = coral("coral_roxo", MapColor.COLOR_PURPLE);
    public static final DeferredBlock<Block> CORAL_YELLOW = coral("coral_amarelo", MapColor.COLOR_YELLOW);
    public static final DeferredBlock<ThinPlantBlock> SEASHELL = plant("concha", MapColor.SNOW, 0, SoundType.BONE_BLOCK,
            Block.box(1, 0, 1, 15, 1, 15));
    public static final DeferredBlock<ThinPlantBlock> STARFISH = plant("estrela_do_mar", MapColor.COLOR_ORANGE, 0, SoundType.SLIME_BLOCK,
            Block.box(1, 0, 1, 15, 1, 15));
    public static final DeferredBlock<Block> DAGON_TENTACLE = stone("tentaculo_de_dagon", MapColor.TERRACOTTA_PINK, 0);
    public static final DeferredBlock<Block> DAGON_TENTACLE_DARK = stone("tentaculo_de_dagon_escuro", MapColor.TERRACOTTA_RED, 0);
    public static final DeferredBlock<Block> SUCKER = cube("ventosa", MapColor.TERRACOTTA_WHITE, 1.0f, SoundType.SLIME_BLOCK, 0);
    public static final DeferredBlock<Block> ABYSS = stone("abismo", MapColor.COLOR_BLACK, 0);

    // ---- Floresta de Hanami: the ancient bark (bare and mossy), the three greens of the canopy and the canopy in bloom,
    // the giant flowers (petals in four colors, the stem, the golden pollen that glows), the wooden stakes of Hanami's
    // attack, the cursed buds with their glowing pink tips, the dark hollows of the colossal tree's eyes, the vines.
    public static final DeferredBlock<Block> ANCIENT_BARK = cube("casca_ancestral", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> ANCIENT_BARK_MOSS = cube("casca_ancestral_musgo", MapColor.COLOR_GREEN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<LeavesBlock> HANAMI_LEAVES = leaves("folhas_de_hanami");
    public static final DeferredBlock<LeavesBlock> HANAMI_LEAVES_LUSH = leaves("folhas_de_hanami_vivas");
    public static final DeferredBlock<LeavesBlock> HANAMI_LEAVES_LIGHT = leaves("folhas_de_hanami_claras");
    public static final DeferredBlock<LeavesBlock> BLOOMING_CANOPY = leaves("copa_florida");
    public static final DeferredBlock<Block> PETAL_WHITE = cube("petala_gigante_branca", MapColor.SNOW, 0.4f, SoundType.AZALEA_LEAVES, 0);
    public static final DeferredBlock<Block> PETAL_PINK = cube("petala_gigante_rosa", MapColor.COLOR_PINK, 0.4f, SoundType.AZALEA_LEAVES, 0);
    public static final DeferredBlock<Block> PETAL_YELLOW = cube("petala_gigante_amarela", MapColor.COLOR_YELLOW, 0.4f, SoundType.AZALEA_LEAVES, 0);
    public static final DeferredBlock<Block> PETAL_RED = cube("petala_gigante_vermelha", MapColor.COLOR_RED, 0.4f, SoundType.AZALEA_LEAVES, 0);
    public static final DeferredBlock<Block> GIANT_FLOWER_STEM = cube("caule_de_flor_gigante", MapColor.PLANT, 1.0f, SoundType.STEM, 0);
    public static final DeferredBlock<Block> GOLDEN_POLLEN = cube("polen_dourado", MapColor.GOLD, 0.4f, SoundType.MOSS, 10);
    public static final DeferredBlock<Block> WOODEN_STAKE = cube("estaca_de_madeira", MapColor.WOOD, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> CURSED_BUD = cube("botao_amaldicoado", MapColor.COLOR_LIGHT_GREEN, 0.8f, SoundType.SLIME_BLOCK, 4);
    public static final DeferredBlock<Block> BUD_TIP = cube("ponta_do_botao", MapColor.COLOR_PINK, 0.8f, SoundType.SLIME_BLOCK, 9);
    public static final DeferredBlock<Block> HANAMI_HOLLOW = cube("oco_de_hanami", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 3);
    public static final DeferredBlock<ThinPlantBlock> HANAMI_VINE = plant("cipo_de_hanami", MapColor.PLANT, 0, SoundType.VINE,
            Block.box(2, 0, 2, 14, 16, 14));

    // ---- Terras do Jogo: the basalt in two tones, the volcanic ash, the basalt cracked with glowing magma, the basalt
    // columns and their tops, the sulfur, the molten rock of the meteor (glowing), the cinders, the volcano's iron bands.
    public static final DeferredBlock<Block> JOGO_BASALT = stone("basalto_do_jogo", MapColor.COLOR_BLACK, 0);
    public static final DeferredBlock<Block> JOGO_BASALT_DARK = stone("basalto_escuro_do_jogo", MapColor.COLOR_BLACK, 0);
    public static final DeferredBlock<Block> VOLCANIC_ASH = cube("cinza_vulcanica", MapColor.COLOR_GRAY, 0.5f, SoundType.SAND, 0);
    public static final DeferredBlock<Block> MAGMA_CRACKED_BASALT = stone("basalto_rachado_de_magma", MapColor.COLOR_ORANGE, 12);
    public static final DeferredBlock<Block> BASALT_COLUMN = stone("coluna_de_basalto", MapColor.COLOR_GRAY, 0);
    public static final DeferredBlock<Block> COLUMN_TOP = stone("topo_de_coluna", MapColor.COLOR_GRAY, 0);
    public static final DeferredBlock<Block> SULFUR = cube("enxofre", MapColor.COLOR_YELLOW, 0.8f, SoundType.TUFF, 0);
    public static final DeferredBlock<Block> MOLTEN_ROCK = stone("rocha_derretida", MapColor.COLOR_RED, 9);
    public static final DeferredBlock<Block> CINDER = cube("escoria", MapColor.TERRACOTTA_BROWN, 0.6f, SoundType.GRAVEL, 0);
    public static final DeferredBlock<Block> IRON_BAND = stone("faixa_de_ferro", MapColor.TERRACOTTA_BROWN, 0);

    // ---- Outono de Kyoto: the fallen leaves on the ground in three colors, the maple trunk and the dark bark of the
    // ginkgos and pines, the maples' four autumn colors, the ginkgo's yellow, the pine needles, the bamboo leaves, the
    // garden's light wood, the Golden Pavilion's gold leaf (shining) and dark gold, the bark roofs and eaves, the
    // bridge's wood and the tatami.
    public static final DeferredBlock<Block> LITTER_RED = cube("chao_de_folhas_vermelhas", MapColor.COLOR_RED, 0.6f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> LITTER_ORANGE = cube("chao_de_folhas_laranjas", MapColor.COLOR_ORANGE, 0.6f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> LITTER_GOLD = cube("chao_de_folhas_douradas", MapColor.COLOR_YELLOW, 0.6f, SoundType.GRASS, 0);
    public static final DeferredBlock<Block> MAPLE_TRUNK = cube("tronco_de_bordo", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> KYOTO_DARK_BARK = cube("casca_escura_de_kyoto", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<LeavesBlock> MAPLE_RED = leaves("folhas_de_bordo_vermelhas");
    public static final DeferredBlock<LeavesBlock> MAPLE_CRIMSON = leaves("folhas_de_bordo_carmim");
    public static final DeferredBlock<LeavesBlock> MAPLE_ORANGE = leaves("folhas_de_bordo_laranjas");
    public static final DeferredBlock<LeavesBlock> MAPLE_GOLD = leaves("folhas_de_bordo_douradas");
    public static final DeferredBlock<LeavesBlock> GINKGO_LEAVES = leaves("folhas_de_ginkgo");
    public static final DeferredBlock<LeavesBlock> JAPANESE_PINE_NEEDLES = leaves("agulhas_de_pinheiro_japones");
    public static final DeferredBlock<LeavesBlock> BAMBOO_LEAVES = leaves("folhas_de_bambu");
    public static final DeferredBlock<Block> KYOTO_LIGHT_WOOD = cube("madeira_clara_de_kyoto", MapColor.WOOD, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> GOLD_LEAF = stone("folha_de_ouro", MapColor.GOLD, 3);
    public static final DeferredBlock<Block> DARK_GOLD = stone("ouro_escuro", MapColor.GOLD, 0);
    public static final DeferredBlock<Block> BARK_ROOF = cube("telhado_de_casca", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> BARK_EAVE = cube("beiral_de_casca", MapColor.COLOR_BLACK, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> BRIDGE_WOOD = cube("madeira_de_ponte", MapColor.COLOR_BROWN, 2.0f, SoundType.WOOD, 0);
    public static final DeferredBlock<Block> TATAMI = cube("tatame", MapColor.SAND, 0.8f, SoundType.GRASS, 0);

    // ---- Colônia do Jogo do Abate: the streets (asphalt, cracked asphalt, the lane line, the sidewalk, the lots'
    // tiles), the buildings' three concretes, the dark and the lit windows, the floor slabs, the rebar sticking out of
    // the broken floors, the rubble, the embers under the fires (they never burn out), the cars' tires, the traffic
    // cones, the work fences and the warning signs of the barricades, and the weeds born in the cracks.
    public static final DeferredBlock<Block> ASPHALT = stone("asfalto", MapColor.COLOR_GRAY, 0);
    public static final DeferredBlock<Block> ASPHALT_CRACKED = stone("asfalto_rachado", MapColor.COLOR_GRAY, 0);
    public static final DeferredBlock<Block> ROAD_LINE = stone("faixa_de_rua", MapColor.SAND, 0);
    public static final DeferredBlock<Block> SIDEWALK = stone("calcada", MapColor.STONE, 0);
    public static final DeferredBlock<Block> LOT_TILES = stone("piso_de_lote", MapColor.STONE, 0);
    public static final DeferredBlock<Block> COLONY_CONCRETE = stone("concreto_da_colonia", MapColor.STONE, 0);
    public static final DeferredBlock<Block> COLONY_CONCRETE_DARK = stone("concreto_escuro_da_colonia", MapColor.COLOR_GRAY, 0);
    public static final DeferredBlock<Block> COLONY_CONCRETE_LIGHT = stone("concreto_claro_da_colonia", MapColor.QUARTZ, 0);
    public static final DeferredBlock<Block> COLONY_WINDOW = stone("janela_da_colonia", MapColor.COLOR_BLUE, 0);
    public static final DeferredBlock<Block> COLONY_WINDOW_LIT = stone("janela_acesa_da_colonia", MapColor.COLOR_ORANGE, 10);
    public static final DeferredBlock<Block> FLOOR_SLAB = stone("laje", MapColor.STONE, 0);
    public static final DeferredBlock<ThinPlantBlock> REBAR = post("vergalhao", MapColor.TERRACOTTA_BROWN, SoundType.METAL,
            Block.box(7, 0, 7, 9, 16, 9));
    public static final DeferredBlock<Block> RUBBLE = cube("entulho", MapColor.STONE, 0.8f, SoundType.GRAVEL, 0);
    public static final DeferredBlock<Block> EMBERS = stone("brasa", MapColor.COLOR_ORANGE, 8);
    public static final DeferredBlock<Block> TIRE = cube("pneu", MapColor.COLOR_BLACK, 1.0f, SoundType.WOOL, 0);
    public static final DeferredBlock<ThinPlantBlock> TRAFFIC_CONE = post("cone_de_transito", MapColor.COLOR_ORANGE, SoundType.WOOL,
            Block.box(4, 0, 4, 12, 13, 12));
    public static final DeferredBlock<ThinPlantBlock> WORK_FENCE = post("grade_de_obra", MapColor.METAL, SoundType.METAL,
            Block.box(5, 0, 5, 11, 16, 11));
    public static final DeferredBlock<ThinPlantBlock> WARNING_SIGN = post("placa_de_aviso", MapColor.COLOR_YELLOW, SoundType.METAL,
            Block.box(2, 3, 2, 14, 14, 14));
    public static final DeferredBlock<ThinPlantBlock> STREET_WEEDS = plant("mato_de_rua", MapColor.PLANT, 0, SoundType.GRASS,
            Block.box(4, 0, 4, 12, 9, 12));

    private JujutsuBlocks() {
    }

    // Loads this class (so every block above is in ModBlocks' registers) and gives each one its item.
    static void init() {
        for (DeferredBlock<? extends Block> b : ALL) {
            ModBlocks.BLOCK_ITEMS.registerSimpleBlockItem(b);
        }
    }

    private static <T extends Block> DeferredBlock<T> add(DeferredBlock<T> b) {
        ALL.add(b);
        return b;
    }

    private static DeferredBlock<Block> cube(String name, MapColor color, float strength, SoundType sound, int light) {
        return add(ModBlocks.BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color).strength(strength)
                .sound(sound).lightLevel(state -> light)));
    }

    private static DeferredBlock<Block> coral(String name, MapColor color) {
        return add(ModBlocks.BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.CORAL_BLOCK).lightLevel(state -> 6)));
    }

    private static DeferredBlock<Block> stone(String name, MapColor color, int light) {
        return add(ModBlocks.BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.STONE).lightLevel(state -> light)));
    }

    private static DeferredBlock<LeavesBlock> leaves(String name) {
        return add(ModBlocks.BLOCKS.register(name, () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES))));
    }

    // A thin solid thing (rebar, a cone, a fence post, a sign): its own box for the outline and the collision.
    private static DeferredBlock<ThinPlantBlock> post(String name, MapColor color, SoundType sound,
            net.minecraft.world.phys.shapes.VoxelShape shape) {
        return add(ModBlocks.BLOCKS.register(name, () -> new ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).noOcclusion().sound(sound), shape)));
    }

    private static DeferredBlock<ThinPlantBlock> plant(String name, MapColor color, int light, SoundType sound,
            net.minecraft.world.phys.shapes.VoxelShape shape) {
        return add(ModBlocks.BLOCKS.register(name, () -> new ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(color)
                .noCollission().noOcclusion().instabreak().sound(sound).lightLevel(state -> light)
                .pushReaction(PushReaction.DESTROY), shape)));
    }
}
