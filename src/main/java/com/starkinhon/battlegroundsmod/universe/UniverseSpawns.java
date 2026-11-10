package com.starkinhon.battlegroundsmod.universe;

import java.util.List;

import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The universe's own enemies around each player in a universe world, day and night (Stark, 2026-11-10: they only showed
// up at night). The vanilla spawner puts them in the monster pool with zombies, skeletons and creepers (about 500 of
// weight against their 30), so by day almost nothing came. Here every few seconds a group of one of the universe's
// mobs comes in 24-48 blocks from the player while there are few of them nearby; and half of the vanilla night
// monsters are dropped inside the universes, so the world feels like that universe.
public final class UniverseSpawns {
    private static final int INTERVAL = 100;
    private static final int NEARBY_CAP = 6;
    private static final double NEARBY_RADIUS = 64;
    private static final int MIN_DIST = 24;
    private static final int MAX_DIST = 48;
    private static final float VANILLA_KEPT = 0.5f;

    private UniverseSpawns() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onServerTick);
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onFinalizeSpawn);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 0 || !Progression.on(event.getServer())) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }
            String id = Universes.universeOf(level.dimension());
            ChallengeSpec.Universe universe = id == null ? null : ChallengeSpec.get().universe(id);
            if (universe == null || universe.mobs() == null || universe.mobs().isEmpty()) {
                continue;
            }
            List<String> mobs = universe.mobs();
            AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
            int nearby = level.getEntitiesOfClass(SoulBoss.class, around, mob -> mobs.contains(mob.bossId())).size();
            if (nearby < NEARBY_CAP) {
                spawnGroup(level, player, mobs);
            }
        }
    }

    private static void spawnGroup(ServerLevel level, ServerPlayer player, List<String> mobs) {
        RandomSource random = level.getRandom();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, mobs.get(random.nextInt(mobs.size()))));
        if (type == null || type == EntityType.PIG) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos pos = ground(level, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2);
            if (pos != null) {
                type.spawn(level, pos, MobSpawnType.EVENT);
            }
        }
    }

    // A dry spot on top of solid ground in a loaded chunk, or null.
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

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Monster)
                || event.getEntity() instanceof SoulBoss
                || event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (Universes.universeOf(level.dimension()) != null && Progression.on(level.getServer())
                && level.getRandom().nextFloat() >= VANILLA_KEPT) {
            event.setSpawnCancelled(true);
        }
    }
}
