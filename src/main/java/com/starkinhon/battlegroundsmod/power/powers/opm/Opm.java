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

// Saitama (Cara de um Soco, Stark 2026-11-11): what is always on, and the Modo Serio.
// Passive Pedra Viva: he takes no knockback from anything; he stays where he is (so his counter, hook, kick and punches
// land). High resistance: he takes a lot of beating. The ult bar is his Barra Seria: it fills dealing damage (like everyone)
// and taking it. Full, V turns on the Modo Serio for 15 s: each key becomes ONE serious move, and using one ends it (the
// bar was spent turning it on). If the mode runs out unused, the bar comes back full.
public final class Opm {
    public static final String ID = "one_punch";
    public static final String SERIO = "opm_serio";
    // Armor while winding up a serious move (the preview's 1-2 s of silence must not be a free hit).
    public static final String ARMOR = "opm_armor";
    // B: the counter's window.
    public static final String COUNTER = "opm_counter";
    public static final int SERIO_TICKS = 300;
    // Pedra Viva: share of every blow that still lands (high resistance), and while winding up a serious move.
    private static final float RESIST = 0.7f, ARMORED = 0.35f;
    // Taking damage fills the Barra Seria: losing the whole life would fill this much of it.
    private static final float TAKEN_FILL = 140f, TAKEN_MAX = 12f;

    // Bosses and bots with his power (players are found every tick anyway).
    private static final Set<LivingEntity> MOBS = Collections.newSetFromMap(new WeakHashMap<>());
    // Who turned the mode on and has not used it yet.
    private static final Map<LivingEntity, Boolean> UNUSED = new WeakHashMap<>();

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
        PowerData data = caster.getData(ModAttachments.POWER);
        // Every key ready for its serious move.
        for (int i = 0; i < 4; i++) {
            data.cooldowns[i] = 0;
        }
        SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), caster.getLookAngle(), 1, SERIO_TICKS, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.6f);
        SubCommon.warn(caster, "opm.serio_on");
        long until = level.getGameTime() + SERIO_TICKS;
        Tasks.add(age -> {
            if (!caster.isAlive() || caster.isRemoved() || !is(caster)) {
                UNUSED.remove(caster);
                return true;
            }
            if (!UNUSED.containsKey(caster)) {
                return true;
            }
            if (level.getGameTime() >= until) {
                // Ran out unused: the bar comes back.
                UNUSED.remove(caster);
                data.ult = PowerData.MAX_ULT;
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

    // A serious move goes off: the mode is spent.
    public static void spend(LivingEntity caster, ServerLevel level) {
        UNUSED.remove(caster);
        SubCommon.clearState(caster, SERIO);
        caster.getData(ModAttachments.POWER).ult = 0;
        SubNet.send(level, SubNet.OPM_SERIO, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    // ---------- Pedra Viva ----------

    // His own motion (dash attack, the combo's steps, a mob's dodge): marked with the velocity he gave himself, so Pedra
    // Viva keeps it and only undoes what someone else pushed on top of it.
    private record Own(Vec3 velocity, long at) {
    }

    private static final Map<LivingEntity, Own> OWN = new WeakHashMap<>();

    // Call right after the entity set its own velocity (and marked it).
    public static void own(LivingEntity entity) {
        if (is(entity)) {
            OWN.put(entity, new Own(entity.getDeltaMovement(), entity.level().getGameTime()));
        }
    }

    // Start of each level tick, before the game sends anyone's motion: whatever pushed him since is undone (a push is
    // only sent on when it was marked, so unmarking it is enough for a player; a mob loses the push itself). A motion he
    // gave himself stays: if a blow changed it since, his own velocity comes back.
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        OpmSerious.runDigs(level);
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.hurtMarked && is(player)) {
                Vec3 mine = mine(player, now);
                if (mine == null) {
                    player.hurtMarked = false;
                } else if (!player.getDeltaMovement().equals(mine)) {
                    player.setDeltaMovement(mine);
                }
            }
        }
        for (LivingEntity mob : MOBS) {
            if (mob.level() == level && mob.hurtMarked && is(mob)) {
                Vec3 mine = mine(mob, now);
                if (mine == null) {
                    mob.hurtMarked = false;
                    mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
                } else if (!mob.getDeltaMovement().equals(mine)) {
                    mob.setDeltaMovement(mine);
                }
            }
        }
    }

    // The velocity he gave himself in the last couple of ticks, if any (used once).
    private static Vec3 mine(LivingEntity entity, long now) {
        Own own = OWN.remove(entity);
        return own != null && now - own.at() <= 2 ? own.velocity() : null;
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
