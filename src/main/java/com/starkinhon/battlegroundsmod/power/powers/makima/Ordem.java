package com.starkinhon.battlegroundsmod.power.powers.makima;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Shift+X Ordem (forja/makima-ordem, approved 2026-11-11). She says one word and the one she looks at obeys, picked by
// how they stand: far "Venha." (walks to her stiffly, like a puppet, and kneels), near "Ajoelhe." (knees into the ground,
// a short crack), near and beneath her "Woof." (on all fours, answers "Au!"), low on life "Morra." (the color leaves
// the world and they drop dead). When she speaks the world goes gray and her voice runs through the air; the target's
// eyes take her rings while it obeys.
public final class Ordem {
    private static final double RANGE = 32;
    private static final double NEAR = 6;
    private static final float DIE_LIFE = 0.2f;
    // The word leaves her mouth at 0.55 s and reaches them at 0.73 s (the preview's SPEAK and hitT).
    private static final int SPEAK = 11;
    private static final int HIT = 15;

    enum Kind {
        VENHA(104, 160), AJOELHE(84, 200), WOOF(100, 240), MORRA(104, 300);

        final int length;
        final int cooldown;

        Kind(int length, int cooldown) {
            this.length = length;
            this.cooldown = cooldown;
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Ordem() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        LivingEntity target = Corrente.aimed(caster, level, RANGE);
        if (target == null) {
            SubCommon.warn(caster, "makima.ordem_none");
            return false;
        }
        Kind kind = pick(caster, target);
        SubCommon.pose(caster, "makima.ordem_" + kind.key());
        Makima.eyes(caster, kind.length);
        SubCommon.busy(caster, HIT + 2);
        DbCommon.cooldown(caster, PowerData.SLOT_SHIFT + 1, kind.cooldown);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = target.position();
        Runnable[] hold = new Runnable[1];
        boolean boss = Makima.boss(target);
        Tasks.add(age -> {
            boolean lost = !target.isAlive() || target.isRemoved() || target.level() != level;
            if (lost || (guard.broken() && age < HIT)) {
                if (hold[0] != null) {
                    hold[0].run();
                }
                return true;
            }
            if (age == SPEAK) {
                Makima.say(caster, kind.key());
                Makima.gray(level, caster.position(), 50, 0.8f);
                Vec3 mouth = caster.getEyePosition().add(0, -0.1, 0);
                MkNet.send(level, MkNet.VOICE, caster.getId(), mouth, target.getEyePosition().subtract(mouth), 0, 0, 0);
                SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.35f, 1.9f);
            }
            if (age == HIT) {
                Makima.obeyEyes(target, kind.length - HIT);
                obey(caster, level, target, kind, start, boss, hold);
            }
            if (age >= kind.length) {
                if (hold[0] != null) {
                    hold[0].run();
                }
                return true;
            }
            return false;
        });
        return true;
    }

    private static Kind pick(LivingEntity caster, LivingEntity target) {
        if (target.getHealth() <= target.getMaxHealth() * DIE_LIFE) {
            return Kind.MORRA;
        }
        if (target.distanceTo(caster) > NEAR) {
            return Kind.VENHA;
        }
        return Makima.inferior(caster, target) ? Kind.WOOF : Kind.AJOELHE;
    }

    private static void obey(LivingEntity caster, ServerLevel level, LivingEntity target, Kind kind, Vec3 start, boolean boss, Runnable[] hold) {
        int left = kind.length - HIT;
        switch (kind) {
            case VENHA -> {
                // Walks stiffly to 2.2 blocks from her (8 poses a second, like a puppet) for 3.2 s, then kneels.
                Vec3 to = SubCommon.flat(caster.position().subtract(start));
                Vec3 goal = to.lengthSqr() < 1e-4 ? start : caster.position().subtract(to.normalize().scale(2.2));
                Vec3 ground = SubCommon.ground(level, goal);
                Vec3 end = new Vec3(goal.x, ground.y, goal.z);
                hold[0] = Freeze.hold(target, left, age -> {
                    double q = Math.floor(Math.min(age, 64) / 2.5) * 2.5 / 60.0;
                    double s = SubCommon.smooth((float) Math.min(1, q));
                    return start.lerp(end, s);
                });
                Stun.apply(target, boss ? 50 : left);
                SubCommon.pose(target, "makima.venha");
                Tasks.later(64, () -> {
                    if (target.isAlive()) {
                        SubCommon.pose(target, "makima.ajoelhado");
                    }
                });
            }
            case AJOELHE -> {
                hold[0] = Freeze.hold(target, left);
                Stun.apply(target, boss ? 50 : 50);
                Combat.hurt(caster, target, 12);
                SubCommon.pose(target, "makima.ajoelhado");
                Tasks.later(2, () -> {
                    MkNet.send(level, MkNet.CRACK, target.getId(), target.position(), 1.6f, 1, target.getId() * 2.3f + 0.7f);
                    MkNet.send(level, MkNet.DUST, target.getId(), SubCommon.ground(level, target.position()), 3, 0, 0);
                    MkNet.send(level, MkNet.BITS, target.getId(), target.position(), new Vec3(0, 0.5, 0), 22, 0.6f, 0);
                    SubCommon.sound(level, target.position(), SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
                    SubCommon.sound(level, target.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.2f, 0.6f);
                });
            }
            case WOOF -> {
                // On all fours and panting; "Au!" a moment later. Long: being beneath her is a long humiliation.
                hold[0] = Freeze.hold(target, left);
                Stun.apply(target, boss ? 60 : left);
                SubCommon.pose(target, "makima.woof");
                Tasks.later(5, () -> MkNet.send(level, MkNet.BITS, target.getId(), target.position().add(target.getLookAngle().scale(0.5)),
                        new Vec3(0, 0.3, 0), 10, 0.4f, 0));
                Tasks.later(11, () -> {
                    if (target.isAlive()) {
                        Makima.sayText(target, "fala.battlegroundsmod.makima.au", Makima.PUPPET_VOICE);
                        SubCommon.sound(level, target.position(), SoundEvents.WOLF_AMBIENT, 1f, 0.9f);
                    }
                });
            }
            case MORRA -> {
                // Silence, the color goes, the rings in their eyes go out and they fall.
                hold[0] = Freeze.hold(target, left);
                Stun.apply(target, 40);
                Makima.gray(level, target.position(), 44, 0.85f);
                SubCommon.pose(target, "makima.morra");
                Tasks.later(22, () -> {
                    if (!target.isAlive()) {
                        return;
                    }
                    MkNet.send(level, MkNet.DUST, target.getId(), SubCommon.ground(level, target.position()), 3, 0, 0);
                    SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);
                    if (boss) {
                        Combat.hurt(caster, target, 110);
                        Stun.apply(target, 40);
                    } else {
                        Corrente.kill(caster, target, 400);
                    }
                });
            }
        }
        Ritual.mark(caster, target);
    }
}
