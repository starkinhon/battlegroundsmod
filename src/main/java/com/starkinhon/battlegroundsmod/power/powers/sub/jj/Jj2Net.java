// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

// The remade Jujutsu SubAlmas of the port's second batch (Todo, Nanami, Itadori; previews approved by Stark 2026-11-27):
// their SubNet kinds, sent with SubNet.send and drawn by client/fx/Jj2Fx. Range 600-619, fixed for this batch so the four
// port PRs never share a number (Toji 560-579 below; the other batches: 580-599, 620-639, 640-659). souls_ki kinds of
// this batch: 85-94.
public final class Jj2Net {
    public static final int FIRST = 600;
    // Todo's V Black Flash, the contact in slow motion: for whoever is near pos the screen loses its color for a ticks
    // (fading in and out fast), and small black and red sparks crackle on the fist at pos.
    public static final int TODO_GRAY = 600;
    public static final int LAST = 619;

    private Jj2Net() {
    }

    public static boolean has(int kind) {
        return kind >= FIRST && kind <= LAST;
    }
}
