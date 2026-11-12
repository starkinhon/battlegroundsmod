package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Inumaki (SubAlma of Jujutsu, tier 7), remade from the approved preview (forja/inumaki-kit): Cursed Speech. Every word
// drops his collar, lights the snake-and-fangs mark on his mouth, is written in the air and flies as one cone of ink
// rings. Every word strains his throat; past the limit he coughs blood and can't speak for 4 s. The throat rests on its
// own. Stark (2026-11-11): Stop, Get crushed and Sleep all stun for real ("one of the powers with the most stun").
// Z "Stop": whoever he looks at (15 blocks) freezes, stunned; held, the order holds on while the throat fills fast.
// X "Get crushed": an unseen weight comes down from above: heavy damage, pinned and stunned, the ground giving way in the
//   shape of the body. A critical on someone frozen by "Stop" (and asleep: the hit wakes them, critical).
// C "Sleep": whoever he looks at (8 blocks) falls asleep, stunned, until hit (that hit is a critical).
// V "Blow away" (ult): a breath in, then a 30-block cone that rips the ground up and throws everyone far. It bursts the
//   throat on the spot (cough, 4 s silent) unless the throat was empty before.
// B Megaphone: the next word (Z, X or C) takes everyone in a 25-block cone instead of one target, at 1.5x the throat.
public final class Inumaki {
    private static final float LIMIT = 100;
    private static final float REST_PER_TICK = 0.15f;
    // His throat: how strained, and when it was last checked.
    private static final Map<UUID, float[]> THROAT = new HashMap<>();
    // The next word goes through the megaphone.
    public static final String MEGA = "jj_inumaki_mega";
    // The words, as the client writes them (JjFx.WORDS).
    static final int W_STOP = 1, W_CRUSH = 2, W_SLEEP = 3, W_BLOW = 4;
    // Voice styles (JjFx.voice): the cone's look and speed.
    static final int V_ORDER = 0, V_SLEEP = 1, V_FROM_ABOVE = 2, V_MEGA = 3, V_MEGA_SLEEP = 4, V_BLOW = 5, V_WORD = 6;

    private Inumaki() {
    }

    public static Power create() {
        return new Power("inumaki", 0x8E7CC3, 0.95f, List.of(
                new Ability(150, 0, Inumaki::stop),
                new Ability(240, 0, Inumaki::crush),
                new Ability(260, 0, Inumaki::sleep),
                new Ability(900, 0, Inumaki::blowAway),
                new Ability(320, 0, Inumaki::megaphone)));
    }

    private static Vec3 mouth(LivingEntity caster) {
        return caster.getEyePosition().add(0, -0.18, 0).add(caster.getLookAngle().scale(0.3));
    }

    private static float throat(LivingEntity caster) {
        float[] t = THROAT.computeIfAbsent(caster.getUUID(), k -> new float[] {0, caster.level().getGameTime()});
        long now = caster.level().getGameTime();
        t[0] = Math.max(0, t[0] - (now - t[1]) * REST_PER_TICK);
        t[1] = now;
        return t[0];
    }

    // A word said: the throat takes it; past the limit he coughs blood and is silenced. Returns false when it burst.
    private static boolean strain(LivingEntity caster, ServerLevel level, float amount) {
        float now = throat(caster) + amount;
        THROAT.get(caster.getUUID())[0] = now;
        if (now <= LIMIT) {
            SubCommon.warn(caster, "inumaki.throat", (int) now);
            return true;
        }
        burst(caster, level);
        return false;
    }

    private static void burst(LivingEntity caster, ServerLevel level) {
        THROAT.get(caster.getUUID())[0] = 40;
        int silent = 80;
        PowerLogic.seal(caster, silent);
        SubNet.send(level, SubNet.COUGH, caster.getId(), caster.position(), Vec3.ZERO, silent, 0, 0);
        SubCommon.warn(caster, "inumaki.silenced");
        if (caster.getHealth() > 3) {
            caster.setHealth(caster.getHealth() - 2);
        }
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_BREATH, 1.4f, 0.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.SKELETON_HURT, 0.8f, 0.6f);
    }

    // The stun of a word: full on someone of his tier or weaker (bosses too); on stronger ones a little less, never
    // under a real stun (Stun.INTERRUPT_TICKS).
    static int wordStun(LivingEntity caster, LivingEntity t, int ticks) {
        int gap = Tiers.of(caster) - Tiers.of(t);
        if (gap > 0) {
            ticks = Math.max(Stun.INTERRUPT_TICKS, ticks - 6 * gap);
        }
        return ticks;
    }

    // Collar down, the mark lit, the word in the air and the voice flying to `end`. r1: the cone's radius at the end.
    private static void voice(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 end, float r1, int style, int word) {
        SubNet.send(level, SubNet.VOICE, caster.getId(), from, end.subtract(from), r1, style, word);
        SubNet.send(level, SubNet.SEAL_MARKS, caster.getId(), caster.position(), Vec3.ZERO, 30, 0, 0);
        SubCommon.sound(level, from, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.8f);
        SubCommon.sound(level, from, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.7f);
    }

    // Who the word takes: the one he looks at, or everyone in the megaphone's cone. Ends the megaphone.
    private static List<LivingEntity> takers(LivingEntity caster, ServerLevel level, double range, boolean mega) {
        List<LivingEntity> out = new ArrayList<>();
        if (mega) {
            SubCommon.clearState(caster, MEGA);
            SubNet.send(level, SubNet.MEGAPHONE, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
            out.addAll(Combat.inCone(caster, 25, 0.8));
            return out;
        }
        LivingEntity t = JjCommon.target(caster, range);
        if (t != null) {
            out.add(t);
        }
        return out;
    }

    private static void megaVoice(LivingEntity caster, ServerLevel level, int style, int word) {
        Vec3 from = mouth(caster).add(caster.getLookAngle().scale(0.6));
        voice(caster, level, from, from.add(caster.getLookAngle().scale(25)), 13, style, word);
    }

    // ---------- Z: Stop ----------

    private static boolean stop(LivingEntity caster, ServerLevel level) {
        boolean mega = SubCommon.hasState(caster, MEGA);
        List<LivingEntity> ts = takers(caster, level, 15, mega);
        SubCommon.pose(caster, "jj.speak");
        SubCommon.busy(caster, 8);
        if (mega) {
            megaVoice(caster, level, V_MEGA, W_STOP);
        } else {
            Vec3 m = mouth(caster);
            voice(caster, level, m, ts.isEmpty() ? m.add(caster.getLookAngle().scale(15)) : Combat.center(ts.get(0)), 1.1f, V_ORDER, W_STOP);
        }
        if (!strain(caster, level, mega ? 30 : 20)) {
            return true;
        }
        if (ts.isEmpty()) {
            SubCommon.warn(caster, "inumaki.no_target");
            DbCommon.cooldown(caster, 0, 40);
            return false;
        }
        Vec3 from = caster.position();
        for (LivingEntity t : ts) {
            int ticks = wordStun(caster, t, 30);
            int delay = Math.max(1, (int) (t.distanceTo(caster) / 3.5));
            Tasks.later(delay, () -> {
                if (!t.isAlive()) {
                    return;
                }
                hold(level, t, ticks);
                DbCommon.screen(level, Combat.center(t), 41);
            });
        }
        if (mega) {
            return true;
        }
        // Held: the order holds on (up to 3 s more) while the throat fills fast.
        LivingEntity t = ts.get(0);
        Tasks.add(age -> {
            if (age < 10) {
                return false;
            }
            if (!caster.isAlive() || !t.isAlive() || !SubCommon.held(caster, age, 30) || age > 70 || from.distanceTo(caster.position()) > 4) {
                return true;
            }
            if (age % 5 == 0) {
                if (!strain(caster, level, 5.5f)) {
                    return true;
                }
                hold(level, t, wordStun(caster, t, 12));
                SubNet.send(level, SubNet.SEAL_MARKS, caster.getId(), caster.position(), Vec3.ZERO, 10, 0, 0);
            }
            if (age % 10 == 0) {
                Vec3 m = mouth(caster);
                voice(caster, level, m, Combat.center(t), 1.1f, V_ORDER, W_STOP);
            }
            return false;
        });
        return true;
    }

    private static void hold(ServerLevel level, LivingEntity t, int ticks) {
        Freeze.hold(t, ticks);
        Stun.apply(t, ticks, true);
        PowerLogic.seal(t, ticks);
        SubNet.send(level, SubNet.HOLD_RINGS, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
    }

    // ---------- X: Get crushed ----------

    private static boolean crush(LivingEntity caster, ServerLevel level) {
        boolean mega = SubCommon.hasState(caster, MEGA);
        List<LivingEntity> ts = takers(caster, level, 15, mega);
        if (ts.isEmpty()) {
            SubCommon.warn(caster, "inumaki.no_target");
            JjCommon.shorten(caster, 1, 40);
            return false;
        }
        SubCommon.pose(caster, "jj.speak_hard");
        SubCommon.busy(caster, 12);
        if (mega) {
            megaVoice(caster, level, V_MEGA, W_CRUSH);
        } else {
            Vec3 m = mouth(caster);
            voice(caster, level, m, m.add(caster.getLookAngle().scale(1.6)), 0, V_WORD, W_CRUSH);
        }
        if (!strain(caster, level, mega ? 65 : 45)) {
            return true;
        }
        for (LivingEntity t : ts) {
            Vec3 g = SubCommon.ground(level, t.position());
            // The weight: a cone of ink rings coming down on him from above.
            SubNet.send(level, SubNet.VOICE, caster.getId(), g.add(0, 6.5, 0), new Vec3(0, -6.6, 0), 1.5f * Math.max(1, t.getBbWidth() / 0.6f), V_FROM_ABOVE, 0);
            Tasks.later(4, () -> {
                if (!t.isAlive()) {
                    return;
                }
                boolean held = Freeze.isFrozen(t);
                SubNet.send(level, SubNet.CRUSH, 0, SubCommon.ground(level, t.position()), Vec3.ZERO, 1.8f, held ? 1 : 0, 0);
                Combat.hurtMagic(caster, t, held ? 22 : 15);
                int down = wordStun(caster, t, 26);
                Stun.apply(t, down, true);
                Freeze.hold(t, down);
                t.setDeltaMovement(0, -1, 0);
                t.hurtMarked = true;
                Vec3 at = SubCommon.ground(level, t.position());
                Vec3 fw = SubCommon.flat(t.getLookAngle());
                // the ground gives way in the shape of the body: longer along it, the arms' dents at the sides
                JjCommon.crater(level, at.add(fw.scale(-0.3)), 1.4, 1.0, 5);
                JjCommon.crater(level, at.add(fw.scale(0.5)), 1.0, 0.8, 3);
                DbCommon.screen(level, Combat.center(t), 42);
                SubCommon.sound(level, t.position(), SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
                SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            });
        }
        return true;
    }

    // ---------- C: Sleep ----------

    private static boolean sleep(LivingEntity caster, ServerLevel level) {
        boolean mega = SubCommon.hasState(caster, MEGA);
        List<LivingEntity> ts = takers(caster, level, 8, mega);
        SubCommon.pose(caster, "jj.speak");
        SubCommon.busy(caster, 8);
        if (mega) {
            megaVoice(caster, level, V_MEGA_SLEEP, W_SLEEP);
        } else {
            Vec3 m = mouth(caster);
            voice(caster, level, m, ts.isEmpty() ? m.add(caster.getLookAngle().scale(8)) : Combat.center(ts.get(0)), 1.3f, V_SLEEP, W_SLEEP);
        }
        if (!strain(caster, level, mega ? 30 : 20)) {
            return true;
        }
        if (ts.isEmpty()) {
            SubCommon.warn(caster, "inumaki.no_target");
            DbCommon.cooldown(caster, 2, 40);
            return false;
        }
        for (LivingEntity t : ts) {
            int ticks = wordStun(caster, t, 60);
            int delay = Math.max(2, (int) (t.distanceTo(caster) / (mega ? 1.2 : 1.1)));
            Tasks.later(delay, () -> {
                if (!t.isAlive()) {
                    return;
                }
                SubCommon.setState(t, JjCommon.ASLEEP, ticks);
                Freeze.hold(t, ticks);
                Stun.apply(t, ticks, true);
                PowerLogic.seal(t, ticks);
                SubNet.send(level, SubNet.SLEEP, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
                SubCommon.sound(level, t.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.6f);
            });
        }
        return true;
    }

    // ---------- B: Megaphone ----------

    private static boolean megaphone(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "jj.megaphone");
        SubCommon.busy(caster, 6);
        SubCommon.setState(caster, MEGA, 120);
        SubNet.send(level, SubNet.MEGAPHONE, caster.getId(), caster.position(), Vec3.ZERO, 1, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 1.4f);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, MEGA) && caster.isAlive();
            SubNet.send(level, SubNet.MEGAPHONE, caster.getId(), caster.position(), Vec3.ZERO, on ? 1 : 0, 0, on ? 0 : 1);
            return !on;
        });
        // The cooldown counts from when the megaphone is put away (used or not).
        return true;
    }

    // ---------- V: Blow away ----------

    private static boolean blowAway(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean empty = throat(caster) < 1;
        int inhale = 12;
        float len = 30, speed = 1.6f;
        SubCommon.pose(caster, "jj.inhale");
        SubCommon.busy(caster, inhale + 24);
        Freeze.holdLookFree(caster, inhale);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, inhale + 4, 2, false, false));
        SubNet.send(level, SubNet.SEAL_MARKS, caster.getId(), caster.position(), Vec3.ZERO, inhale + 20, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_BREATH, 1.4f, 0.5f);
        List<LivingEntity> hit = new ArrayList<>();
        Vec3[] aim = new Vec3[2];
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                Freeze.free(caster);
                return true;
            }
            if (age < inhale) {
                return false;
            }
            if (age == inhale) {
                Vec3 m = mouth(caster);
                Vec3 dir = caster.getLookAngle();
                aim[0] = m;
                aim[1] = dir;
                SubCommon.pose(caster, "jj.shout");
                voice(caster, level, m, m.add(dir.scale(len)), 9, V_BLOW, W_BLOW);
                DbCommon.screen(level, m.add(dir.scale(2)), 33);
                SubCommon.sound(level, m, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
                SubCommon.sound(level, m, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
                if (empty) {
                    THROAT.computeIfAbsent(caster.getUUID(), k -> new float[] {0, level.getGameTime()})[0] = 95;
                } else {
                    throat(caster);
                    burst(caster, level);
                }
            }
            // The front runs out: the ground is ripped up under it and whoever it reaches is thrown far.
            float front = (age - inhale) * speed;
            float back = front - speed;
            Vec3 m = aim[0], dir = aim[1];
            Vec3 flat = SubCommon.flat(dir);
            for (float s = Math.max(2, (float) Math.ceil(back / 2) * 2); s <= front && s <= len; s += 2) {
                float k = s / len;
                Vec3 p = SubCommon.ground(level, m.add(dir.scale(s)));
                if (p.distanceTo(m.add(dir.scale(s))) < 4) {
                    JjCommon.crater(level, p, 1.3 + 3.2 * k, 0.9 + 0.9 * k, 6);
                }
            }
            for (LivingEntity e : Combat.inRadius(level, m.add(dir.scale(len / 2)), len / 2 + 2, caster)) {
                if (hit.contains(e)) {
                    continue;
                }
                Vec3 to = Combat.center(e).subtract(m);
                double along = to.dot(dir);
                double side = to.subtract(dir.scale(along)).length();
                if (along < 0 || along > front || side > 1.5 + 9 * along / len) {
                    continue;
                }
                hit.add(e);
                Combat.hurtMagic(caster, e, 30);
                Combat.push(e, flat, 2.6, 0.9);
                DbCommon.hit(level, Combat.center(e), 1.4f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
            }
            return front >= len;
        });
        return true;
    }

    static void clear() {
        THROAT.clear();
    }
}
