package com.starkinhon.battlegroundsmod.intro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.ArenaTemplate;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.SoulSync;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

// One player's introduction: the scenes of the approved preview, step by step. Each step starts, runs every tick and
// says when it is done; a teaching card pauses everything (in single player the game itself pauses on the card's
// screen) until the player closes it. Between the lessons the player fights for real (the enemy's AI on, but with no
// techniques: punches and dashes), and when the enemy uses a technique the card comes up on its warning, so the
// player has time to read and then really react (Stark 2026-11-19).
final class IntroScript {
    // Madara's kit in the intro: only the Katon; the U opens the rest with these points (X, C, B, then the Meteoro).
    // The Shift variants come awake with the soul (Stark: "shift" only changes a skill), so the Meteoro can open last.
    private static final String MADARA = "uchiha_ghost";
    private static final List<Integer> MADARA_START = List.of(0, PowerData.SLOT_SHIFT, PowerData.SLOT_SHIFT + 1, PowerData.SLOT_SHIFT + 2);
    private static final List<Integer> MADARA_OPEN = List.of(1, 2, 4, PowerData.SLOT_ULT);
    // Hashirama's slots (power/powers/hashirama/Hashirama)
    private static final int HS_STAKES = 1, HS_PRISON = 2, HS_DRAGON = 3, HS_LANCE = 4, HS_BUDDHA = PowerData.SLOT_SHIFT_ULT;

    final UUID playerId;
    final int row;
    private final List<Step> steps = new ArrayList<>();
    private int index = -1;
    private int t;
    private boolean card;
    // the enemy of the scene and its life bar target
    private UUID enemy;
    // the player's hits on the enemy: how many in this step, and the streak (a combo: hits close together)
    private int hits, streak, bestStreak;
    private long lastHit = -100;
    private ChallengeArenas.Placer placer;
    private BlockPos center;
    private ServerLevel level;
    private int act = -1;

    IntroScript(ServerPlayer player, int row) {
        this.playerId = player.getUUID();
        this.row = row;
        actOne();
    }

    private interface Step {
        // t = ticks since the step started (0 on the first call); true when done
        boolean tick(IntroScript s, ServerPlayer p, int t);
    }

    private void add(Step step) {
        steps.add(step);
    }

    void tick(ServerPlayer player) {
        if (card) {
            return;
        }
        if (index < 0 || steps.get(index).tick(this, player, t)) {
            index++;
            t = 0;
            hits = 0;
            stepCast = -1;
            if (index >= steps.size()) {
                Intro.end(player, true, false);
                return;
            }
        } else {
            t++;
        }
        keepEnemy(player);
    }

    boolean vulnerable() {
        return !card && act >= 0;
    }

    void cardClosed() {
        card = false;
    }

    void hit(LivingEntity hurt, float amount) {
        if (enemy == null || !hurt.getUUID().equals(enemy)) {
            return;
        }
        long now = hurt.level().getGameTime();
        hits++;
        streak = now - lastHit <= 30 ? streak + 1 : 1;
        bestStreak = Math.max(bestStreak, streak);
        lastHit = now;
    }

    void dispose() {
        if (placer != null) {
            placer.cancel();
            placer = null;
        }
        SoulBoss boss = boss();
        if (boss != null) {
            boss.discard();
        }
        enemy = null;
    }

    // ---------------------------------------------------------------- building blocks

    private SoulBoss boss() {
        if (enemy == null || level == null) {
            return null;
        }
        Entity e = level.getEntity(enemy);
        return e instanceof SoulBoss b && b.isAlive() ? b : null;
    }

    // The enemy keeps its target, stays on its floor and, while held by the script, stands facing the player.
    private void keepEnemy(ServerPlayer player) {
        SoulBoss boss = boss();
        if (boss == null) {
            return;
        }
        if (boss.getTarget() != player) {
            boss.setTarget(player);
        }
        if (boss.introHold) {
            boss.getNavigation().stop();
            boss.getLookControl().setLookAt(player, 30, 30);
        }
    }

    private static Step ui(String kind, String id, int a, int b) {
        return (s, p, t) -> {
            Intro.ui(p, kind, id, a, b);
            return true;
        };
    }

    private static Step wait(int ticks) {
        return (s, p, t) -> t >= ticks;
    }

    // The teaching card: the game waits for the player to close it.
    private static Step card(String id) {
        return (s, p, t) -> {
            if (t == 0) {
                s.card = true;
                Intro.ui(p, "card", id, 0, 0);
                return false;
            }
            return true;
        };
    }

    private static Step goal(String id, int need) {
        return (s, p, t) -> {
            Intro.ui(p, "goal", id, 0, need);
            return true;
        };
    }

    // Build the act's arena on the row (black screen while it goes up), then bring the player in.
    private Step build(int act) {
        return (s, p, t) -> {
            if (t == 0) {
                s.act = -1;
                s.level = p.server.getLevel(ChallengeArenas.introLevel(p.server, act));
                s.center = ChallengeArenas.introCenter(s.row, act);
                s.placer = ChallengeArenas.place(ChallengeArenas.INTRO_ACTS.get(act), s.level, s.center);
                Intro.ui(p, "black", "", 1, 0);
            }
            if (s.placer != null && !s.placer.step()) {
                if (t % 20 == 19) {
                    Intro.ui(p, "loading", "", s.placer.percent(), 0);
                }
                return false;
            }
            s.placer = null;
            return true;
        };
    }

    // The player on the act's spot, with the act's soul (only its first skills awake), full life, survival.
    private Step enter(int act, String power, List<Integer> awake, int points) {
        return (s, p, t) -> {
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(act));
            int[] j = meta.jogador(), d = meta.desafiante();
            double yaw = Mth.atan2(d[2] - j[2], d[0] - j[0]) * Mth.RAD_TO_DEG - 90;
            BlockPos at = s.center.offset(j[0], j[1], j[2]);
            if (p.gameMode.getGameModeForPlayer() != GameType.SURVIVAL && p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                p.setGameMode(GameType.SURVIVAL);
            }
            p.getInventory().clearContent();
            p.removeAllEffects();
            p.teleportTo(s.level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) yaw, 0);
            p.fallDistance = 0;
            p.setDeltaMovement(Vec3.ZERO);
            lend(p, power, awake, points);
            s.act = act;
            Intro.ui(p, "black", "", 0, 0);
            return true;
        };
    }

    // The soul of the scene, lent: its own Sincronia entry (the real one is in the backup) and a fresh fight state.
    private static void lend(ServerPlayer p, String power, List<Integer> awake, int points) {
        PowerData data = new PowerData();
        data.receivedBook = true;
        data.power = power;
        data.tier = p.getData(ModAttachments.POWER).tier;
        data.outfit = true;
        p.setData(ModAttachments.POWER, data);
        p.setData(ModAttachments.SOUL_SYNC, Map.of(power, new SoulSync.Entry(points, 0f, points, List.copyOf(awake))));
        Tiers.apply(p);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        PowerLogic.sync(p);
        SoulSync.sync(p);
    }

    private Step spawnHashirama(float life) {
        return (s, p, t) -> {
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(s.act));
            int[] d = meta.desafiante();
            BlockPos at = s.center.offset(d[0], d[1], d[2]);
            Entity e = ModEntities.HASHIRAMA.get().spawn(s.level, at, MobSpawnType.EVENT);
            if (!(e instanceof SoulBoss boss)) {
                BattlegroundsMod.LOGGER.error("[intro] Hashirama não nasceu");
                return true;
            }
            boss.setPersistenceRequired();
            boss.introHold = true;
            boss.introMoves = Set.of();
            boss.introFloor = life;
            boss.setHealth(boss.getMaxHealth() * life);
            boss.setTarget(p);
            s.enemy = boss.getUUID();
            return true;
        };
    }

    // The bar goes to this life and stays there until the script moves it again.
    private Step life(float life) {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            if (boss != null) {
                boss.introFloor = life;
                if (boss.getHealth() > boss.getMaxHealth() * life) {
                    boss.setHealth(boss.getMaxHealth() * life);
                }
            }
            return true;
        };
    }

    // Free fight: the enemy fights (punches and dashes, no techniques) until the player lands `need` hits.
    private Step fight(String goalId, int need) {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            if (t == 0) {
                if (boss != null) {
                    boss.introHold = false;
                    boss.introMoves = Set.of();
                }
                heal(p);
                Intro.ui(p, "goal", goalId, 0, need);
            }
            if (t % 5 == 0) {
                Intro.ui(p, "goal", goalId, Math.min(need, s.hits), need);
            }
            if (s.hits >= need || boss == null) {
                Intro.ui(p, "goal", goalId, need, need);
                return true;
            }
            return false;
        };
    }

    // A combo: `need` hits, each within 1.5 s of the last.
    private Step combo(String goalId) {
        return (s, p, t) -> {
            if (t == 0) {
                s.streak = 0;
                s.bestStreak = 0;
                SoulBoss boss = s.boss();
                if (boss != null) {
                    boss.introHold = true;      // he takes it: the first combo is on a still target
                }
                Intro.ui(p, "goal", goalId, 0, 1);
            }
            if (s.bestStreak >= 3) {
                Intro.ui(p, "goal", goalId, 1, 1);
                return true;
            }
            return false;
        };
    }

    // The enemy uses a technique (held by the script); after `warn` ticks (its warning on screen) the card comes up.
    private Step technique(int slot, int warn, String cardId) {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            if (boss == null) {
                return true;
            }
            if (t == 0) {
                boss.introHold = true;
                boss.getNavigation().stop();
                heal(p);
            }
            if (s.stepCast < 0) {
                PowerData data = boss.powerData();
                data.cooldowns[slot] = 0;
                data.busyTicks = 0;
                if (PowerData.isUlt(slot)) {
                    data.ult = PowerData.MAX_ULT;
                }
                if (PowerLogic.cast(boss, slot)) {
                    s.stepCast = t;
                } else if (t > 100) {
                    BattlegroundsMod.LOGGER.warn("[intro] o Hashirama não conseguiu usar o slot {}", slot);
                    return true;
                }
                return false;
            }
            if (cardId != null && t - s.stepCast == warn) {
                s.card = true;
                Intro.ui(p, "card", cardId, 0, 0);
                return false;
            }
            return t - s.stepCast > warn;
        };
    }

    private int stepCast = -1;

    // The player used this skill (cast went through) since the step started.
    private Step useSkill(String goalId, Set<Integer> slots, int need) {
        return (s, p, t) -> {
            if (t == 0) {
                s.used = 0;
                s.seenCast = p.getData(ModAttachments.POWER).lastCastTime;
                SoulBoss boss = s.boss();
                if (boss != null) {
                    boss.introHold = false;
                    boss.introMoves = Set.of();
                }
                Intro.ui(p, "goal", goalId, 0, need);
            }
            PowerData data = p.getData(ModAttachments.POWER);
            if (data.lastCastTime != s.seenCast) {
                s.seenCast = data.lastCastTime;
                if (slots.contains(data.lastCastSlot)) {
                    s.used++;
                    Intro.ui(p, "goal", goalId, Math.min(need, s.used), need);
                }
            }
            return s.used >= need;
        };
    }

    private int used;
    private long seenCast;

    // Waits for the skills to be awakened in the Livro das Almas (U).
    private Step awaken(String goalId, String power, List<Integer> slots) {
        return (s, p, t) -> {
            SoulSync.Entry e = SoulSync.get(p, power);
            int n = 0;
            for (int slot : slots) {
                if (e.has(slot)) {
                    n++;
                }
            }
            if (t % 5 == 0) {
                Intro.ui(p, "goal", goalId, n, slots.size());
            }
            return n >= slots.size();
        };
    }

    private static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
    }

    // ---------------------------------------------------------------- Ato 1: Vale do Fim

    private void actOne() {
        add(build(0));
        add(enter(0, MADARA, MADARA_START, MADARA_OPEN.size()));
        add(spawnHashirama(1f));
        add(ui("title", "vale", 70, 0));
        add(wait(50));
        // os golpes base, e um combo nele parado
        add(card("golpes"));
        add(combo("combo"));
        add(ui("goal", "", 0, 0));
        // luta um pouco; ele solta as Estacas: pausa com elas correndo atrás de você
        add(life(0.94f));
        add(fight("acerte_hashirama", 5));
        add(life(0.86f));
        add(technique(HS_STAKES, 12, "movimento"));
        add(wait(50));
        // luta; ele arma a Prisão: pausa no aviso, você sai antes de fechar
        add(fight("acerte_hashirama", 4));
        add(life(0.74f));
        add(technique(HS_PRISON, 10, "defesa"));
        add(wait(70));
        // o Katon numa janela livre, depois a Lança
        add(card("katon"));
        add(useSkill("use_katon", Set.of(0, PowerData.SLOT_SHIFT), 1));
        add(life(0.66f));
        add(technique(HS_LANCE, 6, "lanca"));
        add(wait(50));
        // luta, a alma no U, e o Dragão de Madeira
        add(fight("acerte_hashirama", 4));
        add(life(0.6f));
        add(card("alma"));
        add(awaken("desperte", MADARA, MADARA_OPEN));
        add(life(0.58f));
        add(technique(HS_DRAGON, 12, "com_tudo"));
        add(wait(60));
        add(useSkill("use_skills", Set.of(1, 2, 4, PowerData.SLOT_SHIFT + 1, PowerData.SLOT_SHIFT + 2), 2));
        // o Buda de Mil Mãos, e a troca de alma
        add(life(0.5f));
        add(technique(HS_BUDDHA, 0, null));
        add(wait(200));
        add(life(0.4f));
        add((s, p, t) -> {
            SoulBoss boss = s.boss();
            if (boss != null) {
                boss.introHold = true;
            }
            return true;
        });
        add(ui("flash", "goku", 1, 0));
        add(wait(40));
        add((s, p, t) -> {
            s.dispose();
            CombatMode.leave(p);
            return true;
        });
        // Os outros atos (Goku x Vegeta, Shibuya, o duelo) entram aqui. Por enquanto a intro fecha no fim do Ato 1.
        add(ui("logo", "", 0, 0));
        add(wait(120));
    }
}
