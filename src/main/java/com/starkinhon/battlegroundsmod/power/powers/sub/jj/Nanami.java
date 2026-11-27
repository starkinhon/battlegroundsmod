// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.item.NanamiBladeItem;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Nanami (SubAlma of Jujutsu, tier 7), kit remade with Stark (2026-11-27, preview forja/nanami-kit/nanami-shinjuku.js v7):
// the Ratio Technique. The 7:3 mark is now the manga's ruler (a thick black bar with ten parts, the ink splat on the 7:3
// point, "7:3" over it in the Minecraft font, a dot running and swelling as it passes the point). Criticals never flash
// on and off: one impact frame each.
// Passive Expediente: after a minute of fighting his blows weigh more (+20%).
// Z 7:3: he measures the target with his hand and the ruler shows up on them (3 s): his blow on the point is a critical
//   (2x) with the 2 s stun. Press again: he steps in and strikes it. With the cleaver in his hand his plain click marks
//   too: the first cut lands normal and leaves the ruler, the next blow on the point is the critical.
// X Collapse: the ruler on the 7:3 point of a wall; he punches it, a real crack runs straight from the ground to the top
//   and the wall comes down in a chain, block pulling block from the crack, falling forward: whoever is under it is
//   caught in the rubble (2 s stun). On the ground: the crack runs straight 12 blocks ahead and the ground sinks in a
//   chain behind it; whoever is on the line falls in and is caught.
// C Wrapped Blade: 3 fast criticals on the 7:3 point (no need to measure first); the cloth snaps on every blow and on the
//   last it comes loose whole and flies unrolling, and the target is thrown far.
// V Overtime: he loosens the tie and wraps it round his fist, the scene goes to the warm tone of the end of the afternoon,
//   and for 12 s everyone round him is marked with the 7:3 on their own. V again: he jumps and punches the 7:3 point of
//   the ground: the whole area sinks and takes them down with it (that ends it).
// B Counter of the Office: stays as it was.
// The 2 s stun of a 7:3 hit is full on bosses of his tier or weaker, shorter on stronger ones (Stark, 2026-11-11).
public final class Nanami {
    private static final int MARK = 60;
    public static final String OVERTIME_FIRST = "jj_overtime_first";
    public static final String COUNTER = "jj_nanami_counter";
    private static final int OVERTIME_TICKS = 240;
    private static final int OVERTIME_RANGE = 12;
    // Who each Nanami marked with 7:3 (with Z or the cleaver; the Overtime marks everyone apart, MARKED_ALL).
    private static final Map<UUID, LivingEntity> MARKED = new HashMap<>();
    // Marks left by the cleaver's click: hitting them doesn't touch the Z's cooldown.
    private static final Set<UUID> BY_BLADE = new HashSet<>();
    // The ones the Overtime marked, per Nanami.
    private static final Map<UUID, Set<LivingEntity>> MARKED_ALL = new HashMap<>();
    // The Nanamis whose Overtime is waiting for the second V.
    private static final Set<UUID> OVERTIME_ON = new HashSet<>();

    private Nanami() {
    }

    public static Power create() {
        return new Power("nanami", 0xD9B86A, 1.05f, List.of(
                new Ability(160, 0, Nanami::ratio),
                new Ability(300, 0, Nanami::collapse),
                new Ability(200, 0, Nanami::blade),
                new Ability(900, 0, Nanami::overtime),
                new Ability(240, 0, Nanami::counterStance)));
    }

    // The stun of a blow on the 7:3 point: 2 s, full on bosses of his tier or weaker, less the stronger they are.
    static void ratioStun(LivingEntity caster, LivingEntity t) {
        int ticks = 40;
        if (JjCommon.isBoss(t)) {
            int gap = Tiers.of(caster) - Tiers.of(t);
            if (gap > 0) {
                ticks = Math.max(16, 40 - 8 * gap);
            }
        }
        Stun.apply(t, ticks, true);
    }

    // The critical (preview critFx2): flash, a gold ring, two cuts crossing, ONE impact frame and a shake.
    private static void critFx(ServerLevel level, LivingEntity t, Vec3 dir, boolean big) {
        Vec3 c = Combat.center(t);
        SubNet.send(level, Jj2Net.NANAMI_CRIT, 0, c, dir, big ? 1 : 0, 0, 0);
        SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
        SubCommon.sound(level, c, SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
    }

    // The ruler on someone (ticks; 0 takes it off).
    static void ruler(ServerLevel level, LivingEntity t, int ticks) {
        SubNet.send(level, Jj2Net.NANAMI_RULER, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
    }

    // ---------- Z: 7:3 ----------

    private static boolean ratio(LivingEntity caster, ServerLevel level) {
        LivingEntity marked = MARKED.get(caster.getUUID());
        if (marked != null && marked.isAlive() && SubCommon.hasState(marked, JjCommon.RATIO) && marked.distanceTo(caster) < 12) {
            crit(caster, level, marked);
            return false;
        }
        LivingEntity t = JjCommon.target(caster, 14);
        if (t == null) {
            SubCommon.warn(caster, "nanami.no_target");
            return false;
        }
        SubCommon.pose(caster, "jj.ratio_cut");
        SubCommon.busy(caster, 6);
        DbCommon.cooldown(caster, 0, 10);
        mark(caster, level, t, false);
        SubCommon.sound(level, caster.position(), SoundEvents.BOOK_PAGE_TURN, 1f, 1.2f);
        return false;
    }

    private static void mark(LivingEntity caster, ServerLevel level, LivingEntity t, boolean blade) {
        SubCommon.setState(t, JjCommon.RATIO, MARK);
        MARKED.put(caster.getUUID(), t);
        if (blade) {
            BY_BLADE.add(caster.getUUID());
        } else {
            BY_BLADE.remove(caster.getUUID());
        }
        ruler(level, t, MARK);
        SubCommon.sound(level, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
        watch(caster, level, t, blade);
    }

    // The mark fades without the critical: the cooldown starts then. Mobs strike it on their own.
    private static void watch(LivingEntity caster, ServerLevel level, LivingEntity t, boolean blade) {
        Tasks.add(age -> {
            if (MARKED.get(caster.getUUID()) != t) {
                return true;
            }
            if (!t.isAlive() || !SubCommon.hasState(t, JjCommon.RATIO) || !caster.isAlive()) {
                MARKED.remove(caster.getUUID());
                ruler(level, t, 0);
                if (!blade) {
                    DbCommon.cooldown(caster, 0, 140);
                }
                return true;
            }
            if (!blade && !(caster instanceof Player) && age == 16 && t.distanceTo(caster) < 10) {
                crit(caster, level, t);
                return true;
            }
            return false;
        });
    }

    // His cleaver's click on someone not marked: the cut lands normal and leaves the ruler (the next blow on the point is
    // the critical). Called from the damage hook after onPoint.
    static void bladeClick(LivingEntity caster, ServerLevel level, LivingEntity t) {
        if (!(caster.getMainHandItem().getItem() instanceof NanamiBladeItem) || SubCommon.hasState(t, JjCommon.RATIO)) {
            return;
        }
        PowerData data = caster.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER);
        if (data.busyTicks > 0) {
            // a skill's own blow, not the plain click
            return;
        }
        LivingEntity old = MARKED.get(caster.getUUID());
        if (old != null && old != t && old.isAlive() && SubCommon.hasState(old, JjCommon.RATIO)) {
            SubCommon.clearState(old, JjCommon.RATIO);
            ruler(level, old, 0);
        }
        mark(caster, level, t, true);
    }

    // A blow of his that lands on someone he measured (or that the Overtime marked): the point. Called from the damage
    // hook; true if it was.
    static boolean onPoint(LivingEntity caster, ServerLevel level, LivingEntity t) {
        Set<LivingEntity> all = MARKED_ALL.get(caster.getUUID());
        boolean overtime = all != null && all.contains(t);
        if ((MARKED.get(caster.getUUID()) != t && !overtime) || !SubCommon.hasState(t, JjCommon.RATIO)) {
            return false;
        }
        SubCommon.clearState(t, JjCommon.RATIO);
        ruler(level, t, 0);
        if (overtime) {
            all.remove(t);
        }
        if (MARKED.get(caster.getUUID()) == t) {
            MARKED.remove(caster.getUUID());
            if (!BY_BLADE.remove(caster.getUUID())) {
                DbCommon.cooldown(caster, 0, 160);
            }
        }
        ratioStun(caster, t);
        critFx(level, t, SubCommon.flat(t.position().subtract(caster.position())), true);
        return true;
    }

    private static void crit(LivingEntity caster, ServerLevel level, LivingEntity t) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean overtime = SubCommon.hasState(caster, JjCommon.OVERTIME);
        Vec3 from = caster.position();
        Vec3[] at = {from};
        int step = 4;
        SubCommon.pose(caster, "jj.ratio_crit");
        SubCommon.busy(caster, step + 8);
        JjCommon.carry(caster, step, at);
        SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), from, Vec3.ZERO, 2, 0, 0);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                Freeze.free(caster);
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 2, 0, 1);
                return true;
            }
            Vec3 to = t.position().subtract(SubCommon.flat(t.position().subtract(from)).scale(1.4));
            at[0] = from.lerp(to, SubCommon.easeOutCubic((age + 1) / (float) step));
            SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), at[0], Vec3.ZERO, 2, 0, age >= step ? 1 : 0);
            if (age < step) {
                return false;
            }
            Freeze.free(caster);
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            SubNet.send(level, SubNet.ARC, 0, caster.position().add(dir.scale(1.2)).add(0, 1.2, 0), dir, 1.8f, Db.GOLD, 0);
            Defense.breakGuard(t, caster);
            // The hook doubles it and stuns: it is on the point.
            Combat.hurt(caster, t, overtime ? 10 : 9);
            Combat.push(t, dir, 0.9, 0.3);
            return true;
        });
    }

    // ---------- X: Collapse ----------

    private static boolean collapse(LivingEntity caster, ServerLevel level) {
        Vec3 eye = caster.getEyePosition();
        Vec3 far = eye.add(caster.getLookAngle().scale(6));
        BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis().isHorizontal()) {
            return wall(caster, level, hit);
        }
        Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
                : SubCommon.ground(level, caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(1.6)));
        return groundLine(caster, level, point);
    }

    // The wall: the 7:3 point where he aims, the crack straight down to the ground and up to the top, then it all comes
    // down forward, block pulling block from the crack.
    private static boolean wall(LivingEntity caster, ServerLevel level, BlockHitResult hit) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Direction face = hit.getDirection();
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 along = new Vec3(-n.z, 0, n.x);
        BlockPos hp = hit.getBlockPos();
        // How far the wall goes: left and right along it (5 each at most), down to its foot and up to its top (12).
        int left = span(level, hp, along.scale(-1), 5);
        int right = span(level, hp, along, 5);
        int down = 0;
        while (down < 10 && solid(level, hp.below(down + 1))) {
            down++;
        }
        int up = 0;
        while (up < 12 && solid(level, hp.above(up + 1))) {
            up++;
        }
        BlockPos foot = hp.below(down);
        int height = down + up + 1;
        Vec3 point = hit.getLocation();
        Vec3 crackFrom = new Vec3(point.x, foot.getY() + 0.05, point.z).add(n.scale(0.02));
        Vec3 crackTo = new Vec3(point.x, foot.getY() + height, point.z).add(n.scale(0.02));
        int measure = 10;
        int punch = 12;
        int fall = punch + 7;
        int bury = fall + 14;
        Vec3 from = caster.position();
        Vec3 dir = SubCommon.flat(point.subtract(from)).normalize();
        SubCommon.pose(caster, "jj.ratio_cut");
        SubCommon.busy(caster, punch + 8);
        Freeze.holdLookFree(caster, punch + 2);
        // The ruler on the facade, its 7:3 on the point, 3.4 times the size of the one on a body.
        int rulerId = SubNet.newId();
        SubNet.send(level, Jj2Net.NANAMI_RULER, rulerId, point.add(n.scale(0.04)), n, punch + 6, 3.4f, 0);
        SubCommon.sound(level, point, SoundEvents.BOOK_PAGE_TURN, 1.2f, 1f);
        SubCommon.sound(level, point, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
        Tasks.add(age -> {
            if (guard.broken() && age < punch) {
                Freeze.free(caster);
                SubNet.send(level, Jj2Net.NANAMI_RULER, rulerId, point, n, 0, 3.4f, 0);
                return true;
            }
            if (age == measure) {
                SubCommon.pose(caster, "jj.div_punch");
            }
            if (age == punch) {
                Freeze.free(caster);
                SubNet.send(level, Jj2Net.NANAMI_CRIT, 0, point.add(n.scale(0.3)), dir, 1, 0, 0);
                SubNet.send(level, Jj2Net.NANAMI_CRACK, SubNet.newId(), crackFrom, crackTo.subtract(crackFrom), face.get3DDataValue(), 0, 0);
                SubCommon.sound(level, point, SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
                SubCommon.sound(level, point, SoundEvents.STONE_BREAK, 1.6f, 0.6f);
                SubCommon.sound(level, point, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
            }
            if (age == fall) {
                SubCommon.sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
                bringDown(level, foot, left, right, height, along, n, hp, point);
            }
            if (age == bury) {
                // Whoever was under it: caught in the rubble.
                double reach = Math.max(4, height * 0.8 + 1);
                Vec3 base = new Vec3(point.x, foot.getY(), point.z);
                AABB zone = new AABB(base, base.add(n.scale(reach))).inflate(Math.abs(along.x) * (Math.max(left, right) + 2) + 0.5, 0, Math.abs(along.z) * (Math.max(left, right) + 2) + 0.5)
                        .expandTowards(0, 3, 0).inflate(0.5);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != caster && e.isAlive() && Combat.isTarget(e, caster))) {
                    Combat.hurtMagic(caster, e, 15);
                    ratioStun(caster, e);
                    Freeze.hold(e, JjCommon.control(caster, e, 40));
                    SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, e.position()), Vec3.ZERO, 1.6f, 6, 1);
                    SubCommon.sound(level, e.position(), SoundEvents.STONE_BREAK, 1.4f, 0.5f);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // How many blocks the wall goes on from p along dir (the same row), up to max.
    private static int span(ServerLevel level, BlockPos p, Vec3 dir, int max) {
        int k = 0;
        while (k < max && solid(level, BlockPos.containing(p.getX() + 0.5 + dir.x * (k + 1), p.getY() + 0.5, p.getZ() + 0.5 + dir.z * (k + 1)))) {
            k++;
        }
        return k;
    }

    private static boolean solid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !BlockRules.keeps(s) && s.getDestroySpeed(level, p) >= 0;
    }

    // The chain: each block of the wall (3 deep at most) lets go after a delay that grows away from the crack and toward
    // the foot, and falls forward, tipping more the higher it was (preview: delay 0.05 + 0.09 per block from the crack +
    // 0.18 toward the foot; push 1.6 + 3.6 * height up out of the wall).
    private static void bringDown(ServerLevel level, BlockPos foot, int left, int right, int height, Vec3 along, Vec3 n, BlockPos hp, Vec3 point) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        List<Object[]> drops = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int a = -left; a <= right; a++) {
                for (int d = 0; d < 3; d++) {
                    BlockPos p = BlockPos.containing(foot.getX() + 0.5 + along.x * a - n.x * d, foot.getY() + y + 0.5, foot.getZ() + 0.5 + along.z * a - n.z * d);
                    if (!solid(level, p)) {
                        continue;
                    }
                    double upK = y / (double) Math.max(1, height);
                    double delay = 0.05 + Math.abs(a) * 0.09 + (1 - upK) * 0.18 + SubCommon.RANDOM.nextDouble() * 0.08;
                    drops.add(new Object[] {p, (int) Math.round(delay * 20), a, upK});
                }
            }
        }
        int cap = 320;
        for (int i = 0; i < drops.size() && i < cap; i++) {
            Object[] o = drops.get(i);
            BlockPos p = (BlockPos) o[0];
            int delay = (Integer) o[1];
            int a = (Integer) o[2];
            double upK = (Double) o[3];
            Tasks.later(Math.max(1, delay), () -> {
                if (!solid(level, p)) {
                    return;
                }
                BlockState state = level.getBlockState(p);
                FallingBlockEntity block = FallingBlockEntity.fall(level, p, state);
                block.dropItem = false;
                double tilt = 1.6 + upK * 3.6;
                Vec3 v = along.scale(a * 0.6 + (SubCommon.RANDOM.nextDouble() - 0.5) * 1.6).add(0, -0.5, 0)
                        .add(n.scale(tilt * (0.8 + SubCommon.RANDOM.nextDouble() * 0.9)));
                block.setDeltaMovement(v.scale(1 / 20.0));
                block.hurtMarked = true;
            });
        }
    }

    // The ground: the crack straight 12 blocks ahead from the 7:3 point, the ground sinking after it block by block.
    private static boolean groundLine(LivingEntity caster, ServerLevel level, Vec3 point) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 fwd = SubCommon.flat(caster.getLookAngle());
        fwd = fwd.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : fwd.normalize();
        Vec3 dir = fwd;
        Vec3 g0 = SubCommon.ground(level, point);
        int length = 12;
        int punch = 7;
        int chain = punch + 8;
        Set<LivingEntity> fell = new HashSet<>();
        SubCommon.pose(caster, "jj.slam_fist");
        SubCommon.busy(caster, punch + 8);
        Freeze.holdLookFree(caster, punch + 2);
        Tasks.add(age -> {
            if (guard.broken() && age < punch) {
                Freeze.free(caster);
                return true;
            }
            if (age == punch) {
                Freeze.free(caster);
                SubNet.send(level, Jj2Net.NANAMI_CRIT, 0, g0.add(0, 0.3, 0), dir, 1, 0, 0);
                SubNet.send(level, Jj2Net.NANAMI_CRACK, SubNet.newId(), g0.add(0, 0.03, 0), dir.scale(length), Direction.UP.get3DDataValue(), 1, 0);
                SubCommon.sound(level, g0, SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
                SubCommon.sound(level, g0, SoundEvents.STONE_BREAK, 1.6f, 0.6f);
            }
            int k = age - chain;
            if (k >= 0 && k % 1 == 0 && k < length) {
                // one step of the chain each tick (preview: 0.055 s)
                Vec3 at = SubCommon.ground(level, g0.add(dir.scale(0.6 + k)));
                JjCommon.crater(level, at, 1.35 + 0.25 * SubCommon.RANDOM.nextDouble(), 1.3 + 0.3 * SubCommon.RANDOM.nextDouble(), 3);
                if (k % 3 == 0) {
                    SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.8f);
                    DbCommon.screen(level, at, 24);
                }
                for (LivingEntity e : Combat.inRadius(level, at.add(0, 0.5, 0), 1.6, caster)) {
                    if (fell.add(e)) {
                        e.setDeltaMovement(0, 0.11, 0);
                        e.hurtMarked = true;
                    }
                }
            }
            if (k == length + 8) {
                for (LivingEntity e : fell) {
                    if (e.isAlive()) {
                        Combat.hurtMagic(caster, e, 14);
                        ratioStun(caster, e);
                        Freeze.hold(e, JjCommon.control(caster, e, 40));
                    }
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- C: Wrapped Blade ----------

    private static boolean blade(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 4.5);
        if (t == null || t.distanceTo(caster) > 4.8) {
            SubCommon.warn(caster, "nanami.miss");
            JjCommon.shorten(caster, 2, 30);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int gap = 4;
        SubCommon.busy(caster, gap * 3 + 4);
        Freeze.holdLookFree(caster, gap * 3 + 2);
        ruler(level, t, gap * 3 + 4);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                Freeze.free(caster);
                ruler(level, t, 0);
                return true;
            }
            if (age == 0 || age % gap != 0) {
                return false;
            }
            int n = age / gap;
            Vec3 dir = SubCommon.flat(t.position().subtract(caster.position()));
            SubCommon.pose(caster, n == 3 ? "jj.ratio_crit" : "jj.ratio_cut");
            SubNet.send(level, SubNet.ARC, 0, caster.position().add(dir.scale(1.2)).add(0, n == 2 ? 1.6 : 1.3, 0), dir, 1.6f + 0.2f * n, Db.GOLD, n == 2 ? 1 : 0);
            if (n == 1) {
                Defense.breakGuard(t, caster);
            }
            // Every cut lands on the point: a critical each.
            Combat.hurt(caster, t, 5 * 2);
            critFx(level, t, dir, n == 3);
            // The cloth snaps (and comes loose whole on the last).
            SubNet.send(level, Jj2Net.NANAMI_CLOTH, caster.getId(), DbCommon.hand(caster), dir, n == 3 ? 1 : 0, 0, 0);
            SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f + 0.15f * n);
            SubCommon.sound(level, caster.position(), SoundEvents.WOOL_BREAK, 1f, n == 3 ? 0.7f : 1.3f);
            if (n < 3) {
                Stun.apply(t, 8);
                return false;
            }
            ratioStun(caster, t);
            Combat.push(t, dir, 2.0, 0.45);
            Freeze.free(caster);
            ruler(level, t, 0);
            return true;
        });
        return true;
    }

    // ---------- B: Counter of the Office ----------

    private static boolean counterStance(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "jj.manji_guard");
        // 1.2 s (test with friends, 2026-11-25: 1 s was too tight to read).
        SubCommon.busy(caster, 24);
        SubCommon.setState(caster, COUNTER, 24);
        SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, 1, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, COUNTER) && !guard.broken();
            SubNet.send(level, SubNet.GUARD, caster.getId(), caster.position(), Vec3.ZERO, on ? 1 : 0, 0, on ? 0 : 1);
            if (!on) {
                if (age >= 23) {
                    JjCommon.shorten(caster, PowerData.SLOT_EXTRA, 100);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // A blow caught on the cloth: he answers on the 7:3 point. Called from the damage hook.
    static void counter(LivingEntity nanami, ServerLevel level, LivingEntity enemy) {
        SubCommon.clearState(nanami, COUNTER);
        Vec3 c = Combat.center(nanami).add(SubCommon.flat(enemy.position().subtract(nanami.position())).normalize().scale(0.6));
        DbCommon.hit(level, c, 0.9f, Db.WHITE);
        SubCommon.sound(level, c, SoundEvents.ANVIL_PLACE, 0.6f, 1.8f);
        SubCommon.pose(nanami, "jj.ratio_crit");
        Tasks.later(3, () -> {
            if (!enemy.isAlive() || enemy.distanceTo(nanami) > 7) {
                return;
            }
            Vec3 dir = SubCommon.flat(enemy.position().subtract(nanami.position()));
            SubNet.send(level, SubNet.ARC, 0, nanami.position().add(dir.scale(1.2)).add(0, 1.2, 0), dir, 1.8f, Db.GOLD, 0);
            Defense.breakGuard(enemy, nanami);
            Combat.hurt(nanami, enemy, 14);
            ratioStun(nanami, enemy);
            critFx(level, enemy, dir, true);
            Combat.push(enemy, dir, 1.3, 0.4);
        });
    }


    // ---------- V: Overtime ----------

    // He loosens the tie and wraps it round his fist; the scene goes warm and everyone near is marked on their own for
    // 12 s (heavier blows the whole time). The ult is spent here; the second V is the leap (overtimePress).
    private static boolean overtime(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        UUID id = caster.getUUID();
        int tie = 14;
        SubCommon.setState(caster, JjCommon.OVERTIME, OVERTIME_TICKS + tie);
        SubCommon.setState(caster, OVERTIME_FIRST, OVERTIME_TICKS + tie);
        OVERTIME_ON.add(id);
        MARKED_ALL.put(id, new HashSet<>());
        SubCommon.pose(caster, "jj.overtime");
        SubCommon.busy(caster, tie);
        Freeze.holdLookFree(caster, tie);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, tie, 3, false, false));
        SubNet.send(level, Jj2Net.NANAMI_TIE, caster.getId(), caster.position(), Vec3.ZERO, OVERTIME_TICKS + tie, 0, 0);
        SubNet.send(level, Jj2Net.NANAMI_WARM, caster.getId(), caster.position(), Vec3.ZERO, OVERTIME_TICKS + tie, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.WOOL_BREAK, 1f, 0.7f);
        SubCommon.sound(level, caster.position(), SoundEvents.BELL_BLOCK, 1.4f, 0.6f);
        Tasks.add(age -> {
            boolean on = OVERTIME_ON.contains(id) && SubCommon.hasState(caster, JjCommon.OVERTIME) && caster.isAlive() && !guard.broken();
            if (!on) {
                endOvertime(caster, level);
                return true;
            }
            if (age == tie) {
                Freeze.free(caster);
                SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2f, 0.8f);
            }
            // Everyone near gets the 7:3 on their own: right after the tie, then every second (a struck point comes back).
            if (age >= tie && (age - tie) % 20 == 0) {
                Set<LivingEntity> all = MARKED_ALL.get(id);
                int left = OVERTIME_TICKS + tie - age;
                for (LivingEntity e : Combat.inRadius(level, caster.position(), OVERTIME_RANGE, caster)) {
                    if (!SubCommon.hasState(e, JjCommon.RATIO)) {
                        SubCommon.setState(e, JjCommon.RATIO, left);
                        all.add(e);
                        ruler(level, e, left);
                    }
                }
                if (age == tie && !all.isEmpty()) {
                    SubCommon.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
                }
            }
            // Mobs leap on their own after 6 s, if someone is close.
            if (!(caster instanceof Player) && age == tie + 120) {
                LivingEntity t = JjCommon.target(caster, 10);
                if (t != null) {
                    slam(caster, level);
                }
            }
            return false;
        });
        return true;
    }

    // V again in Overtime: the leap and the punch on the ground's 7:3 point. True if the V was his (and so used).
    public static boolean overtimePress(ServerPlayer player) {
        if (!OVERTIME_ON.contains(player.getUUID()) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PowerData data = player.getData(com.starkinhon.battlegroundsmod.ModAttachments.POWER);
        if (data.busyTicks <= 0) {
            slam(player, level);
        }
        return true;
    }

    // The leap: he lands on the 7:3 point of the ground ahead (or of the one he aims at, 8 blocks at most), the area sinks
    // and takes them down; the ones marked are struck on the point. Ends the Overtime.
    private static void slam(LivingEntity caster, ServerLevel level) {
        UUID id = caster.getUUID();
        OVERTIME_ON.remove(id);
        SubCommon.Guard guard = SubCommon.guard(caster);
        int air = 10;
        Vec3 start = caster.position();
        Vec3 fwd = SubCommon.flat(caster.getLookAngle());
        fwd = fwd.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : fwd.normalize();
        LivingEntity aimed = JjCommon.target(caster, 9);
        Vec3 spot = SubCommon.ground(level, aimed != null && aimed.distanceTo(caster) < 8 ? aimed.position() : start.add(fwd.scale(3)));
        Vec3 dir = fwd;
        Vec3[] at = {start};
        SubCommon.pose(caster, "jj.slam_leap");
        SubCommon.busy(caster, air + 6);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, air + 2, 3, false, false));
        JjCommon.carry(caster, air, at);
        SubNet.send(level, Jj2Net.NANAMI_RULER, caster.getId() + 1_000_000, spot.add(0, 0.04, 0), new Vec3(0, 1, 0), air + 4, 2.6f, 0);
        SubCommon.sound(level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                endOvertime(caster, level);
                return true;
            }
            if (age < air) {
                float k = (age + 1) / (float) air;
                at[0] = start.lerp(spot, k).add(0, Math.sin(k * Math.PI) * 3.4, 0);
                if (age == air - 4) {
                    SubCommon.pose(caster, "jj.slam_fist");
                }
                return false;
            }
            Freeze.free(caster);
            SubNet.send(level, Jj2Net.NANAMI_CRIT, 0, spot.add(0, 0.4, 0), dir, 1, 0, 0);
            SubNet.send(level, SubNet.CRACKS, 0, spot, Vec3.ZERO, 6.5f, 14, 1);
            // The whole area sinks (preview: crater 6.5 wide, 2.8 deep, no crack).
            JjCommon.crater(level, spot, 6.5, 2.8, 70);
            Set<LivingEntity> all = MARKED_ALL.getOrDefault(id, Set.of());
            for (LivingEntity e : Combat.inRadius(level, spot.add(0, 0.8, 0), 8, caster)) {
                double d = e.position().distanceTo(spot);
                float k = (float) (1 - 0.45 * Math.min(1, d / 8));
                boolean point = all.contains(e) && SubCommon.hasState(e, JjCommon.RATIO);
                Combat.hurtMagic(caster, e, (point ? 30 : 20) * k);
                if (point) {
                    ratioStun(caster, e);
                } else {
                    Stun.apply(e, (int) (25 + 10 * k));
                }
                e.setDeltaMovement(e.getDeltaMovement().add(0, -0.8, 0));
                e.hurtMarked = true;
            }
            SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 0.55f);
            SubCommon.sound(level, spot, SoundEvents.ANVIL_LAND, 1f, 0.5f);
            endOvertime(caster, level);
            return true;
        });
    }

    private static void endOvertime(LivingEntity caster, ServerLevel level) {
        UUID id = caster.getUUID();
        OVERTIME_ON.remove(id);
        SubCommon.clearState(caster, JjCommon.OVERTIME);
        SubCommon.clearState(caster, OVERTIME_FIRST);
        SubNet.send(level, Jj2Net.NANAMI_TIE, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, Jj2Net.NANAMI_WARM, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        Set<LivingEntity> all = MARKED_ALL.remove(id);
        if (all != null) {
            for (LivingEntity e : all) {
                if (e.isAlive() && SubCommon.hasState(e, JjCommon.RATIO) && MARKED.values().stream().noneMatch(m -> m == e)) {
                    SubCommon.clearState(e, JjCommon.RATIO);
                    ruler(level, e, 0);
                }
            }
        }
    }

    // Called on the first hit he lands in Overtime: it cracks the ground under the target.
    static void firstHit(LivingEntity caster, ServerLevel level, LivingEntity target) {
        SubCommon.clearState(caster, OVERTIME_FIRST);
        Vec3 g = SubCommon.ground(level, target.position());
        SubNet.send(level, SubNet.CRACKS, 0, g, Vec3.ZERO, 2.4f, 8, 1);
        JjCommon.crater(level, g, 1.6, 0.8, 8);
        SubCommon.sound(level, g, SoundEvents.STONE_BREAK, 1.6f, 0.5f);
    }

    static void clear() {
        MARKED.clear();
        BY_BLADE.clear();
        MARKED_ALL.clear();
        OVERTIME_ON.clear();
    }
}
