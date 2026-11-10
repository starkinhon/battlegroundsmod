package com.starkinhon.soulsjjk.client;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.soulsjjk.boss.BossSpec;
import com.starkinhon.soulsjjk.boss.SoulBoss;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

// The bosses' own way of standing, walking and running (Stark, 2026-11-07): Bills floats with his hands behind his
// back and glides instead of walking, Madara keeps his arms crossed and runs like a ninja. Made in
// tools/poses/locomocao.js and baked to animations/pose.<character>.<state>.json.
// Only on the bosses: on players it looked out of place (Stark, 2026-11-07), so players, Aizen's illusions and bosses
// without an entry (Gojo) keep Minecraft's walk. The player version is kept on the branch archive/poses-padrao.
// Skills, guard and cutscenes always win over this.
public final class StancePoses {
    // power id -> the character whose poses it uses
    private static final Map<String, String> CHARACTER = Map.of(
            "destruction_god", "bills",
            "uchiha_ghost", "madara");
    private static final String[] STATES = {"parado", "andar", "correr"};
    // How far Minecraft's walk counter (limbSwing) runs per tick at walking and at sprinting speed, so one loop of
    // andar / correr is one stride on the ground at that speed.
    private static final float WALK_SWING = 0.86f;
    private static final float RUN_SWING = 1.0f;
    // Blocks per tick above which a mob counts as running (walk 0.216, sprint 0.281).
    private static final double RUN_SPEED = 0.25;
    // Ticks to blend between standing, walking and running.
    private static final float BLEND_TICKS = 4;

    // Per entity: last ageInTicks seen, how much it is moving (0-1), how much of that is running (0-1).
    private static final Map<Integer, float[]> STATE = new HashMap<>();
    private static Object lastLevel;

    private StancePoses() {
    }

    private static String character(LivingEntity entity) {
        var level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            STATE.clear();
            lastLevel = level;
        }
        return null;
    }

    // Does this entity stand in its character's pose right now? Not while crouching, swimming, flying, sleeping,
    // riding, using an item or swinging an arm (those keep Minecraft's pose).
    public static boolean wants(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        return !(ageInTicks == 0 || character(entity) == null || !entity.isAlive() || entity.isPassenger() || entity.isUsingItem()
                || entity.isFallFlying() || entity.isVisuallySwimming() || entity.isAutoSpinAttack() || entity.getPose() != Pose.STANDING
                || model.crouching || model.attackTime > 0);
    }

    // Adds the pose on top of a model already reset to Minecraft's standing pose (SkillAnimations.standStill),
    // weight 0-1 (to blend it in as a skill blends out).
    public static void pose(LivingEntity entity, HumanoidModel<?> model, float ageInTicks, float weight) {
        String who = character(entity);
        if (who == null || weight <= 0) {
            return;
        }
        float partial = ageInTicks - entity.tickCount;
        float amount = Math.min(1, entity.walkAnimation.speed(partial));
        float swing = entity.walkAnimation.position(partial);
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        boolean running = entity.isSprinting() || dx * dx + dz * dz > RUN_SPEED * RUN_SPEED;
        float[] s = STATE.computeIfAbsent(entity.getId(), k -> new float[] {ageInTicks, 0, 0});
        float dt = Math.max(0, Math.min(2, ageInTicks - s[0]));
        s[0] = ageInTicks;
        float step = dt / BLEND_TICKS;
        s[1] = approach(s[1], Math.min(1, amount / 0.5f), step);
        s[2] = approach(s[2], running ? 1 : 0, step);
        float move = smooth(s[1]);
        float run = smooth(s[2]);
        float[] w = {1 - move, move * (1 - run), move * run};
        float[] seconds = {ageInTicks / 20f, swing / WALK_SWING / 20f, swing / RUN_SWING / 20f};
        for (int i = 0; i < 3; i++) {
            if (w[i] <= 0.001f) {
                continue;
            }
            int state = i;
            KeyframeAnimation.of("pose." + who + "." + STATES[i]).ifPresent(a -> {
                float len = a.length();
                float t = len > 0 ? seconds[state] % len : 0;
                a.apply(model, t < 0 ? t + len : t, w[state] * weight);
            });
        }
    }

    private static float approach(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }
}
