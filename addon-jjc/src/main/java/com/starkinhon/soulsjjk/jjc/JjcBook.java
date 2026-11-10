package com.starkinhon.soulsjjk.jjc;

import com.starkinhon.soulsjjk.SoulsJjk;
import com.starkinhon.soulsjjk.compat.ByteBufCodecs;
import com.starkinhon.soulsjjk.compat.CustomPacketPayload;
import com.starkinhon.soulsjjk.compat.PayloadRegistrar;
import com.starkinhon.soulsjjk.compat.StreamCodec;

import io.netty.buffer.ByteBuf;
import net.mcreator.jujutsucraft.network.JujutsucraftModVariables;
import net.mcreator.jujutsucraft.procedures.SelectedProcedure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

// The two new pages of JujutsuCraft's technique book (cursed_technique_changer): "Gojo Souls" and "Sukuna Souls".
// The buttons themselves are put on the book's second page by JjcBookScreen (client); the choice comes here.
public final class JjcBook {
    private JjcBook() {
    }

    public record Pick(int technique) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(new ResourceLocation(SoulsJjk.MODID, "jjc_pick"));
        public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Pick::technique, Pick::new);

        @Override
        public Type<Pick> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Pick.TYPE, Pick.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                pick(player, payload.technique());
            }
        }));
    }

    // As JujutsuCraft's own book buttons (SelectGojoProcedure and the like): the technique is set, then
    // SelectedProcedure takes the book, clears the effects and gives the technique's advancements.
    static void pick(ServerPlayer player, int technique) {
        if (!JjcTechniques.ours(technique) || !(player.getAbilities().instabuild || holdsBook(player))) {
            return;
        }
        JujutsucraftModVariables.PlayerVariables v = JjcTechniques.vars(player);
        if (v == null) {
            return;
        }
        v.PlayerCurseTechnique = technique;
        v.PlayerSelectCurseTechnique = 0;
        v.syncPlayerVariables(player);
        SelectedProcedure.execute(player.level(), player.getX(), player.getY(), player.getZ(), player);
        JjcTechniques.syncPower(player, v);
        player.closeContainer();
    }

    private static boolean holdsBook(ServerPlayer player) {
        ResourceLocation book = new ResourceLocation("jujutsucraft", "cursed_technique_changer");
        return book.equals(ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem()))
                || book.equals(ForgeRegistries.ITEMS.getKey(player.getOffhandItem().getItem()))
                || player.getInventory().items.stream().anyMatch(s -> book.equals(ForgeRegistries.ITEMS.getKey(s.getItem())));
    }
}
