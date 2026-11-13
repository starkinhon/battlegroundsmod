package com.starkinhon.battlegroundsmod.power.powers.freezaboss;

// Freeza Poder Total's own effects (client/fx/FzFx), sent through SubNet in their range 460-479 (faixas memory). The
// rest of his kit reuses the Dragon Ball effects already in the game (SAW for the Kienzan, DEATH_BEAM, DEATH_BALL,
// KI_BOOM, KI_AURA, VANISH, HIT, SLASH), as the approved preview does (forja/freeza-boss).
public final class FzNet {
    public static final int FIRST = 460;
    // An aim line on the ground (the Rajada Mortal): from pos along dir (its 22 blocks), a = delay in ticks before it
    // shows, b = ticks until it locks (then it fades out as the beams fire).
    public static final int AIM = 460;
    // A warning on the ground at pos, a = radius, b = ticks it takes, c = style: 0 the Blitz's purple circle closing
    // (with his shimmer standing in it), 1 a lava geyser's orange circle, 2 a ki ball's red reticle, 3 a shadow growing
    // (the island, the Death Ball), 4 purple cracks running out to a ring (the island being torn out, the Onda
    // Imperial), 5 the Poder Total's cracks (no ring), 6 lava cracks running out (Namek collapsing; b = ticks to run).
    public static final int MARK = 461;
    // The telekinetic island (stream): id = its own. a = 0 grab (pos = its middle on the ground, b = radius, c = depth:
    // the client copies the blocks there before the server takes them), 1 moving (pos = where its top's middle is, dir
    // = (yaw, tilt, spin)), 2 slammed (pos = where), 3 broken in the air (pos = where).
    public static final int ISLAND = 462;
    // A lava geyser at pos (the ground): a = height, b = seed.
    public static final int GEYSER = 463;
    // A ki ball of the Chuva de Ki flying from pos to pos + dir in a ticks.
    public static final int KI_RAIN = 464;
    // Namek collapsing round pos (stream, id = Freeza): a = 1 on, 0 off; b = radius. The sky goes red and the sea dark
    // for whoever is in it.
    public static final int DOOM = 465;
    // The tail whip: pos = Freeza's hips, dir = the way it sweeps (flat), a = side (1 or -1).
    public static final int WHIP = 466;
    public static final int LAST = 479;

    private FzNet() {
    }
}
