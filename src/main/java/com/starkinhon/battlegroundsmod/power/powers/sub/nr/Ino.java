package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Ino (SubAlma of Naruto, tier 8), the Visualizador's Ino tab.
// Z Shintenshin: diamond seal aimed at the target; her purple spirit form flies slowly and straight (it can be dodged).
//   If it gets in, her body drops and she controls the enemy 4 s: it goes and hits its own allies. Missed: the spirit comes
//   back and she is dizzy 1 s. Z again (inside): Exit, she makes the enemy hurt itself before going back.
// X Shinranshin: a purple wave from her forehead (8 blocks, a cone): whoever is caught has their head scrambled and
//   attacks whoever is closest to them for 4 s (1 s on a boss).
// C Mind Link: purple threads tie the minds of up to 3 enemies: for 6 s half the damage one takes runs along the thread
//   to the others. A thread snaps past 12 blocks.
public final class Ino {
    private static final Map<Integer, boolean[]> INSIDE = new HashMap<>();

    private Ino() {
    }

    public static Power create() {
        return new Power("ino", 0xB05CFF, 0.9f, List.of(
                new Ability(260, 0, Ino::shintenshin),
                new Ability(260, 0, Ino::shinranshin),
                new Ability(300, 0, Ino::link),
                new Ability(1000, 0, Ino::ultMissing)));
    }

    static Vec3 forehead(LivingEntity e) {
        return e.getEyePosition().add(e.getLookAngle().scale(0.25)).add(0, 0.12, 0);
    }

    // Who a controlled or confused one goes for: the closest other living thing (never the one who did it).
    static LivingEntity nearestOther(ServerLevel level, LivingEntity from, LivingEntity not) {
        LivingEntity best = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from.blockPosition()).inflate(16),
                e -> e != from && e != not && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
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
            DbCommon.hit(level, Combat.center(victim), 0.5f, Db.WHITE);
            SubCommon.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
        }
    }

    // ---------- Z: Shintenshin ----------

    private static boolean shintenshin(LivingEntity caster, ServerLevel level) {
        boolean[] in = INSIDE.get(caster.getId());
        if (in != null) {
            in[0] = true;
            return true;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int leave = 28;
        double speed = 0.25;
        double range = 14;
        int id = SubNet.newId();
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        SubCommon.pose(caster, "nr.losango");
        SubCommon.busy(caster, leave);
        DbCommon.cooldown(caster, 0, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.3f);
        boolean[] exit = {false};
        LivingEntity[] host = {null};
        int[] state = {0};
        int[] mark = {0};
        Vec3[] spirit = {null};
        Vec3[] body = {null};
        Tasks.add(age -> {
            if (age < leave) {
                if (guard.broken()) {
                    return true;
                }
                return false;
            }
            if (age == leave) {
                body[0] = caster.position();
                spirit[0] = caster.position().add(dir.scale(0.6));
                SubCommon.pose(caster, "nr.caida");
                SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
            }
            // Her body lies there, still and defenseless, the whole time.
            boolean bodyGone = !caster.isAlive() || guard.broken();
            if (!bodyGone) {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, false, false));
                caster.setDeltaMovement(0, caster.getDeltaMovement().y, 0);
            }
            if (state[0] == 0) {
                // Flying out, slowly and straight.
                spirit[0] = spirit[0].add(dir.scale(speed));
                SubNet.send(level, SubNet.SPIRIT, id, spirit[0], dir, SubCommon.smooth((age - leave) / 4f), 0, 0);
                for (LivingEntity t : Combat.inRadius(level, spirit[0].add(0, 0.9, 0), 0.9, caster)) {
                    if (t instanceof NarutoClone) {
                        continue;
                    }
                    host[0] = t;
                    break;
                }
                if (bodyGone) {
                    SubNet.send(level, SubNet.SPIRIT, id, spirit[0], dir, 0, 0, 1);
                    return true;
                }
                if (host[0] != null) {
                    state[0] = 1;
                    mark[0] = age;
                    INSIDE.put(caster.getId(), exit);
                    SubNet.send(level, SubNet.SPIRIT, id, spirit[0], dir, 0, 0, 1);
                    LivingEntity t = host[0];
                    boolean boss = t instanceof SoulBoss b && b.isBoss();
                    int control = boss ? 40 : 80;
                    mark[0] = age + control;
                    PowerLogic.seal(t, control);
                    SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, control, 2, 0);
                    DbCommon.hit(level, Combat.center(t), 0.8f, Db.PURPLE);
                    DbCommon.screen(level, Combat.center(t), 9);
                    SubCommon.sound(level, t.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);
                    return false;
                }
                if (spirit[0].distanceTo(body[0]) >= range) {
                    // Missed: it comes back and she is dizzy.
                    state[0] = 3;
                    mark[0] = age;
                }
                return false;
            }
            if (state[0] == 1) {
                LivingEntity t = host[0];
                if (bodyGone || !t.isAlive() || exit[0] || age >= mark[0]) {
                    INSIDE.remove(caster.getId());
                    SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, 0, 0, 0);
                    if (exit[0] && t.isAlive()) {
                        // Exit: the body she leaves hurts itself.
                        Combat.hurtMagic(caster, t, 9);
                        Stun.apply(t, 20);
                        DbCommon.hit(level, Combat.center(t), 0.6f, Db.PURPLE);
                        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
                    }
                    if (bodyGone) {
                        return true;
                    }
                    if (!exit[0]) {
                        DbCommon.cooldown(caster, 0, 260);
                    }
                    spirit[0] = t.position();
                    state[0] = 2;
                    mark[0] = age;
                    return false;
                }
                turn(level, t, caster, age, true);
                return false;
            }
            // Coming back to her body (2: from the host, 3: missed).
            int a = age - mark[0];
            int back = 12;
            Vec3 p = spirit[0].lerp(body[0], SubCommon.smooth(a / (float) back));
            SubNet.send(level, SubNet.SPIRIT, id, p, body[0].subtract(spirit[0]), 1, 0, a >= back ? 1 : 0);
            if (a >= back) {
                SubCommon.pose(caster, "");
                if (state[0] == 3) {
                    Stun.apply(caster, 20);
                    SubNet.send(level, SubNet.DIZZY, caster.getId(), caster.position(), Vec3.ZERO, 20, 0, 0);
                    SubCommon.warn(caster, "ino.spirit_miss");
                    DbCommon.cooldown(caster, 0, 200);
                }
                return true;
            }
            return false;
        });
        return false;
    }

    // ---------- X: Shinranshin ----------

    private static boolean shinranshin(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int wave = 18;
        SubCommon.pose(caster, "nr.testa_aponta");
        SubCommon.busy(caster, wave + 4);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < wave) {
                return false;
            }
            SubNet.send(level, SubNet.MIND_WAVE, 0, forehead(caster), caster.getLookAngle(), 0, 0, 0);
            SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.6f);
            List<LivingEntity> caught = Combat.inCone(caster, 8, 0.8);
            if (caught.isEmpty()) {
                SubCommon.warn(caster, "ino.wave_miss");
                return true;
            }
            for (LivingEntity t : caught) {
                boolean boss = t instanceof SoulBoss b && b.isBoss();
                int ticks = boss ? 20 : 80;
                SubNet.send(level, SubNet.CONFUSED, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
                SubNet.send(level, SubNet.EYES, t.getId(), t.getEyePosition(), Vec3.ZERO, ticks, 2, 0);
                if (!(t instanceof Mob)) {
                    t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0, false, false));
                }
                Tasks.add(a -> {
                    if (!t.isAlive() || a >= ticks) {
                        return true;
                    }
                    turn(level, t, caster, a, false);
                    return false;
                });
            }
            return true;
        });
        return true;
    }

    // ---------- C: Mind Link ----------

    private static boolean link(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> near = new ArrayList<>(Combat.inCone(caster, 12, 0.4));
        near.sort((a, b) -> Double.compare(a.distanceTo(caster), b.distanceTo(caster)));
        if (near.size() < 2) {
            SubCommon.warn(caster, "ino.link_few");
            return false;
        }
        List<LivingEntity> members = new ArrayList<>(near.subList(0, Math.min(3, near.size())));
        int ticks = 120;
        int reach = 18;
        NrCommon.Link link = NrCommon.link(caster, members, ticks + reach);
        SubCommon.pose(caster, "nr.fios");
        SubCommon.busy(caster, 10);
        SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
        int[] toIno = new int[members.size()];
        int[] between = new int[members.size()];
        for (int i = 0; i < members.size(); i++) {
            toIno[i] = SubNet.newId();
            between[i] = SubNet.newId();
        }
        Tasks.add(age -> {
            boolean over = age >= ticks + reach || !NrCommon.linked(link) || !caster.isAlive();
            float fadeIno = 1 - SubCommon.smooth((age - 30) / 10f);
            for (int i = 0; i < members.size(); i++) {
                LivingEntity m = members.get(i);
                Vec3 head = m.getEyePosition().add(0, 0.3, 0);
                Vec3 me = forehead(caster);
                if (age < 42) {
                    SubNet.send(level, SubNet.THREAD, toIno[i], me, head.subtract(me), SubCommon.smooth(age / 18f), 0.8f * fadeIno,
                            age >= 41 || over ? 1 : 0);
                }
                // Each one tied to the next.
                LivingEntity next = members.get((i + 1) % members.size());
                if (members.size() == 2 && i == 1) {
                    continue;
                }
                boolean snapped = !m.isAlive() || !next.isAlive() || m.distanceTo(next) > 12;
                if (age >= 14) {
                    Vec3 nh = next.getEyePosition().add(0, 0.3, 0);
                    SubNet.send(level, SubNet.THREAD, between[i], head, nh.subtract(head), SubCommon.smooth((age - 14) / 10f), 0.9f,
                            over || snapped ? 1 : 0);
                }
            }
            members.removeIf(m -> {
                for (LivingEntity o : members) {
                    if (o != m && o.isAlive() && m.distanceTo(o) <= 12) {
                        return !m.isAlive();
                    }
                }
                return true;
            });
            if (members.size() < 2) {
                over = true;
            }
            link.members.retainAll(members);
            if (over) {
                NrCommon.unlink(link);
                return true;
            }
            return false;
        });
        return true;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
