package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.BossOrder;

import net.minecraft.world.phys.Vec3;

// A boss's own way of reading the fight, on top of SoulBossAI (which every boss shares and which reads only distance,
// cooldowns, stuns and pressure). A boss that has one (SoulBoss.tactics, the Gojo boss) gets asked, every decision and
// every move, what the situation calls for. Everything defaults to "no opinion".
public interface BossTactics {
    // Called every tick the AI runs, before it reacts or decides. True = it used this tick (an Infinity stop, a blink).
    default boolean act(SoulBossAI ai) {
        return false;
    }

    // The score SoulBossAI gave a move (a skill, or a combo when combo is true) for this decision, reweighed for the
    // situation. 0 forbids it.
    default double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        return score;
    }

    // How a held technique is used this time (BossOrder), called right before it is cast. null = its own random.
    default BossOrder.Order order(SoulBossAI ai, String name) {
        return null;
    }

    // Where a move aims instead of the target's body, or null.
    default Vec3 aim(SoulBossAI ai, String name) {
        return null;
    }

    // A ranged move it may fire at a target it can't see (it goes through the walls).
    default boolean throughWalls(SoulBossAI ai, String name) {
        return false;
    }

    // A combo step only the tactics know. null = not theirs; true = done (go to the next step); false = still on it
    // (they move the boss themselves this tick).
    default Boolean step(SoulBossAI ai, String step, int stepAge) {
        return null;
    }

    // A technique this boss uses as freely as its punches: no gap after other techniques, and it opens none
    // (Sukuna's Desmantelar taps).
    default boolean basic(String name) {
        return false;
    }

    // What it is thinking, for /soul boss debug.
    default String mood() {
        return "";
    }
}
