package com.starkinhon.battlegroundsmod.universe;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Controle de Energia, Naruto's universe ability (Stark 2026-11-19: "faz com que em Naruto você aprenda a controlar a sua
// energia... e aí você pode andar na água, você pode subir nas árvores"; general, not called chakra, so it fits any soul).
// Taught by Kakashi at Training Ground 3 after the bell test (universe/MasterTraining). Once learned, in any universe:
// - water holds you up like ground (the top of a water block with air over it is solid for you); crouch to sink in;
// - walking into a tree, a wall or any block side climbs it like a ladder; crouch holds you still on it.
// The movement is the client's (the player moves itself), so the learned techniques are sent to the client (Sync).
public final class EnergyControl {
    public static final String TECHNIQUE = "controle_energia";
    // A little under a full block: the feet stand just over the water's surface (a source is 14.2 px high), so the
    // player is not "in water" (no swimming slowdown) while standing on it.
    private static final VoxelShape SURFACE = Block.box(0, 0, 0, 16, 15, 16);

    // The local player's techniques, as the server sent them.
    public static volatile List<String> clientKnown = List.of();

    private EnergyControl() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sync(e.getEntity()));
    }

    // Sends the player's learned techniques to its client (call after learning one).
    public static void sync(Player player) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new ModPayloads.SyncTechniques(List.copyOf(sp.getData(ModAttachments.TECHNIQUES))));
        }
    }

    public static boolean knows(Player player) {
        if (player.level().isClientSide()) {
            // only the player this client moves; the others' positions come from the server
            return player.isLocalPlayer() && clientKnown.contains(TECHNIQUE);
        }
        return player.getData(ModAttachments.TECHNIQUES).contains(TECHNIQUE);
    }

    private static boolean free(Player player) {
        return !player.isShiftKeyDown() && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger();
    }

    // LiquidBlock's collision (mixin/LiquidBlockMixin): the water's top is ground for a player who knows it, standing over it.
    public static VoxelShape waterShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext ec) || !(ec.getEntity() instanceof Player player)) {
            return null;
        }
        if (!state.getFluidState().is(FluidTags.WATER) || !level.getFluidState(pos.above()).isEmpty()) {
            return null;
        }
        if (!free(player) || player.isUnderWater() || !knows(player) || !context.isAbove(SURFACE, pos, true)) {
            return null;
        }
        return SURFACE;
    }

    // LivingEntity.onClimbable (mixin/ClimbMixin): pushing against a block's side climbs it.
    public static boolean climbs(Player player) {
        return player.horizontalCollision && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger()
                && !player.isInWater() && knows(player);
    }
}
