package com.starkinhon.soulsdev.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.platform.NativeImage;
import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

// Prints e gravação quadro a quadro, de dentro do jogo (sem gravador do Windows nem ffmpeg no PC). Cada quadro vira um
// PNG; o vídeo se monta depois com ffmpeg (na nuvem):
// ffmpeg -framerate 30 -pattern_type glob -i 'f*.png' -pix_fmt yuv420p video.mp4
//
// Pasta: -Dsoulsdev.shots=... (gradle -Pshots=...), senão S:/Souls/Claude/_claude_shots se existir (padrão do Stark:
// nunca no C:), senão <pasta do jogo>/soulsdev_shots.
public final class Gravador {
    private static final int MAX_NA_FILA = 24;
    private static final ExecutorService DISCO = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "soulsdev-gravador");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger NA_FILA = new AtomicInteger();

    private static Path pasta;
    private static int cada = 1;
    private static int quadro;
    private static int salvos;
    private static int pulados;
    private static long tick;

    private Gravador() {
    }

    public static Path raiz() {
        String prop = System.getProperty("soulsdev.shots");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        Path stark = Path.of("S:/Souls/Claude/_claude_shots");
        if (Files.isDirectory(stark)) {
            return stark;
        }
        return Minecraft.getInstance().gameDirectory.toPath().resolve("soulsdev_shots");
    }

    public static boolean gravando() {
        return pasta != null;
    }

    public static Path print(String nome) {
        Path arquivo = raiz().resolve(nome.endsWith(".png") ? nome : nome + ".png");
        salvar(arquivo, true);
        SoulsDev.log("print " + arquivo + " tick=" + tick);
        return arquivo;
    }

    public static Path gravar(String nome, int cadaQuadros) {
        parar();
        pasta = raiz().resolve(nome);
        cada = Math.max(1, cadaQuadros);
        quadro = 0;
        salvos = 0;
        pulados = 0;
        SoulsDev.log("gravar inicio " + pasta + " cada=" + cada + " tick=" + tick);
        return pasta;
    }

    public static String parar() {
        if (pasta == null) {
            return "não estava gravando";
        }
        String r = "gravar fim " + pasta + ": " + salvos + " quadros salvos, " + pulados + " pulados (disco lento) tick=" + tick;
        pasta = null;
        SoulsDev.log(r);
        return r;
    }

    private static boolean salvar(Path arquivo, boolean sempre) {
        if (!sempre && NA_FILA.get() >= MAX_NA_FILA) {
            pulados++;
            return false;
        }
        NativeImage imagem = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        NA_FILA.incrementAndGet();
        DISCO.execute(() -> {
            try {
                File pai = arquivo.getParent().toFile();
                pai.mkdirs();
                imagem.writeToFile(arquivo);
            } catch (Exception e) {
                SoulsDev.LOGGER.warn("[soulsdev] não salvou {}", arquivo, e);
            } finally {
                imagem.close();
                NA_FILA.decrementAndGet();
            }
        });
        return true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        tick++;
    }

    // Depois do quadro inteiro (mundo + HUD) e antes de ir pra tela.
    @SubscribeEvent
    public static void quadro(RenderFrameEvent.Post event) {
        if (pasta == null || Minecraft.getInstance().level == null) {
            return;
        }
        if (quadro++ % cada != 0) {
            return;
        }
        // Nome com o número do quadro e o tick do cliente, pra alinhar com o roteiro e o latest.log.
        if (salvar(pasta.resolve(String.format("f%05d_t%06d.png", salvos, tick)), false)) {
            salvos++;
        }
    }
}
