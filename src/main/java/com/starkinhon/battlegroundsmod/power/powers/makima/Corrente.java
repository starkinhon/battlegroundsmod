package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Her chains (forja/makima-correntes, approved 2026-11-11). X: one chain flies out of her hand, wraps the target's chest
// and holds them kneeling while red pulses run from them to her hand: stunned and drained of stamina. V without souls:
// chains burst out of the ground around everyone near (each body bound at its neck, chest, arms and legs, every chain
// its own); V again: "Morram." and they squeeze until the bodies burst. The chain's look (link by link, every link
// forged its own) is drawn by client/fx/MakimaFx on the same timeline as the preview.
public final class Corrente {
    // X, in ticks (the preview's seconds x 20): the chain reaches the target, wraps it, lets go.
    private static final int X_ARRIVE = 15;
    private static final int X_WRAP = 26;
    private static final int X_RELEASE = 112;
    private static final double X_RANGE = 22;
    private static final float X_DAMAGE = 16;
    // V: radius, when the chains come out (0.75 s) and have the bodies (about 1.85 s), when they go back without a
    // "Morram" (7.6 s), and the earliest "Morram" (2.9 s, the preview's).
    private static final double V_RADIUS = 14;
    private static final int V_MAX = 8;
    private static final int V_BIND = 37;
    private static final int V_RELEASE = 152;
    private static final int V_MORRAM_EARLIEST = 58;
    private static final float V_DAMAGE = 14;
    private static final float MORRAM_DAMAGE = 150;

    private static final class Ground {
        final LivingEntity caster;
        final List<LivingEntity> targets = new ArrayList<>();
        final List<Runnable> releases = new ArrayList<>();
        final long start;
        boolean morram;
        boolean over;

        Ground(LivingEntity caster) {
            this.caster = caster;
            this.start = caster.level().getGameTime();
        }
    }

    private static final Map<UUID, Ground> GROUND = new HashMap<>();

    private Corrente() {
    }

    // ---------- X ----------

    public static boolean use(LivingEntity caster, ServerLevel level) {
        LivingEntity target = aimed(caster, level, X_RANGE);
        SubCommon.pose(caster, "makima.corrente");
        Makima.eyes(caster, X_RELEASE + 24);
        SubCommon.busy(caster, X_ARRIVE + 2);
        SubCommon.sound(level, caster.position(), SoundEvents.CHAIN_PLACE, 1.2f, 0.7f);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.2f, 0.6f);
        if (target == null) {
            // Missed: the chain flies to where she pointed and goes back.
            Vec3 end = Combat.raycastPoint(caster, X_RANGE);
            MkNet.send(level, MkNet.X_CHAIN, caster.getId(), end, new Vec3(-1, 0, 0), 20, 0, 0);
            DbCommon.cooldown(caster, 1, 80);
            SubCommon.warn(caster, "makima.x_miss");
            return true;
        }
        MkNet.send(level, MkNet.X_CHAIN, caster.getId(), target.position(), new Vec3(target.getId(), 0, 0), X_RELEASE, 0, 0);
        DbCommon.cooldown(caster, 1, 240);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Runnable[] hold = new Runnable[1];
        Tasks.add(age -> {
            boolean lost = !target.isAlive() || target.isRemoved() || target.level() != level || target.distanceTo(caster) > X_RANGE + 6;
            if (guard.broken() || lost) {
                if (hold[0] != null) {
                    hold[0].run();
                }
                MkNet.send(level, MkNet.X_CHAIN, caster.getId(), target.position(), new Vec3(target.getId(), 0, 0), 0, 0, 1);
                return true;
            }
            if (age == X_ARRIVE) {
                hold[0] = Freeze.hold(target, X_RELEASE - X_ARRIVE);
                Combat.hurt(caster, target, X_DAMAGE);
                SubCommon.sound(level, target.position(), SoundEvents.CHAIN_HIT, 1.4f, 0.6f);
            }
            if (age == X_WRAP) {
                Stun.apply(target, 50);
                SubCommon.pose(target, "makima.preso");
                SubCommon.sound(level, target.position(), SoundEvents.CHAIN_BREAK, 0.8f, 0.5f);
            }
            if (age > X_WRAP && age < X_RELEASE) {
                // The stamina runs out of them down the chain (80% over the hold); a mob without stamina bleeds a little.
                if (target instanceof Player) {
                    Stamina.drain(target, 80.0 / (X_RELEASE - X_WRAP));
                } else if (age % 10 == 0) {
                    Combat.hurt(caster, target, 2);
                }
            }
            if (age >= X_RELEASE) {
                if (hold[0] != null) {
                    hold[0].run();
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // The first one along her aim (generous: the chain is thick and she aims with the hand), never through a wall.
    static LivingEntity aimed(LivingEntity caster, ServerLevel level, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = Combat.raycastPoint(caster, range);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : Combat.alongLine(level, eye, end, 1.1, caster)) {
            if (Makima.ours(caster, e) || !e.isAlive()) {
                continue;
            }
            double d = e.distanceToSqr(caster);
            if (d < bestD && seen(level, eye, e)) {
                best = e;
                bestD = d;
            }
        }
        LivingEntity direct = Combat.raycastTarget(caster, range);
        if (direct != null && !Makima.ours(caster, direct) && (best == null || direct.distanceToSqr(caster) <= bestD)) {
            return direct;
        }
        return best;
    }

    static boolean seen(ServerLevel level, Vec3 from, LivingEntity e) {
        Vec3 to = Combat.center(e);
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
    }

    // ---------- V without souls ----------

    public static boolean ground(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> around = new ArrayList<>();
        for (LivingEntity e : Combat.inRadius(level, caster.position(), V_RADIUS, caster)) {
            if (!Makima.ours(caster, e) && e.isAlive()) {
                around.add(e);
            }
        }
        if (around.isEmpty()) {
            SubCommon.warn(caster, "makima.v_none");
            return false;
        }
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        Ground g = new Ground(caster);
        for (int i = 0; i < Math.min(V_MAX, around.size()); i++) {
            g.targets.add(around.get(i));
        }
        GROUND.put(caster.getUUID(), g);
        SubCommon.pose(caster, "makima.v_ergue");
        Makima.eyes(caster, V_RELEASE + 30);
        SubCommon.busy(caster, 30);
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_EMERGE, 0.8f, 1.3f);
        for (int i = 0; i < g.targets.size(); i++) {
            LivingEntity t = g.targets.get(i);
            MkNet.send(level, MkNet.V_CHAINS, t.getId(), t.position(), caster.getId(), i, 0);
            MkNet.send(level, MkNet.CRACK, t.getId(), t.position(), 3.9f, 0, t.getId() * 3.7f + 1.3f);
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            if (g.over) {
                return true;
            }
            if (guard.broken() && !g.morram) {
                end(level, g);
                return true;
            }
            if (age == 15) {
                for (LivingEntity t : g.targets) {
                    SubCommon.sound(level, t.position(), SoundEvents.CHAIN_PLACE, 1.4f, 0.5f);
                    SubCommon.sound(level, t.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.2f, 0.6f);
                }
            }
            for (int i = 0; i < g.targets.size(); i++) {
                LivingEntity t = g.targets.get(i);
                if (age == V_BIND + i && t.isAlive()) {
                    g.releases.add(Freeze.hold(t, V_RELEASE - V_BIND));
                    Stun.apply(t, 60);
                    Combat.hurt(caster, t, V_DAMAGE);
                    SubCommon.pose(t, "makima.amarrado");
                    SubCommon.sound(level, t.position(), SoundEvents.CHAIN_HIT, 1.2f, 0.5f);
                }
            }
            if (age >= V_RELEASE && !g.morram) {
                end(level, g);
                return true;
            }
            return false;
        });
        return true;
    }

    // V again while the chains hold: "Morram." (from 2.9 s on; earlier it waits for that).
    public static boolean morram(LivingEntity caster) {
        Ground g = GROUND.get(caster.getUUID());
        if (g == null || g.over || g.morram || !(caster.level() instanceof ServerLevel level)) {
            return false;
        }
        g.morram = true;
        int wait = (int) Math.max(0, V_MORRAM_EARLIEST - (level.getGameTime() - g.start));
        Tasks.later(wait, () -> crush(level, g));
        return true;
    }

    private static void crush(ServerLevel level, Ground g) {
        LivingEntity caster = g.caster;
        Makima.say(caster, "morram");
        SubCommon.pose(caster, "makima.morram");
        Makima.eyes(caster, 60);
        Makima.gray(level, caster.position(), 50, 0.85f);
        for (LivingEntity t : g.targets) {
            MkNet.send(level, MkNet.V_CHAINS, t.getId(), t.position(), caster.getId(), 0, 1);
        }
        Tasks.later(9, () -> {
            for (LivingEntity t : g.targets) {
                if (!t.isAlive()) {
                    continue;
                }
                Vec3 c = Combat.center(t);
                MkNet.send(level, MkNet.BITS, t.getId(), c, Vec3.ZERO, 46, 1, 1);
                MkNet.send(level, MkNet.MIST, t.getId(), c, new Vec3(0, 0.3, 0), 60, 0, 0);
                SubCommon.sound(level, c, SoundEvents.SLIME_SQUISH, 1.5f, 0.5f);
                SubCommon.sound(level, c, SoundEvents.CHAIN_BREAK, 1.4f, 0.4f);
                kill(caster, t, MORRAM_DAMAGE);
            }
        });
        Tasks.later(15, () -> end(level, g));
        DbCommon.cooldown(caster, PowerData.SLOT_ULT, 200);
    }

    // A blow meant to kill: the common mobs just die; players and bosses take the damage.
    static void kill(LivingEntity caster, LivingEntity t, float damage) {
        if (t instanceof Mob && !(t instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss)) {
            Combat.hurt(caster, t, Math.max(damage, t.getHealth() * 4 + 50));
        } else {
            Combat.hurt(caster, t, damage);
        }
    }

    private static void end(ServerLevel level, Ground g) {
        if (g.over) {
            return;
        }
        g.over = true;
        for (Runnable r : g.releases) {
            r.run();
        }
        for (LivingEntity t : g.targets) {
            MkNet.send(level, MkNet.V_CHAINS, t.getId(), t.position(), g.caster.getId(), 0, 2);
        }
        if (GROUND.get(g.caster.getUUID()) == g) {
            GROUND.remove(g.caster.getUUID());
        }
    }

    public static void clear() {
        GROUND.clear();
    }
}
