package com.starkinhon.battlegroundsmod.power.powers.opm;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.opm.OpmShape.Frame;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Saitama's four serious moves (the approved page, row one): each is the whole Modo Serio, spent on use. The server runs the
// same timeline as the page (in ticks: 20 a second), hits what the drawn shape covers, and digs exactly the shape the client
// draws (OpmShape). The client plays the rest from the one message sent at the start (OpmFx).
// Z Soco Serio: 2 s of silence, the punch, and a tube of air 200 blocks long that opens a trench up to 12 deep.
// X Saltos Laterais Serios: 15 hops round the target (it can't find him), the ground torn up and floating, a punch from behind.
// C Virada de Mesa Seria: his hands in the ground, a 72 x 66 slab (12 deep) turned over like a table onto the far side.
// V Espirro Serio: "Ha... ha...!" and the sneeze, a fan 150 blocks long that blows the ground away.
public final class OpmSerious {
    // Blocks changed without neighbor updates or drops (the look of the breaking is the client's flying blocks).
    private static final int QUIET = 2 | 16;
    // Per tick: blocks changed, and blocks looked at, by all the digs together (#258: big destruction spread over ticks).
    private static final int CHANGE_BUDGET = 3200, READ_BUDGET = 26000;

    private OpmSerious() {
    }

    private static int seed() {
        return SubCommon.RANDOM.nextInt(1 << 20);
    }

    // Every serious move: the mode is spent, the pose, the silence must not be a free hit (armor while it winds up).
    private static void begin(LivingEntity caster, ServerLevel level, String pose, int busy, int armor) {
        Opm.track(caster);
        Opm.spend(caster, level);
        SubCommon.pose(caster, pose);
        SubCommon.busy(caster, busy);
        SubCommon.setState(caster, Opm.ARMOR, armor);
    }

    private static int at(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    // ---------- the digging, shared ----------

    // Columns to cut: x, z, the lowest and highest y (inclusive). The tops above the ground cost nothing (the heightmap).
    static final class Dig {
        private final ServerLevel level;
        private final ArrayDeque<int[]> columns = new ArrayDeque<>();
        private final Set<Long> seen = new HashSet<>();
        private final boolean on;
        private boolean closed;

        Dig(ServerLevel level) {
            this.level = level;
            this.on = BlockRules.canBreak(level);
            if (on) {
                DIGS.add(this);
            }
        }

        boolean on() {
            return on;
        }

        // Each column once (the first span asked for it).
        boolean add(int x, int z, int lo, int hi) {
            if (!on || hi < lo || !seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
                return false;
            }
            columns.add(new int[] {x, z, lo, hi});
            return true;
        }

        // Nothing more will be added: it ends when the queue is empty.
        void close() {
            closed = true;
        }

        boolean done() {
            return closed && columns.isEmpty();
        }
    }

    private static final List<Dig> DIGS = new java.util.ArrayList<>();

    // Start of each level tick (Opm.onLevelTick): shares the budget among the level's digs going on, oldest first.
    static void runDigs(ServerLevel level) {
        if (DIGS.isEmpty()) {
            return;
        }
        int[] budget = {CHANGE_BUDGET, READ_BUDGET};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Dig dig : DIGS) {
            if (dig.level != level) {
                continue;
            }
            while (budget[0] > 0 && budget[1] > 0 && !dig.columns.isEmpty()) {
                int[] c = dig.columns.poll();
                int surface = dig.level.getHeight(Heightmap.Types.MOTION_BLOCKING, c[0], c[1]) - 1;
                int top = Math.min(c[3], surface + 1);
                for (int y = top; y >= c[2]; y--) {
                    budget[1]--;
                    pos.set(c[0], y, c[1]);
                    BlockState st = dig.level.getBlockState(pos);
                    if (breakable(dig.level, pos, st)) {
                        dig.level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                        budget[0]--;
                    }
                }
            }
        }
        DIGS.removeIf(d -> d.done() || d.level.getServer() == null);
    }

    static boolean breakable(ServerLevel level, BlockPos p, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && !st.hasBlockEntity() && st.getDestroySpeed(level, p) >= 0;
    }

    // The world columns whose centers fall in the local rectangle [x0, x1] x [z0, z1] of the frame, by the local center.
    private interface ColumnAt {
        void at(int wx, int wz, double lx, double lz);
    }

    private static void columns(Frame f, double x0, double x1, double z0, double z1, ColumnAt out) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] c : new double[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            Vec3 w = f.world(c[0], 0, c[1]);
            minX = Math.min(minX, w.x);
            maxX = Math.max(maxX, w.x);
            minZ = Math.min(minZ, w.z);
            maxZ = Math.max(maxZ, w.z);
        }
        for (int wx = Mth.floor(minX); wx <= Mth.floor(maxX); wx++) {
            for (int wz = Mth.floor(minZ); wz <= Mth.floor(maxZ); wz++) {
                double[] l = f.local(new Vec3(wx + 0.5, f.origin().y, wz + 0.5));
                if (l[0] >= x0 && l[0] <= x1 && l[2] >= z0 && l[2] < z1) {
                    out.at(wx, wz, l[0], l[2]);
                }
            }
        }
    }

    private static void hit(LivingEntity caster, LivingEntity target, float damage, int stun, Vec3 velocity, ServerLevel level) {
        Combat.hurt(caster, target, damage);
        Stun.apply(target, stun);
        OpmNormal.launch(level, caster, target, velocity);
    }

    // ---------- Z: Soco Serio ----------

    private static final int SOCO_PUNCH = at(OpmShape.T_P), SOCO_TUBE = at(OpmShape.T_P + OpmShape.HSTOP);

    static boolean soco(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        begin(caster, level, "opm.soco_serio", SOCO_TUBE + 8, SOCO_TUBE + 2);
        int seed = seed();
        boolean breaks = BlockRules.canBreak(level);
        // Planted where he stands, the camera free: the punch goes where he looks when it lands.
        Runnable release = Freeze.holdLookFree(caster, SOCO_TUBE + 6);
        SubNet.send(level, SubNet.OPM_SOCO, caster.getId(), caster.position(), SubCommon.flat(caster.getLookAngle()), seed, 0, breaks ? 1 : 0);
        Frame[] frame = {null};
        Dig dig = new Dig(level);
        Set<LivingEntity> done = new HashSet<>();
        double[] dug = {0.6};
        Tasks.add(age -> {
            if (frame[0] == null && guard.broken()) {
                release.run();
                dig.close();
                SubNet.send(level, SubNet.OPM_SOCO, caster.getId(), caster.position(), Vec3.ZERO, seed, 2, 0);
                return true;
            }
            if (age < SOCO_PUNCH) {
                return false;
            }
            if (frame[0] == null) {
                // The fist lands: the aim is fixed now, everything ahead holds still for the hitstop.
                frame[0] = Frame.of(caster.position(), caster.getLookAngle());
                SubNet.send(level, SubNet.OPM_SOCO, caster.getId(), caster.position(), frame[0].forward(), seed, 1, breaks ? 1 : 0);
                SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.5f);
                for (LivingEntity e : Combat.inRadius(level, frame[0].world(0, 1.4, 4), 6, caster)) {
                    Freeze.hold(e, SOCO_TUBE - SOCO_PUNCH);
                }
                return false;
            }
            Frame f = frame[0];
            double tp = (age - SOCO_TUBE) / 20.0;
            if (tp < 0) {
                return false;
            }
            if (tp < 0.06) {
                SubCommon.sound(level, f.world(0, 1.4, 3), SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.6f);
                SubCommon.sound(level, f.world(0, 1.4, 3), SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.4f);
            }
            double front = OpmShape.frontAt(tp);
            // Whoever the tube's front has passed (each once): thrown along it, far.
            double r = OpmShape.rad(front) * 1.05 + 1;
            AABB box = new AABB(f.world(0, 0, Math.max(0, dug[0] - 2)), f.world(0, 0, front + 1)).inflate(r + 3, r + 6, r + 3);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && Combat.isTarget(e, caster))) {
                double[] l = f.local(Combat.center(e));
                if (l[2] > front + 1 || done.contains(e) || !OpmShape.inTube(l[0], l[1], l[2])) {
                    continue;
                }
                done.add(e);
                hit(caster, e, 300f, 60, f.dir(l[0] * 0.02, 0.45, 4.75), level);
            }
            // The tube and the trench under it, cut as the front passes.
            double to = Math.min(front, OpmShape.LMAX);
            if (dig.on() && to > dug[0]) {
                double w = Math.max(OpmShape.rad(to) * 1.05, OpmShape.groundW(to) * 1.4) + 1;
                double feet = f.origin().y;
                columns(f, -w, w, dug[0], to, (wx, wz, lx, lz) -> {
                    double rr = OpmShape.rad(lz) * 1.05, yc = OpmShape.yc(lz), h = OpmShape.hash(wx, wz, 1);
                    double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                    if (Math.abs(lx) < rr) {
                        double s = Math.sqrt(rr * rr - lx * lx);
                        lo = yc - s;
                        hi = yc + s;
                    }
                    double gw = OpmShape.groundW(lz) * (1 + 0.22 * (OpmShape.hash(0, Mth.floor(lz * 0.6), 7) - 0.5) * 2 + 0.12 * (h - 0.5));
                    if (Math.abs(lx) < gw) {
                        double u = lx / gw;
                        double depth = OpmShape.digD(lz) * (0.85 + 0.3 * h) * Math.max(0, 1 - u * u);
                        lo = Math.min(lo, -depth);
                        hi = Math.max(hi, 2.2);
                    }
                    if (hi > lo) {
                        dig.add(wx, wz, Mth.floor(feet + lo + 0.5), Mth.floor(feet + hi));
                    }
                });
                dug[0] = to;
            }
            if (tp > 3) {
                dig.close();
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- X: Saltos Laterais Serios ----------

    private static final int SL_START = at(OpmShape.SL0), SL_FINAL = at(OpmShape.SLF), SL_HIT = at(OpmShape.SLF + OpmShape.SL_STOP);

    static boolean saltos(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 32);
        if (target == null) {
            target = OpmNormal.front(caster, 16);
        }
        if (target == null) {
            SubCommon.warn(caster, "opm.sem_alvo");
            return false;
        }
        LivingEntity t = target;
        SubCommon.Guard guard = SubCommon.guard(caster);
        begin(caster, level, "opm.saltos", SL_HIT + 10, SL_HIT + 2);
        int seed = seed();
        Vec3 c = t.position();
        Frame f = Frame.of(c, c.subtract(caster.position()));
        double[] start = f.local(caster.position());
        double[][] hops = OpmShape.hops(start[0], start[2], seed);
        SubNet.send(level, SubNet.OPM_SALTOS, caster.getId(), c, f.forward(), seed, t.getId(), BlockRules.canBreak(level) ? 1 : 0);
        // It looks for him and can't find him: held where it stands until the punch.
        Runnable holdTarget = Freeze.hold(t, SL_HIT + 1);
        Runnable holdSelf = Freeze.hold(caster, SL_HIT + 4, age -> {
            double[] p = OpmShape.hopAt(hops, Math.min(age, SL_HIT) / 20.0);
            Vec3 w = f.world(p[0], 0, p[1]);
            return SubCommon.ground(level, new Vec3(w.x, c.y + 1, w.z)).add(0, p[2], 0);
        });
        Set<LivingEntity> pushed = new HashSet<>();
        int[] landed = {-1};
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                holdTarget.run();
                holdSelf.run();
                if (!t.isAlive() && !guard.broken()) {
                    SubNet.send(level, SubNet.OPM_SALTOS, caster.getId(), c, Vec3.ZERO, seed, t.getId(), 2);
                }
                return true;
            }
            double sec = age / 20.0;
            double[] p = OpmShape.hopAt(hops, Math.min(sec, OpmShape.SLF));
            Vec3 to = t.position().subtract(caster.position());
            Freeze.face(caster, (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG), 0);
            int i = (int) p[4];
            if (age >= SL_START && age < SL_FINAL) {
                if (i != landed[0] && i >= 1) {
                    // Each landing digs a little crater (they get deeper as he goes).
                    double[] a = hops[i];
                    double w = 0.55 + 0.5 * ((i - 1) / (double) OpmShape.SLN);
                    dent(level, f.world(a[0], 0, a[1]), 1.1 + 1.2 * w, 0.9 * w);
                    SubCommon.sound(level, f.world(a[0], 0, a[1]), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f + 0.02f * i);
                }
                landed[0] = i;
                // The torn ground floats round the target: the same spots the client lifts (same seed and tick).
                levitate(level, f, seed, age, i / (double) OpmShape.SLN);
                // Whoever else is that close is thrown out of it.
                for (LivingEntity e : Combat.inRadius(level, c, 4.4, caster)) {
                    if (e != t && pushed.add(e)) {
                        Vec3 out = SubCommon.flat(e.position().subtract(c));
                        hit(caster, e, 25f, 30, out.scale(0.8).add(0, 0.45, 0), level);
                    }
                }
            }
            if (age == SL_FINAL) {
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 2f, 0.5f);
            }
            if (age >= SL_HIT) {
                holdTarget.run();
                Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
                hit(caster, t, 280f, 60, dir.scale(6).add(0, 0.5, 0), level);
                dent(level, t.position(), 4.2, 2.3);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
                SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 2f, 1.2f);
                Tasks.later(4, holdSelf);
                return true;
            }
            return false;
        });
        return true;
    }

    // A small bowl where a foot or a fist came down: R wide, deep at the middle.
    public static void dent(ServerLevel level, Vec3 at, double rr, double deep) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(at.y) - 1;
        for (int x = Mth.floor(at.x - rr); x <= Mth.floor(at.x + rr); x++) {
            for (int z = Mth.floor(at.z - rr); z <= Mth.floor(at.z + rr); z++) {
                double r = Math.hypot(x + 0.5 - at.x, z + 0.5 - at.z);
                if (r >= rr) {
                    continue;
                }
                int d = (int) Math.round(deep * (1 - r * r / (rr * rr)) * (0.8 + 0.4 * OpmShape.hash(x, z, 3)));
                for (int y = top; y > top - d; y--) {
                    pos.set(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (breakable(level, pos, st)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                    }
                }
            }
        }
    }

    // The blocks the hops tear up this tick (the client lifts the very same ones): the top of each spot's column goes.
    private static void levitate(ServerLevel level, Frame f, int seed, int age, double kf) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        for (double[] s : OpmShape.levSpawns(seed, age, kf)) {
            Vec3 w = f.world(s[0], 0, s[1]);
            int x = Mth.floor(w.x), z = Mth.floor(w.z);
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (Math.abs(top.getY() - f.origin().y) > 4) {
                continue;
            }
            int n = s[2] > 1.4 ? 2 : 1;
            for (int d = 0; d < n; d++) {
                BlockPos p = top.below(d);
                BlockState st = level.getBlockState(p);
                if (breakable(level, p, st)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
                }
            }
        }
    }

    // ---------- C: Virada de Mesa Seria ----------

    private static final int MESA_GRAB = at(OpmShape.MG), MESA_UP = at(OpmShape.MF), MESA_VERT = at(OpmShape.MV), MESA_DOWN = at(OpmShape.MC);
    private static final int SI = 2 * OpmShape.SLAB_X, SJ = OpmShape.SLAB_DEEP + OpmShape.SLAB_UP, SK = OpmShape.SLAB_Z1 - OpmShape.SLAB_Z0;

    static boolean mesa(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        begin(caster, level, "opm.mesa", MESA_DOWN + 10, MESA_UP + 2);
        int seed = seed();
        Vec3 fwd = OpmShape.cardinal(caster.getLookAngle());
        int fx = (int) fwd.x, fz = (int) fwd.z;
        BlockPos o = BlockPos.containing(caster.position());
        Vec3 feet = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Frame f = Frame.of(feet, fwd);
        boolean breaks = BlockRules.canBreak(level);
        Runnable release = Freeze.hold(caster, MESA_DOWN + 4);
        SubNet.send(level, SubNet.OPM_MESA, caster.getId(), feet, fwd, seed, 0, breaks ? 1 : 0);
        // The slab as it was (null: left where it is: air, water, a chest, bedrock).
        BlockState[] slab = new BlockState[SI * SJ * SK];
        Set<LivingEntity> done = new HashSet<>();
        int[] cursor = {0, 0, 0};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Tasks.add(age -> {
            if (age < MESA_UP && guard.broken()) {
                release.run();
                SubNet.send(level, SubNet.OPM_MESA, caster.getId(), feet, Vec3.ZERO, seed, 2, 0);
                return true;
            }
            // Before it moves, the slab is read (a few rows of k a tick).
            if (breaks && age <= MESA_GRAB + 6) {
                int rows = Math.max(1, SK / (MESA_GRAB + 6));
                for (int n = 0; n < rows + 1 && cursor[0] < SK; n++, cursor[0]++) {
                    int k = cursor[0];
                    for (int i = 0; i < SI; i++) {
                        for (int j = 0; j < SJ; j++) {
                            BlockPos p = OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, j - OpmShape.SLAB_DEEP, k + OpmShape.SLAB_Z0);
                            BlockState st = level.getBlockState(p);
                            slab[(k * SI + i) * SJ + j] = breakable(level, p, st) ? st : null;
                        }
                    }
                }
            }
            if (age == MESA_GRAB) {
                SubCommon.sound(level, feet, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.5f);
            }
            if (age == MESA_UP) {
                SubCommon.sound(level, f.world(0, 0, 30), SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.4f);
                SubCommon.sound(level, f.world(0, 0, 30), SoundEvents.ANVIL_LAND, 2f, 0.4f);
            }
            if (age == MESA_UP + 1) {
                // Whoever stood on it is thrown up by it (higher nearer the far edge, which rises fastest).
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, slabBox(f, OpmShape.SLAB_Z0, OpmShape.SLAB_Z1, -14, 12),
                        e -> e != caster && e.isAlive() && Combat.isTarget(e, caster))) {
                    double[] l = f.local(e.position());
                    if (Math.abs(l[0]) <= OpmShape.SLAB_X && l[2] >= OpmShape.SLAB_Z0 - 0.5 && l[2] <= OpmShape.HINGE && done.add(e)) {
                        double arm = OpmShape.HINGE - l[2];
                        hit(caster, e, 60f, 40, f.dir((SubCommon.RANDOM.nextDouble() - 0.5) * 0.3, (16 + arm * 0.75) / 20, (-6 + SubCommon.RANDOM.nextDouble() * 8) / 20), level);
                    }
                }
            }
            // While it rises, its old place empties (the client draws it turning).
            if (breaks && age >= MESA_UP && age < MESA_VERT + 4) {
                int span = MESA_VERT + 4 - MESA_UP;
                int k0 = (age - MESA_UP) * SK / span, k1 = (age - MESA_UP + 1) * SK / span;
                for (int k = k0; k < k1; k++) {
                    for (int i = 0; i < SI; i++) {
                        for (int j = 0; j < SJ; j++) {
                            if (slab[(k * SI + i) * SJ + j] != null) {
                                level.setBlock(OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, j - OpmShape.SLAB_DEEP, k + OpmShape.SLAB_Z0), Blocks.AIR.defaultBlockState(), QUIET);
                            }
                        }
                    }
                }
            }
            if (age == MESA_DOWN) {
                SubCommon.sound(level, f.world(0, 4, OpmShape.HINGE + 33), SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.3f);
                SubCommon.sound(level, f.world(0, 4, OpmShape.HINGE + 33), SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.4f);
                // It comes down upside down on the far side: whatever is there is crushed and thrown out sideways.
                double far = OpmShape.HINGE + SK;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, slabBox(f, OpmShape.HINGE, far + 2, -4, 16),
                        e -> e != caster && e.isAlive() && Combat.isTarget(e, caster))) {
                    double[] l = f.local(e.position());
                    if (Math.abs(l[0]) <= OpmShape.SLAB_X + 2 && l[2] >= OpmShape.HINGE && l[2] <= far + 2) {
                        hit(caster, e, 220f, 50, f.dir(Math.signum(l[0] == 0 ? 1 : l[0]) * 0.9, 0.5, 0.3), level);
                    }
                }
            }
            // ... and its blocks land there, upside down (k' = 135 - k, j' = -1 - j), crushing what stood above.
            if (breaks && age >= MESA_DOWN && age < MESA_DOWN + 20) {
                int k0 = (age - MESA_DOWN) * SK / 20, k1 = (age - MESA_DOWN + 1) * SK / 20;
                for (int k = k0; k < k1; k++) {
                    int kk = 2 * OpmShape.SLAB_Z1 - 1 - (k + OpmShape.SLAB_Z0);
                    for (int i = 0; i < SI; i++) {
                        for (int j = 0; j < SJ; j++) {
                            int jj = -1 - (j - OpmShape.SLAB_DEEP);
                            BlockState src = slab[(k * SI + i) * SJ + j];
                            pos.set(OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, jj, kk));
                            BlockState here = level.getBlockState(pos);
                            boolean free = here.isAir() || breakable(level, pos, here) || here.canBeReplaced();
                            if (!free) {
                                continue;
                            }
                            if (src != null) {
                                level.setBlock(pos, src, QUIET);
                            } else if (jj >= 0 && !here.isAir()) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                            }
                        }
                        // What stood higher than the slab is crushed too.
                        for (int jj = SJ - OpmShape.SLAB_UP; jj < SJ; jj++) {
                            pos.set(OpmShape.cell(o, fx, fz, i - OpmShape.SLAB_X, jj, kk));
                            BlockState here = level.getBlockState(pos);
                            if (breakable(level, pos, here)) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
                            }
                        }
                    }
                }
            }
            return age >= MESA_DOWN + 20 || (!breaks && age >= MESA_DOWN);
        });
        return true;
    }

    // The box over part of the slab's ground (k from k0 to k1, y from feet + y0 to feet + y1).
    private static AABB slabBox(Frame f, double k0, double k1, double y0, double y1) {
        return new AABB(f.world(-OpmShape.SLAB_X - 2, y0, k0), f.world(OpmShape.SLAB_X + 2, y1, k1));
    }

    // ---------- V: Espirro Serio ----------

    private static final int SN_SAY1 = at(OpmShape.E1), SN_SAY2 = at(OpmShape.E2), SN_OUT = at(OpmShape.EX), SN_SHELL = at(OpmShape.EX + 0.2);

    static boolean espirro(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        begin(caster, level, "opm.espirro", SN_SHELL + 6, SN_OUT + 2);
        int seed = seed();
        boolean breaks = BlockRules.canBreak(level);
        Runnable release = Freeze.holdLookFree(caster, SN_SHELL + 4);
        SubNet.send(level, SubNet.OPM_ESPIRRO, caster.getId(), caster.position(), SubCommon.flat(caster.getLookAngle()), seed, 0, breaks ? 1 : 0);
        Frame[] frame = {null};
        Dig dig = new Dig(level);
        Set<LivingEntity> done = new HashSet<>();
        double[] dug = {2};
        Tasks.add(age -> {
            if (frame[0] == null && guard.broken()) {
                release.run();
                dig.close();
                SubNet.send(level, SubNet.OPM_ESPIRRO, caster.getId(), caster.position(), Vec3.ZERO, seed, 2, 0);
                return true;
            }
            if (age == SN_SAY1 || age == SN_SAY2) {
                SubCommon.sound(level, caster.getEyePosition(), SoundEvents.HORSE_BREATHE, 1.5f, age == SN_SAY1 ? 0.8f : 1.1f);
            }
            if (age < SN_OUT) {
                return false;
            }
            if (frame[0] == null) {
                frame[0] = Frame.of(caster.position(), caster.getLookAngle());
                SubNet.send(level, SubNet.OPM_ESPIRRO, caster.getId(), caster.position(), frame[0].forward(), seed, 1, breaks ? 1 : 0);
                SubCommon.sound(level, caster.getEyePosition(), SoundEvents.PANDA_SNEEZE, 3f, 0.5f);
                SubCommon.sound(level, caster.getEyePosition(), SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
                return false;
            }
            Frame f = frame[0];
            double tp = (age - SN_SHELL) / 20.0;
            if (tp < 0) {
                return false;
            }
            double front = OpmShape.snFront(tp);
            AABB box = new AABB(f.origin(), f.origin()).inflate(front + 2, 24, front + 2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && Combat.isTarget(e, caster))) {
                double[] l = f.local(e.position());
                double r = Math.hypot(l[0], l[2]);
                if (l[2] <= 0 || r > front + 1 || Math.abs(Math.atan2(l[0], l[2])) >= 0.7 || l[1] > 4 + r * 0.2 || !done.add(e)) {
                    continue;
                }
                Vec3 out = f.dir(l[0], 0, l[2]).normalize();
                hit(caster, e, 160f, 40, out.scale(3.75).add(0, 0.8, 0), level);
            }
            // The fan of ground goes once the front is 6 past (its full depth there), and what stood on it.
            double to = Math.min(front - 6, OpmShape.SN_L);
            if (dig.on() && (to > dug[0] || tp > OpmShape.SN_T)) {
                double end = tp > OpmShape.SN_T ? OpmShape.SN_L : to;
                double half = Math.sin(OpmShape.FAN) * end + 1;
                double feet = f.origin().y;
                double from = dug[0];
                columns(f, -half, half, Math.max(0, from * Math.cos(OpmShape.FAN) - 1), end, (wx, wz, lx, lz) -> {
                    double r = Math.hypot(lx, lz - 0.5);
                    if (r < from || r >= end) {
                        return;
                    }
                    double d = OpmShape.fanDepth(lx, lz, 1.0E9);
                    if (d <= 0.05) {
                        return;
                    }
                    dig.add(wx, wz, Mth.floor(feet - d + 0.5), Mth.floor(feet + 1.5 + Math.min(16, r * 0.15)));
                });
                dug[0] = end;
            }
            if (tp > OpmShape.SN_T + 0.5) {
                dig.close();
                return true;
            }
            return false;
        });
        return true;
    }
}
