package com.starkinhon.battlegroundsmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// A bridge post's rope (RopeAnchorBlock): the other end's post, as an offset from this one, and the deck's shape between
// them (how much it sags in the middle and how much of each end stays flat over its pillar's top), so the rope keeps the
// same height over the planks all the way. Only the post at one end of each rope has it; the other end has none.
public class RopeAnchorBlockEntity extends BlockEntity {
    public boolean linked;
    public int dx, dy, dz;
    public float sag, flatA, flatB;

    public RopeAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ROPE_ANCHOR_BE.get(), pos, state);
    }

    public void link(BlockPos to, float sag, float flatA, float flatB) {
        linked = true;
        dx = to.getX() - worldPosition.getX();
        dy = to.getY() - worldPosition.getY();
        dz = to.getZ() - worldPosition.getZ();
        this.sag = sag;
        this.flatA = flatA;
        this.flatB = flatB;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linked) {
            tag.putIntArray("ate", new int[] {dx, dy, dz});
            tag.putFloat("curva", sag);
            tag.putFloat("planoA", flatA);
            tag.putFloat("planoB", flatB);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] a = tag.getIntArray("ate");
        linked = a.length == 3;
        if (linked) {
            dx = a[0];
            dy = a[1];
            dz = a[2];
            sag = tag.getFloat("curva");
            flatA = tag.getFloat("planoA");
            flatB = tag.getFloat("planoB");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
