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

def biome(name, temp, sky, fog, water, water_fog, grass, foliage, feats, music=None, water_life=None, snow=False):
    effects = {"sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog,
               "grass_color": grass, "foliage_color": foliage,
               "mood_sound": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}}
    if music:
        effects["music"] = {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False, "sound": music}
    data = {"has_precipitation": snow, "temperature": temp, "downfall": 0.5 if snow else 0.0, "effects": effects,
            "spawners": {"ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [],
                         "underground_water_creature": [{"type": "minecraft:glow_squid", "maxCount": 6, "minCount": 4, "weight": 10}],
                         "water_ambient": (water_life or {}).get("ambient", []),
                         "water_creature": (water_life or {}).get("creature", [])},
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
# together, flowers and grass under them. No rock pillars: those are the yellow lands' and the desert's, and in the
# forest they made it look like the same place (Stark).
biome("terra_sagrada_de_karin", 0.8, 0x6CC4EC, 0xE2F4DA, 0x3FB8D8, 0x10586A, 0x9CD24A, 0x8ACB3C,
      features([],
               ["battlegroundsmod:puffy_tree_karin", "minecraft:flower_default", "minecraft:patch_grass_forest"]))
# Deserto do Diabo (the biome kept its old id): Stark's Sand Land reference, orange-yellow sand and sandstone hoodoos,
# table rocks and boulders (world/DesertRockFeature) under a strong yellow sky, an orange haze of sand (client/fx/DragonWind
# brings the fog in), dry bushes. A drought, a dangerous place (Stark).
biome("deserto_vermelho", 2.0, 0xE8C46A, 0xE59A4E, 0x3FA9C9, 0x10586A, 0xB8A85A, 0xA8964A,
      features(["battlegroundsmod:desert_rock"],
               ["minecraft:patch_dead_bush_badlands"], ["minecraft:ore_gold_extra"]),
      music="minecraft:music.overworld.desert")

# Mar do Sul (Stark 2026-11-11: "ilhas tropicais da Kame House com piratas", "uma vibe bem ensolarada, um sol forte,
# azul, amarelado"): the warm seas around the Dragon Ball lands. A bright azure sky going warm yellow at the horizon,
# turquoise water over white sand, little palm islands (world/TropicalIslandFeature) and palms on the beaches
# (world/PalmTreeFeature), now and then a pirate ship at anchor (world/PirateShipFeature); warm-sea coral, seagrass, sea
# pickles and kelp under the water (vanilla's, in vanilla's order); tropical fish, pufferfish and dolphins.
biome("mar_do_sul", 0.95, 0x6FC6FF, 0xFFF0BC, 0x2EE0D2, 0x14A4B4, 0x6FE04A, 0x4FD23A,
      features(["battlegroundsmod:tropical_island", "battlegroundsmod:pirate_ship"],
               ["minecraft:warm_ocean_vegetation", "minecraft:seagrass_warm", "minecraft:sea_pickle", "minecraft:kelp_warm",
                "battlegroundsmod:palm_tree_beach"]),
      water_life={"ambient": [{"type": "minecraft:tropical_fish", "maxCount": 8, "minCount": 8, "weight": 25},
                              {"type": "minecraft:pufferfish", "maxCount": 3, "minCount": 1, "weight": 15}],
                  "creature": [{"type": "minecraft:dolphin", "maxCount": 2, "minCount": 1, "weight": 2},
                               {"type": "minecraft:squid", "maxCount": 4, "minCount": 1, "weight": 1}]})

# Norte Gelado (Stark 2026-11-11: "pode usar a criatividade, pode meter bala"): the frozen north, where the Red Ribbon's
# Muscle Tower stood. Deep snow with glazed ice ponds, huge ice crystal clusters (world/IceCrystalFeature) and tall pines
# loaded with snow (world/SnowPineFeature); a pale cold sky, and (client/fx/DragonWind) blizzards that come and go, a cold
# grade with frost at the screen's edges and the aurora over it all at night. It snows when it rains.
# Stark's test: the sky a greyer, less saturated blue, as if the icy wind covered the sun (so it doesn't look like the
# vanilla snow next to it).
biome("norte_gelado", -0.6, 0x98A8BA, 0xC6D0DA, 0x3D57D6, 0x050533, 0x80B497, 0x60A17C,
      features(["battlegroundsmod:ice_crystal"], ["battlegroundsmod:snow_pine"]),
      music="minecraft:music.overworld.frozen_peaks", snow=True)

# Montanhas Paozu (Stark 2026-11-11: "você tem que fazer um diferencial foda, seja o ambiente, seja a geração de
# terreno... pra não ser parecido com uma planície do Mine"): Goku's childhood mountains, the green hills with a forest of
# steep karst spires rising out of them like Guilin's (world/KarstSpireFeature: rock columns, moss, vines, a tree on the
# crown, waterfalls), puffy trees between them, lush grass; clear blue sky and (client/fx/DragonWind) mist lying in the
# valleys between the spires, thicker in the morning and at night.
# Stark's test: a blue-green look (teal sky, minty haze, yellow-green grass).
biome("montanhas_paozu", 0.7, 0x74D2CC, 0xD2F0E2, 0x3FC4E0, 0x0F5A6E, 0x86D24C, 0x5CBE3E,
      features(["battlegroundsmod:karst_spire"],
               ["battlegroundsmod:puffy_tree_paozu", "minecraft:flower_default", "minecraft:patch_grass_forest"]))

# The pillars: one feature, three placements (how often per biome).
os.makedirs(f"{R}/worldgen/configured_feature", exist_ok=True)
os.makedirs(f"{R}/worldgen/placed_feature", exist_ok=True)
with open(f"{R}/worldgen/configured_feature/dragon_pillar.json", "w") as f:
    json.dump({"type": "battlegroundsmod:dragon_pillar", "config": {}}, f, indent=2)
    f.write("\n")
for name, chance in (("plains", 3), ("peaks", 2)):
    with open(f"{R}/worldgen/placed_feature/dragon_pillar_{name}.json", "w") as f:
        json.dump({"feature": "battlegroundsmod:dragon_pillar", "placement": [
            {"type": "minecraft:rarity_filter", "chance": chance},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"}]}, f, indent=2)
        f.write("\n")

# The desert rocks: one or two a chunk, a hoodoo field here and there.
with open(f"{R}/worldgen/configured_feature/desert_rock.json", "w") as f:
    json.dump({"type": "battlegroundsmod:desert_rock", "config": {}}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/desert_rock.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:desert_rock", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"weight": 3, "data": 0}, {"weight": 5, "data": 1}, {"weight": 2, "data": 2}, {"weight": 1, "data": 4}]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
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

# The Mar do Sul's features: islands every few chunks, a pirate ship now and then, palms on any dry beach sand.
for name, data in (("tropical_island", "tropical_island"), ("pirate_ship", "pirate_ship"), ("palm_tree", "palm_tree")):
    with open(f"{R}/worldgen/configured_feature/{name}.json", "w") as f:
        json.dump({"type": f"battlegroundsmod:{data}", "config": {}}, f, indent=2)
        f.write("\n")
with open(f"{R}/worldgen/placed_feature/tropical_island.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:tropical_island", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 5},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/pirate_ship.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:pirate_ship", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 70},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/palm_tree_beach.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:palm_tree", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"weight": 4, "data": 0}, {"weight": 3, "data": 1}, {"weight": 1, "data": 2}]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")

# The Norte Gelado's features: an ice crystal cluster every few chunks, pine woods here and there.
for name in ("ice_crystal", "snow_pine"):
    with open(f"{R}/worldgen/configured_feature/{name}.json", "w") as f:
        json.dump({"type": f"battlegroundsmod:{name}", "config": {}}, f, indent=2)
        f.write("\n")
with open(f"{R}/worldgen/placed_feature/ice_crystal.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:ice_crystal", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 3},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/snow_pine.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:snow_pine", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"weight": 3, "data": 0}, {"weight": 2, "data": 1}, {"weight": 2, "data": 3}, {"weight": 1, "data": 6}]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")

# The Paozu spires, close together (a forest of stone towers), and a few puffy trees between them.
with open(f"{R}/worldgen/configured_feature/karst_spire.json", "w") as f:
    json.dump({"type": "battlegroundsmod:karst_spire", "config": {}}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/karst_spire.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:karst_spire", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"weight": 3, "data": 0}, {"weight": 4, "data": 1}, {"weight": 1, "data": 2}]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")
with open(f"{R}/worldgen/placed_feature/puffy_tree_paozu.json", "w") as f:
    json.dump({"feature": "battlegroundsmod:puffy_tree", "placement": [
        {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 3}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}]}, f, indent=2)
    f.write("\n")

# Which biomes are the Dragon Ball universe's (universe/UniverseBiomes).
os.makedirs(f"{R}/tags/worldgen/biome", exist_ok=True)
with open(f"{R}/tags/worldgen/biome/universe_dragonball.json", "w") as f:
    json.dump({"values": ["battlegroundsmod:terras_do_dragao", "battlegroundsmod:picos_do_dragao",
                          "battlegroundsmod:deserto_vermelho", "battlegroundsmod:terra_sagrada_de_karin", "battlegroundsmod:mar_do_sul",
                          "battlegroundsmod:norte_gelado",
                          "battlegroundsmod:montanhas_paozu"]}, f, indent=2)
    f.write("\n")
print("ok")
