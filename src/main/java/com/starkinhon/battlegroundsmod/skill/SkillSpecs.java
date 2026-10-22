package com.starkinhon.battlegroundsmod.skill;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// Loads skill specs bundled in the mod jar. Both sides read the same file, so the client knows the animation timing.
// A skill can have a "despertado" block with only the values that change in awakened mode; it is laid over the rest.
public final class SkillSpecs {
    private static final Gson GSON = new Gson();
    private static final Map<String, Object> CACHE = new HashMap<>();

    private SkillSpecs() {
    }

    public static synchronized <T> T get(String id, Class<T> type, boolean awakened) {
        return type.cast(CACHE.computeIfAbsent(id + (awakened ? "#despertado" : ""), key -> GSON.fromJson(json(id, awakened), type)));
    }

    private static JsonObject json(String id, boolean awakened) {
        JsonObject json = read(id);
        JsonElement awakenedBlock = json.remove("despertado");
        if (awakened && awakenedBlock instanceof JsonObject overrides) {
            merge(json, overrides);
        }
        return json;
    }

    // Objects merge key by key; anything else (numbers, lists, text) replaces the base value.
    private static void merge(JsonObject base, JsonObject overrides) {
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            JsonElement current = base.get(entry.getKey());
            if (current instanceof JsonObject currentObject && entry.getValue() instanceof JsonObject overrideObject) {
                merge(currentObject, overrideObject);
            } else {
                base.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    private static JsonObject read(String id) {
        String path = "/data/" + BattlegroundsMod.MODID + "/souls_skills/" + id + ".json";
        try (InputStream in = SkillSpecs.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing skill file " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load skill {}", id, e);
            throw new IllegalStateException("Could not load skill " + id, e);
        }
    }
}
