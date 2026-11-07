package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Piccolo (SubAlma of Dragon Ball, tier 7), the Visualizador's Piccolo tab.
// Z Makankosappo: held, two fingers on the forehead crackling; let go, a thin straight ray with two tight spirals round
//   it (gold and pink) pierces everything in the line.
// X Stretched Arm: the arm stretches 8 blocks, grabs the target and pulls it straight into a knee.
// C Hellzone Grenade: ki balls that seem to miss stop in the air round the target (touching one hurts); C again, all of
//   them close on it at once.
public final class Piccolo {
    public static final String HELLZONE = "db_piccolo_hellzone";

    private Piccolo() {
    }

    public static Power create() {
        return new Power("piccolo", 0x62B04A, 1.0f, List.of(
                new Ability(200, 0, Piccolo::makanko),
                new Ability(160, 0, Piccolo::arm),
                new Ability(260, 0, Piccolo::hellzone),
                new Ability(1000, 0, Piccolo::lightGrenadeMissing)));
    }

    // ---------- Z: Makankosappo ----------

    private static boolean makanko(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int most = 62;
        int least = 12;
        int bossHold = 24 + SubCommon.RANDOM.nextInt(38);
        int id = SubNet.newId();
        SubCommon.pose(caster, "db.testa");
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            float k = SubCommon.smooth((age - 4) / 50f);
            boolean held = SubCommon.held(caster, age, bossHold);
            if ((held || age < least) && age < most) {
                if (held) {
                    Charge.report(caster, age / (double) most);
                }
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, k, Db.GOLD, 0);
                if (age % 3 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.REDSTONE_TORCH_BURNOUT, 0.6f, 1.4f + k);
                }
                return false;
            }
            SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, k, Db.GOLD, 1);
            SubCommon.pose(caster, "db.apontar");
            Vec3 tip = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.1));
            Vec3 dir = caster.getLookAngle();
            Vec3 end = DbCommon.beamEnd(caster, level, tip, dir, 30, 0.5, true);
            SubNet.send(level, SubNet.MAKANKO, 0, tip, end.subtract(tip), 28, 0, 0);
            DbCommon.screen(level, tip, 14);
            SubCommon.sound(level, tip, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.8f);
            SubCommon.sound(level, tip, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.4f, 1.4f);
            // Pierces everyone on the line; the damage grows with the charge.
            for (LivingEntity t : Combat.alongLine(level, tip, end, 0.6, caster)) {
                Combat.hurtMagic(caster, t, 8 + 16 * k);
                Stun.apply(t, 10);
                Combat.push(t, dir, 0.8 + k, 0.2);
                DbCommon.hit(level, Combat.center(t), 0.8f, Db.GOLD);
            }
            return true;
        });
        return true;
    }

    // ---------- X: Stretched Arm ----------

    private static boolean arm(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int stretch = 8;
        double reach = 8.5;
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, 30);
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
        LivingEntity[] caught = {null};
        int[] grabAt = {-1};
        Vec3[] from = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.ARM, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, 0, 0, 1);
                if (caught[0] != null) {
                    Freeze.free(caught[0]);
                }
                return true;
            }
            Vec3 shoulder = caster.position().add(0, caster.getBbHeight() * 0.75, 0);
            Vec3 dir = caster.getLookAngle();
            if (grabAt[0] < 0) {
                double len = reach * Math.min(1, SubCommon.easeOutCubic(age / (float) stretch));
                Vec3 tip = shoulder.add(dir.scale(Math.max(0.6, len)));
                SubNet.send(level, SubNet.ARM, caster.getId(), tip, Vec3.ZERO, 0, 0, 0);
                for (LivingEntity t : Combat.alongLine(level, shoulder, tip, 0.7, caster)) {
                    caught[0] = t;
                    break;
                }
                if (caught[0] != null) {
                    grabAt[0] = age;
                    from[0] = caught[0].position();
                    SubCommon.sound(level, caught[0].position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
                    Stun.apply(caught[0], 30);
                    LivingEntity t = caught[0];
                    Vec3 start = from[0];
                    // Pulled straight back in front of Piccolo.
                    Freeze.hold(t, 10, a -> start.lerp(caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(1.3)), SubCommon.smooth(a / 8f))
                            .add(0, a < 8 ? 0.3 : 0, 0));
                    return false;
                }
                if (age >= stretch + 2) {
                    // Nothing caught: the arm comes back empty.
                    SubCommon.warn(caster, "piccolo.arm_miss");
                    grabAt[0] = age;
                    caught[0] = null;
                    from[0] = tip;
                }
                return false;
            }
            int t = age - grabAt[0];
            if (caught[0] == null) {
                Vec3 tip = from[0].lerp(shoulder.add(dir.scale(0.6)), SubCommon.smooth(t / 8f));
                boolean done = t >= 8;
                SubNet.send(level, SubNet.ARM, caster.getId(), tip, Vec3.ZERO, 0, 0, done ? 1 : 0);
                return done;
            }
            LivingEntity target = caught[0];
            if (t <= 8) {
                SubNet.send(level, SubNet.ARM, caster.getId(), Combat.center(target), Vec3.ZERO, 0, 0, t == 8 ? 1 : 0);
                if (t % 2 == 0) {
                    Vec3 c = Combat.center(target);
                    Vec3 back = SubCommon.flat(c.subtract(caster.position())).scale(0.8);
                    SubNet.send(level, SubNet.STREAK, 0, c.add(back), back.scale(1.2), 0.3f, Db.WHITE, 5);
                }
                return false;
            }
            if (t == 10) {
                SubCommon.pose(caster, "db.joelhada");
            }
            if (t == 12) {
                // The knee: it launches them up.
                if (target.isAlive()) {
                    Combat.hurt(caster, target, 9);
                    Stun.apply(target, 18);
                    Combat.push(target, target.position().subtract(caster.position()), 0.6, 1.0);
                    DbCommon.hit(level, Combat.center(target), 0.8f, Db.WHITE);
                    DbCommon.screen(level, Combat.center(target), 15);
                    SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.7f);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- C: Hellzone Grenade ----------

    private static boolean hellzone(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, HELLZONE)) {
            SubCommon.clearState(caster, HELLZONE);
            return true;
        }
        LivingEntity target = SubCommon.aimed(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "piccolo.hellzone_no_target", 24);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int count = 10;
        int every = 7;
        int auto = 120;
        int bossClose = 70 + SubCommon.RANDOM.nextInt(30);
        SubCommon.setState(caster, HELLZONE, auto + 10);
        List<Integer> ids = new ArrayList<>();
        List<Vec3> launched = new ArrayList<>();
        int[] closeAt = {-1};
        Vec3[] center = {Combat.center(target)};
        int[] lastHurt = {-100};
        Tasks.add(age -> {
            if (target.isAlive()) {
                center[0] = Combat.center(target);
            }
            if (guard.broken() && closeAt[0] < 0) {
                for (int id : ids) {
                    SubNet.send(level, SubNet.KI_BALL, id, center[0], Vec3.ZERO, 0, Db.GOLD, 1);
                }
                SubCommon.clearState(caster, HELLZONE);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            int thrownNow = Math.min(count, age / every + 1);
            if (closeAt[0] < 0) {
                boolean close = !SubCommon.hasState(caster, HELLZONE) || age >= auto || (!(caster instanceof Player) && age >= bossClose);
                if (close && !ids.isEmpty()) {
                    closeAt[0] = age;
                    SubCommon.clearState(caster, HELLZONE);
                    SubCommon.pose(caster, "db.bracos_abertos_curto");
                    SubCommon.sound(level, center[0], SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.4f);
                } else if (age % every == 0 && ids.size() < count) {
                    // One more ball, thrown from each hand in turn.
                    ids.add(SubNet.newId());
                    launched.add((ids.size() % 2 == 0 ? DbCommon.hand(caster) : DbCommon.handLeft(caster)));
                    SubCommon.pose(caster, ids.size() % 2 == 0 ? "soco.forte_golpe" : "soco.barragem_final");
                    SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 1.5f);
                }
            }
            int closeT = closeAt[0] < 0 ? -1 : age - closeAt[0];
            boolean anyHit = false;
            for (int i = 0; i < ids.size(); i++) {
                Vec3 slotDir = Db.sphereDir(77, i);
                Vec3 slot = center[0].add(slotDir.x * 2.4, slotDir.y * 1.3 + 0.3, slotDir.z * 2.4);
                int a = age - i * every;
                Vec3 p;
                if (closeT >= 0) {
                    p = slot.lerp(center[0], SubCommon.smooth(closeT / 10f));
                } else if (a < 8) {
                    p = launched.get(i).lerp(slot, SubCommon.easeOutCubic(a / 8f));
                } else {
                    p = slot.add(Math.sin(age * 0.1 + i) * 0.08, Math.cos(age * 0.13 + i) * 0.08, 0);
                }
                boolean end = closeT >= 10;
                SubNet.send(level, SubNet.KI_BALL, ids.get(i), p, Vec3.ZERO, 0.22f, Db.GOLD + (a < 8 && closeT < 0 ? 32 : 0), end ? 1 : 0);
                // The cage: touching a ball hurts.
                if (closeT < 0 && a >= 8 && age - lastHurt[0] >= 10) {
                    for (LivingEntity e : Combat.inRadius(level, p, 0.8, caster)) {
                        Combat.hurtMagic(caster, e, 3);
                        DbCommon.hit(level, p, 0.4f, Db.GOLD);
                        anyHit = true;
                    }
                }
            }
            if (anyHit) {
                lastHurt[0] = age;
            }
            if (closeT == 10) {
                Vec3 c = center[0];
                DbCommon.boom(level, c, 4.6f, Db.GOLD, 13);
                DbCommon.screen(level, c, 16);
                for (int i = 0; i < count; i++) {
                    int k = i;
                    Vec3 d = Db.sphereDir(77, i);
                    Tasks.later(1 + (int) (i * 0.7), () -> {
                        Vec3 q = c.add(d.x * 1.6, d.y, d.z * 1.6);
                        SubNet.send(level, SubNet.KI_BOOM, 0, q, new Vec3(DbCommon.groundY(level, q), 0, 0), 1.6f, k % 2 != 0 ? Db.ORANGE : Db.GOLD, 50 + k);
                        SubCommon.sound(level, q, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
                    });
                }
                DbCommon.hurtRadius(caster, level, c, 4.6, 16 + 1.2f * ids.size(), 1.2, 0.5);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            return false;
        });
        DbCommon.cooldown(caster, 2, 8);
        return false;
    }

    // ---------- V: not made yet in the Visualizador ----------

    private static boolean lightGrenadeMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
