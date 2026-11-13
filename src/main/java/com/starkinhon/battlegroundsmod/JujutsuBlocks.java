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

    private static DeferredBlock<Block> stone(String name, MapColor color, int light) {
        return add(ModBlocks.BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1.5f, 6.0f).sound(SoundType.STONE).lightLevel(state -> light)));
    }

    private static DeferredBlock<LeavesBlock> leaves(String name) {
        return add(ModBlocks.BLOCKS.register(name, () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES))));
    }

    private static DeferredBlock<ThinPlantBlock> plant(String name, MapColor color, int light, SoundType sound,
            net.minecraft.world.phys.shapes.VoxelShape shape) {
        return add(ModBlocks.BLOCKS.register(name, () -> new ThinPlantBlock(BlockBehaviour.Properties.of().mapColor(color)
                .noCollission().noOcclusion().instabreak().sound(sound).lightLevel(state -> light)
                .pushReaction(PushReaction.DESTROY), shape)));
    }
}
