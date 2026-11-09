package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The World Cutting Slash (Stark, 2026-11-09; the 3D preview forja/sukuna-corte-mundo): the fourth stage of the chant,
// with the Modelo bar full. The highest damage and the most destruction in a straight line of the mod; it ignores every
// defense (Combat.hurtPiercing).
//   - Standing: one crescent, enormous but thin (a giant knife thrown), standing up: it cuts everything in its plane and
//     opens a thin slit through the blocks far ahead (gamerule soulsBlockDestruction), down a few blocks into the ground.
//   - Crouched (the waffle, Kashimo's): a grid of lines (the Shift+Z's), giant but with a shorter reach: every line cuts
//     its own slit.
// The cutting is spread over the ticks of the flight, a bounded number of blocks each tick.
public final class WorldSlash {
    // Standing: tip to tip, speed (blocks a tick), reach, how deep under his feet it cuts, damage, stun.
    private static final double SPAN = 160, SPEED = 26, REACH = 400, DEPTH = 8;
    private static final float DAMAGE = 350;
    private static final int STUN = 30;
    // The waffle: the grid's side, speed, reach, damage.
    private static final double W_SIZE = 64, W_SPEED = 20, W_REACH = 200;
    private static final float W_DAMAGE = 260;
    // How close along a line the cut samples the blocks, and how many it may change in one tick.
    private static final double STEP = 0.7;
    private static final int BUDGET = 6000;

    private WorldSlash() {
    }

    // origin: the hand when it leaves; dir: the way it flies.
    public static void standing(LivingEntity caster, Vec3 origin, Vec3 dir) {
        normal(caster, origin, dir, false);
    }

    // Lying (Stark 2026-11-09: standing when he aims right at whom he fights, lying when he doesn't): the same crescent
    // turned flat, its span across to his sides, cutting everything at its height (the preview's three mountains).
    public static void lying(LivingEntity caster, Vec3 origin, Vec3 dir) {
        normal(caster, origin, dir, true);
    }

    // "up" is the crescent's span (the way its tips point): upright, or flat across for the lying one.
    private static void normal(LivingEntity caster, Vec3 origin, Vec3 dir, boolean flat) {
        ServerLevel level = Combat.level(caster);
        Vec3 way = dir.normalize();
        Vec3 upright = new Vec3(0, 1, 0).subtract(way.scale(way.y)).normalize();
        Vec3 up = flat ? upright.cross(way).normalize() : upright;
        Vec3 face = up.cross(way).normalize();
        double unit = SPAN / 2 / SukunaCuts.TIP;
        int seed = caster.getRandom().nextInt(0xFFFF);
        double feet = caster.getY();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaSlash(origin, way, up, (float) unit, (float) SPEED,
                (float) REACH, seed, SukunaCuts.WORLD | (flat ? 0 : SukunaCuts.GROOVE), (float) feet));
        sounds(level, origin);
        boolean cut = BlockRules.canBreak(level);
        Set<Integer> hit = new HashSet<>();
        LongOpenHashSet done = new LongOpenHashSet();
        double grow = 0.306 * unit;
        Tasks.add(age -> {
            if (!caster.isAlive() && age > 0) {
                return true;
            }
            double from = age == 0 ? -1.5 : SPEED * age, upTo = Math.min(REACH, SPEED * (age + 1));
            // how tall it is out there (it grows out of the hand over its first blocks)
            double g = Math.min(1, Math.pow(Math.max(upTo, 0.05) / grow, 0.75));
            double top = SukunaCuts.TIP * unit * g;
            // standing it goes a few blocks into the ground under him; flat it spans the same both ways
            double below = flat ? top : Math.min(top, (origin.y - feet) + DEPTH);
            // whoever is in its plane
            Vec3 a = origin.add(way.scale(from)), b = origin.add(way.scale(upTo));
            AABB box = flat ? new AABB(a, b).inflate(top + 2, 2, top + 2)
                    : new AABB(a, b).inflate(2, 0, 2).expandTowards(0, top, 0).expandTowards(0, -below, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && !e.isSpectator())) {
                if (hit.contains(e.getId()) || e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                    continue;
                }
                Vec3 rel = Combat.center(e).subtract(origin);
                double along = rel.dot(way), off = Math.abs(rel.dot(face)), h = rel.dot(up);
                double half = e.getBbWidth() / 2 + 0.8;
                if (along < from - half || along > upTo + half || off > half || h > top + e.getBbHeight() / 2 || h < -below - e.getBbHeight() / 2) {
                    continue;
                }
                hit.add(e.getId());
                strike(caster, e, DAMAGE, way, unit / 1.75 * 0.42 * 2.2);
            }
            // the slit through the blocks, in its plane
            if (cut) {
                int[] budget = {flat ? BUDGET * 3 / 2 : BUDGET};
                for (double s = Math.max(0.6, from); s < upTo && budget[0] > 0; s += STEP) {
                    Vec3 p = origin.add(way.scale(s));
                    for (double hh = -below; hh <= top && budget[0] > 0; hh += STEP) {
                        carve(level, p.add(up.scale(hh)), done, budget, caster);
                    }
                }
            }
            return upTo >= REACH;
        });
    }

    // The waffle: origin is the hand, above: its height over the ground.
    public static void waffle(LivingEntity caster, Vec3 origin, Vec3 dir) {
        ServerLevel level = Combat.level(caster);
        Vec3 way = new Vec3(dir.x, 0, dir.z).lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = way.cross(up).normalize();
        double above = origin.y - caster.getY();
        int seed = caster.getRandom().nextInt(0xFFFF);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.SukunaWaffle(origin, way, up, (float) W_SIZE, (float) above,
                (float) W_SPEED, (float) W_REACH, seed));
        sounds(level, origin);
        // the lines, as the renderer lays them out (grid units of 110, bottom line just over the ground)
        double sc = W_SIZE / 110;
        float fs = seed / 6553.6f;
        double[] vx = new double[9], va = new double[9], hy = new double[9], ha = new double[9];
        for (int k = 0; k < 9; k++) {
            double u = (k + 0.5) / 9 - 0.5;
            vx[k] = (u * 110 + (hash(k * 3.1 + fs) - 0.5) * 3.2) * sc;
            va[k] = (hash(k * 5.7 + fs) - 0.5) * 0.05;
            hy[k] = (56 + u * 110 + (hash(k * 4.3 + 1 + fs) - 0.5) * 3.2) * sc;
            ha[k] = (hash(k * 6.1 + 2 + fs) - 0.5) * 0.05;
        }
        hy[0] = Math.max(hy[0], 1.2 * sc);
        double feet = caster.getY();
        Vec3 base = new Vec3(origin.x, feet, origin.z);
        double half = W_SIZE / 2 + 12 * sc, height = W_SIZE + 12 * sc;
        double grow = 34 * sc;
        boolean cut = BlockRules.canBreak(level);
        Set<Integer> hit = new HashSet<>();
        LongOpenHashSet done = new LongOpenHashSet();
        Tasks.add(age -> {
            if (!caster.isAlive() && age > 0) {
                return true;
            }
            double from = age == 0 ? -1.5 : W_SPEED * age, upTo = Math.min(W_REACH, W_SPEED * (age + 1));
            Vec3 a = base.add(way.scale(from)), b = base.add(way.scale(upTo));
            AABB box = new AABB(a, b).inflate(half, 0, half).expandTowards(0, height, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && !e.isSpectator())) {
                if (hit.contains(e.getId()) || e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                    continue;
                }
                Vec3 rel = Combat.center(e).subtract(base);
                double along = rel.dot(way), x = rel.dot(right), y = rel.y;
                double w = e.getBbWidth() / 2 + 1, hh = e.getBbHeight() / 2 + 1;
                if (along < from - w || along > upTo + w || Math.abs(x) > half + w || y < -hh || y > height + hh) {
                    continue;
                }
                boolean onLine = false;
                for (int k = 0; k < 9 && !onLine; k++) {
                    onLine = Math.abs(x - (vx[k] + va[k] * (y - 56 * sc))) < w || Math.abs(y - (hy[k] + ha[k] * x)) < hh;
                }
                if (!onLine) {
                    continue;
                }
                hit.add(e.getId());
                strike(caster, e, W_DAMAGE, way, 2.2);
            }
            if (cut && upTo > grow) {
                int[] budget = {BUDGET};
                for (double s = Math.max(grow, from); s < upTo && budget[0] > 0; s += STEP) {
                    Vec3 p = base.add(way.scale(s));
                    for (int k = 0; k < 9 && budget[0] > 0; k++) {
                        // an upright line: its plane from the ground up; a lying one: across the whole width
                        for (double y = -2; y <= height && budget[0] > 0; y += STEP) {
                            carve(level, p.add(right.scale(vx[k] + va[k] * (y - 56 * sc))).add(0, y, 0), done, budget, caster);
                        }
                        for (double x = -half; x <= half && budget[0] > 0; x += STEP) {
                            carve(level, p.add(right.scale(x)).add(0, hy[k] + ha[k] * x, 0), done, budget, caster);
                        }
                    }
                }
            }
            return upTo >= W_REACH;
        });
    }

    private static void strike(LivingEntity caster, LivingEntity target, float damage, Vec3 way, double starSize) {
        ServerLevel level = Combat.level(caster);
        if (Combat.hurtPiercing(caster, target, damage)) {
            Combat.push(target, way, 0.6, 0.25);
            Stun.apply(target, STUN);
        }
        Vec3 c = Combat.center(target);
        SukunaCuts.star(caster, c.subtract(way.scale(0.4)), (float) Math.max(2.2, Math.min(starSize, 9)));
        Fx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
    }

    // One block of the slit, if it is there to cut: not air, fluid, a block entity or unbreakable; loaded.
    private static void carve(ServerLevel level, Vec3 p, LongOpenHashSet done, int[] budget, LivingEntity caster) {
        BlockPos pos = BlockPos.containing(p);
        if (SukunaCarve.underFeet(caster, pos)) {
            return;
        }
        if (!done.add(pos.asLong()) || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return;
        }
        budget[0]--;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void sounds(ServerLevel level, Vec3 at) {
        Fx.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 3f, 0.5f);
        Fx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.45f);
        Fx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.7f);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }
}
