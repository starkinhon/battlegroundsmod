package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CustomDeath;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The Decadência (崩壊), as in the approved preview (forja/shigaraki-tentaculos): whatever Shigaraki's hands touch dries
// grey, cracks and falls apart into dust, starting at the touch and running outwards along cracks.
// Blocks: a seed at a point spreads at its speed up to its reach (DecayNoise.delay). The clients get the same seed
// (Shigaraki.SEED) and draw the dry grey, the cracks and each block crumbling (client/fx/shig/DecayBlocks); the server
// takes each block away when its crumbling starts (0.5 s after the Decadência reaches it), a few thousand per tick at
// most, without neighbour updates (#258). Only with the gamerules mobGriefing and soulsBlockDestruction (BlockRules);
// without them the clients still show the cracks, and nothing breaks.
// Bodies: body() dries the target from the touch out (client/fx/shig/DecayBodies), with damage over the time it takes
// and a stun; if it dies of it, the body crumbles to dust instead of the normal death.
public final class Decay {
    // How a seed spreads (the preview's groups): GROUND = the ground it sits on, only its top two layers, and anything
    // standing on it close by (2.5 blocks); STRUCTURE = what stands above the ground (a building a tentacle hit), all of
    // it within reach, and the ground under it only close by.
    public static final int GROUND = 0, STRUCTURE = 1;
    // Blocks taken away per tick per level at most (the rest wait for the next ticks).
    private static final int PER_TICK = 2500;
    private static final int QUIET = 2 | 16;
    // The crumbling starts this long after the Decadência reaches a block (preview: age 0.5 s).
    public static final float CRUMBLE = 0.5f;

    private record Due(long tick, long pos) implements Comparable<Due> {
        @Override
        public int compareTo(Due o) {
            return Long.compare(tick, o.tick);
        }
    }

    private static final class Sched {
        final PriorityQueue<Due> queue = new PriorityQueue<>();
        final Map<Long, Long> when = new HashMap<>();
        boolean running;
    }

    private static final Map<ServerLevel, Sched> SCHED = new HashMap<>();

    private Decay() {
    }

    // One block the seed reaches: where, what, and how many seconds after the seed the Decadência gets there.
    public record Hit(BlockPos pos, BlockState state, float delay) {
    }

    // The blocks a seed reaches, the same walk on the server and on the clients (client/fx/shig/DecayBlocks).
    // p: the seed (a block's centre for the ground); groundY: the ground's level there (the feet's height); safeX/Z and
    // safeR: the ground under Shigaraki that never goes (only blocks under groundY).
    public static void walk(BlockGetter level, Vec3 p, float speed, float maxR, int mode, double groundY, double safeX, double safeZ, double safeR,
            Consumer<Hit> out) {
        float reach = maxR + 1.6f;
        int r = Mth.ceil(reach);
        int px = Mth.floor(p.x), py = Mth.floor(p.y), pz = Mth.floor(p.z);
        int gy = Mth.floor(groundY + 0.01);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++) {
            int y = py + dy;
            boolean ground = y < gy;
            if (mode == GROUND && ground && y < py - 1) {
                continue; // the ground goes only two layers deep (the preview's world)
            }
            if (mode == STRUCTURE && !ground) {
                continue; // what stands above the ground: only what the seed's own building holds (below)
            }
            float limit = mode == GROUND ? (ground ? maxR : Math.min(maxR, 2.5f)) : (ground ? Math.min(maxR, 2.5f) : maxR);
            if (Math.abs(dy) > limit + 1.6f) {
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = px + dx, z = pz + dz;
                    float cx = x + 0.5f, cy = y + 0.5f, cz = z + 0.5f;
                    float d = (float) Math.sqrt((cx - p.x) * (cx - p.x) + (cy - p.y) * (cy - p.y) + (cz - p.z) * (cz - p.z));
                    if (d > limit + 1.6f) {
                        continue;
                    }
                    if (ground && safeR > 0 && (cx - safeX) * (cx - safeX) + (cz - safeZ) * (cz - safeZ) < safeR * safeR) {
                        continue;
                    }
                    float delay = DecayNoise.delay(cx, cy, cz, d, speed, limit);
                    if (delay < 0) {
                        continue;
                    }
                    at.set(x, y, z);
                    BlockState st = level.getBlockState(at);
                    if (!decays(level, at, st)) {
                        continue;
                    }
                    out.accept(new Hit(at.immutable(), st, delay));
                }
            }
        }
        if (mode == STRUCTURE) {
            structure(level, p, speed, maxR, gy, out);
        }
    }

    // A STRUCTURE seed above the ground: the blocks joined to the one it hit (the preview's groups: a tentacle on one
    // building never takes the next one across the street), within the same reach and with the same delays as above.
    private static void structure(BlockGetter level, Vec3 p, float speed, float maxR, int gy, Consumer<Hit> out) {
        float lim = maxR + 1.6f;
        BlockPos start = BlockPos.containing(p);
        if (start.getY() < gy) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        seen.add(start.asLong());
        open.add(start);
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            BlockState st = level.getBlockState(at);
            if (!decays(level, at, st)) {
                continue;
            }
            float cx = at.getX() + 0.5f, cy = at.getY() + 0.5f, cz = at.getZ() + 0.5f;
            float d = (float) Math.sqrt((cx - p.x) * (cx - p.x) + (cy - p.y) * (cy - p.y) + (cz - p.z) * (cz - p.z));
            float delay = DecayNoise.delay(cx, cy, cz, d, speed, maxR);
            if (delay >= 0) {
                out.accept(new Hit(at, st, delay));
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = at.offset(dx, dy, dz);
                        if (n.getY() < gy || !seen.add(n.asLong())) {
                            continue;
                        }
                        float nx = n.getX() + 0.5f - (float) p.x, ny = n.getY() + 0.5f - (float) p.y, nz = n.getZ() + 0.5f - (float) p.z;
                        if (nx * nx + ny * ny + nz * nz <= lim * lim) {
                            open.add(n);
                        }
                    }
                }
            }
        }
    }

    // What the Decadência can take: solid things, not liquids, chests and other blocks that keep things, nor bedrock.
    public static boolean decays(BlockGetter level, BlockPos pos, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && !st.hasBlockEntity() && st.getDestroySpeed(level, pos) >= 0;
    }

    // A seed of Decadência in the blocks at p (see walk). caster: whose ground is safe (null: none).
    public static void seed(ServerLevel level, LivingEntity caster, Vec3 p, float speed, float maxR, int mode, double groundY, double safeR) {
        double sx = caster == null ? 0 : caster.getX(), sz = caster == null ? 0 : caster.getZ();
        boolean breaks = BlockRules.canBreak(level);
        SubNet.send(level, Shigaraki.SEED, caster == null ? -1 : caster.getId(), p, new Vec3(speed, maxR, groundY), mode,
                (float) (caster == null ? 0 : safeR), breaks ? 1 : 0);
        if (!breaks) {
            return;
        }
        long now = level.getGameTime();
        Sched s = SCHED.computeIfAbsent(level, l -> new Sched());
        walk(level, p, speed, maxR, mode, groundY, sx, sz, caster == null ? 0 : safeR, hit -> {
            long tick = now + Math.round((hit.delay() + CRUMBLE) * 20);
            schedule(s, hit.pos().asLong(), tick);
        });
        run(level, s);
    }

    // Takes one block away at a given tick (the Fim do Mundo's own front uses this too).
    static void schedule(ServerLevel level, BlockPos pos, long tick) {
        Sched s = SCHED.computeIfAbsent(level, l -> new Sched());
        schedule(s, pos.asLong(), tick);
        run(level, s);
    }

    private static void schedule(Sched s, long pos, long tick) {
        Long was = s.when.get(pos);
        if (was != null && was <= tick) {
            return;
        }
        s.when.put(pos, tick);
        s.queue.add(new Due(tick, pos));
    }

    private static void run(ServerLevel level, Sched s) {
        if (s.running) {
            return;
        }
        s.running = true;
        Tasks.addSystem(age -> {
            long now = level.getGameTime();
            int done = 0, sounds = 0;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            while (!s.queue.isEmpty() && s.queue.peek().tick() <= now && done < PER_TICK) {
                Due d = s.queue.poll();
                Long w = s.when.get(d.pos());
                if (w == null || w != d.tick()) {
                    continue; // moved earlier by another seed
                }
                s.when.remove(d.pos());
                at.set(d.pos());
                BlockState st = level.getBlockState(at);
                if (!decays(level, at, st)) {
                    continue;
                }
                level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
                done++;
                if (sounds < 2 && SubCommon.RANDOM.nextInt(40) == 0) {
                    sounds++;
                    SubCommon.sound(level, Vec3.atCenterOf(at), SoundEvents.SAND_BREAK, 0.5f, 0.6f + SubCommon.RANDOM.nextFloat() * 0.3f);
                }
            }
            if (s.queue.isEmpty()) {
                s.running = false;
                SCHED.remove(level);
                return true;
            }
            return false;
        });
    }

    // ---------- bodies ----------

    private static final Set<Integer> DECAYING = new HashSet<>();

    // The Decadência on a body, from touch (a point on it) at speed blocks per second: damage over the ticks it takes to
    // cover it (total, spread out), a stun as long, and the crumbling if it dies of it. Not twice at once on a body.
    public static boolean body(LivingEntity caster, LivingEntity target, Vec3 touch, float speed, float total, int stun) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || !DECAYING.add(target.getId())) {
            return false;
        }
        float h = target.getBbHeight();
        int ticks = Math.max(10, Math.round((h + 0.6f) / speed * 20));
        SubNet.send(level, Shigaraki.BODY, target.getId(), touch, new Vec3(speed, ticks, 0), 0, 0, 0);
        Stun.apply(target, Math.max(stun, ticks));
        int id = target.getId();
        float per = total / ticks;
        Tasks.addSystem(age -> {
            if (!target.isAlive() || target.isRemoved()) {
                DECAYING.remove(id);
                return true;
            }
            boolean wasAlive = !target.isDeadOrDying();
            // the drying body stays where it was touched (the preview): a hurt every tick would knock it away
            Vec3 v = target.getDeltaMovement();
            Combat.hurt(caster, target, per);
            target.setDeltaMovement(v);
            if (wasAlive && target.isDeadOrDying()) {
                // it went to dust: the clients draw it crumbling from the touch out (never the normal death)
                CustomDeath.mark(target);
                SubNet.send(level, Shigaraki.BODY, id, touch, new Vec3(speed, ticks, age), 1, 0, 0);
                DECAYING.remove(id);
                return true;
            }
            if (age >= ticks) {
                SubNet.send(level, Shigaraki.BODY, id, touch, new Vec3(speed, ticks, age), 2, 0, 0);
                DECAYING.remove(id);
                return true;
            }
            return false;
        });
        return true;
    }

    public static boolean decaying(LivingEntity target) {
        return DECAYING.contains(target.getId());
    }

    // The world closed (ServerReset): nothing of it is kept for the next one (old entities, old game times).
    public static void resetStatics() {
        SCHED.clear();
        DECAYING.clear();
    }
}
