// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.boss.BossFalas;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.item.NichirinSwordItem;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// Espada Nichirin against demons (Stark, 2026-11-19: "essa Nichirin vai acabar com ele"). In the manga a Nichirin wound is
// slow to close and only the neck cut (or the sun) kills an oni. Each cut on a demon (the common ones, the Kokushibo boss,
// a player with Kokushibo's soul):
// - Ferida do Sol: one wound for WOUND_TICKS (every cut renews them), up to MAX_WOUNDS. Each takes REGEN_CUT_PER_WOUND
//   of his regeneration away (OniBody.tick); with all of them it stops.
// - BONUS of his max health on top of the blow (the Barragem's cuts give their share only).
// - Corte no pescoço: with all the wounds and him at or under NECK_AT of his life, a charged Ataque Forte (half charge or
//   more) takes the head. A common oni dies. The Kokushibo boss heals no more for the rest of the fight, takes NECK_BONUS
//   and stands stunned (and says the manga's line); a player oni heals no more for NECK_PLAYER_TICKS.
// The attacker sees the wounds on the action bar, so the neck cut is never a riddle.
public final class NichirinWounds {
    public static final int MAX_WOUNDS = 5;
    private static final int WOUND_TICKS = 160;
    private static final double REGEN_CUT_PER_WOUND = 0.2;
    private static final float BONUS = 0.02f;
    private static final float BONUS_MIN = 4f;
    private static final float NECK_AT = 0.15f;
    private static final float NECK_BONUS = 0.10f;
    private static final int NECK_STUN = 40;
    private static final int NECK_PLAYER_TICKS = 600;

    private static final String TAG = "battlegroundsmod_nichirin";
    private static final String WOUNDS = "wounds", UNTIL = "until", NECK = "neck";

    private NichirinWounds() {
    }

    public static boolean wielding(LivingEntity attacker) {
        return attacker != null && attacker.getMainHandItem().getItem() instanceof NichirinSwordItem;
    }

    public static boolean oni(LivingEntity target) {
        return DemonSlayerWorld.isOni(target) || OniBody.is(target);
    }

    private static CompoundTag tag(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(TAG)) {
            root.put(TAG, new CompoundTag());
        }
        return root.getCompound(TAG);
    }

    // Wounds still open on him now.
    public static int wounds(LivingEntity entity) {
        if (!entity.getPersistentData().contains(TAG)) {
            return 0;
        }
        CompoundTag tag = tag(entity);
        return entity.level().getGameTime() < tag.getLong(UNTIL) ? tag.getInt(WOUNDS) : 0;
    }

    // The share of his regeneration left (1 = untouched, 0 = none).
    public static double regenFactor(LivingEntity entity) {
        if (!entity.getPersistentData().contains(TAG)) {
            return 1;
        }
        CompoundTag tag = tag(entity);
        long now = entity.level().getGameTime();
        if (tag.getLong(NECK) == Long.MAX_VALUE || now < tag.getLong(NECK)) {
            return 0;
        }
        return Math.max(0, 1 - REGEN_CUT_PER_WOUND * wounds(entity));
    }

    // After a sword blow landed. scale: the share of the bonus this cut gives (the Barragem's are small); heavy: a charged
    // Ataque Forte, the only cut that takes the head.
    public static void cut(LivingEntity attacker, LivingEntity target, float scale, boolean heavy) {
        if (!wielding(attacker) || !oni(target) || !target.isAlive() || target.level().isClientSide) {
            return;
        }
        CompoundTag tag = tag(target);
        long now = target.level().getGameTime();
        int wounds = Math.min(MAX_WOUNDS, wounds(target) + 1);
        tag.putInt(WOUNDS, wounds);
        tag.putLong(UNTIL, now + WOUND_TICKS);
        DamageSource source = attacker instanceof Player player ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
        float bonus = Math.max(BONUS_MIN, target.getMaxHealth() * BONUS) * scale;
        target.invulnerableTime = 0;
        target.hurt(source, bonus);
        boolean neckDone = tag.getLong(NECK) == Long.MAX_VALUE;
        if (heavy && wounds >= MAX_WOUNDS && target.isAlive() && !neckDone && target.getHealth() <= target.getMaxHealth() * NECK_AT) {
            neck(attacker, target, tag, source, now);
            return;
        }
        if (attacker instanceof ServerPlayer player && target.isAlive()) {
            boolean ready = wounds >= MAX_WOUNDS && !neckDone && target.getHealth() <= target.getMaxHealth() * NECK_AT;
            Component text = ready
                    ? Component.translatable("message.battlegroundsmod.nichirin.neck_ready").withStyle(s -> s.withColor(0xFF7A2E).withBold(true))
                    : Component.translatable("message.battlegroundsmod.nichirin.wounds", bar(wounds)).withStyle(s -> s.withColor(0xFFB25A));
            player.displayClientMessage(text, true);
        }
    }

    private static String bar(int wounds) {
        return "■".repeat(wounds) + "□".repeat(MAX_WOUNDS - wounds);
    }

    private static void neck(LivingEntity attacker, LivingEntity target, CompoundTag tag, DamageSource source, long now) {
        if (attacker instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.nichirin.neck")
                    .withStyle(s -> s.withColor(0xFF4A2A).withBold(true)), true);
        }
        if (target instanceof SoulBoss) {
            // He lives on without his head (the manga), but heals no more: the end is near.
            tag.putLong(NECK, Long.MAX_VALUE);
            target.invulnerableTime = 0;
            target.hurt(source, target.getMaxHealth() * NECK_BONUS);
            if (target.isAlive()) {
                Stun.apply(target, NECK_STUN);
                BossFalas.sayNow(target, "fala.battlegroundsmod.kokushibo.pouca_vida");
            }
        } else if (target instanceof Player) {
            tag.putLong(NECK, now + NECK_PLAYER_TICKS);
            target.invulnerableTime = 0;
            target.hurt(source, target.getMaxHealth() * NECK_BONUS);
            if (target.isAlive()) {
                Stun.apply(target, NECK_STUN);
            }
        } else {
            target.invulnerableTime = 0;
            target.hurt(source, Float.MAX_VALUE);
        }
    }
}
