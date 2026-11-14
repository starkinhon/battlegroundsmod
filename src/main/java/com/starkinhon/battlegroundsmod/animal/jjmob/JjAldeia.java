package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Aldeia Amaldiçoada's curses (tools/mobs/jjmobs/espantalho.js, poco.js, kuchisake.js):
// - Espantalho: the crows (burst out at 13, cover your face 18-68 pecking; you see little) and the hug (17-58, straw
//   stabs at 25, 37, 49; fire makes it let go, so does a friend's three hits);
// - Poço (the Okiku): eight plates thrown one every half second (each lands 6 frames after it leaves the hand, where
//   you stood) and the scream at 104; the hair (shoots 10-17, drags you to the well 24-52, drowns you 60-88);
// - Kuchisake: the question (she appears in front of you at 20, you cannot move from 32, the mask comes off and the
//   scissors close at 98; hit her while the mask is off, 68-98, and she gives up) and the scissors (cuts at 6 and 16, the
//   thrust at 36).
final class JjAldeia {
    private JjAldeia() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "poco/pratos", "poco/puxar", "espantalho/revoada" -> m.hasLineOfSight(t);
                case "kuchisake/pergunta" -> m.hasLineOfSight(t) && !Freeze.isFrozen(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "espantalho/revoada" -> sound(m, SoundEvents.PARROT_IMITATE_WITCH, 1.2f, 0.5f);
                case "espantalho/abraco" -> sound(m, SoundEvents.GRASS_BREAK, 1.2f, 0.6f);
                case "poco/pratos" -> sound(m, SoundEvents.WITCH_AMBIENT, 1.2f, 0.6f);
                case "poco/puxar" -> sound(m, SoundEvents.DROWNED_AMBIENT, 1.4f, 0.6f);
                case "kuchisake/pergunta" -> sound(m, SoundEvents.WITCH_CELEBRATE, 1.2f, 0.6f);
                case "kuchisake/tesouradas" -> sound(m, SoundEvents.SHEEP_SHEAR, 1.2f, 1.2f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "espantalho/revoada" -> {
                    if (f == 13 && alive(t)) {
                        Vec3 from = body(m, 0, 0, 1.6);
                        NmNet.send(level, NmNet.FLOCK, 0, from, t.getEyePosition().subtract(from), 55, 0, 0);
                        sound(m, SoundEvents.PHANTOM_FLAP, 1.6f, 1.4f);
                    }
                    if (f == 18 && alive(t) && m.distanceTo(t) < 8 && m.hasLineOfSight(t)) {
                        m.caught = t.getId();
                        screen(t, 6, 50);
                    }
                    if (f > 18 && f <= 68 && f % 10 == 0 && t != null && t.getId() == m.caught && alive(t)) {
                        hit(m, t, 12, 0.3f, 4);
                        sound(m, t.position(), SoundEvents.PARROT_HURT, 1f, 0.6f);
                    }
                }
                case "espantalho/abraco" -> {
                    if (f == 17 && front(m, t, 1.4, 0.2)) {
                        hold(m, t, 41);
                    }
                    if ((f == 25 || f == 37 || f == 49) && holding(m, t)) {
                        hit(m, t, 25, 0.4f, 4);
                        sound(m, SoundEvents.GRASS_BREAK, 1.2f, 1.2f);
                    }
                    if (m.isOnFire() && m.heldId >= 0) {
                        // straw: it lets go of whatever burns it
                        letGo(m);
                    }
                }
                case "poco/pratos" -> plates(m, f, t);
                case "poco/puxar" -> hair(m, f, t);
                case "kuchisake/pergunta" -> question(m, f, t);
                case "kuchisake/tesouradas" -> {
                    if (f == 6 || f == 16 || f == 36) {
                        // the scissors cut what they close on in front
                        double y = yaw(m);
                        if (f == 36) {
                            carve(m, body(m, 1.6, 0, 1.0), 0.7, 0, 1, 2.5f, 0);
                        } else {
                            arc(m, m.position(), 1.6, y + (f == 6 ? -40 : 40), y + (f == 6 ? 40 : -40), 0.8, 1.6);
                        }
                    }
                    if ((f == 6 || f == 16) && front(m, t, 2.2, 0.3)) {
                        hit(m, t, 30, 0.5f, 4);
                        rip(m, Combat.center(t), 0.7f);
                        sound(m, SoundEvents.SHEEP_SHEAR, 1.4f, 0.9f);
                    }
                    if (f == 36 && front(m, t, 2.5, 0.3)) {
                        hit(m, t, 55, 0.7f, 4);
                        bleed(m, t, 40);
                        Combat.push(t, m.forward(), 1.0, 0.3);
                        SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.6f, 0, 0);
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return switch (m.kind() + "/" + mode) {
                case "espantalho/abraco" -> m.struck >= 3;
                case "poco/puxar" -> m.struck >= 3;
                case "kuchisake/pergunta" -> {
                    int f = m.blowTick() - 1;
                    if (f >= 68 && f < 98) {
                        // hit while the mask is off: she gives up, stunned
                        m.cutBlow();
                        Stun.apply(m, 30);
                        sound(m, SoundEvents.WITCH_HURT, 1.4f, 0.6f);
                        yield true;
                    }
                    yield false;
                }
                default -> false;
            };
        }
    };

    // Eight plates, one every half second from 0.3 s: each leaves the hand 6 frames after it is drawn and lands 6 frames
    // later where the target stood; then the scream (104).
    private static void plates(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        if (m.marks.length != 8) {
            m.marks = new Vec3[8];
        }
        int rel = f - 12;
        if (rel >= 0 && rel % 10 == 0 && rel / 10 < 8) {
            int i = rel / 10;
            Vec3 hand = body(m, 0.3, 0.35, 1.4);
            Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 6).add(0, 1, 0);
            m.marks[i] = aim;
            JmNet.send(level, JmNet.THROW, 0, hand, aim.subtract(hand), 6, 2, 0.5f);
            sound(m, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.6f);
        }
        int land = f - 18;
        if (land >= 0 && land % 10 == 0 && land / 10 < 8 && m.marks[land / 10] != null) {
            Vec3 at = m.marks[land / 10];
            sound(m, at, SoundEvents.GLASS_BREAK, 1f, 1.2f);
            // the plate shatters what is brittle where it hits (glass, leaves, plants)
            carve(m, at, 0.7, 0, 0, 0.6f, 0);
            for (LivingEntity o : around(m, at, 0.9)) {
                hit(m, o, 22, 0.4f, 10);
            }
        }
        if (f == 104) {
            NmNet.send(level, NmNet.SOUND, 0, body(m, 0.3, 0, 1.6), m.forward().scale(4), 0, 0, 0);
            sound(m, SoundEvents.GHAST_SCREAM, 1.4f, 0.6f);
            for (LivingEntity o : around(m, m.position(), 4.5)) {
                magic(m, o, 30);
                screen(o, 0, 30);
            }
        }
    }

    // The hair shoots out (10-17), catches, drags to the well's edge (24-52) and pulls under (60-88), let go at 92.
    private static void hair(JjMob m, int f, LivingEntity t) {
        Vec3 root = body(m, 0.2, 0, 1.5);
        if (f >= 10 && f < 17 && alive(t)) {
            tube(m, root, root.lerp(Combat.center(t), (f - 10) / 7f), 0.2f, 2);
        }
        if (f == 17) {
            if (alive(t) && m.gap(t) < 7 && m.hasLineOfSight(t)) {
                Vec3 from = t.position();
                Vec3 edge = body(m, 0.9, 0, 0);
                hold(m, t, 75, age -> {
                    int g = age + 17;
                    if (g < 24) {
                        return from;
                    }
                    if (g < 52) {
                        float k = (g - 24) / 28f;
                        return from.lerp(edge, k * k * (3 - 2 * k));
                    }
                    if (g < 60) {
                        return edge;
                    }
                    return edge.subtract(0, Mth.clamp((g - 60) / 10f, 0, 1) * 0.8, 0);
                });
            }
            if (!holding(m, t)) {
                endTube(m);
            }
        }
        if (f >= 17 && f <= 56 && holding(m, t)) {
            tube(m, root, Combat.center(t), 0.2f, 2);
        }
        if (f == 57) {
            endTube(m);
        }
        if (f == 60 && holding(m, t)) {
            screen(t, 3, 30);
            sound(m, SoundEvents.PLAYER_SPLASH, 1.4f, 0.6f);
        }
        if ((f == 66 || f == 76 || f == 86) && holding(m, t)) {
            magic(m, t, 20);
            sound(m, t.position(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.2f, 0.6f);
        }
        if (f == 92 && holding(m, t)) {
            letGo(m);
            Combat.push(t, m.forward(), 0.6, 0.3);
        }
    }

    // She appears in front of you (20), you cannot move (32-102), the mask comes off and the scissors close (98).
    private static void question(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        if (f == 20 && alive(t)) {
            Vec3 look = t.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1.0E-3 ? m.position().subtract(t.position()).multiply(1, 0, 1).normalize() : look.normalize();
            Vec3 spot = t.position().add(look.scale(1.6));
            if (level.noCollision(m, m.getBoundingBox().move(spot.subtract(m.position())))) {
                SubNet.send(level, SubNet.POOF, 0, m.position().add(0, 1, 0), Vec3.ZERO, 0.8f, 0, 0);
                m.teleportTo(spot.x, spot.y, spot.z);
            }
            face(m, t);
        }
        if (f == 32 && front(m, t, 3, 0.0) && hold(m, t, 70)) {
            // made to look at her
            Vec3 to = m.getEyePosition().subtract(t.getEyePosition());
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
            Freeze.face(t, yaw, pitch);
            sound(m, SoundEvents.WITCH_AMBIENT, 1.4f, 0.5f);
        }
        if (f == 68 && holding(m, t)) {
            sound(m, SoundEvents.WITCH_CELEBRATE, 1.6f, 0.4f);
        }
        if (f == 98 && holding(m, t)) {
            hit(m, t, 110, 1.0f, 4);
            bleed(m, t, 80);
            rip(m, t.getEyePosition().subtract(0, 0.2, 0), 0.6f);
            SubNet.send(level, SubNet.BLOOD_SPLASH, 0, t.getEyePosition(), m.forward(), 1f, 0, 0);
            sound(m, SoundEvents.SHEEP_SHEAR, 2f, 0.6f);
            letGo(m);
        }
    }
}
