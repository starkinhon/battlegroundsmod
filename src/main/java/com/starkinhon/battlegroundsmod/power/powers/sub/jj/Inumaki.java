// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Blood;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
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

import net.minecraft.core.BlockPos;
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
// 1st update (approved preview forja/inumaki-kit/inumaki-vivo.js, Stark 2026-11-27 "aprovadíssimo"): every word ADDS
// sound rings flying from the mouth and the body in the way shaking, and does exactly what it says:
//   "Get crushed" presses the body into a sheet of paper in the hole until the stun ends; finishing, it becomes a pool.
//   "Sleep" never knocks down any more: 2 s slow and tired, staggering.
//   "Blow away" (V, "Voe pra longe") throws MUCH further, through whatever is in the way, block by block; finishing
//   someone in front, they fly off to the horizon; finishing someone above him (looking up), they become a star.
//   Shift+V "Explode" (new): the body swells and bursts; the whole throat: 6 s silent after it.
public final class Inumaki {
    private static final float LIMIT = 100;
    private static final float REST_PER_TICK = 0.15f;
    // His throat: how strained, and when it was last checked.
    private static final Map<UUID, float[]> THROAT = new HashMap<>();
    // The next word goes through the megaphone.
    public static final String MEGA = "jj_inumaki_mega";
    // The words, as the client writes them (JjFx.WORDS).
    static final int W_STOP = 1, W_CRUSH = 2, W_SLEEP = 3, W_BLOW = 4, W_EXPLODE = 5;
    // Voice styles (JjFx.voice): the cone's look and speed.
    static final int V_ORDER = 0, V_SLEEP = 1, V_FROM_ABOVE = 2, V_MEGA = 3, V_MEGA_SLEEP = 4, V_BLOW = 5, V_WORD = 6, V_EXPLODE = 7;

    private Inumaki() {
    }

    public static Power create() {
        return new Power("inumaki", 0x8E7CC3, 0.95f, List.of(
                new Ability(150, 0, Inumaki::stop),
                new Ability(240, 0, Inumaki::crush),
                new Ability(260, 0, Inumaki::sleep),
                new Ability(900, 0, Inumaki::blowAway),
                new Ability(320, 0, Inumaki::megaphone)),
                Arrays.asList(null, null, null, new Ability(1200, 0, Inumaki::explode), null));
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

    // How strained the throat is, 0 to 1 (the meter by the crosshair: SubHud).
    public static float throatFraction(LivingEntity caster) {
        return THROAT.containsKey(caster.getUUID()) ? Math.min(1, throat(caster) / LIMIT) : 0;
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
        burst(caster, level, 80);
    }

    private static void burst(LivingEntity caster, ServerLevel level, int silent) {
        THROAT.computeIfAbsent(caster.getUUID(), k -> new float[] {0, level.getGameTime()})[0] = 40;
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

    // The sound rings flying out of the mouth along the voice (n rings, how wide they open).
    private static void ripples(ServerLevel level, Vec3 from, Vec3 to, int n, float spread) {
        SubNet.send(level, SubNet.INU_RIPPLE, SubNet.newId(), from, to.subtract(from), n, spread, 0);
    }

    // What a word does to a body, drawn on every client (client/fx/InumakiBody).
    private static void body(ServerLevel level, LivingEntity t, int mode, int ticks) {
        SubNet.send(level, SubNet.INU_BODY, t.getId(), t.position(), Vec3.ZERO, mode, ticks, 0);
    }

    private static final int B_TREMBLE = 1, B_PAPER = 2, B_DROWSY = 3, B_SWELL = 4, B_GONE = 5;

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
        ripples(level, from, from.add(caster.getLookAngle().scale(25)), 8, 3f);
    }

    // A word said to one target: the voice with its rings.
    private static void sayTo(LivingEntity caster, ServerLevel level, Vec3 end, float r1, int style, int word, int rings, float spread) {
        Vec3 m = mouth(caster);
        voice(caster, level, m, end, r1, style, word);
        ripples(level, m, end, rings, spread);
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
            sayTo(caster, level, ts.isEmpty() ? m.add(caster.getLookAngle().scale(15)) : Combat.center(ts.get(0)), 1.1f, V_ORDER, W_STOP, 6, 1);
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
                body(level, t, B_TREMBLE, 10);
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
            if (!caster.isAlive() || !t.isAlive() || !SubCommon.held(caster, age, 30) || age > 46 || from.distanceTo(caster.position()) > 4) {
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
            ripples(level, m, Combat.center(ts.get(0)), 7, 1);
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
                if (!t.isAlive()) {
                    // Finishing: pressed flat in a blink, the body is a pool of blood.
                    Blood.mark(t, Blood.SQUASH, 1.5f, caster);
                    body(level, t, B_PAPER, 40);
                    Tasks.later(2, () -> body(level, t, B_GONE, 40));
                    DbCommon.screen(level, Combat.center(t), 42);
                    SubCommon.sound(level, t.position(), SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
                    SubCommon.sound(level, t.position(), SoundEvents.SLIME_SQUISH, 1.4f, 0.6f);
                    return;
                }
                int down = wordStun(caster, t, 26);
                // A sheet of paper in the hole until the stun ends.
                body(level, t, B_PAPER, down);
                Stun.apply(t, down, true);
                // Flying (the combat flight, a soul in the sky): the weight drives them all the way down into the
                // ground, like every move that slams in the air; the dent opens where they land.
                double above = SubCommon.aboveGround(level, t.position(), 64);
                Vec3 top = t.position();
                Vec3 floor = above < 64 ? top.subtract(0, above, 0) : top;
                int fall = above > 2 && above < 64 ? Math.min(10, 3 + (int) (above / 6)) : 0;
                if (fall > 0) {
                    Freeze.hold(t, down + fall, a -> top.lerp(floor, Math.min(1, a / (double) fall)));
                } else {
                    Freeze.hold(t, down);
                }
                t.setDeltaMovement(0, -1, 0);
                t.hurtMarked = true;
                Vec3 fw = SubCommon.flat(t.getLookAngle());
                Tasks.later(fall, () -> {
                    Vec3 at = fall > 0 ? floor : SubCommon.ground(level, t.position());
                    // the ground gives way in the shape of the body: longer along it, the arms' dents at the sides
                    JjCommon.crater(level, at.add(fw.scale(-0.3)), 1.4, 1.0, 5);
                    JjCommon.crater(level, at.add(fw.scale(0.5)), 1.0, 0.8, 3);
                });
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
            sayTo(caster, level, ts.isEmpty() ? m.add(caster.getLookAngle().scale(8)) : Combat.center(ts.get(0)), 1.3f, V_SLEEP, W_SLEEP, 5, 1);
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
            // The approved preview: nobody falls any more, 2 s slow and tired, staggering, then back to normal.
            int ticks = wordStun(caster, t, 40);
            int delay = Math.max(2, (int) (t.distanceTo(caster) / (mega ? 1.2 : 1.1)));
            Tasks.later(delay, () -> {
                if (!t.isAlive()) {
                    return;
                }
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, false));
                t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 2, false, false));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
                body(level, t, B_DROWSY, ticks);
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
        SubCommon.Guard[] guard = {SubCommon.guard(caster)};
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
            if (guard[0].broken() || !caster.isAlive()) {
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
                ripples(level, m, m.add(dir.scale(len)), 9, 1.6f);
                DbCommon.screen(level, m.add(dir.scale(2)), 33);
                SubCommon.sound(level, m, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
                SubCommon.sound(level, m, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
                if (empty) {
                    THROAT.computeIfAbsent(caster.getUUID(), k -> new float[] {0, level.getGameTime()})[0] = 95;
                } else {
                    throat(caster);
                    burst(caster, level);
                    // The burst silences him (an interrupt): the shout already out still flies and throws them.
                    guard[0] = SubCommon.guard(caster);
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
                // looking up at someone above him (decided before the hit: the body may be dead after it)
                boolean above = caster.getXRot() < -20 && Combat.center(e).y > caster.getEyeY() + 1.5;
                Combat.hurtMagic(caster, e, 30);
                body(level, e, B_TREMBLE, 8);
                DbCommon.hit(level, Combat.center(e), 1.4f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
                if (!e.isAlive() && above) {
                    // Finishing someone above: up into the sky, and a star twinkles where they went.
                    fling(level, e, new Vec3(0, 34, 0), 2, 18, true, () -> {
                        body(level, e, B_GONE, 40);
                        SubNet.send(level, SubNet.INU_STAR, 0, Combat.center(e), Vec3.ZERO, 1, 0, 0);
                    });
                } else if (!e.isAlive()) {
                    // Finishing someone in front: straight and fast to the horizon, through every block in the way.
                    fling(level, e, flat.scale(46).add(0, 4.5, 0), 5, 18, true, () -> body(level, e, B_GONE, 40));
                } else {
                    // MUCH further (~30-40 blocks), through whatever is in the way.
                    fling(level, e, flat.scale(22).add(0, 10, 0), 14, 40, false, null);
                }
            }
            return front >= len;
        });
        return true;
    }

    // ---------- thrown through the world ----------

    // Thrown along v (blocks/s) under gravity g (blocks/s^2), breaking every block the body touches (slower for each),
    // until it lands (alive) or `most` ticks pass. A dead body (a finisher) is carried along the same way and never lands.
    private static void fling(ServerLevel level, LivingEntity e, Vec3 v0, double g, int most, boolean dead, Runnable atEnd) {
        Vec3[] at = {e.position()};
        Vec3[] v = {v0.scale(1 / 20.0)};
        double gt = g / 400.0;
        double mid = e.getBbHeight() * 0.55;
        boolean breaks = BlockRules.canBreak(level);
        if (!dead) {
            Freeze.hold(e, most + 2, a -> at[0]);
            Stun.apply(e, most + 6, true);
        }
        Tasks.add(age -> {
            if (e.isRemoved() || (!dead && !e.isAlive())) {
                Freeze.free(e);
                return true;
            }
            v[0] = v[0].add(0, -gt, 0);
            Vec3 next = at[0].add(v[0]);
            boolean stop = false;
            if (breaks) {
                int n = plow(level, at[0].add(0, mid, 0), next.add(0, mid, 0), 0.95);
                if (n > 0) {
                    v[0] = v[0].scale(Math.pow(dead ? 0.997 : 0.993, n));
                }
            } else if (SubCommon.solid(level, next.add(0, mid, 0))) {
                // No block destruction in this world: the wall stops them.
                next = at[0];
                stop = true;
            }
            if (!dead && v[0].y < 0 && SubCommon.solid(level, next.add(0, -0.05, 0))) {
                next = new Vec3(next.x, Math.floor(next.y - 0.05) + 1, next.z);
                stop = true;
            }
            at[0] = next;
            if (dead) {
                e.setPos(next);
                e.setDeltaMovement(v[0]);
                e.hurtMarked = true;
            }
            if (stop || age >= most) {
                if (!dead) {
                    Freeze.free(e);
                    e.setDeltaMovement(v[0].x * 0.3, 0, v[0].z * 0.3);
                    e.hurtMarked = true;
                }
                if (atEnd != null) {
                    atEnd.run();
                }
                return true;
            }
            return false;
        });
    }

    // Breaks the blocks a body (a ball of radius r) touches from a to b; at most 24 a tick. Returns how many.
    private static int plow(ServerLevel level, Vec3 a, Vec3 b, double r) {
        int n = 0;
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.5));
        java.util.Set<BlockPos> done = new java.util.HashSet<>();
        for (int s = 1; s <= steps && n < 24; s++) {
            Vec3 p = a.lerp(b, s / (double) steps);
            int r1 = (int) Math.ceil(r);
            for (int x = -r1; x <= r1; x++) {
                for (int y = -r1; y <= r1; y++) {
                    for (int z = -r1; z <= r1; z++) {
                        BlockPos bp = BlockPos.containing(p.x + x, p.y + y, p.z + z);
                        if (!done.add(bp) || Vec3.atCenterOf(bp).distanceToSqr(p) > r * r || level.getBlockState(bp).isAir()) {
                            continue;
                        }
                        JjCommon.breakAt(level, bp, p, SubCommon.RANDOM.nextInt(3) == 0);
                        if (level.getBlockState(bp).isAir() && ++n >= 24) {
                            return n;
                        }
                    }
                }
            }
        }
        return n;
    }

    // ---------- Shift+V: Explode (new) ----------

    // The heaviest word: the body swells from inside and bursts. Very high damage and stun; finishing, the body is gone in
    // the burst. It takes the whole throat: 6 s silent after it, coughing blood.
    private static boolean explode(LivingEntity caster, ServerLevel level) {
        boolean mega = SubCommon.hasState(caster, MEGA);
        List<LivingEntity> ts = takers(caster, level, 15, mega);
        if (ts.isEmpty()) {
            SubCommon.warn(caster, "inumaki.no_target");
            return false;
        }
        SubCommon.pose(caster, "jj.shout");
        SubCommon.busy(caster, 16);
        if (mega) {
            megaVoice(caster, level, V_EXPLODE, W_EXPLODE);
        } else {
            sayTo(caster, level, Combat.center(ts.get(0)), 1.4f, V_EXPLODE, W_EXPLODE, 8, 1.3f);
        }
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.8f);
        int swell = 11;
        for (LivingEntity t : ts) {
            int delay = Math.max(1, (int) (t.distanceTo(caster) / 2.75));
            Tasks.later(delay, () -> {
                if (!t.isAlive()) {
                    return;
                }
                // swells from inside, shaking (the body's word first, like the paper of "Esmague")
                body(level, t, B_SWELL, swell);
                Tasks.later(1, () -> body(level, t, B_TREMBLE, swell - 1));
                Freeze.hold(t, swell + 2);
                Stun.apply(t, swell + 4, true);
                SubCommon.sound(level, t.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 1.4f, 0.6f);
                Tasks.later(swell, () -> {
                    if (!t.isAlive()) {
                        return;
                    }
                    Combat.hurtMagic(caster, t, 45);
                    Blood.mark(t, Blood.BURST, 1.8f, caster);
                    DbCommon.screen(level, Combat.center(t), 42);
                    SubCommon.sound(level, t.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.2f);
                    SubCommon.sound(level, t.position(), SoundEvents.SLIME_SQUISH, 1.4f, 0.5f);
                    if (!t.isAlive()) {
                        body(level, t, B_GONE, 40);
                    } else {
                        body(level, t, 0, 0);
                        int down = wordStun(caster, t, 50);
                        Freeze.hold(t, down);
                        Stun.apply(t, down, true);
                    }
                });
            });
        }
        // The whole throat: silent for 6 s, coughing blood.
        throat(caster);
        burst(caster, level, 120);
        return true;
    }

    static void clear() {
        THROAT.clear();
    }
}
