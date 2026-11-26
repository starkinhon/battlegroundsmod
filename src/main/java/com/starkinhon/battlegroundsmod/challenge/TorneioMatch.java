// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// The Torneio do Poder as a game (Stark 2026-11-26): on the menu (client/TorneioScreen) you pick who fights, any of the
// sub-souls, divine souls and final bosses, as many as you like; everyone against everyone or teams by universe (the
// anime's teams); all at Tier 0 or each at its own tier. Start: the fighters and the players in the arena are thrown at
// random spots of the stage. Falling off the stage puts you back on a random spot of it; only dying is out, and a player
// who is out watches as a spectator from the gods' balcony. The chat tells who beat who. The last one standing (or the last team) wins. Teammates
// can't hurt each other.
public final class TorneioMatch {
    private static final int OUT_Y = Torneio.FLOOR_Y - 50;
    private static final int END_TICKS = 160;
    private static final String GAMEMODE = "souls_torneio_modo";

    private static final class Fighter {
        final UUID id;
        final boolean player;
        final String team;
        Component name;
        UUID lastHit;
        long lastHitAt = -1000;
        Entity ref;

        Fighter(UUID id, boolean player, String team, Component name) {
            this.id = id;
            this.player = player;
            this.team = team;
            this.name = name;
        }
    }

    private static final Map<UUID, Fighter> ALIVE = new LinkedHashMap<>();
    private static final Map<UUID, Fighter> ALL = new HashMap<>();
    private static boolean running;
    private static boolean teams;
    private static int endIn = -1;
    // The whole stage stays loaded and ticking while a match runs (Stark 2026-11-26, the fighters far from the player
    // "left the tournament" at the start: their chunks unloaded, so the game took them for gone). A region ticket of
    // radius 24 keeps entities ticking 22 chunks (352 blocks) out, past the "thrown too far" line at RA + 60.
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("battlegroundsmod_torneio", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
    private static final int TICKET_RADIUS = 24;
    private static ServerLevel ticketLevel;

    private TorneioMatch() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onServerTick);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onDamage);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onDeath);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onDrops);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onXp);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onLogout);
        NeoForge.EVENT_BUS.addListener(TorneioMatch::onLogin);
    }

    public static void resetStatics() {
        ticketLevel = null;
        ALIVE.clear();
        ALL.clear();
        running = false;
        endIn = -1;
    }

    public static boolean running() {
        return running;
    }

    // ---------- start ----------

    // From the menu: ids picked, teams or everyone against everyone, all at Tier 0 or each at its own, the player's team.
    public static void start(ServerPlayer starter, List<String> ids, boolean teamMode, boolean tier0, String team) {
        ServerLevel level = starter.server.getLevel(Torneio.LEVEL);
        if (level == null || starter.level() != level) {
            return;
        }
        if (running) {
            starter.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.ja_rolando"), false);
            return;
        }
        List<String> picked = new ArrayList<>();
        for (String id : ids) {
            if (TorneioRoster.known(id) && picked.size() < 64) {
                picked.add(id);
            }
        }
        String playerTeam = TorneioRoster.UNIVERSES.contains(team) ? team : "dragonball";
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) { // creative too (Stark 2026-11-26: he wasn't counted): fights in survival, back after
                players.add(p);
            }
        }
        Set<String> sides = new LinkedHashSet<>();
        for (String id : picked) {
            sides.add(teamMode ? TorneioRoster.universe(id) : id + sides.size());
        }
        for (ServerPlayer p : players) {
            sides.add(teamMode ? playerTeam : p.getStringUUID());
        }
        if (sides.size() < 2) {
            starter.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.poucos"), false);
            return;
        }
        resetStatics();
        running = true;
        teams = teamMode;
        holdStage(level);
        List<BlockPos> used = new ArrayList<>();
        var rng = starter.getRandom();
        for (ServerPlayer p : players) {
            BlockPos at = spot(level, used, rng);
            p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0);
            p.fallDistance = 0;
            if (p.isCreative()) {
                p.getPersistentData().putInt(GAMEMODE, p.gameMode.getGameModeForPlayer().getId());
                p.setGameMode(GameType.SURVIVAL);
            }
            p.setHealth(p.getMaxHealth());
            String t = teamMode ? playerTeam : p.getStringUUID();
            Fighter f = new Fighter(p.getUUID(), true, t, p.getDisplayName());
            ALIVE.put(f.id, f);
            ALL.put(f.id, f);
        }
        for (String id : picked) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id));
            BlockPos at = spot(level, used, rng);
            Entity e = type.spawn(level, at, MobSpawnType.EVENT);
            if (e == null) {
                continue;
            }
            if (e instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            e.fallDistance = 0;
            if (e instanceof SoulBoss soul) {
                // the Dungeon's exact tier: Tier 0 for everyone, or the fighter's own; and no soul is dropped here
                Integer own = soul.spec().tier();
                soul.dungeonTier(tier0 ? 0 : own == null ? (soul.isBoss() ? 0 : 10) : Math.max(0, Math.min(10, own)));
            }
            String t = teamMode ? TorneioRoster.universe(id) : e.getStringUUID();
            Fighter f = new Fighter(e.getUUID(), false, t, e.getDisplayName());
            f.ref = e;
            ALIVE.put(f.id, f);
            ALL.put(f.id, f);
        }
        broadcast(level, Component.translatable("message.battlegroundsmod.torneio.comecou", ALIVE.size()).withStyle(s -> s.withColor(0xE6B4FF)));
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.titulo")
                    .withStyle(s -> s.withColor(0xE6B4FF).withBold(true))));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.valendo")));
            level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
        }
    }

    // A random spot of the stage, at least 30 blocks from the others already given.
    private static BlockPos spot(ServerLevel level, List<BlockPos> used, net.minecraft.util.RandomSource rng) {
        int R = TorneioGenerator.RA - 30;
        BlockPos best = null;
        for (int i = 0; i < 60; i++) {
            double a = rng.nextDouble() * Math.PI * 2, r = 40 + Math.sqrt(rng.nextDouble()) * (R - 40);
            BlockPos p = Torneio.ground(level, (int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r));
            if (p == null) {
                continue;
            }
            best = p;
            boolean far = true;
            for (BlockPos u : used) {
                if (u.distSqr(p) < 30 * 30) {
                    far = false;
                    break;
                }
            }
            if (far) {
                break;
            }
        }
        if (best == null) {
            best = Torneio.center().offset(0, 2, 60);
        }
        used.add(best);
        return best;
    }

    public static void stop(MinecraftServer server) {
        ServerLevel level = server.getLevel(Torneio.LEVEL);
        if (level != null && running) {
            finish(level);
        }
    }

    // ---------- the match ----------

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!running) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(Torneio.LEVEL);
        if (level == null) {
            resetStatics();
            return;
        }
        if (endIn >= 0) {
            if (--endIn <= 0) {
                finish(level);
            }
            return;
        }
        long now = level.getGameTime();
        for (Fighter f : new ArrayList<>(ALIVE.values())) {
            Entity e = level.getEntity(f.id);
            if (e == null) {
                if (!f.player && f.ref != null) {
                    Entity.RemovalReason why = f.ref.getRemovalReason();
                    if (why == null || !why.shouldDestroy()) {
                        continue; // only unloaded with its chunk, it comes back
                    }
                    if (why == Entity.RemovalReason.DISCARDED && takeOver(level, f)) {
                        continue; // turned into another (Itadori into Sukuna): the new one fights on in his place
                    }
                }
                // a mob that vanished or a player who left the arena
                ServerPlayer p = f.player ? server.getPlayerList().getPlayer(f.id) : null;
                if (p == null || p.level() != level) {
                    out(level, f, null, false);
                }
                continue;
            }
            if (!f.player) {
                f.ref = e;
            }
            f.name = e.getDisplayName();
            // Off the stage (fell, or thrown far into the void): back to a random spot of it. Only dying is out (Stark
            // 2026-11-26: some attacks threw everyone off the arena).
            double far = TorneioGenerator.RA + 60;
            if (e.getY() < OUT_Y || e.getX() * e.getX() + e.getZ() * e.getZ() > far * far) {
                BlockPos at = spot(level, new ArrayList<>(), level.getRandom());
                e.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
                e.fallDistance = 0;
            }
        }
        if (now % 5 == 0) {
            aim(level);
        }
        if (now % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                p.displayClientMessage(Component.translatable("message.battlegroundsmod.torneio.restam", ALIVE.size()), true);
            }
        }
        checkWin(level);
    }

    // A fighter discarded on the spot where a new soul just showed up turned into it: the new one takes his place.
    private static boolean takeOver(ServerLevel level, Fighter f) {
        Vec3 at = f.ref.position();
        for (SoulBoss b : level.getEntitiesOfClass(SoulBoss.class, new net.minecraft.world.phys.AABB(at, at).inflate(4))) {
            if (b.isAlive() && !ALL.containsKey(b.getUUID())) {
                Fighter n = new Fighter(b.getUUID(), false, f.team, b.getDisplayName());
                n.ref = b;
                n.lastHit = f.lastHit;
                n.lastHitAt = f.lastHitAt;
                b.setPersistenceRequired();
                ALIVE.remove(f.id);
                ALIVE.put(n.id, n);
                ALL.put(n.id, n);
                return true;
            }
        }
        return false;
    }

    private static void holdStage(ServerLevel level) {
        releaseStage();
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(0, 0);
        level.getChunkSource().addRegionTicket(TICKET, c, TICKET_RADIUS, c);
        ticketLevel = level;
    }

    private static void releaseStage() {
        if (ticketLevel != null) {
            net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(0, 0);
            ticketLevel.getChunkSource().removeRegionTicket(TICKET, c, TICKET_RADIUS, c);
            ticketLevel = null;
        }
    }

    // Every fighter on the nearest one of another team (never a teammate, never someone out).
    private static void aim(ServerLevel level) {
        List<LivingEntity> alive = new ArrayList<>();
        for (Fighter f : ALIVE.values()) {
            if (level.getEntity(f.id) instanceof LivingEntity le && le.isAlive()) {
                alive.add(le);
            }
        }
        for (LivingEntity le : alive) {
            if (!(le instanceof Mob mob)) {
                continue;
            }
            Fighter me = ALIVE.get(mob.getUUID());
            LivingEntity cur = mob.getTarget();
            Fighter curF = cur == null ? null : ALIVE.get(cur.getUUID());
            if (cur != null && cur.isAlive() && curF != null && !curF.team.equals(me.team)) {
                continue;
            }
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity o : alive) {
                Fighter of = ALIVE.get(o.getUUID());
                if (o == mob || of == null || of.team.equals(me.team)) {
                    continue;
                }
                double d = o.distanceToSqr(mob);
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
            if (best != null) {
                mob.setTarget(best);
            }
        }
    }

    private static void checkWin(ServerLevel level) {
        Set<String> left = new LinkedHashSet<>();
        for (Fighter f : ALIVE.values()) {
            left.add(f.team);
        }
        if (left.size() > 1) {
            return;
        }
        Component who;
        if (left.isEmpty()) {
            who = Component.translatable("message.battlegroundsmod.torneio.ninguem");
        } else if (teams) {
            who = Component.literal(TorneioRoster.universeName(left.iterator().next()));
        } else {
            who = ALIVE.values().iterator().next().name;
        }
        Component line = Component.translatable("message.battlegroundsmod.torneio.venceu", who).withStyle(s -> s.withColor(0xFFD84A).withBold(true));
        broadcast(level, line);
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitleTextPacket(line));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        endIn = END_TICKS;
    }

    // The match is over: the fighters still there go away, the players come back to the stage as they were.
    private static void finish(ServerLevel level) {
        for (Fighter f : ALL.values()) {
            Entity e = level.getEntity(f.id);
            if (e != null && !(e instanceof ServerPlayer)) {
                e.discard();
            }
        }
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            restore(p);
            Torneio.toStage(p, level);
        }
        releaseStage();
        resetStatics();
    }

    private static void out(ServerLevel level, Fighter f, Fighter by, boolean fell) {
        if (ALIVE.remove(f.id) == null) {
            return;
        }
        Component line;
        if (fell) {
            line = by != null && by != f ? Component.translatable("message.battlegroundsmod.torneio.derrubou", by.name, f.name)
                    : Component.translatable("message.battlegroundsmod.torneio.caiu", f.name);
        } else if (by != null && by != f) {
            line = Component.translatable("message.battlegroundsmod.torneio.derrotou", by.name, f.name);
        } else {
            line = Component.translatable("message.battlegroundsmod.torneio.saiu", f.name);
        }
        broadcast(level, line.copy().withStyle(s -> s.withColor(0xFF8A6A)));
    }

    private static void broadcast(ServerLevel level, Component line) {
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(line);
        }
    }

    // ---------- players out: watching from the balcony ----------

    private static void spectate(ServerPlayer p) {
        if (!p.getPersistentData().contains(GAMEMODE)) {
            p.getPersistentData().putInt(GAMEMODE, p.gameMode.getGameModeForPlayer().getId());
        }
        p.setGameMode(GameType.SPECTATOR);
        ServerLevel level = p.serverLevel();
        double a = Math.toRadians(265);
        double r = TorneioGenerator.RA + 4 * TorneioGenerator.K;
        Vec3 at = new Vec3(Math.cos(a) * r, Torneio.FLOOR_Y + 34 * TorneioGenerator.K + 2, Math.sin(a) * r);
        float yaw = (float) Math.toDegrees(Math.atan2(at.x, -at.z));
        p.teleportTo(level, at.x, at.y, at.z, yaw, 20f);
        p.fallDistance = 0;
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.fora")
                .withStyle(s -> s.withColor(0xFF6A5A).withBold(true))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.battlegroundsmod.torneio.assistindo")));
    }

    static void restore(ServerPlayer p) {
        var tag = p.getPersistentData();
        if (tag.contains(GAMEMODE)) {
            p.setGameMode(GameType.byId(tag.getInt(GAMEMODE)));
            tag.remove(GAMEMODE);
        }
    }

    // ---------- events ----------

    // The one who hit counts for the knock-off; teammates don't hurt each other.
    private static void onDamage(LivingIncomingDamageEvent event) {
        if (!running || event.getEntity().level().isClientSide()) {
            return;
        }
        Fighter victim = ALIVE.get(event.getEntity().getUUID());
        if (victim == null) {
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src instanceof Projectile pr && pr.getOwner() != null) {
            src = pr.getOwner();
        }
        Fighter by = src == null ? null : ALIVE.get(src.getUUID());
        if (by == null || by == victim) {
            return;
        }
        if (teams && by.team.equals(victim.team)) {
            event.setCanceled(true);
            return;
        }
        victim.lastHit = by.id;
        victim.lastHitAt = event.getEntity().level().getGameTime();
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!running || !(event.getEntity().level() instanceof ServerLevel level) || level.dimension() != Torneio.LEVEL) {
            return;
        }
        Fighter f = ALIVE.get(event.getEntity().getUUID());
        if (f == null) {
            return;
        }
        Entity src = event.getSource().getEntity();
        if (src instanceof Projectile pr && pr.getOwner() != null) {
            src = pr.getOwner();
        }
        Fighter by = src == null ? null : ALL.get(src.getUUID());
        if (by == null && f.lastHit != null && level.getGameTime() - f.lastHitAt < 200) {
            by = ALL.get(f.lastHit);
        }
        out(level, f, by, false);
        if (event.getEntity() instanceof ServerPlayer p) {
            // out, not dead: no items lost, straight to the balcony to watch
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            spectate(p);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        if (ALL.containsKey(event.getEntity().getUUID()) && !(event.getEntity() instanceof ServerPlayer)) {
            event.setCanceled(true);
        }
    }

    private static void onXp(LivingExperienceDropEvent event) {
        if (ALL.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Fighter f = ALIVE.get(p.getUUID());
            if (f != null && p.level() instanceof ServerLevel level) {
                out(level, f, null, false);
            }
            restore(p);
        }
    }

    // a player who left as a spectator of a match that is no longer going
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !running) {
            restore(p);
        }
    }
}
