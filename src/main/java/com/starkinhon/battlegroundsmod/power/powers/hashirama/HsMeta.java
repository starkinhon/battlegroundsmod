package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// The server's read of the baked wood (assets/battlegroundsmod/hashirama/*.bin.gz, tools/hashirama/bake.js): only each
// part's numbers (where a tree stands, when it grows), never the meshes, so the hits fall where the client draws.
public final class HsMeta {
    // A tree of the Nascimento da Floresta, in the page frame (page units): base (bx, bz), thickness R, height H, born t0 s.
    public record Tree(double bx, double bz, double r, double h, double t0) {
    }

    private static List<Tree> trees;

    private HsMeta() {
    }

    public static synchronized List<Tree> trees() {
        if (trees == null) {
            Map<Integer, Tree> by = new HashMap<>();
            for (JsonObject h : headers("forest")) {
                int k = h.get("tree").getAsInt();
                by.putIfAbsent(k, new Tree(h.get("bx").getAsDouble(), h.get("bz").getAsDouble(), h.get("R").getAsDouble(), h.get("H").getAsDouble(),
                        h.get("t0").getAsDouble()));
            }
            List<Tree> l = new ArrayList<>(by.values());
            l.sort((a, b) -> Double.compare(a.t0(), b.t0()));
            trees = l;
        }
        return trees;
    }

    // Every part's JSON header of a baked file, the arrays skipped.
    static List<JsonObject> headers(String name) {
        List<JsonObject> out = new ArrayList<>();
        try (InputStream raw = HsMeta.class.getResourceAsStream("/assets/battlegroundsmod/hashirama/" + name + ".bin.gz")) {
            if (raw == null) {
                BattlegroundsMod.LOGGER.error("Hashirama: {} is missing", name);
                return out;
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            int n = Integer.reverseBytes(in.readInt());
            for (int i = 0; i < n; i++) {
                int len = Integer.reverseBytes(in.readInt());
                byte[] js = new byte[len];
                in.readFully(js);
                JsonObject h = JsonParser.parseString(new String(js, StandardCharsets.UTF_8)).getAsJsonObject();
                out.add(h);
                int nv = h.get("nv").getAsInt(), ni = h.get("ni").getAsInt();
                String has = h.has("has") ? h.get("has").getAsString() : "";
                long skip = nv * 24L + ni * 4L + (has.contains("C") ? nv * 24L : 0) + (has.contains("D") ? nv * 12L : 0) + (has.contains("O") ? nv * 4L : 0)
                        + (has.contains("K") ? nv * 12L : 0);
                while (skip > 0) {
                    long s = in.skip(skip);
                    if (s <= 0) {
                        in.readByte();
                        s = 1;
                    }
                    skip -= s;
                }
            }
        } catch (IOException | RuntimeException e) {
            BattlegroundsMod.LOGGER.error("Hashirama: could not read {}", name, e);
        }
        return out;
    }
}
