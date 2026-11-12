package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Ino (SubAlma of Naruto, tier 8), remade from the approved preview (forja/ino-kit/ino.js, Stark 2026-11-11).
// Passive Leitura Mental: an enemy within 16 blocks starting a skill pulses on her screen only (a seal over his head, a
//   thread to her forehead), so she can dodge or cut it.
// Z Shintenshin: diamond seal, her body drops, the spirit flies straight at 11 blocks/s along the ground (20 blocks; its
//   hitbox is its body). Caught: YOU drive the body (WASD, click hits, space jumps): 10 s on someone weaker, 7 s on the same
//   tier, 5 s on someone stronger, 4 s on a boss. Only the eyes turn purple (no aura, Stark). Z again: Exit, the body she
//   leaves hurts itself. Her body lies there: a hit on it snaps the spirit back and she is dizzy 1 s; so is a miss.
// X Shinranshin: a purple cone from her forehead (12 blocks): mobs attack whoever is closest, players get their controls
//   inverted (walk and look), 7 s (3 s on a boss).
// C Mind Link: threads tie up to 3 enemies for 6 s; half the damage one takes runs along to the others; snaps past 12 blocks.
// B Campo de Flores: seeds thrown where she aims (20 blocks) grow a bed of flowers (2.5 blocks, 30 s, 2 at most). Whoever
//   steps in is covered in pollen and marked 8 s: her next Shintenshin homes in on him.
// V Teatro Mental: kneeling, her mind fills 20 blocks (a dome, the sky turns into her mind); everyone inside gets puppet
//   strings and they beat each other for 10 s (5 s on a boss), then the strings pull them up and each mind bursts (high
//   damage + stun 2 s). A strong hit on her ends it.
public final class Ino {
    private static final double SPIRIT_SPEED = 0.55;
    private static final double SPIRIT_RANGE = 20;
    private static final int SEAL_TICKS = 9;
    private static final int RISE_TICKS = 6;
    private static final int CONTROL_WEAKER = 200, CONTROL_SAME = 140, CONTROL_STRONGER = 100, CONTROL_BOSS = 80;
    private static final int INVERT_TICKS = 140, INVERT_BOSS = 60;
    private static final double READ_RANGE = 16;
    private static final double FIELD_RADIUS = 2.5;
    private static final int FIELD_TICKS = 600, MARK_TICKS = 160;
    private static final double THEATER_RADIUS = 20;
    private static final int THEATER_FIGHT = 200;

    // Spirits out, by caster id.
    private static final Map<Integer, Spirit> SPIRITS = new HashMap<>();
    // What the piloting player presses, by player.
    private static final Map<UUID, Input> INPUTS = new HashMap<>();
    // Marked by the pollen: entity id -> game time it wears off.
    private static final Map<Integer, Long> MARKS = new HashMap<>();
    private static final Map<Integer, List<Field>> FIELDS = new HashMap<>();
    // Leitura Mental: the last warning per (Ino, enemy), so a held skill doesn't flood her.
    private static final Map<Long, Long> READ_LAST = new HashMap<>();

    private record Input(float forward, float strafe, boolean jump, float yaw, float pitch, int attacks, long at) {
    }

    private static final class Spirit {
        LivingEntity host;
        boolean exit;
        boolean controlling;
        int attacksSeen;
        int attackCd;
        boolean hostWasNoAi;
    }

    private static final class Field {
        final int id = SubNet.newId();
        Vec3 pos;
        long until;
        int wilt = -1;
    }

    private Ino() {
    }

    public static Power create() {
        return new Power("ino", 0xB05CFF, 0.9f, List.of(
                new Ability(260, 0, Ino::shintenshin),
                new Ability(260, 0, Ino::shinranshin),
                new Ability(300, 0, Ino::link),
                new Ability(1100, 0, Ino::theater),
                new Ability(240, 0, Ino::flowers)));
    }

    static Vec3 forehead(LivingEntity e) {
        return e.getEyePosition().add(e.getLookAngle().scale(0.25)).add(0, 0.12, 0);
    }

    private static boolean boss(LivingEntity e) {
        return e instanceof SoulBoss b && b.isBoss();
    }

    private static int tier(LivingEntity e) {
        return e.hasData(ModAttachments.POWER) ? Tiers.of(e) : Tiers.HUMANO;
    }

    // How long she drives the body: a lower tier number is stronger.
    static int controlTicks(LivingEntity caster, LivingEntity target) {
        if (boss(target)) {
            return CONTROL_BOSS;
        }
        int c = tier(caster), t = tier(target);
        return t < c ? CONTROL_STRONGER : t == c ? CONTROL_SAME : CONTROL_WEAKER;
    }

    public static boolean piloting(LivingEntity caster) {
        Spirit s = SPIRITS.get(caster.getId());
        return s != null && s.controlling;
    }

    private static boolean spiritOut(LivingEntity caster) {
        return SPIRITS.containsKey(caster.getId());
    }

    // ---------- what the piloting player presses (ModPayloads.InoPilotInput) ----------

    public static void input(ServerPlayer player, float forward, float strafe, boolean jump, float yaw, float pitch, int attacks) {
        INPUTS.put(player.getUUID(), new Input(Mth.clamp(forward, -1, 1), Mth.clamp(strafe, -1, 1), jump, yaw, pitch, attacks,
                player.level().getGameTime()));
    }

    // A punch key while piloting: the body she drives hits instead.
    public static boolean pilotPunch(ServerPlayer player) {
        Spirit s = SPIRITS.get(player.getId());
        if (s == null || !s.controlling) {
            return false;
        }
        Input in = INPUTS.get(player.getUUID());
        INPUTS.put(player.getUUID(), in == null ? new Input(0, 0, false, player.getYRot(), player.getXRot(), 1, player.level().getGameTime())
                : new Input(in.forward, in.strafe, in.jump, in.yaw, in.pitch, in.attacks + 1, in.at));
        return true;
    }

    // Who a controlled or confused one goes for: the closest other living thing (never the one who did it).
    static LivingEntity nearestOther(ServerLevel level, LivingEntity from, LivingEntity not) {
        LivingEntity best = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from.blockPosition()).inflate(16),
                e -> e != from && e != not && Combat.isTarget(e, from))) {
            if (best == null || e.distanceToSqr(from) < best.distanceToSqr(from)) {
                best = e;
            }
        }
        return best;
    }

    // One tick of being turned on the others: mobs change target, players are walked to the closest one and hit it.
    static void turn(ServerLevel level, LivingEntity t, LivingEntity ino, int age, boolean walk) {
        LivingEntity victim = nearestOther(level, t, ino);
        if (victim == null) {
            return;
        }
        if (t instanceof Mob mob) {
            mob.setTarget(victim);
            return;
        }
        Vec3 to = victim.position().subtract(t.position());
        if (walk && to.length() > 1.6) {
            Vec3 step = SubCommon.flat(to).scale(0.2);
            t.setDeltaMovement(step.x, t.getDeltaMovement().y, step.z);
            t.hurtMarked = true;
        }
        if (age % 20 == 10 && to.length() < 3) {
            Combat.hurt(t, victim, 4);
            Stun.apply(victim, 8);
            SubNet.send(level, SubNet.INO_HIT, 0, Combat.center(victim), Vec3.ZERO, 18, 0, 0);
            SubCommon.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
        }
    }

    // ---------- passive: Leitura Mental ----------

    // Called by PowerLogic.cast as anyone starts a skill: every Ino near that enemy feels it (only she sees it).
    public static void sense(LivingEntity caster, ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (p == caster || !p.isAlive() || !"ino".equals(p.getData(ModAttachments.POWER).power) || p.distanceTo(caster) > READ_RANGE
                    || !Combat.isTarget(caster, p)) {
                continue;
            }
            long key = ((long) p.getId() << 32) | (caster.getId() & 0xFFFFFFFFL);
            Long last = READ_LAST.get(key);
            if (last != null && now - last < 10) {
                continue;
            }
            READ_LAST.put(key, now);
            Vec3 me = forehead(p);
            Vec3 head = caster.getEyePosition().add(0, 0.3, 0);
            SubNet.sendTo(p, SubNet.INO_READ, caster.getId(), me, head.subtract(me), 0, 0, 0);
        }
    }

    // ---------- Z: Shintenshin ----------

    private static boolean shintenshin(LivingEntity caster, ServerLevel level) {
        Spirit out = SPIRITS.get(caster.getId());
        if (out != null) {
            if (out.controlling) {
                out.exit = true;
                return true;
            }
            return false;
        }
        Spirit s = new Spirit();
        SPIRITS.put(caster.getId(), s);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3[] dir = {look};
        SubCommon.pose(caster, "nr.losango");
        SubCommon.busy(caster, SEAL_TICKS + 2);
        DbCommon.cooldown(caster, 0, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.3f);
        SubNet.send(level, SubNet.INO_SEAL, caster.getId(), caster.position().add(0, 1.32, 0).add(look.scale(0.62)), look.scale(-1), 0.275f, 0, 0);
        // The pollen's mark: the spirit homes in on him.
        LivingEntity homing = marked(level, caster);
        // 0 seal, 1 rising out, 2 flying, 3 going in, 4 driving, 5 coming back
        int[] state = {0};
        int[] mark = {0};
        int[] control = {0};
        boolean[] miss = {false};
        boolean[] bodyHit = {false};
        float[] lastHealth = {caster.getHealth()};
        Vec3[] spirit = {caster.position()};
        Vec3[] body = {caster.position()};
        Vec3[] from = {caster.position()};
        Tasks.add(age -> {
            boolean dead = !caster.isAlive() || caster.isRemoved();
            if (state[0] == 0) {
                if (dead || guard.broken()) {
                    SPIRITS.remove(caster.getId());
                    return true;
                }
                if (age < SEAL_TICKS) {
                    return false;
                }
                state[0] = 1;
                mark[0] = age;
                body[0] = caster.position();
                spirit[0] = body[0];
                lastHealth[0] = caster.getHealth();
                SubCommon.pose(caster, "nr.caida");
                SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
            }
            // Her body lies there, still and defenseless; any hit on it (or a stun) brings the spirit straight back.
            if (!dead) {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
                caster.setDeltaMovement(0, caster.getDeltaMovement().y, 0);
                if (state[0] < 5 && (caster.getHealth() < lastHealth[0] - 0.01f || guard.broken())) {
                    bodyHit[0] = true;
                }
                lastHealth[0] = caster.getHealth();
            }
            if (dead) {
                end(level, caster, s, id, spirit[0]);
                return true;
            }
            if (state[0] == 1) {
                float k = SubCommon.smooth((age - mark[0]) / (float) RISE_TICKS);
                spirit[0] = body[0].add(dir[0].scale(0.6 * k)).add(0, 0.15 * k, 0);
                SubNet.send(level, SubNet.INO_SPIRIT, id, spirit[0], dir[0], k, 1 - k, 0);
                if (bodyHit[0]) {
                    state[0] = 5;
                    mark[0] = age;
                    from[0] = spirit[0];
                } else if (age - mark[0] >= RISE_TICKS) {
                    state[0] = 2;
                }
                return false;
            }
            if (state[0] == 2) {
                if (homing != null && homing.isAlive()) {
                    Vec3 to = SubCommon.flat(homing.position().subtract(spirit[0]));
                    dir[0] = dir[0].add(to.subtract(dir[0]).scale(0.3)).normalize();
                }
                Vec3 next = spirit[0].add(dir[0].scale(SPIRIT_SPEED));
                Vec3 ground = SubCommon.ground(level, next.add(0, 0.5, 0));
                // A wall too high to step over stops it like a miss.
                boolean wall = ground.y > spirit[0].y + 1.6;
                spirit[0] = new Vec3(next.x, ground.y + 0.15, next.z);
                SubNet.send(level, SubNet.INO_SPIRIT, id, spirit[0], dir[0], 1, 0, 0);
                LivingEntity hit = null;
                AABB box = new AABB(spirit[0].x - 0.3, spirit[0].y, spirit[0].z - 0.3, spirit[0].x + 0.3, spirit[0].y + 1.9, spirit[0].z + 0.3).inflate(0.35);
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.isTarget(e, caster) && !(e instanceof NarutoClone))) {
                    if (hit == null || t.distanceToSqr(spirit[0]) < hit.distanceToSqr(spirit[0])) {
                        hit = t;
                    }
                }
                if (bodyHit[0]) {
                    state[0] = 5;
                    mark[0] = age;
                    from[0] = spirit[0];
                    return false;
                }
                if (hit != null) {
                    s.host = hit;
                    state[0] = 3;
                    mark[0] = age;
                    from[0] = spirit[0];
                    clearMark(hit);
                    SubNet.send(level, SubNet.INO_HIT, 1, Combat.center(hit), Vec3.ZERO, 70, 1, 0);
                    SubCommon.sound(level, hit.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);
                    return false;
                }
                if (wall || spirit[0].distanceTo(body[0]) >= SPIRIT_RANGE) {
                    miss[0] = true;
                    state[0] = 5;
                    mark[0] = age;
                    from[0] = spirit[0];
                }
                return false;
            }
            if (state[0] == 3) {
                LivingEntity t = s.host;
                float k = SubCommon.smooth((age - mark[0]) / 5f);
                SubNet.send(level, SubNet.INO_SPIRIT, id, from[0].lerp(t.position(), k), dir[0], 1 - k, k, age - mark[0] >= 5 ? 1 : 0);
                if (age - mark[0] < 5) {
                    return false;
                }
                // In: only the eyes turn purple.
                control[0] = controlTicks(caster, t);
                s.controlling = true;
                mark[0] = age + control[0];
                PowerLogic.seal(t, control[0] + 10);
                SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, control[0], 2, 0);
                if (t instanceof Mob mob) {
                    s.hostWasNoAi = mob.isNoAi();
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                    if (caster instanceof ServerPlayer) {
                        mob.setNoAi(true);
                    }
                }
                if (caster instanceof ServerPlayer p) {
                    INPUTS.remove(p.getUUID());
                    SubNet.sendTo(p, SubNet.INO_PILOT, t.getId(), t.position(), Vec3.ZERO, control[0], 0, 0);
                }
                state[0] = 4;
                return false;
            }
            if (state[0] == 4) {
                LivingEntity t = s.host;
                boolean over = !t.isAlive() || t.isRemoved() || s.exit || bodyHit[0] || age >= mark[0];
                if (!over) {
                    drive(level, caster, s, t, age);
                    return false;
                }
                release(level, caster, s);
                if (s.exit && t.isAlive()) {
                    // Exit: the body she leaves hurts itself.
                    Combat.hurtMagic(caster, t, 9);
                    Stun.apply(t, 20);
                    SubNet.send(level, SubNet.INO_SPIRAL, t.getId(), t.position(), Vec3.ZERO, 20, 0, 0);
                    SubNet.send(level, SubNet.INO_HIT, 2, t.getEyePosition(), Vec3.ZERO, 90, 2, 0);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
                }
                if (!s.exit) {
                    DbCommon.cooldown(caster, 0, 260);
                }
                state[0] = 5;
                mark[0] = age;
                from[0] = t.position().add(0, 0.4, 0);
                return false;
            }
            // Coming back to her body.
            int back = bodyHit[0] ? 4 : 12;
            float k = SubCommon.smooth((age - mark[0]) / (float) back);
            Vec3 to = body[0].add(0, 0.2, 0);
            Vec3 p = from[0].lerp(to, k);
            SubNet.send(level, SubNet.INO_SPIRIT, id, p, to.subtract(from[0]), 1 - SubCommon.smooth((age - mark[0] - back * 0.8f) / (back * 0.2f)), 0,
                    age - mark[0] >= back ? 1 : 0);
            if (age - mark[0] < back) {
                return false;
            }
            SPIRITS.remove(caster.getId());
            SubCommon.pose(caster, "");
            if (miss[0] || bodyHit[0]) {
                Stun.apply(caster, 20);
                SubNet.send(level, SubNet.INO_SPIRAL, caster.getId(), caster.position(), Vec3.ZERO, 20, 0, 0);
                SubCommon.warn(caster, miss[0] ? "ino.spirit_miss" : "ino.body_hit");
                DbCommon.cooldown(caster, 0, 200);
            }
            return true;
        });
        return false;
    }

    // One tick of the body she drives. A player drives it with their own keys; an Ino boss turns it on the others.
    private static void drive(ServerLevel level, LivingEntity caster, Spirit s, LivingEntity t, int age) {
        if (!(caster instanceof ServerPlayer player)) {
            turn(level, t, caster, age, true);
            return;
        }
        Input in = INPUTS.get(player.getUUID());
        boolean fresh = in != null && level.getGameTime() - in.at <= 6;
        float yaw = fresh ? in.yaw : t.getYRot();
        float pitch = fresh ? in.pitch : t.getXRot();
        float fw = fresh ? in.forward : 0, st = fresh ? in.strafe : 0;
        double rad = Math.toRadians(yaw), sin = Math.sin(rad), cos = Math.cos(rad);
        Vec3 move = new Vec3(st * cos - fw * sin, 0, fw * cos + st * sin);
        if (move.lengthSqr() > 1) {
            move = move.normalize();
        }
        move = move.scale(0.24);
        boolean jump = fresh && in.jump && t.onGround();
        if (t instanceof Mob mob && mob.isNoAi()) {
            // A mob without its AI doesn't move by itself (no travel step): walk it and let it fall here.
            double vy = jump ? 0.42 : t.onGround() ? 0 : (t.getDeltaMovement().y - 0.08) * 0.98;
            t.setDeltaMovement(move.x, vy, move.z);
            t.move(MoverType.SELF, t.getDeltaMovement());
        } else {
            t.setDeltaMovement(move.x, jump ? 0.42 : t.getDeltaMovement().y, move.z);
            t.hurtMarked = true;
        }
        t.setYRot(yaw);
        t.setYHeadRot(yaw);
        t.yBodyRot = yaw;
        t.setXRot(pitch);
        if (t instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
        }
        if (s.attackCd > 0) {
            s.attackCd--;
        }
        if (fresh && in.attacks > s.attacksSeen) {
            s.attacksSeen = in.attacks;
            if (s.attackCd <= 0) {
                s.attackCd = 8;
                hitWith(level, caster, t, yaw);
            }
        }
    }

    // The driven body punches what is in front of it (never her own body).
    private static void hitWith(ServerLevel level, LivingEntity caster, LivingEntity t, float yaw) {
        t.swing(InteractionHand.MAIN_HAND, true);
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        LivingEntity best = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3.5),
                e -> e != caster && Combat.isTarget(e, t))) {
            Vec3 to = Combat.center(e).subtract(t.getEyePosition());
            if (to.length() > 3.2 + e.getBbWidth() * 0.5 || SubCommon.flat(to).dot(look) < 0.45) {
                continue;
            }
            if (best == null || e.distanceToSqr(t) < best.distanceToSqr(t)) {
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        Combat.hurt(t, best, 6);
        Stun.apply(best, 8);
        SubNet.send(level, SubNet.INO_HIT, 0, Combat.center(best), Vec3.ZERO, 18, 0, 0);
        SubCommon.sound(level, best.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
    }

    // Lets go of the body: its own mind (and AI) back, her camera back.
    private static void release(ServerLevel level, LivingEntity caster, Spirit s) {
        if (!s.controlling) {
            return;
        }
        s.controlling = false;
        LivingEntity t = s.host;
        SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, 0, 0, 0);
        if (t instanceof Mob mob && !s.hostWasNoAi) {
            mob.setNoAi(false);
        }
        if (caster instanceof ServerPlayer p) {
            SubNet.sendTo(p, SubNet.INO_PILOT, -1, caster.position(), Vec3.ZERO, 0, 0, 0);
            INPUTS.remove(p.getUUID());
        }
    }

    private static void end(ServerLevel level, LivingEntity caster, Spirit s, int id, Vec3 at) {
        release(level, caster, s);
        SPIRITS.remove(caster.getId());
        SubNet.send(level, SubNet.INO_SPIRIT, id, at, Vec3.ZERO, 0, 1, 1);
    }

    // ---------- X: Shinranshin ----------

    private static boolean shinranshin(LivingEntity caster, ServerLevel level) {
        if (spiritOut(caster)) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int aim = 6;
        SubCommon.pose(caster, "nr.testa_aponta");
        SubCommon.busy(caster, aim + 8);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < aim) {
                return false;
            }
            Vec3 head = forehead(caster);
            SubNet.send(level, SubNet.INO_WAVE, caster.getId(), head, caster.getLookAngle(), 0, 0, 0);
            SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.6f);
            List<LivingEntity> caught = Combat.inCone(caster, 12, 0.8);
            if (caught.isEmpty()) {
                SubCommon.warn(caster, "ino.wave_miss");
                return true;
            }
            for (LivingEntity t : caught) {
                // Caught as the front of the wave gets to him (it opens out in 7 ticks).
                int delay = (int) Math.ceil(Math.min(1, t.distanceTo(caster) / 12) * 7);
                Tasks.later(delay, () -> confuse(level, caster, t));
            }
            return true;
        });
        return true;
    }

    private static void confuse(ServerLevel level, LivingEntity caster, LivingEntity t) {
        if (!t.isAlive()) {
            return;
        }
        int ticks = boss(t) ? INVERT_BOSS : INVERT_TICKS;
        SubNet.send(level, SubNet.INO_SPIRAL, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
        SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, ticks, 2, 0);
        SubNet.send(level, SubNet.INO_HIT, 0, t.getEyePosition(), Vec3.ZERO, 45, 0, 0);
        if (t instanceof ServerPlayer p) {
            // Controls inverted: walking and looking (client side, InoClient).
            SubNet.sendTo(p, SubNet.INO_INVERT, p.getId(), p.position(), Vec3.ZERO, ticks, 0, 0);
            return;
        }
        Tasks.add(a -> {
            if (!t.isAlive() || a >= ticks) {
                return true;
            }
            turn(level, t, caster, a, false);
            return false;
        });
    }

    // ---------- C: Mind Link ----------

    private static boolean link(LivingEntity caster, ServerLevel level) {
        if (spiritOut(caster)) {
            return false;
        }
        List<LivingEntity> near = new ArrayList<>(Combat.inCone(caster, 12, 0.4));
        near.sort((a, b) -> Double.compare(a.distanceTo(caster), b.distanceTo(caster)));
        if (near.size() < 2) {
            SubCommon.warn(caster, "ino.link_few");
            return false;
        }
        List<LivingEntity> members = new ArrayList<>(near.subList(0, Math.min(3, near.size())));
        int ticks = 120;
        NrCommon.Link link = NrCommon.link(caster, members, ticks + 30);
        SubCommon.pose(caster, "nr.fios");
        SubCommon.busy(caster, 10);
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
        List<LivingEntity> all = new ArrayList<>(members);
        int n = all.size();
        int[] toIno = new int[n];
        int[] between = new int[n];
        boolean[] snapped = new boolean[n];
        for (int i = 0; i < n; i++) {
            toIno[i] = SubNet.newId();
            between[i] = SubNet.newId();
        }
        Tasks.add(age -> {
            boolean over = age >= ticks || !NrCommon.linked(link) || !caster.isAlive();
            Vec3 me = forehead(caster);
            float fadeIno = 1 - SubCommon.smooth((age - 18) / 6f);
            for (int i = 0; i < n; i++) {
                LivingEntity m = all.get(i);
                Vec3 head = m.getEyePosition().add(0, 0.3, 0);
                if (age <= 25) {
                    SubNet.send(level, SubNet.INO_THREAD, toIno[i], me, head.subtract(me), SubCommon.smooth((age - i * 1.2f) / 6f), fadeIno,
                            age >= 24 || over ? 1 : 0.02f);
                }
                // Each one tied to the next (two of them: one thread).
                if (n == 2 && i == 1 || snapped[i]) {
                    continue;
                }
                LivingEntity next = all.get((i + 1) % n);
                Vec3 nh = next.getEyePosition().add(0, 0.3, 0);
                boolean snap = !m.isAlive() || !next.isAlive() || m.distanceTo(next) > 12;
                if (snap && age >= 8) {
                    snapped[i] = true;
                    SubNet.send(level, SubNet.INO_SNAP, between[i], head, nh.subtract(head), 0, 0, 0);
                    SubNet.send(level, SubNet.INO_HIT, 0, head.lerp(nh, 0.5), Vec3.ZERO, 50, 0, 0);
                    SubNet.send(level, SubNet.INO_THREAD, between[i], head, nh.subtract(head), 1, 1, 1);
                    continue;
                }
                if (age >= 8) {
                    SubNet.send(level, SubNet.INO_THREAD, between[i], head, nh.subtract(head), SubCommon.smooth((age - 8 - i) / 6f), 0.9f, over ? 1 : 0.026f);
                }
            }
            List<LivingEntity> keep = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LivingEntity m = all.get(i);
                if (!m.isAlive()) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (j != i && all.get(j).isAlive() && m.distanceTo(all.get(j)) <= 12) {
                        keep.add(m);
                        break;
                    }
                }
            }
            link.members.retainAll(keep);
            if (keep.size() < 2) {
                over = true;
            }
            if (over) {
                NrCommon.unlink(link);
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- B: Campo de Flores ----------

    private static boolean flowers(LivingEntity caster, ServerLevel level) {
        if (spiritOut(caster)) {
            return false;
        }
        Vec3 aim = SubCommon.ground(level, SubCommon.aimPoint(caster, 20));
        List<Field> mine = FIELDS.computeIfAbsent(caster.getId(), k -> new ArrayList<>());
        while (mine.size() >= 2) {
            mine.get(0).until = 0;
            mine.remove(0);
        }
        SubCommon.pose(caster, "nr.mao_aberta");
        SubCommon.busy(caster, 8);
        SubCommon.sound(level, caster.position(), SoundEvents.CROP_PLANTED, 1.2f, 1.1f);
        Vec3 hand = Combat.handPos(caster);
        SubNet.send(level, SubNet.INO_SEEDS, caster.getId(), hand, aim.subtract(hand), 0, 0, 0);
        Field f = new Field();
        f.pos = aim;
        // The seeds land in 0.85 s, then the bed grows.
        Tasks.later(17, () -> {
            if (!caster.isAlive()) {
                return;
            }
            f.until = level.getGameTime() + FIELD_TICKS;
            mine.add(f);
            SubCommon.sound(level, aim, SoundEvents.FLOWERING_AZALEA_PLACE, 1.4f, 1.2f);
            Tasks.add(age -> fieldTick(level, caster, f, mine));
        });
        return true;
    }

    private static boolean fieldTick(ServerLevel level, LivingEntity caster, Field f, List<Field> mine) {
        long now = level.getGameTime();
        boolean gone = now >= f.until || !caster.isAlive();
        if (f.wilt < 0 && !gone) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.pos, f.pos).inflate(FIELD_RADIUS, 1.5, FIELD_RADIUS),
                    e -> Combat.isTarget(e, caster) && !(e instanceof NarutoClone))) {
                if (SubCommon.flat(e.position().subtract(f.pos)).lengthSqr() >= 0 && horizontal(e.position(), f.pos) <= FIELD_RADIUS) {
                    f.wilt = 0;
                    mark(level, e, caster);
                    SubNet.send(level, SubNet.INO_POLLEN, f.id, e.position().add(0, 0.6, 0), Vec3.ZERO, 0, 0, 0);
                    SubCommon.sound(level, e.position(), SoundEvents.AZALEA_LEAVES_BREAK, 1.6f, 1.3f);
                    break;
                }
            }
        }
        if (f.wilt >= 0) {
            f.wilt++;
            gone |= f.wilt >= 40;
        }
        SubNet.send(level, SubNet.INO_FIELD, f.id, f.pos, Vec3.ZERO, (float) FIELD_RADIUS, f.wilt < 0 ? 0 : f.wilt / 40f, gone ? 1 : 0);
        if (gone) {
            mine.remove(f);
            return true;
        }
        return false;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void mark(ServerLevel level, LivingEntity e, LivingEntity caster) {
        long until = level.getGameTime() + MARK_TICKS;
        boolean fresh = !MARKS.containsKey(e.getId());
        MARKS.put(e.getId(), until);
        if (!fresh) {
            return;
        }
        Tasks.add(age -> {
            Long u = MARKS.get(e.getId());
            boolean over = u == null || level.getGameTime() >= u || !e.isAlive();
            SubNet.send(level, SubNet.INO_MARK, e.getId(), e.getEyePosition(), Vec3.ZERO, 0, 0, over ? 1 : 0);
            if (over) {
                MARKS.remove(e.getId());
            }
            return over;
        });
    }

    private static void clearMark(LivingEntity e) {
        MARKS.remove(e.getId());
    }

    // The closest one wearing the pollen within the spirit's reach, or null.
    private static LivingEntity marked(ServerLevel level, LivingEntity caster) {
        long now = level.getGameTime();
        LivingEntity best = null;
        for (Iterator<Map.Entry<Integer, Long>> it = MARKS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> m = it.next();
            if (now >= m.getValue()) {
                continue;
            }
            if (level.getEntity(m.getKey()) instanceof LivingEntity e && e.isAlive() && e != caster && e.distanceTo(caster) <= SPIRIT_RANGE + 2
                    && (best == null || e.distanceToSqr(caster) < best.distanceToSqr(caster))) {
                best = e;
            }
        }
        return best;
    }

    // ---------- V: Teatro Mental ----------

    private static boolean theater(LivingEntity caster, ServerLevel level) {
        if (spiritOut(caster)) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int seal = 16, expand = 20, strings = 18, pull = 18, boom = 48;
        int fightAt = seal + expand + strings, pullAt = fightAt + THEATER_FIGHT, boomAt = pullAt + pull, endAt = boomAt + boom;
        Vec3 center = caster.position();
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        SubCommon.pose(caster, "nr.meditar");
        SubCommon.busy(caster, endAt);
        SubCommon.sound(level, center, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.4f, 0.8f);
        SubNet.send(level, SubNet.INO_SEAL, caster.getId(), center.add(0, 1.0, 0).add(look.scale(0.62)), look.scale(-1), 0.4f, 1, 0);
        List<LivingEntity> caught = new ArrayList<>();
        float[] lastHealth = {caster.getHealth()};
        boolean[] broken = {false};
        int[] brokeAt = {-1};
        Tasks.add(age -> {
            boolean dead = !caster.isAlive() || caster.isRemoved() || guard.broken();
            // A strong hit on her ends the play (no burst at the end).
            if (!dead && caster.getHealth() < lastHealth[0] - 8) {
                broken[0] = true;
            }
            lastHealth[0] = caster.getHealth();
            if ((dead || broken[0]) && brokeAt[0] < 0) {
                brokeAt[0] = age;
                SubCommon.pose(caster, "");
                for (LivingEntity t : caught) {
                    SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                }
            }
            if (brokeAt[0] >= 0) {
                // Everything fades out where it is.
                int a = age - brokeAt[0];
                float f = 1 - SubCommon.smooth(a / 16f);
                SubNet.send(level, SubNet.INO_THEATER, id, center, new Vec3(f, f, 0), (float) THEATER_RADIUS, f, a >= 16 ? 1 : 0);
                for (LivingEntity t : caught) {
                    SubNet.send(level, SubNet.INO_STRINGS, t.getId(), t.position(), Vec3.ZERO, 1, SubCommon.smooth(a / 8f), a >= 10 ? 1 : 0);
                }
                return a >= 16;
            }
            if (!caster.isAlive()) {
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
            caster.setDeltaMovement(0, caster.getDeltaMovement().y, 0);
            float aura = SubCommon.smooth(age / (float) 6);
            if (age < seal) {
                SubNet.send(level, SubNet.INO_THEATER, id, center, new Vec3(0, aura, 0), 0, 0, 0);
                return false;
            }
            int t = age - seal;
            if (t == 0) {
                SubNet.send(level, SubNet.INO_HIT, 3, center.add(0, 1, 0), Vec3.ZERO, 0, 3, 0);
                SubCommon.sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.4f);
            }
            float radius = 0.5f + 19.5f * SubCommon.easeOutCubic(t / 14f);
            float sky = SubCommon.smooth(t / 18f);
            float dome = 1;
            // Caught as the dome passes over them.
            if (age < fightAt) {
                for (LivingEntity e : Combat.inRadius(level, center.add(0, 1, 0), radius, caster)) {
                    if (!caught.contains(e) && !(e instanceof NarutoClone)) {
                        caught.add(e);
                                                SubNet.send(level, SubNet.EYES, e.getId(), e.getEyePosition(), Vec3.ZERO, endAt - age, 2, 0);
                        SubNet.send(level, SubNet.INO_HIT, 0, e.getEyePosition(), Vec3.ZERO, 30, 0, 0);
                        PowerLogic.seal(e, boss(e) ? THEATER_FIGHT / 2 + strings : pullAt - age);
                    }
                }
            }
            int s = age - seal - expand;
            if (age >= seal + expand) {
                for (int i = 0; i < caught.size(); i++) {
                    LivingEntity e = caught.get(i);
                    boolean freed = boss(e) && age >= fightAt + THEATER_FIGHT / 2 && age < boomAt;
                    float grow = SubCommon.smooth((s - i * 1.6f) / 10f);
                    if (age < boomAt) {
                        SubNet.send(level, SubNet.INO_STRINGS, e.getId(), e.position(), Vec3.ZERO, grow, freed ? 1 : 0, freed ? 1 : 0);
                    }
                }
            }
            if (age >= fightAt && age < pullAt) {
                int fa = age - fightAt;
                for (LivingEntity e : caught) {
                    if (!e.isAlive() || boss(e) && fa >= THEATER_FIGHT / 2) {
                        continue;
                    }
                    puppet(level, caster, e, caught, fa);
                }
            }
            if (age >= pullAt && age < boomAt) {
                dome = 1 + 0.6f * SubCommon.smooth((age - pullAt) / 12f);
                for (LivingEntity e : caught) {
                    if (e.isAlive()) {
                        e.setDeltaMovement(0, age - pullAt < 10 ? 0.16 : 0.0, 0);
                        e.hurtMarked = true;
                        e.fallDistance = 0;
                    }
                }
            }
            if (age == boomAt) {
                SubCommon.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.5f);
                SubNet.send(level, SubNet.INO_HIT, 3, center.add(0, 1.5, 0), Vec3.ZERO, 0, 4, 0);
                for (LivingEntity e : caught) {
                    if (!e.isAlive()) {
                        continue;
                    }
                    SubNet.send(level, SubNet.INO_MIND_BOOM, e.getId(), e.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                    SubNet.send(level, SubNet.INO_STRINGS, e.getId(), e.position(), Vec3.ZERO, 1, 1, 1);
                    SubNet.send(level, SubNet.EYES, e.getId(), e.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                    Combat.hurtMagic(caster, e, 36);
                    Stun.apply(e, 40);
                    SubNet.send(level, SubNet.INO_SPIRAL, e.getId(), e.position(), Vec3.ZERO, 40, 0, 0);
                }
                SubCommon.pose(caster, "");
            }
            if (age >= boomAt) {
                float f = 1 - SubCommon.smooth((age - boomAt - 12) / 16f);
                dome = 1.6f * f;
                sky = f;
                aura = f;
            }
            boolean last = age >= endAt;
            SubNet.send(level, SubNet.INO_THEATER, id, center, new Vec3(dome, aura, 0), radius, sky, last ? 1 : 0);
            return last;
        });
        return true;
    }

    // One tick of a puppet: it goes for the closest other one caught (or anyone near) and hits.
    private static void puppet(ServerLevel level, LivingEntity caster, LivingEntity e, List<LivingEntity> caught, int age) {
        LivingEntity other = null;
        for (LivingEntity o : caught) {
            if (o != e && o.isAlive() && (other == null || o.distanceToSqr(e) < other.distanceToSqr(e))) {
                other = o;
            }
        }
        if (other == null) {
            turn(level, e, caster, age, true);
            return;
        }
        if (e instanceof Mob mob) {
            mob.setTarget(other);
            return;
        }
        Vec3 to = other.position().subtract(e.position());
        if (to.length() > 1.4) {
            Vec3 step = SubCommon.flat(to).scale(0.2);
            e.setDeltaMovement(step.x, e.getDeltaMovement().y, step.z);
            e.hurtMarked = true;
        }
        if ((age + e.getId()) % 12 == 0 && to.length() < 2.6) {
            e.swing(InteractionHand.MAIN_HAND, true);
            Combat.hurt(e, other, 4);
            Stun.apply(other, 6);
            SubNet.send(level, SubNet.INO_HIT, 0, other.getEyePosition().add(0, -0.4, 0), Vec3.ZERO, 14, 0, 0);
        }
    }

    public static void clear() {
        SPIRITS.clear();
        INPUTS.clear();
        MARKS.clear();
        FIELDS.clear();
        READ_LAST.clear();
    }
}
