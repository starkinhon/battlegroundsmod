// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Holds nothing: it is only there so the client draws the Telão's picture (client/TelaoRenderer).
public class TelaoBlockEntity extends BlockEntity {
    public TelaoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TELAO.get(), pos, state);
    }
}
