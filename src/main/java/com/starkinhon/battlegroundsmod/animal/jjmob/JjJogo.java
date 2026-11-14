package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Terras do Jogo's curses (tools/mobs/jjmobs/inseto.js, vulcaozinho.js, golem.js), made of fire and stone (fire
// does nothing to them):
// - Inseto: the sting (23, sets you on fire) and the burst (swells white until 42, dives and blows up at 43, leaving a
//   pool of magma; it dies in it);
// - Vulcãozinho: the eruption (blows at 24: five lava bombs, one on you and four round you, each leaving a pool of
//   magma where it falls) and the ash (a hot cloud rolls out at 25 and covers whoever is in front until 72);
// - Golem: the fist (slams at 34, five cracks of magma run out in a fan, the middle one under you, and burst at 43) and
//   the columns (stamps at 27, six basalt columns rise one after the other to where you stood, the last under you
//   throwing you up at 42).
final class JjJogo {
    private JjJogo() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                // the burst is its end: only when it is hurt
                case "inseto/estouro" -> m.getHealth() < m.getMaxHealth() * 0.6f;
                case "vulcaozinho/erupcao", "golem/colunas" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "inseto/picada" -> sound(m, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.2f, 0.8f);
                case "inseto/estouro" -> sound(m, SoundEvents.CREEPER_PRIMED, 1.4f, 0.8f);
                case "vulcaozinho/erupcao", "vulcaozinho/cinza" -> sound(m, SoundEvents.LAVA_AMBIENT, 1.6f, 0.6f);
                case "golem/punho", "golem/colunas" -> sound(m, SoundEvents.IRON_GOLEM_ATTACK, 1.6f, 0.5f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "inseto/picada" -> {
                    if (f == 23 && front(m, t, 2.0, 0.2)) {
                        hit(m, t, 35, 0.5f, 11);
                        t.igniteForSeconds(3);
                        sound(m, SoundEvents.FIRECHARGE_USE, 1f, 1.4f);
                    }
                }
                case "inseto/estouro" -> {
                    if (f == 43) {
                        Vec3 at = m.position().add(0, m.getBbHeight() * 0.5, 0);
                        double gy = DbCommon.groundY(level, at);
                        SubNet.send(level, SubNet.FIRE_BOOM, 0, at, new Vec3(gy, 0, 0), 2.6f, 0, SubCommon.RANDOM.nextInt(1000));
                        sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.1f);
                        for (LivingEntity o : around(m, at, 3)) {
                            double k = 1 - 0.5 * Math.min(1, o.position().distanceTo(at) / 3);
                            hit(m, o, (float) (90 * k), 0.8f, 11);
                            Combat.pushFrom(o, at, 1.0 * k, 0.5 * k);
                            o.igniteForSeconds(4);
                        }
                        magma(m, at, 2, 100);
                        // the blast leaves a crater
                        carve(m, ground(m, at), 2.0, 1, 2, 3f, 5);
                        // gone in its own blast
                        m.discard();
                    }
                }
                case "vulcaozinho/erupcao" -> eruption(m, f, t);
                case "vulcaozinho/cinza" -> {
                    Vec3 mouth = body(m, 0.5, 0, 1.0);
                    if (f == 25) {
                        JmNet.send(level, JmNet.ASH_CLOUD, 0, mouth, m.forward().scale(5), 2.4f, 60, 0);
                        sound(m, SoundEvents.FIRECHARGE_USE, 1.6f, 0.5f);
                    }
                    if (f >= 32 && f <= 72 && f % 5 == 0) {
                        for (LivingEntity o : around(m, m.position(), 7)) {
                            Vec3 to = o.position().subtract(m.position()).multiply(1, 0, 1);
                            if (m.gap(o) > 6 || to.lengthSqr() > 0.5 && to.normalize().dot(m.forward()) < 0.4) {
                                continue;
                            }
                            if (once(m, o)) {
                                screen(o, 5, 40);
                            }
                            slow(m, o, 20, 1);
                            if (f % 10 == 0) {
                                magic(m, o, 10);
                            }
                        }
                    }
                }
                case "golem/punho" -> fist(m, f, t);
                case "golem/colunas" -> columns(m, f, t);
                default -> {
                }
            }
        }
    };

    // the frames the five bombs leave the crater (1.2 s + 0.07 s each)
    private static final int[] BOMBS = {24, 25, 27, 28, 30};

    // Five bombs out of the crater (24..30), each in its arc: the first on the target, the rest round it; each leaves a pool.
    private static void eruption(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        Vec3 crater = body(m, 0, 0, m.getBbHeight());
        if (f == 24) {
            impact(m, crater, 1.4f, JmNet.HARD);
            sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
        }
        int i = -1;
        for (int k = 0; k < BOMBS.length; k++) {
            if (BOMBS[k] == f) {
                i = k;
            }
        }
        if (i < 0) {
            return;
        }
        Vec3 base = alive(t) ? t.position() : ahead(m, 6);
        double a = i * 1.57 + m.getId() * 0.7;
        double r = i == 0 ? 0 : 2 + (i % 2) * 0.8;
        Vec3 aim = ground(m, base.add(Math.cos(a) * r, 0, Math.sin(a) * r));
        int dur = 17 + i;
        JmNet.send(level, JmNet.THROW, 0, crater, aim.subtract(crater), dur, 3, 3);
        DbCommon.later(dur, () -> {
            if (!m.isAlive() && !m.isDeadOrDying()) {
                return;
            }
            sound(m, aim, SoundEvents.LAVA_POP, 1.4f, 0.6f);
            // the bomb digs in where it lands, the magma pools in the pit
            carve(m, aim, 1.1, 1, 1, 3f, 2);
            magma(m, aim, 1.2f, 80);
            for (LivingEntity o : around(m, aim.add(0, 0.5, 0), 1.3)) {
                hit(m, o, 40, 0.6f, 11);
                o.igniteForSeconds(3);
            }
        });
    }

    // The fist (34): five cracks of magma in a fan, the middle one to the target, all bursting at 43.
    private static void fist(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        Vec3 imp = ground(m, body(m, 1.8, 0, 0));
        if (f == 34) {
            double len = alive(t) ? Mth.clamp(t.position().distanceTo(imp), 3, 9) : 6;
            m.marks = new Vec3[5];
            for (int i = 0; i < 5; i++) {
                double ang = Math.toRadians((i - 2) * 20);
                Vec3 fwd = m.forward(), rt = right(m);
                Vec3 dir = fwd.scale(Math.cos(ang)).add(rt.scale(Math.sin(ang))).scale(i == 2 ? len : len * 0.8);
                m.marks[i] = ground(m, imp.add(dir));
                JmNet.send(level, JmNet.MAGMA_CRACK, 0, imp, m.marks[i].subtract(imp), 9, i == 2 ? 1 : 0, 0);
            }
            SubNet.send(level, SubNet.CRACKS, 0, imp, Vec3.ZERO, 2.5f, 8, 1);
            smash(m, imp, 2.2);
            NmNet.send(level, NmNet.SHOCK, 0, imp, Vec3.ZERO, 2.4f, 0, 0);
            sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
            wave(m, imp, 2, 50, 0.6, 0.3);
        }
        if (f > 34 && f <= 43 && m.marks.length == 5) {
            // the five cracks split the ground open as the magma runs out along them
            for (int i = 0; i < 5; i++) {
                trench(m, imp, m.marks[i], (f - 35) / 9.0, (f - 34) / 9.0, i == 2 ? 0.8 : 0.6, 1, 0);
            }
        }
        if (f == 43 && m.marks.length == 5) {
            sound(m, m.marks[2], SoundEvents.LAVA_POP, 2f, 0.5f);
            for (int i = 0; i < 5; i++) {
                carve(m, m.marks[i], i == 2 ? 1.8 : 1.2, 1, 2, 3f, i == 2 ? 4 : 1);
            }
            for (int i = 0; i < 5; i++) {
                double r = i == 2 ? 2 : 1.3;
                for (LivingEntity o : around(m, m.marks[i].add(0, 0.5, 0), r)) {
                    if (once(m, o)) {
                        hit(m, o, i == 2 ? 80 : 60, 0.8f, 11);
                        Combat.push(o, m.forward(), 0.2, i == 2 ? 1.0 : 0.6);
                        o.igniteForSeconds(3);
                    }
                }
            }
            for (LivingEntity o : line(m, imp, m.marks[2], 0.8)) {
                if (once(m, o)) {
                    hit(m, o, 40, 0.6f, 11);
                    o.igniteForSeconds(2);
                }
            }
        }
    }

    // Six columns, each taller, rising in a row to where the target stood (28..42); the last under them throws them up.
    private static void columns(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        if (f == 26) {
            m.mark = ground(m, alive(t) ? t.position() : ahead(m, 7));
        }
        if (f == 27) {
            NmNet.send(level, NmNet.SHOCK, 0, m.position(), Vec3.ZERO, 1.8f, 0, 0);
            sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.5f);
        }
        if (f < 28) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int at = 28 + (int) Math.round(i * 2.8);
            if (f != at) {
                continue;
            }
            Vec3 from = ground(m, body(m, 1.5, 0, 0));
            Vec3 p = ground(m, from.lerp(m.mark, (i + 1) / 6.0));
            JmNet.send(level, JmNet.BASALT, 0, p, Vec3.ZERO, 1.0f + 0.4f * i, i == 5 ? 0.7f : 0.45f, 88 - f);
            // each column breaks up through the ground and what stands over it, and the ground between splits
            Vec3 prev = i == 0 ? from : ground(m, from.lerp(m.mark, i / 6.0));
            trench(m, prev, p, 0, 1, 0.6, 1, 0);
            carve(m, p, i == 5 ? 1.3 : 0.9, 0, 2 + i / 2, 3f, i == 5 ? 3 : 1);
            SubNet.send(level, SubNet.DUST, 0, p, Vec3.ZERO, 0.7f, 0, 0);
            sound(m, p, SoundEvents.BASALT_BREAK, 1.6f, 0.6f);
            for (LivingEntity o : around(m, p.add(0, 0.5, 0), i == 5 ? 1.3 : 0.9)) {
                if (!once(m, o)) {
                    continue;
                }
                if (i == 5) {
                    hit(m, o, 60, 0.8f, 11);
                    Combat.push(o, m.forward(), 0.1, 1.3);
                } else {
                    hit(m, o, 30, 0.5f, 11);
                    Combat.pushFrom(o, p, 0.6, 0.3);
                }
            }
        }
    }
}
