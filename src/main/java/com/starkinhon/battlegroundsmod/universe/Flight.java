package com.starkinhon.battlegroundsmod.universe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Voo (Bukujutsu), the Dragon Ball technique (Stark 2026-11-10: universe techniques are the player's, they work in every
// universe). Learned from Gohan (npc/Npcs). Once learned: jump twice to take off, like the creative flight.
// The flight itself is the approved page's (forja voo/voo.html, Stark 2026-11-10 "bem melhor"): the client moves the
// player (client/FlightClient: cruise, arcs in the turns, the boost on Ctrl) and the server only allows it, pays it
// and tells everyone around how that player flies (FlightState), so they see the pose, the shell and the trail.
// Flying burns attack stamina (Stark 2026-11-10): more while cruising, a lot with the boost; floating still costs
// nothing and lets it fill back up (after his test the same day: it ran out too fast and he did not know how to get it
// back). Running low the player is "tired": the boost goes out and they glide down to the ground instead of dropping
// (never exhausted by flying, that is for fights). Without a power (no stamina) it burns hunger like before.
// No flying in the challenge arenas (Stark 2026-11-10; Kuririn was born dead when he came in flying).
public final class Flight {
    public static final String TECHNIQUE = "voo";
    private static final int MIN_FOOD = 6;
    // stamina, % of the max per second
    private static final double CRUISE_COST = 0.7;
    private static final double BOOST_COST = 3.5;
    // % paid at once when the boost goes off (the burst)
    private static final double BURST_COST = 2.5;
    // below this % they are tired (glide down, no boost); taking off again needs TAKEOFF_PCT
    private static final double TIRED_PCT = 3.0;
    private static final double TAKEOFF_PCT = 10.0;
    private static final double BOOST_MIN_PCT = 12.0;

    // Minecraft's own flying push (Abilities default)
    private static final float VANILLA_SPEED = 0.05f;

    public static final int FLYING = 1, BOOST = 2, TIRED = 4;
    // only for the flying Dragon Ball souls (boss/AirCombat, universe/SkyPassers): arms crossed, standing up while it
    // moves (Freeza floating by), the right hand waving
    public static final int CROSSED = 8, UPRIGHT = 16, WAVE = 32;
    // the combat flight (combat/AirFight): standing up in the fighting stance, facing the target
    public static final int COMBAT = 64;

    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static final Map<UUID, Boolean> BOOSTING = new HashMap<>();
    private static final Map<UUID, Boolean> TIRED_NOW = new HashMap<>();
    // flying and paying for it this tick (cruising or boosting): no stamina coming back meanwhile
    private static final java.util.Set<UUID> PAYING = new java.util.HashSet<>();

    private Flight() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Flight::onTick);
        NeoForge.EVENT_BUS.addListener(Flight::onStartTracking);
        NeoForge.EVENT_BUS.addListener(Flight::onLogout);
    }

    public static boolean knows(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    // Whether player is flying with the technique right now (not the creative flight).
    public static boolean flying(Player player) {
        return player instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator() && sp.getAbilities().flying && knows(sp);
    }

    // On the boost right now (the souls flying at them read it: AirCombat's clash of fists).
    public static boolean boosting(Player player) {
        return BOOSTING.getOrDefault(player.getUUID(), false) && flying(player);
    }

    // Whether the flight is paying stamina right now (Stamina keeps it from filling back up then).
    public static boolean paying(Player player) {
        return PAYING.contains(player.getUUID()) && flying(player);
    }

    private static boolean inArena(ServerPlayer player) {
        return ChallengeArenas.isArena(player.level().dimension());
    }

    // The client asks for the boost (Ctrl while flying) or lets it go.
    public static void boost(ServerPlayer player, boolean on) {
        if (!on) {
            BOOSTING.remove(player.getUUID());
            return;
        }
        if (!flying(player) || TIRED_NOW.getOrDefault(player.getUUID(), false) || BOOSTING.getOrDefault(player.getUUID(), false)) {
            return;
        }
        if (Stamina.drains(player)) {
            if (pct(player) < BOOST_MIN_PCT) {
                return;
            }
            Stamina.tire(player, BURST_COST, TIRED_PCT);
        }
        BOOSTING.put(player.getUUID(), true);
    }

    private static double pct(ServerPlayer player) {
        return Stamina.get(player) / Math.max(1, Stamina.max(player)) * 100;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || !knows(player)) {
            release(player);
            send(player, 0);
            return;
        }
        // Gaara's sand platform runs its own flight (speed and stamina) while it lasts.
        if (com.starkinhon.battlegroundsmod.power.powers.sub.nr.Gaara.flying(player)) {
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        boolean stamina = Stamina.drains(player);
        boolean arena = inArena(player);
        // Taking off needs some stamina left (or food without a power); never in an arena.
        boolean may = !arena && (abilities.flying || (stamina ? pct(player) >= TAKEOFF_PCT : player.getFoodData().getFoodLevel() > MIN_FOOD));
        if (abilities.mayfly != may) {
            abilities.mayfly = may;
            if (!may && abilities.flying) {
                abilities.flying = false;
            }
            changed = true;
        }
        // The client moves the player itself (FlightClient): Minecraft's own flying push stays at zero.
        if (Math.abs(abilities.getFlyingSpeed()) > 1e-4) {
            abilities.setFlyingSpeed(0);
            changed = true;
        }
        int state = 0;
        if (abilities.flying) {
            player.fallDistance = 0;
            boolean tired = TIRED_NOW.getOrDefault(player.getUUID(), false);
            boolean boosting = BOOSTING.getOrDefault(player.getUUID(), false) && !tired;
            if (stamina) {
                double speed = player.getDeltaMovement().length() * 20;
                double cost = boosting ? BOOST_COST : speed > 3 ? CRUISE_COST : 0;
                if (cost > 0) {
                    PAYING.add(player.getUUID());
                    Stamina.tire(player, cost / 20.0, TIRED_PCT);
                } else {
                    PAYING.remove(player.getUUID());
                }
                if (!tired && pct(player) <= TIRED_PCT + 0.01) {
                    tired = true;
                    player.displayClientMessage(Component.translatable("message.battlegroundsmod.flight_tired_stamina"), true);
                }
            } else {
                player.causeFoodExhaustion(boosting ? 0.06f : 0.025f);
                if (!tired && player.getFoodData().getFoodLevel() <= MIN_FOOD) {
                    tired = true;
                    player.displayClientMessage(Component.translatable("message.battlegroundsmod.flight_tired"), true);
                }
            }
            if (tired) {
                BOOSTING.remove(player.getUUID());
                boosting = false;
            }
            TIRED_NOW.put(player.getUUID(), tired);
            state = FLYING | (boosting ? BOOST : 0) | (tired ? TIRED : 0)
                    | (com.starkinhon.battlegroundsmod.combat.AirFight.active(player) ? COMBAT : 0);
        } else {
            BOOSTING.remove(player.getUUID());
            TIRED_NOW.remove(player.getUUID());
            PAYING.remove(player.getUUID());
        }
        if (changed) {
            player.onUpdateAbilities();
        }
        send(player, state);
    }

    // Hands the flight back to Minecraft (Stark 2026-11-13: after flying with the technique and switching to creative or
    // spectator in the air the player could not move at all). The technique keeps Minecraft's flying push at zero
    // (onTick) and changing the game mode never resets it, so the creative and the spectator flight had no push left.
    // Only a zero push is put back, so a spectator's own speed (mouse wheel) is left alone.
    private static void release(ServerPlayer player) {
        UUID id = player.getUUID();
        BOOSTING.remove(id);
        TIRED_NOW.remove(id);
        PAYING.remove(id);
        Abilities abilities = player.getAbilities();
        if (Math.abs(abilities.getFlyingSpeed()) < 1e-4) {
            abilities.setFlyingSpeed(VANILLA_SPEED);
            player.onUpdateAbilities();
        }
    }

    private static void send(ServerPlayer player, int state) {
        Integer last = SENT.get(player.getUUID());
        if (last != null && last == state || last == null && state == 0) {
            return;
        }
        SENT.put(player.getUUID(), state);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.FlightState(player.getId(), state));
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            Integer state = SENT.get(target.getUUID());
            if (state != null && state != 0) {
                PacketDistributor.sendToPlayer(viewer, new ModPayloads.FlightState(target.getId(), state));
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SENT.remove(id);
        BOOSTING.remove(id);
        TIRED_NOW.remove(id);
        PAYING.remove(id);
    }
}
