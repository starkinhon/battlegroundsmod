// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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

    // ---------- the kit remade from the approved page (forja/aizen-kit, 2026-11-22), drawn by client/fx/AizenKit ----------
    // Raikoho: entity = caster, pos = the hand, dir = where the beam lands, a = 0 charge starts / 1 the beam fires.
    public static final int RAIKO = 20;
    // Sokatsui: entity = caster, pos = the hand, dir = the way the fire goes (unit), a = reach.
    public static final int SOKA = 21;
    // Um Dedo stops a hit: entity = Aizen, pos = the finger, dir = toward the attacker, a = the attacker's id.
    public static final int DEDO_STOP = 22;
    // He vanishes (pos = where he was) and shows up behind the attacker (dir = where he lands), a = the attacker's id.
    public static final int DEDO_BEHIND = 23;
    // The cut shows up: pos = the attacker's middle, dir = the way it flies.
    public static final int DEDO_CUT = 24;
    // Danku rises: pos = the wall's foot center, dir = its face (toward the blow), a = 0 rise / 1 hit (pos = the hit) / 2 shatter.
    public static final int DANKU = 25;
    // Fragor: entity = caster, pos = the hand, dir = where it detonates, a = the sphere's radius.
    public static final int FRAGOR = 26;
    // Hado 99: entity = caster, pos = his feet, dir = where the dragons land, a = the crater's radius.
    public static final int HADO99 = 27;
    // The Reiatsu (Shift+B): entity = caster, pos = his feet, a = radius, b = power 0..1, every tick while held.
    public static final int PRESS = 28;
    // ...and it explodes: a = radius (0 = cut short: it only fades), b = charge.
    public static final int PRESS_BOOM = 29;

    private static final double VIEW_DISTANCE = 220;

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
