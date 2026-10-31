package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.HashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.SusanooSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// C: Susanoo counter. Opens a short window; the first hit taken in it is blocked, and a spectral arm grabs the
// attacker, crushes them and throws them away. Timing and damage come from madara.susanoo.json.
public final class Susanoo {
    public static final String SKILL = "madara.susanoo";
    // The over-the-shoulder look at the held enemy: short, so it never gets in the way of a real fight.
    private static final int CUTSCENE_TICKS = 34;

    // Casters with the counter ready. The value tells a window apart from a later one, so an old timer can't close it.
    private static final Map<LivingEntity, Object> READY = new HashMap<>();

    private Susanoo() {
    }

    public static boolean use(LivingEntity player, ServerLevel level) {
        SusanooSpec spec = spec(player);
        Object window = new Object();
        READY.put(player, window);
        UchihaFx.susanooReady(level, player, spec);
        arm(player, -1, SusanooArmPhase.GUARD, spec.janela().duracaoTicks(), 0);
        Fx.effects(level, SKILL, awakened(player), "pronto", player, spec.janela().duracaoTicks());
        Tasks.add(age -> {
            if (READY.get(player) != window) {
                return true;
            }
            if (age >= spec.janela().duracaoTicks() || !player.isAlive()) {
                READY.remove(player);
                arm(player, -1, SusanooArmPhase.END, 0, 0);
                return true;
            }
            return false;
        });
        return true;
    }

    // A sealed or interrupted caster loses the counter window.
    public static void cancel(LivingEntity entity) {
        if (READY.remove(entity) != null) {
            arm(entity, -1, SusanooArmPhase.END, 0, 0);
        }
    }

    // True while the counter window is open (bosses read this to know their guard is up).
    public static boolean isReady(LivingEntity entity) {
        return READY.containsKey(entity);
    }

    // Called for every hit a living entity is about to take.
    public static void onIncomingDamage(LivingEntity player, LivingIncomingDamageEvent event) {
        if (!READY.containsKey(player) || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player || !attacker.isAlive()) {
            return;
        }
        SusanooSpec spec = spec(player);
        if (attacker.distanceTo(player) > spec.janela().alcanceMax()) {
            return;
        }
        event.setCanceled(true);
        READY.remove(player);
        // The hit lands on the shield in front of him.
        Fx.effects(Combat.level(player), SKILL, awakened(player), "bloqueio",
                player.position().add(Combat.flatLook(player).scale(3.0)).add(0, 2.1, 0), Combat.flatLook(player));
        grab(player, attacker, spec);
    }

    private static void grab(LivingEntity player, LivingEntity target, SusanooSpec spec) {
        ServerLevel level = Combat.level(player);
        SusanooSpec.Agarrar agarrar = spec.agarrar();
        SusanooSpec.Esmagar esmagar = spec.esmagar();
        int reach = Math.max(1, agarrar.alcancarTicks());
        int total = reach + esmagar.duracaoTicks();
        PowerData data = player.getData(ModAttachments.POWER);
        data.busyTicks = Math.max(data.busyTicks, total);

        // The target's feet follow the hand, so their middle sits in the fist.
        Vec3 start = target.position();
        double half = target.getBbHeight() / 2;
        Freeze.hold(target, total, age -> {
            Vec3 held = hand(player, spec).subtract(0, half, 0);
            return start.lerp(held, smooth(age / (double) reach));
        });
        UchihaFx.susanooGrab(level, player, target, spec);
        // The wall goes down, the ball of light holds the target, and the caster's camera looks over his shoulder.
        Fx.effects(level, SKILL, awakened(player), "fimJanela", player, 0);
        Fx.effects(level, SKILL, awakened(player), "agarrar", target, total);
        if (player instanceof ServerPlayer caster) {
            PacketDistributor.sendToPlayer(caster, new ModPayloads.ShoulderCutscene(player.getId(), target.getId(), CUTSCENE_TICKS));
        }
        // The arm itself is drawn on the clients (client/fx/SusanooArm): it reaches, holds, squeezes and throws.
        arm(player, target.getId(), SusanooArmPhase.GRAB, reach, esmagar.duracaoTicks());

        int hits = Math.max(1, esmagar.golpes());
        for (int i = 1; i <= hits; i++) {
            Tasks.later(reach + esmagar.duracaoTicks() * i / (hits + 1), () -> {
                if (target.isAlive() && player.isAlive()) {
                    Combat.hurt(player, target, esmagar.dano());
                    arm(player, target.getId(), SusanooArmPhase.HIT, 0, 0);
                    UchihaFx.susanooCrush(level, target, spec);
                    Fx.effects(level, SKILL, awakened(player), "esmagar", target, 0);
                }
            });
        }
        Tasks.later(total, () -> {
            arm(player, target.getId(), target.isAlive() && player.isAlive() ? SusanooArmPhase.THROW : SusanooArmPhase.END, 0, 0);
            if (target.isAlive() && player.isAlive()) {
                SusanooSpec.Arremesso arremesso = spec.arremesso();
                Combat.hurt(player, target, arremesso.dano());
                Combat.push(target, Combat.flatLook(player), arremesso.forca(), arremesso.altura());
                UchihaFx.susanooThrow(level, target, spec);
                Fx.effects(level, SKILL, awakened(player), "arremesso", Combat.center(target), Combat.flatLook(player));
                Fx.effects(level, SKILL, awakened(player), "solto", target, 0);
            }
        });
    }

    // Where the Susanoo's fist is: ahead of the caster and up in the air.
    public static Vec3 hand(LivingEntity player, SusanooSpec spec) {
        return player.position().add(Combat.flatLook(player).scale(spec.agarrar().frente())).add(0, spec.agarrar().altura(), 0);
    }

    // The arm comes out from behind the caster's right shoulder, as if a giant stood behind them.
    public static Vec3 shoulder(LivingEntity player) {
        Vec3 flat = Combat.flatLook(player);
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return player.position().add(flat.scale(-0.8)).add(right.scale(1.2)).add(0, 2.6, 0);
    }

    public static void clear() {
        READY.clear();
    }

    // Phases of the arm the clients draw (ModPayloads.SusanooArm, client/fx/SusanooArm).
    private static final class SusanooArmPhase {
        static final int GUARD = 0, GRAB = 1, HIT = 2, THROW = 3, END = 4;
    }

    private static void arm(LivingEntity player, int target, int phase, int a, int b) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ModPayloads.SusanooArm(player.getId(), target, phase, a, b));
    }

    private static boolean awakened(LivingEntity player) {
        return player.getData(ModAttachments.POWER).awakened;
    }

    private static SusanooSpec spec(LivingEntity player) {
        return SkillSpecs.get(SKILL, SusanooSpec.class, player.getData(ModAttachments.POWER).awakened);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
