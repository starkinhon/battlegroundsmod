package com.starkinhon.battlegroundsmod.world;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// The places of the Dragon Ball map (Stark's second drawing, 1000x1000, 2026-11-10). Each spot was read off the real
// Dragon Ball world map (referencias/mapa-dragonball-real.png) and tools/build_worldmap.py moved it to the flattest dry
// ground nearby and flattened it for the building (it prints the spots). The two places Stark circled in red (the
// floating disc island and the purple island) are sea on purpose.
public final class DragonBallPlaces {
    public static final String MAP = "dragonball";
    // Namekusei (Freeza's planet) floats in the sky over the sea, south-west, where the drawing has it.
    public static final int NAMEK_X = -398, NAMEK_Y = 165, NAMEK_Z = 388, NAMEK_R = 22;
    // The Red Ribbon headquarters, in the dense forest north of the desert (its soldiers spawn around it).
    public static final int RED_RIBBON_X = -415, RED_RIBBON_Z = -65;
    public static final int NAMEK_TOP = NAMEK_Y + 7;

    private DragonBallPlaces() {
    }

    public static boolean onNamek(net.minecraft.world.entity.player.Player player) {
        double dx = player.getX() - NAMEK_X;
        double dz = player.getZ() - NAMEK_Z;
        return dx * dx + dz * dz < (NAMEK_R + 6) * (NAMEK_R + 6) && player.getY() > NAMEK_Y - NAMEK_R;
    }

    // A random spot on Namekusei's surface (feet position).
    public static BlockPos namekSpot(net.minecraft.util.RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;
        double d = random.nextDouble() * (NAMEK_R - 4);
        return new BlockPos(NAMEK_X + (int) Math.round(Math.cos(a) * d), NAMEK_TOP + 1, NAMEK_Z + (int) Math.round(Math.sin(a) * d));
    }

    // Falling off Namekusei is not death: the fall slows down (the sea and islands are far below).
    static void catchFalls(net.minecraft.server.level.ServerPlayer player) {
        double dx = player.getX() - NAMEK_X;
        double dz = player.getZ() - NAMEK_Z;
        if (player.fallDistance > 8 && player.getY() < NAMEK_Y && player.getY() > WorldMap.SEA
                && dx * dx + dz * dz < 60 * 60) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    // The floor of a place: first air above the drawn ground.
    private static Build at(WorldMap map, int x, int z, String id) {
        return new Build(x, Math.max(map.ground(x, z), WorldMap.SEA - 1) + 1, z, id.hashCode());
    }

    static void register() {
        arrivalPad(WorldMap.get(MAP));
        place("kame", 412, 88, DragonBallPlaces::kame);
        place("goku", 300, -25, DragonBallPlaces::goku);
        place("capsula", -166, -149, DragonBallPlaces::capsule);
        place("redribbon", RED_RIBBON_X, RED_RIBBON_Z, DragonBallPlaces::redRibbon);
        place("pilaf", 93, -196, DragonBallPlaces::pilaf);
        place("satan", 315, -169, DragonBallPlaces::satan);
        place("cidade_norte", 94, -396, (map, x, z) -> city(map, x, z, "cidade_norte", 18));
        place("cidade_sul", -23, 253, (map, x, z) -> city(map, x, z, "cidade_sul", 22));
        place("raditz", 250, -195, DragonBallPlaces::raditz);
        place("babidi", -322, 54, DragonBallPlaces::wreck);
        place("buu", -283, -284, DragonBallPlaces::buu);
        place("yajirobe", -432, -211, DragonBallPlaces::yajirobe);
        MapPlaces.add(MAP, new MapPlaces.Place("karin", KARIN_X, KARIN_Z, map -> karin(map), DragonBallPlaces::karinDone));
        MapPlaces.add(MAP, new MapPlaces.Place("namek", NAMEK_X, NAMEK_Z, map -> namek(), null));
    }

    private interface Builder {
        Build build(WorldMap map, int x, int z);
    }

    private static void place(String id, int x, int z, Builder builder) {
        MapPlaces.add(MAP, new MapPlaces.Place(id, x, z, map -> builder.build(map, x, z), null));
    }

    // The pad by the arrival point that takes to Namekusei, and the one back.
    private static void arrivalPad(WorldMap map) {
        int px = map.arrivalX;
        int pz = map.arrivalZ - 7;
        int py = map.ground(px, pz);
        MapPlaces.add(MAP, new MapPlaces.Place("chegada", px, pz, m -> {
            Build b = new Build(px, py, pz, 1);
            b.clear(3.5, 1, 6);
            b.disc(0, 0, 0, 2.6, s(Blocks.SMOOTH_STONE));
            b.disc(0, 0, 0, 1.6, s(Blocks.LIGHT_BLUE_CONCRETE));
            b.set(0, 0, 0, Blocks.SEA_LANTERN);
            for (int[] c : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
                b.set(c[0], 1, c[1], Blocks.END_ROD);
            }
            return b;
        }, null));
        BlockPos pad = new BlockPos(px, py, pz);
        BlockPos namek = new BlockPos(NAMEK_X, NAMEK_TOP, NAMEK_Z + 8);
        // Each pad lands the player beside the other one, never on it (Stark's test, 2026-11-10: landing on the pad
        // sent them straight back, back and forth forever).
        BlockPos besidePad = new BlockPos(px, map.ground(px, pz + 3), pz + 3);
        BlockPos besideNamek = new BlockPos(NAMEK_X, NAMEK_TOP, NAMEK_Z + 11);
        MapPlaces.add(MAP, new MapPlaces.Pad("ir_namek", "chegada", pad, "namek", besideNamek,
                "message.battlegroundsmod.pad_namek"));
        MapPlaces.add(MAP, new MapPlaces.Pad("voltar_terra", "namek", namek, "chegada", besidePad,
                "message.battlegroundsmod.pad_terra"));
    }

    // Kame House: the pink house with the red roof, palm trees, on its little island.
    private static Build kame(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "kame");
        b.clear(9, 0, 14);
        b.box(-3, -1, -3, 3, -1, 3, s(Blocks.SMOOTH_SANDSTONE));
        b.walls(-3, 0, -3, 3, 3, 3, s(Blocks.PINK_CONCRETE));
        for (int i : new int[] {-1, 1}) {
            b.set(i, 1, -3, Blocks.GLASS_PANE).set(i, 2, -3, Blocks.GLASS_PANE);
            b.set(-3, 1, i, Blocks.GLASS_PANE).set(-3, 2, i, Blocks.GLASS_PANE);
            b.set(3, 1, i, Blocks.GLASS_PANE).set(3, 2, i, Blocks.GLASS_PANE);
        }
        b.set(0, 0, 3, Blocks.AIR).set(0, 1, 3, Blocks.AIR);
        b.box(-4, 4, -4, 4, 4, 4, s(Blocks.RED_CONCRETE));
        b.pyramid(0, 5, 0, 3, s(Blocks.RED_CONCRETE));
        b.box(-2, 4, 4, 2, 4, 4, s(Blocks.WHITE_CONCRETE));
        b.palm(6, 4);
        b.palm(-6, -4);
        b.palm(-5, 6);
        return b;
    }

    // Goku's house in the woods: round, white, with an orange pointed roof.
    private static Build goku(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "goku");
        b.clear(8, 0, 12);
        b.disc(0, -1, 0, 4.5, s(Blocks.SPRUCE_PLANKS));
        b.ring(0, 0, 0, 4.5, 3, s(Blocks.WHITE_TERRACOTTA));
        b.set(4, 0, 0, Blocks.AIR).set(4, 1, 0, Blocks.AIR);
        b.set(0, 2, -4, Blocks.GLASS_PANE).set(0, 2, 4, Blocks.GLASS_PANE).set(-4, 2, 0, Blocks.GLASS_PANE);
        b.cone(0, 4, 0, 5.5, 5, s(Blocks.ORANGE_TERRACOTTA));
        b.set(0, 9, 0, Blocks.RED_TERRACOTTA);
        b.box(6, 0, -3, 6, 0, 3, s(Blocks.SPRUCE_FENCE));
        return b;
    }

    // Capsule Corporation: the big white dome with the yellow band, two small domes and the tall tower.
    private static Build capsule(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "capsula");
        b.clear(22, 0, 34);
        b.disc(0, -1, 0, 20, s(Blocks.SMOOTH_STONE));
        b.disc(0, -1, 0, 12, s(Blocks.WHITE_CONCRETE));
        b.dome(0, 0, 0, 10, s(Blocks.WHITE_CONCRETE), s(Blocks.YELLOW_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        b.dome(12, 0, -7, 5, s(Blocks.WHITE_CONCRETE), s(Blocks.YELLOW_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        b.dome(-11, 0, 8, 5, s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_BLUE_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        for (int j = 0; j < 3; j++) {
            for (int k = -1; k <= 1; k++) {
                b.set(10 - j, j == 2 ? 1 : 0, k, Blocks.AIR);
                b.set(9, j, k, Blocks.AIR);
            }
        }
        // Tower with a ball on top.
        b.box(7, 0, 12, 8, 22, 13, s(Blocks.WHITE_CONCRETE));
        b.ball(7, 26, 12, 3.5, s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.YELLOW_CONCRETE), s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        // "CC" by the door.
        b.box(13, 0, -1, 13, 3, 1, s(Blocks.WHITE_CONCRETE));
        b.set(13, 2, 0, Blocks.BLUE_CONCRETE).set(13, 1, 0, Blocks.BLUE_CONCRETE);
        b.palm(-16, -6);
        b.palm(15, 9);
        return b;
    }

    // The Red Ribbon headquarters: a tall red tower with white bands in the dense forest, fenced.
    private static Build redRibbon(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "redribbon");
        b.clear(13, 0, 40);
        b.disc(0, -1, 0, 12, s(Blocks.GRAY_CONCRETE));
        for (int j = 0; j < 34; j++) {
            BlockState wall = j % 7 == 0 ? s(Blocks.WHITE_CONCRETE) : s(Blocks.RED_CONCRETE);
            b.ring(0, j, 0, 5.5, j, wall);
            if (j % 7 == 3) {
                for (int[] w : new int[][] {{5, 0}, {-5, 0}, {0, 5}, {0, -5}, {4, 3}, {-4, -3}, {3, -4}, {-3, 4}}) {
                    b.set(w[0], j, w[1], Blocks.GLASS);
                }
            }
        }
        b.disc(0, 34, 0, 6.5, s(Blocks.RED_CONCRETE));
        b.ring(0, 35, 0, 6.5, 36, s(Blocks.WHITE_CONCRETE));
        for (int j = 7; j < 34; j += 7) {
            b.disc(0, j, 0, 4.4, s(Blocks.SMOOTH_STONE));
        }
        b.ladder(0, 0, -4, 34, Direction.SOUTH);
        b.set(5, 0, 0, Blocks.AIR).set(5, 1, 0, Blocks.AIR).set(4, 0, 0, Blocks.AIR).set(4, 1, 0, Blocks.AIR);
        b.ring(0, 0, 0, 12, 1, s(Blocks.IRON_BARS));
        b.box(11, 0, -1, 12, 1, 1, s(Blocks.AIR));
        return b;
    }

    // Pilaf's castle: stone keep with pointed dark-red roofs.
    private static Build pilaf(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "pilaf");
        b.clear(11, 0, 22);
        b.walls(-4, 0, -4, 4, 8, 4, s(Blocks.STONE_BRICKS));
        b.box(-4, -1, -4, 4, -1, 4, s(Blocks.STONE_BRICKS));
        b.cone(0, 9, 0, 5, 6, s(Blocks.RED_NETHER_BRICKS));
        for (int[] c : new int[][] {{6, 5}, {-6, 5}, {0, -7}}) {
            b.ring(c[0], 0, c[1], 2.2, 11, s(Blocks.STONE_BRICKS));
            b.cone(c[0], 12, c[1], 3, 5, s(Blocks.RED_NETHER_BRICKS));
        }
        b.box(-1, 0, 4, 1, 2, 4, s(Blocks.AIR));
        return b;
    }

    // Mister Satan's mansion: white with gold.
    private static Build satan(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "satan");
        b.clear(10, 0, 14);
        b.box(-6, -1, -4, 6, -1, 4, s(Blocks.SMOOTH_QUARTZ));
        b.walls(-6, 0, -4, 6, 5, 4, s(Blocks.QUARTZ_BLOCK));
        b.walls(-6, 3, -4, 6, 3, 4, s(Blocks.GOLD_BLOCK));
        b.box(-7, 6, -5, 7, 6, 5, s(Blocks.RED_CONCRETE));
        b.box(-5, 7, -3, 5, 7, 3, s(Blocks.RED_CONCRETE));
        for (int i = -4; i <= 4; i += 2) {
            b.set(i, 1, 4, Blocks.GLASS_PANE).set(i, 1, -4, Blocks.GLASS_PANE);
        }
        b.box(-1, 0, 4, 1, 2, 4, s(Blocks.AIR));
        b.set(-2, 0, 6, Blocks.QUARTZ_PILLAR).set(2, 0, 6, Blocks.QUARTZ_PILLAR);
        b.set(-2, 1, 6, Blocks.GOLD_BLOCK).set(2, 1, 6, Blocks.GOLD_BLOCK);
        return b;
    }

    // A city: streets, white and grey buildings of different heights, a few capsule domes.
    private static Build city(WorldMap map, int x, int z, String id, int radius) {
        Build b = at(map, x, z, id);
        b.clear(radius, 0, 40);
        b.disc(0, -1, 0, radius, s(Blocks.GRAY_CONCRETE));
        Block[] walls = {Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_STONE};
        for (int cx = -radius; cx <= radius; cx += 8) {
            for (int cz = -radius; cz <= radius; cz += 8) {
                if (cx * cx + cz * cz > (radius - 5) * (radius - 5)) {
                    continue;
                }
                if (b.random.nextInt(4) == 0) {
                    b.dome(cx, 0, cz, 3, s(Blocks.WHITE_CONCRETE), s(Blocks.YELLOW_CONCRETE), null);
                    b.set(cx + 3, 0, cz, Blocks.AIR).set(cx + 2, 0, cz, Blocks.AIR);
                    continue;
                }
                int h = 6 + b.random.nextInt(20);
                Block wall = walls[b.random.nextInt(walls.length)];
                b.walls(cx - 2, 0, cz - 2, cx + 2, h, cz + 2, s(wall));
                for (int j = 2; j < h; j += 3) {
                    b.set(cx, j, cz - 2, Blocks.LIGHT_BLUE_STAINED_GLASS).set(cx, j, cz + 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
                    b.set(cx - 2, j, cz, Blocks.LIGHT_BLUE_STAINED_GLASS).set(cx + 2, j, cz, Blocks.LIGHT_BLUE_STAINED_GLASS);
                }
                b.box(cx - 2, h + 1, cz - 2, cx + 2, h + 1, cz + 2, s(Blocks.SMOOTH_STONE_SLAB));
                b.set(cx, 0, cz + 2, Blocks.AIR).set(cx, 1, cz + 2, Blocks.AIR);
            }
        }
        return b;
    }

    // Height (relative to the build's floor) of the top block of the ground at an offset from the center.
    private static int ground(WorldMap map, Build b, int i, int k) {
        return Math.max(map.ground(b.x + i, b.z + k), WorldMap.SEA - 1) - b.y;
    }

    // Raditz's landing (Stark, 2026-11-10): the crater the terrain already has, and in its middle the round Saiyan pod,
    // half buried, hatch open, with scorched cracks running out from it.
    private static Build raditz(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "raditz");
        b.clear(6, 0, 8);
        // Cracks: crooked lines of blackstone with a little magma still glowing near the pod.
        for (int ray = 0; ray < 11; ray++) {
            double a = ray * Math.PI * 2 / 11 + b.random.nextDouble() * 0.4;
            int length = 10 + b.random.nextInt(16);
            double bend = (b.random.nextDouble() - 0.5) * 0.05;
            for (int step = 3; step < length; step++) {
                a += bend;
                int i = (int) Math.round(Math.cos(a) * step);
                int k = (int) Math.round(Math.sin(a) * step);
                Block crack = step < 7 && b.random.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK
                        : b.random.nextInt(4) == 0 ? Blocks.BASALT : Blocks.BLACKSTONE;
                b.set(i, ground(map, b, i, k), k, crack);
            }
        }
        // Scorched ring around the pod.
        for (int i = -5; i <= 5; i++) {
            for (int k = -5; k <= 5; k++) {
                if (i * i + k * k <= 25 && b.random.nextInt(3) > 0) {
                    b.set(i, ground(map, b, i, k), k, b.random.nextBoolean() ? Blocks.BLACKSTONE : Blocks.COARSE_DIRT);
                }
            }
        }
        // The pod: white ball, red window band, sunk a block in the ground, the hatch facing up the slope.
        b.ball(0, 1, 0, 2.6, s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.RED_STAINED_GLASS));
        b.set(0, 1, 0, Blocks.AIR).set(0, 2, 0, Blocks.AIR);
        b.set(2, 1, 0, Blocks.AIR).set(2, 2, 0, Blocks.AIR);
        b.set(3, 1, 0, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState());
        b.set(0, -1, 0, Blocks.SMOOTH_STONE);
        // Smoke still rising.
        b.set(-2, 0, 2, Blocks.CAMPFIRE).set(1, 0, -3, Blocks.CAMPFIRE);
        return b;
    }

    // The ship that fell in the middle of the desert (Stark, 2026-11-10): a big white round hull, cracked open and half
    // buried at the end of the furrow it dug, wreckage and smoke all along it.
    private static Build wreck(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "babidi");
        b.clear(16, 0, 14);
        // The furrow: a trench coming from the north-east that gets deeper toward the ship.
        double dirX = 0.75;
        double dirZ = -0.66;
        for (int step = 6; step <= 40; step++) {
            int depth = Math.max(0, 3 - (step - 6) / 9);
            int width = step < 20 ? 3 : 2;
            for (int w = -width; w <= width; w++) {
                int i = (int) Math.round(dirX * step - dirZ * w);
                int k = (int) Math.round(dirZ * step + dirX * w);
                int g = ground(map, b, i, k);
                for (int j = g - depth + 1; j <= g + 2; j++) {
                    b.set(i, j, k, Blocks.AIR);
                }
                b.set(i, g - depth, k, Math.abs(w) == width ? Blocks.SANDSTONE
                        : b.random.nextInt(3) == 0 ? Blocks.COARSE_DIRT : Blocks.RED_SAND);
                if (Math.abs(w) == width && depth > 0) {
                    b.set(i, g + 1, k, Blocks.SAND);
                }
            }
        }
        // The hull: a white ball with a grey band and dark windows, sunk four blocks, a smaller dome on its back.
        b.ball(0, 2, 0, 8.5, s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.GRAY_STAINED_GLASS));
        b.ball(-4, 8, 3, 4.2, s(Blocks.WHITE_CONCRETE), null, s(Blocks.GRAY_STAINED_GLASS));
        // Buried half: packed sand under the floor so there is no hollow under the ship.
        b.box(-9, -7, -9, 9, -3, 9, s(Blocks.SAND));
        b.box(-8, -2, -8, 8, -2, 8, s(Blocks.SMOOTH_SANDSTONE));
        // Torn open: holes in the hull on the side that hit the ground, burnt plates around them.
        for (int i = -9; i <= 9; i++) {
            for (int j = 0; j <= 11; j++) {
                for (int k = -9; k <= 9; k++) {
                    double d = Math.sqrt(i * i + (j - 2) * (j - 2) + k * k);
                    if (d > 8.5 || d < 7.2) {
                        continue;
                    }
                    boolean front = i * dirX + k * dirZ > 2;
                    int roll = b.random.nextInt(100);
                    if (front && roll < 35) {
                        b.set(i, j, k, Blocks.AIR);
                    } else if (front && roll < 55 || roll < 6) {
                        b.set(i, j, k, b.random.nextBoolean() ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE);
                    }
                }
            }
        }
        // Sand blown up against the hull.
        for (int i = -10; i <= 10; i++) {
            for (int k = -10; k <= 10; k++) {
                double d = Math.sqrt(i * i + k * k);
                if (d > 7.5 && d < 10.5 && b.random.nextInt(3) > 0) {
                    b.set(i, ground(map, b, i, k) + 1, k, Blocks.SAND);
                }
            }
        }
        // Wreckage along the furrow and around the hull, and smoke.
        for (int n = 0; n < 26; n++) {
            int step = 4 + b.random.nextInt(34);
            int side = b.random.nextInt(11) - 5;
            int i = (int) Math.round(dirX * step - dirZ * side);
            int k = (int) Math.round(dirZ * step + dirX * side);
            int g = ground(map, b, i, k) + 1;
            Block piece = switch (b.random.nextInt(6)) {
                case 0 -> Blocks.IRON_BLOCK;
                case 1 -> Blocks.WHITE_CONCRETE;
                case 2 -> Blocks.LIGHT_GRAY_CONCRETE;
                case 3 -> Blocks.CHAIN;
                case 4 -> Blocks.IRON_BARS;
                default -> Blocks.ANVIL;
            };
            b.set(i, g, k, piece);
            if (b.random.nextInt(3) == 0) {
                b.set(i, g + 1, k, Blocks.WHITE_CONCRETE);
            }
        }
        for (int[] c : new int[][] {{9, -6}, {-6, 9}, {14, -11}, {3, 10}}) {
            b.set(c[0], ground(map, b, c[0], c[1]) + 1, c[1], Blocks.CAMPFIRE);
        }
        // A broken antenna sticking up.
        b.box(-4, 12, 3, -4, 15, 3, s(Blocks.IRON_BARS));
        b.set(-4, 16, 3, Blocks.LIGHTNING_ROD);
        return b;
    }

    // Majin Buu's house: a pink dome.
    private static Build buu(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "buu");
        b.clear(10, 0, 12);
        b.disc(0, -1, 0, 7, s(Blocks.PINK_TERRACOTTA));
        b.dome(0, 0, 0, 6.5, s(Blocks.PINK_CONCRETE), s(Blocks.WHITE_CONCRETE), s(Blocks.PINK_STAINED_GLASS));
        b.set(6, 0, 0, Blocks.AIR).set(6, 1, 0, Blocks.AIR).set(5, 0, 0, Blocks.AIR).set(5, 1, 0, Blocks.AIR);
        b.box(-1, 6, -1, 1, 9, 1, s(Blocks.WHITE_CONCRETE));
        return b;
    }

    // Yajirobe's hut.
    private static Build yajirobe(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "yajirobe");
        b.clear(7, 0, 8);
        b.walls(-2, 0, -2, 2, 2, 2, s(Blocks.OAK_PLANKS));
        b.pyramid(0, 3, 0, 3, s(Blocks.HAY_BLOCK));
        b.set(0, 0, 2, Blocks.AIR).set(0, 1, 2, Blocks.AIR);
        b.set(4, 0, 0, Blocks.CAMPFIRE);
        return b;
    }

    // Karin's tower on top of the Montanha da Karin: a white column with a ladder, and the platform where Korin lives.
    private static final int KARIN_HEIGHT = 40;
    private static final int KARIN_X = -394, KARIN_Z = -225;

    private static Build karin(WorldMap map) {
        int bx = KARIN_X;
        int bz = KARIN_Z;
        // The highest point near the drawn spot.
        int best = map.ground(bx, bz);
        int px = bx;
        int pz = bz;
        for (int i = -10; i <= 10; i++) {
            for (int k = -10; k <= 10; k++) {
                int g = map.ground(bx + i, bz + k);
                if (g > best && !map.water(bx + i, bz + k)) {
                    best = g;
                    px = bx + i;
                    pz = bz + k;
                }
            }
        }
        Build b = new Build(px, best + 1, pz, 77);
        b.clear(6, 0, KARIN_HEIGHT + 6);
        for (int j = -2; j < KARIN_HEIGHT; j++) {
            b.disc(0, j, 0, 2.2, s(j % 9 == 0 ? Blocks.RED_TERRACOTTA : Blocks.SMOOTH_QUARTZ));
        }
        b.ladder(3, 0, 0, KARIN_HEIGHT - 1, Direction.EAST);
        b.disc(0, KARIN_HEIGHT, 0, 5.5, s(Blocks.SMOOTH_QUARTZ));
        // The ladder ends under a closed trapdoor flush with the floor: Korin walks over it (and never falls 40
        // blocks), players open it from below.
        b.set(3, KARIN_HEIGHT, 0, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
        b.ring(0, KARIN_HEIGHT + 1, 0, 5.5, KARIN_HEIGHT + 1, s(Blocks.OAK_FENCE));
        for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            b.box(c[0], KARIN_HEIGHT + 1, c[1], c[0], KARIN_HEIGHT + 3, c[1], s(Blocks.RED_TERRACOTTA));
        }
        b.disc(0, KARIN_HEIGHT + 4, 0, 4.5, s(Blocks.ORANGE_TERRACOTTA));
        b.cone(0, KARIN_HEIGHT + 5, 0, 3.5, 3, s(Blocks.ORANGE_TERRACOTTA));
        return b;
    }

    private static void karinDone(ServerLevel level, Build b) {
        BlockPos top = new BlockPos(b.x - 2, b.y + KARIN_HEIGHT + 1, b.z - 1);
        if (!level.getEntities(ModEntities.NPC_KORIN.get(), new AABB(top).inflate(48, 64, 48), e -> e.isAlive()).isEmpty()) {
            return;
        }
        UniverseNpc korin = ModEntities.NPC_KORIN.get().spawn(level, top, MobSpawnType.EVENT);
        if (korin != null) {
            korin.restrictTo(top, 3);
            korin.setCustomName(korin.getType().getDescription());
            korin.setCustomNameVisible(true);
        }
    }

    // Namekusei, Freeza's planet (Stark, 2026-11-10): a small round planet floating in the sky, green grass and blue-green
    // water, the round Namekian trees and houses, and Freeza's ship landed on it.
    private static Build namek() {
        Build b = new Build(NAMEK_X, NAMEK_Y, NAMEK_Z, 99);
        int r = NAMEK_R;
        int top = NAMEK_TOP - NAMEK_Y;
        for (int i = -r; i <= r; i++) {
            for (int k = -r; k <= r; k++) {
                for (int j = -r; j <= top; j++) {
                    double d = Math.sqrt(i * i + j * j + k * k);
                    if (d > r) {
                        continue;
                    }
                    BlockState state;
                    if (j == top) {
                        state = s(Blocks.GRASS_BLOCK);
                    } else if (d > r - 1.5) {
                        state = s(b.random.nextInt(5) == 0 ? Blocks.MOSS_BLOCK : Blocks.GREEN_TERRACOTTA);
                    } else if (j > top - 4) {
                        state = s(Blocks.DIRT);
                    } else {
                        state = s(b.random.nextInt(6) == 0 ? Blocks.TUFF : Blocks.STONE);
                    }
                    b.set(i, j, k, state);
                }
            }
        }
        // Ponds.
        for (int[] p : new int[][] {{-9, -6}, {8, -10}, {-4, 12}}) {
            b.disc(p[0], top, p[1], 2.8, s(Blocks.WATER));
            b.disc(p[0], top - 1, p[1], 2.8, s(Blocks.WATER));
            b.disc(p[0], top - 2, p[1], 2.8, s(Blocks.CLAY));
        }
        b.clear(r, top + 1, top + 16);
        // Namekian trees: thin pale stalk, round blue-green crown.
        for (int[] t : new int[][] {{-14, 2}, {12, 6}, {-6, -14}, {4, 15}, {15, -6}, {-15, -8}, {-2, -4}}) {
            int h = 6 + b.random.nextInt(4);
            for (int j = 1; j <= h; j++) {
                b.set(t[0], top + j, t[1], Blocks.STRIPPED_BIRCH_LOG);
            }
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    for (int k = -2; k <= 2; k++) {
                        if (i * i + j * j + k * k <= 5) {
                            b.set(t[0] + i, top + h + 2 + j, t[1] + k, Blocks.WARPED_WART_BLOCK);
                        }
                    }
                }
            }
        }
        // Namekian houses: white domes with round windows.
        for (int[] h : new int[][] {{-12, 10}, {10, 12}}) {
            b.dome(h[0], top + 1, h[1], 3.5, s(Blocks.WHITE_TERRACOTTA), null, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            b.set(h[0] + 3, top + 1, h[1], Blocks.AIR).set(h[0] + 3, top + 2, h[1], Blocks.AIR);
        }
        // Freeza's ship: a white ball with a purple ring of windows, on short legs.
        int sx = 4;
        int sz = -4;
        b.ball(sx, top + 8, sz, 6.5, s(Blocks.WHITE_CONCRETE), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.PURPLE_STAINED_GLASS));
        for (int[] l : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            b.box(sx + l[0], top + 1, sz + l[1], sx + l[0], top + 3, sz + l[1], s(Blocks.LIGHT_GRAY_CONCRETE));
        }
        b.box(sx - 1, top + 2, sz + 6, sx + 1, top + 4, sz + 6, s(Blocks.AIR));
        b.box(sx - 1, top + 1, sz + 7, sx + 1, top + 1, sz + 8, s(Blocks.SMOOTH_QUARTZ_STAIRS));
        b.disc(sx, top + 3, sz, 4.5, s(Blocks.SMOOTH_STONE));
        // The pad back to Earth.
        b.disc(0, top, 8, 1.6, s(Blocks.LIGHT_BLUE_CONCRETE));
        b.set(0, top, 8, Blocks.SEA_LANTERN);
        for (int[] c : new int[][] {{2, 10}, {-2, 10}, {2, 6}, {-2, 6}}) {
            b.set(c[0], top + 1, c[1], Blocks.END_ROD);
        }
        return b;
    }
}
