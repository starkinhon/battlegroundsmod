// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.SkillSwap;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dungeon's screens (challenge/Dungeon), in the mod's own look (Stark 2026-11-19: the old one was vanilla buttons with other
// words, "muito ridículo"): the Px grid of the approved menus (Livro dos Desafios, Diário), a dark veil over the arena, gold
// hairlines with diamonds, and big cards with their own pixel icon.
//   MODE   on arriving in the first arena: Normal or Caos (the countdown waits).
//   SOUL   after a win: the beaten one's soul, its four skills shown, Absorver or Recusar.
//   CARDS  the cards of the player's mode (Dungeon.NORMAL / Dungeon.CHAOS).
//   STEAL  Roubar Técnica: which of the boss's Z, X, C (it goes on the same key, over the player's own).
//   PICK   Alma Escolhida: which soul of the ones beaten.
//   KIT    Montar Kit: one skill of the boss (Z, X, C, B or a Shift variant), then the key of the player's kit it goes on.
// It can't be closed with Esc (the run waits for the answer); Esc only goes back from STEAL/PICK/KIT. Keys 1-4 pick too.
public final class DungeonChoiceScreen extends Screen {
    private enum Step { MODE, SOUL, CARDS, STEAL, PICK, KIT, WAIT }

    private static final int NORMAL_COLOR = 0xF2C94C, CHAOS_COLOR = 0xB04CFF, HEART = 0xE0362C;

    // card id -> name, text (%s = the beaten one's name), color
    private record Card(String name, String text, int color) {
    }

    private static final Map<String, Card> CARDS = Map.ofEntries(
            Map.entry("senzu", new Card("Semente dos Deuses", "+2 sementes. Cura tudo na hora.", 0x7AD24A)),
            Map.entry("acaso", new Card("Alma Aleatória", "Troca sua alma por uma qualquer do jogo. Pode vir o Kuririn, pode vir o Bills.", 0x5EC8FF)),
            Map.entry("escolhida", new Card("Alma Escolhida", "Fica com a alma de alguém que você já derrotou nesta dungeon.", 0x7FB2FF)),
            Map.entry("vida", new Card("Vida Extra", "Mais uma vida pra essa dungeon.", 0xE0362C)),
            Map.entry("manter", new Card("Seguir", "Fica com o que tem e vai pra próxima.", 0x9C93B4)),
            Map.entry("roubar", new Card("Roubar Técnica", "Pega o Z, X ou C de %s e põe no lugar do seu.", 0xD8352A)),
            Map.entry("roleta", new Card("Roleta", "Toda luta seu Z, X e C trocam por técnicas sorteadas de qualquer alma. Caiu, para e o sorteio fica.", 0xFF8A2A)),
            Map.entry("ginyu", new Card("Troca de Corpos", "Na próxima luta você entra com a alma de quem vai enfrentar. Ele contra ele mesmo.", 0xB04CFF)),
            Map.entry("edo", new Card("Edo Tensei", "Na próxima queda você levanta na hora e explode tudo em volta. Não perde vida.", 0x8FE3FF)),
            Map.entry("aposta", new Card("Dobro ou Nada", "Vence a próxima luta sem cair: duas cartas. Caiu: perde duas vidas.", 0xFFD23A)),
            Map.entry("tita", new Card("Titã", "Você fica gigante até o fim da dungeon.", 0xFF6A3A)),
            Map.entry("kit", new Card("Montar Kit", "Pega qualquer técnica de %s e põe na tecla que quiser. Até no B e no Shift.", 0x4CE0B0)));

    private static final Map<Character, Integer> PAL = Map.ofEntries(Map.entry('k', 0x140C10), Map.entry('w', 0xFFF6E8),
            Map.entry('g', 0x7AD24A), Map.entry('G', 0x2F7A2A), Map.entry('S', 0x9A93A6), Map.entry('b', 0x5EC8FF),
            Map.entry('c', 0x9FF0FF), Map.entry('r', 0xE0362C), Map.entry('R', 0x8E1A14), Map.entry('y', 0xFFD23A),
            Map.entry('o', 0xD08A1C), Map.entry('p', 0xB04CFF), Map.entry('P', 0xF2C2A0), Map.entry('n', 0x6B4A2A));

    private static final Map<String, String[]> ART = Map.ofEntries(
            Map.entry("senzu", new String[] {"............", "....kkkk....", "...kggggk...", "..kgwgggGk..", "..kgwggggk..", "..kggggggk..",
                "..kgggggGk..", "...kgggGk...", "...kgggGk...", "....kGGk....", ".....kk.....", "............"}),
            Map.entry("acaso", new String[] {"............", ".kkkkkkkkkk.", ".kwwwwwwwwk.", ".kwkwwwwwwk.", ".kwwwwwwwwk.", ".kwwwwkwwwk.",
                ".kwwwwwwwwk.", ".kwwwwwwkwk.", ".kwwwwwwwwk.", ".kSSSSSSSSk.", ".kkkkkkkkkk.", "............"}),
            Map.entry("escolhida", new String[] {".....k......", "....kbk.....", "....kbbk....", "...kbcbk....", "..kbbcbbk...", "..kbccwbk...",
                ".kbcwwwcbk..", ".kbcwwwcbk..", ".kbccwccbk..", "..kbbbbbk...", "...kkkkk....", "............"}),
            Map.entry("vida", new String[] {"............", "..kkk..kkk..", ".krrrkkrrrk.", ".krwrrrrrrk.", ".krwrrrrrrk.", ".krrrrrrrRk.",
                "..krrrrrRk..", "...krrrRk...", "....krRk....", ".....kk.....", "............", "............"}),
            Map.entry("manter", new String[] {"............", "......k.....", "......kk....", ".kkkkkkyk...", ".kyyyyyyyk..", ".kyyyyyyyyk.",
                ".kyyyyyyyk..", ".kkkkkkyk...", "......kk....", "......k.....", "............", "............"}),
            Map.entry("roubar", new String[] {"............", "..k.k.k.k...", ".kPkPkPkPk..", ".kPkPkPkPk..", ".kPkPkPkPk..", ".kPPPPPPPPk.",
                ".kPPPPPPPk..", "..kPPPPPPk..", "...kPPPPk...", "...krrrrk...", "...kRRRRk...", "...kkkkkk..."}),
            Map.entry("roleta", new String[] {"....kkkk....", "..kkrwrwkk..", ".krwrwrwrwk.", ".kwrwrwrwrk.", "krwrwkkwrwrk", "kwrwkyykwrwk",
                "krwrkyykrwrk", "kwrwrkkrwrwk", ".krwrwrwrwk.", ".kwrwrwrwrk.", "..kkwrwrkk..", "....kkkk...."}),
            Map.entry("ginyu", new String[] {"...k........", "..kk........", ".kpkkkkkkkk.", "kpppppppppk.", ".kpkkkkkkkk.", "..kk........",
                "........kk..", ".kkkkkkkkbk.", ".kbbbbbbbbbk", ".kkkkkkkkbk.", "........kk..", "............"}),
            Map.entry("edo", new String[] {"............", "...kkkkkk...", "..kwwwwwwk..", ".kwwwwwwwwk.", ".kwkkwwkkwk.", ".kwkkwwkkwk.",
                ".kwwwwwwwwk.", "..kwwkkwwk..", "...kwwwwk...", "...kwkwkwk..", "....kkkk....", "............"}),
            Map.entry("aposta", new String[] {"............", "....kkkk....", "..kkyyyykk..", ".kyyookyyyk.", ".kyoykkyoyk.", "kyyoyyyyyoyk",
                "kyyoyyyyyoyk", ".kyoykkyoyk.", ".kyyookyyyk.", "..kkyyyykk..", "....kkkk....", "............"}),
            Map.entry("tita", new String[] {".....kk.....", "....kyyk....", "...kyyyyk...", "..kyyyyyyk..", ".kkkkyykkkk.", "....kyyk....",
                "....kyyk....", "...kkyykk...", "..kPPPPPPk..", "..kPkPPkPk..", "..kPPPPPPk..", "..kkkkkkkk.."}),
            Map.entry("kit", new String[] {"............", ".kkkk..kkkk.", ".kggk..kbbk.", ".kggk..kbbk.", ".kkkk..kkkk.", "............",
                "............", ".kkkk..kkkk.", ".kyyk..kwwk.", ".kyyk..kwwk.", ".kkkk..kkkk.", "............"}),
            Map.entry("normal", new String[] {"..........k.", ".........kwk", "........kwk.", ".......kwk..", "......kwk...", ".....kwk....",
                "..k.kwk.....", "...kwk......", "...kok......", "..kok.k.....", ".kok........", "kk.........."}),
            Map.entry("caos", new String[] {"....kkkk....", "..kkppppkk..", ".kppkkkkppk.", ".kpk.pp.kpk.", "kpk.pkkp.kpk", "kpk.pk.kpkpk",
                "kpkp.kkp.kpk", "kpk.kppk.kpk", ".kpk....kpk.", ".kppkkkkppk.", "..kkppppkk..", "....kkkk...."}));

    // pending mode screen while the terrain loads after the teleport (opened when the loading screen goes)
    private static ModPayloads.DungeonModeAsk pendingMode;

    private Step step;
    private ModPayloads.DungeonModeAsk ask;
    private ModPayloads.DungeonChoices choices;
    private int chosen = -1;
    // Montar Kit: the boss's skill picked (its slot), -1 while none
    private int kitFrom = -1;
    private float t;
    private float stepAt;
    private long last = Util.getNanos();

    private DungeonChoiceScreen() {
        super(Component.literal("Dungeon"));
    }

    // ---------------------------------------------------------------- from the server

    public static void mode(ModPayloads.DungeonModeAsk ask) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ReceivingLevelScreen) {
            pendingMode = ask;
            return;
        }
        DungeonChoiceScreen screen = new DungeonChoiceScreen();
        screen.ask = ask;
        screen.step = Step.MODE;
        mc.setScreen(screen);
    }

    public static void choices(ModPayloads.DungeonChoices choices) {
        Minecraft mc = Minecraft.getInstance();
        DungeonChoiceScreen screen = mc.screen instanceof DungeonChoiceScreen open ? open : new DungeonChoiceScreen();
        screen.choices = choices;
        screen.go(choices.offer().isEmpty() ? Step.CARDS : Step.SOUL);
        if (mc.screen != screen) {
            mc.setScreen(screen);
        }
    }

    // Client tick: the mode screen waits for the terrain screen after the teleport.
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingMode != null && mc.player != null && !(mc.screen instanceof ReceivingLevelScreen)) {
            ModPayloads.DungeonModeAsk ask = pendingMode;
            pendingMode = null;
            mode(ask);
        }
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> clientTick());
    }

    private void go(Step next) {
        step = next;
        stepAt = t;
        chosen = -1;
    }

    // ---------------------------------------------------------------- answers

    private void pickMode(boolean chaos) {
        click();
        PacketDistributor.sendToServer(new ModPayloads.DungeonMode(chaos));
        onClose();
    }

    private void answerSoul(boolean take) {
        click();
        PacketDistributor.sendToServer(new ModPayloads.DungeonSoul(take));
        go(Step.WAIT);
    }

    private void pickCard(int index) {
        String id = choices.ids().get(index);
        if ("roubar".equals(id)) {
            click();
            chosen = index;
            step = Step.STEAL;
            stepAt = t;
            return;
        }
        if ("escolhida".equals(id)) {
            click();
            chosen = index;
            step = Step.PICK;
            stepAt = t;
            return;
        }
        if ("kit".equals(id)) {
            click();
            chosen = index;
            kitFrom = -1;
            step = Step.KIT;
            stepAt = t;
            return;
        }
        send(index, -1);
    }

    private void send(int index, int arg) {
        click();
        PacketDistributor.sendToServer(new ModPayloads.DungeonPick(index, arg));
        if (choices.bonus()) {
            go(Step.WAIT);
        } else {
            onClose();
        }
    }

    private void back() {
        click();
        go(Step.CARDS);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6f));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // drawn on the grid in render
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getNanos();
        t += Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        Px.begin(graphics, mouseX, mouseY);
        Px.hits.clear();
        try {
            draw();
            Px.drawTip();
        } finally {
            Px.end();
        }
    }

    private void draw() {
        float in = Px.easeOut((t - stepAt) / 0.35f);
        float fade = Px.easeOut(t / 0.3f);
        // the veil over the arena, darker at the edges
        Px.R(0, 0, Px.GW, Px.GH, Px.VEIL, 0.72f * fade);
        int accent = accent();
        Px.glow(Px.GW / 2f, Px.GH / 2f, Px.GW * 0.55f, accent, 0.10f * fade);
        float cx = Px.GW / 2f, cy = Px.GH / 2f;
        header(cx, cy - 122, accent, fade);
        switch (step) {
            case MODE -> drawMode(cx, cy, in);
            case SOUL -> drawSoul(cx, cy, in);
            case CARDS -> drawCards(cx, cy, in);
            case STEAL -> drawSteal(cx, cy, in);
            case PICK -> drawPick(cx, cy, in);
            case KIT -> drawKit(cx, cy, in);
            case WAIT -> Px.T("...", cx, cy, Px.MUTED, 12, true, 0, true, 0.5f + 0.5f * (float) Math.sin(t * 6));
        }
    }

    private int accent() {
        if (step == Step.MODE) {
            return 0xD8352A;
        }
        return choices != null && choices.chaos() ? CHAOS_COLOR : NORMAL_COLOR;
    }

    private void header(float cx, float y, int accent, float a) {
        boolean chaos = choices != null && choices.chaos();
        String tag = step == Step.MODE ? "DUNGEON" : chaos ? "DUNGEON · CAOS" : "DUNGEON · NORMAL";
        Px.T(tag, cx, y, Px.mix(accent, Px.WHITE, 0.2f), 8, true, 0, true, a);
        String title = switch (step) {
            case MODE -> "ESCOLHA O MODO";
            case SOUL -> "VITÓRIA";
            case CARDS -> choices.bonus() ? "ESCOLHA UMA CARTA · 1 DE 2" : "ESCOLHA UMA CARTA";
            case STEAL -> "ROUBAR DE " + name(choices.boss()).toUpperCase();
            case PICK -> "QUAL ALMA?";
            case KIT -> "MONTAR KIT";
            case WAIT -> "";
        };
        Px.T(title, cx, y + 11, Px.rgb(255, 236, 180), 16, true, 0, true, a);
        Px.centerLine(cx - 110, y + 31, 220, Px.GOLD, 0.8f * a, 0.35f);
        Px.diamond(cx, y + 31, 2, Px.GOLD, a);
        Px.diamond(cx, y + 31, 1, Px.WHITE, 0.9f * a);
        // fights and lives
        int lives = step == Step.MODE ? ask.lives() : choices.lives();
        String fights = step == Step.MODE ? ask.total() + " lutas" : "Vitórias " + choices.won() + "/" + choices.total();
        int w = Px.tw(fights, 8) + 10 + lives * 8;
        float x = cx - w / 2f;
        Px.T(fights, x, y + 37, Px.MUTED, 8, false, -1, true, a);
        x += Px.tw(fights, 8) + 10;
        for (int i = 0; i < lives; i++) {
            heart(x + i * 8, y + 38, a);
        }
    }

    private static void heart(float x, float y, float a) {
        String[] h = {".rr.rr.", "rwrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."};
        for (int j = 0; j < h.length; j++) {
            for (int i = 0; i < h[j].length(); i++) {
                char ch = h[j].charAt(i);
                if (ch != '.') {
                    Px.P(x + i, y + j, ch == 'w' ? 0xFFB0A8 : HEART, a);
                }
            }
        }
    }

    // MODE: two big cards.
    private void drawMode(float cx, float cy, float in) {
        float w = 176, h = 156, gap = 26;
        float y = cy - 58 + (1 - in) * 14;
        modeCard(cx - gap / 2 - w, y, w, h, false, in);
        modeCard(cx + gap / 2, y, w, h, true, in);
        if (ask.group() > 1) {
            Px.T("Cada jogador escolhe o seu. A luta começa quando todos escolherem.", cx, y + h + 10, Px.MUTED, 8, false, 0, true, in);
        }
    }

    private void modeCard(float x, float y, float w, float h, boolean chaos, float a) {
        boolean hot = Px.over(x, y, w, h);
        int color = chaos ? CHAOS_COLOR : NORMAL_COLOR;
        float lift = hot ? 3 : 0;
        y -= lift;
        if (hot) {
            Px.glow(x + w / 2, y + h / 2, w * 0.75f, color, 0.28f);
        }
        Px.frame(x, y, w, h, color, 0.94f * a, true, true, null);
        // icon
        float pulse = chaos ? (float) Math.sin(t * 5) : 0;
        if (chaos && hot) {
            // a glitch: the icon twice, red and blue, shaking
            art("caos", x + w / 2 - 18 + 1 + pulse, y + 14, 3, 0xFF3A3A, 0.35f * a);
            art("caos", x + w / 2 - 18 - 1 - pulse, y + 14, 3, 0x3AC8FF, 0.35f * a);
        }
        art(chaos ? "caos" : "normal", x + w / 2 - 18, y + 14, 3, -1, a);
        String title = chaos ? "CAOS" : "NORMAL";
        if (chaos && hot) {
            Px.T(title, x + w / 2 + 1, y + 58, 0xFF3A3A, 16, true, 0, false, 0.5f * a);
            Px.T(title, x + w / 2 - 1, y + 58, 0x3AC8FF, 16, true, 0, false, 0.5f * a);
        }
        Px.T(title, x + w / 2, y + 58, Px.mix(color, Px.WHITE, 0.25f), 16, true, 0, true, a);
        String text = chaos
                ? "As técnicas se misturam. Venceu alguém? Rouba o Z, X ou C dele. As cartas são maluquice."
                : "No seco, sem bônus. Venceu: absorve ou recusa a alma e pega Semente, Alma Aleatória ou Alma Escolhida.";
        float ly = y + 80;
        for (String line : Px.wrap(text, w - 22, 8)) {
            Px.T(line, x + w / 2, ly, Px.TEXT, 8, false, 0, true, a);
            ly += 10;
        }
        float bw = 90, bh = 15, bx = x + w / 2 - bw / 2, by = y + h - bh - 8;
        Px.button(bx, by, bw, bh, color, hot);
        Px.T("ESCOLHER", x + w / 2, by + 4, Px.WHITE, 8, true, 0, true, a);
        Px.T(chaos ? "2" : "1", x + w - 8, y + 6, Px.MUTED, 8, true, 0, true, 0.7f * a);
        Px.hit(x, y, w, h, () -> pickMode(chaos));
    }

    // SOUL: the beaten one's soul, its skills, absorb or refuse.
    private void drawSoul(float cx, float cy, float in) {
        Power soul = Powers.get(choices.offer());
        int color = soul == null ? Px.GOLD : Px.ink(soul.color());
        float w = 260, h = 150, x = cx - w / 2, y = cy - 66 + (1 - in) * 12;
        Px.glow(cx, y + 40, 90, color, 0.3f * in);
        Px.frame(x, y, w, h, color, 0.95f * in, true, true, null);
        Px.T("ALMA DE " + name(choices.offer()).toUpperCase(), cx, y + 12, color, 14, true, 0, true, in);
        // its skills (what absorbing gives, all awake)
        if (soul != null) {
            List<Integer> slots = soul.slots();
            float iw = 26, gap = 6, sx = cx - (slots.size() * iw + (slots.size() - 1) * gap) / 2f;
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                float ix = sx + i * (iw + gap), iy = y + 34;
                Px.R(ix - 1, iy - 1, iw + 2, iw + 2, Px.mix(Px.VEIL, color, 0.4f), in);
                SkillIcons.draw(Px.g, Px.font, soul, slot, Math.round(ix), Math.round(iy), Math.round(iw), 1f);
                String key = keyName(soul, slot);
                Px.R(ix - 2, iy - 3, Px.tw(key, 7) + 3, 8, Px.VEIL, 0.95f * in);
                Px.T(key, ix, iy - 3, Px.WHITE, 7, true, -1, false, in);
                List<String> tip = List.of(soul.abilityName(slot).getString());
                Px.hit(ix, iy, iw, iw, null, tip, List.of(color));
            }
        }
        String now = choices.soul().isEmpty() ? "Você está sem alma: só o punho." : "Sua alma agora: " + name(choices.soul());
        Px.T(now, cx, y + 70, Px.MUTED, 8, false, 0, true, in);
        Px.T("Absorver troca a sua pela dele, com tudo liberado.", cx, y + 82, Px.TEXT, 8, false, 0, true, in);
        float bw = 96, bh = 17, gap = 18, by = y + h - bh - 14;
        float ax = cx - gap / 2 - bw, rx = cx + gap / 2;
        boolean hotA = Px.over(ax, by, bw, bh), hotR = Px.over(rx, by, bw, bh);
        Px.button(ax, by, bw, bh, soul == null ? Px.GOLD : soul.color(), hotA);
        Px.T("ABSORVER", ax + bw / 2, by + 5, Px.WHITE, 8, true, 0, true, in);
        Px.button(rx, by, bw, bh, 0x5A5068, hotR);
        Px.T("RECUSAR", rx + bw / 2, by + 5, Px.TEXT, 8, true, 0, true, in);
        Px.T("1", ax + 4, by - 9, Px.MUTED, 7, true, -1, true, 0.6f * in);
        Px.T("2", rx + 4, by - 9, Px.MUTED, 7, true, -1, true, 0.6f * in);
        Px.hit(ax, by, bw, bh, () -> answerSoul(true));
        Px.hit(rx, by, bw, bh, () -> answerSoul(false));
    }

    // CARDS: the offer, one tall card each.
    private void drawCards(float cx, float cy, float in) {
        List<String> ids = choices.ids();
        int n = ids.size();
        float gap = 14;
        // four cards (Montar Kit) on a small screen: narrower and a bit taller, so the texts still fit
        float w = Math.min(128, (Px.GW - 24 - (n - 1) * gap) / n);
        float h = w < 128 ? 176 : 164;
        float x0 = cx - (n * w + (n - 1) * gap) / 2f;
        String soulLine = choices.soul().isEmpty() ? "Sem alma" : "Alma: " + name(choices.soul());
        Px.T(soulLine, cx, cy - 60 + h + 6, Px.MUTED, 8, false, 0, true, in);
        for (int i = 0; i < n; i++) {
            float k = Px.easeBack(Px.clamp((t - stepAt - i * 0.07f) / 0.4f, 0, 1));
            card(i, ids.get(i), x0 + i * (w + gap), cy - 60 + (1 - k) * 24, w, h, Math.min(in, k));
        }
    }

    private void card(int index, String id, float x, float y, float w, float h, float a) {
        Card c = CARDS.getOrDefault(id, new Card(id, "", Px.MUTED));
        boolean chaos = choices.chaos();
        boolean hot = Px.over(x, y, w, h);
        y -= hot ? 4 : 0;
        int color = c.color();
        if (hot) {
            Px.glow(x + w / 2, y + h / 2, w * 0.8f, color, 0.3f);
        }
        Px.frame(x, y, w, h, color, 0.95f * a, true, true, null);
        if (chaos) {
            // the Caos cards: a jagged colored edge on top that crawls
            for (int i = 2; i < w - 2; i += 2) {
                float v = (float) Math.sin(i * 0.7f + t * 7) * 1.5f + 1.5f;
                Px.R(x + i, y + 2, 1, v, color, 0.55f * a);
            }
        }
        Px.glow(x + w / 2, y + 34, 26, color, 0.35f * a);
        art(id, x + w / 2 - 18, y + 16, 3, -1, a);
        float ty = y + 60;
        for (String line : Px.wrap(c.name().toUpperCase(), w - 14, 10, true)) {
            Px.T(line, x + w / 2, ty, Px.mix(color, Px.WHITE, 0.3f), 10, true, 0, true, a);
            ty += 12;
        }
        Px.centerLine(x + 14, ty + 1, w - 28, color, 0.6f * a, 0.3f);
        ty += 7;
        String text = c.text().contains("%s") ? String.format(c.text(), name(choices.boss())) : c.text();
        for (String line : Px.wrap(text, w - 16, 8)) {
            Px.T(line, x + w / 2, ty, Px.TEXT, 8, false, 0, true, a);
            ty += 10;
        }
        Px.smallFrame(x + w / 2 - 8, y + h - 16, 16, 11, color, hot, a);
        Px.T(String.valueOf(index + 1), x + w / 2, y + h - 14, Px.WHITE, 8, true, 0, true, a);
        Px.hit(x, y, w, h, () -> pickCard(index));
    }

    // STEAL: the beaten one's Z, X, C, each over the player's own skill on that key.
    private void drawSteal(float cx, float cy, float in) {
        Power boss = Powers.get(choices.boss());
        Power own = Powers.get(choices.soul());
        if (boss == null) {
            back();
            return;
        }
        int color = Px.ink(boss.color());
        float w = 128, h = 132, gap = 14;
        float x0 = cx - (3 * w + 2 * gap) / 2f;
        Px.T("Vai na mesma tecla, no lugar da sua.", cx, cy - 74, Px.MUTED, 8, false, 0, true, in);
        for (int k = 0; k < SkillSwap.KEYS; k++) {
            float x = x0 + k * (w + gap), y = cy - 60 + (1 - in) * 14;
            boolean has = PowerLogic.ability(boss, k) != null;
            boolean hot = has && Px.over(x, y, w, h);
            float yy = y - (hot ? 4 : 0);
            if (hot) {
                Px.glow(x + w / 2, yy + h / 2, w * 0.8f, color, 0.3f);
            }
            float a = has ? in : 0.35f * in;
            Px.frame(x, yy, w, h, color, 0.95f * a, true, true, null);
            String key = keyName(boss, k);
            Px.T(key, x + w / 2, yy + 8, Px.WHITE, 12, true, 0, true, a);
            float iw = 34, ix = x + w / 2 - iw / 2, iy = yy + 24;
            Px.R(ix - 1, iy - 1, iw + 2, iw + 2, Px.mix(Px.VEIL, color, 0.5f), a);
            SkillIcons.draw(Px.g, Px.font, boss, k, Math.round(ix), Math.round(iy), Math.round(iw), has ? 1f : 0.3f);
            float ty = iy + iw + 8;
            for (String line : Px.wrap(boss.abilityName(k).getString(), w - 14, 9, true)) {
                Px.T(line, x + w / 2, ty, Px.mix(color, Px.WHITE, 0.3f), 9, true, 0, true, a);
                ty += 11;
            }
            if (own != null && Minecraft.getInstance().player != null) {
                String out = "Sai: " + SkillSwap.name(Minecraft.getInstance().player, own, k).getString();
                for (String line : Px.wrap(out, w - 14, 7)) {
                    Px.T(line, x + w / 2, ty + 2, Px.MUTED, 7, false, 0, true, a);
                    ty += 9;
                }
            }
            int slot = k;
            if (has) {
                Px.hit(x, yy, w, h, () -> send(chosen, slot));
            }
        }
        backButton(cx, cy + 88, in);
    }

    // KIT: the boss's skills on top (normal row, Shift row), the player's kit below (Z X C B, Shift+Z X C B). A skill first, then
    // the key: what is on that key now goes away.
    private static final int[] KIT_ROW = {0, 1, 2, PowerData.SLOT_EXTRA};

    private void drawKit(float cx, float cy, float in) {
        Power boss = Powers.get(choices.boss());
        Power own = Powers.get(choices.soul());
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (boss == null || player == null) {
            back();
            return;
        }
        int color = Px.ink(boss.color());
        float w = 100, h = 30, gx = 6, gy = 5;
        float x0 = cx - (4 * w + 3 * gx) / 2f;
        float lift = (1 - in) * 10;
        Px.T(kitFrom < 0 ? "1. Escolha a técnica de " + name(choices.boss()) : "2. Escolha a tecla do seu kit",
                cx, cy - 76, Px.mix(color, Px.WHITE, 0.4f), 8, true, 0, true, in);
        // the boss's skills
        for (int row = 0; row < 2; row++) {
            for (int c = 0; c < 4; c++) {
                int slot = row == 0 ? KIT_ROW[c] : com.starkinhon.battlegroundsmod.power.SkillSwap.shiftOf(KIT_ROW[c]);
                float x = x0 + c * (w + gx), y = cy - 66 + row * (h + gy) + lift;
                boolean has = com.starkinhon.battlegroundsmod.challenge.Dungeon.kitSource(boss, slot);
                String label = boss.abilityName(slot).getString();
                tile(x, y, w, h, color, has, kitFrom == slot, keyLabel(slot), has ? label : "—", boss, slot, in,
                        has ? () -> {
                            click();
                            kitFrom = slot;
                        } : null);
            }
        }
        // the player's kit
        Px.centerLine(cx - 150, cy + 4, 300, Px.GOLD, 0.5f * in, 0.3f);
        Px.T("Seu kit", cx, cy + 8, Px.MUTED, 8, false, 0, true, in);
        boolean ready = kitFrom >= 0;
        for (int row = 0; row < 2; row++) {
            for (int c = 0; c < 4; c++) {
                int slot = row == 0 ? KIT_ROW[c] : com.starkinhon.battlegroundsmod.power.SkillSwap.shiftOf(KIT_ROW[c]);
                float x = x0 + c * (w + gx), y = cy + 20 + row * (h + gy) + lift;
                com.starkinhon.battlegroundsmod.power.SkillSwap.Ref ref = com.starkinhon.battlegroundsmod.power.SkillSwap.ref(player, slot);
                Power shown = ref != null ? ref.power() : own;
                int from = ref != null ? ref.slot() : slot;
                boolean filled = shown != null && PowerLogic.ability(shown, from) != null;
                String now = filled ? shown.abilityName(from).getString() : "vazio";
                int tint = filled ? Px.ink(shown.color()) : Px.MUTED;
                int target = slot;
                tile(x, y, w, h, ready ? color : tint, true, false, keyLabel(slot), now, filled ? shown : null, from, in,
                        ready ? () -> send(chosen, (kitFrom << 4) | target) : null);
            }
        }
        backButton(cx, cy + 94, in);
    }

    // One small square of the kit screen: key on top, icon on the left, the skill's name; onClick null = not clickable.
    private void tile(float x, float y, float w, float h, int color, boolean on, boolean selected, String key, String text, Power icon,
                      int slot, float in, Runnable onClick) {
        boolean hot = onClick != null && Px.over(x, y, w, h);
        float a = on ? in : 0.35f * in;
        if (hot || selected) {
            Px.glow(x + w / 2, y + h / 2, w * 0.6f, color, selected ? 0.4f : 0.25f);
        }
        Px.smallFrame(x, y, w, h, color, hot || selected, a);
        float iw = 22, ix = x + 4, iy = y + (h - iw) / 2;
        Px.R(ix - 1, iy - 1, iw + 2, iw + 2, Px.mix(Px.VEIL, color, 0.4f), a);
        if (icon != null) {
            SkillIcons.draw(Px.g, Px.font, icon, slot, Math.round(ix), Math.round(iy), Math.round(iw), on ? 1f : 0.3f);
        }
        float tx = ix + iw + 4;
        Px.T(key, tx, y + 4, Px.WHITE, 7, true, -1, true, a);
        Px.T(Px.fit(text, w - (tx - x) - 4, 7), tx, y + 15, selected ? Px.WHITE : Px.mix(color, Px.WHITE, 0.35f), 7, false, -1, true, a);
        // the full name on hover (it is cut to fit the square)
        Px.hit(x, y, w, h, onClick, List.of(key + "  " + text), List.of(color));
    }

    private static String keyLabel(int slot) {
        return com.starkinhon.battlegroundsmod.challenge.Dungeon.keyLabel(slot);
    }

    // PICK: the souls already beaten.
    private void drawPick(float cx, float cy, float in) {
        List<String> souls = choices.beaten();
        if (souls.isEmpty()) {
            back();
            return;
        }
        int cols = Math.min(4, souls.size());
        float w = 104, h = 24, gx = 8, gy = 8;
        int rows = (souls.size() + cols - 1) / cols;
        float x0 = cx - (cols * w + (cols - 1) * gx) / 2f;
        float y0 = cy - 50 - Math.max(0, rows - 4) * (h + gy) / 2f;
        for (int i = 0; i < souls.size(); i++) {
            Power p = Powers.get(souls.get(i));
            int color = p == null ? Px.MUTED : Px.ink(p.color());
            float x = x0 + (i % cols) * (w + gx), y = y0 + (i / cols) * (h + gy) + (1 - in) * 10;
            boolean hot = Px.over(x, y, w, h);
            if (hot) {
                Px.glow(x + w / 2, y + h / 2, w * 0.6f, color, 0.3f);
            }
            Px.smallFrame(x, y, w, h, color, hot, in);
            Px.diamond(x + 8, y + h / 2, 2, color, in);
            Px.T(Px.fit(name(souls.get(i)), w - 20, 9), x + 15, y + 8, hot ? Px.WHITE : Px.mix(color, Px.WHITE, 0.3f), 9, true, -1, true, in);
            int index = i;
            Px.hit(x, y, w, h, () -> send(chosen, index));
        }
        backButton(cx, cy + 88, in);
    }

    private void backButton(float cx, float y, float a) {
        float bw = 80, bh = 15, bx = cx - bw / 2;
        Px.button(bx, y, bw, bh, 0x5A5068, Px.over(bx, y, bw, bh));
        Px.T("VOLTAR", cx, y + 4, Px.TEXT, 8, true, 0, true, a);
        Px.hit(bx, y, bw, bh, this::back);
    }

    // A 12 x 12 pixel icon, scale grid pixels per pixel; tint >= 0 draws it in one color (the Caos glitch).
    private static void art(String id, float x, float y, int scale, int tint, float a) {
        String[] rows = ART.get(id);
        if (rows == null) {
            return;
        }
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                Integer c = PAL.get(rows[j].charAt(i));
                if (c != null) {
                    Px.R(x + i * scale, y + j * scale, scale, scale, tint >= 0 ? tint : c, a);
                }
            }
        }
    }

    private static String name(String powerId) {
        Power p = Powers.get(powerId);
        return p == null ? powerId : p.name().getString();
    }

    private static String keyName(Power power, int slot) {
        if (PowerData.isUlt(slot)) {
            return "V";
        }
        return switch (slot) {
            case 0 -> "Z";
            case 1 -> "X";
            case 2 -> "C";
            case PowerData.SLOT_EXTRA -> "B";
            default -> "?";
        };
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && step == Step.KIT && kitFrom >= 0) {
            // first back to the skill choice
            click();
            kitFrom = -1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (step == Step.STEAL || step == Step.PICK || step == Step.KIT)) {
            back();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 && t - stepAt > 0.25f) {
            int i = keyCode - GLFW.GLFW_KEY_1;
            switch (step) {
                case MODE -> {
                    if (i < 2) {
                        pickMode(i == 1);
                    }
                }
                case SOUL -> {
                    if (i < 2) {
                        answerSoul(i == 0);
                    }
                }
                case CARDS -> {
                    if (i < choices.ids().size()) {
                        pickCard(i);
                    }
                }
                case STEAL -> {
                    Power boss = Powers.get(choices.boss());
                    if (i < SkillSwap.KEYS && boss != null && PowerLogic.ability(boss, i) != null) {
                        send(chosen, i);
                    }
                }
                case PICK -> {
                    if (i < choices.beaten().size()) {
                        send(chosen, i);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
