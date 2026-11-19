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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// The player inventory drawn as a crystal cluster in the color of the soul the player is wearing (Stark 2026-11-19,
// approved in the web preview "Teste brisado"): no box, a fan of crystals whose root hides behind the slots, a giant soul
// gem behind the player, dark glass slots with a soul colored rim, a faint soul colored outline around each item (it
// follows the item's own pixels), a band of light sweeping the crystals, motes rising and little stars blinking.
// Slot positions are vanilla's, so every mod that adds buttons or reads the menu keeps working. The crystal picture is
// built once per soul into a texture; the moving parts are plain fills each frame. Anything that fails turns the whole
// thing off and the vanilla inventory comes back.
public final class CrystalInventory {
    // Room around the 176x166 vanilla box for the crystals.
    private static final int PAD_X = 56;
    private static final int PAD_Y = 60;
    private static final int W = 176 + PAD_X * 2;
    private static final int H = 166 + PAD_Y + 44;
    private static final float NO_SOUL_HUE = 265f;

    private static boolean broken;
    private static String builtFor;
    private static int[] ramp;
    private static float hue;
    private static ResourceLocation baseTex;
    private static ResourceLocation glowTex;
    // Crystal pixels as runs per row (x0, x1 pairs), for the sweeping light band.
    private static int[][] runs;
    // Light crystal pixels where the little stars may blink.
    private static int[] brightSpots;

    private static final Map<String, Float> SOUL_HUES = new HashMap<>();
    private static final Map<ResourceLocation, Glow> GLOWS = new HashMap<>();

    private record Glow(ResourceLocation tex, int size, int texels) {
    }

    private CrystalInventory() {
    }

    public static boolean active() {
        return !broken && builtFor != null && FxConfig.inventoryStyle() != 2;
    }

    // Light text color for the "Crafting" title (vanilla's dark gray would vanish on the crystals).
    public static int labelColor() {
        return ramp == null ? 0xFFFFFFFF : ramp[6];
    }

    // Called from InventoryScreen.renderBg (mixin). Returns false to let vanilla draw its own background.
    public static boolean renderBg(GuiGraphics g, int left, int top, int mouseX, int mouseY, AbstractContainerMenu menu) {
        if (broken || FxConfig.inventoryStyle() == 2) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return false;
        }
        try {
            String soul = player.getData(ModAttachments.POWER).power;
            if (soul == null) {
                soul = "";
            }
            if (!soul.equals(builtFor)) {
                build(soul);
            }
            draw(g, left, top, mouseX, mouseY, menu, player);
            return true;
        } catch (Exception e) {
            broken = true;
            builtFor = null;
            BattlegroundsMod.LOGGER.error("Crystal inventory failed, going back to the vanilla one", e);
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
        // Glow behind the cluster, breathing slowly.
        g.setColor(1, 1, 1, 0.75f + 0.25f * (float) Math.sin(t * 1.3));
        g.blit(glowTex, ox, oy, 0, 0, W, H, W, H);
        g.setColor(1, 1, 1, 1);
        g.blit(baseTex, ox, oy, 0, 0, W, H, W, H);

        // Light band sweeping across the crystals: three diagonal stripes, the middle one brighter.
        float pos = ((t * 70) % (W + H + 200)) - 100;
        for (int k = 0; k < 3; k++) {
            int color = argb(R[6], k == 1 ? 0.38f : 0.18f);
            for (int y = 0; y < H; y++) {
                int b0 = (int) Math.floor(pos + k * 5 - y);
                int b1 = b0 + 5;
                int[] row = runs[y];
                for (int i = 0; i < row.length; i += 2) {
                    int x0 = Math.max(row[i], b0);
                    int x1 = Math.min(row[i + 1], b1);
                    if (x0 < x1) {
                        g.fill(ox + x0, oy + y, ox + x1, oy + y + 1, color);
                    }
                }
            }
        }

        // Aura dots circling the player, then the player itself, same place and size as vanilla.
        for (int k = 0; k < 12; k++) {
            double a = t * 1.2 + k * 0.52;
            int x = left + 50 + (int) Math.round(Math.cos(a) * 20);
            int y = top + 46 + (int) Math.round(Math.sin(a) * 32);
            px(g, x, y, argb(R[6], 0.5f + 0.5f * (float) Math.sin(t * 3 + k)));
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 26, top + 8, left + 75, top + 78, 30, 0.0625f, mouseX, mouseY, player);

        // Slots: dark (or light) glass with a soul colored rim. The crafting result (slot 0) gets a bigger frame.
        boolean light = FxConfig.inventoryStyle() == 1;
        int glass = light ? hsl(hue, 0.1f, 0.6f) : hsl(hue, 0.12f, 0.17f);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            if (slot.index == 0) {
                slotFrame(g, x - 2, y - 2, 20, R, glass, light);
            } else {
                slotFrame(g, x, y, 16, R, glass, light);
            }
        }
        // Arrow between the crafting grid and the result.
        int arrow = argb(R[6], 0.9f);
        g.fill(left + 136, top + 31, left + 146, top + 35, arrow);
        for (int k = 0; k < 5; k++) {
            g.fill(left + 146 + k, top + 28 + k, left + 147 + k, top + 38 - k, arrow);
        }

        // Outline glowing around each item, following its pixels.
        int n = 0;
        for (Slot slot : menu.slots) {
            if (!slot.isActive() || !slot.hasItem()) {
                continue;
            }
            Glow glow = glowFor(slot.getItem(), player);
            n++;
            if (glow == null) {
                continue;
            }
            float alpha = 0.7f + 0.25f * (float) Math.sin(t * 2.2 + n * 0.6);
            int off = (glow.size - 16) / 2;
            g.setColor(r(R[5]), gr(R[5]), b(R[5]), alpha);
            g.blit(glow.tex, left + slot.x - off, top + slot.y - off, glow.size, glow.size, 0, 0, glow.texels, glow.texels, glow.texels, glow.texels);
            g.setColor(1, 1, 1, 1);
        }

        // Motes rising.
        Random rnd = new Random(builtFor.hashCode() * 31L + 7);
        for (int i = 0; i < 34; i++) {
            float mx = rnd.nextFloat() * W;
            float my = rnd.nextFloat() * H;
            float v = 0.08f + rnd.nextFloat() * 0.18f;
            float sw = rnd.nextFloat() * 6.28f;
            int size = rnd.nextFloat() < 0.25f ? 2 : 1;
            float y = (((my - t * v * 60) % H) + H) % H;
            float x = mx + (float) Math.sin(t + sw) * 3;
            float a = Math.min(1, y / H * 1.6f) * (0.5f + 0.5f * (float) Math.sin(t * 2 + sw)) * 0.9f;
            g.fill(ox + Math.round(x), oy + Math.round(y), ox + Math.round(x) + size, oy + Math.round(y) + size, argb(R[6], a));
        }

        // Little stars blinking on the light faces.
        if (brightSpots.length > 0) {
            for (int i = 0; i < 18; i++) {
                int p = brightSpots[rnd.nextInt(brightSpots.length)];
                float ph = rnd.nextFloat() * 6.28f;
                float sp = 0.6f + rnd.nextFloat() * 1.4f;
                boolean big = rnd.nextFloat() < 0.3f;
                double s = Math.sin(t * sp * 2 + ph);
                if (s < 0.2) {
                    continue;
                }
                float a = (float) ((s - 0.2) / 0.8);
                int x = ox + (p & 0xFFFF);
                int y = oy + (p >>> 16);
                px(g, x, y, argb(0xFFFFFFFF, a));
                int arms = big && a > 0.6f ? 2 : 1;
                for (int k = 1; k <= arms; k++) {
                    int c = argb(R[6], a * (k == 1 ? 0.9f : 0.5f));
                    px(g, x + k, y, c);
                    px(g, x - k, y, c);
                    px(g, x, y + k, c);
                    px(g, x, y - k, c);
                }
            }
        }
        RenderSystem.disableBlend();
    }

    // A slot of inner size s at (x, y): soul colored halo, dark edge, dark glass with a light top/left and a shaded
    // bottom/right.
    private static void slotFrame(GuiGraphics g, int x, int y, int s, int[] R, int glass, boolean light) {
        g.fill(x - 2, y - 2, x + s + 2, y + s + 2, argb(R[4], 0.55f));
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, argb(R[0], 0.95f));
        g.fill(x, y, x + s, y + s, argb(glass, 0.94f));
        int edge = light ? 0xFFFFFFFF : R[4];
        g.fill(x, y, x + s, y + 1, argb(edge, 0.5f));
        g.fill(x, y, x + 1, y + s, argb(edge, 0.35f));
        g.fill(x, y + s - 1, x + s, y + s, 0x40000000);
        g.fill(x + s - 1, y, x + s, y + s, 0x40000000);
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

    // Puts the row under the inventory (called every frame: the recipe book moves the inventory sideways).
    public static void placeButtons(List<StyleButton> buttons, int left, int top) {
        int total = BUTTON_W * 3 + BUTTON_GAP * 2;
        int x = left + (176 - total) / 2;
        for (StyleButton button : buttons) {
            button.setPosition(x + button.style * (BUTTON_W + BUTTON_GAP), top + 170);
        }
    }

    // "Escuro", "Claro", "Minecraft": the look of the survival inventory, saved in the client config. Crystal styled
    // (soul colors) while the crystal inventory is on, plain gray glass on Minecraft's default.
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
            int fill = crystal ? (picked ? ramp[2] : hsl(hue, 0.12f, 0.17f)) : (picked ? 0xFF6E6E6E : 0xFF3A3A3A);
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

    // ---------------------------------------------------------------- item outline

    // A white mask two pixels around the item's own pixels (1 strong ring, 1 faint ring), read from the item's texture.
    // Only flat items (generated models); blocks and 3D items get none.
    private static Glow glowFor(ItemStack stack, Player player) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, 0);
        if (model == null || model.isGui3d()) {
            return null;
        }
        @SuppressWarnings("deprecation")
        TextureAtlasSprite sprite = model.getParticleIcon();
        if (sprite == null) {
            return null;
        }
        ResourceLocation name = sprite.contents().name();
        if (GLOWS.containsKey(name)) {
            return GLOWS.get(name);
        }
        Glow glow = null;
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
        try (InputStream in = mc.getResourceManager().open(file); NativeImage src = NativeImage.read(in)) {
            int w = src.getWidth();
            int s = w <= 16 ? 16 : 32;
            boolean[] on = new boolean[s * s];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int sx = x * w / s;
                    int sy = y * w / s;
                    if (sy < src.getHeight()) {
                        on[y * s + x] = (src.getPixelRGBA(sx, sy) >>> 24) > 40;
                    }
                }
            }
            int texels = s + 4;
            NativeImage mask = new NativeImage(texels, texels, true);
            for (int y = -2; y < s + 2; y++) {
                for (int x = -2; x < s + 2; x++) {
                    if (at(on, s, x, y)) {
                        continue;
                    }
                    int d = 9;
                    for (int j = -2; j <= 2; j++) {
                        for (int i = -2; i <= 2; i++) {
                            if (at(on, s, x + i, y + j)) {
                                d = Math.min(d, Math.max(Math.abs(i), Math.abs(j)) + (Math.abs(i) + Math.abs(j) > 2 ? 1 : 0));
                            }
                        }
                    }
                    int alpha = d == 1 ? 255 : d == 2 ? 110 : 0;
                    mask.setPixelRGBA(x + 2, y + 2, alpha << 24 | 0xFFFFFF);
                }
            }
            String id = "dyn/item_glow_" + Integer.toHexString(name.hashCode());
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, id);
            mc.getTextureManager().register(tex, new DynamicTexture(mask));
            glow = new Glow(tex, texels * 16 / s, texels);
        } catch (Exception e) {
            // No texture to read (made up models etc.): that item just has no outline.
        }
        GLOWS.put(name, glow);
        return glow;
    }

    private static boolean at(boolean[] on, int s, int x, int y) {
        return x >= 0 && y >= 0 && x < s && y < s && on[y * s + x];
    }

    // ---------------------------------------------------------------- building the crystal picture

    private static void build(String soul) {
        Minecraft mc = Minecraft.getInstance();
        hue = soul.isEmpty() ? NO_SOUL_HUE : SOUL_HUES.computeIfAbsent(soul, CrystalInventory::readHue);
        ramp = ramp(hue);
        int[] R = ramp;
        int[] Rb = { R[0], R[0], R[1], R[1], R[2], R[3], R[4] };
        int[] buf = new int[W * H];
        int ox = PAD_X;
        int oy = PAD_Y;
        double cx = ox + 88;
        double cy = oy + 122;

        // Back fan (darker), from one point hidden behind the slots.
        double[][] fan = { { -74, 120, 14 }, { -58, 170, 18 }, { -40, 196, 20 }, { -22, 214, 20 }, { -6, 226, 22 }, { 10, 220, 20 },
                { 26, 208, 22 }, { 42, 190, 20 }, { 60, 160, 18 }, { 76, 118, 14 } };
        for (int i = 0; i < fan.length; i++) {
            crystal(buf, Rb, cx + fan[i][0] / 6, cy, fan[i][1], fan[i][2], fan[i][0], 0.32, ((i % 3) - 1) * 0.35);
        }
        // Thin pointed ones under the hotbar row.
        double[][] down = { { 104, 112, 12 }, { 124, 96, 11 }, { 146, 70, 9 }, { 166, 58, 8 }, { 194, 60, 8 }, { 214, 72, 9 }, { 236, 98, 11 },
                { 256, 114, 12 } };
        for (int i = 0; i < down.length; i++) {
            crystal(buf, Rb, cx, cy, down[i][1], down[i][2], down[i][0], 0.32, i % 2 == 1 ? 0.3 : -0.3);
        }
        // Front ones (light), smaller.
        double[][] front = { { -66, 128, 12 }, { -48, 150, 14 }, { -30, 120, 12 }, { 30, 150, 14 }, { 50, 170, 15 }, { 68, 124, 12 }, { 14, 96, 10 },
                { -14, 86, 10 } };
        for (int i = 0; i < front.length; i++) {
            crystal(buf, R, cx + front[i][0] / 5, cy - 4, front[i][1], front[i][2], front[i][0], 0.32, i % 2 == 1 ? 0.4 : -0.3);
        }
        // The giant soul gem behind the player.
        gem(buf, R, ox + 51, oy + 98, 116, 30, 0.26);

        NativeImage base = new NativeImage(W, H, true);
        NativeImage glow = new NativeImage(W, H, true);
        runs = new int[H][];
        List<Integer> bright = new ArrayList<>();
        int glowColor = R[5];
        for (int y = 0; y < H; y++) {
            List<Integer> row = new ArrayList<>();
            int start = -1;
            for (int x = 0; x <= W; x++) {
                int c = x < W ? buf[y * W + x] : 0;
                if (c != 0 && start < 0) {
                    start = x;
                } else if (c == 0 && start >= 0) {
                    row.add(start);
                    row.add(x);
                    start = -1;
                }
                if (x == W) {
                    break;
                }
                if (c != 0) {
                    base.setPixelRGBA(x, y, abgr(c));
                    if ((c == R[6] || c == R[5]) && (x * 7 + y * 13) % 5 == 0) {
                        bright.add(y << 16 | x);
                    }
                } else {
                    double dx = (x - W / 2.0) / (W * 0.55);
                    double dy = (y - H * 0.55) / (H * 0.55);
                    double a = Math.max(0, 1 - Math.hypot(dx, dy));
                    a = Math.floor(a * a * a * 12) / 12 * 0.5;
                    if (a > 0) {
                        glow.setPixelRGBA(x, y, abgr(argb(glowColor, (float) a)));
                    }
                }
            }
            runs[y] = row.stream().mapToInt(Integer::intValue).toArray();
        }
        brightSpots = bright.stream().mapToInt(Integer::intValue).toArray();

        ResourceLocation baseLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/crystal_inventory_base");
        ResourceLocation glowLoc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/crystal_inventory_glow");
        // register() closes the texture it replaces.
        mc.getTextureManager().register(baseLoc, new DynamicTexture(base));
        mc.getTextureManager().register(glowLoc, new DynamicTexture(glow));
        baseTex = baseLoc;
        glowTex = glowLoc;
        builtFor = soul;
    }

    // A crystal: base at (x, y), length, half width, angle (0 = up, + = right), tip fraction, apex offset (-1..1).
    private static void crystal(int[] buf, int[] R, double x, double y, double len, double w, double angDeg, double tf, double apexK) {
        double a = Math.toRadians(angDeg);
        double ap = apexK * w;
        double dx = Math.sin(a);
        double dy = -Math.cos(a);
        double nx = -Math.cos(a);
        double ny = -Math.sin(a);
        boolean leftLit = (Math.cos(a) + Math.sin(a)) > -0.2;
        int[] layer = new int[W * H];
        double r = len + w + 2;
        double ts = 1 - tf;
        for (int py = (int) Math.floor(y - r); py <= y + r; py++) {
            for (int px = (int) Math.floor(x - r); px <= x + r; px++) {
                double qx = px + 0.5 - x;
                double qy = py + 0.5 - y;
                double v = qx * dx + qy * dy;
                double u = -(qx * nx + qy * ny);
                if (v < 0 || v > len) {
                    continue;
                }
                double f = v / len;
                double lo = -w;
                double hi = w;
                double ridge = 0;
                if (f > ts) {
                    double k = (f - ts) / tf;
                    lo = -w + (ap + w) * k;
                    hi = w + (ap - w) * k;
                    ridge = ap * k;
                }
                if (u < lo - 0.1 || u > hi + 0.1) {
                    continue;
                }
                boolean left = u < ridge;
                boolean lit = leftLit ? left : !left;
                double span = left ? (ridge - lo) : (hi - ridge);
                double rel = span > 0 ? Math.abs(u - ridge) / span : 0;
                int c;
                if (f > ts) {
                    c = lit ? (rel < 0.5 ? R[6] : R[5]) : (rel < 0.6 ? R[3] : R[2]);
                } else {
                    c = lit ? (rel > 0.7 ? R[4] : (rel > 0.35 && rel < 0.55 && f > 0.12 ? R[6] : R[5])) : (rel > 0.65 ? R[2] : R[3]);
                }
                if (Math.abs(f - ts) < 0.7 / len && !lit) {
                    c = R[2];
                }
                set(layer, px, py, c);
            }
        }
        outlineOver(buf, layer, R[0]);
    }

    // The big faceted gem: four faces, the dark base rising in steps, the light face lit at the top, short diagonal
    // glints, and a three faced tip.
    private static void gem(int[] buf, int[] R, double x, double y, double len, double w, double tf) {
        double ts = 1 - tf;
        int[] layer = new int[W * H];
        int[][] pals = { { R[4], R[3] }, { R[5], R[4] }, { R[4], R[3] }, { R[2], R[1] } };
        double[] edges = { -0.62, -0.12, 0.4 };
        for (int py = (int) Math.floor(y - len); py <= y; py++) {
            for (int px = (int) Math.floor(x - w - 1); px <= x + w + 1; px++) {
                double u = px + 0.5 - x;
                double v = y - (py + 0.5);
                double f = v / len;
                if (v < 0 || v > len) {
                    continue;
                }
                double lo = -w;
                double hi = w;
                if (f > ts) {
                    double k = (f - ts) / tf;
                    lo = -w * (1 - k);
                    hi = w * (1 - k);
                }
                if (u < lo || u > hi) {
                    continue;
                }
                double r = u / w;
                int c;
                if (f <= ts) {
                    int face = r < -0.62 ? 0 : r < -0.12 ? 1 : r < 0.4 ? 2 : 3;
                    int[] pal = pals[face];
                    double g = f / ts;
                    double step = (Math.floorMod((int) Math.floor(px / 2.0), 2)) * 0.025;
                    c = g < 0.24 + face * 0.03 + step ? pal[1] : pal[0];
                    if (face == 1 && g > 0.76 - step) {
                        c = R[6];
                    }
                    if (face == 2 && g > 0.86 + step) {
                        c = R[5];
                    }
                    if (face == 0 && g > 0.9) {
                        c = R[5];
                    }
                    for (int i = 0; i < 3; i++) {
                        if (Math.abs(r - edges[i]) * w < 0.6 && g > 0.12) {
                            c = i == 2 ? R[2] : (i == 0 ? R[4] : R[6]);
                        }
                    }
                    double d = v + u * 1.4;
                    if (face == 1 && g > 0.42 && g < 0.62 && Math.abs(d - len * 0.5) < 1.3) {
                        c = R[6];
                    }
                    if (face == 1 && g > 0.5 && g < 0.58 && Math.abs(d - len * 0.5 - 4) < 0.6) {
                        c = R[6];
                    }
                    if (face == 2 && g > 0.6 && g < 0.7 && Math.abs(v - u * 1.4 - len * 0.68) < 0.6) {
                        c = R[5];
                    }
                } else {
                    double k = (f - ts) / tf;
                    double hw = w * (1 - k);
                    double rr = hw > 0 ? u / hw : 0;
                    c = rr < -0.35 ? R[5] : rr < 0.2 ? (k > 0.5 ? R[6] : R[5]) : R[3];
                    if (rr > 0.65) {
                        c = R[2];
                    }
                    if (Math.abs(rr + 0.35) * hw < 0.6) {
                        c = R[6];
                    }
                    if (Math.abs(rr - 0.2) * hw < 0.6) {
                        c = R[4];
                    }
                    if (Math.abs(f - ts) * len < 0.7) {
                        c = rr < 0.2 ? R[6] : R[3];
                    }
                }
                if (v < 1) {
                    c = R[1];
                }
                set(layer, px, py, c);
            }
        }
        outlineOver(buf, layer, R[0]);
    }

    private static void set(int[] layer, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < W && y < H) {
            layer[y * W + x] = c;
        }
    }

    private static int get(int[] layer, int x, int y) {
        return x >= 0 && y >= 0 && x < W && y < H ? layer[y * W + x] : 0;
    }

    // Dark outline around the layer's shape, then the layer pasted over the picture.
    private static void outlineOver(int[] buf, int[] layer, int outline) {
        List<Integer> pts = new ArrayList<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (layer[y * W + x] == 0
                        && (get(layer, x + 1, y) != 0 || get(layer, x - 1, y) != 0 || get(layer, x, y + 1) != 0 || get(layer, x, y - 1) != 0)) {
                    pts.add(y * W + x);
                }
            }
        }
        for (int p : pts) {
            layer[p] = outline;
        }
        for (int i = 0; i < layer.length; i++) {
            if (layer[i] != 0) {
                buf[i] = layer[i];
            }
        }
    }

    // ---------------------------------------------------------------- colors

    // The soul's hue, from the average of the saturated mid tone pixels of its item icon.
    private static float readHue(String soul) {
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
    private static int[] ramp(float h) {
        float toward = (h > 200 && h < 300) ? 1 : -1;
        return new int[] { hsl(h + 14 * toward, 0.45f, 0.07f), hsl(h + 10 * toward, 0.5f, 0.16f), hsl(h + 6 * toward, 0.5f, 0.27f),
                hsl(h, 0.5f, 0.40f), hsl(h - 6 * toward, 0.55f, 0.56f), hsl(h - 12 * toward, 0.65f, 0.73f), hsl(h - 18 * toward, 0.7f, 0.89f) };
    }

    // HSL to opaque ARGB.
    private static int hsl(float h, float s, float l) {
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

    private static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | (rgb & 0xFFFFFF);
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
    }

    private static float r(int argb) {
        return (argb >> 16 & 0xFF) / 255f;
    }

    private static float gr(int argb) {
        return (argb >> 8 & 0xFF) / 255f;
    }

    private static float b(int argb) {
        return (argb & 0xFF) / 255f;
    }
}
