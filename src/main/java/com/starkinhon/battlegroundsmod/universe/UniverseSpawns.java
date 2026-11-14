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
    private static final int EARTH_CAP_DAY = 6;
    private static final int EARTH_CAP_NIGHT = 8;
    private static final int EARTH_MIN_DIST = 24;
    private static final int EARTH_MAX_DIST = 52;

    private UniverseSpawns() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onServerTick);
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onFinalizeAny);
        NeoForge.EVENT_BUS.addListener(UniverseSpawns::onSleepFinished);
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
            String id = UniverseBiomes.universeAt(level, player.blockPosition());
            ChallengeSpec.Universe universe = id == null ? null : ChallengeSpec.get().universe(id);
            if (universe == null || universe.mobs() == null || universe.mobs().isEmpty()) {
                continue;
            }
            if (NarutoWorld.UNIVERSE.equals(id) && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                // The Naruto biomes have their own creatures, each in its biome (NarutoMobSpawns).
                NarutoMobSpawns.tick(level, player);
                continue;
            }
            if (JujutsuWorld.UNIVERSE.equals(id) && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                // The Jujutsu biomes have their own curses, each in its biome (JjMobSpawns), in place of the old common
                // enemies.
                JjMobSpawns.tick(level, player);
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
                    spawnNamekians(level, player);
                } else {
                    spawnEarth(level, player);
                    DbCreatureSpawns.tick(level, player);
                    if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                        skyLife(level, player);
                    }
                }
                continue;
            }
            AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
            List<String> pool = mobs;
            int nearby = level.getEntitiesOfClass(SoulBoss.class, around, mob -> pool.contains(mob.bossId())).size();
            // the Naruto dimension's ninjas are Naruto creatures now, not SoulBoss
            nearby += level.getEntitiesOfClass(com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob.class, around,
                    mob -> pool.contains(mob.id())).size();
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
            "raptor", "tiranossauro", "ladrao_urso", "dino_chifrudo", "tigre_sabre", "androide_gero", "soldado_freeza");

    // In the normal overworld (Stark 2026-11-11: o universo virou biomas): by the Dragon Ball biome of the spot. The
    // pirates by the sea, the Saibamen and bandits in the red desert, the Red Ribbon and the yeti in the peaks, the
    // dinosaurs and Saibamen in the green lands. Null outside the Dragon Ball biomes.
    static List<String> earthMobs(ServerLevel level, int x, int z) {
        if (level.getChunkSource().getGenerator() instanceof com.starkinhon.battlegroundsmod.world.MapChunkGenerator) {
            return earthMobs(x, z);
        }
        var biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
        if (!DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeOf(biome))) {
            return null;
        }
        // around the wrecked Saiyan ship (Stark 2026-11-12, mobs-db: "em volta das naves"): Freeza's soldiers
        if (com.starkinhon.battlegroundsmod.world.WorldSites.near(level, com.starkinhon.battlegroundsmod.world.WorldSites.WRECK,
                new BlockPos(x, 0, z), 60) != null) {
            return List.of("soldado_freeza");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.DESERTO_VERMELHO)) {
            return List.of("saibaman", "saibaman", "bandido_deserto", "raptor", "dino_chifrudo");
        }
        // Korin's tower (Stark 2026-11-11): Bora guards it (DbCreatureSpawns), no dinosaurs around; the Red Ribbon still
        // comes after the dragon ball there
        if (com.starkinhon.battlegroundsmod.world.WorldSites.near(level, com.starkinhon.battlegroundsmod.world.WorldSites.KARIN,
                new BlockPos(x, 0, z), 90) != null) {
            return List.of("soldado_red_ribbon");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.MONTANHAS_PAOZU)) {
            // Goku's mountains: the dinosaurs he grew up hunting, and the Bear Thief: rare by day, he hunts at night
            // (then fewer dinosaurs; Stark 2026-11-11)
            // and the sabertooth by the rivers (mobs-db 2026-11-12)
            return level.isDay() ? List.of("raptor", "raptor", "tiranossauro", "tiranossauro", "dino_chifrudo", "dino_chifrudo", "tigre_sabre",
                    "ladrao_urso") : List.of("ladrao_urso", "ladrao_urso", "ladrao_urso", "tigre_sabre", "raptor", "tiranossauro");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.NORTE_GELADO)) {
            // the frozen north: the yeti and the Red Ribbon (its Muscle Tower stood in the snow)
            // only the yeti (Stark 2026-11-11)
            return List.of("yeti");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.MAR_DO_SUL)) {
            // the islands and the ships' decks: the pirates' sea
            return List.of("pirata");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.TERRA_SAGRADA)) {
            return List.of("raptor", "raptor", "dino_chifrudo", "tiranossauro", "soldado_red_ribbon");
        }
        if (biome.is(com.starkinhon.battlegroundsmod.world.ModWorldgen.PICOS_DO_DRAGAO)) {
            // the yeti only in the north now (mobs-db: "nas Montanhas do Norte"); Gero's androids wander the peaks
            return List.of("soldado_red_ribbon", "soldado_red_ribbon", "raptor", "dino_chifrudo", "androide_gero");
        }
        return List.of("raptor", "raptor", "tiranossauro", "dino_chifrudo", "saibaman", "soldado_red_ribbon", "soldado_red_ribbon", "androide_gero");
    }

    static List<String> earthMobs(int x, int z) {
        double rx = x - com.starkinhon.battlegroundsmod.world.DragonBallPlaces.RED_RIBBON_X;
        double rz = z - com.starkinhon.battlegroundsmod.world.DragonBallPlaces.RED_RIBBON_Z;
        if (rx * rx + rz * rz < 90 * 90) {
            return List.of("soldado_red_ribbon");
        }
        com.starkinhon.battlegroundsmod.world.WorldMap map = com.starkinhon.battlegroundsmod.world.WorldMap.get(
                com.starkinhon.battlegroundsmod.world.DragonBallPlaces.MAP);
        int kind = map.kind(x, z);
        if (kind == com.starkinhon.battlegroundsmod.world.WorldMap.SAND || map.landSize(x, z) < ISLAND) {
            return List.of("pirata");
        }
        // Each enemy only in its own region (Stark's test 2026-11-10: Saibamen are the desert's, not Capsule Corp's)
        return switch (kind) {
            case com.starkinhon.battlegroundsmod.world.WorldMap.SNOW -> List.of("soldado_red_ribbon", "soldado_red_ribbon", "yeti");
            case com.starkinhon.battlegroundsmod.world.WorldMap.DESERT, com.starkinhon.battlegroundsmod.world.WorldMap.CANYON,
                    com.starkinhon.battlegroundsmod.world.WorldMap.REDROCK, com.starkinhon.battlegroundsmod.world.WorldMap.CRATER,
                    com.starkinhon.battlegroundsmod.world.WorldMap.LAVA -> List.of("saibaman", "saibaman", "bandido_deserto");
            default -> List.of("raptor", "raptor", "tiranossauro", "soldado_red_ribbon");
        };
    }

    // Land smaller than this (in columns) is an island: everything but the big continent.
    private static final int ISLAND = 100_000;

    private static void spawnEarth(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        // Much calmer (Stark's test 2026-11-10: five Saibamen in his first minute, hordes at night): a try every 15 s by
        // day and 10 s at night, at most 3 (4 at night) around, farther away, and never by the houses or the arrival.
        // Stark 2026-11-12 ("o spawn tá muito baixinho"): a try every 5 s at night and 2 of 3 by day, up to 6 (8 at
        // night) around, and closer so they are seen.
        boolean night = !level.isDay();
        if (!night && random.nextInt(3) == 0) {
            return;
        }
        int cap = night ? EARTH_CAP_NIGHT : EARTH_CAP_DAY;
        AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
        int nearby = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, around, mob -> EARTH_MOBS.contains(
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath())).size();
        if (nearby >= cap) {
            return;
        }
        // A few spots per try: outside the desert most single spots failed (water by the islands, trees, the houses) and
        // almost nothing came (Stark's test 2026-11-10, the desert was right).
        for (int attempt = 0; attempt < 5; attempt++) {
            if (spawnEarthAt(level, player, random, cap - nearby)) {
                return;
            }
        }
    }

    private static boolean spawnEarthAt(ServerLevel level, ServerPlayer player, RandomSource random, int room) {
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = EARTH_MIN_DIST + random.nextInt(EARTH_MAX_DIST - EARTH_MIN_DIST + 1);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        boolean drawn = level.getChunkSource().getGenerator() instanceof com.starkinhon.battlegroundsmod.world.MapChunkGenerator;
        if (drawn && com.starkinhon.battlegroundsmod.world.DragonBallPlaces.safe(x, z)) {
            return false;
        }
        List<String> mobs = earthMobs(level, x, z);
        if (mobs == null) {
            return false;
        }
        if (!drawn && nearWater(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z))) {
            // the shore of a Dragon Ball biome: pirates
            mobs = List.of("pirata");
        }
        String id = mobs.get(random.nextInt(mobs.size()));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, id));
        if (type == EntityType.PIG) {
            return false;
        }
        // the group sizes of the preview (mobs-db 2026-11-12): the big ones and the hunters alone, the raptors, the horned
        // dinosaurs, the patrols and the pirates 2 to 3, the Saibamen 2 to 4, the bandits 1 to 2, the androids 1 to 3,
        // Freeza's squads 3 to 4
        int count = switch (id) {
            case "tiranossauro", "yeti", "ladrao_urso", "tigre_sabre" -> 1;
            case "raptor", "dino_chifrudo", "soldado_red_ribbon", "pirata" -> 2 + random.nextInt(2);
            case "saibaman" -> 2 + random.nextInt(3);
            case "bandido_deserto" -> 1 + random.nextInt(2);
            case "soldado_freeza" -> 3 + random.nextInt(2);
            default -> 1 + random.nextInt(3);
        };
        count = Math.min(count, room);
        // the pirates live by the sea; the rest keep off it
        boolean shore = "pirata".equals(id);
        boolean any = false;
        for (int i = 0; i < count; i++) {
            BlockPos pos = ground(level, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2);
            if (pos != null && (shore || !nearWater(level, pos))) {
                any |= type.spawn(level, pos, MobSpawnType.EVENT) != null;
            }
        }
        return any;
    }

    // Pterodactyls circling high over the Dragon Ball biomes (Stark 2026-11-11), two to four in sight, in pairs or
    // threes (mobs-db 2026-11-12).
    private static void skyLife(ServerLevel level, ServerPlayer player) {
        int near = level.getEntitiesOfClass(com.starkinhon.battlegroundsmod.animal.dbmob.DbMob.class,
                player.getBoundingBox().inflate(96, 128, 96), m -> "pterodactilo".equals(m.id())).size();
        RandomSource random = level.getRandom();
        if (near >= 2 + random.nextInt(3)) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = 30 + random.nextInt(35);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        if (!level.hasChunkAt(new BlockPos(x, 0, z)) || !DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeAt(level, new BlockPos(x, 64, z)))) {
            return;
        }
        // none over the Norte Gelado (Stark 2026-11-11: only the yeti lives there)
        if (level.getBiome(new BlockPos(x, 64, z)).is(com.starkinhon.battlegroundsmod.world.ModWorldgen.NORTE_GELADO)
                || level.getBiome(player.blockPosition()).is(com.starkinhon.battlegroundsmod.world.ModWorldgen.NORTE_GELADO)) {
            return;
        }
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), player.getBlockY()) + 24 + random.nextInt(18);
        y = Math.min(y, level.getMaxBuildHeight() - 8);
        int n = Math.min(2 + random.nextInt(2), 4 - near);
        for (int i = 0; i < n; i++) {
            com.starkinhon.battlegroundsmod.ModEntities.PTERODACTILO.get().spawn(level, new BlockPos(x + random.nextInt(7) - 3, y + random.nextInt(4),
                    z + random.nextInt(7) - 3), MobSpawnType.EVENT);
        }
    }

    // Freeza's soldiers on Namek (its own dimension now): in groups on dry ground around the player, never too many.
    private static void spawnNamek(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(
                com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID, "soldado_freeza"));
        AABB around = player.getBoundingBox().inflate(NEARBY_RADIUS);
        int nearby = level.getEntitiesOfClass(com.starkinhon.battlegroundsmod.animal.dbmob.DbMob.class, around,
                mob -> "soldado_freeza".equals(mob.id())).size();
        // twice as many at night (Stark 2026-11-11)
        int cap = level.getServer().overworld().isDay() ? NEARBY_CAP : NEARBY_CAP * 2;
        if (type == EntityType.PIG || nearby >= cap) {
            return;
        }
        // never past the cap (Stark's test 2026-11-10: a group of 3 on top of 5 made 8), and away from Namek's green
        // sea, where they ended up swimming. A few spots per try: Namek is islands, most single spots were water and
        // nothing came (Stark 2026-11-11: no soldiers on Namek)
        // in squads of 3 to 4 (mobs-db 2026-11-12)
        groupOn(level, player, type, Math.min(3 + level.getRandom().nextInt(2), cap - nearby));
    }

    // The Namekians (Stark 2026-11-11): by day groups of 2 to 4 walk Namek's islands; at night they stay home.
    private static void spawnNamekians(ServerLevel level, ServerPlayer player) {
        if (!level.getServer().overworld().isDay() || level.getRandom().nextInt(3) != 0) {
            return;
        }
        int nearby = level.getEntitiesOfClass(com.starkinhon.battlegroundsmod.animal.db.Namekiano.class,
                player.getBoundingBox().inflate(NEARBY_RADIUS)).size();
        if (nearby >= 8) {
            return;
        }
        groupOn(level, player, com.starkinhon.battlegroundsmod.ModEntities.NAMEKIANO.get(), Math.min(2 + level.getRandom().nextInt(3), 8 - nearby));
    }

    // A group of this type on dry ground 24-48 blocks from the player, trying a few spots; true when any came.
    static boolean groupOn(ServerLevel level, ServerPlayer player, EntityType<?> type, int count) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 6 && count > 0; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos first = ground(level, x, z);
            if (first == null || nearWater(level, first, 2)) {
                continue;
            }
            boolean any = false;
            for (int i = 0; i < count; i++) {
                BlockPos pos = i == 0 ? first : ground(level, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2);
                if (pos != null && !nearWater(level, pos, 2)) {
                    any |= type.spawn(level, pos, MobSpawnType.EVENT) != null;
                }
            }
            if (any) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        return nearWater(level, pos, 4);
    }

    static boolean nearWater(ServerLevel level, BlockPos pos, int r) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                for (int dy = -2; dy <= 0; dy++) {
                    if (!level.getFluidState(p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // A dry spot on top of solid ground in a loaded chunk, or null. The spot and the one above only need to be free to
    // walk through (Stark 2026-11-12: no new mob anywhere): grass, ferns, flowers and a thin snow layer are fine. Asking
    // for pure air there failed almost every green or snowy column, so only the desert ever got its mobs.
    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos below = pos.below();
        net.minecraft.world.level.block.state.BlockState under = level.getBlockState(below);
        // a snow layer of any depth is ground too: the mob stands on top of it
        boolean snow = under.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock;
        if (y <= level.getMinBuildHeight() || !under.getFluidState().isEmpty()
                || !(snow || under.isFaceSturdy(level, below, net.minecraft.core.Direction.UP))
                || !walkable(level, pos) || !walkable(level, pos.above())) {
            return null;
        }
        return pos;
    }

    private static boolean walkable(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    // A bed in a universe world: vanilla sets the morning on the sleeping level, but the universes read the
    // overworld's clock and ignore that (Stark's test 2026-11-10: slept and it stayed night). Move the overworld's.
    private static void onSleepFinished(net.neoforged.neoforge.event.level.SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || Universes.universeOf(level.dimension()) == null) {
            return;
        }
        ServerLevel overworld = level.getServer().overworld();
        if (overworld != level) {
            overworld.setDayTime(event.getNewTime());
        }
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Monster)
                || event.getEntity() instanceof SoulBoss
                || event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        String universe = UniverseBiomes.universeAt(level, event.getEntity().blockPosition());
        if (universe != null && Progression.on(level.getServer())
                && (DragonBallWorld.UNIVERSE.equals(universe) || level.getRandom().nextFloat() >= VANILLA_KEPT)) {
            event.setSpawnCancelled(true);
        }
    }

    // In the Dragon Ball worlds nothing comes from the vanilla spawner (Stark's test 2026-11-10: zombies, spiders and
    // skeletons by the dozen at night, inside Capsule Corp, plus the overworld Fighters and its own enemies spawned
    // the vanilla way on top of these): only this class's spawns, and the challenge characters (rare, by their rules).
    private static void onFinalizeAny(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!DragonBallWorld.UNIVERSE.equals(UniverseBiomes.universeAt(level, event.getEntity().blockPosition())) || !Progression.on(level.getServer())) {
            return;
        }
        if (event.getEntity() instanceof SoulBoss boss && ChallengeSpec.get().universeOf(boss.bossId()) != null) {
            return;
        }
        event.setSpawnCancelled(true);
    }
}
