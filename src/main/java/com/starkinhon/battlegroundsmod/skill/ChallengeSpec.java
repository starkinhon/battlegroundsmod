package com.starkinhon.battlegroundsmod.skill;

import java.util.List;

// The Livros dos Desafios (souls_skills/padrao.desafios.json): one book per universe, one challenge per character of
// that universe (its SubAlmas and its bosses). Each challenge is fought in its own arena; winning it lets that character
// spawn in the normal world (challenge/ChallengeArenas). The name, tier and color come from the character's boss file
// (souls_bosses/<id>.json) and power; foco is the line on the card that explains how that character fights.
// raio / altura: size of every arena (radius of the floor, height of the invisible walls).
// itens (optional, per challenge): items given the first time the character is beaten, "namespace:item" or
// "namespace:item 3" for a stack (Stark 2026-11-10: some challenges give items).
// piso: the universe's floor blocks (mixed at random), pilar: the block of the broken pillars around the arena.
// descricao: the universe's line on the screen where the player picks where to live (challenge/Universes).
// subAlmasParaChefes: SubAlmas of a universe the player must beat before its bosses' arenas open (Stark 2026-11-10;
// a universe with fewer SubAlmas than that asks for all of them).
// chanceRara: chance that a challenge character not yet beaten still spawns in its universe's world, so the world
// never feels empty (Stark 2026-11-10). Once beaten it spawns normally.
public record ChallengeSpec(int raio, int altura, int subAlmasParaChefes, double chanceRara, List<Universe> universos) {

    public record Universe(String id, String nome, String cor, String descricao, List<String> piso, String pilar, List<Challenge> desafios) {
        public int color() {
            try {
                return Integer.parseInt(cor.replace("#", ""), 16);
            } catch (RuntimeException e) {
                return 0xFFFFFF;
            }
        }
    }

    public record Challenge(String id, String foco, List<String> itens) {
    }

    public Challenge challenge(String id) {
        for (Universe universe : universos) {
            for (Challenge c : universe.desafios()) {
                if (c.id().equals(id)) {
                    return c;
                }
            }
        }
        return null;
    }

    public static ChallengeSpec get() {
        return SkillSpecs.get("padrao.desafios", ChallengeSpec.class, false);
    }

    public Universe universe(String id) {
        for (Universe universe : universos) {
            if (universe.id().equals(id)) {
                return universe;
            }
        }
        return null;
    }

    // The universe a challenge belongs to.
    public Universe universeOf(String challenge) {
        for (Universe universe : universos) {
            for (Challenge c : universe.desafios()) {
                if (c.id().equals(challenge)) {
                    return universe;
                }
            }
        }
        return null;
    }

    // Position of the challenge across every book: each one has its own arena slot.
    public int slot(String challenge) {
        int index = 0;
        for (Universe universe : universos) {
            for (Challenge c : universe.desafios()) {
                if (c.id().equals(challenge)) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }
}
