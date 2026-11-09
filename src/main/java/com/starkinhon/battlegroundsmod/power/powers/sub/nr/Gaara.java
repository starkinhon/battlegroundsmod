package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Gaara (SubAlma of Naruto, tier 7), the Visualizador's Gaara tab. Real sand, no glow.
// Passive, the Sand Shield: the first shot or hit from the front every 6 s sticks in a wall of sand that rises on its own
//   (NrCommon.onIncomingDamage). An ult breaks through it.
// Z Sand Hand: the sand rises from the ground into a giant hand that tips forward and slaps the target ~6 blocks away.
//   Held: the hand closes on the target instead and the Coffin starts on it.
// X Sabaku Kyu (Sand Coffin): the sand runs along the ground to the target and swallows the whole body in a shell: held 3 s,
//   no walking, no skills. X again: Sabaku Soso, the funeral: the sand squeezes at once and bursts.
// C Sand Platform: the sand gathers under his feet and lifts him ~3 blocks; he flies where he aims for up to 10 s (Z and X
//   still work from up there). C again: down, and the platform crumbles.
public final class Gaara {
    private static final String PLATFORM = "nr_gaara_platform";
    private static final String SAND_BUSY = "nr_gaara_sand_busy";
    private static final Map<Integer, Coffin> COFFINS = new HashMap<>();

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

    private Gaara() {
    }

    public static Power create() {
        return new Power("gaara", 0xC9A76B, 0.95f, List.of(
                new Ability(140, 0, Gaara::sandHand),
                new Ability(300, 0, Gaara::coffin),
                new Ability(240, 0, Gaara::platform),
                new Ability(1000, 0, Gaara::ultMissing)));
    }

    // The sand is all in the Coffin: it can't guard him at the same time.
    static boolean coffining(LivingEntity gaara) {
        return SubCommon.hasState(gaara, SAND_BUSY);
    }

    // ---------- Z: Sand Hand ----------

    private static boolean sandHand(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity target = NrCommon.target(caster, 34);
        Vec3 goal = target != null ? target.position() : SubCommon.ground(level, SubCommon.aimPoint(caster, 34));
        Vec3 dir = SubCommon.flat(goal.subtract(caster.position()));
        Vec3 flatMe = new Vec3(caster.getX(), goal.y, caster.getZ());
        Vec3 base0 = goal.subtract(dir.scale(4.6));
        if (base0.subtract(flatMe).dot(dir) < 2.6) {
            base0 = flatMe.add(dir.scale(2.6));
        }
        Vec3 from = SubCommon.ground(level, base0);
        Vec3 to = SubCommon.ground(level, from.add(dir.scale(4)));
        int id = SubNet.newId();
        int rise = 14;
        int adv = 36;
        int slap = 48;
        int gone = slap + 40;
        boolean bossGrab = SubCommon.RANDOM.nextFloat() < 0.3f;
        boolean[] grab = {false};
        SubCommon.pose(caster, "nr.mao_aberta");
        SubCommon.busy(caster, slap + 2);
        SubNet.send(level, SubNet.SAND_FLOW, 0, caster.position(), from.subtract(caster.position()), rise, 0, 0);
        SubCommon.sound(level, from, SoundEvents.SAND_BREAK, 1.2f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken() && age < slap) {
                SubNet.send(level, SubNet.SAND_HAND, id, from, dir, 0, 0, 1);
                return true;
            }
            float form = SubCommon.smooth((age - rise) / 18f);
            float sink = age > slap + 22 ? SubCommon.smooth((age - slap - 22) / 18f) : 0;
            float swing = age < adv ? 0 : age < slap ? SubCommon.smooth((age - adv) / (float) (slap - adv)) * 0.95f
                    : 0.95f - SubCommon.smooth((age - slap - 6) / 14f) * 0.4f;
            Vec3 base = age < adv ? from : from.lerp(to, SubCommon.smooth((age - adv) / (float) (slap - adv)));
            if (age == adv - 2) {
                grab[0] = caster instanceof Player ? SubCommon.held(caster, age, 0) : bossGrab;
            }
            boolean closed = grab[0] && age >= slap - 4;
            if (age >= rise) {
                SubNet.send(level, SubNet.SAND_HAND, id, base, dir, Math.max(0, form - sink), swing, age >= gone ? 1 : closed ? 2 : 0);
            }
            if (age == rise || age == adv) {
                SubCommon.sound(level, base, SoundEvents.SAND_PLACE, 1.6f, 0.5f);
                SubCommon.sound(level, base, SoundEvents.GRINDSTONE_USE, 0.5f, 0.4f);
            }
            if (age == slap) {
                Vec3 palm = to.add(dir.scale(1.6)).add(0, 1, 0);
                LivingEntity first = null;
                for (LivingEntity t : Combat.inRadius(level, palm, 2.6, caster)) {
                    if (first == null || t == target) {
                        first = t;
                    }
                }
                if (grab[0]) {
                    if (first != null) {
                        coffinOn(caster, level, first, false);
                        DbCommon.cooldown(caster, 1, 20);
                    } else {
                        SubCommon.warn(caster, "gaara.grab_miss");
                    }
                } else {
                    for (LivingEntity t : Combat.inRadius(level, palm, 2.6, caster)) {
                        Combat.hurt(caster, t, 9);
                        Stun.apply(t, 20);
                        Combat.push(t, dir, 1.5, 0.55);
                        SubNet.send(level, SubNet.SAND_BURST, 0, Combat.center(t), Vec3.ZERO, 0.8f, 0, 0);
                    }
                    DbCommon.screen(level, palm, 15);
                }
                SubCommon.sound(level, palm, SoundEvents.SAND_BREAK, 2f, 0.5f);
                SubCommon.sound(level, palm, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
            }
            return age >= gone;
        });
        return true;
    }

    // ---------- X: Sand Coffin / Funeral ----------

    private static boolean coffin(LivingEntity caster, ServerLevel level) {
        Coffin c = COFFINS.get(caster.getId());
        if (c != null && !c.done) {
            c.crush = true;
            return true;
        }
        LivingEntity target = NrCommon.target(caster, 20);
        if (target == null) {
            SubCommon.warn(caster, "gaara.no_target");
            return false;
        }
        SubCommon.pose(caster, "nr.mao_aberta");
        coffinOn(caster, level, target, true);
        DbCommon.cooldown(caster, 1, 20);
        return false;
    }

    // The shell grows round the target (after the sand runs to them along the ground, when flow) and holds them 3 s; X
    // again crushes it. Not crushed, it crumbles and the cooldown starts.
    static void coffinOn(LivingEntity caster, ServerLevel level, LivingEntity target, boolean flow) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Coffin c = new Coffin(target, SubNet.newId());
        COFFINS.put(caster.getId(), c);
        int start = flow ? 12 : 0;
        int growTicks = flow ? 26 : 16;
        int full = start + growTicks;
        int hold = full + 60;
        float scale = target.getBbHeight() / 1.8f;
        if (flow) {
            SubNet.send(level, SubNet.SAND_FLOW, 0, caster.position(), target.position().subtract(caster.position()), start, 0, 0);
            SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.2f, 0.7f);
        }
        SubCommon.setState(caster, SAND_BUSY, hold + 10);
        int[] crushAt = {-1};
        Vec3[] feet = {null};
        Tasks.add(age -> {
            boolean lost = guard.broken() || !target.isAlive();
            if (age < start) {
                if (lost) {
                    end(caster, c);
                    return true;
                }
                return false;
            }
            if (feet[0] == null) {
                feet[0] = target.position();
                Freeze.hold(target, hold - start + 6);
                PowerLogic.seal(target, hold - start + 6);
                Stun.apply(target, 20);
                SubCommon.sound(level, feet[0], SoundEvents.SAND_PLACE, 1.6f, 0.6f);
            }
            if (c.crush && crushAt[0] < 0) {
                crushAt[0] = age;
                SubCommon.pose(caster, "nr.punho");
            }
            if (caster instanceof Mob && crushAt[0] < 0 && age == full + 20) {
                c.crush = true;
            }
            float grow = SubCommon.smooth((age - start) / (float) growTicks);
            if (crushAt[0] >= 0) {
                int a = age - crushAt[0];
                SubNet.send(level, SubNet.SAND_SHELL, c.id, feet[0], new Vec3(scale, 0, 0), grow, SubCommon.smooth(a / 3f), a >= 4 ? 1 : 0);
                if (a >= 4) {
                    Freeze.free(target);
                    Vec3 mid = feet[0].add(0, 1.3 * scale, 0);
                    if (target.isAlive()) {
                        Combat.hurtMagic(caster, target, 22);
                        Stun.apply(target, 30);
                    }
                    SubNet.send(level, SubNet.SAND_BURST, 0, mid, Vec3.ZERO, 2.2f * scale, 0, 0);
                    SubNet.send(level, SubNet.DUST, 0, feet[0], Vec3.ZERO, 2f, 0, 0);
                    DbCommon.screen(level, mid, 20);
                    SubCommon.sound(level, mid, SoundEvents.SAND_BREAK, 2.5f, 0.4f);
                    SubCommon.sound(level, mid, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.4f);
                    end(caster, c);
                    return true;
                }
                return false;
            }
            if (lost || age >= hold) {
                // Not crushed: it crumbles and lets go.
                SubNet.send(level, SubNet.SAND_SHELL, c.id, feet[0], new Vec3(scale, 0, 0), grow, 0, 1);
                SubNet.send(level, SubNet.SAND_BURST, 0, feet[0].add(0, 1, 0), Vec3.ZERO, 0.8f * scale, 0, 0);
                Freeze.free(target);
                end(caster, c);
                if (caster.isAlive()) {
                    DbCommon.cooldown(caster, 1, 300);
                }
                return true;
            }
            SubNet.send(level, SubNet.SAND_SHELL, c.id, feet[0], new Vec3(scale, 0, 0), grow, 0, 0);
            if (age % 10 == 0) {
                SubCommon.sound(level, feet[0], SoundEvents.SAND_PLACE, 0.8f, 0.5f);
            }
            return false;
        });
    }

    private static void end(LivingEntity caster, Coffin c) {
        c.done = true;
        COFFINS.remove(caster.getId(), c);
        SubCommon.clearState(caster, SAND_BUSY);
    }

    // ---------- C: Sand Platform ----------

    private static boolean platform(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, PLATFORM)) {
            SubCommon.clearState(caster, PLATFORM);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int gather = 22;
        int up = 52;
        int flyTicks = 200;
        int down = 22;
        int melt = 12;
        SubCommon.setState(caster, PLATFORM, up + flyTicks);
        SubCommon.pose(caster, "nr.bracos_cruzados");
        SubCommon.busy(caster, gather);
        DbCommon.cooldown(caster, 2, 20);
        SubNet.send(level, SubNet.SAND_FLOW, 0, caster.position(), new Vec3(3.2, 0, 0), gather, 1, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.4f, 0.6f);
        int[] landAt = {-1};
        double[] height = {0};
        Tasks.add(age -> {
            boolean broken = guard.broken() || Stun.is(caster) && age > up;
            double ground = SubCommon.ground(level, caster.position().subtract(0, height[0], 0)).y;
            if (landAt[0] < 0 && age > gather && (broken || !SubCommon.hasState(caster, PLATFORM))) {
                landAt[0] = age;
                SubCommon.clearState(caster, PLATFORM);
                SubCommon.pose(caster, "");
            }
            float form = SubCommon.smooth((age - gather + 8) / 10f);
            if (landAt[0] < 0) {
                double want = age < gather ? 0 : 3.2 * SubCommon.smooth((age - gather) / (float) (up - gather));
                height[0] = want;
            } else {
                int a = age - landAt[0];
                height[0] = Math.max(0, height[0] * (1 - SubCommon.smooth(a / (float) down)));
                if (a >= down + melt) {
                    caster.setNoGravity(false);
                    SubNet.send(level, SubNet.SAND_PLAT, caster.getId(), caster.position(), new Vec3(ground, 0, 0), form, 1, 1);
                    SubNet.send(level, SubNet.SAND_BURST, 0, caster.position().add(0, 0.3, 0), Vec3.ZERO, 0.6f, 0, 0);
                    SubCommon.sound(level, caster.position(), SoundEvents.SAND_BREAK, 1.4f, 0.6f);
                    if (caster.isAlive()) {
                        DbCommon.cooldown(caster, 2, 240);
                    }
                    return true;
                }
            }
            if (age >= gather) {
                caster.setNoGravity(true);
                caster.fallDistance = 0;
                double wantY = ground + height[0];
                double vy = (wantY - caster.getY()) * 0.3;
                Vec3 h = Vec3.ZERO;
                if (landAt[0] < 0 && age > up - 10 && caster instanceof Player) {
                    // Flies where he aims, at a run.
                    h = SubCommon.flat(caster.getLookAngle()).scale(0.22);
                }
                Vec3 v = caster.getDeltaMovement();
                caster.setDeltaMovement(caster instanceof Player ? h.x : v.x, vy, caster instanceof Player ? h.z : v.z);
                caster.hurtMarked = true;
                int a = landAt[0] < 0 ? 0 : age - landAt[0];
                float m = landAt[0] < 0 ? 0 : SubCommon.smooth((a - down) / (float) melt);
                SubNet.send(level, SubNet.SAND_PLAT, caster.getId(), caster.position(), new Vec3(ground, 0, 0), form, m, 0);
                if (age % 12 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.SAND_PLACE, 0.5f, 0.6f);
                }
            }
            return false;
        });
        return false;
    }

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
