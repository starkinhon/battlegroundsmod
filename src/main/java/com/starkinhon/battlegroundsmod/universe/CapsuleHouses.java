// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

// Cápsula Hoi-Poi that packs back (Stark 2026-11-20: "uma capsula casa, q eu consigo guardar e botar dnv"). When the
// capsule pops (item/HoiPoiCapsuleItem) the box it builds in is photographed twice: the ground as it was, and the house
// as built. The house is kept here, in the world, so there is never a capsule and a house at the same time. Its owner,
// crouching, right clicks its door with an empty hand: the ground comes back exactly as it was, the chest and the
// furnace go into the capsule with what they held (they come back full next time), anything the player put inside is
// dropped where it stood, and the capsule goes to the player. A house block broken by hand drops nothing (no endless
// concrete, beds and chests from popping and packing).
public final class CapsuleHouses {
    public static final String CONTENTS = "souls_casa_conteudo";

    private CapsuleHouses() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CapsuleHouses::onRightClick);
        NeoForge.EVENT_BUS.addListener(CapsuleHouses::onBreak);
    }

    // The box around a house: the floor disc (and the ground under it) up to the top of the dome.
    public record Box(BlockPos min, int sx, int sy, int sz) {
        int size() {
            return sx * sy * sz;
        }

        BlockPos at(int i) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            return min.offset(x, y, z);
        }

        boolean contains(BlockPos p) {
            int x = p.getX() - min.getX(), y = p.getY() - min.getY(), z = p.getZ() - min.getZ();
            return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
        }
    }

    // What a box holds right now: block states (palette + indices) and block entities by index.
    public static final class Shot {
        final List<BlockState> palette = new ArrayList<>();
        int[] index;
        final Map<Integer, CompoundTag> entities = new HashMap<>();

        static Shot take(ServerLevel level, Box box) {
            Shot shot = new Shot();
            shot.index = new int[box.size()];
            Map<BlockState, Integer> ids = new HashMap<>();
            for (int i = 0; i < box.size(); i++) {
                BlockPos p = box.at(i);
                BlockState st = level.getBlockState(p);
                Integer id = ids.get(st);
                if (id == null) {
                    id = shot.palette.size();
                    ids.put(st, id);
                    shot.palette.add(st);
                }
                shot.index[i] = id;
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    shot.entities.put(i, be.saveWithoutMetadata(level.registryAccess()));
                }
            }
            return shot;
        }

        BlockState state(int i) {
            return palette.get(index[i]);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag pal = new ListTag();
            for (BlockState st : palette) {
                pal.add(NbtUtils.writeBlockState(st));
            }
            tag.put("palette", pal);
            tag.put("index", new IntArrayTag(index));
            ListTag ents = new ListTag();
            for (var e : entities.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putInt("i", e.getKey());
                t.put("nbt", e.getValue());
                ents.add(t);
            }
            tag.put("entities", ents);
            return tag;
        }

        static Shot load(CompoundTag tag) {
            Shot shot = new Shot();
            ListTag pal = tag.getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < pal.size(); i++) {
                shot.palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i)));
            }
            shot.index = tag.getIntArray("index");
            ListTag ents = tag.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < ents.size(); i++) {
                shot.entities.put(ents.getCompound(i).getInt("i"), ents.getCompound(i).getCompound("nbt"));
            }
            return shot;
        }
    }

    static final class House {
        UUID owner;
        String dim;
        Box box;
        BlockPos door, chest, furnace;
        Shot ground, house;

        boolean isHouseBlock(ServerLevel level, BlockPos p) {
            if (!box.contains(p)) {
                return false;
            }
            BlockPos d = p.subtract(box.min());
            int i = d.getX() + d.getZ() * box.sx() + d.getY() * box.sx() * box.sz();
            // by block, not by state: an open door or a lit furnace is still the house's
            Block built = house.state(i).getBlock();
            return built != Blocks.AIR && built == level.getBlockState(p).getBlock() && built != ground.state(i).getBlock();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner);
            tag.putString("dim", dim);
            tag.putLong("min", box.min().asLong());
            tag.putIntArray("size", new int[] {box.sx(), box.sy(), box.sz()});
            tag.putLong("door", door.asLong());
            tag.putLong("chest", chest.asLong());
            tag.putLong("furnace", furnace.asLong());
            tag.put("ground", ground.save());
            tag.put("house", house.save());
            return tag;
        }

        static House load(CompoundTag tag) {
            House h = new House();
            h.owner = tag.getUUID("owner");
            h.dim = tag.getString("dim");
            int[] s = tag.getIntArray("size");
            h.box = new Box(BlockPos.of(tag.getLong("min")), s[0], s[1], s[2]);
            h.door = BlockPos.of(tag.getLong("door"));
            h.chest = BlockPos.of(tag.getLong("chest"));
            h.furnace = BlockPos.of(tag.getLong("furnace"));
            h.ground = Shot.load(tag.getCompound("ground"));
            h.house = Shot.load(tag.getCompound("house"));
            return h;
        }
    }

    // ---------- popping ----------

    // Before building: the ground as it is.
    public static Shot before(ServerLevel level, Box box) {
        return Shot.take(level, box);
    }

    // After building: keeps the house, and fills the chest and the furnace with what the capsule carried.
    public static void built(ServerLevel level, ServerPlayer owner, Box box, Shot ground, BlockPos door, BlockPos chest, BlockPos furnace,
            ItemStack capsule) {
        var data = capsule.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(CONTENTS)) {
            CompoundTag carried = data.copyTag().getCompound(CONTENTS);
            fill(level, chest, carried.getCompound("chest"));
            fill(level, furnace, carried.getCompound("furnace"));
        }
        House h = new House();
        h.owner = owner.getUUID();
        h.dim = level.dimension().location().toString();
        h.box = box;
        h.door = door;
        h.chest = chest;
        h.furnace = furnace;
        h.ground = ground;
        h.house = Shot.take(level, box);
        Data.get(level.getServer()).houses.add(h);
        Data.get(level.getServer()).setDirty();
        owner.displayClientMessage(Component.translatable("item.battlegroundsmod.capsula_hoipoi.como_guardar"), true);
    }

    private static void fill(ServerLevel level, BlockPos pos, CompoundTag tag) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && !tag.isEmpty()) {
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
        }
    }

    // ---------- packing ----------

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof DoorBlock)) {
            return;
        }
        Data data = Data.get(level.getServer());
        String dim = level.dimension().location().toString();
        for (var it = data.houses.iterator(); it.hasNext();) {
            House h = it.next();
            if (!h.dim.equals(dim) || !(pos.equals(h.door) || pos.equals(h.door.above()))) {
                continue;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!h.owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.translatable("item.battlegroundsmod.capsula_hoipoi.nao_e_sua"), true);
                return;
            }
            ItemStack capsule = pack(level, h);
            it.remove();
            data.setDirty();
            if (!player.getInventory().add(capsule)) {
                player.drop(capsule, false);
            }
            return;
        }
    }

    private static ItemStack pack(ServerLevel level, House h) {
        // what the house's chest and furnace hold goes into the capsule (only if they are still the house's own)
        CompoundTag carried = new CompoundTag();
        carried.put("chest", take(level, h, h.chest));
        carried.put("furnace", take(level, h, h.furnace));
        // whatever the player put inside (not a house block, not the ground's own) drops where it stood
        for (int i = 0; i < h.box.size(); i++) {
            BlockPos p = h.box.at(i);
            BlockState now = level.getBlockState(p);
            if (now.isAir() || now.getBlock() == h.house.state(i).getBlock() || now.getBlock() == h.ground.state(i).getBlock()) {
                continue;
            }
            level.destroyBlock(p, true);
        }
        // the ground comes back, quietly: first every block, then the block entities, then one update pass
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int i = 0; i < h.box.size(); i++) {
            BlockPos p = h.box.at(i);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c) {
                // a container that is still here is the house's (its things were taken) or the ground's (restored below)
                c.clearContent();
            }
            level.setBlock(p, h.ground.state(i), flags);
        }
        for (var e : h.ground.entities.entrySet()) {
            BlockEntity be = level.getBlockEntity(h.box.at(e.getKey()));
            if (be != null) {
                be.loadWithComponents(e.getValue(), level.registryAccess());
                be.setChanged();
            }
        }
        for (int i = 0; i < h.box.size(); i++) {
            BlockPos p = h.box.at(i);
            level.blockUpdated(p, h.ground.state(i).getBlock());
        }
        Vec3 mid = Vec3.atCenterOf(h.door);
        Fx.burst(level, ParticleTypes.CLOUD, mid.add(0, 1.5, 0), 160, 2.6, 0.12);
        Fx.burst(level, ParticleTypes.POOF, mid.add(0, 1.5, 0), 60, 2.2, 0.08);
        Fx.sound(level, mid, SoundEvents.PISTON_CONTRACT, 0.9f, 1.5f);
        Fx.sound(level, mid, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 1.8f);
        ItemStack capsule = new ItemStack(ModItems.HOI_POI_CAPSULE.get());
        if (!carried.getCompound("chest").isEmpty() || !carried.getCompound("furnace").isEmpty()) {
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, capsule,
                    tag -> tag.put(CONTENTS, carried));
        }
        return capsule;
    }

    // The house's chest or furnace, if it is still there: its saved contents, then emptied.
    private static CompoundTag take(ServerLevel level, House h, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || level.getBlockState(pos).getBlock() != houseState(h, pos).getBlock()) {
            return new CompoundTag();
        }
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        if (be instanceof Container c) {
            if (c.isEmpty()) {
                tag = new CompoundTag();
            }
            c.clearContent();
        }
        return tag;
    }

    private static BlockState houseState(House h, BlockPos p) {
        BlockPos d = p.subtract(h.box.min());
        return h.house.state(d.getX() + d.getZ() * h.box.sx() + d.getY() * h.box.sx() * h.box.sz());
    }

    // A house block broken by hand drops nothing (a container's things still drop).
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative()) {
            return;
        }
        BlockPos pos = event.getPos();
        String dim = level.dimension().location().toString();
        for (House h : Data.get(level.getServer()).houses) {
            if (h.dim.equals(dim) && h.isHouseBlock(level, pos)) {
                event.setCanceled(true);
                if (level.getBlockEntity(pos) instanceof Container c) {
                    Containers.dropContents(level, pos, c);
                }
                level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                return;
            }
        }
    }

    static final class Data extends SavedData {
        final List<House> houses = new ArrayList<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Data::new, Data::load, null), "battlegroundsmod_casas_capsula");
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("houses", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    d.houses.add(House.load(list.getCompound(i)));
                } catch (RuntimeException ignored) {
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (House h : houses) {
                list.add(h.save());
            }
            tag.put("houses", list);
            return tag;
        }
    }
}
