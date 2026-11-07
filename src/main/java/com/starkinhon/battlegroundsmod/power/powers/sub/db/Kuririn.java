package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Kuririn (SubAlma of Dragon Ball, tier 8), the Visualizador's Kuririn tab.
// Z Kienzan: a thin yellow saw disc grows over the raised hand; thrown, it flies flat and cuts everything on its way
//   (blocks too) with very high damage, but it is easy to dodge. Held while it forms: two discs, one from each hand.
// X Taiyoken: hands by the face, a white flash from the head blinds everyone in front who was looking at him: stunned
//   6 s, white screen, mobs lose their target. A hit from the front wakes them on the spot (DbCommon.onIncomingDamage).
// C Kakusandan: a ki ball shoots up over the aimed spot and splits into ~30 thin rays that rain on the whole area.
public final class Kuririn {
    private static final int FORM = 30;
    private static final double DISC_SPEED = 0.5;
    private static final double DISC_RANGE = 32;
    private static final float DISC_RADIUS = 0.65f;
    private static final int BLIND_TICKS = 120;
    private static final double RAIN_AREA = 8;
    private static final float RAIN_SPREAD = 30;

    private Kuririn() {
    }

    public static Power create() {
        return new Power("kuririn", 0xFF7A1A, 1.0f, List.of(
                new Ability(180, 0, Kuririn::kienzan),
                new Ability(300, 0, Kuririn::taiyoken),
                new Ability(220, 0, Kuririn::kakusandan),
                new Ability(1000, 0, Kuririn::fanMissing)));
    }

    // ---------- Z: Kienzan ----------

    private static boolean kienzan(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int right = SubNet.newId();
        int left = SubNet.newId();
        int bossDouble = SubCommon.RANDOM.nextInt(3);
        boolean[] twin = {true};
        SubCommon.pose(caster, "db.disco_alto");
        SubCommon.busy(caster, FORM + 6);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.SAW, right, DbCommon.raised(caster), new Vec3(0, 1, 0), 0, Db.GOLD, 1);
                SubNet.send(level, SubNet.SAW, left, DbCommon.raised(caster), new Vec3(0, 1, 0), 0, Db.GOLD, 1);
                return true;
            }
            // Let go before it is formed and only one disc comes out; held to the end, two.
            if (!SubCommon.held(caster, age, bossDouble == 0 ? FORM + 1 : 0)) {
                twin[0] = false;
            }
            float k = SubCommon.smooth((age - 2) / 20f);
            float r = 0.2f + 0.45f * k;
            Vec3 overRight = DbCommon.hand(caster).add(0, 0.6, 0);
            Vec3 overLeft = DbCommon.handLeft(caster).add(0, 0.6, 0);
            boolean both = twin[0] && age > 10;
            SubNet.send(level, SubNet.SAW, right, both ? overRight : DbCommon.raised(caster).add(0, 0.35, 0), new Vec3(0, 1, 0), r, Db.GOLD, 0);
            if (both) {
                SubNet.send(level, SubNet.SAW, left, overLeft, new Vec3(0, 1, 0), r, Db.GOLD, 0);
            }
            if (age % 4 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.GRINDSTONE_USE, 0.5f, 1.6f + k * 0.4f);
            }
            if (age < FORM) {
                return false;
            }
            SubCommon.pose(caster, "db.arremesso");
            SubCommon.sound(level, caster.position(), SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.3f);
            Vec3 dir = caster.getLookAngle();
            Vec3 side = SubCommon.flat(dir).cross(new Vec3(0, 1, 0));
            if (both) {
                fly(caster, level, right, overRight, dir.add(side.scale(0.06)).normalize());
                fly(caster, level, left, overLeft, dir.add(side.scale(-0.06)).normalize());
            } else {
                fly(caster, level, right, DbCommon.raised(caster).add(0, 0.35, 0), dir);
            }
            return true;
        });
        return true;
    }

    // One disc flying flat and straight: hurts each one once, cuts the blocks in its plane, stops at range.
    private static void fly(LivingEntity caster, ServerLevel level, int id, Vec3 start, Vec3 dir) {
        Set<Integer> cut = DbCommon.once();
        int life = (int) (DISC_RANGE / DISC_SPEED);
        Vec3[] last = {start};
        Tasks.add(age -> {
            Vec3 p = start.add(dir.scale(age * DISC_SPEED));
            boolean end = age >= life;
            SubNet.send(level, SubNet.SAW, id, p, new Vec3(0, 1, 0), DISC_RADIUS, Db.GOLD, end ? 1 : 0);
            for (LivingEntity t : Combat.alongLine(level, last[0], p, DISC_RADIUS + 0.2, caster)) {
                if (cut.add(t.getId())) {
                    Combat.hurtMagic(caster, t, 18);
                    Stun.apply(t, 12);
                    DbCommon.hit(level, Combat.center(t), 0.7f, Db.GOLD);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
                }
            }
            cutBlocks(level, p);
            if (age % 5 == 0) {
                SubCommon.sound(level, p, SoundEvents.GRINDSTONE_USE, 0.6f, 1.8f);
            }
            last[0] = p;
            return end;
        });
    }

    // The blocks the disc's edge passes through, in its own flat plane (a thin slice, like cutting a pillar in two).
    private static void cutBlocks(ServerLevel level, Vec3 p) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int r = (int) Math.ceil(DISC_RADIUS);
        BlockPos center = BlockPos.containing(p);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                BlockPos pos = center.offset(x, 0, z);
                if (Vec3.atCenterOf(pos).subtract(p).multiply(1, 0, 1).length() > DISC_RADIUS + 0.3) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                float hard = state.getDestroySpeed(level, pos);
                if (!state.isAir() && hard >= 0 && hard < 10 && !state.hasBlockEntity()) {
                    level.destroyBlock(pos, false);
                }
            }
        }
    }

    // ---------- X: Taiyoken ----------

    private static boolean taiyoken(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int flash = 16;
        SubCommon.pose(caster, "db.rosto");
        SubCommon.busy(caster, flash + 8);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < flash) {
                return false;
            }
            Vec3 head = caster.getEyePosition().add(0, 0.15, 0);
            Vec3 look = caster.getLookAngle();
            SubNet.send(level, SubNet.SOLAR, 0, head, look, 0, 0, 0);
            SubCommon.sound(level, head, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
            SubCommon.sound(level, head, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.5f, 1.2f);
            int caught = 0;
            for (LivingEntity t : Combat.inCone(caster, 10, 0.35)) {
                // Only who was looking his way gets blinded; whoever had their back to him sees nothing.
                Vec3 toMe = SubCommon.flat(caster.position().subtract(t.position()));
                if (toMe.dot(SubCommon.flat(t.getLookAngle())) < 0.1) {
                    continue;
                }
                blind(level, t);
                caught++;
            }
            if (caught == 0) {
                SubCommon.warn(caster, "kuririn.taiyoken_miss");
            }
            return true;
        });
        return true;
    }

    private static void blind(ServerLevel level, LivingEntity t) {
        SubCommon.setState(t, DbCommon.BLIND, BLIND_TICKS);
        Stun.apply(t, BLIND_TICKS);
        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, false));
        SubNet.send(level, SubNet.DIZZY, t.getId(), t.position(), Vec3.ZERO, BLIND_TICKS, 0, 0);
        DbCommon.loseTarget(t);
        // Mobs and bosses keep looking for a target; while blind they can't hold one.
        Tasks.add(age -> {
            if (!t.isAlive() || !SubCommon.hasState(t, DbCommon.BLIND)) {
                return true;
            }
            DbCommon.loseTarget(t);
            if (age % 10 == 0) {
                SubCommon.sound(level, t.position(), SoundEvents.PLAYER_BREATH, 0.5f, 0.8f);
            }
            return false;
        });
    }

    // ---------- C: Kakusandan ----------

    private static boolean kakusandan(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 aim = SubCommon.aimPoint(caster, 30);
        Vec3 mid = SubCommon.ground(level, aim);
        Vec3 top = mid.add(0, 8, 0);
        int up = 8;
        int split = 16;
        int seed = SubCommon.RANDOM.nextInt(16);
        int ball = SubNet.newId();
        SubCommon.pose(caster, "db.lanca_alto");
        SubCommon.busy(caster, up + 4);
        Vec3[] from = {null};
        Tasks.add(age -> {
            if (guard.broken() && age < up) {
                return true;
            }
            if (age < up) {
                return false;
            }
            if (from[0] == null) {
                from[0] = DbCommon.hand(caster).add(0, 0.4, 0);
                SubCommon.sound(level, from[0], SoundEvents.FIRECHARGE_USE, 1.2f, 1.5f);
            }
            if (age < split) {
                // Shoots up fast to 8 blocks over the area.
                Vec3 p = from[0].lerp(top, (age - up) / (double) (split - up));
                SubNet.send(level, SubNet.KI_BALL, ball, p, Vec3.ZERO, 0.3f, Db.GOLD + 32, 0);
                return false;
            }
            SubNet.send(level, SubNet.KI_BALL, ball, top, Vec3.ZERO, 0, Db.GOLD, 1);
            SubNet.send(level, SubNet.RAIN, 0, top, mid, (float) RAIN_AREA, RAIN_SPREAD, seed);
            SubCommon.sound(level, top, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1.4f);
            // Each ray lands when the client draws it land (same hash, same times).
            for (int n = 0; n < 30; n++) {
                int i = n;
                int land = 14 + (int) Db.rainTime(seed, i, RAIN_SPREAD) + 4;
                Vec3 spot = mid.add(Db.rainSpot(seed, i, RAIN_AREA));
                Tasks.later(land, () -> {
                    for (LivingEntity t : Combat.inRadius(level, spot.add(0, 0.6, 0), 1.6, caster)) {
                        Combat.hurtMagic(caster, t, 3);
                        Stun.apply(t, 6);
                    }
                    if (i % 3 == 0) {
                        SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
                    }
                });
            }
            return true;
        });
        return true;
    }

    // ---------- V: not made yet in the Visualizador ----------

    private static boolean fanMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
