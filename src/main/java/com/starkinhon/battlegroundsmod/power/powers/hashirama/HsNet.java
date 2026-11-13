package com.starkinhon.battlegroundsmod.power.powers.hashirama;

// Hashirama's effects (client/fx/hs/HsFx), sent through SubNet in their own range 420-459 (faixas memory). Every event
// carries the page's frame: pos = where the page's origin sits on the ground, dir = where the page's -z points (flat),
// so the client draws the approved preview's wood in the same place and timing.
public final class HsNet {
    public static final int FIRST = 420;
    // Nascimento da Floresta: pos = his feet, dir = aim. The forest grows in its wave and stands a while.
    public static final int FOREST = 420;
    // Prisão dos Quatro Pilares: pos = center. a = 0 cracks and closes (b = 1 if it closes empty), 1 = breaks, 2 = gone.
    public static final int PRISON = 421;
    // A stake cluster: pos, dir = way it runs, a = size, b = seed, c = 0 cluster, 1 finale (11 thick), 2 the sleeper's stake.
    public static final int STAKES = 422;
    // Where the stake front bumps the ground (the warning 0.32 s before a cluster): pos.
    public static final int BUMP = 423;
    // Mundo das Árvores Floridas: pos = center of the field. c = 1 ends it early.
    public static final int FLOWERS = 424;
    // The sleep veil on one player's screen: a = sleep 0..1.
    public static final int SLEEP = 425;
    // Cúpula de Madeira: pos = his feet. a = 0 rises, 1 creaks (dir = where it was hit), 2 counter stakes (dir), 3 opens.
    public static final int DOME = 426;
    // Lança Ramificada: id = Hashirama. a = 0 the arm turns to wood (pos = hand, dir = aim); a = 1 fires from pos to
    // dir (the point the branches close on), b = 1 caught someone.
    public static final int LANCE = 427;
    // Dragão de Madeira: id = Hashirama. a = 0 warning (pos = the hole on the ground, dir = flat way to the target; the
    // client plays the crack, the burst and the spiral rise on its own clock, 1.05 s + 1.67 s), a = 2 hunting, every tick
    // (pos = head, dir = heading), a = 3 missed and stuck (pos = head), a = 4 caught (pos = target's feet), a = 5 lifts
    // and slams, a = 7 goes away early.
    public static final int DRAGON = 428;
    // The Buddha: id = Hashirama. pos = where he clapped (page origin), dir = his target. a = 0 rises (2.4 s), 2 dissolves.
    // The Buddha stands BUDDHA_Z behind the origin; its head top is TOP high (page units, 0.9 blocks).
    public static final int BUDDHA = 429;
    // A slap of the Buddha: id = Hashirama, pos = ground target, a = 1 the Top Strike (giant), b = running count (the
    // client picks a free arm of the fan).
    public static final int SLAM = 430;
    // Wood splinters: pos, dir = strength (blocks/s), a = how many.
    public static final int SPLINTERS = 431;
    // Sage Mode marks / shimmer at pos (a = 1 on).
    public static final int SAGE = 432;
    public static final int LAST = 459;
    public static final double PU = 0.9, BUDDHA_Z = 6.5, TOP = 17.2, HEAD_Z = -0.65;

    private HsNet() {
    }
}
