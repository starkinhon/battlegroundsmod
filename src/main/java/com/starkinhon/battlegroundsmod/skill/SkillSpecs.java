package com.starkinhon.battlegroundsmod.skill;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

// Loads skill specs bundled in the mod jar. Both sides read the same file, so the client knows the animation timing.
public final class SkillSpecs {
    private static final Gson GSON = new Gson();
    private static final Map<String, SkillSpec> CACHE = new HashMap<>();

    private SkillSpecs() {
    }

    public static synchronized SkillSpec get(String id) {
        return CACHE.computeIfAbsent(id, SkillSpecs::load);
    }

    private static SkillSpec load(String id) {
        String path = "/data/" + BattlegroundsMod.MODID + "/souls_skills/" + id + ".json";
        try (InputStream in = SkillSpecs.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing skill file " + path);
            }
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SkillSpec.class);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load skill {}", id, e);
            throw new IllegalStateException("Could not load skill " + id, e);
        }
    }
}
