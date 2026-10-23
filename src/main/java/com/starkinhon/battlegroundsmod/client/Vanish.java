package com.starkinhon.battlegroundsmod.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// Entities the server asked not to draw for a while (Madara's invisible dash). Only the body is skipped, so the
// dash's own trail still shows where he goes.
public final class Vanish {
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();

    private Vanish() {
    }

    public static void hide(int entityId, int ticks) {
        var level = Minecraft.getInstance().level;
        if (ticks <= 0 || level == null) {
            HIDDEN.remove(entityId);
            return;
        }
        HIDDEN.put(entityId, level.getGameTime() + ticks);
    }

    public static boolean isHidden(int entityId) {
        Long until = HIDDEN.get(entityId);
        var level = Minecraft.getInstance().level;
        return until != null && level != null && level.getGameTime() < until;
    }

    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        Long until = HIDDEN.get(event.getEntity().getId());
        if (until == null) {
            return;
        }
        if (event.getEntity().level().getGameTime() >= until) {
            HIDDEN.remove(event.getEntity().getId());
            return;
        }
        event.setCanceled(true);
    }

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            HIDDEN.clear();
        }
    }
}
