package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Miwa (SubAlma of Jujutsu, tier 8), the Visualizador's Miwa tab: New Shadow Style.
// Z Batto: hold to stay in the stance (the longer, the harder), let go and the sword is drawn and back in one tick: the
//   cut appears on whoever is within 3 blocks a moment later.
// X Simple Domain: a circle round her; anyone who steps in is cut and thrown out on their own, shots that come in are cut
//   down, and a Domain Expansion has no hold on her inside it. Press again, or walk out of it, to undo it.
// C Line Cut: she crosses 6 blocks in an instant through the target; the cut appears a moment later.
public final class Miwa {
    private static final double RADIUS = 2.21;
    private static final int DOMAIN_TICKS = 300;
    private static final Set<UUID> DOMAINS = new HashSet<>();

    private Miwa() {
    }

    public static Power create() {
        return new Power("miwa", 0x6FB8E8, 0.95f, List.of(
                new Ability(100, 0, Miwa::batto),
                new Ability(300, 0, Miwa::simpleDomain),
                new Ability(160, 0, Miwa::lineCut),
                new Ability(1000, 0, Miwa::ultMissing)));
    }

    private static void cut(LivingEntity caster, ServerLevel level, LivingEntity t, Vec3 swing, float damage, int preset) {
        Vec3 c = Combat.center(t);
        SubNet.send(level, SubNet.MIWA_LINE, 0, c, swing, 1.2f, 0.07f, 0);
        Combat.hurt(caster, t, damage);
        Stun.apply(t, JjCommon.control(caster, t, 15));
        if (preset >= 0) {
            DbCommon.screen(level, c, preset);
        }
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.6f);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.4f);
    }

    // ---------- Z: Batto ----------

    private static boolean batto(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int full = 40;
        int most = 60;
        int bossHold = 8 + SubCommon.RANDOM.nextInt(16);
        SubCommon.pose(caster, "jj.batto_stance");
        SubCommon.busy(caster, most + 10);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            boolean held = SubCommon.held(caster, age, bossHold);
            if (age < 4 || (held && age < most)) {
                Charge.report(caster, Math.min(1, age / (double) full));
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                return false;
            }
            float k = Math.min(1, age / (float) full);
            SubCommon.pose(caster, "jj.batto_draw");
            Vec3 fwd = SubCommon.flat(caster.getLookAngle());
            SubNet.send(level, SubNet.MIWA_ARC, 0, Combat.center(caster), fwd, 3.2f, 0, 0);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.8f);
            List<LivingEntity> hit = Combat.inCone(caster, 3.4, 0.2);
            if (hit.isEmpty()) {
                SubCommon.warn(caster, "miwa.miss");
                JjCommon.shorten(caster, 0, 30);
            }
            Vec3 swing = fwd.cross(new Vec3(0, 1, 0)).add(0, 0.25, 0);
            Tasks.later(4, () -> {
                boolean first = true;
                for (LivingEntity t : hit) {
                    if (t.isAlive()) {
                        cut(caster, level, t, swing, 7 + 7 * k, first ? 43 : -1);
                        first = false;
                    }
                }
            });
            caster.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).busyTicks = 4;
            return true;
        });
        return true;
    }

    // ---------- X: Simple Domain ----------

    private static boolean simpleDomain(LivingEntity caster, ServerLevel level) {
        if (DOMAINS.contains(caster.getUUID())) {
            DOMAINS.remove(caster.getUUID());
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 center = caster.position();
        int id = caster.getId();
        DOMAINS.add(caster.getUUID());
        SubCommon.pose(caster, "jj.batto_stance");
        SubCommon.busy(caster, 6);
        DbCommon.cooldown(caster, 1, 10);
        SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        boolean mob = !(caster instanceof Player);
        int mobTicks = 120;
        if (mob) {
            // Mobs keep still in it (they would walk out of it at once).
            Freeze.holdLookFree(caster, mobTicks);
        }
        Map<Integer, Long> lastCut = new HashMap<>();
        Set<Integer> inside = new HashSet<>();
        Tasks.add(age -> {
            boolean moved = new Vec3(caster.getX() - center.x, 0, caster.getZ() - center.z).length() > 0.8;
            boolean end = guard.broken() || !DOMAINS.contains(caster.getUUID()) || moved || age >= (mob ? mobTicks : DOMAIN_TICKS);
            if (end) {
                DOMAINS.remove(caster.getUUID());
                if (mob) {
                    Freeze.free(caster);
                }
                SubCommon.clearState(caster, JjCommon.SIMPLE_DOMAIN);
                SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, Vec3.ZERO, 0, (float) RADIUS, 1);
                SubNet.send(level, SubNet.DOMAIN_POP, 0, center, Vec3.ZERO, (float) RADIUS, 0, 0);
                SubCommon.sound(level, center, SoundEvents.BEACON_DEACTIVATE, 1f, 1.6f);
                DbCommon.cooldown(caster, 1, 300);
                return true;
            }
            SubCommon.setState(caster, JjCommon.SIMPLE_DOMAIN, 3);
            SubNet.send(level, SubNet.SIMPLE_DOMAIN, id, center, Vec3.ZERO, SubCommon.smooth(age / 8f), (float) RADIUS, 0);
            long now = level.getGameTime();
            Set<Integer> nowIn = new HashSet<>();
            for (LivingEntity e : Combat.inRadius(level, center.add(0, 0.9, 0), RADIUS + 0.4, caster)) {
                if (Math.abs(e.getY() - center.y) > 2.5) {
                    continue;
                }
                nowIn.add(e.getId());
                Long last = lastCut.get(e.getId());
                boolean entered = !inside.contains(e.getId());
                if ((entered || (last != null && now - last > 20)) && (last == null || now - last > 10)) {
                    lastCut.put(e.getId(), now);
                    Vec3 to = SubCommon.flat(e.position().subtract(center));
                    SubNet.send(level, SubNet.MIWA_ARC, 0, Combat.center(caster), to, (float) RADIUS, 0, 0);
                    SubCommon.pose(caster, "jj.batto_draw");
                    cut(caster, level, e, to.cross(new Vec3(0, 1, 0)).add(0, 0.3, 0), 7, 44);
                    Combat.push(e, to, 1.0, 0.3);
                }
            }
            inside.clear();
            inside.addAll(nowIn);
            // Shots that come in are cut down.
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(RADIUS, 2.5, RADIUS))) {
                if (p.getOwner() != caster && p.position().subtract(center).horizontalDistance() < RADIUS) {
                    SubNet.send(level, SubNet.MIWA_LINE, 0, p.position(), p.getDeltaMovement().cross(new Vec3(0, 1, 0)), 0.5f, 0.05f, 0);
                    SubCommon.sound(level, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 2f);
                    p.discard();
                }
            }
            return false;
        });
        return false;
    }

    // ---------- C: Line Cut ----------

    private static boolean lineCut(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        double length = 6;
        int dash = 3;
        LivingEntity t = JjCommon.target(caster, length + 2);
        Vec3 dir = t != null ? SubCommon.flat(t.position().subtract(caster.position())) : SubCommon.flat(caster.getLookAngle());
        Vec3 start = caster.position();
        Vec3 goal = start.add(dir.scale(length));
        // Never into a wall: stops at the last free spot.
        for (double d = 0.5; d <= length; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            if (SubCommon.solid(level, p.add(0, 0.5, 0)) || SubCommon.solid(level, p.add(0, 1.5, 0))) {
                goal = start.add(dir.scale(Math.max(0, d - 0.6)));
                break;
            }
        }
        Vec3 end = goal;
        Vec3[] at = {start};
        SubCommon.pose(caster, "jj.line_cut");
        SubCommon.busy(caster, dash + 10);
        JjCommon.carry(caster, dash, at);
        SubCommon.sound(level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.9f);
        List<LivingEntity> hit = Combat.alongLine(level, start.add(0, 1, 0), end.add(0, 1, 0), 0.9, caster);
        SubNet.send(level, SubNet.DASH_LINE, 0, start.add(0, 1, 0), end.subtract(start), 0, 0, 0);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            at[0] = start.lerp(end, Math.min(1, (age + 1) / (float) dash));
            if (age < dash) {
                return false;
            }
            Freeze.free(caster);
            if (hit.isEmpty()) {
                SubCommon.warn(caster, "miwa.miss");
                JjCommon.shorten(caster, 2, 50);
                return true;
            }
            for (LivingEntity e : hit) {
                Stun.apply(e, 8);
            }
            Tasks.later(8, () -> {
                boolean first = true;
                for (LivingEntity e : hit) {
                    if (e.isAlive()) {
                        cut(caster, level, e, dir.add(0, 0.15, 0), 10, first ? 44 : -1);
                        first = false;
                    }
                }
            });
            return true;
        });
        return true;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }

    static void clear() {
        DOMAINS.clear();
    }
}
