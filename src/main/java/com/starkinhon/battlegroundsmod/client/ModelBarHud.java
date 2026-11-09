package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.Sukuna;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

// Sukuna's Modelo bar (power/powers/sukuna/ModelBar): how far he has studied the world's defenses. A thin bar above the
// hotbar in eight sections (the wheel's eight spokes), black with a white outline, filling dark red to white-hot; full,
// it breathes and glows: the fourth stage of the chant (the World Cutting Slash) is open. Fades out when empty and out
// of combat.
public final class ModelBarHud {
    private static float value;
    private static float shown;
    private static float alpha;
    private static float flash;
    private static long lastNanos;

    private ModelBarHud() {
    }

    public static void set(float v) {
        if (v >= 1 && value < 1) {
            flash = 1;
        }
        value = Mth.clamp(v, 0, 1);
    }

    static void render(GuiGraphics graphics, DeltaTracker deltaTracker, Player player, PowerData data) {
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        if (!Sukuna.ID.equals(data.power)) {
            alpha = 0;
            return;
        }
        shown += (value - shown) * Math.min(1, dt * 6);
        boolean visible = value > 0.001f || data.combat;
        alpha = Mth.clamp(alpha + (visible ? dt * 4 : -dt * 1.5f), 0, 1);
        flash = Math.max(0, flash - dt * 1.5f);
        if (alpha <= 0.01f) {
            return;
        }
        float time = (player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20f;
        boolean full = value >= 1;
        float pulse = full ? 0.5f + 0.5f * Mth.sin(time * 6) : 0;
        float w = 92, h = 4;
        float x = graphics.guiWidth() / 2f - w / 2;
        float y = graphics.guiHeight() - 62;
        if (full || flash > 0) {
            GuiDraw.glow(graphics, x + w * shown, y + h / 2, 10 + 8 * flash, 0xFF3A2A, (0.25f + 0.2f * pulse + 0.5f * flash) * alpha);
        }
        GuiDraw.rect(graphics, x - 2, y - 2, x + w + 2, y + h + 2, a(230, 0xF4F0EC));
        GuiDraw.rect(graphics, x - 1, y - 1, x + w + 1, y + h + 1, a(240, 0x050305));
        if (shown > 0.002f) {
            float fx = x + w * shown;
            int dark = 0x5A0A10, bright = GuiDraw.mix(0xD8301E, 0xFFF2E8, full ? 0.35f + 0.4f * pulse : 0.15f * shown);
            GuiDraw.quad(graphics, x, y, x, y + h, fx, y + h, fx, y, a(255, dark), a(255, dark), a(255, bright), a(255, bright));
            GuiDraw.rect(graphics, Math.max(x, fx - 1), y, fx, y + h, a(255, 0xFFFFFF));
        }
        // the eight spokes
        for (int i = 1; i < 8; i++) {
            float sx = x + w * i / 8f;
            GuiDraw.rect(graphics, sx - 0.5f, y - 1, sx + 0.5f, y + h + 1, a(200, 0x050305));
        }
        if (full) {
            Component label = Component.translatable("hud.battlegroundsmod.sukuna.model_full");
            int tw = Minecraft.getInstance().font.width(label);
            int color = GuiDraw.argb(Math.round(255 * alpha * (0.7f + 0.3f * pulse)), 0xFFE6DC);
            graphics.drawString(Minecraft.getInstance().font, label, Math.round(x + w / 2 - tw / 2f), Math.round(y - 11), color, true);
        }
    }

    private static int a(int alphaByte, int rgb) {
        return GuiDraw.argb(Math.round(Mth.clamp(alphaByte * alpha, 0, 255)), rgb);
    }
}
