package com.starkinhon.battlegroundsmod.client;

import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// Shenlong's wish menu, the approved preview's (forja/shenlong page.html #menu + ui.js): a dark green panel, the title,
// nine cards in three columns with their own 16x16 pixel icon, name and description, and the reply line under them.
// "Dominar o mundo" is the Pilaf's wish: Shenlong refuses it here and the menu stays open (nothing is spent).
// The scene does not pause and Esc does not close it: the dragon waits for an answer.
public class ShenlongWishScreen extends Screen {
    private static final String[] WISHES = {"poder", "reviver", "alma", "imortal", "rival", "tesouro", "senzu", "amigo", "dominar"};

    private static final Map<Character, Integer> PAL = Map.ofEntries(Map.entry('k', 0x1a1410), Map.entry('y', 0xffd23a), Map.entry('o', 0xf08a1c),
            Map.entry('w', 0xfff6d8), Map.entry('r', 0xe0362c), Map.entry('R', 0x8e1a14), Map.entry('b', 0x5ec8ff), Map.entry('B', 0x2a6fd0),
            Map.entry('g', 0x7ad24a), Map.entry('G', 0x2f7a2a), Map.entry('s', 0xcfd6de), Map.entry('S', 0x7b8794), Map.entry('c', 0x7ff3ff),
            Map.entry('C', 0x2aa7c4), Map.entry('p', 0xf2c2a0), Map.entry('n', 0x6b4a2a), Map.entry('e', 0x2fc06a), Map.entry('d', 0x9a6a20));

    private static final Map<String, String[]> ART = Map.of(
            "poder", new String[] {"......kk........", ".....kyyk.......", "....kyyyyk......", "...kyyyyyyk.....", "..kkkyyyykkk....", "....kyyyyk......",
                "....kyoyyk......", "....kyoyyk......", "....kyoyyk......", "..kkkkkkkkkk....", "..kooooooook....", "..kyyyyyyyyk....", "..kkkkkkkkkk...."},
            "reviver", new String[] {"................", "..kkk....kkk....", ".krrrk..krrrk...", "krwrrrkkrrrrrk..", "krwrrrrrrrrrrk..", "krrrrrrrrrrrrk..",
                "krrrrrrrrrrrRk..", ".krrrrrrrrrRk...", "..krrrrrrrRk....", "...krrrrrRk.....", "....krrrRk......", ".....krRk.......", "......kk........"},
            "alma", new String[] {".......k........", "......kbk.......", ".....kbbk.......", "....kbbbbk......", "...kbbcbbbk.....", "...kbccbbBk.....",
                "..kbbcwcbBBk....", "..kbccwccbBk....", "..kbcwwwcbBk....", "..kbcwwwcbBk....", "...kbcwcbBk.....", "....kbbbBk......", ".....kkkk......."},
            "imortal", new String[] {"..kkkkkkkkkk....", "..kdddddddk.....", "...kyyyyyk......", "...kyyyyyk......", "....kyyyk.......", ".....kyk........",
                "......k.........", ".....k.k........", "....k.y.k.......", "...k..y..k......", "...k.yyy.k......", "..kyyyyyyyk.....", "..kdddddddk.....",
                "..kkkkkkkkkk...."},
            "rival", new String[] {"k............k..", "ksk..........ksk", ".ksk........ksk.", "..ksk......ksk..", "...ksk....ksk...", "....ksk..ksk....",
                ".....ksskssk....", "......kssk......", ".....ksskssk....", "....knk..knk....", "...knk....knk...", "..kyk......kyk..", ".kyk........kyk.",
                ".kk..........kk."},
            "tesouro", new String[] {"................", "....kkkkkkk.....", "...kcwcccCCk....", "..kcwccccCCCk...", ".kkkkkkkkkkkkk..", ".kcwcccccccCCk..",
                "..kcwccccccCk...", "...kcwccccCk....", "....kcwcccCk....", ".....kcccCk.....", "......kcCk......", ".......kk.......", "................",
                "..kyyk.kyyk.kyk.", "..kook.kook.kok."},
            "senzu", new String[] {"................", "................", ".....kkkk.......", "....kgggGk......", "...kgwggggk.....", "..kgwgggggGk....",
                "..kgggggggGk....", "..kgggggggGk....", "...kgggggGk.....", "....kGGGGk......", ".....kkkk......."},
            "amigo", new String[] {"................", "...kkk....kkk...", "..knnnk..kkkkk..", "..kpppk..knnnk..", "..kpkpk..kpkpk..", "..kpppk..kpppk..",
                "...kkk....kkk...", "..kooook.kbbbbk.", ".kooooook.bbbbk.", ".kooooook.bbbbk.", ".kook.ook.bbbbk."},
            "dominar", new String[] {"................", "................", ".k....k....k....", ".kk..kyk..kk....", ".kyk.kyk.kyk....", ".kyykyyykyyk....",
                ".kyyyyryyyyk....", ".kyyyyyyyyyk....", ".kyryyyyyryk....", ".kyyyyyyyyyk....", ".kkkkkkkkkkk...."});

    private Component note = Component.empty();
    private boolean sent;

    public ShenlongWishScreen() {
        super(Component.translatable("shenlong.battlegroundsmod.menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // The panel (inset 8% / 7% like the preview's #menu) and its cards.
    private int px0() {
        return Math.round(width * 0.07f);
    }

    private int py0() {
        return Math.round(height * 0.08f);
    }

    private int pw() {
        return width - 2 * px0();
    }

    private int ph() {
        return height - 2 * py0();
    }

    private int pad() {
        return Math.max(6, Math.round(pw() * 0.026f));
    }

    private int titleH() {
        return 9 * titleScale() + 8;
    }

    private int titleScale() {
        return width >= 420 ? 2 : 1;
    }

    private int gap() {
        return Math.max(4, Math.round(width * 0.012f));
    }

    private int[] card(int i) {
        int pad = pad(), gap = gap();
        int gx = px0() + pad, gy = py0() + pad + titleH();
        int gw = pw() - 2 * pad, gh = ph() - 2 * pad - titleH() - 16;
        int cw = (gw - 2 * gap) / 3, ch = (gh - 2 * gap) / 3;
        int col = i % 3, row = i / 3;
        return new int[] {gx + col * (cw + gap), gy + row * (ch + gap), cw, ch};
    }

    private int hovered(double mx, double my) {
        for (int i = 0; i < WISHES.length; i++) {
            int[] c = card(i);
            if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur, no dark wash: the dragon stays in view behind the panel.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x0 = px0(), y0 = py0(), w = pw(), h = ph();
        // Glow (0 0 40px rgba(60,200,110,.25)), the outer dark ring, the green border and the body.
        for (int k = 6; k >= 1; k--) {
            int a = (int) (0x10 * (7 - k) / 6.0);
            g.fill(x0 - 2 - k * 3, y0 - 2 - k * 3, x0 + w + 2 + k * 3, y0 + h + 2 + k * 3, (a << 24) | 0x3cc86e);
        }
        g.fill(x0 - 4, y0 - 4, x0 + w + 4, y0 + h + 4, 0xFF0A1A10);
        g.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, 0xFF3E8F55);
        g.fill(x0, y0, x0 + w, y0 + h, 0xDB060E09);

        int ts = titleScale();
        g.pose().pushPose();
        g.pose().translate(x0 + w / 2f, y0 + pad(), 0);
        g.pose().scale(ts, ts, 1);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xBFF5C6, true);
        g.pose().popPose();

        int hover = sent ? -1 : hovered(mouseX, mouseY);
        for (int i = 0; i < WISHES.length; i++) {
            int[] c = card(i);
            boolean on = i == hover;
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], on ? 0xFFFFB03A : 0xFF2A5A38);
            g.fill(c[0] + 2, c[1] + 2, c[0] + c[2] - 2, c[1] + c[3] - 2, on ? 0xFF18301F : 0xFF0F2216);
            int icon = c[3] >= 44 && c[2] >= 120 ? 2 : 1;
            int ix = c[0] + 8, iy = c[1] + 6;
            icon(g, WISHES[i], ix, iy, icon);
            int tx = ix + 16 * icon + 8, tw = c[0] + c[2] - 8 - tx;
            Component name = Component.translatable("shenlong.battlegroundsmod.desejo." + WISHES[i]);
            g.drawString(font, name, tx, iy, 0xE7F7E8, false);
            List<FormattedCharSequence> lines = font.split(Component.translatable("shenlong.battlegroundsmod.desejo." + WISHES[i] + ".desc"), Math.max(20, tw));
            int ly = iy + 11, maxY = c[1] + c[3] - 4;
            for (FormattedCharSequence line : lines) {
                if (ly + 9 > maxY) {
                    break;
                }
                g.drawString(font, line, tx, ly, 0x9FC2A6, false);
                ly += 10;
            }
        }
        if (!note.getString().isEmpty()) {
            g.drawCenteredString(font, note, x0 + w / 2, y0 + h - pad() - 10, 0xFFB03A);
        }
    }

    private static void icon(GuiGraphics g, String name, int x, int y, int s) {
        String[] art = ART.get(name);
        if (art == null) {
            return;
        }
        for (int r = 0; r < art.length; r++) {
            String row = art[r];
            for (int c = 0; c < row.length(); c++) {
                Integer col = PAL.get(row.charAt(c));
                if (col != null) {
                    g.fill(x + c * s, y + r * s, x + (c + 1) * s, y + (r + 1) * s, 0xFF000000 | col);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (sent) {
            return true;
        }
        int i = hovered(mx, my);
        if (i < 0) {
            return super.mouseClicked(mx, my, button);
        }
        String wish = WISHES[i];
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
        }
        if ("dominar".equals(wish)) {
            note = Component.translatable("shenlong.battlegroundsmod.desejo.dominar.resposta");
            return true;
        }
        sent = true;
        PacketDistributor.sendToServer(new ModPayloads.ShenlongWish(wish));
        return true;
    }
}
