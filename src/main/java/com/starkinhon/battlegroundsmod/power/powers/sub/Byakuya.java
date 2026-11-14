package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Byakuya (SubAlma of Bleach), the 2026-11-11 remake in the Realista+Anime style. The approved page
// (forja/bleach/byakuya-remake.html, byakuya.js) is the program: the client plays its timelines (client/fx/BleachFx, Bk)
// and this side does the same thing at the same moments. Ticks below = the page's seconds x 20.
// Z Senbonzakura: held, a river of a thousand petal-blades follows the aim and cuts whoever is in it (not right by him);
//   let go, it comes back.
// X Gokei: a sure hit (the aimed one, or the closest in front): the petals close a sphere round it while held (it can't
//   get out) and crush it from every side when let go; the more closed, the more damage.
// C Senka: shunpo behind the target (an afterimage stays) and two cuts on its back: a strong stun. A sure hit too.
// B Kido: tap = Hado 4 Byakurai (an instant thin bolt from his finger); hold = Bakudo 81 Danku (a wall of light in front of
//   him that stops what comes through it for 4.4 s).
// V Bankai Senbonzakura Kageyoshi: two rings of giant blades rise round him and turn into a storm of petals for 20 s
//   (Z and X stronger, the storm cuts whoever comes close). V again in the Bankai: Senkei, a thousand swords fence the
//   target in and fly into it. Shift+V in the Bankai: Shukei Hakuteiken, wings of light and one cut with all of it.
public final class Byakuya {
    public static final String BANKAI = "byakuya_bankai";
    private static final String SENKEI = "byakuya_senkei";
    private static final String REST = "byakuya_bankai_rest";
    private static final int BANKAI_TICKS = 400;
    private static final int REST_TICKS = 400;

    private Byakuya() {
    }

    public static Power create() {
        return new Power("byakuya", 0xF4A6C6, 1.0f, List.of(
                new Ability(90, 0, Byakuya::senbonzakura),
                new Ability(200, 0, Byakuya::gokei),
                new Ability(160, 0, Byakuya::senka),
                new Ability(40, 0, Byakuya::bankai),
                new Ability(120, 0, Byakuya::kido)),
                Arrays.asList(null, null, null, new Ability(40, 0, Byakuya::hakuteiken), null));
    }

    static boolean inBankai(LivingEntity caster) {
        return SubCommon.hasState(caster, BANKAI);
    }

    static Vec3 hand(LivingEntity caster) {
        Vec3 look = caster.getLookAngle();
        Vec3 right = SubCommon.flat(look).cross(new Vec3(0, 1, 0));
        return caster.position().add(0, caster.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.45));
    }

    // A sure hit: the one aimed at, or else the closest in front of him.
    static LivingEntity target(LivingEntity caster, double range) {
        LivingEntity t = SubCommon.aimed(caster, range);
        if (t != null) {
            return t;
        }
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : Combat.inRadius((ServerLevel) caster.level(), caster.getEyePosition(), range, caster)) {
            Vec3 to = Combat.center(e).subtract(caster.getEyePosition());
            double d = to.length();
            if (d < 0.5 || to.normalize().dot(look) < 0.55 || !Combat.lockable(e) || !caster.hasLineOfSight(e)) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // ---------- Z: Senbonzakura ----------

    private static boolean senbonzakura(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean big = inBankai(caster);
        float damage = big ? 3f : 2f;
        int most = big ? 80 : 60;
        int bossHold = 20 + SubCommon.RANDOM.nextInt(30);
        SubCommon.pose(caster, "sub.apontar");
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
        SubNet.send(level, SubNet.BL_SENBON, caster.getId(), hand(caster), Vec3.ZERO, 0, 0, 0);
        int[] back = {-1};
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BL_SENBON, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubCommon.busy(caster, 2);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (back[0] < 0 && (age >= most || (age >= 8 && !held))) {
                back[0] = age;
                SubNet.send(level, SubNet.BL_SENBON, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
            }
            if (back[0] < 0 && held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, age / (double) most);
            }
            // the page: the river grows 45 blocks/s up to 22 along the aim; whoever is more than 2 blocks out and within 1.6
            // of it takes a cut every 0.08 s (here every 2 ticks)
            float len = Math.min(22, age / 20f * 45);
            float backK = back[0] < 0 ? 0 : SubCommon.smooth((age - back[0]) / 9f);
            Vec3 h = hand(caster);
            Vec3 aim = caster.getLookAngle();
            if (age % 2 == 0) {
                for (LivingEntity t : Combat.alongLine(level, h, h.add(aim.scale(len * (1 - backK))), 1.8, caster)) {
                    Vec3 c = Combat.center(t);
                    double along = c.subtract(h).dot(aim);
                    double side = c.subtract(h).subtract(aim.scale(along)).length();
                    if (along < 2 || side > 1.6 + t.getBbWidth() * 0.5) {
                        continue;
                    }
                    Integer last = lastHit.get(t.getId());
                    if (last != null && age - last < 2) {
                        continue;
                    }
                    lastHit.put(t.getId(), age);
                    t.invulnerableTime = 0;
                    Combat.hurt(caster, t, damage * 0.5f);
                    t.setDeltaMovement(t.getDeltaMovement().add(aim.scale(0.03)));
                    SubNet.send(level, SubNet.BL_CUT, 0, c, Vec3.ZERO, 0, 0, 0);
                }
            }
            if (age % 3 == 0) {
                SubCommon.sound(level, h.add(aim.scale(len * 0.6)), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.4f);
            }
            return back[0] >= 0 && age - back[0] >= 10;
        });
        return true;
    }

    // ---------- X: Gokei ----------

    private static boolean gokei(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 26);
        if (target == null) {
            SubCommon.warn(caster, "byakuya.gokei_no_target", 26);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean big = inBankai(caster);
        int id = SubNet.newId();
        int bossHold = 20 + SubCommon.RANDOM.nextInt(40);
        Vec3 center = target.position().add(0, 1, 0);
        Vec3 spot = target.position();
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.8f);
        SubNet.send(level, SubNet.BL_GOKEI, id, center, Vec3.ZERO, 0, 0, 0);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BL_GOKEI, id, center, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubCommon.busy(caster, 2);
            // caught inside: it can't get out while the sphere closes (the page: 3 s to close)
            Freeze.hold(target, 2, a -> spot);
            float closed = SubCommon.smooth(age / 60f);
            boolean held = SubCommon.held(caster, age, bossHold);
            if (held) {
                com.starkinhon.battlegroundsmod.combat.Charge.report(caster, closed);
            }
            if (age % 6 == 0) {
                SubCommon.sound(level, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + closed);
            }
            if ((held || age < 6) && age < 80) {
                return false;
            }
            SubNet.send(level, SubNet.BL_GOKEI, id, center, Vec3.ZERO, 0, 0, 1);
            // the crush lands 0.16 s after letting go: 40% to 100% of the damage as it closed
            float damage = (8 + 14 * closed) * (big ? 1.35f : 1);
            Tasks.later(3, () -> {
                SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                SubCommon.sound(level, center, SoundEvents.GLASS_BREAK, 1.2f, 1.5f);
                if (target.isAlive() && caster.isAlive()) {
                    target.invulnerableTime = 0;
                    Combat.hurt(caster, target, damage);
                    Stun.apply(target, 26);
                }
            });
            return true;
        });
        return true;
    }

    // ---------- C: Senka ----------

    private static boolean senka(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 18);
        if (target == null) {
            SubCommon.warn(caster, "byakuya.senka_no_target", 18);
            return false;
        }
        Vec3 from = caster.position();
        Vec3 way = SubCommon.flat(target.position().subtract(from));
        // the page: he shows up 1.4 behind it (seen from where he was), facing it
        Vec3 behind = target.position().add(way.scale(1.4)).add(way.cross(new Vec3(0, 1, 0)).scale(0.2));
        if (SubCommon.solid(level, behind.add(0, 0.5, 0)) || SubCommon.solid(level, behind.add(0, 1.5, 0))) {
            behind = target.position().subtract(way.scale(1.3));
        }
        SubNet.send(level, SubNet.BL_SENKA, caster.getId(), from, Vec3.ZERO, target.getId(), 0, 0);
        Vec3 spot = behind;
        Vec3 face = target.position().subtract(spot);
        float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
        SubCommon.busy(caster, 14);
        Stun.apply(target, 14);
        Tasks.later(1, () -> {
            if (!caster.isAlive()) {
                return;
            }
            if (caster instanceof ServerPlayer player) {
                player.teleportTo(level, spot.x, spot.y, spot.z, yaw, player.getXRot());
            } else {
                caster.teleportTo(spot.x, spot.y, spot.z);
                caster.setYRot(yaw);
            }
            SubCommon.sound(level, spot, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
            SubCommon.pose(caster, "sub.corte");
        });
        int[] at = {2, 4};
        for (int hit = 0; hit < 2; hit++) {
            int k = hit;
            Tasks.later(at[hit], () -> {
                if (!target.isAlive() || !caster.isAlive()) {
                    return;
                }
                target.invulnerableTime = 0;
                Combat.hurt(caster, target, 6f);
                SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                if (k == 1) {
                    // a strong stun: 1.5 s (bosses still keep at least 25 ticks after the halving)
                    Stun.apply(target, 52);
                }
            });
        }
        return true;
    }

    // ---------- B: Kido (tap Byakurai, hold Danku) ----------

    private static boolean kido(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossHold = SubCommon.RANDOM.nextBoolean() ? 0 : 20;
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            boolean held = SubCommon.held(caster, age, bossHold);
            if (!held && age < 5) {
                byakurai(caster, level);
                return true;
            }
            if (age >= 5) {
                danku(caster, level);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void byakurai(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 30);
        Vec3 at = target != null ? Combat.center(target) : Combat.raycastPoint(caster, 30);
        SubCommon.pose(caster, "sub.apontar");
        SubCommon.busy(caster, 8);
        SubNet.send(level, SubNet.BL_BYAKURAI, caster.getId(), at, Vec3.ZERO, 0, 0, 0);
        // the bolt leaves his finger at 0.15 s and is there in 0.04 s
        Tasks.later(4, () -> {
            SubCommon.sound(level, caster.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.9f);
            if (target != null && target.isAlive() && caster.isAlive()) {
                Combat.hurtMagic(caster, target, 9);
                Vec3 d = at.subtract(caster.getEyePosition()).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(d.scale(0.6)).add(0, 0.25, 0));
                target.hurtMarked = true;
            }
        });
    }

    private static void danku(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.defender");
        SubCommon.busy(caster, 6);
        Vec3 feet = caster.position();
        Vec3 face = SubCommon.flat(caster.getLookAngle());
        Vec3 center = feet.add(face.scale(3.2)).add(0, 1.6, 0);
        SubNet.send(level, SubNet.BL_DANKU, caster.getId(), feet, face, 0, 0, 0);
        SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.8f);
        SubDefense.shield(caster, level, center, face, 2.8, 88, -1, (s, attacker) -> {
            Vec3 hitAt = attacker == null ? center : center.add(SubCommon.flat(attacker.position().subtract(center)).scale(0.2))
                    .add(0, Mth.clamp(attacker.getEyeY() - 0.4 - center.y, -1.4, 1.4), 0);
            SubNet.send(level, SubNet.BL_DANKU, caster.getId(), hitAt, face, 0, 0, 1);
        });
    }

    // ---------- V: Bankai (V again: Senkei) ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        if (inBankai(caster)) {
            return senkei(caster, level);
        }
        if (SubCommon.hasState(caster, REST)) {
            SubCommon.warn(caster, "byakuya.bankai_rest", (SubCommon.remaining(caster, REST) + 19) / 20);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 feet = caster.position();
        SubCommon.setState(caster, BANKAI, BANKAI_TICKS);
        SubCommon.pose(caster, "sub.soltar");
        SubCommon.busy(caster, 30);
        // not crushed while the blades rise (the page: 0.45 s to 1.5 s)
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 32, 2, false, false));
        SubCommon.sound(level, feet, SoundEvents.BEACON_ACTIVATE, 2f, 0.6f);
        SubNet.send(level, SubNet.BL_KAGEYOSHI, caster.getId(), feet, Vec3.ZERO, 0, 0, 0);
        Map<Integer, Integer> lastHit = new HashMap<>();
        Tasks.add(age -> {
            if (guard.broken() || !inBankai(caster)) {
                SubCommon.clearState(caster, BANKAI);
                SubCommon.setState(caster, REST, REST_TICKS);
                SubNet.send(level, SubNet.BL_KAGEYOSHI, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
                return true;
            }
            if (age == 4) {
                SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 0.5f);
            }
            if (age == 9) {
                SubCommon.sound(level, feet, SoundEvents.ANVIL_LAND, 1f, 0.5f);
            }
            if (age == 42) {
                SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
            }
            // the storm (from 2.1 s, 4.2 blocks round him) cuts whoever comes into it
            if (age > 42 && age % 10 == 0) {
                for (LivingEntity t : Combat.inRadius(level, caster.position(), 5, caster)) {
                    if (t.distanceTo(caster) < 1.8) {
                        continue;
                    }
                    Integer last = lastHit.get(t.getId());
                    if (last != null && age - last < 10) {
                        continue;
                    }
                    lastHit.put(t.getId(), age);
                    t.invulnerableTime = 0;
                    Combat.hurt(caster, t, 1.5f);
                    SubNet.send(level, SubNet.BL_CUT, 0, Combat.center(t), Vec3.ZERO, 0, 0, 0);
                }
            }
            return false;
        });
        return true;
    }

    // Senkei: three rings of swords fence the target in (0.9 s), hold (to 1.3 s) and fly into it (1.85 s).
    private static boolean senkei(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, SENKEI)) {
            SubCommon.warn(caster, "byakuya.senkei_busy");
            return false;
        }
        if (BANKAI_TICKS - SubCommon.remaining(caster, BANKAI) < 48) {
            SubCommon.warn(caster, "byakuya.bankai_rising");
            return false;
        }
        LivingEntity target = target(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "byakuya.gokei_no_target", 24);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.setState(caster, SENKEI, 60);
        Vec3 spot = target.position();
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, 12);
        SubNet.send(level, SubNet.BL_SENKEI, caster.getId(), spot, Vec3.ZERO, target.getId(), 0, 0);
        SubCommon.sound(level, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.5f);
        // fenced in: it can't leave until the swords land
        Freeze.hold(target, 37, a -> spot);
        Tasks.later(26, () -> SubCommon.sound(level, spot, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.5f));
        Tasks.later(37, () -> {
            SubCommon.sound(level, spot, SoundEvents.GLASS_BREAK, 2f, 0.5f);
            SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.4f);
            if (!guard.broken() && target.isAlive()) {
                target.invulnerableTime = 0;
                Combat.hurt(caster, target, 30);
                Stun.apply(target, 30);
            }
        });
        return true;
    }

    // ---------- Shift+V: Shukei Hakuteiken (in the Bankai) ----------

    private static boolean hakuteiken(LivingEntity caster, ServerLevel level) {
        if (!inBankai(caster)) {
            SubCommon.warn(caster, "byakuya.hakuteiken_needs_bankai");
            return false;
        }
        if (BANKAI_TICKS - SubCommon.remaining(caster, BANKAI) < 48) {
            SubCommon.warn(caster, "byakuya.bankai_rising");
            return false;
        }
        LivingEntity target = target(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "byakuya.gokei_no_target", 24);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubNet.send(level, SubNet.BL_HAKUTEI, caster.getId(), caster.position(), Vec3.ZERO, target.getId(), 0, 0);
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, 30);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 3, false, false));
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 2f, 0.8f);
        Vec3 start = caster.position();
        Vec3 spot = target.position();
        Freeze.hold(target, 24, a -> spot);
        Vec3 way = SubCommon.flat(spot.subtract(start));
        Vec3 end = spot.subtract(way.scale(1.6)).add(0, 0.9, 0);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            // the page: he rises 1.6 (0.4 s to 0.9 s), flies to the target (to 1.15 s), cuts, comes down
            Vec3 p;
            if (age < 18) {
                p = start.add(0, 1.6 * SubCommon.smooth((age - 8) / 10f), 0);
            } else if (age < 23) {
                float k = SubCommon.smooth((age - 18) / 5f);
                p = start.add(0, 1.6, 0).lerp(end, k);
            } else {
                p = end.add(0, -Math.min(0.9, (age - 23) * 0.2), 0);
            }
            caster.setDeltaMovement(Vec3.ZERO);
            caster.fallDistance = 0;
            if (caster instanceof ServerPlayer player) {
                player.connection.teleport(p.x, p.y, p.z, player.getYRot(), player.getXRot());
            } else {
                caster.teleportTo(p.x, p.y, p.z);
            }
            if (age == 18) {
                SubCommon.sound(level, p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4f, 1.2f);
            }
            if (age == 23) {
                SubCommon.sound(level, spot, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.4f);
                SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 1.2f);
                if (target.isAlive()) {
                    target.invulnerableTime = 0;
                    Combat.hurt(caster, target, 42);
                    Stun.apply(target, 30);
                }
            }
            if (age >= 28) {
                // all of it in one cut: the Bankai is spent
                Tasks.later(30, () -> SubCommon.clearState(caster, BANKAI));
                return true;
            }
            return false;
        });
        return true;
    }
}
