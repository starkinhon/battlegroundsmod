package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Nobara (SubAlma of Jujutsu, tier 8), remade from the approved preview (forja/nobara-kit): the Straw Doll Technique.
// Every nail carries her cursed energy; Stark (2026-11-11) wanted her as one of the mod's biggest stunners.
// Passive, Nail in: a nail stays in someone 10 s, up to 3 each; Hairpin and Resonance hit harder for each one.
// Z Nails: she tosses 3 nails up and hammers all three at once: up to 25 blocks, they stick (in someone or in a wall).
//   Z again, Hairpin: every nail of hers that is stuck bursts (black ink with a blue rim) and stuns 25 ticks.
// X Resonance: the hammer on the straw doll; everyone with her nail in them or her blood on them, up to 60 blocks away,
//   is torn from the inside by barbed black thorns with blue veins: heavy damage and 2 s of stun.
// C Hammer Blow: right on someone touching her: the nail goes into the chest, their blood is hers (Resonance), pushed, 1.5 s.
// B Nail Field: crouched, she drives 7 nails into the ground in a fan (traps, 20 s); whoever steps by one takes it in the foot.
// V Shared Resonance (ult, as against Eso and Kechizu): a nail into her own palm. For 8 s a red thread ties her to everyone
//   with her nail or blood, and every hit she takes comes back to them doubled. Then the hammer again: a giant Resonance
//   on all of them and 3 s of stun.
// Stuns follow the tier rule (JjCommon.tierStun): full on bosses of her tier or weaker, a little less on stronger ones.
public final class Nobara {
    private static final int STUCK_TICKS = 200;
    private static final int TRAP_TICKS = 400;
    private static final int MAX_IN_ONE = 3;
    private static final double SPEED = 2.2;
    private static final double RANGE = 25;
    private static final double REACH = 60;
    private static final int SHARED_TICKS = 160;
    // Her own palm nail: the ult is on.
    public static final String SHARED = "jj_nobara_shared";

    // A nail of hers: flying, stuck in someone (at an offset from their feet), in the world, or lying in the ground as a trap.
    static final class Nail {
        final LivingEntity owner;
        final int id = SubNet.newId();
        Vec3 pos;
        Vec3 dir;
        LivingEntity in;
        Vec3 offset;
        boolean stuck;
        boolean trap;
        boolean gone;
        long until;

        Nail(LivingEntity owner, Vec3 pos, Vec3 dir) {
            this.owner = owner;
            this.pos = pos;
            this.dir = dir;
        }
    }

    private static final List<Nail> NAILS = new ArrayList<>();
    // The ult's links: Nobara -> the ones tied to her (entity id -> times a hit came back, for the thread's pulse).
    private static final Map<UUID, Map<LivingEntity, int[]>> LINKS = new HashMap<>();
    private static boolean reflecting;

    private Nobara() {
    }

    public static Power create() {
        return new Power("nobara", 0xB0582E, 0.95f, List.of(
                new Ability(160, 0, Nobara::nails),
                new Ability(300, 0, Nobara::resonance),
                new Ability(140, 0, Nobara::hammerBlow),
                new Ability(1000, 0, Nobara::shared),
                new Ability(360, 0, Nobara::field)));
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

    private static int nailsIn(LivingEntity owner, LivingEntity e) {
        int k = 0;
        for (Nail n : mine(owner)) {
            if (n.in == e) {
                k++;
            }
        }
        return k;
    }

    // Everyone tied to her: a nail of hers in them or her blood on them, up to 60 blocks.
    private static List<LivingEntity> tied(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> out = new ArrayList<>();
        for (Nail n : mine(caster)) {
            if (n.in != null && n.in.isAlive() && !out.contains(n.in) && n.in.distanceTo(caster) <= REACH) {
                out.add(n.in);
            }
        }
        for (LivingEntity e : Combat.inRadius(level, caster.position(), REACH, caster)) {
            if (SubCommon.hasState(e, JjCommon.NOBARA_BLOOD) && !out.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static void burst(ServerLevel level, Vec3 at, float r, float life, boolean red) {
        SubNet.send(level, SubNet.NB_BURST, 0, at, Vec3.ZERO, r, life, red ? 1 : 0);
    }

    // The tearing thorns on one entity (each nail in it, or the chest when only her blood is): `back` = away from her.
    private static void thorns(ServerLevel level, LivingEntity caster, LivingEntity t, float pow) {
        Vec3 back = SubCommon.flat(t.position().subtract(caster.position()));
        if (back.lengthSqr() < 1.0E-4) {
            back = SubCommon.flat(t.getLookAngle()).scale(-1);
        }
        List<Vec3> at = new ArrayList<>();
        for (Nail n : mine(caster)) {
            if (n.in == t) {
                at.add(n.pos);
            }
        }
        if (at.isEmpty()) {
            at.add(Combat.center(t).add(0, 0.25 * t.getBbHeight(), 0));
        }
        for (int i = 0; i < at.size(); i++) {
            float p = at.size() > 1 ? pow + 0.25f * i : pow;
            SubNet.send(level, SubNet.THORNS, SubNet.newId(), at.get(i), back, p, 0, 0);
        }
    }

    private static void dropNailsIn(LivingEntity caster, LivingEntity t) {
        for (Nail n : mine(caster)) {
            if (n.in == t) {
                n.gone = true;
            }
        }
    }

    // The hammer in her right hand (JjFx draws it): raised now, swung through at `strikeAt`, put away at `end` (ticks).
    private static void hammer(LivingEntity caster, ServerLevel level, int strikeAt, int end) {
        Tasks.add(age -> {
            boolean done = age >= end || !caster.isAlive();
            SubNet.send(level, SubNet.HAMMER, caster.getId(), caster.position(), Vec3.ZERO, age >= strikeAt ? 1 : 0, 0, done ? 1 : 0);
            return done;
        });
    }

    // ---------- Z: Nails / Hairpin ----------

    private static boolean nails(LivingEntity caster, ServerLevel level) {
        if (!mine(caster).isEmpty()) {
            hairpin(caster, level);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.nail_throw");
        hammer(caster, level, 7, 15);
        SubCommon.busy(caster, 12);
        DbCommon.cooldown(caster, 0, 14);
        LivingEntity aimed = JjCommon.target(caster, RANGE);
        // The preview: tossed up 0.36 s, the hammer goes through them 0.06 s later.
        Tasks.later(7, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 hand = DbCommon.hand(caster).add(0, 0.3, 0);
            Vec3 dir = aimed != null && !(caster instanceof Player) ? Combat.center(aimed).subtract(hand).normalize() : caster.getLookAngle();
            burst(level, hand, 0.16f, 0.25f, false);
            SubCommon.sound(level, hand, SoundEvents.ANVIL_LAND, 0.5f, 2f);
            SubCommon.sound(level, hand, SoundEvents.ARROW_SHOOT, 1f, 1.4f);
            int[] done = {0};
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            for (int i = 0; i < 3; i++) {
                double spread = (i - 1) * 0.045;
                Vec3 d = dir.add(side.scale(spread)).add(0, (i == 1 ? 0.03 : -0.02), 0).normalize();
                fly(caster, level, new Nail(caster, hand.add(side.scale((i - 1) * 0.24)), d), () -> {
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

    private static void stickIn(LivingEntity caster, ServerLevel level, Nail n, LivingEntity hit) {
        // Up to 3 in one: the oldest falls out for the new one.
        if (nailsIn(caster, hit) >= MAX_IN_ONE) {
            for (Nail o : mine(caster)) {
                if (o.in == hit) {
                    o.gone = true;
                    break;
                }
            }
        }
        n.stuck = true;
        n.trap = false;
        n.in = hit;
        n.offset = n.pos.subtract(hit.position());
        n.until = level.getGameTime() + STUCK_TICKS;
        SubCommon.setState(hit, JjCommon.NOBARA_BLOOD, STUCK_TICKS);
    }

    private static void fly(LivingEntity caster, ServerLevel level, Nail n, Runnable onDone) {
        double[] ran = {0};
        NAILS.add(n);
        Tasks.add(age -> {
            if (n.gone) {
                SubNet.send(level, SubNet.NAIL, n.id, n.pos, n.dir, n.stuck ? 1 : 0, 0, 1);
                NAILS.remove(n);
                return true;
            }
            if (!n.stuck) {
                Vec3 next = n.pos.add(n.dir.scale(SPEED));
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
                    // The tip in the body's front, at the height it flew at.
                    Vec3 c = Combat.center(hit);
                    double y = Math.max(hit.getY() + 0.25, Math.min(hit.getY() + hit.getBbHeight() * 0.85, n.pos.y));
                    n.pos = new Vec3(c.x, y, c.z).subtract(n.dir.scale(hit.getBbWidth() * 0.35));
                    stickIn(caster, level, n, hit);
                    Combat.hurt(caster, hit, 4);
                    Stun.apply(hit, 5);
                    SubCommon.sound(level, n.pos, SoundEvents.ARROW_HIT, 1f, 1.2f);
                    onDone.run();
                } else if (block.getType() != HitResult.Type.MISS) {
                    n.stuck = true;
                    n.pos = block.getLocation();
                    n.until = level.getGameTime() + STUCK_TICKS;
                    SubCommon.sound(level, n.pos, SoundEvents.ARROW_HIT, 0.8f, 1.6f);
                    onDone.run();
                } else {
                    n.pos = next;
                    ran[0] += SPEED;
                    if (ran[0] >= RANGE) {
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
            } else if (n.trap) {
                trapCheck(caster, level, n);
            }
            if (level.getGameTime() > n.until || !caster.isAlive()) {
                n.gone = true;
                return false;
            }
            SubNet.send(level, SubNet.NAIL, n.id, n.pos, n.dir, 1, n.trap ? 1 : 0, 0);
            // Mobs burst them on their own a second after one is in someone.
            if (!(caster instanceof Player) && n.in != null && level.getGameTime() == n.until - STUCK_TICKS + 20 && !SubCommon.hasState(caster, SHARED)) {
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
        // The preview: one burst after another, 0.07 s apart, each its own size.
        Map<LivingEntity, Integer> took = new LinkedHashMap<>();
        boolean[] screen = {false};
        for (int i = 0; i < nails.size(); i++) {
            Nail n = nails.get(i);
            n.gone = true;
            int delay = 2 + i * 3 / 2;
            Vec3 at = n.pos;
            LivingEntity in = n.in;
            float r = new float[] {0.6f, 0.5f, 0.44f}[i % 3];
            float life = 0.6f + 0.08f * (i % 3);
            Tasks.later(delay, () -> {
                burst(level, at, r, life, false);
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
                List<LivingEntity> hit = new ArrayList<>();
                if (in != null && in.isAlive()) {
                    hit.add(in);
                }
                for (LivingEntity e : Combat.inRadius(level, at, 1.6, caster)) {
                    if (!hit.contains(e)) {
                        hit.add(e);
                    }
                }
                for (LivingEntity e : hit) {
                    Combat.hurtMagic(caster, e, e == in ? 6 : 3);
                    if (!took.containsKey(e)) {
                        took.put(e, 1);
                        Stun.apply(e, JjCommon.tierStun(caster, e, Stun.INTERRUPT_TICKS), true);
                    }
                    if (!screen[0]) {
                        DbCommon.screen(level, Combat.center(e), 35);
                        screen[0] = true;
                    }
                }
            });
        }
    }

    // ---------- X: Resonance ----------

    private static boolean resonance(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> targets = tied(caster, level);
        if (targets.isEmpty()) {
            SubCommon.warn(caster, "nobara.no_link");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        // The preview: doll up 0.6 s, the hammer 0.07 s, the thorns 0.12 s later.
        int hammer = 13;
        SubCommon.pose(caster, "jj.resonance");
        hammer(caster, level, hammer - 2, hammer + 8);
        SubCommon.busy(caster, hammer + 8);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, hammer + 4, 1, false, false));
        SubCommon.sound(level, caster.position(), SoundEvents.GRINDSTONE_USE, 0.6f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.DOLL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubNet.send(level, SubNet.DOLL, caster.getId(), caster.position(), Vec3.ZERO, age >= hammer ? 1 : 0, 0, age >= hammer + 8 ? 1 : 0);
            if (age == hammer) {
                burst(level, DbCommon.handLeft(caster).add(caster.getLookAngle().scale(0.15)), 0.11f, 0.3f, false);
                SubCommon.sound(level, caster.position(), SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
            }
            if (age == hammer + 2) {
                boolean screen = false;
                for (LivingEntity t : tied(caster, level)) {
                    int k = nailsIn(caster, t);
                    float pow = k >= 2 ? 0.6f : 0.5f;
                    thorns(level, caster, t, pow);
                    Vec3 c = Combat.center(t);
                    if (!screen) {
                        DbCommon.screen(level, c, 36);
                        screen = true;
                    }
                    Combat.hurtMagic(caster, t, 16 + 6 * Math.max(0, k - 1));
                    Stun.apply(t, JjCommon.tierStun(caster, t, 40), true);
                    dropNailsIn(caster, t);
                    SubCommon.clearState(t, JjCommon.NOBARA_BLOOD);
                    SubCommon.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
                }
                // It costs her too.
                if (caster.getHealth() > 3) {
                    caster.setHealth(caster.getHealth() - 2);
                }
            }
            return age >= hammer + 8;
        });
        return true;
    }

    // ---------- C: Hammer Blow ----------

    private static boolean hammerBlow(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 3.2);
        if (t == null || t.distanceTo(caster) > 3.2) {
            SubCommon.warn(caster, "nobara.too_far");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.point_blank");
        hammer(caster, level, 4, 12);
        SubCommon.busy(caster, 10);
        // The preview: the nail set on the chest 0.24 s, the hammer 0.06 s.
        Tasks.later(6, () -> {
            if (guard.broken() || !t.isAlive()) {
                return;
            }
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            if (dir.lengthSqr() < 1.0E-4) {
                dir = SubCommon.flat(caster.getLookAngle());
            }
            Vec3 c = Combat.center(t).add(0, 0.2 * t.getBbHeight(), 0);
            Vec3 at = c.subtract(dir.scale(t.getBbWidth() * 0.35));
            Combat.hurt(caster, t, 9);
            Stun.apply(t, JjCommon.tierStun(caster, t, 30), true);
            Combat.push(t, dir, 1.0, 0.2);
            burst(level, at, 0.28f, 0.4f, false);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, at, dir.scale(-1).add(0, 0.2, 0), 0.8f, 0, 0);
            DbCommon.screen(level, at, 37);
            SubCommon.sound(level, at, SoundEvents.ANVIL_LAND, 0.7f, 1.8f);
            SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 1f);
            Nail n = new Nail(caster, at, dir);
            stickIn(caster, level, n, t);
            fly(caster, level, n, () -> {
            });
        });
        return true;
    }

    // ---------- B: Nail Field ----------

    private static boolean field(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.nail_field");
        SubCommon.busy(caster, 10);
        // Old traps of hers come out.
        for (Nail n : NAILS) {
            if (n.owner == caster && n.trap) {
                n.gone = true;
            }
        }
        Tasks.later(6, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 hand = DbCommon.hand(caster);
            Vec3 fwd = SubCommon.flat(caster.getLookAngle());
            if (fwd.lengthSqr() < 1.0E-4) {
                fwd = new Vec3(0, 0, 1);
            }
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            SubCommon.sound(level, hand, SoundEvents.CHAIN_FALL, 1f, 1.4f);
            for (int i = 0; i < 7; i++) {
                double a = -0.85 + 1.7 * i / 6 + (SubCommon.RANDOM.nextDouble() - 0.5) * 0.12;
                double d = 3 + 2.6 * SubCommon.RANDOM.nextDouble();
                Vec3 flat = fwd.scale(Math.cos(a) * d).add(right.scale(Math.sin(a) * d));
                Vec3 land = SubCommon.ground(level, caster.position().add(flat).add(0, 1.5, 0));
                Vec3 tilt = new Vec3((SubCommon.RANDOM.nextDouble() - 0.5) * 0.6, -1, (SubCommon.RANDOM.nextDouble() - 0.5) * 0.6).normalize();
                int delay = 1 + i / 3;
                Tasks.later(6 + delay, () -> {
                    Nail n = new Nail(caster, land.add(0, 0.12, 0), tilt);
                    n.stuck = true;
                    n.trap = true;
                    n.until = level.getGameTime() + TRAP_TICKS;
                    burst(level, land.add(0, 0.05, 0), 0.14f, 0.25f, false);
                    SubCommon.sound(level, land, SoundEvents.ARROW_HIT, 0.5f, 1.8f);
                    fly(caster, level, n, () -> {
                    });
                });
            }
        });
        return true;
    }

    // A trap nail: an enemy's foot close by and it jumps into it.
    private static void trapCheck(LivingEntity caster, ServerLevel level, Nail n) {
        for (LivingEntity e : Combat.inRadius(level, n.pos, 1.4, caster)) {
            Vec3 d = e.position().subtract(n.pos);
            if (Math.hypot(d.x, d.z) < 0.6 + e.getBbWidth() * 0.5 && Math.abs(d.y) < 1.2) {
                Vec3 dir = SubCommon.flat(e.position().subtract(caster.position()));
                n.dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0.2, 1).normalize() : dir.normalize().add(0, 0.2, 0).normalize();
                n.pos = e.position().add(0, 0.25 * e.getBbHeight() / 1.8, 0).subtract(n.dir.scale(e.getBbWidth() * 0.35));
                stickIn(caster, level, n, e);
                burst(level, n.pos, 0.2f, 0.3f, false);
                Combat.hurt(caster, e, 3);
                Stun.apply(e, 5);
                SubCommon.sound(level, n.pos, SoundEvents.ARROW_HIT, 1f, 1.1f);
                return;
            }
        }
    }

    // ---------- V: Shared Resonance ----------

    private static boolean shared(LivingEntity caster, ServerLevel level) {
        if (tied(caster, level).isEmpty()) {
            SubCommon.warn(caster, "nobara.no_link");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.nail_palm");
        SubCommon.busy(caster, 14);
        hammer(caster, level, 9, 17);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 14, 1, false, false));
        Tasks.later(11, () -> {
            if (guard.broken()) {
                return;
            }
            Vec3 palm = DbCommon.handLeft(caster);
            if (caster.getHealth() > 4) {
                caster.setHealth(caster.getHealth() - 3);
            }
            burst(level, palm, 0.14f, 0.4f, true);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, palm, new Vec3(0, 0.6, 0).add(caster.getLookAngle().scale(0.4)), 0.6f, 0, 0);
            SubCommon.sound(level, palm, SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
            SubCommon.sound(level, palm, SoundEvents.PLAYER_HURT, 1f, 1.2f);
            SubCommon.setState(caster, SHARED, SHARED_TICKS + 20);
            Map<LivingEntity, int[]> links = new LinkedHashMap<>();
            LINKS.put(caster.getUUID(), links);
            Tasks.add(age -> {
                boolean over = !caster.isAlive() || caster.isRemoved() || !SubCommon.hasState(caster, SHARED);
                Vec3 p = DbCommon.handLeft(caster);
                SubNet.send(level, SubNet.PALM_NAIL, caster.getId(), p, Vec3.ZERO, 0, 0, over || age >= SHARED_TICKS ? 1 : 0);
                // Anyone newly tied joins; the gone ones drop.
                for (LivingEntity e : tied(caster, level)) {
                    links.putIfAbsent(e, new int[] {0});
                }
                links.keySet().removeIf(e -> {
                    if (!e.isAlive() || e.isRemoved() || e.distanceTo(caster) > REACH) {
                        SubNet.send(level, SubNet.NB_LINK, e.getId(), p, Vec3.ZERO, 0, 0, 1);
                        return true;
                    }
                    return false;
                });
                boolean end = over || age >= SHARED_TICKS;
                for (Map.Entry<LivingEntity, int[]> l : links.entrySet()) {
                    SubNet.send(level, SubNet.NB_LINK, l.getKey().getId(), p, anchor(caster, l.getKey()).subtract(p), l.getValue()[0], 0, end ? 1 : 0);
                }
                if (end) {
                    LINKS.remove(caster.getUUID());
                    SubCommon.clearState(caster, SHARED);
                    if (!over) {
                        finale(caster, level, new ArrayList<>(links.keySet()));
                    }
                    return true;
                }
                return false;
            });
        });
        return true;
    }

    // Where the thread ends on someone: her nail in them, else the chest.
    private static Vec3 anchor(LivingEntity caster, LivingEntity e) {
        for (Nail n : mine(caster)) {
            if (n.in == e) {
                return n.pos;
            }
        }
        return Combat.center(e).add(0, 0.25 * e.getBbHeight(), 0);
    }

    // The end of the 8 s: the hammer on her palm again, a giant Resonance on everyone tied, 3 s of stun.
    private static void finale(LivingEntity caster, ServerLevel level, List<LivingEntity> tied) {
        SubCommon.pose(caster, "jj.nail_palm");
        SubCommon.busy(caster, 14);
        hammer(caster, level, 8, 16);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 14, 1, false, false));
        Tasks.later(10, () -> {
            if (!caster.isAlive()) {
                return;
            }
            Vec3 palm = DbCommon.handLeft(caster);
            burst(level, palm, 0.5f, 0.5f, true);
            boolean screen = false;
            for (LivingEntity t : tied) {
                if (!t.isAlive() || t.distanceTo(caster) > REACH) {
                    continue;
                }
                int k = nailsIn(caster, t);
                thorns(level, caster, t, 1.6f);
                if (!screen) {
                    DbCommon.screen(level, Combat.center(t), 36);
                    screen = true;
                }
                Combat.hurtMagic(caster, t, 24 + 8 * k);
                Stun.apply(t, JjCommon.tierStun(caster, t, 60), true);
                dropNailsIn(caster, t);
                SubCommon.clearState(t, JjCommon.NOBARA_BLOOD);
                SubCommon.sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5f, 0.5f);
                SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
            }
            SubCommon.sound(level, palm, SoundEvents.ANVIL_LAND, 1f, 1.2f);
        });
    }

    // Called on every hit she takes (JjCommon): during the ult it comes back doubled to everyone tied, along the thread.
    public static void onHurt(LivingEntity nobara, ServerLevel level, float amount) {
        if (reflecting || !SubCommon.hasState(nobara, SHARED)) {
            return;
        }
        Map<LivingEntity, int[]> links = LINKS.get(nobara.getUUID());
        if (links == null || links.isEmpty()) {
            return;
        }
        float back = Math.min(30, amount * 2);
        for (Map.Entry<LivingEntity, int[]> l : links.entrySet()) {
            l.getValue()[0]++;
            LivingEntity t = l.getKey();
            // The pulse runs the thread in 0.2 s, then it lands.
            Tasks.later(4, () -> {
                if (!t.isAlive()) {
                    return;
                }
                reflecting = true;
                try {
                    Combat.hurtMagic(nobara, t, back);
                } finally {
                    reflecting = false;
                }
                Vec3 at = anchor(nobara, t);
                Vec3 dir = SubCommon.flat(t.position().subtract(nobara.position()));
                SubNet.send(level, SubNet.THORNS, SubNet.newId(), at, dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir, 0.25f, 0, 0);
                Stun.apply(t, 6);
                SubCommon.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.1f);
            });
        }
    }

    static void clear() {
        NAILS.clear();
        LINKS.clear();
    }
}
