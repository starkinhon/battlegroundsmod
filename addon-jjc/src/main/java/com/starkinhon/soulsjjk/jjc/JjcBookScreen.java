package com.starkinhon.soulsjjk.jjc;

import com.starkinhon.soulsjjk.compat.PacketDistributor;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

// Client: the "Gojo Souls" and "Sukuna Souls" buttons on the second page of JujutsuCraft's technique book, in the two
// free spots of its first row (same size as its own buttons, 77 x 20).
public final class JjcBookScreen {
    private static final String PAGE_2 = "net.mcreator.jujutsucraft.client.gui.SelectTechnique2Screen";

    private JjcBookScreen() {
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!PAGE_2.equals(screen.getClass().getName()) || !(screen instanceof AbstractContainerScreen<?> page)) {
            return;
        }
        int left = page.getGuiLeft(), top = page.getGuiTop();
        event.addListener(button("gui.soulsjjk.jjc.gojo", JjcTechniques.GOJO, left + 11, top + 20));
        event.addListener(button("gui.soulsjjk.jjc.sukuna", JjcTechniques.SUKUNA, left + 92, top + 20));
    }

    private static Button button(String key, int technique, int x, int y) {
        return Button.builder(Component.translatable(key), b -> PacketDistributor.sendToServer(new JjcBook.Pick(technique)))
                .bounds(x, y, 77, 20).build();
    }
}
