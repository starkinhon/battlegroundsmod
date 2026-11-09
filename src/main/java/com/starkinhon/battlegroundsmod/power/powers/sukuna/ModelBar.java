package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Sukuna's Modelo bar (Stark, 2026-11-09): like Sukuna studying how Mahoraga adapted to the Infinity, he studies the
// world's defenses. It fills when his cuts land (Z, Shift+Z, Clivar) and much faster when they hit a defense (a guard,
// a higher tier's Escudo, an intangible body: Bills' space); it has to be hard and slow to fill. Full, the chant gets its
// fourth stage: the World Cutting Slash (Desmantelar). Being hit during the gesture halves it; the slash empties it. A
// new body (death) starts empty. Shown by client/ModelBarHud.
public final class ModelBar {
    // How much a landed cut gives (times its weight: a Shift+Z line is a quarter of a Z), clean and against a defense.
    public static final float HIT = 0.012f, DEFENDED = 0.06f;
    private static final Map<LivingEntity, Float> VALUES = new WeakHashMap<>();
    private static final Map<LivingEntity, Float> SENT = new WeakHashMap<>();

    private ModelBar() {
    }

    public static float get(LivingEntity entity) {
        return VALUES.getOrDefault(entity, 0f);
    }

    public static boolean full(LivingEntity entity) {
        return get(entity) >= 1;
    }

    public static void set(LivingEntity entity, float value) {
        VALUES.put(entity, Mth.clamp(value, 0, 1));
        sync(entity);
    }

    // One of his cuts is about to land on target: more when it lands on a defense. Call before the hit.
    public static void landed(LivingEntity attacker, LivingEntity target, float weight) {
        set(attacker, get(attacker) + (defended(attacker, target) ? DEFENDED : HIT) * weight);
    }

    // Whether a blow from attacker would meet a defense: a guard facing it, an untouchable body, a higher tier's Escudo.
    public static boolean defended(LivingEntity attacker, LivingEntity target) {
        return Defense.check(target, attacker) != Defense.Result.NONE || Intangible.untouchableBy(target, attacker)
                || Tiers.shield(target) > Tiers.shield(attacker);
    }

    private static void sync(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        float value = get(entity);
        Float sent = SENT.get(entity);
        if (sent == null || Math.abs(sent - value) >= 0.004f || (value >= 1) != (sent >= 1) || value == 0 && sent != 0) {
            SENT.put(entity, value);
            PacketDistributor.sendToPlayer(player, new ModPayloads.SukunaModel(value));
        }
    }
}
