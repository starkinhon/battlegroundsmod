// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
    // The Outono de Kyoto's air (client/fx/KyotoAirFx), built from noise and tones by tools/sounds/kyoto_sounds.py: the
    // leaves in the wind (looped), a far temple bell, crows, the bell cricket at night, bamboo knocking and the shishi-odoshi.
    public static final Supplier<SoundEvent> KYOTO_FOLHAS = register("kyoto_folhas");
    public static final Supplier<SoundEvent> KYOTO_SINO = register("kyoto_sino");
    public static final Supplier<SoundEvent> KYOTO_CORVO = register("kyoto_corvo");
    public static final Supplier<SoundEvent> KYOTO_SUZUMUSHI = register("kyoto_suzumushi");
    public static final Supplier<SoundEvent> KYOTO_BAMBU = register("kyoto_bambu");
    public static final Supplier<SoundEvent> KYOTO_SHISHI = register("kyoto_shishi");
    // The Templos Antigos' air (client/fx/TemploAirFx), built by tools/sounds/templo_sounds.py: the wind in the cedars
    // (looped), the big bell, the doves taking off, the black kite, the bush warbler by day and the owl at night.
    public static final Supplier<SoundEvent> TEMPLO_VENTO = register("templo_vento");
    public static final Supplier<SoundEvent> TEMPLO_SINO = register("templo_sino");
    public static final Supplier<SoundEvent> TEMPLO_ASAS = register("templo_asas");
    public static final Supplier<SoundEvent> TEMPLO_TOBI = register("templo_tobi");
    public static final Supplier<SoundEvent> TEMPLO_UGUISU = register("templo_uguisu");
    public static final Supplier<SoundEvent> TEMPLO_CORUJA = register("templo_coruja");
    public static final Supplier<SoundEvent> HANAMI_FOLHAS = register("hanami_folhas");
    public static final Supplier<SoundEvent> HANAMI_PASSARO = register("hanami_passaro");
    public static final Supplier<SoundEvent> HANAMI_GRILO = register("hanami_grilo");
    public static final Supplier<SoundEvent> HANAMI_CORACAO = register("hanami_coracao");
    public static final Supplier<SoundEvent> ALDEIA_ARROZ = register("aldeia_arroz");
    public static final Supplier<SoundEvent> ALDEIA_SAPO = register("aldeia_sapo");
    public static final Supplier<SoundEvent> ALDEIA_HIGURASHI = register("aldeia_higurashi");
    public static final Supplier<SoundEvent> ALDEIA_BOLHA = register("aldeia_bolha");
    public static final Supplier<SoundEvent> DAGON_VENTO = register("dagon_vento");
    public static final Supplier<SoundEvent> DAGON_ABISMO = register("dagon_abismo");
    public static final Supplier<SoundEvent> DAGON_ONDA = register("dagon_onda");
    public static final Supplier<SoundEvent> JOGO_RONCO = register("jogo_ronco");
    public static final Supplier<SoundEvent> JOGO_CHIADO = register("jogo_chiado");
    public static final Supplier<SoundEvent> JOGO_BORBULHA = register("jogo_borbulha");
    public static final Supplier<SoundEvent> JOGO_PLOP = register("jogo_plop");
    public static final Supplier<SoundEvent> JOGO_BAQUE = register("jogo_baque");
    public static final Supplier<SoundEvent> JOGO_EXPLOSAO = register("jogo_explosao");
    public static final Supplier<SoundEvent> JOGO_TROVAO = register("jogo_trovao");
    public static final Supplier<SoundEvent> FLORESTA_DRONE = register("floresta_drone");
    public static final Supplier<SoundEvent> FLORESTA_RANGIDO = register("floresta_rangido");
    public static final Supplier<SoundEvent> FLORESTA_GOTA = register("floresta_gota");
    public static final Supplier<SoundEvent> FLORESTA_SOPRO = register("floresta_sopro");
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
    // Base combat, made by Stark in the SFX studio (sounds/combate, CC0 or free-to-use banks). Several files in one event =
    // variations, Minecraft picks one each time. Played from souls_skills/padrao.*.json and from Melee, SwordArts, FlightClient
    // and ReverseCurse.
    public static final Supplier<SoundEvent> COMBATE_ERRO = register("combate_erro");
    public static final Supplier<SoundEvent> COMBATE_GOLPE = register("combate_golpe");
    public static final Supplier<SoundEvent> COMBATE_FINAL = register("combate_final");
    public static final Supplier<SoundEvent> COMBATE_CARREGADO = register("combate_carregado");
    public static final Supplier<SoundEvent> COMBATE_AVANCO = register("combate_avanco");
    public static final Supplier<SoundEvent> COMBATE_DASH_ATAQUE = register("combate_dash_ataque");
    public static final Supplier<SoundEvent> COMBATE_ESPADA_AR = register("combate_espada_ar");
    public static final Supplier<SoundEvent> COMBATE_ESPADA_HIT = register("combate_espada_hit");
    public static final Supplier<SoundEvent> COMBATE_PARRY = register("combate_parry");
    public static final Supplier<SoundEvent> COMBATE_QUEBRA = register("combate_quebra");
    public static final Supplier<SoundEvent> COMBATE_ESQUIVA = register("combate_esquiva");
    public static final Supplier<SoundEvent> COMBATE_DESVIO = register("combate_desvio");
    public static final Supplier<SoundEvent> VOO_BOOM = register("voo_boom");
    public static final Supplier<SoundEvent> VOO_ESQUIVA = register("voo_esquiva");
    public static final Supplier<SoundEvent> REVERSAO = register("reversao");
    // The Playful Cloud's combo hits (Stark's Japanese punch sounds): a medium punch for hits 1-4, a big one for the 5th.
    public static final Supplier<SoundEvent> NUVEM_GOLPE = register("nuvem_golpe");
    public static final Supplier<SoundEvent> NUVEM_FINAL = register("nuvem_final");
    // Shigaraki custom SFX (Stark's Skills Definitivas), layered on top of the existing skill sounds.
    public static final Supplier<SoundEvent> SHIGARAKI_Z_PEGA = register("shigaraki_z_pega");
    public static final Supplier<SoundEvent> SHIGARAKI_Z_ERRA = register("shigaraki_z_erra");
    public static final Supplier<SoundEvent> SHIGARAKI_DECAY_BLOCO = register("shigaraki_decay_bloco");
    public static final Supplier<SoundEvent> SHIGARAKI_X_ONDA = register("shigaraki_x_onda");
    public static final Supplier<SoundEvent> SHIGARAKI_C_PLANTAR = register("shigaraki_c_plantar");
    public static final Supplier<SoundEvent> SHIGARAKI_C_PEGAR = register("shigaraki_c_pegar");
    public static final Supplier<SoundEvent> SHIGARAKI_B_ALVO = register("shigaraki_b_alvo");
    public static final Supplier<SoundEvent> SHIGARAKI_B_SUGAR = register("shigaraki_b_sugar");
    public static final Supplier<SoundEvent> SHIGARAKI_B_ROUBADO = register("shigaraki_b_roubado");
    public static final Supplier<SoundEvent> SHIGARAKI_V_TENTACULOS = register("shigaraki_v_tentaculos");
    public static final Supplier<SoundEvent> SHIGARAKI_V_ACERTO = register("shigaraki_v_acerto");
    public static final Supplier<SoundEvent> SHIGARAKI_DECAY_ENTIDADE = register("shigaraki_decay_entidade");
    public static final Supplier<SoundEvent> SHIGARAKI_SHIFTZ_REGEN = register("shigaraki_shiftz_regen");
    public static final Supplier<SoundEvent> SHIGARAKI_SHIFTV_FIM = register("shigaraki_shiftv_fim");
    // Kokushibo (Moon Breathing) custom SFX: base forms 1, 2, 3, 5, 6 and sword stretch (H), mono 44.1 kHz.
    public static final Supplier<SoundEvent> KOKUSHIBO_FORMA1 = register("kokushibo_forma1");
    public static final Supplier<SoundEvent> KOKUSHIBO_FORMA2 = register("kokushibo_forma2");
    public static final Supplier<SoundEvent> KOKUSHIBO_FORMA3 = register("kokushibo_forma3");
    public static final Supplier<SoundEvent> KOKUSHIBO_FORMA5 = register("kokushibo_forma5");
    public static final Supplier<SoundEvent> KOKUSHIBO_FORMA6 = register("kokushibo_forma6");
    public static final Supplier<SoundEvent> KOKUSHIBO_ALONGAR = register("kokushibo_alongar");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name)));
    }
}
