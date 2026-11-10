package com.starkinhon.battlegroundsmod.world;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;

import net.minecraft.core.BlockPos;
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
        place("capsula", -149, -152, DragonBallPlaces::capsule);
        MapPlaces.keepClear(MAP, 412, 88, 17);
        MapPlaces.keepClear(MAP, -149, -152, 38);
        place("raditz", 250, -195, DragonBallPlaces::raditz);
        place("babidi", -319, 57, DragonBallPlaces::wreck);
        // The six places Stark marked on the 2D map (2026-11-10), drawn after his references by
        // tools/structures/dragonball2.py. The spots are the ones tools/build_worldmap.py flattened.
        place("goku", 323, -29, (map, x, z) -> structure(map, x, z, "goku", 24, 24, "dragonball/goku_house"));
        place("buu", -268, -430, (map, x, z) -> structure(map, x, z, "buu", 16, 18, "dragonball/buu_house"));
        place("cell", -54, -289, (map, x, z) -> structure(map, x, z, "cell", 34, 48, "dragonball/cell_arena"));
        place("babidi_nave", -239, 95, (map, x, z) -> structure(map, x, z, "babidi_nave", 18, 14, "dragonball/babidi_ship"));
        MapPlaces.add(MAP, new MapPlaces.Place("karin", KARIN_X, KARIN_Z,
                map -> structure(map, KARIN_X, KARIN_Z, "karin", 10, 12, "dragonball/korin_tower"), DragonBallPlaces::karinDone));
        MapPlaces.add(MAP, new MapPlaces.Place("lookout", LOOKOUT_X, LOOKOUT_Z,
                map -> new Build(LOOKOUT_X, LOOKOUT_Y, LOOKOUT_Z, 280).template("dragonball/kami_lookout", 0, 0, 0), null));
        MapPlaces.keepClear(MAP, 323, -29, 24);
        MapPlaces.keepClear(MAP, -268, -430, 16);
        MapPlaces.keepClear(MAP, -54, -289, 34);
        MapPlaces.keepClear(MAP, -239, 95, 20);
        MapPlaces.keepClear(MAP, KARIN_X, KARIN_Z, 20);
        lookoutPads(WorldMap.get(MAP));
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

    // Kame House (Stark's references, 2026-11-10): pink wooden house, steep red gable roof with a dormer, porch with its
    // own little roof, white trims and shutters, antenna, palms (tools/structures/dragonball.py draws it).
    private static Build kame(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "kame");
        b.clear(17, 0, 22);
        return b.template("dragonball/kame_house", 0, 0, 0);
    }

    // Capsule Corporation (Stark's reference, 2026-11-10): the big flattened cream dome with long blue window bands,
    // white cap, two round towers, CAPSULE on the front, round entrance with the red door, palms around the yard.
    private static Build capsule(WorldMap map, int x, int z) {
        Build b = at(map, x, z, "capsula");
        b.clear(38, 0, 32);
        return b.template("dragonball/capsule_corp", 0, 0, 0);
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

    // The ship that fell in the middle of the desert (Stark, 2026-11-10): Capsule Corp's round ship, half buried at the
    // end of the furrow it dug, wreckage and smoke all along it.
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
        for (int[] c : new int[][] {{12, -7}, {-8, 11}, {15, -12}, {4, 13}}) {
            b.set(c[0], ground(map, b, c[0], c[1]) + 1, c[1], Blocks.CAMPFIRE);
        }
        // The ship itself (Capsule Corp's round ship, Stark's reference): sunk and leaning, a leg broken, torn on top.
        b.template("dragonball/capsule_ship_wreck", 0, 0, 0);
        return b;
    }

    // Korin's tower (Stark, 2026-11-10): the carved pillar from the forest up into the sky with the ladder, and the round
    // house on top (KORIN_TOP blocks up, balcony floor KORIN_FLOOR) where Korin lives; a pad there goes up to Kami.
    private static final int KARIN_X = -407, KARIN_Z = -87;
    private static final int KORIN_FLOOR = 162;
    // Kami's Lookout floats high over the north-west sea (the drawn disc, Stark's mark 1); its tiles are at y 280.
    private static final int LOOKOUT_X = -400, LOOKOUT_Y = 281, LOOKOUT_Z = -408;

    // A building from a structure file on its flattened spot: clears a cylinder (trees, grass) and draws it.
    private static Build structure(WorldMap map, int x, int z, String id, double clear, int height, String file) {
        Build b = at(map, x, z, id);
        b.clear(clear, 0, height);
        return b.template(file, 0, 0, 0);
    }

    // The pads between Korin's balcony and Kami's Lookout (in the anime Goku flies up; here the pad takes you).
    private static void lookoutPads(WorldMap map) {
        int ky = Math.max(map.ground(KARIN_X, KARIN_Z), WorldMap.SEA - 1) + 1 + KORIN_FLOOR;
        BlockPos korinPad = new BlockPos(KARIN_X + 14, ky, KARIN_Z);
        BlockPos besideKorin = new BlockPos(KARIN_X + 14, ky, KARIN_Z + 3);
        BlockPos lookoutPad = new BlockPos(LOOKOUT_X, LOOKOUT_Y - 1, LOOKOUT_Z + 40);
        BlockPos besideLookout = new BlockPos(LOOKOUT_X, LOOKOUT_Y - 1, LOOKOUT_Z + 36);
        MapPlaces.add(MAP, new MapPlaces.Pad("ir_kami", "karin", korinPad, "lookout", besideLookout,
                "message.battlegroundsmod.pad_kami"));
        MapPlaces.add(MAP, new MapPlaces.Pad("voltar_karin", "lookout", lookoutPad, "karin", besideKorin,
                "message.battlegroundsmod.pad_karin"));
    }

    private static void karinDone(ServerLevel level, Build b) {
        BlockPos top = new BlockPos(b.x - 3, b.y + KORIN_FLOOR + 1, b.z - 2);
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
