// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.item.DragonRadarItem;
import com.starkinhon.battlegroundsmod.universe.DragonBallWorld;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

// The Radar do Dragão's screen (Stark 2026-11-20, prévia forja/radar-scouter/radar.html): while the Radar is in either
// hand, a round green screen with a square grid that runs as you walk, you as the red triangle in the middle (where you
// face is up), the Esferas as yellow blinking dots, an arrow on the rim for one past the zoom, and a beep that comes
// faster as the nearest gets close. The screen is painted pixel by pixel into a 96x96 texture each frame and shown in its
// white case with the steel rim and the button on top; the button sinks when the zoom changes (DragonRadarItem).
public final class DragonRadarHud {
    private static final int S = 96, C = S / 2, R = 46;
    private static final int CASE = 128;
    private static final ResourceLocation SCREEN = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/radar_screen");
    private static final ResourceLocation CASE_TEX = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/radar_case");
    private static DynamicTexture screen;
    private static boolean caseBuilt;
    private static long nextBeep;

    private DragonRadarHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !DragonBallWorld.holdsRadar(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        float pt = delta.getGameTimeDeltaPartialTick(false);
        double px = Mth.lerp(pt, player.xo, player.getX()), pz = Mth.lerp(pt, player.zo, player.getZ());
        float yaw = player.getViewYRot(pt);
        double nearest = paint(px, pz, yaw, now);
        beep(player, nearest, now);
        if (!caseBuilt) {
            buildCase();
        }
        // bottom right, above the hotbar's line; the case is a bit bigger than the screen
        int size = Math.max(64, Math.min(96, graphics.guiHeight() / 4));
        int x = graphics.guiWidth() - size - 8, y = graphics.guiHeight() - size - 30;
        float press = Math.max(0, 1 - (now - DragonRadarItem.clientPressedAt) / 160f);
        int sink = Math.round(press * size / 40f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.blit(CASE_TEX, x, y + sink, size, size, 0, 0, CASE, CASE, CASE, CASE);
        // the button on top sinks back into the case when pressed
        int inset = Math.round(size * 15 / (float) CASE);
        int sw = size - inset * 2;
        graphics.blit(SCREEN, x + inset, y + inset + sink, sw, sw, 0, 0, S, S, S, S);
        // the zoom's three little lights under the screen
        for (int i = 0; i < 3; i++) {
            int lx = x + size / 2 - 7 + i * 5, ly = y + size - Math.max(4, size / 18) + sink;
            graphics.fill(lx, ly, lx + 3, ly + 2, i <= DragonRadarItem.clientZoom ? 0xFF9DFF7A : 0xFF2E4A2E);
        }
    }

    // Paints the screen; returns the distance to the nearest Esfera (MAX_VALUE if none).
    private static double paint(double px, double pz, float yaw, long now) {
        if (screen == null) {
            screen = new DynamicTexture(S, S, true);
            Minecraft.getInstance().getTextureManager().register(SCREEN, screen);
        }
        NativeImage img = screen.getPixels();
        if (img == null) {
            return Double.MAX_VALUE;
        }
        int zoom = DragonRadarItem.ZOOMS[Mth.clamp(DragonRadarItem.clientZoom, 0, DragonRadarItem.ZOOMS.length - 1)];
        double ppb = R / (double) zoom;
        double th = Math.toRadians(yaw);
        double fx = -Math.sin(th), fz = Math.cos(th), rx = -Math.cos(th), rz = -Math.sin(th);
        // the grid: four cells across the zoom, square on the screen, running as the player walks
        double cell = R / 2.0;
        double pr = px * rx + pz * rz, pf = px * fx + pz * fz;
        double ox = Math.floorMod((long) Math.floor(-pr * ppb * 4), (long) (cell * 4)) / 4.0;
        double oy = Math.floorMod((long) Math.floor(pf * ppb * 4), (long) (cell * 4)) / 4.0;
        // the beep's pulse: a light ring opening from the middle
        double pulse = beepPhase(now);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                double dx = x + 0.5 - C, dy = y + 0.5 - C, d = Math.sqrt(dx * dx + dy * dy);
                if (d > R) {
                    img.setPixelRGBA(x, y, 0);
                    continue;
                }
                // light in the upper left, darker to the rim
                double l = 1 - Math.sqrt((dx + 8) * (dx + 8) + (dy + 10) * (dy + 10)) / (R * 1.35);
                int r = (int) (79 + 103 * l), g = (int) (154 + 84 * l), b = (int) (74 + 86 * l);
                boolean line = Math.floorMod((int) Math.floor(x - C - ox), (int) cell) == 0
                        || Math.floorMod((int) Math.floor(y - C - oy), (int) cell) == 0;
                if (line) {
                    r = r * 62 / 100;
                    g = g * 72 / 100;
                    b = b * 62 / 100;
                }
                if (pulse >= 0 && Math.abs(d - (3 + pulse * (R - 3))) < 1.2) {
                    double a = 0.45 * (1 - pulse);
                    r += (int) ((235 - r) * a);
                    g += (int) ((255 - g) * a);
                    b += (int) ((220 - b) * a);
                }
                double v = d / R;
                if (v > 0.72) {
                    double k = 1 - (v - 0.72) / 0.28 * 0.5;
                    r = (int) (r * k);
                    g = (int) (g * k);
                    b = (int) (b * k);
                }
                img.setPixelRGBA(x, y, abgr(255, r, g, b));
            }
        }
        double nearest = Double.MAX_VALUE;
        boolean blink = (now / 385) % 2 == 0, slow = (now / 770) % 2 == 0;
        int[] sig = DragonRadarItem.clientSignals;
        // a reading older than 3 s (out of range of the server, a lag spike) is not shown as if it were fresh
        boolean fresh = now - DragonRadarItem.clientSignalsAt < 3000;
        for (int i = 0; fresh && i + 2 < sig.length; i += 3) {
            double wx = sig[i] + 0.5 - px, wz = sig[i + 1] + 0.5 - pz;
            nearest = Math.min(nearest, Math.sqrt(wx * wx + wz * wz));
            double sx = C + (wx * rx + wz * rz) * ppb, sy = C - (wx * fx + wz * fz) * ppb;
            double ddx = sx - C, ddy = sy - C, dd = Math.sqrt(ddx * ddx + ddy * ddy);
            boolean carried = sig[i + 2] >= 8;
            if (dd <= R - 4) {
                if (blink) {
                    blip(img, (int) sx, (int) sy, carried);
                }
            } else if (slow) {
                arrow(img, ddx / dd, ddy / dd);
            }
        }
        // you: the red triangle, pointing up
        for (int y = -5; y <= 4; y++) {
            int half = (y + 5) * 4 / 9;
            for (int x = -half; x <= half; x++) {
                boolean edge = y == 4 || y == -5 || Math.abs(x) == half;
                img.setPixelRGBA(C + x, C + y, edge ? abgr(255, 58, 12, 8) : abgr(255, 232, 51, 42));
            }
        }
        screen.upload();
        return nearest;
    }

    private static void blip(NativeImage img, int cx, int cy, boolean carried) {
        int fill = carried ? abgr(255, 255, 150, 60) : abgr(255, 255, 210, 58);
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                boolean corner = Math.abs(x) == 2 && Math.abs(y) == 2;
                if (corner) {
                    continue;
                }
                boolean edge = Math.abs(x) == 2 || Math.abs(y) == 2;
                set(img, cx + x, cy + y, edge ? abgr(255, 61, 42, 0) : fill);
            }
        }
        set(img, cx - 1, cy - 1, abgr(255, 255, 246, 200));
    }

    private static void arrow(NativeImage img, double ux, double uy) {
        double ex = C + ux * (R - 6), ey = C + uy * (R - 6);
        // a little filled triangle pointing out
        for (int y = -5; y <= 5; y++) {
            for (int x = -5; x <= 5; x++) {
                double along = x * ux + y * uy, side = Math.abs(-x * uy + y * ux);
                if (along >= -2 && along <= 4 && side <= (4 - along) * 0.55) {
                    set(img, (int) Math.round(ex + x), (int) Math.round(ey + y), abgr(255, 255, 210, 58));
                }
            }
        }
    }

    private static void set(NativeImage img, int x, int y, int c) {
        double dx = x + 0.5 - C, dy = y + 0.5 - C;
        if (x >= 0 && y >= 0 && x < S && y < S && dx * dx + dy * dy <= R * R) {
            img.setPixelRGBA(x, y, c);
        }
    }

    // How often it beeps: every 1.6 s far, down to every 0.32 s at a few blocks.
    private static long beepEvery = 1600;

    private static double beepPhase(long now) {
        long since = now - (nextBeep - beepEvery);
        double ph = since / (double) beepEvery;
        return ph >= 0 && ph < 0.55 ? ph / 0.55 : -1;
    }

    private static void beep(LocalPlayer player, double nearest, long now) {
        beepEvery = nearest < 8 ? 320 : nearest < 64 ? 320 + (long) ((nearest - 8) / 56 * 1100) : 1600;
        if (now >= nextBeep) {
            nextBeep = now + beepEvery;
            if (Minecraft.getInstance().screen == null) {
                player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.25f, nearest < 64 ? 2.0f : 1.7f);
            }
        }
    }

    // The case: white disc lit from the upper left, steel rim around the screen, the button on top. Built once.
    private static void buildCase() {
        NativeImage img = new NativeImage(CASE, CASE, true);
        double c = CASE / 2.0, cy = c + 4;
        for (int y = 0; y < CASE; y++) {
            for (int x = 0; x < CASE; x++) {
                double dx = x + 0.5 - c, dy = y + 0.5 - cy, d = Math.sqrt(dx * dx + dy * dy);
                int col = 0;
                if (d <= 58) {
                    double l = 1 - Math.sqrt((dx + 20) * (dx + 20) + (dy + 24) * (dy + 24)) / 120;
                    int w = (int) Mth.clamp(170 + 85 * l, 150, 255);
                    col = abgr(255, w, w - 2, w - 10);
                    if (d > 56.5) {
                        col = abgr(255, 140, 139, 130);
                    }
                    if (d <= 52 && d > 47) {
                        double s = 1 - (dy + d) / 110;
                        int v = (int) Mth.clamp(70 + 90 * s, 60, 200);
                        col = abgr(255, v, v + 8, v + 20);
                    }
                    if (d <= 47) {
                        col = abgr(255, 24, 70, 34);
                    }
                }
                // the button: stem and cap above the disc
                if (Math.abs(dx) <= 5 && y >= 2 && y <= 10) {
                    int v = Math.abs(dx) > 3.5 ? 160 : 228;
                    col = abgr(255, v, v - 2, v - 8);
                }
                if (Math.abs(dx) <= 9 && y >= 0 && y <= 3) {
                    int v = y == 0 ? 245 : 205;
                    col = abgr(255, v, v - 2, v - 8);
                }
                img.setPixelRGBA(x, y, col);
            }
        }
        Minecraft.getInstance().getTextureManager().register(CASE_TEX, new DynamicTexture(img));
        caseBuilt = true;
    }

    // NativeImage wants ABGR.
    private static int abgr(int a, int r, int g, int b) {
        return (a & 255) << 24 | (Mth.clamp(b, 0, 255)) << 16 | (Mth.clamp(g, 0, 255)) << 8 | Mth.clamp(r, 0, 255);
    }
}
