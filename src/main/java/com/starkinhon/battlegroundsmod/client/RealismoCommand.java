package com.starkinhon.battlegroundsmod.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.starkinhon.battlegroundsmod.client.fx.Realismo;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// /realismo <tornado|fogo|fumaca|explosao|lava|vento> [raio] [altura] [segundos]: puts a realistic volume where you look,
// to test and tune them in game (client only, nobody else sees it). /realismo limpar clears them.
public final class RealismoCommand {
    private static final String[] TYPES = {"tornado", "fogo", "fumaca", "explosao", "lava", "vento"};

    private RealismoCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        RequiredArgumentBuilder<CommandSourceStack, String> type = Commands.argument("tipo", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                .executes(c -> spawn(c, 0, 0, 0));
        type.then(Commands.argument("raio", FloatArgumentType.floatArg(0.5f, 400f))
                .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "raio"), 0, 0))
                .then(Commands.argument("altura", FloatArgumentType.floatArg(0f, 600f))
                        .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "raio"), FloatArgumentType.getFloat(c, "altura"), 0))
                        .then(Commands.argument("segundos", FloatArgumentType.floatArg(0.5f, 600f))
                                .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "raio"), FloatArgumentType.getFloat(c, "altura"),
                                        FloatArgumentType.getFloat(c, "segundos"))))));
        event.getDispatcher().register(Commands.literal("realismo")
                .then(Commands.literal("limpar").executes(c -> {
                    Realismo.clear();
                    c.getSource().sendSystemMessage(Component.literal("Realismo: tudo limpo."));
                    return 1;
                }))
                .then(type));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, float radius, float height, float seconds) {
        String name = StringArgumentType.getString(c, "tipo");
        int type = Realismo.type(name);
        var player = Minecraft.getInstance().player;
        if (type < 0 || player == null) {
            c.getSource().sendSystemMessage(Component.literal("Tipos: tornado, fogo, fumaca, explosao, lava, vento"));
            return 0;
        }
        float[] d = Realismo.defaults(type);
        float r = radius > 0 ? radius : d[0];
        float s = seconds > 0 ? seconds : d[2];
        HitResult hit = player.pick(160, 1, false);
        Vec3 look = player.getLookAngle();
        Vec3 at = hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atBottomCenterOf(((BlockHitResult) hit).getBlockPos().above())
                : player.getEyePosition().add(look.scale(30));
        if (type == Realismo.EXPLOSAO) {
            at = at.add(0, r * 0.5, 0);
        }
        Vec3 flat = new Vec3(look.x, 0, look.z);
        // (wind blows across the view, so it is seen side on; a tornado comes slowly towards you)
        Vec3 dir = type == Realismo.VENTO ? new Vec3(-flat.z, 0, flat.x) : flat.scale(-1);
        float speed = type == Realismo.TORNADO ? 2.5f : 0;
        Realismo.add(type, at, dir, speed, r, height, 1, 0xFFFFFF, Math.round(s * 20), -1, null);
        c.getSource().sendSystemMessage(Component.literal("Realismo: " + Realismo.name(type) + " (raio " + r + ", " + s + " s)"));
        return 1;
    }
}
