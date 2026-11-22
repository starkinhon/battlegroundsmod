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
// hand, a round green screen with a square grid, the Esferas as yellow blinking dots, an arrow on the rim for one past
// the zoom, and a beep that comes faster as the nearest gets close.
// Stark 2026-11-22: the map stays still with north up (the little N on top) and YOU walk on it: the red triangle moves
// across the grid and turns where you face. The map's middle is a point of the world (anchor); when you get near the
// rim it glides to center on you again, so the Esferas never fall off the screen while you walk. The screen is painted pixel by pixel into a 96x96 texture each frame and shown in its
// white case with the steel rim and the button on top; the button sinks when the zoom changes (DragonRadarItem).
public final class DragonRadarHud {
    private static final int S = 96, C = S / 2, R = 46;
    private static final int CASE = 128;
    private static final ResourceLocation SCREEN = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/radar_screen");
    private static final ResourceLocation CASE_TEX = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/radar_case");
    private static DynamicTexture screen;
    private static boolean caseBuilt;
    private static long nextBeep;
    // the world point at the map's middle, where it is gliding to, and when the radar was last on screen
    private static double anchorX, anchorZ, targetX, targetZ;
    private static boolean anchored, gliding;
    private static long lastShown, lastFrame;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> anchorDim;
    private static int anchorZoom = -1;

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
        follow(player, px, pz, now);
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

    // Keeps the map's middle still while you walk; glides it back onto you when you get near the rim. A fresh look at the
    // radar (put away for a while, other dimension, a teleport) starts centered on you.
    private static void follow(LocalPlayer player, double px, double pz, long now) {
        int zi = Mth.clamp(DragonRadarItem.clientZoom, 0, DragonRadarItem.ZOOMS.length - 1);
        double zoom = DragonRadarItem.ZOOMS[zi];
        double far = Math.hypot(px - anchorX, pz - anchorZ);
        if (!anchored || now - lastShown > 1500 || anchorDim != player.level().dimension() || far > zoom * 1.5) {
            anchorX = targetX = px;
            anchorZ = targetZ = pz;
            anchored = true;
            gliding = false;
            anchorDim = player.level().dimension();
            anchorZoom = zi;
        }
        lastShown = now;
        double dt = Mth.clamp((now - lastFrame) / 1000.0, 0, 0.1);
        lastFrame = now;
        // past 62% of the way to the rim (or a zoom in that leaves you out of the screen): glide back to center on you,
        // a bit ahead of where you are walking so the map does not need to move again right away
        if (zi != anchorZoom || far > zoom * 0.62) {
            anchorZoom = zi;
            double vx = player.getX() - player.xo, vz = player.getZ() - player.zo, v = Math.hypot(vx, vz);
            double lead = v > 0.02 ? Math.min(zoom * 0.25, v * 20 * 3) / v : 0;
            targetX = px + vx * lead;
            targetZ = pz + vz * lead;
            gliding = true;
        }
        if (gliding) {
            // keeps chasing the target while you walk, done in about a third of a second
            double k = 1 - Math.exp(-dt * 12);
            anchorX += (targetX - anchorX) * k;
            anchorZ += (targetZ - anchorZ) * k;
            if (Math.hypot(targetX - anchorX, targetZ - anchorZ) < zoom * 0.004) {
                anchorX = targetX;
                anchorZ = targetZ;
                gliding = false;
            }
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
        // north up: east to the right, south down (Minecraft's +X and +Z); you are where you stand on the map
        double th = Math.toRadians(yaw);
        double fx = -Math.sin(th), fz = Math.cos(th);
        double mx = C + (px - anchorX) * ppb, my = C + (pz - anchorZ) * ppb;
        // the grid: four cells across the zoom, square on the screen, nailed to the world (moves only with the map)
        int cell = R / 2;
        int ox = (int) Math.floor(anchorX * ppb), oy = (int) Math.floor(anchorZ * ppb);
        // the beep's pulse: a light ring opening from you
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
                boolean line = Math.floorMod(x - C + ox, cell) == 0 || Math.floorMod(y - C + oy, cell) == 0;
                if (line) {
                    r = r * 62 / 100;
                    g = g * 72 / 100;
                    b = b * 62 / 100;
                }
                double pd = Math.hypot(x + 0.5 - mx, y + 0.5 - my);
                if (pulse >= 0 && Math.abs(pd - (3 + pulse * (R - 3))) < 1.2) {
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
            double bx = sig[i] + 0.5, bz = sig[i + 1] + 0.5;
            nearest = Math.min(nearest, Math.hypot(bx - px, bz - pz));
            double sx = C + (bx - anchorX) * ppb, sy = C + (bz - anchorZ) * ppb;
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
        north(img);
        you(img, mx, my, fx, fz);
        screen.upload();
        return nearest;
    }

    // You: the red triangle at your spot on the map, its tip where you face. Tip 6 px ahead, base 4 px behind, 9 px wide,
    // with a dark outline one pixel thick.
    private static void you(NativeImage img, double mx, double my, double fx, double fz) {
        int cx = (int) Math.floor(mx), cy = (int) Math.floor(my);
        for (int y = -7; y <= 7; y++) {
            for (int x = -7; x <= 7; x++) {
                double lx = cx + x + 0.5 - mx, ly = cy + y + 0.5 - my;
                double along = lx * fx + ly * fz, side = Math.abs(lx * fz - ly * fx);
                if (inTriangle(along, side, 0)) {
                    set(img, cx + x, cy + y, inTriangle(along, side, 1) ? abgr(255, 232, 51, 42) : abgr(255, 58, 12, 8));
                }
            }
        }
    }

    // Inside the triangle shrunk by `in` pixels (0 = the whole shape, 1 = without the outline).
    private static boolean inTriangle(double along, double side, double in) {
        double tip = 6 - in * 1.8, base = -4 + in;
        return along <= tip && along >= base && side <= (tip - along) * 0.45;
    }

    // A small dark N on top of the screen: north is up and the map does not turn.
    private static void north(NativeImage img) {
        int x0 = C - 2, y0 = C - R + 3;
        int col = abgr(255, 30, 72, 34);
        for (int y = 0; y < 5; y++) {
            set(img, x0, y0 + y, col);
            set(img, x0 + 4, y0 + y, col);
            set(img, x0 + y, y0 + y, col);
        }
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
