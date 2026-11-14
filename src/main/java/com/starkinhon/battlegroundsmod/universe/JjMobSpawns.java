package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.jjmob.JjMob;
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

// The Jujutsu biomes' curses out in the world (Stark 2026-11-14, preview tools/mobs/jjmobs approved: "porta"), each
// biome with its own, replacing the old Maldição Menor, Maldição de Grau 2 and Feiticeiro Renegado there: the flies in
// swarms, the big heads and the transfigured in Shibuya's streets, and once in a while the cursed train; the worms, the
// trees of eyes and the womb in the Floresta Negra, and at night the Rastejante; the straw corpses, the kitsune and the
// bells in the Templos; the scarecrows, the Okiku and, at night, the Kuchisake in the Aldeia; the fish in schools in the
// air, the crabs on the shore, the embryos and the drowned in the Mar de Dagon; the sprouts, the boars and the
// guardian in Hanami; the burning insects, the little volcanoes and the golem in the Terras do Jogo; the paper
// lanterns (more at night), the Mechamaru and the oni in Kyoto; the larvae, the players of the Jogo do Abate (with
// their points) and the loose curse in the Colônia, where every player also gets a Kogane.
// Every 5 s for each player in a Jujutsu biome of the overworld: a group comes in 24-48 blocks away, in the biome it
// belongs to, while there are few of them around (fewer by day). The big ones (the train, the Rastejante, the womb)
// at most one near.
final class JjMobSpawns {
    private static final double NEARBY = 64;
    private static final int CAP_DAY = 5;
    private static final int CAP_NIGHT = 7;
    private static final int MIN_DIST = 24;
    private static final int MAX_DIST = 48;

    private JjMobSpawns() {
    }

    private record Entry(String id, int weight, int min, int max, Place place) {
    }

    private enum Place {
        GROUND,
        AIR,
        SHORE
    }

    private static Entry e(String id, int weight, int min, int max) {
        return new Entry("jj_" + id, weight, min, max, Place.GROUND);
    }

    private static Entry e(String id, int weight, int min, int max, Place place) {
        return new Entry("jj_" + id, weight, min, max, place);
    }

    private static List<Entry> table(ServerLevel level, Holder<Biome> biome) {
        boolean day = level.isDay();
        List<Entry> list = new ArrayList<>();
        if (biome.is(ModWorldgen.SHIBUYA)) {
            list.add(e("mosca", 5, 2, 4, Place.AIR));
            list.add(e("cabecao", 2, 1, 1));
            list.add(e("transfigurado", day ? 3 : 5, 2, 3));
            list.add(e("metro", 1, 1, 1));
        } else if (biome.is(ModWorldgen.FLORESTA_NEGRA)) {
            list.add(e("verme", 4, 2, 3));
            list.add(e("olhos", 3, 1, 2));
            list.add(e("utero", 1, 1, 1));
            list.add(e("rastejante", day ? 0 : 1, 1, 1));
        } else if (biome.is(ModWorldgen.TEMPLOS_ANTIGOS)) {
            list.add(e("boneco", 3, 1, 2));
            list.add(e("kitsune", 4, 1, 3));
            list.add(e("sino", 2, 1, 1));
        } else if (biome.is(ModWorldgen.ALDEIA_AMALDICOADA)) {
            list.add(e("espantalho", 4, 1, 2));
            list.add(e("poco", 2, 1, 1));
            list.add(e("kuchisake", day ? 1 : 3, 1, 1));
        } else if (biome.is(ModWorldgen.MAR_DE_DAGON)) {
            list.add(e("peixe", 4, 3, 5, Place.AIR));
            list.add(e("caranguejo", 4, 1, 3, Place.SHORE));
            list.add(e("embriao", 2, 1, 1));
            list.add(e("afogado", 3, 1, 1, Place.SHORE));
        } else if (biome.is(ModWorldgen.FLORESTA_DE_HANAMI)) {
            list.add(e("broto", 4, 2, 4));
            list.add(e("javali", 3, 1, 2));
            list.add(e("guardiao", 1, 1, 1));
        } else if (biome.is(ModWorldgen.TERRAS_DO_JOGO)) {
            list.add(e("inseto", 4, 2, 4, Place.AIR));
            list.add(e("vulcaozinho", 3, 1, 2));
            list.add(e("golem", 1, 1, 1));
        } else if (biome.is(ModWorldgen.OUTONO_DE_KYOTO)) {
            list.add(e("lanterna", day ? 1 : 4, 1, 3, Place.AIR));
            list.add(e("mechamaru", 3, 1, 1));
            list.add(e("oni", 2, 1, 1));
        } else if (biome.is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE)) {
            list.add(e("larva", 4, 2, 3));
            list.add(e("abate", 5, 1, 2));
            list.add(e("solta", 1, 1, 1));
        }
        return list;
    }

    static void tick(ServerLevel level, ServerPlayer player) {
        if (level.getBiome(player.blockPosition()).is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE)) {
            JjMob.kogane(level, player);
        }
        AABB around = player.getBoundingBox().inflate(NEARBY);
        int near = level.getEntitiesOfClass(JjMob.class, around, m -> !m.spec().exclusive && !m.summoned() && !m.isKogane()).size();
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
            List<Entry> table = table(level, biome);
            if (table.isEmpty()) {
                continue;
            }
            Entry pick = pick(table, random);
            var supplier = pick == null ? null : ModEntities.JJ_MOBS.get(pick.id());
            if (supplier == null) {
                continue;
            }
            EntityType<JjMob> type = supplier.get();
            boolean big = com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec.get(pick.id()).exclusive;
            double r = big ? 96 : 24;
            // the same kind not crowding one place; the big ones alone in a wide area
            if (level.getEntitiesOfClass(JjMob.class, new AABB(x - r, level.getMinBuildHeight(), z - r, x + r, level.getMaxBuildHeight(), z + r),
                    m -> m.getType() == type).size() >= (big ? 1 : pick.max())) {
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

    private static boolean spawnGroup(ServerLevel level, EntityType<JjMob> type, Entry entry, int x, int z, ResourceKey<Biome> biome,
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
        BlockPos ground = UniverseSpawns.ground(level, x, z);
        if (ground == null) {
            return null;
        }
        switch (place) {
            case AIR -> {
                // a little over the ground (the flies, the fish of Dagon, the lanterns float low)
                BlockPos up = ground.above(2);
                return level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir() ? up : null;
            }
            case SHORE -> {
                for (BlockPos p : BlockPos.betweenClosed(ground.offset(-4, -2, -4), ground.offset(4, 0, 4))) {
                    if (!level.getFluidState(p).isEmpty()) {
                        return ground;
                    }
                }
                return null;
            }
            default -> {
                return ground;
            }
        }
    }
}
