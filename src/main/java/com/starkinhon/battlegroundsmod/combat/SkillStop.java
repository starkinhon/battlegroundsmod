package com.starkinhon.battlegroundsmod.combat;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The sounds and effects of a move belong to whoever made it (Tasks.owner()). When that move is cut off (stunned out of
// it, sealed, the caster died, the Domain shattered) stop() silences what is still playing on every client, drops the
// sounds still scheduled, and fades its effects out, so nothing keeps going after the move itself is gone.
public final class SkillStop {
    // Same reach as the effects (Fx), so every client that got them also gets the stop.
    private static final double REACH_SQR = 448 * 448;
    // Goes up on each stop: a sound scheduled before the stop (SkillSounds) sees it changed and does not play.
    private static final Map<Entity, Integer> EPOCH = new WeakHashMap<>();

    private SkillStop() {
    }

    public static int epoch(Entity owner) {
        return owner == null ? 0 : EPOCH.getOrDefault(owner, 0);
    }

    public static void stop(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        EPOCH.merge(owner, 1, Integer::sum);
        ModPayloads.StopOwnerFx payload = new ModPayloads.StopOwnerFx(owner.getId());
        for (ServerPlayer player : level.players()) {
            if (player == owner || player.distanceToSqr(owner) < REACH_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // A dead fighter's moves are over: their sounds and effects too. A player's move is cut off like a stun would
    // (interrupts++, so the move's own task stops and cleans up, its pose cleared on every client) and the guard drops;
    // otherwise the pose of the move they died in stayed on the body after the respawn (same entity id), until another
    // move replaced it.
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!event.isCanceled() && !entity.level().isClientSide()) {
            com.starkinhon.battlegroundsmod.power.powers.sukuna.Fuga.onDeath(entity, event.getSource());
            if (entity instanceof ServerPlayer) {
                Defense.stop(entity);
                com.starkinhon.battlegroundsmod.power.PowerLogic.interrupt(entity);
            } else {
                stop(entity);
            }
        }
    }

    // Plays sound for owner's move at pos, to everyone who would hear it (vanilla's reach: 16 blocks, more when louder).
    public static void sound(ServerLevel level, Entity owner, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        ResourceLocation id = id(sound);
        double range = sound.getRange(volume);
        ModPayloads.OwnedSound payload = new ModPayloads.OwnedSound(owner.getId(), id, pos, volume, pitch, false, level.getRandom().nextLong());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < range * range) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // For player's ears only, with no distance ("tela" in the skill files).
    public static void screenSound(ServerPlayer player, Entity owner, SoundEvent sound, float volume, float pitch) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.OwnedSound(owner.getId(), id(sound), player.getEyePosition(), volume, pitch, true,
                player.getRandom().nextLong()));
    }

    private static ResourceLocation id(SoundEvent sound) {
        ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        return id != null ? id : sound.getLocation();
    }
}
