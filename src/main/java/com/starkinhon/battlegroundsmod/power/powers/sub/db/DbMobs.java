package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball world's common enemies' own moves (Stark 2026-11-10: "todos os mobs batem igual", each one needs a
// power of its own on top of the punches). Two moves each, 16 in all, each with its own shape:
//   Saibaman      Z Agarrão Explosivo (leaps on the back, glows green, blows up with the target)  X Ácido (a spit that
//                 leaves a burning puddle)
//   Bandido       Z Punho do Lobo (the wolf fang rush: six blows from alternating sides)  X Areia nos Olhos (blinds)
//   Pirata        Z Corte de Sabre (lunge and a wide cut)  X Bomba de Pólvora (lobbed, sits with a burning fuse, blows up)
//   Red Ribbon    Z Metralhadora (a burst of 14 bullets)  X Bazuca (a rocket that blows a hole)
//   Yeti          Z Rugido Congelante (a cone that freezes)  X Pisão Avalanche (leaps and lands in a ring of ice spikes)
//   Soldado Freeza Z Rajada Tripla (three shots from the arm)  X Bomba de Ki (a purple ball charged overhead and thrown)
//   Raptor        Z Bote (pounces, pins and bites three times)  X Garras (two crossed claws that leave it bleeding)
//   Tiranossauro  Z Investida (charges through everything in a line)  X Pisão Sísmico (stomp: cracks and a wave on the ground)
// The humanoid ones are hidden powers (souls_bosses/<mob>.json picks them with "poder", the AI uses slots 0 and 1);
// the dinosaurs call theirs from animal/Dinosaur. Damages are on the Tier 0 scale like every move (a Tier 10 hits for
// 5% of them, a Tier 8 for 9%).
public final class DbMobs {
    private DbMobs() {
    }

    private static Ability none() {
        return new Ability(20, 0, (caster, level) -> false);
    }

    private static Power power(String id, int color, Ability.Action z, int zCooldown, Ability.Action x, int xCooldown) {
        return new Power(id, color, 1.0f, List.of(new Ability(zCooldown, 0, z), new Ability(xCooldown, 0, x), none(), none()));
    }

    public static Power saibaman() {
        return power("db_saibaman", 0x5BD14A, DbMobs::explosiveGrab, 400, DbMobs::acid, 220);
    }

    public static Power bandit() {
        return power("db_bandido", 0xE0A060, DbMobs::wolfFang, 260, DbMobs::sand, 300);
    }

    public static Power pirate() {
        return power("db_pirata", 0x3A6FD8, DbMobs::saber, 200, DbMobs::powderBomb, 280);
    }

    public static Power redRibbon() {
        return power("db_red_ribbon", 0xD83030, DbMobs::machineGun, 220, DbMobs::bazooka, 340);
    }

    public static Power yeti() {
        return power("db_yeti", 0xBFE8FF, DbMobs::freezingRoar, 320, DbMobs::avalancheStomp, 260);
    }

    public static Power freezaSoldier() {
        return power("db_soldado_freeza", 0xE060C0, DbMobs::tripleShot, 200, DbMobs::kiBomb, 320);
    }

    // ---------- shared ----------

    // The one it fights: a mob's target, else whoever it aims at.
    static LivingEntity target(LivingEntity caster, double range) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget();
        }
        return SubCommon.aimed(caster, range);
    }

    // A move keeps going while its caster lives (the dinosaurs have no power to check).
    private static boolean gone(LivingEntity caster) {
        return !caster.isAlive() || caster.isRemoved() || Stun.real(caster);
    }

    private static Vec3 mouth(LivingEntity caster) {
        return caster.getEyePosition().add(caster.getLookAngle().scale(0.4)).add(0, -0.15, 0);
    }

    private static Vec3 toward(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private static void face(LivingEntity caster, Vec3 at) {
        Vec3 d = at.subtract(caster.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        caster.setYRot(yaw);
        caster.setYHeadRot(yaw);
        caster.yBodyRot = yaw;
        caster.setXRot(pitch);
    }

    // Where a straight shot from `from` toward `dir` stops on a block, up to range.
    private static Vec3 clip(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    private static boolean blocked(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // A small hole (soft blocks only), only with the destruction gamerule.
    private static void dent(ServerLevel level, Vec3 at, double radius) {
        if (BlockRules.canBreak(level)) {
            DbCommon.bigCrater(level, at, radius, radius * 0.6, 3);
        }
    }

    // ================= Saibaman =================

    // Z Agarrão Explosivo: leaps at the target; on the back it clings for 1.5 s glowing faster and faster, then blows up
    // with the target (the Saibaman dies in it, like on Yamcha). Hitting it hard enough while it clings blows it early.
    private static boolean explosiveGrab(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 12);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.grito");
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_ATTACK, 1.2f, 1.6f);
        int aura = SubNet.newId();
        float hp0 = caster.getHealth();
        int[] clungAt = {-1};
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GREEN, 0);
                return true;
            }
            if (age == 6) {
                // the leap: an arc to the target
                Vec3 d = target.position().subtract(caster.position());
                double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                caster.setDeltaMovement(d.x / Math.max(1, flat) * Math.min(1.6, 0.35 + flat * 0.11), 0.55 + Math.min(0.3, d.y * 0.08),
                        d.z / Math.max(1, flat) * Math.min(1.6, 0.35 + flat * 0.11));
                caster.hurtMarked = true;
                SubCommon.sound(level, caster.position(), SoundEvents.SLIME_JUMP, 1.2f, 1.4f);
            }
            if (clungAt[0] < 0) {
                if (age > 6 && caster.distanceTo(target) < 1.9) {
                    clungAt[0] = age;
                    SubCommon.sound(level, target.position(), SoundEvents.SLIME_SQUISH, 1.4f, 0.8f);
                } else if (age > 30) {
                    return true;   // missed
                }
                return false;
            }
            int held = age - clungAt[0];
            // On the back, hanging on: the target slows down, the green glow beats faster and faster.
            Vec3 back = Combat.flatLook(target).scale(-0.45);
            caster.teleportTo(target.getX() + back.x, target.getY() + 0.55, target.getZ() + back.z);
            caster.setDeltaMovement(Vec3.ZERO);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false));
            float beat = 0.5f + 0.5f * Mth.sin(held * (0.4f + held * 0.03f));
            SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6 + beat, 0, 0), 8, Db.GREEN, 0.8f + held * 0.02f);
            if (held % 6 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.CREEPER_PRIMED, 1f, 1.2f + held * 0.02f);
            }
            boolean early = caster.getHealth() < hp0 * 0.5f;
            if (held < 30 && !early) {
                return false;
            }
            Vec3 c = Combat.center(target);
            SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, Db.GREEN, 0);
            DbCommon.boom(level, c, 3.2f, Db.GREEN, 1000 + SubCommon.RANDOM.nextInt(100));
            DbCommon.screen(level, c, 8);
            DbCommon.hurtRadius(caster, level, c, 3.6, 150, 1.4, 0.5);
            dent(level, target.position(), 2.2);
            caster.kill();
            return true;
        });
        return true;
    }

    // X Ácido: a green spit lobbed at the target; where it lands the ground burns for 3 s (poison and a sting every half
    // second for whoever stands in it).
    private static boolean acid(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 18);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.grito");
        Vec3 from = mouth(caster);
        Vec3 land = target.position().add(target.getDeltaMovement().scale(6));
        int flight = Mth.clamp((int) (from.distanceTo(land) / 0.9), 6, 22);
        SubCommon.sound(level, from, SoundEvents.LLAMA_SPIT, 1.4f, 0.7f);
        SubNet.send(level, SubNet.SHOT, 0, from, land.subtract(from), flight, Db.GREEN, 0);
        Tasks.later(flight, () -> {
            Vec3 g = SubCommon.ground(level, land);
            DbCommon.boom(level, g.add(0, 0.2, 0), 0.9f, Db.GREEN, SubCommon.RANDOM.nextInt(100));
            SubCommon.sound(level, g, SoundEvents.LAVA_EXTINGUISH, 1.2f, 1.3f);
            Tasks.repeat(60, tick -> {
                if (tick % 6 == 0) {
                    // the puddle hissing: small green bursts at random spots of it
                    double a = SubCommon.RANDOM.nextDouble() * Math.PI * 2, r = SubCommon.RANDOM.nextDouble() * 2.0;
                    DbCommon.hit(level, g.add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 0.35f, Db.GREEN);
                }
                if (tick % 10 == 0) {
                    for (LivingEntity t : Combat.inRadius(level, g.add(0, 0.5, 0), 2.3, caster)) {
                        Combat.hurtMagic(caster, t, 25);
                        t.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0, false, true));
                    }
                }
            });
        });
        return true;
    }

    // ================= Bandido do deserto =================

    // Z Punho do Lobo: dashes in and hits six times from alternating sides (each with a streak of speed), the last one
    // throws the target back.
    private static boolean wolfFang(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 9);
        if (target == null) {
            return false;
        }
        SubCommon.busy(caster, 30);
        SubCommon.sound(level, caster.position(), SoundEvents.WOLF_GROWL, 1.2f, 1.1f);
        Set<Integer> done = DbCommon.once();
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                return true;
            }
            if (age < 4) {
                return false;
            }
            int blow = (age - 4) / 4;
            if ((age - 4) % 4 != 0) {
                return false;
            }
            if (blow >= 6) {
                return true;
            }
            // each blow from the other side of the target
            Vec3 side = Combat.flatLook(target).cross(new Vec3(0, 1, 0)).scale(blow % 2 == 0 ? 1.1 : -1.1);
            Vec3 from = caster.position();
            Vec3 to = target.position().add(side).add(Combat.flatLook(target).scale(0.6));
            caster.teleportTo(to.x, target.getY(), to.z);
            face(caster, Combat.center(target));
            SubNet.send(level, SubNet.STREAK, 0, from.add(0, 1, 0), to.subtract(from), 0.35f, Db.ORANGE, 6);
            SubCommon.pose(caster, blow % 2 == 0 ? "soco.combo1" : "soco.combo2");
            boolean last = blow == 5;
            Combat.hurtMagic(caster, target, last ? 60 : 22);
            Stun.apply(target, last ? 14 : 6);
            DbCommon.hit(level, Combat.center(target), last ? 0.8f : 0.45f, Db.ORANGE);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.3f - blow * 0.05f);
            if (last) {
                Combat.pushFrom(target, caster.position(), 1.3, 0.35);
                DbCommon.screen(level, Combat.center(target), 4);
            }
            done.add(blow);
            return false;
        });
        return true;
    }

    // X Areia nos Olhos: a handful of sand thrown in a cone: whoever is in it is blinded and dizzy for 3 s.
    private static boolean sand(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 7);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.gesto_baixo");
        face(caster, Combat.center(target));
        Vec3 dir = toward(caster.getEyePosition(), Combat.center(target));
        Tasks.later(5, () -> {
            if (gone(caster)) {
                return;
            }
            SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.6f, 0.7f);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
            // the sand flying out: a spray of streaks and puffs, each its own way
            for (int i = 0; i < 6; i++) {
                Vec3 d = dir.add(new Vec3(SubCommon.RANDOM.nextGaussian() * 0.18, SubCommon.RANDOM.nextGaussian() * 0.1, SubCommon.RANDOM.nextGaussian() * 0.18))
                        .normalize().scale(3.5 + SubCommon.RANDOM.nextDouble() * 2.5);
                SubNet.send(level, SubNet.STREAK, 0, caster.getEyePosition().add(0, -0.3, 0), d, 0.25f + 0.2f * SubCommon.RANDOM.nextFloat(), Db.ORANGE,
                        5 + SubCommon.RANDOM.nextInt(4));
            }
            DbCommon.boom(level, caster.getEyePosition().add(dir.scale(3)), 1.3f, Db.ORANGE, SubCommon.RANDOM.nextInt(100));
            for (LivingEntity t : Combat.inRadius(level, caster.getEyePosition().add(dir.scale(3)), 3.5, caster)) {
                if (toward(caster.getEyePosition(), Combat.center(t)).dot(dir) < 0.55) {
                    continue;
                }
                Combat.hurtMagic(caster, t, 15);
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
                SubNet.send(level, SubNet.DIZZY, t.getId(), t.position(), 60, 0);
                DbCommon.loseTarget(t);
            }
        });
        return true;
    }

    // ================= Pirata =================

    // Z Corte de Sabre: a lunge and one wide cut in front.
    private static boolean saber(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 6);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "soco.forte_carga");
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.8f);
        Tasks.later(7, () -> {
            if (gone(caster)) {
                return;
            }
            Vec3 dir = SubCommon.flat(toward(caster.position(), target.position()));
            caster.setDeltaMovement(dir.scale(1.1).add(0, 0.1, 0));
            caster.hurtMarked = true;
            SubCommon.pose(caster, "soco.forte_golpe");
            Tasks.later(3, () -> {
                Vec3 c = caster.position().add(dir.scale(1.4)).add(0, 1.1, 0);
                Vec3 swing = dir.cross(new Vec3(0, 1, 0)).add(0, -0.35, 0).normalize();
                SubNet.send(level, SubNet.SLASH, 0, c, swing, 2.2f, 0.32f, SubNet.STYLE_BLUE);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.75f);
                for (LivingEntity t : Combat.inRadius(level, c, 2.4, caster)) {
                    if (toward(caster.position(), t.position()).dot(dir) < 0.2) {
                        continue;
                    }
                    Combat.hurtMagic(caster, t, 80);
                    Stun.apply(t, 14);
                    DbCommon.hit(level, Combat.center(t), 0.6f, Db.WHITE);
                    Combat.pushFrom(t, caster.position(), 0.6, 0.2);
                }
            });
        });
        return true;
    }

    // X Bomba de Pólvora: a black bomb lobbed high, it sits there with its fuse burning (sparks) for a second, then blows
    // up (blast, hole).
    private static boolean powderBomb(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 16);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.arremesso");
        SubCommon.sound(level, caster.position(), SoundEvents.TNT_PRIMED, 1f, 1.2f);
        Vec3 from = DbCommon.raised(caster);
        Vec3 land = SubCommon.ground(level, target.position());
        int flight = Mth.clamp((int) (from.distanceTo(land) / 0.6), 10, 30);
        int id = SubNet.newId();
        int sparks = SubNet.newId();
        Tasks.add(age -> {
            if (age <= flight) {
                float k = age / (float) flight;
                Vec3 p = from.lerp(land, k).add(0, Math.sin(k * Math.PI) * (2 + from.distanceTo(land) * 0.25), 0);
                SubNet.send(level, SubNet.KI_BALL, id, p, Vec3.ZERO, 0.22f, Db.RED + 16 * 2, 0);
                return false;
            }
            int fuse = age - flight;
            Vec3 p = land.add(0, 0.25, 0);
            SubNet.send(level, SubNet.KI_BALL, id, p, Vec3.ZERO, 0.22f, Db.RED, 0);
            SubNet.send(level, SubNet.SPARKS, sparks, p.add(0, 0.25, 0), Vec3.ZERO, 0.4f + fuse * 0.03f, Db.ORANGE, 0);
            if (fuse % 4 == 0) {
                SubCommon.sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.8f);
            }
            if (fuse < 20) {
                return false;
            }
            SubNet.send(level, SubNet.KI_BALL, id, p, Vec3.ZERO, 0.22f, Db.RED, 1);
            SubNet.send(level, SubNet.SPARKS, sparks, p, Vec3.ZERO, 0, Db.ORANGE, 1);
            DbCommon.boom(level, p, 2.8f, Db.ORANGE, SubCommon.RANDOM.nextInt(100));
            DbCommon.screen(level, p, 4);
            DbCommon.hurtRadius(caster, level, p, 3.2, 110, 1.1, 0.5);
            dent(level, p, 2.0);
            return true;
        });
        return true;
    }

    // ================= Soldado da Red Ribbon =================

    // Z Metralhadora: 14 bullets two ticks apart, spreading a little; each bullet stops at the first thing it meets.
    private static boolean machineGun(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 22);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.apontar");
        SubCommon.busy(caster, 34);
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                return true;
            }
            face(caster, Combat.center(target));
            if (age < 5 || age % 2 != 0) {
                return false;
            }
            int shot = (age - 5) / 2;
            if (shot >= 14) {
                return true;
            }
            Vec3 from = DbCommon.hand(caster);
            Vec3 dir = toward(from, Combat.center(target)).add(SubCommon.RANDOM.nextGaussian() * 0.04, SubCommon.RANDOM.nextGaussian() * 0.03,
                    SubCommon.RANDOM.nextGaussian() * 0.04).normalize();
            Vec3 end = clip(level, caster, from, dir, 24);
            for (LivingEntity t : Combat.alongLine(level, from, end, 0.45, caster)) {
                end = Combat.center(t);
                Combat.hurtMagic(caster, t, 14);
                Stun.apply(t, 3);
                break;
            }
            SubNet.send(level, SubNet.STREAK, 0, from, end.subtract(from), 0.06f, Db.GOLD_W, 2);
            DbCommon.hit(level, end, 0.18f, Db.GOLD);
            SubCommon.sound(level, from, SoundEvents.IRON_GOLEM_STEP, 0.7f, 1.9f);
            return false;
        });
        return true;
    }

    // X Bazuca: aims for half a second, then a rocket flies straight and fast, trailing; it blows up on the first thing
    // it meets (blast, hole).
    private static boolean bazooka(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 28);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.lanca_alto");
        SubCommon.busy(caster, 14);
        SubCommon.sound(level, caster.position(), SoundEvents.CROSSBOW_LOADING_END.value(), 1.2f, 0.6f);
        int id = SubNet.newId();
        Vec3[] at = {null};
        Vec3[] dir = {null};
        Tasks.add(age -> {
            if (age < 10) {
                if (gone(caster)) {
                    return true;
                }
                face(caster, Combat.center(target));
                return false;
            }
            if (at[0] == null) {
                at[0] = DbCommon.raised(caster);
                dir[0] = toward(at[0], Combat.center(target));
                SubCommon.sound(level, at[0], SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6f, 0.6f);
            }
            Vec3 next = at[0].add(dir[0].scale(1.3));
            boolean hit = blocked(level, next) || age > 40 || !Combat.alongLine(level, at[0], next, 0.6, caster).isEmpty();
            at[0] = next;
            SubNet.send(level, SubNet.KI_BALL, id, next, Vec3.ZERO, 0.2f, Db.RED_W + 16 * 2, hit ? 1 : 0);
            if (!hit) {
                return false;
            }
            DbCommon.boom(level, next, 3.4f, Db.RED_W, 1000 + SubCommon.RANDOM.nextInt(100));
            DbCommon.screen(level, next, 8);
            DbCommon.hurtRadius(caster, level, next, 3.6, 130, 1.2, 0.5);
            dent(level, next, 2.6);
            return true;
        });
        return true;
    }

    // ================= Yeti =================

    // Z Rugido Congelante: a roar in a cone 9 blocks long; the ground in it freezes and whoever is in it is caught in ice
    // for 2 s (hitting the ice breaks it like any freeze).
    private static boolean freezingRoar(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 9);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.grito");
        SubCommon.busy(caster, 20);
        face(caster, Combat.center(target));
        SubCommon.sound(level, caster.position(), SoundEvents.POLAR_BEAR_WARNING, 2f, 0.5f);
        Vec3 dir = SubCommon.flat(toward(caster.position(), target.position()));
        Tasks.later(6, () -> {
            if (gone(caster)) {
                return;
            }
            SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 1.3f);
            for (int i = 1; i <= 4; i++) {
                Vec3 p = SubCommon.ground(level, caster.position().add(dir.scale(i * 2.2)));
                SubNet.send(level, SubNet.FROST, 0, p, Vec3.ZERO, 1.2f + i * 0.5f, 60, 0);
            }
            DbCommon.screen(level, caster.position(), 4);
            for (LivingEntity t : Combat.inRadius(level, caster.position().add(dir.scale(4.5)), 5, caster)) {
                Vec3 to = toward(caster.position(), t.position());
                if (SubCommon.flat(to).dot(dir) < 0.6) {
                    continue;
                }
                Combat.hurtMagic(caster, t, 50);
                if (!Freeze.isFrozen(t)) {
                    Freeze.hold(t, 40);
                    SubNet.send(level, SubNet.FREEZE, t.getId(), t.position(), new Vec3(8, 0, 0), 40, 0, 0);
                }
            }
        });
        return true;
    }

    // X Pisão Avalanche: leaps toward the target and comes down with both fists: a ring of ice spikes bursts up round
    // the landing and throws everyone near up.
    private static boolean avalancheStomp(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 13);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.ceu");
        SubCommon.busy(caster, 30);
        Vec3 d = target.position().subtract(caster.position());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        caster.setDeltaMovement(d.x / Math.max(1, flat) * Math.min(1.3, flat * 0.09), 0.85, d.z / Math.max(1, flat) * Math.min(1.3, flat * 0.09));
        caster.hurtMarked = true;
        SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_STEP, 1.6f, 0.6f);
        Tasks.add(age -> {
            if (gone(caster)) {
                return true;
            }
            if (age < 6 || !caster.onGround() && age < 40) {
                if (age == 8) {
                    SubCommon.pose(caster, "soco.forte_golpe");
                }
                return false;
            }
            Vec3 g = caster.position();
            SubCommon.sound(level, g, SoundEvents.GLASS_BREAK, 2f, 0.5f);
            SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.4f);
            DbCommon.boom(level, g.add(0, 0.2, 0), 2.4f, Db.WHITE, 1000 + SubCommon.RANDOM.nextInt(100));
            SubNet.send(level, SubNet.FROST, 0, g, Vec3.ZERO, 4.5f, 80, 0);
            int spikes = 9;
            float turn = SubCommon.RANDOM.nextFloat() * Mth.TWO_PI;
            for (int i = 0; i < spikes; i++) {
                double a = turn + i * Mth.TWO_PI / spikes + SubCommon.RANDOM.nextGaussian() * 0.15;
                double r = 2.2 + SubCommon.RANDOM.nextDouble() * 1.2;
                Vec3 p = SubCommon.ground(level, g.add(Math.cos(a) * r, 0, Math.sin(a) * r));
                Vec3 lean = new Vec3(Math.cos(a) * 0.45, 1, Math.sin(a) * 0.45).normalize();
                int delay = i % 3;
                Tasks.later(delay, () -> SubNet.send(level, SubNet.ICE_SPIKE, 0, p, lean, 1.2f + SubCommon.RANDOM.nextFloat() * 1.3f, 50, 0));
            }
            DbCommon.screen(level, g, 8);
            DbCommon.hurtRadius(caster, level, g.add(0, 0.5, 0), 4.2, 110, 0.6, 0.7);
            for (LivingEntity t : Combat.inRadius(level, g, 4.2, caster)) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
            }
            return true;
        });
        return true;
    }

    // ================= Soldado do Freeza =================

    // Z Rajada Tripla: three pink shots from the arm, a few ticks apart, each bursting where it lands.
    private static boolean tripleShot(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 22);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, 20);
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                return true;
            }
            face(caster, Combat.center(target));
            if (age < 4 || (age - 4) % 5 != 0) {
                return false;
            }
            int n = (age - 4) / 5;
            if (n >= 3) {
                return true;
            }
            Vec3 from = DbCommon.hand(caster);
            Vec3 aim = Combat.center(target).add(target.getDeltaMovement().scale(3)).add(SubCommon.RANDOM.nextGaussian() * 0.3, 0, SubCommon.RANDOM.nextGaussian() * 0.3);
            Vec3 dir = toward(from, aim);
            Vec3 end = clip(level, caster, from, dir, 24);
            for (LivingEntity t : Combat.alongLine(level, from, end, 0.6, caster)) {
                end = Combat.center(t);
                break;
            }
            int travel = Math.max(2, (int) (from.distanceTo(end) / 2.2));
            SubNet.send(level, SubNet.SHOT, 0, from, end.subtract(from), travel, Db.PINK, 0);
            SubCommon.sound(level, from, SoundEvents.BLAZE_SHOOT, 0.9f, 1.5f);
            Vec3 boomAt = end;
            Tasks.later(travel, () -> {
                DbCommon.boom(level, boomAt, 1.1f, Db.PINK_W, SubCommon.RANDOM.nextInt(100));
                DbCommon.hurtRadius(caster, level, boomAt, 1.6, 45, 0.4, 0.2);
            });
            return false;
        });
        return true;
    }

    // X Bomba de Ki: a purple ball grows over the raised hand for a second, then is thrown, slower and heavy; it blows up
    // big where it lands.
    private static boolean kiBomb(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster, 22);
        if (target == null) {
            return false;
        }
        SubCommon.pose(caster, "db.mao_alto");
        SubCommon.busy(caster, 28);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        int id = SubNet.newId();
        Vec3[] at = {null};
        Vec3[] dir = {null};
        Tasks.add(age -> {
            if (gone(caster) && at[0] == null) {
                SubNet.send(level, SubNet.KI_BALL, id, DbCommon.raised(caster), Vec3.ZERO, 0, Db.PURPLE, 1);
                return true;
            }
            if (age < 20) {
                float r = 0.15f + 0.45f * SubCommon.smooth(age / 20f);
                SubNet.send(level, SubNet.KI_BALL, id, DbCommon.raised(caster).add(0, 0.5, 0), Vec3.ZERO, r, Db.PURPLE + 16, 0);
                face(caster, Combat.center(target));
                return false;
            }
            if (at[0] == null) {
                SubCommon.pose(caster, "db.arremesso");
                at[0] = DbCommon.raised(caster).add(0, 0.5, 0);
                dir[0] = toward(at[0], Combat.center(target).add(target.getDeltaMovement().scale(5)));
                SubCommon.sound(level, at[0], SoundEvents.BLAZE_SHOOT, 1.4f, 0.6f);
            }
            Vec3 next = at[0].add(dir[0].scale(0.9));
            boolean hit = blocked(level, next) || age > 60 || !Combat.alongLine(level, at[0], next, 0.8, caster).isEmpty();
            at[0] = next;
            SubNet.send(level, SubNet.KI_BALL, id, next, Vec3.ZERO, 0.6f, Db.PURPLE + 16 * 2, hit ? 1 : 0);
            if (!hit) {
                return false;
            }
            DbCommon.boom(level, next, 3.4f, Db.PURPLE, SubCommon.RANDOM.nextInt(100));
            DbCommon.screen(level, next, 9);
            DbCommon.hurtRadius(caster, level, next, 3.6, 140, 1.3, 0.5);
            dent(level, next, 2.4);
            return true;
        });
        return true;
    }

    // ================= Dinosaurs (called from animal/Dinosaur) =================

    // Raptor Z Bote: crouches, pounces from up to 10 blocks; if it lands on the target it pins it down and bites three
    // times, then springs off.
    public static boolean pounce(Mob caster, ServerLevel level) {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        caster.getNavigation().stop();
        SubCommon.sound(level, caster.position(), SoundEvents.FOX_AGGRO, 1.4f, 0.6f);
        int[] pinned = {-1};
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                return true;
            }
            caster.getLookControl().setLookAt(target);
            if (age < 7) {
                caster.getNavigation().stop();
                return false;
            }
            if (age == 7) {
                Vec3 d = target.position().subtract(caster.position());
                double flat = Math.sqrt(d.x * d.x + d.z * d.z);
                caster.setDeltaMovement(d.x / Math.max(1, flat) * Math.min(1.5, 0.3 + flat * 0.12), 0.5,
                        d.z / Math.max(1, flat) * Math.min(1.5, 0.3 + flat * 0.12));
                caster.hurtMarked = true;
                SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ATTACK, 1.2f, 1.6f);
                return false;
            }
            if (pinned[0] < 0) {
                if (caster.distanceTo(target) < 2.0) {
                    pinned[0] = age;
                    Stun.apply(target, 22);
                    Combat.pushFrom(target, caster.position(), 0.3, 0);
                } else if (age > 26) {
                    return true;
                }
                return false;
            }
            int held = age - pinned[0];
            Vec3 on = target.position().add(SubCommon.flat(caster.position().subtract(target.position())).scale(0.8));
            caster.teleportTo(on.x, target.getY() + 0.2, on.z);
            if (held % 7 == 0 && held <= 14) {
                Combat.hurtMagic(caster, target, 30);
                DbCommon.hit(level, Combat.center(target).add(0, -0.3, 0), 0.4f, Db.RED);
                SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            if (held < 20) {
                return false;
            }
            caster.setDeltaMovement(SubCommon.flat(caster.position().subtract(target.position())).scale(0.7).add(0, 0.45, 0));
            caster.hurtMarked = true;
            return true;
        });
        return true;
    }

    private static final Map<Integer, Integer> BLEEDING = new HashMap<>();

    // Raptor X Garras: two claw slashes crossing in an X; the target bleeds for 4 s.
    public static boolean claws(Mob caster, ServerLevel level) {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive() || caster.distanceTo(target) > 3.0) {
            return false;
        }
        caster.getNavigation().stop();
        Tasks.add(age -> {
            if (gone(caster) || !target.isAlive()) {
                return true;
            }
            caster.getLookControl().setLookAt(target);
            if (age != 3 && age != 8) {
                return age > 8;
            }
            Vec3 dir = SubCommon.flat(toward(caster.position(), target.position()));
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            Vec3 swing = side.add(0, age == 3 ? 0.8 : -0.8, 0).normalize();
            Vec3 c = Combat.center(target).add(dir.scale(-0.3));
            SubNet.send(level, SubNet.SLASH, 0, c, swing, 1.1f, 0.18f, SubNet.STYLE_CRIMSON);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.5f);
            caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (caster.distanceTo(target) < 3.0) {
                Combat.hurtMagic(caster, target, 28);
                Stun.apply(target, 6);
                if (age == 8) {
                    bleed(caster, level, target);
                }
            }
            return false;
        });
        return true;
    }

    private static void bleed(LivingEntity caster, ServerLevel level, LivingEntity target) {
        BLEEDING.put(target.getId(), 4);
        Tasks.repeat(80, tick -> {
            if (tick % 20 == 19 && target.isAlive()) {
                Combat.hurtMagic(caster, target, 10);
                DbCommon.hit(level, Combat.center(target).add(SubCommon.RANDOM.nextGaussian() * 0.2, -0.2, SubCommon.RANDOM.nextGaussian() * 0.2), 0.22f,
                        Db.RED);
            }
            if (tick == 79) {
                BLEEDING.remove(target.getId());
            }
        });
    }

    // Tyrannosaur Z Investida: lowers its head and charges 12 blocks in a straight line; everyone on the way is thrown
    // aside, soft blocks (leaves, plants, glass) are smashed, and the ground shakes under each step.
    public static boolean rexCharge(Mob caster, ServerLevel level) {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        caster.getNavigation().stop();
        SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
        Vec3 dir = SubCommon.flat(toward(caster.position(), target.position()));
        Set<Integer> hit = DbCommon.once();
        Tasks.add(age -> {
            if (gone(caster)) {
                return true;
            }
            if (age < 10) {
                caster.getLookControl().setLookAt(target);
                caster.getNavigation().stop();
                return false;
            }
            int run = age - 10;
            if (run > 18) {
                return true;
            }
            caster.setDeltaMovement(dir.x * 0.75, caster.getDeltaMovement().y, dir.z * 0.75);
            caster.hurtMarked = true;
            float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
            caster.setYRot(yaw);
            caster.yBodyRot = yaw;
            if (run % 4 == 0) {
                Vec3 feet = caster.position();
                DbCommon.boom(level, feet.add(0, 0.1, 0), 1.0f, Db.WHITE, 1000 + SubCommon.RANDOM.nextInt(100));
                SubCommon.sound(level, feet, SoundEvents.RAVAGER_STEP, 2f, 0.5f);
                DbCommon.screen(level, feet, 4);
            }
            for (LivingEntity t : Combat.inRadius(level, caster.position().add(dir.scale(1.6)).add(0, 1, 0), 2.2, caster)) {
                if (hit.add(t.getId())) {
                    Combat.hurtMagic(caster, t, 110);
                    Vec3 side = dir.cross(new Vec3(0, 1, 0));
                    Vec3 away = t.position().subtract(caster.position()).dot(side) >= 0 ? side : side.scale(-1);
                    Combat.push(t, dir.add(away.scale(0.8)), 1.8, 0.6);
                    Stun.apply(t, 18);
                    DbCommon.hit(level, Combat.center(t), 0.8f, Db.WHITE);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
                }
            }
            smashSoft(level, caster.position().add(dir.scale(1.8)));
            return false;
        });
        return true;
    }

    // Soft blocks in the rex's way (leaves, plants, glass, snow): no drops.
    private static void smashSoft(ServerLevel level, Vec3 at) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos base = BlockPos.containing(at);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = base.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    float hard = s.getDestroySpeed(level, p);
                    if (!s.isAir() && hard >= 0 && hard <= 0.6f && !s.hasBlockEntity() && s.getFluidState().isEmpty()) {
                        level.destroyBlock(p, false);
                    }
                }
            }
        }
    }

    // Tyrannosaur X Pisão Sísmico: rears up and stomps: three cracks tear the ground out from its feet and a wave of
    // dust runs round; everyone on the ground near it takes it and is knocked off balance (stun).
    public static boolean rexStomp(Mob caster, ServerLevel level) {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive() || caster.distanceTo(target) > 5) {
            return false;
        }
        caster.getNavigation().stop();
        SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 0.8f);
        Tasks.later(12, () -> {
            if (gone(caster)) {
                return;
            }
            Vec3 g = caster.position();
            SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.5f);
            SubCommon.sound(level, g, SoundEvents.RAVAGER_STUNNED, 1.6f, 0.5f);
            DbCommon.boom(level, g.add(0, 0.1, 0), 3.0f, Db.WHITE, 1000 + SubCommon.RANDOM.nextInt(100));
            float turn = SubCommon.RANDOM.nextFloat() * Mth.TWO_PI;
            for (int i = 0; i < 3; i++) {
                double a = turn + i * Mth.TWO_PI / 3 + SubCommon.RANDOM.nextGaussian() * 0.3;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                SubNet.send(level, SubNet.SCAR, 0, g.add(0, 0.05, 0), dir.scale(5.5 + SubCommon.RANDOM.nextDouble() * 2.5), 0.6f, 120, 0);
            }
            DbCommon.screen(level, g, 10);
            for (LivingEntity t : Combat.inRadius(level, g, 5.0, caster)) {
                if (!t.onGround()) {
                    continue;   // a jump in time dodges it
                }
                double k = 1 - 0.5 * Math.min(1, t.position().distanceTo(g) / 5.0);
                Combat.hurtMagic(caster, t, (float) (120 * k));
                Stun.apply(t, 25);
                Combat.pushFrom(t, g, 0.5 * k, 0.5);
            }
        });
        return true;
    }
}
