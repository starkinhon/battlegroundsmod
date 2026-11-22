// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.toji;

// Toji's own effects (client/fx/TjFx), sent through SubNet in their range 560-579 (faixas memory). The dust, rings,
// tremors and flying blocks are the end-game bosses' (RuinNet / EpicFx).
public final class TjNet {
    public static final int FIRST = 560;
    // The Corrente de Mil Milhas turning like a fan (stream, id = Toji): pos = the middle of the spin (his feet),
    // dir = (angle of the spear in radians, radius in blocks, height of the spear over his feet), a = 1 on / 0 reeled in,
    // b = turning speed (radians a second). The client runs the chain's physics (MkChain links, no red light) from his
    // hand to the mod's spear at the end.
    public static final int FAN = 560;
    // A building toppling whole: pos = its lowest corner (x0, street + 1, z0), dir = its size (w, height, d), a = the way
    // it falls (0 +x, 1 -x, 2 +z, 3 -z), b = ticks before it starts, c = seed. The client copies its blocks before the
    // server takes them away and draws it falling over its base edge, then breaking up on the ground.
    public static final int TOPPLE = 561;
    // An expansion shattering into shards: pos = its middle, a = radius, b = seed.
    public static final int SHARDS = 562;
    // An afterimage where he stood for an instant (the Sumiço's hops): pos = feet, a = yaw (degrees), b = 0 crouched,
    // 1 running, 2 kicking.
    public static final int GHOST = 563;
    public static final int LAST = 579;

    private TjNet() {
    }
}
