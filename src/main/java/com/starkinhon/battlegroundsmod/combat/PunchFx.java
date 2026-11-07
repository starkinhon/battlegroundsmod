package com.starkinhon.battlegroundsmod.combat;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The look of the hand-to-hand moves (combo, Forte, Barragem, Dash Attack), drawn by the client in client/fx/PunchFxRenderer
// with shader shapes only, no vanilla particles: the air shock (the space bends, vapor rings go through the target, wind
// comes out of its back) tinted in the color of the puncher's soul (short flash at the contact, a few soul flames rising).
// The same look as scene "E · Alma + Choque de ar" of the Visualizador (chosen by Stark on 2026-11-07).
public final class PunchFx {
    // A combo jab.
    public static final int JAB = 0;
    // The last hit of the combo, or the end of the Barragem.
    public static final int FINAL = 1;
    // The Forte landing; a = how charged it was (0 to 1).
    public static final int FORTE = 2;
    // One hit of the Barragem.
    public static final int BARRAGEM = 3;
    // The Dash Attack landing.
    public static final int DASH = 4;
    // The Forte charging on the fist of entity (every tick); a = the charge.
    public static final int CHARGE = 5;
    // The Dash Attack sliding (every tick): vapor cone in front of entity, soul flames behind, dust at the feet.
    public static final int TRAIL = 6;
    // A punch thrown at the air at pos (Forte or Barragem without a target): only the vapor and the wind; a = its size.
    public static final int SWING = 7;

    private static final double VIEW_DISTANCE_SQR = 128 * 128;

    private PunchFx() {
    }

    // A punch landing at pos, the fist going along dir. ground: the height of the target's feet (for the dust).
    public static void hit(ServerLevel level, int kind, Vec3 pos, Vec3 dir, int color, float charge, double ground) {
        send(level, kind, -1, pos, dir, color, charge, (float) ground);
    }

    public static void follow(ServerLevel level, int kind, LivingEntity entity, Vec3 dir, int color, float a) {
        send(level, kind, entity.getId(), entity.position(), dir, color, a, (float) entity.getY());
    }

    public static void swing(ServerLevel level, Vec3 pos, Vec3 dir, int color, float size) {
        send(level, SWING, -1, pos, dir, color, size, 0);
    }

    private static void send(ServerLevel level, int kind, int entity, Vec3 pos, Vec3 dir, int color, float a, float b) {
        ModPayloads.PunchFx payload = new ModPayloads.PunchFx(kind | (color & 0xFFFFFF) << 4, entity, pos, dir, a, b);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
