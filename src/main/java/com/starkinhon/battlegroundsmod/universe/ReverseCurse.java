// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.universe;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModSounds;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stamina;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Técnica de Reversão Amaldiçoada (Reverse Cursed Technique), the Jujutsu technique (Stark 2026-11-10), learned from
// Shoko Ieiri. Hold its key (Keybinds.REVERSE, N) while hurt: the cursed energy is multiplied by itself and turns
// positive, healing HEAL_PCT of the max health per heal while it lasts (Stark 2026-11-19: a share of the max, so it
// weighs the same at every tier; a flat heart a second was a lot at Tier 8 and nothing at Tier 0). It costs attack stamina (Stark 2026-11-19, it used to be hunger and
// a crouch): COST_PCT of the max per heal, and it stops before the bar would empty, so it never exhausts anyone.
// Works in every universe.
public final class ReverseCurse {
    public static final String TECHNIQUE = "reversao";
    private static final int STEP = 10;
    // Percent of the max health per heal (every STEP ticks): 1.5% a second.
    private static final double HEAL_PCT = 0.75;
    // Percent of the stamina max per heal (every STEP ticks): 5% a second, so a full bar (20 s) heals 30% of the life.
    private static final double COST_PCT = 2.5;
    private static final DustParticleOptions POSITIVE = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1f), 0.9f);

    private ReverseCurse() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ReverseCurse::onTick);
    }

    public static boolean knows(ServerPlayer player) {
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()
                || player.tickCount % STEP != 0 || !player.getData(ModAttachments.POWER).reverseHeld || player.getHealth() >= player.getMaxHealth()
                || !knows(player) || !com.starkinhon.battlegroundsmod.world.SoulsRules.reverse(player)) {
            return;
        }
        if (!Stamina.pay(player, COST_PCT)) {
            player.displayClientMessage(Component.translatable("technique.battlegroundsmod.reversao.tired"), true);
            return;
        }
        player.heal((float) (player.getMaxHealth() * HEAL_PCT / 100.0));
        Vec3 at = player.position().add(0, 1, 0);
        player.serverLevel().sendParticles(POSITIVE, at.x, at.y, at.z, 6, 0.35, 0.5, 0.35, 0);
        if (player.tickCount % 40 == 0) {
            Fx.sound(player.serverLevel(), at, ModSounds.REVERSAO.get(), 1.0f, 1.0f);
        }
    }
}
