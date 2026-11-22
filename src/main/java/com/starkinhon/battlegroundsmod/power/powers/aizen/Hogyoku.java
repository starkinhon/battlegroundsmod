// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Evolucao do Hogyoku, Aizen's special (V, aizen.hogyoku.json). The Hogyoku lights up in his chest, a white cocoon closes
// around him (he can't be hurt or move inside it), cracks with violet light and bursts: he comes out with six black
// wings (white with a violet edge, as in the references), the burst throwing everyone near away and opening a crater.
// For duracaoTicks he is evolved: stronger, faster, tougher, his Black Box is the full incantation (Kurohitsugi, the
// "despertado" block of aizen.black_box.json), V again throws the Fragor and Shift+V casts the Hado 99 (once a form).
// Drawn on the clients by client/fx/AizenKit (forja/aizen-kit, the hogyoku scene).
// Timeline: 0 "despertar", casuloTicks "casulo", eclosaoTicks "eclosao", the end "fim".
public final class Hogyoku {
    public static final String SKILL = AizenSpecs.HOGYOKU;
    // The Fragor's own cooldown while evolved (25 s).
    static final int FRAGOR_COOLDOWN = 500;

    private static final class State {
        final boolean wasAwakened;
        final int total;
        int age;
        boolean casting;
        boolean hadoUsed;
        long nextFragor;

        void castDone() {
            casting = false;
        }

        State(boolean wasAwakened, int total) {
            this.wasAwakened = wasAwakened;
            this.total = total;
        }
    }

    private static final Map<LivingEntity, State> ACTIVE = new HashMap<>();

    private Hogyoku() {
    }

    public static boolean evolved(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        return state != null && state.age >= AizenSpecs.Hogyoku.get(false).eclosaoTicks();
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (ACTIVE.containsKey(caster)) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.Hogyoku spec = AizenSpecs.Hogyoku.get(awakened);
        int hatch = Math.max(20, spec.eclosaoTicks());
        int cocoon = Mth.clamp(spec.casuloTicks(), 0, hatch);
        int total = hatch + Math.max(20, spec.duracaoTicks());
        State state = new State(awakened, total);
        ACTIVE.put(caster, state);
        String power = data.power;
        Freeze.hold(caster, hatch);
        Intangible.hold(caster, hatch);
        data.busyTicks = hatch;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Function<String, Vec3> where = onde -> Combat.center(caster);
        SkillSounds.play(level, spec.sons(), "despertar", 0, 0, where, List.of());
        Fx.effects(level, SKILL, awakened, "despertar", chest(caster), Combat.flatLook(caster));
        AizenNet.send(level, AizenNet.HOGYOKU, caster.getId(), caster.position(), Vec3.ZERO, 0, total);

        Tasks.add(age -> {
            state.age = age;
            PowerData now = caster.getData(ModAttachments.POWER);
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || age >= total) {
                end(caster, level, spec, state);
                return true;
            }
            if (age == cocoon) {
                SkillSounds.play(level, spec.sons(), "casulo", 0, hatch - cocoon, where, List.of());
                Fx.effects(level, SKILL, awakened, "casulo", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
            }
            if (age < hatch) {
                now.busyTicks = Math.max(now.busyTicks, 2);
            }
            if (age == hatch) {
                hatch(caster, level, spec, awakened, total - hatch);
            }
            if (age % 20 == 10) {
                // Whoever came near since sees it too.
                AizenNet.send(level, AizenNet.HOGYOKU, caster.getId(), caster.position(), Vec3.ZERO, age, total);
            }
            if (age > hatch) {
                now.awakened = true;
            }
            return false;
        }, () -> end(caster, level, spec, state));
        return true;
    }

    private static void hatch(LivingEntity caster, ServerLevel level, AizenSpecs.Hogyoku spec, boolean awakened, int lasts) {
        Vec3 middle = Combat.center(caster);
        SkillSounds.play(level, spec.sons(), "eclosao", 0, 0, onde -> middle, List.of());
        Fx.effects(level, SKILL, awakened, "eclosao", middle, Combat.flatLook(caster));
        for (LivingEntity target : Combat.inRadius(level, middle, spec.ondaRaio(), caster)) {
            Combat.hurtMagic(caster, target, spec.ondaDano());
            Combat.pushFrom(target, middle, 2.0, 0.7);
        }
        AizenHit.crater(level, AizenHit.ground(level, caster.position()), 9, 2.2, 6, 0, true, 0xD8C4FF);
        buff(caster, MobEffects.DAMAGE_BOOST, spec.forca(), lasts);
        buff(caster, MobEffects.MOVEMENT_SPEED, spec.velocidade(), lasts);
        buff(caster, MobEffects.DAMAGE_RESISTANCE, spec.resistencia(), lasts);
        caster.getData(ModAttachments.POWER).awakened = true;
        com.starkinhon.battlegroundsmod.boss.BossFalas.say(caster, "fala.battlegroundsmod.aizen.hogyoku", 0);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.hogyoku"), true);
        }
    }

    private static void buff(LivingEntity caster, Holder<MobEffect> effect, int level, int ticks) {
        if (level >= 0) {
            caster.addEffect(new MobEffectInstance(effect, ticks, level, false, false, true));
        }
    }

    private static void end(LivingEntity caster, ServerLevel level, AizenSpecs.Hogyoku spec, State state) {
        ACTIVE.remove(caster);
        caster.getData(ModAttachments.POWER).awakened = state.wasAwakened;
        AizenNet.send(level, AizenNet.HOGYOKU_END, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
        if (caster.isAlive()) {
            caster.removeEffect(MobEffects.DAMAGE_BOOST);
            caster.removeEffect(MobEffects.MOVEMENT_SPEED);
            caster.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            SkillSounds.play(level, spec.sons(), "fim", 0, 0, onde -> Combat.center(caster), List.of());
        }
    }

    // ---------- V and Shift+V while evolved ----------

    // From PowerLogic, on V or Shift+V: true when the press was for the evolved form (used up here). V again throws
    // the Fragor (FRAGOR_COOLDOWN between throws); Shift+V the Hado 99, once a form. Shift+V outside the form only
    // says so.
    public static boolean press(LivingEntity caster, int slot) {
        boolean shift = slot == PowerData.SLOT_SHIFT_ULT;
        State state = ACTIVE.get(caster);
        PowerData data = caster.getData(ModAttachments.POWER);
        ServerPlayer player = caster instanceof ServerPlayer p ? p : null;
        if (state == null) {
            if (shift && player != null && "manipulator".equals(data.power) && com.starkinhon.battlegroundsmod.power.SkillSwap.ref(player, slot) == null) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.aizen_hado99_forma"), true);
                return true;
            }
            return false;
        }
        if (!evolved(caster) || state.casting || Freeze.isFrozen(caster) || !Stun.canAttack(caster)) {
            return true;
        }
        ServerLevel level = Combat.level(caster);
        if (shift) {
            if (state.hadoUsed) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.battlegroundsmod.aizen_hado99_usado"), true);
                }
                return true;
            }
            if (Hado99.use(caster, level, state::castDone)) {
                state.hadoUsed = true;
                state.casting = true;
            }
            return true;
        }
        if (level.getGameTime() < state.nextFragor) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.aizen_fragor_espera",
                        String.format("%.1f", (state.nextFragor - level.getGameTime()) / 20.0)), true);
            }
            return true;
        }
        if (Fragor.use(caster, level)) {
            state.nextFragor = level.getGameTime() + FRAGOR_COOLDOWN;
        }
        return true;
    }

    // For the boss's tactics: what the evolved form still has to throw.
    public static boolean fragorReady(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        return state != null && evolved(caster) && !state.casting && caster.level().getGameTime() >= state.nextFragor;
    }

    public static boolean hadoReady(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        return state != null && evolved(caster) && !state.casting && !state.hadoUsed;
    }

    // In the form at all (the cocoon included): V does nothing else then.
    public static boolean active(LivingEntity caster) {
        return ACTIVE.containsKey(caster);
    }

    // Ticks of the form left (0 when not in it).
    public static int left(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        return state == null ? 0 : Math.max(0, state.total - state.age);
    }

    private static Vec3 chest(LivingEntity caster) {
        return caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(Combat.flatLook(caster).scale(0.2));
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
