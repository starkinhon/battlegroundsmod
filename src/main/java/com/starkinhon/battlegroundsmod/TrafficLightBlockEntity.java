// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Holds nothing: it is only there so the client draws the lit lens (client/TrafficLightRenderer).
public class TrafficLightBlockEntity extends BlockEntity {
    public TrafficLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TRAFFIC_LIGHT.get(), pos, state);
    }
}
