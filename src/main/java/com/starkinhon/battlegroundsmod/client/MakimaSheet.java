package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

// The Ritual's sheet (forja/makima-ritual): lined paper with a red margin, a line per puppet she has out (one, in her own
// blood, when she has none). The name is written by hand, not said in chat; the autocomplete offers only those she has
// seen (with her own eyes, through the rats, or in a fight). " on a line repeats the one above (every blow on the same
// one). Enter on the last line signs it (the seal stamps it), Esc puts it away unwritten.
public final class MakimaSheet extends Screen {
    private static final ResourceLocation HAND = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "caveat");
    private static final String DITTO = "〃";
    private static final int PAPER = 0xF4EFE4, RULE = 0xC9D4E4, MARGIN = 0xD8606A, INK = 0x1D1A24, BLOOD = 0x8E0B1E;

    private record Known(String name, String origin, String type, List<String> aliases) {
        // The name, or another one it answers to (the boss's id, the kind of mob).
        boolean starts(String q) {
            if (name.toLowerCase(Locale.ROOT).startsWith(q)) {
                return true;
            }
            for (String a : aliases) {
                if (a.toLowerCase(Locale.ROOT).startsWith(q)) {
                    return true;
                }
            }
            return false;
        }

        boolean has(String q) {
            if (name.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
            for (String a : aliases) {
                if (a.toLowerCase(Locale.ROOT).contains(q)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<Known> known = new ArrayList<>();
    private final String[] lines;
    private final boolean blood;
    private int line;
    private int sug;
    private int age;
    private int sealedAt = -1;
    private boolean sent;

    MakimaSheet(int puppets, List<String> entries) {
        super(Component.translatable("gui.battlegroundsmod.makima.folha"));
        this.blood = puppets <= 0;
        this.lines = new String[Math.max(1, Math.min(3, puppets))];
        java.util.Arrays.fill(lines, "");
        for (String e : entries) {
            String[] p = e.split("\\|", -1);
            List<String> aliases = p.length > 3 && !p[3].isEmpty() ? List.of(p[3].split(",")) : List.of();
            known.add(new Known(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : "", aliases));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (sealedAt >= 0 && age - sealedAt > 9) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (!sent) {
            // Put away unwritten.
            sent = true;
            PacketDistributor.sendToServer(new ModPayloads.MkNames(List.of()));
        }
        super.onClose();
    }

    private List<Known> suggestions() {
        String q = lines[line].trim().toLowerCase(Locale.ROOT);
        List<Known> out = new ArrayList<>();
        if (q.length() < 1 || q.equals(DITTO)) {
            return out;
        }
        for (Known k : known) {
            if (k.starts(q)) {
                out.add(k);
            }
        }
        for (Known k : known) {
            if (!k.starts(q) && k.has(q)) {
                out.add(k);
            }
        }
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (sealedAt >= 0) {
            return true;
        }
        if (c == '"' || c == '\'') {
            if (line > 0 && lines[line].isEmpty()) {
                lines[line] = DITTO;
                return true;
            }
            if (c == '"') {
                return true;
            }
        }
        if (c < 32 || lines[line].length() >= 48) {
            return false;
        }
        if (lines[line].equals(DITTO)) {
            lines[line] = "";
        }
        lines[line] += c;
        sug = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (sealedAt >= 0) {
            return true;
        }
        List<Known> s = suggestions();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                onClose();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!lines[line].isEmpty()) {
                    lines[line] = Screen.hasControlDown() || lines[line].equals(DITTO) ? "" : lines[line].substring(0, lines[line].length() - 1);
                } else if (line > 0) {
                    line--;
                }
                sug = 0;
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (!s.isEmpty()) {
                    sug = (sug + s.size() - 1) % s.size();
                } else if (line > 0) {
                    line--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!s.isEmpty()) {
                    sug = (sug + 1) % s.size();
                } else if (line < lines.length - 1) {
                    line++;
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (!s.isEmpty()) {
                    lines[line] = s.get(Math.min(sug, s.size() - 1)).name();
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String typed = lines[line].trim();
                boolean exact = s.stream().anyMatch(k -> k.name().equalsIgnoreCase(typed));
                if (!s.isEmpty() && !exact) {
                    lines[line] = s.get(Math.min(sug, s.size() - 1)).name();
                    return true;
                }
                if (lines[line].isBlank() && line > 0) {
                    lines[line] = DITTO;
                }
                if (line < lines.length - 1 && !Screen.hasControlDown()) {
                    line++;
                    sug = 0;
                } else {
                    sign();
                }
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
    }

    private void sign() {
        List<String> names = new ArrayList<>();
        String last = "";
        for (String l : lines) {
            String t = l.trim();
            if ((t.isEmpty() || t.equals(DITTO)) && !last.isEmpty()) {
                t = DITTO;
            }
            names.add(t);
            if (!t.isEmpty() && !t.equals(DITTO)) {
                last = t;
            }
        }
        if (last.isEmpty()) {
            return;
        }
        sent = true;
        sealedAt = age;
        PacketDistributor.sendToServer(new ModPayloads.MkNames(names));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // The world goes on behind the sheet.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        float t = age + partial;
        float in = smooth(t / 4f);
        float out = sealedAt < 0 ? 0 : smooth((t - sealedAt - 3) / 7f);
        float alpha = in * (1 - out);
        if (alpha <= 0.01f) {
            return;
        }
        int w = Math.min(340, (int) (width * 0.62f));
        int lh = 28;
        int h = Math.max(150, 18 + 22 + lines.length * lh + 30);
        float cx = width * 0.5f, cy = height * 0.52f;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy + (1 - in) * 30 + out * 60, 50);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.5f + out * 4));
        graphics.pose().translate(-w / 2f, -h / 2f, 0);
        int a = (int) (alpha * 255) << 24;
        // Shadow, paper, the inner brown around the edges.
        for (int i = 6; i > 0; i--) {
            graphics.fill(-i, 4 - i + 8, w + i, h + i + 8, ((int) (alpha * 0x12) << 24));
        }
        graphics.fill(0, 0, w, h, a | PAPER);
        for (int i = 0; i < 8; i++) {
            int ea = (int) (alpha * (0x0C - i)) << 24;
            graphics.fill(i, i, w - i, i + 1, ea | 0x785A32);
            graphics.fill(i, h - i - 1, w - i, h - i, ea | 0x785A32);
            graphics.fill(i, i, i + 1, h - i, ea | 0x785A32);
            graphics.fill(w - i - 1, i, w - i, h - i, ea | 0x785A32);
        }
        int top = 40;
        for (int y = top + 27; y < h; y += lh) {
            graphics.fill(0, y, w, y + 1, a | RULE);
        }
        graphics.fill(34, 0, 36, h, a | MARGIN);
        Component head = Component.translatable(blood ? "gui.battlegroundsmod.makima.folha_sangue" : "gui.battlegroundsmod.makima.folha");
        graphics.pose().pushPose();
        graphics.pose().translate(46, 18, 0);
        graphics.pose().scale(0.85f, 0.85f, 1);
        graphics.drawString(font, head.getString().toUpperCase(Locale.ROOT), 0, 0, a | 0x7A2230, false);
        graphics.pose().popPose();
        int ink = blood ? BLOOD : INK;
        List<Known> s = suggestions();
        float hand = 25f / 11f * 0.82f;
        for (int i = 0; i < lines.length; i++) {
            int y = top + i * lh;
            graphics.drawString(font, String.valueOf(i + 1), 16, y + 10, a | 0x9A8F86, false);
            String text = lines[i];
            boolean ditto = text.equals(DITTO);
            graphics.pose().pushPose();
            graphics.pose().translate(46, y + 4, 0);
            graphics.pose().scale(hand, hand, 1);
            Component c = ditto ? Component.literal("〃") : Component.literal(text).withStyle(Style.EMPTY.withFont(HAND));
            graphics.drawString(font, c, 0, 0, ((int) (alpha * (ditto ? 190 : 255)) << 24) | ink, false);
            int tw = font.width(c);
            if (i == line && sealedAt < 0 && (Util.getMillis() / 330) % 2 == 0) {
                graphics.fill(tw + 1, 0, tw + 2, 9, a | INK);
            }
            graphics.pose().popPose();
        }
        // The seal, stamped on signing.
        if (sealedAt >= 0) {
            float st = smooth((t - sealedAt) / 1.6f);
            graphics.pose().pushPose();
            graphics.pose().translate(w - 14 - 29, h - 10 - 29, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12));
            float sc = 1.6f - 0.6f * st;
            graphics.pose().scale(sc, sc, 1);
            int sa = (int) (alpha * st * 0.9f * 255) << 24;
            for (int y = -29; y <= 29; y++) {
                for (int x = -29; x <= 29; x++) {
                    float d = (float) Math.sqrt(x * x + y * y);
                    boolean edge = d <= 29 && d > 26;
                    boolean rings = d <= 26 && (int) d % 5 < 2;
                    if (edge || rings) {
                        graphics.fill(x, y, x + 1, y + 1, sa | 0xB3122E);
                    }
                }
            }
            graphics.pose().popPose();
        }
        // The autocomplete under the line being written.
        if (sealedAt < 0 && !lines[line].isEmpty() && !lines[line].equals(DITTO)) {
            int y = top + line * lh + 30;
            int bw = 240;
            List<Component[]> rows = new ArrayList<>();
            for (Known k : s) {
                rows.add(new Component[] {Component.literal(k.name()), Component.translatable("gui.battlegroundsmod.makima.origem." + k.origin())});
            }
            if (rows.isEmpty()) {
                rows.add(new Component[] {Component.empty(), Component.translatable("gui.battlegroundsmod.makima.ninguem")});
            }
            for (Component[] r : rows) {
                bw = Math.max(bw, font.width(r[0]) + font.width(r[1]) + 30);
            }
            int bh = rows.size() * 14 + 4;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 20);
            graphics.fill(44, y + 3, 46 + bw + 2, y + bh + 5, (int) (alpha * 0x40) << 24);
            graphics.fill(46, y, 46 + bw, y + bh, a | 0xC9C0B6);
            graphics.fill(47, y + 1, 46 + bw - 1, y + bh - 1, a | 0xFFFFFF);
            for (int i = 0; i < rows.size(); i++) {
                int ry = y + 2 + i * 14;
                boolean on = !s.isEmpty() && i == Math.min(sug, s.size() - 1);
                if (on) {
                    graphics.fill(47, ry, 46 + bw - 1, ry + 14, a | 0xA3122A);
                }
                graphics.drawString(font, rows.get(i)[0], 54, ry + 3, a | (on ? 0xFFFFFF : 0x1E1A1C), false);
                Component o = rows.get(i)[1];
                graphics.drawString(font, o, 46 + bw - 8 - font.width(o), ry + 3, a | (on ? 0xFFD6DC : 0x7D7068), false);
            }
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        // How it works, under the sheet.
        Component tip = Component.translatable("gui.battlegroundsmod.makima.folha_dica");
        graphics.drawString(font, tip, (int) (cx - font.width(tip) / 2f), (int) (cy + h / 2f + 22), ((int) (alpha * 200) << 24) | 0xE8DCDC, true);
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
