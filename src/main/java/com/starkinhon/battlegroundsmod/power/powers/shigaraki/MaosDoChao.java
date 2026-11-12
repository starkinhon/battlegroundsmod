package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// C, Mãos do Chão (preview v4, APROVADÍSSIMO 2026-11-11: "stun muito da hora"). The palm slaps the ground (0.3 s) and a
// crack runs under it to a hand print: on the way to whoever is in front (55% of the way), or 9 blocks ahead. From 1.1 s
// it is armed (the print fades to a hint). Whoever steps on it (1.4 blocks) is held by 8 hands that burst out of the
// ground, each its own, for 2.6 s; then they crumble to dust (0.8 s). Combo: held, then Z. One trap at a time; it waits
// up to 20 s.
public final class MaosDoChao {
    static final double TRIGGER = 1.4;
    static final int ARM = 22, HOLD = 52, DISSOLVE = 16, WAIT = 400;
    private static final float DAMAGE = 18;

    private static final Map<LivingEntity, int[]> TRAP = new WeakHashMap<>();

    private MaosDoChao() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        int id = SubNet.newId();
        int[] mine = {id};
        TRAP.put(caster, mine);
        LivingEntity aim = Toque.lock(caster);
        Vec3 feet = caster.position();
        Vec3 dir;
        Vec3 p;
        if (caster instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss && aim != null && aim.distanceTo(caster) < 30) {
            // A boss sets the trap where the target will be (its warning already gave them time): halfway it caught
            // nothing (test 2026-11-12: 5-11 damage a fight in 7-8 uses).
            dir = Shigaraki.flatTo(caster, aim.position());
            Vec3 lead = aim.getDeltaMovement().multiply(8, 0, 8);
            p = aim.position().add(lead.length() > 4 ? lead.normalize().scale(4) : lead);
        } else if (aim != null && aim.distanceTo(caster) > 5 && aim.distanceTo(caster) < 30) {
            dir = Shigaraki.flatTo(caster, aim.position());
            p = feet.add(new Vec3(aim.getX() - feet.x, 0, aim.getZ() - feet.z).scale(0.55));
        } else {
            dir = Combat.flatLook(caster);
            p = feet.add(dir.scale(9));
        }
        Vec3 print = SubCommon.ground(level, p.add(0, 1, 0));
        SubCommon.busy(caster, 16);
        SubCommon.pose(caster, "shig.tapa");
        SubNet.send(level, Shigaraki.TRAP, id, print, dir, 0, 0, caster.getId());
        Tasks.add(age -> {
            if (age == 6) {
                SubCommon.sound(level, feet, SoundEvents.GRAVEL_BREAK, 1.3f, 0.5f);
                SubCommon.sound(level, feet, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);
            }
            if (TRAP.get(caster) != mine || !caster.isAlive()) {
                SubNet.send(level, Shigaraki.TRAP, id, print, dir, 3, 0, 0);
                return true;
            }
            if (age == ARM) {
                SubNet.send(level, Shigaraki.TRAP, id, print, dir, 1, 0, 0);
            }
            if (age < ARM) {
                return false;
            }
            for (LivingEntity e : Combat.inRadius(level, print.add(0, 0.9, 0), 2.2, caster)) {
                double dx = e.getX() - print.x, dz = e.getZ() - print.z;
                if (dx * dx + dz * dz < TRIGGER * TRIGGER && Math.abs(e.getY() - print.y) < 1.6) {
                    caught(caster, e, level, id, print, dir);
                    TRAP.remove(caster);
                    return true;
                }
            }
            if (age > WAIT) {
                SubNet.send(level, Shigaraki.TRAP, id, print, dir, 3, 0, 0);
                TRAP.remove(caster);
                return true;
            }
            return false;
        });
        return true;
    }

    // Held where it stepped: the hands grip in 0.3-0.7 s and hold 2.6 s (it shakes), then crumble.
    private static void caught(LivingEntity caster, LivingEntity target, ServerLevel level, int id, Vec3 print, Vec3 dir) {
        SubNet.send(level, Shigaraki.TRAP, id, target.position(), dir, 2, target.getId(), 0);
        SubCommon.sound(level, target.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        SubCommon.sound(level, target.position(), SoundEvents.GRAVEL_BREAK, 1.5f, 0.6f);
        Combat.hurt(caster, target, DAMAGE);
        Stun.apply(target, HOLD + 4);
        Vec3 at = target.position();
        Tasks.addSystem(age -> {
            if (!target.isAlive() || age >= HOLD) {
                return true;
            }
            // pinned: the hands don't let it move
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            if (target.distanceToSqr(at) > 0.04) {
                target.teleportTo(at.x, target.getY(), at.z);
            }
            target.hurtMarked = true;
            return false;
        });
        Tasks.later(HOLD, () -> SubCommon.sound(level, at, SoundEvents.SAND_BREAK, 1.2f, 0.5f));
    }
}
