package com.starkinhon.battlegroundsmod.client;

import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// The Dungeon's choices after a win (challenge/Dungeon). Two steps: first the boss's soul on offer, absorb it or refuse it (skipped
// when there is none), then 3 cards, the player picks one and the next fight starts. It can't be closed with Esc (the run waits
// for the answers). The card ids are Dungeon.OPTIONS.
public final class DungeonChoiceScreen extends Screen {
    private static final Map<String, String[]> TEXTS = Map.of(
            "manter", new String[] {"Manter", "Segue em frente com o que você tem, sem bônus."},
            "sacrificar", new String[] {"Sacrificar", "Troca a alma que você tem agora por uma SubAlma aleatória, já com todas as skills."},
            "acaso", new String[] {"Alma do Acaso", "Aposta: troca por qualquer alma do jogo, SubAlma ou divina."},
            "senzu", new String[] {"Sementes dos Deuses", "Ganha Sementes dos Deuses: curam tudo na hora."},
            "vida", new String[] {"Vida Extra", "Ganha uma vida a mais na dungeon."},
            "resistencia", new String[] {"Casca de Ki", "Resistência a dano até o fim da dungeon. Acumula."},
            "forca", new String[] {"Fúria", "Força até o fim da dungeon. Acumula."},
            "ult", new String[] {"Ki Carregado", "Começa cada luta com a ult carregada."},
            "ascensao", new String[] {"Ascensão", "Você luta um tier acima do tier da luta. Limitado."});

    private final List<String> ids;
    private final int lives;
    private final int won;
    private final int total;
    private final String soul;
    private final String offer;
    // the soul the player carries after the answer about the one on offer (the screen's own bookkeeping, for the text)
    private String current;
    // 0 = the soul on offer, 1 = the cards
    private int step;
    private boolean answered;

    private DungeonChoiceScreen(List<String> ids, int lives, int won, int total, String soul, String offer) {
        super(Component.literal("Dungeon"));
        this.ids = ids;
        this.lives = lives;
        this.won = won;
        this.total = total;
        this.soul = soul;
        this.offer = offer;
        this.current = soul;
        this.step = offer.isEmpty() ? 1 : 0;
    }

    public static void open(List<String> ids, int lives, int won, int total, String soul, String offer) {
        Minecraft.getInstance().setScreen(new DungeonChoiceScreen(ids, lives, won, total, soul, offer));
    }

    private static String[] text(String id) {
        return TEXTS.getOrDefault(id, new String[] {id, ""});
    }

    @Override
    protected void init() {
        if (step == 0) {
            int w = 150;
            int gap = 14;
            int x0 = (width - (2 * w + gap)) / 2;
            int y = height / 2 + 20;
            addRenderableWidget(Button.builder(Component.literal("Absorver"), b -> answerSoul(true))
                    .bounds(x0, y, w, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Recusar"), b -> answerSoul(false))
                    .bounds(x0 + w + gap, y, w, 22).build());
            return;
        }
        int n = ids.size();
        int w = 150;
        int gap = 14;
        int x0 = (width - (n * w + (n - 1) * gap)) / 2;
        int y = height / 2 + 30;
        for (int i = 0; i < n; i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(text(ids.get(i))[0]), b -> pick(index))
                    .bounds(x0 + i * (w + gap), y, w, 22).build());
        }
    }

    // The soul: answer the server, then show the cards.
    private void answerSoul(boolean take) {
        if (answered) {
            return;
        }
        answered = true;
        PacketDistributor.sendToServer(new ModPayloads.DungeonSoul(take));
        if (take) {
            current = offer;
        }
        step = 1;
        answered = false;
        rebuildWidgets();
    }

    private void pick(int index) {
        if (answered) {
            return;
        }
        answered = true;
        PacketDistributor.sendToServer(new ModPayloads.DungeonPick(index));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xD0101018);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.literal("Lutas vencidas: " + won + "/" + total + "     Vidas: " + "♥".repeat(Math.max(0, lives))),
                width / 2, height / 2 - 62, 0xFFFFFF);
        if (step == 0) {
            g.drawCenteredString(font, Component.literal("ALMA DE " + offer.toUpperCase()), width / 2, height / 2 - 78, 0xF2C94C);
            g.drawCenteredString(font, Component.literal(soul.isEmpty() ? "Você ainda não tem alma." : "Sua alma atual: " + soul),
                    width / 2, height / 2 - 40, 0xB0B0FF);
            g.drawCenteredString(font, Component.literal("Absorver troca a sua alma pela dele, com todas as skills."),
                    width / 2, height / 2 - 14, 0xC8C8C8);
            g.drawCenteredString(font, Component.literal("Recusar mantém a sua" + (soul.isEmpty() ? " (nenhuma, só o punho)." : ".")),
                    width / 2, height / 2 - 2, 0xC8C8C8);
            return;
        }
        g.drawCenteredString(font, Component.literal("ESCOLHA UMA RECOMPENSA"), width / 2, height / 2 - 78, 0xF2C94C);
        g.drawCenteredString(font, Component.literal(current.isEmpty() ? "Você está sem alma." : "Alma atual: " + current),
                width / 2, height / 2 - 48, 0xB0B0FF);
        int n = ids.size();
        int w = 150;
        int gap = 14;
        int x0 = (width - (n * w + (n - 1) * gap)) / 2;
        int y = height / 2 + 58;
        for (int i = 0; i < n; i++) {
            g.drawWordWrap(font, Component.literal(text(ids.get(i))[1]), x0 + i * (w + gap), y, w, 0xC8C8C8);
        }
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
