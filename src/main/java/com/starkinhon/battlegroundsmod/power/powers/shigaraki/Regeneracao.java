package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

// Shift+Z, Regeneração (preview v6). He stops, hunches over and the body remakes itself: muscle fibres out of the
// shoulder braid the arm back, the wounds are sewn from inside and the skin closes over them (client/fx/shig/RegenFx).
// Strong heal (the preview's bar: 30% to 100% in 3 s, here 70% of the max health over 0.4 to 3.1 s, to the beat of the
// heart), but it costs 75% of the stamina and he can't move: a real stun cuts it short (whatever was healed stays).
public final class Regeneracao {
    private static final int LENGTH = 88, HEAL_FROM = 8, HEAL_TO = 62;
    private static final float SHARE = 0.7f, STAMINA = 75;

    private Regeneracao() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (Stamina.exhausted(caster)) {
            SubCommon.warn(caster, "shigaraki.regen_cansado");
            return false;
        }
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        SubCommon.busy(caster, HEAL_TO + 4);
        SubCommon.pose(caster, "shig.regen");
        SubNet.send(level, Shigaraki.REGEN, caster.getId(), caster.position(), caster.getLookAngle(), LENGTH, 0, 0);
        Runnable release = Freeze.holdLookFree(caster, HEAL_TO + 2);
        // A boss heals half as much: 70% from 29% gave him the whole fight back (test 2026-11-12).
        float total = caster.getMaxHealth() * (caster instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss ? SHARE * 0.5f : SHARE);
        float per = total / (HEAL_TO - HEAL_FROM);
        float drainPer = STAMINA / (HEAL_TO - 4);
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts) {
                release.run();
                SubNet.send(level, Shigaraki.REGEN, caster.getId(), caster.position(), caster.getLookAngle(), 0, 1, 0);
                return true;
            }
            if (age >= 4 && age < HEAL_TO) {
                Stamina.drain(caster, drainPer);
            }
            if (age >= HEAL_FROM && age < HEAL_TO) {
                caster.heal(per);
            }
            // the heart (tum-tum, 1.55 a second)
            if (age % 13 == 0 && age < HEAL_TO) {
                SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.3f, 0.8f);
            }
            if (age == 20 || age == 40) {
                SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 0.8f, 0.6f);
            }
            if (age >= HEAL_TO) {
                release.run();
                return true;
            }
            return false;
        });
        return true;
    }
}
