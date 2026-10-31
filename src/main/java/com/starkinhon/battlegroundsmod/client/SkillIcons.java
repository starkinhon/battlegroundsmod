package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Skill icons: textures/gui/skills/<power>/<n>.png, where n is the skill's number in the language files (0-3 Z X C V,
// 4 the fifth skill, 5-9 the Shift variants). Any square PNG works. A Shift variant without its own icon borrows its
// normal skill's icon, and a skill without any icon gets a drawn placeholder (a sigil with its initial) in the power's color.
public final class SkillIcons {
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private SkillIcons() {
    }

    // The skill's number in the language files (Power.abilityName uses the same one).
    public static int index(int slot) {
        if (slot == PowerData.SLOT_EXTRA) {
            return 4;
        }
        if (slot == PowerData.SLOT_SHIFT_EXTRA) {
            return 9;
        }
        return slot >= PowerData.SLOT_SHIFT ? slot - PowerData.SLOT_SHIFT + 5 : slot;
    }

    public static Component description(Power power, int slot) {
        return Component.translatable("ability." + BattlegroundsMod.MODID + "." + power.id() + "." + index(slot) + ".desc");
    }

    public static String key(Power power, int slot, String suffix) {
        return "ability." + BattlegroundsMod.MODID + "." + power.id() + "." + index(slot) + "." + suffix;
    }

    private static ResourceLocation location(Power power, int slot) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/gui/skills/" + power.id() + "/" + index(slot) + ".png");
    }

    private static boolean exists(ResourceLocation texture) {
        return EXISTS.computeIfAbsent(texture, t -> {
            Optional<?> resource = Minecraft.getInstance().getResourceManager().getResource(t);
            return resource.isPresent();
        });
    }

    // The icon to show for a skill, or null for the placeholder.
    public static ResourceLocation icon(Power power, int slot) {
        ResourceLocation own = location(power, slot);
        if (exists(own)) {
            return own;
        }
        int base = PowerData.baseSlot(slot);
        if (base != slot) {
            ResourceLocation borrowed = location(power, base);
            if (exists(borrowed)) {
                return borrowed;
            }
        }
        return null;
    }

    // Forgets which icons exist (after F3 + T, so new icons show without a restart).
    public static void reload() {
        EXISTS.clear();
    }

    // Draws the skill's icon in a size x size box. brightness < 1 darkens it (cooldown, locked).
    public static void draw(GuiGraphics graphics, Font font, Power power, int slot, int x, int y, int size, float brightness) {
        ResourceLocation icon = icon(power, slot);
        if (icon != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(brightness, brightness, brightness, 1);
            // UVs over the whole texture, whatever its size.
            graphics.blit(icon, x, y, size, size, 0, 0, 1, 1, 1, 1);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            return;
        }
        placeholder(graphics, font, power, slot, x, y, size, brightness);
    }

    private static void placeholder(GuiGraphics graphics, Font font, Power power, int slot, int x, int y, int size, float brightness) {
        int color = power.color();
        int dark = GuiDraw.mix(0x0A060E, color, 0.18f * brightness);
        int deep = GuiDraw.mix(0x050307, color, 0.42f * brightness);
        graphics.fillGradient(x, y, x + size, y + size, 0xFF000000 | dark, 0xFF000000 | deep);
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        int line = GuiDraw.argb(Math.round(170 * brightness), GuiDraw.mix(color, 0xFFFFFF, 0.25f));
        // A sigil: a diamond outline with a smaller filled one inside, and the skill's initial on top.
        float r = size * 0.40f;
        float t = Math.max(0.75f, size / 22f);
        GuiDraw.line(graphics, cx, cy - r, cx + r, cy, t, 0, line);
        GuiDraw.line(graphics, cx + r, cy, cx, cy + r, t, 0, line);
        GuiDraw.line(graphics, cx, cy + r, cx - r, cy, t, 0, line);
        GuiDraw.line(graphics, cx - r, cy, cx, cy - r, t, 0, line);
        GuiDraw.diamond(graphics, cx, cy, r * 0.55f, GuiDraw.argb(Math.round(90 * brightness), color));
        graphics.flush();
        String name = power.abilityName(slot).getString();
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        float scale = Math.max(1, Math.round(size / 18f * 2) / 2f);
        GuiDraw.centeredText(graphics, font, Component.literal(initial), cx + 0.5f, cy - 3.5f * scale, scale,
                GuiDraw.argb(255, GuiDraw.mix(0x777777, 0xFFFFFF, brightness)), true);
    }
}
