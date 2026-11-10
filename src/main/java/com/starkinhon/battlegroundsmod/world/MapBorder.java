package com.starkinhon.battlegroundsmod.world;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// A drawn universe is small and closed (Stark, 2026-11-10: "compact, but full of content"). Past the edge of the map
// there is only sea; near it a wall of light shows the end, and whoever crosses it is pushed back (and, if they insist,
// brought back to the edge).
public final class MapBorder {
    private static final int WARN = 16;
    private static final int TOO_FAR = 12;

    private MapBorder() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MapBorder::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator() || !(player.serverLevel().getChunkSource().getGenerator() instanceof MapChunkGenerator drawn)) {
                continue;
            }
            check(player, drawn.map());
        }
    }

    private static void check(ServerPlayer player, WorldMap map) {
        ServerLevel level = player.serverLevel();
        double dx = player.getX() - map.centerX();
        double dz = player.getZ() - map.centerZ();
        double edge = map.radius() - 2;
        double out = Math.max(Math.abs(dx), Math.abs(dz)) - edge;
        if (out < -WARN) {
            return;
        }
        // The wall: a sheet of light on the nearest side, only around the player.
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        double wall = alongX ? map.centerX() + Math.signum(dx) * edge : map.centerZ() + Math.signum(dz) * edge;
        for (int i = 0; i < 14; i++) {
            double a = (player.getRandom().nextDouble() - 0.5) * 16;
            double y = player.getY() - 2 + player.getRandom().nextDouble() * 8;
            double px = alongX ? wall : player.getX() + a;
            double pz = alongX ? player.getZ() + a : wall;
            level.sendParticles(player, ParticleTypes.END_ROD, true, px, y, pz, 1, 0, 0.2, 0, 0.01);
        }
        if (out <= 0) {
            return;
        }
        if (out > TOO_FAR) {
            double x = map.centerX() + Math.max(-edge + 4, Math.min(edge - 4, dx));
            double z = map.centerZ() + Math.max(-edge + 4, Math.min(edge - 4, dz));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            player.teleportTo(level, x, Math.max(y, WorldMap.SEA) + 1, z, player.getYRot(), player.getXRot());
        } else {
            Vec3 back = new Vec3(-dx, 0, -dz).normalize().scale(0.6 + out * 0.08);
            player.setDeltaMovement(back.x, Math.max(player.getDeltaMovement().y, 0.1), back.z);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.map_edge"), true);
    }
}
