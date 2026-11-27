// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

// /souls panorama [tamanho]: the six faces of a menu panorama (panorama_0..5.png), the same cube the title screen spins.
// Stark, 2026-11-22: he plays, finds a pretty place and shoots it; later the menu draws them (SoulsPanorama).
// Saved to screenshots/souls_panorama/<bioma>_<data>/ so the folder can be copied as is into
// assets/battlegroundsmod/textures/gui/title/souls_panorama/.
// Same faces and turns as vanilla's Minecraft#grabPanoramixScreenshot (FOV 90, no hand, no block outline),
// with three fixes of ours: the camera goes first person with no bobbing for the shot, the world gets a
// second of warm up turning all six ways first, so the chunks behind the player are built before the shot, and the
// faces are drawn into the game's own main target (shrunk to the face's size for the shot, like a window resize).
// Stark 2026-11-27: the first panorama (Floresta da Folha, no shader pack) came out six flat fog-colored squares.
// Vanilla draws into a side TextureTarget, but more than one renderer puts the picture back on the MAIN target in the
// middle of renderLevel (Fabulous graphics, the glowing-entity outline, a shader pack's composite, our own screen
// passes in FxPost), so the side target can end up holding only the fog-colored clear. Drawing into the main target,
// cleared before each face, is the way DomainPanorama (the Santuario's mirror) already takes its six faces.
public final class PanoramaCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    // ticks of warm up (each one renders the six faces off screen, nothing saved)
    private static final int WARMUP_TICKS = 20;

    private static int pendingSize;
    private static int ticksLeft = -1;

    private PanoramaCapture() {
    }

    public static boolean busy() {
        return ticksLeft >= 0;
    }

    // from the command: the shot goes on the next ticks, after the chat has closed
    public static void request(int size) {
        pendingSize = size;
        ticksLeft = WARMUP_TICKS;
    }

    public static void tick(ClientTickEvent.Post event) {
        if (ticksLeft < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getCameraEntity() != mc.player) {
            // left the world or a cutscene took the camera: the shot is dropped
            ticksLeft = -1;
            return;
        }
        boolean save = ticksLeft == 0;
        ticksLeft--;
        File folder = null;
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        try {
            if (save) {
                folder = folder(mc);
            }
            renderFaces(mc, pendingSize, folder, writes);
        } catch (Exception e) {
            LOGGER.error("Panorama do Souls falhou", e);
            ticksLeft = -1;
            mc.player.displayClientMessage(Component.literal("Não deu pra salvar o panorama: " + e.getMessage())
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (save) {
            String path = folder.getAbsolutePath();
            String name = folder.getName();
            CompletableFuture.allOf(writes.toArray(new CompletableFuture[0])).whenComplete((v, err) -> mc.execute(() -> {
                if (mc.player == null) {
                    return;
                }
                if (err != null) {
                    mc.player.displayClientMessage(Component.literal("Não deu pra salvar o panorama: " + err.getMessage())
                            .withStyle(ChatFormatting.RED), false);
                    return;
                }
                Component link = Component.literal(name).withStyle(s -> s.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(path))));
                mc.player.displayClientMessage(Component.literal("Panorama salvo: ").append(link)
                        .append(Component.literal(" (screenshots/souls_panorama)").withStyle(ChatFormatting.GRAY)), false);
            }));
        }
    }

    private static File folder(Minecraft mc) {
        String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(k -> k.location().getPath()).orElse("lugar");
        String base = biome.replaceAll("[^a-z0-9_.-]", "_") + "_" + LocalDateTime.now().format(DATE);
        File root = new File(new File(mc.gameDirectory, "screenshots"), "souls_panorama");
        File folder = new File(root, base);
        for (int i = 2; folder.exists(); i++) {
            folder = new File(root, base + "_" + i);
        }
        if (!folder.mkdirs()) {
            throw new IllegalStateException("não criei a pasta " + folder.getAbsolutePath());
        }
        return folder;
    }

    // Renders the six faces into an off screen target; with a folder, each face is saved (written off the render thread).
    private static void renderFaces(Minecraft mc, int size, File folder, List<CompletableFuture<Void>> writes) {
        LocalPlayer player = mc.player;
        Window window = mc.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        float xRot = player.getXRot();
        float yRot = player.getYRot();
        float xRotO = player.xRotO;
        float yRotO = player.yRotO;
        CameraType camera = mc.options.getCameraType();
        boolean bob = mc.options.bobView().get();
        boolean hideGui = mc.options.hideGui;
        float yHead = player.yHeadRot;
        float yHeadO = player.yHeadRotO;
        RenderTarget target = mc.getMainRenderTarget();
        int targetWidth = target.width;
        int targetHeight = target.height;
        try {
            mc.gameRenderer.setRenderBlockOutline(false);
            mc.gameRenderer.setPanoramicMode(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(false);
            mc.options.hideGui = true;
            mc.gameRenderer.setRenderHand(false);
            window.setWidth(size);
            window.setHeight(size);
            // the same steps as a window resize (Minecraft#resizeDisplay), so the shader pack, the Fabulous chain
            // and the post effects follow the square size too
            target.resize(size, size, Minecraft.ON_OSX);
            mc.gameRenderer.resize(size, size);
            for (int k = 0; k < 6; k++) {
                float yaw = switch (k) {
                    case 1 -> (yRot + 90.0F) % 360.0F;
                    case 2 -> (yRot + 180.0F) % 360.0F;
                    case 3 -> (yRot - 90.0F) % 360.0F;
                    default -> yRot;
                };
                float pitch = k == 4 ? -90.0F : k == 5 ? 90.0F : 0.0F;
                player.setYRot(yaw);
                player.setXRot(pitch);
                player.yRotO = yaw;
                player.xRotO = pitch;
                player.yHeadRot = yaw;
                player.yHeadRotO = yaw;
                // the shot renders twice: the first pass lets the visible sections follow the new direction
                int passes = folder != null ? 2 : 1;
                for (int p = 0; p < passes; p++) {
                    target.bindWrite(true);
                    RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                    mc.gameRenderer.renderLevel(DeltaTracker.ONE);
                }
                if (folder != null) {
                    NativeImage image = Screenshot.takeScreenshot(target);
                    File file = new File(folder, "panorama_" + k + ".png");
                    writes.add(CompletableFuture.runAsync(() -> {
                        try (image) {
                            image.writeToFile(file);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, Util.ioPool()));
                }
            }
        } finally {
            player.setXRot(xRot);
            player.setYRot(yRot);
            player.xRotO = xRotO;
            player.yRotO = yRotO;
            player.yHeadRot = yHead;
            player.yHeadRotO = yHeadO;
            mc.options.setCameraType(camera);
            mc.options.hideGui = hideGui;
            mc.gameRenderer.setRenderHand(true);
            mc.options.bobView().set(bob);
            mc.gameRenderer.setRenderBlockOutline(true);
            mc.gameRenderer.setPanoramicMode(false);
            window.setWidth(width);
            window.setHeight(height);
            target.resize(targetWidth, targetHeight, Minecraft.ON_OSX);
            mc.gameRenderer.resize(targetWidth, targetHeight);
            target.bindWrite(true);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
