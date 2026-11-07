package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Shift + Z of Gojo (gojo.azul_black_flash.json): the Blue pulls the enemy to him and he meets it with a Black Flash.
// A small Blue forms on his left palm (formarTicks) and grabs the target he aims at (up to alcanceAlvo blocks). The
// target is dragged in a straight line, faster and faster, to distanciaFrente blocks in front of him (puxar), unable to
// move or use skills. Let go of the key before it arrives and the punch goes at the lightest charge as soon as it gets
// there; still held, the target floats in front of him while the punch charges (segurado), and it goes when the key is
// let go (or at segurado.maxSegurarTicks). The punch is always a Black Flash: the world stops for golpe.parada ticks
// (the impact frame), the black and red lightning bursts out of the fist and the target is thrown far, breaking the
// blocks it flies through (only with the gamerule soulsBlockDestruction). Everything in golpe goes from x (no charge)
// to xMax (full charge).
// Timeline: "uso" (pressed, the Blue forms on the palm), "puxar" (the pull starts, on the target), "chegou" (it reached
// him), "carregar" (the charge starts), "blackFlash" (the hit, at the contact point), "impacto" (where the thrown target
// stops). The "tempo" and "animacao" blocks are also read as an AnimationSpec (the punch).
public record BlackFlashSpec(String skill, String nome, Puxar puxar, Segurado segurado, Golpe golpe, List<Som> sons) {

    // alcanceAlvo: how far the target can be; formarTicks: the Blue forming before it pulls; velocidade: blocks per tick
    // at the end of the pull (it starts slow and speeds up); minTicks/maxTicks: the pull never takes less or more than
    // this; distanciaFrente: blocks between the two of them when it ends (plus their widths); orbeRaio/orbeRaioMax: the
    // Blue on the palm, from formed to fully charged.
    public record Puxar(double alcanceAlvo, int formarTicks, double velocidade, int minTicks, int maxTicks, double distanciaFrente,
            double orbeRaio, double orbeRaioMax) {
    }

    // carregarTicks: from arriving to full charge; maxSegurarTicks: held longer than this it goes anyway (bosses: their
    // own charge, never more than this).
    public record Segurado(int carregarTicks, int maxSegurarTicks) {
    }

    // dano, empurrao (blocks per tick the target is thrown), subir (how much of the throw goes up), paradaTicks (the
    // world stopped on the hit), each from no charge to full (xMax); atordoadoTicks: stun left on the target;
    // quebraRaio: blocks broken around the target while it flies; quebraTicks: for how long.
    public record Golpe(float dano, float danoMax, double empurrao, double empurraoMax, double subir, int paradaTicks, int paradaTicksMax,
            int atordoadoTicks, double quebraRaio, int quebraTicks) {
    }

    public static final String SKILL = "gojo.azul_black_flash";

    public static BlackFlashSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, BlackFlashSpec.class, awakened);
    }
}
