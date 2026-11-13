package com.starkinhon.battlegroundsmod.power.powers.hashirama;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Phase 2 of the Hashirama boss: Sage Mode and Mokuton Hijutsu: Shinsūsenju (the page's buda scene). He takes Sage Mode
// (0.7 s), slaps the ground (0.3 s), the Buddha rises out of the ground behind him in 2.4 s while the fan opens row by
// row, and at 1.6 s he leaps onto its head (2.2 s there). From then, while it lasts (25 s):
//   Chuva de Mãos: 12 slaps, 0.32 s apart with a gap every third one, each with a 0.75 s shadow (the warning) and a
//     0.14 s drop: 26 damage in 2.3 u.
//   Golpe do Topo: one giant palm, 1.3 s of shadow 6 u wide, 0.22 s drop: 60 damage.
// The weak point is Hashirama on top (the page): 3 blows on him there (flying, ranged) throw him off and the Buddha
// falls apart; otherwise it dissolves at the end and he comes down.
public final class HsBuddha {
    static final double PU = HsNet.PU;
    static final int SAGE = 14, CLAP = 6, RISE = 48, LEAP_FROM = 32, LEAP = 12, LIFE = 20 * 25, KNOCKS = 3;

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
        Vec3 base = Hashirama.toWorld(o, yaw, 0, 0, HsNet.BUDDHA_Z);
        Vec3 head = Hashirama.toWorld(o, yaw, 0, HsNet.TOP, HsNet.BUDDHA_Z + HsNet.HEAD_Z + 0.15);
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
                SubNet.send(level, HsNet.BUDDHA, caster.getId(), o, dir, 0, 0, 0);
                JjCommon.crater(level, base, 3.4 * PU, 2 * PU, 60);
                Hashirama.dust(level, base, 14);
                DbCommon.screen(level, base, 18);
                SubCommon.sound(level, base, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
                SubCommon.sound(level, base, SoundEvents.WARDEN_EMERGE, 3f, 0.5f);
            }
            if (r > 0 && r < RISE && r % 8 == 0) {
                DbCommon.screen(level, base, 4);
                Hashirama.dust(level, base, 6);
            }
            // the leap onto the head, then held there (HashiramaEntity reads perch every tick)
            if (r >= LEAP_FROM) {
                double k = Math.min(1, (r - LEAP_FROM) / (double) LEAP);
                k = k * k * (3 - 2 * k);
                double rise = 1 - Math.pow(1 - Math.min(1, r / (double) RISE), 3);
                Vec3 headNow = head.add(0, -27 * (1 - rise) * PU, 0);
                Vec3 p = o.lerp(headNow, k).add(0, Math.sin(k * Math.PI) * 3 * PU, 0);
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
                        plan.add(new double[] {age + n * 6.4 + (n % 3 == 2 ? 5 : 0), 0, n});
                    }
                } else {
                    plan.add(new double[] {age, 1, 0});
                }
                plan.add(new double[] {age + (cycle == 0 ? 120 : 70), -1, 0});
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
                double spread = big ? 0 : n < 4 ? 2.5 : 4.5;
                Vec3 lead = tg.getDeltaMovement().multiply(1, 0, 1).scale(big ? 6 : 12);
                Vec3 at = tg.position().add(lead).add((Hashirama.RNG.nextDouble() - 0.5) * 2 * spread * PU, 0, (Hashirama.RNG.nextDouble() - 0.5) * 2 * spread * PU);
                Vec3 g = SubCommon.ground(level, at);
                slam(s, caster, g, big, struck);
            }
            return false;
        }, () -> end(s, false));
        return true;
    }

    // One hand: the shadow closes for the warning, then the drop; the hit lands where the shadow was.
    static void slam(Summon s, LivingEntity caster, Vec3 g, boolean big, Set<Integer> struck) {
        ServerLevel level = s.level;
        SubNet.send(level, HsNet.SLAM, caster.getId(), g, Vec3.ZERO, big ? 1 : 0, s.count++, 0);
        int land = big ? 30 : 18;
        double R = (big ? 6 : 2.3) * PU;
        Tasks.add(age -> {
            if (s.ended) {
                return true;
            }
            if (age < land) {
                return false;
            }
            JjCommon.crater(level, g, (big ? 6.2 : 2.2) * PU, big ? 3 : 1.2, big ? 120 : 16);
            Hashirama.dust(level, g, big ? 16 : 5.5f);
            DbCommon.screen(level, g, big ? 6 : 8);
            SubCommon.sound(level, g, SoundEvents.GENERIC_EXPLODE.value(), big ? 3f : 1.6f, big ? 0.4f : 0.7f);
            SubCommon.sound(level, g, SoundEvents.WOOD_BREAK, 2f, 0.4f);
            for (LivingEntity e : Hashirama.near(level, g, R * 0.95, 2, big ? 8 : 4, caster)) {
                Combat.hurt(caster, e, big ? 60 : 26);
                Vec3 away = SubCommon.flat(e.position().subtract(g));
                Hashirama.throwPage(e, away.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : away, big ? 10 : 6, big ? 12 : 7);
                Hashirama.stun(caster, e, big ? 40 : 25);
                struck.add(e.getId());
            }
            return true;
        });
    }
}
