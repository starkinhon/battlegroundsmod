package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Gear 4 (Boundman) is drawn with the player's own skin (forja/luffy-g4, approved): a copy of the skin with its outer
 * layer pressed onto the inner one (the Boundman boxes have one layer) and the flame tattoo painted on the trunk and
 * the upper arms, as the page's tattoo(). Read back from the GPU once per skin, render thread only.
 */
public final class BoundmanSkin {
    // u, v, w, h, d of each box in the 64x64 layout (arms: wide; slim arms are 3 wide)
    public static final int[] HEAD = {0, 0, 8, 8, 8}, BODY = {16, 16, 8, 12, 4}, RLEG = {0, 16, 4, 12, 4}, LLEG = {16, 48, 4, 12, 4};

    public record Skin(ResourceLocation tex, boolean slim, boolean legacy) {
        public int[] rarm() {
            return new int[] {40, 16, slim ? 3 : 4, 12, 4};
        }

        public int[] larm() {
            return legacy ? rarm() : new int[] {32, 48, slim ? 3 : 4, 12, 4};
        }

        public int[] lleg() {
            return legacy ? RLEG : LLEG;
        }
    }

    private static final Map<ResourceLocation, Skin> CACHE = new HashMap<>();
    private static final java.util.Set<ResourceLocation> WARNED = new java.util.HashSet<>();
    private static int next;

    private BoundmanSkin() {
    }

    /** The tattooed copy of this player's skin; the skin itself (no tattoo) until it can be read. */
    public static Skin of(AbstractClientPlayer p) {
        PlayerSkin s = p.getSkin();
        boolean slim = s.model() == PlayerSkin.Model.SLIM;
        ResourceLocation loc = s.texture();
        Skin c = CACHE.get(loc);
        if (c == null) {
            c = make(loc, slim);
            if (c == null) {
                return new Skin(loc, slim, false);
            }
            CACHE.put(loc, c);
        }
        return c;
    }

    // the page's boxFaces: [x, y, w, h] of each face in the texture
    public static int[][] faces(int[] b) {
        int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4];
        // top, bottom, right, front, left, back
        return new int[][] {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}};
    }

    private static Skin make(ResourceLocation loc, boolean slim) {
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }
        // (the one-argument getTexture loads it if nothing drew it yet, e.g. he went Boundman before he was ever seen)
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
        if (tex == null) {
            warn(loc, "no texture", null);
            return null;
        }
        try {
            RenderSystem.bindTexture(tex.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w < 64 || h < w / 2) {
                warn(loc, "size " + w + "x" + h, null);
                return null;
            }
            NativeImage img = new NativeImage(w, h, false);
            img.downloadTexture(0, false);
            int s = w / 64;
            boolean legacy = h < w;
            Skin sk = new Skin(null, slim, legacy);
            if (!legacy) {
                // the outer layer onto the inner one, where it is not see-through
                press(img, s, HEAD, 32, 0);
                press(img, s, BODY, 0, 16);
                press(img, s, sk.rarm(), 0, 16);
                press(img, s, RLEG, 0, 16);
                press(img, s, sk.larm(), 16, 0);
                press(img, s, LLEG, -16, 0);
            }
            tattoo(img, s, sk);
            DynamicTexture dyn = new DynamicTexture(img);
            ResourceLocation out = Minecraft.getInstance().getTextureManager().register("battlegroundsmod_boundman_" + next++, dyn);
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.info("Boundman skin made from {} ({}x{}, slim {}, legacy {}) as {}", loc, w, h, slim, legacy, out);
            if (Boolean.getBoolean("souls.boundmanDump")) {
                img.writeToFile(Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve(out.getPath().replace('/', '_') + ".png"));
            }
            return new Skin(out, slim, legacy);
        } catch (RuntimeException | java.io.IOException ex) {
            warn(loc, "failed", ex);
            return null;
        }
    }

    // once per skin: why the tattooed copy could not be made (he is drawn with the plain skin meanwhile)
    private static void warn(ResourceLocation loc, String why, Exception ex) {
        if (WARNED.add(loc)) {
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.warn("Boundman skin from {}: {}", loc, why, ex);
        }
    }

    private static void press(NativeImage img, int s, int[] box, int dx, int dy) {
        for (int[] f : faces(box)) {
            for (int y = f[1] * s; y < (f[1] + f[3]) * s; y++) {
                for (int x = f[0] * s; x < (f[0] + f[2]) * s; x++) {
                    int ox = x + dx * s, oy = y + dy * s;
                    if (ox < 0 || oy < 0 || ox >= img.getWidth() || oy >= img.getHeight()) {
                        continue;
                    }
                    int o = img.getPixelRGBA(ox, oy);
                    if ((o >>> 24) > 128) {
                        img.setPixelRGBA(x, y, o | 0xFF000000);
                    }
                }
            }
        }
    }

    // the page's hash: fract(sin(a*12.9898 + b*78.233) * 43758.5453), positive
    static double hash(double a, double b) {
        double v = (Math.sin(a * 12.9898 + b * 78.233) * 43758.5453) % 1;
        return v < 0 ? v + 1 : v;
    }

    private static void put(NativeImage img, int s, int x, int y, int rgb) {
        int abgr = 0xFF000000 | ((rgb & 255) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 255);
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                img.setPixelRGBA(x * s + i, y * s + j, abgr);
            }
        }
    }

    // the page's tattoo(): tongues down from the shoulders on the trunk, flames up from the elbow on the upper arms
    private static void tattoo(NativeImage img, int s, Skin sk) {
        int[][] body = faces(BODY);
        String[] names = {"top", "bottom", "right", "front", "left", "back"};
        for (int fi : new int[] {3, 5, 4, 2}) {
            int[] f = body[fi];
            String face = names[fi];
            for (int x = 0; x < f[2]; x++) {
                double edge = fi == 3 || fi == 5 ? Math.min(x, f[2] - 1 - x) : 1;
                int L = (int) Math.round(1.5 + 4.5 * Math.pow(1 - edge / 4, 1.4) * (0.6 + 0.6 * hash(x + 3, face.length())));
                for (int y = 0; y < Math.min(f[3], L); y++) {
                    put(img, s, f[0] + x, f[1] + y, y == L - 1 ? 0x6E0A10 : (y + x) % 5 == 0 && y < L - 2 ? 0xE3402F : 0xB3121B);
                }
            }
        }
        String[] arms = {"rarm", "larm"};
        for (int a = 0; a < 2; a++) {
            if (a == 1 && sk.legacy()) {
                break;
            }
            int[][] f = faces(a == 0 ? sk.rarm() : sk.larm());
            for (int fi : new int[] {3, 5, 4, 2}) {
                for (int x = 0; x < f[fi][2]; x++) {
                    int L = (int) Math.round(2 + 3 * hash(x + 7, names[fi].length() + arms[a].length()));
                    for (int k = 0; k < L; k++) {
                        put(img, s, f[fi][0] + x, f[fi][1] + 5 - k, k == L - 1 ? 0x6E0A10 : 0xB3121B);
                    }
                }
            }
        }
    }
}
