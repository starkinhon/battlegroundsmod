package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Agarrao;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Sasuke (SubAlma of Naruto, tier 6), the Visualizador's Sasuke tab.
// Z Chidori: held, the hand becomes a bundle of chirping lightning scratching the ground; the longer, the farther he goes
//   (4 to 9 blocks). Let go: a straight dash that goes through the target. A target paralyzed by the Senbon can't dodge it:
//   he homes onto them and it is a critical hit.
// X Sharingan: a reading stance of 1 s. Hit in that window (punch or shot), he reads it: no damage, an afterimage stays
//   and he appears behind the attacker with a spinning kick. Nobody attacked: "nothing to read" and a short cooldown.
// C Chidori Senbon: a fan of 9 lightning needles (60°); who takes 2 or more is paralyzed 2 s with sparks on the body.
// V Kirin: storm clouds gather over the target; he raises his hand and a dragon of lightning comes down on it: it can't
//   be dodged (it follows the target). Open sky only.
public final class Sasuke {
    private Sasuke() {
    }

    public static Power create() {
        return new Power("sasuke", 0x5A3A8A, 1.05f, List.of(
                new Ability(150, 0, Sasuke::chidori),
                new Ability(240, 0, Sasuke::read),
                new Ability(200, 0, Sasuke::senbon),
                new Ability(1100, 0, Sasuke::kirin)));
    }

    static Vec3 lowHand(LivingEntity caster) {
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.4, 0).add(right.scale(0.45)).add(look.scale(0.1));
    }

    // ---------- Z: Chidori ----------

    private static boolean chidori(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int full = 50;
        int bossHold = 30 + SubCommon.RANDOM.nextInt(21);
        SubCommon.pose(caster, "nr.chidori");
        SubCommon.busy(caster, full + 12);
        int[] release = {-1};
        Vec3[] start = {null};
        Vec3[] dir = {null};
        double[] dist = {0};
        Map<Integer, Boolean> hit = new HashMap<>();
        Vec3[] last = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.CHIDORI, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (release[0] < 0) {
                float k = SubCommon.smooth(age / 20f);
                Vec3 hand = lowHand(caster);
                Vec3 scratch = SubCommon.ground(level, hand.add(SubCommon.flat(caster.getLookAngle()).scale(0.6)));
                SubNet.send(level, SubNet.CHIDORI, id, hand, scratch.subtract(hand), (0.28f + 0.1f * SubCommon.smooth(age / 40f)) * Math.max(0.3f, k), 0, 0);
                Charge.report(caster, Math.min(1, (age + 1) / (double) full));
                if (age % 3 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.9f, 2f);
                    SubCommon.sound(level, hand, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.3f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.4f);
                }
                if (age >= 8 && (age >= full || !SubCommon.held(caster, age, bossHold))) {
                    release[0] = age;
                    dist[0] = 4 + 5 * Math.min(1, age / (double) full);
                    start[0] = caster.position();
                    Vec3 look = SubCommon.flat(caster.getLookAngle());
                    // A target paralyzed by the Senbon can't dodge: he goes straight for them.
                    for (LivingEntity t : Combat.inCone(caster, dist[0] + 2, 0.6)) {
                        if (SubCommon.hasState(t, NrCommon.PARALYZED)) {
                            look = SubCommon.flat(t.position().subtract(caster.position()));
                            dist[0] = Math.max(dist[0], t.distanceTo(caster) + 1.5);
                            break;
                        }
                    }
                    dir[0] = look;
                    last[0] = Combat.center(caster);
                    SubCommon.pose(caster, "nr.investida");
                    SubCommon.sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
                }
                return false;
            }
            int a = age - release[0];
            int dashTicks = 8;
            if (a <= dashTicks) {
                double speed = dist[0] / dashTicks;
                caster.setDeltaMovement(dir[0].x * speed, Math.min(caster.getDeltaMovement().y, 0.02), dir[0].z * speed);
                caster.hurtMarked = true;
                caster.fallDistance = 0;
                Vec3 now = Combat.center(caster);
                SubNet.send(level, SubNet.CHIDORI, id, lowHand(caster).add(dir[0].scale(0.4)), Vec3.ZERO, 0.38f, 0, 0);
                for (LivingEntity t : Combat.alongLine(level, last[0], now.add(dir[0].scale(0.8)), 0.9, caster)) {
                    if (hit.put(t.getId(), true) != null) {
                        continue;
                    }
                    boolean crit = SubCommon.hasState(t, NrCommon.PARALYZED);
                    if (crit) {
                        Freeze.free(t);
                        SubCommon.clearState(t, NrCommon.PARALYZED);
                    }
                    Combat.hurtMagic(caster, t, crit ? 22 : 13);
                    Stun.apply(t, 25);
                    Combat.push(t, dir[0], 0.6, 0.2);
                    Vec3 c = Combat.center(t);
                    SubNet.send(level, SubNet.PIERCE, 0, c, dir[0], crit ? 1 : 0, 0, 0);
                    DbCommon.screen(level, c, 0);
                    SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 1.4f);
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                }
                last[0] = now;
                return false;
            }
            caster.setDeltaMovement(caster.getDeltaMovement().scale(0.2));
            caster.hurtMarked = true;
            SubNet.send(level, SubNet.CHIDORI, id, lowHand(caster), Vec3.ZERO, 0, 0, 1);
            Vec3 from = start[0].add(0, 0.9, 0);
            SubNet.send(level, SubNet.BOLT_TRAIL, 0, from, caster.position().add(0, 0.9, 0).subtract(from), 0.87f, 0, 0);
            return true;
        });
        return true;
    }

    // ---------- X: Sharingan reading ----------

    private static boolean read(LivingEntity caster, ServerLevel level) {
        int window = 20;
        SubCommon.setState(caster, NrCommon.READ, window);
        SubCommon.pose(caster, "nr.leitura");
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, window + 30, 1, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
        DbCommon.cooldown(caster, 1, 40);
        Tasks.later(window, () -> {
            if (countered.remove(caster.getId()) == null) {
                SubCommon.warn(caster, "sasuke.nothing_to_read");
                SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 0, 0, 0);
            }
        });
        return false;
    }

    private static final Map<Integer, Boolean> countered = new HashMap<>();

    // The hit was read: an afterimage stays, he appears behind the attacker and kicks them away.
    static void counter(LivingEntity sasuke, ServerLevel level, LivingEntity enemy) {
        countered.put(sasuke.getId(), true);
        Vec3 dir = SubCommon.flat(enemy.position().subtract(sasuke.position()));
        Vec3 behind = enemy.position().add(dir.scale(1.6));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = enemy.position().add(dir.scale(0.8));
        }
        Vec3 left = sasuke.position();
        SubNet.send(level, SubNet.AFTERIMAGE, sasuke.getId(), left, behind.subtract(left), sasuke.getBbHeight(), 0, 0);
        SubCommon.sound(level, left, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        NrCommon.teleport(sasuke, behind, NrCommon.yawTo(behind, enemy.position()));
        DbCommon.cooldown(sasuke, 1, 240);
        Tasks.later(4, () -> SubCommon.pose(sasuke, "nr.chute_giro"));
        Tasks.later(8, () -> {
            if (!sasuke.isAlive() || !enemy.isAlive()) {
                return;
            }
            Combat.hurt(sasuke, enemy, 9);
            Stun.apply(enemy, 25);
            Combat.push(enemy, dir.scale(-1), 1.6, 0.45);
            Vec3 c = Combat.center(enemy);
            DbCommon.hit(level, c, 0.8f, Db.RED);
            DbCommon.screen(level, c, 3);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.8f);
        });
    }

    // ---------- C: Chidori Senbon ----------

    private static boolean senbon(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int out = 20;
        SubCommon.pose(caster, "nr.senbon");
        SubCommon.busy(caster, out + 4);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.CHIDORI, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            Vec3 hand = DbCommon.hand(caster);
            if (age < out) {
                SubNet.send(level, SubNet.CHIDORI, id, hand, Vec3.ZERO, 0.18f * SubCommon.smooth((age - 6) / 8f), 0, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.BEEHIVE_WORK, 0.6f, 2f);
                }
                return false;
            }
            SubNet.send(level, SubNet.CHIDORI, id, hand, Vec3.ZERO, 0, 0, 1);
            SubCommon.sound(level, hand, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 2f);
            SubCommon.sound(level, hand, SoundEvents.ARROW_SHOOT, 1f, 1.8f);
            Vec3 look = caster.getLookAngle();
            Map<LivingEntity, Integer> count = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                double ang = (i - 4) / 4.0 * 0.52;
                Vec3 d = look.yRot((float) -ang).add(0, (Db.hash(i, 4, 1) - 0.5) * 0.08, 0).normalize();
                Vec3 far = hand.add(d.scale(8.5));
                var clip = level.clip(new ClipContext(hand, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                Vec3 end = clip.getType() == HitResult.Type.MISS ? far : clip.getLocation();
                SubNet.send(level, SubNet.SENBON, 0, hand, end.subtract(hand), 0, 0, 0);
                for (LivingEntity t : Combat.alongLine(level, hand, end, 0.35, caster)) {
                    count.merge(t, 1, Integer::sum);
                }
            }
            Tasks.later(4, () -> {
                for (var e : count.entrySet()) {
                    LivingEntity t = e.getKey();
                    if (!t.isAlive()) {
                        continue;
                    }
                    Combat.hurtMagic(caster, t, 2 * e.getValue());
                    DbCommon.hit(level, Combat.center(t), 0.6f, Db.BLUE);
                    if (e.getValue() >= 2) {
                        boolean boss = t instanceof SoulBoss b && b.isBoss();
                        NrCommon.paralyze(level, t, boss ? 20 : 40);
                    } else {
                        Stun.apply(t, 8);
                    }
                }
                if (count.isEmpty()) {
                    SubCommon.warn(caster, "sasuke.senbon_miss");
                }
            });
            return true;
        });
        return true;
    }

    // ---------- V: Kirin ----------

    private static boolean kirin(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 40);
        Vec3 point = target != null ? target.position() : SubCommon.ground(level, SubCommon.aimPoint(caster, 40));
        if (!level.canSeeSky(BlockPos.containing(point).above())) {
            SubCommon.warn(caster, "sasuke.kirin_sky");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int raise = 66;
        int strike = 116;
        Vec3[] at = {point};
        SubNet.send(level, SubNet.STORM, 0, point.add(0, 22, 0), Vec3.ZERO, strike, 12, 0);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, strike + 6);
        Freeze.holdLookFree(caster, strike + 4);
        SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, strike + 30, 1, 0);
        SubCommon.sound(level, point, SoundEvents.LIGHTNING_BOLT_THUNDER, 2f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            if (target != null && target.isAlive()) {
                at[0] = target.position();
            }
            if (age == raise) {
                SubCommon.pose(caster, "nr.mao_alto");
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
            }
            if (age > 30 && age < strike && SubCommon.RANDOM.nextFloat() < 0.08f) {
                SubCommon.sound(level, point.add(0, 20, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.8f + SubCommon.RANDOM.nextFloat() * 0.4f);
            }
            if (age < strike - 6) {
                return false;
            }
            if (age == strike - 6) {
                // The dragon comes down where the target is now: it can't be dodged, only blocked.
                SubNet.send(level, SubNet.KIRIN, 0, point.add(0, 22, 0), at[0].add(0, 0.9, 0).subtract(point.add(0, 22, 0)), 0, 0, 0);
                return false;
            }
            if (age < strike) {
                return false;
            }
            Vec3 c = at[0];
            if (target != null && target.isAlive()) {
                Combat.hurtMagic(caster, target, 32);
                Stun.apply(target, 40);
            }
            for (LivingEntity t : Combat.inRadius(level, c.add(0, 0.9, 0), 3.2, caster)) {
                if (t != target) {
                    Combat.hurtMagic(caster, t, 12);
                    Stun.apply(t, 20);
                }
            }
            Agarrao.crater(level, c, 2.2, 1.4);
            SubNet.send(level, SubNet.DUST, 0, c, Vec3.ZERO, 1.6f, 0, 0);
            DbCommon.screen(level, c, 5);
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.7f);
            SubCommon.sound(level, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.5f, 0.8f);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 1.2f);
            return true;
        });
        return true;
    }
}
