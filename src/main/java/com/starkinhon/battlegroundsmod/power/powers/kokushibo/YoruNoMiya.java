package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Moon Breathing, 1st Form: Yoru no Miya (Dark Moon, Evening Palace). A quick draw (espada.sacar_kokushibo) that opens a
// giant crescent slash in front of him, pale cream outside and ember inside, with chains of moons popping out along
// it; everything caught in the arc (a big disc leaning back in front of him) is cut.
// The look is the Visualizer page Stark approved (forja/kokushibo-formas/yoru-no-miya.html), drawn by
// client/fx/MoonSlashRenderer with the souls_moon shader. Its shape lives in the page's 2D "p" space, set up on a plane
// leaning back in front of him: p = C at the plane's centre, D blocks ahead of his eyes, SCALE blocks per unit.
public final class YoruNoMiya {
    // The page's slash (keep in step with souls_moon.fsh and MoonSlashRenderer).
    public static final double CX = 0.06, CY = -0.04, R = 0.60;
    public static final double SCALE = 7.0;
    // Where the plane's centre sits: AHEAD blocks in front of his eyes, RISE above them.
    public static final double AHEAD = 3.6;
    public static final double RISE = 0.7;
    // How far the slash leans back from standing up (degrees).
    public static final double TILT = 55;
    // The cut lands as the point of the slash goes round (the page's head reaches the arc's middle at ~0.2 s).
    private static final int HIT_DELAY = 4;
    private static final float DAMAGE = 14f;
    private static final double DEPTH = 3.0;
    private static final double VIEW_DISTANCE = 128;

    private YoruNoMiya() {
    }

    // The plane's centre and its axes (normal, right, up), the same on both sides. As in Stark's montage: the page's
    // slash faces forward (the same shape he sees on the page) but leans back TILT degrees, so it wraps round him: it
    // rises from beside his left shoulder, passes high above and behind his head and comes down to the ground ahead of
    // him on his right. It follows only his turning, not his pitch, so looking down never buries it.
    public static Vec3[] frame(Vec3 eye, Vec3 look) {
        Vec3 l = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();
        double tilt = Math.toRadians(TILT);
        Vec3 up = new Vec3(0, Math.cos(tilt), 0).add(flat.scale(Math.sin(tilt)));
        Vec3 n = right.cross(up).normalize();
        Vec3 centre = eye.add(0, RISE, 0).add(flat.scale(AHEAD));
        return new Vec3[] {centre, n, right, up};
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, SwordArts.QUICK_DRAW);
        SubCommon.busy(caster, 8);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        int seed = 1 + SubCommon.RANDOM.nextInt(999_999);
        ModPayloads.SwordFx payload = new ModPayloads.SwordFx(SwordArts.FX_MOON, caster.getId(), eye, look, seed, 0);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(eye) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        SubCommon.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        Tasks.later(HIT_DELAY, () -> cut(caster, level, eye, look));
        return true;
    }

    private static void cut(LivingEntity caster, ServerLevel level, Vec3 eye, Vec3 look) {
        if (!caster.isAlive()) {
            return;
        }
        Vec3[] fr = frame(eye, look);
        Vec3 centre = fr[0];
        SubCommon.sound(level, centre, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.2f, 1.6f);
        SubCommon.sound(level, centre, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.7f);
        double reach = (R * 1.07 + 0.2) * SCALE;
        for (LivingEntity target : Combat.inRadius(level, centre, reach + DEPTH, caster)) {
            Vec3 rel = Combat.center(target).subtract(centre);
            double depth = rel.dot(fr[1]);
            double px = rel.dot(fr[2]) / SCALE, py = rel.dot(fr[3]) / SCALE;
            if (Math.abs(depth) > DEPTH || px * px + py * py > (reach / SCALE) * (reach / SCALE)) {
                continue;
            }
            if (Combat.hurt(caster, target, DAMAGE)) {
                Combat.pushFrom(target, eye, 1.1, 0.25);
            }
        }
    }
}
