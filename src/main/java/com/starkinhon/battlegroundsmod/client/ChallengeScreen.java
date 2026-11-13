package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.client.multiplayer.PlayerInfo;
import com.starkinhon.battlegroundsmod.challenge.ArenaParty;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// The Livro dos Desafios, general since the universes stopped being maps (Stark 2026-11-12): every universe in one book,
// a section each (its name and how many of it were beaten), one card per character (its SubAlmas and its bosses) with
// its name, tier and what its power is about. Opened from an old per-universe book it starts at that universe's section. Clicking a card goes to that character's arena (challenge/ChallengeArenas); a beaten
// character's card is stamped DERROTADO. A boss's card stays locked until some of the universe's SubAlmas are beaten
// (ChallengeArenas.locked; free in arena mode and flat worlds). Inside the arenas the book also offers the way back.
public class ChallengeScreen extends Screen {
    private static final int TEXT = 0xFFE6E0EE;
    private static final int MUTED = 0xFF9A93A6;
    private static final int STAMP = 0xD8352A;

    // A universe's title over its cards.
    private record Header(ChallengeSpec.Universe universe, int y, int total) {
    }

    private record Card(String id, Component name, int tier, int color, String foco, boolean items, int x, int y, int w, int h) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final String universeId;
    private final List<Card> cards = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private boolean scrolledToStart;
    private int scroll;
    private int contentHeight;
    private int backX1, backY1, backX2, backY2;

    // The group corner (top left, outside the arenas): Convidar opens the list of the world's players; an invite
    // received shows Aceitar / Recusar under it (challenge/ArenaParty).
    private record Hit(int action, String name, int x1, int y1, int x2, int y2) {
        boolean in(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }

    private static final int TOGGLE = -1;
    private final List<Hit> hits = new ArrayList<>();
    private boolean inviteOpen;

    public ChallengeScreen(String universe) {
        super(Component.translatable("screen.battlegroundsmod.challenges"));
        this.universeId = universe;
    }


    private boolean inArena() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ChallengeArenas.isArena(mc.player.level().dimension());
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        cards.clear();
        headers.clear();
        int margin = 20;
        int columns = width >= 520 ? 3 : width >= 340 ? 2 : 1;
        int gap = 8;
        int cardW = Math.min(170, (width - margin * 2 - gap * (columns - 1)) / columns);
        int cardH = 78;
        int totalW = columns * cardW + (columns - 1) * gap;
        int left = (width - totalW) / 2;
        int top = 50;
        int startY = -1;
        for (ChallengeSpec.Universe universe : ChallengeSpec.get().universos()) {
            if (universe.desafios().isEmpty()) {
                continue;
            }
            if (universe.id().equals(universeId)) {
                startY = top;
            }
            headers.add(new Header(universe, top, universe.desafios().size()));
            top += 20;
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
            top += (i + columns - 1) / columns * (cardH + gap) + 10;
        }
        contentHeight = top + 22;
        if (!scrolledToStart && startY > 0) {
            scroll = startY - 8;
        }
        scrolledToStart = true;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - height));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xE0080510, 0xF0030206);
        int accent = 0xD8B471;
        List<String> wins = ChallengeArenas.clientWins;

        graphics.pose().pushPose();
        graphics.pose().translate(0, -scroll, 0);
        GuiDraw.centeredText(graphics, font, title, width / 2f, 12, 1.6f, 0xFF000000 | GuiDraw.mix(accent, 0xFFFFFF, 0.3f), true);
        int beaten = 0;
        for (Card card : cards) {
            if (wins.contains(card.id())) {
                beaten++;
            }
        }
        Component sub = Component.translatable("screen.battlegroundsmod.challenges.count", beaten, cards.size());
        GuiDraw.centeredText(graphics, font, sub, width / 2f, 32, 1f, TEXT, true);

        for (Header header : headers) {
            ChallengeSpec.Universe universe = header.universe();
            int won = 0;
            for (ChallengeSpec.Challenge challenge : universe.desafios()) {
                if (wins.contains(challenge.id())) {
                    won++;
                }
            }
            int color = universe.color();
            int y = header.y();
            Component name = Component.literal(universe.nome()).append(Component.literal("   " + won + "/" + header.total())
                    .withStyle(s -> s.withColor(MUTED)));
            int w = font.width(name);
            GuiDraw.centeredText(graphics, font, name, width / 2f, y + 2, 1f, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.3f), true);
            int line = GuiDraw.argb(120, color);
            graphics.fill(width / 2 - w / 2 - 70, y + 6, width / 2 - w / 2 - 8, y + 7, line);
            graphics.fill(width / 2 + w / 2 + 8, y + 6, width / 2 + w / 2 + 70, y + 7, line);
        }

        int my = mouseY + scroll;
        for (Card card : cards) {
            boolean locked = ChallengeArenas.locked(wins, card.id(), !Progression.clientOn);
            ChallengeSpec.Universe universe = ChallengeSpec.get().universeOf(card.id());
            int needed = universe == null ? 0 : ChallengeArenas.subsNeeded(universe);
            drawCard(graphics, card, wins.contains(card.id()), locked, needed, !locked && card.hit(mouseX, my));
        }
        GuiDraw.centeredText(graphics, font, Component.translatable("screen.battlegroundsmod.challenges.hint"), width / 2f,
                contentHeight - 22, 0.9f, MUTED, false);
        graphics.pose().popPose();

        hits.clear();
        if (!inArena()) {
            drawGroup(graphics, mouseX, mouseY, accent);
        }

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

    private List<String> worldPlayers() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return names;
        }
        String me = mc.player.getGameProfile().getName();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (!name.equals(me)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private int button(GuiGraphics graphics, Component text, int x, int y, int color, int action, String name, int mouseX, int mouseY) {
        int w = font.width(text) + 12;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 15;
        graphics.fill(x, y, x + w, y + 15, hover ? 0xF0402030 : 0xE0201018);
        GuiDraw.frame(graphics, x, y, x + w, y + 15, GuiDraw.argb(hover ? 255 : 200, color));
        graphics.drawString(font, text, x + 6, y + 4, hover ? 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.5f) : TEXT, true);
        hits.add(new Hit(action, name, x, y, x + w, y + 15));
        return x + w;
    }

    private void drawGroup(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        List<String> party = ArenaParty.clientParty;
        int x = 8, y = 8;
        button(graphics, Component.translatable("screen.battlegroundsmod.challenges.invite").append(inviteOpen ? " \u25B4" : " \u25BE"),
                x, y, accent, TOGGLE, "", mouseX, mouseY);
        y += 19;
        if (!party.isEmpty()) {
            Component line = Component.translatable("screen.battlegroundsmod.challenges.party", String.join(", ", party));
            graphics.fill(x, y - 2, x + font.width(line) + 8, y + 10, 0xC0100A16);
            graphics.drawString(font, line, x + 4, y, 0xFF7FE08A, true);
            y += 14;
        }
        String invite = ArenaParty.clientInvite;
        if (!invite.isEmpty()) {
            Component line = Component.translatable("screen.battlegroundsmod.challenges.invite_from", invite);
            graphics.fill(x, y - 2, x + font.width(line) + 8, y + 10, 0xC0100A16);
            graphics.drawString(font, line, x + 4, y, 0xFFF2C94C, true);
            y += 12;
            int nx = button(graphics, Component.translatable("screen.battlegroundsmod.challenges.accept"), x, y, 0x5BD16A, 1, invite, mouseX, mouseY);
            button(graphics, Component.translatable("screen.battlegroundsmod.challenges.decline"), nx + 4, y, 0xD8352A, 2, invite, mouseX, mouseY);
            y += 19;
        }
        if (!inviteOpen) {
            return;
        }
        List<String> players = worldPlayers();
        int w = 190;
        int rows = Math.max(1, players.size()) + (party.isEmpty() ? 0 : 1);
        int top = 27, bottom = top + 6 + rows * 16 + (players.isEmpty() ? 12 : 0);
        graphics.fill(x, top, x + w, bottom, 0xF0140E1C);
        GuiDraw.frame(graphics, x, top, x + w, bottom, GuiDraw.argb(220, accent));
        int ry = top + 4;
        if (players.isEmpty()) {
            for (FormattedCharSequence seq : font.split(Component.translatable("screen.battlegroundsmod.challenges.invite_none"), w - 10)) {
                graphics.drawString(font, seq, x + 5, ry, MUTED, false);
                ry += 10;
            }
        }
        for (String name : players) {
            boolean in = party.contains(name);
            boolean hover = !in && mouseX >= x + 2 && mouseX < x + w - 2 && mouseY >= ry - 1 && mouseY < ry + 14;
            if (hover) {
                graphics.fill(x + 2, ry - 1, x + w - 2, ry + 14, 0x40D8B471);
            }
            graphics.drawString(font, name, x + 6, ry + 3, TEXT, true);
            Component tag = Component.translatable(in ? "screen.battlegroundsmod.challenges.invite_in" : "screen.battlegroundsmod.challenges.invite_send");
            GuiDraw.rightText(graphics, font, tag, x + w - 6, ry + 4, 0.75f, in ? 0xFF7FE08A : hover ? 0xFF000000 | accent : MUTED, false);
            if (!in) {
                hits.add(new Hit(0, name, x + 2, ry - 1, x + w - 2, ry + 14));
            }
            ry += 16;
        }
        if (!party.isEmpty()) {
            boolean hover = mouseX >= x + 2 && mouseX < x + w - 2 && mouseY >= ry - 1 && mouseY < ry + 14;
            graphics.drawString(font, Component.translatable("screen.battlegroundsmod.challenges.invite_leave"), x + 6, ry + 3,
                    hover ? 0xFFFF7060 : 0xFFB0505A, true);
            hits.add(new Hit(3, "", x + 2, ry - 1, x + w - 2, ry + 14));
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
            for (Hit hit : new ArrayList<>(hits)) {
                if (hit.in(mouseX, mouseY)) {
                    if (hit.action() == TOGGLE) {
                        inviteOpen = !inviteOpen;
                    } else {
                        PacketDistributor.sendToServer(new ModPayloads.ArenaPartyAction(hit.action(), hit.name()));
                        if (hit.action() != 0) {
                            ArenaParty.clientInvite = "";
                        }
                    }
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                    return true;
                }
            }
            if (inviteOpen) {
                inviteOpen = false;              // a click elsewhere closes the list
                return true;
            }
            double my = mouseY + scroll;
            List<String> wins = ChallengeArenas.clientWins;
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
