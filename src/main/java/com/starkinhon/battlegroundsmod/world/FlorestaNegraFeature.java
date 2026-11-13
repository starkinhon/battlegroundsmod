package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.JujutsuBlocks;
import com.starkinhon.battlegroundsmod.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Floresta Negra (Stark 2026-11-12, "só maldições"; port released 2026-11-13), done like the Templos: once per
// chunk of the biome, the preview's forest and lair step by step (FlorestaNegraGen is its line-by-line port, checked
// block for block against the preview in 6 seeds):
// 1. the ground's top: black soil, dead grass, mud, roots and the pools of black water (FlorestaNegraGen.groundTop);
// 2. the black trees on the grid of 6, in the preview's order, every one whose blocks can reach the chunk;
// 3. the fungi and bones on the ground; 4. the shrines, torii and statues on the grid of 40, each clearing the crown
//    around it;
// 5. the lairs: one spot per cell of 320 (45% of them), built once and kept (the cracked clearing, the 8 torii, the
//    dead trees and the Árvore Matriz), and the crown over the clearing trimmed so it stays open to the sky.
// A tree's spot is good or not by its own column only, so a tree is never cut at a chunk's edge. Writes only inside
// its own chunk.
public final class FlorestaNegraFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x5A17E3;
    private static final int LAIR_CELL = 320;
    private static final Cache<Long, Built> LAIRS = CacheBuilder.newBuilder().maximumSize(32).build();
    private static final Cache<Long, int[]> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final int[] NONE = {};

    private record Built(int[] vox, int cx, int cz, int lairSeed) {
    }

    public FlorestaNegraFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState random = ((ServerLevel) level.getLevel()).getChunkSource().randomState();
        long worldSeed = level.getSeed();
        int worldKey = (int) (worldSeed ^ (worldSeed >>> 32));
        int seed = worldKey ^ SALT;
        int sea = generator.getSeaLevel() - 1;
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        IntBinaryOperator ground = (x, z) -> LeafForestFeature.height(generator, level, random, worldKey, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;

        // 1. The ground's top.
        boolean any = false;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int h = ground.applyAsInt(x, z);
                if (!level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.FLORESTA_NEGRA)) {
                    continue;
                }
                any = true;
                if (h < sea || !level.getBlockState(pos.set(x, h + 1, z)).getFluidState().isEmpty()) {
                    continue;
                }
                if (!level.getBlockState(pos.set(x, h, z)).is(BlockTags.DIRT)) {
                    continue;
                }
                byte t = FlorestaNegraGen.groundTop(x, z, seed);
                if (t == FlorestaNegraGen.BLACK_WATER && !held(ground, x, z, h)) {
                    t = FlorestaNegraGen.MUD;
                }
                level.setBlock(pos, block(t), 2);
            }
        }
        if (!any) {
            return false;
        }

        // The lairs that reach the chunk.
        List<Built> lairs = new ArrayList<>();
        int r = FlorestaNegraGen.LAIR_REACH;
        for (int ci = Math.floorDiv(x0 - r, LAIR_CELL); ci <= Math.floorDiv(x1 + r, LAIR_CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - r, LAIR_CELL); cj <= Math.floorDiv(z1 + r, LAIR_CELL); cj++) {
                Built b = lair(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (b != null) {
                    lairs.add(b);
                }
            }
        }

        // 2. The black trees: every grid spot whose tree can reach the chunk, in the preview's order.
        TemploGen.Vox w = new TemploGen.Vox(x0, z0, x1, z1);
        int tr = FlorestaNegraGen.TREE_REACH;
        for (int gx = Math.floorDiv(x0 - tr - 6, 6) * 6; gx <= x1 + tr; gx += 6) {
            for (int gz = Math.floorDiv(z0 - tr - 6, 6) * 6; gz <= z1 + tr; gz += 6) {
                int[] s = FlorestaNegraGen.treeSpot(gx, gz, seed);
                if (s == null || kept(s[0], s[1], lairs)) {
                    continue;
                }
                int h = ground.applyAsInt(s[0], s[1]);
                if (h < sea + 1 || FlorestaNegraGen.groundTop(s[0], s[1], seed) == FlorestaNegraGen.BLACK_WATER
                        || !biomeAt(generator, random, s[0], h, s[1])) {
                    continue;
                }
                FlorestaNegraGen.tree(w, s[0], h, s[1], seed);
            }
        }

        // 3. The fungi and bones (bones spread 2 around their column).
        for (int x = x0 - 2; x <= x1 + 2; x++) {
            for (int z = z0 - 2; z <= z1 + 2; z++) {
                int sc = FlorestaNegraGen.scatterAt(x, z, seed);
                if (sc == FlorestaNegraGen.SCATTER_NONE || kept(x, z, lairs)) {
                    continue;
                }
                if (sc == FlorestaNegraGen.SCATTER_FUNGUS && !w.in(x, z)) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (h < sea + 1 || w.get(x, h + 1, z) != 0 || FlorestaNegraGen.groundTop(x, z, seed) == FlorestaNegraGen.BLACK_WATER
                        || !biomeAt(generator, random, x, h, z)) {
                    continue;
                }
                if (sc == FlorestaNegraGen.SCATTER_FUNGUS) {
                    w.force(x, h + 1, z, FlorestaNegraGen.FUNGUS);
                } else {
                    FlorestaNegraGen.bones(w, x, h + 1, z, JjkGen.hashInt(x, 1, z, seed + 31));
                }
            }
        }

        // 4. The shrines, torii and statues on the grid of 40 (a torii reaches 6 from its spot, a clearing 4).
        for (int gx = Math.floorDiv(x0 - 40, 40) * 40; gx <= x1 + 8; gx += 40) {
            for (int gz = Math.floorDiv(z0 - 40, 40) * 40; gz <= z1 + 8; gz += 40) {
                int[] lm = FlorestaNegraGen.landmark(gx, gz, seed);
                if (lm[0] + 7 < x0 || lm[0] - 7 > x1 || lm[1] + 7 < z0 || lm[1] - 7 > z1 || kept(lm[0], lm[1], lairs)) {
                    continue;
                }
                int h = ground.applyAsInt(lm[0], lm[1]);
                if (h < sea + 1 || !biomeAt(generator, random, lm[0], h, lm[1])) {
                    continue;
                }
                FlorestaNegraGen.placeLandmark(w, lm, ground, seed);
            }
        }

        // 5. The lairs over it all, and the crown over their clearings trimmed.
        for (Built b : lairs) {
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
            int gy = ground.applyAsInt(b.cx(), b.cz());
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (!FlorestaNegraGen.trimmed(x, z, b.cx(), b.cz(), b.lairSeed())) {
                        continue;
                    }
                    for (int y = gy - 10; y < gy + 70; y++) {
                        int t = w.get(x, y, z);
                        if (t == FlorestaNegraGen.LEAF || t == FlorestaNegraGen.LEAF2 || t == FlorestaNegraGen.LEAF_VIOLET
                                || t == FlorestaNegraGen.VINE) {
                            w.force(x, y, z, FlorestaNegraGen.AIR);
                        }
                    }
                }
            }
        }

        // Into the world.
        w.each((x, y, z, t) -> {
            if (y < minY || y > maxY) {
                return;
            }
            pos.set(x, y, z);
            BlockState there = level.getBlockState(pos);
            if (there.is(Blocks.BEDROCK)) {
                return;
            }
            if (t == TemploGen.CARVE) {
                if (!there.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                return;
            }
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (soft(t) && !open) {
                return; // leaves and plants only grow into the air, as the preview's set
            }
            level.setBlock(pos, block(t), 2);
        });
        return true;
    }

    // A pool's water stays put only with the ground around it at least as high.
    private static boolean held(IntBinaryOperator ground, int x, int z, int h) {
        return ground.applyAsInt(x + 1, z) >= h && ground.applyAsInt(x - 1, z) >= h && ground.applyAsInt(x, z + 1) >= h
                && ground.applyAsInt(x, z - 1) >= h;
    }

    private static boolean kept(int x, int z, List<Built> lairs) {
        for (Built b : lairs) {
            if (JjkGen.hypot(x - b.cx(), z - b.cz()) < FlorestaNegraGen.LAIR_KEEP) {
                return true;
            }
        }
        return false;
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.FLORESTA_NEGRA);
    }

    // A cell's lair: its spot well inside the cell, good only on dry land with the biome all around the clearing; built
    // once.
    private static Built lair(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        long key = ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
        try {
            int[] site = SITES.get(key, () -> {
                if (JjkGen.hash3(ci, 61, cj, seed) > 0.45) {
                    return NONE;
                }
                int cx = ci * LAIR_CELL + 70 + JjkGen.floor(JjkGen.hash3(ci, 62, cj, seed) * (LAIR_CELL - 140));
                int cz = cj * LAIR_CELL + 70 + JjkGen.floor(JjkGen.hash3(ci, 63, cj, seed) * (LAIR_CELL - 140));
                int gy = ground.applyAsInt(cx, cz);
                if (gy < sea + 2 || gy + 80 > maxY || !biomeAt(generator, random, cx, gy, cz)) {
                    return NONE;
                }
                for (int a = 0; a < 8; a++) {
                    int x = cx + JjkGen.round(Math.cos(a * Math.PI / 4) * FlorestaNegraGen.LAIR_KEEP);
                    int z = cz + JjkGen.round(Math.sin(a * Math.PI / 4) * FlorestaNegraGen.LAIR_KEEP);
                    if (!biomeAt(generator, random, x, gy, z)) {
                        return NONE;
                    }
                }
                return new int[] {cx, cz};
            });
            if (site == NONE) {
                return null;
            }
            return LAIRS.get(key, () -> {
                TemploGen.Vox v = new TemploGen.Vox();
                int ls = JjkGen.hashInt(ci, cj, 61, seed);
                FlorestaNegraGen.lair(v, site[0], site[1], ground, ls, false);
                return new Built(v.pack(), site[0], site[1], ls);
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static boolean soft(int t) {
        return t == FlorestaNegraGen.LEAF || t == FlorestaNegraGen.LEAF2 || t == FlorestaNegraGen.LEAF_VIOLET
                || t == FlorestaNegraGen.VINE || t == FlorestaNegraGen.FUNGUS || t == FlorestaNegraGen.TALISMAN;
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState block(int t) {
        return switch (t) {
            case FlorestaNegraGen.STONE -> Blocks.DEEPSLATE.defaultBlockState();
            case FlorestaNegraGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case FlorestaNegraGen.SOIL -> JujutsuBlocks.BLACK_SOIL.get().defaultBlockState();
            case FlorestaNegraGen.DEAD_GRASS -> JujutsuBlocks.DEAD_GRASS.get().defaultBlockState();
            case FlorestaNegraGen.MUD -> Blocks.MUD.defaultBlockState();
            case FlorestaNegraGen.BLACK_WATER -> Blocks.WATER.defaultBlockState();
            case FlorestaNegraGen.ROOT -> JujutsuBlocks.BLACK_ROOT.get().defaultBlockState();
            case FlorestaNegraGen.BARK -> JujutsuBlocks.BLACK_WOOD.get().defaultBlockState();
            case FlorestaNegraGen.BARK_DARK -> JujutsuBlocks.BLACK_WOOD_DARK.get().defaultBlockState();
            case FlorestaNegraGen.LEAF -> leaves(JujutsuBlocks.BLACK_LEAVES.get().defaultBlockState());
            case FlorestaNegraGen.LEAF2 -> leaves(JujutsuBlocks.BLACK_LEAVES_MOSS.get().defaultBlockState());
            case FlorestaNegraGen.LEAF_VIOLET -> leaves(JujutsuBlocks.VIOLET_LEAVES.get().defaultBlockState());
            case FlorestaNegraGen.VINE -> JujutsuBlocks.BLACK_VINE.get().defaultBlockState();
            case FlorestaNegraGen.FUNGUS -> JujutsuBlocks.CURSED_FUNGUS.get().defaultBlockState();
            case FlorestaNegraGen.EYE -> JujutsuBlocks.CURSED_EYE.get().defaultBlockState();
            case FlorestaNegraGen.PUPIL -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case FlorestaNegraGen.TALISMAN -> ModBlocks.SHIDE.get().defaultBlockState();
            case FlorestaNegraGen.TORII_ROT -> JujutsuBlocks.ROTTEN_RED_WOOD.get().defaultBlockState();
            case FlorestaNegraGen.TORII_TOP_ROT -> ModBlocks.BLACK_LACQUER.get().defaultBlockState();
            case FlorestaNegraGen.STATUE -> Blocks.STONE.defaultBlockState();
            case FlorestaNegraGen.STATUE_MOSS -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case FlorestaNegraGen.BONE, FlorestaNegraGen.TOOTH -> Blocks.BONE_BLOCK.defaultBlockState();
            case FlorestaNegraGen.CRACKED -> Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
            case FlorestaNegraGen.VEIN -> JujutsuBlocks.CURSED_VEIN.get().defaultBlockState();
            case FlorestaNegraGen.FLESH -> JujutsuBlocks.CURSED_FLESH.get().defaultBlockState();
            case FlorestaNegraGen.FLESH_DARK -> JujutsuBlocks.MAW.get().defaultBlockState();
            case FlorestaNegraGen.ROT_WOOD -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case FlorestaNegraGen.ROOF_ROT -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case FlorestaNegraGen.ASH -> Blocks.TUFF.defaultBlockState();
            default -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }
}
