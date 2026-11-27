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
    // Nanami's ruler: on a body (dir zero, id the body) or on a face (dir its normal, pos the 7:3 point, b its size);
    // a ticks, 0 takes it off.
    public static final int NANAMI_RULER = 601;
    // Nanami's critical at pos (a 1 = big): flash, gold ring, two crossing cuts, one impact frame and a shake.
    public static final int NANAMI_CRIT = 602;
    // Nanami's crack from pos along dir (a = Direction 3D value of the face, b 0 wall / 1 ground).
    public static final int NANAMI_CRACK = 603;
    // Overtime's warm light round Nanami (id), a ticks, 0 ends it.
    public static final int NANAMI_WARM = 604;
    // The cleaver's cloth at pos flying along dir: a 0 snaps, 1 comes loose whole.
    public static final int NANAMI_CLOTH = 605;
    // Overtime's tie wound round Nanami's (id) right fist, a ticks, 0 ends it.
    public static final int NANAMI_TIE = 606;
    // Itadori. The Divergent Fist mode: the cursed energy on both fists of id, lagging behind them; a ticks, 0 ends it.
    public static final int ITA_AURA = 607;
    // The Black Flash ring of the mode's 5th punch, closing on id (the target) in a ticks, b the window each side; a
    // message with c 1 (hit) or 2 (missed) ends it.
    public static final int ITA_RING = 608;
    // The echo of a punch landing a moment after it, at pos going along dir.
    public static final int ITA_ECHO = 609;
    // The Teia de Tesoura at pos (the palm on the ground), id its seed (power/powers/sub/jj/ItadoriWeb).
    public static final int ITA_WEB = 610;
    // The Corte de Tesoura's cut on someone at pos (chest), dir back to his feet; a: which of the two (0, 1).
    public static final int ITA_CUT = 611;
    // The scissors in his hand while he charges the Clivar (id), a ticks, 0 ends them.
    public static final int ITA_HAND_SCISSORS = 612;
    // The Clivar landing on someone at pos (chest), dir the way it goes (flat), a how far the ground opens behind them.
    public static final int ITA_CLIVAR = 613;
    // The Marca na Alma's blink, only on Itadori's own screen: his eyes shut and open on the soul of id.
    public static final int ITA_LIDS = 614;
    // A soul marked by Itadori, trembling inside the body of id: a ticks, 0 ends it.
    public static final int ITA_SOUL = 615;
    // The soul of id torn out along dir and snapping back.
    public static final int ITA_RIP = 616;
    // The Escama Vermelha: the veins of id light up once under the skin, from the heart.
    public static final int ITA_VEINS = 617;
    // Itadori's Expansão de Domínio (power/powers/sub/jj/ItadoriDomain, drawn by client/fx/ItadoriDomainScene): id the
    // caster, pos where it opened, dir (his yaw, the mode ItadoriDomain.M_*, 1 when this player is in it), b its age.
    public static final int ITA_DOMAIN = 618;
    public static final int LAST = 619;

    private Jj2Net() {
    }

    public static boolean has(int kind) {
        return kind >= FIRST && kind <= LAST;
    }
}
