package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Shikamaru (SubAlma of Naruto, tier 8), the Visualizador's Shikamaru tab. The shadows are flat and opaque, no glow.
// Z Kagemane: his shadow stretches along the ground to the target (up to 18 blocks) and catches it: the target copies
//   every step he takes (walk back and it walks back). Z again: Kage Kubishibari, the shadow climbs the body into a hand
//   on the neck: damage while the key is held, and it costs stamina.
// X Kage Nui: the shadow splits toward up to 3 enemies and rises in sharp threads that pierce and pin them 1.5 s.
// C Paper bomb: a kunai with a paper bomb stuck in the ground (2 at most); it goes off when an enemy steps on it. With two
//   armed, C sets them off at once. Walk a Kagemane'd target onto one: checkmate.
public final class Shikamaru {
    private static final double TAG_RADIUS = 1.3;
    private static final Map<Integer, Hold> HOLDS = new HashMap<>();
    private static final Map<Integer, List<Tag>> TAGS = new HashMap<>();

    private static final class Hold {
        boolean choke;
        boolean done;
    }

    private static final class Tag {
        final Vec3 pos;
        final int id = SubNet.newId();
        boolean gone;

        Tag(Vec3 pos) {
            this.pos = pos;
        }
    }

    private Shikamaru() {
    }

    public static Power create() {
        return new Power("shikamaru", 0x5F6B3A, 0.9f, List.of(
                new Ability(200, 0, Shikamaru::kagemane),
                new Ability(220, 0, Shikamaru::kageNui),
                new Ability(220, 0, Shikamaru::paperBomb),
                new Ability(1000, 0, Shikamaru::ultMissing)));
    }

    // ---------- Z: Kagemane / Kubishibari ----------

    private static boolean kagemane(LivingEntity caster, ServerLevel level) {
        Hold h = HOLDS.get(caster.getId());
        if (h != null && !h.done) {
            if (!h.choke) {
                h.choke = true;
                return true;
            }
            return false;
        }
        LivingEntity target = NrCommon.target(caster, 18);
        if (target == null) {
            SubCommon.warn(caster, "shikamaru.no_target");
            return false;
        }
        Hold hold = new Hold();
        HOLDS.put(caster.getId(), hold);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int stretch = 22;
        int catchFor = 100;
        int chokeMax = 80;
        SubCommon.pose(caster, "nr.rato");
        SubCommon.busy(caster, 10);
        DbCommon.cooldown(caster, 0, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 0.6f);
        Vec3[] casterStart = {null};
        Vec3[] targetStart = {null};
        int[] chokeAt = {-1};
        Tasks.add(age -> {
            Vec3 from = caster.position();
            boolean lost = guard.broken() || !target.isAlive();
            if (age < stretch) {
                if (lost || target.distanceTo(caster) > 15) {
                    stop(level, caster, target, hold, id, false);
                    if (!lost) {
                        SubCommon.warn(caster, "shikamaru.escaped");
                    }
                    return true;
                }
                SubNet.send(level, SubNet.SHADOW, id, from, target.position().subtract(from), SubCommon.smooth(age / (float) stretch), 0.42f, 0);
                return false;
            }
            if (age == stretch) {
                casterStart[0] = caster.position();
                targetStart[0] = target.position();
                Freeze.hold(target, catchFor + chokeMax + 10, a -> targetStart[0].add(caster.position().subtract(casterStart[0])));
                PowerLogic.seal(target, catchFor + 10);
                SubCommon.sound(level, target.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.5f);
                SubCommon.pose(caster, "");
            }
            if (lost || !Freeze.isFrozen(target)) {
                stop(level, caster, target, hold, id, chokeAt[0] < 0);
                return true;
            }
            SubNet.send(level, SubNet.SHADOW, id, from, target.position().subtract(from), 1, 0.42f, 0);
            if (caster instanceof Mob && !hold.choke && age == stretch + 30) {
                hold.choke = true;
            }
            if (hold.choke && chokeAt[0] < 0) {
                chokeAt[0] = age;
                SubCommon.pose(caster, "nr.rato");
                PowerLogic.seal(target, chokeMax + 10);
                SubCommon.sound(level, target.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.4f);
            }
            if (chokeAt[0] < 0) {
                SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0.25f, 0, 0);
                if (age >= stretch + catchFor) {
                    stop(level, caster, target, hold, id, true);
                    return true;
                }
                return false;
            }
            int a = age - chokeAt[0];
            SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0.25f + 0.75f * SubCommon.smooth(a / 16f), 0, 0);
            if (a >= 16) {
                SubNet.send(level, SubNet.NECK_HAND, target.getId(), target.position(), Vec3.ZERO, a - 16, 0, 0);
            }
            if (a >= 26 && a % 10 == 6) {
                Combat.hurtMagic(caster, target, 1.6f);
                SubCommon.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.6f, 0.6f);
            }
            if (a % 4 == 0) {
                Stamina.drain(caster, 1.2);
            }
            // Letting go of the key (or running out of breath) lets the target go.
            if (a >= chokeMax || (a > 6 && !SubCommon.held(caster, a, chokeMax)) || Stamina.exhausted(caster)) {
                stop(level, caster, target, hold, id, false);
                return true;
            }
            return false;
        });
        return false;
    }

    private static void stop(ServerLevel level, LivingEntity caster, LivingEntity target, Hold hold, int id, boolean cooldown) {
        hold.done = true;
        HOLDS.remove(caster.getId(), hold);
        Freeze.free(target);
        SubNet.send(level, SubNet.SHADOW, id, caster.position(), Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.NECK_HAND, target.getId(), target.position(), Vec3.ZERO, 0, 0, 1);
        if (cooldown && caster.isAlive()) {
            DbCommon.cooldown(caster, 0, 200);
        }
    }

    // ---------- X: Kage Nui ----------

    private static boolean kageNui(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> targets = new ArrayList<>(Combat.inCone(caster, 18, 0.3));
        targets.sort((a, b) -> Double.compare(a.distanceTo(caster), b.distanceTo(caster)));
        if (targets.isEmpty()) {
            SubCommon.warn(caster, "shikamaru.no_target");
            return false;
        }
        List<LivingEntity> picked = targets.subList(0, Math.min(3, targets.size()));
        SubCommon.Guard guard = SubCommon.guard(caster);
        int run = 18;
        int pin = 30;
        int back = 14;
        int[] shadows = new int[picked.size()];
        int[] spikes = new int[picked.size()];
        for (int i = 0; i < shadows.length; i++) {
            shadows[i] = SubNet.newId();
            spikes[i] = SubNet.newId();
        }
        Vec3 from = caster.position();
        Vec3[] feet = new Vec3[picked.size()];
        SubCommon.pose(caster, "nr.rato");
        SubCommon.busy(caster, run + 4);
        SubCommon.sound(level, from, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 0.7f);
        Tasks.add(age -> {
            for (int i = 0; i < picked.size(); i++) {
                LivingEntity t = picked.get(i);
                if (feet[i] == null || age < run) {
                    feet[i] = t.position();
                }
                float prog = SubCommon.smooth(age / (float) run) * (1 - SubCommon.smooth((age - run - pin) / (float) back));
                boolean end = age >= run + pin + back;
                SubNet.send(level, SubNet.SHADOW, shadows[i], from, feet[i].subtract(from), prog, 0.3f, end ? 1 : 0);
                if (age >= run - 2) {
                    float k = SubCommon.smooth((age - run + 2) / 4f) * (1 - SubCommon.smooth((age - run - pin) / (float) back));
                    SubNet.send(level, SubNet.SPIKES, spikes[i], feet[i], Vec3.ZERO, k, i + 5, end ? 1 : 0);
                }
                if (age == run && t.isAlive() && !guard.broken()) {
                    Combat.hurtMagic(caster, t, 7);
                    Stun.apply(t, pin);
                    Freeze.hold(t, pin);
                    SubCommon.sound(level, t.position(), SoundEvents.TRIDENT_THROW.value(), 1f, 0.5f);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 0.5f);
                }
            }
            return age >= run + pin + back;
        });
        return true;
    }

    // ---------- C: paper bombs ----------

    private static boolean paperBomb(LivingEntity caster, ServerLevel level) {
        List<Tag> tags = TAGS.computeIfAbsent(caster.getId(), k -> new ArrayList<>());
        tags.removeIf(t -> t.gone);
        if (tags.size() >= 2) {
            for (Tag t : new ArrayList<>(tags)) {
                boom(caster, level, t);
            }
            return true;
        }
        LivingEntity target = NrCommon.target(caster, 16);
        Vec3 aim = SubCommon.ground(level, target != null && caster instanceof Mob ? target.position() : SubCommon.aimPoint(caster, 16));
        Vec3 hand = DbCommon.hand(caster);
        int fly = 8;
        SubCommon.pose(caster, "db.arremesso");
        SubCommon.busy(caster, fly);
        SubNet.send(level, SubNet.KUNAI, 0, hand, aim.subtract(hand), fly, 0, 0);
        SubCommon.sound(level, hand, SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.6f);
        DbCommon.cooldown(caster, 2, 30);
        Tag tag = new Tag(aim);
        tags.add(tag);
        Tasks.add(age -> {
            if (tag.gone) {
                return true;
            }
            if (age < fly) {
                return false;
            }
            if (age == fly) {
                SubCommon.sound(level, aim, SoundEvents.ARROW_HIT, 1f, 0.8f);
            }
            if (!caster.isAlive() || age > 1200) {
                tag.gone = true;
                SubNet.send(level, SubNet.TAG, tag.id, aim, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            SubNet.send(level, SubNet.TAG, tag.id, aim, Vec3.ZERO, 0, 0, 0);
            double reach = caster instanceof Mob ? 2.2 : TAG_RADIUS;
            for (LivingEntity e : Combat.inRadius(level, aim.add(0, 0.5, 0), reach + 0.8, caster)) {
                if (Math.abs(e.getY() - aim.y) < 1.6 && new Vec3(e.getX() - aim.x, 0, e.getZ() - aim.z).length() < reach) {
                    boom(caster, level, tag);
                    return true;
                }
            }
            return false;
        });
        return false;
    }

    private static void boom(LivingEntity caster, ServerLevel level, Tag tag) {
        if (tag.gone) {
            return;
        }
        tag.gone = true;
        Vec3 at = tag.pos;
        double r = 3.2;
        SubNet.send(level, SubNet.TAG, tag.id, at, Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.FIRE_BOOM, 0, at.add(0, 0.8, 0), new Vec3(at.y, 0, 0), (float) r, 0, SubCommon.RANDOM.nextInt(40));
        DbCommon.screen(level, at, 22);
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.9f);
        SubCommon.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
        for (LivingEntity t : Combat.inRadius(level, at.add(0, 0.8, 0), r, caster)) {
            // The shadow lets go an instant before, so whoever was walked onto it flies.
            Freeze.free(t);
            double k = 1 - 0.5 * Math.min(1, Combat.center(t).distanceTo(at) / r);
            Combat.hurtMagic(caster, t, (float) (15 * k));
            Stun.apply(t, 20);
            Combat.pushFrom(t, at, 1.1 * k, 0.6);
        }
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
