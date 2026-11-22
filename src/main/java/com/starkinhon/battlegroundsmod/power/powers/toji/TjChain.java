// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.toji;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// Toji's Corrente de Mil Milhas as the approved fan (Stark 2026-11-22: "ele fica girando a lança com a corrente nessa
// area inteira, destruindo a porra toda, dando dano... q nem um ventilador"). He stands in the middle and turns the
// spear round himself on its endless chain, paying chain out each turn:
//   gira        1.1 s close to his body (the warning), the spear over his head
//   abre        2.4 s the chain goes out to 60 blocks, slowing down
//   ventilador  3.2 s at full reach, a turn every 1.5 s
//   recolhe     1.3 s reeled back in
// Everything the chain passes is cut 3 blocks over the street (the block turns to stumps). Whoever it catches is hurt
// and thrown along its way (the spear at the end hurts more); once per 0.8 s each. The answer: hop it (it runs low),
// stay glued to him inside the spin, or get out of its reach. The client draws the chain with physics (TjFx, FAN).
final class TjChain {
    static final double RMAX = 60;
    private static final int END = 160;

    private TjChain() {
    }

    static boolean corrente(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 70);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 c = caster.position();
        int street = Toji.base(caster);
        int cutY = street + 3;
        int fan = caster.getId();
        // the spear goes onto the chain: nothing in his hand while it turns
        Toji.weapon(caster, ItemStack.EMPTY);
        Toji.armor(caster, END + 10);
        SubCommon.pose(caster, "db.lanca_alto");
        SubCommon.sound(level, c, SoundEvents.CHAIN_PLACE, 2.5f, 0.5f);
        double[] th = {Math.atan2(target.getZ() - c.z, target.getX() - c.x) + 2.4};
        Ruin.Cutter cut = Ruin.cutter(level);
        Map<Integer, Long> hitAt = new HashMap<>();
        Runnable end = () -> {
            Toji.send(level, TjNet.FAN, fan, c, Vec3.ZERO, 0, 0, 0);
            cut.close();
            Toji.weapon(caster, Toji.spear());
        };
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive() || age > END) {
                end.run();
                return true;
            }
            Toji.still(caster);
            caster.setPos(c.x, caster.getY(), c.z);
            double t = age / 20.0, om, R;
            if (age < 22) {
                om = 6.5 * Toji.smooth(age / 12.0);
                R = 3 + 4 * Toji.smooth(age / 20.0);
            } else if (age < 70) {
                double k = Toji.smooth((age - 22) / 48.0);
                om = 6.5 - 2.4 * k;
                R = 7 + (RMAX - 7) * k;
            } else if (age < 134) {
                om = 4.1;
                R = RMAX + Math.sin(t * 3) * 1.5;
            } else {
                double k = Toji.smooth((age - 134) / 26.0);
                om = 4.1 * (1 - k) + 1;
                R = RMAX * (1 - k) + 2;
            }
            double prev = th[0];
            th[0] += om / 20;
            double sy = 3.2 + Math.sin(th[0] * 2) * 1.2 + (R < 12 ? (12 - R) * 0.6 : 0);
            // he turns with it, the arm up
            Toji.face(caster, new Vec3(Math.cos(th[0]), 0, Math.sin(th[0])));
            Toji.send(level, TjNet.FAN, fan, c, new Vec3(th[0], R, sy), 1, (float) om, 0);
            // the cut: every column the chain swept since last tick, 3 blocks over the street
            if (R > 5) {
                double da = th[0] - prev;
                for (double r = 4; r <= R; r += 0.8) {
                    int steps = Math.max(1, Mth.ceil(Math.abs(da) * r / 0.8));
                    for (int s = 0; s <= steps; s++) {
                        double an = prev + da * s / steps;
                        cut.add(Mth.floor(c.x + Math.cos(an) * r), Mth.floor(c.z + Math.sin(an) * r), cutY);
                    }
                }
            }
            // whoever it catches
            long now = level.getGameTime();
            for (LivingEntity e : Toji.near(level, c, R + 1.5, 3, 14, caster)) {
                double dx = e.getX() - c.x, dz = e.getZ() - c.z, r = Math.sqrt(dx * dx + dz * dz);
                if (r < 3 || e.getY() > street + 1 + 3.6 || now - hitAt.getOrDefault(e.getId(), -100L) < 16) {
                    continue;
                }
                double ang = Math.atan2(dz, dx);
                double off = Mth.wrapDegrees((ang - prev) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                double sweep = th[0] - prev;
                if (off < -0.2 || off > sweep + 0.1) {
                    continue;
                }
                hitAt.put(e.getId(), now);
                boolean tip = r > R - 3;
                Toji.hurt(caster, e, tip ? 44 : 30);
                Toji.stun(caster, e, 25);
                Vec3 tan = new Vec3(-Math.sin(th[0]), 0, Math.cos(th[0]));
                Combat.push(e, tan.add(dx / r * 0.6, 0, dz / r * 0.6), 2.4, 0.7);
                Toji.hitFx(level, e);
            }
            // each turn at full reach: the ring of wind, the ground shaking
            if (Math.floor(prev / Mth.TWO_PI) != Math.floor(th[0] / Mth.TWO_PI) && R > 15) {
                RuinNet.ring(level, c.add(0, 3, 0), R + 6, 3, true, 16, 0xCFCBD6);
                RuinNet.ring(level, c, R + 10, 4, false, 28, Toji.DUST_LIGHT);
                RuinNet.tremor(level, c, 0.4, 50, R + 10);
            }
            if (R > 20 && age % 3 == 0) {
                double a = th[0] - 0.3 - Toji.RNG.nextDouble() * 0.8, r = R * (0.4 + Toji.RNG.nextDouble() * 0.6);
                RuinNet.dust(level, new Vec3(c.x + Math.cos(a) * r, street + 1, c.z + Math.sin(a) * r), 7 + Toji.RNG.nextDouble() * 6, 5, 48, 6, Toji.DUST);
            }
            if (age % 5 == 0) {
                Vec3 tipAt = new Vec3(c.x + Math.cos(th[0]) * R, c.y + sy, c.z + Math.sin(th[0]) * R);
                SubCommon.sound(level, tipAt, SoundEvents.PLAYER_ATTACK_SWEEP, 2.5f, 0.5f + (float) (om / 13));
                SubCommon.sound(level, c, SoundEvents.CHAIN_HIT, 1.5f, 0.6f);
            }
            if (age % 8 == 0) {
                RuinNet.quake(level, c, R > 20 ? 0.8 : 0.3, false, 0xFFFFFF);
            }
            return false;
        }, end);
        return true;
    }
}
