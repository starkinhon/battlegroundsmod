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
// - lets that character spawn in its universe's world (before that, only rarely: chanceRara);
// - brings the player back home after a few seconds (time to pick up the soul).
// All arenas live in a void dimension (battlegroundsmod:arenas, or arenas_noite for the ones that happen at night), one
// slot each. Each arena is the canon place of that fight (Stark 2026-11-12), made by tools/arenas and read from
// souls_arenas/<id>.bin (ArenaTemplate): the same blocks the web preview shows. Before every fight it is put back the
// way the generator made it over a few ticks (the fight circle cleaned of what the last fight left), with its own
// biome for the sky, fog and grass colours, and invisible walls round the fight circle. Dying or leaving ends the fight.
// A universe's bosses open once the player beats some of its SubAlmas (subAlmasParaChefes).
// With /gamerule soulsProgressao false (arena mode) or in a flat world everything stays free, spawns included.
public final class ChallengeArenas {
    public static final ResourceKey<Level> ARENAS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "arenas"));
    // Same void, the clock stopped at midnight: Shibuya on Halloween night, the metro, the Infinity Castle.
    public static final ResourceKey<Level> ARENAS_NIGHT = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "arenas_noite"));
    // The scenery of an arena reaches ~180 blocks from its centre; the old round arenas (every 400 blocks on z = 0)
    // stay behind, out of sight, on their own row.
    public static final int SPACING = 1024;
    public static final int ROW_Z = 8192;
    private static final int FLOOR_Y = 64;
    // Server time per tick spent putting an arena back (the tick has 50 ms). Stark's PC test 2026-11-13: a fixed 40k
    // blocks per tick ate 200-460 ms per tick, mostly generating the void chunks on the main thread; now the chunks are
    // loaded first by a ticket (worker threads) and the placing stops after this much time.
    private static final long BUILD_NANOS = 12_000_000L;
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> ARENA_TICKET =
            net.minecraft.server.level.TicketType.create("battlegroundsmod_arena", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int COUNTDOWN = 80;
    private static final int RETURN_DELAY = 240;

    private enum State { BUILDING, COUNTDOWN, FIGHT, DONE }

    private static final class Session {
        String id;
        BlockPos center;
        ResourceKey<Level> arena;
        ArenaTemplate.Meta meta;
        Build build;
        ResourceKey<Level> backLevel;
        Vec3 backPos;
        float backYaw;
        long fightAt;
        long buildStart;
        long buildNanos;
        long doneAt;
        UUID boss;
        boolean bossDied;
        int respawns;
        State state = State.COUNTDOWN;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ChallengeArenas() {
    }

    public static boolean isArena(ResourceKey<Level> dimension) {
        return dimension.equals(ARENAS) || dimension.equals(ARENAS_NIGHT);
    }

    // Which arena a position in the arena dimensions belongs to (the client's fog, Fx), or null.
    public static String arenaAt(double x, double z) {
        if (Math.abs(z - ROW_Z) > SPACING / 2.0) {
            return null;
        }
        int slot = (int) Math.round(x / SPACING);
        ChallengeSpec spec = ChallengeSpec.get();
        int index = 0;
        for (ChallengeSpec.Universe universe : spec.universos()) {
            for (ChallengeSpec.Challenge c : universe.desafios()) {
                if (index++ == slot) {
                    return c.id();
                }
            }
        }
        return null;
    }

    // The fight floor's height: low enough for the tallest scenery, high enough for the deepest (the world is 0-255).
    private static int floorY(ArenaTemplate.Meta meta) {
        int y = Math.max(FLOOR_Y, 2 - meta.minY());
        return Math.min(y, 254 - meta.maxY());
    }

    // What an arena without a generated file falls back to: the old round arena of its universe's blocks.
    private static ArenaTemplate.Meta discMeta(String id, ChallengeSpec spec) {
        int r = spec.raio();
        return new ArenaTemplate.Meta(id, r, spec.altura(), new int[] {0, 1, -r / 2}, new int[] {0, 1, r / 2}, null, false, -3, 0, id);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onServerTick);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onDeath);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onLogin);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                syncWins(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                syncWins(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onLogout);
        NeoForge.EVENT_BUS.addListener(ChallengeArenas::onFinalizeSpawn);
    }

    // The client's copy of its own wins (ModPayloads.SyncArenaWins), for the book.
    public static volatile List<String> clientWins = List.of();

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

    // A boss of the book (not a SubAlma): its arena opens after some of the universe's SubAlmas are beaten.
    public static boolean isBoss(String id) {
        try {
            return !Powers.isSub(BossSpec.get(id).poder());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // How many SubAlmas of this universe open its bosses.
    public static int subsNeeded(ChallengeSpec.Universe universe) {
        int subs = 0;
        for (ChallengeSpec.Challenge challenge : universe.desafios()) {
            if (!isBoss(challenge.id())) {
                subs++;
            }
        }
        return Math.min(ChallengeSpec.get().subAlmasParaChefes(), subs);
    }

    public static int subsBeaten(List<String> wins, ChallengeSpec.Universe universe) {
        int beaten = 0;
        for (ChallengeSpec.Challenge challenge : universe.desafios()) {
            if (!isBoss(challenge.id()) && wins.contains(challenge.id())) {
                beaten++;
            }
        }
        return beaten;
    }

    // Whether this character's arena is still closed for this player (free = progression off or a flat world).
    public static boolean locked(List<String> wins, String id, boolean free) {
        ChallengeSpec.Universe universe = ChallengeSpec.get().universeOf(id);
        return !free && universe != null && isBoss(id) && subsBeaten(wins, universe) < subsNeeded(universe);
    }

    // From the book: go to that character's arena.
    public static void start(ServerPlayer player, String id) {
        ChallengeSpec spec = ChallengeSpec.get();
        int slot = spec.slot(id);
        ChallengeSpec.Universe universe = spec.universeOf(id);
        ArenaTemplate.Meta meta = ArenaTemplate.meta(id);
        ArenaTemplate template = meta == null ? null : ArenaTemplate.get(id);
        if (template == null) {
            meta = discMeta(id, spec);
        }
        ResourceKey<Level> key = meta.noite() && player.server.getLevel(ARENAS_NIGHT) != null ? ARENAS_NIGHT : ARENAS;
        ServerLevel arena = player.server.getLevel(key);
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
        if (locked(wins(player), id, !Progression.on(player))) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_locked", subsNeeded(universe), universe.nome()), true);
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
        session.meta = meta;
        session.arena = key;
        session.center = new BlockPos(slot * SPACING, template == null ? FLOOR_Y : floorY(meta), ROW_Z);
        boolean fromArena = isArena(player.level().dimension());
        session.backLevel = fromArena ? Universes.homeLevel(player).dimension() : player.level().dimension();
        session.backPos = fromArena ? Universes.homePos(player) : player.position();
        session.backYaw = player.getYRot();
        SESSIONS.put(player.getUUID(), session);
        if (template == null) {
            buildDisc(arena, session.center, spec, universe);
            enter(player, arena, session);
            return;
        }
        // Put the arena back over the next ticks; the player goes in when it is ready (about a second).
        session.state = State.BUILDING;
        session.build = new Build(template, meta, session.center, arena);
        session.buildStart = arena.getGameTime();
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_building", name(id)), true);
    }

    // The arena is ready: the player comes in at their side, facing the challenger's, and the countdown starts.
    private static void enter(ServerPlayer player, ServerLevel arena, Session session) {
        ArenaTemplate.Meta meta = session.meta;
        int r = meta.raio();
        AABB box = new AABB(session.center).inflate(r + 3, 0, r + 3).expandTowards(0, meta.altura() + 4, 0).expandTowards(0, -16, 0);
        for (Entity entity : arena.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
        BlockPos c = session.center;
        double px = c.getX() + meta.jogador()[0] + 0.5, py = c.getY() + meta.jogador()[1], pz = c.getZ() + meta.jogador()[2] + 0.5;
        double dx = meta.desafiante()[0] - meta.jogador()[0], dz = meta.desafiante()[2] - meta.jogador()[2];
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        session.fightAt = arena.getGameTime() + COUNTDOWN;
        session.state = State.COUNTDOWN;
        player.teleportTo(arena, px, py, pz, yaw, 0);
        player.fallDistance = 0;
        ChallengeSpec.Universe universe = ChallengeSpec.get().universeOf(session.id);
        int color = universe == null ? 0xFFFFFF : universe.color();
        title(player, Component.translatable("message.battlegroundsmod.arena_title", name(session.id))
                .withStyle(s -> s.withColor(color).withBold(true)), Component.translatable("message.battlegroundsmod.arena_ready"));
        Fx.sound(arena, Vec3.atCenterOf(session.center), SoundEvents.END_PORTAL_SPAWN, 0.6f, 1.4f);
    }

    // From the book's "back" button or the end of a fight: home again.
    public static void leave(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            if (isArena(player.level().dimension())) {
                Vec3 home = Universes.homePos(player);
                player.teleportTo(Universes.homeLevel(player), home.x, home.y, home.z, player.getYRot(), 0);
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
        // A soul still lying in the arena goes home with the player: it is the prize of the fight.
        List<ItemStack> souls = new ArrayList<>();
        ServerLevel arena = session.arena == null ? null : player.server.getLevel(session.arena);
        if (arena != null && session.center != null) {
            AABB box = new AABB(session.center).inflate(80, 64, 80);
            for (net.minecraft.world.entity.item.ItemEntity item : arena.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> e.getItem().getItem() instanceof com.starkinhon.battlegroundsmod.item.SoulItem)) {
                souls.add(item.getItem().copy());
                item.discard();
            }
        }
        player.teleportTo(level, session.backPos.x, session.backPos.y, session.backPos.z, session.backYaw, 0);
        for (ItemStack soul : souls) {
            if (!player.getInventory().add(soul)) {
                player.drop(soul, false);
            }
        }
    }

    private static void removeBoss(MinecraftServer server, Session session) {
        if (session.build != null) {
            session.build.release();             // left while the arena was still being put back
            session.build = null;
        }
        ServerLevel arena = session.arena == null ? null : server.getLevel(session.arena);
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
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            Session session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel arena = server.getLevel(session.arena);
            if (player == null || arena == null || (session.state != State.BUILDING && player.level() != arena)) {
                SESSIONS.remove(entry.getKey());
                removeBoss(server, session);
                continue;
            }
            long now = arena.getGameTime();
            switch (session.state) {
                case BUILDING -> {
                    long t0 = System.nanoTime();
                    boolean ready = session.build.step(BUILD_NANOS);
                    if (!ready && (now - session.buildStart) % 20 == 19) {
                        player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_building_pct", name(session.id),
                                session.build.percent()), true);
                    }
                    session.buildNanos += System.nanoTime() - t0;
                    if (ready) {
                        // For the tests on Stark's PC: how long the arena took and how much server time it ate.
                        BattlegroundsMod.LOGGER.info("[arena] {} pronta em {} ticks ({} esperando {} chunks), {} ms de servidor ({} blocos)",
                                session.id, now - session.buildStart + 1, session.build.chunkWaitTicks, session.build.chunkCount,
                                session.buildNanos / 1_000_000, session.build.t.blocks);
                        session.build.release();
                        session.build = null;
                        enter(player, arena, session);
                    }
                }
                case COUNTDOWN -> {
                    catchFall(player, arena, session);
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
                    catchFall(player, arena, session);
                    Entity boss = session.boss == null ? null : arena.getEntity(session.boss);
                    if (boss == null && !session.bossDied && session.respawns < 3) {
                        // Gone without dying (Stark's test 2026-11-10: he came in flying and Kuririn was "born dead",
                        // a win for nothing): bring the character back instead of counting a win.
                        session.respawns++;
                        spawnBoss(player, arena, session);
                    } else if (boss == null || !boss.isAlive()) {
                        win(player, session, now);
                    } else {
                        keepInside(boss, session);
                        if (now % 20 == 0 && boss instanceof Mob mob && mob.getTarget() != player) {
                            mob.setTarget(player);
                        }
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

    // Thrown out of the ring (Stark 2026-11-10: the Shikamaru's shadow walked the Kakashi out of it, and the fight and
    // the soul were lost): back in, at the far side from the player's start.
    private static void keepInside(Entity boss, Session session) {
        ArenaTemplate.Meta meta = session.meta;
        int radius = meta.raio();
        double dx = boss.getX() - (session.center.getX() + 0.5);
        double dz = boss.getZ() - (session.center.getZ() + 0.5);
        double y = boss.getY() - session.center.getY();
        boolean out = dx * dx + dz * dz > (radius - 1.0) * (radius - 1.0) || y < -12 || y > meta.altura() + 20;
        if (out) {
            BlockPos at = spawnPos(session);
            boss.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            boss.fallDistance = 0;
        }
    }

    private static void spawnBoss(ServerPlayer player, ServerLevel arena, Session session) {
        EntityType<?> type = entityType(session.id);
        Entity boss = type == null ? null : type.spawn(arena, spawnPos(session), MobSpawnType.EVENT);
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

    // Fell through the scenery into the void under it (Stark's PC test 2026-11-13: the Todo forest and the Infinity
    // Castle, right after arriving): back to the player's spawn, no fall damage. Logged to find the hole.
    private static void catchFall(ServerPlayer player, ServerLevel arena, Session session) {
        if (player.getY() >= session.center.getY() - 16 || player.isSpectator()) {
            return;
        }
        int[] j = session.meta.jogador();
        BlockPos spawn = session.center.offset(j[0], j[1], j[2]);
        BattlegroundsMod.LOGGER.warn("[arena] {}: jogador caiu em {} {} {} (spawn {}, chão do spawn: {})", session.id,
                (int) player.getX(), (int) player.getY(), (int) player.getZ(), spawn.toShortString(),
                arena.getBlockState(spawn.below()));
        player.teleportTo(arena, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static BlockPos spawnPos(Session session) {
        int[] d = session.meta.desafiante();
        return session.center.offset(d[0], d[1], d[2]);
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
        arenaPoder(player, session.id, first);
        title(player, Component.translatable("message.battlegroundsmod.arena_won").withStyle(s -> s.withColor(0xD8352A).withBold(true)), name(session.id).copy());
        player.displayClientMessage(Component.translatable(first ? "message.battlegroundsmod.arena_unlocked" : "message.battlegroundsmod.arena_again",
                name(session.id), RETURN_DELAY / 20), false);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // Poder for beating a challenge (Stark 2026-11-10: "os bosses dos desafios darem poder quando você vencer"). The kill
    // in the arena pays nothing by itself (Progression skips the arenas), this does: the first win a good part of the
    // player's tier (a whole tier for a book boss like Freeza), a rematch a little, nothing once 3+ tiers above it.
    private static void arenaPoder(ServerPlayer player, String id, boolean first) {
        if (!Progression.on(player)) {
            return;
        }
        int tier = com.starkinhon.battlegroundsmod.power.Tiers.of(player);
        int bossTier = tier;
        try {
            Integer t = BossSpec.get(id).tier();
            bossTier = t == null ? tier : t;
        } catch (RuntimeException ignored) {
        }
        var spec = com.starkinhon.battlegroundsmod.skill.ProgressionSpec.get();
        double worth = Progression.value(bossTier) * spec.multSubAlma();
        double gain;
        if (first) {
            gain = isBoss(id) ? Progression.cost(tier) : Math.max(Progression.cost(tier) * 0.5, worth);
        } else {
            gain = bossTier - tier >= spec.abaixoZera() ? 0 : worth * 0.3;
        }
        Progression.add(player, gain);
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
        for (Session session : SESSIONS.values()) {
            if (event.getEntity().getUUID().equals(session.boss)) {
                session.bossDied = true;
            }
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                removeBoss(player.server, session);
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.arena_lost", name(session.id)), false);
            }
        }
    }

    // A player who logs in inside the arenas without a fight (the server stopped mid-fight) goes back home.
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncWins(player);
            if (isArena(player.level().dimension()) && !SESSIONS.containsKey(player.getUUID())) {
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

    // Challenge characters only spawn in their own universe's world: rarely (chanceRara) until the nearest player has
    // beaten them in their arena, normally after. Outside the universes (the overworld) they don't spawn at all.
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (!(event.getEntity() instanceof SoulBoss boss)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        ChallengeSpec spec = ChallengeSpec.get();
        // A universe's common enemies (Saibaman...) only in that universe.
        ChallengeSpec.Universe mobUniverse = spec.universeOfMob(boss.bossId());
        BlockPos at = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        String here = com.starkinhon.battlegroundsmod.universe.UniverseBiomes.universeAt(level, at);
        if (mobUniverse != null) {
            if (Progression.on(level.getServer()) && !mobUniverse.id().equals(here)) {
                event.setSpawnCancelled(true);
            }
            return;
        }
        ChallengeSpec.Universe universe = spec.universeOf(boss.bossId());
        if (universe == null || !Progression.on(level.getServer())) {
            return;
        }
        Player nearest = level.getNearestPlayer(event.getX(), event.getY(), event.getZ(), 128, false);
        if (level.dimension() == Level.OVERWORLD) {
            // The universes are biomes of the normal world now (Stark 2026-11-11): the characters roam the whole world,
            // rare, and nobody more than two tiers above the nearest player (a Tier 10 meets up to Tier 8), so the
            // world grows with them.
            Integer bossTier = null;
            try {
                bossTier = BossSpec.get(boss.bossId()).tier();
            } catch (RuntimeException ignored) {
            }
            int playerTier = nearest instanceof ServerPlayer sp ? com.starkinhon.battlegroundsmod.power.Tiers.of(sp)
                    : com.starkinhon.battlegroundsmod.power.Tiers.HUMANO;
            if (nearest == null || (bossTier != null && bossTier < playerTier - 2)) {
                event.setSpawnCancelled(true);
                return;
            }
        } else if (!universe.id().equals(here)) {
            event.setSpawnCancelled(true);
            return;
        }
        boolean beaten = nearest != null && wins(nearest).contains(boss.bossId());
        if (!beaten && level.getRandom().nextDouble() >= spec.chanceRara()) {
            event.setSpawnCancelled(true);
        }
    }

    // Puts one generated arena back, a slice per tick (step): 1. every block the generator made (only where the world
    // differs, so after the first time it is mostly checking); 2. the fight circle and a little past it cleaned of what
    // the last fight left (holes filled above, blocks that were not there removed); 3. invisible walls round the circle
    // and a ceiling at its height, only where the scenery leaves air; 4. the arena's own biome (sky, fog, grass).
    private static final class Build {
        private final ArenaTemplate t;
        private final ArenaTemplate.Meta m;
        private final BlockPos c;
        private final ServerLevel level;
        private final int half;
        private final int[] index;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int phase;
        private int cursor;
        private int minCx, maxCx, minCz, maxCz;
        private final List<net.minecraft.world.level.chunk.ChunkAccess> changed = new ArrayList<>();
        private final net.minecraft.world.level.ChunkPos ticketAt;
        private final int ticketRadius;
        private boolean ticketed;
        int chunkWaitTicks;
        int chunkCount;

        Build(ArenaTemplate t, ArenaTemplate.Meta m, BlockPos c, ServerLevel level) {
            this.t = t;
            this.m = m;
            this.c = c;
            this.level = level;
            this.half = m.raio() + 3;
            this.index = t.index(half);
            int x0 = 0, x1 = 0, z0 = 0, z1 = 0;
            for (int i = 0; i < t.columns; i++) {
                x0 = Math.min(x0, t.colX[i]);
                x1 = Math.max(x1, t.colX[i]);
                z0 = Math.min(z0, t.colZ[i]);
                z1 = Math.max(z1, t.colZ[i]);
            }
            minCx = (c.getX() + x0) >> 4;
            maxCx = (c.getX() + x1) >> 4;
            minCz = (c.getZ() + z0) >> 4;
            maxCz = (c.getZ() + z1) >> 4;
            ticketAt = new net.minecraft.world.level.ChunkPos(c);
            ticketRadius = Math.min(32, Math.max(Math.max(ticketAt.x - minCx, maxCx - ticketAt.x), Math.max(ticketAt.z - minCz, maxCz - ticketAt.z)) + 1);
            phase = -1;
        }

        int percent() {
            int w = maxCx - minCx + 1;
            int chunks = w * (maxCz - minCz + 1);
            return switch (phase) {
                case -1 -> cursor * 40 / Math.max(1, chunks);
                case 0 -> 40 + cursor * 50 / Math.max(1, t.columns);
                default -> 95;
            };
        }

        void release() {
            if (ticketed) {
                level.getChunkSource().removeRegionTicket(ARENA_TICKET, ticketAt, ticketRadius, ticketAt);
                ticketed = false;
            }
        }

        private int put(int x, int y, int z, BlockState state) {
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return 0;
            }
            pos.set(x, y, z);
            if (level.getBlockState(pos) != state) {
                level.setBlock(pos, state, FLAGS);
            }
            return 1;
        }

        private int column(int dx, int dz) {
            if (dx < -half || dz < -half || dx > half || dz > half) {
                return -1;
            }
            return index[(dx + half) * (half * 2 + 1) + dz + half];
        }

        // Returns true when the arena is ready. Works until maxNanos of this tick are spent.
        boolean step(long maxNanos) {
            long deadline = System.nanoTime() + maxNanos;
            int ops = 0;
            int side = half * 2 + 1;
            while (true) {
                if (ops >= 2048) {
                    ops = 0;
                    if (System.nanoTime() > deadline) {
                        return false;
                    }
                }
                switch (phase) {
                    case -1 -> {
                        // 0. every chunk of the arena loaded (generated off the main thread) before touching a block
                        if (!ticketed) {
                            level.getChunkSource().addRegionTicket(ARENA_TICKET, ticketAt, ticketRadius, ticketAt);
                            ticketed = true;
                        }
                        int w = maxCx - minCx + 1;
                        int total = w * (maxCz - minCz + 1);
                        while (cursor < total && level.getChunkSource().getChunkNow(minCx + cursor % w, minCz + cursor / w) != null) {
                            cursor++;
                        }
                        if (cursor < total) {
                            chunkWaitTicks++;
                            return false;            // still loading: try again next tick
                        }
                        chunkCount = total;
                        phase = 0;
                        cursor = 0;
                    }
                    case 0 -> {
                        if (cursor >= t.columns) {
                            phase = 1;
                            cursor = 0;
                            continue;
                        }
                        int x = c.getX() + t.colX[cursor];
                        int z = c.getZ() + t.colZ[cursor];
                        for (int r = t.runStart[cursor]; r < t.runStart[cursor + 1]; r++) {
                            BlockState state = t.palette[t.runBlock[r]];
                            for (int y = t.runY[r], end = t.runY[r] + t.runLen[r]; y < end; y++) {
                                ops += put(x, c.getY() + y, z, state);
                            }
                        }
                        cursor++;
                    }
                    case 1, 2 -> {
                        if (cursor >= side * side) {
                            phase++;
                            cursor = 0;
                            continue;
                        }
                        int dx = cursor / side - half;
                        int dz = cursor % side - half;
                        cursor++;
                        double d = Math.sqrt(dx * dx + dz * dz);
                        int col = column(dx, dz);
                        int low = col >= 0 ? t.lowest(col) : -8;
                        int top = m.altura() + 1;
                        if (phase == 1) {
                            if (d > half) {
                                continue;
                            }
                            BlockState air = Blocks.AIR.defaultBlockState();
                            for (int y = low; y <= top; y++) {
                                if (col < 0 || !t.solid(col, y)) {
                                    ops += put(c.getX() + dx, c.getY() + y, c.getZ() + dz, air);
                                }
                            }
                        } else {
                            BlockState barrier = Blocks.BARRIER.defaultBlockState();
                            boolean ring = d > m.raio() + 0.5 && d <= m.raio() + 1.5;
                            if (ring) {
                                for (int y = low; y <= top; y++) {
                                    if (col < 0 || !t.solid(col, y)) {
                                        ops += put(c.getX() + dx, c.getY() + y, c.getZ() + dz, barrier);
                                    }
                                }
                            } else if (d <= m.raio() + 0.5 && (col < 0 || !t.solid(col, top))) {
                                ops += put(c.getX() + dx, c.getY() + top, c.getZ() + dz, barrier);
                            }
                        }
                    }
                    case 3 -> {
                        int w = maxCx - minCx + 1;
                        int total = w * (maxCz - minCz + 1);
                        if (cursor >= total) {
                            if (!changed.isEmpty()) {
                                level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
                            }
                            phase = 4;
                            return true;
                        }
                        biome(minCx + cursor % w, minCz + cursor / w);
                        cursor++;
                        ops += 1500;
                    }
                    default -> {
                        return true;
                    }
                }
            }
        }

        private void biome(int cx, int cz) {
            ResourceKey<net.minecraft.world.level.biome.Biome> key = ResourceKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "arena_" + m.id()));
            var holder = level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key);
            if (holder.isEmpty()) {
                return;
            }
            net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(cx, cz);
            int qy = net.minecraft.core.QuartPos.fromBlock(c.getY());
            if (chunk.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(cx << 4), qy, net.minecraft.core.QuartPos.fromBlock(cz << 4)).is(key)) {
                return;
            }
            var biome = holder.get();
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> biome, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
            changed.add(chunk);
        }
    }

    // The fallback for a character with no generated arena: a round floor of the universe's blocks over two solid layers
    // and a barrier, broken pillars around the edge, invisible walls and ceiling, nothing left from the last fight.
    private static void buildDisc(ServerLevel level, BlockPos center, ChallengeSpec spec, ChallengeSpec.Universe universe) {
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

    // /soul desafios: the Livro dos Desafios (every universe in one book). /soul desafios sair: leave the arena.
    // /soul desafios liberar <id|todos> [jogador], /soul desafios zerar [jogador] and /soul desafios ir <id> (ops): for
    // testing (ir = the book's button, straight into that arena).
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("desafios")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.getInventory().add(new ItemStack(ModItems.CHALLENGE_BOOK.get()));
                    return 1;
                })
                .then(Commands.literal("ir").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (ChallengeSpec.Universe universe : ChallengeSpec.get().universos()) {
                                        for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                                            builder.suggest(challenge.id());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Session old = SESSIONS.remove(player.getUUID());
                                    if (old != null) {
                                        removeBoss(player.server, old);       // tests jump from arena to arena
                                    }
                                    start(player, StringArgumentType.getString(context, "id"));
                                    return 1;
                                })))
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
