package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.challenge.ArenaTemplate;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The challenge arenas on the client (the web preview is the reference, JOGO = PRÉVIA): no clouds crossing the scenery,
// and each arena's own fog distance (arenas.json "neblina"; the colours come from its biome).
public final class ArenaClient {
    private ArenaClient() {
    }

    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "arena"),
                new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
                    @Override
                    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                        return color.multiply(brightness * 0.94f + 0.06f, brightness * 0.94f + 0.06f, brightness * 0.91f + 0.09f);
                    }

                    @Override
                    public boolean isFoggyAt(int x, int y) {
                        return false;
                    }
                });
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ChallengeArenas.isArena(mc.level.dimension()) || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        Vec3 at = event.getCamera().getPosition();
        ArenaTemplate.Meta meta = ArenaTemplate.meta(ChallengeArenas.arenaAt(at.x, at.z));
        if (meta == null || meta.neblina() == null || meta.neblina().length < 2) {
            return;
        }
        float far = Math.min(meta.neblina()[1], event.getFarPlaneDistance());
        float near = Math.min(meta.neblina()[0], far * 0.75f);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}
