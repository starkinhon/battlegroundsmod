package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// The Floresta Negra's curses (tools/mobs/jjmobs/rastejante.js, verme.js, olhos.js, utero.js):
// - Rastejante: the hands (slams at 20, the cracks run to you until 32 and the ground's arms come up there: whoever is
//   on the ground is squeezed until 90; jump and they miss) and the carousel (screams at 42, the wave hits at 49);
// - Verme: swallows what you hold (19) and runs with it, giving it back when it dies; and coils on you (19-70, the
//   view blurs; one hit and it lets go);
// - Olhos: the roots (aimed at 16, catch whoever is still at the end of the row at 26) and the stare (20-72: while
//   you look at it you cannot move; look away and it breaks);
// - Útero: gives birth to a fly (43, four at most) and the cord (15-80: drinks your life to heal; it snaps if you hit it
//   or get 9 blocks away).
final class JjFloresta {
    private JjFloresta() {
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                // only what it can swallow, and nothing more while it carries something
                case "verme/engolir" -> m.stolen.isEmpty() && t instanceof Player p && !p.isCreative() && !p.getMainHandItem().isEmpty();
                case "verme/enrolar" -> m.stolen.isEmpty();
                case "utero/parir" -> summonedAlive(m) < 4;
                case "olhos/olhar" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public Vec3 fleeFrom(JjMob m) {
            LivingEntity t = m.getTarget();
            return m.kind().equals("verme") && !m.stolen.isEmpty() && t != null ? t.position() : null;
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "rastejante/maos" -> sound(m, SoundEvents.WARDEN_AMBIENT, 2f, 0.6f);
                case "rastejante/carrossel" -> sound(m, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.6f);
                case "verme/engolir", "verme/enrolar" -> sound(m, SoundEvents.SILVERFISH_AMBIENT, 1.2f, 0.6f);
                case "olhos/raizes" -> sound(m, SoundEvents.ROOTS_BREAK, 1.6f, 0.6f);
                case "olhos/olhar" -> sound(m, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 1.4f);
                case "utero/parir" -> sound(m, SoundEvents.SLIME_SQUISH, 1.6f, 0.4f);
                case "utero/cordao" -> sound(m, SoundEvents.SLIME_JUMP, 1.4f, 0.5f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "rastejante/maos" -> {
                    if (f == 20) {
                        m.mark = ground(m, alive(t) ? t.position() : ahead(m, 6));
                        SubNet.send(level, SubNet.CRACKS, 0, body(m, 2.2, 0, 0), Vec3.ZERO, 2.4f, 8, 1);
                        smash(m, ground(m, body(m, 2.2, 0, 0)), 2.2);
                        sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.6f);
                    }
                    if (f > 20 && f < 32 && f % 3 == 0) {
                        // the cracks run under the ground to the spot
                        float k = (f - 20) / 12f;
                        Vec3 p = ground(m, body(m, 2.2, 0, 0).lerp(m.mark, k));
                        SubNet.send(level, SubNet.CRACKS, 0, p, Vec3.ZERO, 1.2f, 4, 1);
                    }
                    if (f == 32) {
                        JmNet.send(level, JmNet.HANDS, 0, m.mark, Vec3.ZERO, 1.6f, 58, 6);
                        sound(m, m.mark, SoundEvents.ROOTS_BREAK, 2f, 0.5f);
                        for (LivingEntity o : around(m, m.mark, 1.7)) {
                            if (o.onGround() && hold(m, o, 58)) {
                                hit(m, o, 30, 0.8f, 6);
                                break;
                            }
                        }
                    }
                    if ((f == 52 || f == 65 || f == 77 || f == 90) && m.heldId >= 0 && level.getEntity(m.heldId) instanceof LivingEntity h) {
                        hit(m, h, 40, 0.8f, 6);
                        sound(m, h.position(), SoundEvents.SLIME_BLOCK_BREAK, 1.4f, 0.5f);
                    }
                }
                case "rastejante/carrossel" -> {
                    if (f == 42) {
                        JmNet.send(level, JmNet.WAVE, 0, body(m, 1.6, 0, 0), m.forward().scale(9), 3, 0, 0);
                        sound(m, SoundEvents.WARDEN_ROAR, 2.5f, 0.7f);
                    }
                    if (f == 49) {
                        for (LivingEntity o : around(m, m.position(), 11)) {
                            Vec3 to = o.position().subtract(m.position()).multiply(1, 0, 1);
                            if (to.length() > 10.5 || to.lengthSqr() > 1 && to.normalize().dot(m.forward()) < 0.5 || !once(m, o)) {
                                continue;
                            }
                            magic(m, o, 120);
                            Combat.push(o, m.forward(), 1.4, 0.6);
                            dizzy(m, o, 32);
                            impact(m, Combat.center(o), 0.9f, JmNet.FLESH);
                        }
                    }
                }
                case "verme/engolir" -> {
                    if (f == 19 && front(m, t, 1.6, 0.2) && t instanceof Player p && !p.isCreative()) {
                        ItemStack hand = p.getMainHandItem();
                        if (!hand.isEmpty()) {
                            m.stolen = hand.copy();
                            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            m.setPersistenceRequired();
                            sound(m, SoundEvents.GENERIC_EAT, 1.4f, 0.6f);
                        }
                    } else if (f == 19 && front(m, t, 1.6, 0.2)) {
                        hit(m, t, 25, 0.5f, 10);
                    }
                }
                case "verme/enrolar" -> {
                    if (f == 19 && front(m, t, 1.5, 0.2) && hold(m, t, 51)) {
                        screen(t, 2, 51);
                        sound(m, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
                    }
                    if (f > 19 && f <= 70 && f % 10 == 0 && holding(m, t)) {
                        hit(m, t, 12, 0.4f, 10);
                    }
                }
                case "olhos/raizes" -> {
                    if (f == 16) {
                        m.mark = ground(m, alive(t) ? t.position() : ahead(m, 6));
                        Vec3 from = ground(m, body(m, 0.8, 0, 0));
                        NmNet.send(level, NmNet.STAKES, 0, from, m.mark.subtract(from), 7, 0, 0);
                    }
                    if (f == 26) {
                        boolean held = false;
                        for (LivingEntity o : around(m, m.mark, 1.4)) {
                            if (!held && hold(m, o, 40)) {
                                held = true;
                            }
                            hit(m, o, 50, 0.7f, 10);
                        }
                    }
                }
                case "olhos/olhar" -> stare(m, f, t);
                case "utero/parir" -> {
                    if (f == 43 && summonedAlive(m) < 4) {
                        Vec3 at = body(m, 0.9, 0, 0.6);
                        summon(m, "mosca", at, 900, t);
                        SubNet.send(level, SubNet.BLOOD_SPLASH, 0, at, m.forward(), 0.8f, 0, 0);
                        sound(m, SoundEvents.SLIME_SQUISH, 1.6f, 0.5f);
                    }
                }
                case "utero/cordao" -> cord(m, f, t);
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return switch (m.kind() + "/" + mode) {
                case "verme/enrolar" -> {
                    if (victim(m) != null) {
                        screen(victim(m), 2, 0);
                    }
                    yield true;
                }
                case "utero/cordao" -> {
                    endTube(m);
                    yield true;
                }
                case "rastejante/maos" -> m.struck >= 4;
                default -> false;
            };
        }
    };

    // While the target looks at it (its eyes on the trunk, in line of sight, within 10 blocks) it cannot move; turning
    // away breaks it. The stare hurts a little every second.
    private static void stare(JjMob m, int f, LivingEntity t) {
        if (f < 20 || f > 72 || !alive(t)) {
            if (f == 73) {
                letGo(m);
            }
            return;
        }
        Vec3 eye = m.position().add(0, m.getBbHeight() * 0.7, 0);
        Vec3 to = eye.subtract(t.getEyePosition());
        boolean looks = to.length() < 10.5 && to.normalize().dot(t.getLookAngle()) > 0.5 && m.hasLineOfSight(t);
        if (!looks) {
            if (holding(m, t)) {
                letGo(m);
                screen(t, 4, 0);
            }
            return;
        }
        if (holding(m, t) || !Freeze.isFrozen(t)) {
            letGo(m);
            holdLook(m, t, 4);
        }
        if (f % 10 == 0) {
            screen(t, 4, 14);
        }
        if (f % 20 == 0) {
            magic(m, t, 15);
        }
    }

    // The cord catches (15) and drinks until 80: a pulse every 14 frames takes life and gives it to the womb.
    private static void cord(JjMob m, int f, LivingEntity t) {
        Vec3 root = body(m, 0.7, 0, 0.9);
        if (f == 15 && alive(t) && m.gap(t) < 6 && m.hasLineOfSight(t)) {
            m.caught = t.getId();
            sound(m, SoundEvents.SLIME_ATTACK, 1.4f, 0.6f);
        }
        if (m.caught < 0 || t == null || t.getId() != m.caught) {
            return;
        }
        if (!alive(t) || t.distanceTo(m) > 9 || f > 80) {
            m.caught = -1;
            endTube(m);
            return;
        }
        tube(m, root, Combat.center(t), 0.16f, 1);
        if ((f - 15) % 14 == 7) {
            magic(m, t, 20);
            m.heal(10);
            SubNet.send(lvl(m), SubNet.BLOOD_SPLASH, 0, Combat.center(t), root.subtract(Combat.center(t)).normalize(), 0.4f, 0, 0);
            sound(m, SoundEvents.HONEY_DRINK, 1.2f, 0.5f);
        }
    }
}
