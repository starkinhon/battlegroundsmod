package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Inumaki (SubAlma of Jujutsu, tier 7), the Visualizador's Inumaki tab: Cursed Speech. Every word strains his throat
// ("Stop" and "Sleep" a little, "Get crushed" a lot); past the limit he coughs blood and can't speak for 4 s. The throat
// rests on its own.
// Z "Stop": whoever he looks at (15 blocks) freezes for 1.5 s (less on stronger ones and bosses).
// X "Get crushed": an unseen weight comes down on the target: heavy damage and a second on the ground.
// C "Sleep": whoever he looks at (8 blocks) falls asleep for 3 s, or until hit; that hit is a critical.
public final class Inumaki {
    private static final float LIMIT = 100;
    private static final float REST_PER_TICK = 0.2f;
    // His throat: how strained, and when it was last checked.
    private static final Map<UUID, float[]> THROAT = new HashMap<>();

    private Inumaki() {
    }

    public static Power create() {
        return new Power("inumaki", 0x8E7CC3, 0.95f, List.of(
                new Ability(160, 0, Inumaki::stop),
                new Ability(260, 0, Inumaki::crush),
                new Ability(280, 0, Inumaki::sleep),
                new Ability(1000, 0, Inumaki::ultMissing)));
    }

    private static Vec3 mouth(LivingEntity caster) {
        return caster.getEyePosition().add(0, -0.18, 0).add(caster.getLookAngle().scale(0.3));
    }

    private static float throat(LivingEntity caster) {
        float[] t = THROAT.computeIfAbsent(caster.getUUID(), k -> new float[] {0, caster.level().getGameTime()});
        long now = caster.level().getGameTime();
        t[0] = Math.max(0, t[0] - (now - t[1]) * REST_PER_TICK);
        t[1] = now;
        return t[0];
    }

    // A word said: the throat takes it; past the limit he coughs blood and is silenced.
    private static void strain(LivingEntity caster, ServerLevel level, float amount) {
        float now = throat(caster) + amount;
        THROAT.get(caster.getUUID())[0] = now;
        if (now <= LIMIT) {
            SubCommon.warn(caster, "inumaki.throat", (int) now);
            return;
        }
        THROAT.get(caster.getUUID())[0] = 40;
        int silent = 80;
        PowerLogic.seal(caster, silent);
        SubNet.send(level, SubNet.COUGH, caster.getId(), caster.position(), Vec3.ZERO, silent, 0, 0);
        SubCommon.warn(caster, "inumaki.silenced");
        if (caster.getHealth() > 3) {
            caster.setHealth(caster.getHealth() - 2);
        }
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_BREATH, 1.4f, 0.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.SKELETON_HURT, 0.8f, 0.6f);
    }

    private static void speak(LivingEntity caster, ServerLevel level, LivingEntity t, double range, int color, float size) {
        Vec3 m = mouth(caster);
        Vec3 end = t != null ? Combat.center(t) : m.add(caster.getLookAngle().scale(range));
        SubNet.send(level, SubNet.VOICE, 0, m, end.subtract(m), size, color, 10);
        SubNet.send(level, SubNet.SEAL_MARKS, caster.getId(), caster.position(), Vec3.ZERO, 30, 0, 0);
        SubCommon.sound(level, m, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.8f);
        SubCommon.sound(level, m, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.7f);
    }

    // ---------- Z: Stop ----------

    private static boolean stop(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 15);
        SubCommon.pose(caster, "jj.speak");
        SubCommon.busy(caster, 8);
        speak(caster, level, t, 15, 0, 1);
        strain(caster, level, 20);
        if (t == null) {
            SubCommon.warn(caster, "inumaki.no_target");
            DbCommon.cooldown(caster, 0, 40);
            return false;
        }
        int ticks = JjCommon.control(caster, t, 30);
        Tasks.later(3, () -> {
            if (!t.isAlive()) {
                return;
            }
            Freeze.hold(t, ticks);
            Stun.apply(t, ticks);
            PowerLogic.seal(t, ticks);
            SubNet.send(level, SubNet.HOLD_RINGS, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
            DbCommon.screen(level, Combat.center(t), 41);
        });
        return true;
    }

    // ---------- X: Get crushed ----------

    private static boolean crush(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 15);
        if (t == null) {
            SubCommon.warn(caster, "inumaki.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.speak_hard");
        SubCommon.busy(caster, 12);
        speak(caster, level, t, 15, 0, 1.5f);
        Vec3 g = SubCommon.ground(level, t.position());
        SubNet.send(level, SubNet.CRUSH, 0, g, Vec3.ZERO, 1.8f, 0, 0);
        Tasks.later(6, () -> {
            if (!t.isAlive()) {
                return;
            }
            Combat.hurtMagic(caster, t, 15);
            int down = JjCommon.control(caster, t, 20);
            Stun.apply(t, down);
            Freeze.hold(t, down);
            t.setDeltaMovement(0, -1, 0);
            t.hurtMarked = true;
            JjCommon.crater(level, SubCommon.ground(level, t.position()), 1.6, 0.9, 6);
            DbCommon.screen(level, Combat.center(t), 42);
            SubCommon.sound(level, t.position(), SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
            SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            if (!guard.broken()) {
                SubNet.send(level, SubNet.COUGH, caster.getId(), caster.position(), Vec3.ZERO, 30, 0, 0);
            }
        });
        strain(caster, level, 55);
        return true;
    }

    // ---------- C: Sleep ----------

    private static boolean sleep(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 8);
        SubCommon.pose(caster, "jj.speak");
        SubCommon.busy(caster, 8);
        speak(caster, level, t, 8, 1, 0.9f);
        strain(caster, level, 20);
        if (t == null) {
            SubCommon.warn(caster, "inumaki.no_target");
            DbCommon.cooldown(caster, 2, 40);
            return false;
        }
        int ticks = JjCommon.control(caster, t, 60);
        Tasks.later(3, () -> {
            if (!t.isAlive()) {
                return;
            }
            SubCommon.setState(t, JjCommon.ASLEEP, ticks);
            Freeze.hold(t, ticks);
            Stun.apply(t, Math.min(ticks, 20));
            PowerLogic.seal(t, ticks);
            SubNet.send(level, SubNet.SLEEP, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
            SubCommon.sound(level, t.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.6f);
        });
        return true;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }

    static void clear() {
        THROAT.clear();
    }
}
