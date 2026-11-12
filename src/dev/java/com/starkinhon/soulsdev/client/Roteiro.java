package com.starkinhon.soulsdev.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Roteiro de teste: um .txt com um comando por linha, rodado sozinho pelo cliente. Linhas:
//   # comentário (e linha vazia) - ignorada
//   espera <ticks>                - espera (20 ticks = 1 s de relógio, mesmo com /tick rate lento)
//   sair                          - fecha o jogo (salva o mundo)
//   qualquer outra coisa          - comando, com ou sem "/" (/dev..., /devc..., /soul..., /tp, /tick rate 5...)
// Cada linha vai pro latest.log com [soulsdev] e o tick, pra casar com os quadros gravados.
//
// Onde fica: /devc roteiro <nome> procura <pasta do jogo>/soulsdev/roteiros/<nome>.txt e <repo>/tools/roteiros/<nome>.txt;
// também aceita caminho completo.
// Automático: gradle runClient -Proteiro=<nome ou caminho> roda ao entrar no mundo (veja AutoMundo).
public final class Roteiro {
    private static final int ESPERA_INICIAL = 60;

    private static List<String> linhas = List.of();
    private static int proxima;
    private static int espera;
    private static String nome;
    private static boolean pendenteAuto;

    private Roteiro() {
    }

    public static Path achar(String nomeOuCaminho) {
        Path direto = Path.of(nomeOuCaminho);
        if (direto.isAbsolute() && Files.isRegularFile(direto)) {
            return direto;
        }
        Path pasta = Minecraft.getInstance().gameDirectory.toPath().resolve("soulsdev").resolve("roteiros");
        // No runClient a pasta do jogo é <repo>/run, então os roteiros do repo (tools/roteiros) também valem.
        Path doRepo = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().getParent().resolve("tools").resolve("roteiros");
        for (Path p : List.of(pasta.resolve(nomeOuCaminho + ".txt"), pasta.resolve(nomeOuCaminho), doRepo.resolve(nomeOuCaminho + ".txt"),
                Minecraft.getInstance().gameDirectory.toPath().resolve(nomeOuCaminho))) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    public static String iniciar(String nomeOuCaminho, int esperaAntes) throws IOException {
        Path arquivo = achar(nomeOuCaminho);
        if (arquivo == null) {
            throw new IOException("roteiro não encontrado: " + nomeOuCaminho + " (procurei em soulsdev/roteiros/)");
        }
        linhas = new ArrayList<>(Files.readAllLines(arquivo, StandardCharsets.UTF_8));
        proxima = 0;
        espera = esperaAntes;
        nome = arquivo.getFileName().toString();
        SoulsDev.log("roteiro inicio " + arquivo + " (" + linhas.size() + " linhas)");
        return nome;
    }

    public static String parar() {
        String r = nome == null ? "nenhum roteiro rodando" : "roteiro parado: " + nome + " na linha " + proxima;
        linhas = List.of();
        nome = null;
        Teclas.soltarTodas();
        SoulsDev.log(r);
        return r;
    }

    public static boolean rodando() {
        return nome != null;
    }

    // Roteiro automático (gradle -Proteiro=...): começa quando o jogador entrar no mundo.
    public static void agendarAuto() {
        pendenteAuto = true;
    }

    @SubscribeEvent
    public static void entrou(ClientPlayerNetworkEvent.LoggingIn event) {
        String auto = System.getProperty("soulsdev.roteiro");
        if (pendenteAuto && auto != null && !auto.isBlank()) {
            pendenteAuto = false;
            try {
                iniciar(auto, ESPERA_INICIAL);
            } catch (IOException e) {
                SoulsDev.LOGGER.error("[soulsdev] {}", e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (nome == null || mc.player == null) {
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) {
            // Ainda carregando o mundo.
            return;
        }
        if (espera > 0) {
            espera--;
            return;
        }
        while (nome != null && proxima < linhas.size()) {
            int numero = proxima + 1;
            String linha = linhas.get(proxima++).trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            SoulsDev.log("roteiro " + nome + ":" + numero + " > " + linha);
            if (linha.startsWith("espera ")) {
                try {
                    espera = Integer.parseInt(linha.substring(7).trim());
                } catch (NumberFormatException e) {
                    erro(numero, "espera sem número: " + linha);
                }
                return;
            }
            if (linha.equals("sair")) {
                Gravador.parar();
                SoulsDev.log("roteiro sair: fechando o jogo");
                nome = null;
                mc.stop();
                return;
            }
            String comando = linha.startsWith("/") ? linha.substring(1) : linha;
            try {
                mc.player.connection.sendCommand(comando);
            } catch (RuntimeException e) {
                erro(numero, comando + ": " + e.getMessage());
            }
        }
        if (nome != null) {
            SoulsDev.log("roteiro fim " + nome);
            mc.player.displayClientMessage(Component.literal("[devc] roteiro " + nome + " terminou"), false);
            nome = null;
        }
    }

    private static void erro(int numero, String texto) {
        SoulsDev.LOGGER.warn("[soulsdev] roteiro {}:{} ERRO {}", nome, numero, texto);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[devc] roteiro linha " + numero + ": " + texto), false);
        }
    }
}
