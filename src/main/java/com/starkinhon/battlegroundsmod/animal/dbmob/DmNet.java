package com.starkinhon.battlegroundsmod.animal.dbmob;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball creatures' own effects (client/fx/DmFx), sent through SubNet in their own range 400-419 (the next free
// block after the Naruto creatures' 380-399, see the faixas memory): what the preview draws that no effect of the mod
// had. The rest of their blows use the Dragon Ball kits' own (ki shots, beams, blasts) and the shared ones (cracks,
// dust, ice, slashes).
public final class DmNet {
    public static final int FIRST = 400;
    // the Saibaman's acid: a ball from pos to pos + dir in c ticks, then a bubbling puddle there (a = radius, b = ticks)
    public static final int ACID = 400;
    // the pirate's bomb: thrown from pos to pos + dir in c ticks, its fuse burning until a ticks, then gone
    public static final int BOMB = 401;
    // the Red Ribbon's rocket from pos to pos + dir in a ticks, smoke behind it
    public static final int ROCKET = 402;
    // Babidi's magic circle under pos (a = radius, b = ticks until it goes off, c = 1 goes off now)
    public static final int CIRCLE = 404;
    // the Majin M lit on id's forehead (a = ticks, 0 takes it off)
    public static final int MAJIN = 405;
    // Yamu's needle: a stream of light from pos (the one drained) to pos + dir (the flask), id = Yamu, a = how full;
    // c = 1 ends it
    public static final int DRAIN = 406;
    // the dragon's fire: a jet from pos (the mouth) along dir (its length) over a ticks
    public static final int BREATH = 407;
    // Tao Pai Pai's pillar (stream): id = its own, pos = its foot, dir = its axis (length), c = 1 crumbles it
    public static final int PILLAR = 408;
    // Yakon eating the light: motes spiraling from a radius into pos (the mouth) over b ticks
    public static final int SUCK = 409;
    // a handful of sand thrown in a fan from pos along dir (a = half spread in radians)
    public static final int SAND = 410;
    // the darkness round pos (Yakon's Escuridão): a = radius, b = ticks
    public static final int DARK = 411;
    public static final int LAST = 419;

    private DmNet() {
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        SubNet.send(level, kind, id, pos, dir, a, b, c);
    }
}
