// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.opm;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// Saitama (Cara de um Soco, Stark 2026-11-11, remade 2026-11-25): what is always on, and the Modo Serio.
// Pedra Viva is gone (Stark 2026-11-25: standing still he only got beaten): he is pushed like anyone. High resistance: he
// takes a lot of beating. And he hits back out of a stun (shrugOff): the base fighting (punch, Dash, Barragem, Forte), never
// a skill, breaks the stun for some stamina, at most once every 8 s, so it is a counter at the right time and not an
// immunity (a Clivar + Fuga still lands when it is spent). The ult bar is his Barra Seria: it fills dealing damage (slower than
// everyone, FILL) and taking it. Full, V turns on the Modo Serio for 20 s: each key becomes a serious move, three of them in all (Stark
// 2026-11-20); the Soco Serio always ends the mode on the spot, as the final blow. If the mode runs out unused, the bar comes
// back full.
public final class Opm {
    public static final String ID = "one_punch";
    public static final String SERIO = "opm_serio";
    // Armor while winding up a serious move (the preview's 1-2 s of silence must not be a free hit).
    public static final String ARMOR = "opm_armor";
    // B: the counter's window.
    public static final String COUNTER = "opm_counter";
    public static final int SERIO_TICKS = 400, SERIO_MOVES = 3;
    // Share of every blow that still lands (high resistance), and while winding up a serious move.
    private static final float RESIST = 0.7f, ARMORED = 0.35f;
    // Taking damage fills the Barra Seria: losing the whole life would fill this much of it (2026-11-26 playtest: the bar
    // filled far too easily; was 220 / 18 a hit).
    private static final float TAKEN_FILL = 90f, TAKEN_MAX = 6f;
    // Dealing damage fills it at this share of anyone's ult bar. Was 2.0 (twice as fast); the playtest of 2026-11-26 found
    // the Serio coming every few exchanges: three serious moves at 1.5x are worth a long wait.
    public static final float FILL = 0.6f;
    // B Counter: what a caught blow adds to the bar (was 15).
    public static final float COUNTER_FILL = 6f;
    // shrugOff: the stamina it costs (percent of the bar) and the ticks between two.
    private static final double SHRUG_COST = 18;
    private static final int SHRUG_GAP = 160;

    // Bosses and bots with his power (players are found every tick anyway).
    private static final Set<LivingEntity> MOBS = Collections.newSetFromMap(new WeakHashMap<>());
    // Who turned the mode on and has not used it yet.
    private static final Map<LivingEntity, Boolean> UNUSED = new WeakHashMap<>();
    // Serious moves left in the mode on now.
    private static final Map<LivingEntity, Integer> LEFT = new WeakHashMap<>();
    // When each one last shrugged a stun off.
    private static final Map<LivingEntity, Long> SHRUGS = new WeakHashMap<>();

    private Opm() {
    }

    public static boolean is(Entity entity) {
        return entity instanceof LivingEntity living && living.hasData(ModAttachments.POWER) && ID.equals(living.getData(ModAttachments.POWER).power);
    }

    public static void track(LivingEntity caster) {
        if (!(caster instanceof ServerPlayer)) {
            MOBS.add(caster);
        }
    }

    public static boolean serious(LivingEntity caster) {
        return SubCommon.hasState(caster, SERIO);
    }

    // V in the Modo Serio needs no bar (the Espirro is the move the mode already paid for).
    public static boolean freeUlt(LivingEntity caster) {
        return is(caster) && serious(caster);
    }

    // ---------- the Modo Serio ----------

    public static void turnSerious(LivingEntity caster, ServerLevel level) {
        SubCommon.setState(caster, SERIO, SERIO_TICKS);
        UNUSED.put(caster, true);
        LEFT.put(caster, SERIO_MOVES);
        PowerData data = caster.getData(ModAttachments.POWER);
        // Every key ready for its serious move.
        for (int i = 0; i < 4; i++) {
            data.cooldowns[i] = 0;
        }
        SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), caster.getLookAngle(), 1, SERIO_TICKS, SERIO_MOVES);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.6f);
        // the ground under him cracks open as the mode turns on
        OpmSerious.wake(caster, level);
        SubCommon.warn(caster, "opm.serio_on");
        long until = level.getGameTime() + SERIO_TICKS;
        Tasks.add(age -> {
            if (!caster.isAlive() || caster.isRemoved() || !is(caster)) {
                UNUSED.remove(caster);
                return true;
            }
            if (!serious(caster)) {
                UNUSED.remove(caster);
                LEFT.remove(caster);
                return true;
            }
            if (level.getGameTime() >= until) {
                // Ran out: unused, the bar comes back; after a move or two, it is simply over.
                boolean unused = UNUSED.remove(caster) != null;
                LEFT.remove(caster);
                SubCommon.clearState(caster, SERIO);
                data.ult = unused ? PowerData.MAX_ULT : 0;
                SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                if (caster instanceof ServerPlayer player) {
                    PowerLogic.sync(player);
                }
                return true;
            }
            return false;
        });
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    // A serious move goes off: one of the three is used; the last one, or the Soco Serio (final), ends the mode.
    public static void spend(LivingEntity caster, ServerLevel level, boolean last) {
        UNUSED.remove(caster);
        int left = LEFT.getOrDefault(caster, 1) - 1;
        PowerData data = caster.getData(ModAttachments.POWER);
        data.ult = 0;
        if (last || left <= 0 || !serious(caster)) {
            LEFT.remove(caster);
            SubCommon.clearState(caster, SERIO);
            SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        } else {
            LEFT.put(caster, left);
            // (the time left as it is: the HUD shows the moves left)
            SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), Vec3.ZERO, 2, 0, left);
        }
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    // ---------- out of a stun with his fists ----------

    // The base fighting asked for while stunned (the punch, the Dash, the Barragem, the Forte): the stun breaks, for some
    // stamina, at most once every SHRUG_GAP ticks. True when he is free to attack now. Never out of a freeze (a domain, a
    // grab): only the stun of a blow.
    public static boolean shrugOff(LivingEntity caster) {
        if (!is(caster) || com.starkinhon.battlegroundsmod.combat.Stun.canAttack(caster) || com.starkinhon.battlegroundsmod.combat.Freeze.isFrozen(caster)
                || !caster.isAlive() || caster.hasEffect(com.starkinhon.battlegroundsmod.ModEffects.BRAIN_DAMAGE)
                || !(caster.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        Long last = SHRUGS.get(caster);
        if (last != null && now - last < SHRUG_GAP) {
            return false;
        }
        if (!com.starkinhon.battlegroundsmod.combat.Stamina.pay(caster, SHRUG_COST)) {
            return false;
        }
        SHRUGS.put(caster, now);
        caster.removeEffect(com.starkinhon.battlegroundsmod.ModEffects.ATORDOADO);
        caster.removeEffect(com.starkinhon.battlegroundsmod.ModEffects.COOLDOWN);
        PowerData data = caster.getData(ModAttachments.POWER);
        data.realStunUntil = 0;
        SubNet.send(level, SubNet.OPM_SHRUG, caster.getId(), caster.position(), caster.getLookAngle(), 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        if (caster instanceof ServerPlayer player) {
            com.starkinhon.battlegroundsmod.combat.Stamina.sync(player);
        }
        return true;
    }

    // (Pedra Viva used to keep his own motion apart from pushes; nothing pushes back now, the callers stay as they are)
    public static void own(LivingEntity entity) {
    }

    // Start of each level tick: the serious moves' digging.
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            OpmSerious.runDigs(level);
        }
    }

    // Before the defenses: the counter's catch.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!is(target) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (SubCommon.hasState(target, COUNTER) && attacker instanceof LivingEntity enemy && enemy != target && enemy.isAlive()) {
            event.setCanceled(true);
            // A blow he can reach is caught; a shot from afar is only stopped (the window stays open).
            if (enemy.distanceTo(target) <= 6) {
                SubCommon.clearState(target, COUNTER);
                OpmNormal.caught(target, level, enemy);
            }
        }
    }

    // After the shields and the tier's resistance: his own.
    public static float resist(LivingEntity target, float amount) {
        if (!is(target) || amount <= 0) {
            return amount;
        }
        amount *= SubCommon.hasState(target, ARMOR) ? ARMORED : RESIST;
        if (target instanceof ServerPlayer player && !serious(player)) {
            PowerData data = player.getData(ModAttachments.POWER);
            float gain = Math.min(TAKEN_MAX, amount / Math.max(1, player.getMaxHealth()) * TAKEN_FILL);
            data.ult = Math.min(PowerData.MAX_ULT, data.ult + gain);
        }
        return amount;
    }
}
