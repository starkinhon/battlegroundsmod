package com.starkinhon.battlegroundsmod.power;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.ProgressionSpec;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Climbing the tiers by fighting (plan "Souls: Multiverso e Progressão", decided by Stark 2026-11-10).
// Every defeated enemy gives Poder (called Ki, Chakra, Energia Amaldiçoada... by the soul's universe). A full bar takes the
// player one tier up and starts empty again. Values in souls_skills/padrao.progressao.json (ProgressionSpec):
// - an enemy is worth its own tier's value, so someone stronger pays more; 3+ tiers weaker pays nothing;
// - a SubAlma character pays multSubAlma times its tier; a Divine Soul boss pays the player's whole tier at once;
// - no tier ceiling (Stark 2026-11-10): every soul climbs to Tier 0. A SubAlma and a Divine Soul differ in scale and in
//   the quality of their skills, so a player who fights well with a weak soul is rewarded for it;
// - dying takes perdaMorte of the bar, never a tier.
// /gamerule soulsProgressao false (arena mode) turns all of it off: tiers only change by /soul tier, like before.
// A flat world is the fight/test mode (Stark 2026-11-10): no universes and no progression there either, all free.
public final class Progression {
    public static final GameRules.Key<GameRules.BooleanValue> RULE =
            GameRules.register("soulsProgressao", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));

    // Client side: whether this world runs progression (last SyncProgress). Hides the bar and frees the books when not.
    public static boolean clientOn = true;

    private Progression() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, Progression::onDeath);
    }

    public static boolean on(ServerPlayer player) {
        return on(player.server);
    }

    public static boolean on(MinecraftServer server) {
        return server.getGameRules().getBoolean(RULE) && !flatWorld(server);
    }

    // A superflat world (the overworld's generator): the fight/test mode, with everything free like before.
    public static boolean flatWorld(MinecraftServer server) {
        return server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource;
    }

    // Poder needed to leave this tier (0 at Tier 0: nowhere left to go).
    public static double cost(int tier) {
        List<Double> cost = ProgressionSpec.get().custo();
        tier = Tiers.clamp(tier);
        return tier <= Tiers.DEUS || tier >= cost.size() ? 0 : cost.get(tier);
    }

    // What beating an enemy of this tier is worth.
    public static double value(int tier) {
        List<Double> value = ProgressionSpec.get().valor();
        tier = Tiers.clamp(tier);
        return tier < value.size() ? value.get(tier) : 0;
    }

    // The bar's name for this power's universe (Ki in Dragon Ball, Chakra in Naruto...), "Poder" otherwise.
    public static Component barName(String power) {
        return barName(power, null);
    }

    // Same, falling back to the universe the player lives in when the soul has none.
    public static Component barName(String power, String home) {
        ProgressionSpec spec = ProgressionSpec.get();
        String universe = power == null || spec.universos() == null ? null : spec.universos().get(power);
        if (universe == null && home != null && !home.isEmpty() && !"mundo".equals(home)) {
            universe = home;
        }
        return Component.translatable("progression.battlegroundsmod.bar." + (universe != null ? universe : "poder"));
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof ServerPlayer dead && on(dead) && !com.starkinhon.battlegroundsmod.universe.DragonBallWorld.immortal(dead)) {
            PowerData data = dead.getData(ModAttachments.POWER);
            float before = data.poder;
            data.poder = (float) Math.max(0, data.poder * (1 - ProgressionSpec.get().perdaMorte()));
            // what "Reviver" (Shenlong's wish) gives back
            com.starkinhon.battlegroundsmod.universe.shenlong.ShenlongServer.lostPoder(dead, before - data.poder);
        }
        ServerPlayer killer = killer(event);
        // In the challenge arenas the win pays (ChallengeArenas.arenaPoder), not the kill.
        if (killer == null || killer == victim || !on(killer)
                || com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.isArena(victim.level().dimension())) {
            return;
        }
        Integer victimTier = tierOf(victim);
        if (victimTier == null) {
            return;
        }
        int tier = Tiers.of(killer);
        ProgressionSpec spec = ProgressionSpec.get();
        if (victimTier - tier >= spec.abaixoZera()) {
            return;
        }
        double gain;
        if (victim instanceof SoulBoss boss && boss.isBoss()) {
            gain = cost(tier);
        } else if (victim instanceof SoulBoss mob && Powers.isSub(mob.powerData().power)) {
            gain = value(victimTier) * spec.multSubAlma();
        } else {
            gain = value(victimTier);
        }
        add(killer, gain);
    }

    // Who gets the kill: the player who dealt it (or threw or shot it), else whoever the game credits.
    private static ServerPlayer killer(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player) {
            return player.isSpectator() ? null : player;
        }
        return event.getEntity().getKillCredit() instanceof ServerPlayer player && !player.isSpectator() ? player : null;
    }

    // The tier a defeated enemy counts as, or null when it is worth nothing (animals, villagers...).
    private static Integer tierOf(LivingEntity victim) {
        if (victim instanceof Player || victim instanceof SoulBoss) {
            return Tiers.of(victim);
        }
        if (!(victim instanceof Enemy)) {
            return null;
        }
        List<ProgressionSpec.MobTier> mobs = ProgressionSpec.get().mobsVanilla();
        if (mobs != null) {
            for (ProgressionSpec.MobTier mob : mobs) {
                if (victim.getMaxHealth() <= mob.vidaMax()) {
                    return Tiers.clamp(mob.tier());
                }
            }
        }
        return Tiers.HUMANO;
    }

    // Gives Poder and climbs as many tiers as it pays for.
    public static void add(ServerPlayer player, double amount) {
        if (amount <= 0) {
            return;
        }
        PowerData data = player.getData(ModAttachments.POWER);
        data.poder += (float) amount;
        // the same Poder fills the worn soul's Sincronia (SoulSync)
        SoulSync.gain(player, amount);
        climb(player);
        Component bar = barName(data.power, player.getData(ModAttachments.UNIVERSE));
        double need = cost(data.tier);
        Component line = Component.translatable("message.battlegroundsmod.poder_gain", Math.round(amount), bar,
                Math.round(data.poder), Math.round(need));
        player.displayClientMessage(line, true);
        sync(player);
    }

    // Climbs every tier the bar pays for, all the way to Tier 0.
    public static void climb(ServerPlayer player) {
        PowerData data = player.getData(ModAttachments.POWER);
        boolean rose = false;
        while (data.tier > Tiers.DEUS && data.poder >= cost(data.tier)) {
            data.poder -= (float) cost(data.tier);
            data.tier--;
            rose = true;
        }
        if (rose) {
            Tiers.apply(player);
            player.setHealth(player.getMaxHealth());
            announce(player, data.tier);
        }
        if (data.tier <= Tiers.DEUS) {
            // Nowhere left to climb: the bar stays empty.
            data.poder = 0;
        }
    }

    // The new tier on screen. A first version: the full tier-up scene (aura, cutscene) comes later.
    private static void announce(ServerPlayer player, int tier) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.tier_up", tier)
                .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Tiers.name(tier).copy().withStyle(s -> s.withColor(0xE6E0EE))));
        Vec3 pos = player.position().add(0, 1, 0);
        Fx.burst(player.serverLevel(), ParticleTypes.TOTEM_OF_UNDYING, pos, 80, 0.6, 0.5);
        Fx.burst(player.serverLevel(), ParticleTypes.END_ROD, pos, 40, 0.4, 0.3);
        Fx.sound(player.serverLevel(), pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Fx.sound(player.serverLevel(), pos, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.7f);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncProgress(player.getData(ModAttachments.POWER).poder, on(player),
                player.getData(ModAttachments.UNIVERSE)));
    }

    // /soul poder: shows the bar. /soul poder <quantidade> [jogador]: gives Poder (ops), for testing.
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("poder")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PowerData data = player.getData(ModAttachments.POWER);
                    context.getSource().sendSuccess(() -> Component.translatable("message.battlegroundsmod.poder_is",
                            barName(data.power, player.getData(ModAttachments.UNIVERSE)), Math.round(data.poder), Math.round(cost(data.tier)), data.tier), false);
                    return 1;
                })
                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            add(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "quantidade"));
                            return 1;
                        })
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(context -> {
                                    add(EntityArgument.getPlayer(context, "jogador"), IntegerArgumentType.getInteger(context, "quantidade"));
                                    return 1;
                                })));
    }
}
