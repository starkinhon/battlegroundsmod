package com.starkinhon.battlegroundsmod.power.powers.sub.db;

// What Senhor Kaioh teaches Goku (the Mini Genki Dama on Shift+V), drawn by client/fx/KaiohFx from these SubNet kinds in
// their own range 500-519 (faixas memory), as the approved preview draws it (mestres/mini-genki.html v3).
public final class KaiohNet {
    public static final int FIRST = 500;
    // The Mini Genki Dama (stream, id = its own): pos = the ball, dir = the ground under the hand (charging), a = radius,
    // b = 0 charging in the hand, 1 flying, 2 gone (it landed or was lost), c = how strong the spiral from the ground is.
    public static final int MINI_GENKI = 500;
    // Its landing at pos: dir.x = the ground's height under it (or the column's foot, in the air), dir.y = the height
    // where the ball hit, a = size, b = seed.
    // The first 4 ticks it sinks in and pulls the dust, then the column of light, the ring, the flash and the smoke.
    public static final int MINI_GENKI_BOOM = 501;
    public static final int LAST = 519;

    private KaiohNet() {
    }
}
