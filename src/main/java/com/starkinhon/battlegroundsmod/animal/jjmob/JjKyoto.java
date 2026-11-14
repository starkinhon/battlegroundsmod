package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Outono de Kyoto's curses (tools/mobs/jjmobs/lanterna.js, mechamaru.js, oni.js):
// - Lanterna: the tongue (shoots at 20, coils round your neck at 24, pulls you under it 32-52, lets go at 60) and the
//   onibi (three blue flames spat at 20, 25, 30 circle it and fall one by one at 59, 66, 73 on where you stood);
// - Mechamaru: the cannon (charges 20-40, a thick beam 40-60 that pushes you back) and the blade (dashes through you
//   at 22-27; after it, a second with its back to you);
// - Oni: the club (overhead, slams at 33: the ground sinks and the shockwave throws) and the grab (catches at 19, lifts
//   you to its face, roars at 36, throws you over its shoulder at 63; three hits on it free you).
final class JjKyoto {
    private JjKyoto() {
    }

    // the cannon's beam id per Mechamaru
    private static final Map<Integer, Integer> BEAM = new HashMap<>();

    static void resetStatics() {
        BEAM.clear();
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "lanterna/lingua", "lanterna/onibi", "mechamaru/canhao" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "lanterna/lingua" -> sound(m, SoundEvents.FROG_AMBIENT, 1.2f, 0.6f);
                case "lanterna/onibi" -> sound(m, SoundEvents.BLAZE_AMBIENT, 1.2f, 1.4f);
                case "mechamaru/canhao" -> sound(m, SoundEvents.PISTON_EXTEND, 1.2f, 0.7f);
                case "mechamaru/espada" -> sound(m, SoundEvents.PISTON_CONTRACT, 1.2f, 1.4f);
                case "oni/porrada", "oni/agarrao" -> sound(m, SoundEvents.RAVAGER_ROAR, 1.4f, 0.8f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "lanterna/lingua" -> tongue(m, f, t);
                case "lanterna/onibi" -> {
                    if (f == 0) {
                        m.marks = new Vec3[3];
                    }
                    int i = (f - 20) / 5;
                    if (f >= 20 && f <= 30 && (f - 20) % 5 == 0 && m.marks.length == 3) {
                        Vec3 centre = body(m, 0, 0, 0.5);
                        Vec3 aim = alive(t) ? t.position().add(0, t.getBbHeight() * 0.5, 0) : ahead(m, 6);
                        m.marks[i] = aim;
                        JmNet.send(level, JmNet.WISP, m.getId(), centre, aim.subtract(centre), 32 + 2 * i, i, 7);
                        sound(m, SoundEvents.SOUL_ESCAPE.value(), 1f, 1.3f);
                    }
                    int k = f == 59 ? 0 : f == 66 ? 1 : f == 73 ? 2 : -1;
                    if (k >= 0 && m.marks.length == 3 && m.marks[k] != null) {
                        sound(m, m.marks[k], SoundEvents.FIRECHARGE_USE, 1f, 1.5f);
                        DbCommon.hit(level, m.marks[k], 0.8f, 12);
                        for (LivingEntity o : around(m, m.marks[k], 1.3)) {
                            magic(m, o, 40);
                            o.igniteForSeconds(2);
                        }
                    }
                }
                case "mechamaru/canhao" -> cannon(m, f, t);
                case "mechamaru/espada" -> {
                    if (f >= 22 && f <= 27) {
                        for (LivingEntity o : around(m, body(m, 0.6, 0, 0.8), 1.4)) {
                            if (once(m, o)) {
                                hit(m, o, 60, 0.7f, 12);
                                SubNet.send(level, SubNet.SLASH, 0, Combat.center(o), right(m).add(0, 0.6, 0), 0.8f, 0.1f, SubNet.STYLE_BLUE);
                                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.2f);
                            }
                        }
                    }
                }
                case "oni/porrada" -> {
                    if (f == 33) {
                        Vec3 imp = ground(m, body(m, 2.6, 0.1, 0));
                        SubNet.send(level, SubNet.CRACKS, 0, imp, Vec3.ZERO, 2.5f, 8, 1);
                        NmNet.send(level, NmNet.SHOCK, 0, imp, Vec3.ZERO, 3f, 0, 0);
                        SubNet.send(level, SubNet.DUST, 0, imp, Vec3.ZERO, 1.3f, 0, 0);
                        sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
                        for (LivingEntity o : around(m, imp.add(0, 0.5, 0), 1.2)) {
                            if (once(m, o)) {
                                hit(m, o, 120, 1f, 10);
                                Stun.apply(o, 25);
                            }
                        }
                        wave(m, imp, 3, 90, 1.1, 0.5);
                    }
                }
                case "oni/agarrao" -> {
                    if (f == 19 && front(m, t, 2.2, 0.2)) {
                        Vec3 from = t.position();
                        hold(m, t, 44, age -> {
                            Vec3 hand = body(m, 0.9, -0.4, 1.2);
                            float k = Mth.clamp(age / 13f, 0, 1);
                            return from.lerp(hand, k * k * (3 - 2 * k));
                        });
                    }
                    if (f == 36) {
                        NmNet.send(level, NmNet.SOUND, 0, body(m, 0.4, 0, m.getBbHeight() * 0.85), m.forward().scale(4), 0, 0, 0);
                        sound(m, SoundEvents.RAVAGER_ROAR, 2.4f, 0.6f);
                    }
                    if (f == 40 && holding(m, t)) {
                        magic(m, t, 20);
                        screen(t, 0, 10);
                    }
                    if (f == 63 && holding(m, t)) {
                        letGo(m);
                        hit(m, t, 50, 0.8f, 10);
                        // over its shoulder
                        Combat.push(t, m.forward().scale(-1), 1.6, 1.0);
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return switch (m.kind() + "/" + mode) {
                case "oni/agarrao" -> m.struck >= 3;
                case "lanterna/lingua" -> {
                    if (m.struck >= 2) {
                        endTube(m);
                        yield true;
                    }
                    yield false;
                }
                default -> false;
            };
        }

        public void end(JjMob m, String mode, LivingEntity t) {
            Integer id = BEAM.remove(m.getId());
            if (id != null) {
                SubNet.send(lvl(m), SubNet.KI_BEAM, id, m.position(), Vec3.ZERO, 0, 0, 1);
            }
        }
    };

    // The tongue shoots out (20), coils round the neck (24), pulls the target under the lantern (32-52), lets go at 60.
    private static void tongue(JjMob m, int f, LivingEntity t) {
        Vec3 mouth = body(m, 0.3, 0, 0.4);
        if (f >= 20 && f < 24 && alive(t)) {
            tube(m, mouth, mouth.lerp(t.getEyePosition().subtract(0, 0.25, 0), (f - 19) / 4f), 0.1f, 0);
        }
        if (f == 24) {
            if (alive(t) && m.gap(t) < 4.5 && m.hasLineOfSight(t)) {
                Vec3 from = t.position();
                hold(m, t, 36, age -> {
                    int g = age + 24;
                    Vec3 under = body(m, 0.9, 0, -t.getBbHeight() * 0.8);
                    if (g < 32) {
                        return from;
                    }
                    float k = Mth.clamp((g - 32) / 20f, 0, 1);
                    return from.lerp(under, k * k * (3 - 2 * k));
                });
                sound(m, SoundEvents.FROG_TONGUE, 1.4f, 0.8f);
            }
            if (!holding(m, t)) {
                endTube(m);
            }
        }
        if (f >= 24 && f <= 60 && holding(m, t)) {
            tube(m, mouth, t.getEyePosition().subtract(0, 0.25, 0), 0.1f, 0);
        }
        if (f == 40 && holding(m, t)) {
            magic(m, t, 30);
        }
        if (f == 56 && holding(m, t)) {
            hit(m, t, 20, 0.4f, 12);
        }
        if (f == 60) {
            endTube(m);
            if (holding(m, t)) {
                letGo(m);
                Combat.push(t, m.forward(), 0.5, 0.2);
            }
        }
    }

    // Charges (20-40) at the cannon's mouth, fires 40-60 along where it aimed when it fired.
    private static void cannon(JjMob m, int f, LivingEntity t) {
        ServerLevel level = lvl(m);
        Vec3 muzzle = body(m, 0.7, -0.35, 1.0);
        if (f == 20) {
            JmNet.send(level, JmNet.ENERGY, m.getId(), muzzle, muzzle.subtract(m.position()), 20, 0, 0);
        }
        if (f == 40) {
            Vec3 aim = alive(t) ? Combat.center(t).subtract(muzzle) : m.forward();
            aim = aim.lengthSqr() < 1.0E-3 ? m.forward() : aim.normalize();
            // never aimed far up or down: the arm points ahead
            m.mark = new Vec3(aim.x, Mth.clamp(aim.y, -0.35, 0.35), aim.z).normalize();
            BEAM.put(m.getId(), SubNet.newId());
            sound(m, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.4f);
        }
        Integer id = BEAM.get(m.getId());
        if (f >= 40 && f <= 60 && id != null) {
            Vec3 end = clip(m, muzzle, muzzle.add(m.mark.scale(7)));
            SubNet.send(level, SubNet.KI_BEAM, id, muzzle, end.subtract(muzzle), 0.35f, Db.BLUE, f == 60 ? 1 : 0);
            if (f == 60) {
                BEAM.remove(m.getId());
            }
            if ((f - 40) % 5 == 0) {
                for (LivingEntity o : line(m, muzzle, end, 0.8)) {
                    hit(m, o, 25, 0.5f, 12);
                    Combat.push(o, m.mark, 0.4, 0.05);
                }
            }
        }
    }
}
