package com.starkinhon.soulsjjk;

import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

// Stub: the Souls power book is not in the JujutsuCraft addon (JJC's own book picks the technique).
public final class ModItems {
    private ModItems() {
    }

    public static final Supplier<Item> POWER_BOOK = () -> Items.BOOK;
}
