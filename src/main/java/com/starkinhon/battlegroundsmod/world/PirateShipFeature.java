package com.starkinhon.battlegroundsmod.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

// The pirates of the Mar do Sul (Stark 2026-11-11: "ilhas tropicais da Kame House com piratas"): now and then a pirate
// ship anchored among the islands. A dark wooden hull pointed at the bow and square at the stern, the raised stern
// castle with the captain's cabin and his treasure chest, two masts with white sails bellied by the wind and a black
// flag on top. Length, beam, mast heights and how the sails swell come from the random, and it points any of the four
// ways. The pirates themselves come to its deck from the spawns (UniverseSpawns: the sea is theirs). Within 12 blocks
// of the middle of its chunk.
public final class PirateShipFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<LootTable> TREASURE = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.withDefaultNamespace("chests/shipwreck_treasure"));
    private static final ResourceKey<LootTable> SUPPLY = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.withDefaultNamespace("chests/shipwreck_supply"));

    public PirateShipFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockPos center = new BlockPos((origin.getX() & ~15) + 8, 0, (origin.getZ() & ~15) + 8);
        int sea = level.getSeaLevel();
        // Deep enough water under the whole hull.
        for (int dx = -11; dx <= 11; dx += 11) {
            for (int dz = -11; dz <= 11; dz += 11) {
                if (level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, center.getX() + dx, center.getZ() + dz) > sea - 6) {
                    return false;
                }
            }
        }
        new Ship(level, random, center, sea).build();
        return true;
    }

    private static final class Ship {
        final WorldGenLevel level;
        final RandomSource random;
        final BlockPos center;
        final Direction bow;
        final int half;
        final double beam;
        final int deck;
        final int keel;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Ship(WorldGenLevel level, RandomSource random, BlockPos center, int sea) {
            this.level = level;
            this.random = random;
            this.center = center;
            this.bow = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            this.half = 8 + random.nextInt(3);
            this.beam = 3 + random.nextDouble() * 1.2;
            this.deck = sea + 1;
            this.keel = sea - 4;
        }

        // u along the ship (+ toward the bow), v across it, y in the world.
        void put(int u, int y, int v, BlockState state) {
            Direction side = bow.getClockWise();
            pos.set(center.getX() + bow.getStepX() * u + side.getStepX() * v, y,
                    center.getZ() + bow.getStepZ() * u + side.getStepZ() * v);
            level.setBlock(pos, state, 2);
        }

        // Half the hull's width at a point along it and a height: pointed bow, blunt stern, narrower at the keel.
        double width(int u, int y) {
            double k = (double) u / (half + 1);
            double plan = k > 0 ? 1 - Math.pow(k, 1.7) : 1 - Math.pow(-k, 4);
            double rise = (double) (y - keel) / (deck - keel);
            return beam * Math.max(0, plan) * (0.45 + 0.55 * Math.min(1, rise));
        }

        void build() {
            BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
            BlockState trim = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, bow.getAxis());
            BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            int stern = -half;
            int castle = -half + 4;
            for (int u = -half; u <= half + 1; u++) {
                for (int y = keel; y <= deck; y++) {
                    double w = width(u, y);
                    int wi = (int) Math.round(w);
                    if (w < 0.4 && !(y == deck && u <= half)) {
                        continue;
                    }
                    for (int v = -wi; v <= wi; v++) {
                        boolean shell = Math.abs(v) == wi || y == keel || u == stern || u == half + 1
                                || Math.abs(v) >= (int) Math.round(width(u, y - 1)) && y > keel;
                        if (y == deck) {
                            put(u, y, v, Math.abs(v) == wi ? trim : floor);
                        } else {
                            put(u, y, v, shell ? hull : air);
                        }
                    }
                }
                // the rail along both sides
                int wi = (int) Math.round(width(u, deck));
                if (u > castle && wi > 0) {
                    put(u, deck + 1, wi, fence());
                    put(u, deck + 1, -wi, fence());
                }
            }
            // The stern castle: a raised deck over the captain's cabin, the treasure inside.
            for (int u = stern; u <= castle; u++) {
                int wi = (int) Math.round(width(u, deck));
                for (int v = -wi; v <= wi; v++) {
                    boolean wall = Math.abs(v) == wi || u == stern || u == castle;
                    for (int y = deck + 1; y <= deck + 3; y++) {
                        boolean window = y == deck + 2 && (Math.abs(v) == wi && Math.floorMod(u, 2) == 0 || u == stern && v % 2 == 0);
                        boolean door = u == castle && v == 0 && y < deck + 3;
                        put(u, y, v, door ? air : wall ? (window ? Blocks.GLASS.defaultBlockState() : hull) : air);
                    }
                    put(u, deck + 4, v, Math.abs(v) == wi ? trim : floor);
                    if (Math.abs(v) == wi || u == stern) {
                        put(u, deck + 5, v, fence());
                    }
                }
            }
            chest(stern + 1, deck + 1, 0, TREASURE, bow);
            put(stern + 1, deck + 3, 1, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            put(castle - 1, deck + 1, -1, Blocks.BARREL.defaultBlockState());
            // Barrels and a supply chest on the open deck.
            put(castle + 2, deck + 1, (int) beam - 1, Blocks.BARREL.defaultBlockState());
            put(castle + 2, deck + 2, (int) beam - 1, Blocks.BARREL.defaultBlockState());
            chest(castle + 3, deck + 1, -((int) beam - 1), SUPPLY, bow.getCounterClockWise());
            // The bowsprit, rising out over the water.
            for (int i = 1; i <= 4; i++) {
                put(half + 1 + i, deck + 1 + i / 2, 0, fence());
            }
            mast(half / 2, 11 + random.nextInt(3));
            mast(-half / 2 + 1, 9 + random.nextInt(3));
        }

        void mast(int u, int height) {
            BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
            for (int y = deck - 2; y <= deck + height; y++) {
                put(u, y, 0, log);
            }
            // Two sails, the lower one wider, each swelling toward the bow in the middle.
            int[][] sails = {{deck + 3, deck + 6, (int) Math.round(beam) + 1}, {deck + 8, deck + height - 2, (int) Math.round(beam)}};
            double swell = 1 + random.nextDouble() * 0.8;
            BlockState sail = Blocks.WHITE_WOOL.defaultBlockState();
            for (int[] s : sails) {
                if (s[1] < s[0]) {
                    continue;
                }
                put(u, s[1] + 1, 0, log);
                for (int v = -s[2]; v <= s[2]; v++) {
                    if (v != 0) {
                        put(u, s[1] + 1, v, fence(Blocks.SPRUCE_FENCE.defaultBlockState(), bow.getClockWise()));
                    }
                    for (int y = s[0]; y <= s[1]; y++) {
                        double across = 1 - Math.pow((double) v / (s[2] + 0.5), 2);
                        double down = Math.sin(Math.PI * (y - s[0] + 0.5) / (s[1] - s[0] + 1));
                        int bulge = (int) Math.round(swell * across * down);
                        if (bulge > 0 || v != 0) {
                            put(u + bulge, y, v, sail);
                        }
                    }
                }
            }
            // The black flag on top.
            for (int y = deck + height - 1; y <= deck + height; y++) {
                put(u - 1, y, 0, Blocks.BLACK_WOOL.defaultBlockState());
                put(u - 2, y, 0, Blocks.BLACK_WOOL.defaultBlockState());
            }
            put(u - 1, deck + height - 1, 0, Blocks.BONE_BLOCK.defaultBlockState());
        }

        // A fence joined along the ship (the rails) or across it (the yards).
        BlockState fence() {
            return fence(Blocks.DARK_OAK_FENCE.defaultBlockState(), bow);
        }

        BlockState fence(BlockState state, Direction along) {
            return state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(along), true)
                    .setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(along.getOpposite()), true);
        }

        void chest(int u, int y, int v, ResourceKey<LootTable> loot, Direction facing) {
            put(u, y, v, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), loot);
        }
    }
}
