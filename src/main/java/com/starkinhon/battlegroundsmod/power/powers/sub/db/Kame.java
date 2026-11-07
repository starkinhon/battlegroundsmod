package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Mestre Kame (SubAlma of Dragon Ball, tier 7), the Visualizador's Kame tab.
// Z Suiken (Drunken Fist): 4 s staggering; 60% of the melee hits that come slip past him and he slaps back a crooked
//   palm (DbCommon.onIncomingDamage). Z again: the palm right away.
// X Max Power: he swells (1.4x bigger), the shirt bursts, the ground cracks: punches +40% for 8 s. X again: the original
//   Kamehameha, huge, and he is skinny again.
// V Mafuba: a gale of green lines pulls the target into a spinning green spiral, the tornado narrows into the jar and
//   the lid shuts. Sealed 8 s (bosses 4 s): with 20% of life or less it dies sealed; with more it breaks the jar and
//   comes out with 15% less life. Kame is left on his knees with no stamina.
public final class Kame {
    public static final String MAFUBA_SEALED = "db_mafuba_sealed";
    private static final int SUIKEN_TICKS = 80;
    private static final int MAX_TICKS = 160;
    private static final int SEAL_TICKS = 160;
    private static final ResourceLocation BIG = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "db_max_power");

    private Kame() {
    }

    public static Power create() {
        return new Power("kame", 0xFF9A3A, 0.95f, List.of(
                new Ability(200, 0, Kame::suiken),
                new Ability(300, 0, Kame::maxPower),
                new Ability(260, 0, Kame::janKenMissing),
                new Ability(1400, 0, Kame::mafuba)));
    }

    // ---------- Z: Suiken ----------

    private static boolean suiken(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, DbCommon.SUIKEN)) {
            // Z again: the crooked palm now, on whoever is in front.
            LivingEntity enemy = SubCommon.aimed(caster, 3.5);
            if (enemy == null) {
                for (LivingEntity t : Combat.inCone(caster, 3.5, 0.3)) {
                    enemy = t;
                    break;
                }
            }
            if (enemy == null) {
                SubCommon.warn(caster, "kame.palm_no_target");
            } else {
                palm(caster, level, enemy);
            }
            DbCommon.cooldown(caster, 0, 8);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.setState(caster, DbCommon.SUIKEN, SUIKEN_TICKS);
        SubCommon.pose(caster, "db.bebado");
        SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_DRINK, 1f, 0.8f);
        Tasks.add(age -> {
            if (guard.broken() || !SubCommon.hasState(caster, DbCommon.SUIKEN)) {
                SubCommon.clearState(caster, DbCommon.SUIKEN);
                DbCommon.cooldown(caster, 0, 200);
                return true;
            }
            // Walks crooked and changes direction on his own: hard to aim at.
            if (age % 12 == 0) {
                Vec3 side = SubCommon.flat(caster.getLookAngle()).cross(new Vec3(0, 1, 0));
                double s = SubCommon.RANDOM.nextBoolean() ? 0.32 : -0.32;
                caster.setDeltaMovement(caster.getDeltaMovement().add(side.scale(s)));
                caster.hurtMarked = true;
                SubCommon.pose(caster, "db.bebado");
            }
            return false;
        });
        DbCommon.cooldown(caster, 0, 6);
        return false;
    }

    // The crooked palm (Z again, or every dodge of the Suiken).
    public static void palm(LivingEntity kame, ServerLevel level, LivingEntity enemy) {
        SubCommon.pose(kame, "db.palma_torta");
        Combat.hurt(kame, enemy, 4);
        Stun.apply(enemy, 10);
        Combat.push(enemy, enemy.position().subtract(kame.position()), 0.6, 0.15);
        DbCommon.hit(level, Combat.center(enemy), 0.5f, Db.WHITE);
        DbCommon.screen(level, Combat.center(enemy), 4);
        SubCommon.sound(level, enemy.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 1.3f);
    }

    // ---------- X: Max Power ----------

    private static boolean maxPower(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, DbCommon.MAX_POWER)) {
            kamehamehaMax(caster, level);
            DbCommon.cooldown(caster, 1, 80);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean[] fired = {false};
        SubCommon.setState(caster, DbCommon.MAX_POWER, MAX_TICKS);
        SubCommon.pose(caster, "db.flex");
        SubCommon.busy(caster, 14);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), 40, Db.KAME_W, 1.1f);
        SubNet.send(level, SubNet.FLEX, 0, caster.position(), Vec3.ZERO, 0, 0, 0);
        DbCommon.screen(level, Combat.center(caster), 8);
        SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 1.1f);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.5f, 0.6f);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, DbCommon.MAX_POWER) && !guard.broken();
            // He swells over a second and goes back the same way.
            setSize(caster, on ? 0.4 * SubCommon.smooth(age / 20f) : 0);
            if (!on) {
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                DbCommon.cooldown(caster, 1, 300);
                return true;
            }
            // Mobs and bosses fire the Kamehameha Max too, once someone is in their aim.
            if (!(caster instanceof Player) && age >= 60 && age % 10 == 0 && !fired[0] && SubCommon.aimed(caster, 30) != null) {
                fired[0] = true;
                kamehamehaMax(caster, level);
            }
            return false;
        });
        DbCommon.cooldown(caster, 1, 10);
        return false;
    }

    private static void setSize(LivingEntity caster, double extra) {
        AttributeInstance scale = caster.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(BIG);
        if (extra > 0.001) {
            scale.addTransientModifier(new AttributeModifier(BIG, extra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // X again: the original Kamehameha, the strongest move of the SubAlma; the Max Power ends with it.
    private static void kamehamehaMax(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int cup = 26;
        int beamTicks = 44;
        int ball = SubNet.newId();
        int beam = SubNet.newId();
        SubCommon.pose(caster, "db.concha");
        SubCommon.busy(caster, cup + beamTicks + 4);
        Map<Integer, Integer> hitAt = new HashMap<>();
        Tasks.add(age -> {
            Vec3 hm = DbCommon.hands(caster);
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < cup) {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 4, false, false));
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0.1f + 0.3f * SubCommon.smooth(age / 20f), Db.KAME + 16, 0);
                if (age % 10 == 0) {
                    SubCommon.sound(level, hm, SoundEvents.BEACON_AMBIENT, 1.2f, 0.7f + age / 40f);
                }
                return false;
            }
            int t = age - cup;
            if (t == 0) {
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubCommon.pose(caster, "db.empurra");
                DbCommon.screen(level, hm, 18);
                SubCommon.sound(level, hm, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 1.0f);
                SubCommon.sound(level, hm, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
            }
            boolean end = t >= beamTicks;
            Vec3 dir = caster.getLookAngle();
            Vec3 reach = DbCommon.beamEnd(caster, level, hm, dir, 40, 1.1, false);
            Vec3 to = hm.add(reach.subtract(hm).scale(Math.min(1, t / 5.0)));
            SubNet.send(level, SubNet.KI_BEAM, beam, hm, to.subtract(hm), 1.2f, Db.KAME, end ? 1 : 0);
            DbCommon.hurtAlong(caster, level, hm, to, 1.1, 3, age, 4, hitAt, dir.scale(0.4));
            if (t % 8 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.4f, 0.5f);
            }
            if (end) {
                DbCommon.boom(level, to, 2.4f, Db.KAME, 5);
                DbCommon.hurtRadius(caster, level, to, 2.4, 14, 1.4, 0.5);
                // Spent: skinny again.
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                return true;
            }
            return false;
        });
    }

    // ---------- V: Mafuba ----------

    private static boolean mafuba(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 14);
        if (target == null) {
            SubCommon.warn(caster, "kame.mafuba_no_target", 14);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean boss = target instanceof SoulBoss b && b.isBoss();
        int sealTicks = boss ? SEAL_TICKS / 2 : SEAL_TICKS;
        // Gale, spin, into the jar, shut.
        int gale = 16;
        int spin = 40;
        int intoJar = 70;
        int shut = 84;
        int kneel = 60;
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 jar = SubCommon.ground(level, caster.position().add(look.scale(2.2)));
        Vec3 start = target.position();
        Vec3 hover = start.add(look.scale(-0.4));
        int jarId = SubNet.newId();
        int spiralId = SubNet.newId();
        int tornadoId = SubNet.newId();
        SubCommon.pose(caster, "db.mafuba");
        SubCommon.busy(caster, shut);
        Freeze.holdLookFree(caster, shut);
        SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_PLACE, 1.2f, 0.8f);
        Stun.apply(target, shut + 4);
        PowerLogic.seal(target, shut + sealTicks);
        Freeze.hold(target, shut, a -> {
            if (a < gale) {
                return start;
            }
            if (a < spin) {
                return start.lerp(hover, SubCommon.smooth((a - gale) / 20f)).add(0, 0.3 * SubCommon.smooth((a - gale) / 20f), 0);
            }
            if (a < intoJar) {
                float k = SubCommon.smooth((a - spin) / (float) (intoJar - spin - 6));
                return hover.add(0.3 * Math.sin(a * 0.3), 0.3 + 1.8 * k, 0);
            }
            float k = SubCommon.smooth((a - intoJar) / (float) (shut - intoJar));
            return hover.add(0, 2.1, 0).lerp(jar, k);
        });
        boolean[] done = {false};
        Tasks.add(age -> {
            if (done[0]) {
                return true;
            }
            if (guard.broken() && age < shut) {
                // Kame was cut off before the lid shut: the target drops free.
                SubNet.send(level, SubNet.GALE, 0, DbCommon.hands(caster), Combat.center(target), 0, 0, 1);
                SubNet.send(level, SubNet.SPIRAL, spiralId, Combat.center(target), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.TORNADO, tornadoId, target.position(), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 1, 0, 1);
                Freeze.free(target);
                return true;
            }
            if (!target.isAlive()) {
                SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 1, 0, 1);
                return true;
            }
            Vec3 hm = DbCommon.hands(caster);
            float sc = age < intoJar ? 1 : 1 - SubCommon.smooth((age - intoJar) / (float) (shut - intoJar));
            Vec3 c = target.position().add(0, Math.max(0.2, sc), 0);
            if (age == gale || age == intoJar) {
                DbCommon.screen(level, c, 19);
            }
            if (age >= gale && age < intoJar + 2) {
                float aw = SubCommon.smooth((age - gale) / 8f);
                SubNet.send(level, SubNet.GALE, 0, hm, c, aw, 0, age >= intoJar ? 1 : 0);
                if (age % 6 == 0) {
                    SubCommon.sound(level, c, SoundEvents.ELYTRA_FLYING, 0.8f, 1.4f);
                }
            }
            if (age >= gale + 6 && age <= shut) {
                float a = SubCommon.smooth((age - gale - 6) / 10f) * (age < intoJar ? 1 : 1 - SubCommon.smooth((age - intoJar) / (float) (shut - intoJar)));
                SubNet.send(level, SubNet.SPIRAL, spiralId, c, Vec3.ZERO, 1.7f * (float) Math.max(0.25, sc), a, age >= shut ? 1 : 0);
            }
            if (age >= spin && age <= shut + 2) {
                Vec3 foot = age < intoJar ? new Vec3(hover.x, SubCommon.ground(level, hover).y, hover.z)
                        : new Vec3(hover.x, SubCommon.ground(level, hover).y, hover.z).lerp(jar, SubCommon.smooth((age - intoJar) / (float) (shut - intoJar)));
                float k = age < intoJar ? 0 : SubCommon.smooth((age - intoJar) / (float) (shut - intoJar));
                SubNet.send(level, SubNet.TORNADO, tornadoId, foot, Vec3.ZERO, 3.6f * (1 - k) + 0.3f, 1.5f * (1 - k) + 0.2f, age >= shut + 2 ? 1 : 0);
            }
            if (age < shut) {
                return false;
            }
            // The lid shuts. 20% of life or less: it dies sealed. More: it will break the jar.
            done[0] = true;
            boolean dies = target.getHealth() <= target.getMaxHealth() * 0.2f;
            SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, sealTicks, dies ? 0 : 1, 1);
            DbCommon.screen(level, jar, 19);
            SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_INSERT, 1.5f, 0.7f);
            SubCommon.sound(level, jar, SoundEvents.ENCHANTMENT_TABLE_USE, 1.5f, 0.8f);
            seal(caster, level, target, jar, sealTicks, dies);
            // The cost: on his knees with no stamina.
            SubCommon.pose(caster, "db.ajoelhado");
            Freeze.hold(caster, kneel);
            Stamina.exhaust(caster);
            return true;
        });
        return true;
    }

    private static void seal(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 jar, int sealTicks, boolean dies) {
        SubCommon.setState(target, MAFUBA_SEALED, sealTicks);
        Vec3 inside = jar.add(0, -0.2, 0);
        Freeze.hold(target, sealTicks, a -> inside);
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, sealTicks, 0, false, false));
        Stun.apply(target, sealTicks);
        Tasks.later(sealTicks, () -> {
            SubCommon.clearState(target, MAFUBA_SEALED);
            target.removeEffect(MobEffects.INVISIBILITY);
            Freeze.free(target);
            if (!target.isAlive()) {
                return;
            }
            if (dies) {
                target.hurt(level.damageSources().indirectMagic(caster, caster), Float.MAX_VALUE);
                SubCommon.sound(level, jar, SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.6f);
                return;
            }
            // It breaks the jar and comes out with 15% less life.
            target.teleportTo(jar.x, jar.y + 0.1, jar.z);
            target.hurt(level.damageSources().indirectMagic(caster, caster), target.getMaxHealth() * 0.15f);
            Combat.push(target, target.position().subtract(caster.position()), 0.9, 0.5);
            Stun.apply(target, 14);
            DbCommon.screen(level, jar, 20);
            SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_SHATTER, 2f, 0.7f);
            SubCommon.sound(level, jar, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
        });
        if (target instanceof Player) {
            SubCommon.warn(target, "kame.sealed");
        }
    }

    // ---------- C: not made yet in the Visualizador ----------

    private static boolean janKenMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
