package com.starkinhon.soulsjjk.combat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.power.Tiers;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// /soul debugdano: every blow of the mod that has a player on either end writes in the chat (ops only) and in the log
// where its damage went: what was asked, what the Escudo and Resistencia of the tiers left, which defense of the mod
// cancelled it, or, when the damage event never even came, why Minecraft itself refused it (Criativo, PvP off, team,
// invulnerable). Made for a hit that "just doesn't land" between two players on LAN (Stark, 2026-11-10): one test
// with this on says where the damage goes.
public final class DamageDebug {
    private static boolean on;
    // Filled while one blow goes through LivingEntity.hurt (the server runs one at a time).
    private static boolean tracking;
    private static boolean reached;
    private static float eventIn = -1;
    private static float afterShield = -1;
    private static float eventOut = -1;
    private static String blockedBy;

    private DamageDebug() {
    }

    public static boolean on() {
        return on;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("debugdano").requires(source -> source.hasPermission(2)).executes(context -> {
            on = !on;
            context.getSource().sendSuccess(() -> Component.literal(on
                    ? "Debug de dano LIGADO: cada golpe entre jogadores aparece no chat. /soul debugdano de novo desliga."
                    : "Debug de dano desligado."), true);
            return 1;
        });
    }

    // target.hurt(source, damage), reported when the debug is on.
    public static boolean hurt(LivingEntity attacker, LivingEntity target, DamageSource source, float damage) {
        if (!on || !(attacker instanceof Player || target instanceof Player) || tracking) {
            return target.hurt(source, damage);
        }
        tracking = true;
        reached = false;
        eventIn = afterShield = eventOut = -1;
        blockedBy = null;
        float before = target.getHealth();
        boolean landed;
        try {
            landed = target.hurt(source, damage);
        } finally {
            tracking = false;
        }
        report(attacker, target, damage, before, landed);
        return landed;
    }

    // PowerEvents.onIncomingDamage: the event came, with this amount.
    public static void incoming(float amount) {
        if (tracking) {
            reached = true;
            eventIn = amount;
        }
    }

    // A defense of the mod cancelled the blow.
    public static void blocked(String by) {
        if (tracking && blockedBy == null) {
            blockedBy = by;
        }
    }

    public static void shielded(float amount) {
        if (tracking) {
            afterShield = amount;
        }
    }

    public static void finished(float amount) {
        if (tracking) {
            eventOut = amount;
        }
    }

    private static void report(LivingEntity attacker, LivingEntity target, float asked, float before, boolean landed) {
        StringBuilder line = new StringBuilder();
        line.append(attacker.getName().getString()).append(" (tier ").append(Tiers.of(attacker)).append(") -> ")
                .append(target.getName().getString()).append(" (tier ").append(Tiers.of(target)).append("): pedido ")
                .append(fmt(asked));
        if (!reached) {
            line.append(" | o evento de dano NAO chegou: ").append(vanillaReasons(attacker, target));
        } else {
            line.append(" | evento ").append(fmt(eventIn));
            if (blockedBy != null) {
                line.append(" | CANCELADO por ").append(blockedBy);
            } else {
                if (afterShield >= 0) {
                    line.append(" -> escudo de tier ").append(fmt(afterShield));
                }
                if (eventOut >= 0) {
                    line.append(" -> resistencia ").append(fmt(eventOut));
                }
            }
        }
        line.append(" | vida ").append(fmt(before)).append(" -> ").append(fmt(target.getHealth()))
                .append(landed ? " | acertou" : " | NAO acertou");
        String text = line.toString();
        SoulsJjk.LOGGER.info("[debugdano] {}", text);
        MinecraftServer server = attacker.getServer();
        if (server == null) {
            return;
        }
        Component message = Component.literal("[dano] " + text).withStyle(landed ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendSystemMessage(message);
            }
        }
    }

    // Why LivingEntity/Player.hurt stopped before the event (vanilla checks).
    private static String vanillaReasons(LivingEntity attacker, LivingEntity target) {
        StringBuilder why = new StringBuilder();
        if (target.isDeadOrDying() || target.isRemoved()) {
            why.append("alvo morto/removido; ");
        }
        if (target.isInvulnerable()) {
            why.append("alvo com Invulnerable; ");
        }
        if (target instanceof Player p) {
            if (p.isCreative()) {
                why.append("alvo no Criativo; ");
            }
            if (p.isSpectator()) {
                why.append("alvo no Espectador; ");
            }
            if (p.getAbilities().invulnerable) {
                why.append("alvo com abilities.invulnerable; ");
            }
            if (attacker instanceof Player a) {
                if (p.getServer() != null && !p.getServer().isPvpAllowed()) {
                    why.append("PvP desligado no servidor; ");
                }
                if (!p.canHarmPlayer(a)) {
                    why.append("time sem fogo amigo / PvP; ");
                }
            }
        }
        if (why.length() == 0) {
            why.append("nenhum motivo do Minecraft achado (outro mod ou mixin?)");
        }
        return why.toString();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
