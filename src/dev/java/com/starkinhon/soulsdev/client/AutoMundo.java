package com.starkinhon.soulsdev.client;

import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Abre o jogo direto num mundo, sem ninguém clicar (pros testes por roteiro):
//   gradlew runClient -Pmundo=Teste [-PmundoTipo=plano|normal|<preset>] [-Proteiro=gojo]
// Se o mundo não existe, cria: Criativo, cheats ligados, semente 1, plano por padrão. Depois roda o roteiro, se tiver.
// Também desliga a pausa ao perder o foco (senão o jogo pausa sozinho com a janela atrás de outra).
public final class AutoMundo {
    private static boolean feito;

    private AutoMundo() {
    }

    private static String prop(String nome) {
        String v = System.getProperty(nome);
        return v == null || v.isBlank() ? null : v.trim();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (feito) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen) && !(mc.screen instanceof AccessibilityOnboardingScreen)) {
            return;
        }
        feito = true;
        String mundo = prop("soulsdev.mundo");
        String roteiro = prop("soulsdev.roteiro");
        if (mundo == null && roteiro == null) {
            return;
        }
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        if (roteiro != null) {
            Roteiro.agendarAuto();
        }
        if (mundo == null) {
            mundo = "SoulsDev";
        }
        if (mc.getLevelSource().levelExists(mundo)) {
            SoulsDev.log("abrindo mundo " + mundo);
            mc.createWorldOpenFlows().openWorld(mundo, () -> mc.setScreen(new TitleScreen()));
            return;
        }
        String tipo = prop("soulsdev.mundoTipo");
        ResourceLocation id = tipo == null || tipo.equals("plano") ? ResourceLocation.withDefaultNamespace("flat")
                : tipo.equals("normal") ? ResourceLocation.withDefaultNamespace("normal")
                : ResourceLocation.parse(tipo);
        ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, id);
        SoulsDev.log("criando mundo " + mundo + " (" + id + ")");
        LevelSettings settings = new LevelSettings(mundo, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        // estruturas ligadas fora do mundo plano (o teste do tipo de mundo Souls precisa achar fortaleza e minas)
        boolean estruturas = !id.getPath().equals("flat");
        mc.createWorldOpenFlows().createFreshLevel(mundo, settings, new WorldOptions(1L, estruturas, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions(),
                new TitleScreen());
    }
}
