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
    // When each player last saw each biome's name (game time). Walking along a border (the Norte Gelado mixed with the
    // vanilla snow) flipped in and out every few steps and the name kept popping up (Stark 2026-11-12): a biome's name
    // shows again only after COOLDOWN.
    private static final Map<UUID, Map<String, Long>> SHOWN = new HashMap<>();
    private static final long COOLDOWN = 20 * 60 * 5;

    // The name's color, matching the place (Stark 2026-11-12: the Norte Gelado in gold "não combina"). Gold otherwise.
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("norte_gelado", 0x9FDCFF),
            Map.entry("montanhas_paozu", 0x9AD67E),
            Map.entry("picos_do_dragao", 0xE8B070),
            Map.entry("deserto_vermelho", 0xE8714A),
            Map.entry("terra_sagrada_de_karin", 0xF4E6A6),
            Map.entry("floresta_da_folha", 0x8FD46F),
            Map.entry("floresta_da_morte", 0x8FA66A),
            Map.entry("vale_do_fim", 0x9DB4D6),
            Map.entry("vila_da_chuva", 0xA3AFC4),
            Map.entry("deserto_do_vento", 0xE6C57E),
            Map.entry("monte_myoboku", 0xA8DC74),
            Map.entry("mar_da_nevoa", 0xC4D2E0),
            Map.entry("mar_do_sul", 0x6FD0E8),
            Map.entry("templos_antigos", 0xF2B8C8));

    private UniverseBiomes() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(UniverseBiomes::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            LAST.remove(event.getEntity().getUUID());
            SHOWN.remove(event.getEntity().getUUID());
        });
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
        if (biome.is(ModWorldgen.JUJUTSU_BIOMES)) {
            return JujutsuWorld.UNIVERSE;
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
        long now = player.serverLevel().getGameTime();
        Map<String, Long> seen = SHOWN.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Long at = seen.get(key);
        if (at != null && now - at < COOLDOWN && now >= at) {
            return;
        }
        seen.put(key, now);
        String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        int color = COLORS.getOrDefault(path, 0xF2C94C);
        String name = biome.unwrapKey().map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath()).orElse("");
        if (last == null || last.isEmpty()) {
            // Coming in from outside the universe: the big title.
            player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 50, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(name)
                    .withStyle(s -> s.withColor(color).withBold(true))));
            // Only the place's name: the player works out whose land it is (Stark 2026-11-11).
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        } else {
            // From one of its biomes to another: just the name over the hotbar.
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.entrou_bioma", Component.translatable(name))
                    .withStyle(s -> s.withColor(color)), true);
        }
    }
}
