package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.ui.Px.BLACK;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GH;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GOLD;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GW;
import static com.starkinhon.battlegroundsmod.client.ui.Px.MUTED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.P;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;
import static com.starkinhon.battlegroundsmod.client.ui.Px.T;
import static com.starkinhon.battlegroundsmod.client.ui.Px.TEXT;
import static com.starkinhon.battlegroundsmod.client.ui.Px.VEIL;
import static com.starkinhon.battlegroundsmod.client.ui.Px.WHITE;
import static com.starkinhon.battlegroundsmod.client.ui.Px.clamp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

// The playable introduction's screen (intro/Intro), the approved preview "Introdução" on the menus' pixel grid
// (client/ui/Px, the Diário de Missões family; Stark 2026-11-19: no vanilla tooltip look):
// - the teaching card: title, one line of what is going on, the keys as gold-edged tiles (a repeated press is the key
//   once and "2x", never the key twice), the air moves' cards, and the button. The game pauses on it (single player).
// - the mission in the corner (face, place, what to do, the bar with its diamond), the place's name coming down big,
//   a line of speech at the bottom, the white flash of a soul swap with its text, the black between scenes, the logo.
// Texts are in the lang files: intro.battlegroundsmod.card.<id>(.text/.keys/.cards/.button), .goal.<id>,
// .title.<id>(.sub), .say.<id>(.who), .flash.<id>.
public final class IntroHud {
    private static final int CREAM = rgb(255, 240, 200), SOFT = rgb(214, 206, 232), DIM = rgb(150, 142, 170);

    // the scene: face and color of the corner panel (from the last title)
    private static String face = "madara", place = "";
    private static int color = rgb(214, 72, 60);
    // the mission
    private static String goal = "";
    private static int count, need;
    private static long goalAt, goalDoneAt = -1;
    // the place's name
    private static String title = "", sub = "";
    private static long titleAt = -1, titleUntil;
    // speech
    private static String who = "", line = "";
    private static long sayAt = -1, sayUntil;
    // white flash, black, logo
    private static float flash, flashTo, black, blackTo;
    private static String flashText = "";
    private static long logoAt = -1;
    private static int loading = -1;
    // Act 3 in the real Shibuya: night on this player's screen only (the world's own time goes on for everyone else)
    private static boolean night;
    private static long last = System.currentTimeMillis();

    private IntroHud() {
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("intro.battlegroundsmod." + key, args).getString();
    }

    private static String opt(String key) {
        String k = "intro.battlegroundsmod." + key;
        return I18n.exists(k) ? I18n.get(k) : "";
    }

    // From the server (ModPayloads.IntroUi).
    public static void receive(String kind, String id, int a, int b) {
        long now = System.currentTimeMillis();
        switch (kind) {
            case "card" -> Minecraft.getInstance().setScreen(new CardScreen(id));
            case "goal" -> {
                if (b <= 0) {
                    goal = "";
                    return;
                }
                if (!id.equals(goal)) {
                    goalAt = now;
                    goalDoneAt = -1;
                }
                if (a >= b && count < b) {
                    goalDoneAt = now;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.8f));
                }
                goal = id;
                count = a;
                need = b;
            }
            case "title" -> {
                title = tr("title." + id);
                sub = opt("title." + id + ".sub");
                place = title;
                String f = opt("title." + id + ".face");
                face = f.isEmpty() ? face : f;
                String c = opt("title." + id + ".color");
                if (!c.isEmpty()) {
                    color = Integer.parseInt(c, 16);
                }
                titleAt = now;
                titleUntil = now + Math.max(1, a) * 50L;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1f, 1f));
            }
            case "say" -> {
                who = tr("say." + id + ".who");
                line = tr("say." + id);
                sayAt = now;
                sayUntil = now + Math.max(1, a) * 50L;
            }
            case "flash" -> {
                flashTo = a > 0 ? 1 : 0;
                if (a > 0) {
                    flashText = id.isEmpty() ? "" : tr("flash." + id);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.2f, 0.5f));
                }
            }
            case "black" -> {
                blackTo = a > 0 ? 1 : 0;
                if (a > 0) {
                    black = Math.max(black, 0.01f);
                } else {
                    loading = -1;
                }
            }
            case "loading" -> loading = a;
            case "night" -> night = a > 0;
            case "logo" -> {
                logoAt = now;
                goal = "";
            }
            case "clear" -> clear();
            default -> {
            }
        }
    }

    public static void clear() {
        goal = "";
        titleAt = -1;
        sayAt = -1;
        flash = flashTo = 0;
        black = blackTo = 0;
        logoAt = -1;
        loading = -1;
        night = false;
        if (Minecraft.getInstance().screen instanceof CardScreen) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    // Every frame (BattlegroundsModClient): the client's clock held at night while the scene asks for it (the server's
    // time packets would move it back between frames).
    public static void holdNight() {
        Minecraft mc = Minecraft.getInstance();
        if (night && mc.level != null) {
            mc.level.setDayTime(18000L);
        }
    }

    public static boolean active() {
        return !goal.isEmpty() || titleAt >= 0 || black > 0 || flash > 0 || logoAt >= 0 || sayAt >= 0;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - last) / 1000f);
        last = now;
        flash = approach(flash, flashTo, dt / 0.25f);
        black = approach(black, blackTo, dt / 0.5f);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !active()) {
            return;
        }
        Px.begin(graphics, -1, -1);
        try {
            if (!minecraft.options.hideGui) {
                drawGoal(now);
                drawTitle(now);
                drawSay(now);
            }
        } finally {
            Px.end();
        }
    }

    // The flash, the black and the logo: a layer of their own over every other one (BattlegroundsModClient registers it
    // last), so nothing of the HUD (the base attack's name, the bars) shows through a fade (Stark 2026-11-19 video).
    // No "loading" bar on the black either: only the fade (Stark: "tira esses carregando o terreno").
    public static void renderCover(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || flash <= 0 && black <= 0 && logoAt < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Px.begin(graphics, -1, -1);
        try {
            if (flash > 0) {
                R(0, 0, GW, GH, WHITE, flash);
                T(flashText, GW / 2f, GH / 2f - 10, rgb(60, 44, 10), 18, true, 0, false, flash);
            }
            if (black > 0) {
                R(0, 0, GW, GH, BLACK, black);
            }
            drawLogo(now);
        } finally {
            Px.end();
        }
    }

    // Covering the screen right now (black or white): the world's "loading terrain" screen is not shown over it.
    public static boolean covering() {
        return blackTo > 0 || black > 0.5f || flashTo > 0 || logoAt >= 0;
    }

    private static float approach(float v, float to, float step) {
        return v < to ? Math.min(to, v + step) : Math.max(to, v - step);
    }

    private static float ease(float t) {
        t = clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ---- the corner mission (the QuestHud's panel)
    private static void drawGoal(long now) {
        if (goal.isEmpty()) {
            return;
        }
        float a = ease((now - goalAt) / 300f), x = 4 - (1 - a) * 160, y = 4, w = 170, h = 34;
        Px.hudVeil(x, y, w, h, color, 0.95f, false);
        Px.glow(x + 14.5f, y + 14.5f, 12, color, 0.45f);
        SkinDraw.head(face, x + 14.5f, y + 14.5f, 1.2f, 0xFFFFFFFF);
        T(Px.fit(place, w - 40, 6), x + 32, y + 5, mix(color, WHITE, 0.3f), 6, true);
        T(Px.fit(tr("goal." + goal), w - 40, 8), x + 32, y + 13, CREAM, 8);
        if (need > 1) {
            float bw = 100, f = clamp(count / (float) need, 0, 1);
            R(x + 32, y + 25, bw, 2, WHITE, 0.12f);
            R(x + 32, y + 25, Math.round(bw * f), 2, f >= 1 ? rgb(0x86, 0xe6, 0x5a) : rgb(0x9c, 0xf0, 0x60), 1);
            Px.diamond(x + 32 + Math.round(bw * f), y + 26, 2, GOLD, 1);
            T(count + "/" + need, x + 32 + bw + 6, y + 22, DIM, 6);
        }
        if (goalDoneAt >= 0) {
            float k = ease((now - goalDoneAt) / 300f);
            Px.diamond(x + w - 40, y + 17, 4, GOLD, k, true);
            Px.diamond(x + w - 40, y + 17, 2, WHITE, k, true);
        }
    }

    private static void goldRule(float x0, float x1, float y, float a) {
        float w = x1 - x0;
        for (int i = 0; i < w; i++) {
            float t = 1 - Math.abs(i - w / 2) / (w / 2);
            P(x0 + i, y, GOLD, 0.85f * (float) Math.pow(Math.max(0, t), 0.5) * a);
        }
        Px.diamond(Math.round((x0 + x1) / 2), y, 2, GOLD, a);
        Px.diamond(x0, y, 1, GOLD, 0.7f * a);
        Px.diamond(x1, y, 1, GOLD, 0.7f * a);
    }

    // ---- the place's name coming down big
    private static void drawTitle(long now) {
        if (titleAt < 0) {
            return;
        }
        float age = (now - titleAt) / 1000f, a = Math.min(1, Math.min(age / 0.4f, (titleUntil - now) / 500f));
        if (a <= 0) {
            if (now > titleUntil) {
                titleAt = -1;
            }
            return;
        }
        float y = 40 - (1 - ease(age / 0.5f)) * 8, cx = GW / 2f;
        T(title, cx, y, CREAM, 22, true, 0, true, a);
        goldRule(cx - 100, cx + 100, y + 27, a);
        T(sub, cx, y + 32, SOFT, 9, false, 0, true, a);
    }

    // ---- a line of speech at the bottom (the talk scene's)
    private static void drawSay(long now) {
        if (sayAt < 0) {
            return;
        }
        float age = (now - sayAt) / 1000f, a = Math.min(1, Math.min(age / 0.2f, (sayUntil - now) / 300f));
        if (a <= 0) {
            if (now > sayUntil) {
                sayAt = -1;
            }
            return;
        }
        String shown = line.substring(0, Math.min(line.length(), (int) (age * 45)));
        List<String> lines = Px.wrap(shown, 330, 9);
        // over the whole bottom stack: the base attack's name (BaseAttacks), and the action bar message StatusBars lifts
        // above it, with room for two lines of it (Stark 2026-11-19: the line sat on top of "Ataque de Barragem")
        int full = Math.max(1, Px.wrap(line, 330, 9).size());
        float bottom = GH - 64;
        if (BaseAttacks.stackTop != Integer.MIN_VALUE) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            float perGui = (float) (mc.getWindow().getGuiScale() / Px.S);
            bottom = Math.min(bottom, BaseAttacks.stackTop * perGui - 2 * (mc.font.lineHeight + 3) * perGui - 6);
        }
        float cx = GW / 2f, y = bottom - 8 - full * 12;
        for (int i = 0; i < 34 + full * 12; i += 2) {
            R(0, y - 14 + i, GW, 2, VEIL, 0.5f * a * Math.min(1, i / 16f));
        }
        T(who, cx, y - 10, rgb(255, 214, 110), 10, true, 0, true, a);
        goldRule(cx - 120, cx + 120, y + 3, a);
        for (int i = 0; i < lines.size(); i++) {
            T(lines.get(i), cx, y + 8 + i * 12, CREAM, 9, false, 0, true, a);
        }
    }

    private static void drawLogo(long now) {
        if (logoAt < 0) {
            return;
        }
        float a = ease((now - logoAt) / 1200f), cx = GW / 2f, cy = GH / 2f - 14;
        R(0, 0, GW, GH, BLACK, Math.min(1, a * 1.2f));
        Px.glow(cx, cy, 160, rgb(90, 50, 160), 0.35f * a);
        float k = ease((now - logoAt - 500) / 1000f);
        T("SOULS", cx, cy - 26, CREAM, 44, true, 0, true, k);
        goldRule(cx - 130, cx + 130, cy + 22, k);
        T("B A T T L E G R O U N D S", cx, cy + 28, rgb(255, 214, 110), 11, true, 0, true, k);
    }

    // ---- the teaching card

    // a gold-edged key tile; returns its width
    private static int keyTile(String k, float x, float y) {
        int w = Math.max(11, tw(k, 7, true) + 8);
        R(x, y, w, 11, rgb(20, 16, 30), 0.95f);
        int bc = mix(GOLD, BLACK, 0.2f);
        R(x + 1, y, w - 2, 1, bc, 1);
        R(x + 1, y + 10, w - 2, 1, bc, 1);
        R(x, y + 1, 1, 9, bc, 1);
        R(x + w - 1, y + 1, 1, 9, bc, 1);
        R(x + 2, y + 1, w - 4, 1, WHITE, 0.18f);
        T(k, x + w / 2f, y + 2, CREAM, 7, true, 0, false, 1);
        return w;
    }

    private static boolean repeat(String k) {
        return k.matches("\\d+x");
    }

    private static int keysWidth(String keys) {
        int w = 0;
        for (String k : keys.split(" ")) {
            w += (repeat(k) ? tw(k, 8, true) + 2 : Math.max(11, tw(k, 7, true) + 8)) + 3;
        }
        return w;
    }

    private static List<String[]> pairs(String raw) {
        List<String[]> out = new ArrayList<>();
        if (raw.isEmpty()) {
            return out;
        }
        for (String row : raw.split("\\|")) {
            int i = row.indexOf('=');
            out.add(i < 0 ? new String[] {row, ""} : new String[] {row.substring(0, i), row.substring(i + 1)});
        }
        return out;
    }

    static final class CardScreen extends Screen {
        private final String id;
        private final long at = System.currentTimeMillis();
        private boolean sent;
        private float bx, by, bw, bh;

        CardScreen(String id) {
            super(Component.translatable("intro.battlegroundsmod.card." + id));
            this.id = id;
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Px.begin(graphics, mouseX, mouseY);
            try {
                draw();
            } finally {
                Px.end();
            }
        }

        private void draw() {
            float t = ease((System.currentTimeMillis() - at) / 250f);
            String text = opt("card." + id + ".text");
            List<String[]> keys = pairs(opt("card." + id + ".keys"));
            List<String[]> cards = pairs(opt("card." + id + ".cards"));
            boolean left = !opt("card." + id + ".left").isEmpty();
            int w = cards.isEmpty() ? 210 : 300;
            List<String> lines = text.isEmpty() ? List.of() : Px.wrap(text, w - 24, 8);
            int keysH = keys.size() * 14, cardsH = cards.isEmpty() ? 0 : 46;
            int h = 26 + lines.size() * 11 + (keysH > 0 ? keysH + 6 : 0) + cardsH + 26;
            float x = left ? 18 : Math.round(GW / 2f - w / 2f), y = Math.round(GH / 2f - h / 2f);
            R(0, 0, GW, GH, VEIL, 0.45f * t);
            Px.frame(x, y, w, h, rgb(180, 140, 255), t, true, true, null);
            float yy = y + 8;
            T(getTitle().getString(), x + w / 2f, yy, CREAM, 13, true, 0, true, t);
            yy += 18;
            for (String l : lines) {
                T(l, x + w / 2f, yy, SOFT, 8, false, 0, true, t);
                yy += 11;
            }
            if (keysH > 0) {
                yy += 4;
                int mw = 0;
                for (String[] r : keys) {
                    mw = Math.max(mw, keysWidth(r[0]) + 6 + tw(r[1], 8));
                }
                float kx = Math.round(x + w / 2f - mw / 2f);
                for (String[] r : keys) {
                    float kxx = kx;
                    for (String k : r[0].split(" ")) {
                        if (repeat(k)) {
                            T(k, kxx, yy + 1, rgb(255, 214, 110), 8, true, -1, true, t);
                            kxx += tw(k, 8, true) + 5;
                        } else {
                            kxx += keyTile(k, kxx, yy) + 3;
                        }
                    }
                    T(r[1], kxx + 4, yy + 1, rgb(236, 230, 246), 8, false, -1, true, t);
                    yy += 14;
                }
            }
            if (!cards.isEmpty()) {
                int cw = 86, gap = 8;
                float x0 = Math.round(x + w / 2f - (cw * 3 + gap * 2) / 2f);
                for (int i = 0; i < cards.size(); i++) {
                    float cx = x0 + i * (cw + gap);
                    R(cx, yy, cw, 40, rgb(20, 16, 30), 0.85f);
                    R(cx, yy, cw, 1, GOLD, 0.6f);
                    Px.diamond(cx + cw / 2f, yy, 1, GOLD, 1);
                    T(cards.get(i)[0], cx + cw / 2f, yy + 5, DIM, 7, false, 0, true, t);
                    T(tr("card.vira"), cx + cw / 2f, yy + 14, MUTED, 6, false, 0, true, t);
                    T(cards.get(i)[1], cx + cw / 2f, yy + 24, rgb(255, 214, 110), 9, true, 0, true, t);
                }
                yy += 46;
            }
            String label = opt("card." + id + ".button");
            if (label.isEmpty()) {
                label = tr("card.entendi");
            }
            bw = tw(label, 8, true) + 30;
            bh = 16;
            bx = Math.round(x + w / 2f - bw / 2f);
            by = yy + 2;
            boolean over = Px.over(bx, by, bw, bh);
            Px.button(bx, by, bw, bh, GOLD, over);
            T(label, x + w / 2f, by + 4, CREAM, 8, true, 0, true, 1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
            if (System.currentTimeMillis() - at > 300 && gx >= bx && gx <= bx + bw && gy >= by && gy <= by + bh) {
                done();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (System.currentTimeMillis() - at > 300 && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE
                    || "alma".equals(id) && key == GLFW.GLFW_KEY_U)) {
                done();
                return true;
            }
            return true;
        }

        private void done() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            onClose();
            if ("alma".equals(id)) {
                // the card's button opens the Livro das Almas right there (the U)
                PowerSelectScreen.open();
            }
        }

        @Override
        public void removed() {
            if (!sent) {
                sent = true;
                // closed by a disconnect (clear() on LoggingOut): nobody to tell
                if (Minecraft.getInstance().getConnection() != null) {
                    PacketDistributor.sendToServer(new ModPayloads.IntroAck("card"));
                }
            }
            super.removed();
        }
    }
}
