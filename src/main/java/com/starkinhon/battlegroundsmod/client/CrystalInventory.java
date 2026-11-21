// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

// The player inventory in the color of the soul the player is wearing (Stark 2026-11-21, approved in the web preview
// "Interface sem cristais"): no crystals any more. A plate with cut corners in the soul's shades, a double frame with
// L shaped corner pieces, the player in a sunken window with a radial glow, glass slots with a soul colored rim, a glow
// breathing behind, a band of light sweeping the frame, motes rising, little stars blinking and the aura dots circling
// the player. "Escuro" and "Claro" are dark and light versions of the plate; "Minecraft" is vanilla's inventory.
// Slot positions are vanilla's, so every mod that adds buttons or reads the menu keeps working. The plate is built once
// per soul and style into a texture; the moving parts are plain fills each frame. Anything that fails turns the whole
// thing off and the vanilla inventory comes back.
public final class CrystalInventory {
    // Room around the 176x166 vanilla box for the glow behind it.
    private static final int PAD_X = 75;
    private static final int PAD_Y = 60;
    private static final int W = 176 + PAD_X * 2;
    private static final int H = 166 + PAD_Y * 2;
    static final float NO_SOUL_HUE = 265f;

    private static boolean broken;
    private static String builtFor;
    // True while the soul inventory is the one on screen (set every frame by renderBg).
    private static boolean drawing;
    private static int[] ramp;
    private static float hue;
    private static boolean light;
    private static ResourceLocation baseTex;
    private static ResourceLocation glowTex;
    // Lit frame pixels (y << 16 | x, relative to the inventory box) for the sweeping light band.
    private static int[] lit;
    // Frame pixels where the little stars may blink.
    private static int[] spots;

    static final Map<String, Float> SOUL_HUES = new HashMap<>();

    private CrystalInventory() {
    }

    public static boolean active() {
        return drawing && !broken && ramp != null;
    }

    // The player's soul id, or "" without one (Humano). No soul = Minecraft's own inventory and hotbar (Stark 2026-11-19).
    static String soulOf(Player player) {
        com.starkinhon.battlegroundsmod.power.PowerData data = player.getData(ModAttachments.POWER);
        return data.hasPower() ? data.power : "";
    }

    // Text color for the "Crafting" title: light on the dark plate, dark on the light one.
    public static int labelColor() {
        return ramp == null ? 0xFFFFFFFF : light ? ramp[1] : ramp[6];
    }

    public static boolean labelShadow() {
        return !light;
    }

    // Called from InventoryScreen.renderBg (mixin). Returns false to let vanilla draw its own background.
    public static boolean renderBg(GuiGraphics g, int left, int top, int mouseX, int mouseY, AbstractContainerMenu menu) {
        drawing = false;
        int style = FxConfig.inventoryStyle();
        if (broken || style == 2) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return false;
        }
        try {
            String soul = soulOf(player);
            if (soul.isEmpty()) {
                return false;
            }
            String key = soul + "|" + style;
            if (!key.equals(builtFor)) {
                build(soul, style == 1, key);
            }
            draw(g, left, top, mouseX, mouseY, menu, player);
            drawing = true;
            return true;
        } catch (Exception e) {
            broken = true;
            builtFor = null;
            BattlegroundsMod.LOGGER.error("Soul inventory failed, going back to the vanilla one", e);
            return false;
        }
    }

    // ---------------------------------------------------------------- drawing each frame

    private static void draw(GuiGraphics g, int left, int top, int mouseX, int mouseY, AbstractContainerMenu menu, Player player) {
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        int ox = left - PAD_X;
        int oy = top - PAD_Y;
        int[] R = ramp;

        RenderSystem.enableBlend();
        // Glow behind the plate, breathing slowly.
        g.setColor(1, 1, 1, 0.75f + 0.25f * (float) Math.sin(t * 1.3));
        g.blit(glowTex, ox, oy, 0, 0, W, H, W, H);
        g.setColor(1, 1, 1, 1);

        Random rnd = new Random(builtFor.hashCode() * 31L + 7);
        motes(g, left - 60, top - 50, 176 + 120, 166 + 90, t, rnd, light ? R[5] : R[6], 22);

        g.blit(baseTex, ox, oy, 0, 0, W, H, W, H);
        sweep(g, lit, left, top, t, 176, 166, light ? 0xFFFFFFFF : R[6]);

        // Aura dots circling the player, a soft floor under the feet, then the player itself, same place and size as vanilla.
        for (int k = 0; k < 12; k++) {
            double a = t * 1.2 + k * 0.52;
            int x = left + 50 + (int) Math.round(Math.cos(a) * 20);
            int y = top + 46 + (int) Math.round(Math.sin(a) * 32);
            px(g, x, y, argb(R[6], 0.5f + 0.5f * (float) Math.sin(t * 3 + k)));
        }
        g.fill(left + 36, top + 74, left + 64, top + 76, argb(R[5], 0.35f));
        g.fill(left + 40, top + 73, left + 60, top + 74, argb(R[5], 0.35f));
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 26, top + 8, left + 75, top + 78, 30, 0.0625f, mouseX, mouseY, player);

        // Slots: dark (or light) glass with a soul colored rim. The crafting result (slot 0) gets a slightly bigger frame.
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            if (slot.index == 0) {
                slotFrame(g, x - 1, y - 1, 18, R, hue, light);
            } else {
                slotFrame(g, x, y, 16, R, hue, light);
            }
        }
        // Arrow between the crafting grid and the result.
        int arrow = argb(light ? R[2] : R[6], 0.9f);
        g.fill(left + 136, top + 31, left + 146, top + 35, arrow);
        for (int k = 0; k < 5; k++) {
            g.fill(left + 146 + k, top + 28 + k, left + 147 + k, top + 38 - k, arrow);
        }

        stars(g, spots, left, top, t, rnd, R, light, 16);
        RenderSystem.disableBlend();
    }

    // A slot of inner size s at (x, y): soul colored halo, dark edge, glass with a light top/left and a shaded bottom/right.
    static void slotFrame(GuiGraphics g, int x, int y, int s, int[] R, float hue, boolean light) {
        int glass = light ? mix(R[4], 0xA8A8A8, 0.55f) : mix(R[1], R[0], 0.45f);
        g.fill(x - 2, y - 2, x + s + 2, y + s + 2, argb(R[4], light ? 0.45f : 0.55f));
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, argb(light ? R[2] : R[0], 0.95f));
        g.fill(x, y, x + s, y + s, argb(glass, 0.94f));
        int edge = light ? 0xFFFFFFFF : R[4];
        g.fill(x, y, x + s, y + 1, argb(edge, 0.5f));
        g.fill(x, y, x + 1, y + s, argb(edge, 0.35f));
        g.fill(x, y + s - 1, x + s, y + s, 0x40000000);
        g.fill(x + s - 1, y, x + s, y + s, 0x40000000);
    }

    // Three diagonal stripes of light running over the lit frame pixels (packed y << 16 | x, relative to (ox, oy)).
    static void sweep(GuiGraphics g, int[] pixels, int ox, int oy, float t, int w, int h, int color) {
        float pos = ((t * 70) % (w + h + 200)) - 100;
        for (int k = 0; k < 3; k++) {
            int c = argb(color, k == 1 ? 0.55f : 0.25f);
            float lo = pos + k * 5;
            float hi = lo + 5;
            for (int p : pixels) {
                int x = (short) (p & 0xFFFF);
                int y = p >> 16;
                int d = x + y;
                if (d >= lo && d < hi) {
                    g.fill(ox + x, oy + y, ox + x + 1, oy + y + 1, c);
                }
            }
        }
    }

    // Little stars blinking on the frame and the corner pieces.
    static void stars(GuiGraphics g, int[] pixels, int ox, int oy, float t, Random rnd, int[] R, boolean light, int count) {
        if (pixels.length == 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int p = pixels[rnd.nextInt(pixels.length)];
            float ph = rnd.nextFloat() * 6.28f;
            float sp = 0.6f + rnd.nextFloat() * 1.4f;
            boolean big = rnd.nextFloat() < 0.3f;
            double s = Math.sin(t * sp * 2 + ph);
            if (s < 0.2) {
                continue;
            }
            float a = (float) ((s - 0.2) / 0.8);
            int x = ox + (short) (p & 0xFFFF);
            int y = oy + (p >> 16);
            px(g, x, y, argb(light ? R[6] : 0xFFFFFFFF, a));
            int arms = big && a > 0.6f ? 2 : 1;
            int armColor = light ? R[3] : R[6];
            for (int k = 1; k <= arms; k++) {
                int c = argb(armColor, a * (k == 1 ? 0.9f : 0.5f));
                px(g, x + k, y, c);
                px(g, x - k, y, c);
                px(g, x, y + k, c);
                px(g, x, y - k, c);
            }
        }
    }

    // Motes rising in the area (ox, oy, w, h).
    static void motes(GuiGraphics g, int ox, int oy, int w, int h, float t, Random rnd, int color, int count) {
        for (int i = 0; i < count; i++) {
            float mx = rnd.nextFloat() * w;
            float my = rnd.nextFloat() * h;
            float v = 0.08f + rnd.nextFloat() * 0.18f;
            float sw = rnd.nextFloat() * 6.28f;
            int size = rnd.nextFloat() < 0.25f ? 2 : 1;
            float y = (((my - t * v * 60) % h) + h) % h;
            float x = mx + (float) Math.sin(t + sw) * 3;
            float a = Math.min(1, y / h * 1.6f) * (0.5f + 0.5f * (float) Math.sin(t * 2 + sw)) * 0.9f;
            int ix = ox + Math.round(x);
            int iy = oy + Math.round(y);
            g.fill(ix, iy, ix + size, iy + size, argb(color, a));
        }
    }

    private static void px(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
    }

    // ---------------------------------------------------------------- style buttons

    // Width of one style button and the gap between them; the row sits centered under the inventory.
    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_GAP = 4;

    public static List<StyleButton> makeButtons() {
        List<StyleButton> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            list.add(new StyleButton(i));
        }
        return list;
    }

    // Puts the row in the screen's top right corner (Stark 2026-11-19), called every frame. Hidden without a soul: the
    // inventory is Minecraft's own then and the buttons would change nothing.
    public static void placeButtons(List<StyleButton> buttons, int screenWidth) {
        Player player = Minecraft.getInstance().player;
        boolean show = player != null && !soulOf(player).isEmpty();
        int total = BUTTON_W * 3 + BUTTON_GAP * 2;
        int x = screenWidth - total - 6;
        for (StyleButton button : buttons) {
            button.setPosition(x + button.style * (BUTTON_W + BUTTON_GAP), 6);
            button.visible = show;
        }
    }

    // "Escuro", "Claro", "Minecraft": the look of the survival inventory and hotbar, saved in the client config. In
    // the soul's colors while the soul inventory is on, plain gray on Minecraft's default.
    public static final class StyleButton extends net.minecraft.client.gui.components.AbstractButton {
        final int style;

        StyleButton(int style) {
            super(0, 0, BUTTON_W, BUTTON_H, net.minecraft.network.chat.Component.translatable("gui.battlegroundsmod.inventory_style." + style));
            this.style = style;
        }

        @Override
        public void onPress() {
            FxConfig.setInventoryStyle(style);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean picked = FxConfig.inventoryStyle() == style;
            boolean crystal = active() && ramp != null;
            int rim = crystal ? ramp[4] : 0xFFA0A0A0;
            int dark = crystal ? ramp[0] : 0xFF202020;
            int fill = crystal ? (picked ? ramp[2] : mix(ramp[1], ramp[0], 0.45f)) : (picked ? 0xFF6E6E6E : 0xFF3A3A3A);
            int x = getX();
            int y = getY();
            RenderSystem.enableBlend();
            g.fill(x - 1, y - 1, x + width + 1, y + height + 1, argb(rim, picked || isHoveredOrFocused() ? 0.9f : 0.45f));
            g.fill(x, y, x + width, y + height, argb(dark, 0.95f));
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, argb(fill, 0.95f));
            g.fill(x + 1, y + 1, x + width - 1, y + 2, argb(crystal ? ramp[5] : 0xFFFFFFFF, picked ? 0.6f : 0.25f));
            int text = picked ? 0xFFFFFFFF : crystal ? ramp[5] : 0xFFC8C8C8;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + 3, text);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // ---------------------------------------------------------------- building the plate

    // The soul's hue (purple with no soul), read once per soul from its icon.
    static float hueFor(String soul) {
        if (NEUTRAL_SOULS.contains(soul)) {
            return 220;
        }
        if (SoulColors.has(soul)) {
            return SoulColors.hue(soul);
        }
        return soul.isEmpty() ? NO_SOUL_HUE : SOUL_HUES.computeIfAbsent(soul, CrystalInventory::readHue);
    }

    private static void build(String soul, boolean lightStyle, String key) {
        Minecraft mc = Minecraft.getInstance();
        hue = hueFor(soul);
        ramp = rampFor(soul);
        light = lightStyle;
        int[] R = ramp;
        int[] buf = new int[W * H];
        List<Integer> litList = new ArrayList<>();
        List<Integer> spotList = new ArrayList<>();
        plate(buf, W, PAD_X, PAD_Y, 176, 166, 5, R, hue, light, true, litList, spotList);
        playerWindow(buf, W, PAD_X + 26, PAD_Y + 8, 49, 70, R, hue, light);
        lit = toRelative(litList, PAD_X, PAD_Y);
        spots = toRelative(spotList, PAD_X, PAD_Y);

        ResourceLocation baseLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_inventory_base");
        ResourceLocation glowLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/soul_inventory_glow");
        // register() closes the texture it replaces.
        mc.getTextureManager().register(baseLoc, new DynamicTexture(image(buf, W, H)));
        mc.getTextureManager().register(glowLoc, new DynamicTexture(glow(W, H, light ? R[5] : R[4], light ? 0.38f : 0.55f)));
        baseTex = baseLoc;
        glowTex = glowLoc;
        builtFor = key;
    }

    static int[] toRelative(List<Integer> pixels, int dx, int dy) {
        int[] out = new int[pixels.size()];
        for (int i = 0; i < out.length; i++) {
            int p = pixels.get(i);
            int x = (p & 0xFFFF) - dx;
            int y = (p >> 16) - dy;
            out[i] = y << 16 | (x & 0xFFFF);
        }
        return out;
    }

    static NativeImage image(int[] buf, int w, int h) {
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = buf[y * w + x];
                image.setPixelRGBA(x, y, c == 0 ? 0 : abgr(c));
            }
        }
        return image;
    }

    // Soft glow in steps, strongest in the middle of the picture.
    static NativeImage glow(int w, int h, int color, float strength) {
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w * 0.5);
                double dy = (y - h / 2.0) / (h * 0.5);
                double a = Math.max(0, 1 - Math.hypot(dx, dy));
                a = Math.floor(a * a * 12) / 12 * strength;
                image.setPixelRGBA(x, y, a > 0 ? abgr(argb(color, (float) a)) : 0);
            }
        }
        return image;
    }

    // A plate with cut corners at (x0, y0) in a picture buf of width bw: outline, a lit top/left and shaded bottom/right
    // ring with a light stroke in the middle of the top, a banded gradient with a faint diagonal grain, and with ornate an
    // inner frame line plus L shaped corner pieces. Lit frame pixels go to lit, star spots to spots (packed y << 16 | x).
    static void plate(int[] buf, int bw, int x0, int y0, int w, int h, int cut, int[] R, float hue, boolean light, boolean ornate,
            List<Integer> lit, List<Integer> spots) {
        // Taken from the ramp (its darks lean in hue), so a yellow soul's plate is warm brown, not olive green.
        int top = light ? mix(R[6], 0xFFFFFF, 0.25f) : mix(R[1], R[2], 0.12f);
        int bot = light ? mix(R[5], R[6], 0.45f) : mix(R[0], R[1], 0.55f);
        int outline = light ? R[2] : R[0];
        int hi = light ? 0xFFFFFFFF : R[4];
        int lo = light ? R[4] : R[1];
        int inset = light ? mix(bot, R[4], 0.55f) : mix(top, R[3], 0.5f);
        int orn = light ? R[3] : R[5];
        List<Integer> frame = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int corner = Math.min(i, w - 1 - i) + Math.min(j, h - 1 - j);
                if (corner < cut) {
                    continue;
                }
                int depth = Math.min(Math.min(i, j), Math.min(w - 1 - i, h - 1 - j));
                float band = (float) Math.floor(j / (float) h * 6) / 5f;
                int c = mix(top, bot, band);
                if ((i - j + 999) % 7 == 0) {
                    c = mix(c, hi, light ? 0.18f : 0.05f);
                }
                if (depth == 0 || corner == cut) {
                    c = outline;
                } else if (depth == 1 || corner == cut + 1) {
                    boolean isLit = j == 1 || (j != h - 2 && (i == 1 || (i != w - 2 && j < h / 2)));
                    c = isLit ? hi : lo;
                    if (isLit) {
                        frame.add((y0 + j) << 16 | (x0 + i));
                    }
                    if (j == 1 && Math.abs(i - w / 2) < 16) {
                        c = light ? 0xFFFFFFFF : R[6];
                    }
                } else if (ornate && depth == 3 && corner > cut + 4) {
                    c = inset;
                    frame.add((y0 + j) << 16 | (x0 + i));
                }
                put(buf, bw, x0 + i, y0 + j, c);
            }
        }
        if (ornate) {
            int[][] corners = { { 0, 0, 1, 1 }, { w - 1, 0, -1, 1 }, { 0, h - 1, 1, -1 }, { w - 1, h - 1, -1, -1 } };
            for (int[] k : corners) {
                int cx = x0 + k[0];
                int cy = y0 + k[1];
                int dx = k[2];
                int dy = k[3];
                for (int n = 0; n < 9; n++) {
                    put(buf, bw, cx + dx * (3 + n), cy + dy * 3, orn);
                    put(buf, bw, cx + dx * 3, cy + dy * (3 + n), orn);
                    frame.add((cy + dy * 3) << 16 | (cx + dx * (3 + n)));
                }
                put(buf, bw, cx + dx * 4, cy + dy * 4, orn);
                put(buf, bw, cx + dx * 5, cy + dy * 5, orn);
                spots.add((cy + dy * 5) << 16 | (cx + dx * 5));
                spots.add((cy + dy * 3) << 16 | (cx + dx * 11));
                spots.add((cy + dy * 11) << 16 | (cx + dx * 3));
            }
        }
        for (int n = 0; n < frame.size(); n += 9) {
            spots.add(frame.get(n));
        }
        lit.addAll(frame);
    }

    // The window behind the player: sunken frame, radial gradient in the soul's color, a few faint dots.
    private static void playerWindow(int[] buf, int bw, int x0, int y0, int w, int h, int[] R, float hue, boolean light) {
        int inner = light ? R[4] : R[3];
        int outer = light ? R[2] : R[0];
        Random rnd = new Random((long) (hue * 100));
        for (int j = -1; j <= h; j++) {
            for (int i = -1; i <= w; i++) {
                int c;
                if (j == -1 || i == -1 || j == h || i == w) {
                    c = light ? R[2] : R[0];
                } else if (j == 0 || i == 0) {
                    c = light ? R[3] : R[0];
                } else if (j == h - 1 || i == w - 1) {
                    c = light ? 0xFFFFFFFF : R[4];
                } else {
                    double d = Math.min(1, Math.hypot((i - w / 2.0) / (w * 0.75), (j - h * 0.85) / (h * 0.95)));
                    c = mix(inner, outer, (float) (Math.floor(d * 6) / 6));
                    if (rnd.nextFloat() < 0.012f) {
                        c = mix(c, R[6], 0.6f);
                    }
                }
                put(buf, bw, x0 + i, y0 + j, c);
            }
        }
    }

    private static void put(int[] buf, int bw, int x, int y, int c) {
        int bh = buf.length / bw;
        if (x >= 0 && y >= 0 && x < bw && y < bh) {
            buf[y * bw + x] = 0xFF000000 | c;
        }
    }

    // Blend of two colors, k = 0 gives a, 1 gives b.
    static int mix(int a, int b, float k) {
        int r = Math.round((a >> 16 & 0xFF) * (1 - k) + (b >> 16 & 0xFF) * k);
        int g = Math.round((a >> 8 & 0xFF) * (1 - k) + (b >> 8 & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // ---------------------------------------------------------------- colors

    // The soul's hue, from the average of the saturated mid tone pixels of its item icon.
    static float readHue(String soul) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/item/soul_" + soul + ".png");
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(location); NativeImage image = NativeImage.read(in)) {
            long rs = 0;
            long gs = 0;
            long bs = 0;
            int n = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    if ((abgr >>> 24) < 200) {
                        continue;
                    }
                    int r = abgr & 0xFF;
                    int g = abgr >> 8 & 0xFF;
                    int b = abgr >> 16 & 0xFF;
                    float v = Math.max(r, Math.max(g, b)) / 255f;
                    if (v > 0.25f && v < 0.9f) {
                        rs += r;
                        gs += g;
                        bs += b;
                        n++;
                    }
                }
            }
            if (n > 0) {
                return hueOf(rs / (float) n, gs / (float) n, bs / (float) n);
            }
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.warn("Crystal inventory: could not read {}", location);
        }
        return NO_SOUL_HUE;
    }

    private static float hueOf(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return NO_SOUL_HUE;
        }
        float d = max - min;
        float h;
        if (max == r) {
            h = (g - b) / d + (g < b ? 6 : 0);
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        return h * 60;
    }

    // Seven shades, darkest to lightest; darks lean toward blue/purple and lights toward yellow (pixel art hue shift).
    // The soul's shades. "inata" (Técnicas Inatas, no soul of its own) is light gray/white like its techniques panel,
    // not the purple fallback (Stark 2026-11-19).
    static int[] rampFor(String soul) {
        if (NEUTRAL_SOULS.contains(soul)) {
            float h = 220;
            return new int[] { hsl(h, 0.08f, 0.07f), hsl(h, 0.08f, 0.17f), hsl(h, 0.08f, 0.31f), hsl(h, 0.07f, 0.47f), hsl(h, 0.06f, 0.65f),
                    hsl(h, 0.05f, 0.81f), hsl(h, 0.04f, 0.94f) };
        }
        if (SoulColors.has(soul)) {
            return SoulColors.ramp(soul);
        }
        return ramp(hueFor(soul));
    }

    private static final java.util.Set<String> NEUTRAL_SOULS = java.util.Set.of("inata");

    static int[] ramp(float h) {
        float toward = (h > 200 && h < 300) ? 1 : -1;
        return new int[] { hsl(h + 14 * toward, 0.45f, 0.07f), hsl(h + 10 * toward, 0.5f, 0.16f), hsl(h + 6 * toward, 0.5f, 0.27f),
                hsl(h, 0.5f, 0.40f), hsl(h - 6 * toward, 0.55f, 0.56f), hsl(h - 12 * toward, 0.65f, 0.73f), hsl(h - 18 * toward, 0.7f, 0.89f) };
    }

    // HSL to opaque ARGB.
    static int hsl(float h, float s, float l) {
        h = ((h % 360) + 360) % 360;
        float a = s * Math.min(l, 1 - l);
        int r = Math.round(hslF(0, h, l, a) * 255);
        int g = Math.round(hslF(8, h, l, a) * 255);
        int b = Math.round(hslF(4, h, l, a) * 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static float hslF(int n, float h, float l, float a) {
        float k = (n + h / 30) % 12;
        return l - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1));
    }

    static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | (rgb & 0xFFFFFF);
    }

    static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
    }

    static float r(int argb) {
        return (argb >> 16 & 0xFF) / 255f;
    }

    static float gr(int argb) {
        return (argb >> 8 & 0xFF) / 255f;
    }

    static float b(int argb) {
        return (argb & 0xFF) / 255f;
    }
}
