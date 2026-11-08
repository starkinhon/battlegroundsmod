package com.starkinhon.battlegroundsmod.boss;

import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.OniBody;

import net.minecraft.world.item.SwordItem;

// How the Kokushibo boss plays. SoulBossAI does the fighting; this reads the situation (TacticsBase) and tells it what the
// Upper Moon One would do with his Moon Breathing.
//
// Normal sword (above 600 life):
// - 1st Form, Yoru no Miya (Z): the quick draw that stuns for a second. His opener up close, then the punches, a heavy
//   hit or the 3rd Form (combos). Never into a raised Susanoo.
// - 2nd and 5th Forms (X, B): his answers when he is being beaten up (they go off even mid combo, Ability.asBeatenCounter):
//   the 2nd (he jumps and the loops close round him) on one, or on someone in the air; the 5th (the vortex) when more than
//   one is on him.
// - 3rd Form (C): two big crescents ahead; mid range, through a group, on someone running.
// - 6th Form (V): the giant cuts land 16 blocks ahead, so only from far enough; best on someone stunned, on a group, on a
//   strong foe or on someone running away.
// Awakened sword (600 and below), Stark's cooldowns (15 s 7th and 9th, 10 s 8th, 30 s 14th, 40 s 16th):
// - 7th (Z): the ring around him plus the waves of cuts fanning out on the ground: groups, mid range.
// - 8th (X): the giant 300 degree curve around him: whoever stands 4 to 20 blocks from him, groups around him.
// - 9th (C): the claws falling one after the other further and further, up to ~50 blocks: someone keeping away, running.
// - 10th (B): the three saws drilling ~180 blocks ahead: long range, someone fleeing or hiding (through walls when the
//   world breaks).
// - 14th (V): the vortex of moons all around him, his defense and attack (Stark: it also goes off mid combo): when he
//   is beaten up, when he is surrounded.
// - 16th (Shift + V): the six cuts from the sky homing on everyone within 50 blocks. His final move: only when it is
//   worth it (a group, someone stunned or running, or him low), never twice in a row.
// - Something thrown at him: out of its way, or behind whoever threw it.
// - Stronger at night: more aggressive, bigger forms.
//
// Styles he switches every 20 to 40 seconds and at the awakening, picked by the situation but never certain. Normal sword:
// samurai (up close: draw, punches, counters), lua (mid range forms) and paciente (waits for them to come, counters).
// Awakened: massacre (everything around him), cacador (long range, nobody gets away) and implacavel (all of it, the 16th
// sooner).
final class KokushiboTactics extends TacticsBase {
    private enum Style {
        SAMURAI("samurai"), LUA("lua"), PACIENTE("paciente"), MASSACRE("massacre"), CACADOR("cacador"), IMPLACAVEL("implacavel");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    private static final Set<String> NORMAL = Set.of("yoru", "segunda", "terceira", "quinta", "sexta");
    private static final Set<String> AWAKENED = Set.of("setima", "oitava", "nona", "decima", "decima_quarta", "decima_sexta");
    private static final Set<String> NORMAL_COMBOS = Set.of("noite_sem_lua", "lua_crescente", "corte_e_porrada");
    private static final Set<String> AWAKENED_COMBOS = Set.of("lua_despedacada", "caca_lunar", "porrada_desperta");
    // Least time between two 16th Forms (on top of its own cooldown).
    private static final int ULT_GAP = 900;
    private static final int GUARD_COOLDOWN = 30;
    private static final double GUARD_RANGE = 5;
    private static final int COUNTER_COOLDOWN = 10;

    private Style style;
    private int styleLeft;
    private boolean styleAwakened;
    private long lastUlt = -100_000;
    private int guardCooldown;
    private int counterCooldown;
    private boolean night;
    private boolean swordFoe;

    KokushiboTactics(KokushiboEntity boss) {
        super(boss);
    }

    private KokushiboEntity kokushibo() {
        return (KokushiboEntity) boss;
    }

    @Override
    protected void read(SoulBossAI ai) {
        long before = readAt;
        super.read(ai);
        if (readAt == before || ai.target() == null) {
            return;
        }
        int phase = OniBody.phase(boss);
        night = phase == OniBody.NIGHT || phase == OniBody.FULL_MOON;
        swordFoe = ai.target().getMainHandItem().getItem() instanceof SwordItem;
    }

    private boolean awakened() {
        return kokushibo().awakened();
    }

    // The 16th is worth it now.
    private boolean finalMoment(SoulBossAI ai) {
        if (ai.now() - lastUlt < ULT_GAP) {
            return false;
        }
        return enemies >= 2 || ai.stunned() || myLife <= 0.35 || fleeing && distance > 15 || strongFoe && myLife <= 0.5
                || style == Style.IMPLACAVEL && random.nextDouble() < 0.5;
    }

    // ---------------------------------------------------------------- style

    private void pickStyle() {
        styleAwakened = awakened();
        double n = night ? 0.3 : 0;
        if (styleAwakened) {
            double massacre = 0.8 + (enemies >= 2 ? 0.7 : 0) + (distance < 15 ? 0.3 : 0) + n;
            double cacador = 0.6 + (distance >= 20 ? 0.6 : 0) + (fleeing ? 0.5 : 0);
            double implacavel = 0.5 + (myLife < 0.35 ? 0.8 : 0) + (strongFoe ? 0.4 : 0) + n;
            style = Style.values()[3 + pick(new double[] {massacre, cacador, implacavel},
                    style == null || style.ordinal() < 3 ? -1 : style.ordinal() - 3)];
        } else {
            double samurai = 0.9 + (swordFoe ? 0.4 : 0) + (kind == Kind.LUTADOR ? 0.3 : 0) + n;
            double lua = 0.7 + (enemies >= 2 ? 0.5 : 0) + (distance >= 10 ? 0.4 : 0);
            double paciente = 0.5 + (kind == Kind.LUTADOR ? 0.5 : 0) + (myLife < 0.8 ? 0.2 : 0);
            style = Style.values()[pick(new double[] {samurai, lua, paciente}, style == null || style.ordinal() >= 3 ? -1 : style.ordinal())];
        }
        styleLeft = 400 + random.nextInt(401);
    }

    private double styleWeight(String name) {
        return switch (style) {
            case SAMURAI -> name.equals("yoru") || name.equals("segunda") || melee(name) || name.equals("noite_sem_lua")
                    || name.equals("corte_e_porrada") ? 1.4 : name.equals("sexta") ? 0.7 : 1;
            case LUA -> name.equals("terceira") ? 1.5 : name.equals("sexta") ? 1.5 : name.equals("lua_crescente") ? 1.3 : melee(name) ? 0.6 : 1;
            case PACIENTE -> name.equals("segunda") || name.equals("quinta") ? 1.6 : name.equals("yoru") ? 1.2 : melee(name) ? 0.7 : 0.9;
            case MASSACRE -> name.equals("setima") || name.equals("oitava") || name.equals("decima_quarta") ? 1.5
                    : name.equals("lua_despedacada") ? 1.4 : name.equals("decima") ? 0.8 : 1;
            case CACADOR -> name.equals("nona") || name.equals("decima") ? 1.6 : name.equals("caca_lunar") ? 1.5
                    : name.equals("oitava") ? 0.8 : melee(name) ? 0.6 : 1;
            case IMPLACAVEL -> name.equals("decima_sexta") ? 1.6 : melee(name) ? 1.2 : 1.15;
        };
    }

    private static boolean melee(String name) {
        return switch (name) {
            case "forte", "barragem", "porrada_desperta" -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- BossTactics

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        if (guardCooldown > 0) {
            guardCooldown--;
        }
        if (counterCooldown > 0) {
            counterCooldown--;
        }
        if (--styleLeft <= 0 || style == null || styleAwakened != awakened()) {
            pickStyle();
        }
        lines(ai);
        if (ai.usingMove()) {
            return false;
        }
        // Being beaten up: the counter forms go off even mid combo.
        if (counterCooldown == 0 && ai.pressure() >= 2 && distance < 7) {
            counterCooldown = COUNTER_COOLDOWN;
            String counter = awakened() ? "decima_quarta"
                    : (crowd >= 1 || enemies >= 2) && !air ? "quinta" : "segunda";
            String other = awakened() ? null : counter.equals("quinta") ? "segunda" : "quinta";
            if (ai.use(counter) || other != null && ai.use(other)) {
                ai.say("contra-ataque");
                return true;
            }
        }
        // Surrounded with the awakened sword: the vortex clears them off.
        if (awakened() && enemies >= 3 && distance < 12 && random.nextDouble() < 0.05 && ai.use("decima_quarta")) {
            ai.say("cercado");
            return true;
        }
        // Something thrown at him: not there when it arrives.
        if (guardCooldown == 0 && ai.now() % 2 == 0 && Destructible.nearestThreat(Combat.level(boss), boss, GUARD_RANGE) != null) {
            guardCooldown = GUARD_COOLDOWN;
            if (random.nextBoolean() ? ai.stepBehind() : blinkAway(ai, 7)) {
                ai.say("saiu do ataque");
                return true;
            }
        }
        return false;
    }

    // His lines as the fight goes, from the manga (a draft to go over with Stark).
    private void lines(SoulBossAI ai) {
        if (ai.target() == null || boss.staged()) {
            return;
        }
        if (myLife <= 0.25) {
            BossFalas.say(boss, "fala.battlegroundsmod.kokushibo.pouca_vida", 100_000);
        }
        if (enemies >= 2) {
            BossFalas.say(boss, "fala.battlegroundsmod.kokushibo.dois", 2400);
        }
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        if (style == null) {
            pickStyle();
        }
        // Only the forms of the sword in his hand (under soulbossfight false he has both kits in the list).
        boolean awake = awakened();
        if (awake && (NORMAL.contains(name) || NORMAL_COMBOS.contains(name))
                || !awake && (AWAKENED.contains(name) || AWAKENED_COMBOS.contains(name))) {
            return 0;
        }
        double m = 1;
        boolean stunned = ai.stunned();
        switch (name) {
            case "yoru" -> {
                m *= distance <= 6 ? 1.3 : 1;
                m *= ai.busyTarget() ? 1.5 : 1;
                m *= guarded ? 0.3 : 1;
                m *= fleeing ? 0.7 : 1;
            }
            case "terceira" -> {
                m *= crowd >= 1 ? 1.5 : 1;
                m *= fleeing ? 1.3 : 1;
                m *= distance >= 5 && distance <= 12 ? 1.2 : 1;
            }
            case "sexta" -> {
                m *= stunned ? 2 : 1;
                m *= crowd >= 1 ? 1.5 : 1;
                m *= strongFoe ? 1.2 : 1;
                m *= fleeing ? 1.4 : 1;
                m *= air ? 0.6 : 1;
                m *= ai.pressure() > 0 ? 0.5 : 1;
            }
            case "segunda", "quinta" -> {
                m *= guarded ? 0.4 : 1;
            }
            case "setima" -> {
                m *= crowd >= 1 || enemies >= 2 ? 1.6 : 1;
                m *= distance >= 6 && distance <= 40 ? 1.2 : 1;
                m *= stunned ? 1.4 : 1;
            }
            case "oitava" -> {
                m *= distance >= 4 && distance <= 18 ? 1.4 : 0.6;
                m *= enemies >= 2 ? 1.5 : 1;
                m *= ai.busyTarget() ? 1.3 : 1;
            }
            case "nona" -> {
                m *= distance >= 12 ? 1.4 : 1;
                m *= fleeing ? 1.6 : 1;
                m *= crowd >= 1 ? 1.2 : 1;
                m *= air ? 0.8 : 1;
            }
            case "decima" -> {
                m *= distance >= 25 ? 1.6 : distance < 12 ? 0.5 : 1;
                m *= fleeing ? 1.8 : 1;
                m *= !sees ? 1.5 : 1;
            }
            case "decima_quarta" -> {
                m *= enemies >= 2 ? 1.6 : 1;
                m *= ai.pressure() > 0 ? 1.5 : 1;
            }
            case "decima_sexta" -> {
                if (!finalMoment(ai)) {
                    return 0;
                }
                m = 3;
                m *= enemies >= 2 ? 1.6 : 1;
                m *= stunned ? 1.5 : 1;
                m *= theirLife < 0.1 && !strongFoe ? 0.4 : 1;
            }
            case "lua_crescente", "noite_sem_lua", "corte_e_porrada" -> {
                m *= guarded ? 0.2 : 1;
                m *= swordFoe ? 1.2 : 1;
            }
            case "lua_despedacada" -> {
                m *= enemies >= 2 ? 1.4 : 1;
            }
            case "caca_lunar" -> {
                m *= fleeing ? 1.8 : 1;
                m *= distance >= 15 ? 1.3 : 0.6;
            }
            case "forte", "barragem", "porrada_desperta" -> {
                m *= guarded ? 0.2 : 1;
                m *= kind == Kind.LUTADOR ? 0.9 : 1;
            }
            default -> {
            }
        }
        // At night he is hungrier for blood.
        if (night && !melee(name)) {
            m *= 1.15;
        }
        m *= styleWeight(name);
        m *= jitter();
        return score * m;
    }

    @Override
    public com.starkinhon.battlegroundsmod.power.BossOrder.Order order(SoulBossAI ai, String name) {
        if ("decima_sexta".equals(name)) {
            lastUlt = ai.now();
        }
        return null;
    }

    @Override
    public boolean throughWalls(SoulBossAI ai, String name) {
        read(ai);
        // The 16th homes on them wherever they are; the saws and the claws cut their way to them with the world breakable.
        return "decima_sexta".equals(name) && distance <= 50
                || breaks && ("decima".equals(name) && distance <= 150 || "nona".equals(name) && distance <= 50);
    }

    @Override
    public String mood() {
        if (style == null) {
            return "";
        }
        return style.label + (awakened() ? " (despertada)" : "") + " " + kind.name().toLowerCase() + (night ? " noite" : "")
                + (readAt >= 0 ? String.format(" vida %.0f%%", myLife * 100) : "");
    }
}
