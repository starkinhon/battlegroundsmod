// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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

// The still pose of each challenger on the book's stage and the little moves it makes now and then. Remade on
// 2026-11-21 (Stark: "pega elas e refaz, aplicando a nova regra não só nos braços, mas pernas tmb"): every key is now the
// whole body in absolute numbers, baked from the approved preview (forja/livro-idles), worked out fist first and foot
// first (estudo-animacao section 11, issue #480): the hand lands where it should and the shoulder follows, the foot stays
// planted on the ground and the hips drop when the knees bend. Read from assets/battlegroundsmod/ui/idle_poses.json:
// base is the still pose, each clip a list of [time 0..1, pose] keys eased with smootherstep. A pose is 38 numbers:
// body, head, right arm, left arm, right leg, left leg as [x, y, z pivot in skin pixels, xRot, yRot, zRot in degrees],
// then lift (skin pixels up) and yaw (radians).
public final class IdlePoses {
    private record Key(float t, float[] v) {
    }

    private record Clip(float d, List<Key> keys) {
    }

    private record Idle(float[] base, List<Clip> clips) {
    }

    public static final int SIZE = 38, LIFT = 36, YAW = 37;

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
                            keys.add(new Key(ka.get(0).getAsFloat(), values(ka.get(1).getAsJsonArray())));
                        }
                        clips.add(new Clip(co.get("d").getAsFloat(), keys));
                    }
                    all.put(e.getKey(), new Idle(values(o.getAsJsonArray("base")), clips));
                }
            } catch (Exception e) {
                BattlegroundsMod.LOGGER.error("Livro: não li ui/idle_poses.json", e);
            }
        }
        return all;
    }

    private static float[] values(JsonArray a) {
        float[] out = new float[SIZE];
        for (int i = 0; i < SIZE && i < a.size(); i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    public static boolean has(String id) {
        return all().containsKey(id);
    }

    // The pose of this character now (null when it has none: the stage uses the plain breathing stand).
    public static SkinDraw.Pose pose(String id, float time) {
        Idle idle = all().get(id);
        if (idle == null) {
            return null;
        }
        if (!id.equals(currentId)) {
            currentId = id;
            clip = -1;
            next = time + 1.6f;
            last = -1;
        }
        float[] p = idle.base();
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
                p = new float[SIZE];
                for (int j = 0; j < SIZE; j++) {
                    p[j] = Px.lerp(k0.v()[j], k1.v()[j], e);
                }
            }
        }
        // the smallest breathing: shoulders (zRot of each arm) and floating; the head stays still
        float br = (float) Math.sin(time * 1.7);
        float[] abs = p.clone();
        abs[17] += 0.9f * br;
        abs[23] -= 0.9f * br;
        SkinDraw.Pose out = new SkinDraw.Pose();
        out.abs = abs;
        out.yaw = p[YAW];
        out.lift = p[LIFT] + (idle.base()[LIFT] != 0 ? 0.6f * (float) Math.sin(time * 1.3) : 0);
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
