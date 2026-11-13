# The new Jujutsu biomes' JSON (Stark 2026-11-13, "pode portar tudo"): each biome is the Templos Antigos' file (the same
# vanilla carvers, ores and springs) with its own look from the approved preview (sky, fog, water, grass, leaves), its
# ambient particles and sounds from the ambience doc, its animals, and its own feature in the vegetation step. Also the
# configured and placed feature, the universe_jujutsu tag and the biome's name. Rerunning writes the same files. Run from
# the repo root.
import copy
import json

D = "src/main/resources/data/battlegroundsmod/"
L = "src/main/resources/assets/battlegroundsmod/lang/"


def c(h):
    return int(h.lstrip("#"), 16)


def mob(t, w, lo, hi):
    return {"type": t, "maxCount": hi, "minCount": lo, "weight": w}


BIOMES = {
    "floresta_negra": dict(
        pt="Floresta Negra", en="Black Forest", temp=0.5, down=0.9, rain=True,
        sky="#34303f", fog="#2a2434", water="#0a0a10", water_fog="#050508", grass="#4a4636", foliage="#18201b",
        particle=("minecraft:ash", 0.012),
        loop="minecraft:ambient.soul_sand_valley.loop",
        additions=("minecraft:ambient.soul_sand_valley.additions", 0.0111),
        mood="minecraft:ambient.soul_sand_valley.mood",
        creature=[], ambient=[mob("minecraft:bat", 10, 8, 8)]),
    "aldeia_amaldicoada": dict(
        pt="Aldeia Amaldiçoada", en="Cursed Village", temp=0.8, down=0.9, rain=True,
        sky="#626a6a", fog="#8e958a", water="#2a3424", water_fog="#1a2018", grass="#5a6a3a", foliage="#46523a",
        particle=("minecraft:spore_blossom_air", 0.0015),
        additions=("minecraft:ambient.cave", 0.0011),
        creature=[mob("minecraft:frog", 10, 2, 5)], ambient=[mob("minecraft:bat", 10, 8, 8)]),
    "mar_de_dagon": dict(
        pt="Mar de Dagon", en="Dagon's Sea", temp=0.95, down=0.6, rain=True,
        sky="#3c8cdc", fog="#c4e4ee", water="#40e0c8", water_fog="#2a9e9a", grass="#6aa040", foliage="#3e8a2e",
        particle=("minecraft:white_ash", 0.0008),
        creature=[mob("minecraft:turtle", 5, 2, 5)], ambient=[mob("minecraft:bat", 10, 8, 8)],
        water_ambient=[mob("minecraft:tropical_fish", 25, 8, 8)], water_creature=[mob("minecraft:dolphin", 2, 1, 2)]),
    "floresta_de_hanami": dict(
        pt="Floresta de Hanami", en="Hanami's Forest", temp=0.85, down=0.9, rain=True,
        sky="#5a9ad8", fog="#cfe2c6", water="#3a7a8a", water_fog="#1a3a40", grass="#4e8a34", foliage="#367f2e",
        particle=("minecraft:spore_blossom_air", 0.004),
        creature=[mob("minecraft:rabbit", 6, 2, 3), mob("minecraft:fox", 4, 2, 3)], ambient=[mob("minecraft:bat", 10, 8, 8)]),
    "terras_do_jogo": dict(
        pt="Terras do Jogo", en="Jogo's Lands", temp=2.0, down=0.0, rain=False,
        sky="#6a4a3a", fog="#8a5a40", water="#6ab0b8", water_fog="#3a6a70", grass="#5a5040", foliage="#4a4030",
        particle=("minecraft:white_ash", 0.02),
        loop="minecraft:ambient.basalt_deltas.loop",
        additions=("minecraft:ambient.basalt_deltas.additions", 0.0111),
        mood="minecraft:ambient.basalt_deltas.mood",
        creature=[], ambient=[]),
    "outono_de_kyoto": dict(
        pt="Outono de Kyoto", en="Kyoto Autumn", temp=0.6, down=0.6, rain=True,
        sky="#6a9ad0", fog="#e8d0b4", water="#3a6a6a", water_fog="#1a3a3a", grass="#6a8a3a", foliage="#c8301e",
        particle=("minecraft:cherry_leaves", 0.003),
        creature=[mob("minecraft:fox", 6, 2, 3), mob("minecraft:rabbit", 4, 2, 3)], ambient=[mob("minecraft:bat", 10, 8, 8)]),
    "colonia_do_jogo_do_abate": dict(
        pt="Colônia do Jogo do Abate", en="Culling Game Colony", temp=0.8, down=0.4, rain=True,
        sky="#4a4a68", fog="#6e6276", water="#4a5a6a", water_fog="#1e242a", grass="#6a7040", foliage="#5a6a34",
        particle=("minecraft:white_ash", 0.006),
        creature=[mob("minecraft:cat", 3, 1, 2)], ambient=[mob("minecraft:bat", 10, 8, 8)]),
    "shibuya": dict(
        pt="Shibuya", en="Shibuya", temp=0.7, down=0.5, rain=True,
        sky="#6a88b8", fog="#b8bcc8", water="#3a5a6a", water_fog="#1a2a34", grass="#6a8a4a", foliage="#4f7a3a",
        particle=None,
        creature=[mob("minecraft:cat", 4, 1, 2)], ambient=[mob("minecraft:bat", 10, 8, 8)]),
}


def main():
    base = json.load(open(D + "worldgen/biome/templos_antigos.json"))
    tag = json.load(open(D + "tags/worldgen/biome/universe_jujutsu.json"))
    pt, en = json.load(open(L + "pt_br.json")), json.load(open(L + "en_us.json"))
    for name, b in BIOMES.items():
        j = copy.deepcopy(base)
        j["temperature"], j["downfall"], j["has_precipitation"] = b["temp"], b["down"], b["rain"]
        e = {"sky_color": c(b["sky"]), "fog_color": c(b["fog"]), "water_color": c(b["water"]),
             "water_fog_color": c(b["water_fog"]), "grass_color": c(b["grass"]), "foliage_color": c(b["foliage"])}
        if b.get("particle"):
            e["particle"] = {"options": {"type": b["particle"][0]}, "probability": b["particle"][1]}
        if b.get("loop"):
            e["ambient_sound"] = b["loop"]
        if b.get("additions"):
            e["additions_sound"] = {"sound": b["additions"][0], "tick_chance": b["additions"][1]}
        e["mood_sound"] = {"block_search_extent": 8, "offset": 2.0, "sound": b.get("mood", "minecraft:ambient.cave"), "tick_delay": 6000}
        j["effects"] = e
        j["spawners"]["creature"] = b["creature"]
        j["spawners"]["ambient"] = b["ambient"]
        j["spawners"]["water_ambient"] = b.get("water_ambient", [])
        j["spawners"]["water_creature"] = b.get("water_creature", [])
        veg = j["features"][9]
        j["features"][9] = [f if f != "battlegroundsmod:templos_antigos" else "battlegroundsmod:" + name for f in veg
                            if f != "minecraft:patch_grass_forest" or b.get("grass_patches", False)]
        with open(D + "worldgen/biome/" + name + ".json", "w") as f:
            json.dump(j, f, indent=2)
            f.write("\n")
        with open(D + "worldgen/configured_feature/" + name + ".json", "w") as f:
            f.write(json.dumps({"type": "battlegroundsmod:" + name, "config": {}}))
        with open(D + "worldgen/placed_feature/" + name + ".json", "w") as f:
            f.write(json.dumps({"feature": "battlegroundsmod:" + name, "placement": []}))
        if "battlegroundsmod:" + name not in tag["values"]:
            tag["values"].append("battlegroundsmod:" + name)
        pt["biome.battlegroundsmod." + name] = b["pt"]
        en["biome.battlegroundsmod." + name] = b["en"]
    with open(D + "tags/worldgen/biome/universe_jujutsu.json", "w") as f:
        json.dump(tag, f, indent=2)
        f.write("\n")
    for path, d in ((L + "pt_br.json", pt), (L + "en_us.json", en)):
        with open(path, "w") as f:
            json.dump(d, f, ensure_ascii=False, indent=2)
            f.write("\n")


if __name__ == "__main__":
    main()
