package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    // Blocks per page unit: the arc's radius (R) started as the reach of his arm plus the Kokushibo blade (~0.6 + 2.7
    // blocks, 5.5); Stark then asked for everything 1.5x bigger (size, reach, destruction): 8.25.
    public static final double SCALE = 8.25;
    // Where the arc's centre sits: AHEAD blocks in front of his eyes, RISE above them (negative: at his chest).
    public static final double AHEAD = 0.9;
    public static final double RISE = -0.35;
    // How far the slash leans back from standing up (degrees): nearly flat, a horizontal cut.
    public static final double TILT = 82;
    // Turn about where he faces (negative: his right side lower), for a bit of a top-down feel.
    public static final double ROLL = -12;
    // Turn of the arc in its own plane (degrees, positive: from his right toward the front): centres the gap behind him.
    public static final double SPIN = 25;
    // How far up or down the slash follows his look (degrees).
    public static final double MAX_PITCH = 80;
    // The cut lands as the point of the slash goes round (the page's head reaches the arc's middle at ~0.2 s).
    private static final int HIT_DELAY = 4;
    private static final float DAMAGE = 14f;
    private static final double DEPTH = 2.5;
    // The sword trails during the move: a deep gold, since the trail is mixed halfway with white (SwordFx.drawTrail), so it
    // ends up the yellow of the moons.
    public static final int MOON_GOLD = 0xFFA000;
    // The hitbox reaches this many times as far as the drawn slash (Stark: 1.5x).
    private static final double HIT_REACH = 1.5;
    // Black Flash style: the target stands frozen this long after the cut before the blow lands (0.5 s), then stays
    // stunned STUN_AFTER more.
    private static final int IMPACT_DELAY = 10;
    private static final int STUN_AFTER = 10;
    // The blow's knockback: blocks per tick away from him, and up.
    private static final double FLING = 2.2;
    private static final double FLING_UP = 0.55;
    // The crescent cut into blocks: out past the arc by CARVE_OUT of its radius at the fattest, CARVE_ABOVE blocks above
    // the slash's plane and CARVE_BELOW below it.
    // Stark: destroy EVERYTHING in the slash's shape, so a fat moon: from CARVE_IN of the radius inside the arc to CARVE_OUT
    // past it at the middle of the swing, and tall (a whole cliff in its way gets the crescent cut through it).
    private static final double CARVE_IN = 0.45;
    private static final double CARVE_OUT = 0.6;
    private static final double CARVE_ABOVE = 6.0;
    private static final double CARVE_BELOW = 5.0;
    // The arc as on the page (souls_moon.fsh): starts at A0 and turns SPAN.
    private static final double A0 = Math.toRadians(200), SPAN = Math.toRadians(-265);
    private static final double VIEW_DISTANCE = 128;

    private YoruNoMiya() {
    }

    // The plane's centre and its axes (normal, right, up), the same on both sides. Stark: a horizontal cut forward that
    // he and the one hit both see well, sized to the sword (the slash comes off its point): the page's arc is centred
    // on his chest, a little ahead, with its radius the reach of arm plus blade, and lies nearly flat (TILT degrees back
    // from standing, so the part ahead of him rises a little), then the whole plane turns ROLL degrees about where he
    // faces so his right side dips, for a bit of a top-down slash. It follows his turning and, up to MAX_PITCH, his pitch.
    public static Vec3[] frame(Vec3 eye, Vec3 look) {
        Vec3 l = look.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();
        double tilt = Math.toRadians(TILT);
        Vec3 up = new Vec3(0, Math.cos(tilt), 0).add(flat.scale(Math.sin(tilt)));
        // Turn the arc in its own plane so its open gap sits right behind him and it wraps him evenly (Stark: it looked
        // off to one side, its thick end coming at the camera on his right).
        double spin = Math.toRadians(SPIN);
        Vec3 r0 = right;
        right = r0.scale(Math.cos(spin)).add(up.scale(Math.sin(spin)));
        up = up.scale(Math.cos(spin)).subtract(r0.scale(Math.sin(spin)));
        // (turning about "flat" by a positive angle lifts his right side, hence the minus)
        double roll = -Math.toRadians(ROLL);
        right = turn(right, flat, roll);
        up = turn(up, flat, roll);
        // Aimed up and down with his look (Stark: otherwise no one above or below him can be hit): the whole slash,
        // hitbox and carving included, turns about his right-hand side by his pitch, round his eyes.
        Vec3 side = flat.cross(new Vec3(0, 1, 0)).normalize();
        double pitch = Math.asin(Mth.clamp(l.y, -1, 1));
        pitch = Mth.clamp(pitch, -Math.toRadians(MAX_PITCH), Math.toRadians(MAX_PITCH));
        right = turn(right, side, pitch);
        up = turn(up, side, pitch);
        Vec3 n = right.cross(up).normalize();
        Vec3 centre = eye.add(turn(new Vec3(0, RISE, 0).add(flat.scale(AHEAD)), side, pitch));
        return new Vec3[] {centre, n, right, up};
    }

    // v turned by angle a about the unit axis k (Rodrigues).
    private static Vec3 turn(Vec3 v, Vec3 k, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        // The sword's own trails during the move are the moon's gold, not the white of his basic swings (each basic
        // swing tells its own color again when it starts, so those stay as they are).
        ModPayloads.SwordFx gold = new ModPayloads.SwordFx(SwordArts.FX_COLOR | MOON_GOLD << 4, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, gold);
        SubCommon.pose(caster, SwordArts.QUICK_DRAW);
        SubCommon.busy(caster, 8);
        com.starkinhon.battlegroundsmod.boss.BossFalas.firstForm(caster);
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
        SubCommon.sound(level, centre, SoundEvents.GENERIC_EXPLODE.value(), 1.1f, 1.5f);
        if (BlockRules.canBreak(level)) {
            carve(level, fr, caster.position());
        }
        // The hitbox fills the whole shape the slash sweeps (inside of the arc included, the open gap behind him left
        // out) and reaches HIT_REACH times as far as the drawn slash (Stark).
        double reach = (R * 1.07 + 0.2) * SCALE * HIT_REACH;
        for (LivingEntity target : Combat.inRadius(level, centre, reach + DEPTH, caster)) {
            Vec3 rel = Combat.center(target).subtract(centre);
            double depth = rel.dot(fr[1]);
            double px = rel.dot(fr[2]) / SCALE, py = rel.dot(fr[3]) / SCALE;
            if (Math.abs(depth) > DEPTH + target.getBbHeight() * 0.5 || px * px + py * py > (reach / SCALE) * (reach / SCALE)) {
                continue;
            }
            if (px * px + py * py > 0.04 && sweep(px, py) > 1.08) {
                continue;
            }
            strike(caster, target, level, eye);
        }
    }

    // How far round the swing (0 at its start, 1 at its end, past 1 in the gap) the page point (x, y) from the arc's centre is.
    private static double sweep(double x, double y) {
        double th = Math.atan2(y, x);
        double a = (A0 - th) % (2 * Math.PI);
        if (a < 0) {
            a += 2 * Math.PI;
        }
        return a / Math.abs(SPAN);
    }

    // Like the Black Flash: the cut lands but the target freezes, stunned, and only takes the blow (damage, knockback and
    // its impact frame) IMPACT_DELAY ticks later.
    private static void strike(LivingEntity caster, LivingEntity target, ServerLevel level, Vec3 eye) {
        Stun.apply(target, IMPACT_DELAY);
        Freeze.hold(target, IMPACT_DELAY + 1);
        Tasks.later(IMPACT_DELAY, () -> {
            Freeze.free(target);
            if (!target.isAlive()) {
                return;
            }
            Vec3 at = Combat.center(target);
            Vec3 away = at.subtract(eye);
            ModPayloads.SwordFx hit = new ModPayloads.SwordFx(SwordArts.FX_MOON_HIT, target.getId(), at, away, 0, 0);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(at) < VIEW_DISTANCE * VIEW_DISTANCE) {
                    PacketDistributor.sendToPlayer(player, hit);
                }
            }
            SubCommon.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.6f);
            SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
            if (caster.isAlive()) {
                Combat.hurt(caster, target, DAMAGE);
            }
            // The knockback whatever the damage did (it used to ride on the hurt landing, and Stark saw none), and once
            // more the next tick so nothing that ran in between (the hold letting go, the hurt's own knockback) eats it.
            Vec3 fling = fling(target, eye);
            target.setDeltaMovement(fling);
            target.hurtMarked = true;
            Tasks.later(1, () -> {
                if (target.isAlive()) {
                    target.setDeltaMovement(fling);
                    target.hurtMarked = true;
                }
            });
            Stun.apply(target, STUN_AFTER);
        });
    }

    // Thrown away from him (flat), and up.
    private static Vec3 fling(LivingEntity target, Vec3 eye) {
        Vec3 away = target.position().subtract(eye);
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
        return away.scale(FLING).add(0, FLING_UP, 0);
    }

    // Blocks: the crescent of the slash cut out of whatever it crosses. A thin moon (pointed at both ends, fattest in the
    // middle of the swing) from the arc out past it, through the plane and down below it, so on flat ground it digs a
    // crescent trench under the slash and a wall in its way gets a crescent hole through it. Never what he stands on.
    private static void carve(ServerLevel level, Vec3[] fr, Vec3 feet) {
        Vec3 centre = fr[0], n = fr[1], right = fr[2], up = fr[3];
        double outer = R * 1.07 * (1 + CARVE_OUT) * SCALE;
        int reach = (int) Math.ceil(outer + CARVE_BELOW);
        int[] debris = {0};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = Mth.floor(centre.x), cy = Mth.floor(centre.y), cz = Mth.floor(centre.z);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int y = cy - reach; y <= cy + reach; y++) {
                for (int z = cz - reach; z <= cz + reach; z++) {
                    Vec3 rel = new Vec3(x + 0.5, y + 0.5, z + 0.5).subtract(centre);
                    double depth = rel.dot(n);
                    if (depth > CARVE_ABOVE || depth < -CARVE_BELOW) {
                        continue;
                    }
                    double px = rel.dot(right) / SCALE, py = rel.dot(up) / SCALE;
                    double s = sweep(px, py);
                    if (s > 1.0) {
                        continue;
                    }
                    double ru = R * (1 + 0.07 * s);
                    double fat = Math.pow(Math.sin(Math.PI * s), 0.6);
                    double r = Math.sqrt(px * px + py * py);
                    double jag = 0.06 * (hash(x, y, z) - 0.5);
                    if (r < ru * (1 - CARVE_IN * fat) + jag || r > ru * (1 + CARVE_OUT * fat) + jag) {
                        continue;
                    }
                    pos.set(x, y, z);
                    remove(level, pos, centre, feet, debris);
                }
            }
        }
    }

    // One block out: some fly off as debris, the rest just go.
    private static void remove(ServerLevel level, BlockPos.MutableBlockPos pos, Vec3 from, Vec3 feet, int[] debris) {
        double fx = pos.getX() + 0.5 - feet.x, fz = pos.getZ() + 0.5 - feet.z;
        if (fx * fx + fz * fz < 1.6 * 1.6 && pos.getY() < feet.y + 2.2) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return;
        }
        if (debris[0] < 40 && SubCommon.RANDOM.nextInt(5) == 0) {
            debris[0]++;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
            block.dropItem = false;
            Vec3 out = new Vec3(pos.getX() + 0.5 - from.x, 0, pos.getZ() + 0.5 - from.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
            block.setDeltaMovement(out.scale(0.3 + 0.4 * SubCommon.RANDOM.nextDouble()).add(0, 0.4 + 0.5 * SubCommon.RANDOM.nextDouble(), 0));
            block.hurtMarked = true;
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
