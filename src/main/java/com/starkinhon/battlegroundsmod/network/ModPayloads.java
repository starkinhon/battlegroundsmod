package com.starkinhon.battlegroundsmod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.client.ClientHooks;
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

    // Client -> server: left click with an empty hand: the next punch of the combo, even into the air.
    public record Punch() implements CustomPacketPayload {
        public static final Type<Punch> TYPE = payloadType("punch");
        public static final StreamCodec<ByteBuf, Punch> STREAM_CODEC = StreamCodec.unit(new Punch());

        @Override
        public Type<Punch> type() {
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
    public record SyncPower(String power, float energy, float ult, List<Integer> cooldowns) implements CustomPacketPayload {
        public static final Type<SyncPower> TYPE = payloadType("sync_power");
        public static final StreamCodec<ByteBuf, SyncPower> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SyncPower::power,
                ByteBufCodecs.FLOAT, SyncPower::energy,
                ByteBufCodecs.FLOAT, SyncPower::ult,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), SyncPower::cooldowns,
                SyncPower::new);

        public static SyncPower of(PowerData data) {
            List<Integer> cooldowns = new ArrayList<>();
            for (int cooldown : data.cooldowns) {
                cooldowns.add(cooldown);
            }
            return new SyncPower(data.power, data.energy, data.ult, cooldowns);
        }

        void applyTo(Player player) {
            PowerData data = player.getData(ModAttachments.POWER);
            data.power = power;
            data.energy = energy;
            data.ult = ult;
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
    public record BossBarStyle(UUID barId, String texture, int frames, int width, int height) implements CustomPacketPayload {
        public static final Type<BossBarStyle> TYPE = payloadType("boss_bar_style");
        public static final StreamCodec<ByteBuf, BossBarStyle> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BossBarStyle::barId,
                ByteBufCodecs.STRING_UTF8, BossBarStyle::texture,
                ByteBufCodecs.VAR_INT, BossBarStyle::frames,
                ByteBufCodecs.VAR_INT, BossBarStyle::width,
                ByteBufCodecs.VAR_INT, BossBarStyle::height,
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
            if (context.player() instanceof ServerPlayer player && player.getMainHandItem().isEmpty()
                    && player.getData(ModAttachments.POWER).hasPower() && !player.isSpectator()) {
                Melee.punch(player, null);
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
        registrar.playToClient(ShoulderCutscene.TYPE, ShoulderCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.shoulderCutscene(payload.casterId(), payload.targetId(), payload.ticks())));
        registrar.playToClient(AshDeath.TYPE, AshDeath.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.ashDeath(payload.entityId())));
        registrar.playToClient(BossBarStyle.TYPE, BossBarStyle.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossBarStyle(payload.barId(), payload.texture(), payload.frames(), payload.width(), payload.height())));
        registrar.playToClient(BossCutscene.TYPE, BossCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossCutscene(payload.entityId(), payload.zoomTicks(), payload.skyTicks(), payload.backTicks(), payload.lookAt())));
        registrar.playToClient(BossBarToggle.TYPE, BossBarToggle.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.bossBarToggle(payload.on())));
    }
}
