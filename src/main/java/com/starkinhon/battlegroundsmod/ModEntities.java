package com.starkinhon.battlegroundsmod;

import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.boss.BillsEntity;
import com.starkinhon.battlegroundsmod.boss.GojoEntity;
import com.starkinhon.battlegroundsmod.boss.KokushiboEntity;
import com.starkinhon.battlegroundsmod.boss.MadaraEntity;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.boss.SoulMob;
import com.starkinhon.battlegroundsmod.boss.SukunaEntity;
import com.starkinhon.battlegroundsmod.dummy.AttackerDummy;
import com.starkinhon.battlegroundsmod.dummy.TrainingDummy;
import com.starkinhon.battlegroundsmod.power.powers.aizen.AizenIllusion;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NarutoClone;

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

    // Upper Moon One (Kokushibo): player sized.
    public static final Supplier<EntityType<KokushiboEntity>> KOKUSHIBO = ENTITY_TYPES.register("kokushibo",
            () -> EntityType.Builder.of(KokushiboEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("kokushibo"));

    // King of Curses (Sukuna): player sized.
    public static final Supplier<EntityType<SukunaEntity>> SUKUNA = ENTITY_TYPES.register("sukuna",
            () -> EntityType.Builder.of(SukunaEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("sukuna"));

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

    // Naruto's shadow clones (power/powers/sub/nr/Naruto).
    public static final Supplier<EntityType<NarutoClone>> NARUTO_CLONE = ENTITY_TYPES.register("naruto_clone",
            () -> EntityType.Builder.<NarutoClone>of(NarutoClone::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .noSummon()
                    .clientTrackingRange(16)
                    .build("naruto_clone"));

    // Sukuna's Malevolent Shrine: static blocky model (domain/MalevolentShrine, /soul santuario).
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.domain.MalevolentShrine>> MALEVOLENT_SHRINE = ENTITY_TYPES.register("malevolent_shrine",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.domain.MalevolentShrine>of(com.starkinhon.battlegroundsmod.domain.MalevolentShrine::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("malevolent_shrine"));

    // SubAlma mobs: each fights with its SubAlma's skills (souls_bosses/<id>.json), as a plain monster.
    public static final Supplier<EntityType<SoulMob>> BYAKUYA = mob("byakuya");
    public static final Supplier<EntityType<SoulMob>> RUKIA = mob("rukia");
    public static final Supplier<EntityType<SoulMob>> GOKU = mob("goku");
    public static final Supplier<EntityType<SoulMob>> VEGETA = mob("vegeta");
    public static final Supplier<EntityType<SoulMob>> FREEZA = mob("freeza");
    public static final Supplier<EntityType<SoulMob>> PICCOLO = mob("piccolo");
    public static final Supplier<EntityType<SoulMob>> KAME = mob("kame");
    public static final Supplier<EntityType<SoulMob>> KURIRIN = mob("kuririn");
    public static final Supplier<EntityType<SoulMob>> NARUTO = mob("naruto");
    public static final Supplier<EntityType<SoulMob>> SASUKE = mob("sasuke");
    public static final Supplier<EntityType<SoulMob>> KAKASHI = mob("kakashi");
    public static final Supplier<EntityType<SoulMob>> GAARA = mob("gaara");
    public static final Supplier<EntityType<SoulMob>> SHIKAMARU = mob("shikamaru");
    public static final Supplier<EntityType<SoulMob>> INO = mob("ino");
    public static final Supplier<EntityType<SoulMob>> ITADORI = mob("itadori");
    public static final Supplier<EntityType<SoulMob>> CHOSO = mob("choso");
    public static final Supplier<EntityType<SoulMob>> NANAMI = mob("nanami");
    public static final Supplier<EntityType<SoulMob>> NOBARA = mob("nobara");
    public static final Supplier<EntityType<SoulMob>> TODO = mob("todo");
    public static final Supplier<EntityType<SoulMob>> INUMAKI = mob("inumaki");
    public static final Supplier<EntityType<SoulMob>> MIWA = mob("miwa");

    // Fighters: weak mobs with only the base combat, Tier 10 (human), 9 (street) and 8 (block).
    public static final Supplier<EntityType<SoulMob>> FIGHTER_HUMANO = mob("fighter_humano");
    public static final Supplier<EntityType<SoulMob>> FIGHTER_RUA = mob("fighter_rua");
    public static final Supplier<EntityType<SoulMob>> FIGHTER_QUARTEIRAO = mob("fighter_quarteirao");

    public static final java.util.List<Supplier<EntityType<SoulMob>>> SUB_MOBS = java.util.List.of(BYAKUYA, RUKIA, GOKU, VEGETA, FREEZA, PICCOLO, KAME, KURIRIN,
            NARUTO, SASUKE, KAKASHI, GAARA, SHIKAMARU, INO, ITADORI, CHOSO, NANAMI, NOBARA, TODO, INUMAKI, MIWA);
    // Common enemies of Dragon Ball (only spawn in its universe: padrao.desafios.json "mobs"), base combat only for now.
    public static final Supplier<EntityType<SoulMob>> SOLDADO_RED_RIBBON = mob("soldado_red_ribbon");
    public static final Supplier<EntityType<SoulMob>> SAIBAMAN = mob("saibaman");
    public static final Supplier<EntityType<SoulMob>> SOLDADO_FREEZA = mob("soldado_freeza");

    public static final java.util.List<Supplier<EntityType<SoulMob>>> FIGHTERS = java.util.List.of(FIGHTER_HUMANO, FIGHTER_RUA, FIGHTER_QUARTEIRAO,
            SOLDADO_RED_RIBBON, SAIBAMAN, SOLDADO_FREEZA);

    // Friendly NPCs of the universes (npc/UniverseNpc): they teach, give and talk, never fight.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_GOHAN = npc("npc_gohan");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_KORIN = npc("npc_korin");

    private static Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> npc(String id) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.npc.UniverseNpc>of(
                        com.starkinhon.battlegroundsmod.npc.UniverseNpc::new, MobCategory.CREATURE)
                .sized(0.6f, 1.8f)
                .eyeHeight(1.62f)
                .clientTrackingRange(10)
                .build(id));
    }

    private static Supplier<EntityType<SoulMob>> mob(String id) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.<SoulMob>of(SoulMob::new, MobCategory.MONSTER)
                .sized(0.6f, 1.8f)
                .eyeHeight(1.62f)
                .clientTrackingRange(10)
                .build(id));
    }

    private ModEntities() {
    }

    // Natural spawns (the biome modifiers in data/battlegroundsmod/neoforge/biome_modifier add them to the overworld):
    // on the ground, by day or night, never in peaceful.
    public static void onSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        for (Supplier<EntityType<SoulMob>> type : SUB_MOBS) {
            placement(event, type.get());
        }
        for (Supplier<EntityType<SoulMob>> type : FIGHTERS) {
            placement(event, type.get());
        }
    }

    private static void placement(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event, EntityType<SoulMob> type) {
        event.register(type, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkAnyLightMonsterSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MADARA.get(), SoulBoss.createAttributes().build());
        event.put(BILLS.get(), SoulBoss.createAttributes().build());
        event.put(GOJO.get(), SoulBoss.createAttributes().build());
        event.put(KOKUSHIBO.get(), SoulBoss.createAttributes().build());
        event.put(SUKUNA.get(), SoulBoss.createAttributes().build());
        event.put(DUMMY.get(), TrainingDummy.createAttributes().build());
        event.put(DUMMY_ATTACKER.get(), TrainingDummy.createAttributes().build());
        event.put(AIZEN_ILLUSION.get(), AizenIllusion.createAttributes().build());
        event.put(NARUTO_CLONE.get(), NarutoClone.createAttributes().build());
        for (Supplier<EntityType<SoulMob>> type : SUB_MOBS) {
            event.put(type.get(), SoulBoss.createAttributes().build());
        }
        for (Supplier<EntityType<SoulMob>> type : FIGHTERS) {
            event.put(type.get(), SoulBoss.createAttributes().build());
        }
        event.put(NPC_GOHAN.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_KORIN.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
    }
}
