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

    // The Colônia do Jogo do Abate's air (client/fx/ColoniaAirFx), built from noise by tools/sounds/colonia_sounds.py: the
    // wind through the ruins and a burning building's roar (both looped), a far collapse and the far cursed-energy blast.
    public static final Supplier<SoundEvent> COLONIA_VENTO = register("colonia_vento");
    public static final Supplier<SoundEvent> COLONIA_FOGO = register("colonia_fogo");
    public static final Supplier<SoundEvent> COLONIA_DESABAMENTO = register("colonia_desabamento");
    public static final Supplier<SoundEvent> COLONIA_ESTRONDO = register("colonia_estrondo");
    // Shibuya's air and metro (client/fx/ShibuyaAirFx, ShibuyaMetro), built from noise by tools/sounds/shibuya_sounds.py.
    public static final Supplier<SoundEvent> SHIBUYA_CIDADE = register("shibuya_cidade");
    public static final Supplier<SoundEvent> SHIBUYA_NEON = register("shibuya_neon");
    public static final Supplier<SoundEvent> SHIBUYA_ESTALO = register("shibuya_estalo");
    public static final Supplier<SoundEvent> SHIBUYA_VAPOR = register("shibuya_vapor");
    public static final Supplier<SoundEvent> SHIBUYA_CARRO = register("shibuya_carro");
    public static final Supplier<SoundEvent> SHIBUYA_SIRENE = register("shibuya_sirene");
    public static final Supplier<SoundEvent> SHIBUYA_SIRENE_LONGE = register("shibuya_sirene_longe");
    public static final Supplier<SoundEvent> METRO_RONCO = register("metro_ronco");
    public static final Supplier<SoundEvent> METRO_MOTOR = register("metro_motor");
    public static final Supplier<SoundEvent> METRO_FREIO = register("metro_freio");
    public static final Supplier<SoundEvent> METRO_TRILHO = register("metro_trilho");
    public static final Supplier<SoundEvent> METRO_PORTA = register("metro_porta");
    public static final Supplier<SoundEvent> METRO_AVISO = register("metro_aviso");
    public static final Supplier<SoundEvent> METRO_CHEGADA = register("metro_chegada");
    public static final Supplier<SoundEvent> METRO_PINPON = register("metro_pinpon");
    public static final Supplier<SoundEvent> METRO_MELODIA = register("metro_melodia");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name)));
    }
}
