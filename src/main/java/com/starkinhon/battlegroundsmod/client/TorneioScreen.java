// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.challenge.TorneioRoster;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

// The Torneio do Poder's menu (Stark 2026-11-26): every sub-soul, divine soul and boss as a card with its face, click to
// put it in or out; everyone against everyone or teams by universe; all at Tier 0 or each at its own tier; the team the
// players fight for. Start sends it to the server (challenge/TorneioMatch). The colours are the arena's painted stone
// (cream, peach, teal and the brown line) on the dark of space. Drawn on the screens' pixel grid (ui/Px).
public class TorneioScreen extends Screen {
    private static final int CREAM = 0xE8DCC4, PEACH = 0xD6A07C, TEAL = 0x5C8E8C, LINE = 0x5C4232, SPACE = 0x140A22, GLOW = 0xE6B4FF;
    private static final String[][] GROUPS = {
            TorneioRoster.SUBS.toArray(new String[0]), TorneioRoster.DIVINE.toArray(new String[0]), TorneioRoster.BOSSES.toArray(new String[0])};
    private static final String[] GROUP_KEYS = {"subs", "divinas", "bosses"};

    // kept between openings (a rematch starts from the last setup)
    private static final Set<String> PICKED = new LinkedHashSet<>();
    private static boolean teams;
    private static boolean tier0 = true;
    private static int team;

    private float scroll;
    private float contentH;
    // clickable boxes of this frame: x0, y0, x1, y1, action
    private final List<float[]> boxes = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    public TorneioScreen() {
        super(Component.translatable("screen.battlegroundsmod.torneio.titulo"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TorneioScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the arena stays in view behind the panel
    }

    private void box(float x0, float y0, float x1, float y1, Runnable action) {
        boxes.add(new float[] {x0, y0, x1, y1});
        actions.add(action);
    }

    private static boolean over(float x0, float y0, float x1, float y1) {
        return Px.mx >= x0 && Px.mx < x1 && Px.my >= y0 && Px.my < y1;
    }

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // a flat button: brown line, stone face, lighter when hovered or on
    private void button(GuiGraphics g, float x, float y, float w, float h, Component text, boolean on, Runnable action) {
        boolean hover = over(x, y, x + w, y + h);
        int face = on ? PEACH : hover ? 0x8FB4B0 : TEAL;
        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), argb(LINE, 255));
        g.fill((int) x + 1, (int) y + 1, (int) (x + w) - 1, (int) (y + h) - 1, argb(face, 255));
        g.fill((int) x + 1, (int) (y + h) - 2, (int) (x + w) - 1, (int) (y + h) - 1, argb(0x000000, 60));
        int tw = font.width(text);
        g.drawString(font, text, (int) (x + (w - tw) / 2), (int) (y + (h - 8) / 2), on ? 0x2A1A10 : 0xFFFFFF, !on);
        box(x, y, x + w, y + h, action);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Px.begin(graphics, mouseX, mouseY);
        try {
            draw(graphics);
        } finally {
            Px.end();
        }
    }

    private void draw(GuiGraphics g) {
        boxes.clear();
        actions.clear();
        int W = Px.GW, H = Px.GH;
        float x0 = Math.round(W * 0.06f), y0 = Math.round(H * 0.05f), x1 = W - x0, y1 = H - y0;
        // the panel: the arena's brown line and its triangle band round the dark of space
        g.fill((int) x0 - 3, (int) y0 - 3, (int) x1 + 3, (int) y1 + 3, argb(LINE, 255));
        g.fill((int) x0 - 2, (int) y0 - 2, (int) x1 + 2, (int) y1 + 2, argb(CREAM, 255));
        for (float x = x0 - 2; x < x1 + 2; x += 8) {
            int c = ((int) ((x - x0) / 8)) % 2 == 0 ? PEACH : TEAL;
            g.fill((int) x, (int) y0 - 2, (int) Math.min(x + 4, x1 + 2), (int) y0, argb(c, 255));
            g.fill((int) x, (int) y1, (int) Math.min(x + 4, x1 + 2), (int) y1 + 2, argb(c, 255));
        }
        g.fill((int) x0, (int) y0, (int) x1, (int) y1, argb(SPACE, 238));
        // title
        g.pose().pushPose();
        g.pose().translate((x0 + x1) / 2, y0 + 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, title, -font.width(title) / 2, 0, GLOW, true);
        g.pose().popPose();
        // the settings row
        float rowY = y0 + 28, bw = Math.min(130, (x1 - x0 - 40) / 3), bh = 16, gap = 6;
        float bx = (x0 + x1) / 2 - (bw * 3 + gap * 2) / 2;
        button(g, bx, rowY, bw, bh, Component.translatable(teams ? "screen.battlegroundsmod.torneio.times" : "screen.battlegroundsmod.torneio.ffa"),
                false, () -> teams = !teams);
        button(g, bx + bw + gap, rowY, bw, bh, Component.translatable(tier0 ? "screen.battlegroundsmod.torneio.tier0" : "screen.battlegroundsmod.torneio.tier_proprio"),
                false, () -> tier0 = !tier0);
        if (teams) {
            String uni = TorneioRoster.UNIVERSES.get(Math.floorMod(team, TorneioRoster.UNIVERSES.size()));
            button(g, bx + 2 * (bw + gap), rowY, bw, bh, Component.translatable("screen.battlegroundsmod.torneio.seu_time",
                    TorneioRoster.universeName(uni)), false, () -> team = Math.floorMod(team + 1, TorneioRoster.UNIVERSES.size()));
        }
        // the cards, scrolling inside the panel
        float top = rowY + bh + 8, bottom = y1 - 28;
        float cw = 46, ch = 52, cg = 4;
        float innerX0 = x0 + 10, innerX1 = x1 - 10;
        int perRow = Math.max(1, (int) ((innerX1 - innerX0 + cg) / (cw + cg)));
        float gridW = perRow * (cw + cg) - cg;
        float gx = innerX0 + ((innerX1 - innerX0) - gridW) / 2;
        Px.scissor(x0, top, x1, bottom);
        float y = top - scroll;
        for (int gi = 0; gi < GROUPS.length; gi++) {
            String[] ids = GROUPS[gi];
            Component head = Component.translatable("screen.battlegroundsmod.torneio." + GROUP_KEYS[gi]);
            g.drawString(font, head, (int) gx, (int) y + 4, CREAM, true);
            g.fill((int) gx + font.width(head) + 6, (int) y + 8, (int) (gx + gridW - 100), (int) y + 9, argb(LINE, 255));
            boolean visibleRow = y + 16 > top && y < bottom;
            final String[] group = ids;
            if (visibleRow) {
                button(g, gx + gridW - 94, y, 45, 14, Component.translatable("screen.battlegroundsmod.torneio.todos"), false,
                        () -> PICKED.addAll(List.of(group)));
                button(g, gx + gridW - 45, y, 45, 14, Component.translatable("screen.battlegroundsmod.torneio.nenhum"), false,
                        () -> List.of(group).forEach(PICKED::remove));
            }
            y += 18;
            for (int i = 0; i < ids.length; i++) {
                String id = ids[i];
                float cx = gx + (i % perRow) * (cw + cg), cy = y + (i / perRow) * (ch + cg);
                if (cy + ch < top || cy > bottom) {
                    continue;
                }
                boolean on = PICKED.contains(id);
                boolean hover = over(cx, cy, cx + cw, cy + ch) && Px.my >= top && Px.my < bottom;
                g.fill((int) cx, (int) cy, (int) (cx + cw), (int) (cy + ch), argb(on ? GLOW : LINE, 255));
                g.fill((int) cx + 1, (int) cy + 1, (int) (cx + cw) - 1, (int) (cy + ch) - 1, argb(on ? 0x3A2450 : hover ? 0x2C2238 : 0x1E1628, 255));
                if (gi == 2) {
                    g.fill((int) cx + 1, (int) cy + 1, (int) (cx + cw) - 1, (int) cy + 3, argb(0xD8352A, 255));
                } else if (gi == 1) {
                    g.fill((int) cx + 1, (int) cy + 1, (int) (cx + cw) - 1, (int) cy + 3, argb(0xF2C94C, 255));
                }
                SkinDraw.head(id, cx + cw / 2, cy + 20, 1.1f, on ? 0xFFFFFFFF : 0xFF9A90A8);
                Component name = Component.translatable("entity.battlegroundsmod." + id);
                String s = font.plainSubstrByWidth(name.getString(), (int) ((cw - 4) / 0.75f));
                g.pose().pushPose();
                g.pose().translate(cx + cw / 2, cy + ch - 10, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.drawString(font, s, -font.width(s) / 2, 0, on ? 0xFFFFFF : 0xB8B0C8, true);
                g.pose().popPose();
                if (Px.my >= top && Px.my < bottom) {
                    box(cx, cy, cx + cw, cy + ch, () -> {
                        if (!PICKED.remove(id)) {
                            PICKED.add(id);
                        }
                    });
                }
            }
            y += ((ids.length + perRow - 1) / perRow) * (ch + cg) + 6;
        }
        Px.endScissor();
        contentH = y + scroll - top;
        // footer: how many, start, close
        float fy = y1 - 22;
        g.drawString(font, Component.translatable("screen.battlegroundsmod.torneio.escolhidos", PICKED.size()), (int) x0 + 10, (int) fy + 4, CREAM, true);
        button(g, x1 - 10 - 80, fy, 80, 16, Component.translatable("screen.battlegroundsmod.torneio.comecar"), true, this::start);
        button(g, x1 - 10 - 80 - 6 - 60, fy, 60, 16, Component.translatable("screen.battlegroundsmod.torneio.fechar"), false, this::onClose);
    }

    private void start() {
        String uni = TorneioRoster.UNIVERSES.get(Math.floorMod(team, TorneioRoster.UNIVERSES.size()));
        PacketDistributor.sendToServer(new ModPayloads.TorneioStart(new ArrayList<>(PICKED), teams, tier0, uni));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        float mx = Px.toGrid(mouseX), my = Px.toGrid(mouseY);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            float[] b = boxes.get(i);
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                actions.get(i).run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float view = Math.max(0, contentH - (Px.GH * 0.9f - 80));
        scroll = Math.max(0, Math.min(view, scroll - (float) scrollY * 20));
        return true;
    }
}
