package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.power.PowerData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BattlegroundsMod.MODID);

    // The player's chosen power, energy and ult charge. Kept on death.
    public static final Supplier<AttachmentType<PowerData>> POWER = ATTACHMENT_TYPES.register("power",
            () -> AttachmentType.builder(() -> new PowerData()).serialize(PowerData.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }
}
