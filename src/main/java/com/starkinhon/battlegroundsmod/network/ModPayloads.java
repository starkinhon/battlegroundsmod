package com.starkinhon.battlegroundsmod.network;

import java.util.ArrayList;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
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

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name));
    }

    // Client -> server: the player pressed an ability key.
    public record UseAbility(int slot) implements CustomPacketPayload {
        public static final Type<UseAbility> TYPE = type("use_ability");
        public static final StreamCodec<ByteBuf, UseAbility> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UseAbility::slot, UseAbility::new);

        @Override
        public Type<UseAbility> type() {
            return TYPE;
        }
    }

    // Client -> server: the player picked a power in the book.
    public record SelectPower(String power) implements CustomPacketPayload {
        public static final Type<SelectPower> TYPE = type("select_power");
        public static final StreamCodec<ByteBuf, SelectPower> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SelectPower::power, SelectPower::new);

        @Override
        public Type<SelectPower> type() {
            return TYPE;
        }
    }

    // Server -> client: power state for the HUD.
    public record SyncPower(String power, float energy, float ult, List<Integer> cooldowns) implements CustomPacketPayload {
        public static final Type<SyncPower> TYPE = type("sync_power");
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
    }
}
