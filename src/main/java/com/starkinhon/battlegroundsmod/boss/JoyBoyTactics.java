package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.powers.luffy.Luffy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

// How the Joy Boy boss fights (Stark, 2026-11-12). Three moments, read from his life (so it works the same as a plain mob,
// without /gamerule soulbossfight):
// - Gear 2 (above 60%): fast and cheeky, Jet Pistol / Jet Gatling / Jet Bazooka, a tap now and then to mix it up.
// - Gear 4 Boundman (60% to 25%): mostly the G4 versions (Kong Gun, Kong Organ, Leo Bazooka, King Kong Gun), the G3 once
//   in a while (it shrinks him after: an opening on purpose).
// - The Tambores da Libertação (25%): the total Gear 5 for a minute, every key in its G5 version, the Giant once if there
//   is sky over him. When it ends he is starving, slow and weak, without Gears: the window to kill him.
// The Gear is a BossOrder for Luffy.press (the charge picks it). While he holds a key nothing else starts: the climb of
// the Gear is one move, not a stance he attacks from (Stark: no skill on top of another one).
final class JoyBoyTactics extends TacticsBase {
    private static final double G4_LIFE = 0.6, G5_LIFE = 0.25;
    // The Giant only after this long in the G5, and only with this much open sky over him.
    private static final int GIANT_AFTER = 300;
    private static final int GIANT_SKY = 22;

    private boolean g5Used;
    private boolean giantUsed;
    private boolean g4Said;
    private boolean fomeSaid;
    private long g5Start = -1;
    private long lastFight = -100_000;

    JoyBoyTactics(JoyBoyEntity boss) {
        super(boss);
    }

    private int stage() {
        return Luffy.g5(boss) ? 2 : myLife > G4_LIFE ? 0 : 1;
    }

    @Override
    public boolean act(SoulBossAI ai) {
        read(ai);
        coolDown();
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        if (ai.now() - lastFight > 300 && boss.getHealth() >= boss.getMaxHealth() * 0.99f) {
            // A new fight, healed up: the drums can beat again.
            g5Used = false;
            giantUsed = false;
            g4Said = false;
        }
        lastFight = ai.now();
        if (Luffy.starving(boss)) {
            if (!fomeSaid) {
                fomeSaid = BossFalas.sayNow(boss, "fala.battlegroundsmod.luffy.fome");
            }
        } else {
            fomeSaid = false;
        }
        if (Luffy.holding(boss)) {
            // Climbing a Gear: he stays on it (and looks at them) until he lets go.
            ai.lookAt(Combat.center(target), 20);
            ai.say("segurando o Gear");
            return true;
        }
        if (ai.usingMove() || Freeze.isFrozen(boss)) {
            return false;
        }
        // The Tambores: at a quarter of his life, once a fight, the moment he is free.
        if (!g5Used && myLife <= G5_LIFE && !Luffy.g5(boss) && !Luffy.starving(boss) && ai.has("gear5") && ai.use("gear5")) {
            ai.say("tambores da libertacao");
            return true;
        }
        if (Luffy.g5(boss)) {
            if (g5Start < 0) {
                g5Start = ai.now();
            }
            if (!giantUsed && !Luffy.giant(boss) && ai.now() - g5Start > GIANT_AFTER && breaks && openSky() && random.nextDouble() < 0.02
                    && ai.has("gigante") && ai.use("gigante")) {
                ai.say("gomu gomu no giant");
                return true;
            }
        } else {
            g5Start = -1;
        }
        return false;
    }

    // Nothing over him for GIANT_SKY blocks around: 10x his size fits.
    private boolean openSky() {
        BlockPos at = boss.blockPosition();
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dz = -4; dz <= 4; dz += 4) {
                int top = boss.level().getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() + dx, at.getZ() + dz);
                if (top > at.getY() + 1) {
                    return false;
                }
            }
        }
        return boss.level().getMaxBuildHeight() - at.getY() > GIANT_SKY;
    }

    @Override
    public double weigh(SoulBossAI ai, String name, boolean combo, double score) {
        read(ai);
        if (Luffy.holding(boss) || "gear5".equals(name) || "gigante".equals(name)) {
            // (The drums and the Giant only come from act, at their moment.)
            return 0;
        }
        double m = 1;
        boolean stunned = ai.stunned();
        switch (name) {
            case "pistol" -> {
                m *= distance >= 6 ? 1.4 : 1;
                m *= fleeing ? 1.6 : 1;
            }
            case "gatling" -> {
                m *= distance <= 7 ? 1.4 : 0.7;
                m *= stunned ? 1.5 : 1;
                m *= crowd >= 1 ? 1.3 : 1;
            }
            case "bazooka" -> {
                m *= distance <= 6 ? 1.5 : 1;
                m *= ai.pressure() > 1 ? 1.4 : 1;
            }
            case "especial" -> {
                m *= stunned ? 1.8 : 1;
                m *= air ? 1.3 : 1;
                m *= stage() == 2 ? 1.3 : 1;
            }
            default -> {
            }
        }
        if (Luffy.starving(boss)) {
            // Spent after the Gear 5: he barely manages the plain punches.
            m *= combo || "forte".equals(name) || "barragem".equals(name) ? 0.8 : 0.5;
        } else if (stage() == 0) {
            // Gear 2: the quick stuff, more combos.
            m *= combo ? 1.2 : 1;
        } else {
            m *= "forte".equals(name) || "barragem".equals(name) ? 0.8 : 1.15;
        }
        m *= jitter();
        return score * m;
    }

    @Override
    public BossOrder.Order order(SoulBossAI ai, String name) {
        read(ai);
        return switch (name) {
            case "pistol", "gatling", "bazooka", "especial" -> {
                int gear = gear();
                if (gear == 3 && !g4Said) {
                    g4Said = BossFalas.say(boss, "fala.battlegroundsmod.luffy.gear4", 0);
                }
                yield gear == 0 ? BossOrder.Order.tap() : BossOrder.Order.charged(gear / 3.0);
            }
            case "gear5" -> {
                g5Used = true;
                BossFalas.sayNow(boss, "fala.battlegroundsmod.luffy.gear5");
                yield null;
            }
            case "gigante" -> {
                giantUsed = true;
                yield null;
            }
            default -> null;
        };
    }

    // The Gear this blow is thrown in (0 tap, 1 G2, 2 G3, 3 G4).
    private int gear() {
        double r = random.nextDouble();
        if (stage() == 0) {
            return r < 0.7 ? 1 : 0;
        }
        return r < 0.6 ? 3 : r < 0.75 ? 2 : 1;
    }

    @Override
    public String mood() {
        String s = Luffy.g5(boss) ? (Luffy.giant(boss) ? "gear 5 gigante" : "gear 5") : Luffy.starving(boss) ? "com fome" : stage() == 0 ? "gear 2" : "gear 4";
        return s + " " + kind.name().toLowerCase();
    }
}
