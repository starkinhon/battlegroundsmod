package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Piccolo (SubAlma of Dragon Ball, tier 7), the approved forja/piccolo-kit page in the Realista+Anime style.
// Z Masenko: both hands over the head, a yellow ball crackling between them; the arms come down and a short, thick
//   beam pushes the target and blows up on it (dome, crater, dust ring, manga impact frame). The blast is twice the
//   first port's (Stark, 2026-11-09), and the beam tears the ground it runs over (DbScar).
// X Stretched Arm: the arm (drawn with the user's own skin) stretches 17 blocks, grabs the target (stun) and pulls it
//   straight into a knee (hitstop, manga frame) that stuns again, long enough for the Hellzone (the X then C combo).
// C Hellzone Grenade: only on a stunned target (Stamina.stunnable), kept stunned while the balls are thrown. Piccolo
//   vanishes and shows up at three spots round the target, throwing balls that seem to miss;
//   they stop in the air (20 of them, each with its own size and sway) and hurt whoever touches them. C again (or by
//   itself after 4 s) all of them dive into the target and blow up together in one big golden dome.
// V Makankosappo: first he throws off the heavy cape, turban and pads (they crack the ground), then two fingers on the
//   forehead while yellow and pink sparks spiral in; let go, a thin ray with two tight spirals pierces everyone in line
//   (hitstop on each hole), 68 blocks out (twice the first port), and blows up where it hits the ground far away.
public final class Piccolo {
    public static final String HELLZONE = "db_piccolo_hellzone";

    private Piccolo() {
    }

    public static Power create() {
        return new Power("piccolo", 0x62B04A, 1.0f, List.of(
                new Ability(140, 0, Piccolo::masenko),
                new Ability(160, 0, Piccolo::arm),
                new Ability(280, 0, Piccolo::hellzone),
                new Ability(600, 0, Piccolo::makanko)));
    }

    private static void hitstop(LivingEntity e, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ModPayloads.Hitstop(e.getId(), ticks));
    }

    private static void teleport(LivingEntity caster, ServerLevel level, Vec3 to, Vec3 face) {
        float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
        if (caster instanceof ServerPlayer player) {
            player.teleportTo(level, to.x, to.y, to.z, yaw, player.getXRot());
        } else {
            caster.teleportTo(to.x, to.y, to.z);
            caster.setYRot(yaw);
            caster.setYHeadRot(yaw);
        }
    }

    // ---------- Z: Masenko ----------

    private static boolean masenko(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int ball = SubNet.newId();
        int beam = SubNet.newId();
        int charge = 21;
        int fire = 26;
        Map<Integer, Integer> hitAt = new HashMap<>();
        Vec3[] origin = {null};
        SubCommon.pose(caster, "db.ceu");
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, ball, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                if (origin[0] != null) {
                    SubNet.send(level, SubNet.KI_BEAM, beam, origin[0], Vec3.ZERO, 0, Db.GOLD, 1);
                }
                return true;
            }
            SubCommon.busy(caster, 3);
            Vec3 look = caster.getLookAngle();
            if (age < charge) {
                // The ball is born between the palms over the head, crackling.
                float k = SubCommon.smooth(age / 18f);
                Vec3 p = caster.position().add(0, caster.getBbHeight() * 1.18, 0).add(SubCommon.flat(look).scale(0.3));
                float r = 0.08f + 0.36f * k + 0.04f * Mth.sin(age * 2.05f) + 0.02f * Mth.sin(age * 3.35f);
                SubNet.send(level, SubNet.KI_BALL, ball, p, Vec3.ZERO, r * 0.8f, Db.GOLD + 48, 0);
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                if (age % 4 == 0) {
                    SubCommon.sound(level, p, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.5f, 1.3f + k * 0.6f);
                }
                return false;
            }
            int t = age - charge;
            Vec3 o = DbCommon.hands(caster).add(look.scale(0.4));
            if (t == 0) {
                SubNet.send(level, SubNet.KI_BALL, ball, o, Vec3.ZERO, 0, Db.GOLD, 1);
                SubCommon.pose(caster, "db.empurra");
                DbCommon.screen(level, o, 45);
                SubCommon.sound(level, o, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.5f);
                SubCommon.sound(level, o, SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.1f);
            }
            origin[0] = o;
            Vec3 reach = DbCommon.beamEnd(caster, level, o, look, 24, 0.8, false);
            double full = o.distanceTo(reach);
            Vec3 to = o.add(look.scale(Math.min(full, (t + 1) * 1.6)));
            boolean end = t >= fire;
            SubNet.send(level, SubNet.KI_BEAM, beam, o, to.subtract(o), 1.13f + 0.11f * Mth.sin(t * 0.45f), Db.GOLD, end ? 1 : 0);
            DbCommon.hurtAlong(caster, level, o, to, 0.8, 1.6f, age, 4, hitAt, look.scale(0.45));
            if (t % 7 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 0.8f);
            }
            if (end) {
                // Twice the first port's blast (Stark, 2026-11-09): dome, crater and reach.
                DbCommon.boom(level, to, 5.4f, Db.GOLD, 5);
                DbCommon.screen(level, to, 46);
                double ground = DbCommon.groundY(level, to);
                if (to.y - ground < 5) {
                    JjCommon.crater(level, new Vec3(to.x, ground, to.z), 5.4, 2.6, 32);
                }
                DbCommon.hurtRadius(caster, level, to, 5.4, 10, 1.6, 0.8);
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- X: Stretched Arm ----------

    private static boolean arm(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        // Twice the first port's reach (Stark, 2026-11-09: "too hard to land"), a little faster and a little fatter.
        int stretch = 11;
        double reach = 17;
        int pull = 10;
        SubCommon.pose(caster, "db.palma_dir");
        SubCommon.busy(caster, 36);
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
        LivingEntity[] caught = {null};
        int[] grabAt = {-1};
        Vec3[] from = {null};
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.ARM, caster.getId(), DbCommon.hand(caster), Vec3.ZERO, 0, 0, 1);
                if (caught[0] != null) {
                    Freeze.free(caught[0]);
                }
                return true;
            }
            Vec3 shoulder = caster.position().add(0, caster.getBbHeight() * 0.75, 0);
            Vec3 dir = caster.getLookAngle();
            if (grabAt[0] < 0) {
                double len = reach * Math.min(1, SubCommon.easeOutCubic(age / (float) stretch));
                Vec3 tip = shoulder.add(dir.scale(Math.max(0.6, len)));
                SubNet.send(level, SubNet.ARM, caster.getId(), tip, Vec3.ZERO, 0, 0, 0);
                for (LivingEntity t : Combat.alongLine(level, shoulder, tip, 0.9, caster)) {
                    caught[0] = t;
                    break;
                }
                if (caught[0] != null) {
                    grabAt[0] = age;
                    from[0] = caught[0].position();
                    SubCommon.sound(level, caught[0].position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
                    Stun.apply(caught[0], 30);
                    LivingEntity t = caught[0];
                    Vec3 start = from[0];
                    // Pulled straight back in front of Piccolo.
                    Freeze.hold(t, pull + 2, a -> start.lerp(caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(1.3)),
                            SubCommon.smooth(a / (float) pull)).add(0, a < pull ? 0.3 : 0, 0));
                    return false;
                }
                if (age >= stretch + 2) {
                    // Nothing caught: the arm comes back empty.
                    SubCommon.warn(caster, "piccolo.arm_miss");
                    grabAt[0] = age;
                    caught[0] = null;
                    from[0] = tip;
                }
                return false;
            }
            int t = age - grabAt[0];
            if (caught[0] == null) {
                Vec3 tip = from[0].lerp(shoulder.add(dir.scale(0.6)), SubCommon.smooth(t / 8f));
                boolean done = t >= 8;
                SubNet.send(level, SubNet.ARM, caster.getId(), tip, Vec3.ZERO, 0, 0, done ? 1 : 0);
                return done;
            }
            LivingEntity target = caught[0];
            if (t <= pull) {
                SubNet.send(level, SubNet.ARM, caster.getId(), Combat.center(target), Vec3.ZERO, 0, 0, t == pull ? 1 : 0);
                return false;
            }
            if (t == pull + 2) {
                SubCommon.pose(caster, "db.joelhada");
            }
            if (t == pull + 4) {
                // The knee in the stomach: hitstop, the manga frame, the shock and it flies up spinning.
                if (target.isAlive()) {
                    Combat.hurt(caster, target, 9);
                    // A real stun (Stark, 2026-11-09): the knee opens the Hellzone, which only lands on a stunned target. 3 s
                    // and full on bosses too: halved (45 -> 22 ticks) it fell under Stun.INTERRUPT_TICKS, so it never
                    // counted as a stun and the C refused ("the X's stun is tiny").
                    Stun.apply(target, 60, true);
                    Combat.push(target, target.position().subtract(caster.position()), 0.6, 1.0);
                    Vec3 c = Combat.center(target);
                    DbCommon.hit(level, c, 0.8f, Db.WHITE);
                    SubNet.send(level, SubNet.KI_BOOM, 0, c, new Vec3(DbCommon.groundY(level, c), 0, 0), 0.5f, Db.WHITE, 1000 + SubCommon.RANDOM.nextInt(100));
                    DbCommon.screen(level, c, 50);
                    hitstop(caster, 3);
                    hitstop(target, 3);
                    SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.7f);
                }
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- C: Hellzone Grenade ----------

    private static final int HZ_BALLS = 20;
    private static final int HZ_SPOT = 11;
    private static final int HZ_FLIGHT = 8;

    // Where the ball i of 20 hovers round the target: a fibonacci sphere, each one at its own distance.
    private static Vec3 hover(Vec3 c, int i) {
        double y = 1 - 2 * (i + 0.5) / HZ_BALLS;
        double r = Math.sqrt(1 - y * y);
        double a = i * 2.39996 + Db.hash(i, 1, 9) * 0.4;
        double big = 2.4 + Db.hash(i, 2, 9) * 1.1;
        return c.add(Math.cos(a) * r * big, Math.max(-0.75, y * big * 0.8), Math.sin(a) * r * big);
    }

    private static boolean hellzone(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, HELLZONE)) {
            SubCommon.clearState(caster, HELLZONE);
            return true;
        }
        LivingEntity target = SubCommon.aimed(caster, 24);
        if (target == null) {
            SubCommon.warn(caster, "piccolo.hellzone_no_target", 24);
            return false;
        }
        // Only on a stunned target (Stark, 2026-11-09: it never landed on someone moving and hitting back): the X's knee
        // opens it, and the target stays stunned while the balls are thrown round it.
        if (!Stamina.stunnable(target)) {
            SubCommon.warn(caster, "piccolo.hellzone_needs_stun");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int throwEnd = 3 * HZ_SPOT + 5 * 2 + 4;
        int auto = throwEnd + 80;
        int bossClose = throwEnd + 30 + SubCommon.RANDOM.nextInt(30);
        SubCommon.setState(caster, HELLZONE, auto + 10);
        Stun.apply(target, throwEnd + 10, true);
        Vec3 home = caster.position();
        // The three spots round the target (left, far left, far right), seen from where Piccolo stands.
        Vec3 toMe = SubCommon.flat(home.subtract(target.position()));
        if (toMe.lengthSqr() < 1.0E-4) {
            toMe = new Vec3(0, 0, 1);
        }
        Vec3 back = toMe.normalize();
        Vec3 side = back.cross(new Vec3(0, 1, 0));
        double[][] offsets = {{-5, 2.5}, {-3.2, -4.5}, {4.6, -3.6}};
        List<Integer> ids = new ArrayList<>();
        List<Vec3> from = new ArrayList<>();
        List<Vec3> mid = new ArrayList<>();
        List<Integer> born = new ArrayList<>();
        List<Vec3> last = new ArrayList<>();
        int[] closeAt = {-1};
        boolean[] home0 = {false};
        Vec3[] center = {Combat.center(target)};
        Vec3[] cage = {Combat.center(target)};
        int[] lastHurt = {-100};
        Tasks.add(age -> {
            if (target.isAlive()) {
                center[0] = Combat.center(target);
            }
            if (guard.broken() && closeAt[0] < 0) {
                for (int i = 0; i < ids.size(); i++) {
                    SubNet.send(level, SubNet.KI_BALL, ids.get(i), last.get(i), Vec3.ZERO, 0, Db.ORANGE + 64, 1);
                }
                SubCommon.clearState(caster, HELLZONE);
                DbCommon.cooldown(caster, 2, 280);
                return true;
            }
            boolean throwing = closeAt[0] < 0 && ids.size() < HZ_BALLS && age < throwEnd;
            if (throwing) {
                SubCommon.busy(caster, 3);
                int spot = age / HZ_SPOT;
                if (age % HZ_SPOT == 0 && spot < 3) {
                    // He vanishes and shows up at the next spot, hanging in the air a moment.
                    double[] o = offsets[spot];
                    Vec3 at = target.position().add(side.scale(o[0])).add(back.scale(o[1])).add(0, 0.6, 0);
                    if (SubCommon.solid(level, at.add(0, 0.5, 0)) || SubCommon.solid(level, at.add(0, 1.5, 0))) {
                        at = SubCommon.ground(level, at.add(0, 2, 0));
                    }
                    if (!SubCommon.solid(level, at.add(0, 0.5, 0)) && !SubCommon.solid(level, at.add(0, 1.5, 0))) {
                        SubNet.send(level, SubNet.VANISH, 0, caster.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                        SubCommon.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
                        teleport(caster, level, at, target.position().subtract(at));
                        Vec3 hold = at;
                        Freeze.hold(caster, HZ_SPOT, a -> hold);
                        SubNet.send(level, SubNet.VANISH, 0, at.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    }
                } else if (age % HZ_SPOT == 0 && spot == 3) {
                    // The last handful is thrown from where he started.
                    home0[0] = true;
                    SubNet.send(level, SubNet.VANISH, 0, caster.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    teleport(caster, level, home, target.position().subtract(home));
                    SubNet.send(level, SubNet.VANISH, 0, home.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                }
                int inSpot = age % HZ_SPOT;
                int perSpot = age / HZ_SPOT < 3 ? 5 : HZ_BALLS - ids.size();
                if (inSpot >= 1 && (inSpot - 1) % 2 == 0 && (inSpot - 1) / 2 < perSpot && ids.size() < HZ_BALLS) {
                    // One more ball that seems to miss, from each hand in turn.
                    int i = ids.size();
                    ids.add(SubNet.newId());
                    Vec3 hand = i % 2 == 0 ? DbCommon.hand(caster) : DbCommon.handLeft(caster);
                    Vec3 to = hover(center[0], i);
                    from.add(hand);
                    mid.add(hand.lerp(to, 0.5).add((Db.hash(i, 4, 9) - 0.5) * 3, 1 + Db.hash(i, 5, 9) * 1.5, (Db.hash(i, 6, 9) - 0.5) * 3));
                    born.add(age);
                    last.add(hand);
                    SubCommon.pose(caster, i % 2 == 0 ? "soco.forte_golpe" : "soco.barragem_final");
                    SubCommon.sound(level, hand, SoundEvents.FIRECHARGE_USE, 0.7f, 1.4f + Db.hash(i, 7, 9) * 0.4f);
                }
            }
            if (!throwing && !home0[0] && closeAt[0] < 0) {
                // Done throwing: back where he started.
                home0[0] = true;
                if (caster.position().distanceToSqr(home) > 1) {
                    SubNet.send(level, SubNet.VANISH, 0, caster.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    teleport(caster, level, home, target.position().subtract(home));
                    SubNet.send(level, SubNet.VANISH, 0, home.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                }
            }
            if (closeAt[0] < 0) {
                boolean close = !SubCommon.hasState(caster, HELLZONE) || age >= auto || (!(caster instanceof Player) && age >= bossClose);
                if (close && !ids.isEmpty()) {
                    closeAt[0] = age;
                    cage[0] = center[0];
                    SubCommon.clearState(caster, HELLZONE);
                    SubCommon.pose(caster, "db.bracos_abertos_curto");
                    SubCommon.sound(level, center[0], SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.4f);
                } else if (close) {
                    SubCommon.clearState(caster, HELLZONE);
                    DbCommon.cooldown(caster, 2, 40);
                    return true;
                }
            }
            int closeT = closeAt[0] < 0 ? -1 : age - closeAt[0];
            boolean all = closeT >= 0;
            boolean anyHit = false;
            for (int i = 0; i < ids.size(); i++) {
                int a = age - born.get(i);
                float size = 0.2f + Db.hash(i, 8, 9) * 0.13f;
                float fq = 1.3f + Db.hash(i, 9, 9) * 1.6f;
                float ph = Db.hash(i, 10, 9) * Mth.TWO_PI;
                float tt = age / 20f;
                Vec3 slot = hover(center[0], i);
                Vec3 p;
                boolean end = false;
                float r = size * (1 + 0.12f * Mth.sin(tt * 23 + ph * 5));
                if (closeT >= 0) {
                    // Each one dives in after its own little delay, faster and faster.
                    float k = Mth.clamp((closeT - Db.hash(i, 7, 9) * 3) / 5f, 0, 1);
                    Vec3 start = hover(cage[0], i);
                    p = start.lerp(center[0], k * k);
                    r *= 1 - 0.3f * k;
                    end = k >= 1;
                    if (!end) {
                        all = false;
                    }
                } else if (a < HZ_FLIGHT) {
                    // A curve from the hand: it seems to miss, then stops where it hovers.
                    float e = SubCommon.easeOutCubic(a / (float) HZ_FLIGHT);
                    Vec3 q1 = from.get(i).lerp(mid.get(i), e);
                    Vec3 q2 = mid.get(i).lerp(slot, e);
                    p = q1.lerp(q2, e);
                } else {
                    p = slot.add(Mth.sin(tt * fq + ph) * 0.12, Mth.sin(tt * fq * 1.3f + ph * 2) * 0.16, Mth.cos(tt * fq * 0.8f + ph) * 0.12);
                }
                last.set(i, p);
                SubNet.send(level, SubNet.KI_BALL, ids.get(i), p, Vec3.ZERO, r, Db.ORANGE + 64, end ? 1 : 0);
                // The cage: touching a ball hurts.
                if (closeT < 0 && a >= HZ_FLIGHT && age - lastHurt[0] >= 10) {
                    for (LivingEntity e : Combat.inRadius(level, p, 0.8, caster)) {
                        Combat.hurtMagic(caster, e, 3);
                        DbCommon.hit(level, p, 0.4f, Db.ORANGE);
                        anyHit = true;
                    }
                }
            }
            if (anyHit) {
                lastHurt[0] = age;
            }
            if (closeT >= 0 && all) {
                Vec3 c = center[0];
                int n = ids.size();
                // One big blast (radius 6.5): the orange dome, a golden one inside, the deep crater.
                SubNet.send(level, SubNet.KI_BOOM, 0, c, new Vec3(DbCommon.groundY(level, c), 0, 0), 6.5f, Db.ORANGE, 3000 + SubCommon.RANDOM.nextInt(500));
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
                SubCommon.sound(level, c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 2f, 0.8f);
                DbCommon.screen(level, c, 51);
                double ground = DbCommon.groundY(level, c);
                if (c.y - ground < 5) {
                    JjCommon.crater(level, new Vec3(c.x, ground, c.z), 4.4 * Math.max(0.5, n / (double) HZ_BALLS), 2.3, 40);
                }
                DbCommon.hurtRadius(caster, level, c, 5.5, 16 + 0.8f * n, 1.6, 0.9);
                DbCommon.cooldown(caster, 2, 280);
                return true;
            }
            return false;
        });
        DbCommon.cooldown(caster, 2, 8);
        return false;
    }

    // ---------- V: Makankosappo ----------

    private static final double SAPPO_SPEED = 4;

    private static boolean makanko(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int gear = 28;
        int most = 62;
        int least = 12;
        int bossHold = 24 + SubCommon.RANDOM.nextInt(38);
        int id = SubNet.newId();
        int aura = caster.getId();
        SubCommon.pose(caster, "db.bracos_abertos_curto");
        SubCommon.busy(caster, gear + 4);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, 0, Db.GOLD + 16, 1);
                SubNet.send(level, SubNet.KI_AURA, aura, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < gear) {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false));
                if (age == 5) {
                    // Off with the weight: the cape, the turban and the pads fall like a ton each.
                    Vec3 feet = caster.position();
                    SubNet.send(level, SubNet.SCREEN, caster.getId(), feet, new Vec3(DbCommon.groundY(level, feet), caster.getYRot() * Mth.DEG_TO_RAD, 0), 52, 0, 0);
                    SubCommon.sound(level, feet, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2f, 0.6f);
                }
                if (age == 11 || age == 14) {
                    Vec3 behind = caster.position().subtract(SubCommon.flat(caster.getLookAngle()).scale(age == 11 ? 1.2 : 0.4));
                    double ground = DbCommon.groundY(level, behind);
                    Vec3 at = new Vec3(behind.x, ground, behind.z);
                    SubCommon.sound(level, at, SoundEvents.ANVIL_LAND, 1.1f, 0.5f);
                    SubNet.send(level, SubNet.KI_BOOM, 0, at.add(0, 0.2, 0), new Vec3(ground, 0, 0), 0.35f, Db.WHITE, 1000 + age);
                    if (age == 11) {
                        JjCommon.crater(level, at, 0.9, 0.4, 3);
                    }
                }
                return false;
            }
            int c = age - gear;
            float k = SubCommon.smooth((c - 4) / 50f);
            boolean held = SubCommon.held(caster, c, bossHold);
            if (c == 0) {
                SubCommon.pose(caster, "db.testa");
                SubNet.send(level, SubNet.KI_AURA, aura, caster.position(), new Vec3(0.55, 0, 0), most + 6, Db.GOLD, 1);
            }
            if ((held || c < least) && c < most) {
                if (held) {
                    Charge.report(caster, c / (double) most);
                }
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, k, Db.GOLD + 16, 0);
                if (c % 3 == 0) {
                    SubCommon.sound(level, caster.position(), SoundEvents.REDSTONE_TORCH_BURNOUT, 0.6f, 1.4f + k);
                }
                return false;
            }
            SubNet.send(level, SubNet.SPARKS, id, DbCommon.forehead(caster), Vec3.ZERO, k, Db.GOLD + 16, 1);
            SubNet.send(level, SubNet.KI_AURA, aura, caster.position(), Vec3.ZERO, 0, Db.GOLD, 1);
            SubCommon.pose(caster, "db.apontar");
            Vec3 tip = DbCommon.hand(caster).add(caster.getLookAngle().scale(0.1));
            Vec3 dir = caster.getLookAngle();
            // Twice the first port's reach (Stark, 2026-11-09).
            Vec3 end = DbCommon.beamEnd(caster, level, tip, dir, 68, 0.5, true);
            double dist = tip.distanceTo(end);
            // Far too fast: 4 blocks a tick, then it blows up where it stops.
            int reach = Math.max(1, (int) Math.ceil(dist / SAPPO_SPEED));
            double ground = DbCommon.groundY(level, end);
            SubNet.send(level, SubNet.MAKANKO, 0, tip, end.subtract(tip), reach, (float) ground, 0);
            DbCommon.screen(level, tip, 45);
            SubCommon.sound(level, tip, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.8f);
            SubCommon.sound(level, tip, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.4f, 1.4f);
            float damage = 8 + 16 * k;
            // Pierces everyone on the line, each one when the ray gets there, with a hitstop on each hole.
            for (LivingEntity t : Combat.alongLine(level, tip, end, 0.6, caster)) {
                int when = (int) (Combat.center(t).distanceTo(tip) / SAPPO_SPEED);
                Tasks.later(Math.max(0, when), () -> {
                    if (!t.isAlive()) {
                        return;
                    }
                    Combat.hurtMagic(caster, t, damage);
                    Stun.apply(t, 12);
                    Combat.push(t, dir, 0.8 + k, 0.35);
                    Vec3 hole = Combat.center(t);
                    DbCommon.hit(level, hole, 0.8f, Db.PINK);
                    DbCommon.screen(level, hole, 53);
                    hitstop(t, 2);
                    hitstop(caster, 2);
                    SubCommon.sound(level, hole, SoundEvents.TRIDENT_HIT, 1.4f, 0.8f);
                });
            }
            // The thin ray tears a narrow furrow where it runs low, as it gets there.
            for (int i = 0; i < reach; i++) {
                Vec3 a = tip.add(dir.scale(i * SAPPO_SPEED));
                Vec3 b = tip.add(dir.scale(Math.min(dist, (i + 1) * SAPPO_SPEED + 1.2)));
                double skip = i == 0 ? 1.2 : 0;
                Tasks.later(i, () -> DbScar.along(caster, level, a, b, 0.5, skip));
            }
            Tasks.later(reach, () -> {
                SubCommon.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 1.1f);
                if (end.y - ground < 3) {
                    JjCommon.crater(level, new Vec3(end.x, ground, end.z), 2.6, 2, 11);
                }
                DbCommon.screen(level, end, 46);
                DbCommon.hurtRadius(caster, level, end, 3, 6 + 6 * k, 1.2, 0.6);
            });
            return true;
        });
        return true;
    }
}
