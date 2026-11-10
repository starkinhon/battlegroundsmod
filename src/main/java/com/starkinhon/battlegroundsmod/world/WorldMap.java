package com.starkinhon.battlegroundsmod.world;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// A universe drawn by hand (Stark, 2026-11-10: the 600x600 Dragon Ball map). tools/build_worldmap.py turns the picture
// into data/battlegroundsmod/worldmaps/<name>.bin: for every column the ground height and what the ground is (sea,
// beach, forest, snow...). MapChunkGenerator builds the world from it; outside the picture it is open sea.
public final class WorldMap {
    // What each column is, in the order the tool writes them.
    public static final int DEEP = 0, SHALLOW = 1, SAND = 2, GRASS = 3, FOREST = 4, SNOW = 5, ROCK = 6, REDROCK = 7,
            LAVA = 8, DARK = 9, DESERT = 10, CANYON = 11, CRATER = 12,
            // Namek (Stark, 2026-11-10): blue grass plains, the flat-topped pink-beige mesas, tan bare ground, green sea.
            NGRASS = 13, NMESA = 14, NSAND = 15, NSEA = 16;
    public static final String[] NAMES = {"deep", "shallow", "sand", "grass", "forest", "snow", "rock", "redrock", "lava",
            "dark", "desert", "canyon", "crater", "ngrass", "nmesa", "nsand", "nsea"};
    public static final int SEA = 63;
    private static final int OUTSIDE_FLOOR = 40;

    private static final Map<String, WorldMap> LOADED = new ConcurrentHashMap<>();

    public final int width, depth, originX, originZ, arrivalX, arrivalZ;
    private final short[] height;
    private final byte[] kind;

    private WorldMap(int width, int depth, int originX, int originZ, int arrivalX, int arrivalZ, short[] height, byte[] kind) {
        this.width = width;
        this.depth = depth;
        this.originX = originX;
        this.originZ = originZ;
        this.arrivalX = arrivalX;
        this.arrivalZ = arrivalZ;
        this.height = height;
        this.kind = kind;
    }

    public static WorldMap get(String name) {
        return LOADED.computeIfAbsent(name, WorldMap::load);
    }

    private static WorldMap load(String name) {
        String path = "/data/" + BattlegroundsMod.MODID + "/worldmaps/" + name + ".bin";
        try (InputStream raw = WorldMap.class.getResourceAsStream(path)) {
            if (raw == null) {
                throw new IllegalStateException("Mapa não encontrado: " + path);
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            byte[] magic = new byte[4];
            in.readFully(magic);
            int version = in.readInt();
            int width = in.readInt();
            int depth = in.readInt();
            int originX = in.readInt();
            int originZ = in.readInt();
            int arrivalX = in.readInt();
            int arrivalZ = in.readInt();
            if (magic[0] != 'S' || version != 1) {
                throw new IllegalStateException("Mapa inválido: " + path);
            }
            short[] height = new short[width * depth];
            for (int i = 0; i < height.length; i++) {
                height[i] = in.readShort();
            }
            byte[] kind = new byte[width * depth];
            in.readFully(kind);
            return new WorldMap(width, depth, originX, originZ, arrivalX, arrivalZ, height, kind);
        } catch (IOException e) {
            throw new IllegalStateException("Mapa ilegível: " + path, e);
        }
    }

    private int index(int x, int z) {
        int c = x - originX;
        int r = z - originZ;
        if (c < 0 || r < 0 || c >= width || r >= depth) {
            return -1;
        }
        return r * width + c;
    }

    public boolean inside(int x, int z) {
        return index(x, z) >= 0;
    }

    // Top solid block of the column.
    public int ground(int x, int z) {
        int i = index(x, z);
        return i < 0 ? OUTSIDE_FLOOR : height[i];
    }

    public int kind(int x, int z) {
        int i = index(x, z);
        return i < 0 ? DEEP : kind[i];
    }

    public boolean water(int x, int z) {
        int k = kind(x, z);
        return k == DEEP || k == SHALLOW || k == NSEA;
    }

    // How many columns the piece of land under (x, z) has (0 on water): small ones are the islands (the spawns tell
    // them from the continent). Counted once, the first time it is asked.
    private int[] landSize;

    public int landSize(int x, int z) {
        int i = index(x, z);
        if (i < 0 || water(x, z)) {
            return 0;
        }
        if (landSize == null) {
            landSize = countLand();
        }
        return landSize[i];
    }

    private int[] countLand() {
        int n = width * depth;
        int[] size = new int[n];
        int[] queue = new int[n];
        int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        for (int start = 0; start < n; start++) {
            if (comp[start] >= 0 || isWater(kind[start])) {
                continue;
            }
            int head = 0, tail = 0;
            queue[tail++] = start;
            comp[start] = start;
            while (head < tail) {
                int c = queue[head++];
                int cx = c % width, cz = c / width;
                for (int k = 0; k < 4; k++) {
                    int nx = cx + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = cz + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx >= width || nz >= depth) {
                        continue;
                    }
                    int ni = nz * width + nx;
                    if (comp[ni] < 0 && !isWater(kind[ni])) {
                        comp[ni] = start;
                        queue[tail++] = ni;
                    }
                }
            }
            for (int q = 0; q < tail; q++) {
                size[queue[q]] = tail;
            }
        }
        return size;
    }

    private static boolean isWater(int k) {
        return k == DEEP || k == SHALLOW || k == NSEA;
    }

    public int centerX() {
        return originX + width / 2;
    }

    public int centerZ() {
        return originZ + depth / 2;
    }

    // Half the side: how far from the center the drawn world goes.
    public int radius() {
        return Math.max(width, depth) / 2;
    }
}
