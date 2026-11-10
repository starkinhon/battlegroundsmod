package com.starkinhon.soulsjjk.skill;

import java.util.List;

// Red (gojo.vermelho.json), Gojo's X. Positive energy through the Limitless: the reverse of the Blue, a massive repulsion.
// The red orb lights up at his fingertip (only sparks leaking out of it at first; past segurado.cargaRabos of charge its
// tails form and spin around it). Tap: let go within segurado.decidirTicks and it fires at once at the lightest charge.
// Held: it charges up to full in segurado.carregarTicks and fires only when the key is let go (bosses after their own
// charge, at most maxSegurarTicks). Firing, the orb doesn't travel: it bursts into a wall of repulsion straight where he
// aims, a cylinder of destruction that carves a tunnel through the ground and walls (only with the gamerule
// soulsBlockDestruction) and throws whoever is in it far away. The charge takes everything from tiro.x to tiro.xMax.
// Shift + X (shift): the move he used on Toji. He appears behind the target he aims at (no trail, no sound), floating a
// little, raises the finger and fires the Red point blank.
// Timeline: "uso" (press), "carregar" (held), "rabos" (the tails start forming), "disparo" (fired, at the fingertip),
// "disparoForte" (also, when fired charged past segurado.cargaRabos), "acerto" (on each one it hits), "impacto" (where
// the tunnel ends), "teleporte" (Shift: where he appears). The "tempo" and "animacao" blocks are also read as an
// AnimationSpec (the finger raised, then pointed).
public record VermelhoSpec(String skill, String nome, Orbe orbe, Segurado segurado, Tiro tiro, Shift shift, List<Som> sons) {

    // raio: the orb (blocks); escalaMax: how many times bigger fully charged.
    public record Orbe(double raio, double escalaMax) {
    }

    // decidirTicks: held longer than this it charges; carregarTicks: to full; maxSegurarTicks: bosses only (players let
    // go with the key); cargaRabos: charge from which the tails form.
    public record Segurado(int decidirTicks, int carregarTicks, int maxSegurarTicks, double cargaRabos) {
    }

    // alcance: how far the tunnel goes (blocks); raio: its radius; velocidade: blocks per tick its front moves; dano and
    // empurrao (the throw, blocks per tick) to whoever the front reaches, subir: how much of it goes up. Each one from the
    // lightest charge to the full one (xMax). blocosPorTick: at most this many blocks carved each tick. pedacos: at most
    // this many carved blocks fly off to the sides as debris.
    public record Tiro(double alcance, double alcanceMax, double raio, double raioMax, double velocidade, double velocidadeMax, float dano,
            float danoMax, double empurrao, double empurraoMax, double subir, int blocosPorTick, int pedacos) {
    }

    // alcanceAlvo: how far the target can be; atras: blocks behind it he appears; flutuar: blocks above the ground he
    // floats; preparoTicks: from appearing to firing; carga: the charge the shot has; dano and empurrao override the
    // charge's ones; stunTicks: how long whoever it hits stays stunned.
    public record Shift(double alcanceAlvo, double atras, double flutuar, int preparoTicks, double carga, float dano, double empurrao, int stunTicks) {
    }

    public static final String SKILL = "gojo.vermelho";

    public static VermelhoSpec get(boolean awakened) {
        return SkillSpecs.get(SKILL, VermelhoSpec.class, awakened);
    }
}
