package com.starkinhon.battlegroundsmod.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.universe.Flight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

// The universes' friendly NPCs (Stark 2026-11-10: "NPCs that teach you, like DragonMineZ; good ones that help, bad ones
// that attack, and the bosses"). The bad ones are the common enemies (ModEntities, Saibaman...). The good ones live
// near each universe's arrival point and come back if they go missing:
// - Gohan (Dragon Ball) teaches Voo once the player reaches Tier 9;
// - Korin (Dragon Ball) gives one Senzu per day and hints about the universe.
public final class Npcs {
    private Npcs() {
    }

    private record Home(String universe, Supplier<? extends EntityType<UniverseNpc>> type, int dx, int dz) {
    }

    private static final List<Home> HOMES = List.of(
            new Home("dragonball", ModEntities.NPC_GOHAN, 6, 3),
            new Home("dragonball", ModEntities.NPC_KORIN, -5, 4));

    // Makes sure this universe's NPCs stand around its arrival point.
    public static void ensure(ServerLevel level, String universe, BlockPos arrival) {
        for (Home home : HOMES) {
            if (!home.universe().equals(universe)) {
                continue;
            }
            EntityType<UniverseNpc> type = home.type().get();
            AABB area = new AABB(arrival).inflate(96, 64, 96);
            if (!level.getEntities(type, area, e -> e.isAlive()).isEmpty()) {
                continue;
            }
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arrival.offset(home.dx(), 0, home.dz()));
            if (at.getY() <= level.getMinBuildHeight() + 1) {
                at = arrival;
            }
            UniverseNpc npc = type.spawn(level, at, MobSpawnType.EVENT);
            if (npc != null) {
                npc.restrictTo(at, 10);
                npc.setCustomName(npc.getType().getDescription());
                npc.setCustomNameVisible(true);
            }
        }
    }

    public static void talk(ServerPlayer player, UniverseNpc npc) {
        switch (npc.npcId()) {
            case "gohan" -> gohan(player, npc);
            case "korin" -> korin(player, npc);
            default -> say(player, npc, Component.literal("..."));
        }
    }

    private static void say(ServerPlayer player, UniverseNpc npc, Component line) {
        player.sendSystemMessage(Component.empty().append(npc.getType().getDescription().copy().withStyle(s -> s.withColor(0xF2C94C).withBold(true)))
                .append(Component.literal(": ").withStyle(s -> s.withColor(0xAAAAAA))).append(line.copy().withStyle(s -> s.withColor(0xE6E0EE))));
    }

    private static Component pick(ServerPlayer player, String key, int count) {
        return Component.translatable(key + "." + player.getRandom().nextInt(count));
    }

    private static void gohan(ServerPlayer player, UniverseNpc npc) {
        if (Flight.knows(player)) {
            say(player, npc, pick(player, "npc.battlegroundsmod.gohan.after", 3));
            return;
        }
        if (Tiers.of(player) > Tiers.HUMANO - 1) {
            say(player, npc, Component.translatable("npc.battlegroundsmod.gohan.weak"));
            return;
        }
        List<String> techniques = new ArrayList<>(player.getData(ModAttachments.TECHNIQUES));
        techniques.add(Flight.TECHNIQUE);
        player.setData(ModAttachments.TECHNIQUES, List.copyOf(techniques));
        say(player, npc, Component.translatable("npc.battlegroundsmod.gohan.teach"));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.battlegroundsmod.technique_learned")
                .withStyle(s -> s.withColor(0xF2C94C).withBold(true))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("technique.battlegroundsmod.voo")));
        Fx.burst(player.serverLevel(), ParticleTypes.CLOUD, player.position(), 60, 0.8, 0.1);
        Fx.sound(player.serverLevel(), player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }

    private static final String SENZU_DAY = "souls_korin_dia";

    private static void korin(ServerPlayer player, UniverseNpc npc) {
        long day = player.serverLevel().getGameTime() / 24000L;
        if (!player.getPersistentData().contains(SENZU_DAY) || player.getPersistentData().getLong(SENZU_DAY) < day) {
            player.getPersistentData().putLong(SENZU_DAY, day);
            ItemStack senzu = new ItemStack(ModItems.SENZU.get());
            if (!player.getInventory().add(senzu)) {
                player.drop(senzu, false);
            }
            say(player, npc, Component.translatable("npc.battlegroundsmod.korin.senzu"));
            Fx.sound(player.serverLevel(), player.position(), SoundEvents.ITEM_PICKUP, 0.8f, 1.2f);
            return;
        }
        say(player, npc, pick(player, "npc.battlegroundsmod.korin.hint", 5));
    }
}
