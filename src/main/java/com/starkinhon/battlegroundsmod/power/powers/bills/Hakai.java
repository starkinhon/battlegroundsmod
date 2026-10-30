package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
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
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Hakai (bills.hakai.json). Bills appears in front of whoever he aims at, the camera closes in on his eyes
// while he says "Hakai.", he opens his hand at them in a white and purple flash and they are erased: the body lights up
// and falls apart into purple sparks from the head down (client/fx/AshDeath, hakai style). The strongest move of the
// mod: once the hand has opened nothing stops it, and the kill goes through everything (the hakai damage type is in
// every bypass tag, so armor, shields, totems, creative, intangibility and a boss's last life don't count). Only a
// target already under hitkill.limiteVida of its life is erased; anyone else gets the hakai ball (HakaiBola).
public final class Hakai {
    public static final String SKILL = HakaiSpec.SKILL;
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "hakai"));

    // Whoever is falling apart right now, so a second hit doesn't start it again.
    private static final Set<LivingEntity> ERASING = new HashSet<>();

    private Hakai() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        HakaiSpec spec = HakaiSpec.get(awakened);
        LivingEntity target = lock(caster, spec.alvo().alcance());
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        if (!spec.canErase(target)) {
            // Still too much life to erase: the hakai ball instead.
            return HakaiBola.use(caster, level, target);
        }
        int touch = Math.max(1, spec.tempo().atrasoTicks());
        int dissolve = spec.dissolveTick();
        int kill = spec.killTick();
        int end = spec.cutsceneEnd();
        if (spec.alvo().aparecer()) {
            appear(level, caster, target, spec.alvo().distancia());
        }
        face(caster, target);
        face(target, caster);

        // Both stand still for the whole scene; the target can't use anything, not even a counter.
        data.busyTicks = end;
        Freeze.hold(caster, end);
        Freeze.hold(target, kill + 1);
        Stun.apply(target, kill + 1);
        PowerLogic.seal(target, kill + 1);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, end);
            Intangible.hold(target, kill + 1);
        }
        List<ServerPlayer> screen = screens(caster, target);
        if (spec.cutscene().ativa()) {
            for (ServerPlayer player : screen) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.HakaiCutscene(caster.getId(), target.getId(), awakened));
            }
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));

        Function<String, Vec3> where = onde -> switch (onde == null ? "" : onde) {
            case "alvo" -> Combat.center(target);
            case "olhos" -> caster.getEyePosition();
            case "mao" -> hand(caster, target);
            default -> caster.position().add(0, 1, 0);
        };
        SkillSounds.play(level, spec.sons(), "uso", 0, touch, where, screen);
        SkillSounds.play(level, spec.sons(), "mao", touch, dissolve - touch, where, screen);
        SkillSounds.play(level, spec.sons(), "hakai", dissolve, kill - dissolve, where, screen);
        SkillSounds.play(level, spec.sons(), "fim", kill, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, touch);
        // His eyes light up yellow while he speaks (souls_auras/destruction_god.json: duranteSkills).
        Fx.effects(level, SKILL, awakened, "olhosLigar", caster, kill);

        int interrupts = data.interrupts;
        boolean[] cut = {false};
        Tasks.later(touch, () -> {
            // Only a Bills taken out before his hand opens misses; after that the target is already gone.
            if (!caster.isAlive() || data.interrupts != interrupts || !target.isAlive()) {
                cut[0] = true;
                Fx.effects(level, SKILL, awakened, "olhosDesligar", caster, 0);
                return;
            }
            Fx.effects(level, SKILL, awakened, "mao", hand(caster, target), Combat.center(target).subtract(caster.getEyePosition()).normalize());
        });
        Tasks.later(dissolve, () -> {
            if (cut[0] || !target.isAlive()) {
                return;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.HakaiDeath(target.getId(), awakened));
            Fx.effects(level, SKILL, awakened, "hakai", target, kill - dissolve);
        });
        Tasks.later(kill, () -> {
            if (cut[0]) {
                return;
            }
            Fx.effects(level, SKILL, awakened, "olhosDesligar", caster, 0);
            Fx.effects(level, SKILL, awakened, "fim", Combat.center(target), Vec3.ZERO);
            erase(caster, target);
        });
        return true;
    }

    // Erases target without the cutscene (the hakaizinho finishing someone low on life): it stops where it is, lights up
    // and falls apart into purple sparks like under the Hakai, and is gone at the end.
    public static void disintegrate(LivingEntity caster, LivingEntity target, boolean awakened) {
        if (!target.isAlive() || !ERASING.add(target)) {
            return;
        }
        HakaiSpec spec = HakaiSpec.get(awakened);
        int ticks = spec.killTick() - spec.dissolveTick();
        Freeze.hold(target, ticks + 1);
        Stun.apply(target, ticks + 1);
        PowerLogic.seal(target, ticks + 1);
        Intangible.hold(target, ticks + 1);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.HakaiDeath(target.getId(), awakened));
        Fx.effects((ServerLevel) target.level(), SKILL, awakened, "hakai", target, ticks);
        Tasks.later(ticks, () -> {
            ERASING.remove(target);
            Fx.effects((ServerLevel) target.level(), SKILL, awakened, "fim", Combat.center(target), Vec3.ZERO);
            erase(caster, target);
        });
    }

    // Kills it no matter what: the hakai damage first (credited to Bills, with its own death message), then /kill's,
    // and anything that still stands (a mob of another mod refusing to die) is removed from the world.
    public static void erase(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        DamageSource source = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DAMAGE)
                .map(type -> new DamageSource(type, caster))
                .orElseGet(() -> target.damageSources().genericKill());
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);
        if (target.isAlive()) {
            target.kill();
        }
        if (target.isAlive() && !(target instanceof Player)) {
            target.discard();
        }
    }

    // Whoever he is fighting, if close enough and in front of him; else whoever he aims at.
    private static LivingEntity lock(LivingEntity caster, double range) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= range && caster.hasLineOfSight(fought)
                && flatTo(caster, fought).dot(Combat.flatLook(caster)) > 0.5) {
            return fought;
        }
        return Combat.raycastTarget(caster, range);
    }

    // Steps in front of the target, distance blocks away on the side he came from, if there is room (same height or a
    // block up or down); else he stays where he is.
    private static void appear(ServerLevel level, LivingEntity caster, LivingEntity target, double distance) {
        Vec3 from = flatTo(target, caster);
        Vec3 spot = target.position().add(from.scale(Math.max(0.8, distance)));
        for (double dy : new double[] {0, 1, -1}) {
            Vec3 at = spot.add(0, dy, 0);
            if (level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())))) {
                Fx.effects(level, SKILL, caster.getData(ModAttachments.POWER).awakened, "sumir", caster.position().add(0, 1, 0), Vec3.ZERO);
                move(caster, at, caster.getYRot(), caster.getXRot());
                return;
            }
        }
    }

    // Turns one to look the other in the eyes (Freeze keeps the turn).
    private static void face(LivingEntity who, LivingEntity at) {
        Vec3 look = at.getEyePosition().subtract(who.getEyePosition());
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, Math.max(0.1, look.horizontalDistance())) * Mth.RAD_TO_DEG);
        move(who, who.position(), yaw, pitch);
        who.setYHeadRot(yaw);
        who.setYBodyRot(yaw);
    }

    private static void move(LivingEntity who, Vec3 at, float yaw, float pitch) {
        who.setDeltaMovement(Vec3.ZERO);
        if (who instanceof ServerPlayer player) {
            player.connection.teleport(at.x, at.y, at.z, yaw, pitch);
        } else {
            who.moveTo(at.x, at.y, at.z, yaw, pitch);
        }
        who.setYRot(yaw);
        who.setXRot(pitch);
        who.hurtMarked = true;
    }

    // His open palm, reaching out towards the target's chest.
    private static Vec3 hand(LivingEntity caster, LivingEntity target) {
        Vec3 shoulder = caster.position().add(0, caster.getBbHeight() * 0.75, 0);
        Vec3 to = Combat.center(target).subtract(shoulder);
        return shoulder.add(to.normalize().scale(Math.min(0.9, to.length() * 0.5)));
    }

    private static List<ServerPlayer> screens(LivingEntity caster, LivingEntity target) {
        List<ServerPlayer> out = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            out.add(player);
        }
        if (target instanceof ServerPlayer player) {
            out.add(player);
        }
        return out;
    }

    private static Vec3 flatTo(LivingEntity from, LivingEntity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }
}
