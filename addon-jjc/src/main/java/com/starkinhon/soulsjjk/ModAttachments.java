package com.starkinhon.soulsjjk;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.starkinhon.soulsjjk.movement.MovementData;
import com.starkinhon.soulsjjk.power.PowerData;

import com.starkinhon.soulsjjk.compat.AttachmentType;

public final class ModAttachments {

    // The player's chosen power, energy and ult charge. Kept on death.
    public static final Supplier<AttachmentType<PowerData>> POWER = com.starkinhon.soulsjjk.compat.Att.register("power",
            () -> AttachmentType.builder(() -> new PowerData()).serialize(PowerData.CODEC).copyOnDeath().build());

    // Standard moves (dodge, air jump) for any fighter, players and bosses. Not saved.
    public static final Supplier<AttachmentType<MovementData>> MOVEMENT = com.starkinhon.soulsjjk.compat.Att.register("movement",
            () -> AttachmentType.builder(() -> new MovementData()).build());

    // The player turned the custom boss bars off (/soul bossbar). Saved and kept on death.
    public static final Supplier<AttachmentType<Boolean>> BOSS_BAR_OFF = com.starkinhon.soulsjjk.compat.Att.register("boss_bar_off",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    // Ate an Inseto de Alma: hunger bar always full (item/SoulInsectItem). Saved and kept on death.
    public static final Supplier<AttachmentType<Boolean>> SOUL_FED = com.starkinhon.soulsjjk.compat.Att.register("soul_fed",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    // The souls this player already stole from a boss (power ids): the first time plays the whole soul ceremony, later
    // ones the short version (boss/SoulCeremony). Saved and kept on death.
    public static final Supplier<AttachmentType<java.util.List<String>>> STOLEN_SOULS = com.starkinhon.soulsjjk.compat.Att.register("stolen_souls",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // The characters this player beat in their challenge arenas (challenge/ChallengeArenas): their cards are stamped and
    // they spawn in the world. Saved and kept on death.
    public static final Supplier<AttachmentType<java.util.List<String>>> ARENA_WINS = com.starkinhon.soulsjjk.compat.Att.register("arena_wins",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // The universe the player lives in (challenge/Universes), "" before choosing one, and where they last stood in each.
    public static final Supplier<AttachmentType<String>> UNIVERSE = com.starkinhon.soulsjjk.compat.Att.register("universe",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());
    public static final Supplier<AttachmentType<java.util.Map<String, net.minecraft.core.BlockPos>>> UNIVERSE_POS = com.starkinhon.soulsjjk.compat.Att.register("universe_pos",
            () -> AttachmentType.<java.util.Map<String, net.minecraft.core.BlockPos>>builder(() -> java.util.Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, net.minecraft.core.BlockPos.CODEC)).copyOnDeath().build());

    // Universe techniques the player learned (universe/Flight "voo"...). They are the player's, so they work everywhere.
    public static final Supplier<AttachmentType<java.util.List<String>>> TECHNIQUES = com.starkinhon.soulsjjk.compat.Att.register("techniques",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    private ModAttachments() {
    }
}
