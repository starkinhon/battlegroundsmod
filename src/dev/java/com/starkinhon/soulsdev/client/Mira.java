package com.starkinhon.soulsdev.client;

import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Mira automática do cliente: vira o jogador pro peito do mob/boss mais perto (64 blocos). "seguir" mantém a mira
// todo tick, pra skill carregada/segurada (/devc tecla z 40) ir teleguiada no boss mesmo se ele andar.
public final class Mira {
    private static boolean seguir;

    private Mira() {
    }

    public static LivingEntity maisPerto() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return null;
        }
        LivingEntity melhor = null;
        double d = 64 * 64;
        for (Entity e : p.clientLevel.entitiesForRendering()) {
            if (e instanceof LivingEntity l && !(e instanceof Player) && l.isAlive() && p.distanceToSqr(e) < d) {
                d = p.distanceToSqr(e);
                melhor = l;
            }
        }
        return melhor;
    }

    public static String mirar() {
        LocalPlayer p = Minecraft.getInstance().player;
        LivingEntity alvo = maisPerto();
        if (p == null || alvo == null) {
            return null;
        }
        Vec3 peito = alvo.position().add(0, alvo.getBbHeight() * 0.6, 0);
        Vec3 d = peito.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
        return alvo.getName().getString();
    }

    public static void seguir(boolean on) {
        seguir = on;
        SoulsDev.log("mira seguir " + on);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        if (seguir) {
            mirar();
        }
    }

    @SubscribeEvent
    public static void saiu(ClientPlayerNetworkEvent.LoggingOut event) {
        seguir = false;
    }
}
