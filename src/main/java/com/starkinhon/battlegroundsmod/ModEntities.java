package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.boss.BillsEntity;
import com.starkinhon.battlegroundsmod.boss.GojoEntity;
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

    // God of Destruction: player sized, nothing burns him.
    public static final Supplier<EntityType<BillsEntity>> BILLS = ENTITY_TYPES.register("bills",
            () -> EntityType.Builder.of(BillsEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("bills"));

    // The Strongest (Gojo): player sized.
    public static final Supplier<EntityType<GojoEntity>> GOJO = ENTITY_TYPES.register("gojo",
            () -> EntityType.Builder.of(GojoEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("gojo"));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MADARA.get(), SoulBoss.createAttributes().build());
        event.put(BILLS.get(), SoulBoss.createAttributes().build());
        event.put(GOJO.get(), SoulBoss.createAttributes().build());
    }
}
