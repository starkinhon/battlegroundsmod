package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift+Z: the cuts born on the opponent, as in the 3D preview Stark approved (forja/sukuna-desmantelar, "Shift+Z"). He
// throws nothing: he opens his hand towards the target and the cuts appear right on its body, one after the other, in
// every direction: straight black strokes with a white outline thinning to needle tips, of very different sizes (long
// hairlines, medium, short and fat), some bending into a second stroke. Each one is a blow that jolts the target; the
// last throws it far. Drawn by client/fx/SukunaCutsRenderer (the bars); the layout is the preview's, stroke for stroke.
public final class DesmantelarShift {
    // Stark 2026-11-09 (Sukuna "low scale" next to Gojo): twice the reach (24) and more per cut (3.2).
    private static final double RANGE = 48;
    // Ticks: the first cut; the spread of the others; strokes; how long he holds the hand out.
    private static final int START = 5, COUNT = 20, BUSY = 34;
    private static final double SPAN = 14 * 1.8;
    private static final float DAMAGE = 5f, LAST_DAMAGE = 14;
    private static final int HITSTUN = 8, LAST_STUN = 20;
    private static final float WEIGHT = 0.25f;

    // One stroke: its direction in the target's frame (x to the side, y up, z away from Sukuna), size, where on the body
    // it is born, and the tick it lands.
    private record Stroke(int k, Vec3 wdir, double len, double thick, double ox, double oy, double oz, double shift, float skew, float rough,
            int tick, boolean last, Stroke bend) {
    }

    private static final List<Stroke> STROKES = layout();

    private DesmantelarShift() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        LivingEntity target = target(caster);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        data.busyTicks = Math.max(data.busyTicks, BUSY);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "soco.forte_golpe", false));
        Fx.sound(level, caster.position(), SoundEvents.TRIDENT_RETURN, 1f, 0.55f);
        int seed = caster.getRandom().nextInt(0x7FFF);
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts) {
                return true;
            }
            caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, 3, 0, false, false, false));
            if (!target.isAlive() || target.isRemoved() || target.level() != caster.level() || target.distanceTo(caster) > RANGE + 12) {
                return age >= BUSY;
            }
            for (Stroke s : STROKES) {
                if (s.tick() == age) {
                    land(caster, target, s, seed);
                }
            }
            return age >= BUSY;
        });
        return true;
    }

    private static void land(LivingEntity caster, LivingEntity target, Stroke s, int seed) {
        ServerLevel level = Combat.level(caster);
        double body = target.getBbHeight() / 1.8, wide = Math.max(0.6, target.getBbWidth() / 0.6);
        Vec3 away = target.position().subtract(caster.position());
        away = new Vec3(away.x, 0, away.z).lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : new Vec3(away.x, 0, away.z).normalize();
        Vec3 side = new Vec3(away.z, 0, -away.x);
        Vec3 dir = frame(s.wdir(), side, away);
        double len = s.len() * Math.max(1, body * 0.8);
        Vec3 c = target.position().add(side.scale(s.ox() * wide)).add(0, s.oy() * body, 0).add(away.scale(s.oz() * wide)).add(dir.scale(s.shift()));
        send(caster, c, dir, len, s.thick(), s.shift(), s.skew(), s.rough(), 0, 1, seed + s.k(), s.k());
        // each stroke cuts the world it runs through too (SukunaCarve), a block thin
        it.unimi.dsi.fastutil.longs.LongOpenHashSet carved = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        SukunaCarve.line(level, caster, c, dir, len, carved);
        if (s.bend() != null) {
            Stroke b = s.bend();
            Vec3 bd = frame(b.wdir(), side, away);
            double bl = b.len() * Math.max(1, body * 0.8);
            Vec3 bc = c.add(dir.scale(len * (b.oz() - 0.5))).add(bd.scale(bl * 0.5));
            send(caster, bc, bd, bl, b.thick(), 0, 1, s.rough(), 0.5f, 1, seed + b.k(), b.k());
            SukunaCarve.line(level, caster, bc, bd, bl, carved);
        }
        Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.3f + caster.getRandom().nextFloat() * 0.4f);
        if (!Combat.isTarget(target, caster)) {
            return;
        }
        ModelBar.landed(caster, target, WEIGHT);
        if (s.last()) {
            if (Combat.hurt(caster, target, LAST_DAMAGE)) {
                Combat.push(target, away, 1.2, 0.5);
                Stun.apply(target, LAST_STUN);
                Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.7f);
            }
        } else if (Combat.hurt(caster, target, DAMAGE)) {
            Vec3 v = target.getDeltaMovement();
            target.setDeltaMovement(v.x * 0.3 + away.x * 0.06, Math.max(v.y, 0.04), v.z * 0.3 + away.z * 0.06);
            target.hurtMarked = true;
            Stun.apply(target, HITSTUN);
        }
    }

    private static void send(LivingEntity caster, Vec3 center, Vec3 dir, double len, double thick, double shift, double skew, double rough, float s0,
            float s1, int seed, int k) {
        boolean star = hash(k * 11.3) < 0.6;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaBar(center, dir, (float) len, (float) thick, (float) shift,
                (float) skew, (float) rough, s0, s1, seed, star));
    }

    // The preview's frame (x side, y up, z away) in the world.
    private static Vec3 frame(Vec3 w, Vec3 side, Vec3 away) {
        return side.scale(w.x).add(0, w.y, 0).add(away.scale(w.z)).normalize();
    }

    // The preview's strokes: directions on a golden spiral, three families of size, a bend on about a third of them.
    private static List<Stroke> layout() {
        List<Stroke> out = new ArrayList<>();
        for (int k = 0; k < COUNT; k++) {
            double fz = 1 - 2 * (k + 0.5) / COUNT, fr = Math.sqrt(1 - fz * fz), fp = k * 2.39996 + 0.3 + (hash(k * 4.4) - 0.5) * 0.6;
            Vec3 wdir = new Vec3(fr * Math.cos(fp), fz * 0.75, fr * Math.sin(fp)).normalize();
            double c = hash(k * 3.1), h = hash(k * 5.7), len, thick;
            if (c < 0.25) {
                len = 7 + 3 * h;
                thick = 0.05 + 0.04 * hash(k * 8.1);
            } else if (c < 0.75) {
                len = 3.2 + 3.3 * h;
                thick = 0.13 + 0.13 * hash(k * 8.1);
            } else {
                len = 1.4 + 1.6 * h;
                thick = 0.24 + 0.14 * hash(k * 8.1);
            }
            boolean last = k == COUNT - 1;
            if (last) {
                len = 6.5;
                thick = 0.3;
            }
            double t = last ? START + SPAN : START + SPAN * k / COUNT + (hash(k * 9.1) - 0.5) * 1.6;
            Stroke bend = null;
            if (!last && hash(k * 15.9) < 0.35) {
                // a second stroke from the thick part, turned about a random axis (oz carries where along the parent)
                double f = 0.6 + 0.25 * hash(k * 16.1);
                Vec3 ax = new Vec3(hash(k * 17.3) - 0.5, hash(k * 18.1) - 0.5, hash(k * 19.7) - 0.5).cross(wdir);
                ax = ax.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : ax.normalize();
                double ang = (hash(k * 20.3) < 0.5 ? 1 : -1) * (0.6 + 0.5 * hash(k * 21.1));
                bend = new Stroke(k + 100, rotate(wdir, ax, ang), len * (0.3 + 0.25 * hash(k * 22.7)), thick * 0.9, 0, 0, f, 0, 1, 0,
                        (int) Math.round(t), false, null);
            }
            out.add(new Stroke(k, wdir, len, thick, (hash(k * 7.3) - 0.5) * 1.6, 0.2 + hash(k * 1.9) * 1.8, (hash(k * 6.6) - 0.5) * 1.0,
                    (hash(k * 12.7) - 0.5) * len * 0.45, (float) (0.65 + 0.9 * hash(k * 13.3)), (float) (0.3 + 0.7 * hash(k * 14.1)),
                    Math.max(1, (int) Math.round(t)), last, bend));
        }
        return out;
    }

    // Rodrigues: v turned by ang about the unit axis.
    private static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        double cos = Math.cos(ang), sin = Math.sin(ang);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1 - cos))).normalize();
    }

    // In the crosshair, else the one he is fighting if roughly in front of him.
    private static LivingEntity target(LivingEntity caster) {
        LivingEntity target = Combat.raycastTarget(caster, RANGE);
        if (target != null) {
            return target;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        LivingEntity other = null;
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought) {
            other = fought;
        } else if (caster instanceof Mob mob) {
            other = mob.getTarget();
        }
        if (other == null || !Combat.isTarget(other, caster) || other.distanceTo(caster) > RANGE) {
            return null;
        }
        Vec3 to = Combat.center(other).subtract(caster.getEyePosition()).normalize();
        return to.dot(caster.getLookAngle()) > 0.5 ? other : null;
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }
}
