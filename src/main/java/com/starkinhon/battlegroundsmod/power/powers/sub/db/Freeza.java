// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Freeza (SubAlma of Dragon Ball, tier 6), the Visualizador's Freeza tab.
// Z Death Beam: a thin pink ray, instant, that pierces the target; held, a burst of 8 (some miss and hole the ground),
//   the last one pushes.
// X Telecinese viva (1st update, approved 2026-11-27): held, the target follows the aim limp as a rag doll; dragged fast
//   it plows through walls, pulled down it is crushed, dragged on the ground it digs a furrow; let go, it falls loose.
// C Death Saucer: a pink saw disc cuts the target and goes on, turns and comes back... and stops for no one: it cuts
//   Freeza too. C again blows it up before it comes back.
// V Supernova: a little red ball on the raised finger grows into a red sun the size of a house; a flick and it homes in
//   on the target: a dome of fire of radius 36, a crater, lava cracks and tremors.
// B Death Psycho Bomb (1st update, approved 2026-11-27): the target held in the telekinesis rises and bursts in the sky.
public final class Freeza {
    public static final String TELE = "db_freeza_tele";
    public static final String SAUCER = "db_freeza_saucer";
    // 1.5x of the first port (15 and 30 blocks).
    private static final double TELE_RANGE = 22.5;
    private static final double SAUCER_RANGE = 45;

    private Freeza() {
    }

    public static Power create() {
        return new Power("freeza", 0xC050FF, 1.0f, List.of(
                new Ability(160, 0, Freeza::deathBeam),
                new Ability(220, 0, Freeza::telekinesis),
                new Ability(260, 0, Freeza::saucer),
                new Ability(1100, 0, Freeza::deathBall),
                new Ability(600, 0, Freeza::psychoBomb)));
    }

    // ---------- Z: Death Beam ----------

    private static boolean deathBeam(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossShots = 1 + SubCommon.RANDOM.nextInt(8);
        SubCommon.pose(caster, "db.apontar");
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < 2) {
                return false;
            }
            // Held: a burst of 8, one every 3 ticks; the last one pushes.
            int shot = (age - 2) / 3;
            boolean held = caster instanceof Player ? SubCommon.held(caster, age, 0) : shot < bossShots;
            if ((age - 2) % 3 == 0) {
                if (shot > 0 && !held) {
                    return true;
                }
                boolean last = shot >= 7 || (shot > 0 && !(caster instanceof Player) && shot + 1 >= bossShots);
                fire(caster, level, shot, last);
                if (last) {
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    private static void fire(LivingEntity caster, ServerLevel level, int shot, boolean last) {
        Vec3 tip = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.2));
        Vec3 dir = caster.getLookAngle();
        // In the burst some miss: they go low and punch a hole in the ground behind.
        boolean miss = shot > 0 && !last && SubCommon.RANDOM.nextFloat() < 0.3f;
        if (shot > 0) {
            // Each one at a slightly new spot.
            double spread = miss ? 0.2 : 0.06;
            dir = dir.add((SubCommon.RANDOM.nextDouble() - 0.5) * spread, (SubCommon.RANDOM.nextDouble() - 0.5) * spread - (miss ? 0.15 : 0),
                    (SubCommon.RANDOM.nextDouble() - 0.5) * spread).normalize();
        }
        Vec3 end = DbCommon.beamEnd(caster, level, tip, dir, 40, 0.4, true);
        DbScar.along(caster, level, tip, end, 0.4);
        SubNet.send(level, SubNet.DEATH_BEAM, 0, tip, end.subtract(tip), 0, 0, 0);
        SubCommon.sound(level, tip, SoundEvents.TRIDENT_THROW.value(), 1.2f, 2f);
        SubCommon.sound(level, tip, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 2f);
        boolean hitSomeone = false;
        for (LivingEntity t : Combat.alongLine(level, tip, end, 0.45, caster)) {
            hitSomeone = true;
            Combat.hurtMagic(caster, t, 5.5f);
            DbCommon.hit(level, Combat.center(t), 0.5f, Db.PINK);
            if (last) {
                t.setDeltaMovement(t.getDeltaMovement().add(dir.x * 1.1, 0.35, dir.z * 1.1));
                t.hurtMarked = true;
            }
        }
        if (last && hitSomeone) {
            DbCommon.screen(level, end, 12);
        }
        if (!hitSomeone && end.distanceTo(tip) < 39.5 && SubCommon.solid(level, end.add(dir.scale(0.3)))) {
            JjCommon.crater(level, end, 0.6, 1.5, 3);
        }
    }

    // ---------- X: Telecinese viva ----------

    // Held up to 3 s (a boss 1.5 s); the one held follows the aim up to 12 blocks away.
    private static final int TELE_HOLD = 60;
    private static final int TELE_HOLD_BOSS = 30;
    private static final double TELE_REACH = 12;
    // The most it moves in a tick (32 blocks a second), and how fast it must go to plow through a wall (6 a second).
    private static final double TELE_STEP = 1.6;
    private static final double TELE_PLOW = 0.3;
    // All the damage one hold can deal (walls, slams and furrows added up).
    private static final float TELE_DAMAGE_MAX = 32;

    // Hold X (the approved Telecinese viva, Stark 2026-11-27: "top melhores coisas desse mod"): the target is torn off the
    // ground and FOLLOWS THE AIM, limp as a rag doll (client LimpBody, the same springs as Gojo's Shift+Z). Dragged fast
    // against a wall it goes through it breaking block by block (damage per block, by the speed); slow, the wall holds
    // it. Pulled fast to the ground it is crushed (crater and damage by the speed); dragged along the ground it digs a
    // furrow. Let go: it falls like a doll, keeping its speed (a fast fall still crushes it). No wire: only the purple
    // outline trembling on it and the sparks (DbFx.teleLive).
    private static boolean telekinesis(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, TELE)) {
            return false;
        }
        LivingEntity target = SubCommon.aimed(caster, TELE_RANGE);
        if (target == null) {
            SubCommon.warn(caster, "freeza.tele_no_target", TELE_RANGE);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean player = caster instanceof Player;
        int most = player ? TELE_HOLD : TELE_HOLD_BOSS;
        double reach = Math.max(4, Math.min(TELE_REACH, caster.getEyePosition().distanceTo(Combat.center(target))));
        Vec3 start = target.position();
        double half = target.getBbHeight() * 0.5;
        SubCommon.setState(caster, TELE, most + 2);
        SubCommon.pose(caster, "db.mao_alto");
        SubCommon.sound(level, start, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.7f);
        Vec3[] feet = {start};
        Vec3[] vel = {Vec3.ZERO};
        float[] dealt = {0};
        int[] slamCool = {0};
        double[] down = {0};
        Runnable release = Freeze.hold(target, most + 4, a -> feet[0]);
        PowerLogic.seal(target, most + 4);
        limp(target, ModPayloads.Limp.PULL, 0, 0);
        Tasks.add(age -> {
            boolean let = player ? !SubCommon.held(caster, age, 0) : age >= most;
            if (guard.broken() || !target.isAlive() || target.isRemoved() || age >= most || (age > 2 && let)) {
                SubCommon.clearState(caster, TELE);
                release.run();
                target.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).sealedUntil = 0;
                SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), 0, 2, 1);
                SubCommon.pose(caster, "");
                if (target.isAlive()) {
                    // It falls like a doll from where it is, keeping the speed it had.
                    Vec3 v = vel[0].length() > 2.2 ? vel[0].normalize().scale(2.2) : vel[0];
                    target.setDeltaMovement(v);
                    target.hurtMarked = true;
                    limp(target, ModPayloads.Limp.THROW, 20, (float) Math.min(1, v.length()));
                    fallWatch(caster, level, target, dealt);
                }
                DbCommon.cooldown(caster, 1, 220);
                return true;
            }
            SubCommon.busy(caster, 3);
            slamCool[0]--;
            // Where it should be: the aim at the hold distance, or the first block the aim meets before that (aiming at
            // the ground pulls it into the ground). A boss lifts it high and brings it down hard.
            Vec3 aimC;
            if (player) {
                Vec3 eye = caster.getEyePosition();
                Vec3 far = eye.add(caster.getLookAngle().scale(reach));
                var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
                aimC = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS ? hit.getLocation() : far;
            } else {
                aimC = age < most * 0.6 ? start.add(0, 6 + half, 0) : new Vec3(start.x, DbCommon.groundY(level, start.add(0, 2, 0)) - 2, start.z);
            }
            Vec3 want = aimC.subtract(0, half, 0);
            Vec3 c = feet[0];
            Vec3 step = want.subtract(c).scale(0.5);
            if (step.length() > TELE_STEP) {
                step = step.normalize().scale(TELE_STEP);
            }
            Vec3 next = c.add(step);
            double sp = step.length();
            // Through a wall: fast enough, the blocks the body meets break one by one (damage for each); slow, it holds.
            int broke = 0;
            boolean stopped = false;
            for (double y : new double[] {0.5, Math.max(0.9, half * 2 - 0.4)}) {
                for (int s = 1; s <= 3; s++) {
                    Vec3 p = c.lerp(next, s / 3.0).add(0, y, 0);
                    BlockPos bp = BlockPos.containing(p);
                    BlockState st = level.getBlockState(bp);
                    if (st.isAir() || st.getCollisionShape(level, bp).isEmpty()) {
                        continue;
                    }
                    if (sp >= TELE_PLOW && breakable(level, bp, st) && com.starkinhon.battlegroundsmod.combat.BlockRules.canBreak(level)) {
                        JjCommon.breakAt(level, bp, c, SubCommon.RANDOM.nextInt(3) == 0);
                        broke++;
                    } else {
                        stopped = true;
                    }
                }
            }
            if (stopped) {
                next = c;
            }
            // The ground stops it: coming down fast it is crushed.
            double ground = DbCommon.groundY(level, next.add(0, 1, 0));
            double fallSp = c.y - next.y;
            down[0] = Math.max(down[0] * 0.75, fallSp);
            if (next.y < ground) {
                next = new Vec3(next.x, ground, next.z);
                if (down[0] > 0.45 && slamCool[0] <= 0) {
                    slamCool[0] = 7;
                    slam(caster, level, target, next, down[0] * 20, dealt);
                    down[0] = 0;
                }
            }
            vel[0] = next.subtract(c);
            feet[0] = next;
            if (broke > 0) {
                float d = Math.min(TELE_DAMAGE_MAX - dealt[0], (float) (broke * sp * 20 * 0.12));
                if (d > 0) {
                    Combat.hurt(caster, target, d);
                    dealt[0] += d;
                }
                limp(target, ModPayloads.Limp.JOLT, 0, Math.min(1, 0.3f + broke * 0.1f));
                DbCommon.hit(level, Combat.center(target), 0.5f + Math.min(1, broke * 0.1f), Db.WHITE);
                DbCommon.screen(level, Combat.center(target), 4);
                SubCommon.sound(level, Combat.center(target), SoundEvents.STONE_BREAK, 1.4f, 0.7f);
            }
            // Dragged along the ground, fast: a furrow behind it.
            double flat = Math.hypot(vel[0].x, vel[0].z);
            if (next.y - ground < 0.45 && flat > 0.22 && age % 2 == 0) {
                JjCommon.crater(level, next, 0.7, 0.55, 2);
                if (dealt[0] < TELE_DAMAGE_MAX) {
                    Combat.hurt(caster, target, 1);
                    dealt[0] += 1;
                }
                SubCommon.sound(level, next, SoundEvents.GRAVEL_BREAK, 1f, 0.8f);
            }
            SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster).add(0, 0.3, 0), 1, 2, 0);
            if (age % 10 == 0) {
                SubCommon.sound(level, target.position(), SoundEvents.BEACON_AMBIENT, 1f, 1.6f);
            }
            return false;
        });
        // The key stays free: the cooldown starts when it lets go.
        DbCommon.cooldown(caster, 1, 6);
        return false;
    }

    // Crushed on the ground at speed sp (blocks a second): crater and damage grow with it.
    private static void slam(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 at, double sp, float[] dealt) {
        double r = Mth.clamp(sp * 0.075, 1.2, 2.7);
        double deep = Mth.clamp(sp * 0.055, 0.6, 1.8);
        float d = Math.min(TELE_DAMAGE_MAX - dealt[0], (float) Mth.clamp(sp * 0.6, 6, 20));
        if (d > 0) {
            Combat.hurt(caster, target, d);
            dealt[0] += d;
        }
        Stun.apply(target, 25);
        limp(target, ModPayloads.Limp.JOLT, 0, 1);
        DbCommon.hit(level, at.add(0, 0.5, 0), 1.2f, Db.WHITE);
        DbCommon.screen(level, at, 10);
        SubNet.send(level, SubNet.KI_BOOM, 0, at.add(0, 0.5, 0), new Vec3(at.y, 0, 0), (float) (r * 0.6), Db.PURPLE, 1000);
        SubCommon.sound(level, at, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.2f);
        JjCommon.crater(level, at, r, deep, (int) (6 + sp * 0.3));
    }

    // Let go: up to 2 s watching the fall; landing fast it is crushed like a slam.
    private static void fallWatch(LivingEntity caster, ServerLevel level, LivingEntity target, float[] dealt) {
        double[] vy = {0};
        Tasks.add(age -> {
            if (!target.isAlive() || age > 40) {
                return true;
            }
            if (target.onGround() && age > 1) {
                if (vy[0] > 0.65) {
                    slam(caster, level, target, target.position(), vy[0] * 20 * 0.8, dealt);
                }
                return true;
            }
            vy[0] = Math.max(0, -target.getDeltaMovement().y);
            return false;
        });
    }

    private static void limp(LivingEntity target, int phase, int ticks, float strength) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.Limp(target.getId(), phase, ticks, strength));
    }

    // ---------- B: Death Psycho Bomb ----------

    private static final double PSYCHO_RANGE = 25;
    private static final float PSYCHO_DAMAGE = 22;

    // The death of Kuririn (approved 2026-11-27, forja/freeza-kit/freeza-novas.js): he points, the target is held in the
    // telekinesis (the same outline and sparks as X), rises 7 blocks struggling (1.2 s, nothing it can do), and as he
    // closes the hand it swells with light from inside and bursts in the sky: high damage, the ground untouched. Survived:
    // it falls burnt and lies 1 s. Finishing (the blow would kill it): it rises 18 blocks and bursts in a giant blast with
    // two shockwaves.
    private static boolean psychoBomb(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, PSYCHO_RANGE);
        if (target == null) {
            SubCommon.warn(caster, "freeza.psycho_no_target", (int) PSYCHO_RANGE);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean finish = target.getHealth() <= PSYCHO_DAMAGE;
        double lift = finish ? 18 : 7;
        int point = 6;
        int grip = point + 7;
        int rise = grip + (finish ? 36 : 24);
        int swell = rise + 9;
        int ballId = SubNet.newId();
        Vec3 start = target.position();
        double half = target.getBbHeight() * 0.5;
        SubCommon.pose(caster, "db.apontar");
        SubCommon.busy(caster, swell + 4);
        Runnable[] hold = {null};
        Vec3[] at = {start};
        Tasks.add(age -> {
            boolean gripped = age >= point;
            if (guard.broken() || !target.isAlive() || target.isRemoved()) {
                if (hold[0] != null) {
                    hold[0].run();
                    target.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).sealedUntil = 0;
                    SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), 0, 3, 1);
                    SubNet.send(level, SubNet.KI_BALL, ballId, at[0], Vec3.ZERO, 0, Db.PINK, 1);
                }
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age == point) {
                // Held: the rim, the sparks, stuck where it stands.
                hold[0] = Freeze.hold(target, swell - point + 2, a -> at[0]);
                PowerLogic.seal(target, swell - point + 2);
                DbCommon.screen(level, Combat.center(target), 9);
                SubCommon.sound(level, start, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 1.1f);
            }
            if (age >= grip && age < rise) {
                if (age == grip) {
                    SubCommon.pose(caster, "db.mao_alto");
                }
                // Up, trembling and struggling.
                float k = SubCommon.smooth((age - grip) / (float) (rise - grip));
                Vec3 up = start.add(Math.sin(age * 2.6) * 0.04, lift * k, Math.cos(age * 2.3) * 0.04);
                if (!SubCommon.solid(level, up.add(0, half * 2 + 0.2, 0))) {
                    at[0] = up;
                }
                if (age % 6 == 0) {
                    limp(target, ModPayloads.Limp.JOLT, 0, 0.5f);
                }
            }
            if (age >= swell - 9 && age < swell) {
                // He closes the hand: it swells with light from inside.
                if (age == swell - 9) {
                    SubCommon.pose(caster, "db.gesto");
                    SubCommon.sound(level, at[0], SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.6f);
                }
                float k = (age - (swell - 9)) / 9f;
                SubNet.send(level, SubNet.KI_BALL, ballId, at[0].add(0, half, 0), Vec3.ZERO, 0.25f + 1.1f * k * k, Db.PINK_W, 0);
            }
            if (gripped && age < swell) {
                float strength = age >= swell - 9 ? 1 + 1.5f * (age - (swell - 9)) / 9f : Math.min(1, (age - point) / 6f);
                SubNet.send(level, SubNet.TELEKINESIS, target.getId(), Combat.center(target), DbCommon.hand(caster), strength, 3, 0);
                return false;
            }
            if (age < swell) {
                return false;
            }
            // It bursts in the sky.
            hold[0].run();
            target.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).sealedUntil = 0;
            Vec3 c = at[0].add(0, half, 0);
            SubNet.send(level, SubNet.TELEKINESIS, target.getId(), c, DbCommon.hand(caster), 0, 3, 1);
            SubNet.send(level, SubNet.KI_BALL, ballId, c, Vec3.ZERO, 0, Db.PINK, 1);
            DbCommon.boom(level, c, finish ? 8 : 3.2f, Db.PINK, 11);
            DbCommon.screen(level, c, finish ? 11 : 6);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), finish ? 5f : 3f, finish ? 0.6f : 0.9f);
            SubCommon.sound(level, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.7f);
            if (finish) {
                // the second shockwave a moment later
                Tasks.later(5, () -> {
                    DbCommon.boom(level, c, 6, Db.PINK_W, 12);
                    SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                });
            }
            Combat.hurtMagic(caster, target, finish ? Math.max(PSYCHO_DAMAGE, target.getHealth() + 1) : PSYCHO_DAMAGE);
            for (LivingEntity e : Combat.inRadius(level, c, finish ? 8 : 3.2, caster)) {
                if (e != target) {
                    Combat.hurtMagic(caster, e, finish ? 10 : 5);
                    Combat.pushFrom(e, c, 1.2, 0.4);
                }
            }
            if (target.isAlive()) {
                // Survived: it falls burnt and lies a second.
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 40));
                target.setDeltaMovement(0, -0.1, 0);
                target.hurtMarked = true;
                limp(target, ModPayloads.Limp.THROW, 20, 0.8f);
                Stun.apply(target, 45);
            }
            SubCommon.pose(caster, "");
            return true;
        });
        return true;
    }

    // ---------- C: Death Saucer ----------

    private static boolean saucer(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, SAUCER)) {
            SubCommon.clearState(caster, SAUCER);
            return true;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int form = 24;
        int id = SubNet.newId();
        LivingEntity target = SubCommon.aimed(caster, SAUCER_RANGE);
        SubCommon.setState(caster, SAUCER, 200);
        SubCommon.pose(caster, "db.dedo_ceu");
        SubCommon.busy(caster, form);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        Set<Integer> cutOut = DbCommon.once();
        Set<Integer> cutBack = DbCommon.once();
        List<Vec3> path = new ArrayList<>();
        Vec3[] p = {null};
        Vec3[] goal = {null};
        int[] phase = {0};
        int[] turnAt = {0};
        Vec3[] turnFrom = {null};
        Vec3[] turnDir = {null};
        Tasks.add(age -> {
            boolean popped = !SubCommon.hasState(caster, SAUCER) && age > form;
            if (age < form) {
                if (guard.broken()) {
                    SubCommon.clearState(caster, SAUCER);
                    SubNet.send(level, SubNet.SAW, id, DbCommon.raised(caster), new Vec3(0, 1, 0), 0, Db.PINK, 1);
                    DbCommon.cooldown(caster, 2, 130);
                    return true;
                }
                float r = 0.15f + 0.45f * SubCommon.smooth((age - 2) / 20f);
                SubNet.send(level, SubNet.SAW, id, DbCommon.raised(caster).add(0, 0.45, 0), new Vec3(0, 1, 0), r, Db.PINK, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, 0.8f, 1.6f);
                }
                return false;
            }
            if (age == form) {
                SubCommon.pose(caster, "db.arremesso");
                p[0] = DbCommon.raised(caster).add(0, 0.45, 0);
                goal[0] = target != null ? Combat.center(target) : SubCommon.aimPoint(caster, SAUCER_RANGE).add(0, 1, 0);
                SubCommon.sound(level, p[0], SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.2f);
            }
            if (popped) {
                // C again: it bursts before coming back (no second cut).
                SubNet.send(level, SubNet.SAW, id, p[0], new Vec3(0, 1, 0), 0.6f, Db.PINK, 1);
                DbCommon.boom(level, p[0], 1.2f, Db.PINK, 21);
                DbCommon.hurtRadius(caster, level, p[0], 1.6, 6, 0.6, 0.2);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            int t = age - form;
            double speed = 0.55;
            Vec3 next;
            if (phase[0] == 0) {
                // Out: it follows the target and cuts it, then goes on past.
                if (target != null && target.isAlive() && !cutOut.contains(target.getId())) {
                    goal[0] = Combat.center(target);
                }
                Vec3 to = goal[0].subtract(p[0]);
                if (to.length() < speed && (target == null || cutOut.contains(target.getId()) || !target.isAlive())) {
                    phase[0] = 1;
                    turnAt[0] = t;
                    turnFrom[0] = p[0];
                    turnDir[0] = SubCommon.flat(p[0].subtract(path.isEmpty() ? caster.position() : path.get(0)));
                    next = p[0];
                } else {
                    Vec3 step = to.normalize().scale(speed);
                    next = p[0].add(step);
                    if (target != null && cutOut.contains(target.getId()) && t > 4) {
                        // Past the target: keep going a few blocks before turning.
                        goal[0] = goal[0].add(step.scale(0.6));
                        if (path.size() > 6 && next.distanceTo(Combat.center(target)) > 3) {
                            phase[0] = 1;
                            turnAt[0] = t;
                            turnFrom[0] = next;
                            turnDir[0] = SubCommon.flat(step);
                        }
                    }
                }
            } else if (phase[0] == 1) {
                // The turn: half a circle (radius 2.2), then back the way it came.
                float k = (t - turnAt[0]) / 18f;
                Vec3 side = turnDir[0].cross(new Vec3(0, 1, 0));
                double a = Math.min(1, k) * Math.PI;
                next = turnFrom[0].add(side.scale(1.1 - 1.1 * Math.cos(a))).add(turnDir[0].scale(1.1 * Math.sin(a)));
                if (k >= 1) {
                    phase[0] = 2;
                    turnDir[0] = turnDir[0].scale(-1);
                    SubCommon.sound(level, next, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2f, 1.2f);
                }
            } else {
                // Back toward Freeza (wherever he is now), and on past him once it has cut him.
                Vec3 to = Combat.center(caster).subtract(p[0]);
                if (!cutBack.contains(caster.getId()) && to.lengthSqr() > 1.0E-4) {
                    turnDir[0] = to.normalize();
                }
                next = p[0].add(turnDir[0].scale(speed));
            }
            Vec3 prev = p[0];
            p[0] = next;
            path.add(next);
            if (path.size() > 40) {
                path.remove(0);
            }
            SubNet.send(level, SubNet.SAW, id, next, new Vec3(0, 1, 0), 0.6f, Db.PINK, 0);
            if (t % 5 == 0) {
                SubCommon.sound(level, next, SoundEvents.UI_STONECUTTER_TAKE_RESULT, 0.9f, 1.4f);
            }
            // Cuts whoever it passes: once each way. On the way back it cuts Freeza too.
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(prev, next).inflate(0.9),
                    e -> e.isAlive() && !e.isSpectator())) {
                boolean self = e == caster;
                if (!self && !Combat.isTarget(e, caster)) {
                    continue;
                }
                Set<Integer> done = phase[0] == 2 ? cutBack : cutOut;
                if (self && phase[0] != 2) {
                    continue;
                }
                if (!done.add(e.getId())) {
                    continue;
                }
                Vec3 c = Combat.center(e);
                if (self) {
                    e.invulnerableTime = 0;
                    e.hurt(level.damageSources().magic(), 14 * com.starkinhon.battlegroundsmod.power.Tiers.damageMultiplier(caster) * com.starkinhon.battlegroundsmod.combat.Combat.subScale(caster));
                    Stun.apply(e, 26);
                    DbCommon.screen(level, c, 13);
                } else {
                    Combat.hurtMagic(caster, e, 15);
                    Stun.apply(e, 14);
                    DbCommon.screen(level, c, 12);
                }
                SubNet.send(level, SubNet.SLASH, 0, c, next.subtract(prev).normalize().add(0, 0.25, 0), 0.75f, 0.12f, SubNet.STYLE_PINK);
                DbCommon.hit(level, c, 0.8f, Db.PINK);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.2f);
            }
            boolean gone = t > 240 || (phase[0] == 2 && cutBack.contains(caster.getId()) && next.distanceTo(Combat.center(caster)) > 6)
                    || SubCommon.solid(level, next);
            if (gone) {
                SubNet.send(level, SubNet.SAW, id, next, new Vec3(0, 1, 0), 0.6f, Db.PINK, 1);
                SubCommon.clearState(caster, SAUCER);
                DbCommon.cooldown(caster, 2, 260);
                return true;
            }
            return false;
        });
        DbCommon.cooldown(caster, 2, form + 4);
        return false;
    }

    // ---------- V: Supernova ----------
    // Stark, 2026-11-09: "comparado ao tamanho da bola, a explosão tá ridícula": now it homes in on the target and lands
    // as the approved preview (forja/freeza-supernova): a dome of fire of radius 36, a crater, lava cracks running out
    // to ~40 blocks and three tremors popping the ground's blocks up. The hitbox is the dome itself (an ellipsoid R x R/2
    // growing with the fire): its front hits once with knockback, then whoever stays inside only burns, no knockback.
    public static final double NOVA_R = 36;
    private static final int NOVA_TICKS = 180;

    // The Freeza Poder Total boss throws it too (FreezaTotal.chuva, asked with an order by its tactics).
    public static boolean supernova(LivingEntity caster, ServerLevel level) {
        return deathBall(caster, level);
    }

    private static boolean deathBall(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int grow = 110;
        float R = 3.48f;
        SubCommon.pose(caster, "db.dedo_ceu_longo");
        // his aura, pink (Stark 2026-11-13: each one's own aura color)
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), grow + 4, Db.PINK, 1.2f);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        Vec3[] pos = {null};
        Vec3[] vel = {null};
        Vec3[] goal = {null};
        LivingEntity[] target = {null};
        double[] speed = {0.25};
        Destructible.Attack[] ball = {null};
        // Stark, 2026-11-26: charged at his feet height the ball was so close to the ground that it burst the moment he
        // threw it. Now he floats up to 3 blocks while it grows (less under a ceiling), so it is born high and leaves
        // clear of the ground; on the throw he drifts back down with slow falling (no fall damage).
        Vec3 base = caster.position();
        double lift = NOVA_LIFT;
        for (double y = 0.5; y <= NOVA_LIFT + 0.5; y += 0.5) {
            if (SubCommon.solid(level, base.add(0, caster.getBbHeight() + y, 0))) {
                lift = Math.max(0, y - 0.5);
                break;
            }
        }
        double up = lift;
        Tasks.add(age -> {
            if (age < grow) {
                if (guard.broken()) {
                    SubNet.send(level, SubNet.DEATH_BALL, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                    land(caster);
                    return true;
                }
                Vec3 hover = base.add(0, up * SubCommon.smooth(age / 20f), 0);
                Freeze.holdLookFree(caster, 2, a -> hover);
                caster.fallDistance = 0;
                SubCommon.busy(caster, 3);
                float g = SubCommon.smooth((age - 4) / 100f);
                float r = 0.08f + 3.4f * g;
                SubNet.send(level, SubNet.DEATH_BALL, id, DbCommon.raised(caster).add(0, 0.6 + r, 0), Vec3.ZERO, r, 0, 0);
                if (age % 20 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.FIRE_AMBIENT, 2f, 0.4f + g * 0.3f);
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 2f, 0.4f);
                }
                return false;
            }
            if (age == grow) {
                pos[0] = DbCommon.raised(caster).add(0, 0.6 + R, 0);
                land(caster);
                target[0] = SubCommon.aimed(caster, 70);
                goal[0] = SubCommon.aimPoint(caster, 50).add(0, R - 0.4, 0);
                Vec3 to = (target[0] != null ? target[0].position() : goal[0]).subtract(pos[0]);
                vel[0] = SubCommon.flat(to).scale(0.25).add(0, 0.125, 0);
                // Clash de poderes: thrown, it can lock with a beam or a ball coming the other way.
                ball[0] = Destructible.trackClash(level, caster, pos[0], R,
                        () -> SubNet.send(level, SubNet.DEATH_BALL, id, ball[0].pos(), Vec3.ZERO, R, 0, 1)).clash(1.5f, 0xFF5A3C, 22f)
                        .clashDraw(p -> SubNet.send(level, SubNet.DEATH_BALL, id, p, Vec3.ZERO, R, 0, 0));
                SubCommon.pose(caster, "db.gesto");
                SubCommon.sound(level, pos[0], SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.5f);
            }
            int t = age - grow;
            if (ball[0] != null && ball[0].gone()) {
                return true;
            }
            // Homing: it turns at most 1.5 rad/s toward the target and speeds up to 24 blocks/s, so it visibly curves but
            // walking sideways doesn't shake it off. It aims a ball's radius above the feet (aiming at the chest, a ball
            // this big hits the ground 17 blocks short).
            LivingEntity tg = target[0];
            Vec3 aim = tg != null && tg.isAlive() ? tg.position().add(0, Math.max(1, R - 0.4), 0) : goal[0];
            speed[0] = Math.min(1.2, speed[0] + 0.0275);
            Vec3 cur = vel[0].normalize(), want = aim.subtract(pos[0]);
            want = want.lengthSqr() < 1.0E-6 ? cur : want.normalize();
            double ang = Math.acos(Math.max(-1, Math.min(1, cur.dot(want))));
            double turn = ang > 1.0E-4 ? Math.min(1, 0.075 / ang) : 1;
            Vec3 dir = cur.lerp(want, turn);
            dir = dir.lengthSqr() < 1.0E-6 ? want : dir.normalize();
            vel[0] = dir.scale(speed[0]);
            pos[0] = pos[0].add(vel[0]);
            Vec3 at = pos[0];
            if (ball[0] != null) {
                ball[0].move(at);
            }
            boolean touch = !Combat.inRadius(level, at, R + 0.3, caster).isEmpty();
            // The first half second after the throw it can't burst on the ground (only on someone or a wall ahead).
            boolean ground = t >= 10 && at.y - R < DbCommon.groundY(level, at) - 0.4 || SubCommon.solid(level, at.add(dir.scale(R * 0.8)));
            if (!touch && !ground && t < 180) {
                SubNet.send(level, SubNet.DEATH_BALL, id, at, Vec3.ZERO, R, 0, 0);
                if (t % 10 == 0) {
                    SubCommon.sound(level, at, SoundEvents.FIRE_AMBIENT, 2.5f, 0.4f);
                }
                return false;
            }
            if (ball[0] != null) {
                ball[0].end();
            }
            SubNet.send(level, SubNet.DEATH_BALL, id, at, Vec3.ZERO, R, 0, 1);
            nova(caster, level, at);
            return true;
        });
        return true;
    }

    // How high he floats while the Supernova grows.
    private static final double NOVA_LIFT = 3;

    // Lets him drift down after the float: slow falling until he touches the ground (at most 30 s), no fall damage.
    private static void land(LivingEntity caster) {
        caster.fallDistance = 0;
        if (caster.onGround()) {
            return;
        }
        caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 600, 0, false, false));
        Tasks.add(a -> {
            caster.fallDistance = 0;
            if (a > 2 && (caster.onGround() || caster.isInWater()) || a > 600 || !caster.isAlive()) {
                caster.removeEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
                return true;
            }
            return false;
        });
    }

    private static double easeOut4(double x) {
        double u = 1 - Math.max(0, Math.min(1, x));
        return 1 - u * u * u * u;
    }

    // The dome's radius at a tick of the landing (same curve as the client's NovaRenderer.radius).
    private static double novaRadius(int t) {
        double s = t / 20.0;
        return 1 + (NOVA_R - 2.5) * easeOut4(s / 1.5) + 1.5 * Math.max(0, Math.min(1, (s - 1.5) / 6));
    }

    private static void nova(LivingEntity caster, ServerLevel level, Vec3 at) {
        double ground = DbCommon.groundY(level, at);
        Vec3 c = new Vec3(at.x, ground, at.z);
        SubNet.send(level, SubNet.SUPERNOVA, 0, c, new Vec3(ground, 0, 0), 1, 0, 0);
        DbCommon.screen(level, c, 11);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.4f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.5f);
        Tasks.later(5, () -> SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.6f));
        Tasks.later(9, () -> SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.5f));
        boolean breaks = com.starkinhon.battlegroundsmod.combat.BlockRules.canBreak(level);
        List<List<double[]>> cracks = breaks ? cracks() : List.of();
        int[] carved = new int[cracks.size()];
        double[] burnt = {11};
        Set<Long> done = new java.util.HashSet<>();
        Set<Integer> hit = DbCommon.once();
        Tasks.add(t -> {
            double R = novaRadius(t), H = R * 0.5, life = t / (double) NOVA_TICKS;
            // The hitbox: the dome's own shape. The fire's front hits once (knockback falling off with distance), then
            // inside it only burns (the hurt's knockback taken back, like the domain's cuts and Kokushibo's moons).
            if (life < 0.85) {
                for (LivingEntity e : Combat.inRadius(level, c.add(0, H * 0.5, 0), R + 2, caster)) {
                    Vec3 ec = Combat.center(e);
                    double dh = Math.hypot(ec.x - c.x, ec.z - c.z), yy = Math.max(0, ec.y - ground);
                    if ((dh / R) * (dh / R) + (yy / H) * (yy / H) >= 1) {
                        continue;
                    }
                    if (hit.add(e.getId())) {
                        double k = 1 - 0.55 * Math.min(1, dh / (NOVA_R + 4));
                        Combat.hurtMagic(caster, e, (float) (40 * k));
                        Combat.pushFrom(e, c, 0.8 + 1.6 * k, 0.45 + 0.6 * k);
                        Stun.apply(e, 20);
                        DbCommon.hit(level, ec, 1.2f, Db.ORANGE);
                    } else if (t % 10 == 0) {
                        Vec3 motion = e.getDeltaMovement();
                        Combat.hurtMagic(caster, e, 2.5f);
                        e.setDeltaMovement(motion);
                        e.hurtMarked = true;
                    }
                }
            }
            if (breaks) {
                // Every block change here skips the neighbour updates (flag 2): thousands of them in a few ticks were the
                // lag Stark saw (2026-11-09), together with dozens of falling blocks at once.
                if (t < 6) {
                    // The crater, dug in 6 slices (a deep bowl with its rim raised, like the preview).
                    crater(level, c, t, done);
                }
                // The cracks open behind the front (41 blocks in 2.6 s), magma at the bottom, their lips pushed up.
                double front = 41 * (1 - Math.pow(1 - Math.min(1, t / 52.0), 3));
                for (int i = 0; i < cracks.size(); i++) {
                    List<double[]> path = cracks.get(i);
                    while (carved[i] < path.size() && path.get(carved[i])[2] < front) {
                        double[] q = path.get(carved[i]++);
                        crack(level, c, q, done);
                    }
                }
                // The ground scorched where the fire went, ring by ring as the dome grows.
                double reach = Math.min(R, NOVA_R) * 0.97;
                if (reach > burnt[0] + 0.5) {
                    scorch(level, c, burnt[0], reach, done);
                    burnt[0] = reach;
                }
            }
            if (t == 2 || t == 18 || t == 34) {
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.35f);
            }
            if (t % 20 == 0 && t < 160) {
                SubCommon.sound(level, c, SoundEvents.FIRE_AMBIENT, 4f, 0.5f);
            }
            return t >= NOVA_TICKS;
        });
    }

    private static final int QUIET = 2 | 16;

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static double hash(int x, int z, int k) {
        long h = x * 374761393L + z * 668265263L + k * 1442695041L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    // One slice (of 6) of the crater: a bowl 7 deep at the middle and 10.5 wide, its rim raised 1-2 blocks; a few of
    // its blocks fly out (each its own push), the rest just go.
    private static void crater(ServerLevel level, Vec3 c, int slice, Set<Long> done) {
        int cx = Mth.floor(c.x), cz = Mth.floor(c.z);
        double rc = 10.5;
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                if (Math.floorMod(dx * 7 + dz * 13, 6) != slice) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                double r = Math.hypot(dx + 0.5 - (c.x - cx), dz + 0.5 - (c.z - cz));
                BlockPos top = top(level, c, x, z);
                if (top == null) {
                    continue;
                }
                if (r < rc) {
                    done.add(key(x, z));
                    int depth = (int) Math.round(7 * (1 - r * r / (rc * rc)) * (0.8 + 0.4 * hash(x, z, 1))) + 1;
                    for (int d = 0; d < depth; d++) {
                        BlockPos p = top.below(d);
                        BlockState st = level.getBlockState(p);
                        if (!breakable(level, p, st)) {
                            break;
                        }
                        if (d == 0 && hash(x, z, 2) < 0.06) {
                            fly(level, p, st, c);
                        } else {
                            com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, p, QUIET);
                        }
                    }
                } else if (r < rc + 3.2) {
                    // the rim: the dug ground piled up round the hole
                    double k = 1 - Math.abs(r - (rc + 1.3)) / 1.9;
                    int h = (int) Math.round(Math.max(0, k) * (0.6 + 1.6 * hash(x, z, 3)));
                    BlockState pile = hash(x, z, 4) < 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    for (int d = 1; d <= h; d++) {
                        if (level.getBlockState(top.above(d)).canBeReplaced()) {
                            level.setBlock(top.above(d), pile, QUIET);
                        }
                    }
                }
            }
        }
    }

    private static void fly(ServerLevel level, BlockPos p, BlockState st, Vec3 c) {
        FallingBlockEntity block = FallingBlockEntity.fall(level, p, st);
        block.dropItem = false;
        Vec3 out = SubCommon.flat(new Vec3(p.getX() + 0.5 - c.x, 0, p.getZ() + 0.5 - c.z));
        double k = 0.4 + SubCommon.RANDOM.nextDouble() * 1.1;
        block.setDeltaMovement(out.scale(0.5 * k).add(0, 0.6 + 0.6 * SubCommon.RANDOM.nextDouble(), 0));
        block.hurtMarked = true;
    }

    // The cracks: 10-13 crooked lines from the crater's rim out to 36-41 blocks, each point {x, z, distance, depth,
    // width, angle} relative to the impact: 3 wide near the crater, 2 by the middle, 1 at the ends.
    private static List<List<double[]>> cracks() {
        List<List<double[]>> out = new ArrayList<>();
        int n = 10 + SubCommon.RANDOM.nextInt(4);
        for (int i = 0; i < n; i++) {
            List<double[]> path = new ArrayList<>();
            double a = Math.PI * 2 * i / n + (SubCommon.RANDOM.nextDouble() - 0.5) * 0.4, bend = 0;
            double end = 36 + SubCommon.RANDOM.nextDouble() * 5;
            for (double r = 9.5; r < end; r += 0.35) {
                bend += (SubCommon.RANDOM.nextDouble() - 0.5) * 0.03;
                bend *= 0.97;
                a += bend;
                int depth = 2 + SubCommon.RANDOM.nextInt(r < 20 ? 4 : 2);
                int width = r < 20 ? 3 : r < 30 ? 2 : 1;
                path.add(new double[] {Math.cos(a) * r, Math.sin(a) * r, r, depth, width, a});
            }
            out.add(path);
        }
        return out;
    }

    // The top block column at (x, z) near the impact's ground, or null.
    private static BlockPos top(ServerLevel level, Vec3 c, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return Math.abs(y - c.y) > 6 ? null : new BlockPos(x, y, z);
    }

    private static boolean breakable(ServerLevel level, BlockPos p, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && !com.starkinhon.battlegroundsmod.combat.BlockRules.keeps(st) && st.getDestroySpeed(level, p) >= 0;
    }

    // One point of a crack: the columns across it dug down (magma at the bottom, or in the last dug block when the one
    // under it can't break), and the lips on both sides pushed up a block here and there.
    public static void crack(ServerLevel level, Vec3 c, double[] q, Set<Long> done) {
        double a = q[5];
        double sx = -Math.sin(a), sz = Math.cos(a);
        int width = (int) q[4];
        for (int w = 0; w < width; w++) {
            double off = w - (width - 1) / 2.0;
            int x = Mth.floor(c.x + q[0] + sx * off), z = Mth.floor(c.z + q[1] + sz * off);
            if (!done.add(key(x, z))) {
                continue;
            }
            BlockPos top = top(level, c, x, z);
            if (top == null) {
                continue;
            }
            int depth = (int) q[3];
            BlockPos last = null;
            int d = 0;
            for (; d < depth; d++) {
                BlockPos p = top.below(d);
                if (!breakable(level, p, level.getBlockState(p))) {
                    break;
                }
                com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, p, QUIET);
                last = p;
            }
            BlockPos bottom = top.below(d);
            if (breakable(level, bottom, level.getBlockState(bottom))) {
                level.setBlock(bottom, Blocks.MAGMA_BLOCK.defaultBlockState(), QUIET);
            } else if (last != null) {
                level.setBlock(last, Blocks.MAGMA_BLOCK.defaultBlockState(), QUIET);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            double off = side * ((width + 1) / 2.0);
            int x = Mth.floor(c.x + q[0] + sx * off), z = Mth.floor(c.z + q[1] + sz * off);
            if (done.contains(key(x, z)) || hash(x, z, 5) > 0.4) {
                continue;
            }
            done.add(key(x, z));
            BlockPos top = top(level, c, x, z);
            if (top != null && level.getBlockState(top.above()).canBeReplaced()) {
                BlockState st = level.getBlockState(top);
                if (breakable(level, top, st)) {
                    level.setBlock(top.above(), st, QUIET);
                }
            }
        }
    }

    // The scorched ring between r0 and r1: the top blocks turned to coarse dirt, blackstone, basalt, a reddish bit of
    // netherrack and a few embers of magma (fewer toward the edge), the grass and flowers on top burnt away.
    private static void scorch(ServerLevel level, Vec3 c, double r0, double r1, Set<Long> done) {
        int cx = Mth.floor(c.x), cz = Mth.floor(c.z), m = (int) Math.ceil(r1) + 1;
        for (int dx = -m; dx <= m; dx++) {
            for (int dz = -m; dz <= m; dz++) {
                double r = Math.hypot(dx, dz);
                if (r < r0 || r >= r1) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                if (done.contains(key(x, z)) || hash(x, z, 6) > 1 - Math.max(0, (r - 28) / 9)) {
                    continue;
                }
                BlockPos top = top(level, c, x, z);
                if (top == null) {
                    continue;
                }
                BlockState st = level.getBlockState(top);
                if (!(st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(net.minecraft.tags.BlockTags.SAND) || st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                        || st.is(Blocks.GRAVEL))) {
                    continue;
                }
                double h = hash(x, z, 7);
                BlockState burnt = h < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : h < 0.6 ? Blocks.BLACKSTONE.defaultBlockState()
                        : h < 0.75 ? Blocks.BASALT.defaultBlockState() : h < 0.87 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                        : h < 0.95 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                level.setBlock(top, burnt, QUIET);
                BlockState over = level.getBlockState(top.above());
                if (!over.isAir() && over.canBeReplaced()) {
                    level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), QUIET);
                }
            }
        }
    }
}
