package com.starkinhon.battlegroundsmod;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // Joy Boy (Luffy) boss (2026-11-12): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.JoyBoyEntity>> JOY_BOY = ENTITY_TYPES.register("joy_boy",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.JoyBoyEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("joy_boy"));

    // Saitama boss (2026-11-12): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.SaitamaEntity>> SAITAMA = ENTITY_TYPES.register("saitama",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.SaitamaEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("saitama"));

    // Hashirama boss (2026-11-13, end game, Vale do Fim): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.HashiramaEntity>> HASHIRAMA = ENTITY_TYPES.register("hashirama",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.HashiramaEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("hashirama"));

    // Freeza Poder Total boss (2026-11-13, end game): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.FreezaTotalEntity>> FREEZA_TOTAL = ENTITY_TYPES.register("freeza_total",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.FreezaTotalEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("freeza_total"));

    // Shigaraki boss (2026-11-12): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.ShigarakiEntity>> SHIGARAKI = ENTITY_TYPES.register("shigaraki",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.ShigarakiEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("shigaraki"));

    // Makima boss (2026-11-12): player sized.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.boss.MakimaEntity>> MAKIMA = ENTITY_TYPES.register("makima",
            () -> EntityType.Builder.of(com.starkinhon.battlegroundsmod.boss.MakimaEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("makima"));

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

    // What a ninja throws: Shuriken, Kunai, Papel-Bomba (item/NinjaThrown, Itens de Naruto 2026-11-12).
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.item.NinjaThrown>> NINJA_THROWN = ENTITY_TYPES.register("ninja_thrown",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.item.NinjaThrown>of(com.starkinhon.battlegroundsmod.item.NinjaThrown::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("ninja_thrown"));

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
    public static final Supplier<EntityType<SoulMob>> GOKU = mob("goku", 16);
    public static final Supplier<EntityType<SoulMob>> VEGETA = mob("vegeta", 16);
    public static final Supplier<EntityType<SoulMob>> FREEZA = mob("freeza", 16);
    public static final Supplier<EntityType<SoulMob>> PICCOLO = mob("piccolo", 16);
    public static final Supplier<EntityType<SoulMob>> KAME = mob("kame");
    public static final Supplier<EntityType<SoulMob>> KURIRIN = mob("kuririn", 16);
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
    // The Dragon Ball creatures (Stark 2026-11-12, preview tools/mobs/dbmobs approved): one entity type per kind, all one
    // class (animal/dbmob/DbMob, its kind from the id), the same framework as the Naruto ones. The old ids stay, so the
    // worlds keep their spawn eggs and the structures their mobs.
    public static final Map<String, Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>>> DB_MOBS = new LinkedHashMap<>();

    static {
        for (com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec spec : com.starkinhon.battlegroundsmod.animal.dbmob.DbMobSpec.all()) {
            float w = spec.width, h = spec.height;
            final float fw = w, fh = h;
            DB_MOBS.put(spec.id, ENTITY_TYPES.register(spec.id, () -> EntityType.Builder
                    .<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>of(com.starkinhon.battlegroundsmod.animal.dbmob.DbMob::new, MobCategory.MONSTER)
                    .sized(fw, fh).eyeHeight(fh * 0.85f).clientTrackingRange(spec.height > 3 || spec.width > 1.5f ? 12 : 10).build(spec.id)));
        }
    }

    public static EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob> db(String id) {
        return DB_MOBS.get(id).get();
    }

    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> SOLDADO_RED_RIBBON = DB_MOBS.get("soldado_red_ribbon");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> SAIBAMAN = DB_MOBS.get("saibaman");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> SOLDADO_FREEZA = DB_MOBS.get("soldado_freeza");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> BANDIDO_DESERTO = DB_MOBS.get("bandido_deserto");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> PIRATA = DB_MOBS.get("pirata");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> YETI = DB_MOBS.get("yeti");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> TIRANOSSAURO = DB_MOBS.get("tiranossauro");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> RAPTOR = DB_MOBS.get("raptor");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> PTERODACTILO = DB_MOBS.get("pterodactilo");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> PEIXE_GIGANTE = DB_MOBS.get("peixe_gigante");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> CELL_JR = DB_MOBS.get("cell_jr");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.dbmob.DbMob>> YAKON = DB_MOBS.get("yakon");

    // The Dragon Ball biomes' own creatures (Stark 2026-11-11, prévia mobs-db aprovada; animal/db): the Namekians of
    // Namek, the giant fish of the Mar do Sul, Android 8 in the Norte Gelado, the Bear Thief in Paozu, Bora by Karin.
    // Shibuya's pedestrians (npc/Pedestre, PedestreCrowd): the player's size, never saved.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.Pedestre>> PEDESTRE = ENTITY_TYPES.register("pedestre",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.npc.Pedestre>of(com.starkinhon.battlegroundsmod.npc.Pedestre::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(8).updateInterval(2).noSave().build("pedestre"));
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.db.Namekiano>> NAMEKIANO = ENTITY_TYPES.register("namekiano",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.animal.db.Namekiano>of(com.starkinhon.battlegroundsmod.animal.db.Namekiano::new, MobCategory.MONSTER)
                    .sized(0.85f, 2.2f).eyeHeight(1.95f).clientTrackingRange(10).build("namekiano"));
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.db.Androide8>> ANDROIDE_8 = ENTITY_TYPES.register("androide_8",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.animal.db.Androide8>of(com.starkinhon.battlegroundsmod.animal.db.Androide8::new, MobCategory.MONSTER)
                    .sized(1.6f, 3.1f).eyeHeight(2.8f).clientTrackingRange(12).build("androide_8"));
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.db.LadraoUrso>> LADRAO_URSO = ENTITY_TYPES.register("ladrao_urso",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.animal.db.LadraoUrso>of(com.starkinhon.battlegroundsmod.animal.db.LadraoUrso::new, MobCategory.MONSTER)
                    .sized(1.5f, 2.9f).eyeHeight(2.5f).clientTrackingRange(10).build("ladrao_urso"));
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.db.Bora>> BORA = ENTITY_TYPES.register("bora",
            () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.animal.db.Bora>of(com.starkinhon.battlegroundsmod.animal.db.Bora::new, MobCategory.MONSTER)
                    .sized(1.0f, 2.7f).eyeHeight(2.45f).clientTrackingRange(10).build("bora"));

    // The Dragon Ball start (Stark 2026-11-10): Raditz's opening fight, Babidi's guardians (the Cell Jr. and Yakon are DB_MOBS).
    public static final Supplier<EntityType<SoulMob>> RADITZ = mob("raditz");
    public static final Supplier<EntityType<SoulMob>> PUI_PUI = mob("pui_pui");
    public static final Supplier<EntityType<SoulMob>> DABURA = mob("dabura");

    // Common enemies of Naruto.
    // The Naruto biomes' creatures (Stark 2026-11-12, preview tools/mobs/naruto approved): one entity type per kind, all
    // one class (animal/naruto/NarutoMob, its kind from the id). The renegade and the Sound ninja keep their old ids.
    public static final Map<String, Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob>>> NARUTO_MOBS = new LinkedHashMap<>();

    static {
        for (com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec spec : com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.all()) {
            float w = spec.width, h = spec.height;
            if (spec.scale != null) {
                float k = 0;
                for (float v : spec.scale) {
                    k = Math.max(k, v);
                }
                w *= k;
                h *= k;
            }
            final float fw = w, fh = h;
            NARUTO_MOBS.put(spec.id, ENTITY_TYPES.register(spec.id, () -> EntityType.Builder
                    .<com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob>of(com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob::new,
                            MobCategory.MONSTER)
                    .sized(fw, fh).eyeHeight(fh * 0.85f).clientTrackingRange(spec.height > 3 || spec.width > 1.5f ? 12 : 10).build(spec.id)));
        }
    }

    public static EntityType<com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob> naruto(String id) {
        return NARUTO_MOBS.get(id).get();
    }

    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob>> NINJA_RENEGADO = NARUTO_MOBS.get("ninja_renegado");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob>> NINJA_SOM = NARUTO_MOBS.get("ninja_som");
    public static final Supplier<EntityType<SoulMob>> ANBU_RAIZ = mob("anbu_raiz");

    // The Jujutsu biomes' curses (Stark 2026-11-14, preview tools/mobs/jjmobs approved: "porta"): one entity type per kind
    // ("jj_" + the preview's id), all one class (animal/jjmob/JjMob, its kind from the id), on the Naruto creatures' body.
    public static final Map<String, Supplier<EntityType<com.starkinhon.battlegroundsmod.animal.jjmob.JjMob>>> JJ_MOBS = new LinkedHashMap<>();

    static {
        for (com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec spec : com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec.all()) {
            final float fw = spec.width, fh = spec.height;
            JJ_MOBS.put(spec.id, ENTITY_TYPES.register(spec.id, () -> EntityType.Builder
                    .<com.starkinhon.battlegroundsmod.animal.jjmob.JjMob>of(com.starkinhon.battlegroundsmod.animal.jjmob.JjMob::new, MobCategory.MONSTER)
                    .sized(fw, fh).eyeHeight(fh * 0.85f).clientTrackingRange(spec.height > 3 || spec.width > 1.5f ? 12 : 10).build(spec.id)));
        }
    }

    // Common enemies of Jujutsu.
    public static final Supplier<EntityType<SoulMob>> MALDICAO_MENOR = mob("maldicao_menor");
    public static final Supplier<EntityType<SoulMob>> MALDICAO_GRAU2 = mob("maldicao_grau2");
    public static final Supplier<EntityType<SoulMob>> FEITICEIRO_RENEGADO = mob("feiticeiro_renegado");

    // Common enemies of Bleach (Hueco Mundo) and Demon Slayer.
    public static final Supplier<EntityType<SoulMob>> HOLLOW = mob("hollow");
    public static final Supplier<EntityType<SoulMob>> ARRANCAR = mob("arrancar");
    public static final Supplier<EntityType<SoulMob>> MENOS_GRANDE = mob("menos_grande");
    public static final Supplier<EntityType<SoulMob>> ONI_COMUM = mob("oni_comum");
    public static final Supplier<EntityType<SoulMob>> ONI_LUA_INFERIOR = mob("oni_lua_inferior");
    public static final Supplier<EntityType<SoulMob>> ONI_ESPADACHIM = mob("oni_espadachim");

    public static final java.util.List<Supplier<EntityType<SoulMob>>> FIGHTERS = java.util.List.of(FIGHTER_HUMANO, FIGHTER_RUA, FIGHTER_QUARTEIRAO,
            ANBU_RAIZ,
            MALDICAO_MENOR, MALDICAO_GRAU2, FEITICEIRO_RENEGADO, HOLLOW, ARRANCAR, MENOS_GRANDE,
            ONI_COMUM, ONI_LUA_INFERIOR, ONI_ESPADACHIM, RADITZ, PUI_PUI, DABURA);

    // Friendly NPCs of the universes (npc/UniverseNpc): they teach, give and talk, never fight.
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_GOHAN = npc("npc_gohan");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_KORIN = npc("npc_korin");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_BULMA = npc("npc_bulma");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_MESTRE_KAME = npc("npc_mestre_kame");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_BOO = npc("npc_boo");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_JIRAIYA = npc("npc_jiraiya");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_TEUCHI = npc("npc_teuchi");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_SHOKO = npc("npc_shoko");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_YAGA = npc("npc_yaga");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_YORUICHI = npc("npc_yoruichi");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_URAHARA = npc("npc_urahara");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_UROKODAKI = npc("npc_urokodaki");
    public static final Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> NPC_TAMAYO = npc("npc_tamayo");

    private static Supplier<EntityType<com.starkinhon.battlegroundsmod.npc.UniverseNpc>> npc(String id) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.<com.starkinhon.battlegroundsmod.npc.UniverseNpc>of(
                        com.starkinhon.battlegroundsmod.npc.UniverseNpc::new, MobCategory.CREATURE)
                .sized(0.6f, 1.8f)
                .eyeHeight(1.62f)
                .clientTrackingRange(10)
                .build(id));
    }

    private static Supplier<EntityType<SoulMob>> mob(String id) {
        return mob(id, 10);
    }

    // The Dragon Ball souls fly across the sky far off (universe/SkyPassers): seen from further away.
    private static Supplier<EntityType<SoulMob>> mob(String id, int trackingChunks) {
        return ENTITY_TYPES.register(id, () -> EntityType.Builder.<SoulMob>of(SoulMob::new, MobCategory.MONSTER)
                .sized(0.6f, 1.8f)
                .eyeHeight(1.62f)
                .clientTrackingRange(trackingChunks)
                // fast in the air (up to 62 blocks a second): every tick, or the client sees them jump
                .updateInterval(trackingChunks > 10 ? 1 : 3)
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
        event.put(JOY_BOY.get(), SoulBoss.createAttributes().build());
        event.put(SAITAMA.get(), SoulBoss.createAttributes().build());
        event.put(SHIGARAKI.get(), SoulBoss.createAttributes().build());
        event.put(HASHIRAMA.get(), SoulBoss.createAttributes().build());
        event.put(FREEZA_TOTAL.get(), SoulBoss.createAttributes().build());
        event.put(MAKIMA.get(), SoulBoss.createAttributes().build());
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
        event.put(PEDESTRE.get(), com.starkinhon.battlegroundsmod.npc.Pedestre.createAttributes().build());
        event.put(NAMEKIANO.get(), com.starkinhon.battlegroundsmod.animal.db.Namekiano.attributes().build());
        event.put(ANDROIDE_8.get(), com.starkinhon.battlegroundsmod.animal.db.Androide8.attributes().build());
        event.put(LADRAO_URSO.get(), com.starkinhon.battlegroundsmod.animal.db.LadraoUrso.attributes().build());
        event.put(BORA.get(), com.starkinhon.battlegroundsmod.animal.db.Bora.attributes().build());
        for (var e : DB_MOBS.entrySet()) {
            event.put(e.getValue().get(), com.starkinhon.battlegroundsmod.animal.dbmob.DbMob
                    .attributes(com.starkinhon.battlegroundsmod.animal.dbmob.DbMobSpec.get(e.getKey())).build());
        }
        for (var e : NARUTO_MOBS.entrySet()) {
            event.put(e.getValue().get(), com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob
                    .attributes(com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.get(e.getKey())).build());
        }
        for (var e : JJ_MOBS.entrySet()) {
            event.put(e.getValue().get(), com.starkinhon.battlegroundsmod.animal.jjmob.JjMob
                    .attributes(com.starkinhon.battlegroundsmod.animal.jjmob.JjMobSpec.get(e.getKey())).build());
        }
        event.put(NPC_GOHAN.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_KORIN.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_BULMA.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_MESTRE_KAME.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_BOO.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_JIRAIYA.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_TEUCHI.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_SHOKO.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_YAGA.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_YORUICHI.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_URAHARA.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_UROKODAKI.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
        event.put(NPC_TAMAYO.get(), com.starkinhon.battlegroundsmod.npc.UniverseNpc.createAttributes().build());
    }
}
