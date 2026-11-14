package com.starkinhon.battlegroundsmod.animal;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.combat.BlockBreakFx;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.npc.UniverseNpc;
import com.starkinhon.battlegroundsmod.power.powers.DomainSpace;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.world.MapPlaces;
import com.starkinhon.battlegroundsmod.world.NarutoSites;
import com.starkinhon.battlegroundsmod.world.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The marks the universe creatures' blows leave on the world (Stark, 2026-11-14: "os mobs não destroem nada, parece que
// os golpes são só efeito; faz eles destruírem pelo menos um pouquinho, que faça sentido com o golpe"). Light and true to
// each blow: a claw or a blade mows the grass, flowers and leaves in its arc; a shot shatters glass; a bomb or a ki
// blast opens a small bowl with a few clods thrown out; a stomp cracks and heaves the ground; water leaves mud, ice
// leaves frost and snow, acid and poison leave dead earth, fire leaves burnt ground.
//
// Every mark: only with soulsBlockDestruction (and mobGriefing, BlockRules), never up in an expansion (no blocks there,
// #261), never in a challenge arena, never at a mission building (WorldSites, NarutoSites, the drawn maps' places) nor
// near a teaching NPC. No drops, no neighbor updates (#258), nothing with a block entity (chests, signs), no doors or
// beds; craters spread over a few ticks. Blocks past the blow's hardness stay (a T10 punch never cuts stone).
public final class MobGrief {
    // Hardness a blow gets through: earth (dirt, sand, grass, gravel, sandstone, ice), rock (stone, terracotta) and
    // heavy (cobblestone, planks, logs, bricks: only the big blasts).
    public static final float EARTH = 0.9f;
    public static final float ROCK = 1.6f;
    public static final float HEAVY = 2.1f;

    // How far from a building's center (horizontal) and from a teaching NPC the ground is left alone.
    private static final double SITE_MARGIN = 56;
    private static final double NPC_MARGIN = 16;

    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private MobGrief() {
    }

    // ---------------------------------------------------------------- may it mark the world here

    public static boolean allowed(LivingEntity mob, Vec3 at) {
        if (!(mob.level() instanceof ServerLevel level) || !BlockRules.canBreak(level) || DomainSpace.inside(mob)
                || ChallengeArenas.isArena(level.dimension())) {
            return false;
        }
        return !spared(level, at);
    }

    // A mission building or a teaching NPC near: the blows leave the blocks there alone.
    public static boolean spared(ServerLevel level, Vec3 at) {
        double r2 = SITE_MARGIN * SITE_MARGIN;
        if (level.dimension() == Level.OVERWORLD) {
            for (WorldSites.Site s : WorldSites.known(level)) {
                double dx = s.x() - at.x, dz = s.z() - at.z;
                if (dx * dx + dz * dz < r2) {
                    return true;
                }
            }
            for (NarutoSites.Site s : NarutoSites.known(level)) {
                double dx = s.x() - at.x, dz = s.z() - at.z;
                if (dx * dx + dz * dz < r2) {
                    return true;
                }
            }
        }
        if (MapPlaces.nearPlace(level, at.x, at.z, SITE_MARGIN)) {
            return true;
        }
        return !level.getEntitiesOfClass(UniverseNpc.class, new AABB(at, at).inflate(NPC_MARGIN), e -> e.isAlive()).isEmpty();
    }

    // ---------------------------------------------------------------- what a block is

    // Never touched: air, liquids, block entities, unbreakable blocks, doors and beds (a half left floating).
    private static boolean keep(ServerLevel level, BlockPos pos, BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0
                || s.is(BlockTags.DOORS) || s.is(BlockTags.BEDS) || s.is(Blocks.FARMLAND) || s.is(BlockTags.CROPS);
    }

    // What a claw, a blade or a gust takes: plants, leaves, glass, snow, cobwebs.
    public static boolean soft(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.REPLACEABLE)
                || s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.getBlock() instanceof StainedGlassPaneBlock
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.COBWEB) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.CACTUS) || s.is(BlockTags.CAVE_VINES) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean breakable(ServerLevel level, BlockPos pos, BlockState s, float maxHard) {
        return !keep(level, pos, s) && (soft(s) || s.getDestroySpeed(level, pos) <= maxHard);
    }

    // Erases the block (no drops, no neighbor updates) with its other half (tall grass) and whatever stood on it with no
    // body of its own (a flower, a torch, a snow layer), so nothing is left floating.
    private static void erase(ServerLevel level, BlockPos pos, BlockState s) {
        BlockBreakFx.erase(level, pos, s, Block.UPDATE_CLIENTS);
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            if (level.getBlockState(other).is(s.getBlock())) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos up = pos.above();
        BlockState over = level.getBlockState(up);
        if (!over.isAir() && over.getFluidState().isEmpty() && !over.hasBlockEntity() && over.getCollisionShape(level, up).isEmpty()
                && over.getDestroySpeed(level, up) >= 0) {
            erase(level, up, over);
        }
    }

    // The top solid block under a point (a few blocks down at most), or null when the point is high in the air.
    private static BlockPos top(ServerLevel level, Vec3 at, double maxGap) {
        Vec3 g = SubCommon.ground(level, at);
        if (at.y - g.y > maxGap) {
            return null;
        }
        BlockPos p = BlockPos.containing(g.x, g.y - 0.01, g.z);
        return level.getBlockState(p).isAir() ? null : p;
    }

    // ---------------------------------------------------------------- the marks

    // A blast's bowl: radius blocks across at the ground under at (none when it went off high in the air), deeper in the
    // middle, ragged at the rim, what stands beside it within the blast gone too, a few clods thrown out. One ring a
    // tick from the middle out.
    public static void crater(LivingEntity mob, Vec3 at, double radius, float maxHard) {
        if (radius <= 0 || !allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        BlockPos mid = top(level, at, radius + 1.2);
        if (mid == null) {
            return;
        }
        int r = Mth.ceil(radius);
        double depth = Math.max(1, radius * 0.55);
        int up = Mth.ceil(radius * 0.8);
        int ticks = radius < 1.6 ? 1 : radius < 2.6 ? 2 : 3;
        int[] flown = {0};
        int flyMax = 1 + (int) (radius * 1.5);
        long seed = RANDOM.nextLong();
        Tasks.repeat(ticks, tick -> {
            double from = radius * tick / ticks;
            double to = radius * (tick + 1) / ticks;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double flat = Math.sqrt(dx * dx + dz * dz);
                    if (flat < from || flat >= to && tick < ticks - 1) {
                        continue;
                    }
                    // a ragged rim: each column a little in or out
                    double rim = radius * (0.8 + 0.35 * hash(mid.getX() + dx, mid.getZ() + dz, seed));
                    if (flat > rim) {
                        continue;
                    }
                    double k = flat / rim;
                    int bottom = -(int) Math.round(depth * Math.sqrt(Math.max(0, 1 - k * k)) * (0.8 + 0.4 * hash(dx, dz, seed + 7)));
                    for (int dy = up; dy >= bottom; dy--) {
                        // above the ground the blast reaches a little less far (a wall beside it loses a corner)
                        if (dy > 0 && flat * flat + dy * dy * 1.6 > radius * radius) {
                            continue;
                        }
                        BlockPos p = mid.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) {
                            continue;
                        }
                        BlockState s = level.getBlockState(p);
                        if (!breakable(level, p, s, maxHard)) {
                            continue;
                        }
                        if (dy <= 0 && dy >= bottom && flown[0] < flyMax && !soft(s) && RANDOM.nextInt(5) == 0) {
                            flown[0]++;
                            throwOut(level, p, s, new Vec3(dx, 0, dz), 0.25 + 0.35 * (1 - k), 0.45 + 0.45 * (1 - k));
                        } else {
                            erase(level, p, s);
                        }
                    }
                }
            }
        });
    }

    // The ground torn and thrown up around a stomp, a leap's landing or something bursting out of the earth: count
    // blocks off the top within radius, flung up and out (they land again around it), the rest of the ring cracked.
    public static void heave(LivingEntity mob, Vec3 at, double radius, int count, float maxHard) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        BlockPos mid = top(level, at, 1.5);
        if (mid == null) {
            return;
        }
        Vec3 feet = mob.position();
        int r = Mth.ceil(radius);
        for (int i = 0, tries = 0; i < count && tries < count * 4; tries++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double d = radius * Math.sqrt(0.15 + 0.85 * RANDOM.nextDouble());
            int x = mid.getX() + Mth.floor(Math.cos(a) * d + 0.5);
            int z = mid.getZ() + Mth.floor(Math.sin(a) * d + 0.5);
            BlockPos p = surface(level, x, mid.getY(), z, r);
            if (p == null || feet.distanceToSqr(p.getX() + 0.5, feet.y, p.getZ() + 0.5) < 1.2) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (!breakable(level, p, s, maxHard) || soft(s)) {
                continue;
            }
            i++;
            BlockPos over = p.above();
            BlockState o = level.getBlockState(over);
            if (!o.isAir() && o.getCollisionShape(level, over).isEmpty() && !keep(level, over, o)) {
                erase(level, over, o);
            }
            throwOut(level, p, s, new Vec3(x - mid.getX(), 0, z - mid.getZ()), 0.12 + 0.18 * RANDOM.nextDouble(), 0.45 + 0.4 * RANDOM.nextDouble());
        }
    }

    // A line of ground heaved, one block every step (stakes or roots shooting out along it).
    public static void heaveLine(LivingEntity mob, Vec3 from, Vec3 to, double step, float maxHard) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5 || !allowed(mob, from) || !allowed(mob, to)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        Vec3 dir = d.scale(1 / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 feet = mob.position();
        for (double s = step; s <= len; s += step) {
            Vec3 p = from.add(dir.scale(s)).add(side.scale((RANDOM.nextDouble() - 0.5) * 0.8));
            BlockPos b = top(level, p, 1.5);
            if (b == null || feet.distanceToSqr(b.getX() + 0.5, feet.y, b.getZ() + 0.5) < 1.2) {
                continue;
            }
            BlockState st = level.getBlockState(b);
            if (!breakable(level, b, st, maxHard) || soft(st)) {
                continue;
            }
            BlockPos over = b.above();
            BlockState o = level.getBlockState(over);
            if (!o.isAir() && o.getCollisionShape(level, over).isEmpty() && !keep(level, over, o)) {
                erase(level, over, o);
            }
            throwOut(level, b, st, side.scale(RANDOM.nextBoolean() ? 1 : -1).add(dir.scale(0.4)), 0.08 + 0.1 * RANDOM.nextDouble(),
                    0.4 + 0.3 * RANDOM.nextDouble());
        }
    }

    // A claw, a blade, a tail or a gust: the soft blocks (plants, leaves, glass, snow) in front of the body, within reach
    // blocks of `from` along dir and `width` blocks to each side, from the feet up to `height`.
    public static void mow(LivingEntity mob, Vec3 from, Vec3 dir, double reach, double width, double height) {
        if (!allowed(mob, from)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        Vec3 f = SubCommon.flat(dir);
        if (f.lengthSqr() < 1.0E-4) {
            return;
        }
        f = f.normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        Set<Long> done = new HashSet<>();
        int y0 = Mth.floor(mob.getY());
        int y1 = Mth.floor(mob.getY() + height);
        for (double s = 0.4; s <= reach; s += 0.5) {
            double w = width * Math.min(1, 0.45 + s / Math.max(1, reach));
            for (double l = -w; l <= w; l += 0.5) {
                Vec3 p = from.add(f.scale(s)).add(side.scale(l));
                for (int y = y0; y <= y1; y++) {
                    BlockPos b = BlockPos.containing(p.x, y, p.z);
                    if (!done.add(b.asLong())) {
                        continue;
                    }
                    BlockState st = level.getBlockState(b);
                    if (soft(st) && !keep(level, b, st)) {
                        erase(level, b, st);
                    }
                }
            }
        }
    }

    // A bullet, a needle or a sound wave hitting at `at`: the glass and the plants right there shatter.
    public static void shatter(LivingEntity mob, Vec3 at, double radius) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        int r = Mth.ceil(radius);
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distToCenterSqr(at) > radius * radius + 0.3) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (soft(s) && !keep(level, p, s)) {
                erase(level, p.immutable(), s);
            }
        }
    }

    // The soft blocks (and glass) along a line, `radius` thick: a sound wave, a gust, a jet of water.
    public static void shatterLine(LivingEntity mob, Vec3 from, Vec3 to, double radius) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1 || !allowed(mob, from)) {
            return;
        }
        Vec3 dir = d.scale(1 / len);
        for (double s = 0.6; s <= len; s += 0.8) {
            shatter(mob, from.add(dir.scale(s)), radius);
        }
    }

    // Digging through: a drill or a burrowing body carves a hole radius wide at `at` (earth only, never under the feet's
    // level minus one), a trench along the way it runs.
    public static void bore(LivingEntity mob, Vec3 at, double radius, float maxHard) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        int r = Mth.ceil(radius);
        int floor = Mth.floor(mob.getY()) - 1;
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.getY() < floor || p.distToCenterSqr(at) > radius * radius) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (breakable(level, p, s, maxHard)) {
                erase(level, p.immutable(), s);
            }
        }
    }

    // Fire or a hot blast: the grass burnt to dirt, the plants and leaves gone, a flame or two left on the ground.
    public static void scorch(LivingEntity mob, Vec3 at, double radius, int flames) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        int lit = 0;
        for (BlockPos p : surfaceAround(level, at, radius)) {
            BlockState s = level.getBlockState(p);
            BlockPos up = p.above();
            BlockState over = level.getBlockState(up);
            if (soft(over) && !keep(level, up, over)) {
                erase(level, up, over);
            }
            if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK)) {
                level.setBlock(p, (RANDOM.nextInt(3) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (s.is(BlockTags.LEAVES)) {
                erase(level, p, s);
                continue;
            }
            if (lit < flames && RANDOM.nextInt(4) == 0 && level.getBlockState(up).isAir() && level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) {
                lit++;
                level.setBlock(up, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // Acid or poison: the plants die, the grass turns to bare coarse earth.
    public static void blight(LivingEntity mob, Vec3 at, double radius) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        for (BlockPos p : surfaceAround(level, at, radius)) {
            BlockState s = level.getBlockState(p);
            BlockPos up = p.above();
            BlockState over = level.getBlockState(up);
            if (!over.isAir() && soft(over) && !over.is(BlockTags.LEAVES) && !keep(level, up, over)) {
                erase(level, up, over);
            }
            if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.PODZOL)) && RANDOM.nextInt(4) != 0) {
                level.setBlock(p, (RANDOM.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // A splash of water: the plants and snow washed off, fire put out, the earth turned to mud here and there.
    public static void soak(LivingEntity mob, Vec3 at, double radius) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        for (BlockPos p : surfaceAround(level, at, radius)) {
            BlockState s = level.getBlockState(p);
            BlockPos up = p.above();
            BlockState over = level.getBlockState(up);
            if (over.is(Blocks.FIRE) || (soft(over) && !over.is(BlockTags.LEAVES) && !keep(level, up, over))) {
                erase(level, up, over);
            }
            if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)) && RANDOM.nextInt(5) < 3) {
                level.setBlock(p, Blocks.MUD.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // Cold: still water at the top freezes over (and melts again), the ground gets a layer of snow.
    public static void frost(LivingEntity mob, Vec3 at, double radius) {
        if (!allowed(mob, at)) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        int r = Mth.ceil(radius);
        BlockPos c = BlockPos.containing(at);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius * (0.75 + 0.4 * RANDOM.nextDouble())) {
                    continue;
                }
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    BlockPos up = p.above();
                    if (s.is(Blocks.WATER) && s.getFluidState().isSource() && s.getFluidState().is(Fluids.WATER) && level.getBlockState(up).isAir()) {
                        level.setBlock(p, Blocks.FROSTED_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.scheduleTick(p, Blocks.FROSTED_ICE, Mth.nextInt(RANDOM, 120, 220));
                    } else if (soft(s) && !s.is(Blocks.SNOW) && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.IMPERMEABLE) && !keep(level, p, s)
                            && s.getCollisionShape(level, p).isEmpty()) {
                        // the grass under the snow
                        erase(level, p, s);
                        BlockPos below = p.below();
                        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                            level.setBlock(p, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    } else if (s.isFaceSturdy(level, p, Direction.UP) && level.getBlockState(up).isAir() && !keep(level, p, s)) {
                        level.setBlock(up, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void throwOut(ServerLevel level, BlockPos p, BlockState s, Vec3 out, double speed, double rise) {
        FallingBlockEntity clod = FallingBlockEntity.fall(level, p.immutable(), s);
        clod.dropItem = false;
        Vec3 o = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
        clod.setDeltaMovement(o.scale(speed).add(0, rise, 0));
        clod.hurtMarked = true;
        BlockBreakFx.note(level, p, s);
    }

    // The top solid block of the column (x, z) within reach blocks up or down of y, or null.
    private static BlockPos surface(ServerLevel level, int x, int y, int z, int reach) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + reach, z);
        for (int i = 0; i <= reach * 2; i++, p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return p.immutable();
            }
        }
        return null;
    }

    // The top solid block of every column within radius of at (two blocks up or down), ragged at the edge.
    private static java.util.List<BlockPos> surfaceAround(ServerLevel level, Vec3 at, double radius) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        BlockPos c = BlockPos.containing(at);
        int r = Mth.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius * (0.7 + 0.45 * RANDOM.nextDouble())) {
                    continue;
                }
                BlockPos p = surface(level, c.getX() + dx, c.getY(), c.getZ() + dz, 2);
                if (p != null) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static double hash(int x, int z, long seed) {
        long h = x * 73856093L ^ z * 19349663L ^ seed * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h >>> 20) & 0xFFFF) / 65535.0;
    }
}
