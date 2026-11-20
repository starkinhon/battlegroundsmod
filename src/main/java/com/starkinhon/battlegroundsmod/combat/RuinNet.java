// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// What the end-game bosses' destruction tells the clients to draw (client/fx/epic/EpicFx), through ModPayloads.SubFx in
// its own range 520-539 (faixas memory). Sent to everyone within 320 blocks: a Death Ball is seen from far away.
public final class RuinNet {
    public static final int FIRST = 520;
    // A volume: pos = its foot on the ground, dir = (R, H, grow ticks), a = mode + 4 * shape + 16 * heat (0..100),
    // b = life in ticks, c = dust color (rgb). Mode 0 fire, 1 dust, 2 smoke with embers; shape 0 dome, 1 mushroom.
    public static final int BOOM = 520;
    // A ring: pos, dir = (Rmax, width, 1 = in the air, 0 = dust running over the ground), a = life in ticks, b = color.
    public static final int RING = 521;
    // The ground's top blocks lifting in a wave out from pos: a = height (blocks), b = speed (blocks/s), c = how far.
    public static final int TREMOR = 522;
    // The camera shaking (a = strength 60 blocks away, more closer), b = 1 with an impact frame, c = its tint (rgb).
    public static final int QUAKE = 523;
    // Blocks flying out of a crater: pos = middle, dir = (radius, speed, heat 0..1), a = how many, b = size.
    public static final int DEBRIS = 524;
    // Blocks flying up off a line (a furrow, a crack): pos = from, dir = to - from, a = how many, b = speed.
    public static final int DEBRIS_LINE = 525;
    // Freeza's ship (FzShipShape) coming down (b = 1) to pos or going up (b = -1) from it: dir = (door x, height, door z),
    // a = how long in ticks.
    public static final int SHIP = 526;
    public static final int LAST = 539;

    public static final int MODE_FIRE = 0, MODE_DUST = 1, MODE_SMOKE = 2;
    public static final int SHAPE_DOME = 0, SHAPE_MUSHROOM = 1;
    private static final double VIEW = 320;

    private RuinNet() {
    }

    public static void send(ServerLevel level, int kind, Vec3 pos, Vec3 dir, float a, float b, float c) {
        ModPayloads.SubFx payload = new ModPayloads.SubFx(kind, 0, pos, dir, a, b, c);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW * VIEW) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // A dust volume (the page's boom with mode 1).
    public static void dust(ServerLevel level, Vec3 at, double R, double H, int life, int grow, int color) {
        send(level, BOOM, at, new Vec3(R, H, grow), MODE_DUST, life, color);
    }

    public static void boom(ServerLevel level, Vec3 at, double R, double H, int life, int grow, int mode, int shape, double heat, int color) {
        send(level, BOOM, at, new Vec3(R, H, grow), mode + 4 * shape + 16 * Math.round(heat * 100), life, color);
    }

    public static void ring(ServerLevel level, Vec3 at, double Rmax, double width, boolean sky, int life, int color) {
        send(level, RING, at, new Vec3(Rmax, width, sky ? 1 : 0), life, color, 0);
    }

    public static void tremor(ServerLevel level, Vec3 at, double amp, double speed, double reach) {
        send(level, TREMOR, at, Vec3.ZERO, (float) amp, (float) speed, (float) reach);
    }

    public static void quake(ServerLevel level, Vec3 at, double strength, boolean impact, int tint) {
        send(level, QUAKE, at, Vec3.ZERO, (float) strength, impact ? 1 : 0, tint);
    }

    public static void debris(ServerLevel level, Vec3 at, double radius, double speed, double heat, int count, double size) {
        if (BlockRules.canBreak(level)) {
            send(level, DEBRIS, at, new Vec3(radius, speed, heat), count, (float) size, 0);
        }
    }

    public static void debrisLine(ServerLevel level, Vec3 from, Vec3 to, int count, double speed) {
        if (BlockRules.canBreak(level)) {
            send(level, DEBRIS_LINE, from, to.subtract(from), count, (float) speed, 0);
        }
    }
}
