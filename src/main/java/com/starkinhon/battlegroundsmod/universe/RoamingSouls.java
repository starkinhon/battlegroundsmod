package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
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

    private RoamingSouls() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoamingSouls::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 37 || !Progression.on(event.getServer())) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (player.isSpectator() || !player.isAlive() || level.dimension() != Level.OVERWORLD
                    || player.tickCount < GRACE_TICKS || level.getDifficulty() == Difficulty.PEACEFUL
                    || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }
            RandomSource random = level.getRandom();
            List<String> pool = pool(Tiers.of(player));
            // in their own biome they turn up more, and more of them are the ones who live there (Stark 2026-11-12:
            // the Leaf's ninja in the Floresta da Folha, Gaara in the Deserto do Vento)
            List<String> home = home(level, player.blockPosition(), pool);
            if (random.nextDouble() >= (home.isEmpty() ? CHANCE : HOME_CHANCE)) {
                continue;
            }
            boolean someone = !level.getEntitiesOfClass(SoulBoss.class, player.getBoundingBox().inflate(NEARBY),
                    boss -> pool.contains(boss.bossId())).isEmpty();
            if (pool.isEmpty() || someone) {
                continue;
            }
            BlockPos at = spot(level, player, random);
            if (at == null) {
                continue;
            }
            List<String> homeHere = home(level, at, pool);
            String first = !homeHere.isEmpty() && random.nextDouble() < HOME_PICK ? homeHere.get(random.nextInt(homeHere.size()))
                    : pool.get(random.nextInt(pool.size()));
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
            Entity b = spawn(level, rivals.get(random.nextInt(rivals.size())), ground(level, at.getX() + 6, at.getZ() + 3));
            if (a instanceof SoulBoss ba && b instanceof SoulBoss bb) {
                ba.setTarget(bb);
                bb.setTarget(ba);
                ba.setLastHurtByMob(bb);
                bb.setLastHurtByMob(ba);
            }
        }
    }

    private static final double HOME_CHANCE = 0.7;
    private static final double HOME_PICK = 0.75;
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>, List<String>> HOMES = java.util.Map.of(
            com.starkinhon.battlegroundsmod.world.ModWorldgen.FLORESTA_DA_FOLHA, List.of("naruto", "sasuke", "kakashi", "shikamaru", "ino"),
            com.starkinhon.battlegroundsmod.world.ModWorldgen.DESERTO_DO_VENTO, List.of("gaara"));

    // The SubAlmas of the pool who live in the biome at pos.
    private static List<String> home(ServerLevel level, BlockPos pos, List<String> pool) {
        var biome = level.getBiome(pos);
        for (var e : HOMES.entrySet()) {
            if (biome.is(e.getKey())) {
                List<String> out = new ArrayList<>();
                for (String id : e.getValue()) {
                    if (pool.contains(id)) {
                        out.add(id);
                    }
                }
                return out;
            }
        }
        return List.of();
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

    // Dry, solid ground with room to stand, in a loaded chunk; or null.
    private static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (y <= level.getMinBuildHeight() || !level.getFluidState(pos.below()).isEmpty()
                || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return null;
        }
        return pos;
    }
}
