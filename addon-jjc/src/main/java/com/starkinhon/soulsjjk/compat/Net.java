package com.starkinhon.soulsjjk.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.starkinhon.soulsjjk.SoulsJjk;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// One Forge SimpleChannel carrying every payload: an index (registration order, the same on both sides) and the
// payload's own codec.
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(SoulsJjk.MODID, "main"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    private record Entry<T extends CustomPacketPayload>(int index, CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler, boolean toServer, boolean toClient) {
    }

    private static final List<Entry<?>> ENTRIES = new ArrayList<>();
    private static final Map<ResourceLocation, Entry<?>> BY_ID = new HashMap<>();
    // Set on the client (SoulsJjkClient): the local player, so handlers on the client get one without touching client
    // classes from common code.
    public static Supplier<Player> clientPlayer = () -> null;

    private Net() {
    }

    // Wraps a payload for the channel.
    public record Envelope(CustomPacketPayload payload) {
    }

    public static void init() {
        CHANNEL.registerMessage(0, Envelope.class, Net::encode, Net::decode, Net::handle);
    }

    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super net.minecraft.network.FriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler, boolean toServer, boolean toClient) {
        Entry<T> entry = new Entry<>(ENTRIES.size(), type, codec, handler, toServer, toClient);
        ENTRIES.add(entry);
        BY_ID.put(type.id(), entry);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void encode(Envelope envelope, FriendlyByteBuf buf) {
        Entry<T> entry = (Entry<T>) BY_ID.get(envelope.payload().type().id());
        if (entry == null) {
            throw new IllegalStateException("Unregistered payload " + envelope.payload().type().id());
        }
        buf.writeVarInt(entry.index());
        entry.codec().encode(buf, (T) envelope.payload());
    }

    private static Envelope decode(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        Entry<?> entry = ENTRIES.get(index);
        return new Envelope((CustomPacketPayload) entry.codec().decode(buf));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handle(Envelope envelope, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        Entry<T> entry = (Entry<T>) BY_ID.get(envelope.payload().type().id());
        boolean server = ctx.getDirection().getReceptionSide().isServer();
        if (entry != null && (server ? entry.toServer() : entry.toClient())) {
            IPayloadContext context = new IPayloadContext() {
                public Player player() {
                    return server ? ctx.getSender() : clientPlayer.get();
                }

                public void enqueueWork(Runnable work) {
                    ctx.enqueueWork(work);
                }
            };
            entry.handler().accept((T) envelope.payload(), context);
        }
        ctx.setPacketHandled(true);
    }
}
