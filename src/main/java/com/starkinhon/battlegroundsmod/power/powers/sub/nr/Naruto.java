package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModEntities;
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

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Naruto (SubAlma of Naruto, tier 6), the Visualizador's Naruto tab.
// Z Rasengan: the chakra sphere (the Azul's glass, style 4) forms in the palm, he runs 7 blocks and drives it into the
//   target: it grinds for half a second and launches them ~16 blocks spinning like a drill. With clones alive it is the
//   Oodama Rasengan (twice as big, pushes harder).
// X Kage Bunshin: cross seal, three clones of 1 life appear in smoke and fight on their own. X again: he swaps places with
//   the clone closest to the target and punches from behind.
// C Uzumaki Naruto Rendan: four clones appear round the target and kick it up (U, ZU, MA, KI), he jumps over and comes
//   down with his heel: the target hits the ground.
// V Sage Mode: he sits and meditates 4 s (a hit breaks it), nature energy flows in, orange around the eyes. In the mode
//   (30 s) every punch reaches 2 blocks farther and hits harder (Kawazu Kumite). V again: the Rasenshuriken, thrown; at the
//   target a giant sphere of wind needles tears up the ground. It ends the mode.
public final class Naruto {
    private static final int SAGE_TICKS = 600;
    private static final int CLONE_TICKS = 400;

    private Naruto() {
    }

    public static Power create() {
        return new Power("naruto", 0xFF8A1A, 1.05f, List.of(
                new Ability(160, 0, Naruto::rasengan),
                new Ability(300, 0, Naruto::kageBunshin),
                new Ability(260, 0, Naruto::rendan),
                new Ability(900, 0, Naruto::sage)));
    }

    // The clones that fight (not the Rendan's puppets) of this caster.
    static List<NarutoClone> clones(LivingEntity caster, ServerLevel level) {
        List<NarutoClone> out = new ArrayList<>();
        for (NarutoClone c : level.getEntitiesOfClass(NarutoClone.class, new AABB(caster.blockPosition()).inflate(48))) {
            if (!c.isNoAi() && c.owner().map(caster.getUUID()::equals).orElse(false)) {
                out.add(c);
            }
        }
        return out;
    }

    static NarutoClone spawnClone(LivingEntity caster, ServerLevel level, Vec3 at, float yaw, int ticks, boolean puppet) {
        NarutoClone c = ModEntities.NARUTO_CLONE.get().create(level);
        if (c == null) {
            return null;
        }
        c.moveTo(at.x, at.y, at.z, yaw, 0);
        c.copy(caster, ticks, puppet);
        c.setYHeadRot(yaw);
        c.setYBodyRot(yaw);
        level.addFreshEntity(c);
        SubNet.send(level, SubNet.POOF, 0, at.add(0, 0.9, 0), Vec3.ZERO, 1, 0, 0);
        SubCommon.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.5f);
        return c;
    }

    // A point beside the caster: x to his right, z ahead.
    private static Vec3 local(LivingEntity caster, double x, double z) {
        Vec3 f = SubCommon.flat(caster.getLookAngle());
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        return caster.position().add(r.scale(x)).add(f.scale(z));
    }

    // ---------- Z: Rasengan ----------

    private static boolean rasengan(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        boolean big = !clones(caster, level).isEmpty();
        float r = big ? 0.42f : 0.21f;
        int form = 40;
        int dashEnd = 50;
        SubCommon.pose(caster, "nr.molda");
        SubCommon.busy(caster, form + 4);
        if (big) {
            // A clone helps mold it: the Oodama Rasengan.
            SubCommon.sound(level, caster.position(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.7f);
        }
        LivingEntity[] hit = {null};
        int[] contact = {-1};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.RASENGAN, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            Vec3 look = caster.getLookAngle();
            Vec3 hand = DbCommon.hand(caster);
            if (age < form) {
                float k = SubCommon.smooth((age - 6) / 28f);
                SubNet.send(level, SubNet.RASENGAN, id, hand.add(0, 0.22, 0).add(look.scale(0.05)), Vec3.ZERO, (0.04f + 0.17f * k) * r / 0.21f,
                        SubCommon.smooth((age - 6) / 6f), 2);
                if (age == 6 || age == 20) {
                    SubCommon.sound(level, hand, SoundEvents.BREEZE_CHARGE, 0.9f, 1.4f + age * 0.02f);
                }
                if (age % 6 == 0) {
                    SubCommon.sound(level, hand, SoundEvents.ELYTRA_FLYING, 0.25f, 2f);
                }
                return false;
            }
            if (contact[0] < 0) {
                if (age == form) {
                    SubCommon.pose(caster, "nr.investida");
                    SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                }
                Vec3 ball = hand.add(look.scale(0.18)).add(0, 0.05, 0);
                SubNet.send(level, SubNet.RASENGAN, id, ball, Vec3.ZERO, r, 1, 0);
                // Runs 7 blocks with the sphere in front.
                Vec3 run = SubCommon.flat(look).scale(0.7);
                caster.setDeltaMovement(run.x, Math.min(caster.getDeltaMovement().y, 0.05), run.z);
                caster.hurtMarked = true;
                caster.fallDistance = 0;
                double reach = 1.3 + r + NrCommon.extraReach(caster);
                LivingEntity best = null;
                for (LivingEntity t : Combat.inRadius(level, ball.add(look.scale(reach * 0.5)), reach, caster)) {
                    if (best == null || t.distanceTo(caster) < best.distanceTo(caster)) {
                        best = t;
                    }
                }
                if (best != null) {
                    hit[0] = best;
                    contact[0] = age;
                    caster.setDeltaMovement(Vec3.ZERO);
                    caster.hurtMarked = true;
                    Freeze.hold(best, 10);
                    Freeze.hold(caster, 10);
                    SubCommon.busy(caster, 22);
                    DbCommon.screen(level, Combat.center(best), 0);
                    SubCommon.sound(level, best.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.8f);
                    return false;
                }
                if (age >= dashEnd) {
                    SubNet.send(level, SubNet.RASENGAN, id, ball, Vec3.ZERO, r, 1, 1);
                    SubNet.send(level, SubNet.POOF, 0, ball, Vec3.ZERO, 0.6f, 0, 0);
                    SubCommon.warn(caster, "naruto.rasengan_miss");
                    return true;
                }
                return false;
            }
            LivingEntity t = hit[0];
            int a = age - contact[0];
            Vec3 cp = Combat.center(t).add(look.scale(-t.getBbWidth() * 0.5));
            if (a < 10) {
                // Grinds against the chest: several small hits.
                SubNet.send(level, SubNet.RASENGAN, id, cp, look, r + 0.12f * SubCommon.smooth(a / 4f), 1, 0);
                if (a % 2 == 0) {
                    Combat.hurtMagic(caster, t, big ? 2.6f : 1.8f);
                    SubCommon.sound(level, cp, SoundEvents.GRINDSTONE_USE, 1.2f, 1.4f);
                }
                return false;
            }
            SubNet.send(level, SubNet.RASENGAN, id, cp, Vec3.ZERO, 0, 0, 1);
            Freeze.free(t);
            Combat.hurtMagic(caster, t, big ? 13 : 9);
            Stun.apply(t, 30);
            Combat.push(t, look, big ? 2.0 : 1.6, 0.6);
            DbCommon.hit(level, cp, 1.6f, Db.KAME);
            DbCommon.screen(level, cp, 18);
            SubCommon.sound(level, cp, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
            SubCommon.sound(level, cp, SoundEvents.BREEZE_CHARGE, 1.5f, 0.6f);
            drill(level, t, 32);
            return true;
        });
        return true;
    }

    // The spiral of wind behind someone launched, for ticks.
    static void drill(ServerLevel level, LivingEntity t, int ticks) {
        Tasks.add(age -> {
            boolean end = age >= ticks || !t.isAlive();
            SubNet.send(level, SubNet.DRILL, t.getId(), Combat.center(t), Vec3.ZERO, 0.85f, 0, end ? 1 : 0);
            if (end && t.isAlive()) {
                SubNet.send(level, SubNet.DUST, 0, t.position(), Vec3.ZERO, 1.4f, 0, 0);
            }
            return end;
        });
    }

    // ---------- X: Kage Bunshin ----------

    private static boolean kageBunshin(LivingEntity caster, ServerLevel level) {
        List<NarutoClone> alive = clones(caster, level);
        if (!alive.isEmpty()) {
            return swap(caster, level, alive);
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, 20);
        DbCommon.cooldown(caster, 1, 20);
        double[][] homes = {{-1.6, 0.6}, {1.6, 0.6}, {0.7, -1.2}};
        List<NarutoClone> mine = new ArrayList<>();
        boolean[] swapped = {false};
        Tasks.add(age -> {
            if (age < 18) {
                return guard.broken();
            }
            int i = age - 18;
            if (i % 2 == 0 && i / 2 < homes.length) {
                if (guard.broken()) {
                    return true;
                }
                Vec3 at = SubCommon.ground(level, local(caster, homes[i / 2][0], homes[i / 2][1]));
                NarutoClone c = spawnClone(caster, level, at, caster.getYRot(), CLONE_TICKS, false);
                if (c != null) {
                    mine.add(c);
                }
                if (i / 2 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
                }
                return false;
            }
            if (i < 6) {
                return false;
            }
            mine.removeIf(c -> !c.isAlive() || c.isRemoved());
            if (mine.isEmpty() || !caster.isAlive()) {
                // All gone without a swap: now the cooldown runs.
                if (caster.isAlive() && !swapped[0]) {
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            // Mobs swap by themselves once the clones are on the target.
            if (caster instanceof Mob mob && age == 100 && mob.getTarget() != null) {
                swapped[0] = swap(caster, level, mine);
                if (swapped[0]) {
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            return false;
        });
        return false;
    }

    private static boolean swap(LivingEntity caster, ServerLevel level, List<NarutoClone> alive) {
        LivingEntity target = NrCommon.target(caster, 40);
        Vec3 near = target != null ? target.position() : SubCommon.aimPoint(caster, 40);
        NarutoClone best = null;
        for (NarutoClone c : alive) {
            if (best == null || c.distanceToSqr(near) < best.distanceToSqr(near)) {
                best = c;
            }
        }
        if (best == null) {
            return false;
        }
        Vec3 mine = caster.position();
        Vec3 theirs = best.position();
        float yaw = target != null ? NrCommon.yawTo(theirs, target.position()) : best.getYRot();
        SubNet.send(level, SubNet.POOF, 0, mine.add(0, 0.9, 0), Vec3.ZERO, 1, 0, 0);
        SubNet.send(level, SubNet.POOF, 0, theirs.add(0, 0.9, 0), Vec3.ZERO, 1, 0, 0);
        SubCommon.sound(level, mine, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.4f);
        SubCommon.sound(level, theirs, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.4f);
        best.moveTo(mine.x, mine.y, mine.z, caster.getYRot(), 0);
        NrCommon.teleport(caster, theirs, yaw);
        SubCommon.pose(caster, "nr.investida");
        if (target != null && target.distanceTo(caster) < 3.5 + NrCommon.extraReach(caster)) {
            LivingEntity t = target;
            Tasks.later(8, () -> {
                if (!caster.isAlive() || !t.isAlive()) {
                    return;
                }
                Combat.hurt(caster, t, 7);
                Stun.apply(t, 20);
                Combat.push(t, t.position().subtract(caster.position()), 1.4, 0.35);
                DbCommon.hit(level, Combat.center(t), 0.8f, Db.WHITE);
                DbCommon.screen(level, Combat.center(t), 15);
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.9f);
            });
        }
        return true;
    }

    // ---------- C: Uzumaki Naruto Rendan ----------

    private static boolean rendan(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 6);
        if (target == null) {
            SubCommon.warn(caster, "naruto.rendan_no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int[] kicks = {30, 38, 46, 54};
        int jump = 52;
        int heel = 70;
        int land = 76;
        Vec3 base = target.position();
        Vec3 start = caster.position();
        Vec3 face = SubCommon.flat(base.subtract(start));
        Vec3 side = face.cross(new Vec3(0, 1, 0));
        double[][] offs = {{-0.9, -0.5}, {0.9, -0.5}, {-0.9, 0.6}, {0.9, 0.6}};
        NarutoClone[] puppets = new NarutoClone[4];
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, land + 10);
        Stun.apply(target, 30);
        Freeze.hold(target, land + 2, age -> base.add(0, height(age, kicks, heel, land), 0));
        Vec3 top = base.subtract(face.scale(0.4));
        Freeze.hold(caster, land + 14, age -> {
            if (age < jump) {
                return start;
            }
            double k = Math.min(1, (age - jump) / (double) (heel - jump));
            double sy = height(age, kicks, heel, land);
            if (age < heel) {
                Vec3 p = start.lerp(top, SubCommon.smooth((float) k));
                return p.add(0, Math.sin(k * Math.PI * 0.5) * (sy + 2.2), 0);
            }
            if (age < land) {
                double u = (age - heel) / (double) (land - heel);
                return top.add(0, (height(heel, kicks, heel, land) + 2.2) * (1 - u * u) + 1.6 * u * u, 0);
            }
            double u = SubCommon.smooth((age - land) / 14f);
            return top.add(0, 1.6 * (1 - u), 0).lerp(top.subtract(face.scale(1)), u);
        });
        Tasks.add(age -> {
            if (guard.broken() && age < land) {
                Freeze.free(target);
                Freeze.free(caster);
                for (NarutoClone c : puppets) {
                    if (c != null) {
                        c.poof();
                    }
                }
                return true;
            }
            if (age >= 10 && age < 14) {
                int i = age - 10;
                Vec3 at = SubCommon.ground(level, base.add(side.scale(offs[i][0])).add(face.scale(offs[i][1])));
                puppets[i] = spawnClone(caster, level, at, NrCommon.yawTo(at, base), 90, true);
            }
            for (int i = 0; i < 4; i++) {
                if (age == kicks[i] - 3 && puppets[i] != null) {
                    SubCommon.pose(puppets[i], "nr.chute_alto");
                }
                if (age == kicks[i] + 2 && target.isAlive()) {
                    Combat.hurt(caster, target, 2.5f);
                    Stun.apply(target, 12);
                    DbCommon.hit(level, target.position().add(0, 0.6, 0), 0.6f, Db.WHITE);
                    DbCommon.screen(level, target.position(), 4);
                    SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f, 0.9f + i * 0.1f);
                }
            }
            if (age == jump) {
                SubCommon.pose(caster, "nr.salto");
                SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            }
            if (age == heel) {
                SubCommon.pose(caster, "nr.calcanhar");
                DbCommon.hit(level, target.position().add(0, 1.8, 0), 1, Db.WHITE);
            }
            if (age == land) {
                Combat.hurt(caster, target, 11);
                Stun.apply(target, 34);
                SubNet.send(level, SubNet.DUST, 0, base, Vec3.ZERO, 1.3f, 0, 0);
                DbCommon.screen(level, base, 20);
                SubCommon.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
                SubCommon.sound(level, base, SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
            }
            if (age == land + 10) {
                for (NarutoClone c : puppets) {
                    if (c != null) {
                        c.poof();
                    }
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // How high the four kicks have lifted the target, then the fall under the heel.
    private static double height(int age, int[] kicks, int heel, int land) {
        double sy = 0;
        for (int t : kicks) {
            sy += 1.15 * SubCommon.easeOutCubic(Math.max(0, Math.min(1, (age - t - 2) / 6f)));
        }
        if (age >= heel) {
            double k = Math.min(1, (age - heel) / (double) (land - heel));
            sy = sy * (1 - k * k);
        }
        return sy;
    }

    // ---------- V: Sage Mode / Rasenshuriken ----------

    private static boolean sage(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, NrCommon.SAGE)) {
            return rasenshuriken(caster, level);
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int open = 82;
        int burst = 88;
        SubCommon.pose(caster, "nr.meditar");
        SubCommon.setState(caster, NrCommon.MEDITATE, burst);
        SubCommon.clearState(caster, NrCommon.MEDITATE_BROKEN);
        Freeze.holdLookFree(caster, burst);
        DbCommon.cooldown(caster, 3, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_AMBIENT, 1f, 1.4f);
        boolean[] thrown = {false};
        Tasks.add(age -> {
            if (age < burst) {
                boolean broken = SubCommon.hasState(caster, NrCommon.MEDITATE_BROKEN) || !SubCommon.hasState(caster, NrCommon.MEDITATE);
                if (guard.broken() || broken) {
                    SubNet.send(level, SubNet.SAGE_GATHER, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                    Freeze.free(caster);
                    SubCommon.clearState(caster, NrCommon.MEDITATE);
                    if (caster.isAlive()) {
                        SubCommon.pose(caster, "");
                        SubCommon.warn(caster, "naruto.sage_broken");
                        DbCommon.cooldown(caster, 3, 200);
                    }
                    return true;
                }
                SubNet.send(level, SubNet.SAGE_GATHER, caster.getId(), caster.position(), Vec3.ZERO, SubCommon.smooth((age - 8) / 40f), 0, 0);
                if (age % 20 == 10) {
                    SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f + age * 0.01f);
                }
                if (age == open) {
                    SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, SAGE_TICKS + burst - open, 0, 0);
                    SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
                }
                return false;
            }
            if (age == burst) {
                SubNet.send(level, SubNet.SAGE_GATHER, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                SubCommon.clearState(caster, NrCommon.MEDITATE);
                SubCommon.setState(caster, NrCommon.SAGE, SAGE_TICKS);
                SubCommon.pose(caster, "");
                SubNet.send(level, SubNet.SAGE_BURST, 0, caster.position(), Vec3.ZERO, 0, 0, 0);
                DbCommon.screen(level, caster.position(), 21);
                SubCommon.sound(level, caster.position(), SoundEvents.BREEZE_CHARGE, 1.5f, 0.7f);
                for (LivingEntity t : Combat.inRadius(level, Combat.center(caster), 5, caster)) {
                    Combat.pushFrom(t, caster.position(), 1.2, 0.3);
                }
                return false;
            }
            if (!caster.isAlive() || guard.broken()) {
                SubCommon.clearState(caster, NrCommon.SAGE);
                SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            if (!SubCommon.hasState(caster, NrCommon.SAGE)) {
                // The mode ran out (or the Rasenshuriken ended it).
                SubNet.send(level, SubNet.EYES, caster.getId(), caster.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                if (!thrown[0] && !SubCommon.hasState(caster, "nr_rasenshuriken")) {
                    DbCommon.cooldown(caster, 3, 600);
                }
                return true;
            }
            // Mobs throw the Rasenshuriken on their own once in the mode.
            if (caster instanceof Mob mob && age == burst + 60 && mob.getTarget() != null && mob.distanceTo(mob.getTarget()) < 26) {
                thrown[0] = rasenshuriken(caster, level);
            }
            return false;
        });
        return false;
    }

    private static boolean rasenshuriken(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity target = NrCommon.target(caster, 32);
        Vec3 aim = target != null ? Combat.center(target) : SubCommon.aimPoint(caster, 32).add(0, 1, 0);
        int id = SubNet.newId();
        int throwAt = 60;
        int fly = 18;
        SubCommon.setState(caster, "nr_rasenshuriken", throwAt + fly + 70);
        SubCommon.pose(caster, "nr.mao_alto");
        SubCommon.busy(caster, throwAt + 6);
        Vec3[] from = {null};
        Vec3[] to = {aim};
        Tasks.add(age -> {
            if (guard.broken() && age < throwAt) {
                SubNet.send(level, SubNet.RASENSHURIKEN, id, caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            Vec3 right = SubCommon.flat(caster.getLookAngle()).cross(new Vec3(0, 1, 0));
            Vec3 over = caster.position().add(0, caster.getBbHeight() + 0.75, 0).add(right.scale(0.3));
            if (age < throwAt) {
                float k = SubCommon.smooth((age - 18) / 30f);
                SubNet.send(level, SubNet.RASENSHURIKEN, id, over, Vec3.ZERO, Math.max(0.01f, k), 1.2f, 0);
                if (age % 5 == 0) {
                    SubCommon.sound(level, over, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.8f + k * 0.2f);
                    SubCommon.sound(level, over, SoundEvents.ELYTRA_FLYING, 0.3f, 2f);
                }
                if (target != null && target.isAlive()) {
                    to[0] = Combat.center(target);
                }
                return false;
            }
            if (age == throwAt) {
                from[0] = over;
                SubCommon.pose(caster, "db.arremesso");
                SubCommon.sound(level, over, SoundEvents.TRIDENT_THROW.value(), 1.4f, 0.8f);
                SubCommon.clearState(caster, NrCommon.SAGE);
            }
            int a = age - throwAt;
            Vec3 p = from[0].lerp(to[0], SubCommon.easeOutCubic(Math.min(1, a / (float) fly)));
            if (a < fly && !SubCommon.solid(level, p)) {
                SubNet.send(level, SubNet.RASENSHURIKEN, id, p, Vec3.ZERO, 1, 1.2f, 0);
                return false;
            }
            SubNet.send(level, SubNet.RASENSHURIKEN, id, p, Vec3.ZERO, 1, 1.2f, 1);
            explode(caster, level, p);
            return true;
        });
        return true;
    }

    // The sphere of wind needles: it grows to 6.5 blocks, hurts everyone inside every few ticks and holds them spinning in
    // it, and tears up the ground (a crater of ~4.5 blocks, if the gamerule allows).
    private static void explode(LivingEntity caster, ServerLevel level, Vec3 c) {
        int lasts = 58;
        Vec3 ground = SubCommon.ground(level, c);
        SubNet.send(level, SubNet.WIND_SPHERE, 0, c, new Vec3(ground.y, 0, 0), 6.5f, lasts, 0);
        DbCommon.screen(level, c, 5);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.BREEZE_CHARGE, 2f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.6f);
        Tasks.later(4, () -> Agarrao.crater(level, ground, 4.5, 2.6));
        Tasks.add(age -> {
            float grow = SubCommon.easeOutCubic(age / 9f);
            double r = 6.5 * grow;
            if (age % 3 == 0) {
                for (LivingEntity t : Combat.inRadius(level, c, r, caster)) {
                    Combat.hurtMagic(caster, t, 1.7f);
                    Stun.apply(t, 8);
                    Vec3 in = c.subtract(Combat.center(t)).scale(0.05);
                    t.setDeltaMovement(in.x, 0.06, in.z);
                    t.hurtMarked = true;
                }
            }
            if (age % 8 == 0) {
                SubCommon.sound(level, c, SoundEvents.ELYTRA_FLYING, 1.5f, 1.8f);
            }
            if (age >= lasts) {
                for (LivingEntity t : Combat.inRadius(level, c, 7, caster)) {
                    Combat.hurtMagic(caster, t, 6);
                    Combat.pushFrom(t, c, 1.2, 0.5);
                }
                DbCommon.screen(level, c, 18);
                return true;
            }
            return false;
        });
    }
}
