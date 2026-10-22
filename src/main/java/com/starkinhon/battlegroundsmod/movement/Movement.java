package com.starkinhon.battlegroundsmod.movement;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.skill.MovementSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// The standard moves every fighter has, separate from the powers: the back dodge (back + jump on the ground) and the
// air jump (jump again in the air). Values come from souls_skills/padrao.esquiva.json and padrao.pulo_duplo.json.
//
// Players: the client starts the move itself so it feels instant (client/MovementInput) and tells the server, which
// checks it and adds the rest (dodge window, fall protection, puffs, sounds).
// Mobs and bosses: call tryPerform on the server and tick every tick; it pushes the mob too.
public final class Movement {
    // Grace for the cooldown check on the server: the client's clock can be a tick or two ahead.
    private static final int COOLDOWN_SLACK = 2;
    // A move's take-off tick still reads as "on the ground", so landing only counts after this.
    private static final int LANDING_DELAY = 3;
    private static final RandomSource RANDOM = RandomSource.create();

    public enum Kind {
        ESQUIVA("padrao.esquiva"),
        PULO_DUPLO("padrao.pulo_duplo");

        public final String skill;

        Kind(String skill) {
            this.skill = skill;
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
                || entity.isInWater() || entity.isInLava() || entity.isSleeping()) {
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
        return switch (kind) {
            // The server can see the client's take-off a moment late, so it doesn't insist on the ground.
            case ESQUIVA -> slack > 0 || entity.onGround();
            case PULO_DUPLO -> !data.airJumpUsed && (slack > 0 || !entity.onGround());
        };
    }

    // The speed a move gives. forward and strafe are the move input from -1 to 1 (strafe positive = left, like
    // vanilla); the dodge always goes back, with the strafe tilting it.
    public static Vec3 velocity(LivingEntity entity, Kind kind, float forward, float strafe) {
        MovementSpec spec = kind.spec();
        if (kind == Kind.ESQUIVA) {
            forward = -1;
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

    private static void effects(ServerLevel level, LivingEntity entity, Kind kind, Vec3 velocity) {
        MovementSpec spec = kind.spec();
        MovementData data = data(entity);
        if (spec.invencivelTicks() > 0) {
            Intangible.hold(entity, spec.invencivelTicks());
        }
        if (spec.semDanoDeQueda()) {
            data.safeFall = true;
            entity.resetFallDistance();
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

    // Every tick, both sides: landing gives the air jump back and ends the fall protection.
    public static void tick(LivingEntity entity) {
        MovementData data = data(entity);
        if (!data.airJumpUsed && !data.safeFall) {
            return;
        }
        boolean landed = entity.onGround() && entity.level().getGameTime() - data.lastMoveAt >= LANDING_DELAY;
        if (landed || entity.isInWater() || entity.onClimbable()) {
            data.airJumpUsed = false;
            data.safeFall = false;
        }
    }

    // Fall damage is skipped while protected. Called from LivingFallEvent before tick clears the flag.
    public static boolean ignoresFall(LivingEntity entity) {
        return entity.hasData(ModAttachments.MOVEMENT) && data(entity).safeFall;
    }
}
