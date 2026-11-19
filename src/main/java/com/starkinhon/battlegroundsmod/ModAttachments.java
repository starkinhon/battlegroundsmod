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

    // The player already went through the playable introduction (intro/Intro), or skipped it. Saved and kept on death.
    public static final Supplier<AttachmentType<Boolean>> INTRO_DONE = ATTACHMENT_TYPES.register("souls_intro_feita",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    // The real character while the introduction runs (intro/Intro): the soul, Sincronia, inventory and place are lent to
    // the scenes and put back at the end, or at the next login after a crash. Same snapshot as the Dungeon's.
    public static final Supplier<AttachmentType<com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup>> INTRO_BACKUP = ATTACHMENT_TYPES.register("intro_backup",
            () -> AttachmentType.builder(() -> com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup.EMPTY)
                    .serialize(com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup.CODEC).copyOnDeath().build());
    // ... and its game mode (-1 = none saved).
    public static final Supplier<AttachmentType<Integer>> INTRO_GAMEMODE = ATTACHMENT_TYPES.register("intro_gamemode",
            () -> AttachmentType.builder(() -> -1).serialize(Codec.INT).copyOnDeath().build());

    // Ate an Inseto de Alma: hunger bar always full (item/SoulInsectItem). Saved and kept on death.
    // Dedos do Sukuna the player has eaten; the 20th wakes his soul (item/SukunaFingerItem, Stark 2026-11-19).
    public static final Supplier<AttachmentType<Integer>> SUKUNA_FINGERS = ATTACHMENT_TYPES.register("sukuna_fingers",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    public static final Supplier<AttachmentType<Boolean>> SOUL_FED = ATTACHMENT_TYPES.register("soul_fed",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    // The souls this player already stole from a boss (power ids): the first time plays the whole soul ceremony, later
    // ones the short version (boss/SoulCeremony). Saved and kept on death.
    public static final Supplier<AttachmentType<java.util.List<String>>> STOLEN_SOULS = ATTACHMENT_TYPES.register("stolen_souls",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // The characters this player beat in their challenge arenas (challenge/ChallengeArenas): their cards are stamped and
    // they spawn in the world. Saved and kept on death.
    // The player's record in each arena, "id:wins:best seconds" (the Livro dos Desafios' best time and medals, Stark
    // 2026-11-13). Saved and kept on death.
    public static final Supplier<AttachmentType<java.util.List<String>>> ARENA_RECORDS = ATTACHMENT_TYPES.register("arena_records",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    public static final Supplier<AttachmentType<java.util.List<String>>> ARENA_WINS = ATTACHMENT_TYPES.register("arena_wins",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // The universe the player lives in (challenge/Universes), "" before choosing one, and where they last stood in each.
    public static final Supplier<AttachmentType<String>> UNIVERSE = ATTACHMENT_TYPES.register("universe",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());
    public static final Supplier<AttachmentType<java.util.Map<String, net.minecraft.core.BlockPos>>> UNIVERSE_POS = ATTACHMENT_TYPES.register("universe_pos",
            () -> AttachmentType.<java.util.Map<String, net.minecraft.core.BlockPos>>builder(() -> java.util.Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, net.minecraft.core.BlockPos.CODEC)).copyOnDeath().build());

    // Universe techniques the player learned (universe/Flight "voo"...). They are the player's, so they work everywhere.
    public static final Supplier<AttachmentType<java.util.List<String>>> TECHNIQUES = ATTACHMENT_TYPES.register("techniques",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    // Sincronia per soul (power/SoulSync): level, points to spend and the skills awake. Saved and kept on death.
    public static final Supplier<AttachmentType<java.util.Map<String, com.starkinhon.battlegroundsmod.power.SoulSync.Entry>>> SOUL_SYNC = ATTACHMENT_TYPES.register("soul_sync",
            () -> AttachmentType.<java.util.Map<String, com.starkinhon.battlegroundsmod.power.SoulSync.Entry>>builder(() -> java.util.Map.of())
                    .serialize(com.starkinhon.battlegroundsmod.power.SoulSync.MAP_CODEC).copyOnDeath().build());

    // The Dungeon (challenge/Dungeon): the real character while a run is on (inventory, soul, tier, where it was), put back at the end
    // of the run or, after a crash, at the next login. Saved and kept on death.
    public static final Supplier<AttachmentType<com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup>> DUNGEON_BACKUP = ATTACHMENT_TYPES.register("dungeon_backup",
            () -> AttachmentType.builder(() -> com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup.EMPTY)
                    .serialize(com.starkinhon.battlegroundsmod.challenge.Dungeon.Backup.CODEC).copyOnDeath().build());

    // The Dungeon's Modo Caos (power/SkillSwap): the keys of this player that run a skill of ANOTHER soul ("slot:power:slot").
    // Only while a run is on: cleared when it starts and when the real character comes back (Dungeon.putBack). Saved, so a
    // crash in the middle of a run still clears them at the next login with the rest of the run.
    public static final Supplier<AttachmentType<java.util.List<String>>> SKILL_SWAPS = ATTACHMENT_TYPES.register("dungeon_skill_swaps",
            () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    private ModAttachments() {
    }
}
