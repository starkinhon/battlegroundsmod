// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Shibuya's Telão (Stark 2026-11-13: "os telões que piscam em cores ... ficou muito foda"): the block keeps its dark
// model, and its block entity lets the client draw the moving picture over its open faces (client/TelaoRenderer), one
// procedural video across the whole screen, as in the approved preview.
public class TelaoBlock extends Block implements EntityBlock {
    public TelaoBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelaoBlockEntity(pos, state);
    }
}
