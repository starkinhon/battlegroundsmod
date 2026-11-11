package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Itadori (SubAlma of Jujutsu, tier 6), kit remade with Stark (2026-11-11, preview forja/itadori-kit v2).
// Passive Corpo Monstruoso: the base punches hit 15% harder, and every blow he lands fills his Ritmo (it drains after 5 s
//   without hitting). The more Ritmo, the wider the Black Flash window.
// Z Punho Divergente: three fast punches (the first with a short step in); the third carries the delayed cursed energy,
//   which lands again a moment later, through the back. Holding Z keeps the delay stored on the target (a blue mark):
//   letting go sets it off wherever the target went. Weaker on a guard, but it breaks it.
// X Black Flash: a ring closes on his fist; press again right when it closes and the cursed energy meets the blow: black
//   lightning, a lot of damage. Then the zone for 5 s: the window doubles and he can chain up to 4 (the 4th sinks the ground).
// C Chute Manji: he guards for a second; a blow from the front is caught and answered with a high kick, and a shot from
//   afar is kicked back at whoever threw it.
// V Corte de Tesoura (his Dismantle, as in the manga): his palm touches something and a dashed "cut here" line with white
//   scissors goes round it. Up to 3 marks (an enemy, a pillar, a wall); V again closes every pair of scissors at once, or
//   they close on their own 3 s after the last mark: the scissors run along the line and cut. Pillars fall, enemies bleed.
// B Golpe na Alma: a punch to the soul. On someone using or charging a power, the soul is knocked out for a moment: the
//   move is cut and there is no power for 3 s. On someone doing nothing, just a punch.
public final class Itadori {
    private static final int RING = 18;
    private static final int ZONE_TICKS = 100;
    private static final int B = PowerData.SLOT_EXTRA;
    // The Black Flash ring of each Itadori: when it started, and how many he chained in this zone.
    private static final Map<UUID, long[]> RINGS = new HashMap<>();
    // The Itadoris whose zone aura is already burning.
    private static final Set<UUID> AURA = new HashSet<>();
    // Ritmo: how much (0..1) and the game time of the last blow landed.
    private static final Map<UUID, double[]> RITMO = new HashMap<>();
    // The Corte de Tesoura of each Itadori while its marks are up.
    private static final Map<UUID, Cut> CUTS = new HashMap<>();

    private Itadori() {
    }

    public static Power create() {
        return new Power("itadori", 0xE0473C, 1.15f, List.of(
                new Ability(140, 0, Itadori::divergent),
                new Ability(300, 0, Itadori::blackFlash),
                new Ability(160, 0, Itadori::manji),
                new Ability(500, 0, Itadori::tesoura),
                new Ability(500, 0, Itadori::alma)));
    }

    // ---------- Passive: Ritmo ----------

    public static float ritmo(LivingEntity e) {
        double[] r = RITMO.get(e.getUUID());
        if (r == null) {
            return 0;
        }
        long idle = e.level().getGameTime() - (long) r[1];
        return (float) Math.max(0, Math.min(1, r[0] - Math.max(0, idle - 100) * 0.01));
    }

    // Called by JjCommon on every blow an Itadori lands.
    static void landed(LivingEntity itadori) {
        RITMO.put(itadori.getUUID(), new double[] {Math.min(1, ritmo(itadori) + 0.12), itadori.level().getGameTime()});
    }

    // ---------- Z: Punho Divergente ----------

    private static boolean divergent(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int[] lands = {3, 7, 11};
        String[] poses = {"jj.scale_punch_l", "jj.scale_punch", "jj.div_punch"};
        int delay = 8;
        int last = lands[2];
        SubCommon.busy(caster, last + 6);
        LivingEntity first = JjCommon.target(caster, 6);
        if (first != null && first.distanceTo(caster) > 2.2) {
            // The short step in.
            Vec3 step = SubCommon.flat(first.position().subtract(caster.position())).scale(Math.min(0.9, (first.distanceTo(caster) - 1.6) * 0.35));
            caster.setDeltaMovement(step.x, caster.getDeltaMovement().y, step.z);
            caster.hurtMarked = true;
        }
        LivingEntity[] hit = {null};
        boolean[] stored = {false};
        int[] storedAt = {0};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                if (hit[0] != null) {
                    SubNet.send(level, SubNet.DIV_MARK, hit[0].getId(), hit[0].position(), Vec3.ZERO, 0, 0, 1);
                    Freeze.free(hit[0]);
                }
                return true;
            }
            for (int i = 0; i < 3; i++) {
                if (age == lands[i] - 3 || (i == 0 && age == 0)) {
                    SubCommon.pose(caster, poses[i]);
                    SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.3f + 0.15f * i);
                }
            }
            if (age <= last + 2) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, SubCommon.smooth(age / 4f), 0, 0);
            }
            if (age == lands[0] || age == lands[1]) {
                LivingEntity t = JjCommon.target(caster, 4);
                if (t != null && t.distanceTo(caster) < 4.4) {
                    Combat.hurt(caster, t, 3);
                    Stun.apply(t, 10);
                    DbCommon.hit(level, JjCommon.contact(caster, t), 0.6f, Db.WHITE);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 1.4f);
                }
                return false;
            }
            if (age == last) {
                LivingEntity t = JjCommon.target(caster, 4);
                if (t == null || t.distanceTo(caster) > 4.4) {
                    SubCommon.warn(caster, "itadori.miss");
                    JjCommon.shorten(caster, 0, 40);
                    SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                    return true;
                }
                hit[0] = t;
                Vec3 at = JjCommon.contact(caster, t);
                Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
                double h = at.y - SubCommon.ground(level, at).y;
                SubNet.send(level, SubNet.DIVERGENT, caster.getId(), at, dir, delay, 0, (float) h);
                DbCommon.screen(level, at, 24);
                Combat.hurt(caster, t, 5);
                Stun.apply(t, delay + 4);
                Freeze.hold(t, delay);
                SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 1.2f);
                return false;
            }
            if (hit[0] == null) {
                return age > last;
            }
            LivingEntity t = hit[0];
            // The body aura while the cursed energy is late.
            if (age > last && age < last + delay && !stored[0]) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 1, 2, 0);
            }
            if (age == last + delay - 1 && !stored[0] && SubCommon.held(caster, age, 0) && t.isAlive()) {
                // Holding Z: the delay stays stored on the target, who is free to move.
                stored[0] = true;
                storedAt[0] = age;
                Freeze.free(t);
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                SubCommon.sound(level, t.position(), SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.6f);
                return false;
            }
            if (stored[0]) {
                boolean go = !SubCommon.held(caster, age, 0) || age - storedAt[0] >= 60 || !t.isAlive() || t.distanceTo(caster) > 32;
                SubNet.send(level, SubNet.DIV_MARK, t.getId(), t.position(), Vec3.ZERO, 1, 0, go ? 1 : 0);
                if (!go) {
                    return false;
                }
                if (t.isAlive() && t.distanceTo(caster) <= 32) {
                    Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
                    Vec3 c = Combat.center(t);
                    SubNet.send(level, SubNet.DIVERGENT, caster.getId(), c, dir, 0, 1, (float) (c.y - SubCommon.ground(level, c).y));
                    second(caster, level, t, dir);
                }
                return true;
            }
            if (age == last + delay) {
                SubNet.send(level, SubNet.DIV_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                Freeze.free(t);
                if (t.isAlive()) {
                    second(caster, level, t, SubCommon.flat(t.position().subtract(caster.position())));
                }
                return true;
            }
            return age > last + delay;
        });
        return true;
    }

    // The delayed cursed energy landing: through the back, throws, breaks a guard (less damage on a guard).
    private static void second(LivingEntity caster, ServerLevel level, LivingEntity t, Vec3 dir) {
        boolean guarding = Defense.isGuarding(t);
        Defense.breakGuard(t, caster);
        Combat.hurtMagic(caster, t, guarding ? 6 : 12);
        Stun.apply(t, 25);
        Combat.push(t, dir, 1.3, 0.35);
        DbCommon.screen(level, Combat.center(t), 25);
        SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.5f);
        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.7f);
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
            // b: how wide the window is (the Ritmo and the zone), for the ring's rim.
            SubNet.send(level, SubNet.BF_RING, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, Math.min(1, age / (float) RING), window(caster) / 8f, 0);
            return false;
        });
        return false;
    }

    // The window round the ring closing: 2 ticks, up to 4 with the Ritmo full, doubled in the zone.
    private static int window(LivingEntity caster) {
        int w = 2 + Math.round(2 * ritmo(caster));
        return SubCommon.hasState(caster, JjCommon.ZONE) ? w * 2 : w;
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
        // The zone: the window doubles and he can chain; the 4th ends it and sinks the ground.
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
                JjCommon.crater(level, SubCommon.ground(level, contact), last ? 4 : 2.4, last ? 2.4 : 1.4, last ? 30 : 14);
                SubCommon.sound(level, contact, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, last ? 0.6f : 0.8f);
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

    // ---------- C: Chute Manji ----------

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
            DbCommon.hit(level, c, 1.1f, Db.BLUE);
            DbCommon.screen(level, c, 27);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.7f);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.9f);
        });
    }

    // A shot coming at an Itadori with the guard up, from the front: he kicks it back at whoever threw it. Called from
    // the projectile impact event; true when the shot was kicked back (the impact is cancelled).
    public static boolean reflect(Projectile shot, Entity hitEntity) {
        if (!(hitEntity instanceof LivingEntity itadori) || !(itadori.level() instanceof ServerLevel level)
                || !SubCommon.hasState(itadori, JjCommon.MANJI)) {
            return false;
        }
        Vec3 v = shot.getDeltaMovement();
        Vec3 from = SubCommon.flat(v.scale(-1));
        if (from.dot(SubCommon.flat(itadori.getLookAngle())) <= 0.1) {
            return false;
        }
        Entity owner = shot.getOwner();
        double speed = Math.max(1.2, v.length() * 1.4);
        Vec3 back = owner != null && owner.isAlive() && owner != itadori
                ? Combat.center(owner).subtract(shot.position()).normalize()
                : v.normalize().scale(-1);
        shot.setDeltaMovement(back.scale(speed));
        shot.setOwner(itadori);
        shot.hurtMarked = true;
        if (shot instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        SubCommon.pose(itadori, "jj.manji_kick");
        Vec3 dir = SubCommon.flat(back);
        SubNet.send(level, SubNet.ARC, 0, itadori.position().add(dir.scale(1.1)).add(0, 1.3, 0), dir, 1.4f, Db.BLUE, 1);
        DbCommon.hit(level, shot.position(), 0.8f, Db.WHITE);
        SubCommon.sound(level, shot.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 1.1f);
        return true;
    }

    // ---------- V: Corte de Tesoura ----------

    private static final class Mark {
        final int id = SubNet.newId();
        LivingEntity entity;
        Vec3 center;
        float radius;
    }

    private static final class Cut {
        final List<Mark> marks = new ArrayList<>();
        int lastMarkAge;
        int age;
        int closeAt = -1;
    }

    private static final int CLOSE_TICKS = 10;
    private static final int AUTO_CLOSE = 60;

    private static boolean tesoura(LivingEntity caster, ServerLevel level) {
        if (CUTS.containsKey(caster.getUUID())) {
            return false;
        }
        Mark first = markAt(caster, level, null);
        if (first == null) {
            SubCommon.warn(caster, "itadori.tesoura_nada");
            return false;
        }
        Cut cut = new Cut();
        cut.marks.add(first);
        CUTS.put(caster.getUUID(), cut);
        touched(caster, level, first);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            cut.age = age;
            if (guard.broken()) {
                end(level, cut);
                CUTS.remove(caster.getUUID());
                return true;
            }
            if (cut.closeAt < 0 && age - cut.lastMarkAge >= AUTO_CLOSE) {
                cut.closeAt = age;
            }
            float k = cut.closeAt < 0 ? 0 : Math.min(1, (age - cut.closeAt) / (float) CLOSE_TICKS);
            for (Mark m : cut.marks) {
                if (m.entity != null && !m.entity.isAlive()) {
                    continue;
                }
                Vec3 c = m.entity != null ? m.entity.position() : m.center;
                SubNet.send(level, SubNet.CUT_LOOP, m.id, c, new Vec3(m.radius, m.entity != null ? 1 : 0, m.entity != null ? m.entity.getId() : 0), k, 0, 0);
            }
            if (cut.closeAt >= 0 && age == cut.closeAt) {
                SubCommon.sound(level, caster.position(), SoundEvents.SHEEP_SHEAR, 1.2f, 0.7f);
            }
            if (k >= 1) {
                for (Mark m : cut.marks) {
                    snip(caster, level, m);
                }
                end(level, cut);
                CUTS.remove(caster.getUUID());
                return true;
            }
            return false;
        });
        return true;
    }

    // V again while the marks are up (players): another mark, or close them all. True when the press was taken.
    public static boolean tesouraPress(ServerPlayer player) {
        Cut cut = CUTS.get(player.getUUID());
        if (cut == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (cut.closeAt >= 0) {
            return true;
        }
        if (cut.marks.size() < 3) {
            Mark m = markAt(player, level, cut);
            if (m != null) {
                cut.marks.add(m);
                cut.lastMarkAge = cut.age;
                touched(player, level, m);
                return true;
            }
        }
        cut.closeAt = cut.age;
        SubCommon.pose(player, "jj.clap");
        return true;
    }

    // What his palm touches: someone in reach, or the block in the aim. Null when nothing (or it is already marked).
    private static Mark markAt(LivingEntity caster, ServerLevel level, Cut cut) {
        LivingEntity t = JjCommon.target(caster, 4);
        if (t != null && t.distanceTo(caster) <= 4.2 && (cut == null || cut.marks.stream().noneMatch(m -> m.entity == t))) {
            Mark m = new Mark();
            m.entity = t;
            m.radius = t.getBbWidth() * 0.75f + 0.05f;
            return m;
        }
        Vec3 eye = caster.getEyePosition();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(4.5)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        Vec3 c = new Vec3(pos.getX() + 0.5, hit.getLocation().y, pos.getZ() + 0.5);
        if (cut != null && cut.marks.stream().anyMatch(m -> m.entity == null && m.center.distanceTo(c) < 1.5)) {
            return null;
        }
        Mark m = new Mark();
        m.center = c;
        m.radius = 0.6f + 0.55f * width(level, pos);
        return m;
    }

    // How many blocks wide the thing touched is round pos, on its row (1 for a lone column, up to 3).
    private static int width(ServerLevel level, BlockPos pos) {
        int w = 1;
        for (int r = 1; r <= 1; r++) {
            boolean all = true;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (level.getBlockState(pos.offset(dx, 0, dz)).isAir()) {
                        all = false;
                    }
                }
            }
            if (all) {
                w = 3;
            }
        }
        return w;
    }

    private static void touched(LivingEntity caster, ServerLevel level, Mark m) {
        SubCommon.pose(caster, "jj.point_blank");
        SubCommon.busy(caster, 6);
        Vec3 c = m.entity != null ? Combat.center(m.entity) : m.center;
        SubCommon.sound(level, c, SoundEvents.SHEEP_SHEAR, 0.9f, 1.5f);
        SubCommon.sound(level, c, SoundEvents.BOOK_PAGE_TURN, 1f, 1.2f);
        if (m.entity != null) {
            Stun.apply(m.entity, 6);
        }
    }

    // The scissors meet: the thing is cut.
    private static void snip(LivingEntity caster, ServerLevel level, Mark m) {
        if (m.entity != null) {
            LivingEntity t = m.entity;
            if (!t.isAlive() || t.distanceTo(caster) > 48) {
                return;
            }
            Vec3 c = Combat.center(t);
            Defense.breakGuard(t, caster);
            Combat.hurtMagic(caster, t, 24);
            Stun.apply(t, JjCommon.isBoss(t) ? 52 : 30);
            SubNet.send(level, SubNet.CUT_SNAP, SubNet.newId(), c, Vec3.ZERO, m.radius, 1, 0);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, SubCommon.flat(c.subtract(caster.position())), 1.1f, 0, 0);
            DbCommon.screen(level, c, 26);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            return;
        }
        SubNet.send(level, SubNet.CUT_SNAP, SubNet.newId(), m.center, Vec3.ZERO, m.radius, 0, 0);
        SubCommon.sound(level, m.center, SoundEvents.STONE_BREAK, 1.4f, 0.7f);
        slice(level, m.center, m.radius, caster.position());
    }

    // A thing cut through at center's height: that row goes, and what stood above it falls, tipping away from him.
    private static void slice(ServerLevel level, Vec3 center, float radius, Vec3 from) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos base = BlockPos.containing(center);
        int r = Math.max(1, (int) Math.ceil(radius));
        Vec3 away = SubCommon.flat(center.subtract(from));
        List<BlockPos> cutRow = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) {
                    continue;
                }
                BlockPos p = base.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if (!s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && s.getDestroySpeed(level, p) >= 0) {
                    cutRow.add(p);
                }
            }
        }
        for (BlockPos p : cutRow) {
            JjCommon.breakAt(level, p, center, false);
        }
        // Above the cut, column by column while it stands: it falls (no drops), pushed a little away.
        int fallen = 0;
        for (BlockPos p : cutRow) {
            for (int y = 1; y <= 10 && fallen < 60; y++) {
                BlockPos q = p.above(y);
                BlockState s = level.getBlockState(q);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, q) < 0) {
                    break;
                }
                FallingBlockEntity block = FallingBlockEntity.fall(level, q, s);
                block.dropItem = false;
                block.setDeltaMovement(away.scale(0.08 + 0.03 * y).add(0, 0.05, 0));
                block.hurtMarked = true;
                fallen++;
            }
        }
    }

    private static void end(ServerLevel level, Cut cut) {
        for (Mark m : cut.marks) {
            Vec3 c = m.entity != null ? m.entity.position() : m.center;
            SubNet.send(level, SubNet.CUT_LOOP, m.id, c, new Vec3(m.radius, m.entity != null ? 1 : 0, m.entity != null ? m.entity.getId() : 0), 1, 0, 1);
        }
    }

    // ---------- B: Golpe na Alma ----------

    private static boolean alma(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 4.5);
        if (t == null || t.distanceTo(caster) > 4.6) {
            SubCommon.warn(caster, "itadori.miss");
            return false;
        }
        SubCommon.pose(caster, "jj.uppercut");
        SubCommon.busy(caster, 10);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.later(4, () -> {
            if (guard.broken() || !t.isAlive() || t.distanceTo(caster) > 5.5) {
                return;
            }
            Vec3 c = JjCommon.contact(caster, t);
            boolean casting = usingPower(t);
            Combat.hurt(caster, t, 6);
            DbCommon.screen(level, c, 25);
            if (!casting) {
                Stun.apply(t, 10);
                DbCommon.hit(level, c, 0.8f, Db.WHITE);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.1f, 0.9f);
                SubCommon.warn(caster, "itadori.alma_vazio");
                JjCommon.shorten(caster, B, 160);
                return;
            }
            int ticks = JjCommon.control(caster, t, 60);
            PowerLogic.seal(t, ticks);
            Stun.apply(t, JjCommon.isBoss(t) ? 50 : 25);
            SubNet.send(level, SubNet.SOUL, t.getId(), t.position(), SubCommon.flat(t.position().subtract(caster.position())), ticks, 0, 0);
            DbCommon.hit(level, c, 1.2f, Db.WHITE);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
            SubCommon.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 2f, 0.8f);
            SubCommon.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 1.2f);
            if (caster instanceof Player player) {
                SubCommon.warn(player, "itadori.alma", Math.round(ticks / 20f));
            }
        });
        return true;
    }

    // Using or charging a power right now: a move running, one cast a moment ago, or a held charge.
    private static boolean usingPower(LivingEntity t) {
        PowerData data = t.getData(ModAttachments.POWER);
        long now = t.level().getGameTime();
        return data.busyTicks > 0 || (data.lastCastTime >= 0 && now - data.lastCastTime <= 20) || Charge.charging(t)
                || SubCommon.hasState(t, JjCommon.MANJI);
    }

    public static void clear() {
        RINGS.clear();
        AURA.clear();
        RITMO.clear();
        CUTS.clear();
    }
}
