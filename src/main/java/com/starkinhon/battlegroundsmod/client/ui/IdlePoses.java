package com.starkinhon.battlegroundsmod.client.ui;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

// The still pose of each challenger on the book's stage and the little moves it makes now and then (approved in the
// preview, Stark 2026-11-13: "poses idle dos personagens de DB"): Kuririn shadow boxing and hopping, Piccolo floating
// cross-legged, Kame stroking his beard, Goku scratching his head, Vegeta turning away, Freeza floating with his finger
// up, Bills yawning. Read from assets/battlegroundsmod/ui/idle_poses.json (the preview's IDLE table, same numbers): base
// is the still pose, each clip a list of [time 0..1, what changes] keys eased with smootherstep.
public final class IdlePoses {
    private record Key(float t, Map<String, float[]> v) {
    }

    private record Clip(float d, List<Key> keys) {
    }

    private record Idle(Map<String, float[]> base, List<Clip> clips) {
    }

    private static Map<String, Idle> all;
    private static String currentId;
    private static int clip = -1, last = -1;
    private static float start, next;

    private IdlePoses() {
    }

    private static Map<String, Idle> all() {
        if (all == null) {
            all = new HashMap<>();
            try (var in = Minecraft.getInstance().getResourceManager()
                    .open(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "ui/idle_poses.json"))) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    List<Clip> clips = new ArrayList<>();
                    for (JsonElement c : o.getAsJsonArray("clips")) {
                        JsonObject co = c.getAsJsonObject();
                        List<Key> keys = new ArrayList<>();
                        for (JsonElement k : co.getAsJsonArray("k")) {
                            JsonArray ka = k.getAsJsonArray();
                            keys.add(new Key(ka.get(0).getAsFloat(), values(ka.get(1).getAsJsonObject())));
                        }
                        clips.add(new Clip(co.get("d").getAsFloat(), keys));
                    }
                    all.put(e.getKey(), new Idle(values(o.getAsJsonObject("base")), clips));
                }
            } catch (Exception e) {
                BattlegroundsMod.LOGGER.error("Livro: não li ui/idle_poses.json", e);
            }
        }
        return all;
    }

    private static Map<String, float[]> values(JsonObject o) {
        Map<String, float[]> out = new HashMap<>();
        for (var e : o.entrySet()) {
            if (e.getValue().isJsonArray()) {
                JsonArray a = e.getValue().getAsJsonArray();
                out.put(e.getKey(), new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()});
            } else {
                out.put(e.getKey(), new float[] {e.getValue().getAsFloat()});
            }
        }
        return out;
    }

    public static boolean has(String id) {
        return all().containsKey(id);
    }

    private static Map<String, float[]> over(Map<String, float[]> base, Map<String, float[]> add) {
        Map<String, float[]> out = new HashMap<>(base);
        out.putAll(add);
        return out;
    }

    private static Map<String, float[]> mixPose(Map<String, float[]> a, Map<String, float[]> b, float e) {
        Map<String, float[]> out = new HashMap<>();
        java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (String k : keys) {
            float[] va = a.get(k), vb = b.get(k);
            if (va == null) {
                va = vb.length == 2 ? new float[] {0, 0} : new float[] {0};
            }
            if (vb == null) {
                vb = va;
            }
            float[] o = new float[va.length];
            for (int i = 0; i < va.length; i++) {
                o[i] = Px.lerp(va[i], vb.length > i ? vb[i] : va[i], e);
            }
            out.put(k, o);
        }
        return out;
    }

    private static float one(Map<String, float[]> p, String k) {
        float[] v = p.get(k);
        return v == null ? 0 : v[0];
    }

    private static float[] two(Map<String, float[]> p, String k) {
        float[] v = p.get(k);
        return v == null || v.length < 2 ? new float[] {0, 0} : v.clone();
    }

    // The pose of this character now (null when it has none: the stage uses the plain breathing stand).
    public static SkinDraw.Pose pose(String id, float time) {
        Idle idle = all().get(id);
        if (idle == null) {
            return null;
        }
        Map<String, float[]> base = new HashMap<>();
        base.put("lift", new float[] {0});
        base.put("yaw", new float[] {0});
        base.putAll(idle.base());
        if (!id.equals(currentId)) {
            currentId = id;
            clip = -1;
            next = time + 1.6f;
            last = -1;
        }
        Map<String, float[]> p = base;
        if (clip < 0 && time >= next && !idle.clips().isEmpty()) {
            int n = (int) (Math.random() * idle.clips().size());
            if (n == last && idle.clips().size() > 1) {
                n = (n + 1) % idle.clips().size();
            }
            clip = n;
            start = time;
        }
        if (clip >= 0) {
            Clip c = idle.clips().get(clip);
            float u = (time - start) / c.d();
            if (u >= 1) {
                last = clip;
                clip = -1;
                next = time + 3 + (float) Math.random() * 3;
            } else {
                List<Key> ks = c.keys();
                int i = 0;
                while (i < ks.size() - 2 && u > ks.get(i + 1).t()) {
                    i++;
                }
                Key k0 = ks.get(i), k1 = ks.get(i + 1);
                float e = Px.smoother(Px.clamp((u - k0.t()) / (k1.t() - k0.t()), 0, 1));
                p = mixPose(over(base, k0.v()), over(base, k1.v()), e);
            }
        }
        // the smallest breathing: shoulders and floating; the head stays still
        float br = (float) Math.sin(time * 1.7);
        SkinDraw.Pose out = new SkinDraw.Pose();
        out.rarm = two(p, "rarm");
        out.larm = two(p, "larm");
        out.rleg = two(p, "rleg");
        out.lleg = two(p, "lleg");
        out.rarm[1] -= 0.015f * br;
        out.larm[1] += 0.015f * br;
        out.head = one(p, "head");
        out.yaw = one(p, "yaw");
        float baseLift = one(base, "lift");
        out.lift = one(p, "lift") + (baseLift > 0 ? 0.6f * (float) Math.sin(time * 1.3) : 0.15f * br);
        return out;
    }

    // The talking NPC's gestures (the preview's TALKG): a loop of hand moves over its still pose while the line types.
    private static final float[][] TALK_T = {{0}, {0.18f}, {0.34f}, {0.52f}, {0.7f}, {0.86f}, {1}};

    public static SkinDraw.Pose talking(float u, boolean talking) {
        float[][] r = {{-0.12f, -0.06f}, {-0.75f, 0.18f}, {-0.62f, 0.1f}, {-0.12f, -0.06f}, {-0.85f, 0.22f}, {-0.4f, 0.08f}, {-0.12f, -0.06f}};
        float[][] l = {{0.06f, 0.08f}, {0.06f, 0.08f}, {-0.35f, -0.08f}, {-0.55f, -0.2f}, {0.06f, 0.08f}, {0.06f, 0.08f}, {0.06f, 0.08f}};
        SkinDraw.Pose p = new SkinDraw.Pose();
        p.rarm = r[0].clone();
        p.larm = l[0].clone();
        p.rleg = new float[] {0, -0.03f};
        p.lleg = new float[] {0, 0.03f};
        p.head = 0.02f;
        if (!talking) {
            return p;
        }
        int i = 0;
        while (i < TALK_T.length - 2 && u > TALK_T[i + 1][0]) {
            i++;
        }
        float t0 = TALK_T[i][0], t1 = TALK_T[i + 1][0];
        float e = Px.smoother(Px.clamp((u - t0) / (t1 - t0), 0, 1));
        p.rarm = new float[] {Px.lerp(r[i][0], r[i + 1][0], e), Px.lerp(r[i][1], r[i + 1][1], e)};
        p.larm = new float[] {Px.lerp(l[i][0], l[i + 1][0], e), Px.lerp(l[i][1], l[i + 1][1], e)};
        return p;
    }
}
