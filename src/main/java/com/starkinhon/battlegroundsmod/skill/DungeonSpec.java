package com.starkinhon.battlegroundsmod.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// padrao.dungeon.json: the roguelike mode (/souls dungeon, challenge/Dungeon). A run starts human (Tier 10, no soul, bare
// fists, only sementesIniciais Sementes dos Deuses) and climbs the etapas one fight after another, in a random arena each
// time. Each etapa: the challenge character to fight (boss, its id in souls_bosses), the soul it gives (alma, a power id) and
// the ADAPTED tier of the fight, for the boss and for the player alike (so Kuririn is Tier 10 here, not his own 8).
// vidas: deaths a run can take (a death restarts the fight); vidasMax: the most the "Vida extra" choice can raise it to.
// contagemSegundos: the wait before each fight. efeitoMax: the most levels the Forca and Resistencia choices stack.
// ascensaoMax: the most tiers the "Ascensao" choice can put the player above the fight's tier. opcoesPorEscolha: how many
// choices are offered after each win (the pool is in challenge/Dungeon.NORMAL and CHAOS; efeitoMax and ascensaoMax are no longer used: the stat cards are gone).
// embaralhar (per etapa): the characters of all the etapas that have it are drawn again, at random, at the start of every run, so
// no two runs have the same order (the SubAlmas are marked, the divine souls are not and stay at the end, in order). The tier
// belongs to the POSITION, not to the character: whoever falls on an etapa fights there at that etapa's tier (sorteio()).
public record DungeonSpec(int vidas, int vidasMax, int contagemSegundos, int opcoesPorEscolha, int ascensaoMax, int efeitoMax,
        int sementesIniciais, int sementesPremio, List<Etapa> etapas) {

    public record Etapa(String boss, String alma, int tier, boolean embaralhar) {
    }

    // The etapas of ONE run: the marked ones trade their characters at random among themselves, each keeping the tier of its place;
    // every other etapa stays exactly where it is.
    public List<Etapa> sorteio(Random random) {
        List<Integer> places = new ArrayList<>();
        for (int i = 0; i < etapas.size(); i++) {
            if (etapas.get(i).embaralhar()) {
                places.add(i);
            }
        }
        List<Etapa> drawn = new ArrayList<>();
        for (int place : places) {
            drawn.add(etapas.get(place));
        }
        Collections.shuffle(drawn, random);
        List<Etapa> run = new ArrayList<>(etapas);
        for (int k = 0; k < places.size(); k++) {
            Etapa place = etapas.get(places.get(k));
            Etapa character = drawn.get(k);
            run.set(places.get(k), new Etapa(character.boss(), character.alma(), place.tier(), true));
        }
        return run;
    }

    public static DungeonSpec get() {
        return SkillSpecs.get("padrao.dungeon", DungeonSpec.class, false);
    }
}
