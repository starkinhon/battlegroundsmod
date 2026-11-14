package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Floresta de Hanami's curses (tools/mobs/jjmobs/broto.js, javali.js, guardiao.js):
// - Broto: the seeds (three spat at 20, 25, 30, landing round your feet; at 38 a vine grows from each and holds the foot
//   of whoever stands on one: slow) and the burrow (digs in, the ground bulges forward 20-40 and it bursts up under the
//   spot 2.8 blocks ahead, throwing whoever is there up);
// - Javali: the charge (scrapes, runs 22-50; the tusks throw you over its back; if it hits a tree or a wall it is
//   stunned 2 s) and the roots (stamps at 27, a row of roots breaks out to where you stood and throws you up at 45);
// - Guardião: the flowers (kneels, a field of flowers opens round it at 20: whoever is in it is slowed, weakened and sees
//   blurred until 120) and the stakes (six root stakes close on where you stood at 31 and hold you until 73).
final class JjHanami {
    private JjHanami() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "broto/sementes", "javali/raizes", "guardiao/estacas" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "broto/sementes" -> sound(m, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 1.2f, 1.2f);
                case "broto/enraizar" -> sound(m, SoundEvents.ROOTED_DIRT_BREAK, 1.4f, 0.8f);
                case "javali/investida" -> sound(m, SoundEvents.HOGLIN_ANGRY, 1.4f, 1f);
                case "javali/raizes" -> sound(m, SoundEvents.HOGLIN_AMBIENT, 1.4f, 0.8f);
                case "guardiao/flores" -> sound(m, SoundEvents.AZALEA_LEAVES_BREAK, 1.4f, 0.6f);
                case "guardiao/estacas" -> sound(m, SoundEvents.WOOD_HIT, 1.4f, 0.5f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "broto/sementes" -> {
                    if (f == 0) {
                        m.marks = new Vec3[3];
                    }
                    int i = (f - 20) / 5;
                    if (f >= 20 && f <= 30 && (f - 20) % 5 == 0 && m.marks.length == 3) {
                        Vec3 base = alive(t) ? t.position() : ahead(m, 5);
                        double a = i * 2.1 + m.getId();
                        Vec3 aim = ground(m, base.add(Math.cos(a) * 0.5 * (i == 0 ? 0 : 1), 0, Math.sin(a) * 0.5 * (i == 0 ? 0 : 1)));
                        m.marks[i] = aim;
                        Vec3 mouth = body(m, 0.3, 0, 0.8);
                        JmNet.send(level, JmNet.THROW, 0, mouth, aim.subtract(mouth), 10, 0, 1.2f);
                        sound(m, SoundEvents.LLAMA_SPIT, 1f, 1.4f);
                    }
                    if (f == 38 && m.marks.length == 3) {
                        for (Vec3 p : m.marks) {
                            if (p == null) {
                                continue;
                            }
                            for (LivingEntity o : around(m, p, 1.0)) {
                                if (o.onGround() && once(m, o)) {
                                    JmNet.send(level, JmNet.VINE, 0, o.position(), Vec3.ZERO, 42, 0, 0);
                                    slow(m, o, 42, 2);
                                    hit(m, o, 15, 0.3f, 9);
                                }
                            }
                        }
                    }
                }
                case "broto/enraizar" -> {
                    Vec3 from = ground(m, body(m, 0.3, 0, 0));
                    Vec3 spot = ground(m, ahead(m, 2.8));
                    if (f == 20) {
                        JmNet.send(level, JmNet.MOUND, 0, from, spot.subtract(from), 20, 0, 0);
                    }
                    if (f > 20 && f <= 40) {
                        // the ground bulges and breaks over it as it digs forward
                        trench(m, from, spot, (f - 21) / 20.0, (f - 20) / 20.0, 0.8, 1, 0);
                    }
                    if (f == 40) {
                        SubNet.send(level, SubNet.DUST, 0, spot, Vec3.ZERO, 1f, 0, 0);
                        carve(m, spot, 1.5, 1, 2, 3f, 4);
                        sound(m, spot, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.6f);
                        for (LivingEntity o : around(m, spot.add(0, 0.5, 0), 1.4)) {
                            hit(m, o, 50, 0.7f, 9);
                            Combat.push(o, right(m), 0.3, 1.1);
                        }
                    }
                }
                case "javali/investida" -> {
                    if (f >= 30 && f <= 50) {
                        // tramples and tusks through what is in its way
                        plow(m, 0.4, 0.9, 1.3);
                        for (LivingEntity o : around(m, body(m, 0.8, 0, 0.6), 1.3)) {
                            if (once(m, o)) {
                                hit(m, o, 70, 0.8f, 10);
                                // over its back
                                Combat.push(o, m.forward().scale(-1), 0.6, 0.9);
                                sound(m, SoundEvents.HOGLIN_ATTACK, 1.4f, 1f);
                            }
                        }
                        Vec3 nose = ahead(m, m.getBbWidth() * 0.5 + 0.4).add(0, 0.5, 0);
                        net.minecraft.core.BlockPos np = net.minecraft.core.BlockPos.containing(nose);
                        boolean wall = m.horizontalCollision || !level.getBlockState(np).getCollisionShape(level, np).isEmpty();
                        if (wall && f > 32) {
                            // ran into a tree or a wall: breaks into it and is stunned
                            smash(m, nose.add(m.forward().scale(0.4)), 1.4);
                            Stun.apply(m, 40, true);
                            SubNet.send(level, SubNet.DUST, 0, ahead(m, 0.8), Vec3.ZERO, 0.8f, 0, 0);
                            sound(m, SoundEvents.WOOD_BREAK, 1.6f, 0.6f);
                            m.cutBlow();
                        }
                    }
                }
                case "javali/raizes" -> {
                    if (f == 26) {
                        m.mark = ground(m, alive(t) ? t.position() : ahead(m, 6));
                    }
                    if (f == 27) {
                        NmNet.send(level, NmNet.SHOCK, 0, m.position(), Vec3.ZERO, 1.6f, 0, 0);
                        Vec3 from = ground(m, body(m, 0.9, 0, 0));
                        NmNet.send(level, NmNet.STAKES, 0, from, m.mark.subtract(from), 8, 0, 0);
                        sound(m, SoundEvents.ROOTS_BREAK, 1.6f, 0.6f);
                    }
                    if (f > 27 && f <= 45 && m.mark != null) {
                        // the row of roots tears the ground open as it runs to the spot
                        trench(m, ground(m, body(m, 0.9, 0, 0)), m.mark, (f - 28) / 18.0, (f - 27) / 18.0, 0.9, 1, 1);
                    }
                    if (f == 45) {
                        carve(m, m.mark, 1.4, 1, 2, 3f, 4);
                        for (LivingEntity o : around(m, m.mark.add(0, 0.5, 0), 1.3)) {
                            hit(m, o, 55, 0.7f, 9);
                            Combat.push(o, m.forward(), 0.2, 0.9);
                        }
                    }
                }
                case "guardiao/flores" -> {
                    if (f == 20) {
                        JmNet.send(level, JmNet.FLOWERS, 0, m.position(), Vec3.ZERO, 6, 100, Math.floorMod(m.variant(), 3));
                    }
                    if (f >= 30 && f <= 120 && f % 10 == 0) {
                        for (LivingEntity o : around(m, m.position(), 6)) {
                            slow(m, o, 25, 0);
                            o.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, false, true), m);
                            if (f % 20 == 0) {
                                screen(o, 2, 16);
                                magic(m, o, 6);
                            }
                        }
                    }
                }
                case "guardiao/estacas" -> {
                    if (f == 28) {
                        m.mark = ground(m, alive(t) ? t.position() : ahead(m, 5));
                    }
                    if (f == 31) {
                        NmNet.send(level, NmNet.CAGE, 0, m.mark, Vec3.ZERO, 42, 0, 0);
                        // the stakes burst up through the ground round the spot
                        carve(m, m.mark, 1.6, 0, 2, 3f, 4);
                        sound(m, m.mark, SoundEvents.WOOD_PLACE, 1.8f, 0.5f);
                        for (LivingEntity o : around(m, m.mark.add(0, 0.8, 0), 1.3)) {
                            hit(m, o, 40, 0.6f, 9);
                            if (m.heldId < 0) {
                                hold(m, o, 42);
                            }
                        }
                    }
                    if (f == 73) {
                        NmNet.send(level, NmNet.CAGE, 0, m.mark, Vec3.ZERO, 0, 0, 1);
                        letGo(m);
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            if (m.kind().equals("guardiao") && mode.equals("estacas") && m.struck >= 3) {
                NmNet.send(lvl(m), NmNet.CAGE, 0, m.mark, Vec3.ZERO, 0, 0, 1);
                return true;
            }
            return false;
        }
    };
}
