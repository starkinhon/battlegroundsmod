// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.mixin;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.starkinhon.battlegroundsmod.client.SoulsWorldTypeBadge;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

// The "Souls" world type comes first on the create world screen and is already picked on a fresh world
// (Stark 2026-11-22). Default, Superflat, Amplified... stay in the list right after it, in vanilla order.
// "Re-Create" of an existing world keeps that world's own type (it comes with a seed, openFresh doesn't).
// require = 0: if Mojang renames something the screen just opens the vanilla way.
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> normalPresetList;

    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> altPresetList;

    @Shadow
    public abstract void setWorldType(WorldCreationUiState.WorldTypeEntry worldType);

    @Inject(method = "updatePresetLists", at = @At("TAIL"), require = 0)
    private void battlegrounds$soulsFirst(CallbackInfo ci) {
        battlegrounds$moveFirst(this.normalPresetList);
        battlegrounds$moveFirst(this.altPresetList);
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void battlegrounds$soulsDefault(Path savesFolder, WorldCreationContext settings, Optional<ResourceKey<WorldPreset>> preset,
            OptionalLong seed, CallbackInfo ci) {
        if (seed.isPresent() || !preset.equals(Optional.of(WorldPresets.NORMAL))) {
            return;
        }
        for (WorldCreationUiState.WorldTypeEntry entry : this.normalPresetList) {
            if (SoulsWorldTypeBadge.isSouls(entry)) {
                this.setWorldType(entry);
                return;
            }
        }
    }

    private static void battlegrounds$moveFirst(List<WorldCreationUiState.WorldTypeEntry> list) {
        for (int i = 1; i < list.size(); i++) {
            if (SoulsWorldTypeBadge.isSouls(list.get(i))) {
                list.add(0, list.remove(i));
                return;
            }
        }
    }
}
