package com.starkinhon.battlegroundsmod.network;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.client.ClientHooks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UseAbility.TYPE, UseAbility.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PowerLogic.tryUse(player, payload.slot());
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
        registrar.playToClient(FaceCutscene.TYPE, FaceCutscene.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.startFaceCutscene(payload.entityId(), payload.skill(), payload.awakened())));
        registrar.playToClient(ScreenShake.TYPE, ScreenShake.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                () -> ClientHooks.screenShake(payload.strength(), payload.ticks())));
    }
}
