package com.starkinhon.soulsjjk.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// NeoForge 1.21's PacketDistributor calls, sent through Net's channel.
public final class PacketDistributor {
    private PacketDistributor() {
    }

    private static Net.Envelope wrap(CustomPacketPayload payload) {
        return new Net.Envelope(payload);
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.sendToServer(wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.sendToServer(wrap(p));
        }
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), wrap(p));
        }
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), wrap(p));
        }
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), wrap(p));
        }
    }

    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), wrap(p));
        }
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload... more) {
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension), wrap(p));
        }
    }

    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded, double x, double y, double z, double radius,
            CustomPacketPayload payload, CustomPacketPayload... more) {
        net.minecraftforge.network.PacketDistributor.TargetPoint point = new net.minecraftforge.network.PacketDistributor.TargetPoint(excluded, x, y, z,
                radius, level.dimension());
        Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.NEAR.with(() -> point), wrap(payload));
        for (CustomPacketPayload p : more) {
            Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.NEAR.with(() -> point), wrap(p));
        }
    }
}
