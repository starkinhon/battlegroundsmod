package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Naruto (SubAlma of Naruto, tier 6), the Visualizador's Naruto tab.
// Z Rasengan: the chakra sphere (the Azul's glass, style 4) forms in the palm, he runs 14 blocks and drives it into the
//   target: it grinds for half a second and launches them ~16 blocks spinning like a drill. With clones alive it is the
//   Oodama Rasengan (twice as big, pushes harder).
// X Kage Bunshin: cross seal, three clones of 1 life appear in smoke and fight on their own. X again: he swaps places with
//   the clone closest to the target and punches from behind.
// C Uzumaki Naruto Rendan: four clones appear round the target and kick it up (U, ZU, MA, KI), he jumps over and comes
//   down with his heel: the target hits the ground.
// V Sage Mode: he sits and meditates 4 s (a hit breaks it), nature energy flows in, orange around the eyes. In the mode
//   (30 s) every punch reaches 2 blocks farther and hits harder (Kawazu Kumite). V again: the Rasenshuriken, thrown; at the
//   target a giant sphere of wind needles tears up the ground. It ends the mode.
// B Henge Armadilha (forja/naruto-kit, Stark 2026-11-11): a clone goes out in a puff where you aim (16 blocks) and
//   turns into a rock. Up to 45 s; when an enemy passes by it, it bursts into smoke, Naruto comes out behind them, molds
//   a Rasengan in a blink and drives it in (the Z's grind and launch). Two armed at most.
// Shift+B: the clone turns into a Fuuma Shuriken and you throw it. If the enemy dodges, it turns into Naruto behind
//   them and hits the Rasengan (the trick against Zabuza); if it hits, the Rasengan comes right after the cut.
public final class Naruto {
    private static final int SAGE_TICKS = 600;
    private static final int CLONE_TICKS = 400;
    private static final int TRAP_TICKS = 900;
    private static final int TRAP_MAX = 2;
    private static final Map<UUID, Integer> TRAPS = new HashMap<>();

    private Naruto() {
    }

    public static Power create() {
        return new Power("naruto", 0xFF8A1A, 1.05f, List.of(
                new Ability(160, 0, Naruto::rasengan),
                new Ability(300, 0, Naruto::kageBunshin),
                new Ability(260, 0, Naruto::rendan),
                new Ability(900, 0, Naruto::sage),
                new Ability(360, 0, Naruto::henge)),
                Arrays.asList(null, null, null, null, new Ability(360, 0, Naruto::fuuma)));
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
        // Twice the first port's run (Stark, 2026-11-09: the same reach pass as Dragon Ball).
        int dashEnd = 54;
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
                // Runs with the sphere in front.
                Vec3 run = SubCommon.flat(look).scale(1.3);   // ~18 blocks (Stark, 2026-11-11: reach to fight bosses)
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
            return grind(caster, hit[0], level, id, big, r, look, age - contact[0]);
        });
        return true;
    }

    // The Rasengan grinding into t (a = ticks since the contact): small hits for half a second, then the blow that
    // launches them spinning like a drill. True when it is over. The stun holds a boss too (>= 25 ticks after its half).
    static boolean grind(LivingEntity owner, LivingEntity t, ServerLevel level, int id, boolean big, float r, Vec3 look, int a) {
        Vec3 cp = Combat.center(t).add(look.scale(-t.getBbWidth() * 0.5));
        if (a < 10) {
            // Grinds against the chest: several small hits.
            SubNet.send(level, SubNet.RASENGAN, id, cp, look, r + 0.12f * SubCommon.smooth(a / 4f), 1, 0);
            if (a % 2 == 0) {
                Combat.hurtMagic(owner, t, big ? 2.6f : 1.8f);
                SubCommon.sound(level, cp, SoundEvents.GRINDSTONE_USE, 1.2f, 1.4f);
            }
            return false;
        }
        SubNet.send(level, SubNet.RASENGAN, id, cp, Vec3.ZERO, 0, 0, 1);
        Freeze.free(t);
        Combat.hurtMagic(owner, t, big ? 13 : 9);
        Stun.apply(t, 52);
        Combat.push(t, look, big ? 2.0 : 1.6, 0.6);
        DbCommon.hit(level, cp, 1.6f, Db.KAME);
        DbCommon.screen(level, cp, 18);
        SubCommon.sound(level, cp, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
        SubCommon.sound(level, cp, SoundEvents.BREEZE_CHARGE, 1.5f, 0.6f);
        drill(level, t, 32);
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
        LivingEntity target = NrCommon.target(caster, 10);
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
                if (puppets[i] != null) {
                    // Each clone springs up off its own kick and lands again (no stiff standing: Stark, 2026-11-11).
                    int k0 = kicks[i] - age;
                    double hop = 1.0 + 0.35 * i;
                    Freeze.hold(puppets[i], land + 10 - age, b -> {
                        double u = (b - k0 + 2) / 13.0;
                        return at.add(0, u > 0 && u < 1 ? Math.sin(u * Math.PI) * hop : 0, 0);
                    });
                }
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
                Stun.apply(target, 52);
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
        LivingEntity target = NrCommon.target(caster, 48);
        Vec3 aim = target != null ? Combat.center(target) : SubCommon.aimPoint(caster, 48).add(0, 1, 0);
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

    // The sphere of wind needles: it grows to 9 blocks (it was 6.5; Stark, 2026-11-09, bigger reach and destruction), hurts
    // everyone inside every few ticks and holds them spinning in it, and tears up the ground (a crater of ~7 blocks with
    // clods flying, if the gamerule allows).
    private static final double SPHERE_R = 9;

    private static void explode(LivingEntity caster, ServerLevel level, Vec3 c) {
        int lasts = 58;
        Vec3 ground = SubCommon.ground(level, c);
        SubNet.send(level, SubNet.WIND_SPHERE, 0, c, new Vec3(ground.y, 0, 0), (float) SPHERE_R, lasts, 0);
        DbCommon.screen(level, c, 5);
        SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.BREEZE_CHARGE, 2f, 0.5f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.6f);
        Tasks.later(4, () -> JjCommon.crater(level, ground, 7, 3.4, 40));
        Tasks.add(age -> {
            float grow = SubCommon.easeOutCubic(age / 9f);
            double r = SPHERE_R * grow;
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
                for (LivingEntity t : Combat.inRadius(level, c, SPHERE_R + 1.5, caster)) {
                    Combat.hurtMagic(caster, t, 8);
                    Combat.pushFrom(t, c, 1.2, 0.5);
                }
                DbCommon.screen(level, c, 18);
                return true;
            }
            return false;
        });
    }

    // ---------- B: Henge Armadilha ----------

    private static void trapGone(LivingEntity caster) {
        TRAPS.computeIfPresent(caster.getUUID(), (k, n) -> n > 1 ? n - 1 : null);
    }

    private static boolean henge(LivingEntity caster, ServerLevel level) {
        if (TRAPS.getOrDefault(caster.getUUID(), 0) >= TRAP_MAX) {
            SubCommon.warn(caster, "naruto.henge_full");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 spot = SubCommon.ground(level, SubCommon.aimPoint(caster, 16));
        int id = SubNet.newId();
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, 8);
        TRAPS.merge(caster.getUUID(), 1, Integer::sum);
        Tasks.add(age -> {
            if (age < 6) {
                if (guard.broken()) {
                    trapGone(caster);
                    return true;
                }
                return false;
            }
            if (age == 6) {
                // The clone goes out in a puff and is a rock when the smoke opens.
                SubNet.send(level, SubNet.POOF, 0, spot.add(0, 0.8, 0), Vec3.ZERO, 1.25f, 0, 0);
                SubCommon.sound(level, spot, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.5f);
            }
            if (age >= 6 + TRAP_TICKS || !caster.isAlive() || caster.level() != level) {
                SubNet.send(level, SubNet.HENGE, id, spot, Vec3.ZERO, 0, 1, 1);
                SubNet.send(level, SubNet.POOF, 0, spot.add(0, 0.6, 0), Vec3.ZERO, 0.9f, 0, 0);
                trapGone(caster);
                return true;
            }
            if (age >= 8 && age % 2 == 0) {
                SubNet.send(level, SubNet.HENGE, id, spot, Vec3.ZERO, 0, 1, 0);
            }
            if (age > 16) {
                for (LivingEntity t : Combat.inRadius(level, spot.add(0, 0.8, 0), 3.0, caster)) {
                    if (t instanceof NarutoClone) {
                        continue;
                    }
                    SubNet.send(level, SubNet.HENGE, id, spot, Vec3.ZERO, 0, 1, 1);
                    trapGone(caster);
                    // Behind them: on the side away from where they look (back at the rock if a wall is there).
                    Vec3 at = SubCommon.ground(level, t.position().add(SubCommon.flat(t.getLookAngle()).scale(-1.2)));
                    ambush(caster, level, SubCommon.solid(level, at.add(0, 0.5, 0)) ? spot : at, spot, t, 6);
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    // Shift+B: the clone becomes a Fuuma Shuriken and is thrown (32 blocks).
    private static boolean fuuma(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity lock = NrCommon.target(caster, 32);
        int id = SubNet.newId();
        SubCommon.pose(caster, "nr.selo_cruz");
        SubCommon.busy(caster, 12);
        Vec3[] p = {null};
        Vec3[] v = {null};
        Tasks.add(age -> {
            if (age < 6) {
                return guard.broken();
            }
            if (age == 6) {
                Vec3 side = SubCommon.flat(caster.getLookAngle()).cross(new Vec3(0, 1, 0)).scale(0.6);
                p[0] = caster.getEyePosition().add(side).add(0, -0.35, 0);
                Vec3 aim = lock != null && lock.isAlive() ? Combat.center(lock) : SubCommon.aimPoint(caster, 32).add(0, 1, 0);
                v[0] = aim.subtract(p[0]).normalize().scale(0.95);
                SubNet.send(level, SubNet.POOF, 0, p[0], Vec3.ZERO, 0.9f, 0, 0);
                SubCommon.pose(caster, "db.arremesso");
                SubCommon.sound(level, p[0], SoundEvents.TRIDENT_THROW.value(), 1.3f, 0.7f);
                return false;
            }
            int a = age - 6;
            p[0] = p[0].add(v[0]);
            SubNet.send(level, SubNet.HENGE, id, p[0], v[0], 1, 0.9f, 0);
            if (a % 3 == 0) {
                SubCommon.sound(level, p[0], SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
            }
            for (LivingEntity t : Combat.inRadius(level, p[0], 1.15, caster)) {
                if (t instanceof NarutoClone) {
                    continue;
                }
                // It did not dodge: the cut, and the Rasengan right after it.
                Combat.hurt(caster, t, 6);
                Stun.apply(t, 12);
                DbCommon.hit(level, p[0], 0.7f, Db.WHITE);
                SubCommon.sound(level, p[0], SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                SubNet.send(level, SubNet.HENGE, id, p[0], v[0], 1, 0.9f, 1);
                Vec3 at = SubCommon.ground(level, Combat.center(t).subtract(SubCommon.flat(v[0]).normalize().scale(1.2)));
                ambush(caster, level, at, p[0], t, 4);
                return true;
            }
            if (lock != null && lock.isAlive()) {
                // Dodged: it has gone past the one it was thrown at, close by. It turns into Naruto behind them.
                Vec3 rel = p[0].subtract(Combat.center(lock));
                if (rel.dot(v[0]) > 0 && rel.length() > 1.4 && rel.length() < 3.6) {
                    SubNet.send(level, SubNet.HENGE, id, p[0], v[0], 1, 0.9f, 1);
                    ambush(caster, level, SubCommon.ground(level, p[0]), p[0], lock, 6);
                    return true;
                }
            }
            if (a > 34 || SubCommon.solid(level, p[0]) || !caster.isAlive()) {
                SubNet.send(level, SubNet.HENGE, id, p[0], v[0], 1, 0.9f, 1);
                SubNet.send(level, SubNet.POOF, 0, p[0], Vec3.ZERO, 0.9f, 0, 0);
                SubCommon.warn(caster, "naruto.fuuma_miss");
                return true;
            }
            return false;
        });
        return true;
    }

    // The clone comes out of the smoke at `at` (smoke at `smoke`), molds a Rasengan in `form` ticks and drives it into t.
    // The surprise holds them the moment it takes (they turn too late).
    private static void ambush(LivingEntity owner, ServerLevel level, Vec3 at, Vec3 smoke, LivingEntity t, int form) {
        SubNet.send(level, SubNet.POOF, 0, smoke.add(0, 0.8, 0), Vec3.ZERO, 1.3f, 0, 0);
        SubCommon.sound(level, smoke, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.3f);
        NarutoClone c = spawnClone(owner, level, at, NrCommon.yawTo(at, t.position()), 80, true);
        if (c == null) {
            return;
        }
        SubCommon.pose(c, "nr.molda");
        Stun.apply(t, form + 6);
        int id = SubNet.newId();
        Tasks.add(age -> {
            if (!c.isAlive() || !t.isAlive() || !owner.isAlive()) {
                SubNet.send(level, SubNet.RASENGAN, id, c.position(), Vec3.ZERO, 0, 0, 1);
                c.poof();
                return true;
            }
            Vec3 look = SubCommon.flat(t.position().subtract(c.position()));
            if (look.lengthSqr() < 1.0E-6) {
                look = SubCommon.flat(t.getLookAngle());
            }
            c.setYRot(NrCommon.yawTo(c.position(), t.position()));
            c.setYHeadRot(c.getYRot());
            c.setYBodyRot(c.getYRot());
            if (age < form) {
                float k = SubCommon.smooth(age / (float) form);
                SubNet.send(level, SubNet.RASENGAN, id, DbCommon.hand(c).add(look.scale(0.15)), Vec3.ZERO, 0.04f + 0.17f * k, 1, 2);
                if (age == 0) {
                    SubCommon.sound(level, c.position(), SoundEvents.BREEZE_CHARGE, 0.9f, 1.6f);
                }
                return false;
            }
            if (age == form) {
                SubCommon.pose(c, "nr.investida");
                Freeze.hold(t, 12);
                DbCommon.screen(level, Combat.center(t), 0);
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.8f);
            }
            if (grind(owner, t, level, id, false, 0.21f, look, age - form)) {
                Tasks.later(6, c::poof);
                return true;
            }
            return false;
        });
    }

    // The world closed (ServerReset): nothing of it is kept for the next one (old entities, old game times).
    public static void resetStatics() {
        TRAPS.clear();
    }
}
