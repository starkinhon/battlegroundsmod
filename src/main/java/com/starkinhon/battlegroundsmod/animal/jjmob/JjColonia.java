package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Colônia do Jogo do Abate's curses and players (tools/mobs/jjmobs/larva.js, abate.js, solta.js):
// - Larva: the pounce (jumps at 20, clings to your chest at 27 chewing until 66; hit it and it falls off) and the
//   cocoon (silk spat at 20 holds you 26-66);
// - Jogador do Abate (a person, points over the head): the blade (cursed energy on it, two cuts at 19 and 29) and the
//   fist (energy gathers 8-28, the punch at 30 throws you 3 blocks);
// - Maldição Solta: the claws (four arms, six cuts from 21, one every 6 frames) and the scream (28-72: the view goes dark
//   and spins, slows, and no technique comes out).
final class JjColonia {
    private JjColonia() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "larva/casulo" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "larva/bote", "larva/casulo" -> sound(m, SoundEvents.SILVERFISH_AMBIENT, 1.2f, 0.5f);
                case "abate/corte" -> sound(m, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 1.2f);
                case "abate/energia" -> sound(m, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
                case "solta/garras" -> sound(m, SoundEvents.WARDEN_ANGRY, 1.2f, 1.2f);
                case "solta/grito" -> sound(m, SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.7f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "larva/bote" -> latch(m, f, t);
                case "larva/casulo" -> {
                    Vec3 mouth = body(m, 0.5, 0, 0.4);
                    if (f >= 20 && f < 26 && alive(t)) {
                        tube(m, mouth, mouth.lerp(Combat.center(t), (f - 19) / 6f), 0.13f, 3);
                    }
                    if (f == 26) {
                        if (alive(t) && m.gap(t) < 6 && m.hasLineOfSight(t) && hold(m, t, 40)) {
                            hit(m, t, 10, 0.3f, 10);
                            sound(m, SoundEvents.COBWEB_PLACE, 1.4f, 0.8f);
                        } else {
                            endTube(m);
                        }
                    }
                    if (f > 26 && f <= 66 && holding(m, t)) {
                        tube(m, mouth, Combat.center(t), 0.13f, 3);
                    }
                    if (f == 67) {
                        endTube(m);
                    }
                }
                case "abate/corte" -> {
                    if (f == 4) {
                        JmNet.send(level, JmNet.ENERGY, m.getId(), body(m, 0.5, 0.35, 1.1), body(m, 0.5, 0.35, 1.1).subtract(m.position()), 14, 1, 0);
                    }
                    if ((f == 19 || f == 29) && front(m, t, 2.2, 0.3)) {
                        hit(m, t, 45, 0.6f, 12);
                        rip(m, Combat.center(t), 0.8f);
                        sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1f);
                    }
                }
                case "abate/energia" -> {
                    if (f == 8) {
                        Vec3 fist = body(m, 0.3, -0.3, 1.2);
                        JmNet.send(level, JmNet.ENERGY, m.getId(), fist, fist.subtract(m.position()), 20, 1, 0);
                    }
                    if (f == 30 && front(m, t, 1.8, 0.3)) {
                        hit(m, t, 70, 0.9f, 12);
                        Combat.push(t, m.forward(), 1.3, 0.4);
                        NmNet.send(level, NmNet.SHOCK, 0, Combat.center(t), Vec3.ZERO, 1.2f, 0, 0);
                        sound(m, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.8f);
                    }
                }
                case "solta/garras" -> {
                    int j = (f - 21) / 6;
                    if (f >= 21 && (f - 21) % 6 == 0 && j < 6 && front(m, t, 2.6, 0.2)) {
                        hit(m, t, 25, 0.5f, 4);
                        rip(m, Combat.center(t), 0.7f);
                        if (j == 5) {
                            Combat.push(t, m.forward(), 0.8, 0.3);
                        }
                        sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
                    }
                }
                case "solta/grito" -> {
                    if (f == 28) {
                        NmNet.send(level, NmNet.SOUND, 0, body(m, 0.5, 0, 1.4), m.forward().scale(8), 0, 0, 0);
                        sound(m, SoundEvents.WARDEN_ROAR, 2f, 1.2f);
                    }
                    if (f >= 28 && f <= 72 && f % 10 == 8) {
                        for (LivingEntity o : around(m, m.position(), 8)) {
                            if (once(m, o)) {
                                screen(o, 0, 44);
                                dizzy(m, o, 40);
                            }
                            // no technique comes out while it screams
                            Stun.cooldown(o, 14);
                            slow(m, o, 14, 2);
                            if (f % 20 == 8) {
                                magic(m, o, 10);
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            if (m.kind().equals("larva") && mode.equals("bote")) {
                m.setNoGravity(false);
                m.setDeltaMovement(m.forward().scale(-0.3).add(0, 0.2, 0));
                return true;
            }
            return false;
        }
    };

    // Clings to the chest (27) and chews until 66.
    private static void latch(JjMob m, int f, LivingEntity t) {
        if (f == 27 && front(m, t, 2.2, 0.2)) {
            m.caught = t.getId();
            m.setNoGravity(true);
            sound(m, SoundEvents.SLIME_ATTACK, 1.2f, 0.8f);
        }
        if (m.caught < 0 || t == null || t.getId() != m.caught) {
            return;
        }
        if (!alive(t) || f >= 66) {
            m.caught = -1;
            m.setNoGravity(false);
            return;
        }
        Vec3 look = t.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-3 ? m.forward().scale(-1) : look.normalize();
        Vec3 chest = t.position().add(look.scale(t.getBbWidth() * 0.5 + 0.2)).add(0, t.getBbHeight() * 0.55 - m.getBbHeight() * 0.5, 0);
        m.moveTo(chest.x, chest.y, chest.z, t.getYRot() + 180, 0);
        m.setDeltaMovement(Vec3.ZERO);
        if (f % 8 == 3) {
            hit(m, t, 15, 0.3f, 4);
            sound(m, SoundEvents.GENERIC_EAT, 0.8f, 0.7f);
        }
    }
}
