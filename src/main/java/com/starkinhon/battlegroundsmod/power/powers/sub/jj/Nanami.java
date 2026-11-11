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
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Nanami (SubAlma of Jujutsu, tier 7), kit remade with Stark (2026-11-11, preview forja/nanami-kit v4): the Ratio Technique.
// Passive Expediente: after a minute of fighting his blows weigh more (+20%).
// Z 7:3: he measures the target: a ruler of 10 marks over their body, the 7:3 point lit in gold with "7:3" written over
//   it (3 s). The blow of his that lands on the point is a critical (2x) and 2 s of stun. Press again: he steps in and
//   strikes the point himself.
// X Collapse: he finds the 7:3 point of a wall or of the ground and strikes it: cracks run from the point and it all
//   comes down in blocks; everyone near is hit and thrown.
// C Wrapped Blade: 3 fast critical cuts with the blunt cleaver wrapped in cloth, all on the 7:3 point; the last throws.
// V Overtime: he loosens the tie and wraps it round his fist (12 s of double damage), jumps and strikes the 7:3 point of
//   the ground: the whole area round him sinks and takes the enemies down with it.
// B Counter of the Office: a 1 s stance with the cleaver; a blow on him in that time is stopped on the cloth and he
//   answers on the 7:3 point.
// The 2 s stun of a 7:3 hit is full on bosses of his tier or weaker, shorter on stronger ones (Stark, 2026-11-11).
public final class Nanami {
    private static final int MARK = 60;
    public static final String OVERTIME_FIRST = "jj_overtime_first";
    public static final String COUNTER = "jj_nanami_counter";
    // Who each Nanami marked with 7:3.
    private static final Map<UUID, LivingEntity> MARKED = new HashMap<>();

    private Nanami() {
    }

    public static Power create() {
        return new Power("nanami", 0xD9B86A, 1.05f, List.of(
                new Ability(160, 0, Nanami::ratio),
                new Ability(300, 0, Nanami::collapse),
                new Ability(200, 0, Nanami::blade),
                new Ability(900, 0, Nanami::overtime),
                new Ability(240, 0, Nanami::counterStance)));
    }

    // The stun of a blow on the 7:3 point: 2 s, full on bosses of his tier or weaker, less the stronger they are.
    static void ratioStun(LivingEntity caster, LivingEntity t) {
        int ticks = 40;
        if (JjCommon.isBoss(t)) {
            int gap = Tiers.of(caster) - Tiers.of(t);
            if (gap > 0) {
                ticks = Math.max(16, 40 - 8 * gap);
            }
        }
        Stun.apply(t, ticks, true);
    }

    private static void critFx(ServerLevel level, LivingEntity t, Vec3 dir) {
        Vec3 c = Combat.center(t);
        SubNet.send(level, SubNet.RATIO_CRIT, 0, c, dir, 0, 0, 0);
        DbCommon.screen(level, c, 32);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
    }

    // ---------- Z: 7:3 ----------

    private static boolean ratio(LivingEntity caster, ServerLevel level) {
        LivingEntity marked = MARKED.get(caster.getUUID());
        if (marked != null && marked.isAlive() && SubCommon.hasState(marked, JjCommon.RATIO) && marked.distanceTo(caster) < 12) {
            crit(caster, level, marked);
            return false;
        }
        LivingEntity t = JjCommon.target(caster, 14);
        if (t == null) {
            SubCommon.warn(caster, "nanami.no_target");
            return false;
        }
        SubCommon.pose(caster, "jj.ratio_cut");
        SubCommon.busy(caster, 6);
        DbCommon.cooldown(caster, 0, 10);
        SubCommon.setState(t, JjCommon.RATIO, MARK);
        MARKED.put(caster.getUUID(), t);
        SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, MARK, 0, 0);
        SubCommon.sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
        SubCommon.sound(level, caster.position(), SoundEvents.BOOK_PAGE_TURN, 1f, 1.2f);
        watch(caster, level, t);
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

    // A blow of his that lands on someone he measured: the point. Called from the damage hook; true if it was.
    static boolean onPoint(LivingEntity caster, ServerLevel level, LivingEntity t) {
        if (MARKED.get(caster.getUUID()) != t || !SubCommon.hasState(t, JjCommon.RATIO)) {
            return false;
        }
        MARKED.remove(caster.getUUID());
        SubCommon.clearState(t, JjCommon.RATIO);
        SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
        DbCommon.cooldown(caster, 0, 160);
        ratioStun(caster, t);
        critFx(level, t, SubCommon.flat(t.position().subtract(caster.position())));
        return true;
    }

    private static void crit(LivingEntity caster, ServerLevel level, LivingEntity t) {
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
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            SubNet.send(level, SubNet.ARC, 0, caster.position().add(dir.scale(1.2)).add(0, 1.2, 0), dir, 1.8f, Db.GOLD, 0);
            Defense.breakGuard(t, caster);
            // The hook doubles it and stuns: it is on the point.
            Combat.hurt(caster, t, overtime ? 10 : 9);
            Combat.push(t, dir, 0.9, 0.3);
            return true;
        });
    }

    // ---------- X: Collapse ----------

    private static boolean collapse(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 eye = caster.getEyePosition();
        Vec3 far = eye.add(caster.getLookAngle().scale(6));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        boolean found = hit.getType() == HitResult.Type.BLOCK;
        Direction face = found ? hit.getDirection() : Direction.UP;
        boolean ground = face == Direction.UP;
        Vec3 point = found ? hit.getLocation() : SubCommon.ground(level, caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(2.5)));
        Vec3 from = caster.position();
        Vec3 dir = SubCommon.flat(point.subtract(from)).normalize();
        int strike = 5;
        int fall = 14;
        SubCommon.pose(caster, ground ? "jj.slam_fist" : "jj.div_punch");
        SubCommon.busy(caster, fall + 6);
        Freeze.holdLookFree(caster, strike + 2);
        SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), point, Vec3.ZERO, 1, 0, 0);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), point, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age < strike) {
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), point, Vec3.ZERO, 1, 0, 0);
                return false;
            }
            if (age == strike) {
                SubNet.send(level, SubNet.RATIO_CRIT, 0, point, dir, 0, 0, 0);
                if (ground) {
                    SubNet.send(level, SubNet.CRACKS, 0, point, Vec3.ZERO, 3.4f, 11, 1);
                }
                SubCommon.sound(level, point, SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
                SubCommon.sound(level, point, SoundEvents.STONE_BREAK, 1.6f, 0.6f);
                return false;
            }
            if (age < fall) {
                return false;
            }
            // It all comes down.
            SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), point, Vec3.ZERO, 0, 0, 1);
            if (ground) {
                SubNet.send(level, SubNet.SLAM, SubNet.newId(), point, Vec3.ZERO, 6, 0, 0);
                JjCommon.crater(level, point, 3.6, 2.0, 40);
            } else {
                JjCommon.carve(level, point.subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(1.2)), 3.2, from);
                SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, point), Vec3.ZERO, 3f, 9, 1);
            }
            DbCommon.screen(level, point, 33);
            for (LivingEntity e : Combat.inRadius(level, point, 5.5, caster)) {
                double d = e.position().distanceTo(point);
                float k = (float) (1 - 0.5 * Math.min(1, d / 5.5));
                Combat.hurtMagic(caster, e, 15 * k);
                Stun.apply(e, (int) (12 + 10 * k));
                Combat.pushFrom(e, point.subtract(dir), 1.1 * k, 0.5 * k + 0.2);
            }
            SubCommon.sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
            return true;
        });
        return true;
    }

    // ---------- C: Wrapped Blade ----------

    private static boolean blade(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 4.5);
        if (t == null || t.distanceTo(caster) > 4.8) {
            SubCommon.warn(caster, "nanami.miss");
            JjCommon.shorten(caster, 2, 30);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int gap = 3;
        SubCommon.busy(caster, gap * 3 + 4);
        Freeze.holdLookFree(caster, gap * 3 + 2);
        SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, gap * 3 + 4, 0, 0);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            if (age == 0 || age % gap != 0) {
                return false;
            }
            int n = age / gap;
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            SubCommon.pose(caster, n == 3 ? "jj.ratio_crit" : "jj.ratio_cut");
            SubNet.send(level, SubNet.ARC, 0, caster.position().add(dir.scale(1.2)).add(0, n == 3 ? 1.0 : 1.3, 0), dir, 1.6f + 0.2f * n, Db.GOLD, n == 1 ? 1 : 0);
            if (n == 1) {
                Defense.breakGuard(t, caster);
            }
            // Every cut lands on the point: a critical each.
            Combat.hurt(caster, t, 5 * 2);
            critFx(level, t, dir);
            SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f + 0.15f * n);
            if (n < 3) {
                Stun.apply(t, 8);
                return false;
            }
            ratioStun(caster, t);
            Combat.push(t, dir, 1.2, 0.35);
            Freeze.free(caster);
            SubNet.send(level, SubNet.RATIO, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
            return true;
        });
        return true;
    }

    // ---------- B: Counter of the Office ----------

    private static boolean counterStance(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "jj.manji_guard");
        SubCommon.busy(caster, 20);
        SubCommon.setState(caster, COUNTER, 20);
        SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, 1, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, COUNTER) && !guard.broken();
            SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, on ? 1 : 0, 0, on ? 0 : 1);
            if (!on) {
                if (age >= 19) {
                    JjCommon.shorten(caster, PowerData.SLOT_EXTRA, 100);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // A blow caught on the cloth: he answers on the 7:3 point. Called from the damage hook.
    static void counter(LivingEntity nanami, ServerLevel level, LivingEntity enemy) {
        SubCommon.clearState(nanami, COUNTER);
        Vec3 c = Combat.center(nanami).add(SubCommon.flat(enemy.position().subtract(nanami.position())).normalize().scale(0.6));
        DbCommon.hit(level, c, 0.9f, Db.WHITE);
        SubCommon.sound(level, c, SoundEvents.ANVIL_PLACE, 0.6f, 1.8f);
        SubCommon.pose(nanami, "jj.ratio_crit");
        Tasks.later(3, () -> {
            if (!enemy.isAlive() || enemy.distanceTo(nanami) > 7) {
                return;
            }
            Vec3 dir = SubCommon.flat(enemy.position().subtract(nanami.position()));
            SubNet.send(level, SubNet.ARC, 0, nanami.position().add(dir.scale(1.2)).add(0, 1.2, 0), dir, 1.8f, Db.GOLD, 0);
            Defense.breakGuard(enemy, nanami);
            Combat.hurt(nanami, enemy, 14);
            ratioStun(nanami, enemy);
            critFx(level, enemy, dir);
            Combat.push(enemy, dir, 1.3, 0.4);
        });
    }

    // ---------- V: Overtime ----------

    private static boolean overtime(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ticks = 240;
        int tie = 16;
        int air = 10;
        int land = tie + air;
        SubCommon.setState(caster, JjCommon.OVERTIME, ticks);
        SubNet.send(level, SubNet.GOLD_AURA, caster.getId(), caster.position(), Vec3.ZERO, ticks, 0, 0);
        SubCommon.pose(caster, "jj.overtime");
        SubCommon.busy(caster, land + 6);
        Freeze.holdLookFree(caster, tie);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, land, 3, false, false));
        SubCommon.sound(level, caster.position(), SoundEvents.BELL_BLOCK, 1.4f, 0.6f);
        Vec3 start = caster.position();
        Vec3 fwd = SubCommon.flat(caster.getLookAngle()).normalize();
        Vec3 spot = SubCommon.ground(level, start.add(fwd.scale(2.5)));
        Vec3[] at = {start};
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, JjCommon.OVERTIME);
            if (guard.broken() || !on) {
                Freeze.free(caster);
                SubCommon.clearState(caster, JjCommon.OVERTIME);
                SubNet.send(level, SubNet.GOLD_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), spot, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age == tie) {
                SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), Combat.center(caster), new Vec3(0, 1, 0), 1.4f, 1, 0);
                SubCommon.pose(caster, "jj.slam_leap");
                JjCommon.carry(caster, air, at);
            }
            if (age >= tie && age < land) {
                float k = (age - tie + 1) / (float) air;
                at[0] = start.lerp(spot, k).add(0, Math.sin(k * Math.PI) * 3.4, 0);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), spot, Vec3.ZERO, SubCommon.smooth(k * 2), 0, 0);
                if (age == land - 4) {
                    SubCommon.pose(caster, "jj.slam_fist");
                }
            }
            if (age == land) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.GROUND_MARK, caster.getId(), spot, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.RATIO_CRIT, 0, spot.add(0, 0.4, 0), fwd, 0, 0, 0);
                SubNet.send(level, SubNet.SLAM, SubNet.newId(), spot, Vec3.ZERO, 10, 0, 0);
                SubNet.send(level, SubNet.CRACKS, 0, spot, Vec3.ZERO, 6f, 14, 1);
                DbCommon.screen(level, spot.add(0, 1, 0), 33);
                // The whole area round him sinks.
                JjCommon.crater(level, spot, 6, 2.6, 70);
                for (LivingEntity e : Combat.inRadius(level, spot.add(0, 0.8, 0), 8, caster)) {
                    double d = e.position().distanceTo(spot);
                    float k = (float) (1 - 0.45 * Math.min(1, d / 8));
                    Combat.hurtMagic(caster, e, 20 * k);
                    Stun.apply(e, (int) (20 + 14 * k));
                    e.setDeltaMovement(e.getDeltaMovement().add(0, -0.8, 0));
                    e.hurtMarked = true;
                }
                SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 0.55f);
                SubCommon.sound(level, spot, SoundEvents.ANVIL_LAND, 1f, 0.5f);
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

    static void clear() {
        MARKED.clear();
    }
}
