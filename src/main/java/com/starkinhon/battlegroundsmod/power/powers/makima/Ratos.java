package com.starkinhon.battlegroundsmod.power.powers.makima;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Shift+C Ratos (forja/makima-ratos, approved 2026-11-11). Her eyes in the rats: 8 rats around (some by the enemies she
// can't see), the world goes gray and dark round a vignette for her, enemies show through walls as red ghosts and her
// yellow rings light over each rat from the nearest out (client/fx/MakimaFx). Shift+C again: she sinks into a pool of
// shadow (rats run out of it) and comes up jerkily beside the rat she looks at, facing the nearest enemy.
public final class Ratos {
    public static final int SLOT = PowerData.SLOT_SHIFT + 2;
    private static final int RATS = 8;
    private static final int VISION = 200;
    private static final int LIFE = 400;
    private static final double SEE_FROM_RAT = 18;
    private static final double SEE_FROM_HER = 48;
    private static final int SINK = 7;
    private static final int RISE = 9;

    private static final class Pack {
        final List<Vec3> rats = new ArrayList<>();
        final long start;
        boolean jumped;

        Pack(long start) {
            this.start = start;
        }
    }

    private static final Map<UUID, Pack> PACKS = new HashMap<>();

    private Ratos() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        Pack pack = new Pack(level.getGameTime());
        long seed = level.getGameTime() * 31 + caster.getId();
        java.util.Random r = new java.util.Random(seed);
        // Some by the enemies (behind their walls), the rest scattered round her.
        List<LivingEntity> enemies = new ArrayList<>();
        for (LivingEntity e : Combat.inRadius(level, caster.position(), 40, caster)) {
            if (!Makima.ours(caster, e) && e.isAlive()) {
                enemies.add(e);
            }
        }
        enemies.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        for (int i = 0; i < Math.min(4, enemies.size()); i++) {
            Vec3 at = enemies.get(i).position();
            double an = r.nextDouble() * Math.PI * 2;
            double d = 1.8 + r.nextDouble() * 2.2;
            pack.rats.add(spot(level, at.add(Math.cos(an) * d, 0, Math.sin(an) * d)));
        }
        while (pack.rats.size() < RATS) {
            double an = r.nextDouble() * Math.PI * 2;
            double d = 4 + Math.pow(r.nextDouble(), 0.8) * 18;
            pack.rats.add(spot(level, caster.position().add(Math.cos(an) * d, 0, Math.sin(an) * d)));
        }
        PACKS.put(caster.getUUID(), pack);
        // Sent nearest first (the client lights the rings in that order); c is the rat's own index (the vision picks by it).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pack.rats.size(); i++) {
            order.add(i);
        }
        order.sort(java.util.Comparator.comparingDouble(i -> pack.rats.get(i).distanceToSqr(caster.position())));
        for (int rank = 0; rank < order.size(); rank++) {
            int i = order.get(rank);
            MkNet.send(level, MkNet.RAT, caster.getId(), pack.rats.get(i), (float) (seed % 1000 + i * 13.7), LIFE, i);
        }
        if (caster instanceof ServerPlayer player) {
            MkNet.to(player, MkNet.RAT_VISION, caster.getId(), caster.position(), Vec3.ZERO, VISION, -1, 0);
        }
        SubCommon.pose(caster, "makima.ratos");
        Makima.eyes(caster, VISION);
        SubCommon.sound(level, caster.position(), SoundEvents.SILVERFISH_AMBIENT, 0.8f, 0.6f);
        DbCommon.cooldown(caster, SLOT, 500);
        SubCommon.Guard guard = SubCommon.guard(caster);
        Tasks.add(age -> {
            if (PACKS.get(caster.getUUID()) != pack) {
                return true;
            }
            if (guard.broken() || age >= LIFE) {
                end(level, caster, pack);
                return true;
            }
            if (age < VISION && age % 10 == 0) {
                // What the rats see, she knows (the Ritual's sheet).
                for (LivingEntity e : Combat.inRadius(level, caster.position(), SEE_FROM_HER, caster)) {
                    if (!Makima.ours(caster, e)) {
                        Ritual.see(caster, e, near(pack, e) ? "ratos" : "olhos");
                    }
                }
            }
            // A boss jumps by itself to the rat nearest its target.
            if (!(caster instanceof ServerPlayer) && age == 30) {
                press(caster);
            }
            return false;
        });
        return true;
    }

    private static boolean near(Pack pack, LivingEntity e) {
        for (Vec3 r : pack.rats) {
            if (r.distanceToSqr(e.position()) < SEE_FROM_RAT * SEE_FROM_RAT) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 spot(ServerLevel level, Vec3 at) {
        return SubCommon.ground(level, at);
    }

    // Shift+C again while her rats are out: she goes to the one she looks at.
    public static boolean press(LivingEntity caster) {
        Pack pack = PACKS.get(caster.getUUID());
        if (pack == null || pack.jumped || !(caster.level() instanceof ServerLevel level) || !Makima.is(caster)) {
            return false;
        }
        int pick = -1;
        double best = -2;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity foe = caster instanceof Mob mob ? mob.getTarget() : null;
        for (int i = 0; i < pack.rats.size(); i++) {
            Vec3 r = pack.rats.get(i);
            double score;
            if (foe != null) {
                score = -r.distanceTo(foe.position());
            } else {
                Vec3 to = r.add(0, 0.2, 0).subtract(eye);
                score = to.normalize().dot(look) - to.length() * 0.0015;
            }
            if (score > best) {
                best = score;
                pick = i;
            }
        }
        if (pick < 0) {
            return false;
        }
        pack.jumped = true;
        Vec3 rat = pack.rats.get(pick);
        Vec3 from = caster.position();
        // Beside the rat, facing the nearest enemy there.
        LivingEntity face = foe;
        if (face == null) {
            double d = Double.MAX_VALUE;
            for (LivingEntity e : Combat.inRadius(level, rat, 20, caster)) {
                if (!Makima.ours(caster, e) && e.distanceToSqr(rat) < d) {
                    d = e.distanceToSqr(rat);
                    face = e;
                }
            }
        }
        Vec3 off = face == null ? new Vec3(-0.6, 0, 0.5) : SubCommon.flat(face.position().subtract(rat)).normalize().scale(0.7);
        Vec3 dest = SubCommon.ground(level, rat.add(off.x, 0, off.z));
        float yaw = face == null ? caster.getYRot() : (float) (Math.atan2(face.getZ() - dest.z, face.getX() - dest.x) * 180 / Math.PI) - 90f;
        if (caster instanceof ServerPlayer player) {
            MkNet.to(player, MkNet.RAT_VISION, caster.getId(), rat, Vec3.ZERO, 30, pick, 0);
        }
        SubCommon.pose(caster, "makima.afunda");
        MkNet.send(level, MkNet.POOL, caster.getId(), SubCommon.ground(level, from), 1.7f, 36, 0);
        MkNet.send(level, MkNet.RATS_FLEE, caster.getId(), from, 5, 0, caster.getId() * 1.9f + level.getGameTime() % 97);
        MkNet.send(level, MkNet.SINK, caster.getId(), from, 0, SINK, 0);
        SubCommon.sound(level, from, SoundEvents.SCULK_BLOCK_SPREAD, 1.3f, 0.5f);
        SubCommon.sound(level, from, SoundEvents.SILVERFISH_AMBIENT, 1.0f, 0.8f);
        SubCommon.busy(caster, SINK + RISE + 2);
        Runnable[] hold = {Freeze.hold(caster, SINK + 1)};
        Tasks.later(SINK + 1, () -> {
            hold[0].run();
            if (!caster.isAlive()) {
                return;
            }
            if (caster instanceof ServerPlayer player) {
                player.connection.teleport(dest.x, dest.y, dest.z, yaw, player.getXRot());
            } else {
                caster.teleportTo(dest.x, dest.y, dest.z);
                caster.setYRot(yaw);
                caster.setYHeadRot(yaw);
            }
            Freeze.hold(caster, RISE);
            MkNet.send(level, MkNet.POOL, caster.getId(), dest, 1.7f, 40, 0);
            MkNet.send(level, MkNet.SINK, caster.getId(), dest, 1, RISE, 0);
            MkNet.send(level, MkNet.RAT, caster.getId(), rat, 0, -1, pick);
            SubCommon.sound(level, dest, SoundEvents.SCULK_BLOCK_SPREAD, 1.3f, 0.6f);
            SubCommon.sound(level, dest, SoundEvents.SILVERFISH_STEP, 1.0f, 1.2f);
            SubCommon.pose(caster, "makima.sobe");
            Tasks.later(RISE + 6, () -> end(level, caster, pack));
        });
        DbCommon.cooldown(caster, SLOT, 400);
        return true;
    }

    private static void end(ServerLevel level, LivingEntity caster, Pack pack) {
        if (PACKS.get(caster.getUUID()) != pack) {
            return;
        }
        PACKS.remove(caster.getUUID());
        for (int i = 0; i < pack.rats.size(); i++) {
            MkNet.send(level, MkNet.RAT, caster.getId(), pack.rats.get(i), 0, 0, i);
        }
        if (caster instanceof ServerPlayer player) {
            MkNet.to(player, MkNet.RAT_VISION, caster.getId(), caster.position(), Vec3.ZERO, 0, -1, 0);
        }
    }

    public static void clear() {
        PACKS.clear();
    }
}
