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

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name)));
    }
}
