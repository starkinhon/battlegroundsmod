package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * The color of a player's own hand, read from their skin, so a stretched rubber arm (or anything drawn as
 * "their skin") matches whatever skin they use instead of a fixed tan.
 * Render thread only: it reads the skin texture back from the GPU once per skin and keeps the answer.
 */
public final class SkinTone {
    private static final Map<ResourceLocation, Integer> CACHE = new HashMap<>();

    private SkinTone() {
    }

    /** The hand's color (0xRRGGBB) of this entity's skin, or {@code fallback} when it has none or it can't be read. */
    public static int of(Entity e, int fallback) {
        if (!(e instanceof AbstractClientPlayer p)) {
            return fallback;
        }
        ResourceLocation loc = p.getSkin().texture();
        Integer c = CACHE.get(loc);
        if (c == null) {
            c = read(loc);
            if (c == null) {
                // not uploaded yet (still downloading) or unreadable: try again next frame, keep the default meanwhile
                return fallback;
            }
            CACHE.put(loc, c);
        }
        return c;
    }

    private static Integer read(ResourceLocation loc) {
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc, null);
        if (tex == null) {
            return null;
        }
        try {
            RenderSystem.bindTexture(tex.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            // old 64x32 skins have no overlay below; the arm itself is at the same place
            if (w < 64 || h < w / 2) {
                return null;
            }
            try (NativeImage img = new NativeImage(w, h, false)) {
                img.downloadTexture(0, false);
                int s = w / 64;
                boolean overlay = h >= w;
                // the right arm's front (44..46) and outer (40..43) faces, bottom four rows = the hand
                List<int[]> px = new ArrayList<>();
                for (int y = 28; y < 32; y++) {
                    for (int x = 40; x < 47; x++) {
                        int c = pick(img, x * s, y * s, overlay ? (y + 16) * s : -1);
                        if (c != 0) {
                            px.add(new int[] { (c >> 16) & 255, (c >> 8) & 255, c & 255 });
                        }
                    }
                }
                if (px.size() < 4) {
                    return null;
                }
                // median by brightness: a stray outline or highlight pixel doesn't tint the whole arm
                px.sort((a, b) -> Integer.compare(a[0] * 3 + a[1] * 6 + a[2], b[0] * 3 + b[1] * 6 + b[2]));
                int[] m = px.get(px.size() / 2);
                return (m[0] << 16) | (m[1] << 8) | m[2];
            }
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // the overlay (sleeve/glove layer) wins where it is opaque; 0 = transparent
    private static int pick(NativeImage img, int x, int y, int oy) {
        if (oy >= 0 && oy < img.getHeight()) {
            int o = img.getPixelRGBA(x, oy);
            if ((o >>> 24) > 128) {
                return abgr(o);
            }
        }
        int b = img.getPixelRGBA(x, y);
        return (b >>> 24) > 128 ? abgr(b) : 0;
    }

    private static int abgr(int c) {
        return 0xFF000000 | ((c & 255) << 16) | (c & 0xFF00) | ((c >> 16) & 255);
    }
}
