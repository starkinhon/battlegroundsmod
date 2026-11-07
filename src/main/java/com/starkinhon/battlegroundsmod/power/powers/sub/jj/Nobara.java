package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Nobara (SubAlma of Jujutsu, tier 8), the Visualizador's Nobara tab: Straw Doll Technique.
// Z Nails: three nails hammered off her hand fly up to 20 blocks and stay where they stick (in someone or in a wall) for
//   10 s. Press again (Hairpin): every one of them bursts, a short stun on whoever has one in them.
// X Resonance: she hammers a nail into the straw doll and whoever has her nail in them, or her blood on them, feels it
//   wherever they are (up to 60 blocks): heavy damage and 2 s of stun. It costs her a little blood too.
// C Point-blank Nail: a nail hammered straight into whoever is touching her: a push, a second of stun, and the nail stays.
public final class Nobara {
    private static final int STUCK_TICKS = 200;

    // A nail of hers: flying, stuck in someone (at an offset from their feet) or in the world.
    static final class Nail {
        final LivingEntity owner;
        final int id = SubNet.newId();
        Vec3 pos;
        Vec3 dir;
        LivingEntity in;
        Vec3 offset;
        boolean stuck;
        boolean gone;
        long until;

        Nail(LivingEntity owner, Vec3 pos, Vec3 dir) {
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
        }
    }

    private static final List<Nail> NAILS = new ArrayList<>();

    private Nobara() {
    }

    public static Power create() {
        return new Power("nobara", 0xB0582E, 0.95f, List.of(
                new Ability(160, 0, Nobara::nails),
                new Ability(300, 0, Nobara::resonance),
                new Ability(120, 0, Nobara::pointBlank),
                new Ability(1000, 0, Nobara::ultMissing)));
    }

    private static List<Nail> mine(LivingEntity owner) {
        List<Nail> out = new ArrayList<>();
        for (Nail n : NAILS) {
            if (n.owner == owner && n.stuck && !n.gone) {
                out.add(n);
            }
        }
        return out;
    }

    // ---------- Z: Nails / Hairpin ----------

    private static boolean nails(LivingEntity caster, ServerLevel level) {
        if (!mine(caster).isEmpty()) {
            hairpin(caster, level);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.nail_throw");
        SubCommon.busy(caster, 10);
        DbCommon.cooldown(caster, 0, 14);
        LivingEntity aimed = JjCommon.target(caster, 20);
        Tasks.later(5, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 hand = DbCommon.hand(caster);
            Vec3 dir = aimed != null && !(caster instanceof Player) ? Combat.center(aimed).subtract(hand).normalize() : caster.getLookAngle();
            SubCommon.sound(level, hand, SoundEvents.ANVIL_LAND, 0.5f, 2f);
            SubCommon.sound(level, hand, SoundEvents.ARROW_SHOOT, 1f, 1.4f);
            boolean[] anyHit = {false};
            int[] done = {0};
            for (int i = 0; i < 3; i++) {
                double spread = (i - 1) * 0.07;
                Vec3 side = dir.cross(new Vec3(0, 1, 0));
                side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 d = dir.add(side.scale(spread)).add(0, (i == 1 ? 0.03 : -0.02), 0).normalize();
                fly(caster, level, new Nail(caster, hand, d), () -> anyHit[0] = true, () -> {
                    done[0]++;
                    if (done[0] == 3 && mine(caster).isEmpty()) {
                        SubCommon.warn(caster, "nobara.nails_miss");
                        DbCommon.cooldown(caster, 0, 60);
                    }
                });
            }
        });
        return false;
    }

    private static void fly(LivingEntity caster, ServerLevel level, Nail n, Runnable onHit, Runnable onDone) {
        double speed = 2.2;
        double range = 20;
        double[] ran = {0};
        NAILS.add(n);
        Tasks.add(age -> {
            if (n.gone) {
                SubNet.send(level, SubNet.NAIL, n.id, n.pos, n.dir, n.stuck ? 1 : 0, 0, 1);
                NAILS.remove(n);
                return true;
            }
            if (!n.stuck) {
                Vec3 next = n.pos.add(n.dir.scale(speed));
                LivingEntity hit = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : Combat.alongLine(level, n.pos, next, 0.5, caster)) {
                    double d = Combat.center(e).distanceTo(n.pos);
                    if (d < best) {
                        best = d;
                        hit = e;
                    }
                }
                HitResult block = level.clip(new ClipContext(n.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (hit != null) {
                    n.stuck = true;
                    n.in = hit;
                    n.pos = Combat.center(hit).subtract(n.dir.scale(hit.getBbWidth() * 0.4));
                    n.offset = n.pos.subtract(hit.position());
                    n.until = level.getGameTime() + STUCK_TICKS;
                    Combat.hurt(caster, hit, 3);
                    Stun.apply(hit, 5);
                    SubCommon.setState(hit, JjCommon.NOBARA_BLOOD, 200);
                    SubCommon.sound(level, n.pos, SoundEvents.ARROW_HIT, 1f, 1.2f);
                    onHit.run();
                    onDone.run();
                } else if (block.getType() != HitResult.Type.MISS) {
                    n.stuck = true;
                    n.pos = block.getLocation();
                    n.until = level.getGameTime() + STUCK_TICKS;
                    SubCommon.sound(level, n.pos, SoundEvents.ARROW_HIT, 0.8f, 1.6f);
                    onDone.run();
                } else {
                    n.pos = next;
                    ran[0] += speed;
                    if (ran[0] >= range) {
                        n.gone = true;
                        onDone.run();
                    }
                }
                SubNet.send(level, SubNet.NAIL, n.id, n.pos, n.dir, n.stuck ? 1 : 0, 0, n.gone ? 1 : 0);
                if (n.gone) {
                    NAILS.remove(n);
                    return true;
                }
                return false;
            }
            if (n.in != null) {
                if (!n.in.isAlive() || n.in.isRemoved()) {
                    n.gone = true;
                    return false;
                }
                n.pos = n.in.position().add(n.offset);
            }
            if (level.getGameTime() > n.until || !caster.isAlive()) {
                n.gone = true;
                if (mine(caster).isEmpty()) {
                    DbCommon.cooldown(caster, 0, 160);
                }
                return false;
            }
            SubNet.send(level, SubNet.NAIL, n.id, n.pos, n.dir, 1, 0, 0);
            // Mobs burst them on their own once one is in someone.
            if (!(caster instanceof Player) && n.in != null && level.getGameTime() == n.until - STUCK_TICKS + 20) {
                hairpin(caster, level);
            }
            return false;
        });
    }

    private static void hairpin(LivingEntity caster, ServerLevel level) {
        List<Nail> nails = mine(caster);
        if (nails.isEmpty()) {
            return;
        }
        SubCommon.pose(caster, "jj.hairpin");
        SubCommon.busy(caster, 8);
        DbCommon.cooldown(caster, 0, 160);
        SubCommon.sound(level, caster.position(), SoundEvents.ANVIL_LAND, 0.4f, 2f);
        boolean screen = false;
        for (Nail n : nails) {
            n.gone = true;
            SubNet.send(level, SubNet.RESONANCE, 0, n.pos, n.dir.scale(-1), 0.7f, 0, 0);
            SubCommon.sound(level, n.pos, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
            List<LivingEntity> hit = new ArrayList<>();
            if (n.in != null && n.in.isAlive()) {
                hit.add(n.in);
            }
            for (LivingEntity e : Combat.inRadius(level, n.pos, 1.6, caster)) {
                if (!hit.contains(e)) {
                    hit.add(e);
                }
            }
            for (LivingEntity e : hit) {
                Combat.hurtMagic(caster, e, e == n.in ? 6 : 3);
                Stun.apply(e, JjCommon.control(caster, e, 10));
                if (!screen) {
                    DbCommon.screen(level, Combat.center(e), 35);
                    screen = true;
                }
            }
        }
    }

    // ---------- X: Resonance ----------

    private static boolean resonance(LivingEntity caster, ServerLevel level) {
        LivingEntity target = null;
        Nail used = null;
        for (Nail n : mine(caster)) {
            if (n.in != null && n.in.isAlive() && n.in.distanceTo(caster) <= 60) {
                target = n.in;
                used = n;
                break;
            }
        }
        if (target == null) {
            LivingEntity aimed = JjCommon.target(caster, 60);
            if (aimed != null && SubCommon.hasState(aimed, JjCommon.NOBARA_BLOOD)) {
                target = aimed;
            }
        }
        if (target == null) {
            double best = 60;
            for (LivingEntity e : Combat.inRadius(level, caster.position(), 60, caster)) {
                if (SubCommon.hasState(e, JjCommon.NOBARA_BLOOD) && e.distanceTo(caster) < best) {
                    best = e.distanceTo(caster);
                    target = e;
                }
            }
        }
        if (target == null) {
            SubCommon.warn(caster, "nobara.no_link");
            return false;
        }
        LivingEntity t = target;
        Nail nail = used;
        SubCommon.Guard guard = SubCommon.guard(caster);
        int hammer = 12;
        SubCommon.pose(caster, "jj.resonance");
        SubCommon.busy(caster, hammer + 8);
        SubCommon.sound(level, caster.position(), SoundEvents.GRINDSTONE_USE, 0.6f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                SubNet.send(level, SubNet.DOLL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubNet.send(level, SubNet.DOLL, caster.getId(), caster.position(), Vec3.ZERO, age >= hammer ? 1 : 0, 0, age >= hammer + 8 ? 1 : 0);
            if (age < hammer) {
                return false;
            }
            if (age == hammer) {
                Vec3 doll = DbCommon.handLeft(caster);
                Vec3 c = Combat.center(t);
                SubNet.send(level, SubNet.RESO_LINE, 0, doll, c.subtract(doll), 0, 0, 0);
                SubNet.send(level, SubNet.RESONANCE, 0, c, new Vec3(0, 1, 0), 1.4f, 0, 0);
                DbCommon.screen(level, c, 36);
                Combat.hurtMagic(caster, t, 16);
                Stun.apply(t, JjCommon.control(caster, t, 40));
                if (nail != null) {
                    nail.gone = true;
                }
                SubCommon.clearState(t, JjCommon.NOBARA_BLOOD);
                // It costs her too.
                if (caster.getHealth() > 3) {
                    caster.setHealth(caster.getHealth() - 2);
                }
                SubCommon.sound(level, caster.position(), SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
                SubCommon.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
            }
            return age >= hammer + 8;
        });
        return true;
    }

    // ---------- C: Point-blank Nail ----------

    private static boolean pointBlank(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 3);
        if (t == null || t.distanceTo(caster) > 3) {
            SubCommon.warn(caster, "nobara.too_far");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.point_blank");
        SubCommon.busy(caster, 10);
        Tasks.later(4, () -> {
            if (guard.broken() || !t.isAlive()) {
                return;
            }
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            Vec3 c = Combat.center(t);
            Combat.hurt(caster, t, 9);
            Stun.apply(t, JjCommon.control(caster, t, 20));
            Combat.push(t, dir, 1.0, 0.2);
            SubNet.send(level, SubNet.RESONANCE, 0, c, dir, 0.9f, 0, 0);
            DbCommon.screen(level, c, 37);
            SubCommon.sound(level, c, SoundEvents.ANVIL_LAND, 0.7f, 1.8f);
            SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 1f);
            // The nail stays in.
            Nail n = new Nail(caster, c.subtract(dir.scale(t.getBbWidth() * 0.4)), dir);
            n.stuck = true;
            n.in = t;
            n.offset = n.pos.subtract(t.position());
            n.until = level.getGameTime() + STUCK_TICKS;
            SubCommon.setState(t, JjCommon.NOBARA_BLOOD, 200);
            fly(caster, level, n, () -> {
            }, () -> {
            });
        });
        return true;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }

    static void clear() {
        NAILS.clear();
    }
}
