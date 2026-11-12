package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.db.Androide8;
import com.starkinhon.battlegroundsmod.animal.db.Bora;
import com.starkinhon.battlegroundsmod.animal.dbmob.DbMob;
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
        dragon(level, player, random);
        taoPaiPai(level, player, random);
        majins(level, player, random);
    }

    // how many of this Dragon Ball creature are within r blocks of the player
    private static int count(ServerLevel level, ServerPlayer player, String id, double r) {
        return level.getEntitiesOfClass(DbMob.class, player.getBoundingBox().inflate(r, 128, r), m -> id.equals(m.id())).size();
    }

    private static int[] around(ServerPlayer player, RandomSource random, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = min + random.nextInt(max - min + 1);
        return new int[] {player.getBlockX() + (int) Math.round(Math.cos(angle) * dist),
                player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist)};
    }

    private static void fish(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(level.isDay() ? 3 : 2) != 0 || count(level, player, "peixe_gigante", 64) > 0) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] xz = around(player, random, 20, 48);
            BlockPos column = new BlockPos(xz[0], level.getSeaLevel(), xz[1]);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            // the Mar do Sul, and the rivers and lakes of Paozu (mobs-db 2026-11-12), shallower there
            boolean sea = level.getBiome(column).is(ModWorldgen.MAR_DO_SUL);
            if (!sea && !level.getBiome(column).is(ModWorldgen.MONTANHAS_PAOZU)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, xz[0], xz[1]);
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, xz[0], xz[1]);
            // deep water only: at least 5 blocks of it in the sea, 3 in a river
            if (surface - floor < (sea ? 5 : 3) || level.getFluidState(new BlockPos(xz[0], surface - 1, xz[1])).isEmpty()) {
                continue;
            }
            if (ModEntities.PEIXE_GIGANTE.get().spawn(level, new BlockPos(xz[0], floor + 1, xz[1]), MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }

    private static void android(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(10) != 0 || !level.getBiome(player.blockPosition()).is(ModWorldgen.NORTE_GELADO)
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

    // The dragon (mobs-db 2026-11-12: "neutro e raro, um por vez, pousado em pico de rocha, oásis ou perto de água"): one
    // within 160 blocks at most, on a high spot or by the water of any Dragon Ball biome but the frozen north. A tamed
    // one counts too, so a rider does not meet a new one at every turn.
    private static void dragon(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(24) != 0 || count(level, player, "dragao", 160) > 0) {
            return;
        }
        for (int attempt = 0; attempt < 6; attempt++) {
            int[] xz = around(player, random, 40, 72);
            BlockPos pos = UniverseSpawns.ground(level, xz[0], xz[1]);
            if (pos == null || level.getBiome(pos).is(ModWorldgen.NORTE_GELADO) || level.getBiome(pos).is(ModWorldgen.NAMEK)
                    || !DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeAt(level, pos))) {
                continue;
            }
            // a peak (higher than the ground around it) or the water's edge
            int around = 0;
            for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                around += level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz[0] + d[0], xz[1] + d[1]);
            }
            boolean peak = pos.getY() >= around / 4 + 5;
            if (!peak && !UniverseSpawns.nearWater(level, pos, 4)) {
                continue;
            }
            if (ModEntities.db("dragao").spawn(level, pos, MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }

    // Tao Pai Pai (mobs-db: "vaga sozinho pelas Terras do Dragão e perto da torre do Karin", rare): one within 200
    // blocks, never by the player's side.
    private static void taoPaiPai(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (random.nextInt(40) != 0 || count(level, player, "tao_pai_pai", 200) > 0) {
            return;
        }
        var biome = level.getBiome(player.blockPosition());
        if (!biome.is(ModWorldgen.TERRAS_DO_DRAGAO) && !biome.is(ModWorldgen.TERRA_SAGRADA)
                && WorldSites.near(level, WorldSites.KARIN, player.blockPosition(), 120) == null) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] xz = around(player, random, 36, 60);
            BlockPos pos = UniverseSpawns.ground(level, xz[0], xz[1]);
            if (pos != null && !UniverseSpawns.nearWater(level, pos, 2) && ModEntities.db("tao_pai_pai").spawn(level, pos, MobSpawnType.EVENT) != null) {
                return;
            }
        }
    }

    // Spopovich and Yamu (mobs-db: "anda com o Yamu pelo Deserto do Diabo, de noite"): together, at night, in the red
    // desert, one pair within 160 blocks. Yamu keeps behind him on his own (DbMobMoves idle).
    private static void majins(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (level.isDay() || random.nextInt(14) != 0 || !level.getBiome(player.blockPosition()).is(ModWorldgen.DESERTO_VERMELHO)
                || count(level, player, "spopovich", 160) > 0) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] xz = around(player, random, 32, 56);
            BlockPos pos = UniverseSpawns.ground(level, xz[0], xz[1]);
            if (pos == null || !level.getBiome(pos).is(ModWorldgen.DESERTO_VERMELHO)) {
                continue;
            }
            if (ModEntities.db("spopovich").spawn(level, pos, MobSpawnType.EVENT) != null) {
                BlockPos back = UniverseSpawns.ground(level, pos.getX() + 2, pos.getZ() + 2);
                ModEntities.db("yamu").spawn(level, back == null ? pos : back, MobSpawnType.EVENT);
                return;
            }
        }
    }
}
