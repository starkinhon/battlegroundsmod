package com.starkinhon.soulsjjk.power.powers.sukuna;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.combat.Stun;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// Sukuna's thrown technique crescents on the server (the Z and its chant, power/powers/sukuna/Desmantelar): the cut flies
// straight from the hand, goes through whoever is in its plane and breaks a little past its reach (drawn by
// client/fx/SukunaCutsRenderer). The blade is wide across its span and thin across its face, so the hit test is the
// crescent's own shape: within its span, within a block of its plane, between where its front was and is. The space
// between the hand and the first tick is covered too, so someone right against him is cut.
public final class SukunaCuts {
    // The crescent's tips are this many units from its middle (the preview's shape).
    public static final double TIP = 1.077;
    // The renderer's flags (client/fx/SukunaCutsRenderer has the same values): a groove on the ground, the World Cutting
    // Slash's look.
    public static final int GROOVE = 1, WORLD = 2;

    private SukunaCuts() {
    }

    // One throw. unit: the crescent's unit in blocks (a Z standing is 1.75); reach: how far its front flies; knock: how
    // hard it throws whoever it cuts (blocks a tick, horizontal and up); stun: ticks of stun on a hit; flags: the
    // renderer's (GROOVE for a standing cut on the ground).
    public record Throw(Vec3 origin, Vec3 dir, Vec3 across, double unit, double speed, double reach, float damage, double knock, double knockUp,
            int stun, int flags, float modelWeight, double starSize, int carve) {
    }

    public static void fire(LivingEntity caster, Throw t) {
        ServerLevel level = Combat.level(caster);
        Vec3 dir = t.dir().normalize();
        Vec3 across = t.across().subtract(dir.scale(t.across().dot(dir)));
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(-dir.z, 0, dir.x).normalize() : across.normalize();
        Vec3 normal = across.cross(dir).normalize();
        int seed = caster.getRandom().nextInt(0xFFFF);
        double ground = caster.getY();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaSlash(t.origin(), dir, across, (float) t.unit(),
                (float) t.speed(), (float) t.reach(), seed, t.flags(), (float) ground));
        Fx.sound(level, t.origin(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.75f + caster.getRandom().nextFloat() * 0.2f);
        Fx.sound(level, t.origin(), SoundEvents.TRIDENT_THROW, 0.8f, 1.6f);
        Set<Integer> hit = new HashSet<>();
        double span = TIP * t.unit() * 1.05;
        Vec3 way = dir, side = across, face = normal;
        // the crescent's own width cut out of the world as it flies (SukunaCarve)
        it.unimi.dsi.fastutil.longs.LongOpenHashSet carved = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        double blade = TIP * t.unit(), bow = 0.35 * t.unit();
        Tasks.add(age -> {
            if (!caster.isAlive()) {
                return true;
            }
            double from = age == 0 ? -1.5 : t.speed() * age - 0.6 * t.unit(), upTo = Math.min(t.reach(), t.speed() * (age + 1)) + 0.3 * t.unit();
            SukunaCarve.sweep(level, caster, t.origin(), way, side, blade, bow, age == 0 ? 0 : t.speed() * age, Math.min(t.reach(), t.speed() * (age + 1)),
                    t.carve(), carved);
            Vec3 a = t.origin().add(way.scale(from)), b = t.origin().add(way.scale(upTo));
            AABB around = new AABB(a, b).inflate(span + 1.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, around, e -> Combat.isTarget(e, caster))) {
                if (hit.contains(e.getId())) {
                    continue;
                }
                Vec3 rel = Combat.center(e).subtract(t.origin());
                double along = rel.dot(way), across1 = Math.abs(rel.dot(side)), off = Math.abs(rel.dot(face));
                double half = e.getBbWidth() / 2 + 0.6, tall = e.getBbHeight() / 2;
                if (along < from - half || along > upTo + half || off > half + Math.abs(face.y) * tall || across1 > span + half + Math.abs(side.y) * tall) {
                    continue;
                }
                hit.add(e.getId());
                strike(caster, e, t, way);
            }
            return t.speed() * (age + 1) >= t.reach();
        });
    }

    private static void strike(LivingEntity caster, LivingEntity target, Throw t, Vec3 way) {
        ServerLevel level = Combat.level(caster);
        ModelBar.landed(caster, target, t.modelWeight());
        if (Combat.hurt(caster, target, t.damage())) {
            if (t.knock() > 0 || t.knockUp() > 0) {
                Combat.push(target, way, t.knock(), t.knockUp());
            }
            if (t.stun() > 0) {
                Stun.apply(target, t.stun());
            }
            Vec3 c = Combat.center(target);
            star(caster, c.subtract(way.scale(0.3)), (float) t.starSize());
            Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.8f + caster.getRandom().nextFloat() * 0.2f);
        }
    }

    public static void star(LivingEntity caster, Vec3 pos, float size) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaStar(pos, size, caster.getRandom().nextInt(0xFFFF)));
    }
}
