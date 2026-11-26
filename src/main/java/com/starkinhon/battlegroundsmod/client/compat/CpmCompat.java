// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.compat;

import java.util.function.Function;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

// Customizable Player Models (CPM, tom5454), optional. The mod draws players' bodies itself in a few places (the first
// person arms of a move, Kyoka's clone, the shadow clones, Saitama's afterimages); with CPM those must show the player's
// CPM model and skin, not the vanilla skin (Stark 2026-11-26, playing with friends). Without CPM nothing here runs, and no
// CPM class is ever loaded: everything that touches CPM is in CpmBridge.
public final class CpmCompat {
    private static final boolean LOADED = ModList.get().isLoaded("cpm");

    // A body bound to someone's CPM model: draw the model with renderType() (or texture() where the renderer picks the
    // render type itself), then end() right after, also when drawing throws.
    public interface Bound {
        RenderType renderType();

        ResourceLocation texture();

        void end();
    }

    private CpmCompat() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    // model drawn as player's CPM model, from now until end(); null (nothing bound) without CPM, without a player, or
    // when that player has no CPM model (or it is still downloading): then draw the vanilla way. skin: the player's own
    // skin, worn by a CPM model that has no texture of its own.
    //
    // CPM swaps the model's parts (model.rightArm and the rest are other objects while bound): read the parts from the
    // model only after bind(). The pose they had is carried over to the swapped-in parts.
    public static Bound bind(Model model, Player player, ResourceLocation skin, MultiBufferSource buffers,
            Function<ResourceLocation, RenderType> type) {
        if (!LOADED || player == null || model == null) {
            return null;
        }
        ModelPart[] before = model instanceof PlayerModel<?> pm ? parts(pm) : null;
        PartPose[] poses = null;
        boolean[] shown = null;
        if (before != null) {
            poses = new PartPose[before.length];
            shown = new boolean[before.length];
            for (int i = 0; i < before.length; i++) {
                poses[i] = before[i].storePose();
                shown[i] = before[i].visible;
            }
        }
        Bound bound;
        try {
            bound = CpmBridge.bind(model, player, skin, buffers, type);
        } catch (Throwable t) {
            // this frame without CPM, never a crash; a bad frame does not switch the compat off (Stark 2026-11-26)
            CpmBridge.failed(t);
            return null;
        }
        if (bound != null && before != null) {
            ModelPart[] now = parts((PlayerModel<?>) model);
            for (int i = 0; i < now.length; i++) {
                if (now[i] != before[i]) {
                    now[i].loadPose(poses[i]);
                    now[i].visible = shown[i];
                }
            }
        }
        return bound;
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[] {m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg, m.rightPants,
                m.leftPants};
    }
}
