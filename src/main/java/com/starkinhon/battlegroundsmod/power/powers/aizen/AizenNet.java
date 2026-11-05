package com.starkinhon.battlegroundsmod.power.powers.aizen;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// What Aizen's skills tell the clients to draw (client/fx/AizenFx), through ModPayloads.AizenFx.
public final class AizenNet {
    // entity = the caster, pos = his feet, a = radius, b = power 0..1. Every tick while it is held.
    public static final int REIATSU_HELD = 0;
    // The held pressure bursts (a = radius).
    public static final int REIATSU_BURST = 1;
    // A tap: a quick small pulse (a = radius).
    public static final int REIATSU_CLICK = 2;
    // entity evolves: a = ticks since it started, b = total ticks (cocoon and evolved form). Sent again now and then.
    public static final int HOGYOKU = 3;
    // The evolved form ends early (or on time).
    public static final int HOGYOKU_END = 4;
    // A cut in space at pos along dir: a = half length, b = width scale.
    public static final int CUT = 5;
    // The clone at pos (feet, dir = the way it faces) cracks for a ticks...
    public static final int MIRROR_CRACK = 6;
    // ...and breaks like a mirror.
    public static final int MIRROR_BREAK = 7;

    // A sword crescent around pos (the swinger's middle) toward dir: a = reach, b = ticks the sweep takes.
    public static final int SLASH = 8;

    private static final double VIEW_DISTANCE = 160;

    private AizenNet() {
    }

    public static void send(ServerLevel level, int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        ModPayloads.AizenFx payload = new ModPayloads.AizenFx(kind, entityId, pos, dir, a, b);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
