package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.db.Androide8;
import com.starkinhon.battlegroundsmod.animal.db.Bora;
import com.starkinhon.battlegroundsmod.animal.db.PeixeGigante;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

// The Dragon Ball biomes' one-of-a-kind creatures (Stark 2026-11-11), each where it belongs and never many: the giant
// fish in the deep water of the Mar do Sul (one within 64 blocks, twice as likely at night), Android 8 alone in the
// Norte Gelado (one within 128, rare), Bora by Korin's tower (one, within 40 blocks of it). Called with the other
// Earth spawns every 5 s for each player in a Dragon Ball biome of the overworld.
final class DbCreatureSpawns {
    private DbCreatureSpawns() {
    }

    static void tick(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        RandomSource random = level.getRandom();
        fish(level, player, random);
        android(level, player, random);
        bora(level, player, random);
    }

    private static int[] around(ServerPlayer player, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = min + random.nextInt(max - min + 1);
        return new int[] {player.getBlockX() + (int) Math.round(Math.cos(angle) * dist),
                player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist)};
    }

    private static void fish(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(level.isDay() ? 6 : 3) != 0
                || !level.getEntitiesOfClass(PeixeGigante.class, player.getBoundingBox().inflate(64)).isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] xz = around(player, random, 20, 48);
            BlockPos column = new BlockPos(xz[0], level.getSeaLevel(), xz[1]);
            if (!level.hasChunkAt(column) || !level.getBiome(column).is(ModWorldgen.MAR_DO_SUL)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, xz[0], xz[1]);
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, xz[0], xz[1]);
            // deep water only: at least 5 blocks of it
            if (surface - floor < 5 || level.getFluidState(new BlockPos(xz[0], surface - 1, xz[1])).isEmpty()) {
                continue;
            }
            if (ModEntities.PEIXE_GIGANTE.get().spawn(level, new BlockPos(xz[0], floor + 2, xz[1]), MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }

    private static void android(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(30) != 0 || !level.getBiome(player.blockPosition()).is(ModWorldgen.NORTE_GELADO)
                || !level.getEntitiesOfClass(Androide8.class, player.getBoundingBox().inflate(128)).isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] xz = around(player, random, 32, 56);
            BlockPos pos = UniverseSpawns.ground(level, xz[0], xz[1]);
            if (pos != null && level.getBiome(pos).is(ModWorldgen.NORTE_GELADO)
                    && ModEntities.ANDROIDE_8.get().spawn(level, pos, MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }

    private static void bora(ServerLevel level, ServerPlayer player, RandomSource random) {
        WorldSites.Site karin = WorldSites.near(level, WorldSites.KARIN, player.blockPosition(), 80);
        if (karin == null) {
            return;
        }
        BlockPos tower = karin.pos();
        if (!level.getEntitiesOfClass(Bora.class, new net.minecraft.world.phys.AABB(tower).inflate(64, 128, 64)).isEmpty()) {
            return;
        }
        // not before the player's eyes: he's already there when they arrive
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 16 + random.nextInt(20);
            int x = tower.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = tower.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (player.distanceToSqr(x, player.getY(), z) < 20 * 20) {
                continue;
            }
            BlockPos pos = UniverseSpawns.ground(level, x, z);
            if (pos != null && ModEntities.BORA.get().spawn(level, pos, MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }
}
