package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.starkinhon.battlegroundsmod.movement.MovementData;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BattlegroundsMod.MODID);

    // The player's chosen power, energy and ult charge. Kept on death.
    public static final Supplier<AttachmentType<PowerData>> POWER = ATTACHMENT_TYPES.register("power",
            () -> AttachmentType.builder(() -> new PowerData()).serialize(PowerData.CODEC).copyOnDeath().build());

    // Standard moves (dodge, air jump) for any fighter, players and bosses. Not saved.
    public static final Supplier<AttachmentType<MovementData>> MOVEMENT = ATTACHMENT_TYPES.register("movement",
            () -> AttachmentType.builder(() -> new MovementData()).build());

    // The player turned the custom boss bars off (/soul bossbar). Saved and kept on death.
    public static final Supplier<AttachmentType<Boolean>> BOSS_BAR_OFF = ATTACHMENT_TYPES.register("boss_bar_off",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private ModAttachments() {
    }
}
