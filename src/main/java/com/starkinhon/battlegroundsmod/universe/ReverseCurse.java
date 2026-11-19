package com.starkinhon.battlegroundsmod.universe;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stamina;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// Técnica de Reversão Amaldiçoada (Reverse Cursed Technique), the Jujutsu technique (Stark 2026-11-10), learned from
// Shoko Ieiri. Hold its key (Keybinds.REVERSE, N) while hurt: the cursed energy is multiplied by itself and turns
// positive, healing 1 heart a second while it lasts. It costs attack stamina (Stark 2026-11-19, it used to be hunger and
// a crouch): COST_PCT of the max per heal, and it stops before the bar would empty, so it never exhausts anyone.
// Works in every universe.
public final class ReverseCurse {
    public static final String TECHNIQUE = "reversao";
    private static final int STEP = 10;
    private static final float HEAL = 1f;
    // Percent of the stamina max per heal (every STEP ticks): 5% a second, a full bar heals about 20 hearts.
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
                || !knows(player)) {
            return;
        }
        if (!Stamina.pay(player, COST_PCT)) {
            player.displayClientMessage(Component.translatable("technique.battlegroundsmod.reversao.tired"), true);
            return;
        }
        player.heal(HEAL);
        Vec3 at = player.position().add(0, 1, 0);
        player.serverLevel().sendParticles(POSITIVE, at.x, at.y, at.z, 6, 0.35, 0.5, 0.35, 0);
        if (player.tickCount % 40 == 0) {
            Fx.sound(player.serverLevel(), at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.6f);
        }
    }
}
