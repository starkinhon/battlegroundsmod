// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
// - walking into a tree, a wall or any block side RUNS up it, the body turned flat with the feet on it (Stark 2026-11-19:
//   "não é pra escalar, é pra correr na árvore"): forward runs up, back slides down, the sides run along it, crouch
//   holds still, jump kicks off it (client/WallRun moves and draws it; the server only knows it is on and keeps the
//   fall from hurting).
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
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            RUN_SERVER.remove(e.getEntity().getUUID());
            sync(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> RUN_SERVER.remove(e.getEntity().getUUID()));
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
        if (!(context instanceof EntityCollisionContext ec)) {
            return null;
        }
        // the introduction's enemies (SoulBoss.introMoves set by the script) stand on the water like the player
        if (ec.getEntity() instanceof com.starkinhon.battlegroundsmod.boss.SoulBoss boss && boss.introMoves != null) {
            if (!state.getFluidState().is(FluidTags.WATER) || !level.getFluidState(pos.above()).isEmpty()
                    || boss.isUnderWater() || !context.isAbove(SURFACE, pos, true)) {
                return null;
            }
            return SURFACE;
        }
        if (!(ec.getEntity() instanceof Player player)) {
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
    // On while running on a wall (client/WallRun): the ladder rules keep the fall from hurting and the player on it.
    public static boolean climbs(Player player) {
        return running(player) && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger()
                && !player.isInWater() && knows(player);
    }

    // Who is running on a wall now, per side (the integrated server shares this class with its client).
    // Server keeps the wall direction (0..3) so StartTracking can show the pose to players who walk up later.
    private static final java.util.Set<java.util.UUID> RUN_CLIENT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<java.util.UUID, Integer> RUN_SERVER = new java.util.concurrent.ConcurrentHashMap<>();

    public static void resetStatics() {
        RUN_SERVER.clear();
    }

    public static boolean running(Player player) {
        return player.level().isClientSide() ? RUN_CLIENT.contains(player.getUUID()) : RUN_SERVER.containsKey(player.getUUID());
    }

    public static int runDir(ServerPlayer player) {
        return RUN_SERVER.getOrDefault(player.getUUID(), -1);
    }

    public static void setRunning(Player player, boolean on) {
        setRunning(player, on ? 0 : -1);
    }

    public static void setRunning(Player player, int dir) {
        if (player.level().isClientSide()) {
            if (dir >= 0) {
                RUN_CLIENT.add(player.getUUID());
            } else {
                RUN_CLIENT.remove(player.getUUID());
            }
        } else if (dir >= 0) {
            RUN_SERVER.put(player.getUUID(), dir);
        } else {
            RUN_SERVER.remove(player.getUUID());
        }
    }
}
