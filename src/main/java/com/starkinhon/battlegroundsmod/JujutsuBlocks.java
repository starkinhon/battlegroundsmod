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
