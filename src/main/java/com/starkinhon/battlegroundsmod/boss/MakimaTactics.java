package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.makima.Almas;
import com.starkinhon.battlegroundsmod.power.powers.makima.Corrente;
import com.starkinhon.battlegroundsmod.power.powers.makima.Fantoche;

import net.minecraft.world.entity.LivingEntity;

// How the Makima boss fights (Stark, 2026-11-12). Calm, from afar, never in a hurry:
// - Her puppets first: up to 3 out at once, let out again from her shadow while she has any kept. They take the fatal
//   blow for her (Primeiro-Ministro, puppets only for the boss), so the fight is killing them first.
// - She keeps her distance (a blink back when someone gets in her face), the Corrente to bind and drain, the Ordem
//   (Venha / Ajoelhe / Woof / Morra by how they stand), the Bang: weak on the healthy, the finish on 15% or less.
// - From half life: the Ritual dos Nomes (each puppet she gives up crushes the target; without puppets her own blood,
//   only while she can spare it) and the chains out of the ground on everyone around, then "Morram."
// - The souls she took (Stark 2026-11-12, "bosses usam tudo que têm"): a player or a boss she killed comes back on her V,
//   fights for her, and "Morram." when the target is low or after 15 s.
final class MakimaTactics extends TacticsBase {
    private static final double LATE = 0.5;
    private static final float FINISH = 0.15f;
    private static final double TOO_CLOSE = 4.5;

    private static final int SOULS_MORRAM = 300;
    private static final float SOULS_FINISH = 0.3f;

    private boolean morramSaid;
    private int soulsOut;
    private boolean soulsMorram;

    MakimaTactics(MakimaEntity boss) {
        super(boss);
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        // The chains out of the ground hold them: "Morram." (Corrente waits for its moment by itself.)
        if (Corrente.grounded(boss)) {
            if (!morramSaid) {
                morramSaid = Corrente.morram(boss);
            }
        } else {
            morramSaid = false;
        }
        // Her souls out: "Morram." once, when the target is low or they have fought long enough.
        if (Almas.out(boss) > 0) {
            soulsOut++;
            if (!soulsMorram && soulsOut > 40 && (soulsOut >= SOULS_MORRAM || theirLife <= SOULS_FINISH) && Almas.morram(boss)) {
                soulsMorram = true;
                ai.say("almas: morram");
            }
        } else {
            soulsOut = 0;
            soulsMorram = false;
        }
        if (ai.usingMove() || Freeze.isFrozen(boss)) {
            return false;
        }
        // Her puppets out, as many as she may.
        if (Fantoche.out(boss) < 3 && Fantoche.kept(boss) > 0 && ai.has("fantoche") && ai.isOff("fantoche") && ai.use("fantoche")) {
            ai.say("chamando um fantoche");
            return true;
        }
        // Someone in her face: a step back into the distance (not while they are held: then she uses it).
        if (!ai.chaos() && distance < TOO_CLOSE && blinkCooldown <= 0 && !ai.stunned() && random.nextDouble() < 0.08 && blinkAway(ai, 11)) {
            ai.say("mantendo distancia");
            return true;
        }
        return false;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        double m = 1;
        int out = Fantoche.out(boss);
        switch (name) {
            case "fantoche" -> {
                return 0;
            }
            case "bang" -> {
                m *= theirLife <= FINISH ? 5 : 0.9;
                m *= fleeing ? 1.3 : 1;
            }
            case "corrente", "corrente_e_ordem" -> {
                m *= distance >= 6 ? 1.3 : 1;
                m *= theirLife <= FINISH ? 0.4 : 1;
            }
            case "ordem" -> {
                m *= ai.stunned() ? 1.6 : 1;
                m *= theirLife <= FINISH ? 1.8 : 1;
            }
            case "ritual" -> {
                // Her puppets are her shield: she gives them up only with more kept to call, or her blood while she can spare it.
                boolean spare = out > 0 && Fantoche.kept(boss) > 0 || out == 0 && myLife > 0.35;
                if (!spare || myLife > LATE && !ai.chaos()) {
                    return 0;
                }
                m *= theirLife <= 0.5 ? 1.5 : 1;
            }
            case "correntes" -> {
                // With souls kept, the V brings them back: worth more than the chains, never on top of the ones out.
                if (Almas.canSummon(boss)) {
                    if (Almas.out(boss) > 0) {
                        return 0;
                    }
                    m *= 1.6;
                }
                m *= enemies >= 2 ? 1.8 : 1;
                m *= distance <= 10 ? 1.2 : 0.8;
            }
            case "forte", "barragem" -> m *= 0.5;
            default -> {
            }
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        if ("bang".equals(name)) {
            BossFalas.say(boss, "fala.battlegroundsmod.makima.bang", 200);
        }
        return null;
    }

    @Override
    public String mood() {
        return String.format("fantoches %d (+%d guardados) almas %d %s", Fantoche.out(boss), Fantoche.kept(boss), Almas.out(boss),
                kind.name().toLowerCase());
    }
}
