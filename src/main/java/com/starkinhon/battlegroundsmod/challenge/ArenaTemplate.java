package com.starkinhon.battlegroundsmod.challenge;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// One challenge arena as the generator made it (tools/arenas, Stark 2026-11-12: each arena is the canon place of that
// fight). The same blocks the web preview draws (JOGO = PRÉVIA), in data/battlegroundsmod/souls_arenas/<id>.bin
// (format in tools/arenas/modexport.py): columns of block runs, closest to the centre first, y = 0 the fight floor.
// arenas.json holds each arena's fight radius, wall height, spawn points, fog and whether it happens at night.
public final class ArenaTemplate {
    private static final String DIR = "/data/" + BattlegroundsMod.MODID + "/souls_arenas/";
    private static final int MAGIC = 0x41524E31;

    public record Meta(String id, int raio, int altura, int[] jogador, int[] desafiante, float[] neblina, boolean noite,
                       int minY, int maxY, String nome) {
    }

    private static Map<String, Meta> meta;
    private static final Map<String, SoftReference<ArenaTemplate>> CACHE = new HashMap<>();

    public final String id;
    public final BlockState[] palette;
    public final int columns;
    public final short[] colX;
    public final short[] colZ;
    public final int[] runStart;     // runs of column i are runStart[i] .. runStart[i + 1] - 1
    public final short[] runY;
    public final short[] runLen;
    public final short[] runBlock;
    public final int blocks;

    private ArenaTemplate(String id, BlockState[] palette, short[] colX, short[] colZ, int[] runStart, short[] runY,
                          short[] runLen, short[] runBlock) {
        this.id = id;
        this.palette = palette;
        this.columns = colX.length;
        this.colX = colX;
        this.colZ = colZ;
        this.runStart = runStart;
        this.runY = runY;
        this.runLen = runLen;
        this.runBlock = runBlock;
        int n = 0;
        for (short len : runLen) {
            n += len;
        }
        this.blocks = n;
    }

    public static synchronized Map<String, Meta> meta() {
        if (meta == null) {
            Map<String, Meta> map = new HashMap<>();
            try (InputStream in = ArenaTemplate.class.getResourceAsStream(DIR + "arenas.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        map.put(e.getKey(), new Meta(e.getKey(), o.get("R").getAsInt(), o.get("altura").getAsInt(),
                                ints(o.getAsJsonArray("jogador")), ints(o.getAsJsonArray("desafiante")),
                                floats(o.getAsJsonArray("neblina")), o.has("noite") && o.get("noite").getAsBoolean(),
                                o.get("minY").getAsInt(), o.get("maxY").getAsInt(), o.has("nome") ? o.get("nome").getAsString() : e.getKey()));
                    }
                }
            } catch (IOException | RuntimeException e) {
                BattlegroundsMod.LOGGER.error("Arenas: não li arenas.json", e);
            }
            meta = map;
        }
        return meta;
    }

    public static Meta meta(String id) {
        return id == null ? null : meta().get(id);
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    // The arena's blocks, read once and kept while memory allows (a big one is ~600k blocks in a few MB of arrays).
    public static synchronized ArenaTemplate get(String id) {
        SoftReference<ArenaTemplate> ref = CACHE.get(id);
        ArenaTemplate t = ref == null ? null : ref.get();
        if (t == null) {
            t = read(id);
            if (t != null) {
                CACHE.put(id, new SoftReference<>(t));
            }
        }
        return t;
    }

    private static ArenaTemplate read(String id) {
        try (InputStream raw = ArenaTemplate.class.getResourceAsStream(DIR + id + ".bin")) {
            if (raw == null) {
                return null;
            }
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16));
            if (in.readInt() != MAGIC) {
                BattlegroundsMod.LOGGER.error("Arena {}: arquivo inválido", id);
                return null;
            }
            BlockState[] palette = new BlockState[in.readInt()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = parse(id, in.readUTF());
            }
            int columns = in.readInt();
            short[] colX = new short[columns];
            short[] colZ = new short[columns];
            int[] runStart = new int[columns + 1];
            short[] runY = new short[columns * 2];
            short[] runLen = new short[columns * 2];
            short[] runBlock = new short[columns * 2];
            int runs = 0;
            for (int c = 0; c < columns; c++) {
                colX[c] = in.readShort();
                colZ[c] = in.readShort();
                int n = in.readShort();
                runStart[c] = runs;
                if (runs + n > runY.length) {
                    int size = Math.max(runY.length * 2, runs + n);
                    runY = java.util.Arrays.copyOf(runY, size);
                    runLen = java.util.Arrays.copyOf(runLen, size);
                    runBlock = java.util.Arrays.copyOf(runBlock, size);
                }
                for (int r = 0; r < n; r++, runs++) {
                    runY[runs] = in.readShort();
                    runLen[runs] = in.readShort();
                    runBlock[runs] = in.readShort();
                }
            }
            runStart[columns] = runs;
            return new ArenaTemplate(id, palette, colX, colZ, runStart, runY, runLen, runBlock);
        } catch (IOException | RuntimeException e) {
            BattlegroundsMod.LOGGER.error("Arena {}: não consegui ler", id, e);
            return null;
        }
    }

    private static BlockState parse(String id, String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.warn("Arena {}: bloco desconhecido {}", id, state);
            return Blocks.STONE.defaultBlockState();
        }
    }

    // Whether the generator put a block at height y of column c.
    public boolean solid(int c, int y) {
        for (int r = runStart[c]; r < runStart[c + 1]; r++) {
            if (y >= runY[r] && y < runY[r] + runLen[r]) {
                return true;
            }
        }
        return false;
    }

    public int lowest(int c) {
        return runStart[c] < runStart[c + 1] ? runY[runStart[c]] : 0;
    }

    // Column index by position, for the columns near the centre (the fight circle and its walls).
    public int[] index(int half) {
        int size = half * 2 + 1;
        int[] idx = new int[size * size];
        java.util.Arrays.fill(idx, -1);
        for (int c = 0; c < columns; c++) {
            int x = colX[c] + half;
            int z = colZ[c] + half;
            if (x >= 0 && z >= 0 && x < size && z < size) {
                idx[x * size + z] = c;
            }
        }
        return idx;
    }
}
