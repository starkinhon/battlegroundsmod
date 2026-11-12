package com.starkinhon.battlegroundsmod.universe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.challenge.Universes;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The universes as biomes of the normal overworld (Stark 2026-11-11). universeAt says whose a spot is: in the overworld
// by its biome (tags/worldgen/biome/universe_<id>), elsewhere by the dimension (Namek, the old universe worlds).
// Walking into a universe's land shows its name on screen, like a new area (just "Terras do Dragão").
public final class UniverseBiomes {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private UniverseBiomes() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(UniverseBiomes::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> LAST.remove(event.getEntity().getUUID()));
    }

    public static String universeAt(Level level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD) {
            return universeOf(level.getBiome(pos));
        }
        return Universes.universeOf(level.dimension());
    }

    public static String universeOf(Holder<Biome> biome) {
        if (biome.is(ModWorldgen.DRAGONBALL_BIOMES)) {
            return DragonBallWorld.UNIVERSE;
        }
        return biome.is(ModWorldgen.NARUTO_BIOMES) ? NarutoWorld.UNIVERSE : null;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        Holder<Biome> biome = player.level().getBiome(player.blockPosition());
        String universe = universeOf(biome);
        String key = biome.unwrapKey().map(ResourceKey::location).map(Object::toString).orElse("");
        String last = LAST.put(player.getUUID(), universe == null ? "" : key);
        if (universe == null || key.equals(last)) {
            return;
        }
        String name = biome.unwrapKey().map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath()).orElse("");
        if (last == null || last.isEmpty()) {
            // Coming in from outside the universe: the big title.
            player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 50, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(name)
                    .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
            // Only the place's name: the player works out whose land it is (Stark 2026-11-11).
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        } else {
            // From one of its biomes to another: just the name over the hotbar.
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.entrou_bioma", Component.translatable(name))
                    .withStyle(s -> s.withColor(0xF2C94C)), true);
        }
    }
}
