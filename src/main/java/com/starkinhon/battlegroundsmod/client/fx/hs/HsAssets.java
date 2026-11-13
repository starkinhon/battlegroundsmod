package com.starkinhon.battlegroundsmod.client.fx.hs;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// The wood that never changes shape (the forest, the prison, the flowers, the dome, the dragon's head and skin, the
// Buddha and its thousand hands), exported from the approved page itself (tools/hashirama/bake.js runs the page and writes
// every mesh with its material numbers), so it is the page's own geometry, vertex for vertex.
// File: "HSW1", part count, then per part a JSON header (material + meta) and its arrays (float32 LE), indices int32.
public final class HsAssets {
    public record Part(JsonObject h, WoodGeo.Geo g, HsWood.Mat m) {
        public float f(String k, float def) {
            return h.has(k) ? h.get(k).getAsFloat() : def;
        }

        public String s(String k) {
            return h.has(k) ? h.get(k).getAsString() : "";
        }

        public float[] arr(String k) {
            var a = h.getAsJsonArray(k);
            float[] o = new float[a.size()];
            for (int i = 0; i < o.length; i++) {
                o[i] = a.get(i).getAsFloat();
            }
            return o;
        }
    }

    private static final Map<String, List<Part>> CACHE = new HashMap<>();

    private HsAssets() {
    }

    public static List<Part> get(String name) {
        return CACHE.computeIfAbsent(name, HsAssets::load);
    }

    private static List<Part> load(String name) {
        List<Part> out = new ArrayList<>();
        String path = "/assets/battlegroundsmod/hashirama/" + name + ".bin.gz";
        try (InputStream raw = HsAssets.class.getResourceAsStream(path)) {
            if (raw == null) {
                BattlegroundsMod.LOGGER.error("Hashirama: missing {}", path);
                return out;
            }
            byte[] all = new DataInputStream(new GZIPInputStream(raw)).readAllBytes();
            ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
            bb.position(4);
            int count = bb.getInt();
            for (int p = 0; p < count; p++) {
                int jl = bb.getInt();
                byte[] js = new byte[jl];
                bb.get(js);
                JsonObject h = JsonParser.parseString(new String(js, StandardCharsets.UTF_8)).getAsJsonObject();
                int nv = h.get("nv").getAsInt(), ni = h.get("ni").getAsInt();
                String has = h.get("has").getAsString();
                boolean tube = has.contains("C");
                WoodGeo.Geo g = new WoodGeo.Geo(nv, ni, tube);
                fl(bb, g.p);
                fl(bb, g.n);
                if (tube) {
                    fl(bb, g.c);
                    fl(bb, g.u);
                    fl(bb, g.b);
                }
                if (has.contains("D")) {
                    fl(bb, g.d);
                }
                if (has.contains("O")) {
                    g.ao = new float[nv];
                    fl(bb, g.ao);
                }
                int mode = h.get("mode").getAsInt();
                if (has.contains("K")) {
                    g.k = new float[nv * 3];
                    fl(bb, g.k);
                } else if (mode == 2 || mode == 3) {
                    float[] c = WoodGeo.hex(h.has("color") ? h.get("color").getAsString() : "#ffffff");
                    g.k = new float[nv * 3];
                    for (int i = 0; i < nv; i++) {
                        g.k[i * 3] = c[0];
                        g.k[i * 3 + 1] = c[1];
                        g.k[i * 3 + 2] = c[2];
                    }
                }
                for (int i = 0; i < ni; i++) {
                    g.idx[i] = bb.getInt();
                }
                out.add(new Part(h, g, mat(h)));
            }
        } catch (IOException | RuntimeException e) {
            BattlegroundsMod.LOGGER.error("Hashirama: could not read {}", path, e);
        }
        return out;
    }

    private static void fl(ByteBuffer bb, float[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = bb.getFloat();
        }
    }

    static HsWood.Mat mat(JsonObject h) {
        HsWood.Mat m = new HsWood.Mat();
        m.mode = h.get("mode").getAsInt();
        if (h.has("seed")) {
            m.seed = h.get("seed").getAsFloat();
        }
        if (h.has("wood")) {
            m.wood = WoodGeo.hex(h.get("wood").getAsString());
        }
        if (h.has("dark")) {
            m.dark = WoodGeo.hex(h.get("dark").getAsString());
        }
        if (h.has("tip")) {
            m.tip = h.get("tip").getAsFloat();
        }
        if (h.has("gr")) {
            m.gr = h.get("gr").getAsFloat();
        }
        if (h.has("fine")) {
            m.fine = h.get("fine").getAsFloat();
        }
        if (h.has("ring")) {
            m.ring = h.get("ring").getAsFloat();
        }
        if (h.has("cav")) {
            m.cav = h.get("cav").getAsFloat();
        }
        if (h.has("grain")) {
            m.grain = h.get("grain").getAsFloat();
        }
        if (m.mode == 2 && h.has("emissive")) {
            m.tint = WoodGeo.hex(h.get("emissive").getAsString());
        }
        return m;
    }
}
