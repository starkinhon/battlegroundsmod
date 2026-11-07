package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// Shift + Z of Gojo (gojo.azul_black_flash.json): the Blue pulls the enemy to him and he meets it with a Black Flash.
// No orb: he holds his hand out and the Blue (only implied, a light bending on the target) grabs whoever he aims at, up
// to alcanceAlvo blocks away, and drags it in a straight line, faster and faster (puxar). lentoDistancia blocks before
// it arrives time slows down: for lentoTicks it comes in slowly while his fist cocks back crackling black lightning.
// Then the punch, always a Black Flash: the impact frames, and the punch "fits" (encaixe): the target stays stuck on
// the fist for golpe.encaixeTicks while the black lightning bursts out in waves (golpe.ondas, ticks after the punch)
// and the crater opens at the punch point (only with the gamerule soulsBlockDestruction). Only then a last burst
// throws it far. Holding the key from the start charges it (segurado.carregarTicks to full): everything in golpe goes
// from x (no charge) to xMax (full charge).
// Timeline: "uso" (pressed), "puxar" (the pull starts, on the target), "lento" (slow motion, at the fist), "blackFlash"
// (the punch, at the contact point), "onda" (each wave), "arremesso" (the throw), "impacto" (where the thrown target
// stops). The "tempo" and "animacao" blocks are also read as an AnimationSpec (the punch, held through the encaixe).
public record BlackFlashSpec(String skill, String nome, Puxar puxar, Segurado segurado, Golpe golpe, List<Som> sons) {

    // alcanceAlvo: how far the target can be; velocidade: blocks per tick at the end of the pull (it starts slow and
    // speeds up); minTicks/maxTicks: the pull never takes less or more than this; distanciaFrente: blocks between the two
    // of them at the punch (plus their widths); lentoDistancia: how far from the punch the slow motion starts;
    // lentoTicks: how long the slow motion lasts.
    public record Puxar(double alcanceAlvo, double velocidade, int minTicks, int maxTicks, double distanciaFrente,
            double lentoDistancia, int lentoTicks) {
    }

    // carregarTicks: holding the key this long from the start gives the full charge (bosses: their own charge).
    public record Segurado(int carregarTicks) {
    }

    // dano at the punch and danoFinal at the throw; empurrao (blocks per tick the target is thrown) and subir (how much of
    // the throw goes up); encaixeTicks: the target stuck on the fist; ondas: the ticks after the punch when a wave of
    // black lightning bursts; atordoadoTicks: stun left on the target after the throw; crateraRaio: the crater at the
    // punch point (it grows with each wave up to this).
    public record Golpe(float dano, float danoMax, float danoFinal, float danoFinalMax, double empurrao, double empurraoMax, double subir,
            int encaixeTicks, List<Integer> ondas, int atordoadoTicks, double crateraRaio, double crateraRaioMax) {
    }

    public static final String SKILL = "gojo.azul_black_flash";

    public static BlackFlashSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, BlackFlashSpec.class, awakened);
    }
}
