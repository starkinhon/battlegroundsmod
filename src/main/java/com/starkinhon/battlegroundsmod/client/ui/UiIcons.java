package com.starkinhon.battlegroundsmod.client.ui;

import static com.starkinhon.battlegroundsmod.client.ui.Px.P;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// The rewards and items of the menus: the vanilla inventory slot with the real item, and the pixel icons of what is not an
// item (the preview's drawPowerOrb, drawKiOrb, drawWing, drawMajin, drawRare): Poder, a technique, the flight, Babidi's
// mark and "a rare item".
public final class UiIcons {
    public static final String PODER = "__poder", TECNICA = "__tecnica", VOO = "__voo", MARCA = "__marca", RARO = "__rare";
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");

    private UiIcons() {
    }

    public static boolean special(String id) {
        return id != null && id.startsWith("__");
    }

    // One of the pixel icons, 16 x 16 at x, y.
    public static void icon(String id, float x, float y) {
        switch (id) {
            case PODER -> powerOrb(x, y);
            case TECNICA -> kiOrb(x, y, Px.rgb(120, 200, 255));
            case VOO -> wing(x, y);
            case MARCA -> majin(x, y);
            case RARO -> rare(x, y);
            default -> {
            }
        }
    }

    // An item (16 x 16) on the grid, over what is already drawn.
    public static void item(ItemStack stack, float x, float y, float alpha) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (alpha < 0.99f) {
            RenderSystem.setShaderColor(1, 1, 1, alpha);
        }
        Px.g.renderItem(stack, Math.round(x), Math.round(y));
        if (alpha < 0.99f) {
            Px.g.flush();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    // An item drawn bigger or smaller (size in grid pixels), centered on cx, cy.
    public static void itemAt(ItemStack stack, float cx, float cy, float size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        var pose = Px.g.pose();
        pose.pushPose();
        pose.translate(cx - size / 2, cy - size / 2, 0);
        pose.scale(size / 16f, size / 16f, 1);
        Px.g.renderItem(stack, 0, 0);
        pose.popPose();
    }

    // The count in the corner, the way the game writes it.
    public static void count(int n, float x, float y) {
        if (n <= 1) {
            return;
        }
        Px.g.pose().pushPose();
        Px.g.pose().translate(0, 0, 300);
        Px.Tr(String.valueOf(n), x + 18, y + 9, Px.WHITE, 8, false);
        Px.g.pose().popPose();
    }

    // The vanilla slot (18 x 18) with an item or an icon, its count, and an optional glow/dim.
    public static void slot(float x, float y, String special, ItemStack stack, int n, int glow, boolean dim) {
        Px.g.blitSprite(SLOT, Math.round(x), Math.round(y), 18, 18);
        if (glow >= 0) {
            R(x + 1, y + 1, 16, 16, glow, 0.35f + 0.15f * (float) Math.sin(Px.time * 4));
        }
        if (special(special)) {
            icon(special, x + 1, y + 1);
        } else {
            item(stack, x + 1, y + 1, 1);
        }
        if (dim) {
            Px.g.pose().pushPose();
            Px.g.pose().translate(0, 0, 250);
            R(x + 1, y + 1, 16, 16, Px.rgb(40, 40, 40), 0.62f);
            Px.g.pose().popPose();
        }
        count(n, x, y);
    }

    public static void powerOrb(float x, float y) {
        float t = Px.time * 3;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                float d = (float) Math.hypot(i - 7.5, j - 7.5);
                if (d < 5.5f) {
                    float k = 1 - d / 5.5f;
                    P(x + i, y + j, Px.mix(Px.rgb(60, 160, 20), Px.rgb(220, 255, 120), k + 0.15f * (float) Math.sin(t + i * 0.4f)), 1);
                }
            }
        }
        P(x + 6, y + 5, Px.WHITE, 1);
        P(x + 5, y + 6, Px.rgb(0xef, 0xff, 0xd0), 1);
    }

    public static void kiOrb(float x, float y, int c) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                float d = (float) Math.hypot(i - 7.5, j - 7.5);
                if (d < 7) {
                    float k = 1 - d / 7;
                    float fl = 0.5f + 0.5f * (float) Math.sin(Px.time * 6 + Math.atan2(j - 7.5, i - 7.5) * 3);
                    if (k > 0.25f || fl > 0.6f) {
                        P(x + i, y + j, Px.mix(c, Px.WHITE, k * k * 1.2f), Px.clamp(k * 2.2f, 0, 1));
                    }
                }
            }
        }
    }

    public static void wing(float x, float y) {
        int[][] pts = {{2, 10}, {3, 8}, {4, 6}, {5, 5}, {6, 4}, {8, 3}, {10, 3}, {12, 2}, {13, 3}, {12, 5}, {11, 6}, {13, 6}, {12, 8}, {10, 9},
                {12, 9}, {10, 11}, {8, 12}, {6, 12}, {4, 12}};
        for (int[] a : pts) {
            for (int k = 0; k < 3; k++) {
                P(x + a[0], y + a[1] + k, Px.mix(Px.rgb(230, 240, 255), Px.rgb(150, 180, 230), k / 3f), 1);
            }
        }
        P(x + 12, y + 2, Px.WHITE, 1);
    }

    public static void majin(float x, float y) {
        R(x + 2, y + 2, 12, 12, Px.rgb(0x2a, 0x0a, 0x12), 1);
        int[][] m = {{3, 4}, {3, 5}, {3, 6}, {3, 7}, {3, 8}, {3, 9}, {4, 5}, {5, 6}, {6, 7}, {7, 6}, {8, 5}, {9, 4}, {9, 5}, {9, 6}, {9, 7}, {9, 8},
                {9, 9}, {11, 4}, {11, 5}, {11, 6}, {11, 7}, {11, 8}, {11, 9}};
        for (int[] p : m) {
            P(x + p[0] + 1, y + p[1], Px.rgb(255, 59, 59), 1);
        }
    }

    public static void rare(float x, float y) {
        int[] cs = {Px.rgb(255, 90, 200), Px.rgb(120, 220, 255), Px.rgb(255, 220, 90)};
        int c = cs[(int) Math.floor(Px.time * 2) % 3];
        for (int i = 0; i < 5; i++) {
            P(x + 7, y + 3 + i, c, 1);
            P(x + 3 + i * 2, y + 7, c, 1);
        }
        P(x + 8, y + 8, Px.WHITE, 1);
        Px.T("?", x + 8, y + 5, Px.WHITE, 9, true, 0, true, 1);
    }

    // The soul crystal of a challenger: its soul item when it has one, else a drawn crystal in its color; with a soft
    // pulsing light around (glow) and gray when the challenger is locked.
    public static void crystal(ItemStack soul, int color, float x, float y, float sz, float a, boolean glow, boolean gray, int seed) {
        if (glow) {
            float r = sz * 0.9f, pul = 0.75f + 0.25f * (float) Math.sin(Px.time * 3 + seed);
            for (int i = (int) -r; i <= r; i++) {
                for (int j = (int) -r; j <= r; j++) {
                    float d = (float) Math.hypot(i, j) / r;
                    if (d < 1 && ((i + j) & 1) == 0) {
                        P(x + i, y + j, Px.mix(color, Px.WHITE, 0.35f), (1 - d) * (1 - d) * 0.55f * pul * a);
                    }
                }
            }
        }
        if (soul != null && !soul.isEmpty()) {
            if (gray || a < 0.99f) {
                Px.g.flush();
                float v = gray ? 0.45f : 1;
                RenderSystem.setShaderColor(v, v, v, a);
            }
            itemAt(soul, x, y, sz);
            if (gray || a < 0.99f) {
                Px.g.flush();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            return;
        }
        float h = sz * 0.5f;
        for (int i = (int) -h; i <= h; i++) {
            int w = Math.round((h - Math.abs(i)) * 0.55f);
            R(x - w, y + i, w * 2 + 1, 1, gray ? Px.rgb(90, 86, 100) : Px.mix(color, Px.WHITE, i < 0 ? 0.35f : 0), a);
        }
        P(x - 1, y - h * 0.4f, Px.WHITE, a);
    }
}
