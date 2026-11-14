package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// The Mar de Dagon's curses (tools/mobs/jjmobs/peixe.js, caranguejo.js, embriao.js, afogado.js):
// - Peixe: the bite (17, shaking at 30) and the school (four more fish come in from around you and bite one after the
//   other; it bites at 32);
// - Caranguejo: the claw (closes on your waist at 23, squeezes at 36, 45, 54, throws you aside at 66) and the barnacles
//   (seven shells spat at 34-40 stick where you stood: every one that sticks slows you more);
// - Embrião: the school (the ground turns to water under it at 15 and five fish swim out to bite, then dive back) and
//   the tide (a jet of sea water 34-58 pushes you back);
// - Afogado: the hug (19-88: the water rises round you both until it covers your head at 52; drowning) and the vomit
//   (the sea pours out of its mouth 25-57, knocks you down, and leaves a slippery puddle).
final class JjDagon {
    private JjDagon() {
    }

    // how many barnacle shells stuck on each one (they slow more and more), until when
    private static final Map<Integer, Integer> SHELLS = new HashMap<>();

    static void resetStatics() {
        SHELLS.clear();
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return switch (m.kind() + "/" + mode) {
                case "peixe/cardume" -> !m.summoned();
                case "embriao/cardume" -> summonedAlive(m) == 0;
                case "caranguejo/cuspe", "embriao/mare", "afogado/vomito" -> m.hasLineOfSight(t);
                default -> true;
            };
        }

        public void start(JjMob m, String mode, LivingEntity t) {
            switch (m.kind() + "/" + mode) {
                case "peixe/mordida", "peixe/cardume" -> sound(m, SoundEvents.DOLPHIN_SWIM, 1f, 1.4f);
                case "caranguejo/pincar" -> sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.2f, 0.6f);
                case "caranguejo/cuspe" -> sound(m, SoundEvents.SNIFFER_SNIFFING, 1.2f, 1.2f);
                case "embriao/cardume" -> sound(m, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.4f, 0.8f);
                case "embriao/mare" -> sound(m, SoundEvents.PUFFER_FISH_BLOW_UP, 1.4f, 0.6f);
                case "afogado/abraco" -> sound(m, SoundEvents.DROWNED_AMBIENT, 1.4f, 0.6f);
                case "afogado/vomito" -> sound(m, SoundEvents.DROWNED_AMBIENT_WATER, 1.4f, 0.5f);
                default -> {
                }
            }
        }

        public void tick(JjMob m, String mode, int f, LivingEntity t) {
            ServerLevel level = lvl(m);
            switch (m.kind() + "/" + mode) {
                case "peixe/mordida" -> {
                    if (f == 17 && front(m, t, 1.5, 0.2)) {
                        hit(m, t, 25, 0.5f, 4);
                        SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.4f, 0, 0);
                        sound(m, SoundEvents.FOX_BITE, 1f, 1.5f);
                    }
                    if (f == 30 && front(m, t, 1.2, 0.2)) {
                        hit(m, t, 10, 0.3f, 4);
                    }
                }
                case "peixe/cardume" -> {
                    if (f == 2 && alive(t) && summonedAlive(m) < 4) {
                        // the others come in from every side
                        for (int i = 0; i < 4; i++) {
                            double a = i * Math.PI / 2 + 0.6;
                            Vec3 at = t.position().add(Math.cos(a) * 4, 1 + 0.3 * i, Math.sin(a) * 4);
                            if (level.noCollision(m.getBoundingBox().move(at.subtract(m.position())))) {
                                summon(m, "peixe", at, 110, t);
                            }
                        }
                    }
                    if (f == 32 && alive(t) && m.gap(t) < 2.5) {
                        hit(m, t, 18, 0.4f, 4);
                        SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.4f, 0, 0);
                    }
                }
                case "caranguejo/pincar" -> {
                    if (f == 23 && front(m, t, 1.6, 0.2)) {
                        hold(m, t, 43, age -> {
                            float lift = Mth.clamp((age - 1) / 8f, 0, 1) * 0.6f;
                            return body(m, 1.0 + m.getBbWidth() * 0.3, 0.4, lift);
                        });
                    }
                    if ((f == 36 || f == 45 || f == 54) && holding(m, t)) {
                        hit(m, t, 30, 0.5f, 10);
                        sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.4f, 0.4f);
                    }
                    if (f == 66 && holding(m, t)) {
                        letGo(m);
                        Combat.push(t, right(m).scale(-1), 1.0, 0.4);
                    }
                }
                case "caranguejo/cuspe" -> {
                    if (f == 33) {
                        m.mark = alive(t) ? Combat.center(t) : ahead(m, 5).add(0, 1, 0);
                    }
                    if (f >= 34 && f <= 40) {
                        Vec3 from = body(m, 0.3, (f - 37) * 0.12, 0.7);
                        Vec3 aim = m.mark.add((f - 37) * 0.1, ((f * 7) % 5) * 0.1 - 0.2, 0);
                        JmNet.send(level, JmNet.THROW, 0, from, aim.subtract(from), 9, 1, 0.6f);
                    }
                    if (f >= 43 && f <= 49) {
                        for (LivingEntity o : around(m, m.mark, 1.3)) {
                            hit(m, o, 8, 0.3f, 10);
                            int n = SHELLS.merge(o.getId(), 1, Integer::sum);
                            slow(m, o, 120, Math.min(3, n / 2));
                        }
                    }
                    if (f == 60) {
                        SHELLS.clear();
                    }
                }
                case "embriao/cardume" -> {
                    if (f == 15) {
                        NmNet.send(level, NmNet.SPLASH, 0, m.position(), Vec3.ZERO, 1.6f, 0, 0);
                        sound(m, SoundEvents.PLAYER_SPLASH, 1.4f, 0.8f);
                    }
                    if (f >= 15 && f < 25 && f % 2 == 1 && summonedAlive(m) < 5) {
                        double a = f * 1.3;
                        Vec3 at = m.position().add(Math.cos(a) * 1.0, 0.4, Math.sin(a) * 1.0);
                        summon(m, "peixe", at, 60, t);
                    }
                }
                case "embriao/mare" -> jet(m, f, t, 34, 58, 0, 7, 1.2);
                case "afogado/abraco" -> {
                    if (f == 19 && front(m, t, 1.4, 0.2) && hold(m, t, 69)) {
                        JmNet.send(level, JmNet.RISING, 0, m.position().lerp(t.position(), 0.5), Vec3.ZERO, 2.4f, 32, 1.4f);
                        sound(m, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.6f, 0.6f);
                    }
                    if (f == 52 && holding(m, t)) {
                        screen(t, 3, 34);
                    }
                    if ((f == 56 || f == 66 || f == 76 || f == 86) && holding(m, t)) {
                        magic(m, t, 15);
                        t.setAirSupply(Math.max(0, t.getAirSupply() - 60));
                        sound(m, t.position(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.2f, 0.6f);
                    }
                    if (f == 88 && holding(m, t)) {
                        letGo(m);
                        slow(m, t, 40, 1);
                        sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.4f, 0.6f);
                    }
                }
                case "afogado/vomito" -> {
                    jet(m, f, t, 25, 57, 1, 5, 1.5);
                    if (f == 48) {
                        puddle(m, ground(m, ahead(m, 3)), 100);
                    }
                }
                default -> {
                }
            }
        }

        public boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return switch (m.kind() + "/" + mode) {
                case "caranguejo/pincar", "afogado/abraco" -> m.struck >= 3;
                default -> false;
            };
        }
    };

    // A jet from the mouth along where it faces (from frame a to b, len blocks): pushes whoever it meets back; the tide is
    // sea water (look 0), the vomit seaweed and fish (look 1).
    private static void jet(JjMob m, int f, LivingEntity t, int a, int b, int look, double len, double mouthUp) {
        Vec3 mouth = body(m, 0.45, 0, mouthUp);
        Vec3 dir = m.forward().scale(len).add(0, -mouthUp * 0.6, 0);
        if (f == a) {
            JmNet.send(lvl(m), JmNet.JET, m.getId(), mouth, dir, b - a, look, look == 1 ? 100 : 0);
            sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.5f);
        }
        if (f >= a && f <= b && (f - a) % 4 == 0) {
            Vec3 end = clip(m, mouth, mouth.add(dir));
            for (LivingEntity o : line(m, mouth, end, 1.0)) {
                hit(m, o, look == 1 ? 8 : 10, 0.3f, 12);
                Combat.push(o, m.forward(), 0.35, 0.05);
                if (once(m, o)) {
                    screen(o, 3, 20);
                }
                if (look == 1 && f >= a + 20 && o.onGround()) {
                    // knocked down by it
                    Stun.apply(o, 25);
                }
            }
        }
    }

    // the vomit's slippery puddle: whoever walks in it slides and slows
    private static void puddle(NarutoMob m, Vec3 at, int ticks) {
        zone(new Zone(lvl(m)) {
            boolean tick() {
                if (age > ticks) {
                    return true;
                }
                if (age % 5 == 0) {
                    for (LivingEntity o : Combat.inRadius(level, at, 1.6, m)) {
                        if (!(o instanceof NarutoMob) && o.onGround()) {
                            slow(m, o, 10, 2);
                        }
                    }
                }
                return false;
            }
        });
    }
}
