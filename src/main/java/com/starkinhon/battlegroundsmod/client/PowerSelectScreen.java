package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Opened by the Book of Souls: one button per soul, hover shows its moves. Two tabs on top: the Divine Souls (the
// bosses' powers) and the SubAlmas (Low Souls). The book opens on the tab of the soul you have.
public class PowerSelectScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;
    private static final int TAB_WIDTH = 108;

    // Fixed header (tabs, title, hint) and footer (page arrows); the souls in between are paged so the tabs never
    // scroll off screen, however many souls a tab has.
    private static final int HEADER = 64;
    private static final int FOOTER = 34;

    private int top;
    private int perPage;
    private static int tab = -1;
    private static int page = 0;

    public PowerSelectScreen() {
        super(Component.translatable("screen.battlegroundsmod.select_power"));
    }

    private List<Power> all() {
        return tab == 1 ? Powers.sub() : Powers.divine();
    }

    private int pages() {
        return Math.max(1, (all().size() + perPage - 1) / perPage);
    }

    private List<Power> shown() {
        List<Power> list = all();
        int from = Math.min(page * perPage, list.size());
        return list.subList(from, Math.min(from + perPage, list.size()));
    }

    private void turn(int delta) {
        int next = Math.max(0, Math.min(pages() - 1, page + delta));
        if (next != page) {
            page = next;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        if (tab < 0) {
            tab = minecraft != null && minecraft.player != null && Powers.isSub(minecraft.player.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER).power) ? 1 : 0;
        }
        perPage = Math.max(1, (height - HEADER - FOOTER + SPACING - BUTTON_HEIGHT) / SPACING);
        page = Math.max(0, Math.min(page, pages() - 1));
        // Centered like before when the tab fits on one page, but never above the header.
        int tabsY = Math.max(8, height / 2 - shown().size() * SPACING / 2 - 44);
        top = tabsY + 52;
        for (int i = 0; i < 2; i++) {
            int which = i;
            Component name = Component.translatable(i == 0 ? "screen.battlegroundsmod.select_power.divine" : "screen.battlegroundsmod.select_power.sub")
                    .withStyle(i == tab ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            Button button = Button.builder(name, b -> {
                        tab = which;
                        page = 0;
                        rebuildWidgets();
                    })
                    .bounds(width / 2 - TAB_WIDTH - 2 + i * (TAB_WIDTH + 4), tabsY, TAB_WIDTH, 18)
                    .build();
            button.active = i != tab;
            addRenderableWidget(button);
        }
        int y = top;
        for (Power power : shown()) {
            Component label = power.name().copy().withStyle(s -> s.withColor(power.color()));
            addRenderableWidget(Button.builder(label, button -> {
                        PacketDistributor.sendToServer(new ModPayloads.SelectPower(power.id()));
                        onClose();
                    })
                    .bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(describe(power)))
                    .build());
            y += SPACING;
        }
        if (pages() > 1) {
            int arrowsY = top + perPage * SPACING + 4;
            Button prev = Button.builder(Component.literal("<"), b -> turn(-1))
                    .bounds(width / 2 - BUTTON_WIDTH / 2, arrowsY, 40, BUTTON_HEIGHT).build();
            prev.active = page > 0;
            addRenderableWidget(prev);
            Button next = Button.builder(Component.literal(">"), b -> turn(1))
                    .bounds(width / 2 + BUTTON_WIDTH / 2 - 40, arrowsY, 40, BUTTON_HEIGHT).build();
            next.active = page < pages() - 1;
            addRenderableWidget(next);
        }
    }

    // Mouse wheel and the arrow keys also turn the page.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pages() > 1 && scrollY != 0) {
            turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static Component describe(Power power) {
        MutableComponent text = Component.empty()
                .append(power.name().copy().withStyle(s -> s.withColor(power.color()).withBold(true)))
                .append("\n")
                .append(power.description().copy().withStyle(ChatFormatting.GRAY));
        for (int slot : power.slots()) {
            text.append("\n")
                    .append(Component.literal("[").append(Keybinds.forSlot(power, slot).getTranslatedKeyMessage()).append("] ")
                            .withStyle(slot == PowerData.SLOT_ULT ? ChatFormatting.GOLD : ChatFormatting.YELLOW))
                    .append(power.abilityName(slot));
        }
        return text;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Each power's soul crystal next to its button.
        int y = top;
        for (Power power : shown()) {
            ItemStack soul = new ItemStack(ModItems.SOULS.get(power.id()).get());
            graphics.renderItem(soul, width / 2 - BUTTON_WIDTH / 2 - 20, y + 2);
            graphics.renderItem(soul, width / 2 + BUTTON_WIDTH / 2 + 4, y + 2);
            y += SPACING;
        }
        graphics.drawCenteredString(font, title, width / 2, top - 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.battlegroundsmod.select_power.hint"), width / 2, top - 12, 0xFFAAAAAA);
        if (pages() > 1) {
            graphics.drawCenteredString(font, (page + 1) + " / " + pages(), width / 2, top + perPage * SPACING + 10, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
