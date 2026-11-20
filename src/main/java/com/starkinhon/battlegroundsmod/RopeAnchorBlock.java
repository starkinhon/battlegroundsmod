// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// The top of a rope bridge's post in the Mar da Névoa, the rope wound round it (Stark 2026-11-20: the railing was rope
// blocks going in steps, "zoado"; he asked for one curved rope). The world generator gives the post at one end of the
// bridge the other end's post (RopeAnchorBlockEntity) and the client draws one smooth rope between them that follows the
// deck's sag (client/RopeRenderer). No physics, nothing to bump into but the post.
public class RopeAnchorBlock extends Block implements EntityBlock {
    public RopeAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RopeAnchorBlockEntity(pos, state);
    }
}
