// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// No flying in the end-game boss fights (Stark 2026-11-20: "A Aura de Freeza não te deixa voar", and the same for the
// Hashirama). A boss that answers flightLock() with a message holds every survival player within RADIUS on the ground
// while it fights (it has a target, or a player hit it a moment ago): everyone in the fight in LAN, nobody outside it.
// Nothing here touches the player's saved flight: universe/Flight only refuses the take-off while the lock lasts (and
// lets whoever was already in the air glide down, like when out of breath), so the flight is back the moment the fight
// is over, the boss dies or the player walks away. The introduction's own Hashirama scene is left alone.
public final class BossFlightLock {
    private static final double RADIUS = 64;
    // how often the bosses around are looked up again (ticks)
    private static final int RECHECK = 10;
    // a player's hit keeps the fight on this long even with no target (ticks)
    private static final int HIT_MEMORY = 100;
    // the message at most this often (ticks)
    private static final int TELL_EVERY = 50;
    // after a refused take-off, no new one for this long (so tapping jump can't hop the player upwards)
    private static final int RETRY = 30;

    private record Seen(String key, long at) {
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static final Map<UUID, Long> TOLD = new HashMap<>();
    private static final Map<UUID, Long> BARRED = new HashMap<>();
    // was flying when the fight reached them: gliding down, not dropped
    private static final Set<UUID> DESCENDING = new HashSet<>();
    // held on the ground last tick
    private static final Set<UUID> WAS = new HashSet<>();

    // /gamerule soulsDesativarVoo (default false): no flying for any survival player in the whole world (Stark
    // 2026-11-26), the same lock as a boss fight's: the Voo, Gaara's sand platform and any other flight a player was
    // lent are refused, and whoever is in the air when it is turned on glides down. Creative, spectators and the
    // introduction (it lends the flight for its own scenes) are left alone.
    public static final net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> FLIGHT_OFF =
            net.minecraft.world.level.GameRules.register("soulsDesativarVoo", net.minecraft.world.level.GameRules.Category.PLAYER,
                    net.minecraft.world.level.GameRules.BooleanValue.create(false));
    private static final String FLIGHT_OFF_KEY = "message.battlegroundsmod.flight_disabled";

    private BossFlightLock() {
    }

    // Loads the class so the rule is registered at startup.
    public static void init() {
    }

    // The message key of the boss (or the gamerule) holding this player on the ground right now, or null when they may fly.
    public static String key(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || com.starkinhon.battlegroundsmod.intro.Intro.active(player)) {
            return null;
        }
        if (player.level().getGameRules().getBoolean(FLIGHT_OFF)) {
            return FLIGHT_OFF_KEY;
        }
        long now = player.level().getGameTime();
        Seen seen = SEEN.get(player.getUUID());
        if (seen == null || now - seen.at() >= RECHECK || now < seen.at()) {
            seen = new Seen(find(player, now), now);
            SEEN.put(player.getUUID(), seen);
        }
        return seen.key();
    }

    private static String find(ServerPlayer player, long now) {
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (SoulBoss boss : player.level().getEntitiesOfClass(SoulBoss.class, player.getBoundingBox().inflate(RADIUS),
                b -> b.isAlive() && b.isBoss() && b.puppetOwner() == null && b.flightLock() != null)) {
            double d = boss.distanceToSqr(player);
            if (d > RADIUS * RADIUS || d >= bestD || !fighting(boss, now)) {
                continue;
            }
            best = boss.flightLock();
            bestD = d;
        }
        return best;
    }

    private static boolean fighting(SoulBoss boss, long now) {
        if (boss.getTarget() != null && boss.getTarget().isAlive()) {
            return true;
        }
        return boss.getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player
                && boss.tickCount - boss.getLastHurtByMobTimestamp() < HIT_MEMORY;
    }

    // Called by Flight every tick: whether the lock began this tick. When it ends, the gliding down is over too.
    public static boolean began(ServerPlayer player, boolean locked) {
        if (!locked) {
            WAS.remove(player.getUUID());
            DESCENDING.remove(player.getUUID());
            return false;
        }
        return WAS.add(player.getUUID());
    }

    // Called by Flight every tick while the player is in the air with the flight. True: let them glide down (they were
    // already flying when the lock began). False: a take-off under the lock, to be refused.
    public static boolean descending(ServerPlayer player, boolean lockBegan) {
        if (lockBegan) {
            DESCENDING.add(player.getUUID());
            tell(player, key(player));
        }
        return DESCENDING.contains(player.getUUID());
    }

    // The player touched down or the lock ended.
    public static void landed(ServerPlayer player) {
        DESCENDING.remove(player.getUUID());
    }

    // A take-off refused: the message, and no new try for a moment.
    public static void refused(ServerPlayer player, String key) {
        BARRED.put(player.getUUID(), player.level().getGameTime() + RETRY);
        tell(player, key);
    }

    // Right after a refused take-off: no flight allowed at all for a moment.
    public static boolean barred(ServerPlayer player) {
        Long until = BARRED.get(player.getUUID());
        if (until == null) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now >= until || until - now > RETRY) {
            BARRED.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void tell(ServerPlayer player, String key) {
        if (key == null) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = TOLD.get(player.getUUID());
        if (last != null && now - last < TELL_EVERY && now >= last) {
            return;
        }
        TOLD.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(key), true);
    }

    public static void forget(UUID id) {
        SEEN.remove(id);
        TOLD.remove(id);
        BARRED.remove(id);
        DESCENDING.remove(id);
        WAS.remove(id);
    }

    public static void resetStatics() {
        SEEN.clear();
        TOLD.clear();
        BARRED.clear();
        DESCENDING.clear();
        WAS.clear();
    }
}
