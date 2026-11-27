// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The SubAlmas out in the normal world (Stark 2026-11-11): any of them can turn up anywhere, in any biome, rare, and
// never more than two tiers above the player (a Tier 10 meets up to Tier 8), so the world grows with them. Sometimes
// two of different universes turn up already fighting each other (Shikamaru against Goku): walk in, finish both, take
// both souls. Nobody strong in the first minutes of a new player (the "Raditz in the face" of the first test).
public final class RoamingSouls {
    private static final int INTERVAL = 20 * 75;
    private static final double CHANCE = 0.4;
    private static final double PAIR_CHANCE = 0.3;
    private static final int GRACE_TICKS = 20 * 60 * 5;
    private static final double NEARBY = 96;
    private static final int MIN_DIST = 40;
    private static final int MAX_DIST = 72;

    // After a SubAlma out in the world is beaten (Stark 2026-11-20: "as subalmas tao spawnando dps d derrotar?"): with
    // a draw every 75 s and the home biome picking its own 75% of the time, Goku in the Montanhas Paozu or Gaara in the
    // Deserto do Vento came back a minute or two after the kill, and it looked like the same one respawning. Now the
    // players who were there get a rest (QUIET, no roaming SubAlma for them) and that same one keeps away from them for
    // longer (SAME_AWAY). In memory, by the server's tick count: a restart or another world starts clean.
    private static final int QUIET = 20 * 60 * 5;
    private static final int SAME_AWAY = 20 * 60 * 20;
    private static final Map<UUID, Integer> QUIET_UNTIL = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> AWAY_UNTIL = new HashMap<>();

    private RoamingSouls() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoamingSouls::onServerTick);
        // LOWEST and not when cancelled: a death another listener takes back (a revive) is no kill
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, RoamingSouls::onDeath);
        // Not forgotten on logout (2026-11-27): leaving and coming back cleared the rest, and the same SubAlma could turn
        // up again right after. The entries end by themselves (by tick count) and the server's stop clears them all.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            QUIET_UNTIL.clear();
            AWAY_UNTIL.clear();
        });
    }

    // A SubAlma of the world killed by a player: the rest for every player near it (the party that fought it too).
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SoulBoss boss) || boss.level().isClientSide || boss.isBoss()
                || boss.puppetOwner() != null || boss.dungeonFight() || boss.level().dimension() != Level.OVERWORLD
                || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        ChallengeSpec spec = ChallengeSpec.get();
        String id = boss.bossId();
        if (spec == null || spec.universeOf(id) == null || ChallengeArenas.isBoss(id)) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level();
        int now = level.getServer().getTickCount();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(NEARBY))) {
            QUIET_UNTIL.put(player.getUUID(), now + QUIET);
            AWAY_UNTIL.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(id, now + SAME_AWAY);
        }
    }

    // The grace counts the player's whole time in this world (Stark 2026-11-27: "mundo ta vazio de subalma"): it used
    // tickCount, which starts over at every death and every login, so in a mod where you die a lot the first five
    // minutes never ended and nobody turned up.
    private static int playTime(ServerPlayer player) {
        return player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
    }

    // True while that player rests after a kill.
    public static boolean resting(ServerPlayer player) {
        return resting(player, player.getServer().getTickCount());
    }

    private static boolean resting(ServerPlayer player, int now) {
        Integer until = QUIET_UNTIL.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (now >= until) {
            QUIET_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // The pool without the ones that player beat out in the world a short while ago.
    private static List<String> withoutRecent(ServerPlayer player, List<String> pool, int now) {
        Map<String, Integer> away = AWAY_UNTIL.get(player.getUUID());
        if (away == null) {
            return pool;
        }
        away.values().removeIf(until -> now >= until);
        if (away.isEmpty()) {
            AWAY_UNTIL.remove(player.getUUID());
            return pool;
        }
        List<String> out = new ArrayList<>();
        for (String id : pool) {
            if (!away.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 37 || !Progression.on(event.getServer())) {
            return;
        }
        int now = event.getServer().getTickCount();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (player.isSpectator() || !player.isAlive() || level.dimension() != Level.OVERWORLD
                    || playTime(player) < GRACE_TICKS || level.getDifficulty() == Difficulty.PEACEFUL
                    || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || resting(player, now)) {
                continue;
            }
            RandomSource random = level.getRandom();
            List<String> all = pool(Tiers.of(player));
            List<String> pool = withoutRecent(player, all, now);
            // the same everywhere (Stark 2026-11-27): the home biomes (more of them, and their own ones) left the other
            // biomes emptier and some SubAlmas turned up much less than others
            if (random.nextDouble() >= CHANCE) {
                continue;
            }
            boolean someone = !level.getEntitiesOfClass(SoulBoss.class, player.getBoundingBox().inflate(NEARBY),
                    boss -> all.contains(boss.bossId())).isEmpty();
            if (pool.isEmpty() || someone) {
                continue;
            }
            BlockPos at = spot(level, player, random);
            if (at == null) {
                continue;
            }
            String first = pick(player, pool, random);
            if (first == null) {
                continue;
            }
            Entity a = spawn(level, first, at);
            if (a == null || random.nextDouble() >= PAIR_CHANCE) {
                continue;
            }
            // The fight: a second one from another universe, a few blocks away, at each other's throat.
            ChallengeSpec spec = ChallengeSpec.get();
            ChallengeSpec.Universe firstUniverse = spec.universeOf(first);
            List<String> rivals = new ArrayList<>();
            for (String id : pool) {
                ChallengeSpec.Universe u = spec.universeOf(id);
                if (!id.equals(first) && (firstUniverse == null || u == null || !u.id().equals(firstUniverse.id()))) {
                    rivals.add(id);
                }
            }
            if (rivals.isEmpty()) {
                continue;
            }
            String second = pick(player, rivals, random);
            if (second == null) {
                continue;
            }
            Entity b = spawn(level, second, ground(level, at.getX() + 6, at.getZ() + 3));
            if (a instanceof SoulBoss ba && b instanceof SoulBoss bb) {
                ba.setTarget(bb);
                bb.setTarget(ba);
                ba.setLastHurtByMob(bb);
                bb.setLastHurtByMob(ba);
            }
        }
    }

    // A SubAlma not yet beaten in its challenge comes much less (Stark 2026-11-12): the way to meet Kuririn is his
    // challenge, and after it he turns up more in the world. A draw that lands on one not beaten only goes through
    // NOT_BEATEN of the times, else nobody comes this time; null then.
    private static final double NOT_BEATEN = 0.25;

    // A draw that misses one not beaten goes to one the player has beaten, if any (Stark 2026-11-27: the world was
    // empty; with few challenges done, three draws in four ended with nobody).
    private static String pick(ServerPlayer player, List<String> from, RandomSource random) {
        if (from.isEmpty()) {
            return null;
        }
        String id = from.get(random.nextInt(from.size()));
        List<String> wins = ChallengeArenas.wins(player);
        if (wins.contains(id) || random.nextDouble() < NOT_BEATEN) {
            return id;
        }
        List<String> beaten = new ArrayList<>();
        for (String other : from) {
            if (wins.contains(other)) {
                beaten.add(other);
            }
        }
        return beaten.isEmpty() ? null : beaten.get(random.nextInt(beaten.size()));
    }

    // The SubAlmas of every challenge book whose tier is at most two above the player's.
    private static List<String> pool(int playerTier) {
        List<String> ids = new ArrayList<>();
        ChallengeSpec spec = ChallengeSpec.get();
        if (spec == null || spec.universos() == null) {
            return ids;
        }
        for (ChallengeSpec.Universe universe : spec.universos()) {
            if (universe.desafios() == null) {
                continue;
            }
            for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                if (ChallengeArenas.isBoss(challenge.id())) {
                    continue;
                }
                Integer tier = null;
                try {
                    tier = BossSpec.get(challenge.id()).tier();
                } catch (RuntimeException ignored) {
                }
                if (tier != null && tier >= playerTier - 2) {
                    ids.add(challenge.id());
                }
            }
        }
        return ids;
    }

    private static Entity spawn(ServerLevel level, String id, BlockPos at) {
        if (at == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id));
        if (type == EntityType.PIG) {
            return null;
        }
        return type.spawn(level, at, MobSpawnType.EVENT);
    }

    private static BlockPos spot(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
            BlockPos pos = ground(level, player.getBlockX() + (int) Math.round(Math.cos(angle) * dist),
                    player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist));
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    // Dry, solid ground with room to stand, in a loaded chunk; or null. The same test as the universe mobs (Stark
    // 2026-11-12: no SubAlma ever seen): its old copy asked for pure air, so grass, flowers and snow failed the spot.
    private static BlockPos ground(ServerLevel level, int x, int z) {
        return UniverseSpawns.ground(level, x, z);
    }
}
