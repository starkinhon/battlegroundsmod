package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Shibuya's curses (the preview's timings, tools/mobs/jjmobs/mosca.js, cabecao.js, transfigurado.js, metro.js):
// - Mosca: the bite (frame 21) and the fly on the face (lands at 18, blinds and nibbles until 60; a hit shoos it);
// - Cabeção: the grab (the arm at 17, to the mouth by 35, the bite at 40, the toss at 64; two hits free you) and the
//   pounce (lands at 30: a wave, and whoever is right under it is pinned for a moment);
// - Transfigurado: the charge (runs 16-34, throws whoever it meets) and the hug (17-58, bites at 21, 35, 49; three hits
//   free you);
// - Metrô: the run over (19-33, throws whoever is in the way back over it) and the doors (the tongue from the nearest
//   door at 23, dragged in by 38, chewed inside until 72, spat out of the front; two hits on it during the pull free you).
final class JjShibuya {
    private JjShibuya() {
    }

    static final Moves MOVES = new Moves() {
        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "mosca/mordida", "mosca/cara" -> sound(m, SoundEvents.BEE_LOOP_AGGRESSIVE, 1f, 1.6f);
                case "cabecao/agarrao" -> sound(m, SoundEvents.ZOMBIE_AMBIENT, 1.4f, 0.5f);
                case "cabecao/bote" -> sound(m, SoundEvents.RAVAGER_ROAR, 1f, 1.4f);
                case "transfigurado/investida", "transfigurado/abraco" -> sound(m, SoundEvents.ZOMBIE_VILLAGER_AMBIENT, 1.2f, 0.6f);
                case "metro/atropelar" -> sound(m, SoundEvents.MINECART_RIDING, 2f, 0.6f);
                case "metro/portas" -> sound(m, SoundEvents.IRON_DOOR_OPEN, 2f, 0.6f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "mosca/mordida" -> {
                    if (f == 21 && front(m, t, 1.6, 0.2)) {
                        hit(m, t, 30, 0.5f, 4);
                        bleed(m, t, 60);
                        SubNet.send(lvl(m), SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.4f, 0, 0);
                        sound(m, SoundEvents.FOX_BITE, 1f, 1.6f);
                    }
                }
                case "mosca/cara" -> flyOnFace(m, f, t);
                case "cabecao/agarrao" -> grab(m, f, t);
                case "cabecao/bote" -> {
                    if (f == 30) {
                        Vec3 at = m.position();
                        ServerLevel level = lvl(m);
                        NmNet.send(level, NmNet.SHOCK, 0, at, Vec3.ZERO, 2.4f, 0, 0);
                        SubNet.send(level, SubNet.DUST, 0, at, Vec3.ZERO, 1.2f, 0, 0);
                        SubNet.send(level, SubNet.CRACKS, 0, at, Vec3.ZERO, 2.2f, 7, 1);
                        smash(m, ahead(m, 1.2), 1.6);
                        // the landing's shock flattens what stands round it
                        sweepGround(m, at, 1.6, 2.8, 180, 1.5);
                        sound(m, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.3f);
                        for (LivingEntity o : around(m, at, 1.3)) {
                            // right under the head: pinned
                            if (once(m, o)) {
                                hit(m, o, 100, 0.9f, 10);
                                Stun.apply(o, 30);
                            }
                        }
                        wave(m, at, 2.4, 70, 1.0, 0.4);
                    }
                }
                case "transfigurado/investida" -> {
                    if (f >= 16 && f <= 34) {
                        plow(m, 0.3, 0.8, 1.8);
                        for (LivingEntity o : around(m, body(m, 0.5, 0, 0.9), 1.2)) {
                            if (once(m, o)) {
                                hit(m, o, 50, 0.7f, 10);
                                Combat.push(o, m.forward(), 1.0, 0.45);
                                sound(m, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 1.2f);
                            }
                        }
                    }
                }
                case "transfigurado/abraco" -> {
                    if (f == 17 && front(m, t, 1.6, 0.2)) {
                        hold(m, t, 42);
                    }
                    if ((f == 21 || f == 35 || f == 49) && holding(m, t)) {
                        hit(m, t, 22, 0.5f, 4);
                        bleed(m, t, 40);
                        SubNet.send(lvl(m), SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.5f, 0, 0);
                        sound(m, SoundEvents.FOX_BITE, 1f, 0.6f);
                    }
                    if (f == 59 && holding(m, t)) {
                        letGo(m);
                        Combat.push(t, m.forward(), 0.8, 0.3);
                    }
                }
                case "metro/atropelar" -> {
                    if (f == 18) {
                        // the headlights flash before it runs
                        SubNet.send(lvl(m), SubNet.HIT, 0, body(m, 1.3, 0, 0.9), Vec3.ZERO, 1.4f, 2, 7);
                    }
                    if (f >= 19 && f <= 33) {
                        // the train runs through what is in its way
                        plow(m, 0.3, 1.4, 2.6);
                        for (LivingEntity o : around(m, body(m, 0.9, 0, 0.9), 1.7)) {
                            if (once(m, o)) {
                                hit(m, o, 120, 1.0f, 10);
                                // thrown up and back over the train
                                Combat.push(o, m.forward().scale(-1), 1.2, 1.1);
                                Stun.apply(o, 25);
                                sound(m, SoundEvents.ANVIL_LAND, 1.2f, 0.6f);
                            }
                        }
                    }
                }
                case "metro/portas" -> doors(m, f, t);
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return switch (m.kind() + "/" + mode) {
                case "mosca/cara" -> {
                    if (victim(m) != null) {
                        screen(victim(m), 7, 0);
                    }
                    yield true;
                }
                case "cabecao/agarrao" -> m.struck >= 2;
                case "transfigurado/abraco" -> m.struck >= 3;
                case "metro/portas" -> m.blowTick() < 39 && m.struck >= 2;
                default -> false;
            };
        }

        public void end(JjMob m, String mode, LivingEntity t) {
            if (mode.equals("cara") && m.caught >= 0) {
                // flies off the face
                m.setDeltaMovement(m.forward().scale(-0.4).add(0, 0.3, 0));
            }
        }
    };

    // The fly lands on the face (frame 18): the target sees it (screen), walks slower, and it nibbles every half second
    // until 60. Any hit from them shoos it.
    private static void flyOnFace(JjMob m, int f, LivingEntity t) {
        if (f == 18 && front(m, t, 1.8, 0.2)) {
            m.caught = t.getId();
            screen(t, 7, 42);
            slow(m, t, 42, 0);
            sound(m, SoundEvents.BEE_STING, 1f, 1.4f);
        }
        if (m.caught < 0 || t == null || t.getId() != m.caught) {
            return;
        }
        if (!alive(t) || f >= 60) {
            m.caught = -1;
            screen(t, 7, 0);
            return;
        }
        Vec3 face = t.getEyePosition().add(t.getLookAngle().scale(0.35)).add(0, -m.getBbHeight() * 0.5, 0);
        m.moveTo(face.x, face.y, face.z, t.getYRot() + 180, 0);
        m.setDeltaMovement(Vec3.ZERO);
        if (f % 10 == 0) {
            hit(m, t, 15, 0.3f, 4);
        }
    }

    // The arm shoots out (17) and catches whoever is on its line; brought to the mouth by 35, bitten at 40, tossed at 64.
    private static void grab(JjMob m, int f, LivingEntity t) {
        if (f == 17 && alive(t) && m.gap(t) < 5.5 && front(m, t, 6.5, 0.6)) {
            Vec3 from = t.position();
            if (hold(m, t, 48, age -> {
                Vec3 mouth = body(m, 1.0, 0, 1.4);
                float k = Mth.clamp(age / 18f, 0, 1);
                return from.lerp(mouth, k * k * (3 - 2 * k));
            })) {
                sound(m, SoundEvents.SLIME_ATTACK, 1.4f, 0.5f);
            }
        }
        if (f == 40 && holding(m, t)) {
            hit(m, t, 70, 0.9f, 4);
            screen(t, 0, 12);
            SubNet.send(lvl(m), SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.8f, 0, 0);
            sound(m, SoundEvents.FOX_BITE, 1.6f, 0.4f);
        }
        if (f == 64 && holding(m, t)) {
            letGo(m);
            Combat.push(t, m.forward().add(right(m).scale(0.6)).normalize(), 1.0, 0.5);
        }
    }

    // The train's doors: the tongue comes out of the nearest one (23), drags the target in (to 38), it is chewed inside
    // in the dark until 72 and spat out of the front.
    private static void doors(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        if (f == 23 && alive(t) && m.gap(t) < 5 && !t.isPassenger()) {
            Vec3 to = t.position().subtract(m.position());
            double fwd = Mth.clamp(to.dot(m.forward()), -0.9, 0.9);
            double side = Math.signum(to.dot(right(m)));
            Vec3 door = body(m, fwd, side * 1.15, 0.7);
            Vec3 from = t.position();
            Vec3 inside = body(m, 0, 0, 0.3);
            m.mark = door;
            if (hold(m, t, 50, age -> {
                if (age < 15) {
                    float k = age / 15f;
                    return from.lerp(door.subtract(0, 0.7, 0), k * k);
                }
                return inside;
            })) {
                m.caught = t.getId();
                sound(m, SoundEvents.SLIME_ATTACK, 1.6f, 0.4f);
            }
        }
        if (f >= 23 && f <= 38 && holding(m, t)) {
            tube(m, m.mark, Combat.center(t), 0.22f, 0);
        }
        if (f == 39) {
            endTube(m);
            if (holding(m, t)) {
                screen(t, 0, 34);
                sound(m, SoundEvents.IRON_DOOR_CLOSE, 2f, 0.6f);
            }
        }
        if ((f == 45 || f == 58 || f == 70) && holding(m, t)) {
            magic(m, t, 30);
            sound(m, SoundEvents.GENERIC_EAT, 1.4f, 0.5f);
        }
        if (f == 72 && holding(m, t)) {
            letGo(m);
            Vec3 out = clip(m, body(m, 0, 0, 0.3), body(m, 2.0, 0, 0.3)).subtract(m.forward().scale(0.3));
            t.teleportTo(out.x, out.y, out.z);
            Combat.push(t, m.forward(), 1.6, 0.4);
            SubNet.send(level, SubNet.DUST, 0, out, Vec3.ZERO, 0.7f, 0, 0);
        }
    }
}
