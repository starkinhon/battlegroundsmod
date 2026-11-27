// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Itadori (SubAlma of Jujutsu, tier 6), kit remade with Stark (2026-11-27, preview forja/itadori-kit/shinjuku.js, all
// approved): what he learned in Shinjuku.
// Z Punho Divergente (mode): the cursed energy lights both fists (dense, blue inside, black edge) and lags behind them.
//   For 8 punches (or 8 s) every plain punch lands twice: the punch and, a moment later, the echo, which pushes a little.
//   On the 5th punch the Black Flash ring closes on the target: Z right when it closes is a Black Flash (2.5x, black
//   lightning, a throw); a miss is just the double punch. The Black Flash has no key of its own any more.
// X Teia de Tesoura: his palm on the ground and a web of thin faint threads opens round him; a pair of scissors runs along
//   every spoke turning it into a cut, and every ring it crosses cuts at once. Then the web goes, the ground bursts
//   along the threads, and whoever stands on one is cut and thrown up.
// C Corte de Tesoura (his Dismantle): two hand cuts in the air and the X shows up on the target at middle range, with the
//   ghost scissors snapping, a thread scratching the ground to them. Held (Clivar): scissors open and close in his hand
//   while he charges, he dashes in and lays his palm on them, the "cut here" line goes round them and the scissors close:
//   a far heavier cut that opens the ground behind them.
// V Sangue Perfurante (Choso's Blood Manipulation, the same blood): palms together, a trembling ball, and a thin fast
//   thread that goes through the guard and out the back. Held (Convergence): threads of blood spiral from his body to the
//   hands, the ball grows, and a thick jet follows his aim, ending by raking the ground from far to near.
// B Marca na Alma: no blow and no step: only his own screen blinks, the eyes open and the soul of the one he aims at is
//   marked, pale and trembling inside the body for 8 s. His next strong blow (a technique, a sprinting or falling punch)
//   tears it out: whatever they were doing is undone and they have no power for 3 s.
// Shift+Z Escama Vermelha (Choso's, inherited): fist on the chest, the heart beats, the veins light up under the skin once
//   (0.9 s). 10 s faster and heavier, for 8% of his life.
public final class Itadori {
    // The Divergent mode: 8 punches or 8 s.
    private static final int MODE_TICKS = 160;
    private static final int MODE_PUNCHES = 8;
    private static final int RING_PUNCH = 5;
    // The ring of the 5th punch closes in RING ticks; the window round it, each side.
    private static final int RING = 10;
    private static final int WINDOW = 3;
    private static final int ECHO = 3;
    public static final String ESCAMA = "jj_itadori_escama";
    public static final String SOUL_MARK = "jj_itadori_soul";
    private static final int SOUL_TICKS = 160;
    private static final int B = 4;

    // The Divergent mode of each Itadori.
    private static final class Mode {
        int punches;
        LivingEntity ringOn;
        long ringStart = -1;
        boolean done;
    }

    private static final Map<UUID, Mode> MODES = new HashMap<>();
    // Whose soul each Itadori marked.
    private static final Map<UUID, LivingEntity> MARKED = new HashMap<>();
    // Set while an echo or a Black Flash hurts, so the hook doesn't count it as a punch.
    private static boolean echoing;

    private Itadori() {
    }

    public static Power create() {
        return new Power("itadori", 0xE0473C, 1.15f, List.of(
                new Ability(240, 0, Itadori::divergent),
                new Ability(320, 0, Itadori::teia),
                new Ability(220, 0, Itadori::corte),
                new Ability(600, 0, Itadori::sangue),
                new Ability(400, 0, Itadori::marca)),
                Arrays.asList(new Ability(400, 0, Itadori::escama), null, null, null, null));
    }

    static void sendFlash(ServerLevel level, ModPayloads.BlackFlash payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(payload.pos()) < 160 * 160) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // The window counts the round trip back (up to 6 ticks), so the timing that looks right on screen is right here too.
    private static int lag(LivingEntity caster) {
        if (caster instanceof ServerPlayer player && player.connection != null) {
            return Math.min(6, Math.round(player.connection.latency() / 50f));
        }
        return 0;
    }

    // ---------- the hook: every blow an Itadori lands ----------

    // Called by JjCommon on every blow of his that lands. plain: a punch of the base fighting (not a technique).
    static void onHit(LivingEntity itadori, ServerLevel level, LivingEntity target, boolean plain) {
        if (echoing) {
            return;
        }
        // The marked soul: a strong blow tears it out (a technique, or a punch thrown sprinting or falling).
        boolean strong = !plain || itadori.isSprinting() || (itadori.fallDistance > 0 && !itadori.onGround());
        if (strong && MARKED.get(itadori.getUUID()) == target && SubCommon.hasState(target, SOUL_MARK)) {
            rip(itadori, level, target);
        }
        Mode m = MODES.get(itadori.getUUID());
        if (!plain || m == null || m.done || m.ringOn != null) {
            return;
        }
        m.punches++;
        if (m.punches == RING_PUNCH) {
            ring(itadori, level, target, m);
        } else {
            Tasks.later(ECHO, () -> echo(itadori, level, target, false));
        }
        if (m.punches >= MODE_PUNCHES) {
            m.done = true;
        }
    }

    // ---------- Z: Punho Divergente (mode) ----------

    private static boolean divergent(LivingEntity caster, ServerLevel level) {
        UUID id = caster.getUUID();
        Mode m = new Mode();
        MODES.put(id, m);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.bf_ready");
        SubCommon.busy(caster, 6);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 0.5f, 0.6f);
        SubNet.send(level, Jj2Net.ITA_AURA, caster.getId(), caster.position(), Vec3.ZERO, MODE_TICKS + RING + 10, 0, 0);
        Tasks.add(age -> {
            boolean over = MODES.get(id) != m || guard.broken() || !caster.isAlive() || age >= MODE_TICKS + (m.ringOn != null ? RING + 10 : 0)
                    || (m.done && m.ringOn == null && age > 2);
            if (over) {
                if (MODES.get(id) == m) {
                    MODES.remove(id);
                }
                SubNet.send(level, Jj2Net.ITA_AURA, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            return false;
        });
        return true;
    }

    // The echo: the cursed energy landing again a moment after the fist.
    private static void echo(LivingEntity caster, ServerLevel level, LivingEntity t, boolean big) {
        if (!t.isAlive() || !caster.isAlive() || t.distanceTo(caster) > 6) {
            return;
        }
        Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
        Vec3 c = Combat.center(t);
        echoing = true;
        try {
            Combat.hurtMagic(caster, t, big ? 6 : 4);
        } finally {
            echoing = false;
        }
        Combat.push(t, dir, big ? 0.6 : 0.35, 0.1);
        SubNet.send(level, Jj2Net.ITA_ECHO, 0, c, dir, big ? 1 : 0, 0, 0);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 1.3f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 0.25f, 1.9f);
    }

    // The 5th punch: the ring closes on the target; the echo waits for it.
    private static void ring(LivingEntity caster, ServerLevel level, LivingEntity t, Mode m) {
        m.ringOn = t;
        m.ringStart = level.getGameTime();
        long start = m.ringStart;
        int win = WINDOW + lag(caster);
        SubNet.send(level, Jj2Net.ITA_RING, t.getId(), Combat.center(t), Vec3.ZERO, RING, WINDOW, 0);
        SubCommon.sound(level, t.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.9f);
        boolean mob = !(caster instanceof Player);
        int mobPress = RING + (SubCommon.RANDOM.nextFloat() < 0.65f ? 0 : (SubCommon.RANDOM.nextBoolean() ? -5 : 5));
        Tasks.add(age -> {
            if (m.ringStart != start) {
                return true;
            }
            if (mob && age == mobPress) {
                press(caster, level, m);
                return true;
            }
            if (age > RING + win) {
                // Never pressed: just the double punch.
                closeRing(level, m, 2);
                echo(caster, level, t, false);
                SubCommon.warn(caster, "itadori.bf_late");
                return true;
            }
            return false;
        });
    }

    private static void closeRing(ServerLevel level, Mode m, int how) {
        if (m.ringOn != null) {
            SubNet.send(level, Jj2Net.ITA_RING, m.ringOn.getId(), Combat.center(m.ringOn), Vec3.ZERO, 0, 0, how);
        }
        m.ringOn = null;
        m.ringStart = -1;
    }

    // Z in the mode (players): on the ring, the Black Flash press. True when the press was his.
    public static boolean divergentPress(ServerPlayer player) {
        Mode m = MODES.get(player.getUUID());
        if (m == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (m.ringOn != null) {
            press(player, level, m);
        }
        return true;
    }

    private static void press(LivingEntity caster, ServerLevel level, Mode m) {
        LivingEntity t = m.ringOn;
        int age = (int) (level.getGameTime() - m.ringStart) - lag(caster);
        boolean timed = Math.abs(age - RING) <= WINDOW;
        if (!timed || t == null || !t.isAlive() || t.distanceTo(caster) > 6) {
            closeRing(level, m, 2);
            if (t != null) {
                echo(caster, level, t, false);
            }
            SubCommon.warn(caster, age < RING ? "itadori.bf_early" : "itadori.bf_late");
            return;
        }
        closeRing(level, m, 1);
        blackFlash(caster, level, t);
    }

    // The Black Flash on the echo (BlackFlashRenderer, Itadori's style: kind = event + 10).
    private static void blackFlash(LivingEntity caster, ServerLevel level, LivingEntity target) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int fit = 10;
        Vec3 contact = JjCommon.contact(caster, target);
        Vec3 dir = target.position().subtract(caster.position()).normalize();
        Vec3 hold = target.position();
        SubCommon.pose(caster, "jj.bf_punch");
        SubCommon.busy(caster, fit + 6);
        Freeze.hold(caster, fit);
        Freeze.hold(target, fit + 2, a -> hold);
        sendFlash(level, new ModPayloads.BlackFlash(11, caster.getId(), contact, dir, 1f, fit));
        DbCommon.screen(level, contact, 26);
        Defense.breakGuard(target, caster);
        echoing = true;
        try {
            Combat.hurt(caster, target, 8);
        } finally {
            echoing = false;
        }
        SubCommon.sound(level, contact, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        SubCommon.sound(level, contact, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
        Tasks.add(a -> {
            if (!target.isAlive() || guard.broken()) {
                Freeze.free(target);
                return true;
            }
            if (a == fit) {
                Freeze.free(target);
                sendFlash(level, new ModPayloads.BlackFlash(12, caster.getId(), contact, dir, 1f, 0));
                echoing = true;
                try {
                    Combat.hurtMagic(caster, target, 14);
                } finally {
                    echoing = false;
                }
                Stun.apply(target, 30);
                target.setDeltaMovement(dir.scale(2.2).add(0, 0.5, 0));
                target.hurtMarked = true;
                JjCommon.crater(level, SubCommon.ground(level, contact), 2.4, 1.4, 14);
                SubCommon.sound(level, contact, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
                return true;
            }
            return false;
        });
    }

    // ---------- X: Teia de Tesoura ----------

    // Ticks: the palm lands at PALM; the web spreads in 8, the scissors run in 9, and it bursts 2 after.
    private static final int PALM = 5;
    private static final int BURST = PALM + 19;

    private static boolean teia(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 fwd = SubCommon.flat(caster.getLookAngle());
        fwd = fwd.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : fwd.normalize();
        Vec3 center = SubCommon.ground(level, caster.position().add(fwd.scale(1.1)));
        int seed = SubCommon.RANDOM.nextInt(1 << 20);
        SubCommon.pose(caster, "jj.slam_fist");
        SubCommon.busy(caster, BURST + 4);
        Freeze.holdLookFree(caster, BURST);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            if (age == PALM) {
                SubNet.send(level, Jj2Net.ITA_WEB, seed, center, Vec3.ZERO, 0, 0, 0);
                DbCommon.screen(level, center, 24);
                SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.6f);
                SubCommon.sound(level, center, SoundEvents.SHEEP_SHEAR, 1.2f, 0.7f);
            }
            if (age > PALM && age < BURST && (age - PALM) % 3 == 0 && age - PALM >= 8) {
                SubCommon.sound(level, center, SoundEvents.SHEEP_SHEAR, 1f, 1.2f + 0.1f * (age - PALM));
            }
            if (age == BURST) {
                Freeze.free(caster);
                burstWeb(caster, level, center, seed);
                return true;
            }
            return false;
        });
        return true;
    }

    // The web cuts: whoever stands on a thread is cut and thrown up, and the ground jumps along every thread.
    private static void burstWeb(LivingEntity caster, ServerLevel level, Vec3 center, int seed) {
        List<ItadoriWeb.Line> lines = ItadoriWeb.lines(seed);
        DbCommon.screen(level, center.add(0, 1, 0), 27);
        SubCommon.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.3f, 0.8f);
        SubCommon.sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.6f);
        for (LivingEntity e : Combat.inRadius(level, center.add(0, 0.5, 0), ItadoriWeb.R + 1.2, caster)) {
            if (Math.abs(e.getY() - center.y) > 2.5) {
                continue;
            }
            double dx = e.getX() - center.x;
            double dz = e.getZ() - center.z;
            boolean on = false;
            for (ItadoriWeb.Line l : lines) {
                if (ItadoriWeb.distance(l, dx, dz) < 0.6 + e.getBbWidth() * 0.5) {
                    on = true;
                    break;
                }
            }
            if (!on) {
                continue;
            }
            Vec3 c = Combat.center(e);
            Combat.hurtMagic(caster, e, 14);
            Stun.apply(e, 25);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.95, 0));
            e.hurtMarked = true;
            SubNet.send(level, Jj2Net.ITA_CUT, 0, c, caster.position().subtract(c), 2, 0, 0);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, new Vec3(0, 1, 0), 1f, 0, 0);
        }
        if (!BlockRules.canBreak(level)) {
            return;
        }
        // The blocks along the threads jump (spread over 3 ticks).
        int cap = 120;
        int n = 0;
        for (ItadoriWeb.Line l : lines) {
            double len = Math.hypot(l.bx() - l.ax(), l.bz() - l.az());
            double step = l.spoke() ? 0.75 : 0.85;
            int k = Math.max(1, (int) Math.round(len / step));
            for (int i = 0; i < k && n < cap; i++, n++) {
                double u = (i + 0.5) / k;
                Vec3 p = center.add(l.ax() + (l.bx() - l.ax()) * u, 0, l.az() + (l.bz() - l.az()) * u);
                double sx = -(l.bz() - l.az()) / len;
                double sz = (l.bx() - l.ax()) / len;
                double sg = SubCommon.RANDOM.nextBoolean() ? 1 : -1;
                double pow = l.spoke() ? 1 : 0.8;
                Tasks.later(1 + n % 3, () -> jump(level, p, sx * sg, sz * sg, pow));
            }
        }
    }

    // One block of the ground under p thrown up (and a little to one side), as the preview's burstLine.
    private static void jump(ServerLevel level, Vec3 p, double sx, double sz, double pow) {
        Vec3 g = SubCommon.ground(level, p);
        BlockPos at = BlockPos.containing(g.x, g.y - 0.5, g.z);
        BlockState s = level.getBlockState(at);
        if (s.isAir() || !s.getFluidState().isEmpty() || BlockRules.keeps(s) || s.getDestroySpeed(level, at) < 0) {
            return;
        }
        FallingBlockEntity block = FallingBlockEntity.fall(level, at, s);
        block.dropItem = false;
        double side = 0.6 + SubCommon.RANDOM.nextDouble() * 1.6;
        double up = (4 + SubCommon.RANDOM.nextDouble() * 5) * pow;
        block.setDeltaMovement(new Vec3(sx * side, up, sz * side).scale(1 / 20.0 * 1.6));
        block.hurtMarked = true;
    }

    // ---------- C: Corte de Tesoura (tap) / Clivar (held) ----------

    private static boolean corte(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossHold = SubCommon.RANDOM.nextFloat() < 0.6f ? 0 : 20;
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            boolean held = SubCommon.held(caster, age, bossHold);
            if (!held && age < 4) {
                cut(caster, level, guard);
                return true;
            }
            if (age >= 4) {
                clivar(caster, level, guard);
                return true;
            }
            return false;
        });
        return true;
    }

    // The tap: two hand cuts in the air, the X on the target at middle range.
    private static void cut(LivingEntity caster, ServerLevel level, SubCommon.Guard guard) {
        LivingEntity t = JjCommon.target(caster, 18);
        if (t == null) {
            SubCommon.warn(caster, "itadori.miss");
            JjCommon.shorten(caster, 2, 40);
            return;
        }
        SubCommon.pose(caster, "jj.line_cut");
        SubCommon.busy(caster, 12);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                return true;
            }
            if (age == 4 || age == 7) {
                Vec3 c = Combat.center(t);
                SubNet.send(level, Jj2Net.ITA_CUT, 0, c, caster.position().subtract(c), age == 4 ? 0 : 1, 0, 0);
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, SubCommon.flat(c.subtract(caster.position())), 0.7f, 0, 0);
                Combat.hurtMagic(caster, t, 6);
                Stun.apply(t, 8);
                SubCommon.sound(level, c, SoundEvents.SHEEP_SHEAR, 1.3f, 0.8f + 0.2f * (age / 4));
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
            }
            if (age == 10) {
                Vec3 c = Combat.center(t);
                Vec3 dir = SubCommon.flat(c.subtract(caster.position()));
                Combat.hurtMagic(caster, t, 6);
                Stun.apply(t, 25);
                Combat.push(t, dir, 0.6, 0.35);
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, c, dir, 1f, 0, 0);
                DbCommon.screen(level, c, 24);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                return true;
            }
            return false;
        });
    }

    // Held: the Clivar. Charge with the scissors in his hand, dash in, palm on them, the "cut here" line, the scissors close.
    private static void clivar(LivingEntity caster, ServerLevel level, SubCommon.Guard guard) {
        int charge = 14;
        int dash = 3;
        int close = 10;
        SubCommon.pose(caster, "jj.dash_charge");
        SubCommon.busy(caster, charge + dash + close + 6);
        SubNet.send(level, Jj2Net.ITA_HAND_SCISSORS, caster.getId(), caster.position(), Vec3.ZERO, charge + 2, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.SHEEP_SHEAR, 1f, 1.4f);
        int loopId = SubNet.newId();
        LivingEntity[] t = {null};
        Vec3[] at = {caster.position()};
        Vec3[] from = {caster.position()};
        Tasks.add(age -> {
            if (guard.broken() || (t[0] != null && !t[0].isAlive())) {
                Freeze.free(caster);
                SubNet.send(level, Jj2Net.ITA_HAND_SCISSORS, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                if (t[0] != null) {
                    SubNet.send(level, SubNet.CUT_LOOP, loopId, t[0].position(), new Vec3(loopR(t[0]), 1, t[0].getId()), 1, 0, 1);
                    Freeze.free(t[0]);
                }
                return true;
            }
            if (age < charge) {
                Charge.report(caster, age / (float) charge);
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                if (age % 4 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.SHEEP_SHEAR, 0.6f, 1.5f + age * 0.03f);
                }
                return false;
            }
            if (age == charge) {
                SubNet.send(level, Jj2Net.ITA_HAND_SCISSORS, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                t[0] = JjCommon.target(caster, 12);
                if (t[0] == null) {
                    SubCommon.warn(caster, "itadori.miss");
                    JjCommon.shorten(caster, 2, 60);
                    return true;
                }
                from[0] = caster.position();
                SubCommon.pose(caster, "jj.dash_run");
                JjCommon.carry(caster, dash, at);
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), from[0], Vec3.ZERO, 3, 0, 0);
                SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            }
            LivingEntity target = t[0];
            int d = age - charge;
            if (d <= dash) {
                Vec3 to = target.position().subtract(SubCommon.flat(target.position().subtract(from[0])).scale(1.1));
                at[0] = from[0].lerp(to, SubCommon.easeOutCubic(Math.min(1, (d + 1) / (float) dash)));
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), at[0], Vec3.ZERO, 3, 0, d >= dash ? 1 : 0);
                if (d < dash) {
                    return false;
                }
                // The palm on them: the loop goes round, they are held while it closes.
                Freeze.free(caster);
                SubCommon.pose(caster, "jj.point_blank");
                Freeze.hold(target, JjCommon.control(caster, target, close + 2));
                SubCommon.sound(level, target.position(), SoundEvents.BOOK_PAGE_TURN, 1.2f, 1.2f);
                DbCommon.hit(level, JjCommon.contact(caster, target), 0.6f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
            }
            int c = d - dash;
            float k = c < 6 ? 0 : Math.min(1, (c - 6) / 4f);
            SubNet.send(level, SubNet.CUT_LOOP, loopId, target.position(), new Vec3(loopR(target), 1, target.getId()), k, 0, 0);
            if (c == 6) {
                SubCommon.sound(level, target.position(), SoundEvents.SHEEP_SHEAR, 1.4f, 0.7f);
            }
            if (c < close) {
                return false;
            }
            SubNet.send(level, SubNet.CUT_LOOP, loopId, target.position(), new Vec3(loopR(target), 1, target.getId()), 1, 0, 1);
            Freeze.free(target);
            Vec3 tc = Combat.center(target);
            Vec3 dir = SubCommon.flat(target.position().subtract(caster.position()));
            float reach = 8.5f;
            SubNet.send(level, Jj2Net.ITA_CLIVAR, 0, tc, dir, reach, 0, 0);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, tc, dir, 1.4f, 0, 0);
            Defense.breakGuard(target, caster);
            Combat.hurtMagic(caster, target, 26);
            Stun.apply(target, JjCommon.isBoss(target) ? 52 : 40);
            Combat.push(target, dir, 1.0, 0.45);
            DbCommon.screen(level, tc, 27);
            SubCommon.sound(level, tc, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
            SubCommon.sound(level, tc, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
            SubCommon.sound(level, tc, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
            groundCut(caster, level, target.position(), dir, reach);
            return true;
        });
    }

    private static float loopR(LivingEntity t) {
        return t.getBbWidth() * 0.75f + 0.05f;
    }

    // The two cuts opening the ground behind them (preview: from 0.35 to 0.9 to each side, 8.5 long): the blocks jump.
    private static void groundCut(LivingEntity caster, ServerLevel level, Vec3 feet, Vec3 dir, float reach) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int n = 0;
        for (int sg = -1; sg <= 1; sg += 2) {
            Vec3 a = feet.add(side.scale(sg * 0.35)).add(dir.scale(0.4));
            Vec3 b = feet.add(side.scale(sg * 0.9)).add(dir.scale(reach));
            int k = (int) Math.round(a.distanceTo(b) / 0.7);
            for (int i = 0; i < k; i++) {
                Vec3 p = a.lerp(b, (i + 0.5) / k);
                double s = sg;
                Tasks.later(1 + i / 3, () -> jump(level, p, side.x * s, side.z * s, 0.8));
                n++;
            }
        }
    }

    // ---------- V: Sangue Perfurante (tap) / Convergence (held) ----------

    private static boolean sangue(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int press = 9;
        int full = 22;
        int bossHold = SubCommon.RANDOM.nextFloat() < 0.5f ? 0 : full + press;
        int id = SubNet.newId();
        SubCommon.pose(caster, "jj.blood_charge");
        SubCommon.busy(caster, press + full + 30);
        SubCommon.sound(level, caster.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1f, 0.6f);
        float[] k = {0};
        int[] fired = {-1};
        int[] pour = {0};
        boolean[] thick = {false};
        float[] aim = {0, 0};
        Set<Integer> hit = new HashSet<>();
        Tasks.add(age -> {
            Vec3 hands = DbCommon.hands(caster);
            if (guard.broken()) {
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (fired[0] < 0) {
                boolean held = SubCommon.held(caster, age, bossHold);
                if (age < press) {
                    // the ball compressed between the palms, trembling
                    SubNet.send(level, SubNet.BLOOD_BALL, id, hands, caster.getLookAngle(), 0.22f, 0.3f * age / press, 0);
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                    return false;
                }
                if (held && age < press + full) {
                    // the Convergence: blood pulled from his body to the hands, the ball growing
                    k[0] = (age - press) / (float) full;
                    thick[0] = true;
                    Charge.report(caster, k[0]);
                    SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, k[0], 0, 0);
                    SubNet.send(level, SubNet.BLOOD_BALL, id, hands, caster.getLookAngle(), 0.3f + 0.25f * k[0], k[0], 0);
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                    if (age % 8 == 0) {
                        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 1.2f + 0.4f * k[0]);
                    }
                    return false;
                }
                fired[0] = age;
                thick[0] = thick[0] && k[0] > 0.3f;
                pour[0] = thick[0] ? 18 : 6;
                SubNet.send(level, SubNet.BLOOD_BALL, id, hands, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.MAX_CHARGE, caster.getId(), hands, Vec3.ZERO, 1, 0, 1);
                SubCommon.pose(caster, "jj.blood_shoot");
                SubCommon.busy(caster, pour[0] + 4);
                DbCommon.screen(level, hands, thick[0] ? 30 : 29);
                SubCommon.sound(level, hands, thick[0] ? SoundEvents.TRIDENT_RIPTIDE_3.value() : SoundEvents.TRIDENT_THROW.value(), 1.4f, thick[0] ? 0.7f : 1.7f);
                aim[0] = caster.getYRot();
                aim[1] = caster.getXRot();
            }
            int t = age - fired[0];
            boolean last = t >= pour[0];
            double len = thick[0] ? 40 : 30;
            Vec3 dir = caster.getLookAngle();
            LivingEntity aimed = caster instanceof Player ? null : JjCommon.target(caster, len);
            if (aimed != null) {
                dir = Combat.center(aimed).subtract(hands).normalize();
            }
            if (thick[0] && t > pour[0] - 8) {
                // the end: the jet comes down and rakes the ground from far to near
                float u = (t - (pour[0] - 8)) / 8f;
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
                float pitch = (float) (-Math.asin(Mth.clamp(dir.y, -1, 1)) * Mth.RAD_TO_DEG);
                dir = Vec3.directionFromRotation(Mth.lerp(SubCommon.smooth(u), pitch, 70), yaw);
            }
            Vec3 end = DbCommon.beamEnd(caster, level, hands, dir, len, thick[0] ? 1.2 : 0.5, true);
            SubNet.send(level, SubNet.BLOOD_SWEEP, id, hands, end.subtract(hands), thick[0] ? 0.55f : 0.06f, 0, last ? 1 : 0);
            if (thick[0] && t > pour[0] - 8 && t % 2 == 0) {
                Vec3 g = SubCommon.ground(level, end);
                if (end.y - g.y < 2.5) {
                    JjCommon.carve(level, g, 0.9, hands);
                    SubNet.send(level, SubNet.BLOOD_POOL, SubNet.newId(), g, Vec3.ZERO, 200, 0.7f + 0.4f * SubCommon.RANDOM.nextFloat(), 0);
                }
            }
            for (LivingEntity e : Combat.alongLine(level, hands, end, thick[0] ? 1.3 : 0.6, caster)) {
                if (!hit.add(e.getId())) {
                    if (thick[0] && t % 6 == 0) {
                        Combat.hurtMagic(caster, e, 2);
                    }
                    continue;
                }
                Defense.breakGuard(e, caster);
                Combat.hurtMagic(caster, e, thick[0] ? 26 : 12);
                Stun.apply(e, thick[0] ? 30 : 25);
                Combat.push(e, dir, thick[0] ? 1.4 : 0.3, thick[0] ? 0.4 : 0.05);
                // the blood goes out the back
                SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(e), dir, thick[0] ? 1.5f : 0.9f, 0, 0);
            }
            if (t % 3 == 0) {
                SubCommon.sound(level, end, SoundEvents.HONEY_BLOCK_BREAK, 0.9f, 0.7f);
            }
            return last;
        });
        return true;
    }

    // ---------- B: Marca na Alma ----------

    private static boolean marca(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 24);
        if (t == null) {
            SubCommon.warn(caster, "itadori.alma_nada");
            return false;
        }
        if (caster instanceof ServerPlayer player) {
            SubNet.sendTo(player, Jj2Net.ITA_LIDS, t.getId(), Combat.center(t), Vec3.ZERO, 0, 0, 0);
        }
        UUID id = caster.getUUID();
        LivingEntity old = MARKED.get(id);
        if (old != null && old != t && SubCommon.hasState(old, SOUL_MARK)) {
            SubCommon.clearState(old, SOUL_MARK);
            SubNet.send(level, Jj2Net.ITA_SOUL, old.getId(), old.position(), Vec3.ZERO, 0, 0, 0);
        }
        // the soul shows when his eyes open (0.22 s)
        Tasks.later(4, () -> {
            if (!t.isAlive() || !caster.isAlive()) {
                return;
            }
            SubCommon.setState(t, SOUL_MARK, SOUL_TICKS);
            MARKED.put(id, t);
            SubNet.send(level, Jj2Net.ITA_SOUL, t.getId(), t.position(), Vec3.ZERO, SOUL_TICKS, 0, 0);
            SubCommon.sound(level, t.position(), SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.7f);
            Tasks.add(age -> {
                if (MARKED.get(id) != t) {
                    return true;
                }
                if (!t.isAlive() || !SubCommon.hasState(t, SOUL_MARK)) {
                    MARKED.remove(id);
                    SubNet.send(level, Jj2Net.ITA_SOUL, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
                    return true;
                }
                return false;
            });
        });
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1.6f);
        return true;
    }

    // A strong blow on the marked soul: torn out of the body (back with a jolt), the move undone, 3 s without power.
    private static void rip(LivingEntity caster, ServerLevel level, LivingEntity t) {
        MARKED.remove(caster.getUUID());
        SubCommon.clearState(t, SOUL_MARK);
        Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
        SubNet.send(level, Jj2Net.ITA_SOUL, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, Jj2Net.ITA_RIP, t.getId(), t.position(), dir, 0, 0, 0);
        int ticks = JjCommon.control(caster, t, 60);
        PowerLogic.seal(t, ticks);
        Vec3 c = Combat.center(t);
        DbCommon.hit(level, c, 1.2f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.SOUL_ESCAPE.value(), 2f, 0.8f);
        SubCommon.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 1.2f);
        if (caster instanceof Player player) {
            SubCommon.warn(player, "itadori.alma", Math.round(ticks / 20f));
        }
    }

    // ---------- Shift+Z: Escama Vermelha ----------

    private static boolean escama(LivingEntity caster, ServerLevel level) {
        int beat = 7;
        int ticks = 200;
        SubCommon.pose(caster, "jj.scale_on");
        SubCommon.busy(caster, beat + 4);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6f, 1f);
        Tasks.later(beat, () -> {
            if (guard.broken() || !caster.isAlive()) {
                return;
            }
            SubNet.send(level, Jj2Net.ITA_VEINS, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
            SubCommon.setState(caster, ESCAMA, ticks);
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false));
            caster.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 2, false, false));
            // the forced blood: 8% of his life
            caster.setHealth(Math.max(1, caster.getHealth() - caster.getMaxHealth() * 0.08f));
            SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2f, 1.3f);
            SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 0.6f, 0.5f);
        });
        return true;
    }

    public static void clear() {
        MODES.clear();
        MARKED.clear();
    }
}
