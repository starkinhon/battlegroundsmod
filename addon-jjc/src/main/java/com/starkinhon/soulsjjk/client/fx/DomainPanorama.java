package com.starkinhon.soulsjjk.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.soulsjjk.SoulsJjk;

import net.minecraft.client.CameraType;
import com.starkinhon.soulsjjk.compat.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;

import org.lwjgl.opengl.GL11;

// The place Sukuna's domain opened in, taken all round (Stark 05:06: the floor of the Santuário reflects the outside,
// upside down). When the domain starts, the next frame renders the world six more times from the viewer's eyes, one
// 90 degree face each (Minecraft's own panorama way: panoramic mode, the player turned to each side), and keeps the
// middle square of each in a 3 x 2 atlas, world axes:
//   0 +z (yaw 0)   1 -x (yaw 90)   2 -z (yaw 180)
//   3 +x (yaw 270) 4 up            5 down
// The shader souls_sd_pano reads it back (SukunaDomainScene.inside). It happens while the wave is on screen, so the
// one heavier frame hides in the wave; with a shader pack on (Iris) it is skipped and the mirror keeps the red sky.
public final class DomainPanorama {
    private static final int MAX_FACE = 512;
    private static final int GL_READ_FRAMEBUFFER = 36008;
    private static final int GL_DRAW_FRAMEBUFFER = 36009;
    private static final float[][] FACES = {{0, 0}, {90, 0}, {180, 0}, {270, 0}, {0, -90}, {0, 90}};

    private static TextureTarget atlas;
    private static int face;
    private static boolean pending;
    private static boolean ready;
    private static boolean capturing;

    private DomainPanorama() {
    }

    // Take it on the next frame.
    public static void request() {
        pending = true;
        ready = false;
    }

    public static void clear() {
        pending = false;
        ready = false;
    }

    public static boolean ready() {
        return ready && atlas != null;
    }

    public static int texture() {
        return atlas.getColorTextureId();
    }

    // Half a texel of one face, in face units (so the faces never bleed into each other).
    public static float texel() {
        return 0.5f / Math.max(1, face);
    }

    // While the six faces render: the mod's world effects stay out of them.
    public static boolean capturing() {
        return capturing;
    }

    public static void onRenderFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !pending) {
            return;
        }
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || FxShaders.flat()) {
            return;
        }
        try {
            capture(mc, player);
            ready = true;
        } catch (RuntimeException e) {
            ready = false;
            SoulsJjk.LOGGER.error("Could not take the panorama for Sukuna's domain mirror", e);
        }
    }

    private static void capture(Minecraft mc, LocalPlayer player) {
        RenderTarget main = mc.getMainRenderTarget();
        int side = Math.min(main.width, main.height);
        face = Math.min(MAX_FACE, side);
        if (atlas == null || atlas.width != face * 3 || atlas.height != face * 2) {
            if (atlas != null) {
                atlas.destroyBuffers();
            }
            atlas = new TextureTarget(face * 3, face * 2, false, Minecraft.ON_OSX);
            atlas.setFilterMode(GL11.GL_LINEAR);
        }
        float yRot = player.getYRot(), xRot = player.getXRot(), yRotO = player.yRotO, xRotO = player.xRotO;
        float yHead = player.yHeadRot, yHeadO = player.yHeadRotO;
        CameraType type = mc.options.getCameraType();
        boolean bob = mc.options.bobView().get();
        boolean hideGui = mc.options.hideGui;
        capturing = true;
        try {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(false);
            mc.options.hideGui = true;
            mc.gameRenderer.setRenderBlockOutline(false);
            mc.gameRenderer.setRenderHand(false);
            mc.gameRenderer.setPanoramicMode(true);
            int x0 = (main.width - side) / 2, y0 = (main.height - side) / 2;
            for (int k = 0; k < 6; k++) {
                player.setYRot(FACES[k][0]);
                player.setXRot(FACES[k][1]);
                player.yRotO = player.getYRot();
                player.xRotO = player.getXRot();
                player.yHeadRot = player.yHeadRotO = player.getYRot();
                main.bindWrite(true);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                mc.gameRenderer.renderLevel(1.0f, net.minecraft.Util.getNanos(), new com.mojang.blaze3d.vertex.PoseStack());
                int col = k % 3, row = k / 3;
                GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, atlas.frameBufferId);
                GlStateManager._glBlitFrameBuffer(x0, y0, x0 + side, y0 + side, col * face, row * face, (col + 1) * face, (row + 1) * face,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }
        } finally {
            capturing = false;
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yRotO = yRotO;
            player.xRotO = xRotO;
            player.yHeadRot = yHead;
            player.yHeadRotO = yHeadO;
            mc.options.setCameraType(type);
            mc.options.bobView().set(bob);
            mc.options.hideGui = hideGui;
            mc.gameRenderer.setPanoramicMode(false);
            mc.gameRenderer.setRenderHand(true);
            mc.gameRenderer.setRenderBlockOutline(true);
            main.bindWrite(true);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
