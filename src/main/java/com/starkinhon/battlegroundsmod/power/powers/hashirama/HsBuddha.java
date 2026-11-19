package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Phase 2 of the Hashirama boss: Sage Mode and Mokuton Hijutsu: Shinsūsenju, at the end-game scale (approved page
// forja/escala-fim-de-jogo, Stark 2026-11-19): the Buddha 2.8 times the first port, ~43 blocks to the head. He takes Sage
// Mode (0.7 s), slaps the ground (0.3 s), the Buddha tears the ground open behind him (a crater of 18, cracks to 44, dust
// rising with it) and rises in 3.2 s while the fan opens row by row; at 2.2 s he leaps onto its head. While it lasts (25 s):
//   Chuva de Mãos: 12 slaps, 0.36 s apart with a gap every third one, each hand ~11 blocks: 0.75 s of shadow 9 wide (the
//     warning), 0.15 s drop, the ground sunk in the shape of the hand (Ruin.palm): 30 damage within 7.
//   Golpe do Topo: one palm of ~50 blocks: 1.6 s of shadow 30 wide (only who runs from the start or dashes twice gets
//     out), 0.26 s drop, a print 24 long and 9 deep, cracks to 72, the ground shaking: 75 damage within 26. The arm
//     stays 3 s in the ground.
// The weak point is Hashirama on top (the page): 3 blows on him there (flying, ranged) throw him off and the Buddha
// falls apart; otherwise it dissolves at the end and he comes down.
public final class HsBuddha {
    static final double PU = HsNet.PU;
    static final int SAGE = 14, CLAP = 6, RISE = 64, LEAP_FROM = 44, LEAP = 16, LIFE = 20 * 25, KNOCKS = 3;
    // the Buddha's size against the first port (the client scales the same frame)
    public static final double SB = 2.8;

    static final class Summon {
        final LivingEntity boss;
        final ServerLevel level;
        final Vec3 o;
        final double yaw;
        int age, hits, count;
        Vec3 perch;
        boolean ended;

        Summon(LivingEntity boss, ServerLevel level, Vec3 o, double yaw) {
            this.boss = boss;
            this.level = level;
            this.o = o;
            this.yaw = yaw;
        }
    }

    private static final Map<Integer, Summon> LIVE = new HashMap<>();

    private HsBuddha() {
    }

    static void resetStatics() {
        LIVE.clear();
    }

    public static boolean active(LivingEntity e) {
        return LIVE.containsKey(e.getId());
    }

    // Where the boss stands this tick (on the Buddha's head, or flying up to it), or null.
    public static Vec3 perch(LivingEntity e) {
        Summon s = LIVE.get(e.getId());
        return s == null ? null : s.perch;
    }

    // A blow on him while he stands on top: the third one throws him off.
    public static void hitOnTop(LivingEntity e) {
        Summon s = LIVE.get(e.getId());
        if (s == null || s.perch == null || s.age < SAGE + CLAP + LEAP_FROM + LEAP) {
            return;
        }
        if (++s.hits >= KNOCKS) {
            end(s, true);
        }
    }

    static void end(Summon s, boolean knocked) {
        if (s.ended) {
            return;
        }
        s.ended = true;
        s.perch = null;
        LIVE.remove(s.boss.getId());
        SubNet.send(s.level, HsNet.BUDDHA, s.boss.getId(), s.o, Vec3.ZERO, 2, 0, 0);
        s.boss.setNoGravity(false);
        if (knocked && s.boss.isAlive()) {
            Stun.apply(s.boss, 60);
            DbCommon.screen(s.level, s.boss.position(), 10);
            SubCommon.sound(s.level, s.boss.position(), SoundEvents.WOOD_BREAK, 3f, 0.4f);
        }
        if (s.boss instanceof com.starkinhon.battlegroundsmod.boss.HashiramaEntity h) {
            h.buddhaEnded();
        }
    }

    public static boolean summon(LivingEntity caster, ServerLevel level) {
        if (LIVE.containsKey(caster.getId())) {
            return false;
        }
        LivingEntity target = NrCommon.target(caster, 48);
        Vec3 dir = Hashirama.aimDir(caster, target);
        Vec3 o = SubCommon.ground(level, caster.position());
        double yaw = Hashirama.yawOf(dir);
        Summon s = new Summon(caster, level, o, yaw);
        LIVE.put(caster.getId(), s);
        if (caster instanceof com.starkinhon.battlegroundsmod.boss.HashiramaEntity h) {
            h.setSage(true);
        }
        SubNet.send(level, HsNet.SAGE, caster.getId(), caster.position(), Vec3.ZERO, 1, 0, 0);
        DbCommon.screen(level, caster.position(), 21);
        SubCommon.pose(caster, "nr.selo");
        SubCommon.busy(caster, SAGE + CLAP + LEAP_FROM + LEAP);
        SubCommon.sound(level, o, SoundEvents.BEACON_ACTIVATE, 1.6f, 0.6f);
        Vec3 base = SubCommon.ground(level, Hashirama.toWorld(o, yaw, 0, 0, HsNet.BUDDHA_Z * SB));
        Vec3 head = Hashirama.toWorld(o, yaw, 0, HsNet.TOP * SB, (HsNet.BUDDHA_Z + HsNet.HEAD_Z + 0.15) * SB);
        List<double[]> plan = new ArrayList<>();
        Set<Integer> struck = new HashSet<>();
        Tasks.add(age -> {
            s.age = age;
            if (s.ended || !caster.isAlive()) {
                end(s, false);
                return true;
            }
            if (age == SAGE) {
                SubCommon.pose(caster, "nr.mao_chao");
            }
            int r = age - SAGE - CLAP;
            if (r == 0) {
                SubNet.send(level, HsNet.BUDDHA, caster.getId(), o, dir, 0, 0, (float) SB);
                // it tears the ground open to come out
                Ruin.bowl(level, base.x, base.z, 18, 6, 14, 0, Ruin.opts().rim(0.5));
                Ruin.fissures(level, base.x, base.z, 16, 44, 1.5, 2, 3, Ruin.opts().seed(9));
                RuinNet.debris(level, base, 18, 24, 0, 160, 2.2);
                RuinNet.dust(level, base, 26, 18, 100, 32, 0x86735E);
                RuinNet.ring(level, base, 70, 5, false, 44, 0xB8A890);
                RuinNet.tremor(level, base, 1.3, 26, 60);
                HsEpic.quake(level, base, 1.6, true);
                SubCommon.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.35f);
                SubCommon.sound(level, base, SoundEvents.WARDEN_EMERGE, 4f, 0.45f);
            }
            if (r > 0 && r < RISE && r % 6 == 0) {
                HsEpic.quake(level, base, 0.8 * (1 - r / (double) RISE), false);
                RuinNet.debris(level, base, 19, 9, 0, 6, 2);
            }
            // the leap onto the head, then held there (HashiramaEntity reads perch every tick)
            if (r >= LEAP_FROM) {
                double k = Math.min(1, (r - LEAP_FROM) / (double) LEAP);
                k = k * k * (3 - 2 * k);
                double rise = 1 - Math.pow(1 - Math.min(1, r / (double) RISE), 3);
                Vec3 headNow = head.add(0, -27 * (1 - rise) * PU * SB, 0);
                Vec3 p = o.lerp(headNow, k).add(0, Math.sin(k * Math.PI) * 8, 0);
                s.perch = p;
                caster.setNoGravity(true);
            }
            if (r < LEAP_FROM + LEAP + 8) {
                return false;
            }
            int t = r - (LEAP_FROM + LEAP + 8);
            if (t >= LIFE) {
                end(s, false);
                return true;
            }
            // the attacks: rain, rest, top strike, rest, again
            LivingEntity tg = target != null && target.isAlive() ? target : NrCommon.target(caster, 48);
            if (plan.isEmpty() && tg != null) {
                int cycle = (t / 200) % 2;
                if (cycle == 0) {
                    for (int n = 0; n < 12; n++) {
                        plan.add(new double[] {age + n * 7.2 + (n % 3 == 2 ? 6 : 0), 0, n});
                    }
                } else {
                    plan.add(new double[] {age, 1, 0});
                }
                plan.add(new double[] {age + (cycle == 0 ? 130 : 120), -1, 0});
            }
            for (int i = 0; i < plan.size(); i++) {
                double[] q = plan.get(i);
                if (q[0] > age) {
                    continue;
                }
                plan.remove(i);
                i--;
                if (q[1] < 0 || tg == null) {
                    continue;
                }
                boolean big = q[1] > 0.5;
                int n = (int) q[2];
                double spread = big ? 0 : n < 4 ? 7 : 13;
                Vec3 lead = tg.getDeltaMovement().multiply(1, 0, 1).scale(big ? 6 : 12);
                Vec3 at = tg.position().add(lead).add((Hashirama.RNG.nextDouble() - 0.5) * 1.4 * spread, 0, (Hashirama.RNG.nextDouble() - 0.5) * spread);
                Vec3 g = SubCommon.ground(level, at);
                slam(s, caster, g, big, struck, base);
            }
            return false;
        }, () -> end(s, false));
        return true;
    }

    // One hand: the shadow closes for the warning, then the drop; the hit lands where the shadow was, and the ground sinks
    // in the shape of the hand (fingers pointing away from the Buddha).
    static void slam(Summon s, LivingEntity caster, Vec3 g, boolean big, Set<Integer> struck, Vec3 base) {
        ServerLevel level = s.level;
        SubNet.send(level, HsNet.SLAM, caster.getId(), g, Vec3.ZERO, big ? 1 : 0, s.count++, 0);
        int land = big ? 37 : 18;
        double R = big ? 26 : 7;
        double yaw = Math.atan2(g.x - base.x, -(g.z - base.z));
        boolean left = (s.count & 1) == 0;
        Tasks.add(age -> {
            if (s.ended) {
                return true;
            }
            if (age < land) {
                return false;
            }
            if (big) {
                Ruin.palm(level, g.x, g.z, yaw, 24, 9, 7, 0, Ruin.opts().lip(2.4).left(left));
                Ruin.fissures(level, g.x, g.z, 16, 72, 3, 3, 5, Ruin.opts().width(0.16).seed(4));
                RuinNet.debris(level, g, 26, 30, 0, 220, 2.6);
                RuinNet.dust(level, g, 40, 22, 110, 12, 0x88755F);
                RuinNet.ring(level, g, 90, 6, false, 44, 0xB8A890);
                RuinNet.ring(level, g.add(0, 18, 0), 70, 3, true, 26, 0xFFF3DC);
                RuinNet.tremor(level, g, 2, 34, 80);
                RuinNet.tremor(level, g, 1.2, 24, 60);
                HsEpic.quake(level, g, 2.6, true);
                SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.3f);
                SubCommon.sound(level, g, SoundEvents.WOOD_BREAK, 4f, 0.3f);
            } else {
                Ruin.palm(level, g.x, g.z, yaw, 7, 3.5, 3, 0, Ruin.opts().lip(1.4).left(left));
                RuinNet.debris(level, g, 7, 22, 0, 40, 1.2);
                RuinNet.dust(level, g, 11, 7, 52, 7, 0x8C7A64);
                RuinNet.ring(level, g, 26, 2.2, false, 22, 0xB8A890);
                HsEpic.quake(level, g, 0.9, false);
                SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), 2.4f, 0.6f);
                SubCommon.sound(level, g, SoundEvents.WOOD_BREAK, 2f, 0.4f);
            }
            for (LivingEntity e : Hashirama.near(level, g, R, 2, big ? 14 : 6, caster)) {
                Combat.hurt(caster, e, big ? 75 : 30);
                Vec3 away = SubCommon.flat(e.position().subtract(g));
                Hashirama.throwPage(e, away.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : away.normalize(), big ? 14 : 7, big ? 14 : 8);
                Hashirama.stun(caster, e, big ? 40 : 25);
                if (!big) {
                    HsEpic.quake(level, Combat.center(e), 1.0, true);
                }
                struck.add(e.getId());
            }
            return true;
        });
    }
}
