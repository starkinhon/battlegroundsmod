package com.starkinhon.battlegroundsmod.power.powers.makima;

import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// What Makima's skills tell the clients to draw (client/fx/MakimaFx), through the same ModPayloads.SubFx as the
// SubAlmas, on kinds of their own (200 on). The client plays most of these on its own timeline, copied from the
// approved previews (forja/makima-*), so the server only says when each one starts.
public final class MkNet {
    // Ring eyes lit: id = entity, a = ticks (0 puts them out), b = 0 Makima's own, 1 someone obeying her (puppets too).
    public static final int EYES = 200;
    // X Corrente: id = caster, dir.x = target entity id (-1 missed), pos = where a missed chain lands, a = ticks the chain
    // holds before it lets go, c = 1 lets go now.
    public static final int X_CHAIN = 201;
    // V without souls: the chains out of the ground on one body. id = target, a = caster id, b = its order (a small
    // delay each), c = 0 binds, 1 "Morram" (squeeze and crush), 2 back into the ground.
    public static final int V_CHAINS = 202;
    // A crack in the ground at pos: a = radius, b = style (0 the V's (radial, with the chains' holes), 1 a kneel's short
    // one, 2 the Ritual's web), c = seed.
    public static final int CRACK = 203;
    // A dark pool on the ground at pos (bodies sink into it, puppets rise from it): a = size, b = ticks it lasts, c = 0
    // the shadow pool, 1 blood.
    public static final int POOL = 204;
    // Pieces flying: pos, dir (a push), a = how many, b = force, c = 0 dirt and grass, 1 a body (gore), 2 a body crushed
    // from above (flat, low).
    public static final int BITS = 205;
    // Red mist (each drop its own size and flight): pos, dir, a = how many.
    public static final int MIST = 206;
    // The shock shell of a hit: pos, a = size, c = seed.
    public static final int SHELL = 207;
    // A ring of dust on the ground: pos, a = size.
    public static final int DUST = 208;
    // Bang: id = caster, pos = the fingertip, dir = to the hit, a = 1 a shot, 2 the finisher, b = shot index.
    public static final int BANG = 209;
    // The eyes' mark over a target: id = target, a = ticks, b = 0 Bang's lock, 1 the Ritual's mark.
    public static final int MARK = 210;
    // A puppet's leash, from her back to its nape (every 10 ticks while it lasts): id = puppet, a = owner id, b = 0 a mob,
    // 1 a soul (its body bound by two more chains), c = ticks since it rose.
    public static final int LEASH = 211;
    // A pulse along a puppet's leash: id = puppet, a = owner, b = 0 the Primeiro-Ministro (the blow runs down to it), 1
    // the Ritual (it says the name).
    public static final int PULSE = 212;
    // The Ritual's column of pressure coming down on pos: a = radius, b = ticks.
    public static final int COLUMN = 213;
    // The world loses its color for whoever sees it: pos = where, a = ticks, b = strength (0..1).
    public static final int GRAY = 214;
    // Her voice runs through the air from the mouth to the target: pos = mouth, dir = to the target's head.
    public static final int VOICE = 215;
    // A body that sinks into the ground or rises out of it: id = entity, a = 0 sinks, 1 rises (jerky), b = ticks.
    public static final int SINK = 216;
    // A dead body pulled by a chain to her and sunk into her shadow (stored): id = the dead body, a = owner id.
    public static final int STORE = 217;
    // Seeing through the rats (only to her): a = ticks, b = rat id picked for the jump (-1 none).
    public static final int RAT_VISION = 218;
    // Rats running out of a pool (each its own shape): pos, a = how many, c = seed.
    public static final int RATS_FLEE = 219;
    // Drops of her own blood from the hand (the Ritual without puppets): id = caster, a = ticks.
    public static final int DRIP = 220;
    // Impact frame + shock for whoever is near: pos, a = 0 Bang, 1 Bang finisher, 2 Ritual's crush, 3 Primeiro-Ministro.
    public static final int IMPACT = 221;
    // The Ritual's knees and floor: id = target, a = level (1 kneel, 2 on all fours, 3 crushed).
    public static final int CRUSH = 222;
    // Stretched-out Azul / cuts of a soul puppet's "Morram": id = puppet, dir.x = target id.
    public static final int SOUL_KILL = 223;
    // One of her rats (each its own shape by the seed): id = caster, pos = where it sits, a = seed, b = ticks it stays (0
    // it runs off now), c = its index (the rings light from the nearest one out).
    public static final int RAT = 224;

    private static final double RANGE = 160;

    private MkNet() {
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        ModPayloads.SubFx payload = new ModPayloads.SubFx(kind, id, pos, dir, a, b, c);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < RANGE * RANGE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, float a, float b, float c) {
        send(level, kind, id, pos, Vec3.ZERO, a, b, c);
    }

    // Only to one player (what only she sees: the rats' vision).
    public static void to(ServerPlayer player, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.SubFx(kind, id, pos, dir, a, b, c));
    }
}
