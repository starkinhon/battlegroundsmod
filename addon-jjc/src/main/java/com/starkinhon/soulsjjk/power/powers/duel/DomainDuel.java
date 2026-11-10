package com.starkinhon.soulsjjk.power.powers.duel;

import com.starkinhon.soulsjjk.compat.Att;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.ModEffects;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Freeze;
import com.starkinhon.soulsjjk.combat.Stamina;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.power.PowerLogic;
import com.starkinhon.soulsjjk.power.powers.Sukuna;
import com.starkinhon.soulsjjk.power.powers.gojo.InfiniteVoid;
import com.starkinhon.soulsjjk.power.powers.sukuna.ShrineDomain;
import com.starkinhon.soulsjjk.skill.DominioSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.starkinhon.soulsjjk.compat.LivingIncomingDamageEvent;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// The Duelo de Expansões (Stark, 2026-11-09, design in forja/duelo-expansoes, port released 09:09): when Gojo and Sukuna
// open their domains within WINDOW ticks of each other (either first) and in each other's reach, they fight for it.
// - Gojo's white swallows everything as always and takes the two into his tunnel, but Sukuna is not caught: he stands in
//   his own sign, held still, untouchable (InfiniteVoid.spare / exempt). His own Santuário gives way (ShrineDomain.yieldTo).
// - PAUSE_AFTER ticks into the tunnel Gojo's clock stops (InfiniteVoid.pause): the Santuário sprouts in the middle of
//   the tunnel, the sky splits cyan and red, and the two play one of three minigames (DuelRules), standing still with
//   the camera on their faces (client/fx/DuelScene). Players play with their keys (their client measures and sends each
//   move, ModPayloads.ExpansionDuelInput); mobs and bosses play as bots with human reactions (DuelRules.bot).
// - Gojo wins: the Santuário falls, the tunnel goes on to the void and Sukuna is caught like everyone (paralyzed).
// - Sukuna wins: the tunnel shatters (Gojo's technique burns out) and the Santuário takes over, the fast version, with
//   Gojo caught in it. (The open Santuário, Shift+V, does not exist yet: it will come in here when it does.)
// - Same score: a tie. Both go into the void, where the sure hits cancel out (Gojo takes no cuts, Sukuna is not
//   paralyzed) and techniques work. The Santuário cracks the barrier from outside (red cracks in the sky); when it
//   breaks both domains fall, everyone is back in the world and both techniques burn out. Gojo can reopen it with V,
//   paying heavy stamina; each reopening lasts less.
// Bots answer an enemy's domain too: a Gojo or Sukuna mob near the other's domain opens its own after a human delay.
public final class DomainDuel {
    public static final int WINDOW = 40;
    // How far into the tunnel the duel starts (the tunnel open and the two face to face, as in the preview).
    // The tunnel takes ~30 ticks to tear open: stopping earlier froze the white over the whole duel (in-game test
    // 2026-11-09).
    private static final int PAUSE_AFTER = 34;
    private static final int HOLD_STEP = 10;
    private static final double VIEW_DISTANCE = 192;
    // The tie: ticks until the Santuário breaks the barrier, each reopening this much shorter, never below REOPEN_MIN.
    private static final int TIE_CRACK = 300;
    private static final double REOPEN_SHRINK = 0.6;
    private static final int REOPEN_MIN = 60;
    // Stamina a reopening costs (percent of the max), and where the void's clock goes back to.
    private static final double REOPEN_COST = 45;
    private static final int REOPEN_REWIND = 20;
    private static final int BURNOUT_TICKS = 200;
    // Payload modes (client/fx/DuelScene).
    public static final int M_FIGHTERS = 0, M_BEGIN = 1, M_POINT = 2, M_RESULT = 3, M_TIE = 4, M_END = 5, M_SYNC = 6;
    public static final int GOJO = 0, SUKUNA = 1, TIE = 2;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private enum Phase {
        // Gojo's domain on its way to the tunnel; Sukuna in his sign.
        WHITE,
        // The minigame (and the result on screen).
        DUEL,
        // The tie in the void.
        TIE,
        // Decided; only waiting a little before it is forgotten.
        DONE
    }

    private static final class Duel {
        final LivingEntity gojo;
        final LivingEntity sukuna;
        final ServerLevel level;
        final Vec3 center;
        final Vec3 arena;
        Phase phase = Phase.WHITE;
        int t;
        int seed;
        int game;
        final int[] score = new int[2];
        final boolean[] human = new boolean[2];
        @SuppressWarnings("unchecked")
        final List<DuelRules.Move>[] bot = new List[2];
        final int[] botNext = new int[2];
        @SuppressWarnings("unchecked")
        final Set<Integer>[] used = new Set[] {new HashSet<Integer>(), new HashSet<Integer>()};
        final int[] beats = new int[2];
        final int[] beatTick = {-1, -1};
        final int[] beatsThisTick = new int[2];
        final Runnable[] holds = new Runnable[2];
        int result = -1;
        int crackT;
        int crackLen;
        int reopens;
        int doneAt;

        Duel(LivingEntity gojo, LivingEntity sukuna, ServerLevel level, Vec3 center) {
            this.gojo = gojo;
            this.sukuna = sukuna;
            this.level = level;
            this.center = center;
            this.arena = DominioSpec.arena(level, center);
        }

        LivingEntity fighter(int side) {
            return side == GOJO ? gojo : sukuna;
        }
    }

    private static final List<Duel> DUELS = new ArrayList<>();

    private DomainDuel() {
    }

    // ---- who fights ----

    private static Duel of(LivingEntity entity) {
        for (Duel d : DUELS) {
            if (d.phase != Phase.DONE && (d.gojo == entity || d.sukuna == entity)) {
                return d;
            }
        }
        return null;
    }

    // One of the two of a Duelo de Expansões still going (the tie too).
    public static boolean fighting(LivingEntity entity) {
        return !DUELS.isEmpty() && of(entity) != null;
    }

    private static boolean isGojo(LivingEntity e) {
        return Att.has(e, ModAttachments.POWER) && "strongest".equals(Att.get(e, ModAttachments.POWER).power);
    }

    private static boolean isSukuna(LivingEntity e) {
        return Att.has(e, ModAttachments.POWER) && Sukuna.ID.equals(Att.get(e, ModAttachments.POWER).power);
    }

    // V can open a domain right now even though the other one's has them (caught, held, busy): it answers it.
    public static boolean canClash(LivingEntity caster) {
        if (of(caster) != null) {
            return false;
        }
        if (isSukuna(caster)) {
            LivingEntity gojo = InfiniteVoid.openedNear(caster, WINDOW);
            return gojo != null && of(gojo) == null;
        }
        if (isGojo(caster)) {
            LivingEntity sukuna = ShrineDomain.openedNear(caster, WINDOW);
            return sukuna != null && of(sukuna) == null;
        }
        return false;
    }

    // The Sukuna whose Santuário Gojo's V is answering right now (it opened at most WINDOW ticks ago with him in it,
    // out in the world or already up in its space), or null. InfiniteVoid.use asks this before anything else: the
    // Santuário gives way first (everyone back in the world), so Gojo's domain opens down where they fight and not up in
    // the Santuário's space (Stark, 2026-11-09: Gojo never got a duel when Sukuna opened first; 16 ticks after the press
    // everyone was already up there, too far from where it opened to count).
    public static LivingEntity answering(LivingEntity gojo) {
        if (of(gojo) != null) {
            return null;
        }
        LivingEntity sukuna = ShrineDomain.openedNear(gojo, WINDOW);
        if (sukuna == null || of(sukuna) != null) {
            return null;
        }
        ShrineDomain.yieldTo(sukuna);
        return sukuna;
    }

    // Gojo's domain just opened: a duel with the Sukuna it answered (answering), else the Sukuna mobs around may answer.
    public static void gojoOpened(LivingEntity gojo, ServerLevel level, LivingEntity sukuna) {
        if (sukuna != null && sukuna.isAlive() && !sukuna.isRemoved() && of(sukuna) == null && of(gojo) == null) {
            start(gojo, sukuna, level);
            return;
        }
        botsAnswer(gojo, level, Sukuna.ID);
    }

    // Sukuna pressed V: true when it is an answer to Gojo's domain (the duel starts, no Santuário of his own opens).
    public static boolean sukunaOpened(LivingEntity sukuna, ServerLevel level) {
        LivingEntity gojo = InfiniteVoid.openedNear(sukuna, WINDOW);
        if (gojo != null && of(gojo) == null && of(sukuna) == null) {
            start(gojo, sukuna, level);
            return true;
        }
        botsAnswer(sukuna, level, "strongest");
        return false;
    }

    // A Gojo or Sukuna mob near an enemy domain opening answers with its own, after a human delay (not always).
    private static void botsAnswer(LivingEntity opener, ServerLevel level, String power) {
        Vec3 center = opener.position();
        for (LivingEntity e : Combat.inRadius(level, center, ShrineDomain.RADIUS, opener)) {
            if (e instanceof Player || !e.isAlive() || !Att.has(e, ModAttachments.POWER) || !power.equals(Att.get(e, ModAttachments.POWER).power)) {
                continue;
            }
            PowerData data = Att.get(e, ModAttachments.POWER);
            if (data.cooldowns[PowerData.SLOT_ULT] > 0) {
                continue;
            }
            boolean boss = e instanceof com.starkinhon.soulsjjk.boss.SoulBoss b && b.isBoss();
            // A boss always answers (bosses use everything they have; in the in-game test the boss Gojo let a Santuário
            // through); a mob only sometimes.
            if (!boss && RANDOM.nextDouble() > 0.6) {
                continue;
            }
            int delay = 10 + RANDOM.nextInt(18);
            Tasks.later(delay, () -> Tasks.as(e, () -> {
                if (e.isAlive() && canClash(e) && PowerLogic.cast(e, PowerData.SLOT_ULT)) {
                    Att.get(e, ModAttachments.POWER).cooldowns[PowerData.SLOT_ULT] = 900;
                }
            }));
        }
    }

    // ---- the duel ----

    private static void start(LivingEntity gojo, LivingEntity sukuna, ServerLevel level) {
        Vec3 center = gojo.position();
        Duel d = new Duel(gojo, sukuna, level, center);
        DUELS.add(d);
        // Everything of before stops as the duel opens (Stark, 2026-11-09: Gojo's Blue kept pulling him through it):
        // every attack in the air around, the Blues out, and whatever Sukuna had going (his tasks read the interrupt and
        // close themselves). Gojo's own domain is the duel's, so he is not interrupted.
        com.starkinhon.soulsjjk.combat.Destructible.eraseAlong(level, center, center, ShrineDomain.RADIUS, null);
        com.starkinhon.soulsjjk.power.powers.gojo.Azul.dispel(gojo, center, ShrineDomain.RADIUS);
        com.starkinhon.soulsjjk.power.powers.gojo.Azul.dispel(sukuna, center, ShrineDomain.RADIUS);
        if (Att.has(sukuna, ModAttachments.POWER)) {
            PowerLogic.interrupt(sukuna);
        }
        // Sukuna is no longer caught: he answers. Held in his sign, his camera free, until the tunnel.
        InfiniteVoid.spare(gojo, sukuna);
        if (Att.has(sukuna, ModAttachments.POWER)) {
            Att.get(sukuna, ModAttachments.POWER).busyTicks = 0;
        }
        hold(d, SUKUNA, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sukuna, new ModPayloads.PlaySkillAnimation(sukuna.getId(), ShrineDomain.SKILL, false));
        level.playSound(null, sukuna.getX(), sukuna.getY(), sukuna.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.4f, 0.6f);
        send(d, M_FIGHTERS, 0, 0, 0);
        Tasks.addSystem(age -> tick(d));
    }

    // Held still (the camera free while Gojo's white plays, locked in the duel); renewed in short stretches.
    private static void hold(Duel d, int side, boolean lookFree) {
        LivingEntity e = d.fighter(side);
        Runnable old = d.holds[side];
        d.holds[side] = lookFree ? Freeze.holdLookFree(e, HOLD_STEP + 1) : Freeze.hold(e, HOLD_STEP + 1);
        if (old != null) {
            old.run();
        }
        e.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO.get(), HOLD_STEP + 1, 0, false, false, false));
    }

    private static void release(Duel d) {
        for (int side = 0; side < 2; side++) {
            if (d.holds[side] != null) {
                d.holds[side].run();
                d.holds[side] = null;
            }
            d.fighter(side).removeEffect(ModEffects.ENRAIZADO.get());
        }
    }

    private static boolean gone(LivingEntity e, Duel d) {
        return !e.isAlive() || e.isRemoved() || e.level() != d.level;
    }

    private static boolean tick(Duel d) {
        if (d.phase == Phase.DONE) {
            if (++d.doneAt > 60) {
                DUELS.remove(d);
                return true;
            }
            return false;
        }
        boolean gojoGone = gone(d.gojo, d);
        boolean sukunaGone = gone(d.sukuna, d);
        if (gojoGone || sukunaGone || (d.phase != Phase.TIE && !InfiniteVoid.open(d.gojo))) {
            // Someone fell (or Gojo's domain is no longer there): the duel is off; Gojo's domain goes on (or shatters) as
            // it would without it.
            release(d);
            InfiniteVoid.pause(d.gojo, false);
            if (d.phase == Phase.TIE && !gojoGone && !sukunaGone) {
                burnout(d.sukuna);
            }
            finish(d);
            return false;
        }
        switch (d.phase) {
            case WHITE -> white(d);
            case DUEL -> duel(d);
            case TIE -> tie(d);
            default -> {
            }
        }
        return false;
    }

    private static void finish(Duel d) {
        d.phase = Phase.DONE;
        d.doneAt = 0;
        send(d, M_END, d.result, 0, 0);
    }

    private static void white(Duel d) {
        if (d.t++ % HOLD_STEP == 0) {
            hold(d, SUKUNA, true);
        }
        int tunnel = InfiniteVoid.tunnelTick(d.gojo);
        if (tunnel < 0 || InfiniteVoid.age(d.gojo) < tunnel + PAUSE_AFTER) {
            return;
        }
        // The tunnel is open, the two face to face: Gojo's clock stops and the duel begins.
        InfiniteVoid.pause(d.gojo, true);
        d.phase = Phase.DUEL;
        d.t = 0;
        d.seed = 1 + RANDOM.nextInt(1_000_000);
        d.game = DuelRules.game(d.seed);
        for (int side = 0; side < 2; side++) {
            LivingEntity e = d.fighter(side);
            d.human[side] = e instanceof ServerPlayer;
            boolean boss = e instanceof com.starkinhon.soulsjjk.boss.SoulBoss b && b.isBoss();
            d.bot[side] = d.human[side] ? List.of() : DuelRules.bot(d.game, d.seed, side, boss ? DuelRules.HARD : DuelRules.NORMAL);
        }
        faceEachOther(d);
        hold(d, GOJO, false);
        hold(d, SUKUNA, false);
        send(d, M_BEGIN, d.seed, d.game, 0);
        d.level.playSound(null, d.sukuna.getX(), d.sukuna.getY(), d.sukuna.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 2f, 0.6f);
    }

    private static void faceEachOther(Duel d) {
        Vec3 to = d.sukuna.position().subtract(d.gojo.position());
        if (to.horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        Freeze.face(d.gojo, yaw, 0);
        Freeze.face(d.sukuna, Mth.wrapDegrees(yaw + 180), 0);
    }

    private static void duel(Duel d) {
        int t = ++d.t;
        if (t % HOLD_STEP == 0) {
            hold(d, GOJO, false);
            hold(d, SUKUNA, false);
        }
        d.beatsThisTick[0] = d.beatsThisTick[1] = 0;
        if (t % 10 == 0) {
            send(d, M_SYNC, t, 0, 0);
        }
        int end = DuelRules.gameEnd(d.game);
        for (int side = 0; side < 2; side++) {
            List<DuelRules.Move> moves = d.bot[side];
            while (d.botNext[side] < moves.size() && moves.get(d.botNext[side]).tick() <= t) {
                DuelRules.Move m = moves.get(d.botNext[side]++);
                score(d, side, m.index(), m.value());
            }
        }
        if (t == end) {
            d.result = d.score[GOJO] > d.score[SUKUNA] ? GOJO : d.score[SUKUNA] > d.score[GOJO] ? SUKUNA : TIE;
            send(d, M_RESULT, d.result, d.score[GOJO], d.score[SUKUNA]);
        }
        if (d.result >= 0 && t >= DuelRules.settleAt(d.game, d.result, SUKUNA)) {
            settle(d);
        }
    }

    // A move counts: from a bot, or measured by a player's client (ExpansionDuelInput). Each index only once (the beats
    // of the Cabo in order, never faster than a few a tick).
    private static void score(Duel d, int side, int index, int value) {
        if (d.phase != Phase.DUEL || d.t > DuelRules.gameEnd(d.game)) {
            return;
        }
        int t = d.t;
        switch (d.game) {
            case DuelRules.SEQ -> {
                if (index < 0 || index > 9 || !d.used[side].add(index) || t < DuelRules.slotStart(index) - 2 || t > DuelRules.slotEnd(index) + 8) {
                    return;
                }
                value = value > 0 ? 1 : 0;
            }
            case DuelRules.CABO -> {
                if (index != d.beats[side] || t < DuelRules.K0 - 2 || t > DuelRules.K0 + DuelRules.CABO_LEN + 8 || d.beatsThisTick[side] >= 3) {
                    return;
                }
                d.beats[side]++;
                d.beatsThisTick[side]++;
                value = 1;
            }
            default -> {
                if (index < 0 || index > 4 || !d.used[side].add(index) || t < DuelRules.roundStart(index) - 2 || t > DuelRules.roundEnd(index) + 8) {
                    return;
                }
                value = Mth.clamp(value, 0, 2);
            }
        }
        d.score[side] += value;
        send(d, M_POINT, side, index, value);
    }

    // The result is done showing: the winner's domain takes over.
    private static void settle(Duel d) {
        LivingEntity gojo = d.gojo;
        LivingEntity sukuna = d.sukuna;
        release(d);
        switch (d.result) {
            case GOJO -> {
                // The Santuário falls (the clients), the tunnel goes on to the void, Sukuna is caught like everyone. His
                // technique is burnt out when Gojo's domain is over.
                InfiniteVoid.pause(gojo, false);
                InfiniteVoid.seize(gojo, sukuna);
                int left = Math.max(0, DominioSpec.get(Att.get(gojo, ModAttachments.POWER).awakened).closeTick() - InfiniteVoid.age(gojo));
                burnout(sukuna, left + BURNOUT_TICKS);
                finish(d);
            }
            case SUKUNA -> {
                // The red takes the tunnel over (Stark, 2026-11-09): no shatter, no trip back to the world. Gojo's domain is
                // handed over right here (his technique burns out) and the Santuário opens from the black aura and the six
                // eyes (its fast version), with everyone who was in Gojo's domain caught in it, straight into its space.
                List<LivingEntity> victims = InfiniteVoid.everyone(gojo);
                victims.remove(sukuna);
                victims.add(gojo);
                if (sukuna.isAlive() && !sukuna.isRemoved() && sukuna.level() == d.level) {
                    InfiniteVoid.Handover from = InfiniteVoid.handOver(gojo);
                    ShrineDomain.startWon(sukuna, d.level, d.center, victims, from);
                } else {
                    // Nobody to take it over: it shatters as usual, everyone back in the world.
                    InfiniteVoid.cancel(gojo);
                }
                finish(d);
            }
            default -> {
                // The tie: into the void, both free, the sure hits cancelled out (Sukuna stays spared in Gojo's domain,
                // and no Santuário cuts Gojo). The barrier starts cracking.
                InfiniteVoid.toVoid(gojo);
                InfiniteVoid.pause(gojo, false);
                d.phase = Phase.TIE;
                d.crackT = 0;
                d.crackLen = TIE_CRACK;
                send(d, M_TIE, d.crackLen, d.reopens, 0);
                tell(gojo, "duel_tie_gojo");
                tell(sukuna, "duel_tie_sukuna");
            }
        }
    }

    private static void tie(Duel d) {
        if (!InfiniteVoid.open(d.gojo)) {
            // The void closed on its own before the barrier broke: over all the same.
            burnout(d.sukuna);
            finish(d);
            return;
        }
        d.crackT++;
        // A Gojo bot reopens it when it is about to break (not forever).
        if (!(d.gojo instanceof Player) && d.reopens < 3 && d.crackT == (int) (d.crackLen * 0.85) && RANDOM.nextDouble() < 0.7) {
            reopen(d);
            return;
        }
        if (d.crackT < d.crackLen) {
            return;
        }
        // The Santuário broke the barrier: both domains fall, everyone back in the world, both techniques burnt out.
        InfiniteVoid.cancel(d.gojo);
        burnout(d.sukuna);
        d.level.playSound(null, d.arena.x, d.arena.y + 2, d.arena.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3f, 0.5f);
        finish(d);
    }

    private static void reopen(Duel d) {
        LivingEntity gojo = d.gojo;
        if (gojo instanceof ServerPlayer && !Att.get(gojo, ModAttachments.POWER).noCooldown && !((ServerPlayer) gojo).isCreative()) {
            if (Stamina.exhausted(gojo) || Stamina.get(gojo) < Stamina.max(gojo) * REOPEN_COST / 100.0) {
                tell(gojo, "duel_reopen_stamina");
                return;
            }
            Stamina.drain(gojo, REOPEN_COST);
        }
        d.reopens++;
        d.crackLen = Math.max(REOPEN_MIN, (int) Math.round(d.crackLen * REOPEN_SHRINK));
        d.crackT = 0;
        InfiniteVoid.rewindVoid(gojo, REOPEN_REWIND);
        send(d, M_TIE, d.crackLen, d.reopens, 1);
        d.level.playSound(null, gojo.getX(), gojo.getY() + 1, gojo.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.7f);
    }

    private static void burnout(LivingEntity e) {
        burnout(e, BURNOUT_TICKS);
    }

    private static void burnout(LivingEntity e, int ticks) {
        if (!e.isAlive() || !Att.has(e, ModAttachments.POWER)) {
            return;
        }
        PowerLogic.seal(e, ticks);
        if (e instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.soulsjjk.technique_burnout", ticks / 20)
                    .withStyle(st -> st.withColor(0xFF5555)), true);
        }
    }

    private static void tell(LivingEntity e, String key) {
        if (e instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.soulsjjk." + key).withStyle(st -> st.withColor(0xFFD9DE)), true);
        }
    }

    // ---- hooks ----

    // A player's client measured one of their moves.
    public static void input(ServerPlayer player, int index, int value) {
        Duel d = of(player);
        if (d == null || d.phase != Phase.DUEL) {
            return;
        }
        int side = d.gojo == player ? GOJO : SUKUNA;
        if (!d.human[side]) {
            return;
        }
        score(d, side, index, value);
    }

    // V in a duel: Gojo reopens the void in the tie; every other press is swallowed (nobody closes or opens anything
    // while the two fight for the domain). True when it was theirs.
    public static boolean press(LivingEntity caster) {
        Duel d = of(caster);
        if (d == null) {
            return false;
        }
        if (d.phase == Phase.TIE) {
            if (caster == d.gojo) {
                reopen(d);
                return true;
            }
            return false;
        }
        return true;
    }

    // Gojo's domain spares him: Sukuna, until the duel is decided (or in the tie).
    public static boolean exempt(LivingEntity gojo, LivingEntity victim) {
        for (Duel d : DUELS) {
            if (d.gojo == gojo && d.sukuna == victim && (d.phase == Phase.WHITE || d.phase == Phase.DUEL || d.phase == Phase.TIE)) {
                return true;
            }
        }
        return false;
    }

    // Who Gojo's tunnel faces him with: the one who fights him for it.
    public static LivingEntity foe(LivingEntity gojo) {
        for (Duel d : DUELS) {
            if (d.gojo == gojo && (d.phase == Phase.WHITE || d.phase == Phase.DUEL)) {
                return d.sukuna;
            }
        }
        return null;
    }

    // Nothing touches the two while they fight for the domain.
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (DUELS.isEmpty()) {
            return;
        }
        Duel d = of(event.getEntity());
        if (d != null && (d.phase == Phase.WHITE || d.phase == Phase.DUEL)) {
            event.setCanceled(true);
        }
    }

    public static void clear() {
        DUELS.clear();
    }

    private static void send(Duel d, int mode, int a, int b, int c) {
        ModPayloads.ExpansionDuel payload = new ModPayloads.ExpansionDuel(d.gojo.getId(), d.sukuna.getId(), mode, a, b, c);
        for (ServerPlayer player : d.level.players()) {
            if (player == d.gojo || player == d.sukuna || player.position().distanceToSqr(d.center) <= VIEW_DISTANCE * VIEW_DISTANCE
                    || player.position().distanceToSqr(d.arena) <= VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
