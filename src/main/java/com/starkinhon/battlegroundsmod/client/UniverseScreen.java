package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// Where to live (challenge/Universes): one card per universe with its name, its places and how many characters its
// Livro dos Desafios holds. first = the player has no universe yet: the screen can't be closed until they choose
// ("there is no first universe", Stark 2026-11-10). Otherwise it comes from a Cristal Multiversal and the universe the
// player stands in is greyed out.
public class UniverseScreen extends Screen {
    private static final int TEXT = 0xFFE6E0EE;
    private static final int MUTED = 0xFF9A93A6;

    private record Card(ChallengeSpec.Universe universe, boolean here, int x, int y, int w, int h) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final boolean first;
    private final List<Card> cards = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private int opened;

    public UniverseScreen(boolean first) {
        super(Component.translatable(first ? "screen.battlegroundsmod.universes.first" : "screen.battlegroundsmod.universes.travel"));
        this.first = first;
    }

    @Override
    protected void init() {
        cards.clear();
        Minecraft mc = Minecraft.getInstance();
        String here = mc.player == null ? null : Universes.universeOf(mc.player.level().dimension());
        List<ChallengeSpec.Universe> universes = ChallengeSpec.get().universos();
        int margin = 20;
        int columns = width >= 560 ? 3 : width >= 360 ? 2 : 1;
        int gap = 10;
        int cardW = Math.min(180, (width - margin * 2 - gap * (columns - 1)) / columns);
        int cardH = 86;
        int totalW = columns * cardW + (columns - 1) * gap;
        int left = (width - totalW) / 2;
        int top = 62;
        for (int i = 0; i < universes.size(); i++) {
            ChallengeSpec.Universe universe = universes.get(i);
            int row = i / columns;
            int inRow = Math.min(columns, universes.size() - row * columns);
            // A last row with fewer cards is centered.
            int rowLeft = left + (columns - inRow) * (cardW + gap) / 2;
            cards.add(new Card(universe, universe.id().equals(here), rowLeft + (i % columns) * (cardW + gap), top + row * (cardH + gap), cardW, cardH));
        }
        int rows = (universes.size() + columns - 1) / columns;
        contentHeight = top + rows * (cardH + gap) + 30;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - height));
    }

    @Override
    public void tick() {
        opened++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xF0060410, 0xFF020108);
        float fade = Mth.clamp((opened + partialTick) / 12f, 0, 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        GuiDraw.centeredText(graphics, font, title, width / 2f, 14, 1.7f, GuiDraw.argb((int) (255 * fade), 0xF2E6FF), true);
        Component sub = Component.translatable(first ? "screen.battlegroundsmod.universes.first.sub" : "screen.battlegroundsmod.universes.travel.sub");
        GuiDraw.centeredText(graphics, font, sub, width / 2f, 36, 1f, MUTED, false);
        int my = mouseY + scroll;
        for (Card card : cards) {
            drawCard(graphics, card, !card.here() && card.hit(mouseX, my));
        }
        graphics.pose().popPose();
    }

    private void drawCard(GuiGraphics graphics, Card card, boolean hover) {
        ChallengeSpec.Universe universe = card.universe();
        int x1 = card.x(), y1 = card.y(), x2 = x1 + card.w(), y2 = y1 + card.h();
        int color = card.here() ? 0x5A5560 : universe.color();
        graphics.fillGradient(x1, y1, x2, y2, hover ? 0xF0261A34 : 0xE0140E1C, 0xF0080510);
        GuiDraw.ornateFrame(graphics, x1, y1, x2, y2, GuiDraw.argb(hover ? 240 : 150, GuiDraw.mix(color, 0x000000, 0.2f)),
                GuiDraw.argb(255, GuiDraw.mix(color, 0xFFFFFF, 0.35f)));
        GuiDraw.text(graphics, font, Component.literal(universe.nome()), x1 + 7, y1 + 7, 1.3f, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.25f), true);
        int y = y1 + 22;
        String desc = universe.descricao() != null ? universe.descricao() : "";
        for (FormattedCharSequence line : font.split(Component.literal(desc), card.w() - 14)) {
            if (y > y2 - 22) {
                break;
            }
            graphics.drawString(font, line, x1 + 7, y, card.here() ? MUTED : TEXT, false);
            y += 9;
        }
        Component count = Component.translatable("screen.battlegroundsmod.universes.count", universe.desafios().size());
        GuiDraw.text(graphics, font, count, x1 + 7, y2 - 20, 0.75f, MUTED, false);
        Component action = Component.translatable(card.here() ? "screen.battlegroundsmod.universes.here"
                : first ? "screen.battlegroundsmod.universes.born" : "screen.battlegroundsmod.universes.go");
        GuiDraw.text(graphics, font, action, x1 + 7, y2 - 11, 0.8f, hover ? 0xFF000000 | color : MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && opened > 6) {
            double my = mouseY + scroll;
            for (Card card : cards) {
                if (!card.here() && card.hit(mouseX, my)) {
                    PacketDistributor.sendToServer(new ModPayloads.PickUniverse(card.universe().id()));
                    Minecraft.getInstance().setScreen(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) (scrollY * 18), 0, Math.max(0, contentHeight - height));
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !first;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
