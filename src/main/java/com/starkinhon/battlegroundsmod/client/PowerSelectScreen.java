package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Opened by the Book of Souls: one button per soul, hover shows its moves.
public class PowerSelectScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;

    private int top;

    public PowerSelectScreen() {
        super(Component.translatable("screen.battlegroundsmod.select_power"));
    }

    @Override
    protected void init() {
        top = height / 2 - Powers.all().size() * SPACING / 2;
        int y = top;
        for (Power power : Powers.all()) {
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
    }

    private static Component describe(Power power) {
        MutableComponent text = Component.empty()
                .append(power.name().copy().withStyle(s -> s.withColor(power.color()).withBold(true)))
                .append("\n")
                .append(power.description().copy().withStyle(ChatFormatting.GRAY));
        for (int slot : power.slots()) {
            text.append("\n")
                    .append(Component.literal("[").append(Keybinds.forSlot(slot).getTranslatedKeyMessage()).append("] ")
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
        for (Power power : Powers.all()) {
            ItemStack soul = new ItemStack(ModItems.SOULS.get(power.id()).get());
            graphics.renderItem(soul, width / 2 - BUTTON_WIDTH / 2 - 20, y + 2);
            graphics.renderItem(soul, width / 2 + BUTTON_WIDTH / 2 + 4, y + 2);
            y += SPACING;
        }
        graphics.drawCenteredString(font, title, width / 2, top - 24, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.battlegroundsmod.select_power.hint"), width / 2, top - 12, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
