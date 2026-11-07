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
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Itadori (SubAlma of Jujutsu, tier 6), the Visualizador's Itadori tab. Passive: his body is the strongest of the
// SubAlmas, the base punches hit 15% harder.
// Z Divergent Fist: the punch lands light, and a moment later the cursed energy behind it lands again, much harder: the
//   second impact throws and breaks a guard.
// X Black Flash: a ring closes on his fist; press again right when it closes (a window of 2 ticks) and the cursed energy
//   meets the blow within a millionth of a second: 2.5x the damage, black lightning. After one he is in the zone for 5 s:
//   the window doubles and he can chain up to 4. Pressed too early or too late: a plain punch.
// C Manji Kick: he guards for a second; a blow from the front is caught and answered with a high kick.
// V Dash: he crouches, the ground cracks, and he runs 9 blocks through anything in the way into a flying knee.
public final class Itadori {
    private static final int RING = 18;
    private static final int ZONE_TICKS = 100;
    // The Black Flash ring of each Itadori: when it started, and how many he chained in this zone.
    private static final Map<UUID, long[]> RINGS = new HashMap<>();
    // The Itadoris whose zone aura is already burning.
    private static final java.util.Set<UUID> AURA = new java.util.HashSet<>();

    private Itadori() {
    }

    public static Power create() {
        return new Power("itadori", 0xE0473C, 1.15f, List.of(
                new Ability(140, 0, Itadori::divergent),
                new Ability(300, 0, Itadori::blackFlash),
                new Ability(160, 0, Itadori::manji),
                new Ability(600, 0, Itadori::dash)));
    }

    // ---------- Z: Divergent Fist ----------

    private static boolean divergent(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int land = 5;
        int delay = 14;
        SubCommon.pose(caster, "jj.div_punch");
        SubCommon.busy(caster, land + 8);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        LivingEntity[] hit = {null};
        Vec3[] at = {null};
        Tasks.add(age -> {
            if (guard.broken() && age <= land) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age <= land + 2) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, SubCommon.smooth(age / 4f), 0, 0);
            }
            if (age == land) {
                LivingEntity t = JjCommon.target(caster, 4);
                if (t == null || t.distanceTo(caster) > 4.2) {
                    SubCommon.warn(caster, "itadori.miss");
                    JjCommon.shorten(caster, 0, 40);
                    SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                    return true;
                }
                hit[0] = t;
                at[0] = JjCommon.contact(caster, t);
                Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
                double h = at[0].y - SubCommon.ground(level, at[0]).y;
                SubNet.send(level, SubNet.DIVERGENT, caster.getId(), at[0], dir, delay, 0, (float) h);
                DbCommon.screen(level, at[0], 24);
                Combat.hurt(caster, t, 6);
                Stun.apply(t, delay + 2);
                Freeze.hold(t, delay);
                SubCommon.sound(level, at[0], SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 1.3f);
                return false;
            }
            if (hit[0] != null && age == land + delay) {
                LivingEntity t = hit[0];
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                if (!t.isAlive()) {
                    return true;
                }
                Freeze.free(t);
                Defense.breakGuard(t, caster);
                Combat.hurtMagic(caster, t, 11);
                Stun.apply(t, 20);
                Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
                Combat.push(t, dir, 1.3, 0.35);
                DbCommon.screen(level, Combat.center(t), 25);
                SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), Combat.center(t), dir, 1.2f, 0, 0);
                SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.5f);
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.7f);
                return true;
            }
            return age > land + delay;
        });
        return true;
    }

    // ---------- X: Black Flash ----------

    private static boolean blackFlash(LivingEntity caster, ServerLevel level) {
        long now = level.getGameTime();
        long[] ring = RINGS.get(caster.getUUID());
        if (ring != null && ring[0] >= 0 && now - ring[0] <= RING + window(caster)) {
            strike(caster, level, (int) (now - ring[0]));
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        long start = now;
        long chain = ring != null && SubCommon.hasState(caster, JjCommon.ZONE) ? ring[1] : 0;
        RINGS.put(caster.getUUID(), new long[] {start, chain});
        SubCommon.pose(caster, "jj.bf_ready");
        DbCommon.cooldown(caster, 1, 3);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 0.6f, 1.8f);
        boolean mob = !(caster instanceof Player);
        // Mobs press on their own: most of the time right on the ring.
        int mobPress = RING + (SubCommon.RANDOM.nextFloat() < 0.65f ? 0 : (SubCommon.RANDOM.nextBoolean() ? -6 : 6));
        Tasks.add(age -> {
            long[] r = RINGS.get(caster.getUUID());
            boolean mine = r != null && r[0] == start;
            if (guard.broken() || !mine) {
                SubNet.send(level, SubNet.BF_RING, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, 1, 0, 1);
                return true;
            }
            if (mob && age == mobPress) {
                strike(caster, level, age);
                return true;
            }
            if (age > RING + window(caster)) {
                // Never pressed: the ring fades.
                SubNet.send(level, SubNet.BF_RING, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, 1, 0, 1);
                r[0] = -1;
                SubCommon.warn(caster, "itadori.bf_late");
                DbCommon.cooldown(caster, 1, 120);
                return true;
            }
            SubNet.send(level, SubNet.BF_RING, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, Math.min(1, age / (float) RING), 0, 0);
            return false;
        });
        return false;
    }

    private static int window(LivingEntity caster) {
        return SubCommon.hasState(caster, JjCommon.ZONE) ? 4 : 2;
    }

    // The second press, age ticks after the ring started.
    private static void strike(LivingEntity caster, ServerLevel level, int age) {
        long[] r = RINGS.get(caster.getUUID());
        if (r == null) {
            return;
        }
        r[0] = -1;
        SubNet.send(level, SubNet.BF_RING, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, 1, 0, 1);
        boolean timed = Math.abs(age - RING) <= window(caster);
        LivingEntity t = JjCommon.target(caster, 4);
        if (t != null && t.distanceTo(caster) > 4.5) {
            t = null;
        }
        if (!timed || t == null) {
            // A plain punch.
            SubCommon.pose(caster, "jj.div_punch");
            SubCommon.busy(caster, 8);
            if (t != null) {
                Combat.hurt(caster, t, 6);
                Stun.apply(t, 8);
                DbCommon.hit(level, JjCommon.contact(caster, t), 0.7f, Db.WHITE);
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
            }
            SubCommon.warn(caster, t == null ? "itadori.miss" : age < RING ? "itadori.bf_early" : "itadori.bf_late");
            SubCommon.clearState(caster, JjCommon.ZONE);
            DbCommon.cooldown(caster, 1, 160);
            return;
        }
        LivingEntity target = t;
        SubCommon.Guard guard = SubCommon.guard(caster);
        int fit = 18;
        Vec3 contact = JjCommon.contact(caster, target);
        Vec3 dir = target.position().subtract(caster.position()).normalize();
        Vec3 hold = target.position();
        SubCommon.pose(caster, "jj.bf_punch");
        SubCommon.busy(caster, fit + 6);
        Freeze.hold(caster, fit);
        Freeze.hold(target, fit + 2, a -> hold);
        sendFlash(level, new ModPayloads.BlackFlash(1, caster.getId(), contact, dir, 1f, fit));
        DbCommon.screen(level, contact, 26);
        Defense.breakGuard(target, caster);
        Combat.hurt(caster, target, 10);
        SubCommon.sound(level, contact, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        SubCommon.sound(level, contact, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
        // The zone: the window doubles and he can chain; the 4th ends it.
        r[1]++;
        boolean last = r[1] >= 4;
        if (last) {
            SubCommon.clearState(caster, JjCommon.ZONE);
            r[1] = 0;
            DbCommon.cooldown(caster, 1, 300);
        } else {
            SubCommon.setState(caster, JjCommon.ZONE, ZONE_TICKS);
            DbCommon.cooldown(caster, 1, 30);
            if (caster instanceof Player player) {
                SubCommon.warn(player, "itadori.zone", (int) r[1]);
            }
            zoneAura(caster, level);
        }
        Tasks.add(a -> {
            if (!target.isAlive() || guard.broken()) {
                Freeze.free(target);
                return true;
            }
            if (a == 6 || a == 12) {
                Combat.hurtMagic(caster, target, 3);
                JjCommon.crater(level, SubCommon.ground(level, contact), 1.2 + a * 0.08, 0.8, 6);
                SubCommon.sound(level, contact, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.4f);
            }
            if (a == fit) {
                Freeze.free(target);
                sendFlash(level, new ModPayloads.BlackFlash(2, caster.getId(), contact, dir, 1f, 0));
                Combat.hurtMagic(caster, target, 9);
                Stun.apply(target, 30);
                target.setDeltaMovement(dir.scale(2.2).add(0, 0.5, 0));
                target.hurtMarked = true;
                JjCommon.crater(level, SubCommon.ground(level, contact), 2.4, 1.4, 14);
                SubCommon.sound(level, contact, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
                return true;
            }
            return false;
        });
    }

    // Both fists burn with cursed energy while he is in the zone.
    private static void zoneAura(LivingEntity caster, ServerLevel level) {
        if (!AURA.add(caster.getUUID())) {
            return;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, JjCommon.ZONE);
            if (guard.broken() || !on) {
                AURA.remove(caster.getUUID());
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 1, 1);
                return true;
            }
            SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0.8f, 1, 0);
            return false;
        });
    }

    private static void sendFlash(ServerLevel level, ModPayloads.BlackFlash payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(payload.pos()) < 160 * 160) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // ---------- C: Manji Kick ----------

    private static boolean manji(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int up = 22;
        SubCommon.setState(caster, JjCommon.MANJI, up);
        SubCommon.pose(caster, "jj.manji_guard");
        SubCommon.busy(caster, up);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, JjCommon.MANJI);
            if (guard.broken() || !on) {
                SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                if (!guard.broken() && age >= up - 1) {
                    // Nobody struck: the guard comes back sooner.
                    JjCommon.shorten(caster, 2, 50);
                }
                return true;
            }
            SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, SubCommon.smooth(age / 3f), 0, 0);
            return false;
        });
        return true;
    }

    // Called by JjCommon when a blow from the front hits the guard.
    static void counter(LivingEntity caster, ServerLevel level, LivingEntity enemy) {
        SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
        SubCommon.pose(caster, "jj.manji_kick");
        SubCommon.busy(caster, 10);
        Vec3 dir = SubCommon.flat(enemy.position().subtract(caster.position()));
        Vec3 mid = caster.position().add(dir.scale(1.2)).add(0, 1.3, 0);
        SubNet.send(level, SubNet.ARC, 0, mid, dir, 1.6f, Db.BLUE, 1);
        Stun.apply(enemy, 6);
        SubCommon.sound(level, caster.position(), SoundEvents.SHIELD_BLOCK, 1f, 1.3f);
        Tasks.later(3, () -> {
            if (!enemy.isAlive() || !caster.isAlive()) {
                return;
            }
            Vec3 c = Combat.center(enemy);
            Defense.breakGuard(enemy, caster);
            Combat.hurt(caster, enemy, 12);
            Stun.apply(enemy, 25);
            Combat.push(enemy, dir, 1.4, 0.7);
            SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), c, dir.add(0, 0.8, 0), 1f, 0, 0);
            DbCommon.screen(level, c, 27);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.7f);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.9f);
        });
    }

    // ---------- V: Dash ----------

    private static boolean dash(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int charge = 14;
        int run = 14;
        double length = 9;
        Vec3 feet = caster.position();
        SubCommon.pose(caster, "jj.dash_charge");
        SubCommon.busy(caster, charge + run + 8);
        Freeze.holdLookFree(caster, charge);
        SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, feet), Vec3.ZERO, 1.8f, 6, 1);
        SubCommon.sound(level, feet, SoundEvents.STONE_BREAK, 1.4f, 0.6f);
        Vec3[] at = {feet};
        Vec3[] dir = {null};
        boolean[] struck = {false};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age < charge) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0.6f * SubCommon.smooth(age / 8f), 1, 0);
                if (age == charge / 2) {
                    SubNet.send(level, SubNet.CRACKS, 1, SubCommon.ground(level, feet), Vec3.ZERO, 2.6f, 9, 1);
                    SubCommon.sound(level, feet, SoundEvents.STONE_BREAK, 1.4f, 0.5f);
                }
                return false;
            }
            if (age == charge) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 1, 1);
                dir[0] = SubCommon.flat(caster.getLookAngle());
                LivingEntity t = JjCommon.target(caster, length + 2);
                if (t != null) {
                    dir[0] = SubCommon.flat(t.position().subtract(caster.position()));
                }
                at[0] = caster.position();
                JjCommon.carry(caster, run, at);
                SubCommon.pose(caster, "jj.dash_run");
                SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), feet.add(0, 0.3, 0), dir[0].scale(-1), 1.2f, 0, 0);
                SubCommon.sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.6f);
            }
            int t = age - charge;
            if (t < run && !struck[0]) {
                Vec3 next = at[0].add(dir[0].scale(length / run));
                JjCommon.carve(level, next.add(0, 1, 0), 1.1, at[0]);
                if (SubCommon.solid(level, next.add(0, 0.5, 0)) || SubCommon.solid(level, next.add(0, 1.5, 0))) {
                    // A wall he can't break stops him.
                    struck[0] = true;
                } else {
                    Vec3 floor = SubCommon.ground(level, next);
                    at[0] = new Vec3(next.x, Math.abs(floor.y - next.y) < 1.2 ? floor.y : next.y, next.z);
                }
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), at[0], dir[0], 0, 0, 0);
                for (LivingEntity e : Combat.inRadius(level, at[0].add(dir[0].scale(0.6)).add(0, 1, 0), 1.3, caster)) {
                    knee(caster, level, e, dir[0]);
                    struck[0] = true;
                    break;
                }
                if (t % 3 == 0) {
                    SubCommon.sound(level, at[0], SoundEvents.STONE_STEP, 1f, 0.6f);
                }
                return false;
            }
            Freeze.free(caster);
            SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
            if (!struck[0]) {
                SubCommon.warn(caster, "itadori.miss");
            }
            return true;
        });
        return true;
    }

    private static void knee(LivingEntity caster, ServerLevel level, LivingEntity t, Vec3 dir) {
        SubCommon.pose(caster, "jj.dash_knee");
        Vec3 c = Combat.center(t);
        Defense.breakGuard(t, caster);
        Combat.hurt(caster, t, 20);
        Stun.apply(t, 30);
        Combat.push(t, dir, 1.8, 0.9);
        DbCommon.screen(level, c, 28);
        SubNet.send(level, SubNet.INK_BURST, SubNet.newId(), c, dir, 1.6f, 0, 0);
        SubNet.send(level, SubNet.CRACKS, 2, SubCommon.ground(level, c), Vec3.ZERO, 2.2f, 7, 1);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.8f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.2f);
    }

    public static void clear() {
        RINGS.clear();
        AURA.clear();
    }
}
