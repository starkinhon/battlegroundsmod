package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.starkinhon.battlegroundsmod.challenge.Universes;

// Namek (Stark, 2026-11-10; redone 2026-11-11 as the Dragon Ball's "gran finale"): a planet of its own, a dimension of
// the Dragon Ball universe with generated terrain (tools/biomes/namek.py: seas and islands, blue-green grass, the smooth
// rock towers of NamekSpireFeature, the Namekian trees; DragonWind gives it the green haze and three suns). Freeza's
// ship has landed here, a Capsule Corp ship too, a Namekian village, Guru's house. The way there is the pad by Capsule
// Corp on the Earth (WorldSites: Bulma's ship went to Namek); the pad here takes you back to the one you came from.
public final class NamekWorld {
    public static final String MAP = "namek";
    public static final ResourceKey<Level> LEVEL = Universes.key(MAP);
    public static final String PAD_PLACE = "chegada";
    // The arrival: a pad on a round platform of rock at a fixed height near the middle of the planet.
    private static final int PAD_Y = 80;

    private NamekWorld() {
    }

    private static BlockPos pad() {
        return new BlockPos(0, PAD_Y, 0);
    }

    // Where a pad to Namek lands you: beside Namek's pad, never on it.
    public static BlockPos besidePad() {
        return new BlockPos(0, PAD_Y, 3);
    }

    // The old drawn Dragon Ball map had its own pad to Namek; the way back now goes to the Earth pad you came from.
    public static void linkBack(BlockPos besideEarthPad) {
    }

    public static void register() {
        BlockPos p = pad();
        MapPlaces.add(MAP, new MapPlaces.Place(PAD_PLACE, p.getX(), p.getZ(), m -> {
            Build b = new Build(p.getX(), p.getY(), p.getZ(), 1);
            b.clearUp(7.5, 1, 12);
            b.foundation(7, 50, s(Blocks.GRASS_BLOCK), s(com.starkinhon.battlegroundsmod.ModBlocks.NAMEK_ROCK.get()));
            b.disc(0, 0, 0, 7, s(Blocks.GRASS_BLOCK));
            b.disc(0, 0, 0, 2.6, s(Blocks.SMOOTH_STONE));
            b.disc(0, 0, 0, 1.6, s(Blocks.LIGHT_BLUE_CONCRETE));
            b.set(0, 0, 0, Blocks.SEA_LANTERN);
            for (int[] c : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
                b.set(c[0], 1, c[1], Blocks.END_ROD);
            }
            return b;
        }, null));
        MapPlaces.add(MAP, new MapPlaces.Pad("voltar_terra", PAD_PLACE, p, null, null, "message.battlegroundsmod.pad_terra",
                MapPlaces.EARTH));
        structure("nave_freeza", -80, -60, 36, 26, "dragonball/freeza_ship");
        structure("nave_capsula", 65, 85, 14, 20, "dragonball/namek_capsule");
        structure("vila", 85, -50, 30, 16, "dragonball/namek_village");
        structure("guru", -105, 90, 16, 22, "dragonball/guru_house");
    }

    // A building on the generated ground: its floor is the ground's middle height around it (or just over the sea), on
    // a round foot of grass and rock so it never hangs over a slope or the water.
    private static void structure(String id, int x, int z, double clear, int height, String file) {
        MapPlaces.add(MAP, new MapPlaces.Place(id, x, z, map -> {
            int y = groundAt(x, z, (int) clear);
            Build b = new Build(x, y + 1, z, id.hashCode());
            b.clearUp(clear, 0, height);
            b.foundation(clear - 1, 30, s(Blocks.GRASS_BLOCK), s(com.starkinhon.battlegroundsmod.ModBlocks.NAMEK_ROCK.get()));
            return b.template(file, 0, 0, 0);
        }, null));
    }

    private static int groundAt(int x, int z, int radius) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        var level = server == null ? null : server.getLevel(LEVEL);
        if (level == null) {
            return WorldMap.SEA;
        }
        int[] h = new int[9];
        int n = 0;
        int step = Math.max(1, radius / 2);
        for (int dx = -step; dx <= step; dx += step) {
            for (int dz = -step; dz <= step; dz += step) {
                h[n++] = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz) - 1;
            }
        }
        java.util.Arrays.sort(h);
        return Math.max(h[4], level.getSeaLevel());
    }

    private static BlockState s(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }
}
