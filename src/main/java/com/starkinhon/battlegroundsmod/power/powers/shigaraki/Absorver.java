package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// B, Absorver (All For One; preview v5, "PERFEITO, uma das melhores", 2026-11-11). He sees everyone's power (an aura on
// each: Fogo orange, Raio yellow, Gelo blue), dashes to the closest one within 18 blocks, grabs it at 0.65 s and pulls
// its power through a twisted cord until 1.85 s; at 1.8 s it is his for 15 s (his own aura and eyes take its colour) and
// the victim wilts grey on its knees (weak and slow while he has it). B again uses it (every 3 s):
// Fogo: a ball flies to the target and bursts in a dome that throws and chars; Raio: a bolt at once, stuns 2.4 s;
// Gelo: a row of spikes to the target, frozen 3 s.
// Which power: the victim's soul (by its colour) or, for a mob without one, what the mob is.
public final class Absorver {
    static final double RANGE = 18, USE_RANGE = 32;
    private static final int LOOK = 8, DASH_END = 13, STEAL = 36, LENGTH = 50, KEEP = 300, USE_COOLDOWN = 60;

    private static final class State {
        int element = -1;
        long until;
        long nextUse;
        long busyUntil;
    }

    private static final Map<LivingEntity, State> STATE = new WeakHashMap<>();

    private Absorver() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        State s = STATE.computeIfAbsent(caster, c -> new State());
        long now = level.getGameTime();
        if (now < s.busyUntil) {
            return false;
        }
        if (s.element >= 0 && now < s.until) {
            if (now < s.nextUse) {
                return false;
            }
            s.nextUse = now + USE_COOLDOWN;
            Usar.use(caster, level, s.element);
            return true;
        }
        LivingEntity victim = closest(caster, level);
        SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), Vec3.ZERO, 0, LENGTH, 0);
        if (victim == null) {
            SubCommon.warn(caster, "shigaraki.b_miss");
            return false;
        }
        int element = elementOf(victim);
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        s.busyUntil = now + LENGTH;
        SubCommon.busy(caster, DASH_END + 4);
        SubCommon.pose(caster, "shig.absorver");
        Vec3 from = caster.position();
        SubCommon.sound(level, from, SoundEvents.ENDER_EYE_DEATH, 1f, 0.5f);
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts || !victim.isAlive()) {
                s.busyUntil = 0;
                SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), Vec3.ZERO, 3, 0, 0);
                return true;
            }
            Vec3 dir = Shigaraki.flatTo(caster, victim.position());
            if (age >= LOOK && age < DASH_END) {
                // the dash (preview: 0.4 to 0.65 s, ease out) to 1.9 blocks short
                Vec3 goal = new Vec3(victim.getX(), caster.getY(), victim.getZ()).subtract(dir.scale(1.9));
                float k0 = ease((age - LOOK) / (float) (DASH_END - LOOK)), k = ease((age + 1 - LOOK) / (float) (DASH_END - LOOK));
                // the curve's own step (a player's server position is a tick behind their client: want - position overshot)
                Vec3 step = goal.subtract(from).scale(k - k0);
                caster.setDeltaMovement(step.x, Math.min(caster.getDeltaMovement().y, 0.05), step.z);
                caster.hurtMarked = true;
            }
            if (age == DASH_END) {
                caster.setDeltaMovement(Vec3.ZERO);
                caster.hurtMarked = true;
                Shigaraki.landOn(caster, new Vec3(victim.getX(), caster.getY(), victim.getZ()).subtract(dir.scale(1.9)));
                if (victim.distanceTo(caster) > 4.5) {
                    s.busyUntil = 0;
                    SubCommon.warn(caster, "shigaraki.b_miss");
                    SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), Vec3.ZERO, 3, 0, 0);
                    return true;
                }
                SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), dir, 1, victim.getId(), element);
                SubCommon.sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
                Stun.apply(victim, STEAL - DASH_END + 20);
                Freeze.hold(victim, STEAL - DASH_END + 2);
            }
            if (age > DASH_END && age < STEAL && age % 6 == 0) {
                SubCommon.sound(level, victim.position(), SoundEvents.BEACON_AMBIENT, 1.2f, 1.6f);
            }
            if (age == STEAL) {
                s.element = element;
                s.until = level.getGameTime() + KEEP;
                s.nextUse = 0;
                SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), dir, 2, KEEP, element);
                SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
                // the victim wilts: weak, slow, its power gone grey for as long as he keeps it
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, KEEP, 2, false, false, true));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KEEP, 1, false, false, true));
                SubNet.send(level, Shigaraki.ABSORB, victim.getId(), victim.position(), dir, 4, KEEP, element);
                Combat.hurt(caster, victim, 12);
            }
            if (age >= LENGTH) {
                return true;
            }
            return false;
        });
        Tasks.later(KEEP + STEAL, () -> {
            if (s.element >= 0 && level.getGameTime() >= s.until) {
                s.element = -1;
                SubNet.send(level, Shigaraki.ABSORB, caster.getId(), caster.position(), Vec3.ZERO, 3, 0, 0);
            }
        });
        return true;
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    // The power B would steal now (or -1), for the HUD and the bosses.
    public static int stolen(LivingEntity caster) {
        State s = STATE.get(caster);
        return s != null && s.element >= 0 && caster.level().getGameTime() < s.until ? s.element : -1;
    }

    private static LivingEntity closest(LivingEntity caster, ServerLevel level) {
        LivingEntity aimed = Combat.raycastTarget(caster, RANGE);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bd = RANGE;
        for (LivingEntity e : Combat.inRadius(level, caster.position(), RANGE, caster)) {
            double d = e.distanceTo(caster);
            if (d < bd && caster.hasLineOfSight(e)) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // Fogo, Raio or Gelo: by the soul's colour (warm = fire, yellow = lightning, cold = ice), else by the mob.
    static int elementOf(LivingEntity victim) {
        if (victim.hasData(ModAttachments.POWER)) {
            PowerData data = victim.getData(ModAttachments.POWER);
            Power p = data.power == null ? null : Powers.get(data.power);
            if (p != null) {
                int c = p.color();
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                if (b > r && b >= g * 0.8) {
                    return Shigaraki.ICE;
                }
                if (r > 150 && g > 150 && b < 120) {
                    return Shigaraki.LIGHTNING;
                }
                return Shigaraki.FIRE;
            }
        }
        EntityType<?> t = victim.getType();
        if (t == EntityType.BLAZE || t == EntityType.MAGMA_CUBE || t == EntityType.GHAST || t == EntityType.STRIDER || t == EntityType.WITHER_SKELETON
                || t == EntityType.PIGLIN || t == EntityType.HOGLIN || t == EntityType.ZOMBIFIED_PIGLIN) {
            return Shigaraki.FIRE;
        }
        if (t == EntityType.STRAY || t == EntityType.SNOW_GOLEM || t == EntityType.POLAR_BEAR || t == EntityType.DROWNED || t == EntityType.GUARDIAN
                || t == EntityType.ELDER_GUARDIAN || t == EntityType.SQUID || t == EntityType.GLOW_SQUID) {
            return Shigaraki.ICE;
        }
        if (t == EntityType.CREEPER || t == EntityType.BEE || t == EntityType.PHANTOM || t == EntityType.VEX || t == EntityType.ALLAY) {
            return Shigaraki.LIGHTNING;
        }
        return Math.floorMod(victim.getUUID().hashCode(), 3);
    }
}
