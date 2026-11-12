package com.starkinhon.battlegroundsmod.client.blocky;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

// The approved preview's animation, baked frame by frame (tools/mobs/naruto/export.js: 20 frames a second, every
// channel that moves): each part's position, rotation and scale added to its rest pose, and the whole body's offset,
// turn and visibility ('@'). Played back here instead of rewriting each animation by hand, so the game moves exactly
// like the preview. Idle and walk loop (the 8 s bakes cross-fade their seam), the attacks play once.
public final class BakedAnim {
    public static final int FPS = 20;
    // root channels: x, y, z, rx, ry, rz, hide
    public static final int ROOT = 7;
    private static final int SEAM = 10;

    public static final class Channel {
        final String part;
        final int kind;
        final float[] v;

        Channel(String part, int kind, float[] v) {
            this.part = part;
            this.kind = kind;
            this.v = v;
        }
    }

    public static final class Mode {
        public final String name;
        public final int frames;
        final boolean seam;
        final List<Channel> channels = new ArrayList<>();
        final float[][] root = new float[ROOT][];

        Mode(String name, int frames, boolean seam) {
            this.name = name;
            this.frames = frames;
            this.seam = seam;
        }

        public float seconds() {
            return frames / (float) FPS;
        }
    }

    private final Map<String, Mode> modes = new HashMap<>();

    public BakedAnim(JsonObject json) {
        JsonObject ms = json.getAsJsonObject("modes");
        for (String name : ms.keySet()) {
            JsonObject m = ms.getAsJsonObject(name);
            int n = m.get("frames").getAsInt();
            boolean idle = name.equals("parado") || name.equals("andar");
            Mode mode = new Mode(name, n, idle && m.get("loop").getAsBoolean() && n > SEAM * 3);
            JsonObject ch = m.getAsJsonObject("ch");
            for (String key : ch.keySet()) {
                JsonArray arr = ch.getAsJsonArray(key);
                float[] v = new float[n];
                for (int i = 0; i < n && i < arr.size(); i++) {
                    v[i] = arr.get(i).getAsFloat();
                }
                int dot = key.lastIndexOf('.');
                String part = key.substring(0, dot), c = key.substring(dot + 1);
                if (part.equals("@")) {
                    int k = switch (c) {
                        case "x" -> 0;
                        case "y" -> 1;
                        case "z" -> 2;
                        case "rx" -> 3;
                        case "ry" -> 4;
                        case "rz" -> 5;
                        default -> 6;
                    };
                    mode.root[k] = v;
                    continue;
                }
                int k = switch (c) {
                    case "x" -> 0;
                    case "y" -> 1;
                    case "z" -> 2;
                    case "rx" -> 3;
                    case "ry" -> 4;
                    case "rz" -> 5;
                    case "sx" -> 6;
                    case "sy" -> 7;
                    default -> 8;
                };
                mode.channels.add(new Channel(part, k, v));
            }
            modes.put(name, mode);
        }
    }

    public Mode mode(String name) {
        return modes.get(name);
    }

    // value of a channel at frame f (fractional): linear between frames; a loop wraps, and an 8 s idle bake plays
    // its first n - SEAM frames and blends its tail into its head so the seam never pops.
    static float at(float[] v, Mode m, float f, boolean loop) {
        int n = m.frames;
        if (!loop) {
            f = Mth.clamp(f, 0, n - 1);
            int i = (int) f;
            int j = Math.min(n - 1, i + 1);
            return Mth.lerp(f - i, v[i], v[j]);
        }
        if (!m.seam) {
            f = f % n;
            if (f < 0) {
                f += n;
            }
            int i = (int) f;
            return Mth.lerp(f - i, v[i], v[(i + 1) % n]);
        }
        int p = n - SEAM;
        f = f % p;
        if (f < 0) {
            f += p;
        }
        float here = lin(v, f);
        if (f >= SEAM) {
            return here;
        }
        float w = f / SEAM;
        w = w * w * (3 - 2 * w);
        return Mth.lerp(w, lin(v, f + p), here);
    }

    private static float lin(float[] v, float f) {
        int i = Math.min((int) f, v.length - 1);
        int j = Math.min(i + 1, v.length - 1);
        return Mth.lerp(f - i, v[i], v[j]);
    }

    // Adds w times this mode's pose at frame f onto the model.
    public static void apply(BlockyModel<?> model, Mode m, float f, boolean loop, float w) {
        if (m == null || w <= 0) {
            return;
        }
        for (Channel c : m.channels) {
            ModelPart p = model.part(c.part);
            if (p == null) {
                continue;
            }
            float d = at(c.v, m, f, loop) * w;
            switch (c.kind) {
                case 0 -> p.x += d;
                case 1 -> p.y += d;
                case 2 -> p.z += d;
                case 3 -> p.xRot += d;
                case 4 -> p.yRot += d;
                case 5 -> p.zRot += d;
                case 6 -> p.xScale += d;
                case 7 -> p.yScale += d;
                default -> p.zScale += d;
            }
        }
    }

    // Adds w times the body's offset/turn/hidden at frame f into out[7].
    public static void root(Mode m, float f, boolean loop, float w, float[] out) {
        if (m == null || w <= 0) {
            return;
        }
        for (int k = 0; k < ROOT; k++) {
            if (m.root[k] != null) {
                out[k] += at(m.root[k], m, f, loop) * w;
            }
        }
    }

    // lowest body height of a mode (the idle of a flyer hovers 1-2 blocks up in the preview; in the game it flies for real)
    public float minY(String name) {
        Mode m = modes.get(name);
        if (m == null || m.root[1] == null) {
            return 0;
        }
        float lo = Float.MAX_VALUE;
        for (float y : m.root[1]) {
            lo = Math.min(lo, y);
        }
        return lo;
    }
}
