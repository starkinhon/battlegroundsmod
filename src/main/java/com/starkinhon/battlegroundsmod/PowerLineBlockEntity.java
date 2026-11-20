// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Holds nothing saved: the client keeps here the next power line its cable runs to (client/PowerLineRenderer).
public class PowerLineBlockEntity extends BlockEntity {
    public BlockPos cableTo;
    public long nextLook;

    public PowerLineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.POWER_LINE.get(), pos, state);
    }
}
