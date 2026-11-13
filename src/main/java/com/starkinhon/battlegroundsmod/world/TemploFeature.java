package com.starkinhon.battlegroundsmod.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.IntBinaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.ModBlocks;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

// The Templos Antigos (Stark 2026-11-12, "muito lindo, pode meter no game", from the approved preview), done like the
// Naruto features: once per chunk of the biome, the preview's templePiece step by step (TemploGen is its line-by-line
// port, checked block for block against the preview in 5 seeds):
// 1. the ground's top: moss stone on the steep slopes, pink petals in the sakura groves, podzol (needles) under the
//    cedars, moss in patches, grass (TemploGen.groundTop);
// 2. the part of every temple that reaches the chunk: one spot per cell of 150 on the game's own hills, the courtyard
//    on its wall, the hall, pagoda or belfry, lanterns, sacred cedar, sakuras and the stairway down the slope with its
//    torii (built once and kept, the same in every chunk so the pieces meet);
// 3. the woods on the grid of 5: cedars, sakuras, ferns and shrubs, in the preview's order (a later tree's leaves don't
//    replace an earlier one's), never on a temple or within 26 of its center;
// 4. the loose ferns and shrubs; 5. the hokoras in the woods, which clear the leaves and ferns around them.
// A tree's spot is good or not by its own column only (the game has no whole square to look at), so a tree is never cut
// at a chunk's edge. Writes only inside its own chunk.
public final class TemploFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SALT = 0x7E3B10;
    // A temple's blocks (packed x, y, z, type), and the set of its solid voxels' keys (for the trees' check), per cell.
    private static final Cache<Long, Built> TEMPLES = CacheBuilder.newBuilder().maximumSize(64).build();
    // A cell's spot, null kept as NONE.
    private static final Cache<Long, TemploGen.Site> SITES = CacheBuilder.newBuilder().maximumSize(4096).build();
    private static final TemploGen.Site NONE = new TemploGen.Site(0, 0, 0, 'n', 0);

    // clear = how far from the center the woods keep away (26 for a temple, 56 for the school); npcs = where the school's
    // Yaga and Shoko stand (x, y, z each), empty for a temple.
    private record Built(int[] vox, Set<Long> solid, TemploGen.Site site, int clear, int[] npcs) {
    }

    public TemploFeature(Codec<NoneFeatureConfiguration> codec) {
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
        // the preview's water is at 0 and its ground is the top block: here the water's top is at sea level - 1
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
                if (!level.getBiome(pos.set(x, h + 1, z)).is(ModWorldgen.TEMPLOS_ANTIGOS)) {
                    continue;
                }
                any = true;
                if (h < sea || !level.getBlockState(pos.set(x, h + 1, z)).getFluidState().isEmpty()) {
                    continue;
                }
                BlockState top = level.getBlockState(pos.set(x, h, z));
                if (!top.is(BlockTags.DIRT)) {
                    continue;
                }
                byte t = TemploGen.groundTop(x, h, z, slope(ground, x, z, h), seed);
                level.setBlock(pos, ground(t), 2);
                if (t == TemploGen.PETALS && level.getBlockState(pos.set(x, h + 1, z)).isAir()) {
                    level.setBlock(pos, petals(x, z, seed), 2);
                }
            }
        }
        if (!any) {
            return false;
        }

        // 2. The temples that reach the chunk, into the chunk's own map first (the preview's order of who wins).
        TemploGen.Vox w = new TemploGen.Vox(x0, z0, x1, z1);
        List<Built> temples = new ArrayList<>();
        int r = Math.max(TemploGen.TEMPLE_REACH, TemploGen.ESCOLA_REACH) + 26;
        for (int ci = Math.floorDiv(x0 - r, TemploGen.SITE_CELL); ci <= Math.floorDiv(x1 + r, TemploGen.SITE_CELL); ci++) {
            for (int cj = Math.floorDiv(z0 - r, TemploGen.SITE_CELL); cj <= Math.floorDiv(z1 + r, TemploGen.SITE_CELL); cj++) {
                Built b = temple(ci, cj, worldKey, seed, sea, generator, random, ground, maxY);
                if (b != null) {
                    temples.add(b);
                }
            }
        }
        for (Built b : temples) {
            int[] v = b.vox();
            for (int i = 0; i < v.length; i += 4) {
                w.force(v[i], v[i + 1], v[i + 2], (byte) v[i + 3]);
            }
        }
        // the school's Yaga and Shoko, each by the chunk that holds their spot
        for (Built b : temples) {
            int[] n = b.npcs();
            for (int i = 0; i < n.length; i += 3) {
                if (n[i] >= x0 && n[i] <= x1 && n[i + 2] >= z0 && n[i + 2] <= z1) {
                    npc(level, i == 0 ? ModEntities.NPC_YAGA.get() : ModEntities.NPC_SHOKO.get(), n[i], n[i + 1], n[i + 2]);
                }
            }
        }

        // 3. The woods: every grid spot whose tree can reach the chunk, in the preview's order.
        int tr = TemploGen.TREE_REACH;
        for (int gx = Math.floorDiv(x0 - tr - 5, 5) * 5; gx <= x1 + tr; gx += 5) {
            for (int gz = Math.floorDiv(z0 - tr - 5, 5) * 5; gz <= z1 + tr; gz += 5) {
                TemploGen.Spot s = TemploGen.spot(gx, gz, seed);
                if (s == null) {
                    continue;
                }
                int h = ground.applyAsInt(s.x(), s.z());
                if (!treeGood(s.x(), s.z(), h, sea, seed, temples, ground, generator, random)) {
                    continue;
                }
                if (s.kind() == TemploGen.Spot.PLANT) {
                    w.force(s.x(), h + 1, s.z(), TemploGen.plantAt(s.x(), s.z(), seed));
                } else {
                    TemploGen.tree(w, s, h, seed);
                }
            }
        }

        // 4. The loose ferns and shrubs.
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                byte p = TemploGen.scatterAt(x, z, seed);
                if (p == TemploGen.AIR) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (h < sea + 1 || !biomeAt(generator, random, x, h, z)) {
                    continue;
                }
                int t = w.get(x, h, z) != 0 ? w.get(x, h, z) : TemploGen.groundTop(x, h, z, slope(ground, x, z, h), seed);
                if ((t == TemploGen.GRASS || t == TemploGen.NEEDLES || t == TemploGen.MOSS) && w.get(x, h + 1, z) == 0) {
                    w.force(x, h + 1, z, p);
                }
            }
        }

        // 5. The hokoras.
        for (int gx = Math.floorDiv(x0 - 48, 48) * 48; gx <= x1 + 8; gx += 48) {
            for (int gz = Math.floorDiv(z0 - 48, 48) * 48; gz <= z1 + 8; gz += 48) {
                int[] p = TemploGen.hokoraSpot(gx, gz, seed);
                if (p == null || p[0] + 3 < x0 || p[0] - 3 > x1 || p[1] + 3 < z0 || p[1] - 3 > z1) {
                    continue;
                }
                int x = p[0], z = p[1];
                if (busy(x, z, temples)) {
                    continue;
                }
                int h = ground.applyAsInt(x, z);
                if (h < sea + 1 || !biomeAt(generator, random, x, h, z)) {
                    continue;
                }
                for (int a = -3; a <= 3; a++) {
                    for (int b = -3; b <= 3; b++) {
                        for (int y = h + 1; y < h + 8; y++) {
                            if (TemploGen.clearedByHokora(w.get(x + a, y, z + b))) {
                                w.force(x + a, y, z + b, TemploGen.AIR);
                            }
                        }
                    }
                }
                TemploGen.hokora(w, x, h, z, TemploGen.hashInt(x, 0, z, seed + 27));
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
            if (t == TemploGen.CARVE || t == TemploGen.DARK) {
                if (!there.isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                return;
            }
            boolean soft = soft(t);
            boolean open = there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty();
            if (soft && !open) {
                return; // leaves and plants only grow into the air, as the preview's set
            }
            if (t == TemploGen.DOOR) {
                level.setBlock(pos, door(w, x, y, z), 2);
                return;
            }
            level.setBlock(pos, block(t, x, y, z), 2);
        });
        return true;
    }

    private static int slope(IntBinaryOperator ground, int x, int z, int h) {
        return Math.max(Math.abs(ground.applyAsInt(x + 1, z) - h), Math.abs(ground.applyAsInt(x, z + 1) - h));
    }

    private static boolean busy(int x, int z, List<Built> temples) {
        for (Built b : temples) {
            if (Math.abs(x - b.site().cx()) < b.clear() && Math.abs(z - b.site().cz()) < b.clear()) {
                return true;
            }
        }
        return false;
    }

    // The preview's checks for a tree's spot: dry land, not near a temple's center, nothing of a temple on it or above
    // it, not on the steep stone; and in the biome.
    private static boolean treeGood(int x, int z, int h, int sea, int seed, List<Built> temples, IntBinaryOperator ground,
            ChunkGenerator generator, RandomState random) {
        if (h < sea + 1 || busy(x, z, temples)) {
            return false;
        }
        for (Built b : temples) {
            if (b.solid().contains(LeafTreeGen.Keys.key(x, h + 1, z)) || b.solid().contains(LeafTreeGen.Keys.key(x, h, z))) {
                return false;
            }
        }
        byte top = TemploGen.groundTop(x, h, z, slope(ground, x, z, h), seed);
        if (top == TemploGen.STONE || top == TemploGen.MOSSY_STONE) {
            return false;
        }
        return biomeAt(generator, random, x, h, z);
    }

    // Is the biome anywhere in the cell (a grid of 24, finer than any courtyard)? Looking for a spot costs about 50
    // terrain samples, so a cell without the biome skips it (PC profile of 2026-11-13: 5% of the generation).
    private static boolean biomeInCell(int ci, int cj, int sea, ChunkGenerator generator, RandomState random) {
        int s = TemploGen.SITE_CELL;
        for (int x = ci * s; x < (ci + 1) * s; x += 24) {
            for (int z = cj * s; z < (cj + 1) * s; z += 24) {
                if (biomeAt(generator, random, x, sea + 8, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean biomeAt(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler()).is(ModWorldgen.TEMPLOS_ANTIGOS);
    }

    // A cell's temple, built once: its spot (on the game's ground), and good only with the biome under the whole
    // courtyard and room above it for the pagoda.
    private static Built temple(int ci, int cj, int worldKey, int seed, int sea, ChunkGenerator generator, RandomState random,
            IntBinaryOperator ground, int maxY) {
        long key = ((long) ci << 36) ^ ((long) (cj & 0xFFFFFFFL) << 8) ^ ((long) worldKey * 0x9E3779B97F4A7C15L);
        try {
            TemploGen.Site site = SITES.get(key, () -> {
                if (!biomeInCell(ci, cj, sea, generator, random)) {
                    return NONE; // a temple needs the biome under its whole courtyard; without it no spot is looked for
                }
                TemploGen.Site s = TemploGen.siteIn(ci, cj, seed, ground, sea);
                return s != null && siteGood(s, TemploGen.isEscolaSite(s, seed) ? 60 : 24, generator, random, maxY) ? s : NONE;
            });
            if (site == NONE) {
                return null;
            }
            return TEMPLES.get(key, () -> {
                TemploGen.Vox v = new TemploGen.Vox();
                int[] npcs = {};
                boolean school = TemploGen.isEscolaSite(site, seed);
                if (school) {
                    TemploGen.School sc = TemploGen.escola(v, site, ground, seed, sea);
                    npcs = new int[] {sc.yaga()[0], sc.yaga()[1], sc.yaga()[2], sc.shoko()[0], sc.shoko()[1], sc.shoko()[2]};
                } else {
                    TemploGen.temple(v, site, ground, seed, sea);
                }
                Set<Long> solid = new HashSet<>();
                v.each((x, y, z, t) -> {
                    if (t != TemploGen.CARVE) {
                        solid.add(LeafTreeGen.Keys.key(x, y, z));
                    }
                });
                return new Built(v.pack(), solid, site, school ? 56 : 26, npcs);
            });
        } catch (ExecutionException e) {
            return null;
        }
    }

    // The biome under the whole courtyard (radius 24; the school's terraces, 60) and room above it for the pagoda.
    private static boolean siteGood(TemploGen.Site s, int radius, ChunkGenerator generator, RandomState random, int maxY) {
        if (s.y() + 70 > maxY || !biomeAt(generator, random, s.cx(), s.y(), s.cz())) {
            return false;
        }
        for (int a = 0; a < 8; a++) {
            int x = s.cx() + (int) Math.round(Math.cos(a * Math.PI / 4) * radius);
            int z = s.cz() + (int) Math.round(Math.sin(a * Math.PI / 4) * radius);
            if (!biomeAt(generator, random, x, s.y(), z)) {
                return false;
            }
        }
        return true;
    }

    // Leaves and plants: they go only into the air (the preview's set), the rest takes the place of what is there.
    private static boolean soft(int t) {
        return t == TemploGen.CEDAR_LEAF || t == TemploGen.CEDAR_LEAF2 || t == TemploGen.SAKURA0 || t == TemploGen.SAKURA1
                || t == TemploGen.SAKURA2 || t == TemploGen.SAKURA_W || t == TemploGen.FERN || t == TemploGen.SHRUB;
    }

    // The classroom's door: two blocks high and three wide in a wall; the door looks out across the wall.
    private static BlockState door(TemploGen.Vox w, int x, int y, int z) {
        boolean upper = w.get(x, y - 1, z) == TemploGen.DOOR;
        boolean alongX = w.get(x - 1, y, z) == TemploGen.DOOR || w.get(x + 1, y, z) == TemploGen.DOOR;
        return Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.FACING, alongX ? Direction.SOUTH : Direction.EAST);
    }

    // A school NPC at its spot, kept near it (they come back to the same place) and never despawned.
    private static void npc(WorldGenLevel level, EntityType<UniverseNpc> type, int x, int y, int z) {
        UniverseNpc npc = type.create(level.getLevel());
        if (npc == null) {
            return;
        }
        npc.moveTo(x + 0.5, y, z + 0.5, 0, 0);
        npc.setPersistenceRequired();
        npc.restrictTo(new BlockPos(x, y, z), 10);
        npc.setCustomName(npc.getType().getDescription());
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    private static BlockState petals(int x, int z, int seed) {
        double h = TemploGen.hash3(x, 13, z, seed);
        return Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + (int) (h * 4))
                .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue((int) (TemploGen.hash3(x, 14, z, seed) * 4)));
    }

    private static BlockState ground(byte t) {
        return switch (t) {
            case TemploGen.STONE -> Blocks.STONE.defaultBlockState();
            case TemploGen.MOSSY_STONE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case TemploGen.NEEDLES -> Blocks.PODZOL.defaultBlockState();
            case TemploGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            default -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private static BlockState leaves(BlockState b) {
        return b.setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState block(int t, int x, int y, int z) {
        return switch (t) {
            case TemploGen.STONE -> Blocks.STONE.defaultBlockState();
            case TemploGen.DIRT -> Blocks.DIRT.defaultBlockState();
            case TemploGen.GRASS, TemploGen.PETALS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case TemploGen.MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case TemploGen.NEEDLES -> Blocks.PODZOL.defaultBlockState();
            case TemploGen.MOSSY_STONE -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case TemploGen.GRAVEL_W -> ModBlocks.WHITE_GRAVEL.get().defaultBlockState();
            case TemploGen.PATH -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case TemploGen.WATER -> Blocks.WATER.defaultBlockState();
            case TemploGen.CEDAR_BARK -> Blocks.MANGROVE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
            case TemploGen.CEDAR_LEAF -> leaves(ModBlocks.CEDAR_LEAVES.get().defaultBlockState());
            case TemploGen.CEDAR_LEAF2 -> leaves(ModBlocks.CEDAR_LEAVES_LIGHT.get().defaultBlockState());
            case TemploGen.SAKURA_BARK -> Blocks.CHERRY_WOOD.defaultBlockState();
            case TemploGen.SAKURA0 -> leaves(Blocks.CHERRY_LEAVES.defaultBlockState());
            case TemploGen.SAKURA1 -> leaves(ModBlocks.SAKURA_LEAVES_DARK.get().defaultBlockState());
            case TemploGen.SAKURA2 -> leaves(ModBlocks.SAKURA_LEAVES_LIGHT.get().defaultBlockState());
            case TemploGen.SAKURA_W -> leaves(ModBlocks.SAKURA_LEAVES_WHITE.get().defaultBlockState());
            case TemploGen.WALL_STONE -> Blocks.STONE_BRICKS.defaultBlockState();
            case TemploGen.STEP -> Blocks.SMOOTH_STONE.defaultBlockState();
            case TemploGen.PLASTER -> ModBlocks.PLASTER.get().defaultBlockState();
            case TemploGen.WOOD -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            case TemploGen.WOOD_DARK -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case TemploGen.VERMILION, TemploGen.TORII -> ModBlocks.VERMILION_WOOD.get().defaultBlockState();
            case TemploGen.LATTICE -> ModBlocks.SHOJI.get().defaultBlockState();
            case TemploGen.FLOOR_WOOD -> Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState();
            case TemploGen.ROOF_TILE -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case TemploGen.ROOF_RIDGE -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            case TemploGen.ROOF_EAVE -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            case TemploGen.GOLD -> Blocks.GOLD_BLOCK.defaultBlockState();
            case TemploGen.BRONZE -> Blocks.WAXED_WEATHERED_COPPER.defaultBlockState();
            case TemploGen.TORII_TOP -> ModBlocks.BLACK_LACQUER.get().defaultBlockState();
            case TemploGen.LANTERN_STONE -> Blocks.ANDESITE.defaultBlockState();
            case TemploGen.LANTERN_FIRE -> ModBlocks.STONE_LANTERN_LIGHT.get().defaultBlockState();
            case TemploGen.ROPE -> ModBlocks.SHIMENAWA.get().defaultBlockState();
            case TemploGen.SHIDE -> ModBlocks.SHIDE.get().defaultBlockState();
            case TemploGen.FERN -> Blocks.FERN.defaultBlockState();
            case TemploGen.SHRUB -> Blocks.AZALEA.defaultBlockState();
            case TemploGen.PACKED -> Blocks.COARSE_DIRT.defaultBlockState();
            case TemploGen.GLASS -> Blocks.GLASS.defaultBlockState();
            case TemploGen.STRAW -> Blocks.HAY_BLOCK.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }
}
