package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// V, Tentáculos de Mãos (the ult; preview v1, "humilhou meu amigo"). Five tentacles made of hands come out of his back
// (0.45 to 0.8 s), sweep around him, strike a second time (4.2 to 6 s), writhe and go back (7.45 s); 9.2 s in all.
// Same timeline and shapes as the preview (TentacleSpine); the clients draw the hands (client/fx/shig/TentacleFx).
// Whatever a tentacle touches decays: the ground (a seed every 3.2 blocks, not near him), the buildings it hits (a
// structure seed, at most MAX_STRUCT), and whoever it touches (the body dries and crumbles, 90 damage).
// He stands still meanwhile (looking around is free); a real stun cuts it short.
public final class Tentaculos {
    private static final int LENGTH = Math.round(TentacleSpine.CAST_LEN * 20);
    private static final float DAMAGE = 90, BODY_SPEED = 2.4f;
    private static final int MAX_STRUCT = 8, MAX_GROUND = 60;

    private Tentaculos() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        Vec3 feet = caster.position();
        Vec3 face = Combat.flatLook(caster);
        // the preview's frame: +z = where he faces; Minecraft's yaw for (−sin, cos)
        float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
        SubCommon.busy(caster, LENGTH);
        SubCommon.pose(caster, "shig.tentaculos");
        SubNet.send(level, Shigaraki.TENTACLES, caster.getId(), feet, face, 0, 0, 0);
        SubCommon.sound(level, feet, SoundEvents.WARDEN_EMERGE, 1.4f, 0.7f);
        Runnable release = Freeze.holdLookFree(caster, LENGTH);
        List<Vec3> ground = new ArrayList<>(), struct = new ArrayList<>();
        Set<Integer> hit = new HashSet<>();
        float[][] S = new float[TentacleSpine.KK][3];
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts) {
                release.run();
                SubNet.send(level, Shigaraki.TENTACLES, caster.getId(), feet, face, 1, 0, 0);
                return true;
            }
            float t = age / 20f;
            if (age == 9 || age == 16) {
                SubCommon.sound(level, feet.add(0, 1.5, 0), SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.2f, 0.5f);
            }
            if (age == 84 || age == 100) {
                SubCommon.sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.4f);
            }
            for (TentacleSpine.Def d : TentacleSpine.DEFS) {
                float g = d.grow(t);
                if (g <= 0) {
                    continue;
                }
                TentacleSpine.spine(d, t, S);
                for (int k = 0; k < TentacleSpine.KK; k++) {
                    float dd = k / (float) (TentacleSpine.KK - 1) * d.L;
                    if (dd > g * d.L - 1) {
                        break;
                    }
                    float R = d.radius(dd) + 0.4f;
                    double[] w = TentacleSpine.toWorld(S[k], feet.x, feet.y, feet.z, yaw);
                    Vec3 p = new Vec3(w[0], w[1], w[2]);
                    // the ground it lies on (the preview: under 0.25 above the ground, not within 3.5 of him)
                    if (S[k][1] - R < 0.25f && Math.hypot(S[k][0], S[k][2]) > 3.5 && ground.size() < MAX_GROUND && far(ground, p, 3.2)) {
                        Vec3 at = SubCommon.ground(level, new Vec3(p.x, feet.y + 2, p.z));
                        ground.add(p);
                        Decay.seed(level, caster, Shigaraki.groundSeed(at), 5.5f, 4.5f + SubCommon.RANDOM.nextFloat() * 3.5f, Decay.GROUND, at.y, 2.0);
                    }
                    // a building: the first solid block above the ground within its radius
                    if (struct.size() < MAX_STRUCT && S[k][1] > 0.6f && (k & 1) == 0) {
                        BlockPos b = solidNear(level, p, R, feet.y);
                        if (b != null) {
                            Vec3 c = Vec3.atCenterOf(b);
                            if (far(struct, c, 4)) {
                                struct.add(c);
                                Decay.seed(level, caster, c, 5f, 20f, Decay.STRUCTURE, feet.y, 2.0);
                                SubCommon.sound(level, c, SoundEvents.GRAVEL_BREAK, 1.6f, 0.5f);
                            }
                        }
                    }
                    for (LivingEntity e : Combat.inRadius(level, p, R + 1.6, caster)) {
                        if (e.getBoundingBox().getCenter().distanceTo(p) < R + 0.8 + e.getBbWidth() * 0.5 && hit.add(e.getId())) {
                            Vec3 touch = e.getBoundingBox().getCenter().add(p.subtract(e.getBoundingBox().getCenter()).scale(0.5));
                            SubCommon.sound(level, touch, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.5f);
                            Decay.body(caster, e, touch, BODY_SPEED, DAMAGE, 40);
                        }
                    }
                }
            }
            if (age >= LENGTH) {
                release.run();
                return true;
            }
            return false;
        });
        return true;
    }

    private static boolean far(List<Vec3> list, Vec3 p, double gap) {
        for (Vec3 q : list) {
            if (q.distanceToSqr(p) < gap * gap) {
                return false;
            }
        }
        return true;
    }

    // A block above his ground level the tentacle (radius R at p) is touching: p itself and R along each axis.
    private static BlockPos solidNear(ServerLevel level, Vec3 p, float R, double groundY) {
        int gy = Mth.floor(groundY + 0.01);
        double[][] offs = {{0, 0, 0}, {R, 0, 0}, {-R, 0, 0}, {0, R, 0}, {0, -R, 0}, {0, 0, R}, {0, 0, -R}};
        for (double[] o : offs) {
            BlockPos b = BlockPos.containing(p.x + o[0], p.y + o[1], p.z + o[2]);
            if (b.getY() < gy) {
                continue;
            }
            if (Decay.decays(level, b, level.getBlockState(b))) {
                return b;
            }
        }
        return null;
    }
}
