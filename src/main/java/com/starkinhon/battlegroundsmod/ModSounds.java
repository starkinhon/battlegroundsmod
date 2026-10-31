package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's own sounds (files in assets/battlegroundsmod/sounds, listed in sounds.json).
// Sounds played at a spot in the world must be mono, or Minecraft won't fade them with distance.
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, BattlegroundsMod.MODID);

    // Madara's fire stream, played at the mouth when the fire comes out.
    public static final Supplier<SoundEvent> KATON = register("katon");
    // Played for everyone in the Mangekyo cutscene when the camera zooms on the face.
    public static final Supplier<SoundEvent> SHARINGAN = register("sharingan");
    // Bills: his voice saying "Hakai." with the subtitle, and the body starting to fall apart under the Hakai.
    public static final Supplier<SoundEvent> HAKAI_VOZ = register("hakai_voz");
    public static final Supplier<SoundEvent> HAKAI_PULVERIZAR = register("hakai_pulverizar");
    // The hakai ball charging over his hand until it is thrown.
    public static final Supplier<SoundEvent> HAKAI_BOLA = register("hakai_bola");
    // The hakaizinho flicked off his finger, and whatever it pulverizes (an attack, or someone it erases).
    public static final Supplier<SoundEvent> HAKAIZINHO = register("hakaizinho");
    public static final Supplier<SoundEvent> HAKAIZINHO_PULVERIZAR = register("hakaizinho_pulverizar");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name)));
    }
}
