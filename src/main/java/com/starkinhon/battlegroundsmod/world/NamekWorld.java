package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.starkinhon.battlegroundsmod.challenge.Universes;

// Namek (Stark, 2026-11-10): a planet of its own, 400x400, a sub-dimension of the Dragon Ball universe. Green sky with a
// yellow horizon, blue grass, green sea, the flat-topped pink-beige mesas and square pillars, the long Namekian trees
// with round crowns (worldmaps/namek.bin from tools/build_namek.py; colors from the biome; MapChunkGenerator grows the
// trees). Freeza's ship has landed here with his soldiers' pods; a Capsule Corp ship too; a Namekian village; Guru's
// house on its mesa. A pad by the Earth's arrival point brings you here and a pad here takes you back.
public final class NamekWorld {
    public static final String MAP = "namek";
    public static final ResourceKey<Level> LEVEL = Universes.key(MAP);
    public static final String PAD_PLACE = "chegada";

    private NamekWorld() {
    }

    private static WorldMap map() {
        return WorldMap.get(MAP);
    }

    private static BlockPos pad() {
        WorldMap map = map();
        int x = map.arrivalX, z = map.arrivalZ - 7;
        return new BlockPos(x, map.ground(x, z), z);
    }

    // Where the pad from the Earth lands you: beside Namek's pad, never on it.
    public static BlockPos besidePad() {
        BlockPos p = pad();
        return new BlockPos(p.getX(), map().ground(p.getX(), p.getZ() + 3), p.getZ() + 3);
    }

    // The pad back to the Earth, landing beside the Earth's pad.
    public static void linkBack(BlockPos besideEarthPad) {
        MapPlaces.add(MAP, new MapPlaces.Pad("voltar_terra", PAD_PLACE, pad(), "chegada", besideEarthPad,
                "message.battlegroundsmod.pad_terra", DragonBallPlaces.MAP));
    }

    public static void register() {
        BlockPos p = pad();
        MapPlaces.add(MAP, new MapPlaces.Place(PAD_PLACE, p.getX(), p.getZ(), m -> {
            Build b = new Build(p.getX(), p.getY(), p.getZ(), 1);
            b.clear(3.5, 1, 6);
            b.disc(0, 0, 0, 2.6, s(Blocks.SMOOTH_STONE));
            b.disc(0, 0, 0, 1.6, s(Blocks.LIGHT_BLUE_CONCRETE));
            b.set(0, 0, 0, Blocks.SEA_LANTERN);
            for (int[] c : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
                b.set(c[0], 1, c[1], Blocks.END_ROD);
            }
            return b;
        }, null));
        // the places tools/build_namek.py flattened for them (it prints the spots)
        structure("nave_freeza", -80, -60, 36, 26, "dragonball/freeza_ship");
        structure("nave_capsula", 65, 85, 14, 20, "dragonball/namek_capsule");
        structure("vila", 85, -50, 30, 16, "dragonball/namek_village");
        structure("guru", -105, 90, 16, 22, "dragonball/guru_house");
        for (int[] c : new int[][] {{-80, -60, 36}, {65, 85, 14}, {85, -50, 30}, {-105, 90, 16}, {p.getX(), p.getZ(), 6}}) {
            MapPlaces.keepClear(MAP, c[0], c[1], c[2] + 8);
        }
    }

    private static void structure(String id, int x, int z, double clear, int height, String file) {
        MapPlaces.add(MAP, new MapPlaces.Place(id, x, z, map -> {
            Build b = new Build(x, Math.max(map.ground(x, z), WorldMap.SEA - 1) + 1, z, id.hashCode());
            b.clear(clear, 0, height);
            return b.template(file, 0, 0, 0);
        }, null));
    }

    private static BlockState s(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }
}
