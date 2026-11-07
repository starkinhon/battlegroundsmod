package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.List;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Kakashi (SubAlma of Naruto, tier 7), the Visualizador's Kakashi tab.
// Z Raiton: Raiju Tsuiga: the Raikiri in his hand becomes a hound of lightning when he drives the hand into the ground. It
//   runs along the ground and follows the target even if it dodges (3 s or 14 blocks), and bites: damage and 1 s paralysis.
// X Kamui: the Mangekyo turns and the space round the target twists into a spiral: it is pulled into another dimension and
//   is out of the fight for 4 s (can't hit, can't be hit), then spat back out, hurt. His sight blurs for 2 s after.
// C Sharingan Copy: the last skill ANYONE used near him in the last 10 s (a Soul, a SubAlma or a boss, any move of the mod)
//   he uses once, 20% stronger. A Divine Soul's ult can't be copied. Nothing used near him: "nothing to copy".
public final class Kakashi {
    private Kakashi() {
    }

    public static Power create() {
        return new Power("kakashi", 0x56653A, 1.0f, List.of(
                new Ability(200, 0, Kakashi::raiju),
                new Ability(400, 0, Kakashi::kamui),
                new Ability(300, 0, Kakashi::copy),
                new Ability(1000, 0, Kakashi::ultMissing)));
    }

    // ---------- Z: Raiju Tsuiga ----------

    private static boolean raiju(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity target = NrCommon.target(caster, 22);
        int ball = SubNet.newId();
        int dog = SubNet.newId();
        int ground = 40;
        int run = 52;
        double speed = 0.45;
        double maxRun = 14;
        SubCommon.pose(caster, "nr.raikiri");
        SubCommon.busy(caster, run + 4);
        Vec3[] head = {null};
        Vec3[] dir = {null};
        double[] ran = {0};
        Tasks.add(age -> {
            if (guard.broken() && age < run) {
                SubNet.send(level, SubNet.CHIDORI, ball, caster.position(), Vec3.ZERO, 0, 1, 1);
                return true;
            }
            Vec3 hand = DbCommon.hand(caster);
            if (age < ground) {
                SubNet.send(level, SubNet.CHIDORI, ball, hand, Vec3.ZERO, 0.24f * SubCommon.smooth(age / 14f), 1, 0);
                if (age % 3 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.8f, 2f);
                }
                return false;
            }
            if (age == ground) {
                SubCommon.pose(caster, "nr.mao_chao");
                SubCommon.sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
            }
            if (age < run) {
                Vec3 low = caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(0.7)).add(0, 0.3, 0);
                SubNet.send(level, SubNet.CHIDORI, ball, low, Vec3.ZERO, 0.3f * (1 - SubCommon.smooth((age - run + 4) / 4f)), 1, age == run - 1 ? 1 : 0);
                return false;
            }
            if (head[0] == null) {
                dir[0] = SubCommon.flat(caster.getLookAngle());
                head[0] = caster.position().add(dir[0].scale(1.2)).add(0, 0.35, 0);
                SubCommon.sound(level, head[0], SoundEvents.WOLF_GROWL, 1.4f, 1.6f);
            }
            // Runs low along the ground and turns after the target.
            if (target != null && target.isAlive()) {
                Vec3 want = SubCommon.flat(target.position().subtract(head[0]));
                dir[0] = SubCommon.flat(dir[0].lerp(want, 0.25));
            }
            Vec3 next = head[0].add(dir[0].scale(speed));
            Vec3 floor = SubCommon.ground(level, next);
            next = new Vec3(next.x, floor.y + 0.35, next.z);
            ran[0] += speed;
            head[0] = next;
            boolean end = ran[0] >= maxRun || SubCommon.solid(level, next.add(0, 0.3, 0));
            LivingEntity bitten = null;
            for (LivingEntity t : Combat.inRadius(level, next.add(0, 0.5, 0), 1.1, caster)) {
                bitten = t;
                break;
            }
            if (bitten != null) {
                Combat.hurtMagic(caster, bitten, 11);
                NrCommon.paralyze(level, bitten, 20);
                DbCommon.hit(level, Combat.center(bitten), 1, Db.BLUE);
                DbCommon.screen(level, Combat.center(bitten), 0);
                SubCommon.sound(level, bitten.position(), SoundEvents.WOLF_GROWL, 1.2f, 1.2f);
                SubCommon.sound(level, bitten.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 1.6f);
                end = true;
            }
            SubNet.send(level, SubNet.RAIJU, dog, next, dir[0], 0, 0, end ? 1 : 0);
            if ((age - run) % 4 == 0) {
                SubCommon.sound(level, next, SoundEvents.BEEHIVE_WORK, 0.8f, 1.8f);
            }
            return end;
        });
        return true;
    }

    // ---------- X: Kamui ----------

    private static boolean kamui(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "kakashi.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean boss = target instanceof SoulBoss b && b.isBoss();
        int eye = 6;
        int open = 24;
        int vanish = 62;
        int gone = boss ? 40 : 78;
        int back = vanish + gone;
        int out = back + 10;
        int id = SubNet.newId();
        boolean wasInvisible = target.isInvisible();
        Vec3 c = Combat.center(target);
        SubCommon.pose(caster, "nr.bandana");
        SubCommon.busy(caster, vanish);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, vanish + 20, 3, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.7f);
        Tasks.add(age -> {
            boolean lost = !target.isAlive() || target.isRemoved();
            if (age < vanish && (guard.broken() || lost)) {
                SubNet.send(level, SubNet.KAMUI, id, c, Vec3.ZERO, 0, 0, 1);
                Freeze.free(target);
                return true;
            }
            Vec3 axis = caster.getEyePosition().subtract(c).normalize();
            if (age == open) {
                Freeze.hold(target, vanish - open + 2);
                Stun.apply(target, vanish - open);
                SubCommon.sound(level, c, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.8f);
            }
            if (age >= open && age < vanish) {
                float k = SubCommon.smooth((age - open) / 10f) * (1 - SubCommon.smooth((age - vanish + 4) / 8f));
                SubNet.send(level, SubNet.KAMUI, id, c, axis, k, 0, 0);
                // It turns faster and faster as it is pulled in.
                float yaw = target.getYRot() + (age - open) * (age - open) * 0.35f;
                target.setYRot(yaw);
                target.setYHeadRot(yaw);
                target.setYBodyRot(yaw);
            }
            if (age == vanish) {
                SubNet.send(level, SubNet.KAMUI, id, c, axis, 0, 0, 1);
                target.setInvisible(true);
                Intangible.hold(target, gone);
                Freeze.hold(target, gone);
                PowerLogic.seal(target, gone);
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                } else {
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, gone, 0, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, gone, 0, false, false));
                }
                SubNet.send(level, SubNet.SELF_SCREEN, caster.getId(), caster.position(), Vec3.ZERO, 23, 0, 0);
                SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
                return false;
            }
            if (age > vanish && age < out) {
                if (age >= back - 10 && age < out) {
                    float k = SubCommon.smooth((age - back + 10) / 6f) * (1 - SubCommon.smooth((age - back - 4) / 6f));
                    SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, k, 0, 0);
                }
                if (age == back) {
                    if (!wasInvisible) {
                        target.setInvisible(false);
                    }
                    Freeze.free(target);
                    if (!lost) {
                        Combat.hurtMagic(caster, target, 12);
                        Stun.apply(target, 20);
                        Combat.push(target, target.position().subtract(caster.position()), 0.7, 0.3);
                        DbCommon.screen(level, c, 4);
                    }
                    SubCommon.sound(level, c, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
                }
                return false;
            }
            SubNet.send(level, SubNet.KAMUI, id + 1, c, axis, 0, 0, 1);
            if (!wasInvisible && target.isAlive()) {
                target.setInvisible(false);
            }
            return true;
        });
        return true;
    }

    // ---------- C: Sharingan Copy ----------

    private static boolean copy(LivingEntity caster, ServerLevel level) {
        NrCommon.Cast cast = NrCommon.lastCast(caster, 16);
        if (cast == null) {
            SubCommon.warn(caster, "kakashi.nothing_to_copy");
            return false;
        }
        var power = Powers.get(cast.power());
        Component name = power != null ? power.abilityName(cast.slot()) : Component.literal("?");
        SubCommon.pose(caster, "nr.selo");
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 40, 3, 0);
        SubCommon.warn(caster, "kakashi.copied", name);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        SubCommon.setState(caster, NrCommon.COPY, 200);
        Tasks.later(6, () -> {
            if (!caster.isAlive()) {
                return;
            }
            if (!cast.ability().action().use(caster, level)) {
                SubCommon.warn(caster, "kakashi.copy_failed", name);
                SubCommon.clearState(caster, NrCommon.COPY);
            }
        });
        return true;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
