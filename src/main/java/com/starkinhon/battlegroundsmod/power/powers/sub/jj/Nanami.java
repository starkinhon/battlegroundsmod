package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Nanami (SubAlma of Jujutsu, tier 7), the Visualizador's Nanami tab: the Ratio Technique.
// Z 7:3: a cut with the wrapped blade marks the target with the lines of the 7:3 point (6 s). Press again: he steps in
//   and strikes the point: a critical, 3x the damage, and a second of stun.
// X Collapse: he jumps and drives his fist into the 7:3 point of the ground: a crater with flying blocks and a shockwave
//   that throws everyone round him.
// V Overtime: only after a minute of fighting ("still on the clock" before that). He takes the tie off: 12 s of double
//   strength on the punches, every hit heavier, the critical counts anywhere, and the first hit cracks the ground.
public final class Nanami {
    private static final int MARK = 120;
    public static final String OVERTIME_FIRST = "jj_overtime_first";
    // Who each Nanami marked with 7:3.
    private static final Map<UUID, LivingEntity> MARKED = new HashMap<>();

    private Nanami() {
    }

    public static Power create() {
        return new Power("nanami", 0xD9B86A, 1.05f, List.of(
                new Ability(180, 0, Nanami::ratio),
                new Ability(300, 0, Nanami::collapse),
                new Ability(200, 0, Nanami::ultMissing),
                new Ability(900, 0, Nanami::overtime)));
    }

    // ---------- Z: 7:3 ----------

    private static boolean ratio(LivingEntity caster, ServerLevel level) {
        LivingEntity marked = MARKED.get(caster.getUUID());
        if (marked != null && marked.isAlive() && SubCommon.hasState(marked, JjCommon.RATIO) && marked.distanceTo(caster) < 12) {
            crit(caster, level, marked);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.ratio_cut");
        SubCommon.busy(caster, 8);
        DbCommon.cooldown(caster, 0, 10);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        Tasks.later(4, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 fwd = SubCommon.flat(caster.getLookAngle());
            SubNet.send(level, SubNet.ARC, 0, caster.position().add(fwd.scale(1.3)).add(0, 1.2, 0), fwd, 1.8f, Db.GOLD, 0);
            LivingEntity t = JjCommon.target(caster, 4);
            if (t == null || t.distanceTo(caster) > 4.2) {
                SubCommon.warn(caster, "nanami.miss");
                DbCommon.cooldown(caster, 0, 40);
                return;
            }
            Combat.hurt(caster, t, 6);
            Stun.apply(t, 8);
            DbCommon.hit(level, JjCommon.contact(caster, t), 0.7f, Db.GOLD);
            SubCommon.setState(t, JjCommon.RATIO, MARK);
            MARKED.put(caster.getUUID(), t);
            SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, MARK, 0, 0);
            SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.1f);
            SubCommon.sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
            watch(caster, level, t);
        });
        return false;
    }

    // The mark fades without the critical: the cooldown starts then. Mobs strike it on their own.
    private static void watch(LivingEntity caster, ServerLevel level, LivingEntity t) {
        Tasks.add(age -> {
            if (MARKED.get(caster.getUUID()) != t) {
                return true;
            }
            if (!t.isAlive() || !SubCommon.hasState(t, JjCommon.RATIO) || !caster.isAlive()) {
                MARKED.remove(caster.getUUID());
                SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
                DbCommon.cooldown(caster, 0, 140);
                return true;
            }
            if (!(caster instanceof Player) && age == 16 && t.distanceTo(caster) < 10) {
                crit(caster, level, t);
                return true;
            }
            return false;
        });
    }

    private static void crit(LivingEntity caster, ServerLevel level, LivingEntity t) {
        MARKED.remove(caster.getUUID());
        SubCommon.clearState(t, JjCommon.RATIO);
        SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
        DbCommon.cooldown(caster, 0, 180);
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean overtime = SubCommon.hasState(caster, JjCommon.OVERTIME);
        Vec3 from = caster.position();
        Vec3[] at = {from};
        int step = 4;
        SubCommon.pose(caster, "jj.ratio_crit");
        SubCommon.busy(caster, step + 8);
        JjCommon.carry(caster, step, at);
        SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), from, Vec3.ZERO, 2, 0, 0);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 2, 0, 1);
                return true;
            }
            Vec3 to = t.position().subtract(SubCommon.flat(t.position().subtract(from)).scale(1.4));
            at[0] = from.lerp(to, SubCommon.easeOutCubic((age + 1) / (float) step));
            SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), at[0], Vec3.ZERO, 2, 0, age >= step ? 1 : 0);
            if (age < step) {
                return false;
            }
            Freeze.free(caster);
            Vec3 c = Combat.center(t);
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            Defense.breakGuard(t, caster);
            Combat.hurt(caster, t, overtime ? 21 : 18);
            Stun.apply(t, JjCommon.control(caster, t, 20));
            Combat.push(t, dir, 0.9, 0.3);
            SubNet.send(level, SubNet.RATIO_CRIT, 0, c, dir, 0, 0, 0);
            DbCommon.screen(level, c, 32);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
            SubCommon.sound(level, c, SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
            return true;
        });
    }

    // ---------- X: Collapse ----------

    private static boolean collapse(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        Vec3 fwd = SubCommon.flat(caster.getLookAngle());
        LivingEntity t = JjCommon.target(caster, 9);
        double reach = t != null ? Math.min(7, caster.distanceTo(t)) : 3.5;
        Vec3 land = SubCommon.ground(level, caster.position().add(fwd.scale(reach)));
        SubCommon.pose(caster, "jj.slam_leap");
        SubCommon.busy(caster, 30);
        Vec3 start = caster.position();
        Vec3[] at = {start};
        int air = 12;
        JjCommon.carry(caster, air, at);
        SubCommon.sound(level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), land, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age < air) {
                float k = (age + 1) / (float) air;
                at[0] = start.lerp(land, k).add(0, Math.sin(k * Math.PI) * 3.2, 0);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), land, Vec3.ZERO, SubCommon.smooth(age / 6f), 0, 0);
                if (age == air - 4) {
                    SubCommon.pose(caster, "jj.slam_fist");
                }
                return false;
            }
            Freeze.free(caster);
            SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), land, Vec3.ZERO, 0, 0, 1);
            float wave = 7;
            SubNet.send(level, SubNet.SLAM, id, land, Vec3.ZERO, wave, 0, 0);
            DbCommon.screen(level, land.add(0, 1, 0), 33);
            JjCommon.crater(level, land, 3.3, 1.8, 24);
            for (LivingEntity e : Combat.inRadius(level, land.add(0, 0.8, 0), wave, caster)) {
                double d = e.position().distanceTo(land);
                float k = (float) (1 - 0.55 * Math.min(1, d / wave));
                Combat.hurtMagic(caster, e, 14 * k);
                Stun.apply(e, (int) (12 + 10 * k));
                Combat.pushFrom(e, land, 1.1 * k, 0.6 * k + 0.2);
            }
            SubCommon.sound(level, land, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
            SubCommon.sound(level, land, SoundEvents.ANVIL_LAND, 1f, 0.5f);
            return true;
        });
        return true;
    }

    // ---------- V: Overtime ----------

    private static boolean overtime(LivingEntity caster, ServerLevel level) {
        long fought = JjCommon.fighting(caster);
        if (fought < 1200) {
            SubCommon.warn(caster, "nanami.not_yet", (int) Math.ceil((1200 - fought) / 20.0));
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ticks = 240;
        SubCommon.setState(caster, JjCommon.OVERTIME, ticks);
        SubCommon.setState(caster, OVERTIME_FIRST, ticks);
        SubNet.send(level, SubNet.GOLD_AURA, caster.getId(), caster.position(), Vec3.ZERO, ticks, 0, 0);
        SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), Combat.center(caster), new Vec3(0, 1, 0), 1.4f, 1, 0);
        DbCommon.screen(level, Combat.center(caster), 34);
        SubCommon.pose(caster, "jj.overtime");
        SubCommon.busy(caster, 14);
        SubCommon.sound(level, caster.position(), SoundEvents.BELL_BLOCK, 1.4f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken() || !SubCommon.hasState(caster, JjCommon.OVERTIME)) {
                SubCommon.clearState(caster, JjCommon.OVERTIME);
                SubCommon.clearState(caster, OVERTIME_FIRST);
                SubNet.send(level, SubNet.GOLD_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            return false;
        });
        return true;
    }

    // Called on every hit he lands in Overtime: the first one cracks the ground under the target.
    static void firstHit(LivingEntity caster, ServerLevel level, LivingEntity target) {
        SubCommon.clearState(caster, OVERTIME_FIRST);
        Vec3 g = SubCommon.ground(level, target.position());
        SubNet.send(level, SubNet.CRACKS, 0, g, Vec3.ZERO, 2.4f, 8, 1);
        JjCommon.crater(level, g, 1.6, 0.8, 8);
        SubCommon.sound(level, g, SoundEvents.STONE_BREAK, 1.6f, 0.5f);
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }

    static void clear() {
        MARKED.clear();
    }
}
