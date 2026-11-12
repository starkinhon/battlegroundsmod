package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Gaara (SubAlma of Naruto, tier 7), the approved preview forja/gaara-kit v1 (gaara.js). Real sand, no glow: every piece
// is drawn by GaaraSand in the page's sand material, with loose grains each of its own size, color and life.
// Passive, the Sand Shield: the first shot or hit from the front every 6 s sticks in a wall of sand that rises on its own
//   (NrCommon.onIncomingDamage). An ult breaks through it.
// Z Sand Hand: he points and the sand springs from the ground 3 blocks ahead into a giant hand (palm, four fingers of
//   three joints, thumb, a forearm of sand) that runs low along the ground to the target (18 blocks) and slaps it away,
//   tearing the ground. HOLD Z: the fingers close round the target and the hand becomes the Coffin.
// X Sabaku Kyu: the sand runs along the ground to the target (24 blocks), climbs the legs and covers the whole body in one
//   shell: held 3 s, no walking, no skills. X again: Sabaku Soso, it squeezes at once and bursts. With someone sinking in
//   his quicksand, X takes them from any distance.
// C Sand Platform: a thick disc of sand under his feet and he flies free and fast, like the creative flight (aim = way,
//   Space up, Shift down, sprint = burst). It burns stamina while it flies, more at speed; out of stamina it crumbles and
//   he floats down. C again: lands.
// V Sabaku Taiso (ult): both hands up; the whole area aimed at (radius 10, up to 30 away) turns into turning quicksand that
//   nobody can leave, a ring wave of sand rises round it as tall as a building, curls in into a dome and falls: heavy
//   damage, a long stun, the area buried in a crater.
// B Quicksand (trap): he touches the ground and the sand runs under it to where he aims (20 blocks); the ground becomes an
//   almost hidden spiral of sand (armed 40 s, two at a time). Whoever steps in sinks to the waist: very slow, no jumping
//   for 4 s, pulled to the middle. Combo with X.
public final class Gaara {
    // One page unit in blocks (the page's Gaara is 2 units tall).
    private static final double PU = 0.9;
    private static final String PLATFORM = "nr_gaara_platform";
    private static final String SAND_BUSY = "nr_gaara_sand_busy";
    private static final String SINKING = "nr_gaara_sinking";
    private static final Map<Integer, Coffin> COFFINS = new HashMap<>();
    // Each Gaara's armed quicksand (two at a time) and who is sinking in one (by the victim).
    private static final Map<UUID, List<Quick>> QUICKS = new HashMap<>();
    private static final Map<Integer, Quick> SINKS = new HashMap<>();

    private static final class Coffin {
        final LivingEntity target;
        final int id;
        boolean crush;
        boolean done;

        Coffin(LivingEntity target, int id) {
            this.target = target;
            this.id = id;
        }
    }

    private static final class Quick {
        final LivingEntity owner;
        final Vec3 at;
        final int id;
        LivingEntity victim;
        boolean taken;
        boolean gone;

        Quick(LivingEntity owner, Vec3 at, int id) {
            this.owner = owner;
            this.at = at;
            this.id = id;
        }
    }

    private Gaara() {
    }

    public static Power create() {
        return new Power("gaara", 0xC9A76B, 0.95f, List.of(
                new Ability(140, 0, Gaara::sandHand),
                new Ability(300, 0, Gaara::coffin),
                new Ability(160, 0, Gaara::platform),
                new Ability(1600, 0, Gaara::taiso),
                new Ability(400, 0, Gaara::quicksand)));
    }

    // The sand is all in the Coffin: it can't guard him at the same time.
    static boolean coffining(LivingEntity gaara) {
        return SubCommon.hasState(gaara, SAND_BUSY);
    }

    // On the platform: the Voo (Flight) leaves his flying alone meanwhile.
    public static boolean flying(LivingEntity e) {
        return SubCommon.hasState(e, PLATFORM);
    }

    private static float yawOf(Vec3 dir) {
        return (float) Math.atan2(dir.x, dir.z);
    }

    private static float e3(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static void grains(ServerLevel level, Vec3 at, Vec3 dirPow, int n, float cone) {
        SubNet.send(level, SubNet.GA_GRAINS, 0, at, dirPow, n, cone, 0);
    }

    // ---------- Z: Sand Hand / hold: the hand closes into the Coffin ----------

    private static boolean sandHand(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity target = NrCommon.target(caster, 20);
        Vec3 me = caster.position();
        Vec3 fwd0 = SubCommon.flat(target != null ? target.position().subtract(me) : caster.getLookAngle());
        if (fwd0.lengthSqr() < 1.0E-6) {
            fwd0 = SubCommon.flat(caster.getLookAngle());
        }
        Vec3 fwd = fwd0;
        Vec3 aim = target != null ? target.position() : SubCommon.ground(level, SubCommon.aimPoint(caster, 18));
        Vec3 base = SubCommon.ground(level, me.add(fwd.scale(3 * PU)));
        int id = SubNet.newId();
        int point = 4;
        int sprout = 10;
        int adv = 11;
        int t0 = point;
        int t1 = t0 + sprout;
        int t2 = t1 + adv;
        // Bots close the hand on a third of their slaps.
        boolean botGrab = SubCommon.RANDOM.nextFloat() < 0.33f;
        boolean[] grab = {false};
        boolean[] hit = {false};
        int[] endAt = {-1};
        Vec3[] wrist = {base};
        Vec3[] goal = {aim};
        float[] lastYaw = {yawOf(fwd)};
        SubCommon.pose(caster, "nr.mao_aberta");
        SubCommon.busy(caster, t2 + 2);
        Tasks.add(age -> {
            if (endAt[0] < 0 && age < t2 && guard.broken()) {
                SubNet.send(level, SubNet.GA_HAND, id, wrist[0], Vec3.ZERO, lastYaw[0], 0, 1);
                return true;
            }
            if (age < t0) {
                return false;
            }
            if (age == t0) {
                SubCommon.sound(level, base, SoundEvents.SAND_BREAK, 1.6f, 0.6f);
                SubCommon.sound(level, base, SoundEvents.ROOTED_DIRT_BREAK, 1.4f, 0.6f);
                JjCommon.crater(level, base, 0.9 * PU, 0.5, 3);
                grains(level, base.add(0, 0.1, 0), new Vec3(0, 5 * PU, 0), 120, 0.5f);
            }
            float lean;
            float curl;
            float wy;
            float rise = 4;
            Vec3 w;
            Vec3 dir = fwd;
            if (age < t1) {
                float k = e3((age - t0) / (float) sprout);
                w = base;
                wy = 0.6f + 1.9f * k;
                lean = 0.15f;
                curl = 0.25f * (1 - k);
                rise = -0.4f + 4.4f * k;
            } else if (age < t2 || endAt[0] < 0 && !hit[0]) {
                // It runs low along the ground to the target, following it.
                if (target != null && target.isAlive() && target.distanceTo(caster) < 26) {
                    goal[0] = target.position();
                }
                Vec3 to = SubCommon.flat(goal[0].subtract(base));
                if (to.lengthSqr() > 1.0E-4) {
                    dir = to;
                }
                Vec3 stop = goal[0].subtract(dir.scale(2.2 * PU));
                if (stop.subtract(base).dot(dir) < 0) {
                    stop = base;
                }
                float s = SubCommon.smooth(Math.min(1, (age - t1) / (float) adv));
                Vec3 flat = base.lerp(stop, s);
                w = SubCommon.ground(level, new Vec3(flat.x, goal[0].y * s + base.y * (1 - s), flat.z));
                wy = 2.5f - 0.3f * s;
                lean = 0.15f + 0.35f * s;
                curl = grab[0] ? 0.1f : 0;
                if (age % 2 == 0) {
                    JjCommon.crater(level, w.add(dir.scale(2.4 * PU)), 0.55 * PU, 0.3, 0);
                }
                if (age == t1 || age == t1 + 6) {
                    SubCommon.sound(level, w, SoundEvents.SAND_PLACE, 1.6f, 0.5f);
                    SubCommon.sound(level, w, SoundEvents.GRINDSTONE_USE, 0.5f, 0.4f);
                }
                if (age == t2 - 2) {
                    grab[0] = caster instanceof Player ? SubCommon.held(caster, age, 0) : botGrab;
                }
                if (age >= t2 - 1) {
                    hit[0] = true;
                    endAt[0] = age + 1;
                }
            } else {
                // The slap (or the grab): from where the run stopped.
                int a = age - endAt[0];
                Vec3 stop = wrist[0];
                dir = SubCommon.flat(goal[0].subtract(stop));
                if (dir.lengthSqr() < 1.0E-4) {
                    dir = fwd;
                }
                if (!grab[0]) {
                    float k = SubCommon.smooth(Math.min(1, a / 2f));
                    w = stop;
                    wy = 2.2f - 0.9f * k;
                    lean = 0.5f + 0.9f * k;
                    curl = 0.15f;
                    Vec3 palmAt = stop.add(dir.scale((1.2 * k + 1.0) * PU));
                    if (a == 2) {
                        slap(caster, level, target, palmAt, dir);
                    }
                    float dis = SubCommon.smooth((a - 10) / 14f);
                    if (a >= 24) {
                        SubNet.send(level, SubNet.GA_HAND, id, w, new Vec3(lean, curl, wy), yawOf(dir), 1, 1);
                        return true;
                    }
                    SubNet.send(level, SubNet.GA_HAND, id, SubCommon.ground(level, palmAt.subtract(dir.scale(1.0 * PU))), new Vec3(lean, curl, wy), yawOf(dir), dis,
                            -(rise + 1));
                    return false;
                }
                float k = SubCommon.smooth(Math.min(1, a / 6f));
                wy = 2.0f - 0.6f * k;
                lean = 0.5f + 0.6f * k;
                curl = 0.1f + 0.85f * k;
                Vec3 palmAt = stop.add(dir.scale((1.6 * k + 2.2) * PU));
                w = SubCommon.ground(level, stop.add(dir.scale(1.6 * k * PU)));
                if (a == 6) {
                    LivingEntity caught = null;
                    for (LivingEntity t : Combat.inRadius(level, palmAt.add(0, 1, 0), 2.6, caster)) {
                        if (caught == null || t == target) {
                            caught = t;
                        }
                    }
                    if (caught != null && !coffinOn(caster, level, caught, null)) {
                        caught = null;
                    }
                    if (caught != null) {
                        DbCommon.cooldown(caster, 1, 20);
                        SubCommon.sound(level, palmAt, SoundEvents.SAND_PLACE, 2f, 0.5f);
                    } else {
                        SubCommon.warn(caster, "gaara.grab_miss");
                    }
                }
                float dis = SubCommon.smooth((a - 6) / 9f);
                if (a >= 16) {
                    SubNet.send(level, SubNet.GA_HAND, id, w, new Vec3(lean, curl, wy), yawOf(dir), 1, 1);
                    return true;
                }
                SubNet.send(level, SubNet.GA_HAND, id, w, new Vec3(lean, curl, wy), yawOf(dir), dis, -(rise + 1));
                return false;
            }
            wrist[0] = w;
            lastYaw[0] = yawOf(dir);
            SubNet.send(level, SubNet.GA_HAND, id, w, new Vec3(lean, curl, wy), lastYaw[0], 0, -(rise + 1));
            return false;
        });
        return true;
    }

    private static void slap(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 palm, Vec3 dir) {
        Vec3 at = palm.add(0, 1, 0);
        boolean any = false;
        for (LivingEntity t : Combat.inRadius(level, at, 2.8, caster)) {
            any = true;
            Combat.hurtMagic(caster, t, 12);
            Stun.apply(t, JjCommon.control(caster, t, 25));
            Combat.push(t, dir, 1.7, 0.6);
        }
        DbCommon.screen(level, at, any ? 0 : 4);
        JjCommon.crater(level, SubCommon.ground(level, palm), 1.4 * PU, 0.8, 6);
        grains(level, palm.add(0, 0.6, 0), dir.add(0, 0.6, 0).normalize().scale(7 * PU), 160, 0.9f);
        SubNet.send(level, SubNet.DUST, 0, SubCommon.ground(level, palm), Vec3.ZERO, 1.2f, 0, 0);
        SubCommon.sound(level, palm, SoundEvents.SAND_BREAK, 2f, 0.5f);
        SubCommon.sound(level, palm, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f);
        SubCommon.sound(level, palm, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.5f);
    }

    // ---------- X: Sabaku Kyu / X again: Sabaku Soso ----------

    private static boolean coffin(LivingEntity caster, ServerLevel level) {
        Coffin c = COFFINS.get(caster.getId());
        if (c != null && !c.done) {
            c.crush = true;
            return false;
        }
        // Someone sinking in his quicksand: taken from any distance.
        for (Quick q : QUICKS.getOrDefault(caster.getUUID(), List.of())) {
            if (q.victim != null && !q.taken && q.victim.isAlive() && SubCommon.hasState(q.victim, SINKING)) {
                q.taken = true;
                SubCommon.pose(caster, "nr.mao_aberta");
                coffinOn(caster, level, q.victim, null);
                DbCommon.cooldown(caster, 1, 20);
                return false;
            }
        }
        LivingEntity target = NrCommon.target(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "gaara.no_target");
            return false;
        }
        SubCommon.pose(caster, "nr.mao_aberta");
        SubCommon.busy(caster, 6);
        Vec3 look = SubCommon.flat(target.position().subtract(caster.position()));
        coffinOn(caster, level, target, caster.position().add(look.scale(0.9 * PU)).add(SubCommon.flat(look.cross(new Vec3(0, 1, 0))).scale(0.3 * PU)));
        DbCommon.cooldown(caster, 1, 20);
        return false;
    }

    // The shell rises round the target (after the sand runs to them along the ground from `from`, when not null) and holds
    // them 3 s; X again crushes it. Not crushed, it crumbles and the cooldown starts. False when it can't start.
    static boolean coffinOn(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 from) {
        Coffin old = COFFINS.get(caster.getId());
        if (old != null && !old.done) {
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Coffin c = new Coffin(target, SubNet.newId());
        COFFINS.put(caster.getId(), c);
        int stream = SubNet.newId();
        int run = from != null ? 10 : 0;
        int rise = 14;
        int full = run + rise;
        int hold = full + JjCommon.control(caster, target, 60);
        float scale = Math.max(0.6f, target.getBbHeight() / 1.8f);
        Quick sunk = SINKS.get(target.getId());
        SubCommon.setState(caster, SAND_BUSY, hold + 20);
        if (from != null) {
            SubCommon.sound(level, from, SoundEvents.SAND_BREAK, 1.4f, 0.7f);
        }
        int[] crushAt = {-1};
        Vec3[] feet = {null};
        Runnable[] release = {null};
        Tasks.add(age -> {
            boolean lost = guard.broken() || !target.isAlive() || target.isRemoved();
            if (age < run) {
                Vec3 to = target.position();
                if (lost || to.distanceTo(from) > 30) {
                    SubNet.send(level, SubNet.GA_STREAM, stream, from, to.subtract(from), age / (float) run, 1, 1);
                    if (!lost) {
                        SubCommon.warn(caster, "gaara.no_target");
                    }
                    end(caster, c);
                    return true;
                }
                SubNet.send(level, SubNet.GA_STREAM, stream, from, to.subtract(from), SubCommon.smooth((age + 1) / (float) run), 0, 0.38f);
                if (age % 3 == 0) {
                    SubCommon.sound(level, from.lerp(to, age / (float) run), SoundEvents.SAND_STEP, 1.2f, 0.6f);
                }
                return false;
            }
            if (feet[0] == null) {
                feet[0] = sunk != null ? sunk.at : target.position();
                release[0] = Freeze.hold(target, hold - run + 6, a -> feet[0]);
                PowerLogic.seal(target, hold - run + 6);
                Stun.apply(target, hold - run + 6);
                if (sunk != null) {
                    endSink(sunk, level);
                }
                SubCommon.sound(level, feet[0], SoundEvents.SAND_PLACE, 1.6f, 0.6f);
                SubCommon.sound(level, feet[0], SoundEvents.SAND_BREAK, 1.2f, 0.5f);
            }
            if (from != null && age <= full + 12) {
                SubNet.send(level, SubNet.GA_STREAM, stream, from, feet[0].subtract(from), 1, SubCommon.smooth((age - full) / 12f), age >= full + 12 ? 1 : 0.38f);
            }
            float grow = SubCommon.smooth((age - run) / (float) rise);
            if (c.crush && crushAt[0] < 0 && age >= run + 4) {
                crushAt[0] = age;
                SubCommon.pose(caster, "nr.punho");
                SubCommon.sound(level, feet[0], SoundEvents.SAND_PLACE, 2f, 0.4f);
            }
            if (caster instanceof Mob && crushAt[0] < 0 && age == full + 20) {
                c.crush = true;
            }
            if (crushAt[0] >= 0) {
                int a = age - crushAt[0];
                if (a < 3) {
                    SubNet.send(level, SubNet.GA_COFFIN, c.id, feet[0], new Vec3(scale, SubCommon.smooth((a + 1) / 3f), 0), grow, 0, 0);
                    return false;
                }
                // Sabaku Soso: the sand squeezes at once and bursts.
                SubNet.send(level, SubNet.GA_COFFIN, c.id, feet[0], new Vec3(scale, 1, 0), grow, 0, 1);
                release[0].run();
                Vec3 mid = feet[0].add(0, 1.2 * PU * scale, 0);
                if (target.isAlive()) {
                    Combat.hurtMagic(caster, target, 24);
                    Stun.apply(target, JjCommon.control(caster, target, 35));
                }
                grains(level, mid, new Vec3(0, 9 * PU, 0), 520, 1.6f);
                grains(level, feet[0].add(0, 0.4 * PU, 0), SubCommon.flat(target.position().subtract(caster.position())).add(0, 0.2, 0).scale(6 * PU), 160, 1.4f);
                SubNet.send(level, SubNet.DUST, 0, feet[0], Vec3.ZERO, 2f, 0, 0);
                JjCommon.crater(level, feet[0], 1.3 * PU, 0.6, 8);
                DbCommon.screen(level, mid, 0);
                SubCommon.sound(level, mid, SoundEvents.SAND_BREAK, 2.5f, 0.4f);
                SubCommon.sound(level, mid, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.4f);
                end(caster, c);
                if (caster.isAlive()) {
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            if (lost || age >= hold) {
                // Not crushed: it crumbles and lets go.
                SubNet.send(level, SubNet.GA_COFFIN, c.id, feet[0], new Vec3(scale, 0, 0), grow, 0, 1);
                grains(level, feet[0].add(0, 1, 0), new Vec3(0, 1.5, 0), 120, 2f);
                release[0].run();
                end(caster, c);
                if (caster.isAlive()) {
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            SubNet.send(level, SubNet.GA_COFFIN, c.id, feet[0], new Vec3(scale, grow >= 1 ? 0.04f * Mth.sin(age * 0.45f) : 0, 0), grow, 0, 0);
            if (age % 10 == 0) {
                SubCommon.sound(level, feet[0], SoundEvents.SAND_PLACE, 0.8f, 0.5f);
            }
            return false;
        });
        return true;
    }

    private static void end(LivingEntity caster, Coffin c) {
        c.done = true;
        COFFINS.remove(caster.getId(), c);
        SubCommon.clearState(caster, SAND_BUSY);
    }

    // ---------- C: Sand Platform (free flight on stamina) ----------

    private static boolean platform(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, PLATFORM)) {
            SubCommon.clearState(caster, PLATFORM);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Player player = caster instanceof Player p ? p : null;
        int gather = 9;
        int melt = 12;
        int mobTicks = 160;
        SubCommon.setState(caster, PLATFORM, 20 * 600);
        SubCommon.pose(caster, "nr.bracos_cruzados");
        DbCommon.cooldown(caster, 2, 10);
        SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.4f, 0.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.SAND_PLACE, 1.2f, 0.5f);
        boolean wasMayfly = player != null && player.getAbilities().mayfly;
        int[] landAt = {-1};
        float[] yaw = {-caster.getYRot() * Mth.DEG_TO_RAD};
        float[] bank = {0};
        double[] budget = {100};
        Vec3[] prev = {caster.position()};
        Tasks.add(age -> {
            Vec3 at = caster.position();
            Vec3 vel = at.subtract(prev[0]);
            prev[0] = at;
            double v = vel.length() * 20 / PU;
            if (landAt[0] < 0) {
                boolean stop = guard.broken() || !SubCommon.hasState(caster, PLATFORM) || Stun.is(caster) || Freeze.isFrozen(caster);
                if (player != null && age > gather + 8 && !player.getAbilities().flying) {
                    // Touched down or let go of the flight.
                    stop = true;
                }
                if (caster instanceof Mob && age > mobTicks) {
                    stop = true;
                }
                if (age > gather) {
                    // Stamina by speed: hovering is cheap, a burst is not (the page: 7.5% * (0.4 + v / 14) a second).
                    double per = 7.5 * (0.4 + v / 14) / 20;
                    if (Stamina.drains(caster)) {
                        Stamina.drain(caster, per);
                        if (Stamina.exhausted(caster)) {
                            stop = true;
                            SubCommon.warn(caster, "gaara.platform_tired");
                        }
                    } else if (player != null && !player.isCreative()) {
                        budget[0] -= per;
                        stop |= budget[0] <= 0;
                    }
                }
                if (stop) {
                    landAt[0] = age;
                    SubCommon.clearState(caster, PLATFORM);
                    SubCommon.pose(caster, "");
                    if (player != null) {
                        Abilities ab = player.getAbilities();
                        ab.flying = false;
                        ab.mayfly = player.isCreative() || player.isSpectator() || wasMayfly
                                || player instanceof ServerPlayer sp && Flight.knows(sp);
                        ab.setFlyingSpeed(0.05f);
                        player.onUpdateAbilities();
                    }
                    caster.setNoGravity(false);
                    if (!caster.onGround()) {
                        // Floats down however high he was (until he touches the ground, at most 30 s).
                        caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false));
                        Tasks.add(a -> {
                            caster.fallDistance = 0;
                            if (a > 2 && (caster.onGround() || caster.isInWater()) || a > 600 || !caster.isAlive()) {
                                caster.removeEffect(MobEffects.SLOW_FALLING);
                                return true;
                            }
                            return false;
                        });
                    }
                    caster.fallDistance = 0;
                    SubCommon.sound(level, at, SoundEvents.SAND_BREAK, 1.4f, 0.6f);
                }
            }
            if (landAt[0] < 0) {
                if (player != null) {
                    if (age == 0) {
                        Vec3 d = caster.getDeltaMovement();
                        caster.setDeltaMovement(d.x, 0.62, d.z);
                        caster.hurtMarked = true;
                    }
                    if (age >= 4) {
                        Abilities ab = player.getAbilities();
                        if (!ab.mayfly || Math.abs(ab.getFlyingSpeed() - 0.1f) > 1e-4 || age == 4 && !ab.flying) {
                            ab.mayfly = true;
                            ab.setFlyingSpeed(0.1f);
                            if (age == 4) {
                                ab.flying = true;
                            }
                            player.onUpdateAbilities();
                        }
                    }
                } else if (age >= gather) {
                    // Bots hover after their target, 3 blocks over the ground.
                    caster.setNoGravity(true);
                    LivingEntity t = caster instanceof Mob m ? m.getTarget() : null;
                    double ground = SubCommon.ground(level, at.subtract(0, 3, 0)).y;
                    Vec3 h = Vec3.ZERO;
                    if (t != null && t.distanceTo(caster) > 5) {
                        h = SubCommon.flat(t.position().subtract(at)).scale(0.4);
                    }
                    caster.setDeltaMovement(h.x, (ground + 3 - at.y) * 0.25, h.z);
                    caster.hurtMarked = true;
                }
                caster.fallDistance = 0;
            }
            // The platform leans into the way it flies.
            Vec3 flat = SubCommon.flat(vel);
            float want = vel.horizontalDistanceSqr() > 0.15 * 0.15 ? yawOf(flat) : -caster.getYRot() * Mth.DEG_TO_RAD;
            float turn = Mth.wrapDegrees((want - yaw[0]) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            yaw[0] += turn * 0.5f;
            bank[0] += (Mth.clamp(-turn * 2, -0.45f, 0.45f) - bank[0]) * 0.2f;
            double dy = vel.length() > 1.0E-4 ? vel.y / vel.length() : 0;
            float pitch = (float) (-Mth.clamp(dy, -0.5, 0.5) * 0.25 - Math.min(0.25, v * 0.012));
            float form = SubCommon.smooth((age + 1) / (float) gather);
            if (landAt[0] >= 0) {
                int a = age - landAt[0];
                float dis = SubCommon.smooth(a / (float) melt);
                SubNet.send(level, SubNet.GA_PLAT, caster.getId(), at, new Vec3(yaw[0], pitch, bank[0] * (1 - dis)), form, dis, a >= melt ? 1 : 0);
                if (a >= melt) {
                    if (caster.isAlive()) {
                        DbCommon.cooldown(caster, 2, 160);
                    }
                    return true;
                }
                return false;
            }
            SubNet.send(level, SubNet.GA_PLAT, caster.getId(), at, new Vec3(yaw[0], pitch, bank[0]), form, 0, 0);
            if (age % 14 == 0) {
                SubCommon.sound(level, at, SoundEvents.SAND_STEP, 0.6f, 0.5f + (float) Math.min(0.6, v * 0.02));
            }
            return false;
        });
        return false;
    }

    // ---------- B: Quicksand (trap) ----------

    private static boolean quicksand(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity aimedAt = caster instanceof Mob m ? m.getTarget() : null;
        Vec3 at = SubCommon.ground(level, aimedAt != null && aimedAt.distanceTo(caster) < 20 ? aimedAt.position() : SubCommon.aimPoint(caster, 20));
        Vec3 look = SubCommon.flat(at.subtract(caster.position()));
        if (look.lengthSqr() < 1.0E-4) {
            look = SubCommon.flat(caster.getLookAngle());
        }
        Vec3 from = caster.position().add(look.scale(0.7 * PU)).add(0, -0.11, 0);
        Quick q = new Quick(caster, at, SubNet.newId());
        List<Quick> mine = QUICKS.computeIfAbsent(caster.getUUID(), k -> new ArrayList<>());
        mine.removeIf(o -> o.gone);
        while (mine.size() >= 2) {
            Quick old = mine.remove(0);
            if (old.victim == null) {
                old.gone = true;
            }
        }
        mine.add(q);
        int stream = SubNet.newId();
        int touch = 5;
        int run = 10;
        int arm = touch + run;
        int armed = 800;
        float r = (float) (2.8 * PU);
        SubCommon.pose(caster, "nr.mao_chao");
        SubCommon.busy(caster, touch + 4);
        int[] sinkAt = {-1};
        int[] sinkFor = {0};
        Tasks.add(age -> {
            if (q.gone && sinkAt[0] < 0 || age < arm && guard.broken() || !caster.isAlive() || age > armed && sinkAt[0] < 0) {
                SubNet.send(level, SubNet.GA_QUICK, q.id, at, new Vec3(r, 0, 0), 0, 0, 1);
                if (age <= arm + 6) {
                    SubNet.send(level, SubNet.GA_STREAM, stream, from, at.subtract(from), 1, 1, 1);
                }
                q.gone = true;
                mine.remove(q);
                return true;
            }
            if (age < touch) {
                return false;
            }
            if (age == touch) {
                SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.2f, 0.6f);
                JjCommon.crater(level, from.add(0, 0.11, 0), 0.5, 0.3, 0);
            }
            if (age <= arm + 6) {
                float pr = SubCommon.smooth((age - touch + 1) / (float) run);
                float dis = SubCommon.smooth((age - arm) / 6f);
                SubNet.send(level, SubNet.GA_STREAM, stream, from, at.subtract(from), pr, dis, age >= arm + 6 ? 1 : 0.3f);
                if (age % 3 == 0 && age < arm) {
                    SubCommon.sound(level, from.lerp(at, pr), SoundEvents.SAND_STEP, 1f, 0.5f);
                }
            }
            if (age < arm) {
                return false;
            }
            if (age == arm) {
                SubCommon.sound(level, at, SoundEvents.SAND_PLACE, 1.4f, 0.4f);
            }
            if (sinkAt[0] < 0) {
                SubNet.send(level, SubNet.GA_QUICK, q.id, at, new Vec3(r, 0, 0), 0.55f * SubCommon.smooth((age - arm) / 8f), 0, 0);
                for (LivingEntity t : Combat.inRadius(level, at.add(0, 0.5, 0), 1.9, caster)) {
                    if (Math.abs(t.getY() - at.y) < 1.4 && !SubCommon.hasState(t, SINKING)) {
                        q.victim = t;
                        sinkAt[0] = age;
                        sinkFor[0] = JjCommon.control(caster, t, 80);
                        SubCommon.setState(t, SINKING, sinkFor[0] + 2);
                        SINKS.put(t.getId(), q);
                        Combat.hurtMagic(caster, t, 3);
                        SubCommon.sound(level, at, SoundEvents.SAND_BREAK, 1.8f, 0.4f);
                        SubCommon.sound(level, at, SoundEvents.MUD_STEP, 1.6f, 0.5f);
                        break;
                    }
                }
                return false;
            }
            int b = age - sinkAt[0];
            LivingEntity t = q.victim;
            float sink = SubCommon.smooth(Math.min(1, b / 14f));
            boolean over = b >= sinkFor[0] || !t.isAlive() || q.taken && b > 0 && !SubCommon.hasState(t, SINKING);
            if (over) {
                if (!q.taken) {
                    endSink(q, level);
                }
                SubNet.send(level, SubNet.GA_QUICK, q.id, at, new Vec3(r, 0, 0), 0, 0, 1);
                q.gone = true;
                mine.remove(q);
                return true;
            }
            SubNet.send(level, SubNet.GA_QUICK, q.id, at, new Vec3(r, 0, 0), q.taken ? 0.55f + 0.45f * sink : 0.55f + 0.45f * sink, sink, 0);
            if (!q.taken) {
                holdIn(t, at, 0.12);
                if (b % 8 == 0) {
                    SubCommon.sound(level, at, SoundEvents.SAND_STEP, 1.2f, 0.4f);
                }
            }
            return false;
        });
        return true;
    }

    // Stuck in sand: pulled toward center, very slow, no jumping.
    private static void holdIn(LivingEntity t, Vec3 center, double pull) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 5, false, false));
        Vec3 to = SubCommon.flat(center.subtract(t.position()));
        double d = new Vec3(center.x - t.getX(), 0, center.z - t.getZ()).length();
        Vec3 v = t.getDeltaMovement();
        double vy = Math.min(v.y, 0);
        double k = Math.min(d, pull);
        t.setDeltaMovement(v.x * 0.3 + to.x * k, vy, v.z * 0.3 + to.z * k);
        t.hurtMarked = true;
        t.fallDistance = 0;
    }

    private static void endSink(Quick q, ServerLevel level) {
        if (q.victim != null) {
            SubCommon.clearState(q.victim, SINKING);
            SINKS.remove(q.victim.getId(), q);
        }
    }

    // ---------- V: Sabaku Taiso ----------

    private static boolean taiso(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity aimedAt = caster instanceof Mob m ? m.getTarget() : NrCommon.target(caster, 30);
        Vec3 c = SubCommon.ground(level, aimedAt != null && aimedAt.distanceTo(caster) < 32 ? aimedAt.position() : SubCommon.aimPoint(caster, 30));
        double radius = 10;
        float rPage = (float) (radius / PU);
        float quickR = (float) ((rPage + 1.2f) * PU);
        int wave = SubNet.newId();
        int quick = SubNet.newId();
        int raise = 22;
        int up = 21;
        int curl = 30;
        int t1 = raise;
        int t2 = t1 + up;
        int t3 = t2 + curl;
        int impact = t3 + 4;
        int end = t3 + 52;
        Runnable shield = Intangible.shield(caster, impact + 2);
        Map<LivingEntity, Runnable> held = new HashMap<>();
        Set<Integer> hit = new HashSet<>();
        SubCommon.pose(caster, "nr.mao_alto");
        SubCommon.busy(caster, impact + 4);
        SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.6f, 0.4f);
        Tasks.add(age -> {
            if (age < impact && guard.broken()) {
                shield.run();
                held.values().forEach(Runnable::run);
                SubNet.send(level, SubNet.GA_QUICK, quick, c, new Vec3(quickR, 0, 0), 0, 0, 1);
                SubNet.send(level, SubNet.GA_WAVE, wave, c, new Vec3(rPage, 1, 1), 0, 0, 1);
                return true;
            }
            if (age < impact) {
                // The area is quicksand: whoever is in it can't leave.
                float qa = SubCommon.smooth((age - 4) / 16f);
                SubNet.send(level, SubNet.GA_QUICK, quick, c, new Vec3(quickR, 0, 0), qa, SubCommon.smooth((age - 4) / 20f), 0);
                if (age >= 8) {
                    for (LivingEntity t : Combat.inRadius(level, c.add(0, 1, 0), radius, caster)) {
                        if (!held.containsKey(t) && Math.abs(t.getY() - c.y) < 6) {
                            int ticks = JjCommon.control(caster, t, impact - age + 10);
                            held.put(t, Freeze.holdLoose(t, ticks, p -> inside(p, c, radius - 0.6)));
                            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, false));
                        }
                    }
                }
                if (age % 6 == 0) {
                    SubCommon.sound(level, c, SoundEvents.SAND_STEP, 2f, 0.4f);
                }
            }
            if (age >= t1 && age < impact) {
                float ext;
                float phi;
                if (age < t2) {
                    float k = e3((age - t1) / 20f);
                    ext = 0.5f + 8.5f * k;
                    phi = 0.12f * k;
                    if (age == t1) {
                        SubCommon.sound(level, c, SoundEvents.SAND_BREAK, 3f, 0.3f);
                        SubCommon.sound(level, c, SoundEvents.GRINDSTONE_USE, 1f, 0.3f);
                    }
                } else {
                    float k = SubCommon.smooth((age - t2) / 22f);
                    ext = 9 + 3.5f * k;
                    phi = 0.12f + 1.15f * k;
                }
                float sy = 1;
                if (age >= t3) {
                    sy = Math.max(0.05f, 1 - 0.95f * SubCommon.smooth((age - t3) / 4.4f));
                    if (age == t3) {
                        SubCommon.pose(caster, "nr.punho");
                    }
                }
                SubNet.send(level, SubNet.GA_WAVE, wave, c, new Vec3(rPage, 0, sy), ext, phi, 0);
                if (age % 5 == 0) {
                    SubCommon.sound(level, c, SoundEvents.SAND_PLACE, 2.5f, 0.3f);
                }
                return false;
            }
            if (age == impact) {
                shield.run();
                SubNet.send(level, SubNet.GA_QUICK, quick, c, new Vec3(quickR, 0, 0), 0, 1, 1);
                for (LivingEntity t : Combat.inRadius(level, c.add(0, 1, 0), radius + 1, caster)) {
                    if (hit.add(t.getId())) {
                        Runnable r = held.remove(t);
                        if (r != null) {
                            r.run();
                        }
                        Combat.hurtMagic(caster, t, 45);
                        Stun.apply(t, JjCommon.control(caster, t, 60));
                    }
                }
                held.values().forEach(Runnable::run);
                held.clear();
                DbCommon.bigCrater(level, c, radius * 0.95, 3, 10);
                SubNet.send(level, SubNet.GA_GRAINS, 0, c, Vec3.ZERO, 900, (float) radius, 1);
                SubNet.send(level, SubNet.DUST, 0, c, Vec3.ZERO, 6f, 0, 0);
                DbCommon.screen(level, c.add(0, 2, 0), 0);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                SubCommon.sound(level, c, SoundEvents.SAND_BREAK, 3f, 0.3f);
                SubCommon.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
            }
            if (age >= impact) {
                float dis = SubCommon.smooth((age - t3 - 7) / 10f);
                SubNet.send(level, SubNet.GA_WAVE, wave, c, new Vec3(rPage, dis, 0.05), 12.5f, 1.27f, age >= end ? 1 : 0);
                if (age == impact + 8) {
                    SubCommon.pose(caster, "");
                }
                return age >= end;
            }
            return false;
        });
        return true;
    }

    // p kept within r of c (flat).
    private static Vec3 inside(Vec3 p, Vec3 c, double r) {
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= r) {
            return p;
        }
        return new Vec3(c.x + dx / d * r, p.y, c.z + dz / d * r);
    }
}
