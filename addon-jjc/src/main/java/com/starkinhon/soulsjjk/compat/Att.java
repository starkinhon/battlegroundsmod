package com.starkinhon.soulsjjk.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import com.mojang.serialization.Dynamic;
import com.starkinhon.soulsjjk.SoulsJjk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Per-entity data (NeoForge attachments). Kept in memory per entity; the ones with a codec are written into the
// entity's persistent NBT when the player is saved and read back the first time they are asked for.
@Mod.EventBusSubscriber(modid = SoulsJjk.MODID)
public final class Att {
    private static final String ROOT = SoulsJjk.MODID + "_att";
    private static final Map<Entity, Map<AttachmentType<?>, Object>> DATA = new WeakHashMap<>();
    private static final List<Named> TYPES = new ArrayList<>();

    private record Named(String name, Supplier<? extends AttachmentType<?>> type) {
    }

    private Att() {
    }

    // Registers a type under a name (the save key).
    public static <T> Supplier<AttachmentType<T>> register(String name, Supplier<AttachmentType<T>> factory) {
        AttachmentType<T> type = factory.get();
        type.name = name;
        Supplier<AttachmentType<T>> supplier = () -> type;
        TYPES.add(new Named(name, supplier));
        return supplier;
    }

    private static synchronized Map<AttachmentType<?>, Object> map(Entity entity) {
        return DATA.computeIfAbsent(entity, e -> new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Entity entity, Supplier<AttachmentType<T>> supplier) {
        AttachmentType<T> type = supplier.get();
        Map<AttachmentType<?>, Object> map = map(entity);
        Object value = map.get(type);
        if (value == null) {
            value = load(entity, type);
            if (value == null) {
                value = type.defaults.get();
            }
            map.put(type, value);
        }
        return (T) value;
    }

    public static <T> boolean has(Entity entity, Supplier<AttachmentType<T>> supplier) {
        AttachmentType<T> type = supplier.get();
        return map(entity).containsKey(type) || entity.getPersistentData().getCompound(ROOT).contains(type.name);
    }

    public static <T> T set(Entity entity, Supplier<AttachmentType<T>> supplier, T value) {
        T old = map(entity).containsKey(supplier.get()) ? get(entity, supplier) : null;
        map(entity).put(supplier.get(), value);
        return old;
    }

    public static <T> T remove(Entity entity, Supplier<AttachmentType<T>> supplier) {
        @SuppressWarnings("unchecked")
        T old = (T) map(entity).remove(supplier.get());
        entity.getPersistentData().getCompound(ROOT).remove(supplier.get().name);
        return old;
    }

    private static <T> T load(Entity entity, AttachmentType<T> type) {
        if (type.codec == null) {
            return null;
        }
        CompoundTag root = entity.getPersistentData().getCompound(ROOT);
        if (!root.contains(type.name)) {
            return null;
        }
        return type.codec.parse(new Dynamic<>(NbtOps.INSTANCE, root.get(type.name))).result().orElse(null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void save(Entity entity) {
        Map<AttachmentType<?>, Object> map = DATA.get(entity);
        if (map == null) {
            return;
        }
        CompoundTag root = entity.getPersistentData().getCompound(ROOT);
        for (Map.Entry<AttachmentType<?>, Object> e : map.entrySet()) {
            AttachmentType type = e.getKey();
            if (type.codec != null) {
                type.codec.encodeStart(NbtOps.INSTANCE, e.getValue()).result().ifPresent(tag -> root.put(type.name, (Tag) tag));
            }
        }
        entity.getPersistentData().put(ROOT, root);
    }

    @SubscribeEvent
    public static void onSave(PlayerEvent.SaveToFile event) {
        save(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        save(event.getEntity());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player old = event.getOriginal();
        old.reviveCaps();
        Map<AttachmentType<?>, Object> map = DATA.get(old);
        if (map != null) {
            for (Map.Entry<AttachmentType<?>, Object> e : map.entrySet()) {
                if (e.getKey().copyOnDeath || !event.isWasDeath()) {
                    map(event.getEntity()).put(e.getKey(), e.getValue());
                }
            }
        }
        save(old);
        CompoundTag root = old.getPersistentData().getCompound(ROOT);
        CompoundTag copy = new CompoundTag();
        for (String key : root.getAllKeys()) {
            for (Named n : TYPES) {
                if (n.name().equals(key) && (n.type().get().copyOnDeath || !event.isWasDeath())) {
                    copy.put(key, root.get(key).copy());
                }
            }
        }
        event.getEntity().getPersistentData().put(ROOT, copy);
        old.invalidateCaps();
    }
}
