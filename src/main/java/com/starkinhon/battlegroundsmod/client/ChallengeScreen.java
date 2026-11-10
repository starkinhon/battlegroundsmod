package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.BossSpec;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.skill.ChallengeSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// A universe's Livro dos Desafios: one card per character of that universe (its SubAlmas and its bosses) with its name,
// tier and what its power is about. Clicking a card goes to that character's arena (challenge/ChallengeArenas); a beaten
// character's card is stamped DERROTADO. A boss's card stays locked until some of the universe's SubAlmas are beaten
// (ChallengeArenas.locked; free in arena mode and flat worlds). Inside the arenas the book also offers the way back.
public class ChallengeScreen extends Screen {
    private static final int TEXT = 0xFFE6E0EE;
    private static final int MUTED = 0xFF9A93A6;
    private static final int STAMP = 0xD8352A;

    private record Card(String id, Component name, int tier, int color, String foco, boolean items, int x, int y, int w, int h) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final String universeId;
    private final List<Card> cards = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private int backX1, backY1, backX2, backY2;

    public ChallengeScreen(String universe) {
        super(Component.translatable("screen.battlegroundsmod.challenges"));
        this.universeId = universe;
    }

    private ChallengeSpec.Universe universe() {
        return ChallengeSpec.get().universe(universeId);
    }

    private boolean inArena() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.level().dimension().equals(ChallengeArenas.ARENAS);
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        cards.clear();
        ChallengeSpec.Universe universe = universe();
        if (universe == null) {
            return;
        }
        int margin = 20;
        int columns = width >= 520 ? 3 : width >= 340 ? 2 : 1;
        int gap = 8;
        int cardW = Math.min(170, (width - margin * 2 - gap * (columns - 1)) / columns);
        int cardH = 78;
        int totalW = columns * cardW + (columns - 1) * gap;
        int left = (width - totalW) / 2;
        int top = 54;
        int i = 0;
        for (ChallengeSpec.Challenge challenge : universe.desafios()) {
            int col = i % columns;
            int row = i / columns;
            Component name = Component.literal(challenge.id());
            int tier = Tiers.HUMANO;
            int color = universe.color();
            try {
                BossSpec spec = BossSpec.get(challenge.id());
                Power power = Powers.get(spec.poder());
                if (power != null) {
                    name = power.name();
                    color = power.color();
                }
                tier = spec.tier() != null ? spec.tier() : Tiers.HUMANO;
            } catch (RuntimeException ignored) {
            }
            cards.add(new Card(challenge.id(), name, tier, color, challenge.foco(), challenge.itens() != null && !challenge.itens().isEmpty(),
                    left + col * (cardW + gap), top + row * (cardH + gap), cardW, cardH));
            i++;
        }
        int rows = (cards.size() + columns - 1) / columns;
        contentHeight = top + rows * (cardH + gap) + 30;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - height));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xE0080510, 0xF0030206);
        ChallengeSpec.Universe universe = universe();
        if (universe == null) {
            return;
        }
        int accent = universe.color();
        List<String> wins = minecraft != null && minecraft.player != null ? minecraft.player.getData(ModAttachments.ARENA_WINS) : List.of();

        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        GuiDraw.centeredText(graphics, font, title, width / 2f, 12, 1.6f, 0xFF000000 | GuiDraw.mix(accent, 0xFFFFFF, 0.3f), true);
        int beaten = 0;
        for (Card card : cards) {
            if (wins.contains(card.id())) {
                beaten++;
            }
        }
        Component sub = Component.literal(universe.nome()).append(Component.literal("  ·  ").withStyle(s -> s.withColor(MUTED)))
                .append(Component.translatable("screen.battlegroundsmod.challenges.count", beaten, cards.size()));
        GuiDraw.centeredText(graphics, font, sub, width / 2f, 32, 1f, TEXT, true);

        int my = mouseY + scroll;
        int needed = ChallengeArenas.subsNeeded(universe);
        for (Card card : cards) {
            boolean locked = ChallengeArenas.locked(wins, card.id(), !Progression.clientOn);
            drawCard(graphics, card, wins.contains(card.id()), locked, needed, !locked && card.hit(mouseX, my));
        }
        GuiDraw.centeredText(graphics, font, Component.translatable("screen.battlegroundsmod.challenges.hint"), width / 2f,
                contentHeight - 22, 0.9f, MUTED, false);
        graphics.pose().popPose();

        backX1 = backX2 = 0;
        if (inArena()) {
            Component back = Component.translatable("screen.battlegroundsmod.challenges.back");
            int w = font.width(back) + 16;
            backX1 = width - w - 8;
            backY1 = 8;
            backX2 = width - 8;
            backY2 = 24;
            boolean hover = mouseX >= backX1 && mouseX < backX2 && mouseY >= backY1 && mouseY < backY2;
            graphics.fill(backX1, backY1, backX2, backY2, hover ? 0xF0402030 : 0xD0201018);
            GuiDraw.frame(graphics, backX1, backY1, backX2, backY2, GuiDraw.argb(220, accent));
            graphics.drawString(font, back, backX1 + 8, backY1 + 4, TEXT, true);
        }
    }

    private void drawCard(GuiGraphics graphics, Card card, boolean won, boolean locked, int needed, boolean hover) {
        int x1 = card.x(), y1 = card.y(), x2 = card.x() + card.w(), y2 = card.y() + card.h();
        int color = locked ? GuiDraw.mix(card.color(), 0x403A48, 0.75f) : card.color();
        graphics.fillGradient(x1, y1, x2, y2, hover ? 0xF0221830 : 0xE0140E1C, 0xF0080510);
        GuiDraw.ornateFrame(graphics, x1, y1, x2, y2, GuiDraw.argb(hover ? 230 : 150, GuiDraw.mix(color, 0x000000, 0.2f)),
                GuiDraw.argb(255, GuiDraw.mix(color, 0xFFFFFF, 0.35f)));
        graphics.drawString(font, card.name(), x1 + 6, y1 + 6, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.25f), true);
        Component tier = Component.literal("Tier " + card.tier() + " · ").append(Tiers.name(card.tier()));
        graphics.drawString(font, tier, x1 + 6, y1 + 17, MUTED, false);
        if (card.items()) {
            Component items = Component.translatable("screen.battlegroundsmod.challenges.items");
            GuiDraw.rightText(graphics, font, items, x2 - 6, y1 + 17, 0.75f, 0xFFF2C94C, false);
        }
        int y = y1 + 30;
        for (FormattedCharSequence line : font.split(Component.literal(card.foco()), card.w() - 12)) {
            if (y > y2 - 18) {
                break;
            }
            graphics.drawString(font, line, x1 + 6, y, TEXT, false);
            y += 9;
        }
        Component action = locked ? Component.translatable("screen.battlegroundsmod.challenges.locked", needed)
                : Component.translatable(won ? "screen.battlegroundsmod.challenges.again" : "screen.battlegroundsmod.challenges.go");
        GuiDraw.text(graphics, font, action, x1 + 6, y2 - 11, 0.75f, locked ? 0xFFB0505A : hover ? 0xFF000000 | color : MUTED, false);
        if (won) {
            // The stamp, tilted across the card.
            Component stamp = Component.translatable("screen.battlegroundsmod.challenges.won");
            graphics.pose().pushPose();
            graphics.pose().translate(x2 - 40, y1 + card.h() / 2f, 0);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-14));
            int w = font.width(stamp);
            graphics.pose().scale(1.3f, 1.3f, 1);
            graphics.fill(-w / 2 - 4, -6, w / 2 + 4, 8, GuiDraw.argb(200, 0x1A0505));
            GuiDraw.frame(graphics, -w / 2 - 4, -6, w / 2 + 4, 8, GuiDraw.argb(230, STAMP));
            graphics.drawString(font, stamp, -w / 2, -3, 0xFF000000 | STAMP, false);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (backX2 > backX1 && mouseX >= backX1 && mouseX < backX2 && mouseY >= backY1 && mouseY < backY2) {
                PacketDistributor.sendToServer(new ModPayloads.ChallengeLeave());
                onClose();
                return true;
            }
            double my = mouseY + scroll;
            List<String> wins = minecraft != null && minecraft.player != null ? minecraft.player.getData(ModAttachments.ARENA_WINS) : List.of();
            for (Card card : cards) {
                if (card.hit(mouseX, my) && !ChallengeArenas.locked(wins, card.id(), !Progression.clientOn)) {
                    PacketDistributor.sendToServer(new ModPayloads.ChallengeStart(card.id()));
                    onClose();
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
    public boolean isPauseScreen() {
        return false;
    }
}
