# Generates the Souls advancement tabs (data/battlegroundsmod/advancement/<tab>/*.json) and their lang keys.
# Stark 2026-11-12 (thread "Refazer o sistema de conquistas", preview https://claude.ai/artifact/VjRj6PGgwMkdqUcu9iJyPJ):
# only POWER and WHO YOU BEAT, nothing of "first time you did X", and each kind in its OWN tab:
# - Almas: one branch per soul you have (hidden until you get it): the crystal, its skills as they awaken with
#   Sincronia (power/SoulSync), and "Sincronia Total" with the whole kit.
# - Alvos: one row per universe of the Livro dos Desafios, from the weakest to the Divine Soul; beating all of them
#   gives the universe. A Bestiário of the universe's common mobs hangs under each row (optional, not needed for it).
# - Técnicas: the ten tiers (9 to 0) and the universe techniques (flight, Sage Mode...).
# - Exploração: the universes' biomes, Namek, the seven balls and Shenlong, Bulma's missions, the Time Chamber and
#   the legendary weapons.
# Stark 2026-11-19: they had all gone into one "Souls Mod" tab; this makes the four tabs of the preview.
# Criteria "minecraft:impossible" (and the per-character / per-soul ones) are given by power/Achievements.java.
# Run from the repo root: python3 tools/advancements/souls_advancements.py
import json, os, shutil

ROOT = "src/main/resources"
DATA = f"{ROOT}/data/battlegroundsmod"
OUT = f"{DATA}/advancement"
B = "battlegroundsmod:"
TABS = ["almas", "alvos", "tiers", "tecnicas", "exploracao"]
LANG = {l: json.load(open(f"{ROOT}/assets/battlegroundsmod/lang/{l}.json", encoding="utf-8")) for l in ("pt_br", "en_us")}
ITEM_MODELS = set(f[:-5] for f in os.listdir(f"{ROOT}/assets/battlegroundsmod/models/item"))

A = []
TEXT = {"pt_br": {}, "en_us": {}}


def tr(key, *args):
    return {"translate": key, "with": list(args)} if args else {"translate": key}


def text(key, pt, en):
    TEXT["pt_br"][key], TEXT["en_us"][key] = pt, en
    return tr(key)


def adv(tab, id, parent, icon, frame, crit, title, desc, hidden=False, any_of=False, background=None):
    """title/desc: (pt, en) for a new lang key, or a ready text component (dict)."""
    key = f"advancements.battlegroundsmod.{tab}.{id}"
    t = title if isinstance(title, dict) else text(key + ".title", *title)
    d = desc if isinstance(desc, dict) else text(key + ".description", *desc)
    if icon.startswith(B):
        assert icon[len(B):] in ITEM_MODELS, "item sem modelo: " + icon
    A.append(dict(tab=tab, id=id, parent=parent, icon=icon, frame=frame, crit=crit, title=t, desc=d, hidden=hidden,
                  any_of=any_of, background=background))


J = "java"


def inv(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}


def impossible(names):
    return {n: {"trigger": "minecraft:impossible"} for n in names}


def biome(b):
    return {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"location": {"biomes": b}}}]}}


def entity_name(id):
    return tr(f"entity.battlegroundsmod.{id}")


def lang_has(key):
    return key in LANG["pt_br"]


DESAFIOS = json.load(open(f"{DATA}/souls_skills/padrao.desafios.json", encoding="utf-8"))
UNIS = [u for u in DESAFIOS["universos"] if u.get("mundo", True) is not False]


def boss(id):
    p = f"{DATA}/souls_bosses/{id}.json"
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}


# ======================= Almas =======================
# Souls with a crystal item, in the universes' order (weakest first), then the rest.
CRYSTALS = sorted(m[5:] for m in ITEM_MODELS if m.startswith("soul_") and m != "soul_insect")
order = []
for u in UNIS:
    for c in u["desafios"]:
        p = boss(c["id"]).get("poder")
        if p in CRYSTALS and p not in order:
            order.append(p)
order += [p for p in CRYSTALS if p not in order]
SUBS = {p for u in UNIS for c in u["desafios"] for p in [boss(c["id"]).get("poder")] if boss(c["id"]).get("tier", 0) >= 6}
KEY = ["Z", "X", "C", "V", "B", "Shift+Z", "Shift+X", "Shift+C", "Shift+V", "Shift+B"]
ULT_INDEX = {3, 8}

adv("almas", "root", None, B + "soul_kuririn", "task", {"tick": {"trigger": "minecraft:tick"}},
    ("Almas", "Souls"),
    ("Cada alma roubada abre um galho. Lute com ela pra subir a Sincronia e escolha qual skill despertar",
     "Every stolen soul opens a branch. Fight with it to raise its Sync and choose which skill to awaken"),
    background="minecraft:textures/block/crying_obsidian.png")
adv("almas", "colecionador", "root", B + "soul_destruction_god", "challenge", impossible(order),
    ("Colecionador de Almas", "Soul Collector"), ("Roube todas as almas do multiverso", "Steal every soul in the multiverse"))

SKILL_TITLE = text("advancements.battlegroundsmod.almas.skill", "[%s] %s", "[%s] %s")
SOUL_TITLE_SUB = "advancements.battlegroundsmod.almas.alma"
SOUL_TITLE_DIV = "advancements.battlegroundsmod.almas.alma_divina"
TEXT["pt_br"][SOUL_TITLE_SUB], TEXT["en_us"][SOUL_TITLE_SUB] = "Alma: %s", "Soul: %s"
TEXT["pt_br"][SOUL_TITLE_DIV], TEXT["en_us"][SOUL_TITLE_DIV] = "Alma Divina: %s", "Divine Soul: %s"
TOTAL_TITLE = "advancements.battlegroundsmod.almas.total"
TEXT["pt_br"][TOTAL_TITLE], TEXT["en_us"][TOTAL_TITLE] = "Sincronia Total: %s", "Full Sync: %s"
TOTAL_DESC = text("advancements.battlegroundsmod.almas.total.description",
                  "Desperte o kit inteiro dessa alma", "Awaken this soul's whole kit")
SKILLS = {}
for p in order:
    name = tr(f"power.battlegroundsmod.{p}")
    adv("almas", p, "root", B + "soul_" + p, "goal" if p in SUBS else "challenge", J,
        tr(SOUL_TITLE_SUB if p in SUBS else SOUL_TITLE_DIV, name),
        tr(f"power.battlegroundsmod.{p}.desc") if lang_has(f"power.battlegroundsmod.{p}.desc") else name, hidden=True)
    idx = []
    for i in range(10):
        k = f"ability.battlegroundsmod.{p}.{i}"
        v = LANG["pt_br"].get(k, "")
        if v and "(nenhuma)" not in v and "em breve" not in v.lower():
            idx.append(i)
    SKILLS[p] = idx
    prev = p
    for i in sorted(idx, key=lambda i: (i in ULT_INDEX, i)):
        k = f"ability.battlegroundsmod.{p}.{i}"
        adv("almas", f"{p}_{i}", prev, B + "soul_" + p, "goal" if i in ULT_INDEX else "task", J,
            tr(SKILL_TITLE["translate"], KEY[i], tr(k)), tr(k + ".desc") if lang_has(k + ".desc") else tr(k))
        prev = f"{p}_{i}"
    adv("almas", f"{p}_total", prev, B + "soul_" + p, "challenge", J, tr(TOTAL_TITLE, name), TOTAL_DESC)

# ======================= Alvos =======================
adv("alvos", "root", None, "minecraft:target", "task", {"tick": {"trigger": "minecraft:tick"}},
    ("Alvos", "Targets"),
    ("Todo personagem que você derrotar fica guardado aqui. Feche a fileira e o universo é seu",
     "Every character you beat is kept here. Close a row and the universe is yours"),
    background="minecraft:textures/block/nether_bricks.png")
BEAT_TITLE = "advancements.battlegroundsmod.alvos.vencer"
TEXT["pt_br"][BEAT_TITLE], TEXT["en_us"][BEAT_TITLE] = "Derrote %s", "Defeat %s"
BEAT_DESC = text("advancements.battlegroundsmod.alvos.vencer.description",
                 "Vença no mundo ou na arena do Livro dos Desafios", "Beat them in the world or in the Book of Challenges arena")
BEST_DESC = text("advancements.battlegroundsmod.alvos.bestiario.description",
                 "Derrote cada tipo de inimigo comum desse universo pelo menos uma vez",
                 "Defeat every kind of this universe's common enemies at least once")
UNI_NAMES = {"dragonball": ("Dragon Ball", "Dragon Ball"), "naruto": ("Naruto", "Naruto"),
             "jujutsu": ("Jujutsu Kaisen", "Jujutsu Kaisen"), "bleach": ("Bleach", "Bleach"), "demonslayer": ("Demon Slayer", "Demon Slayer")}
ALVOS = {}
DIVINE_TARGETS = []
for u in UNIS:
    uid = u["id"]
    pt, en = UNI_NAMES.get(uid, (u["nome"], u["nome"]))
    chars = [c["id"] for c in u["desafios"]]
    ALVOS[uid] = chars
    first = boss(chars[0]).get("poder") if chars else None
    icon = B + "soul_" + boss(chars[-1]).get("poder", "") if chars and boss(chars[-1]).get("poder") in CRYSTALS else "minecraft:nether_star"
    adv("alvos", uid, "root", icon, "challenge", impossible(chars),
        (f"Universo {pt}", f"{en} Universe"),
        (f"Derrote todos os personagens de {pt}, das SubAlmas à Alma Divina", f"Defeat every {en} character, from SubSouls to the Divine Soul"))
    prev = uid
    for c in chars:
        b = boss(c)
        p = b.get("poder")
        div = b.get("tier", 9) <= 5
        if div:
            DIVINE_TARGETS.append(c)
        egg = f"{c}_spawn_egg"
        icon = B + "soul_" + p if p in CRYSTALS else (B + egg if egg in ITEM_MODELS else "minecraft:player_head")
        adv("alvos", c, prev, icon, "challenge" if div else "goal", J, tr(BEAT_TITLE, entity_name(c)), BEAT_DESC)
        prev = c
    mobs = [m for m in u.get("mobs", []) if lang_has(f"entity.battlegroundsmod.{m}")]
    if mobs:
        adv("alvos", f"bestiario_{uid}", uid, "minecraft:skeleton_skull", "task", impossible(mobs),
            (f"Bestiário {pt}", f"{en} Bestiary"), BEST_DESC)
        ALVOS["bestiario_" + uid] = mobs
adv("alvos", "multiverso", "root", B + "cristal_multiversal", "challenge", impossible([u["id"] for u in UNIS]),
    ("Senhor do Multiverso", "Lord of the Multiverse"), ("Feche a fileira de todos os universos", "Close every universe's row"))
adv("alvos", "devorador_de_deuses", "multiverso", "minecraft:dragon_egg", "challenge", impossible(DIVINE_TARGETS),
    ("Devorador de Deuses", "Devourer of Gods"), ("Derrote todas as Almas Divinas. Você zerou o Souls", "Defeat every Divine Soul. You beat Souls"))
# The end-game bosses of the open world (Stark 2026-11-19): Hashirama at the Valley of the End, Freeza's ship; Toji in
# Shibuya (2026-11-22).
END_BOSSES = [("hashirama", "battlegroundsmod:hashirama_spawn_egg",
               ("Vença o Deus Shinobi no Vale do Fim", "Beat the God of Shinobi at the Valley of the End")),
              ("freeza_total", "battlegroundsmod:freeza_total_spawn_egg",
               ("Vença o Imperador quando a nave dele pousar", "Beat the Emperor when his ship lands")),
              # Toji Fushiguro (Stark 2026-11-22), hunting in Shibuya.
              ("toji", "battlegroundsmod:toji_spawn_egg",
               ("Vença o Assassino de Feiticeiros em Shibuya", "Beat the Sorcerer Killer in Shibuya"))]
adv("alvos", "fim_de_jogo", "devorador_de_deuses", "minecraft:nether_star", "challenge", impossible([b[0] for b in END_BOSSES]),
    ("Os Três Mais Fortes", "The Three Strongest"), ("Derrote Hashirama, Freeza no Poder Total e Toji", "Defeat Hashirama, Full Power Freeza and Toji"))
for c, icon, desc in END_BOSSES:
    adv("alvos", c, "fim_de_jogo", icon, "challenge", J, tr(BEAT_TITLE, entity_name(c)), desc)
ALVOS["fim_de_jogo"] = [b[0] for b in END_BOSSES]

# ======================= Técnicas =======================
TIER_NAMES = [("Deus", "God"), ("Multiversal", "Multiversal"), ("Universal", "Universal"), ("Galáctico", "Galactic"),
              ("Estelar", "Stellar"), ("Planetário", "Planetary"), ("Continental", "Continental"), ("Cidade", "City"),
              ("Quarteirão", "Block"), ("Rua", "Street"), ("Humano", "Human")]
TIER_ICONS = {10: "minecraft:leather", 9: "minecraft:iron_ingot", 8: "minecraft:iron_block", 7: "minecraft:gold_ingot",
              6: "minecraft:gold_block", 5: "minecraft:diamond", 4: "minecraft:amethyst_shard", 3: "minecraft:nether_star",
              2: "minecraft:ender_eye", 1: "minecraft:end_crystal", 0: "minecraft:dragon_egg"}
# Stark 2026-11-19: the tiers are the main straight line of their own tab (the tab is named after the tree, not after
# Tier 10), and the techniques get theirs: one chain per tab is the only way vanilla keeps the line straight.
adv("tiers", "root", None, TIER_ICONS[0], "task", {"tick": {"trigger": "minecraft:tick"}},
    ("Tiers", "Tiers"),
    ("Do Tier 10 (Humano) ao Tier 0 (Deus). Lute e roube almas pra encher a barra de Poder",
     "From Tier 10 (Human) to Tier 0 (God). Fight and steal souls to fill the Power bar"),
    background="minecraft:textures/block/deepslate_tiles.png")
adv("tecnicas", "root", None, "minecraft:totem_of_undying", "task", {"tick": {"trigger": "minecraft:tick"}},
    ("Técnicas", "Techniques"),
    ("As técnicas que cada universo ensina. Funcionam com qualquer alma",
     "The techniques each universe teaches. They work with any soul"),
    background="minecraft:textures/block/polished_blackstone_bricks.png")
prev = "root"
for t in range(9, -1, -1):
    frame = "challenge" if t in (5, 0) else "goal" if t in (7, 3, 1) else "task"
    pt, en = TIER_NAMES[t]
    adv("tiers", f"tier_{t}", prev, TIER_ICONS[t], frame, J, (f"Tier {t}: {pt}", f"Tier {t}: {en}"),
        (f"Chegue ao Tier {t}. +50 de vida e +5 de força" + (", o topo de tudo" if t == 0 else ""),
         f"Reach Tier {t}. +50 health and +5 strength" + (", the top of everything" if t == 0 else "")))
    prev = f"tier_{t}"
TECH = [
    ("voo", "minecraft:feather", "Céu Aberto", "Aprenda a voar no Dragon Ball", "Open Sky", "Learn to fly in Dragon Ball"),
    ("kaioh", "minecraft:grass_block", "Treino do Senhor Kaioh", "Termine o treino no planeta do Senhor Kaioh", "King Kai's Training", "Finish the training on King Kai's planet"),
    ("marca_majin", "minecraft:redstone", "Marca Majin", "Deixe o Babidi marcar você", "Majin Mark", "Let Babidi mark you"),
    ("modo_sabio", "minecraft:lily_pad", "Modo Sábio", "Aprenda o Modo Sábio no Naruto", "Sage Mode", "Learn Sage Mode in Naruto"),
    ("controle_energia", "minecraft:water_bucket", "Controle de Energia", "Aprenda com o Kakashi a andar na água e correr nas árvores", "Energy Control", "Learn from Kakashi to walk on water and run up trees"),
    ("reversao", "minecraft:enchanted_golden_apple", "Energia Reversa", "Aprenda a Técnica Reversa no Jujutsu", "Reverse Cursed Technique", "Learn the Reverse Cursed Technique in Jujutsu"),
    ("shunpo", "minecraft:phantom_membrane", "Passo Relâmpago", "Aprenda o Shunpo no Bleach", "Flash Step", "Learn Shunpo in Bleach"),
    ("marca_cacador", "minecraft:blaze_powder", "Marca do Caçador", "Desperte a Marca do Caçador no Demon Slayer", "Slayer Mark", "Awaken the Slayer Mark in Demon Slayer"),
]
for id, icon, pt, ptd, en, end in TECH:
    adv("tecnicas", "tecnica_" + id, "root", icon, "goal", J, (pt, en), (ptd + ". Funciona com qualquer alma", end + ". Works with any soul"))
adv("tecnicas", "todas_tecnicas", "tecnica_reversao", "minecraft:totem_of_undying", "challenge", impossible([t[0] for t in TECH]),
    ("Mestre das Técnicas", "Technique Master"), ("Aprenda as técnicas de todos os universos", "Learn the techniques of every universe"))

# ======================= Exploração =======================
adv("exploracao", "root", None, "minecraft:filled_map", "task", {"tick": {"trigger": "minecraft:tick"}},
    ("Exploração", "Exploration"), ("Os lugares, segredos e aventuras de cada universo", "Each universe's places, secrets and adventures"),
    background="minecraft:textures/block/coarse_dirt.png")
BIOME_TITLES = {"dragonball": ("Mochileiro do Dragon Ball", "Dragon Ball Backpacker", "minecraft:orange_terracotta"),
                "naruto": ("Andarilho Ninja", "Ninja Wanderer", "minecraft:oak_sapling"),
                "jujutsu": ("Turista Amaldiçoado", "Cursed Tourist", "minecraft:crimson_fungus")}
for uid, (pt, en, icon) in BIOME_TITLES.items():
    p = f"{DATA}/tags/worldgen/biome/universe_{uid}.json"
    if not os.path.exists(p):
        continue
    biomes = json.load(open(p))["values"]
    upt, uen = UNI_NAMES[uid]
    adv("exploracao", f"biomas_{uid}", "root", icon, "goal", {b.split(":")[1]: biome(b) for b in biomes}, (pt, en),
        (f"Pise em todos os biomas de {upt}", f"Set foot in every {uen} biome"))
adv("exploracao", "namek", "biomas_dragonball", "minecraft:lime_dye", "task",
    {"went": {"trigger": "minecraft:changed_dimension", "conditions": {"to": B + "namek"}}},
    ("Planeta Verde", "Green Planet"), ("Pise em Namekusei", "Set foot on Namek"))
adv("exploracao", "sete_esferas", "root", B + "esfera_dragao_4", "goal",
    {f"e{n}": inv(f"{B}esfera_dragao_{n}") for n in range(1, 8)},
    ("Sete Esferas", "Seven Balls"), ("Junte as sete Esferas do Dragão", "Gather all seven Dragon Balls"))
adv("exploracao", "shenlong", "sete_esferas", "minecraft:dragon_head", "challenge", J,
    ("Desejo Concedido", "Wish Granted"), ("Use as sete esferas e chame o Shenlong", "Use the seven balls to summon Shenlong"))
adv("exploracao", "missoes_bulma", "root", B + "radar_dragao", "goal", J,
    ("Aventura Completa", "Adventure Complete"), ("Termine todas as missões da Bulma", "Finish all of Bulma's missions"))
adv("exploracao", "sala_tempo", "missoes_bulma", "minecraft:clock", "challenge", J,
    ("Um Ano em Um Dia", "A Year in a Day"), ("Fique o ano inteiro na Sala do Espírito e do Tempo", "Stay the whole year in the Hyperbolic Time Chamber"))
WEAPONS = [w for w in ["nuvem_brincalhona", "gunbai", "kusanagi", "dedo_sukuna", "venda_gojo", "lanca_invertida", "zanpakuto", "espada_nichirin"]
           if w in ITEM_MODELS]
adv("exploracao", "arsenal", "root", B + "lanca_invertida", "challenge", {w: inv(B + w) for w in WEAPONS},
    ("Arsenal do Multiverso", "Multiverse Arsenal"), ("Tenha nas mãos, alguma vez, todas as armas lendárias", "Hold every legendary weapon at least once"))


def write():
    for tab in TABS + ["souls"]:
        shutil.rmtree(f"{OUT}/{tab}", ignore_errors=True)
    for a in A:
        disp = {"icon": {"id": a["icon"]}, "title": a["title"], "description": a["desc"], "frame": a["frame"],
                "show_toast": a["id"] != "root", "announce_to_chat": a["frame"] == "challenge", "hidden": a["hidden"]}
        if a["background"]:
            disp["background"] = a["background"]
        crit = {"done": {"trigger": "minecraft:impossible"}} if a["crit"] == J else a["crit"]
        names = list(crit.keys())
        data = {}
        if a["parent"]:
            data["parent"] = f"{B}{a['tab']}/{a['parent']}"
        data.update({"display": disp, "criteria": crit, "requirements": [names] if a["any_of"] else [[n] for n in names]})
        os.makedirs(f"{OUT}/{a['tab']}", exist_ok=True)
        with open(f"{OUT}/{a['tab']}/{a['id']}.json", "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    for lang in ("pt_br", "en_us"):
        path = f"{ROOT}/assets/battlegroundsmod/lang/{lang}.json"
        d = {k: v for k, v in LANG[lang].items() if not any(k.startswith(f"advancements.battlegroundsmod.{t}.") for t in TABS + ["souls"])}
        d.update(TEXT[lang])
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    # What Achievements.java needs to know, so the two never drift apart.
    meta = {"souls": order, "skills": SKILLS, "alvos": ALVOS, "divinas": DIVINE_TARGETS, "tecnicas": [t[0] for t in TECH]}
    with open(f"{DATA}/souls_skills/conquistas.json", "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print(len(A), "advancements em", TABS)


write()
