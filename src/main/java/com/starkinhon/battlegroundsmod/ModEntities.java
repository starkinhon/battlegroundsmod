package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.boss.BillsEntity;
import com.starkinhon.battlegroundsmod.boss.GojoEntity;
import com.starkinhon.battlegroundsmod.boss.MadaraEntity;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.dummy.AttackerDummy;
import com.starkinhon.battlegroundsmod.dummy.TrainingDummy;
import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenIllusion;

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

    // Test dummies: one only takes hits, the other also punches back on a steady rhythm.
    public static final Supplier<EntityType<TrainingDummy>> DUMMY = ENTITY_TYPES.register("dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("dummy"));

    public static final Supplier<EntityType<AttackerDummy>> DUMMY_ATTACKER = ENTITY_TYPES.register("dummy_attacker",
            () -> EntityType.Builder.<AttackerDummy>of(AttackerDummy::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("dummy_attacker"));

    // Kyoka Suigetsu's clone of Aizen (power/powers/aizen/KyokaSuigetsu).
    public static final Supplier<EntityType<AizenIllusion>> AIZEN_ILLUSION = ENTITY_TYPES.register("aizen_illusion",
            () -> EntityType.Builder.<AizenIllusion>of(AizenIllusion::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(16)
                    .build("aizen_illusion"));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MADARA.get(), SoulBoss.createAttributes().build());
        event.put(BILLS.get(), SoulBoss.createAttributes().build());
        event.put(GOJO.get(), SoulBoss.createAttributes().build());
        event.put(DUMMY.get(), TrainingDummy.createAttributes().build());
        event.put(DUMMY_ATTACKER.get(), TrainingDummy.createAttributes().build());
        event.put(AIZEN_ILLUSION.get(), AizenIllusion.createAttributes().build());
    }
}
