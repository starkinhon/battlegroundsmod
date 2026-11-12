package com.starkinhon.battlegroundsmod.animal.naruto;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Naruto creatures' own effects (client/fx/NmFx), sent through SubNet in their own range 380-399 (the next free
// block after Makima's 340-379, see the faixas memory): what the preview draws that no existing effect had.
public final class NmNet {
    public static final int FIRST = 380;
    // a water ball flying from pos to pos + dir (a = radius, b = flight ticks), bursting at the end
    public static final int WATER_BALL = 380;
    // the water prison's bubble around someone (id = who, a = radius, c = 1 bursts it)
    public static final int BUBBLE = 381;
    // poison gas spreading on the ground (a = radius, b = ticks)
    public static final int GAS = 382;
    // spores stuck on someone (id = who, a = how many are left; 0 = all gone)
    public static final int SPORES = 383;
    // wooden stakes rising one after the other from pos to pos + dir (a = how many)
    public static final int STAKES = 384;
    // the wooden cage closing around pos (a = ticks it stands, c = 1 breaks it now)
    public static final int CAGE = 385;
    // the genjutsu's tomoe spinning between the crow's eye (pos) and pos + dir (a = ticks)
    public static final int TOMOE = 386;
    // three rings of sound running from pos along dir
    public static final int SOUND = 387;
    // the fan's wind blades thrown from pos along dir (a = half spread in radians)
    public static final int WIND = 388;
    // a flock of crows from pos to pos + dir (a = ticks)
    public static final int FLOCK = 389;
    // the Hidden Mist around pos (a = radius, b = ticks)
    public static final int MIST = 390;
    // the chain and hook from the hand (pos) to pos + dir (id = thrower; c = 1 pulls it back)
    public static final int CHAIN = 391;
    // the umbrella spinning over pos and its rain of needles (a = radius, b = ticks)
    public static final int UMBRELLA = 392;
    // a shockwave on the ground (a = radius)
    public static final int SHOCK = 393;
    // crow feathers falling around pos (a = ticks)
    public static final int FEATHERS = 394;
    // the Gatsuga's drill of wind around a body (id = who, a = 1 while it spins, 0 stops)
    public static final int DRILL = 395;
    // the screen of the one hit (id = who; a = 0 dark, 1 genjutsu red, 2 foam blur, 3 drowning; b = ticks)
    public static final int SCREEN = 396;
    // a splash of water (a = size)
    public static final int SPLASH = 397;
    // the marionetista's chakra threads to its puppet (id = marionetista, pos = its hands, dir = to the puppet's back)
    public static final int THREADS = 398;
    public static final int LAST = 399;

    private NmNet() {
    }

    public static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        SubNet.send(level, kind, id, pos, dir, a, b, c);
    }

    // a screen effect only the one hit sees
    public static void screen(LivingEntity target, int mode, int ticks) {
        if (target instanceof ServerPlayer player) {
            SubNet.sendTo(player, SCREEN, player.getId(), player.position(), Vec3.ZERO, mode, ticks, 0);
        }
    }
}
