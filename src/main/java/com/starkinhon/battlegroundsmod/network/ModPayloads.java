package com.starkinhon.battlegroundsmod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.client.ClientHooks;
import com.starkinhon.battlegroundsmod.combat.CombatMode;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {

    private ModPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }

    // Client -> server: the player pressed an ability key.
    public record UseAbility(int slot) implements CustomPacketPayload {
        public static final Type<UseAbility> TYPE = payloadType("use_ability");
        public static final StreamCodec<ByteBuf, UseAbility> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UseAbility::slot, UseAbility::new);

        @Override
        public Type<UseAbility> type() {
            return TYPE;
        }
    }

    // Client -> server: the player started a standard move (Movement.Kind index) with this move input.
    public record UseMovement(int kind, float forward, float strafe) implements CustomPacketPayload {
        public static final Type<UseMovement> TYPE = payloadType("use_movement");
        public static final StreamCodec<ByteBuf, UseMovement> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UseMovement::kind,
                ByteBufCodecs.FLOAT, UseMovement::forward,
                ByteBufCodecs.FLOAT, UseMovement::strafe,
                UseMovement::new);

        @Override
        public Type<UseMovement> type() {
            return TYPE;
        }
    }

    // Client -> server: the next punch of the combo, even into the air. key: the "Ataque rápido" of the base attack
    // selector (works with anything in hand and starts combat mode); otherwise a left click with an empty hand in combat.
    public record Punch(boolean key) implements CustomPacketPayload {
        public static final Type<Punch> TYPE = payloadType("punch");
        public static final StreamCodec<ByteBuf, Punch> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Punch::key, Punch::new);

        @Override
        public Type<Punch> type() {
            return TYPE;
        }
    }

    // Client -> server: the defense key went down or up.
    public record Guard(boolean down) implements CustomPacketPayload {
        public static final Type<Guard> TYPE = payloadType("guard");
        public static final StreamCodec<ByteBuf, Guard> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Guard::down, Guard::new);

        @Override
        public Type<Guard> type() {
            return TYPE;
        }
    }

    // Client -> server: the key that uses the picked base attack went down or up (a held Forte charges, a held
    // Barragem lasts longer).
    public record AttackHold(boolean down) implements CustomPacketPayload {
        public static final Type<AttackHold> TYPE = payloadType("attack_hold");
        public static final StreamCodec<ByteBuf, AttackHold> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AttackHold::down, AttackHold::new);

        @Override
        public Type<AttackHold> type() {
            return TYPE;
        }
    }

    // Client -> server: a skill key went down or up (the giant Katon is thrown when it comes up).
    public record AbilityHold(boolean down) implements CustomPacketPayload {
        public static final Type<AbilityHold> TYPE = payloadType("ability_hold");
        public static final StreamCodec<ByteBuf, AbilityHold> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AbilityHold::down, AbilityHold::new);

        @Override
        public Type<AbilityHold> type() {
            return TYPE;
        }
    }

    // Server -> client: the giant fireball. mode 0: growing over the caster's head, from radius to maxRadius over
    // ticks; 1: flying from pos at velocity per tick for ticks; 2: gone (it blew up).
    public record KatonBall(int id, int casterId, int mode, Vec3 pos, Vec3 velocity, float radius, float maxRadius, int ticks)
            implements CustomPacketPayload {
        public static final Type<KatonBall> TYPE = payloadType("katon_ball");
        public static final StreamCodec<ByteBuf, KatonBall> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public KatonBall decode(ByteBuf buf) {
                return new KatonBall(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        VEC3.decode(buf), VEC3.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, KatonBall b) {
                ByteBufCodecs.VAR_INT.encode(buf, b.id());
                ByteBufCodecs.VAR_INT.encode(buf, b.casterId());
                ByteBufCodecs.VAR_INT.encode(buf, b.mode());
                VEC3.encode(buf, b.pos());
                VEC3.encode(buf, b.velocity());
                ByteBufCodecs.FLOAT.encode(buf, b.radius());
                ByteBufCodecs.FLOAT.encode(buf, b.maxRadius());
                ByteBufCodecs.VAR_INT.encode(buf, b.ticks());
            }
        };

        @Override
        public Type<KatonBall> type() {
            return TYPE;
        }
    }

    // Server -> client: the Izanami turned time back. Every effect still on screen is wiped (it never happened).
    public record Rewind() implements CustomPacketPayload {
        public static final Type<Rewind> TYPE = payloadType("rewind");
        public static final StreamCodec<ByteBuf, Rewind> STREAM_CODEC = StreamCodec.unit(new Rewind());

        @Override
        public Type<Rewind> type() {
            return TYPE;
        }
    }

    // Client -> server: double tap of the sprint key (Ctrl): appear in front of whoever the player fights or aims at.
    public record Teleport() implements CustomPacketPayload {
        public static final Type<Teleport> TYPE = payloadType("teleport");
        public static final StreamCodec<ByteBuf, Teleport> STREAM_CODEC = StreamCodec.unit(new Teleport());

        @Override
        public Type<Teleport> type() {
            return TYPE;
        }
    }

    // Server -> client: entityId is guarding (arms up) or stopped.
    public record GuardPose(int entityId, boolean on) implements CustomPacketPayload {
        public static final Type<GuardPose> TYPE = payloadType("guard_pose");
        public static final StreamCodec<ByteBuf, GuardPose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GuardPose::entityId,
                ByteBufCodecs.BOOL, GuardPose::on,
                GuardPose::new);

        @Override
        public Type<GuardPose> type() {
            return TYPE;
        }
    }

    // Client -> server: the player picked a power in the book.
    public record SelectPower(String power) implements CustomPacketPayload {
        public static final Type<SelectPower> TYPE = payloadType("select_power");
        public static final StreamCodec<ByteBuf, SelectPower> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SelectPower::power, SelectPower::new);

        @Override
        public Type<SelectPower> type() {
            return TYPE;
        }
    }

    // Server -> client: power state for the HUD.
    public record SyncPower(String power, float ult, List<Integer> cooldowns, boolean combat, int tier) implements CustomPacketPayload {
        public static final Type<SyncPower> TYPE = payloadType("sync_power");
        public static final StreamCodec<ByteBuf, SyncPower> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SyncPower::power,
                ByteBufCodecs.FLOAT, SyncPower::ult,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SyncPower::cooldowns,
                ByteBufCodecs.BOOL, SyncPower::combat,
                ByteBufCodecs.VAR_INT, SyncPower::tier,
                SyncPower::new);

        public static SyncPower of(PowerData data) {
            List<Integer> cooldowns = new ArrayList<>();
            for (int cooldown : data.cooldowns) {
                cooldowns.add(cooldown);
            }
            return new SyncPower(data.power, data.ult, cooldowns, data.combat, data.tier);
        }

        void applyTo(Player player) {
            PowerData data = player.getData(ModAttachments.POWER);
            data.power = power;
            data.ult = ult;
            data.combat = combat;
            data.tier = tier;
            for (int i = 0; i < data.cooldowns.length && i < cooldowns.size(); i++) {
                data.cooldowns[i] = cooldowns.get(i);
            }
        }

        @Override
        public Type<SyncPower> type() {
            return TYPE;
        }
    }

    // Server -> client: the player's attack stamina, its max and the exhaustion ticks left (combat/Stamina).
    public record SyncStamina(float stamina, float max, int exhaustedTicks) implements CustomPacketPayload {
        public static final Type<SyncStamina> TYPE = payloadType("sync_stamina");
        public static final StreamCodec<ByteBuf, SyncStamina> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, SyncStamina::stamina,
                ByteBufCodecs.FLOAT, SyncStamina::max,
                ByteBufCodecs.VAR_INT, SyncStamina::exhaustedTicks,
                SyncStamina::new);

        void applyTo(Player player) {
            PowerData data = player.getData(ModAttachments.POWER);
            data.stamina = stamina;
            data.staminaMax = max;
            data.exhaustedTicks = exhaustedTicks;
        }

        @Override
        public Type<SyncStamina> type() {
            return TYPE;
        }
    }

    // Server -> client: how far the attack the player holds has charged, 0..1, or -1 when nothing charges (combat/Charge).
    public record SyncCharge(float progress) implements CustomPacketPayload {
        public static final Type<SyncCharge> TYPE = payloadType("sync_charge");
        public static final StreamCodec<ByteBuf, SyncCharge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, SyncCharge::progress, SyncCharge::new);

        void applyTo(Player player) {
            player.getData(ModAttachments.POWER).chargeSent = progress;
        }

        @Override
        public Type<SyncCharge> type() {
            return TYPE;
        }
    }

    // Server -> client: a player started a skill with a body animation (timing comes from the skill's spec).
    public record PlaySkillAnimation(int entityId, String skill, boolean awakened) implements CustomPacketPayload {
        public static final Type<PlaySkillAnimation> TYPE = payloadType("play_skill_animation");
        // Sent as the skill when a held skill is let go: an animation waiting at its "soltar" mark plays the rest (the
        // throw); anything else just stops, like "".
        public static final String RELEASE = ">";
        public static final StreamCodec<ByteBuf, PlaySkillAnimation> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlaySkillAnimation::entityId,
                ByteBufCodecs.STRING_UTF8, PlaySkillAnimation::skill,
                ByteBufCodecs.BOOL, PlaySkillAnimation::awakened,
                PlaySkillAnimation::new);

        @Override
        public Type<PlaySkillAnimation> type() {
            return TYPE;
        }
    }

    // Server -> client: entityId threw hit number step of the punch combo (its pose is in padrao.combo.json).
    public record ComboAnimation(int entityId, int step) implements CustomPacketPayload {
        public static final Type<ComboAnimation> TYPE = payloadType("combo_animation");
        public static final StreamCodec<ByteBuf, ComboAnimation> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ComboAnimation::entityId,
                ByteBufCodecs.VAR_INT, ComboAnimation::step,
                ComboAnimation::new);

        @Override
        public Type<ComboAnimation> type() {
            return TYPE;
        }
    }

    // Server -> client: entityId's punch connected; its animation stands still for ticks (hitstop).
    public record Hitstop(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<Hitstop> TYPE = payloadType("hitstop");
        public static final StreamCodec<ByteBuf, Hitstop> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hitstop::entityId,
                ByteBufCodecs.VAR_INT, Hitstop::ticks,
                Hitstop::new);

        @Override
        public Type<Hitstop> type() {
            return TYPE;
        }
    }

    // Server -> client: point this player's camera at someone's face for a moment. The timing is the "cutscene"
    // block of the skill's spec, which the client reads itself.
    public record FaceCutscene(int entityId, String skill, boolean awakened) implements CustomPacketPayload {
        public static final Type<FaceCutscene> TYPE = payloadType("face_cutscene");
        public static final StreamCodec<ByteBuf, FaceCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FaceCutscene::entityId,
                ByteBufCodecs.STRING_UTF8, FaceCutscene::skill,
                ByteBufCodecs.BOOL, FaceCutscene::awakened,
                FaceCutscene::new);

        @Override
        public Type<FaceCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: shake this player's camera (big impacts).
    public record ScreenShake(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<ScreenShake> TYPE = payloadType("screen_shake");
        public static final StreamCodec<ByteBuf, ScreenShake> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, ScreenShake::strength,
                ByteBufCodecs.VAR_INT, ScreenShake::ticks,
                ScreenShake::new);

        @Override
        public Type<ScreenShake> type() {
            return TYPE;
        }
    }

    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    // Server -> client: a meteor's body falls from start to end over fallTicks, starting after delay ticks. Its size,
    // speed curve and colors are read from the skill's spec on the client.
    public record MeteorFall(String skill, boolean awakened, Vec3 start, Vec3 end, int delay, int fallTicks) implements CustomPacketPayload {
        public static final Type<MeteorFall> TYPE = payloadType("meteor_fall");
        public static final StreamCodec<ByteBuf, MeteorFall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MeteorFall::skill,
                ByteBufCodecs.BOOL, MeteorFall::awakened,
                VEC3, MeteorFall::start,
                VEC3, MeteorFall::end,
                ByteBufCodecs.VAR_INT, MeteorFall::delay,
                ByteBufCodecs.VAR_INT, MeteorFall::fallTicks,
                MeteorFall::new);

        @Override
        public Type<MeteorFall> type() {
            return TYPE;
        }
    }

    // Server -> client: moment momento of a skill happened at pos facing dir; the client plays the matching entries
    // of the skill's "vfx" list (client/fx/SkillEffects). entityId (-1 for none) is who the effects can follow, and
    // ticks how long this moment lasts (0 when it has no length).
    // owner (-1 for none) is whose move it belongs to: when that move is cut off (SkillStop) the client takes its effects away.
    public record PlayEffects(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entityId, int ticks, int owner)
            implements CustomPacketPayload {
        public static final Type<PlayEffects> TYPE = payloadType("play_effects");
        public static final StreamCodec<ByteBuf, PlayEffects> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.skill());
            ByteBufCodecs.BOOL.encode(buf, p.awakened());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.momento());
            VEC3.encode(buf, p.pos());
            VEC3.encode(buf, p.dir());
            ByteBufCodecs.VAR_INT.encode(buf, p.entityId());
            ByteBufCodecs.VAR_INT.encode(buf, p.ticks());
            ByteBufCodecs.VAR_INT.encode(buf, p.owner());
        }, buf -> new PlayEffects(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                VEC3.decode(buf), VEC3.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public Type<PlayEffects> type() {
            return TYPE;
        }
    }

    // Server -> client: a sound of owner's move (combat/SkillStop). Played by the client itself so it can be cut off the
    // moment the move is (StopOwnerFx). screen: only for this player's ears, at their own position (no distance).
    public record OwnedSound(int owner, ResourceLocation sound, Vec3 pos, float volume, float pitch, boolean screen, long seed)
            implements CustomPacketPayload {
        public static final Type<OwnedSound> TYPE = payloadType("owned_sound");
        public static final StreamCodec<ByteBuf, OwnedSound> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ByteBufCodecs.VAR_INT.encode(buf, p.owner());
            ResourceLocation.STREAM_CODEC.encode(buf, p.sound());
            VEC3.encode(buf, p.pos());
            ByteBufCodecs.FLOAT.encode(buf, p.volume());
            ByteBufCodecs.FLOAT.encode(buf, p.pitch());
            ByteBufCodecs.BOOL.encode(buf, p.screen());
            ByteBufCodecs.VAR_LONG.encode(buf, p.seed());
        }, buf -> new OwnedSound(ByteBufCodecs.VAR_INT.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf), VEC3.decode(buf),
                ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)));

        @Override
        public Type<OwnedSound> type() {
            return TYPE;
        }
    }

    // Server -> client: owner's move was cut off (stunned, sealed, died, the Domain shattered): its sounds stop now and its
    // effects (the skill file's vfx, held orbs) fade out at once.
    public record StopOwnerFx(int owner) implements CustomPacketPayload {
        public static final Type<StopOwnerFx> TYPE = payloadType("stop_owner_fx");
        public static final StreamCodec<ByteBuf, StopOwnerFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StopOwnerFx::owner,
                StopOwnerFx::new);

        @Override
        public Type<StopOwnerFx> type() {
            return TYPE;
        }
    }

    // Server -> client: a short over-the-shoulder shot of casterId looking at targetId (the Susanoo counter).
    public record ShoulderCutscene(int casterId, int targetId, int ticks) implements CustomPacketPayload {
        public static final Type<ShoulderCutscene> TYPE = payloadType("shoulder_cutscene");
        public static final StreamCodec<ByteBuf, ShoulderCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShoulderCutscene::casterId,
                ByteBufCodecs.VAR_INT, ShoulderCutscene::targetId,
                ByteBufCodecs.VAR_INT, ShoulderCutscene::ticks,
                ShoulderCutscene::new);

        @Override
        public Type<ShoulderCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: the Susanoo counter's arm (client/fx/SusanooArm). phase: 0 guard with the shield up, 1 grab
    // (target, a = ticks to reach, b = ticks of crushing), 2 a squeeze, 3 the throw, 4 gone (window closed, cut off).
    public record SusanooArm(int casterId, int targetId, int phase, int a, int b) implements CustomPacketPayload {
        public static final Type<SusanooArm> TYPE = payloadType("susanoo_arm");
        public static final StreamCodec<ByteBuf, SusanooArm> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SusanooArm::casterId,
                ByteBufCodecs.VAR_INT, SusanooArm::targetId,
                ByteBufCodecs.VAR_INT, SusanooArm::phase,
                ByteBufCodecs.VAR_INT, SusanooArm::a,
                ByteBufCodecs.VAR_INT, SusanooArm::b,
                SusanooArm::new);

        @Override
        public Type<SusanooArm> type() {
            return TYPE;
        }
    }

    // Server -> client: the neck grab's cutscene for the two fighters. The camera cuts in now; the slam lands at impactTick.
    public record GrabCutscene(int casterId, int targetId, boolean awakened, int impactTick) implements CustomPacketPayload {
        public static final Type<GrabCutscene> TYPE = payloadType("grab_cutscene");
        public static final StreamCodec<ByteBuf, GrabCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GrabCutscene::casterId,
                ByteBufCodecs.VAR_INT, GrabCutscene::targetId,
                ByteBufCodecs.BOOL, GrabCutscene::awakened,
                ByteBufCodecs.VAR_INT, GrabCutscene::impactTick,
                GrabCutscene::new);

        @Override
        public Type<GrabCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: the Destruction Bop's cutscene for the two fighters; endTick is when the camera starts going back.
    public record BopCutscene(int casterId, int targetId, boolean awakened, int endTick) implements CustomPacketPayload {
        public static final Type<BopCutscene> TYPE = payloadType("bop_cutscene");
        public static final StreamCodec<ByteBuf, BopCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BopCutscene::casterId,
                ByteBufCodecs.VAR_INT, BopCutscene::targetId,
                ByteBufCodecs.BOOL, BopCutscene::awakened,
                ByteBufCodecs.VAR_INT, BopCutscene::endTick,
                BopCutscene::new);

        @Override
        public Type<BopCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: don't draw this entity for ticks (an invisible dash); 0 shows it again.
    public record HideEntity(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<HideEntity> TYPE = payloadType("hide_entity");
        public static final StreamCodec<ByteBuf, HideEntity> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HideEntity::entityId,
                ByteBufCodecs.VAR_INT, HideEntity::ticks,
                HideEntity::new);

        @Override
        public Type<HideEntity> type() {
            return TYPE;
        }
    }

    // Server -> client: this entity was killed by fire (Katon, Meteoro): it turns to ash instead of the normal death.
    public record AshDeath(int entityId) implements CustomPacketPayload {
        public static final Type<AshDeath> TYPE = payloadType("ash_death");
        public static final StreamCodec<ByteBuf, AshDeath> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AshDeath::entityId, AshDeath::new);

        @Override
        public Type<AshDeath> type() {
            return TYPE;
        }
    }

    // Server -> client: Bills' Hakai cutscene for him and his target (the shots and timing come from bills.hakai.json).
    public record HakaiCutscene(int casterId, int targetId, boolean awakened) implements CustomPacketPayload {
        public static final Type<HakaiCutscene> TYPE = payloadType("hakai_cutscene");
        public static final StreamCodec<ByteBuf, HakaiCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HakaiCutscene::casterId,
                ByteBufCodecs.VAR_INT, HakaiCutscene::targetId,
                ByteBufCodecs.BOOL, HakaiCutscene::awakened,
                HakaiCutscene::new);

        @Override
        public Type<HakaiCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: this entity is being erased by the Hakai: it lights up and falls apart into purple sparks.
    public record HakaiDeath(int entityId, boolean awakened) implements CustomPacketPayload {
        public static final Type<HakaiDeath> TYPE = payloadType("hakai_death");
        public static final StreamCodec<ByteBuf, HakaiDeath> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HakaiDeath::entityId,
                ByteBufCodecs.BOOL, HakaiDeath::awakened,
                HakaiDeath::new);

        @Override
        public Type<HakaiDeath> type() {
            return TYPE;
        }
    }

    // Server -> client: Bills' trip to outer space with his target (Esfera da Destruição Planetária, Shift + C), for the
    // two of them; timing from bills.esfera.json.
    public record EsferaCutscene(int casterId, int targetId, boolean awakened) implements CustomPacketPayload {
        public static final Type<EsferaCutscene> TYPE = payloadType("esfera_cutscene");
        public static final StreamCodec<ByteBuf, EsferaCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EsferaCutscene::casterId,
                ByteBufCodecs.VAR_INT, EsferaCutscene::targetId,
                ByteBufCodecs.BOOL, EsferaCutscene::awakened,
                EsferaCutscene::new);

        @Override
        public Type<EsferaCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: the sphere of destruction comes down from the sky on top of the target (Esfera da Destruição
    // Planetária, client/fx/EsferaRenderer.ride).
    public record EsferaFall(int targetId, Vec3 start, Vec3 lastHead, boolean awakened) implements CustomPacketPayload {
        public static final Type<EsferaFall> TYPE = payloadType("esfera_fall");
        public static final StreamCodec<ByteBuf, EsferaFall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EsferaFall::targetId,
                VEC3, EsferaFall::start,
                VEC3, EsferaFall::lastHead,
                ByteBufCodecs.BOOL, EsferaFall::awakened,
                EsferaFall::new);

        @Override
        public Type<EsferaFall> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's domain (Vazio Infinito). mode 0: it started around center; 1: it is cracking open early
    // because he fell; 2: other (an entity id) was killed inside it, the screen goes to red and white ink.
    // See client/fx/DomainScene.
    public record GojoDomain(int casterId, Vec3 center, boolean awakened, int mode, int other) implements CustomPacketPayload {
        public static final Type<GojoDomain> TYPE = payloadType("gojo_domain");
        public static final StreamCodec<ByteBuf, GojoDomain> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GojoDomain::casterId,
                VEC3, GojoDomain::center,
                ByteBufCodecs.BOOL, GojoDomain::awakened,
                ByteBufCodecs.VAR_INT, GojoDomain::mode,
                ByteBufCodecs.VAR_INT, GojoDomain::other,
                GojoDomain::new);

        @Override
        public Type<GojoDomain> type() {
            return TYPE;
        }
    }

    // Server -> client: an attack in flight was erased (Bills' hakaizinho): stop drawing what skill had around pos.
    public record EraseAttack(String skill, Vec3 pos, float radius) implements CustomPacketPayload {
        public static final Type<EraseAttack> TYPE = payloadType("erase_attack");
        public static final StreamCodec<ByteBuf, EraseAttack> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, EraseAttack::skill,
                VEC3, EraseAttack::pos,
                ByteBufCodecs.FLOAT, EraseAttack::radius,
                EraseAttack::new);

        @Override
        public Type<EraseAttack> type() {
            return TYPE;
        }
    }

    // Server -> client: Bills' hakai ball, every tick while it lives. mode 0 chasing, 1 with targetId trapped inside, 2
    // gone (it blew up, or the chase ran out).
    public record HakaiBall(int id, int mode, Vec3 pos, int targetId, float radius, boolean awakened) implements CustomPacketPayload {
        public static final Type<HakaiBall> TYPE = payloadType("hakai_ball");
        public static final StreamCodec<ByteBuf, HakaiBall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HakaiBall::id,
                ByteBufCodecs.VAR_INT, HakaiBall::mode,
                VEC3, HakaiBall::pos,
                ByteBufCodecs.VAR_INT, HakaiBall::targetId,
                ByteBufCodecs.FLOAT, HakaiBall::radius,
                ByteBufCodecs.BOOL, HakaiBall::awakened,
                HakaiBall::new);

        @Override
        public Type<HakaiBall> type() {
            return TYPE;
        }
    }

    // Server -> client: a ball of Bills' Destruction Energy (power/powers/bills/DestructionEnergy), sent every tick it
    // exists. mode 0 = there at pos, 2 = gone. grow: 0 to 1 while it forms (the rings open one after another); small: one
    // of the many balls of the Shift + Z, drawn lighter.
    // Server -> client: the look of a hand-to-hand move (combat/PunchFx; kinds there, sent as kind | rgb << 4), drawn by
    // client/fx/PunchFxRenderer. entityId: who it follows (-1: stays at pos).
    public record PunchFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) implements CustomPacketPayload {
        public static final Type<PunchFx> TYPE = payloadType("punch_fx");
        public static final StreamCodec<ByteBuf, PunchFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PunchFx::kind,
                ByteBufCodecs.VAR_INT, PunchFx::entityId,
                VEC3, PunchFx::pos,
                VEC3, PunchFx::dir,
                ByteBufCodecs.FLOAT, PunchFx::a,
                ByteBufCodecs.FLOAT, PunchFx::b,
                PunchFx::new);

        @Override
        public Type<PunchFx> type() {
            return TYPE;
        }
    }

    // Server -> client: one of the sword moves' effects (client/fx/SwordFx; kinds there, sent as kind | rgb << 4).
    public record SwordFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) implements CustomPacketPayload {
        public static final Type<SwordFx> TYPE = payloadType("sword_fx");
        public static final StreamCodec<ByteBuf, SwordFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SwordFx::kind,
                ByteBufCodecs.VAR_INT, SwordFx::entityId,
                VEC3, SwordFx::pos,
                VEC3, SwordFx::dir,
                ByteBufCodecs.FLOAT, SwordFx::a,
                ByteBufCodecs.FLOAT, SwordFx::b,
                SwordFx::new);

        @Override
        public Type<SwordFx> type() {
            return TYPE;
        }
    }

    // Server -> client: one of Aizen's other effects (client/fx/AizenFx; kinds there): the Reiatsu around entityId, the
    // Hogyoku evolution on entityId, a cut in space at pos along dir, or Kyoka Suigetsu's clone cracking and breaking.
    public record AizenFx(int kind, int entityId, Vec3 pos, Vec3 dir, float a, float b) implements CustomPacketPayload {
        public static final Type<AizenFx> TYPE = payloadType("aizen_fx");
        public static final StreamCodec<ByteBuf, AizenFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AizenFx::kind,
                ByteBufCodecs.VAR_INT, AizenFx::entityId,
                VEC3, AizenFx::pos,
                VEC3, AizenFx::dir,
                ByteBufCodecs.FLOAT, AizenFx::a,
                ByteBufCodecs.FLOAT, AizenFx::b,
                AizenFx::new);

        @Override
        public Type<AizenFx> type() {
            return TYPE;
        }
    }

    // Server -> client: one effect of the SubAlmas (power/powers/sub/SubNet has the kinds; client/fx/SubFx draws them).
    // id: the caster, the target or a projectile, depending on the kind; a, b, c: the kind's own numbers.
    public record SubFx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) implements CustomPacketPayload {
        public static final Type<SubFx> TYPE = payloadType("sub_fx");
        // Seven fields: one more than StreamCodec.composite takes, so written out by hand.
        public static final StreamCodec<ByteBuf, SubFx> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            ByteBufCodecs.VAR_INT.encode(buf, p.kind());
            ByteBufCodecs.VAR_INT.encode(buf, p.id());
            VEC3.encode(buf, p.pos());
            VEC3.encode(buf, p.dir());
            buf.writeFloat(p.a());
            buf.writeFloat(p.b());
            buf.writeFloat(p.c());
        }, buf -> new SubFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), VEC3.decode(buf), VEC3.decode(buf),
                buf.readFloat(), buf.readFloat(), buf.readFloat()));

        @Override
        public Type<SubFx> type() {
            return TYPE;
        }
    }

    // Server -> client: Aizen's Black Box (power/powers/aizen/BlackBox). mode 0 held (every tick), 1 shut (once), 2 gone.
    // base: the bottom middle of the box; size: x = half its width, y = its height, z = the charge (0 to 1).
    public record BlackBox(int id, int mode, Vec3 base, Vec3 size, boolean awakened) implements CustomPacketPayload {
        public static final Type<BlackBox> TYPE = payloadType("black_box");
        public static final StreamCodec<ByteBuf, BlackBox> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BlackBox::id,
                ByteBufCodecs.VAR_INT, BlackBox::mode,
                VEC3, BlackBox::base,
                VEC3, BlackBox::size,
                ByteBufCodecs.BOOL, BlackBox::awakened,
                BlackBox::new);

        @Override
        public Type<BlackBox> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's Blue (power/powers/gojo/Azul), every tick while it lives. mode 0 alive, 1 imploded (the
    // held one's end), 2 gone (the tap's end, or cut short). charge 0 to 1 (0 the tap); style: the shader's look (2 realism, 3 mix).
    public record Azul(int id, int mode, Vec3 pos, float radius, float charge, int style) implements CustomPacketPayload {
        public static final Type<Azul> TYPE = payloadType("azul");
        public static final StreamCodec<ByteBuf, Azul> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Azul::id,
                ByteBufCodecs.VAR_INT, Azul::mode,
                VEC3, Azul::pos,
                ByteBufCodecs.FLOAT, Azul::radius,
                ByteBufCodecs.FLOAT, Azul::charge,
                ByteBufCodecs.VAR_INT, Azul::style,
                Azul::new);

        @Override
        public Type<Azul> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's Red (power/powers/gojo/Vermelho), every tick while it sits at owner's fingertip.
    // mode 0 alive, 1 fired (it bursts into the blast), 2 gone (cut short, it fades). charge 0 to 1. The client places it
    // on the owner's hand itself, so it never lags behind him.
    // flip: he hangs upside down (Shift + X).
    public record Vermelho(int id, int owner, int mode, float charge, boolean flip) implements CustomPacketPayload {
        public static final Type<Vermelho> TYPE = payloadType("vermelho");
        public static final StreamCodec<ByteBuf, Vermelho> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Vermelho::id,
                ByteBufCodecs.VAR_INT, Vermelho::owner,
                ByteBufCodecs.VAR_INT, Vermelho::mode,
                ByteBufCodecs.FLOAT, Vermelho::charge,
                ByteBufCodecs.BOOL, Vermelho::flip,
                Vermelho::new);

        @Override
        public Type<Vermelho> type() {
            return TYPE;
        }
    }

    // Server -> client: the Red fired, a wall of repulsion from `from` along dir for range blocks, radius wide, its front
    // moving speed blocks per tick. charge 0 to 1.
    public record VermelhoBlast(Vec3 from, Vec3 dir, float range, float radius, float speed, float charge) implements CustomPacketPayload {
        public static final Type<VermelhoBlast> TYPE = payloadType("vermelho_blast");
        public static final StreamCodec<ByteBuf, VermelhoBlast> STREAM_CODEC = StreamCodec.composite(
                VEC3, VermelhoBlast::from,
                VEC3, VermelhoBlast::dir,
                ByteBufCodecs.FLOAT, VermelhoBlast::range,
                ByteBufCodecs.FLOAT, VermelhoBlast::radius,
                ByteBufCodecs.FLOAT, VermelhoBlast::speed,
                ByteBufCodecs.FLOAT, VermelhoBlast::charge,
                VermelhoBlast::new);

        @Override
        public Type<VermelhoBlast> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's Black Flash (power/powers/gojo/AzulFlash) at pos, the fist going along dir, charge 0 to 1.
    // kind 0 the slow motion (entity = Gojo, ticks = how long), 1 the punch (ticks = how
    // long the target stays on the fist), 2 the throw.
    public record BlackFlash(int kind, int entity, Vec3 pos, Vec3 dir, float charge, int ticks) implements CustomPacketPayload {
        public static final Type<BlackFlash> TYPE = payloadType("black_flash");
        public static final StreamCodec<ByteBuf, BlackFlash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BlackFlash::kind,
                ByteBufCodecs.VAR_INT, BlackFlash::entity,
                VEC3, BlackFlash::pos,
                VEC3, BlackFlash::dir,
                ByteBufCodecs.FLOAT, BlackFlash::charge,
                ByteBufCodecs.VAR_INT, BlackFlash::ticks,
                BlackFlash::new);

        @Override
        public Type<BlackFlash> type() {
            return TYPE;
        }
    }

    // Server -> client: a body out of its own control (client/LimpBody), for whoever sees it. phase: 0 pulled, 1 slow
    // motion, 2 stuck on the fist, 3 thrown (ticks = how long it lies stunned when it lands), 5 let go, 6 a jolt
    // (strength 0 to 1).
    public record Limp(int entity, int phase, int ticks, float strength) implements CustomPacketPayload {
        public static final int PULL = 0, SLOW = 1, FIT = 2, THROW = 3, OFF = 5, JOLT = 6;
        public static final Type<Limp> TYPE = payloadType("limp");
        public static final StreamCodec<ByteBuf, Limp> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Limp::entity,
                ByteBufCodecs.VAR_INT, Limp::phase,
                ByteBufCodecs.VAR_INT, Limp::ticks,
                ByteBufCodecs.FLOAT, Limp::strength,
                Limp::new);

        @Override
        public Type<Limp> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's Expansão de 0,2 s (power/powers/gojo/VazioRelampago) opening at center: the void snaps
    // open on the screen and closes in an X (client/fx/VoidFlashScene).
    public record VoidFlash(int caster, Vec3 center) implements CustomPacketPayload {
        public static final Type<VoidFlash> TYPE = payloadType("void_flash");
        public static final StreamCodec<ByteBuf, VoidFlash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, VoidFlash::caster,
                VEC3, VoidFlash::center,
                VoidFlash::new);

        @Override
        public Type<VoidFlash> type() {
            return TYPE;
        }
    }

    // Server -> client: Gojo's Purple (power/powers/gojo/Roxo), every tick while it forms and grows in front of owner's
    // chest. mode 0 alive, 2 gone (cut short, it fades). tick: ticks since he pressed C (the client runs the whole forming
    // timeline from it, placing the two spheres on his body itself so they never lag); charge 0 to 1: how much it grew
    // after forming.
    public record Roxo(int id, int owner, int mode, int tick, float charge) implements CustomPacketPayload {
        public static final Type<Roxo> TYPE = payloadType("roxo");
        public static final StreamCodec<ByteBuf, Roxo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Roxo::id,
                ByteBufCodecs.VAR_INT, Roxo::owner,
                ByteBufCodecs.VAR_INT, Roxo::mode,
                ByteBufCodecs.VAR_INT, Roxo::tick,
                ByteBufCodecs.FLOAT, Roxo::charge,
                Roxo::new);

        @Override
        public Type<Roxo> type() {
            return TYPE;
        }
    }

    // Server -> client: the Purple launched (it replaces the held one with the same id). It flies from `from` along dir;
    // motion = (start speed, added each tick, top speed) in blocks per tick; size = (radius, range, charge).
    public record RoxoShot(int id, int owner, Vec3 from, Vec3 dir, Vec3 motion, Vec3 size) implements CustomPacketPayload {
        public static final Type<RoxoShot> TYPE = payloadType("roxo_shot");
        public static final StreamCodec<ByteBuf, RoxoShot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RoxoShot::id,
                ByteBufCodecs.VAR_INT, RoxoShot::owner,
                VEC3, RoxoShot::from,
                VEC3, RoxoShot::dir,
                VEC3, RoxoShot::motion,
                VEC3, RoxoShot::size,
                RoxoShot::new);

        @Override
        public Type<RoxoShot> type() {
            return TYPE;
        }
    }

    // Server -> client: the Unlimited Purple at center (a full Red into a parked full Blue). size = (the Purple's radius,
    // the blast's radius, the Blue's radius); timing = (implode, grow, wait) ticks; blast = its ticks.
    public record RoxoIrrestrito(Vec3 center, Vec3 size, Vec3 timing, int blast) implements CustomPacketPayload {
        public static final Type<RoxoIrrestrito> TYPE = payloadType("roxo_irrestrito");
        public static final StreamCodec<ByteBuf, RoxoIrrestrito> STREAM_CODEC = StreamCodec.composite(
                VEC3, RoxoIrrestrito::center,
                VEC3, RoxoIrrestrito::size,
                VEC3, RoxoIrrestrito::timing,
                ByteBufCodecs.VAR_INT, RoxoIrrestrito::blast,
                RoxoIrrestrito::new);

        @Override
        public Type<RoxoIrrestrito> type() {
            return TYPE;
        }
    }

    // Server -> client (the owner only), every tick while his held Blue is under his control: the mouse wheel steers it
    // (and the hotbar doesn't scroll). false when it no longer is.
    public record AzulControl(boolean on) implements CustomPacketPayload {
        public static final Type<AzulControl> TYPE = payloadType("azul_control");
        public static final StreamCodec<ByteBuf, AzulControl> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AzulControl::on, AzulControl::new);

        @Override
        public Type<AzulControl> type() {
            return TYPE;
        }
    }

    // Client -> server: the mouse wheel turned while the Blue is under control (+ away, - closer).
    public record AzulScroll(int steps) implements CustomPacketPayload {
        public static final Type<AzulScroll> TYPE = payloadType("azul_scroll");
        public static final StreamCodec<ByteBuf, AzulScroll> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AzulScroll::steps, AzulScroll::new);

        @Override
        public Type<AzulScroll> type() {
            return TYPE;
        }
    }

    public record EnergyBall(int id, int mode, Vec3 pos, float radius, float grow, boolean small) implements CustomPacketPayload {
        public static final Type<EnergyBall> TYPE = payloadType("energy_ball");
        public static final StreamCodec<ByteBuf, EnergyBall> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EnergyBall::id,
                ByteBufCodecs.VAR_INT, EnergyBall::mode,
                VEC3, EnergyBall::pos,
                ByteBufCodecs.FLOAT, EnergyBall::radius,
                ByteBufCodecs.FLOAT, EnergyBall::grow,
                ByteBufCodecs.BOOL, EnergyBall::small,
                EnergyBall::new);

        @Override
        public Type<EnergyBall> type() {
            return TYPE;
        }
    }

    // Server -> client: the sphere of Bills' Esfera da Destruição on C (power/powers/bills/DestructionSphere), sent every
    // tick it exists. mode 0 = there at pos with radius, 2 = gone. giant: past the implosion (the screen shakes near it).
    public record PlanetSphere(int id, int mode, Vec3 pos, float radius, boolean giant) implements CustomPacketPayload {
        public static final Type<PlanetSphere> TYPE = payloadType("planet_sphere");
        public static final StreamCodec<ByteBuf, PlanetSphere> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlanetSphere::id,
                ByteBufCodecs.VAR_INT, PlanetSphere::mode,
                VEC3, PlanetSphere::pos,
                ByteBufCodecs.FLOAT, PlanetSphere::radius,
                ByteBufCodecs.BOOL, PlanetSphere::giant,
                PlanetSphere::new);

        @Override
        public Type<PlanetSphere> type() {
            return TYPE;
        }
    }

    // Server -> client: the Esfera da Destruição (C) hit the ground at pos. The whole explosion (the blast, the smoke
    // rising, the rewind and the second blast) plays from here on the timing in bills.destruction_sphere.json.
    public record PlanetBlast(Vec3 pos, boolean awakened) implements CustomPacketPayload {
        public static final Type<PlanetBlast> TYPE = payloadType("planet_blast");
        public static final StreamCodec<ByteBuf, PlanetBlast> STREAM_CODEC = StreamCodec.composite(
                VEC3, PlanetBlast::pos,
                ByteBufCodecs.BOOL, PlanetBlast::awakened,
                PlanetBlast::new);

        @Override
        public Type<PlanetBlast> type() {
            return TYPE;
        }
    }

    // Server -> client: the hakaizinho, flying straight from start at velocity per tick for ticks.
    public record HakaiShot(Vec3 start, Vec3 velocity, int ticks, boolean awakened) implements CustomPacketPayload {
        public static final Type<HakaiShot> TYPE = payloadType("hakai_shot");
        public static final StreamCodec<ByteBuf, HakaiShot> STREAM_CODEC = StreamCodec.composite(
                VEC3, HakaiShot::start,
                VEC3, HakaiShot::velocity,
                ByteBufCodecs.VAR_INT, HakaiShot::ticks,
                ByteBufCodecs.BOOL, HakaiShot::awakened,
                HakaiShot::new);

        @Override
        public Type<HakaiShot> type() {
            return TYPE;
        }
    }

    // Server -> client: a boss's ult cutscene (zoom on its face, then look up at lookAt in the sky, then back).
    public record BossCutscene(int entityId, int zoomTicks, int skyTicks, int backTicks, Vec3 lookAt) implements CustomPacketPayload {
        public static final Type<BossCutscene> TYPE = payloadType("boss_cutscene");
        public static final StreamCodec<ByteBuf, BossCutscene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BossCutscene::entityId,
                ByteBufCodecs.VAR_INT, BossCutscene::zoomTicks,
                ByteBufCodecs.VAR_INT, BossCutscene::skyTicks,
                ByteBufCodecs.VAR_INT, BossCutscene::backTicks,
                VEC3, BossCutscene::lookAt,
                BossCutscene::new);

        @Override
        public Type<BossCutscene> type() {
            return TYPE;
        }
    }

    // Server -> client: draw this boss bar with the boss's own frames (textures/gui/bossbar/<texture>/).
    // glow: color of the light circling the bar, -1 for none.
    public record BossBarStyle(UUID barId, String texture, int frames, int width, int height, int glow) implements CustomPacketPayload {
        public static final Type<BossBarStyle> TYPE = payloadType("boss_bar_style");
        public static final StreamCodec<ByteBuf, BossBarStyle> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BossBarStyle::barId,
                ByteBufCodecs.STRING_UTF8, BossBarStyle::texture,
                ByteBufCodecs.VAR_INT, BossBarStyle::frames,
                ByteBufCodecs.VAR_INT, BossBarStyle::width,
                ByteBufCodecs.VAR_INT, BossBarStyle::height,
                ByteBufCodecs.INT, BossBarStyle::glow,
                BossBarStyle::new);

        @Override
        public Type<BossBarStyle> type() {
            return TYPE;
        }
    }

    // Server -> client: custom boss bars on or off for this player (/soul bossbar).
    public record BossBarToggle(boolean on) implements CustomPacketPayload {
        public static final Type<BossBarToggle> TYPE = payloadType("boss_bar_toggle");
        public static final StreamCodec<ByteBuf, BossBarToggle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BossBarToggle::on, BossBarToggle::new);

        @Override
        public Type<BossBarToggle> type() {
            return TYPE;
        }
    }

    // Server -> client: this fighter's aura is on with this power's look, or off (empty power). Players send it when
    // combat mode starts or ends, and to anyone who starts seeing them.
    // Server -> clients: the 3D outfit a player is wearing (the power's id, "" when off).
    public record OutfitState(int entityId, String power) implements CustomPacketPayload {
        public static final Type<OutfitState> TYPE = payloadType("outfit_state");
        public static final StreamCodec<ByteBuf, OutfitState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OutfitState::entityId,
                ByteBufCodecs.STRING_UTF8, OutfitState::power,
                OutfitState::new);

        @Override
        public Type<OutfitState> type() {
            return TYPE;
        }
    }

    // Server -> client: whether this player carries Kokushibo's sword in the inventory (sheath on the hip), and whether
    // it is drawn (held in combat mode) or resting in the sheath (power/PowerLogic.sheathCode).
    public record SheathState(int entityId, boolean carrying, boolean drawn) implements CustomPacketPayload {
        public static final Type<SheathState> TYPE = payloadType("sheath_state");
        public static final StreamCodec<ByteBuf, SheathState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SheathState::entityId,
                ByteBufCodecs.BOOL, SheathState::carrying,
                ByteBufCodecs.BOOL, SheathState::drawn,
                SheathState::new);

        @Override
        public Type<SheathState> type() {
            return TYPE;
        }
    }

    // Client -> server: put the power's outfit on or take it off (abilities menu).
    public record ToggleOutfit(boolean on) implements CustomPacketPayload {
        public static final Type<ToggleOutfit> TYPE = payloadType("toggle_outfit");
        public static final StreamCodec<ByteBuf, ToggleOutfit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ToggleOutfit::on,
                ToggleOutfit::new);

        @Override
        public Type<ToggleOutfit> type() {
            return TYPE;
        }
    }

    public record AuraState(int entityId, String power) implements CustomPacketPayload {
        public static final Type<AuraState> TYPE = payloadType("aura_state");
        public static final StreamCodec<ByteBuf, AuraState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AuraState::entityId,
                ByteBufCodecs.STRING_UTF8, AuraState::power,
                AuraState::new);

        @Override
        public Type<AuraState> type() {
            return TYPE;
        }
    }

    // A player's power, for everyone who sees them (client/StancePoses: each character's way of standing and walking).
    public record StanceState(int entityId, String power) implements CustomPacketPayload {
        public static final Type<StanceState> TYPE = payloadType("stance_state");
        public static final StreamCodec<ByteBuf, StanceState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StanceState::entityId,
                ByteBufCodecs.STRING_UTF8, StanceState::power,
                StanceState::new);

        @Override
        public Type<StanceState> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UseAbility.TYPE, UseAbility.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Tasks.as(player, () -> PowerLogic.tryUse(player, payload.slot()));
            }
        }));
        registrar.playToServer(UseMovement.TYPE, UseMovement.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Tasks.as(player, () -> Movement.onPlayerMove(player, payload.kind(), payload.forward(), payload.strafe()));
            }
        }));
        registrar.playToServer(Punch.TYPE, Punch.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.getData(ModAttachments.POWER).hasPower() || player.isSpectator()) {
                return;
            }
            if (payload.key()) {
                CombatMode.enter(player);
                Tasks.as(player, () -> Melee.punch(player, null));
            } else if (CombatMode.freeHand(player) && CombatMode.in(player)) {
                Tasks.as(player, () -> Melee.punch(player, null));
            }
        }));
        registrar.playToServer(Guard.TYPE, Guard.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Tasks.as(player, () -> Defense.setGuard(player, payload.down()));
            }
        }));
        registrar.playToServer(AttackHold.TYPE, AttackHold.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.getData(ModAttachments.POWER).attackHeld = payload.down();
            }
        }));
        registrar.playToServer(AzulScroll.TYPE, AzulScroll.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.starkinhon.battlegroundsmod.power.powers.gojo.Azul.scroll(player, payload.steps());
            }
        }));
        registrar.playToServer(AbilityHold.TYPE, AbilityHold.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.getData(ModAttachments.POWER).abilityHeld = payload.down();
            }
        }));
        registrar.playToServer(Teleport.TYPE, Teleport.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Tasks.as(player, () -> Movement.teleport(player));
            }
        }));
        registrar.playToServer(SelectPower.TYPE, SelectPower.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PowerLogic.selectFromBook(player, payload.power());
            }
        }));
        registrar.playToClient(SyncPower.TYPE, SyncPower.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> payload.applyTo(context.player())));
        registrar.playToClient(SyncStamina.TYPE, SyncStamina.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> payload.applyTo(context.player())));
        registrar.playToClient(SyncCharge.TYPE, SyncCharge.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> payload.applyTo(context.player())));
        registrar.playToClient(PlaySkillAnimation.TYPE, PlaySkillAnimation.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.playSkillAnimation(payload.entityId(), payload.skill(), payload.awakened())));
        registrar.playToClient(ComboAnimation.TYPE, ComboAnimation.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.comboAnimation(payload.entityId(), payload.step())));
        registrar.playToClient(GuardPose.TYPE, GuardPose.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.guardPose(payload.entityId(), payload.on())));
        registrar.playToClient(Hitstop.TYPE, Hitstop.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hitstop(payload.entityId(), payload.ticks())));
        registrar.playToClient(FaceCutscene.TYPE, FaceCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.startFaceCutscene(payload.entityId(), payload.skill(), payload.awakened())));
        registrar.playToClient(ScreenShake.TYPE, ScreenShake.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.screenShake(payload.strength(), payload.ticks())));
        registrar.playToClient(MeteorFall.TYPE, MeteorFall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.meteorFall(payload.skill(), payload.awakened(), payload.start(), payload.end(), payload.delay(), payload.fallTicks())));
        registrar.playToClient(PlayEffects.TYPE, PlayEffects.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.playEffects(payload.skill(), payload.awakened(), payload.momento(), payload.pos(), payload.dir(),
                        payload.entityId(), payload.ticks(), payload.owner())));
        registrar.playToClient(OwnedSound.TYPE, OwnedSound.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.ownedSound(payload.owner(), payload.sound(), payload.pos(), payload.volume(), payload.pitch(), payload.screen(),
                        payload.seed())));
        registrar.playToClient(StopOwnerFx.TYPE, StopOwnerFx.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.stopOwnerFx(payload.owner())));
        registrar.playToClient(SusanooArm.TYPE, SusanooArm.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.susanooArm(payload.casterId(), payload.targetId(), payload.phase(), payload.a(), payload.b())));
        registrar.playToClient(ShoulderCutscene.TYPE, ShoulderCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.shoulderCutscene(payload.casterId(), payload.targetId(), payload.ticks())));
        registrar.playToClient(GrabCutscene.TYPE, GrabCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.grabCutscene(payload.casterId(), payload.targetId(), payload.awakened(), payload.impactTick())));
        registrar.playToClient(BopCutscene.TYPE, BopCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bopCutscene(payload.casterId(), payload.targetId(), payload.awakened(), payload.endTick())));
        registrar.playToClient(HideEntity.TYPE, HideEntity.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hideEntity(payload.entityId(), payload.ticks())));
        registrar.playToClient(AshDeath.TYPE, AshDeath.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.ashDeath(payload.entityId())));
        registrar.playToClient(HakaiCutscene.TYPE, HakaiCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiCutscene(payload.casterId(), payload.targetId(), payload.awakened())));
        registrar.playToClient(HakaiDeath.TYPE, HakaiDeath.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiDeath(payload.entityId(), payload.awakened())));
        registrar.playToClient(EsferaCutscene.TYPE, EsferaCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.esferaCutscene(payload.casterId(), payload.targetId(), payload.awakened())));
        registrar.playToClient(EsferaFall.TYPE, EsferaFall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.esferaFall(payload.targetId(), payload.start(), payload.lastHead(), payload.awakened())));
        registrar.playToClient(GojoDomain.TYPE, GojoDomain.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.gojoDomain(payload.casterId(), payload.center(), payload.awakened(), payload.mode(), payload.other())));
        registrar.playToClient(EraseAttack.TYPE, EraseAttack.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.eraseAttack(payload.skill(), payload.pos(), payload.radius())));
        registrar.playToClient(HakaiBall.TYPE, HakaiBall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiBall(payload.id(), payload.mode(), payload.pos(), payload.targetId(), payload.radius(), payload.awakened())));
        registrar.playToClient(HakaiShot.TYPE, HakaiShot.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiShot(payload.start(), payload.velocity(), payload.ticks(), payload.awakened())));
        registrar.playToClient(BlackBox.TYPE, BlackBox.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.blackBox(payload.id(), payload.mode(), payload.base(), payload.size(), payload.awakened())));
        registrar.playToClient(AzulControl.TYPE, AzulControl.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.azulControl(payload.on())));
        registrar.playToClient(Azul.TYPE, Azul.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.azul(payload.id(), payload.mode(), payload.pos(), payload.radius(), payload.charge(), payload.style())));
        registrar.playToClient(Vermelho.TYPE, Vermelho.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.vermelho(payload.id(), payload.owner(), payload.mode(), payload.charge(), payload.flip())));
        registrar.playToClient(Roxo.TYPE, Roxo.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.roxo(payload.id(), payload.owner(), payload.mode(), payload.tick(), payload.charge())));
        registrar.playToClient(RoxoShot.TYPE, RoxoShot.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.roxoShot(payload.id(), payload.owner(), payload.from(), payload.dir(), payload.motion(), payload.size())));
        registrar.playToClient(RoxoIrrestrito.TYPE, RoxoIrrestrito.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.roxoIrrestrito(payload.center(), payload.size(), payload.timing(), payload.blast())));
        registrar.playToClient(VermelhoBlast.TYPE, VermelhoBlast.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.vermelhoBlast(payload.from(), payload.dir(), payload.range(), payload.radius(), payload.speed(), payload.charge())));
        registrar.playToClient(BlackFlash.TYPE, BlackFlash.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.blackFlash(payload.kind(), payload.entity(), payload.pos(), payload.dir(), payload.charge(), payload.ticks())));
        registrar.playToClient(Limp.TYPE, Limp.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.limp(payload.entity(), payload.phase(), payload.ticks(), payload.strength())));
        registrar.playToClient(VoidFlash.TYPE, VoidFlash.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.voidFlash(payload.caster(), payload.center())));
        registrar.playToClient(SubFx.TYPE, SubFx.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.subFx(payload.kind(), payload.id(), payload.pos(), payload.dir(), payload.a(), payload.b(), payload.c())));
        registrar.playToClient(PunchFx.TYPE, PunchFx.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.punchFx(payload.kind(), payload.entityId(), payload.pos(), payload.dir(), payload.a(), payload.b())));
        registrar.playToClient(SwordFx.TYPE, SwordFx.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.swordFx(payload.kind(), payload.entityId(), payload.pos(), payload.dir(), payload.a(), payload.b())));
        registrar.playToClient(AizenFx.TYPE, AizenFx.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.aizenFx(payload.kind(), payload.entityId(), payload.pos(), payload.dir(), payload.a(), payload.b())));
        registrar.playToClient(EnergyBall.TYPE, EnergyBall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.energyBall(payload.id(), payload.mode(), payload.pos(), payload.radius(), payload.grow(), payload.small())));
        registrar.playToClient(PlanetSphere.TYPE, PlanetSphere.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.planetSphere(payload.id(), payload.mode(), payload.pos(), payload.radius(), payload.giant())));
        registrar.playToClient(PlanetBlast.TYPE, PlanetBlast.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.planetBlast(payload.pos(), payload.awakened())));
        registrar.playToClient(BossBarStyle.TYPE, BossBarStyle.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossBarStyle(payload.barId(), payload.texture(), payload.frames(), payload.width(), payload.height(), payload.glow())));
        registrar.playToClient(BossCutscene.TYPE, BossCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossCutscene(payload.entityId(), payload.zoomTicks(), payload.skyTicks(), payload.backTicks(), payload.lookAt())));
        registrar.playToClient(BossBarToggle.TYPE, BossBarToggle.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossBarToggle(payload.on())));
        registrar.playToClient(KatonBall.TYPE, KatonBall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.katonBall(payload.id(), payload.casterId(), payload.mode(), payload.pos(), payload.velocity(), payload.radius(),
                        payload.maxRadius(), payload.ticks())));
        registrar.playToClient(Rewind.TYPE, Rewind.STREAM_CODEC, (payload, context) -> context.enqueueWork(ClientHooks::rewind));
        registrar.playToClient(AuraState.TYPE, AuraState.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.auraState(payload.entityId(), payload.power())));
        registrar.playToClient(OutfitState.TYPE, OutfitState.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.outfitState(payload.entityId(), payload.power())));
        registrar.playToClient(SheathState.TYPE, SheathState.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.sheathState(payload.entityId(), payload.carrying(), payload.drawn())));
        registrar.playToClient(StanceState.TYPE, StanceState.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.stanceState(payload.entityId(), payload.power())));
        registrar.playToServer(ToggleOutfit.TYPE, ToggleOutfit.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.getData(ModAttachments.POWER).outfit = payload.on();
            }
        }));
    }
}
