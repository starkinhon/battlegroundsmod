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

    // Server -> client: a player started a skill with a body animation (timing comes from the skill's spec).
    public record PlaySkillAnimation(int entityId, String skill, boolean awakened) implements CustomPacketPayload {
        public static final Type<PlaySkillAnimation> TYPE = payloadType("play_skill_animation");
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
    public record PlayEffects(String skill, boolean awakened, String momento, Vec3 pos, Vec3 dir, int entityId, int ticks)
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
        }, buf -> new PlayEffects(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                VEC3.decode(buf), VEC3.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public Type<PlayEffects> type() {
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

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UseAbility.TYPE, UseAbility.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PowerLogic.tryUse(player, payload.slot());
            }
        }));
        registrar.playToServer(UseMovement.TYPE, UseMovement.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Movement.onPlayerMove(player, payload.kind(), payload.forward(), payload.strafe());
            }
        }));
        registrar.playToServer(Punch.TYPE, Punch.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.getData(ModAttachments.POWER).hasPower() || player.isSpectator()) {
                return;
            }
            if (payload.key()) {
                CombatMode.enter(player);
                Melee.punch(player, null);
            } else if (player.getMainHandItem().isEmpty() && CombatMode.in(player)) {
                Melee.punch(player, null);
            }
        }));
        registrar.playToServer(Guard.TYPE, Guard.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Defense.setGuard(player, payload.down());
            }
        }));
        registrar.playToServer(AttackHold.TYPE, AttackHold.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.getData(ModAttachments.POWER).attackHeld = payload.down();
            }
        }));
        registrar.playToServer(AbilityHold.TYPE, AbilityHold.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.getData(ModAttachments.POWER).abilityHeld = payload.down();
            }
        }));
        registrar.playToServer(Teleport.TYPE, Teleport.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Movement.teleport(player);
            }
        }));
        registrar.playToServer(SelectPower.TYPE, SelectPower.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PowerLogic.selectFromBook(player, payload.power());
            }
        }));
        registrar.playToClient(SyncPower.TYPE, SyncPower.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> payload.applyTo(context.player())));
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
                        payload.entityId(), payload.ticks())));
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
        registrar.playToClient(EraseAttack.TYPE, EraseAttack.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.eraseAttack(payload.skill(), payload.pos(), payload.radius())));
        registrar.playToClient(HakaiBall.TYPE, HakaiBall.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiBall(payload.id(), payload.mode(), payload.pos(), payload.targetId(), payload.radius(), payload.awakened())));
        registrar.playToClient(HakaiShot.TYPE, HakaiShot.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.hakaiShot(payload.start(), payload.velocity(), payload.ticks(), payload.awakened())));
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
    }
}
