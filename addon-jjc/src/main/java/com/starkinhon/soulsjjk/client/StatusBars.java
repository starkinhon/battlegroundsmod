package com.starkinhon.soulsjjk.client;




// Vanilla status bars changed by the mod:
// - In combat mode the XP bar becomes the ult bar: the same bar tinted red (its title is drawn by BaseAttacks).
// - Vanilla's action bar message ("Combat mode on!", warnings) is lifted above the base attack selector, so it never
//   lands on the ult title or the selector.
// - Above Tier 10 health goes past 20, so the hearts row stays one row of 10 hearts, each one a tenth of the max,
//   with the real numbers next to it (vanilla would stack dozens of rows).
import net.minecraftforge.client.event.RenderGuiOverlayEvent;

// In the JujutsuCraft addon the HUD (hearts, energy) is JujutsuCraft's own: the Souls mod's ult bar and tier hearts
// are left out.
public final class StatusBars {
    private StatusBars() {
    }

    public static void onRenderLayer(RenderGuiOverlayEvent.Pre event) {
    }

    public static void onRenderLayerPost(RenderGuiOverlayEvent.Post event) {
    }
}
