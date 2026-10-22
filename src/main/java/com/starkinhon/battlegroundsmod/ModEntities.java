package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.boss.MadaraEntity;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BattlegroundsMod.MODID);

    // Player sized, fire proof (he is the fire user).
    public static final Supplier<EntityType<MadaraEntity>> MADARA = ENTITY_TYPES.register("madara",
            () -> EntityType.Builder.of(MadaraEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("madara"));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MADARA.get(), SoulBoss.createAttributes().build());
    }
}
