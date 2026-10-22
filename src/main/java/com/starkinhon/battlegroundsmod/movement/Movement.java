package com.starkinhon.battlegroundsmod.movement;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.MovementSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.TeleporteSpec;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// The standard moves every fighter has, separate from the powers: the back dodge (back + jump on the ground), the air
// jump (jump again in the air) and, in combat mode, the side dodge (left/right + jump on the ground), the dash (dash key,
// towards the move input) and the teleport to the target (double tap of the sprint key (Ctrl)). Values come from
// souls_skills/padrao.esquiva.json, padrao.pulo_duplo.json, padrao.desvio.json, padrao.dash.json and padrao.teleporte.json.
//
// Players: the client starts the move itself so it feels instant (client/MovementInput) and tells the server, which
// checks it and adds the rest (dodge window, fall protection, puffs, sounds).
// Mobs and bosses: call tryPerform on the server and tick every tick; it pushes the mob too.
public final class Movement {
    // Grace for the cooldown check on the server: the client's clock can be a few ticks ahead.
    private static final int COOLDOWN_SLACK = 5;
    // The no-fall effect lasts at least this long, then ends on landing, and never longer than the max.
    private static final int SAFE_FALL_MIN_TICKS = 20;
    private static final int SAFE_FALL_MAX_TICKS = 200;
    // A move's take-off tick still reads as "on the ground", so landing only counts after this.
    private static final int LANDING_DELAY = 3;
    private static final RandomSource RANDOM = RandomSource.create();

    public enum Kind {
        ESQUIVA("padrao.esquiva"),
        PULO_DUPLO("padrao.pulo_duplo"),
        DESVIO("padrao.desvio"),
        DASH("padrao.dash");

        public final String skill;

        Kind(String skill) {
            this.skill = skill;
        }

        // The effect shown while the move keeps fall damage off.
        public Holder<MobEffect> effect() {
            return this == PULO_DUPLO ? ModEffects.PULO_DUPLO : ModEffects.ESQUIVA;
        }

        public MovementSpec spec() {
            return SkillSpecs.get(skill, MovementSpec.class, false);
        }

        public static Kind byIndex(int index) {
            Kind[] all = values();
            return index >= 0 && index < all.length ? all[index] : null;
        }
    }

    private Movement() {
    }

    public static MovementData data(LivingEntity entity) {
        return entity.getData(ModAttachments.MOVEMENT);
    }

    // Can this entity move on its own right now? Both sides (the client can't see Freeze, the server checks that).
    public static boolean free(LivingEntity entity) {
        if (!entity.isAlive() || entity.isPassenger() || entity.isSpectator() || entity.isFallFlying() || entity.onClimbable()
                || entity.isInWater() || entity.isInLava() || entity.isSleeping() || Stun.is(entity)) {
            return false;
        }
        return !(entity instanceof Player player && player.getAbilities().flying);
    }

    // The rules for starting a move, the same on both sides (cooldown slack only on the server).
    public static boolean allowed(LivingEntity entity, Kind kind, int slack) {
        MovementSpec spec = kind.spec();
        MovementData data = data(entity);
        if (!free(entity) || entity.level().getGameTime() + slack < data.readyAt[kind.ordinal()]) {
            return false;
        }
        if (!spec.funcionaNoCriativo() && entity instanceof Player player && player.getAbilities().mayfly) {
            return false;
        }
        // The side dodge and the dash are fight moves: players only get them in combat mode.
        if ((kind == Kind.DESVIO || kind == Kind.DASH) && entity instanceof Player && !CombatMode.in(entity)) {
            return false;
        }
        return switch (kind) {
            // For players the server only checks the cooldown: it sees their take-off and landing a moment late,
            // and the client already checked the ground and the spent air jump.
            case ESQUIVA, DESVIO -> slack > 0 || entity.onGround();
            case PULO_DUPLO -> slack > 0 || !data.airJumpUsed && !entity.onGround();
            case DASH -> true;
        };
    }

    // The speed a move gives. forward and strafe are the move input from -1 to 1 (strafe positive = left, like
    // vanilla); the dodge always goes back, with the strafe tilting it.
    public static Vec3 velocity(LivingEntity entity, Kind kind, float forward, float strafe) {
        MovementSpec spec = kind.spec();
        if (kind == Kind.ESQUIVA) {
            forward = -1;
        } else if (kind == Kind.DESVIO) {
            forward = 0;
            strafe = strafe >= 0 ? 1 : -1;
        } else if (kind == Kind.DASH && forward == 0 && strafe == 0) {
            forward = 1;
        }
        Vec3 dir = inputDirection(entity.getYRot(), forward, strafe);
        Vec3 current = entity.getDeltaMovement();
        double keep = Mth.clamp(spec.manterVelocidade(), 0, 1);
        return new Vec3(current.x * keep + dir.x * spec.impulsoHorizontal(), spec.impulsoVertical(), current.z * keep + dir.z * spec.impulsoHorizontal());
    }

    // World direction of a move input for an entity facing yRot (same math as vanilla's getInputVector).
    private static Vec3 inputDirection(float yRot, float forward, float strafe) {
        Vec3 input = new Vec3(strafe, 0, forward);
        if (input.lengthSqr() < 1.0E-4) {
            return Vec3.ZERO;
        }
        input = input.normalize();
        float sin = Mth.sin(yRot * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yRot * Mth.DEG_TO_RAD);
        return new Vec3(input.x * cos - input.z * sin, 0, input.z * cos + input.x * sin);
    }

    // Marks the move as used (cooldown, air jump). Both sides.
    public static void spend(LivingEntity entity, Kind kind) {
        MovementSpec spec = kind.spec();
        MovementData data = data(entity);
        long now = entity.level().getGameTime();
        data.readyAt[kind.ordinal()] = now + Math.max(0, spec.recargaTicks());
        data.lastMoveAt = now;
        if (kind == Kind.PULO_DUPLO) {
            data.airJumpUsed = true;
        }
    }

    // Server: a mob or boss tries a move. Returns false when it can't right now (cooldown, in the air, etc).
    public static boolean tryPerform(LivingEntity entity, Kind kind, float forward, float strafe) {
        if (!(entity.level() instanceof ServerLevel level) || Freeze.isFrozen(entity) || !allowed(entity, kind, 0)) {
            return false;
        }
        Vec3 velocity = velocity(entity, kind, forward, strafe);
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        spend(entity, kind);
        effects(level, entity, kind, velocity);
        return true;
    }

    // Shortcuts for the boss AI. backstep jumps straight back from where the entity faces; doubleJump only works in
    // the air (jump first) and also pushes forward. Both return false when the move isn't ready.
    public static boolean backstep(LivingEntity entity) {
        return tryPerform(entity, Kind.ESQUIVA, -1, 0);
    }

    public static boolean doubleJump(LivingEntity entity) {
        return tryPerform(entity, Kind.PULO_DUPLO, 1, 0);
    }

    // Server: a player's client started a move. The client already moved the player.
    public static void onPlayerMove(ServerPlayer player, int index, float forward, float strafe) {
        Kind kind = Kind.byIndex(index);
        if (kind == null || Freeze.isFrozen(player) || !allowed(player, kind, COOLDOWN_SLACK)) {
            return;
        }
        spend(player, kind);
        effects(player.serverLevel(), player, kind, velocity(player, kind, Mth.clamp(forward, -1, 1), Mth.clamp(strafe, -1, 1)));
    }

    // Server: double tap of the sprint key (Ctrl). Appears in front of the target, facing it.
    public static void teleport(ServerPlayer player) {
        TeleporteSpec spec = TeleporteSpec.get();
        MovementData data = data(player);
        long now = player.level().getGameTime();
        if (!CombatMode.in(player) || !free(player) || Freeze.isFrozen(player) || now + COOLDOWN_SLACK < data.teleportReadyAt) {
            return;
        }
        LivingEntity target = teleportTarget(player, spec);
        if (target == null) {
            return;
        }
        Vec3 spot = spotInFront(player, target, spec.distanciaFrente());
        if (spot == null) {
            return;
        }
        data.teleportReadyAt = now + spec.recargaTicks();
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position().add(0, 1, 0);
        Vec3 look = Combat.center(target).subtract(spot.add(0, player.getEyeHeight(), 0));
        float yRot = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float xRot = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        player.connection.teleport(spot.x, spot.y, spot.z, yRot, xRot);
        player.setYHeadRot(yRot);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        CombatMode.enter(player);
        Vec3 to = spot.add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "saida", 0, 0, onde -> from, List.of());
        SkillSounds.play(level, spec.sons(), "chegada", 0, 0, onde -> to, List.of());
        Fx.effects(level, "padrao.teleporte", false, "saida", from, look.normalize());
        Fx.effects(level, "padrao.teleporte", false, "chegada", to, look.normalize());
    }

    // Who the player aims at, else who they fought last.
    private static LivingEntity teleportTarget(ServerPlayer player, TeleporteSpec spec) {
        LivingEntity aimed = Combat.raycastTarget(player, spec.alcance());
        if (aimed != null) {
            return aimed;
        }
        PowerData power = player.getData(ModAttachments.POWER);
        if (power.foughtId >= 0 && player.level().getGameTime() - power.foughtAt <= spec.lembrarAlvoTicks()
                && player.level().getEntity(power.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, player)
                && fought.distanceTo(player) <= spec.alcance()) {
            return fought;
        }
        return null;
    }

    // Where to stand in front of target (where it faces), or to its sides or back when that is blocked.
    private static Vec3 spotInFront(ServerPlayer player, LivingEntity target, double distance) {
        Vec3 facing = Combat.flatLook(target);
        for (double angle : new double[] {0, 50, -50, 100, -100, 180}) {
            Vec3 dir = facing.yRot((float) Math.toRadians(angle));
            Vec3 spot = target.position().add(dir.scale(distance + target.getBbWidth() * 0.5));
            for (double up : new double[] {0, 1, -1}) {
                Vec3 feet = spot.add(0, up, 0);
                if (Combat.canStandAt(player, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static void effects(ServerLevel level, LivingEntity entity, Kind kind, Vec3 velocity) {
        MovementSpec spec = kind.spec();
        if (spec.invencivelTicks() > 0) {
            Intangible.hold(entity, spec.invencivelTicks());
        }
        if (spec.semDanoDeQueda()) {
            entity.resetFallDistance();
            entity.removeEffect(kind.effect());
            entity.addEffect(new MobEffectInstance(kind.effect(), SAFE_FALL_MAX_TICKS, 0, false, false, true));
        }
        puffs(level, entity, spec.poeira(), velocity);
        Vec3 feet = entity.position();
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where -> "pes".equals(where) ? feet : entity.position(), List.of());
    }

    // A ring of puffs at the feet, thrown against the push so they look kicked off the ground.
    private static void puffs(ServerLevel level, LivingEntity entity, MovementSpec.Poeira poeira, Vec3 velocity) {
        if (poeira == null || !poeira.ativa() || poeira.quantidade() <= 0) {
            return;
        }
        Vec3 feet = entity.position().add(0, 0.1, 0);
        Vec3 push = new Vec3(velocity.x, 0, velocity.z);
        Vec3 kick = push.lengthSqr() > 1.0E-4 ? push.normalize().scale(-poeira.velocidade() * 0.6) : Vec3.ZERO;
        int color = poeira.rgb();
        for (int i = 0; i < poeira.quantidade(); i++) {
            double angle = i * Math.PI * 2 / poeira.quantidade() + RANDOM.nextDouble() * 0.4;
            double speed = poeira.velocidade() * (0.6 + RANDOM.nextDouble() * 0.4);
            Vec3 out = new Vec3(Math.cos(angle) * speed, 0.01 + RANDOM.nextDouble() * 0.03, Math.sin(angle) * speed).add(kick);
            float size = poeira.tamanho() * (0.7f + RANDOM.nextFloat() * 0.6f);
            int life = Math.max(1, (int) (poeira.vidaTicks() * (0.7 + RANDOM.nextDouble() * 0.6)));
            Fx.moving(level, ModParticles.puff(color, size, life, 0.9f), feet, out);
        }
    }

    // Every tick, both sides: landing gives the air jump back. On the server it also ends the no-fall effect once
    // the entity stands still on the ground (fall distance back to 0) and the minimum time has passed.
    public static void tick(LivingEntity entity) {
        MovementData data = data(entity);
        long sinceMove = entity.level().getGameTime() - data.lastMoveAt;
        if (data.airJumpUsed && (entity.onGround() && sinceMove >= LANDING_DELAY || entity.isInWater() || entity.onClimbable())) {
            data.airJumpUsed = false;
        }
        if (!entity.level().isClientSide() && sinceMove >= SAFE_FALL_MIN_TICKS && entity.onGround() && entity.fallDistance == 0) {
            for (Kind kind : Kind.values()) {
                if (entity.hasEffect(kind.effect())) {
                    entity.removeEffect(kind.effect());
                }
            }
        }
    }

    // LivingFallEvent: a landing under the no-fall effect deals no damage and ends the effect.
    public static boolean ignoresFall(LivingEntity entity) {
        boolean safe = false;
        for (Kind kind : Kind.values()) {
            if (entity.hasEffect(kind.effect())) {
                safe = true;
                if (!entity.level().isClientSide()) {
                    entity.removeEffect(kind.effect());
                }
            }
        }
        return safe;
    }
}
