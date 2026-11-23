// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.ui.Px.BLACK;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GH;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GOLD;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GW;
import static com.starkinhon.battlegroundsmod.client.ui.Px.MUTED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;
import static com.starkinhon.battlegroundsmod.client.ui.Px.T;
import static com.starkinhon.battlegroundsmod.client.ui.Px.TEXT;
import static com.starkinhon.battlegroundsmod.client.ui.Px.WHITE;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.starkinhon.battlegroundsmod.client.fx.FxConfig;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Experiência do jogador (Stark 2026-11-22, after the Jujutsu Verse's "Player settings"): the player's own options on
// one screen, opened with O (Controls can change it) or the button on the pause menu.
// - Desempenho: what the effects cost (quality, screen shake, impact frames, shaders, debris, auras, trails, the
//   biomes' particles, how far effects are drawn, other players' effects in a light version). The defaults are the
//   game as it looks today: the lower options only take away.
// - Interface: the missions' auto tracking, the cooldown seconds, the hint bubbles, Kokushibo's kanji and Saitama's
//   camera cuts.
// - Mundo: the Souls game rules of the world; only its owner or an operator changes them (the server checks it,
//   world/SoulsRules).
// The player's options are saved in the client config file (client/fx/FxConfig) at every click; the rules go to the
// server. Drawn on the menus' pixel grid (client/ui/Px), like the Livro and the Diário.
public class SoulsSettingsScreen extends Screen {
    private static final int TAB_PERF = 0, TAB_UI = 1, TAB_WORLD = 2;
    private static final int ACCENT = rgb(176, 140, 255);

    // the world's rules as the server last said (bit i = SoulsRules.rules().get(i)), and whether this player may change them
    private static int ruleBits;
    private static boolean canEdit, rulesKnown;
    // the tab last opened (kept while the game runs)
    private static int tab = TAB_PERF;

    private final Screen parent;
    private float scroll, maxScroll;
    // the list's box on the grid, for the wheel
    private float listX0, listY0, listX1, listY1;

    // One row: its lang key (title .<key>, description .<key>.desc), the values' labels left to right, and how to read
    // and write the value (an index into the labels).
    private record Opt(String key, String[] labels, IntSupplier get, IntConsumer set, boolean world) {
    }

    public SoulsSettingsScreen(Screen parent) {
        super(Component.translatable("screen.battlegroundsmod.exp.title"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- opening

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.screen == null) {
            minecraft.setScreen(new SoulsSettingsScreen(null));
        }
    }

    // The pause menu's button, in the menu's own column right under its last button ("Salvar e voltar"), the same width
    // and the same 4 px gap as the others. The corners are taken by other mods (Stark 2026-11-23: Customizable Player
    // Models sits in the top left one). Only the buttons in the middle column count, so a mod's corner button never moves
    // it; with no room below (a tiny window), it goes to the bottom right corner.
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause) || !pause.showsPauseMenu()) {
            return;
        }
        int w = 204, h = 20, mid = pause.width / 2, bottom = -1;
        for (net.minecraft.client.gui.components.events.GuiEventListener l : event.getListenersList()) {
            if (l instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible
                    && Math.abs(widget.getX() + widget.getWidth() / 2 - mid) <= 4 && widget.getWidth() >= 98) {
                bottom = Math.max(bottom, widget.getY() + widget.getHeight());
            }
        }
        int x = mid - w / 2, y = bottom < 0 ? pause.height / 4 + 152 : bottom + 4;
        if (y + h > pause.height - 4) {
            w = 130;
            x = pause.width - w - 6;
            y = pause.height - h - 6;
        }
        event.addListener(Button.builder(Component.translatable("screen.battlegroundsmod.exp.button"),
                b -> Minecraft.getInstance().setScreen(new SoulsSettingsScreen(pause))).bounds(x, y, w, h).build());
    }

    // A world joined: the server learns this player's choice of the missions' auto tracking.
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        rulesKnown = false;
        sendPrefs();
    }

    private static void sendPrefs() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ModPayloads.SoulsPrefs(FxConfig.autoTrack()));
        }
    }

    // From the server (ModPayloads.SoulsRulesSync).
    public static void rules(int bits, boolean edit) {
        ruleBits = bits;
        canEdit = edit;
        rulesKnown = true;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new ModPayloads.SoulsRule(-1, false));
        }
    }

    // ---------------------------------------------------------------- the options

    private static String[] labels(String... keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = keys[i].matches("\\d+") ? keys[i] : Component.translatable("screen.battlegroundsmod.exp.v." + keys[i]).getString();
        }
        return out;
    }

    private static int b(boolean on) {
        return on ? 1 : 0;
    }

    private static List<Opt> options(int which) {
        List<Opt> list = new ArrayList<>();
        String[] offOn = labels("off", "on");
        if (which == TAB_PERF) {
            list.add(new Opt("quality", labels("low", "mid", "high"), FxConfig::effectQuality, v -> FxConfig.set(FxConfig.EFFECT_QUALITY, v), false));
            list.add(new Opt("shake", labels("off", "normal", "strong"), () -> Math.round(Math.max(0, Math.min(2, FxConfig.shake()))),
                    v -> FxConfig.set(FxConfig.SHAKE, (double) v), false));
            list.add(new Opt("impact", labels("off", "soft", "on"),
                    () -> !FxConfig.get(FxConfig.IMPACT_FRAMES) ? 0 : FxConfig.get(FxConfig.REDUCED_FLASH) ? 1 : 2, v -> {
                        FxConfig.set(FxConfig.IMPACT_FRAMES, v > 0);
                        FxConfig.set(FxConfig.REDUCED_FLASH, v == 1);
                    }, false));
            list.add(new Opt("shaders", offOn,
                    () -> b(FxConfig.get(FxConfig.DISTORTION) && FxConfig.get(FxConfig.BLOOM) && FxConfig.get(FxConfig.RADIAL_BLUR)), v -> {
                        FxConfig.set(FxConfig.DISTORTION, v == 1);
                        FxConfig.set(FxConfig.BLOOM, v == 1);
                        FxConfig.set(FxConfig.RADIAL_BLUR, v == 1);
                    }, false));
            list.add(new Opt("realism", offOn, () -> b(FxConfig.get(FxConfig.REALISMO)), v -> FxConfig.set(FxConfig.REALISMO, v == 1), false));
            list.add(new Opt("debris", labels("few", "normal"), () -> b(!FxConfig.fewDebris()), v -> FxConfig.set(FxConfig.FEW_DEBRIS, v == 0), false));
            list.add(new Opt("aura", labels("off", "light", "full"), FxConfig::auraMode, v -> FxConfig.set(FxConfig.AURA_MODE, v), false));
            list.add(new Opt("trails", offOn, () -> b(FxConfig.bladeTrails()), v -> FxConfig.set(FxConfig.BLADE_TRAILS, v == 1), false));
            list.add(new Opt("blood", labels("off", "little", "medium", "more"), FxConfig::blood, v -> FxConfig.set(FxConfig.BLOOD, v), false));
            list.add(new Opt("gore", offOn, () -> b(FxConfig.gore()), v -> FxConfig.set(FxConfig.GORE, v == 1), false));
            list.add(new Opt("ambient", offOn, () -> b(FxConfig.ambient()), v -> FxConfig.set(FxConfig.AMBIENT, v == 1), false));
            list.add(new Opt("range", labels("16", "32", "64", "all"), () -> FxConfig.get(FxConfig.EFFECT_RANGE),
                    v -> FxConfig.set(FxConfig.EFFECT_RANGE, v), false));
            list.add(new Opt("others", offOn, () -> b(FxConfig.othersLight()), v -> FxConfig.set(FxConfig.OTHERS_LIGHT, v == 1), false));
        } else if (which == TAB_UI) {
            list.add(new Opt("autotrack", offOn, () -> b(FxConfig.autoTrack()), v -> {
                FxConfig.set(FxConfig.AUTO_TRACK, v == 1);
                sendPrefs();
            }, false));
            list.add(new Opt("column", labels("normal", "compact"), () -> b(FxConfig.compactColumn()),
                    v -> FxConfig.set(FxConfig.COMPACT_COLUMN, v == 1), false));
            list.add(new Opt("cooldown", offOn, () -> b(FxConfig.cooldownNumbers()), v -> FxConfig.set(FxConfig.COOLDOWN_NUMBERS, v == 1), false));
            list.add(new Opt("hints", offOn, () -> b(FxConfig.hints()), v -> FxConfig.set(FxConfig.HINTS, v == 1), false));
            list.add(new Opt("kanji", offOn, () -> b(FxConfig.formKanji()), v -> FxConfig.setFormKanji(v == 1), false));
            list.add(new Opt("saitama", offOn, () -> b(FxConfig.saitamaCuts()), v -> FxConfig.set(FxConfig.SAITAMA_CUTS, v == 1), false));
        } else {
            // the order of world/SoulsRules.rules()
            String[] rules = {"rule_destruction", "rule_clash", "rule_bots", "rule_kokushibo", "rule_tier0"};
            for (int i = 0; i < rules.length; i++) {
                int bit = 1 << i, index = i;
                list.add(new Opt(rules[i], offOn, () -> b((ruleBits & bit) != 0), v -> {
                    ruleBits = v == 1 ? ruleBits | bit : ruleBits & ~bit;
                    PacketDistributor.sendToServer(new ModPayloads.SoulsRule(index, v == 1));
                }, true));
            }
        }
        return list;
    }

    private static String tr(String key) {
        return Component.translatable("screen.battlegroundsmod.exp." + key).getString();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Px.begin(graphics, mouseX, mouseY);
        Px.hits.clear();
        try {
            R(0, 0, GW, GH, BLACK, 0.55f);
            float pw = Math.min(GW - 24, 440), ph = Math.min(GH - 24, 250);
            float x = Math.round((GW - pw) / 2), y = Math.round((GH - ph) / 2);
            Px.frame(x, y, pw, ph, ACCENT, 0.95f, true, true, tr("title").toUpperCase(java.util.Locale.ROOT));
            T(tr("subtitle"), x + 12, y + 10, MUTED, 6);

            // the tabs, on the left
            String[] tabs = {"tab_perf", "tab_ui", "tab_world"};
            float tx = x + 10, ty = y + 24, tw0 = 88, th = 18;
            for (int i = 0; i < tabs.length; i++) {
                float yy = ty + i * (th + 4);
                boolean lit = tab == i, hot = Px.over(tx, yy, tw0, th);
                Px.smallFrame(tx, yy, tw0, th, ACCENT, lit || hot, lit ? 1 : 0.8f);
                T(String.format("%02d", i + 1), tx + 6, yy + 6, lit ? GOLD : MUTED, 6);
                T(tr(tabs[i]), tx + 20, yy + 6, lit ? WHITE : TEXT, 6, lit);
                int which = i;
                Px.hit(tx, yy, tw0, th, () -> {
                    if (tab != which) {
                        tab = which;
                        scroll = 0;
                        click();
                    }
                });
            }

            // the list
            listX0 = x + 108;
            listY0 = y + 22;
            listX1 = x + pw - 10;
            listY1 = y + ph - 22;
            R(listX0 - 6, listY0, 1, listY1 - listY0, GOLD, 0.18f);
            drawList();

            // the footer
            String foot;
            if (tab == TAB_WORLD) {
                foot = !rulesKnown ? tr("world_wait") : canEdit ? tr("world_edit") : tr("world_locked");
            } else {
                foot = tr("saved");
            }
            T(foot, listX0, y + ph - 14, tab == TAB_WORLD && rulesKnown && !canEdit ? rgb(255, 170, 120) : MUTED, 6);
            String close = tr("close");
            float cw = tw(close, 6, true) + 16, cx = x + pw - 10 - cw, cy = y + ph - 18;
            boolean hot = Px.over(cx, cy, cw, 13);
            Px.button(cx, cy, cw, 13, ACCENT, hot);
            T(close, cx + cw / 2, cy + 3, WHITE, 6, true, 0, true, 1);
            Px.hit(cx, cy, cw, 13, () -> {
                click();
                onClose();
            });
        } finally {
            Px.end();
        }
    }

    private void drawList() {
        List<Opt> list = options(tab);
        float w = listX1 - listX0;
        boolean locked = tab == TAB_WORLD && (!rulesKnown || !canEdit);
        // the rows' heights first (the description wraps beside the buttons)
        float[] heights = new float[list.size()];
        float[] chipsW = new float[list.size()];
        List<List<String>> descs = new ArrayList<>();
        float total = 0;
        for (int i = 0; i < list.size(); i++) {
            Opt o = list.get(i);
            float cw = 0;
            for (String l : o.labels()) {
                cw += tw(l, 6) + 10 + 2;
            }
            chipsW[i] = cw;
            List<String> d = Px.wrap(tr(o.key() + ".desc"), Math.max(60, w - cw - 14), 6);
            descs.add(d);
            heights[i] = Math.max(30, 16 + d.size() * 8 + 5);
            total += heights[i] + 3;
        }
        float view = listY1 - listY0;
        maxScroll = Math.max(0, total - view);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        Px.scissor(listX0, listY0, listX1, listY1);
        try {
            float y = listY0 - scroll;
            for (int i = 0; i < list.size(); i++) {
                Opt o = list.get(i);
                float h = heights[i];
                if (y + h >= listY0 && y <= listY1) {
                    drawRow(o, listX0, y, w, h, chipsW[i], descs.get(i), locked);
                }
                y += h + 3;
            }
        } finally {
            Px.endScissor();
        }
        // the scroll bar
        if (maxScroll > 0) {
            float barH = Math.max(14, view * view / (view + maxScroll));
            float barY = listY0 + (view - barH) * (scroll / maxScroll);
            R(listX1 + 3, listY0, 2, view, WHITE, 0.08f);
            R(listX1 + 3, barY, 2, barH, ACCENT, 0.8f);
        }
    }

    private void drawRow(Opt o, float x, float y, float w, float h, float chipsW, List<String> desc, boolean locked) {
        boolean hotRow = Px.over(x, y, w, h) && Px.my >= listY0 && Px.my <= listY1;
        R(x, y, w, h, mix(Px.VEIL, ACCENT, hotRow ? 0.16f : 0.08f), 0.7f);
        R(x, y + h - 1, w, 1, ACCENT, hotRow ? 0.5f : 0.25f);
        float alpha = locked ? 0.5f : 1;
        T(tr(o.key()), x + 6, y + 5, WHITE, 8, true, -1, true, alpha);
        float dy = y + 16;
        for (String line : desc) {
            T(line, x + 6, dy, MUTED, 6, false, -1, true, alpha);
            dy += 8;
        }
        // the values, side by side on the right, the current one lit
        int current = o.get().getAsInt();
        float cx = x + w - 6 - chipsW + 2, cy = y + (h - 13) / 2;
        for (int i = 0; i < o.labels().length; i++) {
            String label = o.labels()[i];
            float cw = tw(label, 6) + 10;
            boolean lit = i == current;
            boolean hot = !locked && Px.over(cx, cy, cw, 13) && Px.my >= listY0 && Px.my <= listY1;
            Px.smallFrame(cx, cy, cw, 13, lit ? GOLD : ACCENT, lit || hot, alpha);
            T(label, cx + cw / 2, cy + 3, lit ? rgb(255, 236, 180) : TEXT, 6, lit, 0, true, alpha);
            // only the part of the chip inside the list takes the click
            float hy0 = Math.max(cy, listY0), hy1 = Math.min(cy + 13, listY1);
            if (!locked && hy1 > hy0) {
                int value = i;
                Px.hit(cx, hy0, cw, hy1 - hy0, () -> {
                    if (o.get().getAsInt() != value) {
                        o.set().accept(value);
                        click();
                    }
                });
            }
            cx += cw + 2;
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && Px.click(Px.toGrid(mouseX), Px.toGrid(mouseY))) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
        if (gx >= listX0 && gx <= listX1 + 6 && gy >= listY0 && gy <= listY1) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (float) scrollY * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.SETTINGS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
