package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Templos Antigos' curses (tools/mobs/jjmobs/boneco.js, kitsune.js, sino.js):
// - Boneco (neutral, a cursed straw corpse): the combo (jabs at 8 and 14, the cross at 22) and the alarm clock (only
//   on whoever stood still 3 s near it: the uppercut at 68 throws them up, dizzy);
// - Kitsune: the wisps (three blue flames born over its head, each falls at 42, 51, 60 on where you stood when it made
//   them) and the paper illusion (bursts in paper at 15, two fakes run out while it walks round to your side and bites
//   at 59; a fake is paper at the first hit);
// - Sino: the toll (28: the rings stun whoever is in front and not behind a wall) and the trap (lands over you at 21,
//   dark inside until 87, tolls at 40 and 64; three hits on it from outside free you).
final class JjTemplos {
    private JjTemplos() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "boneco/despertador" -> m.stillTicks >= 60;
                case "kitsune/fogo", "sino/badalada" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void idle(JjMob m) {
            if (!m.kind().equals("boneco")) {
                return;
            }
            // how long the one it watches has stood still near it
            LivingEntity t = m.getTarget();
            if (t == null || m.distanceTo(t) > 6) {
                m.stillTicks = 0;
                return;
            }
            if (t.position().distanceToSqr(m.stillAt) < 0.02) {
                m.stillTicks++;
            } else {
                m.stillTicks = 0;
                m.stillAt = t.position();
            }
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "boneco/combo" -> sound(m, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1f, 1.4f);
                case "boneco/despertador" -> sound(m, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 1.6f);
                case "kitsune/fogo" -> sound(m, SoundEvents.FOX_AGGRO, 1.2f, 0.8f);
                case "kitsune/ilusao" -> sound(m, SoundEvents.FOX_AGGRO, 1.2f, 1.2f);
                case "sino/badalada", "sino/prender" -> sound(m, SoundEvents.BELL_RESONATE, 1f, 0.6f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "boneco/combo" -> {
                    if ((f == 8 || f == 14) && front(m, t, 1.5, 0.3)) {
                        hit(m, t, 20, 0.4f, 10);
                        sound(m, SoundEvents.WOOL_HIT, 1.2f, 1.2f);
                    }
                    if (f == 22 && front(m, t, 1.6, 0.3)) {
                        hit(m, t, 35, 0.6f, 10);
                        Combat.push(t, m.forward(), 0.7, 0.25);
                        sound(m, SoundEvents.WOOL_HIT, 1.4f, 0.8f);
                    }
                }
                case "boneco/despertador" -> {
                    if (f == 68 && front(m, t, 1.9, 0.2)) {
                        hit(m, t, 70, 0.8f, 10);
                        Combat.push(t, m.forward(), 0.2, 1.0);
                        dizzy(m, t, 40);
                        sound(m, SoundEvents.NOTE_BLOCK_BELL.value(), 1.6f, 0.8f);
                        m.stillTicks = 0;
                    }
                }
                case "kitsune/fogo" -> wisps(m, f, t);
                case "kitsune/ilusao" -> {
                    if (f == 15) {
                        SubNet.send(level, JmNet.PAPER, 0, m.position().add(0, 0.4, 0), Vec3.ZERO, 16, 0, 0);
                        sound(m, SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.8f);
                        if (summonedAlive(m) < 2) {
                            for (int s = -1; s <= 1; s += 2) {
                                JjMob fake = summon(m, "kitsune", body(m, 0, s * 1.2, 0), 85, t);
                                if (fake != null) {
                                    fake.setVariant(m.variant());
                                    fake.setHealth(1);
                                }
                            }
                        }
                    }
                    if (f == 58) {
                        face(m, t);
                    }
                    if (f == 59 && front(m, t, 2.0, 0.0)) {
                        hit(m, t, 50, 0.6f, 4);
                        bleed(m, t, 40);
                        sound(m, SoundEvents.FOX_BITE, 1.4f, 0.9f);
                    }
                }
                case "sino/badalada" -> {
                    if (f == 28) {
                        Vec3 from = body(m, 0.5, 0, 1.2);
                        NmNet.send(level, NmNet.SOUND, 0, from, m.forward().scale(6), 0, 0, 0);
                        sound(m, SoundEvents.BELL_BLOCK, 3f, 0.5f);
                        for (LivingEntity o : around(m, m.position(), 7)) {
                            Vec3 to = o.position().subtract(m.position()).multiply(1, 0, 1);
                            if (m.gap(o) > 6 || to.lengthSqr() > 0.5 && to.normalize().dot(m.forward()) < 0.3 || !m.hasLineOfSight(o)) {
                                continue;
                            }
                            magic(m, o, 30);
                            dizzy(m, o, 60);
                            Combat.pushFrom(o, m.position(), 0.5, 0.15);
                        }
                    }
                }
                case "sino/prender" -> {
                    if (f == 21) {
                        SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 1f, 0, 0);
                        sound(m, SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
                        for (LivingEntity o : around(m, m.position().add(0, 0.8, 0), 1.6)) {
                            if (hold(m, o, 66)) {
                                screen(o, 0, 66);
                                hit(m, o, 20, 0.5f, 10);
                                break;
                            }
                        }
                    }
                    if ((f == 40 || f == 64)) {
                        sound(m, SoundEvents.BELL_BLOCK, 3f, 0.4f);
                        LivingEntity v = victim(m);
                        if (v != null && holding(m, v)) {
                            magic(m, v, 40);
                            impact(m, Combat.center(v), 0.6f, JmNet.FLESH);
                            if (f == 64) {
                                dizzy(m, v, 60);
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            if (m.kind().equals("sino") && mode.equals("prender") && m.struck >= 3) {
                LivingEntity v = victim(m);
                if (v != null) {
                    screen(v, 0, 0);
                }
                return true;
            }
            return false;
        }
    };

    // Three wisps born over its head (16), circling until each falls on where the target stood: 42, 51, 60.
    private static void wisps(JjMob m, int f, LivingEntity t) {
        if (f == 16) {
            Vec3 centre = body(m, 0, 0, 1.3);
            Vec3 aim = alive(t) ? t.position().add(0, t.getBbHeight() * 0.5, 0) : ahead(m, 6).add(0, 0.9, 0);
            m.mark = aim;
            for (int i = 0; i < 3; i++) {
                JmNet.send(lvl(m), JmNet.WISP, m.getId(), centre, aim.subtract(centre), 20 + 9 * i, i, 6);
            }
            sound(m, SoundEvents.SOUL_ESCAPE.value(), 1.2f, 1.2f);
        }
        if (f == 42 || f == 51 || f == 60) {
            sound(m, m.mark, SoundEvents.FIRECHARGE_USE, 1f, 1.4f);
            impact(m, m.mark, 0.8f, JmNet.FLESH);
            for (LivingEntity o : around(m, m.mark, 1.3)) {
                magic(m, o, 30);
                o.igniteForSeconds(2);
            }
        }
    }
}
