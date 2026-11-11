# Generates the "Souls Mod" advancement tab (data/battlegroundsmod/advancement/souls/*.json) and its lang keys.
# Stark 2026-11-11: "bota advancements ... roubar certas armas, conquistar certos tiers, conseguir o voo, o sage mode,
# a reversão, dominar o universo, entre outras". Criteria "impossible" are given by power/Achievements.java.
# Run from the repo root: python3 tools/advancements/souls_advancements.py
import json, os

ROOT = "src/main/resources"
OUT = f"{ROOT}/data/battlegroundsmod/advancement/souls"
B = "battlegroundsmod:"

# id, parent, icon, frame, criterion ("java" | dict of criteria), pt title, pt desc, en title, en desc, hidden
A = []
def adv(id, parent, icon, frame, crit, pt, ptd, en, end, hidden=False, any_of=False):
    A.append(dict(id=id, parent=parent, icon=icon, frame=frame, crit=crit, pt=pt, ptd=ptd, en=en, end=end, hidden=hidden, any_of=any_of))

def has(*items):
    return {"items": [{"items": i} for i in items]}

def inv(item):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}

J = "java"

adv("root", None, B + "power_book", "task", {"tick": {"trigger": "minecraft:tick"}},
    "Souls Mod", "Roube almas, suba de tier e domine o multiverso",
    "Souls Mod", "Steal souls, climb the tiers and rule the multiverse")

# Almas
adv("primeira_alma", "root", B + "soul_kuririn", "task", J,
    "Primeiro Roubo", "Absorva uma alma pela primeira vez",
    "First Theft", "Absorb a soul for the first time")
for id, power, pt, ptd, en, end in [
    ("alma_gojo", "strongest", "O Mais Forte", "Absorva a alma do Gojo", "The Strongest", "Absorb Gojo's soul"),
    ("alma_sukuna", "sukuna", "Rei das Maldições", "Absorva a alma do Sukuna", "King of Curses", "Absorb Sukuna's soul"),
    ("alma_kokushibo", "moon_demon", "Lua Superior Um", "Absorva a alma do Kokushibo", "Upper Moon One", "Absorb Kokushibo's soul"),
    ("alma_madara", "uchiha_ghost", "Fantasma Uchiha", "Absorva a alma do Madara", "Uchiha Ghost", "Absorb Madara's soul"),
    ("alma_bills", "destruction_god", "Deus da Destruição", "Absorva a alma do Bills", "God of Destruction", "Absorb Beerus' soul"),
    ("alma_aizen", "manipulator", "Tudo Conforme o Plano", "Absorva a alma do Aizen", "All According to Plan", "Absorb Aizen's soul"),
]:
    adv(id, "primeira_alma", B + "soul_" + power, "goal", J, pt, ptd, en, end)
adv("todas_divinas", "alma_bills", B + "soul_destruction_god", "challenge", J,
    "Colecionador de Deuses", "Absorva as seis Almas Divinas: Gojo, Sukuna, Kokushibo, Madara, Bills e Aizen",
    "God Collector", "Absorb all six Divine Souls: Gojo, Sukuna, Kokushibo, Madara, Beerus and Aizen")

# Almas Divinas derrotadas (Stark 2026-11-11): as conquistas principais do mod; todas = zerar.
# Só os bosses que já existem; Aizen, Luffy, Saitama, Shigaraki entram quando tiverem boss (e em power/Achievements).
DIVINE_BOSSES = [
    ("madara", "uchiha_ghost", "Madara", "Madara"),
    ("gojo", "strongest", "Gojo", "Gojo"),
    ("sukuna", "sukuna", "Sukuna", "Sukuna"),
    ("kokushibo", "moon_demon", "Kokushibo", "Kokushibo"),
    ("bills", "destruction_god", "Bills", "Beerus"),
]
adv("almas_divinas", "root", "minecraft:nether_star", "goal", J,
    "Caçador de Deuses", "Derrote sua primeira Alma Divina",
    "God Hunter", "Defeat your first Divine Soul")
for id, power, pt, en in DIVINE_BOSSES:
    adv("derrotar_" + id, "almas_divinas", B + "soul_" + power, "challenge", J,
        f"Derrote {pt}", f"Vença a Alma Divina de {pt}",
        f"Defeat {en}", f"Beat {en}'s Divine Soul")
adv("devorador_de_deuses", "derrotar_bills", "minecraft:dragon_egg", "challenge", J,
    "Devorador de Deuses", "Derrote todas as Almas Divinas. Você zerou o Souls",
    "Devourer of Gods", "Defeat every Divine Soul. You beat Souls")

# Armas (roubadas dos desafios e dos universos): any one opens the branch.
WEAPONS = [
    ("espada_z", "espada_z", "A Lenda da Espada Z", "Consiga a Espada Z", "Legend of the Z Sword", "Get the Z Sword"),
    ("bastao_magico", "bastao_magico", "Estica, Bastão!", "Consiga o Bastão Mágico", "Extend, Pole!", "Get the Power Pole"),
    ("nuvem_brincalhona", "nuvem_brincalhona", "Kinto'un", "Consiga a Nuvem Voadora", "Flying Nimbus", "Get the Flying Nimbus"),
    ("samehada", "samehada", "A Espada que Come Chakra", "Consiga a Samehada", "The Chakra Eater", "Get Samehada"),
    ("kusanagi", "kusanagi", "Lâmina da Serpente", "Consiga a Kusanagi", "Serpent Blade", "Get the Kusanagi"),
    ("dedo_sukuna", "dedo_sukuna", "Um dos Vinte", "Consiga um Dedo do Sukuna", "One of Twenty", "Get one of Sukuna's Fingers"),
    ("venda_gojo", "venda_gojo", "Olhos Vendados", "Consiga a Venda do Gojo", "Blindfolded", "Get Gojo's Blindfold"),
    ("lanca_invertida", "lanca_invertida", "Lança Celestial Invertida", "Consiga a Lança Invertida", "Inverted Spear of Heaven", "Get the Inverted Spear of Heaven"),
    ("zanpakuto", "zanpakuto", "Desperte, Zanpakutō", "Consiga uma Zanpakutō", "Awaken, Zanpakuto", "Get a Zanpakuto"),
    ("espada_nichirin", "espada_nichirin", "Lâmina Nichirin", "Consiga uma Espada Nichirin", "Nichirin Blade", "Get a Nichirin Sword"),
]
adv("primeira_arma", "root", B + "espada_z", "task",
    {w[0]: inv(B + w[1]) for w in WEAPONS},
    "Mão Leve", "Roube a arma de algum personagem",
    "Light Fingers", "Steal a character's weapon", any_of=True)
for id, item, pt, ptd, en, end in WEAPONS:
    adv("arma_" + id, "primeira_arma", B + item, "task", {"got": inv(B + item)}, pt, ptd, en, end)
adv("arsenal", "arma_espada_z", B + "lanca_invertida", "challenge",
    {w[0]: inv(B + w[1]) for w in WEAPONS},
    "Arsenal do Multiverso", "Tenha nas mãos, alguma vez, todas as armas lendárias",
    "Multiverse Arsenal", "Hold every legendary weapon at least once")

# Tiers
prev = "root"
for tier, icon, frame, pt, en, ptd, end in [
    (9, "minecraft:iron_ingot", "task", "Nível Rua", "Street Level", "Chegue ao Tier 9", "Reach Tier 9"),
    (7, "minecraft:gold_ingot", "task", "Nível Cidade", "City Level", "Chegue ao Tier 7", "Reach Tier 7"),
    (5, "minecraft:diamond", "goal", "Nível Planetário", "Planet Level", "Chegue ao Tier 5", "Reach Tier 5"),
    (3, "minecraft:nether_star", "goal", "Nível Galáctico", "Galaxy Level", "Chegue ao Tier 3", "Reach Tier 3"),
    (1, "minecraft:end_crystal", "goal", "Nível Multiversal", "Multiverse Level", "Chegue ao Tier 1", "Reach Tier 1"),
    (0, "minecraft:dragon_egg", "challenge", "Deus", "God", "Chegue ao Tier 0, o topo de tudo", "Reach Tier 0, the top of everything"),
]:
    adv(f"tier_{tier}", prev, icon, frame, J, pt, ptd, en, end)
    prev = f"tier_{tier}"

# Técnicas de universo
TECH = [
    ("voo", "minecraft:feather", "Céu Aberto", "Aprenda a voar no Dragon Ball", "Open Sky", "Learn to fly in Dragon Ball"),
    ("modo_sabio", "minecraft:lily_pad", "Modo Sábio", "Aprenda o Modo Sábio no Naruto", "Sage Mode", "Learn Sage Mode in Naruto"),
    ("reversao", "minecraft:enchanted_golden_apple", "Energia Reversa", "Aprenda a Técnica Reversa no Jujutsu", "Reverse Cursed Technique", "Learn the Reverse Cursed Technique in Jujutsu"),
    ("shunpo", "minecraft:phantom_membrane", "Passo Relâmpago", "Aprenda o Shunpo no Bleach", "Flash Step", "Learn Shunpo in Bleach"),
    ("marca_cacador", "minecraft:blaze_powder", "Marca do Caçador", "Desperte a Marca do Caçador no Demon Slayer", "Slayer Mark", "Awaken the Slayer Mark in Demon Slayer"),
]
for id, icon, pt, ptd, en, end in TECH:
    adv("tecnica_" + id, "root", icon, "goal", J, pt, ptd, en, end)
adv("todas_tecnicas", "tecnica_reversao", "minecraft:totem_of_undying", "challenge", J,
    "Mestre das Técnicas", "Aprenda as técnicas de todos os universos",
    "Technique Master", "Learn the techniques of every universe")

# Universos
adv("primeiro_desafio", "root", "minecraft:iron_sword", "task", J,
    "Desafiante", "Vença seu primeiro desafio do Livro dos Desafios",
    "Challenger", "Win your first challenge from the Book of Challenges")
UNIS = [
    ("dragonball", "Dragon Ball", "Dragon Ball"),
    ("naruto", "Naruto", "Naruto"),
    ("jujutsu", "Jujutsu Kaisen", "Jujutsu Kaisen"),
    ("bleach", "Bleach", "Bleach"),
    ("demonslayer", "Demon Slayer", "Demon Slayer"),
]
for id, pt, en in UNIS:
    adv("dominar_" + id, "primeiro_desafio", B + "livro_desafios_" + id, "goal", J,
        f"Dono do {pt}" if id != "dragonball" else "Dono do Dragon Ball",
        f"Domine o universo {pt}: vença todos os desafios do livro dele",
        f"{en} Conquered", f"Rule the {en} universe: beat every challenge in its book")
adv("multiverso", "dominar_dragonball", B + "cristal_multiversal", "challenge", J,
    "Senhor do Multiverso", "Domine todos os universos",
    "Lord of the Multiverse", "Rule every universe")

# Dragon Ball
adv("missoes_bulma", "raditz", B + "radar_dragao", "goal", J,
    "Aventura Completa", "Termine todas as missões da Bulma",
    "Adventure Complete", "Finish all of Bulma's missions")
adv("raditz", "root", B + "scouter", "task", J,
    "Nível de Poder: 1500", "Vença o Raditz quando ele chegar à Terra",
    "Power Level: 1500", "Defeat Raditz when he lands on Earth")
adv("sete_esferas", "raditz", B + "esfera_dragao_4", "goal",
    {f"e{n}": inv(f"{B}esfera_dragao_{n}") for n in range(1, 8)},
    "Sete Esferas", "Junte as sete Esferas do Dragão",
    "Seven Balls", "Gather all seven Dragon Balls")
adv("shenlong", "sete_esferas", "minecraft:dragon_head", "challenge", J,
    "Desejo Concedido", "Use as sete esferas e chame o Shenlong",
    "Wish Granted", "Use the seven balls to summon Shenlong")
adv("sala_tempo", "raditz", "minecraft:clock", "challenge", J,
    "Um Ano em Um Dia", "Fique o ano inteiro na Sala do Espírito e do Tempo",
    "A Year in a Day", "Stay the whole year in the Hyperbolic Time Chamber")
adv("namek", "raditz", "minecraft:lime_dye", "task",
    {"went": {"trigger": "minecraft:changed_dimension", "conditions": {"to": B + "namek"}}},
    "Planeta Verde", "Pise em Namekusei",
    "Green Planet", "Set foot on Namek")

def write():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".json"):
            os.remove(os.path.join(OUT, f))
    pt, en = {}, {}
    for a in A:
        key = f"advancements.battlegroundsmod.souls.{a['id']}"
        disp = {
            "icon": {"id": a["icon"]},
            "title": {"translate": key + ".title"},
            "description": {"translate": key + ".description"},
            "frame": a["frame"],
            "show_toast": True,
            "announce_to_chat": a["id"] != "root",
            "hidden": a["hidden"],
        }
        if a["parent"] is None:
            disp["background"] = "minecraft:textures/block/black_concrete.png"
        crit = a["crit"]
        if crit == J:
            crit = {"done": {"trigger": "minecraft:impossible"}}
        names = list(crit.keys())
        reqs = [names] if a["any_of"] else [[n] for n in names]
        data = {}
        if a["parent"]:
            data["parent"] = B + "souls/" + a["parent"]
        data.update({"display": disp, "criteria": crit, "requirements": reqs})
        with open(f"{OUT}/{a['id']}.json", "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        pt[key + ".title"], pt[key + ".description"] = a["pt"], a["ptd"]
        en[key + ".title"], en[key + ".description"] = a["en"], a["end"]
    for lang, add in (("pt_br", pt), ("en_us", en)):
        path = f"{ROOT}/assets/battlegroundsmod/lang/{lang}.json"
        with open(path, encoding="utf-8") as fh:
            d = json.load(fh)
        d = {k: v for k, v in d.items() if not k.startswith("advancements.battlegroundsmod.souls.")}
        d.update(add)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    print(len(A), "advancements")

write()
