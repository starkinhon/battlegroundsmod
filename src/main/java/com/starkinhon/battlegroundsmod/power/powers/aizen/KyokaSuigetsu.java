package com.starkinhon.battlegroundsmod.power.powers.aizen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.AizenSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Kyoka Suigetsu, Aizen's X (aizen.kyoka.json). With no pose, sound or light (so no one can tell when), a clone of him
// stays standing in his place
// (AizenIllusion) while the real one turns invisible to everyone else and walks free, for up to limiteTicks. Pressing X
// again (or the time running out, or anyone hitting the clone) breaks it: the clone cracks and shatters like a mirror
// and he shows up wherever he is. Mobs that were after him go after the clone. It is not a counter: the clone never
// hits back. The cooldown starts when it breaks.
// Timeline: on the press again "rachar", quebrarTicks later "quebrar" (at the clone) and "aparecer" (at him).
public final class KyokaSuigetsu {
    public static final String SKILL = AizenSpecs.KYOKA;
    public static final int SLOT = 1;

    private static final class State {
        final LivingEntity caster;
        AizenIllusion clone;
        boolean breaking;
        int unseenFor;

        State(LivingEntity caster) {
            this.caster = caster;
        }
    }

    private static final Map<LivingEntity, State> ACTIVE = new HashMap<>();

    private KyokaSuigetsu() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (ACTIVE.containsKey(caster)) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.Kyoka spec = AizenSpecs.Kyoka.get(awakened);
        State state = new State(caster);
        ACTIVE.put(caster, state);
        String power = data.power;
        int limit = Math.max(20, spec.limiteTicks());
        Function<String, Vec3> where = onde -> "alvo".equals(onde) && state.clone != null ? Combat.center(state.clone) : Combat.center(caster);
        // No pose, no sound, no light: the clone takes his place right where he stands, exactly as he stands, so no one
        // can tell when he used it.
        swapIn(caster, level, state, limit);
        if (state.clone == null) {
            return false;
        }

        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            if (state.breaking) {
                return true;
            }
            if (gone) {
                end(caster, level, state, spec, awakened, where, 0);
                return true;
            }
            state.unseenFor++;
            // Anyone who just came near also stops seeing him.
            if (state.unseenFor % 20 == 0) {
                hide(caster, limit - state.unseenFor + 4);
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, caster.getBoundingBox().inflate(40), m -> m.getTarget() == caster)) {
                if (state.clone.isAlive()) {
                    mob.setTarget(state.clone);
                }
            }
            if (state.unseenFor >= limit || !state.clone.isAlive()) {
                breakIllusion(caster);
            }
            return false;
        });
        return true;
    }

    private static void swapIn(LivingEntity caster, ServerLevel level, State state, int limit) {
        AizenIllusion clone = ModEntities.AIZEN_ILLUSION.get().create(level);
        if (clone == null) {
            ACTIVE.remove(caster);
            return;
        }
        clone.copy(caster);
        level.addFreshEntity(clone);
        state.clone = clone;
        hide(caster, limit + 4);
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, limit + 4, 0, false, false, false));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, caster.getBoundingBox().inflate(40), m -> m.getTarget() == caster)) {
            mob.setTarget(clone);
        }
    }

    // X again while it is up. True when there was an illusion to break (the key press is used up either way).
    public static boolean breakIllusion(LivingEntity caster) {
        State state = ACTIVE.get(caster);
        if (state == null) {
            return false;
        }
        if (state.clone == null || state.breaking) {
            return true;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        AizenSpecs.Kyoka spec = AizenSpecs.Kyoka.get(awakened);
        ServerLevel level = Combat.level(caster);
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? Combat.center(state.clone) : Combat.center(caster);
        end(caster, level, state, spec, awakened, where, Math.max(1, spec.quebrarTicks()));
        return true;
    }

    static void cloneHit(AizenIllusion clone) {
        for (State state : ACTIVE.values()) {
            if (state.clone == clone) {
                breakIllusion(state.caster);
                return;
            }
        }
    }

    private static void end(LivingEntity caster, ServerLevel level, State state, AizenSpecs.Kyoka spec, boolean awakened,
            Function<String, Vec3> where, int crack) {
        state.breaking = true;
        AizenIllusion clone = state.clone;
        Vec3 feet = clone.position();
        Vec3 facing = Combat.flatLook(clone);
        AizenNet.send(level, AizenNet.MIRROR_CRACK, clone.getId(), feet, facing, crack, 0);
        SkillSounds.play(level, spec.sons(), "rachar", 0, 0, where, List.of());
        Tasks.later(crack, () -> {
            Vec3 middle = feet.add(0, 1.1, 0);
            AizenNet.send(level, AizenNet.MIRROR_BREAK, clone.getId(), feet, facing, 0, 0);
            SkillSounds.play(level, spec.sons(), "quebrar", 0, 0, onde -> "alvo".equals(onde) ? middle : Combat.center(caster), List.of());
            Fx.effects(level, SKILL, awakened, "quebrar", middle, facing);
            clone.discard();
            ACTIVE.remove(caster);
            if (caster.isAlive()) {
                hide(caster, 0);
                caster.removeEffect(MobEffects.INVISIBILITY);
                SkillSounds.play(level, spec.sons(), "aparecer", 0, 0, onde -> Combat.center(caster), List.of());
                Fx.effects(level, SKILL, awakened, "aparecer", Combat.center(caster), new Vec3(0, 1, 0));
                Fx.effects(level, SKILL, awakened, "aparecer", caster.position().add(0, 0.05, 0), new Vec3(0, 1, 0));
                PowerData data = caster.getData(ModAttachments.POWER);
                if (!data.noCooldown) {
                    data.cooldowns[SLOT] = Math.max(data.cooldowns[SLOT], spec.recargaTicks());
                }
                if (caster instanceof ServerPlayer player) {
                    PowerLogic.sync(player);
                }
            }
        });
    }

    public static boolean active(LivingEntity caster) {
        return ACTIVE.containsKey(caster);
    }

    // Not drawn for anyone but himself (ticks 0 = drawn again).
    private static void hide(LivingEntity caster, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(caster, new ModPayloads.HideEntity(caster.getId(), ticks));
    }

    public static void clear() {
        for (State state : ACTIVE.values()) {
            if (state.clone != null) {
                state.clone.discard();
            }
        }
        ACTIVE.clear();
    }
}
