package com.starkinhon.battlegroundsmod.combat;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;

// The rule of repeated blows (Stark, 2026-11-08): when one skill lands many small hits on the same target (the 40 balls
// of Bills' Rain), each hit keeps NEXT of the one before, so all of them together never pass about 3.3 full hits.
// Counted per attacker, target and skill; the count starts over after GAP ticks with no hit. Kokushibo's forms share
// their total the same way inside MoonFormSkill.
public final class RepeatHits {
    private static final float NEXT = 0.7f;
    private static final int GAP = 40;
    private static final Map<String, long[]> COUNTS = new HashMap<>();

    private RepeatHits() {
    }

    // The share of its damage this hit deals (1 for the first, 0.7 for the second, 0.49...).
    public static float share(LivingEntity attacker, LivingEntity target, String skill) {
        long now = attacker.level().getGameTime();
        if (COUNTS.size() > 512) {
            COUNTS.values().removeIf(c -> now - c[1] > GAP);
        }
        String key = attacker.getId() + ":" + target.getId() + ":" + skill;
        long[] c = COUNTS.get(key);
        if (c == null || now - c[1] > GAP) {
            c = new long[] {0, now};
            COUNTS.put(key, c);
        }
        c[0]++;
        c[1] = now;
        return (float) Math.pow(NEXT, c[0] - 1);
    }
}
