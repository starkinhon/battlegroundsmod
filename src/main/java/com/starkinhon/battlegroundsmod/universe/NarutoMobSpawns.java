package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

// The Naruto biomes' creatures out in the world (Stark 2026-11-12: "só caprichar no spawn agora"), each biome with its
// own and in the way the preview describes them: the renegades in pairs and the ninken in packs of mixed breeds in the
// Floresta da Folha; the Sound ninja in threes, the tiger alone and the centipedes in the Floresta da Morte; the fan
// ninja, the scorpions and, in the sandstorm, the marionetista with its puppet in the Deserto do Vento; the swordsman
// over the water, the sharks in the deep water, the crabs in groups on the shore and the hunter-nin (who comes for whoever
// carries a stolen soul) in the Mar da Névoa; the Rain ninja and the flocks of paper cranes between the towers of the
// Vila da Chuva; the White Zetsu in bands at night (by day they stay underground), the crows and the old wood clones in
// the Vale do Fim; the ninja toads and the beetles under the giant flowers of Monte Myoboku. The Giant Snake, the Poison
// Salamander and the Giant Toad live only in their structures (NarutoSites), never here.
// Every 5 s for each player in a Naruto biome of the overworld: a group comes in 24-48 blocks away, in the biome it
// belongs to, while there are few of them around (fewer by day). Never in front of the player's nose.
final class NarutoMobSpawns {
    private static final double NEARBY = 64;
    private static final int CAP_DAY = 5;
    private static final int CAP_NIGHT = 7;
    private static final int MIN_DIST = 24;
    private static final int MAX_DIST = 48;

    private NarutoMobSpawns() {
    }

    // A kind that can come: its weight, how many at once, and where it stands (ground, deep water, the air, the shore).
    private record Entry(String id, int weight, int min, int max, Place place) {
    }

    private enum Place {
        GROUND,
        WATER,
        AIR,
        SHORE
    }

    private static Entry e(String id, int weight, int min, int max) {
        return new Entry(id, weight, min, max, Place.GROUND);
    }

    private static Entry e(String id, int weight, int min, int max, Place place) {
        return new Entry(id, weight, min, max, place);
    }

    // What comes in this biome now.
    private static List<Entry> table(ServerLevel level, Holder<Biome> biome, ServerPlayer player) {
        boolean day = level.isDay();
        boolean storm = level.isRaining() || level.isThundering();
        List<Entry> list = new ArrayList<>();
        if (biome.is(ModWorldgen.FLORESTA_DA_FOLHA)) {
            list.add(e("ninja_renegado", day ? 4 : 6, 2, 2));
            list.add(e("ninken", 4, 3, 4));
        } else if (biome.is(ModWorldgen.FLORESTA_DA_MORTE)) {
            list.add(e("ninja_som", 3, 3, 3));
            list.add(e("tigre", 3, 1, 1));
            list.add(e("centopeia", day ? 3 : 5, 1, 2));
        } else if (biome.is(ModWorldgen.DESERTO_DO_VENTO)) {
            list.add(e("ninja_leque", 4, 1, 2));
            list.add(e("escorpiao_areia", day ? 3 : 5, 1, 2));
            // the marionetista comes with the sandstorm
            list.add(e("marionetista", storm ? 4 : 1, 1, 1));
        } else if (biome.is(ModWorldgen.MAR_DA_NEVOA)) {
            list.add(e("espadachim_nevoa", 3, 1, 1));
            list.add(e("tubarao_agua", 3, 1, 1, Place.WATER));
            list.add(e("caranguejo_gigante", day ? 5 : 3, 3, 6, Place.SHORE));
            // the hunter-nin comes after whoever carries a stolen soul
            list.add(e("cacador_nevoa", stolenSoul(player) ? 4 : 1, 1, 1));
        } else if (biome.is(ModWorldgen.VILA_DA_CHUVA)) {
            list.add(e("ninja_chuva", 5, 1, 2));
            list.add(e("garca_papel", 2, 3, 5, Place.AIR));
        } else if (biome.is(ModWorldgen.VALE_DO_FIM)) {
            // by day the Zetsu stay underground
            list.add(e("zetsu_branco", day ? 0 : 6, 3, 6));
            list.add(e("corvo_uchiha", 3, 1, 3));
            list.add(e("clone_madeira", 2, 1, 1));
        } else if (biome.is(ModWorldgen.MONTE_MYOBOKU)) {
            list.add(e("sapo_ninja", 5, 2, 5));
            list.add(e("inseto_gigante", 4, 2, 4));
        }
        return list;
    }

    private static boolean stolenSoul(ServerPlayer player) {
        String power = player.getData(ModAttachments.POWER).power;
        return power != null && Powers.isSub(power);
    }

    static void tick(ServerLevel level, ServerPlayer player) {
        AABB around = player.getBoundingBox().inflate(NEARBY);
        int near = level.getEntitiesOfClass(NarutoMob.class, around, m -> !m.spec().exclusive && !m.isClone()).size();
        if (near >= (level.isDay() ? CAP_DAY : CAP_NIGHT)) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
            List<Entry> table = table(level, biome, player);
            if (table.isEmpty()) {
                continue;
            }
            Entry pick = pick(table, random);
            if (pick == null) {
                continue;
            }
            // the same kind not crowding one place: at most one group of it close by
            EntityType<NarutoMob> type = ModEntities.naruto(pick.id());
            if (level.getEntitiesOfClass(NarutoMob.class, new AABB(x - 24, level.getMinBuildHeight(), z - 24, x + 24, level.getMaxBuildHeight(), z + 24),
                    m -> m.getType() == type).size() >= pick.max()) {
                continue;
            }
            if (spawnGroup(level, type, pick, x, z, biome.unwrapKey().orElse(null), random)) {
                return;
            }
        }
    }

    private static Entry pick(List<Entry> table, RandomSource random) {
        int total = 0;
        for (Entry e : table) {
            total += e.weight();
        }
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (Entry e : table) {
            r -= e.weight();
            if (r < 0) {
                return e;
            }
        }
        return null;
    }

    private static boolean spawnGroup(ServerLevel level, EntityType<NarutoMob> type, Entry entry, int x, int z, ResourceKey<Biome> biome,
            RandomSource random) {
        int count = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
        int done = 0;
        for (int i = 0; i < count * 3 && done < count; i++) {
            int px = x + (i == 0 ? 0 : random.nextInt(7) - 3);
            int pz = z + (i == 0 ? 0 : random.nextInt(7) - 3);
            BlockPos pos = place(level, entry.place(), px, pz);
            if (pos == null || biome != null && !level.getBiome(pos).is(biome)) {
                continue;
            }
            if (type.spawn(level, pos, MobSpawnType.EVENT) != null) {
                done++;
            }
        }
        return done > 0;
    }

    private static BlockPos place(ServerLevel level, Place place, int x, int z) {
        switch (place) {
            case WATER -> {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                // deep water only: at least 3 blocks of it
                if (surface - floor < 3 || level.getFluidState(new BlockPos(x, surface - 1, z)).isEmpty()) {
                    return null;
                }
                return new BlockPos(x, floor + 1, z);
            }
            case AIR -> {
                BlockPos ground = UniverseSpawns.ground(level, x, z);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos up = new BlockPos(x, Math.max(top, ground == null ? top : ground.getY()) + 7, z);
                return level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir() ? up : null;
            }
            case SHORE -> {
                BlockPos ground = UniverseSpawns.ground(level, x, z);
                if (ground == null) {
                    return null;
                }
                // on the beach or the rocks: water within 4 blocks
                for (BlockPos p : BlockPos.betweenClosed(ground.offset(-4, -2, -4), ground.offset(4, 0, 4))) {
                    if (!level.getFluidState(p).isEmpty()) {
                        return ground;
                    }
                }
                return null;
            }
            default -> {
                return UniverseSpawns.ground(level, x, z);
            }
        }
    }
}
