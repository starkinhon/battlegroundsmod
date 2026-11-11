# Writes the Dragon Ball biomes of the normal overworld (Stark 2026-11-11: os universos viram biomas no mundo normal,
# "ambiente incrível"): Terras do Dragão (green plains full of rock pillars), Picos do Dragão (striped mountains) and
# Deserto Vermelho (red sand). Feature lists start from vanilla 1.21.1 savanna/badlands/desert (data from the game jar);
# no vanilla monsters or animals: only the universe's own (universe/UniverseSpawns). The pillars are
# battlegroundsmod:dragon_pillar_* (world/DragonPillarFeature). Run from the repo root.
import json, os

R = "src/main/resources/data/battlegroundsmod"
ORES = ["minecraft:ore_dirt","minecraft:ore_gravel","minecraft:ore_granite_upper","minecraft:ore_granite_lower",
    "minecraft:ore_diorite_upper","minecraft:ore_diorite_lower","minecraft:ore_andesite_upper","minecraft:ore_andesite_lower",
    "minecraft:ore_tuff","minecraft:ore_coal_upper","minecraft:ore_coal_lower","minecraft:ore_iron_upper",
    "minecraft:ore_iron_middle","minecraft:ore_iron_small","minecraft:ore_gold","minecraft:ore_gold_lower",
    "minecraft:ore_redstone","minecraft:ore_redstone_lower","minecraft:ore_diamond","minecraft:ore_diamond_medium",
    "minecraft:ore_diamond_large","minecraft:ore_diamond_buried","minecraft:ore_lapis","minecraft:ore_lapis_buried",
    "minecraft:ore_copper","minecraft:underwater_magma","minecraft:disk_sand","minecraft:disk_clay","minecraft:disk_gravel"]

# The extra ores go before the soft disks (sand, clay, gravel), as in vanilla badlands: the features of a step must keep
# the same order in every biome, or the game stops with "Feature order cycle found".
def features(surface, vegetal, extra_ores=()):
    ores = ORES[:-3] + list(extra_ores) + ORES[-3:]
    return [[], ["minecraft:lake_lava_underground", "minecraft:lake_lava_surface"], ["minecraft:amethyst_geode"],
            ["minecraft:monster_room", "minecraft:monster_room_deep"], surface, [], ores, [],
            ["minecraft:spring_water", "minecraft:spring_lava"], ["minecraft:glow_lichen"] + vegetal,
            ["minecraft:freeze_top_layer"]]

def biome(name, temp, sky, fog, water, water_fog, grass, foliage, feats, music=None):
    effects = {"sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog,
               "grass_color": grass, "foliage_color": foliage,
               "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}}
    if music:
        effects["music"] = {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False, "sound": music}
    data = {"has_precipitation": False, "temperature": temp, "downfall": 0.0, "effects": effects,
            "spawners": {"ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                         "underground_water_creature": [{"type": "minecraft:glow_squid", "maxCount": 6, "minCount": 4, "weight": 10}],
                         "water_ambient": [], "water_creature": []},
            "spawn_costs": {},
            "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
            "features": feats}
    os.makedirs(f"{R}/worldgen/biome", exist_ok=True)
    with open(f"{R}/worldgen/biome/{name}.json", "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

# The sky of the Dragon World: a warm desert yellow by day (Stark), hazy gold on the horizon.
SKY = 0xE9C56E
FOG = 0xF4DDA6

biome("terras_do_dragao", 1.2, SKY, FOG, 0x2FB6D6, 0x10586A, 0x7CC94A, 0x5DB43A,
      features(["battlegroundsmod:dragon_pillar_plains"],
               ["minecraft:patch_tall_grass", "minecraft:trees_savanna", "minecraft:flower_warm",
                "minecraft:patch_grass_savanna", "minecraft:patch_sugar_cane"]))
biome("picos_do_dragao", 1.0, 0xE3B65E, 0xF0D29A, 0x2FB6D6, 0x10586A, 0x8FBF4E, 0x6DA83C,
      features(["battlegroundsmod:dragon_pillar_peaks"],
               ["minecraft:patch_grass_badlands", "minecraft:trees_savanna", "minecraft:patch_dead_bush_badlands"]),
      music="minecraft:music.overworld.badlands")
# Terra Sagrada de Karin: the dense forest where Korin's tower stands (Stark 2026-11-11: "aquela floresta densa, que é
# onde fica a Torre do Karin"; his frames of the anime: a turquoise sky going pale mint at the horizon over a sea of round
# puffy crowns in light yellow-green). A temperate forest, not a jungle: the puffy trees (world/PuffyTreeFeature), close
# together, flowers and grass under them, a pillar now and then breaking out of the canopy.
biome("terra_sagrada_de_karin", 0.8, 0x6CC4EC, 0xE2F4DA, 0x3FB8D8, 0x10586A, 0x9CD24A, 0x8ACB3C,
      features(["battlegroundsmod:dragon_pillar_forest"],
               ["battlegroundsmod:puffy_tree_karin", "minecraft:flower_default", "minecraft:patch_grass_forest"]))
biome("deserto_vermelho", 2.0, 0xEDB95C, 0xF6D79A, 0x3FA9C9, 0x10586A, 0x9E9E4E, 0x9E8E3E,
      features(["battlegroundsmod:dragon_pillar_desert"],
               ["minecraft:patch_dead_bush_badlands", "minecraft:patch_cactus_decorated"], ["minecraft:ore_gold_extra"]),
      music="minecraft:music.overworld.desert")

# The pillars: one feature, three placements (how often per biome).
os.makedirs(f"{R}/worldgen/configured_feature", exist_ok=True)
os.makedirs(f"{R}/worldgen/placed_feature", exist_ok=True)
with open(f"{R}/worldgen/configured_feature/dragon_pillar.json", "w") as f:
    json.dump({"type": "battlegroundsmod:dragon_pillar", "config": {}}, f, indent=2)
    f.write("\n")
for name, chance in (("plains", 3), ("peaks", 2), ("desert", 4), ("forest", 7)):
    with open(f"{R}/worldgen/placed_feature/dragon_pillar_{name}.json", "w") as f:
        json.dump({"feature": "battlegroundsmod:dragon_pillar", "placement": [
            {"type": "minecraft:rarity_filter", "chance": chance},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"}]}, f, indent=2)
        f.write("\n")

# The puffy trees: close together, on dry ground only.
with open(f"{R}/worldgen/configured_feature/puffy_tree.json", "w") as f:
    json.dump({"type": "battlegroundsmod:puffy_tree", "config": {}}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/puffy_tree_karin.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:puffy_tree", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": 7, "max_inclusive": 11}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")

# Which biomes are the Dragon Ball universe's (universe/UniverseBiomes).
os.makedirs(f"{R}/tags/worldgen/biome", exist_ok=True)
with open(f"{R}/tags/worldgen/biome/universe_dragonball.json", "w") as f:
    json.dump({"values": ["battlegroundsmod:terras_do_dragao", "battlegroundsmod:picos_do_dragao",
                          "battlegroundsmod:deserto_vermelho", "battlegroundsmod:terra_sagrada_de_karin"]}, f, indent=2)
    f.write("\n")
print("ok")
