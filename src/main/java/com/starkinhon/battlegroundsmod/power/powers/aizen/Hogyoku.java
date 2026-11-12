package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.ArrayList;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Evolucao do Hogyoku, Aizen's special (V, aizen.hogyoku.json). The Hogyoku lights up in his chest, a white cocoon closes
// around him (he can't be hurt or move inside it), cracks with violet light and bursts: he comes out with six black
// butterfly wings, the burst throwing everyone near away. For duracaoTicks he is evolved: stronger, faster, tougher, his
// Black Box is the full incantation (Kurohitsugi, the "despertado" block of aizen.black_box.json), and V again is the
// dash of cuts (aizen.dash.json): he vanishes, cuts the target from every side, shows up behind it and every cut lights
// up at once. Drawn on the clients by client/fx/AizenFx.
// Timeline: 0 "despertar", casuloTicks "casulo", eclosaoTicks "eclosao", the end "fim". Dash: 0 "sumir", "corte" on each
// cut, "aparecer", "final".
public final class Hogyoku {
    public static final String SKILL = AizenSpecs.HOGYOKU;
    public static final String DASH = AizenSpecs.DASH;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private static final class State {
        final boolean wasAwakened;
        final int total;
        int age;
        boolean dashing;
        long nextDash;

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

    // ---------- the dash of cuts ----------

    // V while it is going: true when V was for this (the press is used up), so the ult isn't cast again.
    public static boolean dash(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        if (state == null) {
            return false;
        }
        if (!evolved(caster) || state.dashing || Freeze.isFrozen(caster) || !Stun.canAttack(caster)) {
            return true;
        }
        ServerLevel level = Combat.level(caster);
        if (level.getGameTime() < state.nextDash) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.hogyoku_dash_wait",
                        String.format("%.1f", (state.nextDash - level.getGameTime()) / 20.0)), true);
            }
            return true;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        AizenSpecs.Dash spec = AizenSpecs.Dash.get(data.awakened);
        LivingEntity target = BlackBox.lock(caster, spec.alcance());
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return true;
        }
        state.dashing = true;
        state.nextDash = level.getGameTime() + Math.max(0, spec.recargaTicks());
        runDash(caster, target, level, spec, data.awakened, state);
        return true;
    }

    private static void runDash(LivingEntity caster, LivingEntity target, ServerLevel level, AizenSpecs.Dash spec, boolean awakened, State state) {
        int cuts = Math.max(1, spec.cortes());
        int every = Math.max(1, spec.intervaloTicks());
        int first = Math.max(1, spec.sumirTicks());
        int appear = first + cuts * every + 2;
        int last = appear + Math.max(1, spec.finalTicks());
        Vec3 start = caster.position();
        List<Vec3> dirs = new ArrayList<>();
        for (int i = 0; i < cuts; i++) {
            Vec3 d = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
            d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
            dirs.add(new Vec3(d.x, Mth.clamp(d.y, -0.35, 0.65), d.z).normalize());
        }
        caster.getData(ModAttachments.POWER).busyTicks = last + 2;
        Freeze.hold(target, last + 2);
        Freeze.hold(caster, appear);
        Intangible.hold(caster, appear);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), appear));
        SkillSounds.play(level, spec.sons(), "sumir", 0, 0, onde -> Combat.center(caster), List.of());
        Fx.effects(level, DASH, awakened, "sumir", Combat.center(caster), new Vec3(0, 1, 0));

        Tasks.add(age -> {
            if (!caster.isAlive() || caster.isRemoved()) {
                Freeze.free(target);
                state.dashing = false;
                return true;
            }
            Vec3 middle = Combat.center(target);
            int k = age - first;
            if (k >= 0 && k % every == 0 && k / every < cuts) {
                Vec3 dir = dirs.get(k / every);
                AizenNet.send(level, AizenNet.CUT, caster.getId(), middle, dir, 2.4f, 1f);
                SkillSounds.play(level, spec.sons(), "corte", 0, 0, onde -> middle, List.of());
                Fx.effects(level, DASH, awakened, "corte", middle, dir);
                if (target.isAlive() && Combat.isTarget(target, caster)) {
                    Combat.hurtMagic(caster, target, spec.danoCorte());
                    Stun.apply(target, 10);
                }
            }
            if (age == appear) {
                Vec3 away = middle.subtract(start);
                away = new Vec3(away.x, 0, away.z);
                away = away.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster) : away.normalize();
                Vec3 behind = target.position().add(away.scale(1.8));
                if (caster instanceof ServerPlayer player && !Combat.canStandAt(player, behind)) {
                    behind = caster.position();
                }
                // His back to the target.
                float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
                Freeze.free(caster);
                Freeze.place(caster, behind, yaw, 0);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), DASH, awakened));
                SkillSounds.play(level, spec.sons(), "aparecer", 0, 0, onde -> Combat.center(caster), List.of());
            }
            if (age < last) {
                return false;
            }
            for (Vec3 dir : dirs) {
                AizenNet.send(level, AizenNet.CUT, caster.getId(), middle, dir, 3.0f, 1.6f);
            }
            SkillSounds.play(level, spec.sons(), "final", 0, 0, onde -> middle, List.of());
            Fx.effects(level, DASH, awakened, "final", middle, Combat.flatLook(caster));
            Freeze.free(target);
            state.dashing = false;
            if (target.isAlive() && Combat.isTarget(target, caster)) {
                Combat.hurtMagic(caster, target, spec.danoFinal());
                Vec3 push = target.position().subtract(caster.position());
                Combat.push(target, push.lengthSqr() < 1.0E-4 ? Combat.flatLook(caster).reverse() : push.normalize(), spec.empurrao(), 0.6);
                Stun.apply(target, 20);
            }
            return true;
        });
    }

    private static Vec3 chest(LivingEntity caster) {
        return caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(Combat.flatLook(caster).scale(0.2));
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
