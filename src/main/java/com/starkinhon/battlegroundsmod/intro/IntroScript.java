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
import com.starkinhon.battlegroundsmod.universe.EnergyControl;

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
    // Madara's kit in the intro: only the Katon awake; the U opens all the rest, one point per skill, in the book's
    // own order (X, C, B, the Shift variants, then the Meteoro and the Meteoro Duplo last). Nothing else comes awake
    // already (Stark 2026-11-19: the Shift skills showed up open, and the B was counted on the wrong slot, so the goal
    // stopped at 3 of 4 and the Meteoro Duplo never opened).
    private static final String MADARA = "uchiha_ghost";
    // The player's tier in every scene (Tiers: 0 is a god, 10 a human).
    private static final int INTRO_TIER = 1;
    private static final List<Integer> MADARA_START = List.of(0);
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
            // the card pauses the game only in single player; on a LAN the server goes on, so the enemy at least
            // stands still while it is open (issue #379)
            SoulBoss boss = boss();
            if (boss != null && !boss.introHold) {
                boss.introHold = true;
                heldForCard = true;
            }
            keepEnemy(player);
            return;
        }
        if (heldForCard) {
            heldForCard = false;
            SoulBoss boss = boss();
            if (boss != null) {
                boss.introHold = false;
            }
        }
        rescue(player);
        if (index < 0 || steps.get(index).tick(this, player, t)) {
            index++;
            t = 0;
            hits = 0;
            stepCast = -1;
            stepCard = -1;
            if (index >= steps.size()) {
                Intro.end(player, true, false);
                return;
            }
        } else {
            t++;
        }
        keepEnemy(player);
    }

    private boolean heldForCard;

    // Out of the arena (thrown off the edge, or under the floor): back on the act's spot (issue #379).
    private void rescue(ServerPlayer p) {
        if (act < 0 || level == null || center == null || p.level() != level) {
            return;
        }
        ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(act));
        if (p.getY() > center.getY() + Math.min(0, meta.minY()) - 16) {
            return;
        }
        int[] j = meta.jogador();
        BlockPos at = center.offset(j[0], j[1], j[2]);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
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
        SoulBoss gojo = ally();
        if (gojo != null) {
            com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.GOJO_WINS.remove(gojo.getUUID());
            com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.cancel(gojo);
            gojo.discard();
        }
        ally = null;
        SoulBoss boss = boss();
        if (boss != null) {
            boss.discard();
        }
        enemy = null;
    }

    // Gojo, who walks with the player in Shibuya (not an enemy).
    private UUID ally;

    private SoulBoss ally() {
        if (ally == null || level == null) {
            return null;
        }
        Entity e = level.getEntity(ally);
        return e instanceof SoulBoss b && b.isAlive() ? b : null;
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
    // A scene left behind by a closed game (an enemy saved in the row's chunks) never shows up twice
    // (Stark 2026-11-19: two Hashiramas): any character in this act's arena that is not this scene's goes away.
    private static void sweep(IntroScript s) {
        if (s.level == null || s.center == null || s.act < 0) {
            return;
        }
        int r = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(s.act)).raio() + 16;
        for (SoulBoss other : s.level.getEntitiesOfClass(SoulBoss.class, new net.minecraft.world.phys.AABB(s.center).inflate(r, 96, r))) {
            if (!other.getUUID().equals(s.enemy) && !other.getUUID().equals(s.ally)) {
                other.discard();
            }
        }
    }

    private void keepEnemy(ServerPlayer player) {
        if (player.tickCount % 20 == 7) {
            sweep(this);
        }
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
        // a strong body for the scenes, whatever the real tier (Stark 2026-11-19: at Tier 10 the Hashirama crumbled the bar)
        data.tier = Math.min(p.getData(ModAttachments.POWER).tier, INTRO_TIER);
        data.outfit = true;
        p.setData(ModAttachments.POWER, data);
        // lent techniques (the real ones are in the backup; nothing here counts as learned, Achievements.counts):
        // the Controle de Energia, so the river of the Vale do Fim (and any water on the way) is ground, not a trap,
        // and the flight only for the desert's Goku (Stark 2026-11-19: "Voe" with no way to fly before the Gohan teaches
        // it); the Madara and the Itadori stay on the ground, where their scenes are
        boolean flies = com.starkinhon.battlegroundsmod.power.Powers.GOKU.id().equals(power);
        p.setData(ModAttachments.TECHNIQUES, flies
                ? List.of(EnergyControl.TECHNIQUE, com.starkinhon.battlegroundsmod.universe.Flight.TECHNIQUE)
                : List.of(EnergyControl.TECHNIQUE));
        EnergyControl.sync(p);
        p.setData(ModAttachments.SOUL_SYNC, Map.of(power, new SoulSync.Entry(points, 0f, points, List.copyOf(awake))));
        Tiers.apply(p);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        PowerLogic.sync(p);
        SoulSync.sync(p);
    }

    // The Hashirama comes in close and already fighting (Stark 2026-11-19: he stood still far across the river).
    private Step spawnHashirama(float life) {
        return spawn(() -> ModEntities.HASHIRAMA.get(), life, 5);
    }

    private Step spawn(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, float life) {
        return spawn(type, life, 0);
    }

    // near > 0: that many blocks in front of the player, towards the challenger's spot, and fighting from the start
    // (back to the challenger's spot if that place is inside a block). 0: on the challenger's spot, held.
    private Step spawn(java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, float life, int near) {
        return (s, p, t) -> {
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(s.act));
            int[] d = meta.desafiante();
            BlockPos at = s.center.offset(d[0], d[1], d[2]);
            sweep(s);
            Entity e = type.get().spawn(s.level, at, MobSpawnType.EVENT);
            if (!(e instanceof SoulBoss boss)) {
                BattlegroundsMod.LOGGER.error("[intro] o inimigo do ato {} não nasceu", s.act);
                return true;
            }
            if (near > 0) {
                Vec3 toward = Vec3.atBottomCenterOf(at).subtract(p.position()).multiply(1, 0, 1);
                if (toward.lengthSqr() > near * near) {
                    Vec3 spot = p.position().add(toward.normalize().scale(near));
                    float yaw = (float) (Mth.atan2(p.getZ() - spot.z, p.getX() - spot.x) * Mth.RAD_TO_DEG) - 90;
                    Vec3 back = boss.position();
                    boss.moveTo(spot.x, p.getY(), spot.z, yaw, 0);
                    if (!s.level.noCollision(boss)) {
                        boss.moveTo(back.x, back.y, back.z, yaw, 0);
                    }
                }
            }
            boss.setPersistenceRequired();
            boss.introHold = near <= 0;
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
                    boss.introHold = false;     // he keeps fighting (punches and dashes): the combo is on a real fight
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
        List<Integer> open = all(MADARA).stream().filter(slot -> !MADARA_START.contains(slot)).toList();
        add(enter(0, MADARA, MADARA_START, open.size()));
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
        add(awaken("desperte", MADARA, open));
        add(life(0.58f));
        add(technique(HS_DRAGON, 12, "com_tudo"));
        add(wait(60));
        add(useSkill("use_skills", Set.of(1, 2, PowerData.SLOT_EXTRA, PowerData.SLOT_SHIFT, PowerData.SLOT_SHIFT + 1, PowerData.SLOT_SHIFT + 2), 2));
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
        add(leaveAct());
        actTwo();
        actThree();
    }

    // The scene's enemy gone, the player out of combat (the white of the soul swap still on screen).
    private Step leaveAct() {
        return (s, p, t) -> {
            s.dispose();
            CombatMode.leave(p);
            if (p.getAbilities().flying) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            return true;
        };
    }

    // ---------------------------------------------------------------- Ato 2: Deserto dos Saiyajins

    // Goku's whole kit is awake (the lesson here is the air, not the soul).
    private static List<Integer> all(String power) {
        return SoulSync.skills(com.starkinhon.battlegroundsmod.power.Powers.get(power));
    }

    private void actTwo() {
        add(build(1));
        add(enter(1, "goku", all("goku"), 0));
        add(spawn(() -> ModEntities.VEGETA.get(), 1f));
        add(ui("flash", "", 0, 0));
        add(ui("title", "deserto", 70, 0));
        add(wait(20));
        add(ui("say", "kakarotto", 50, 0));
        add(wait(40));
        // voa: ele vem atrás no ar
        add(card("voo"));
        add(fly("voe"));
        add(airborne(160));
        add(card("aereo"));
        // luta um pouco no ar
        add(life(0.85f));
        add(fight("acerte_vegeta", 5));
        // ele abre a Rajada de ki: pausa com a primeira bola vindo
        add(life(0.7f));
        add(airMove("rajada", "devolver"));
        add(wait(60));
        // de novo; ele vem na Investida: pausa na arrancada
        add(fight("acerte_vegeta", 4));
        add(life(0.55f));
        add(airMove("investida", "ele_vem"));
        add(wait(50));
        add(life(0.3f));
        add(fight("derrube_vegeta", 6));
        add(hold());
        add(ui("flash", "itadori", 1, 0));
        add(wait(40));
        add(leaveAct());
    }

    private Step hold() {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            if (boss != null) {
                boss.introHold = true;
            }
            return true;
        };
    }

    // The player takes off (Espaço 2x). The enemy chases on the ground meanwhile, and follows it up.
    private Step fly(String goalId) {
        return (s, p, t) -> {
            if (t == 0) {
                SoulBoss boss = s.boss();
                if (boss != null) {
                    boss.introHold = false;
                    boss.introMoves = Set.of();
                }
                Intro.ui(p, "goal", goalId, 0, 1);
            }
            if (com.starkinhon.battlegroundsmod.universe.Flight.flying(p)) {
                Intro.ui(p, "goal", goalId, 1, 1);
                return true;
            }
            return false;
        };
    }

    // Waits for the enemy to be up in the air with the player (or gives up after max ticks).
    private Step airborne(int max) {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            return boss == null || t >= max || boss.air() != null && boss.air().fightingInAir();
        };
    }

    // The enemy's air move, forced; the card comes up when it really starts (the first ki ball, the boost).
    private Step airMove(String move, String cardId) {
        return (s, p, t) -> {
            SoulBoss boss = s.boss();
            if (boss == null || boss.air() == null || t > 300) {
                return true;
            }
            var air = boss.air();
            if (t == 0) {
                heal(p);
                boss.introHold = true;
                s.stepCast = -1;
            }
            if (s.stepCast < 0) {
                if (air.fightingInAir()) {
                    air.force(move);
                    s.stepCast = t;
                }
                return false;
            }
            boolean started = air.currentMove().equalsIgnoreCase(move) && (!"rajada".equals(move) || air.shotsFired() >= 1);
            if (s.stepCard < 0 && (started || t - s.stepCast > 60)) {
                s.stepCard = t;
                s.card = true;
                Intro.ui(p, "card", cardId, 0, 0);
                return false;
            }
            return s.stepCard >= 0;
        };
    }

    private int stepCard = -1;

    // ---------------------------------------------------------------- Ato 3: Shibuya, e o duelo

    private static final int[] TIPS_AT = {8, 35, 60, 85};

    private void actThree() {
        add(build(2));
        add(enter(2, "itadori", all("itadori"), 0));
        add(spawnGojo());
        add(ui("flash", "", 0, 0));
        add(ui("title", "shibuya", 80, 0));
        add(wait(40));
        add(follow("siga_gojo"));
        add(ui("goal", "", 0, 0));
        add(ui("title", "metro", 60, 0));
        add(wait(30));
        add(build(3));
        add(intoMetro());
        add(enter(3, "itadori", all("itadori"), 0));
        add(spawn(() -> ModEntities.SUKUNA.get(), 1f));
        add(wait(30));
        add(ui("say", "coincidencia", 70, 0));
        add(wait(75));
        add(ui("say", "licao", 70, 0));
        add(wait(75));
        add(ui("say", "so_eu", 70, 0));
        add(wait(70));
        add(duel());
        // corta no branco do túnel, antes do Vazio Infinito aparecer
        add(ui("flash", "", 1, 0));
        add(wait(8));
        add(leaveAct());
        add(ui("logo", "", 0, 0));
        add(wait(10));
        add(ui("flash", "", 0, 0));
        add(wait(130));
    }

    // Gojo a few steps ahead of the player, on the way to the far end of the crossing.
    private Step spawnGojo() {
        return (s, p, t) -> {
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(s.act));
            int[] j = meta.jogador(), d = meta.desafiante();
            Vec3 dir = new Vec3(d[0] - j[0], 0, d[2] - j[2]).normalize();
            BlockPos at = BlockPos.containing(s.center.getX() + j[0] + 0.5 + dir.x * 4, s.center.getY() + j[1], s.center.getZ() + j[2] + 0.5 + dir.z * 4);
            newGojo(s, at);
            return true;
        };
    }

    private static void newGojo(IntroScript s, BlockPos at) {
        Entity e = ModEntities.GOJO.get().spawn(s.level, at, MobSpawnType.EVENT);
        if (e instanceof SoulBoss gojo) {
            gojo.setPersistenceRequired();
            gojo.introHold = true;
            gojo.introMoves = Set.of();
            gojo.introFloor = 1f;
            s.ally = gojo.getUUID();
        }
    }

    // "Siga o Gojo": he walks to the far end, waiting whenever the player falls behind, and gives his tips on the way.
    private Step follow(String goalId) {
        return (s, p, t) -> {
            SoulBoss gojo = s.ally();
            if (gojo == null) {
                return true;
            }
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(s.act));
            int[] j = meta.jogador(), d = meta.desafiante();
            Vec3 from = Vec3.atBottomCenterOf(s.center.offset(j[0], j[1], j[2]));
            Vec3 to = Vec3.atBottomCenterOf(s.center.offset(d[0], d[1], d[2]));
            if (t == 0) {
                s.used = 0;
                Intro.ui(p, "goal", goalId, 0, 1);
            }
            double total = Math.max(1, from.distanceTo(to));
            int pct = (int) Math.round(100 * Math.min(1, gojo.position().subtract(from).dot(to.subtract(from)) / (total * total)));
            if (s.used < TIPS_AT.length && pct >= TIPS_AT[s.used]) {
                Intro.ui(p, "say", "dica" + (s.used + 1), 110, 0);
                s.used++;
            }
            boolean there = gojo.position().distanceTo(to) < 2.5;
            if (p.distanceTo(gojo) > 9) {
                gojo.getNavigation().stop();
                gojo.getLookControl().setLookAt(p, 30, 30);
            } else if (!there && (t % 10 == 0 || gojo.getNavigation().isDone())) {
                gojo.getNavigation().moveTo(to.x, to.y, to.z, 0.55);
            }
            if (t > 2400 && !there) {
                gojo.teleportTo(to.x, to.y, to.z);     // stuck on something: the scene goes on
                return true;
            }
            return there && p.distanceTo(gojo) < 9;
        };
    }

    // Gojo goes down to the metro ahead of the player: the Shibuya one (still loaded, the player is still there behind
    // the black screen) goes away and a new one waits in the tunnel, 6 blocks ahead of the player's spot. Moving the
    // same entity 1024 blocks after the player could find his chunk already unloaded and skip the duel (issue #379).
    private Step intoMetro() {
        return (s, p, t) -> {
            if (s.ally != null) {
                Entity old = p.serverLevel().getEntity(s.ally);
                if (old != null) {
                    com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.GOJO_WINS.remove(old.getUUID());
                    old.discard();
                }
                s.ally = null;
            }
            ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.INTRO_ACTS.get(3));
            int[] j = meta.jogador(), d = meta.desafiante();
            Vec3 dir = new Vec3(d[0] - j[0], 0, d[2] - j[2]).normalize();
            Vec3 at = Vec3.atBottomCenterOf(s.center.offset(j[0], j[1], j[2])).add(dir.scale(6));
            newGojo(s, BlockPos.containing(at));
            return true;
        };
    }

    // The Duelo de Expansões, Gojo against Sukuna (both held: only the domains). Gojo always wins it here; done a
    // moment after the result, with the Santuário falling and the tunnel going white.
    private Step duel() {
        return (s, p, t) -> {
            SoulBoss gojo = s.ally(), sukuna = s.boss();
            if (gojo == null || sukuna == null || t > 1800) {
                return true;
            }
            boolean fighting = com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.fighting(gojo);
            if (t == 0) {
                com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.GOJO_WINS.add(gojo.getUUID());
                s.stepCast = -1;
                s.stepCard = -1;
                for (SoulBoss b : List.of(gojo, sukuna)) {
                    PowerData data = b.powerData();
                    data.ult = PowerData.MAX_ULT;
                    data.cooldowns[PowerData.SLOT_ULT] = 0;
                    data.busyTicks = 0;
                }
            }
            if (s.stepCast < 0) {
                if (PowerLogic.cast(gojo, PowerData.SLOT_ULT)) {
                    s.stepCast = t;
                }
                return false;
            }
            // Sukuna answers on his own (a boss always does); if he hasn't by now, he does
            if (!fighting && s.stepCard < 0 && t - s.stepCast == 30) {
                sukuna.powerData().cooldowns[PowerData.SLOT_ULT] = 0;
                PowerLogic.cast(sukuna, PowerData.SLOT_ULT);
            }
            if (fighting && s.stepCard < 0) {
                s.stepCard = 0;          // the duel is on
            }
            if (s.stepCard == 0 && !fighting) {
                s.stepCard = t;          // decided: Gojo won
            }
            if (s.stepCard < 0 && t - s.stepCast > 200) {
                return true;             // no duel came: the scene ends here
            }
            return s.stepCard > 0 && t - s.stepCard >= 56;
        };
    }
}
