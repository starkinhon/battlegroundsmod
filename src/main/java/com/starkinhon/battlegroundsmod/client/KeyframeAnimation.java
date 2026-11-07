package com.starkinhon.battlegroundsmod.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// A body animation made by hand in an animation editor, keyframe by keyframe, for a skill:
// assets/battlegroundsmod/animations/<skill>.json, in the same shape as Minecraft's own animations:
// {"length": s, "animations": [{"bone": "Right Arm", "target": "rotation" | "position" | "scale",
//   "keyframes": [{"timestamp": s, "target": [x, y, z], "interpolation": "linear" | "catmullrom"}]}]}
// Rotations are degrees and positions pixels, as the editor shows them (x and y flipped into Minecraft's axes). Before the first keyframe a
// channel holds its first value, after the last its last, so the final pose stays as long as the skill wants it.
// Bones: Head, Body, Right Arm, Left Arm, Right Leg, Left Leg (spaces, underscores and case don't matter).
// One more bone, Item, turns what the right hand holds about the wrist (the sword moves): its rotation is degrees in
// the hand's own frame, applied Z then Y then X, with nothing flipped (see SkillAnimations.wrist).
public final class KeyframeAnimation {
    private record Keyframe(float time, float x, float y, float z, boolean smooth) {
    }

    // target: 0 rotation, 1 position, 2 scale.
    private record Channel(String bone, int target, Keyframe[] keys) {
    }

    private static final Map<String, Optional<KeyframeAnimation>> CACHE = new HashMap<>();
    private static final float DEG = (float) (Math.PI / 180);

    private final List<Channel> channels;
    private final Keyframe[] wrist;
    private final float length;

    private KeyframeAnimation(List<Channel> channels, Keyframe[] wrist, float length) {
        this.channels = channels;
        this.wrist = wrist;
        this.length = length;
    }

    // Seconds, from the file's "length" (or the last keyframe).
    public float length() {
        return length;
    }

    // The hand's turn at this time (radians x, y, z, scaled by weight), or null when the animation doesn't turn it.
    public float[] wrist(float seconds, float weight) {
        if (wrist == null) {
            return null;
        }
        float[] v = sample(wrist, seconds);
        return new float[] {v[0] * DEG * weight, v[1] * DEG * weight, v[2] * DEG * weight};
    }

    // The hand-made animation of this skill, if it has one.
    public static Optional<KeyframeAnimation> of(String skill) {
        return CACHE.computeIfAbsent(skill, KeyframeAnimation::load);
    }

    private static Optional<KeyframeAnimation> load(String skill) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "animations/" + skill + ".json");
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Channel> channels = new ArrayList<>();
            Keyframe[] wrist = null;
            float end = 0;
            for (JsonElement element : root.getAsJsonArray("animations")) {
                JsonObject anim = element.getAsJsonObject();
                String bone = anim.get("bone").getAsString().toLowerCase().replace(" ", "").replace("_", "");
                String targetName = anim.get("target").getAsString();
                int target = "position".equals(targetName) ? 1 : "scale".equals(targetName) ? 2 : 0;
                List<Keyframe> keys = new ArrayList<>();
                for (JsonElement k : anim.getAsJsonArray("keyframes")) {
                    JsonObject key = k.getAsJsonObject();
                    JsonArray v = key.getAsJsonArray("target");
                    String interpolation = key.has("interpolation") ? key.get("interpolation").getAsString() : "linear";
                    keys.add(new Keyframe(key.get("timestamp").getAsFloat(), v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat(),
                            "catmullrom".equals(interpolation)));
                }
                keys.sort((a, b) -> Float.compare(a.time(), b.time()));
                if (!keys.isEmpty()) {
                    end = Math.max(end, keys.get(keys.size() - 1).time());
                }
                if (!keys.isEmpty() && "item".equals(bone)) {
                    wrist = keys.toArray(new Keyframe[0]);
                } else if (!keys.isEmpty()) {
                    channels.add(new Channel(bone, target, keys.toArray(new Keyframe[0])));
                }
            }
            float length = root.has("length") ? root.get("length").getAsFloat() : end;
            return Optional.of(new KeyframeAnimation(channels, wrist, length));
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Bad animation {}", id, e);
            return Optional.empty();
        }
    }

    // Poses the model at time seconds, weight 0-1 (to blend it out at the end).
    public void apply(HumanoidModel<?> model, float seconds, float weight) {
        for (Channel channel : channels) {
            ModelPart part = part(model, channel.bone());
            if (part == null) {
                continue;
            }
            float[] v = sample(channel.keys(), seconds);
            // The editor's axes are Minecraft's turned half round the forward axis (its x and y point the other way, as
            // in Blockbench), so x and y flip, for both turning and moving.
            if (channel.target() == 2) {
                // (scale is the same on every axis, so nothing flips; 1 is the normal size)
                part.xScale = 1 + (v[0] - 1) * weight;
                part.yScale = 1 + (v[1] - 1) * weight;
                part.zScale = 1 + (v[2] - 1) * weight;
            } else if (channel.target() == 1) {
                part.x -= v[0] * weight;
                part.y -= v[1] * weight;
                part.z += v[2] * weight;
            } else {
                part.xRot -= v[0] * DEG * weight;
                part.yRot -= v[1] * DEG * weight;
                part.zRot += v[2] * DEG * weight;
            }
        }
    }

    private static ModelPart part(HumanoidModel<?> model, String bone) {
        return switch (bone) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "rightarm" -> model.rightArm;
            case "leftarm" -> model.leftArm;
            case "rightleg" -> model.rightLeg;
            case "leftleg" -> model.leftLeg;
            default -> null;
        };
    }

    // Like Minecraft's own: linear, or Catmull-Rom through the keyframes around (ends repeated).
    private static float[] sample(Keyframe[] keys, float t) {
        if (t <= keys[0].time()) {
            return values(keys[0]);
        }
        int last = keys.length - 1;
        if (t >= keys[last].time()) {
            return values(keys[last]);
        }
        int i = 0;
        while (i < last - 1 && keys[i + 1].time() <= t) {
            i++;
        }
        Keyframe a = keys[i];
        Keyframe b = keys[i + 1];
        float u = (t - a.time()) / Math.max(1.0E-4f, b.time() - a.time());
        float[] va = values(a);
        float[] vb = values(b);
        float[] out = new float[3];
        if (b.smooth()) {
            float[] v0 = values(keys[Math.max(0, i - 1)]);
            float[] v3 = values(keys[Math.min(last, i + 2)]);
            for (int c = 0; c < 3; c++) {
                out[c] = Mth.catmullrom(u, v0[c], va[c], vb[c], v3[c]);
            }
        } else {
            for (int c = 0; c < 3; c++) {
                out[c] = Mth.lerp(u, va[c], vb[c]);
            }
        }
        return out;
    }

    private static float[] values(Keyframe k) {
        return new float[] {k.x(), k.y(), k.z()};
    }
}
