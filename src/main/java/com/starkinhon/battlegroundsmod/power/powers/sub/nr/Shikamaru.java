package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Shikamaru (SubAlma of Naruto, tier 8), redone from the approved preview (forja/shikamaru, 2026-11-10). His strength is
// the head: the shadows hold with no fixed time, on STAMINA, and the paper bombs are a field he lays out himself.
// Shadows drain, per target and per second, a share of the bar by the target's tier against his: stronger 20% (~5 s),
// same tier 10% (~10 s), weaker 5% (~20 s); several targets add up; the Kubishibari drains 1.5x; at zero the shadow
// lets go and he is exhausted. Whoever has no stamina to spend (a boss, creative) runs on a full bar of its own, so the
// same 5/10/20 s.
// Z Kagemane: the shadow runs up to 24 blocks and catches; the target copies every step he takes. Z again (held):
//   Kubishibari, the shadow climbs the body into a hand on the neck, damage while held; letting go ends it all.
// X Kage Nui: the shadow splits toward up to 4 enemies (20 blocks) and rises in a needle into each, pinned while the
//   stamina lasts. X again lets go.
// C Paper bomb: only throws. A tap throws one where he aims (22 blocks); held, a fan opens one by one up to 10 at once.
//   10 armed at most; 10 in the pouch, one back every 3 s; they last 60 s. One goes off when an enemy steps on it and
//   lights its neighbours (4.6 blocks) one after the other. Shift+C sets them all off. The strategy is the Z: walk a
//   caught target into the field. Held by his shadow through 4 or more blasts: the pit, buried and stunned 3 s.
// V Xeque-mate: he kneels 1 s, the shadow floods 16 blocks round him and catches everyone, a paper bomb rises from each
//   one's feet and they go off in a chain, nearest first.
// B Signal flare: a flare up behind him for 8 s: his shadows stretch (Kagemane and Kage Nui reach twice as far) and
//   drain half. Recharge 40 s.
public final class Shikamaru {
    static final double RANGE = 24;
    static final double NUI_RANGE = 20;
    static final double DRAIN_STRONGER = 20;
    static final double DRAIN_SAME = 10;
    static final double DRAIN_WEAKER = 5;
    static final double CHOKE_DRAIN = 1.5;
    static final int MAX_TAGS = 10;
    static final double TAG_RANGE = 22;
    static final int TAG_LIFE = 1200;
    static final int TAG_REFILL = 60;
    static final double CHAIN = 4.6;
    static final float TAG_DAMAGE = 8;
    static final int PIT_HITS = 4;
    static final int PIT_STUN = 60;
    static final double SEA = 16;
    static final String FLARE = "nr_shika_flare";
    static final int FLARE_TICKS = 160;

    private static final Map<Integer, Hold> HOLDS = new HashMap<>();
    private static final Map<Integer, Nui> NUIS = new HashMap<>();
    private static final Map<Integer, List<Tag>> TAGS = new HashMap<>();
    private static final Map<Integer, long[]> POUCH = new HashMap<>();
    // Per target: {blasts while held by his shadow, the tick of the last one, the tick it was last thrown}.
    private static final Map<Integer, long[]> HITS = new HashMap<>();
    private static final Map<Integer, Long> SCREEN = new HashMap<>();
    // Casters opening a fan right now and since when (a held key must not start a second one; it expires on its own).
    private static final Map<Integer, Long> FANNING = new HashMap<>();

    private static final class Hold {
        boolean choke;
        boolean caught;
        boolean done;
        LivingEntity target;
        Runnable release;
    }

    private static final class Nui {
        boolean release;
        boolean done;
    }

    private static final class Tag {
        final Vec3 pos;
        final int id = SubNet.newId();
        final int seed = SubCommon.RANDOM.nextInt(1000);
        boolean set;
        boolean gone;
        int fuse = -1;
        int fuse0 = 1;

        Tag(Vec3 pos) {
            this.pos = pos;
        }
    }

    // A full bar for whoever has no stamina to spend (bosses, creative): the shadows last the same seconds on it.
    private static final class Budget {
        double left = 100;
    }

    private Shikamaru() {
    }

    public static Power create() {
        return new Power("shikamaru", 0x5F6B3A, 0.9f, List.of(
                new Ability(200, 0, Shikamaru::kagemane),
                new Ability(200, 0, Shikamaru::kageNui),
                new Ability(8, 0, Shikamaru::paperBomb),
                new Ability(1100, 0, Shikamaru::xeque),
                new Ability(800, 0, Shikamaru::flare)),
                Arrays.asList(null, null, new Ability(20, 0, Shikamaru::detonate), null, null));
    }

    // ---------- the shadows' stamina ----------

    private static int tier(LivingEntity entity) {
        return entity.hasData(ModAttachments.POWER) ? Tiers.of(entity) : Tiers.HUMANO;
    }

    // % of the bar per second one target costs (a lower tier number is stronger).
    static double drain(LivingEntity caster, LivingEntity target) {
        int c = tier(caster);
        int t = tier(target);
        double d = t < c ? DRAIN_STRONGER : t == c ? DRAIN_SAME : DRAIN_WEAKER;
        return SubCommon.hasState(caster, FLARE) ? d * 0.5 : d;
    }

    // One tick of percentPerSecond; true when the bar ran out (the shadow lets go; a player is exhausted by it).
    private static boolean spend(LivingEntity caster, Budget budget, double percentPerSecond) {
        double per = percentPerSecond / 20.0;
        if (Stamina.drains(caster)) {
            Stamina.drain(caster, per);
            return Stamina.exhausted(caster);
        }
        budget.left -= per;
        return budget.left <= 0;
    }

    private static boolean keyHeld(LivingEntity caster, int age, int bossTicks) {
        return caster instanceof Mob ? age < bossTicks : caster.getData(ModAttachments.POWER).abilityHeld;
    }

    private static double reach(LivingEntity caster, double range) {
        return SubCommon.hasState(caster, FLARE) ? range * 2 : range;
    }

    // ---------- Z: Kagemane / Kubishibari ----------

    private static boolean kagemane(LivingEntity caster, ServerLevel level) {
        Hold h = HOLDS.get(caster.getId());
        if (h != null && !h.done) {
            if (h.caught && !h.choke) {
                h.choke = true;
            }
            return false;
        }
        double range = reach(caster, RANGE);
        LivingEntity target = NrCommon.target(caster, range);
        if (target == null) {
            SubCommon.warn(caster, "shikamaru.no_target");
            return false;
        }
        if (Freeze.isFrozen(target)) {
            SubCommon.warn(caster, "shikamaru.held");
            return false;
        }
        Hold hold = new Hold();
        hold.target = target;
        HOLDS.put(caster.getId(), hold);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int stretch = (int) Math.max(8, Math.min(30, 6 + target.distanceTo(caster) * 0.55));
        int chokeMax = 80;
        Budget budget = new Budget();
        SubCommon.pose(caster, "nr.rato");
        SubCommon.busy(caster, 8);
        DbCommon.cooldown(caster, 0, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 0.6f);
        Vec3[] casterStart = {null};
        Vec3[] targetStart = {null};
        int[] chokeAt = {-1};
        Tasks.add(age -> {
            if (hold.done) {
                return true;
            }
            Vec3 from = caster.position();
            boolean lost = guard.broken() || !target.isAlive();
            if (age < stretch) {
                if (lost || target.distanceTo(caster) > range + 4) {
                    stop(level, caster, hold, id, 40);
                    if (!lost) {
                        SubCommon.warn(caster, "shikamaru.escaped");
                    }
                    return true;
                }
                SubNet.send(level, SubNet.SHADOW, id, from, target.position().subtract(from), SubCommon.smooth(age / (float) stretch), 0.3f, 0);
                return false;
            }
            if (age == stretch) {
                if (Freeze.isFrozen(target)) {
                    stop(level, caster, hold, id, 40);
                    SubCommon.warn(caster, "shikamaru.held");
                    return true;
                }
                casterStart[0] = caster.position();
                targetStart[0] = target.position();
                hold.release = Freeze.hold(target, 40, a -> targetStart[0].add(caster.position().subtract(casterStart[0])));
                hold.caught = true;
                SubCommon.sound(level, target.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 0.5f);
                SubCommon.pose(caster, "");
            }
            if (lost || !Freeze.isFrozen(target)) {
                stop(level, caster, hold, id, 200);
                return true;
            }
            int held = age - stretch;
            if (held % 20 == 0) {
                PowerLogic.seal(target, 30);
            }
            if (held % 30 == 29) {
                // Held in short grips renewed while it lasts: a cancelled move never leaves anyone stuck for long.
                Runnable old = hold.release;
                hold.release = Freeze.hold(target, 40, a -> targetStart[0].add(caster.position().subtract(casterStart[0])));
                old.run();
            }
            SubNet.send(level, SubNet.SHADOW, id, from, target.position().subtract(from), 1 + SubCommon.smooth(held / 4f), 0.3f, 0);
            if (spend(caster, budget, drain(caster, target) * (chokeAt[0] >= 0 ? CHOKE_DRAIN : 1))) {
                stop(level, caster, hold, id, 200);
                SubCommon.warn(caster, "shikamaru.out_of_breath");
                return true;
            }
            if (caster instanceof Mob && !hold.choke && held == 30) {
                hold.choke = true;
            }
            if (hold.choke && chokeAt[0] < 0) {
                chokeAt[0] = age;
                SubCommon.pose(caster, "nr.rato");
                SubCommon.sound(level, target.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.4f);
            }
            if (chokeAt[0] < 0) {
                SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0.12f, 0, 0);
                return false;
            }
            int a = age - chokeAt[0];
            SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0.12f + 0.88f * SubCommon.smooth(a / 16f), 0, 0);
            if (a >= 16) {
                SubNet.send(level, SubNet.NECK_HAND, target.getId(), target.position(), Vec3.ZERO, a - 16, 0, 0);
            }
            if (a >= 26 && a % 10 == 6) {
                Combat.hurtMagic(caster, target, 1.6f);
                SubCommon.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.6f, 0.6f);
            }
            // Letting go of the key ends the choke and the hold with it.
            if (a > 6 && !keyHeld(caster, a, chokeMax)) {
                stop(level, caster, hold, id, 200);
                return true;
            }
            return false;
        });
        return false;
    }

    private static void stop(ServerLevel level, LivingEntity caster, Hold hold, int id, int cooldown) {
        if (hold.done) {
            return;
        }
        hold.done = true;
        HOLDS.remove(caster.getId(), hold);
        if (hold.release != null) {
            hold.release.run();
        }
        LivingEntity target = hold.target;
        SubNet.send(level, SubNet.SHADOW, id, caster.position(), Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.BODY_SHADOW, target.getId(), target.position(), Vec3.ZERO, 0, 0, 1);
        SubNet.send(level, SubNet.NECK_HAND, target.getId(), target.position(), Vec3.ZERO, 0, 0, 1);
        if (caster.isAlive()) {
            SubCommon.pose(caster, "");
            DbCommon.cooldown(caster, 0, cooldown);
        }
    }

    // Whether target is held right now by his Kagemane.
    private static boolean shadowed(LivingEntity caster, LivingEntity target) {
        Hold h = HOLDS.get(caster.getId());
        return h != null && !h.done && h.caught && h.target == target;
    }

    // ---------- X: Kage Nui ----------

    private static boolean kageNui(LivingEntity caster, ServerLevel level) {
        Nui cur = NUIS.get(caster.getId());
        if (cur != null && !cur.done) {
            cur.release = true;
            return false;
        }
        List<LivingEntity> targets = new ArrayList<>(Combat.inCone(caster, reach(caster, NUI_RANGE), 0.3));
        targets.removeIf(Freeze::isFrozen);
        targets.sort(Comparator.comparingDouble(t -> t.distanceTo(caster)));
        if (targets.isEmpty()) {
            SubCommon.warn(caster, "shikamaru.no_target");
            return false;
        }
        List<LivingEntity> picked = new ArrayList<>(targets.subList(0, Math.min(4, targets.size())));
        Nui nui = new Nui();
        NUIS.put(caster.getId(), nui);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int run = 14;
        int back = 10;
        int n = picked.size();
        int[] shadows = new int[n];
        int[] needles = new int[n];
        boolean[] pinned = new boolean[n];
        Runnable[] releases = new Runnable[n];
        Vec3[] feet = new Vec3[n];
        for (int i = 0; i < n; i++) {
            shadows[i] = SubNet.newId();
            needles[i] = SubNet.newId();
        }
        Budget budget = new Budget();
        int[] relAt = {-1};
        SubCommon.pose(caster, "nr.rato");
        SubCommon.busy(caster, run + 4);
        DbCommon.cooldown(caster, 1, 20);
        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 0.7f);
        Tasks.add(age -> {
            Vec3 from = caster.position();
            if (age == run) {
                for (int i = 0; i < n; i++) {
                    LivingEntity t = picked.get(i);
                    if (!t.isAlive() || guard.broken() || Freeze.isFrozen(t)) {
                        continue;
                    }
                    Combat.hurtMagic(caster, t, 7);
                    Stun.apply(t, 30);
                    releases[i] = Freeze.hold(t, 40);
                    PowerLogic.seal(t, 30);
                    pinned[i] = true;
                    SubCommon.sound(level, t.position(), SoundEvents.TRIDENT_THROW.value(), 1f, 0.5f);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 0.5f);
                }
                SubCommon.pose(caster, "");
            }
            int held = age - run;
            if (age >= run && relAt[0] < 0) {
                double d = 0;
                for (int i = 0; i < n; i++) {
                    LivingEntity t = picked.get(i);
                    if (pinned[i] && t.isAlive() && Freeze.isFrozen(t)) {
                        d += drain(caster, t);
                        if (held % 20 == 0) {
                            PowerLogic.seal(t, 30);
                        }
                        if (held % 30 == 29) {
                            Runnable old = releases[i];
                            releases[i] = Freeze.hold(t, 40);
                            old.run();
                        }
                    }
                }
                boolean out = d > 0 && spend(caster, budget, d);
                if (out) {
                    SubCommon.warn(caster, "shikamaru.out_of_breath");
                }
                if (nui.release || out || d == 0 || guard.broken()) {
                    relAt[0] = age;
                    for (int i = 0; i < n; i++) {
                        if (releases[i] != null) {
                            releases[i].run();
                        }
                    }
                    if (caster.isAlive()) {
                        DbCommon.cooldown(caster, 1, 200);
                    }
                }
            }
            float k = relAt[0] < 0 ? 1 : 1 - SubCommon.smooth((age - relAt[0]) / (float) back);
            boolean end = relAt[0] >= 0 && age >= relAt[0] + back;
            for (int i = 0; i < n; i++) {
                LivingEntity t = picked.get(i);
                if (feet[i] == null || age < run) {
                    feet[i] = t.position();
                }
                float prog = age < run ? SubCommon.smooth(age / (float) run) : relAt[0] < 0 ? 1 + SubCommon.smooth(held / 4f) : k;
                SubNet.send(level, SubNet.SHADOW, shadows[i], from, feet[i].subtract(from), prog, 0.22f, end ? 1 : 0);
                if (age >= run - 2 && (pinned[i] || age < run)) {
                    float g = SubCommon.smooth((age - run + 2) / 4f) * k;
                    SubNet.send(level, SubNet.SPIKES, needles[i], feet[i], from.subtract(feet[i]), g, i + 5, end ? 1 : 0);
                }
            }
            if (end) {
                nui.done = true;
                NUIS.remove(caster.getId(), nui);
            }
            return end;
        });
        return false;
    }

    // ---------- C: paper bombs ----------

    private static List<Tag> tags(LivingEntity caster) {
        List<Tag> tags = TAGS.computeIfAbsent(caster.getId(), k -> new ArrayList<>());
        tags.removeIf(t -> t.gone);
        return tags;
    }

    // Papers left in the pouch (10 at most, one back every 3 s).
    private static int pouch(LivingEntity caster) {
        long now = caster.level().getGameTime();
        long[] p = POUCH.computeIfAbsent(caster.getId(), k -> new long[] {MAX_TAGS, now});
        if (p[0] >= MAX_TAGS) {
            p[1] = now;
        } else {
            long back = (now - p[1]) / TAG_REFILL;
            if (back > 0) {
                p[0] = Math.min(MAX_TAGS, p[0] + back);
                p[1] += back * TAG_REFILL;
            }
        }
        return (int) p[0];
    }

    private static boolean canThrow(LivingEntity caster, boolean warn) {
        if (tags(caster).size() >= MAX_TAGS) {
            if (warn) {
                SubCommon.warn(caster, "shikamaru.tag_limit", MAX_TAGS);
            }
            return false;
        }
        if (pouch(caster) <= 0) {
            if (warn) {
                SubCommon.warn(caster, "shikamaru.no_tags", TAG_REFILL / 20);
            }
            return false;
        }
        return true;
    }

    private static boolean paperBomb(LivingEntity caster, ServerLevel level) {
        if (!canThrow(caster, true)) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        if (caster instanceof Mob) {
            // A boss lays a fan of 5 round the one it fights.
            LivingEntity t = NrCommon.target(caster, TAG_RANGE);
            Vec3 c = t != null ? t.position() : SubCommon.aimPoint(caster, TAG_RANGE);
            Tasks.add(age -> {
                if (guard.broken() || age >= 10 || !canThrow(caster, false)) {
                    return true;
                }
                if (age % 2 == 0) {
                    throwTag(caster, level, fanPoint(caster, level, c, age / 2));
                }
                return false;
            });
            return true;
        }
        // A tap throws one where he aims; held, a fan opens one by one (every 2 ticks), up to 10.
        long now = level.getGameTime();
        Long since = FANNING.get(caster.getId());
        if (since != null && now - since < 45) {
            return false;
        }
        FANNING.put(caster.getId(), now);
        SubCommon.busy(caster, 4);
        Tasks.add(age -> {
            boolean done = paperStep(caster, level, guard, age);
            if (done) {
                FANNING.remove(caster.getId());
            }
            return done;
        });
        return true;
    }

    private static boolean paperStep(LivingEntity caster, ServerLevel level, SubCommon.Guard guard, int age) {
        if (guard.broken() || age > 40) {
            return true;
        }
        boolean held = caster.getData(ModAttachments.POWER).abilityHeld;
        if (age < 5) {
            if (!held) {
                throwTag(caster, level, SubCommon.ground(level, SubCommon.aimPoint(caster, TAG_RANGE)));
                return true;
            }
            return false;
        }
        if ((age - 5) % 2 != 0) {
            return false;
        }
        int i = (age - 5) / 2;
        if (!held || i >= MAX_TAGS || !canThrow(caster, i == 0)) {
            return true;
        }
        Vec3 aim = SubCommon.aimPoint(caster, TAG_RANGE);
        throwTag(caster, level, fanPoint(caster, level, aim, i));
        SubCommon.busy(caster, 3);
        return false;
    }

    // The i-th paper of a fan toward aim: alternating sides, each one a bit further out, each at its own distance.
    private static Vec3 fanPoint(LivingEntity caster, ServerLevel level, Vec3 aim, int i) {
        Vec3 from = caster.position();
        Vec3 flat = new Vec3(aim.x - from.x, 0, aim.z - from.z);
        double d0 = Math.max(5, Math.min(TAG_RANGE, flat.length()));
        Vec3 dir = SubCommon.flat(flat.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : flat);
        double side = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 0.14;
        double d = Math.max(3, d0 + ((i * 7) % 3 - 1) * 1.3);
        Vec3 rot = dir.yRot((float) side);
        return SubCommon.ground(level, from.add(rot.scale(d)).add(0, 1, 0));
    }

    private static void throwTag(LivingEntity caster, ServerLevel level, Vec3 aim) {
        List<Tag> tags = tags(caster);
        long[] p = POUCH.get(caster.getId());
        if (p != null) {
            if (p[0] >= MAX_TAGS) {
                p[1] = caster.level().getGameTime();
            }
            p[0]--;
        }
        Tag tag = new Tag(aim);
        tags.add(tag);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 hand = DbCommon.hand(caster);
        int fly = 4 + (int) (hand.distanceTo(aim) * 0.22);
        SubCommon.pose(caster, "db.arremesso");
        SubNet.send(level, SubNet.KUNAI, 0, hand, aim.subtract(hand), fly, 0, 0);
        SubCommon.sound(level, hand, SoundEvents.TRIDENT_THROW.value(), 0.7f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.2f);
        Tasks.add(age -> {
            if (tag.gone) {
                return true;
            }
            if (age < fly) {
                return false;
            }
            if (age == fly) {
                tag.set = true;
                SubCommon.sound(level, aim, SoundEvents.ARROW_HIT, 0.9f, 0.8f);
            }
            if (guard.broken() || age > TAG_LIFE) {
                tag.gone = true;
                SubNet.send(level, SubNet.TAG, tag.id, aim, Vec3.ZERO, 1, 0, 1);
                return true;
            }
            if (tag.fuse >= 0) {
                SubNet.send(level, SubNet.TAG, tag.id, aim, Vec3.ZERO, 1, 1 - tag.fuse / (float) tag.fuse0, 0);
                if (tag.fuse-- <= 0) {
                    boom(caster, level, tag);
                    return true;
                }
                return false;
            }
            SubNet.send(level, SubNet.TAG, tag.id, aim, Vec3.ZERO, 1, 0, 0);
            double r = caster instanceof Mob ? 2.2 : 1.3;
            for (LivingEntity e : Combat.inRadius(level, aim.add(0, 0.5, 0), r + 0.8, caster)) {
                if (Math.abs(e.getY() - aim.y) < 1.6 && new Vec3(e.getX() - aim.x, 0, e.getZ() - aim.z).length() < r) {
                    light(level, tag, 4);
                    break;
                }
            }
            return false;
        });
    }

    private static void light(ServerLevel level, Tag tag, int ticks) {
        if (!tag.set || tag.gone || tag.fuse >= 0) {
            return;
        }
        tag.fuse = ticks;
        tag.fuse0 = Math.max(1, ticks);
        SubCommon.sound(level, tag.pos, SoundEvents.TNT_PRIMED, 0.5f, 1.6f);
    }

    // Shift+C: all of them, nearest first, one after the other.
    private static boolean detonate(LivingEntity caster, ServerLevel level) {
        List<Tag> armed = new ArrayList<>(tags(caster));
        armed.removeIf(t -> !t.set || t.fuse >= 0);
        if (armed.isEmpty()) {
            SubCommon.warn(caster, "shikamaru.no_armed");
            return false;
        }
        armed.sort(Comparator.comparingDouble(t -> t.pos.distanceToSqr(caster.position())));
        for (int i = 0; i < armed.size(); i++) {
            light(level, armed.get(i), 2 + (int) Math.round(i * 1.4));
        }
        SubCommon.pose(caster, "nr.rato");
        return true;
    }

    private static void boom(LivingEntity caster, ServerLevel level, Tag tag) {
        if (tag.gone) {
            return;
        }
        tag.gone = true;
        Vec3 at = tag.pos;
        double r = 3.0 + 0.8 * (tag.seed % 100) / 100.0;
        long now = level.getGameTime();
        SubNet.send(level, SubNet.TAG, tag.id, at, Vec3.ZERO, 1, 0, 1);
        SubNet.send(level, SubNet.KATON_BOOM, 0, at, new Vec3(at.y, 0, 0), (float) r, 0, tag.seed % 40);
        Long last = SCREEN.get(caster.getId());
        if (last == null || now - last > 12) {
            SCREEN.put(caster.getId(), now);
            DbCommon.screen(level, at, 58);
        }
        SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 0.85f + SubCommon.RANDOM.nextFloat() * 0.2f);
        SubCommon.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
        JjCommon.crater(level, at, 1.2, 0.6, 3);
        // The chain: the neighbours light, the nearer the sooner.
        for (Tag o : tags(caster)) {
            double d = o.pos.distanceTo(at);
            if (o != tag && d < CHAIN) {
                light(level, o, 2 + (int) Math.round(d * 0.7));
            }
        }
        for (LivingEntity t : Combat.inRadius(level, at.add(0, 0.8, 0), r, caster)) {
            double k = 1 - 0.5 * Math.min(1, Combat.center(t).distanceTo(at) / r);
            Combat.hurtMagic(caster, t, (float) (TAG_DAMAGE * k));
            Stun.apply(t, 20);
            long[] h = HITS.computeIfAbsent(t.getId(), x -> new long[] {0, 0, -100});
            if (shadowed(caster, t)) {
                h[0] = now - h[1] > 40 ? 1 : h[0] + 1;
                h[1] = now;
                if (h[0] >= PIT_HITS) {
                    h[0] = 0;
                    pit(caster, level, t);
                }
            } else if (now - h[2] > 10) {
                // One throw per chain: repeated blasts on the same spot don't keep knocking (area damage rule).
                h[2] = now;
                Combat.pushFrom(t, at, 0.9 * k, 0.55);
            }
        }
    }

    // Held by his shadow in the middle of the blasts: the pit, like Hidan. The shadow lets go, the ground caves in under
    // them and they stay down stunned.
    private static void pit(LivingEntity caster, ServerLevel level, LivingEntity target) {
        Hold h = HOLDS.get(caster.getId());
        if (h != null && h.target == target) {
            stop(level, caster, h, SubNet.newId(), 200);
        }
        Vec3 feet = target.position();
        JjCommon.crater(level, feet, 2.4, 1.7, 24);
        Stun.apply(target, PIT_STUN);
        PowerLogic.seal(target, PIT_STUN);
        DbCommon.screen(level, feet, 22);
        SubCommon.sound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.6f);
        SubCommon.sound(level, feet, SoundEvents.ANVIL_LAND, 1f, 0.5f);
    }

    // ---------- V: Xeque-mate ----------

    private static boolean xeque(LivingEntity caster, ServerLevel level) {
        Vec3 c = caster.position();
        List<LivingEntity> near = new ArrayList<>(Combat.inRadius(level, c.add(0, 1, 0), SEA, caster));
        near.removeIf(t -> Math.abs(t.getY() - c.y) > 4 || Freeze.isFrozen(t));
        if (near.isEmpty()) {
            SubCommon.warn(caster, "shikamaru.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int kneel = 20;
        int rise = 12;
        int gap = 4;
        int sea = SubNet.newId();
        SubCommon.pose(caster, "db.ajoelhado");
        SubCommon.busy(caster, kneel + 6);
        Freeze.holdLookFree(caster, kneel);
        // Not crushed while he kneels (a boss can't break it in that second).
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, kneel + 4, 2, false, false));
        SubCommon.sound(level, c, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1f, 0.5f);
        List<LivingEntity> caught = new ArrayList<>();
        Map<LivingEntity, Runnable> releases = new HashMap<>();
        List<Integer> tagIds = new ArrayList<>();
        int[] lastAt = {Integer.MAX_VALUE};
        Tasks.add(age -> {
            if (guard.broken() && caught.isEmpty()) {
                SubNet.send(level, SubNet.SHADOW_SEA, sea, c, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age <= kneel) {
                float k = SubCommon.smooth(age / (float) kneel);
                SubNet.send(level, SubNet.SHADOW_SEA, sea, c, Vec3.ZERO, (float) (0.6 + (SEA - 0.6) * k), 0, 0);
            }
            if (age == kneel) {
                for (LivingEntity t : near) {
                    if (!t.isAlive() || Freeze.isFrozen(t) || t.position().distanceTo(c) > SEA + 1) {
                        continue;
                    }
                    caught.add(t);
                    releases.put(t, Freeze.hold(t, rise + gap * near.size() + 20));
                    PowerLogic.seal(t, rise + gap * near.size() + 20);
                    tagIds.add(SubNet.newId());
                    SubNet.send(level, SubNet.BODY_SHADOW, t.getId(), t.position(), Vec3.ZERO, 0.4f, 0, 0);
                }
                // Nearest first.
                caught.sort(Comparator.comparingDouble(t -> t.position().distanceToSqr(c)));
                SubCommon.pose(caster, "");
                SubCommon.sound(level, c, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.4f);
                lastAt[0] = kneel + rise + gap * Math.max(0, caught.size() - 1);
            }
            if (age > kneel && age <= kneel + rise) {
                float k = SubCommon.smooth((age - kneel) / (float) rise);
                for (int i = 0; i < caught.size(); i++) {
                    Vec3 p = caught.get(i).position().add(0, 0.2 + 1.3 * k, 0);
                    SubNet.send(level, SubNet.TAG, tagIds.get(i), p, Vec3.ZERO, 1.8f, 0.6f * k, 0);
                }
            }
            if (age > kneel + rise) {
                int i = (age - kneel - rise) / gap;
                if ((age - kneel - rise) % gap == 0 && i < caught.size()) {
                    LivingEntity t = caught.get(i);
                    Vec3 at = t.position();
                    // The shadow lets this one go an instant before, so it flies.
                    Runnable rel = releases.remove(t);
                    if (rel != null) {
                        rel.run();
                    }
                    double r = 3.4 + 1.3 * ((t.getId() * 37) % 100) / 100.0;
                    SubNet.send(level, SubNet.TAG, tagIds.get(i), at, Vec3.ZERO, 1.8f, 1, 1);
                    SubNet.send(level, SubNet.BODY_SHADOW, t.getId(), at, Vec3.ZERO, 0, 0, 1);
                    SubNet.send(level, SubNet.KATON_BOOM, 0, at, new Vec3(at.y, 0, 0), (float) r, 0, (t.getId() * 13) % 40);
                    DbCommon.screen(level, at, i == 0 || i == caught.size() - 1 ? 22 : 58);
                    SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 0.75f);
                    JjCommon.crater(level, at, 2.4, 1.1, 18);
                    for (LivingEntity e : Combat.inRadius(level, at.add(0, 0.8, 0), r, caster)) {
                        double k = 1 - 0.4 * Math.min(1, Combat.center(e).distanceTo(at) / r);
                        Combat.hurtMagic(caster, e, (float) (18 * k));
                        Stun.apply(e, 30);
                        if (e == t || !caught.contains(e)) {
                            Combat.pushFrom(e, at.add(0, -1, 0), 0.9 * k, 0.9);
                        }
                    }
                }
                // The ones still waiting keep burning; the next in line burns faster.
                int next = (age - kneel - rise) % gap == 0 ? i + 1 : i;
                for (int j = next; j < caught.size(); j++) {
                    float k = j == next ? SubCommon.smooth(((age - kneel - rise) % gap) / (float) gap) : 0;
                    SubNet.send(level, SubNet.TAG, tagIds.get(j), caught.get(j).position().add(0, 1.5, 0), Vec3.ZERO, 1.8f, 0.6f + 0.35f * k, 0);
                }
            }
            if (age > kneel && age >= lastAt[0]) {
                float k = 1 - SubCommon.smooth((age - lastAt[0]) / 12f);
                SubNet.send(level, SubNet.SHADOW_SEA, sea, c, Vec3.ZERO, (float) (SEA * k), 0, k <= 0 ? 1 : 0);
                if (k <= 0) {
                    releases.values().forEach(Runnable::run);
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    // ---------- B: signal flare ----------

    private static boolean flare(LivingEntity caster, ServerLevel level) {
        SubCommon.setState(caster, FLARE, FLARE_TICKS);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int light = SubNet.newId();
        int stretch = SubNet.newId();
        Vec3 behind = SubCommon.flat(caster.getLookAngle()).scale(-5);
        Vec3 sky = caster.position().add(behind).add(0, 9.5, 0);
        SubCommon.pose(caster, "db.arremesso");
        SubCommon.sound(level, caster.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.8f);
        Tasks.add(age -> {
            boolean end = age >= FLARE_TICKS || guard.broken();
            if (age == 8) {
                SubCommon.sound(level, sky, SoundEvents.FIREWORK_ROCKET_BLAST, 2f, 0.7f);
            }
            float on = Math.min(SubCommon.smooth((age - 6) / 6f), 1 - SubCommon.smooth((age - FLARE_TICKS + 20) / 20f));
            Vec3 at = age < 8 ? caster.position().add(behind.scale(age / 8.0)).add(0, 1.5 + 8 * SubCommon.smooth(age / 8f), 0) : sky.add(0, -age * 0.012, 0);
            SubNet.send(level, SubNet.FLARE, light, at, Vec3.ZERO, Math.max(0.15f, on), age < 8 ? 1 : 0, end ? 1 : 0);
            // His own shadow, stretched away from the light.
            Vec3 from = caster.position();
            Vec3 away = SubCommon.flat(from.subtract(at)).scale(9 * Math.max(0, on));
            SubNet.send(level, SubNet.SHADOW, stretch, from, away, 1, -0.42f, end ? 1 : 0);
            if (end) {
                SubCommon.clearState(caster, FLARE);
            }
            return end;
        });
        return true;
    }
}
