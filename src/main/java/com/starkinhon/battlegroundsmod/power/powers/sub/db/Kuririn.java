package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Kuririn (SubAlma of Dragon Ball, tier 8), the approved forja/kuririn-kit page in the Realista+Anime style.
// Z Kienzan: a real thin disc with 26 small saw teeth and rings of ki running inside forms over the raised hand; thrown,
//   it flies flat and cuts everything on its way: blocks (the part over the cut slides off and falls) and whoever is in
//   the line, without slowing down. Held while it forms: two discs, one from each hand.
// X Taiyoken: hands on the forehead, a white flash from the head blinds everyone in front who was looking at him:
//   stunned 6 s, white screen, mobs lose their target. A hit from the front wakes them on the spot.
// C Kakusan Koha: the ball shoots up very fast and hangs high, pulsing; it splits into 14 thin rays, each with its own
//   thickness, speed and delay, that rain on the enemies and the ground round them (small blast and hole each).
// V Kienzan Teleguiado: a bigger Kienzan (34 teeth); thrown, held key guides it with the finger, so it curves through
//   one target after another (hitstop on each cut) and cuts down whatever stands in its way.
public final class Kuririn {
    private static final int FORM = 22;
    private static final double DISC_SPEED = 1.3;
    private static final double DISC_RANGE = 34;
    private static final float DISC_RADIUS = 0.65f;
    private static final float BIG_RADIUS = 0.9f;
    private static final int BLIND_TICKS = 120;
    private static final double RAIN_AREA = 7;
    private static final int RAYS = 14;

    private Kuririn() {
    }

    public static Power create() {
        return new Power("kuririn", 0xFF7A1A, 1.0f, List.of(
                new Ability(180, 0, Kuririn::kienzan),
                new Ability(300, 0, Kuririn::taiyoken),
                new Ability(220, 0, Kuririn::kakusan),
                new Ability(500, 0, Kuririn::guided)));
    }

    private static void hitstop(LivingEntity e, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ModPayloads.Hitstop(e.getId(), ticks));
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
            float k = SubCommon.smooth(age / 16f);
            float r = DISC_RADIUS * k * (1 + 0.03f * Mth.sin(age * 2f));
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
        int[] slid = {0};
        Tasks.add(age -> {
            Vec3 p = start.add(dir.scale(age * DISC_SPEED));
            boolean end = age >= life;
            SubNet.send(level, SubNet.SAW, id, p, new Vec3(0, 1, 0), DISC_RADIUS, Db.GOLD, end ? 1 : 0);
            for (LivingEntity t : Combat.alongLine(level, last[0], p, DISC_RADIUS + 0.2, caster)) {
                if (cut.add(t.getId())) {
                    slash(caster, level, t, 18, false);
                }
            }
            cutBlocks(level, last[0], p, DISC_RADIUS, dir, slid, 24);
            if (age % 5 == 0) {
                SubCommon.sound(level, p, SoundEvents.GRINDSTONE_USE, 0.6f, 1.8f);
            }
            last[0] = p;
            return end;
        });
    }

    // A disc cutting someone: the damage, a hitstop, the manga frame and the shock where it went through.
    private static void slash(LivingEntity caster, ServerLevel level, LivingEntity t, float damage, boolean big) {
        Combat.hurtMagic(caster, t, damage);
        Stun.apply(t, 12);
        Vec3 c = Combat.center(t);
        DbCommon.hit(level, c, big ? 0.9f : 0.7f, Db.GOLD);
        SubNet.send(level, SubNet.KI_BOOM, 0, c, new Vec3(DbCommon.groundY(level, c), 0, 0), big ? 0.5f : 0.35f, Db.GOLD_W, 1000 + SubCommon.RANDOM.nextInt(100));
        DbCommon.screen(level, c, big ? 13 : 12);
        hitstop(t, big ? 3 : 2);
        hitstop(caster, big ? 3 : 2);
        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
    }

    // The blocks the disc's edge passed through from a to b, in its own flat plane (a thin slice, like cutting a pillar
    // in two); whatever stood on the cut slides off along the throw and falls (at most `max` blocks per disc).
    private static void cutBlocks(ServerLevel level, Vec3 a, Vec3 b, float radius, Vec3 dir, int[] slid, int max) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.5));
        int r = (int) Math.ceil(radius);
        Vec3 push = SubCommon.flat(dir);
        push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize();
        for (int s = 1; s <= steps; s++) {
            Vec3 p = a.lerp(b, s / (double) steps);
            BlockPos center = BlockPos.containing(p);
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, 0, z);
                    if (Vec3.atCenterOf(pos).subtract(p).multiply(1, 0, 1).length() > radius + 0.3) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    float hard = state.getDestroySpeed(level, pos);
                    if (state.isAir() || hard < 0 || hard >= 10 || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.destroyBlock(pos, false);
                    // What stood on the cut slides off: the higher, the more it tips along the throw.
                    for (int h = 1; h <= 10 && slid[0] < max; h++) {
                        BlockPos up = pos.above(h);
                        BlockState above = level.getBlockState(up);
                        if (above.isAir() || above.hasBlockEntity() || !above.getFluidState().isEmpty() || above.getDestroySpeed(level, up) < 0) {
                            break;
                        }
                        slid[0]++;
                        FallingBlockEntity block = FallingBlockEntity.fall(level, up, above);
                        block.dropItem = false;
                        block.setDeltaMovement(push.scale(0.12 + 0.05 * h).add(0, 0.05, 0));
                        block.hurtMarked = true;
                    }
                }
            }
        }
    }

    // ---------- X: Taiyoken ----------

    private static boolean taiyoken(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int flash = 9;
        SubCommon.pose(caster, "db.rosto");
        SubCommon.busy(caster, flash + 8);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < flash) {
                return false;
            }
            Vec3 head = caster.getEyePosition().add(caster.getLookAngle().scale(0.3)).add(0, 0.1, 0);
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

    // ---------- C: Kakusan Koha ----------

    private static boolean kakusan(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 aim = SubCommon.aimPoint(caster, 30);
        Vec3 mid = SubCommon.ground(level, aim);
        Vec3 apex = mid.lerp(caster.position(), 0.25).add(0, 8.5, 0);
        int push = 2;
        int rise = 5;
        int hang = 9;
        int ball = SubNet.newId();
        int salt = SubCommon.RANDOM.nextInt(1000);
        SubCommon.pose(caster, "db.empurra");
        SubCommon.busy(caster, push + rise + 4);
        Vec3[] from = {null};
        Tasks.add(age -> {
            if (guard.broken() && age < push + rise) {
                SubNet.send(level, SubNet.KI_BALL, ball, from[0] == null ? caster.position() : from[0], Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            if (age < push) {
                return false;
            }
            if (from[0] == null) {
                from[0] = DbCommon.hands(caster);
                SubCommon.sound(level, from[0], SoundEvents.FIRECHARGE_USE, 1.2f, 1.5f);
                SubNet.send(level, SubNet.KI_BOOM, 0, from[0], new Vec3(DbCommon.groundY(level, from[0]), 0, 0), 0.3f, Db.GOLD_W, 1000 + salt % 100);
            }
            int t = age - push;
            if (t < rise) {
                // Up very fast, easing into its spot high over the area.
                float e = SubCommon.easeOutCubic((t + 1) / (float) rise);
                SubNet.send(level, SubNet.KI_BALL, ball, from[0].lerp(apex, e), Vec3.ZERO, 0.28f + 0.24f * e, Db.GOLD + 48, 0);
                return false;
            }
            if (t < rise + hang) {
                if (t == rise) {
                    SubCommon.pose(caster, "db.dedo_ceu");
                }
                SubNet.send(level, SubNet.KI_BALL, ball, apex, Vec3.ZERO, 0.52f + 0.06f * Mth.sin(t * 1.3f), Db.GOLD + 48, 0);
                return false;
            }
            SubNet.send(level, SubNet.KI_BALL, ball, apex, Vec3.ZERO, 0, Db.GOLD, 1);
            SubNet.send(level, SubNet.KI_BOOM, 0, apex, new Vec3(DbCommon.groundY(level, apex), 0, 0), 0.6f, Db.GOLD_W, 1000 + salt % 100);
            SubCommon.sound(level, apex, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1.4f);
            // The first rays go for whoever is in the area; the rest fall on the ground round them.
            List<LivingEntity> foes = new ArrayList<>(Combat.inRadius(level, mid.add(0, 1, 0), RAIN_AREA, caster));
            boolean[] framed = {false};
            for (int n = 0; n < RAYS; n++) {
                int i = n;
                LivingEntity foe = !foes.isEmpty() && i < 6 ? foes.get(i % foes.size()) : null;
                Vec3 spot = foe != null
                        ? foe.position().add((Db.hash(i, 1, salt) - 0.5) * 0.5, 0.8 + Db.hash(i, 2, salt) * 0.9, 0)
                        : SubCommon.ground(level, mid.add((Db.hash(i, 3, salt) - 0.5) * RAIN_AREA * 1.6, 2, (Db.hash(i, 4, salt) - 0.5) * RAIN_AREA * 1.6));
                int delay = (int) (Db.hash(i, 5, salt) * 4.4f);
                float speed = 2.1f + Db.hash(i, 6, salt) * 0.8f;
                float w = (0.14f + Db.hash(i, 7, salt) * 0.12f) / 0.55f;
                ray(caster, level, apex, spot, foe, delay, speed, w, framed);
            }
            return true;
        });
        return true;
    }

    // One of the Kakusan's rays: a thin 3D beam from the apex to its spot; where it lands, a small blast and a hole.
    private static void ray(LivingEntity caster, ServerLevel level, Vec3 apex, Vec3 spot, LivingEntity foe, int delay, float speed, float w, boolean[] framed) {
        int id = SubNet.newId();
        double full = apex.distanceTo(spot);
        Vec3 dir = spot.subtract(apex).normalize();
        Tasks.add(age -> {
            int t = age - delay;
            if (t < 0) {
                return false;
            }
            double len = Math.min(full, (t + 1) * speed);
            boolean hit = len >= full;
            SubNet.send(level, SubNet.KI_BEAM, id, apex, dir.scale(len), w, Db.GOLD, hit ? 1 : 0);
            if (!hit) {
                return false;
            }
            double ground = DbCommon.groundY(level, spot);
            SubNet.send(level, SubNet.KI_BOOM, 0, spot, new Vec3(ground, 0, 0), 0.6f + w * 1.6f, Db.ORANGE, 1 + SubCommon.RANDOM.nextInt(900));
            if (spot.y - ground < 1.5) {
                JjCommon.crater(level, new Vec3(spot.x, ground, spot.z), foe != null ? 0.6 : 0.85, 0.5, 2);
            }
            boolean any = false;
            for (LivingEntity e : Combat.inRadius(level, spot, 1.6, caster)) {
                Combat.hurtMagic(caster, e, 3.5f);
                Stun.apply(e, 8);
                Combat.pushFrom(e, spot, 0.3, 0.45);
                any = true;
            }
            if (any && !framed[0]) {
                framed[0] = true;
                DbCommon.screen(level, spot, 46);
            }
            SubCommon.sound(level, spot, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.3f);
            return true;
        });
    }

    // ---------- V: Kienzan Teleguiado ----------

    private static boolean guided(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int id = SubNet.newId();
        int form = 28;
        int flight = 90;
        double speed = 0.75;
        double turn = 0.22;
        Set<Integer> cut = DbCommon.once();
        Vec3[] pos = {null};
        Vec3[] vel = {null};
        int[] slid = {0};
        int bossSteer = 30 + SubCommon.RANDOM.nextInt(40);
        SubCommon.pose(caster, "db.disco_alto");
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.SAW, id, pos[0] == null ? DbCommon.raised(caster) : pos[0], new Vec3(0, 1, 0), 0, Db.GOLD_W, 1);
                return true;
            }
            if (age < form) {
                SubCommon.busy(caster, 3);
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                float k = SubCommon.smooth(age / 22f);
                Vec3 p = DbCommon.hand(caster).add(0, 0.7, 0);
                SubNet.send(level, SubNet.SAW, id, p, new Vec3(0, 1, 0), BIG_RADIUS * k * (1 + 0.03f * Mth.sin(age * 1.85f)), Db.GOLD_W, 0);
                if (age % 4 == 0) {
                    SubCommon.sound(level, p, SoundEvents.GRINDSTONE_USE, 0.6f, 1.3f + k * 0.4f);
                }
                pos[0] = p;
                return false;
            }
            int t = age - form;
            if (t == 0) {
                SubCommon.pose(caster, "db.arremesso");
                SubCommon.sound(level, caster.position(), SoundEvents.TRIDENT_THROW.value(), 1.3f, 1.1f);
                vel[0] = caster.getLookAngle();
            }
            if (t == 4) {
                SubCommon.pose(caster, "db.apontar");
            }
            // The finger guides it: held, the disc turns toward where he looks (bosses: toward their target).
            boolean steer = caster instanceof Player ? SubCommon.held(caster, t, 0) : t < bossSteer;
            if (steer && t > 2) {
                Vec3 goal;
                LivingEntity foe = caster instanceof Player ? null : SubCommon.aimed(caster, 40);
                goal = foe != null ? Combat.center(foe) : SubCommon.aimPoint(caster, 40);
                Vec3 want = goal.subtract(pos[0]);
                if (want.lengthSqr() > 0.25) {
                    want = want.normalize();
                    double dot = Mth.clamp(vel[0].dot(want), -1, 1);
                    double ang = Math.acos(dot);
                    double k = ang < 1.0E-4 ? 1 : Math.min(1, turn / ang);
                    vel[0] = vel[0].lerp(want, k).normalize();
                }
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false));
            }
            Vec3 before = pos[0];
            Vec3 p = before.add(vel[0].scale(speed));
            pos[0] = p;
            boolean end = t >= flight;
            // Tilted a little into its turns.
            Vec3 bank = new Vec3(0, 1, 0).add(vel[0].cross(new Vec3(0, 1, 0)).scale(0.25 * Mth.sin(t * 0.2f)));
            SubNet.send(level, SubNet.SAW, id, p, bank.normalize(), BIG_RADIUS, Db.GOLD_W, end ? 1 : 0);
            for (LivingEntity e : Combat.alongLine(level, before, p, BIG_RADIUS + 0.25, caster)) {
                if (cut.add(e.getId())) {
                    slash(caster, level, e, 16, true);
                    Combat.push(e, vel[0], 0.6, 0.45);
                }
            }
            cutBlocks(level, before, p, BIG_RADIUS, vel[0], slid, 40);
            if (t % 4 == 0) {
                SubCommon.sound(level, p, SoundEvents.GRINDSTONE_USE, 0.7f, 1.6f);
            }
            return end;
        });
        return true;
    }
}
