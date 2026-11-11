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
import com.starkinhon.battlegroundsmod.fx.Smoke;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.MovementSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;
import com.starkinhon.battlegroundsmod.skill.TeleporteSpec;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
                || entity.isInWater() || entity.isInLava() || entity.isSleeping() || Stun.is(entity)
                || entity.hasEffect(com.starkinhon.battlegroundsmod.ModEffects.ENRAIZADO)) {
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
        if (kind == Kind.DASH || kind == Kind.DESVIO) {
            com.starkinhon.battlegroundsmod.combat.Stamina.gain(player,
                    com.starkinhon.battlegroundsmod.skill.StaminaSpec.get().ganhoDashPct());
        }
    }

    // Server: double tap of the sprint key (Ctrl). Appears in front of the target, facing it, and starts combat mode.
    public static void teleport(ServerPlayer player) {
        TeleporteSpec spec = TeleporteSpec.get();
        MovementData data = data(player);
        long now = player.level().getGameTime();
        if (!free(player) || Freeze.isFrozen(player)) {
            return;
        }
        // Says on the action bar why it didn't go, so a miss doesn't look like a broken key.
        if (now + COOLDOWN_SLACK < data.teleportReadyAt) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.teleport_cooldown"), true);
            return;
        }
        LivingEntity target = teleportTarget(player, spec);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.teleport_no_target", (int) spec.alcance()), true);
            return;
        }
        if (com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.apart(player, target) || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.apart(player, target)) {
            // A Domain Expansion is a space of its own: no way in or out by teleport.
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.teleport_domain"), true);
            return;
        }
        Vec3 spot = spotInFront(player, target, spec.distanciaFrente());
        if (spot == null) {
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.teleport_blocked"), true);
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
        puffs(level, entity, kind, spec.poeira(), velocity);
        Vec3 feet = entity.position();
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where -> "pes".equals(where) ? feet : entity.position(), List.of());
    }

    // Dust kicked off the ground against the push (procedural smoke, in the color of the block underneath), or a
    // ring of wind under the feet for a jump in the air.
    private static void puffs(ServerLevel level, LivingEntity entity, Kind kind, MovementSpec.Poeira poeira, Vec3 velocity) {
        if (poeira == null || !poeira.ativa() || poeira.quantidade() <= 0) {
            return;
        }
        Vec3 feet = entity.position();
        Vec3 push = new Vec3(velocity.x, 0, velocity.z);
        // a bigger body (the Giant Luffy, SCALE 10) kicks up dust as big as he is
        double body = Math.max(1, entity.getScale());
        double sizeScale = poeira.tamanho() / 0.7 * body;
        if (kind == Kind.PULO_DUPLO || push.lengthSqr() < 1.0E-4) {
            Smoke.airJump(level, feet.add(0, 0.05, 0), poeira.rgb(), poeira.tamanho() / 0.8 * body);
            return;
        }
        Smoke.kick(level, feet, push.normalize(), Smoke.groundColor(level, feet), sizeScale, poeira.vidaTicks() / 14.0 * Math.sqrt(body),
                poeira.velocidade() / 0.22 * body, (int) Math.round(poeira.quantidade() * Math.min(3, body)));
        MovementData data = data(entity);
        data.trailDir = push.normalize();
    }

    // Every tick, both sides: landing gives the air jump back. On the server it also ends the no-fall effect once
    // the entity stands still on the ground (fall distance back to 0) and the minimum time has passed.
    public static void tick(LivingEntity entity) {
        MovementData data = data(entity);
        long sinceMove = entity.level().getGameTime() - data.lastMoveAt;
        if (data.airJumpUsed && (entity.onGround() && sinceMove >= LANDING_DELAY || entity.isInWater() || entity.onClimbable())) {
            data.airJumpUsed = false;
        }
        // The first ticks of a dash on the ground: dust trail at the feet and wind along the body.
        if (data.trailDir != null && entity.level() instanceof ServerLevel level) {
            if (sinceMove >= 1 && sinceMove <= 6) {
                Smoke.dashTrail(level, entity.position(), data.trailDir, Smoke.groundColor(level, entity.position()), entity.onGround() ? (int) sinceMove : 99,
                        Math.max(1, entity.getScale()));
            } else if (sinceMove > 6) {
                data.trailDir = null;
            }
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
