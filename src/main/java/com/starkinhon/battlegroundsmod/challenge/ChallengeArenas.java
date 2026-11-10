package com.starkinhon.battlegroundsmod.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The challenge arenas (plan "Souls: Multiverso e Progressão", Stark 2026-11-10): every character of a universe's
// Livro dos Desafios has its own arena. The player goes to any of them whenever they want (from the book), a 3-2-1
// countdown brings the character in, invisible walls keep both inside, and beating it:
// - stamps the card DERROTADO in the book;
// - lets that character spawn in the normal world (before that, the natural spawns of challenge characters are
//   blocked near the player);
// - brings the player back home after a few seconds (time to pick up the soul).
// All arenas live in one void dimension (battlegroundsmod:arenas), one slot each, built from code in the universe's
// blocks (padrao.desafios.json) and rebuilt clean before every fight. Dying or leaving ends the fight.
// With /gamerule soulsProgressao false (arena mode) the world spawns stay free.
public final class ChallengeArenas {
    public static final ResourceKey<Level> ARENAS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "arenas"));
    private static final int SPACING = 400;
    private static final int FLOOR_Y = 64;
    private static final int COUNTDOWN = 80;
    private static final int RETURN_DELAY = 240;

    private enum State { COUNTDOWN, FIGHT, DONE }

    private static final class Session {
        String id;
        BlockPos center;
        ResourceKey<Level> backLevel;
        Vec3 backPos;
        float backYaw;
        long fightAt;
        long doneAt;
        UUID boss;
        State state = State.COUNTDOWN;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ChallengeArenas() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onServerTick);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onDeath);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onLogin);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onLogout);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onFinalizeSpawn);
    }

    public static List<String> wins(Player player) {
        return player.getData(ModAttachments.ARENA_WINS);
    }

    public static void syncWins(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncArenaWins(List.copyOf(wins(player))));
    }

    private static Component name(String id) {
        try {
            Power power = Powers.get(BossSpec.get(id).poder());
            if (power != null) {
                return power.name();
            }
        } catch (RuntimeException ignored) {
        }
        return Component.literal(id);
    }

    // From the book: go to that character's arena.
    public static void start(ServerPlayer player, String id) {
        ChallengeSpec spec = ChallengeSpec.get();
        int slot = spec.slot(id);
        ChallengeSpec.Universe universe = spec.universeOf(id);
        ServerLevel arena = player.server.getLevel(ARENAS);
        if (slot < 0 || universe == null) {
            return;
        }
        if (arena == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_missing"), false);
            return;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_busy"), true);
            return;
        }
        if (entityType(id) == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_no_entity", id), false);
            return;
        }
        for (Session other : SESSIONS.values()) {
            if (other.id.equals(id)) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_taken"), true);
                return;
            }
        }
        Session session = new Session();
        session.id = id;
        session.center = new BlockPos(slot * SPACING, FLOOR_Y, 0);
        boolean fromArena = player.level().dimension().equals(ARENAS);
        session.backLevel = fromArena ? Level.OVERWORLD : player.level().dimension();
        session.backPos = fromArena ? Vec3.atBottomCenterOf(player.server.overworld().getSharedSpawnPos()) : player.position();
        session.backYaw = player.getYRot();
        build(arena, session.center, spec, universe);
        session.fightAt = arena.getGameTime() + COUNTDOWN;
        SESSIONS.put(player.getUUID(), session);
        player.teleportTo(arena, session.center.getX() + 0.5, FLOOR_Y + 1, session.center.getZ() - spec.raio() / 2.0 + 0.5, 0, 0);
        title(player, Component.translatable("message.battlegroundsmod.arena_title", name(id))
                .withStyle(s -> s.withColor(universe.color()).withBold(true)), Component.translatable("message.battlegroundsmod.arena_ready"));
        Fx.sound(arena, Vec3.atCenterOf(session.center), SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.4f);
    }

    // From the book's "back" button or the end of a fight: home again.
    public static void leave(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            if (player.level().dimension().equals(ARENAS)) {
                ServerLevel overworld = player.server.overworld();
                Vec3 spawn = Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
                player.teleportTo(overworld, spawn.x, spawn.y, spawn.z, player.getYRot(), 0);
            }
            return;
        }
        if (session.state == State.FIGHT) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_no_escape"), true);
            SESSIONS.put(player.getUUID(), session);
            return;
        }
        sendBack(player, session);
    }

    private static void sendBack(ServerPlayer player, Session session) {
        removeBoss(player.server, session);
        ServerLevel level = player.server.getLevel(session.backLevel);
        if (level == null) {
            level = player.server.overworld();
        }
        player.teleportTo(level, session.backPos.x, session.backPos.y, session.backPos.z, session.backYaw, 0);
    }

    private static void removeBoss(MinecraftServer server, Session session) {
        ServerLevel arena = server.getLevel(ARENAS);
        if (arena != null && session.boss != null) {
            Entity boss = arena.getEntity(session.boss);
            if (boss != null && boss.isAlive()) {
                boss.discard();
            }
        }
    }

    private static EntityType<?> entityType(String id) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(key) ? BuiltInRegistries.ENTITY_TYPE.get(key) : null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel arena = server.getLevel(ARENAS);
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || arena == null || player.level() != arena) {
                SESSIONS.remove(entry.getKey());
                removeBoss(server, session);
                continue;
            }
            long now = arena.getGameTime();
            switch (session.state) {
                case COUNTDOWN -> {
                    long left = session.fightAt - now;
                    if (left == 60 || left == 40 || left == 20) {
                        title(player, Component.literal(String.valueOf(left / 20)).withStyle(s -> s.withColor(0xF2C94C).withBold(true)), Component.empty());
                        Fx.sound(arena, player.position(), SoundEvents.NOTE_BLOCK_HAT.value(), 1f, 0.8f);
                    }
                    if (left <= 0) {
                        spawnBoss(player, arena, session);
                    }
                }
                case FIGHT -> {
                    Entity boss = session.boss == null ? null : arena.getEntity(session.boss);
                    if (boss == null || !boss.isAlive()) {
                        win(player, session, now);
                    } else if (now % 20 == 0 && boss instanceof Mob mob && mob.getTarget() != player) {
                        mob.setTarget(player);
                    }
                }
                case DONE -> {
                    if (now >= session.doneAt) {
                        SESSIONS.remove(entry.getKey());
                        sendBack(player, session);
                    }
                }
            }
        }
    }

    private static void spawnBoss(ServerPlayer player, ServerLevel arena, Session session) {
        EntityType<?> type = entityType(session.id);
        int radius = ChallengeSpec.get().raio();
        Entity boss = type == null ? null
                : type.spawn(arena, session.center.offset(0, 1, radius / 2), MobSpawnType.EVENT);
        if (boss == null) {
            SESSIONS.remove(player.getUUID());
            sendBack(player, session);
            return;
        }
        if (boss instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.lookAt(player, 360, 360);
            mob.setTarget(player);
        }
        session.boss = boss.getUUID();
        session.state = State.FIGHT;
        title(player, Component.translatable("message.battlegroundsmod.arena_fight").withStyle(s -> s.withColor(0xFF4A3A).withBold(true)), Component.empty());
        Fx.sound(arena, player.position(), SoundEvents.RAID_HORN.value(), 0.7f, 1.2f);
    }

    private static void win(ServerPlayer player, Session session, long now) {
        session.state = State.DONE;
        session.doneAt = now + RETURN_DELAY;
        List<String> wins = new ArrayList<>(wins(player));
        boolean first = !wins.contains(session.id);
        if (first) {
            wins.add(session.id);
            player.setData(ModAttachments.ARENA_WINS, List.copyOf(wins));
            syncWins(player);
            giveItems(player, ChallengeSpec.get().challenge(session.id));
        }
        title(player, Component.translatable("message.battlegroundsmod.arena_won").withStyle(s -> s.withColor(0xD8352A).withBold(true)), name(session.id).copy());
        player.displayClientMessage(Component.translatable(first ? "message.battlegroundsmod.arena_unlocked" : "message.battlegroundsmod.arena_again",
                name(session.id), RETURN_DELAY / 20), false);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // The challenge's items, the first time it is beaten.
    private static void giveItems(ServerPlayer player, ChallengeSpec.Challenge challenge) {
        if (challenge == null || challenge.itens() == null) {
            return;
        }
        for (String entry : challenge.itens()) {
            String[] parts = entry.trim().split("\\s+");
            ResourceLocation key = ResourceLocation.tryParse(parts[0]);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                BattlegroundsMod.LOGGER.warn("Desafio {}: item desconhecido {}", challenge.id(), entry);
                continue;
            }
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key), count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    // Dying in the arena ends the fight; the player respawns at home as usual.
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                removeBoss(player.server, session);
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_lost", name(session.id)), false);
            }
        }
    }

    // A player who logs in inside the arenas without a fight (the server stopped mid-fight) goes back to the world spawn.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncWins(player);
            if (player.level().dimension().equals(ARENAS) && !SESSIONS.containsKey(player.getUUID())) {
                leave(player);
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                sendBack(player, session);
            }
        }
    }

    // Challenge characters only spawn naturally near a player who already beat them in their arena.
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (!(event.getEntity() instanceof SoulBoss boss)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!level.getGameRules().getBoolean(Progression.RULE) || ChallengeSpec.get().slot(boss.bossId()) < 0) {
            return;
        }
        Player nearest = level.getNearestPlayer(event.getX(), event.getY(), event.getZ(), 128, false);
        if (nearest == null || !wins(nearest).contains(boss.bossId())) {
            event.setSpawnCancelled(true);
        }
    }

    // Builds (or cleans) one arena: a round floor of the universe's blocks over two solid layers and a barrier, broken
    // pillars around the edge, invisible walls and ceiling, and nothing left inside from the last fight.
    private static void build(ServerLevel level, BlockPos center, ChallengeSpec spec, ChallengeSpec.Universe universe) {
        int radius = spec.raio();
        int height = spec.altura();
        List<BlockState> floor = new ArrayList<>();
        for (String id : universe.piso()) {
            floor.add(block(id, Blocks.STONE));
        }
        if (floor.isEmpty()) {
            floor.add(Blocks.STONE.defaultBlockState());
        }
        BlockState pillar = block(universe.pilar(), Blocks.STONE_BRICKS);
        BlockState base = Blocks.STONE.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        RandomSource random = RandomSource.create(center.asLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = center.getX();
        int cz = center.getZ();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 1.5) {
                    continue;
                }
                boolean inside = d <= radius;
                for (int y = FLOOR_Y - 3; y <= FLOOR_Y + height; y++) {
                    BlockState state;
                    if (!inside) {
                        state = y > FLOOR_Y ? barrier : air;
                    } else if (y == FLOOR_Y - 3 || y == FLOOR_Y + height) {
                        state = barrier;
                    } else if (y < FLOOR_Y) {
                        state = base;
                    } else if (y == FLOOR_Y) {
                        state = floor.get(random.nextInt(floor.size()));
                    } else {
                        state = air;
                    }
                    pos.set(cx + dx, y, cz + dz);
                    if (level.getBlockState(pos) != state) {
                        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // Broken pillars around the edge, the same every time for this arena.
        for (int i = 0; i < 8; i++) {
            float angle = i / 8f * Mth.TWO_PI + 0.3f;
            int px = cx + Math.round(Mth.cos(angle) * (radius - 4));
            int pz = cz + Math.round(Mth.sin(angle) * (radius - 4));
            int tall = 2 + random.nextInt(6);
            for (int y = 1; y <= tall; y++) {
                level.setBlock(pos.set(px, FLOOR_Y + y, pz), pillar, Block.UPDATE_CLIENTS);
            }
        }
        // Items, souls and leftovers from the last fight.
        AABB box = new AABB(cx - radius - 2, FLOOR_Y - 4, cz - radius - 2, cx + radius + 2, FLOOR_Y + height + 2, cz + radius + 2);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    private static BlockState block(String id, Block fallback) {
        if (id != null) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
                return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
            }
        }
        return fallback.defaultBlockState();
    }

    private static void title(ServerPlayer player, Component big, Component small) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 30, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(big));
        player.connection.send(new ClientboundSetSubtitleTextPacket(small));
    }

    // /soul desafios: the books of every universe. /soul desafios sair: leave the arena.
    // /soul desafios liberar <id|todos> [jogador] and /soul desafios zerar [jogador] (ops): for testing.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("desafios")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    for (var book : ModItems.CHALLENGE_BOOKS.values()) {
                        player.getInventory().add(new ItemStack(book.get()));
                    }
                    return 1;
                })
                .then(Commands.literal("sair").executes(context -> {
                    leave(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("liberar").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> unlock(context.getSource(), context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "id")))
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(context -> unlock(context.getSource(), EntityArgument.getPlayer(context, "jogador"),
                                                StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("zerar").requires(source -> source.hasPermission(2))
                        .executes(context -> reset(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> reset(context.getSource(), EntityArgument.getPlayer(context, "jogador")))));
    }

    private static int unlock(CommandSourceStack source, ServerPlayer player, String id) {
        List<String> wins = new ArrayList<>(wins(player));
        for (ChallengeSpec.Universe universe : ChallengeSpec.get().universos()) {
            for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                if ((id.equals("todos") || id.equals(challenge.id())) && !wins.contains(challenge.id())) {
                    wins.add(challenge.id());
                }
            }
        }
        player.setData(ModAttachments.ARENA_WINS, List.copyOf(wins));
        syncWins(player);
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.arena_wins", player.getDisplayName(), wins.size()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        player.setData(ModAttachments.ARENA_WINS, List.of());
        syncWins(player);
        source.sendSuccess(() -> Component.translatable("message.battlegroundsmod.arena_wins", player.getDisplayName(), 0), true);
        return 1;
    }
}
