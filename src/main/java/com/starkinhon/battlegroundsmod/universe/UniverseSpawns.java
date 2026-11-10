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
            if (DemonSlayerWorld.UNIVERSE.equals(id) && level.isDay()) {
                // The demons hide from the sun.
                continue;
            }
            List<String> mobs = universe.mobs();
            if (DragonBallWorld.UNIVERSE.equals(id)) {
                if (com.starkinhon.battlegroundsmod.world.DragonBallPlaces.onNamek(player)) {
                    spawnNamek(level, player);
                } else {
                    spawnEarth(level, player);
                }
                continue;
            }
            AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
            List<String> pool = mobs;
            int nearby = level.getEntitiesOfClass(SoulBoss.class, around, mob -> pool.contains(mob.bossId())).size();
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

    // The Earth's enemies by region (Stark 2026-11-10): each kind of ground of the drawn map has its own. The plains and
    // forests have the Saibamen and the dinosaurs (the Dragon World is full of them), the deserts the desert bandits
    // and raptors, the snow the Red Ribbon (its Muscle Tower stood in the snow) and the yeti, the islands and beaches
    // the pirates; around the Red Ribbon base its soldiers. A group is picked for the spot it lands on.
    static final List<String> EARTH_MOBS = List.of("saibaman", "soldado_red_ribbon", "bandido_deserto", "pirata", "yeti",
            "raptor", "tiranossauro");

    static List<String> earthMobs(int x, int z) {
        double rx = x - com.starkinhon.battlegroundsmod.world.DragonBallPlaces.RED_RIBBON_X;
        double rz = z - com.starkinhon.battlegroundsmod.world.DragonBallPlaces.RED_RIBBON_Z;
        if (rx * rx + rz * rz < 90 * 90) {
            return List.of("soldado_red_ribbon", "soldado_red_ribbon", "saibaman");
        }
        com.starkinhon.battlegroundsmod.world.WorldMap map = com.starkinhon.battlegroundsmod.world.WorldMap.get(
                com.starkinhon.battlegroundsmod.world.DragonBallPlaces.MAP);
        int kind = map.kind(x, z);
        if (kind == com.starkinhon.battlegroundsmod.world.WorldMap.SAND || map.landSize(x, z) < ISLAND) {
            return List.of("pirata", "pirata", "pirata", "saibaman", "raptor");
        }
        return switch (kind) {
            case com.starkinhon.battlegroundsmod.world.WorldMap.SNOW -> List.of("soldado_red_ribbon", "soldado_red_ribbon", "yeti");
            case com.starkinhon.battlegroundsmod.world.WorldMap.DESERT, com.starkinhon.battlegroundsmod.world.WorldMap.CANYON,
                    com.starkinhon.battlegroundsmod.world.WorldMap.REDROCK, com.starkinhon.battlegroundsmod.world.WorldMap.CRATER,
                    com.starkinhon.battlegroundsmod.world.WorldMap.LAVA -> List.of("bandido_deserto", "bandido_deserto", "raptor");
            default -> List.of("saibaman", "saibaman", "saibaman", "raptor", "raptor", "tiranossauro");
        };
    }

    // Land smaller than this (in columns) is an island: everything but the big continent.
    private static final int ISLAND = 100_000;

    private static void spawnEarth(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
        int nearby = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, around, mob -> EARTH_MOBS.contains(
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath())).size();
        if (nearby >= NEARBY_CAP) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        List<String> mobs = earthMobs(x, z);
        String id = mobs.get(random.nextInt(mobs.size()));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, id));
        if (type == EntityType.PIG) {
            return;
        }
        // the big ones alone, the raptors in packs
        int count = "tiranossauro".equals(id) || "yeti".equals(id) ? 1 : "raptor".equals(id) ? 2 + random.nextInt(2)
                : 1 + random.nextInt(3);
        count = Math.min(count, NEARBY_CAP - nearby);
        for (int i = 0; i < count; i++) {
            BlockPos pos = ground(level, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2);
            if (pos != null && !nearWater(level, pos)) {
                type.spawn(level, pos, MobSpawnType.EVENT);
            }
        }
    }

    // Freeza's soldiers on Namek (its own dimension now): in groups on dry ground around the player, never too many.
    private static void spawnNamek(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "soldado_freeza"));
        AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
        int nearby = level.getEntitiesOfClass(SoulBoss.class, around, mob -> "soldado_freeza".equals(mob.bossId())).size();
        if (type == EntityType.PIG || nearby >= NEARBY_CAP) {
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        // never past the cap (Stark's test 2026-11-10: a group of 3 on top of 5 made 8), and away from Namek's green
        // sea, where they ended up swimming
        int count = Math.min(1 + random.nextInt(3), NEARBY_CAP - nearby);
        for (int i = 0; i < count; i++) {
            BlockPos pos = ground(level, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2);
            if (pos != null && !nearWater(level, pos)) {
                type.spawn(level, pos, MobSpawnType.EVENT);
            }
        }
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                for (int dy = -2; dy <= 0; dy++) {
                    if (!level.getFluidState(p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
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
